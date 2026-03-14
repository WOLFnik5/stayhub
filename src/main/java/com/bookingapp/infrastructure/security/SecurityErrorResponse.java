package com.bookingapp.infrastructure.security;

import java.time.Instant;

public record SecurityErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
