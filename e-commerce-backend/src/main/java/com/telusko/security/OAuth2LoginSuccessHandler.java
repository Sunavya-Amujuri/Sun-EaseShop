package com.telusko.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import com.telusko.model.RefreshToken;
import com.telusko.model.User;
import com.telusko.repository.RefreshTokenRepository;
import com.telusko.repository.UserRepository;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository users;
    private final RefreshTokenRepository rtokens;
    private final JwtService jwt;

    @Value("${app.oauth2.redirect-url}")
    private String frontendRedirectUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        Map<String, Object> attrs = oauthToken.getPrincipal().getAttributes();

        String githubLogin = (String) attrs.get("login");
        String email = (String) attrs.get("email");
        if (email == null || email.isBlank()) {
            email = githubLogin + "@github.local";
        }

        User user = users.findByEmail(email).orElseThrow();

        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", user.getId());
        claims.put("role", user.getRole().name());

        String accessToken = jwt.generateAccessToken(user.getEmail(), claims);
        String refreshToken = jwt.generateRefreshToken(user.getEmail());

        long refreshExp = System.currentTimeMillis() + 7L * 86400_000;

        rtokens.save(RefreshToken.builder()
                .token(refreshToken)
                .user(user)
                .expiresAt(refreshExp)
                .build());

        String redirectUrl = frontendRedirectUrl
                + "?accessToken=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8)
                + "&refreshToken=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8);

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
