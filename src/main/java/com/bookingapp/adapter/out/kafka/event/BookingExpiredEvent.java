package com.bookingapp.adapter.out.kafka.event;

import java.time.Instant;

public record BookingExpiredEvent(
        Long bookingId,
        Long accommodationId,
        Long userId,
        Instant expiredAt
) {
}
