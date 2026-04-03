package com.bookingapp.domain.model;

import com.bookingapp.domain.model.enums.AccommodationType;
import com.bookingapp.exception.BusinessValidationException;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Getter
public final class Accommodation {

    private final Long id;
    private final AccommodationType type;
    private final String location;
    private final String size;
    private final List<String> amenities;
    private final BigDecimal dailyRate;
    private final Integer availability;

    public Accommodation(
            Long id,
            AccommodationType type,
            String location,
            String size,
            List<String> amenities,
            BigDecimal dailyRate,
            Integer availability
    ) {
        this.id = id;
        this.type = Objects.requireNonNull(type, "Accommodation type must not be null");
        this.location = requireNonBlank(location, "Accommodation location must not be blank");
        this.size = requireNonBlank(size, "Accommodation size must not be blank");
        this.amenities = sanitizeAmenities(amenities);
        this.dailyRate = validateDailyRate(dailyRate);
        this.availability = validateAvailability(availability);
    }

    public static Accommodation createNew(
            AccommodationType type,
            String location,
            String size,
            List<String> amenities,
            BigDecimal dailyRate,
            Integer availability
    ) {
        return new Accommodation(null, type, location, size, amenities, dailyRate, availability);
    }

    public Accommodation updateDetails(
            AccommodationType type,
            String location,
            String size,
            List<String> amenities,
            BigDecimal dailyRate,
            Integer availability
    ) {
        return new Accommodation(id, type, location, size, amenities, dailyRate, availability);
    }

    public Accommodation decreaseAvailability(int units) {
        validateUnits(units);
        int updatedAvailability = availability - units;
        if (updatedAvailability < 0) {
            throw new BusinessValidationException("Accommodation availability cannot be negative");
        }
        return new Accommodation(
                id, type, location, size, amenities, dailyRate, updatedAvailability
        );
    }

    public Accommodation increaseAvailability(int units) {
        validateUnits(units);
        return new Accommodation(
                id, type, location, size, amenities, dailyRate, availability + units
        );
    }

    private static List<String> sanitizeAmenities(List<String> amenities) {
        if (amenities == null) {
            return List.of();
        }

        return amenities.stream()
                .map(amenity -> requireNonBlank(amenity,
                        "Accommodation amenity must not be blank"))
                .toList();
    }

    private static BigDecimal validateDailyRate(BigDecimal dailyRate) {
        Objects.requireNonNull(dailyRate, "Accommodation daily rate must not be null");
        if (dailyRate.signum() < 0) {
            throw new BusinessValidationException("Accommodation daily rate must not be negative");
        }
        return dailyRate;
    }

    private static Integer validateAvailability(Integer availability) {
        Objects.requireNonNull(availability, "Accommodation availability must not be null");
        if (availability < 0) {
            throw new BusinessValidationException("Accommodation availability cannot be negative");
        }
        return availability;
    }

    private static void validateUnits(int units) {
        if (units <= 0) {
            throw new BusinessValidationException(
                    "Availability change units must be greater than zero"
            );
        }
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessValidationException(message);
        }
        return value.trim();
    }
}
