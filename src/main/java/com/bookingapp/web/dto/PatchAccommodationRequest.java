package com.bookingapp.web.dto;

import com.bookingapp.domain.enums.AccommodationType;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;

public record PatchAccommodationRequest(
        AccommodationType type,
        String location,
        String size,
        List<String> amenities,
        @PositiveOrZero BigDecimal dailyRate,
        @PositiveOrZero Integer availability
) {
}
