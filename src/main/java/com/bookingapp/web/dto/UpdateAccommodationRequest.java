package com.bookingapp.web.dto;

import com.bookingapp.domain.enums.AccommodationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;

public record UpdateAccommodationRequest(
        @NotNull AccommodationType type,
        @NotBlank String location,
        @NotBlank String size,
        @NotNull List<@NotBlank String> amenities,
        @NotNull @PositiveOrZero BigDecimal dailyRate,
        @NotNull @PositiveOrZero Integer availability
) {
}
