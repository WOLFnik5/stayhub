package com.bookingapp.domain.service.dto;

import com.bookingapp.domain.enums.BookingStatus;

public record BookingFilterQuery(
        Long userId,
        BookingStatus status
) {
}
