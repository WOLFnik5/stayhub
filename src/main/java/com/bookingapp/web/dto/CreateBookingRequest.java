package com.bookingapp.web.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateBookingRequest(
        @NotNull Long accommodationId,
        @NotNull @Future LocalDate checkInDate,
        @NotNull @Future LocalDate checkOutDate
) {
}
