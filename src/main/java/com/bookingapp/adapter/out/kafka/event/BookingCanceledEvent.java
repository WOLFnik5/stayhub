package com.bookingapp.adapter.out.kafka.event;

import java.time.Instant;

public record BookingCanceledEvent(
        Long bookingId,
        Long accommodationId,
        Long userId,
        Instant canceledAt
) {
}
