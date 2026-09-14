package com.bookingapp.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookingapp.exception.RateLimitExceededException;
import com.bookingapp.infrastructure.config.AuthRateLimitProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuthRateLimiterTest {

    @Test
    void rejectsRequestsOverLimitForSameClientAndOperation() {
        AuthRateLimitProperties properties = properties();
        AuthRateLimiter limiter = new AuthRateLimiter(properties,
                Clock.fixed(Instant.parse("2026-09-14T10:00:00Z"), ZoneOffset.UTC));

        limiter.checkLogin("192.0.2.1");
        limiter.checkLogin("192.0.2.1");

        assertThatThrownBy(() -> limiter.checkLogin("192.0.2.1"))
                .isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> limiter.checkRegister("192.0.2.1"))
                .doesNotThrowAnyException();
        assertThatCode(() -> limiter.checkLogin("192.0.2.2"))
                .doesNotThrowAnyException();
    }

    private AuthRateLimitProperties properties() {
        AuthRateLimitProperties properties = new AuthRateLimitProperties();
        properties.setLoginAttempts(2);
        properties.setRegisterAttempts(1);
        properties.setWindowSeconds(60);
        properties.setMaxTrackedClients(100);
        return properties;
    }
}
