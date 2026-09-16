package com.telusko.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.telusko.dto.ApiErrorResponse;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final UserDetailsService uds;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws ServletException, IOException {

        String auth = req.getHeader("Authorization");

        if (auth == null || !auth.startsWith("Bearer ")) {
            chain.doFilter(req, res);
            return;
        }

        String token = auth.substring(7);

        try {
            Jws<Claims> jws = jwt.parseSigned(token);
            Claims claims = jws.getBody();

            String tokenUse = claims.get("token_use", String.class);
            if (!"access".equals(tokenUse)) {
                sendErrorResponse(res, req,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "REFRESH_TOKEN_NOT_ALLOWED",
                        "Use access token for API requests. Refresh token is only for /api/v1/users/refresh-token endpoint.");
                return;
            }

            String email = claims.getSubject();
            UserDetails user = uds.loadUserByUsername(email);
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authToken);

            chain.doFilter(req, res);

        } catch (ExpiredJwtException ex) {

            String refresh = req.getHeader("X-Refresh-Token");

            try {
                if (refresh != null && !refresh.isBlank()) {
                    jwt.parseSigned(refresh);

                    sendErrorResponse(res, req,
                            402,
                            "ACCESS_TOKEN_EXPIRED",
                            "Access token expired. Refresh token is still valid. Call /api/v1/users/refresh-token.");
                    return;
                }
            } catch (JwtException ignore) {
                // refresh is missing/invalid/expired -> fall through to 401
            }
            sendErrorResponse(res, req,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "SESSION_EXPIRED",
                    "Access token expired and refresh token is missing/invalid/expired. Please login again.");

        }catch (JwtException ex) {
            sendErrorResponse(res, req,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "INVALID_TOKEN",
                    "Invalid or malformed JWT token: " + ex.getMessage());

        } catch (Exception ex) {
            ex.printStackTrace();
            sendErrorResponse(res, req,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "AUTHENTICATION_FAILED",
                    "Authentication failed: " + ex.getMessage());
        }
    }

    private void sendErrorResponse(HttpServletResponse res, HttpServletRequest req,
                                   int status, String error, String message) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiErrorResponse body = ApiErrorResponse.builder()
                .status(status)
                .error(error)
                .message(message)
                .path(req.getServletPath())
                .timestamp(LocalDateTime.now().toString())
                .build();

        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(res.getOutputStream(), body);
    }
}