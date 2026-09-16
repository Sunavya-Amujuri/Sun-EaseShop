package com.telusko.security;

import com.telusko.dto.ApiMessage;
import com.telusko.dto.LoginRequest;
import com.telusko.dto.RegisterRequest;
import com.telusko.dto.TokenResponse;
import com.telusko.enums.Role;
import com.telusko.model.PasswordResetToken;
import com.telusko.model.RefreshToken;
import com.telusko.model.User;
import com.telusko.model.VerificationToken;
import com.telusko.repository.PasswordResetTokenRepository;
import com.telusko.repository.RefreshTokenRepository;
import com.telusko.repository.UserRepository;
import com.telusko.repository.VerificationTokenRepository;
import com.telusko.service.MailService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final VerificationTokenRepository vtokens;
    private final RefreshTokenRepository rtokens;
    private final PasswordResetTokenRepository prtokens;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final MailService mail;
    private final OtpAttemptRecorder attemptRecorder;

    // ---------------------------------------------------------------------
    // One-time codes
    //
    // Email confirmation and password reset both use a six digit code rather than a link. The
    // customer reads it from their inbox and types it into the page they are already on, which
    // avoids bouncing them through a URL and works when mail clients rewrite or strip links.
    //
    // Codes are treated as credentials: hashed at rest, short lived, single use, capped attempts.
    // ---------------------------------------------------------------------

    private static final int OTP_LENGTH = 6;
    private static final long OTP_TTL_MS = 10 * 60_000L;      // 10 minutes
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final long RESEND_COOLDOWN_MS = 60_000L;   // 1 minute

    private static final SecureRandom RANDOM = new SecureRandom();

    /** A zero-padded numeric code, so "004321" stays six digits. */
    private String generateOtp() {
        int bound = (int) Math.pow(10, OTP_LENGTH);
        return String.format("%0" + OTP_LENGTH + "d", RANDOM.nextInt(bound));
    }

    /**
     * Shared validity rules for a code.
     * <p>
     * The failure messages are deliberately specific about <em>why</em> a code was rejected -
     * expired, already used, too many attempts - because that is what a confused customer needs,
     * and none of it helps an attacker who must still guess the digits.
     */
    private void assertUsable(boolean used, long expiresAt, int attempts) {
        if (used) {
            throw new IllegalStateException("This code has already been used. Request a new one.");
        }
        if (attempts >= MAX_OTP_ATTEMPTS) {
            throw new IllegalStateException("Too many incorrect attempts. Request a new code.");
        }
        if (expiresAt < System.currentTimeMillis()) {
            throw new IllegalStateException("This code has expired. Request a new one.");
        }
    }

    private void assertNotTooSoon(Long createdAt) {
        if (createdAt != null && System.currentTimeMillis() - createdAt < RESEND_COOLDOWN_MS) {
            long wait = (RESEND_COOLDOWN_MS - (System.currentTimeMillis() - createdAt)) / 1000 + 1;
            throw new IllegalStateException("Please wait " + wait + " seconds before requesting another code.");
        }
    }

    private String otpEmail(String heading, String code, String purpose) {
        return """
               %s

                   %s

               Enter this code on the %s screen. It is valid for 10 minutes and can be used once.
               If you did not request it, you can ignore this email.
               """.formatted(heading, code, purpose);
    }

    @Transactional
    public ApiMessage register(RegisterRequest req) {
        if (users.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }
        if (users.existsByPhoneNumber(req.getPhoneNumber())) {
            throw new IllegalArgumentException("Phone already in use");
        }

        User user = users.save(User.builder()
                .fullName(req.getFullName())
                .email(req.getEmail())
                .password(encoder.encode(req.getPassword()))
                .phoneNumber(req.getPhoneNumber())
                .role(req.getRole() == null ? Role.USER : req.getRole())
                .enabled(false)
                .emailVerified(false)
                .build());

        issueVerificationOtp(user, "Hi " + user.getFullName()
                + ",\n\nWelcome to Sun-EaseMart. Your verification code is:");

        return new ApiMessage("Registered. We emailed a 6 digit verification code.");
    }

    /** Issues a fresh verification code, replacing any earlier one. */
    private void issueVerificationOtp(User user, String heading) {
        vtokens.deleteByUser(user);

        String otp = generateOtp();
        vtokens.save(VerificationToken.builder()
                .token(encoder.encode(otp))          // stored hashed
                .user(user)
                .expiresAt(System.currentTimeMillis() + OTP_TTL_MS)
                .createdAt(System.currentTimeMillis())
                .build());

        mail.send(user.getEmail(), "Your Sun-EaseMart verification code",
                otpEmail(heading, otp, "email verification"));
    }

    @Transactional
    public ApiMessage verifyEmailOtp(String email, String otp) {
        User user = users.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No account found for " + email));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return new ApiMessage("Email already verified. You can sign in.");
        }

        VerificationToken vt = vtokens.findTopByUserOrderByIdDesc(user)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No verification code found. Request a new one."));

        assertUsable(vt.isUsed(), vt.getExpiresAt(), vt.getAttempts());

        if (!encoder.matches(otp, vt.getToken())) {
            // Committed in its own transaction - throwing below rolls this one back.
            int used = attemptRecorder.recordVerificationFailure(vt.getId());
            int left = MAX_OTP_ATTEMPTS - used;
            throw new IllegalArgumentException(left > 0
                    ? "Incorrect code. " + left + " attempt(s) remaining."
                    : "Incorrect code. Request a new one.");
        }

        user.setEnabled(true);
        user.setEmailVerified(true);
        users.save(user);

        vt.setUsed(true);
        vtokens.save(vt);

        return new ApiMessage("Email verified");
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword())
        );

        User user = users.findByEmail(req.getEmail()).orElseThrow();

        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", user.getId());
        claims.put("role", user.getRole().name());

        String accessToken = jwt.generateAccessToken(user.getEmail(), claims);
        String refreshToken = jwt.generateRefreshToken(user.getEmail());

        long refreshExp = System.currentTimeMillis() + 7L * 86400_000; // 7 days
        long accessExp = System.currentTimeMillis() + 15L * 60_000;   // 15 minutes

        rtokens.save(RefreshToken.builder()
                .token(refreshToken).user(user).expiresAt(refreshExp).build());

        return new TokenResponse(accessToken, refreshToken, accessExp, refreshExp);
    }

    @Transactional
    public ApiMessage logout(Long userId) {
        rtokens.deleteAllByUserId(userId);
        return new ApiMessage("Logged out");
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        try {
            // First validate the JWT structure and expiration
            Jws<Claims> jws = jwt.parseSigned(refreshToken);
            Claims claims = jws.getBody();

            // Verify it's a refresh token
            String tokenUse = claims.get("token_use", String.class);
            if (!"refresh".equals(tokenUse)) {
                throw new IllegalArgumentException("Invalid token type. Expected refresh token.");
            }

            String email = claims.getSubject();

            // Check if refresh token exists in database and is valid
            RefreshToken rt = rtokens.findByToken(refreshToken)
                    .orElseThrow(() -> new IllegalArgumentException("Refresh token not found"));

            if (rt.isRevoked()) {
                throw new IllegalStateException("Refresh token has been revoked");
            }

            if (rt.getExpiresAt() < System.currentTimeMillis()) {
                // Clean up expired token
                rtokens.delete(rt);
                throw new IllegalStateException("Refresh token expired. Please login again.");
            }

            // Generate new access token
            User user = rt.getUser();
            Map<String, Object> accessClaims = new HashMap<>();
            accessClaims.put("uid", user.getId());
            accessClaims.put("role", user.getRole().name());

            String newAccessToken = jwt.generateAccessToken(user.getEmail(), accessClaims);
            long accessExp = System.currentTimeMillis() + 15L * 60_000; // 15 minutes

            // Return new access token with same refresh token
            return new TokenResponse(newAccessToken, refreshToken, accessExp, rt.getExpiresAt());

        } catch (io.jsonwebtoken.ExpiredJwtException ex) {
            // Refresh token JWT itself is expired
            throw new IllegalStateException("Refresh token expired. Please login again.");
        } catch (io.jsonwebtoken.JwtException ex) {
            // Invalid JWT
            throw new IllegalArgumentException("Invalid refresh token format");
        }
    }

    @Transactional
    public ApiMessage resendVerification(String email) {
        User user = users.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return new ApiMessage("Email already verified");
        }

        // Rate limited so the endpoint cannot be used to flood somebody's inbox.
        vtokens.findTopByUserOrderByIdDesc(user)
                .ifPresent(vt -> assertNotTooSoon(vt.getCreatedAt()));

        issueVerificationOtp(user, "Here is your new verification code:");

        return new ApiMessage("A new 6 digit code has been emailed to you.");
    }

    @Transactional
    public ApiMessage changePassword(Long userId, String oldPw, String newPw) {
        User user = users.findById(userId).orElseThrow();

        if (!encoder.matches(oldPw, user.getPassword())) {
            throw new IllegalArgumentException("Old password incorrect");
        }

        user.setPassword(encoder.encode(newPw));
        users.save(user);
        rtokens.deleteAllByUserId(userId); // kill old refresh tokens

        return new ApiMessage("Password changed");
    }

    /**
     * Emails a reset code.
     * <p>
     * The reply is the same whether or not the address exists, so this endpoint cannot be used to
     * discover who has an account.
     */
    @Transactional
    public ApiMessage forgotPassword(String email) {
        String generic = "If an account exists for that email, a 6 digit code has been sent.";

        User user = users.findByEmail(email).orElse(null);
        if (user == null) {
            return new ApiMessage(generic);
        }

        prtokens.findTopByUserOrderByIdDesc(user)
                .ifPresent(pr -> assertNotTooSoon(pr.getCreatedAt()));

        prtokens.deleteByUser(user);

        String otp = generateOtp();
        prtokens.save(PasswordResetToken.builder()
                .token(encoder.encode(otp))          // stored hashed
                .user(user)
                .expiresAt(System.currentTimeMillis() + OTP_TTL_MS)
                .createdAt(System.currentTimeMillis())
                .build());

        mail.send(user.getEmail(), "Your Sun-EaseMart password reset code",
                otpEmail("We received a request to reset your Sun-EaseMart password. Your code is:",
                        otp, "password reset"));

        return new ApiMessage(generic);
    }

    @Transactional
    public ApiMessage resetPasswordWithOtp(String email, String otp, String newPw) {
        User user = users.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No account found for " + email));

        PasswordResetToken pr = prtokens.findTopByUserOrderByIdDesc(user)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No reset code found. Request a new one."));

        assertUsable(pr.isUsed(), pr.getExpiresAt(), pr.getAttempts());

        if (!encoder.matches(otp, pr.getToken())) {
            int used = attemptRecorder.recordResetFailure(pr.getId());
            int left = MAX_OTP_ATTEMPTS - used;
            throw new IllegalArgumentException(left > 0
                    ? "Incorrect code. " + left + " attempt(s) remaining."
                    : "Incorrect code. Request a new one.");
        }

        user.setPassword(encoder.encode(newPw));
        users.save(user);

        pr.setUsed(true);
        prtokens.save(pr);

        // Any session opened with the old password is no longer trusted.
        rtokens.deleteAllByUserId(user.getId());

        return new ApiMessage("Password reset successful");
    }

    @Transactional
    public ApiMessage assignRole(Long userId, Role newRole, Long adminId) {
        User admin = users.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        if (admin.getRole() != Role.ADMIN) {
            throw new IllegalStateException("Only administrators can assign roles");
        }

        User user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (userId.equals(adminId)) {
            throw new IllegalStateException("You cannot change your own role");
        }

        Role oldRole = user.getRole();

        user.setRole(newRole);
        users.save(user);

        rtokens.deleteAllByUserId(userId);

        return new ApiMessage(
                String.format("User role changed from %s to %s. User must login again.",
                        oldRole, newRole)
        );
    }
}