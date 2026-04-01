package com.bookingapp.web.dto;

import com.bookingapp.domain.model.enums.AccommodationType;
import java.math.BigDecimal;

public record AccommodationListResponse(
        Long id,
        AccommodationType type,
        String location,
        String size,
        BigDecimal dailyRate,
        Integer availability
) {
}
