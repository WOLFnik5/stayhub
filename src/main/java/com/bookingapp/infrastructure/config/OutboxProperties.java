package com.bookingapp.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
        int maxAttempts,
        int sentRetentionDays,
        long publishFixedDelayMs,
        String cleanupCron
) {
}
