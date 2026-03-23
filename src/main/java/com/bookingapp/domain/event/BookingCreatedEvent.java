package com.bookingapp.domain.event;

import java.time.Instant;
import java.time.LocalDate;

public record BookingCreatedEvent(
        Long bookingId,
        Long accommodationId,
        Long userId,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        String status,
        Instant createdAt
) {
}
