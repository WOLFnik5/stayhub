package com.bookingapp.web.dto;

import com.bookingapp.domain.model.enums.AccommodationType;

public record AccommodationSummaryResponse(
        Long id,
        AccommodationType type,
        String location,
        String size
) {
}
