package com.bookingapp.domain.service.accommodation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.PageResult;
import com.bookingapp.domain.model.enums.AccommodationType;
import com.bookingapp.exception.BusinessValidationException;
import com.bookingapp.exception.EntityNotFoundDomainException;
import com.bookingapp.infrastructure.kafka.KafkaEventPublisher;
import com.bookingapp.persistence.AccommodationRepositoryImpl;
import com.bookingapp.service.AccommodationService;
import com.bookingapp.web.dto.CreateAccommodationRequest;
import com.bookingapp.web.dto.UpdateAccommodationRequest;
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
    private AccommodationRepositoryImpl accommodationRepository;

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    @InjectMocks
    private AccommodationService accommodationService;

    @Test
    void createAccommodation_shouldSaveAndPublishEvent() {
        Accommodation savedAccommodation = new Accommodation(
                1L,
                AccommodationType.APARTMENT,
                "Kyiv",
                "55m2",
                List.of("WiFi"),
                new BigDecimal("100"),
                3
        );

        when(accommodationRepository.save(any()))
                .thenReturn(savedAccommodation);

        CreateAccommodationRequest request = new CreateAccommodationRequest(
                AccommodationType.APARTMENT,
                "Kyiv",
                "55m2",
                List.of("WiFi"),
                new BigDecimal("100"),
                3
        );

        Accommodation result = accommodationService.createAccommodation(request);

        assertEquals(savedAccommodation, result);

        verify(accommodationRepository).save(any());
        verify(kafkaEventPublisher)
                .publishAccommodationCreated(savedAccommodation);
    }

    @Test
    void createAccommodationShouldRejectBlankLocation() {
        CreateAccommodationRequest request = new CreateAccommodationRequest(
                AccommodationType.APARTMENT,
                "   ",
                "55m2",
                List.of("WiFi"),
                new BigDecimal("100"),
                1
        );

        assertThrows(
                BusinessValidationException.class,
                () -> accommodationService.createAccommodation(request)
        );
    }

    @Test
    void getAccommodationById_shouldReturnAccommodation() {
        Accommodation accommodation = new Accommodation(
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

        Accommodation result =
                accommodationService.getAccommodationById(1L);

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
    void listAccommodations_shouldReturnRequestedPage() {
        Accommodation first = new Accommodation(
                1L,
                AccommodationType.APARTMENT,
                "Kyiv",
                "55m2",
                List.of("WiFi"),
                new BigDecimal("100"),
                2
        );

        Accommodation second = new Accommodation(
                2L,
                AccommodationType.HOUSE,
                "Lviv",
                "100m2",
                List.of("Parking"),
                new BigDecimal("200"),
                1
        );

        PageResult<Accommodation> repositoryResult =
                new PageResult<>(
                        List.of(first, second),
                        0,
                        20,
                        2
                );

        when(accommodationRepository.findAvailablePage(0, 20))
                .thenReturn(repositoryResult);

        PageResult<Accommodation> result =
                accommodationService.listAccommodations(0, 20);

        assertEquals(2, result.content().size());
        assertEquals(first, result.content().get(0));
        assertEquals(second, result.content().get(1));

        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(2, result.totalElements());
        assertEquals(1, result.totalPages());

        verify(accommodationRepository)
                .findAvailablePage(0, 20);
    }

    @Test
    void listAccommodations_shouldReturnEmptyPage() {
        PageResult<Accommodation> repositoryResult =
                new PageResult<>(
                        List.of(),
                        0,
                        20,
                        0
                );

        when(accommodationRepository.findAvailablePage(0, 20))
                .thenReturn(repositoryResult);

        PageResult<Accommodation> result =
                accommodationService.listAccommodations(0, 20);

        assertEquals(0, result.content().size());
        assertEquals(0, result.totalElements());
        assertEquals(0, result.totalPages());

        verify(accommodationRepository)
                .findAvailablePage(0, 20);
    }

    @Test
    void listAccommodations_shouldRejectNegativePage() {
        assertThrows(
                BusinessValidationException.class,
                () -> accommodationService.listAccommodations(-1, 20)
        );
    }

    @Test
    void listAccommodations_shouldRejectZeroSize() {
        assertThrows(
                BusinessValidationException.class,
                () -> accommodationService.listAccommodations(0, 0)
        );
    }

    @Test
    void listAccommodations_shouldRejectTooLargeSize() {
        assertThrows(
                BusinessValidationException.class,
                () -> accommodationService.listAccommodations(0, 101)
        );
    }

    @Test
    void updateAccommodation_shouldSaveUpdatedAccommodation() {
        Accommodation existing = new Accommodation(
                1L,
                AccommodationType.APARTMENT,
                "Kyiv",
                "55m2",
                List.of("WiFi"),
                new BigDecimal("100"),
                1
        );

        Accommodation updated = new Accommodation(
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

        UpdateAccommodationRequest request =
                new UpdateAccommodationRequest(
                        AccommodationType.HOUSE,
                        "Krakow",
                        "80m2",
                        List.of("WiFi"),
                        new BigDecimal("150"),
                        2
                );

        Accommodation result =
                accommodationService.updateAccommodation(1L, request);

        assertEquals(updated, result);

        verify(accommodationRepository).save(any());
    }

    @Test
    void deleteAccommodation_shouldDelete_whenExists() {
        when(accommodationRepository.existsById(1L))
                .thenReturn(true);

        accommodationService.deleteAccommodation(1L);

        verify(accommodationRepository).deleteById(1L);
    }

    @Test
    void deleteAccommodation_shouldThrow_whenNotExists() {
        when(accommodationRepository.existsById(1L))
                .thenReturn(false);

        assertThrows(
                EntityNotFoundDomainException.class,
                () -> accommodationService.deleteAccommodation(1L)
        );
    }
}