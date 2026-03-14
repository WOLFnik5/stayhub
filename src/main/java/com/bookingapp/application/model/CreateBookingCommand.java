package com.bookingapp.application.model;

import java.time.LocalDate;

public record CreateBookingCommand(
        Long accommodationId,
        LocalDate checkInDate,
        LocalDate checkOutDate
) {
}
