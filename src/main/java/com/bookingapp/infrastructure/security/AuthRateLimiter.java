package com.bookingapp.infrastructure.security;

import com.bookingapp.exception.RateLimitExceededException;
import com.bookingapp.infrastructure.config.AuthRateLimitProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AuthRateLimiter {

    private static final String LOGIN = "login";
    private static final String REGISTER = "register";

    private final AuthRateLimitProperties properties;
    private final Clock clock;
    private final ConcurrentMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Autowired
    public AuthRateLimiter(AuthRateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AuthRateLimiter(AuthRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void checkLogin(String clientAddress) {
        check(LOGIN, clientAddress, properties.getLoginAttempts());
    }

    public void checkRegister(String clientAddress) {
        check(REGISTER, clientAddress, properties.getRegisterAttempts());
    }

    private void check(String operation, String clientAddress, int limit) {
        Instant now = clock.instant();
        String key = operation + ":" + normalize(clientAddress);
        ensureCapacity(key, now);

        WindowCounter counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expiresAt().isBefore(now)
                    || existing.expiresAt().equals(now)) {
                return new WindowCounter(1, now.plusSeconds(properties.getWindowSeconds()));
            }
            return new WindowCounter(existing.attempts() + 1, existing.expiresAt());
        });

        if (counter.attempts() > limit) {
            throw new RateLimitExceededException(
                    "Too many authentication attempts. Try again later."
            );
        }
    }

    private void ensureCapacity(String key, Instant now) {
        if (counters.containsKey(key) || counters.size() < properties.getMaxTrackedClients()) {
            return;
        }
        counters.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        if (counters.size() >= properties.getMaxTrackedClients()) {
            throw new RateLimitExceededException(
                    "Too many authentication attempts. Try again later."
            );
        }
    }

    private String normalize(String clientAddress) {
        if (clientAddress == null || clientAddress.isBlank()) {
            return "unknown";
        }
        return clientAddress;
    }

    private record WindowCounter(int attempts, Instant expiresAt) {
    }
}
