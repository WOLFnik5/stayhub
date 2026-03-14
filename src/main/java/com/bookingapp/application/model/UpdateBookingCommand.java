package com.bookingapp.application.model;

import java.time.LocalDate;

public record UpdateBookingCommand(
        Long bookingId,
        LocalDate checkInDate,
        LocalDate checkOutDate
) {
}
