package com.bookingapp.web.dto;

import com.bookingapp.domain.model.enums.AccommodationType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record UpdateAccommodationRequest(
        @NotNull AccommodationType type,
        @NotBlank @Size(max = 255) String location,
        @NotBlank @Size(max = 255) String size,
        @NotNull List<@NotBlank @Size(max = 255) String> amenities,
        @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal dailyRate,
        @NotNull @PositiveOrZero Integer availability
) {
}
