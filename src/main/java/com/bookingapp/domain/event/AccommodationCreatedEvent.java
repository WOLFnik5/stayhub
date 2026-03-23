package com.bookingapp.domain.event;

import java.math.BigDecimal;
import java.time.Instant;

public record AccommodationCreatedEvent(
        Long accommodationId,
        String type,
        String location,
        BigDecimal dailyRate,
        Integer availability,
        Instant createdAt
) {
}
