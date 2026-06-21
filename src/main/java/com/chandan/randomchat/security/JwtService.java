package com.chandan.randomchat.security;

import com.chandan.randomchat.exception.InvalidTokenException;
import com.chandan.randomchat.exception.TokenExpiredException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * JwtService — creates and verifies JWT tokens.
 *
 * Token payload (claims):
 *   sub   = userId (UUID string)        — standard JWT subject
 *   appId = appId string                — which app this user belongs to
 *   iat   = issued at timestamp
 *   exp   = expiry timestamp (30 days)
 *
 * Algorithm: HS256 (HMAC-SHA256) with a secret key from application.properties
 *
 * How it's used:
 *   1. /api/user/init → JwtService.generateToken(userId, appId) → returned to Android
 *   2. Every other request → JwtAuthFilter extracts Bearer token → JwtService.validateToken()
 *   3. Extracted userId used in controller/service — NO DB call for identification
 */
@Service
@Slf4j
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secretString;

    @Value("${app.jwt.expiry-days:30}")
    private int expiryDays;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        // Key must be at least 32 chars for HS256
        // In prod: openssl rand -base64 64 → set as JWT_SECRET env var
        if (secretString.length() < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 characters long");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretString.getBytes(StandardCharsets.UTF_8));
        log.info("JWT service initialized. Token expiry: {} days", expiryDays);
    }

    // =========================================================================
    // Generate
    // =========================================================================

    /**
     * Generate a JWT for a user after successful /init.
     * Android saves this token to DataStore and sends it on every request.
     *
     * @param userId UUID of the user
     * @param appId  which app (e.g. "app1")
     * @return signed JWT string
     */
    public String generateToken(UUID userId, String appId) {
        Instant now    = Instant.now();
        Instant expiry = now.plus(expiryDays, ChronoUnit.DAYS);

        return Jwts.builder()
                .subject(userId.toString())          // "sub" claim = userId
                .claim("appId", appId)               // custom claim
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)                 // HS256 by default with SecretKey
                .compact();
    }

    // =========================================================================
    // Validate + Extract
    // =========================================================================

    /**
     * Validate the JWT and extract claims.
     * Throws TokenExpiredException or InvalidTokenException on failure.
     * Called by JwtAuthFilter on every request (except /api/user/init).
     *
     * @param token raw JWT string (without "Bearer " prefix)
     * @return Claims object containing userId and appId
     */
    public Claims validateAndExtract(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            // Specific case: token was valid but has expired
            // Android catches 401 + TOKEN_EXPIRED → auto re-calls /init
            throw new TokenExpiredException();
        } catch (JwtException | IllegalArgumentException ex) {
            // Any other JWT problem: bad signature, malformed, null
            throw new InvalidTokenException(ex.getMessage());
        }
    }

    /**
     * Extract userId UUID from a validated token.
     * Call validateAndExtract() first, then pass claims here.
     */
    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    /**
     * Extract appId from a validated token.
     */
    public String extractAppId(Claims claims) {
        return claims.get("appId", String.class);
    }

    /**
     * Convenience: validate token and return userId directly.
     * Used in controllers that just need the userId fast.
     */
    public UUID getUserIdFromToken(String token) {
        return extractUserId(validateAndExtract(token));
    }
}