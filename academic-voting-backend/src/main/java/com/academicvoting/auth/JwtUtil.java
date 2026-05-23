package com.academicvoting.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.io.Decoders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT utility — generates and validates stateless tokens using JJWT 0.12.x.
 *
 * <p>Tokens carry:
 * <ul>
 *   <li>{@code sub}  — userId (e.g., "student001")</li>
 *   <li>{@code role} — "STUDENT" or "ADMIN"</li>
 *   <li>{@code name} — display name</li>
 *   <li>{@code iat}  — issued-at timestamp</li>
 *   <li>{@code exp}  — expiry timestamp</li>
 * </ul>
 *
 * <p>The signing key is an HMAC-SHA256 key derived from a base64-encoded secret.
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String base64Secret;

    @Value("${jwt.expiry-ms:86400000}")
    private long expiryMs;

    // ── Key Derivation ────────────────────────────────────────────────────────

    /**
     * Derives the HMAC-SHA256 signing key from the configured base64 secret.
     * Called lazily to allow @Value injection to complete first.
     */
    private SecretKey signingKey() {
        byte[] keyBytes = Decoders.BASE64.decode(base64Secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ── Token Generation ──────────────────────────────────────────────────────

    /**
     * Generates a signed JWT for an authenticated university user.
     *
     * @param user The authenticated user
     * @return Compact JWT string (header.payload.signature)
     */
    public String generateToken(UniversityUser user) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expiryMs);

        return Jwts.builder()
            .subject(user.getUserId())
            .claim("role",  user.getRole())
            .claim("name",  user.getName())
            .claim("email", user.getEmail())
            .issuedAt(now)
            .expiration(expiry)
            .signWith(signingKey())
            .compact();
    }

    // ── Token Validation ──────────────────────────────────────────────────────

    /**
     * Validates a token and returns its claims.
     *
     * @param token Compact JWT string
     * @return Parsed {@link Claims}
     * @throws JwtException if the token is invalid, expired, or tampered
     */
    public Claims validateAndParse(String token) {
        return Jwts.parser()
            .verifyWith(signingKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /**
     * Returns {@code true} if the token is valid and not expired.
     *
     * @param token Compact JWT string
     */
    public boolean isValid(String token) {
        try {
            validateAndParse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    // ── Claim Extraction ──────────────────────────────────────────────────────

    /** Extracts the subject (userId) from a token without re-validating. */
    public String extractUserId(String token) {
        return validateAndParse(token).getSubject();
    }

    /** Extracts the role claim from a token. */
    public String extractRole(String token) {
        return validateAndParse(token).get("role", String.class);
    }

    /** Extracts the name claim from a token. */
    public String extractName(String token) {
        return validateAndParse(token).get("name", String.class);
    }
}
