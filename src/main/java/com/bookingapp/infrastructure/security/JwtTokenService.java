package com.bookingapp.infrastructure.security;

import com.bookingapp.domain.model.User;
import com.bookingapp.domain.model.enums.UserRole;
import com.bookingapp.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenService {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ROLE = "role";

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = createSigningKey(jwtProperties.getSecret());
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.getExpirationMinutes(), ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public AuthenticatedUserPrincipal parsePrincipal(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long userId = claims.get(CLAIM_USER_ID, Long.class);
        String email = claims.getSubject();
        String role = claims.get(CLAIM_ROLE, String.class);

        return new AuthenticatedUserPrincipal(userId, email, UserRole.valueOf(role));
    }

    private SecretKey createSigningKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT_SECRET must be configured");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT_SECRET must be valid Base64");
        }
        if (decoded.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 decoded bytes");
        }
        return Keys.hmacShaKeyFor(decoded);
    }
}
