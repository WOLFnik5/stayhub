package com.bookingapp.web.dto;

import com.bookingapp.domain.model.enums.BookingStatus;
import java.time.LocalDate;

public record BookingDetailResponse(
        Long id,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        Long accommodationId,
        Long userId,
        BookingStatus status,
        AccommodationSummaryResponse accommodation
) {
}
