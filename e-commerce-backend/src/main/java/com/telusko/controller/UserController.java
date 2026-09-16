package com.telusko.controller;

import com.telusko.dto.*;
import com.telusko.enums.Role;
import com.telusko.model.User;
import com.telusko.repository.UserRepository;
import com.telusko.security.AuthService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;


import java.util.Map;


@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Authentication", description = "User registration, login, and account management")
public class UserController {

    private final AuthService authService;
    private final UserRepository users;

    // Emailed links now point at the storefront, whose address comes from configuration
    // (app.frontend.base-url) rather than being derived from the incoming request.

    @PostMapping("/register")
    public ResponseEntity<ApiMessage> register(@Valid @RequestBody RegisterRequest req) {
        ApiMessage response = authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Confirms an email address with the six digit code that was mailed out.
     * <p>
     * This replaces the old {@code GET /verify-email/{token}} link. A code the customer types on
     * the page they are already on is simpler than a link, and it does not break when a mail
     * client rewrites or strips URLs.
     */
    @PostMapping("/verify-email")
    public ResponseEntity<ApiMessage> verifyEmail(@Valid @RequestBody VerifyOtpRequest req) {
        ApiMessage response = authService.verifyEmailOtp(req.getEmail(), req.getOtp());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-email-verification")
    public ResponseEntity<ApiMessage> resend(@RequestParam String email) {
        ApiMessage response = authService.resendVerification(email);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        TokenResponse response = authService.login(req);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiMessage> logout(@AuthenticationPrincipal UserDetails me) {
        User user = users.findByEmail(me.getUsername()).orElseThrow();
        ApiMessage response = authService.logout(user.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<TokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest req) {
        TokenResponse response = authService.refresh(req.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<User> me(@AuthenticationPrincipal UserDetails me) {
        User user = users.findByEmail(me.getUsername()).orElseThrow();
        user.setPassword("********"); // mask password
        return ResponseEntity.ok(user);
    }

    @PostMapping("/change-password")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiMessage> changePassword(@AuthenticationPrincipal UserDetails me,
                                                     @Valid @RequestBody ChangePasswordRequest req) {
        User user = users.findByEmail(me.getUsername()).orElseThrow();
        ApiMessage response = authService.changePassword(user.getId(),
                req.getOldPassword(), req.getNewPassword());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiMessage> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        ApiMessage response = authService.forgotPassword(req.getEmail());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiMessage> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        ApiMessage response = authService.resetPasswordWithOtp(
                req.getEmail(), req.getOtp(), req.getNewPassword());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/assign-role/{userId}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, String>> assignRole(@PathVariable Long userId, @RequestParam String role,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        User admin = users.findByEmail(currentUser.getUsername())
                .orElseThrow(() -> new EntityNotFoundException(
                        "User not found: " + currentUser.getUsername()));

        authService.assignRole(userId, Role.valueOf(role), admin.getId());

        return ResponseEntity.ok(
                Map.of("message", "Role updated successfully to " + role)
        );
    }

}