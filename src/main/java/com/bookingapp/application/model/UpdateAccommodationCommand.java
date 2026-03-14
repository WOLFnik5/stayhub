package com.bookingapp.application.model;

import com.bookingapp.domain.enums.AccommodationType;

import java.math.BigDecimal;
import java.util.List;

public record UpdateAccommodationCommand(
        Long accommodationId,
        AccommodationType type,
        String location,
        String size,
        List<String> amenities,
        BigDecimal dailyRate,
        Integer availability
) {
}
