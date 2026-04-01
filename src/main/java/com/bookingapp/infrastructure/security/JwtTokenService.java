package com.bookingapp.infrastructure.security;

import com.bookingapp.domain.model.enums.UserRole;
import com.bookingapp.domain.model.User;
import com.bookingapp.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenService {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ROLE = "role";

    private final JwtProperties jwtProperties;

    public JwtTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
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
                .signWith(getSigningKey())
                .compact();
    }

    public AuthenticatedUserPrincipal parsePrincipal(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long userId = claims.get(CLAIM_USER_ID, Long.class);
        String email = claims.getSubject();
        String role = claims.get(CLAIM_ROLE, String.class);

        return new AuthenticatedUserPrincipal(userId, email, UserRole.valueOf(role));
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(decodeSecret(jwtProperties.getSecret()));
    }

    private byte[] decodeSecret(String secret) {
        try {
            return Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException exception) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
