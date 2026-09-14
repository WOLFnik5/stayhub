package com.bookingapp.service;

import java.util.List;

public record BookingExpirationResult(
        int expiredCount,
        List<Long> expiredBookingIds,
        List<Long> failedBookingIds
) {

    public BookingExpirationResult(int expiredCount, List<Long> expiredBookingIds) {
        this(expiredCount, expiredBookingIds, List.of());
    }
}
