package com.bookingapp.domain.service;

import com.bookingapp.domain.exception.BusinessValidationException;
import com.bookingapp.domain.exception.EntityNotFoundDomainException;
import com.bookingapp.domain.enums.AccommodationType;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.repository.AccommodationRepository;
import com.bookingapp.infrastructure.kafka.KafkaEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
}
