package com.bookingapp.domain.service.accommodation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bookingapp.service.AccommodationService;
import com.bookingapp.infrastructure.kafka.KafkaEventPublisher;
import com.bookingapp.domain.repository.AccommodationRepository;
import com.bookingapp.domain.model.enums.AccommodationType;
import com.bookingapp.exception.BusinessValidationException;
import com.bookingapp.exception.EntityNotFoundDomainException;
import com.bookingapp.domain.model.Accommodation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccommodationServiceTest {

    @Mock
    private AccommodationRepository accommodationRepository;

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    @InjectMocks
    private AccommodationService accommodationService;

    @Test
    void createAccommodation_shouldSaveAndPublishEvent() {

        Accommodation savedAccommodation =
                new Accommodation(
                        1L,
                        AccommodationType.APARTMENT,
                        "Kyiv",
                        "55m2",
                        List.of("WiFi"),
                        new BigDecimal("100"),
                        3
                );

        when(accommodationRepository.save(any())).thenReturn(savedAccommodation);

        Accommodation result = accommodationService.createAccommodation(
                AccommodationType.APARTMENT,
                "Kyiv",
                "55m2",
                List.of("WiFi"),
                new BigDecimal("100"),
                3
        );

        assertEquals(savedAccommodation, result);

        verify(accommodationRepository).save(any());
        verify(kafkaEventPublisher).publishAccommodationCreated(savedAccommodation);
    }

    @Test
    void createAccommodationShouldRejectBlankLocation() {

        assertThrows(
                BusinessValidationException.class,
                () -> accommodationService.createAccommodation(
                        AccommodationType.APARTMENT,
                        "   ",
                        "55m2",
                        List.of("WiFi"),
                        new BigDecimal("100"),
                        1
                )
        );
    }

    @Test
    void getAccommodationById_shouldReturnAccommodation() {

        Accommodation accommodation =
                new Accommodation(
                        1L,
                        AccommodationType.HOUSE,
                        "Lviv",
                        "80m2",
                        List.of("Parking"),
                        new BigDecimal("200"),
                        2
                );

        when(accommodationRepository.findById(1L))
                .thenReturn(Optional.of(accommodation));

        Accommodation result = accommodationService.getAccommodationById(1L);

        assertEquals(accommodation, result);
    }

    @Test
    void getAccommodationById_shouldThrow_whenNotFound() {

        when(accommodationRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundDomainException.class,
                () -> accommodationService.getAccommodationById(1L)
        );
    }

    @Test
    void listAccommodations_shouldReturnOnlyAvailable() {

        Accommodation available =
                new Accommodation(
                        1L,
                        AccommodationType.APARTMENT,
                        "Kyiv",
                        "55m2",
                        List.of("WiFi"),
                        new BigDecimal("100"),
                        2
                );

        Accommodation unavailable =
                new Accommodation(
                        2L,
                        AccommodationType.HOUSE,
                        "Lviv",
                        "100m2",
                        List.of("Parking"),
                        new BigDecimal("200"),
                        0
                );

        when(accommodationRepository.findAll())
                .thenReturn(List.of(available, unavailable));

        List<Accommodation> result = accommodationService.listAccommodations();

        assertEquals(1, result.size());
        assertEquals(available, result.get(0));
    }

    @Test
    void updateAccommodation_shouldSaveUpdatedAccommodation() {

        Accommodation existing =
                new Accommodation(
                        1L,
                        AccommodationType.APARTMENT,
                        "Kyiv",
                        "55m2",
                        List.of("WiFi"),
                        new BigDecimal("100"),
                        1
                );

        Accommodation updated =
                new Accommodation(
                        1L,
                        AccommodationType.HOUSE,
                        "Krakow",
                        "80m2",
                        List.of("WiFi"),
                        new BigDecimal("150"),
                        2
                );

        when(accommodationRepository.findById(1L))
                .thenReturn(Optional.of(existing));

        when(accommodationRepository.save(any()))
                .thenReturn(updated);

        Accommodation result =
                accommodationService.updateAccommodation(
                        1L,
                        AccommodationType.HOUSE,
                        "Krakow",
                        "80m2",
                        List.of("WiFi"),
                        new BigDecimal("150"),
                        2
                );

        assertEquals(updated, result);

        verify(accommodationRepository).save(any());
    }

    @Test
    void deleteAccommodation_shouldDelete_whenExists() {

        when(accommodationRepository.existsById(1L)).thenReturn(true);

        accommodationService.deleteAccommodation(1L);

        verify(accommodationRepository).deleteById(1L);
    }

    @Test
    void deleteAccommodation_shouldThrow_whenNotExists() {

        when(accommodationRepository.existsById(1L)).thenReturn(false);

        assertThrows(
                EntityNotFoundDomainException.class,
                () -> accommodationService.deleteAccommodation(1L)
        );
    }
}
