package com.bookingapp.domain.event;

import java.time.Instant;

public record BookingExpiredEvent(
        Long bookingId,
        Long accommodationId,
        Long userId,
        Instant expiredAt
) {
}
