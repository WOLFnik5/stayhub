package com.bookingapp.service;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.enums.AccommodationType;
import com.bookingapp.domain.repository.AccommodationRepository;
import com.bookingapp.exception.BusinessValidationException;
import com.bookingapp.exception.EntityNotFoundDomainException;
import com.bookingapp.infrastructure.kafka.KafkaEventPublisher;
import com.bookingapp.web.dto.PatchAccommodationRequest;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccommodationService {

    private final AccommodationRepository accommodationRepository;
    private final KafkaEventPublisher kafkaEventPublisher;

    public AccommodationService(
            AccommodationRepository accommodationRepository,
            KafkaEventPublisher kafkaEventPublisher
    ) {
        this.accommodationRepository = accommodationRepository;
        this.kafkaEventPublisher = kafkaEventPublisher;
    }

    @Transactional
    public Accommodation createAccommodation(
            AccommodationType type,
            String location,
            String size,
            List<String> amenities,
            BigDecimal dailyRate,
            int availability
    ) {
        Accommodation accommodationToSave = buildAccommodation(
                null,
                type,
                location,
                size,
                amenities,
                dailyRate,
                availability
        );

        Accommodation savedAccommodation = accommodationRepository.save(accommodationToSave);
        kafkaEventPublisher.publishAccommodationCreated(savedAccommodation);
        return savedAccommodation;
    }

    public Accommodation getAccommodationById(Long accommodationId) {
        return accommodationRepository.findById(accommodationId)
                .orElseThrow(() -> new EntityNotFoundDomainException(
                        "Accommodation with id '" + accommodationId + "' was not found"
                ));
    }

    public List<Accommodation> listAccommodations() {
        return accommodationRepository.findAll().stream()
                .filter(accommodation -> accommodation.getAvailability() > 0)
                .toList();
    }

    @Transactional
    public Accommodation updateAccommodation(
            Long accommodationId,
            AccommodationType type,
            String location,
            String size,
            List<String> amenities,
            BigDecimal dailyRate,
            int availability
    ) {
        Accommodation existingAccommodation = getAccommodationById(accommodationId);
        Accommodation updatedAccommodation = buildAccommodation(
                existingAccommodation.getId(),
                type,
                location,
                size,
                amenities,
                dailyRate,
                availability
        );

        return accommodationRepository.save(updatedAccommodation);
    }

    @Transactional
    public void deleteAccommodation(Long accommodationId) {
        if (!accommodationRepository.existsById(accommodationId)) {
            throw new EntityNotFoundDomainException(
                    "Accommodation with id '" + accommodationId + "' was not found"
            );
        }

        accommodationRepository.deleteById(accommodationId);
    }

    @Transactional
    public Accommodation patchAccommodation(Long id, PatchAccommodationRequest request) {
        Accommodation current = getAccommodationById(id);

        AccommodationType type = request.type() != null
                ? request.type()
                : current.getType();

        String location = selectNonBlank(request.location(), current.getLocation(), "location");
        String size = selectNonBlank(request.size(), current.getSize(), "size");

        List<String> amenities = request.amenities() != null
                ? request.amenities()
                : current.getAmenities();

        BigDecimal dailyRate = request.dailyRate() != null
                ? request.dailyRate()
                : current.getDailyRate();

        int availability = request.availability() != null // optional.ofnullable
                ? request.availability()
                : current.getAvailability();

        Accommodation updated = buildAccommodation(
                current.getId(),
                type,
                location,
                size,
                amenities,
                dailyRate,
                availability
        );
        return accommodationRepository.save(updated);
    }

    @Transactional
    public Accommodation decreaseAvailability(Long accommodationId, int units) {
        Accommodation accommodation = getAccommodationById(accommodationId);
        validateAvailabilityUnits(units);

        int updatedAvailability = accommodation.getAvailability() - units;
        if (updatedAvailability < 0) {
            throw new BusinessValidationException("Accommodation availability cannot be negative");
        }

        accommodation.setAvailability(updatedAvailability);
        return accommodationRepository.save(accommodation);
    }

    @Transactional
    public Accommodation increaseAvailability(Long accommodationId, int units) {
        Accommodation accommodation = getAccommodationById(accommodationId);
        validateAvailabilityUnits(units);
        accommodation.setAvailability(accommodation.getAvailability() + units);
        return accommodationRepository.save(accommodation);
    }

    private Accommodation buildAccommodation(
            Long id,
            AccommodationType type,
            String location,
            String size,
            List<String> amenities,
            BigDecimal dailyRate,
            Integer availability
    ) {
        return new Accommodation(
                id,
                validateType(type),
                requireNonBlank(location, "Accommodation location must not be blank"),
                requireNonBlank(size, "Accommodation size must not be blank"),
                sanitizeAmenities(amenities),
                validateDailyRate(dailyRate),
                validateAvailability(availability)
        );
    }

    private static String selectNonBlank(String candidate, String fallback, String fieldName) {
        if (candidate == null) {
            return fallback;
        }
        return requireNonBlank(candidate, "Field '" + fieldName + "' must not be blank");
    }

    private static AccommodationType validateType(AccommodationType type) {
        if (type == null) {
            throw new BusinessValidationException("Accommodation type must not be null");
        }
        return type;
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
        if (dailyRate == null) {
            throw new BusinessValidationException("Accommodation daily rate must not be null");
        }
        if (dailyRate.signum() < 0) {
            throw new BusinessValidationException("Accommodation daily rate must not be negative");
        }
        return dailyRate;
    }

    private static Integer validateAvailability(Integer availability) {
        if (availability == null) {
            throw new BusinessValidationException("Accommodation availability must not be null");
        }
        if (availability < 0) {
            throw new BusinessValidationException("Accommodation availability cannot be negative");
        }
        return availability;
    }

    private static void validateAvailabilityUnits(int units) {
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
