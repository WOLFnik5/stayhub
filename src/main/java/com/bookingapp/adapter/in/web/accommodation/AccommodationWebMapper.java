package com.bookingapp.adapter.in.web.accommodation;

import com.bookingapp.application.model.CreateAccommodationCommand;
import com.bookingapp.application.model.UpdateAccommodationCommand;
import com.bookingapp.domain.exception.BusinessValidationException;
import com.bookingapp.domain.model.Accommodation;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AccommodationWebMapper {

    public CreateAccommodationCommand toCreateCommand(CreateAccommodationRequest request) {
        return new CreateAccommodationCommand(
                request.type(),
                request.location(),
                request.size(),
                request.amenities(),
                request.dailyRate(),
                request.availability()
        );
    }

    public UpdateAccommodationCommand toUpdateCommand(Long accommodationId, UpdateAccommodationRequest request) {
        return new UpdateAccommodationCommand(
                accommodationId,
                request.type(),
                request.location(),
                request.size(),
                request.amenities(),
                request.dailyRate(),
                request.availability()
        );
    }

    public UpdateAccommodationCommand toPatchCommand(Long accommodationId, PatchAccommodationRequest request, Accommodation current) {
        return new UpdateAccommodationCommand(
                accommodationId,
                request.type() != null ? request.type() : current.getType(),
                selectString(request.location(), current.getLocation(), "location"),
                selectString(request.size(), current.getSize(), "size"),
                selectAmenities(request.amenities(), current.getAmenities()),
                request.dailyRate() != null ? request.dailyRate() : current.getDailyRate(),
                request.availability() != null ? request.availability() : current.getAvailability()
        );
    }

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

    private String selectString(String candidate, String currentValue, String fieldName) {
        if (candidate == null) {
            return currentValue;
        }

        String trimmed = candidate.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessValidationException("Field '" + fieldName + "' must not be blank");
        }
        return trimmed;
    }

    private List<String> selectAmenities(List<String> amenities, List<String> currentAmenities) {
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
