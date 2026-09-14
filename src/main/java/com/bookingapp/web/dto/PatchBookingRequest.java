package com.bookingapp.web.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import java.time.LocalDate;

public record PatchBookingRequest(
        @FutureOrPresent LocalDate checkInDate,
        @Future LocalDate checkOutDate
) {
}
