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
            java.util.List<String> amenities,
            java.math.BigDecimal dailyRate,
            int availability
    ) {
        Accommodation accommodationToSave = Accommodation.createNew(
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
            java.util.List<String> amenities,
            java.math.BigDecimal dailyRate,
            int availability
    ) {
        Accommodation existingAccommodation = getAccommodationById(accommodationId);

        Accommodation updatedAccommodation = existingAccommodation.updateDetails(
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

        int availability = request.availability() != null
                ? request.availability()
                : current.getAvailability();

        Accommodation updated = current.updateDetails(
                type,
                location,
                size,
                amenities,
                dailyRate,
                availability
        );
        return accommodationRepository.save(updated);
    }

    private static String selectNonBlank(String candidate, String fallback, String fieldName) {
        if (candidate == null) {
            return fallback;
        }
        String trimmed = candidate.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessValidationException("Field '" + fieldName + "' must not be blank");
        }
        return trimmed;
    }
}
