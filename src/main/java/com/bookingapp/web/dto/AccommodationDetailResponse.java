package com.bookingapp.web.dto;

import com.bookingapp.domain.model.enums.AccommodationType;
import java.math.BigDecimal;
import java.util.List;

public record AccommodationDetailResponse(
        Long id,
        AccommodationType type,
        String location,
        String size,
        List<String> amenities,
        BigDecimal dailyRate,
        Integer availability
) {
}
