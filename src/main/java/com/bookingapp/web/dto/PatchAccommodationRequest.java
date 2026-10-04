package com.bookingapp.web.dto;

import com.bookingapp.domain.model.enums.AccommodationType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record PatchAccommodationRequest(
        AccommodationType type,
        @Size(max = 255) String location,
        @Size(max = 255) String size,
        List<@NotBlank @Size(max = 255) String> amenities,
        @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal dailyRate,
        @PositiveOrZero Integer availability
) {
}
