package com.bookingapp.web.mapper;

import com.bookingapp.domain.exception.BusinessValidationException;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.web.dto.AccommodationDetailResponse;
import com.bookingapp.web.dto.AccommodationListResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AccommodationWebMapper {

    public AccommodationListResponse toListResponse(Accommodation accommodation) {
        return new AccommodationListResponse(
                accommodation.getId(),
                accommodation.getType(),
                accommodation.getLocation(),
                accommodation.getSize(),
                accommodation.getDailyRate(),
                accommodation.getAvailability()
        );
    }

    public AccommodationDetailResponse toDetailResponse(Accommodation accommodation) {
        return new AccommodationDetailResponse(
                accommodation.getId(),
                accommodation.getType(),
                accommodation.getLocation(),
                accommodation.getSize(),
                accommodation.getAmenities(),
                accommodation.getDailyRate(),
                accommodation.getAvailability()
        );
    }

    public String selectString(String candidate, String currentValue, String fieldName) {
        if (candidate == null) {
            return currentValue;
        }

        String trimmed = candidate.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessValidationException("Field '" + fieldName + "' must not be blank");
        }
        return trimmed;
    }

    public List<String> selectAmenities(List<String> amenities, List<String> currentAmenities) {
        if (amenities == null) {
            return currentAmenities;
        }

        return amenities.stream()
                .map(this::sanitizeAmenity)
                .toList();
    }

    private String sanitizeAmenity(String amenity) {
        if (amenity == null || amenity.isBlank()) {
            throw new BusinessValidationException("Accommodation amenity must not be blank");
        }
        return amenity.trim();
    }
}
