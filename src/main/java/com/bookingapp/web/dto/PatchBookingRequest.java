package com.bookingapp.web.dto;

import jakarta.validation.constraints.Future;
import java.time.LocalDate;

public record PatchBookingRequest(
        @Future LocalDate checkInDate,
        @Future LocalDate checkOutDate
) {
}
