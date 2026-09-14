package com.bookingapp.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.security.auth-rate-limit")
public class AuthRateLimitProperties {

    @Min(1)
    private int loginAttempts = 10;

    @Min(1)
    private int registerAttempts = 5;

    @Min(1)
    private long windowSeconds = 60;

    @Min(1)
    private int maxTrackedClients = 100_000;
}
