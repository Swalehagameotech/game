package com.teenpatti.platform.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Isolated JWT Token Utility for generating, parsing, and validating JWT access & refresh tokens.
 * Can be reused across REST security filters and WebSocket message handlers.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret:dev_secret_key_change_in_production_environment_with_at_least_256_bits_length}") String secret,
            @Value("${jwt.access-expiration:900000}") long accessExpirationMs,
            @Value("${jwt.refresh-expiration:604800000}") long refreshExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateAccessToken(String userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessExpirationMs);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim("tokenType", TokenType.ACCESS.name())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(String userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpirationMs);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim("tokenType", TokenType.REFRESH.name())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public String getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.getSubject();
    }

    public TokenType getTokenTypeFromToken(String token) {
        Claims claims = parseClaims(token);
        String tokenTypeStr = claims.get("tokenType", String.class);
        return TokenType.valueOf(tokenTypeStr);
    }

    public Date getExpirationFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.getExpiration();
    }

    public boolean validateToken(String token, TokenType expectedType) {
        try {
            Claims claims = parseClaims(token);
            String tokenTypeStr = claims.get("tokenType", String.class);
            return expectedType.name().equals(tokenTypeStr) && !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token validation failure: {}", e.getMessage());
            return false;
        }
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public long getAccessExpirationMs() {
        return accessExpirationMs;
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
