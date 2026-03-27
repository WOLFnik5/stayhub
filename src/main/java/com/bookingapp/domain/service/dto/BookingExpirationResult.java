package com.bookingapp.domain.service.dto;

import java.util.List;

public record BookingExpirationResult(
        int expiredCount,
        List<Long> expiredBookingIds
) {
}
