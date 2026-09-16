package com.telusko.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Component
public class JwtService {

    private final SecretKey key;
    private final String issuer;
    private final long accessMinutes;
    private final long refreshDays;


    private static final String TOKEN_USE = "token_use";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    public JwtService(@Value("${security.jwt.secret}")String secret,
                      @Value("${security.jwt.issuer}") String issuer,
                      @Value("${security.jwt.accessMinutes}")long accessMinutes,
                      @Value("${security.jwt.refreshDays}")long refreshDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessMinutes = accessMinutes;
        this.refreshDays = refreshDays;
    }

    public Jws<Claims> parseSigned(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
    }

    public String generateAccessToken(String subject, Map<String, Object> claims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setHeaderParam(Header.TYPE, Header.JWT_TYPE)
                .setIssuer(issuer)
                .setSubject(subject)
                .addClaims(claims)
                .claim(TOKEN_USE, ACCESS)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(accessMinutes * 60)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(String subject) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setIssuer(issuer)
                .setSubject(subject)
                .claim(TOKEN_USE, REFRESH)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(refreshDays * 86400)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}

