package com.bookingapp.application.model;

import java.util.List;

public record ExpireBookingsResult(
        int expiredCount,
        List<Long> expiredBookingIds
) {
}
