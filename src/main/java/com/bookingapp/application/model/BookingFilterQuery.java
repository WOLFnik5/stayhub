package com.bookingapp.application.model;

import com.bookingapp.domain.enums.BookingStatus;

public record BookingFilterQuery(
        Long userId,
        BookingStatus status
) {
}
