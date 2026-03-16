package com.bookingapp.application.service.accommodation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bookingapp.application.model.CreateAccommodationCommand;
import com.bookingapp.application.model.UpdateAccommodationCommand;
import com.bookingapp.application.port.out.integration.EventPublisherPort;
import com.bookingapp.application.port.out.persistence.AccommodationRepositoryPort;
import com.bookingapp.domain.enums.AccommodationType;
import com.bookingapp.domain.exception.BusinessValidationException;
import com.bookingapp.domain.exception.EntityNotFoundDomainException;
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
class AccommodationApplicationServiceTest {

    @Mock
    private AccommodationRepositoryPort accommodationRepositoryPort;

    @Mock
    private EventPublisherPort eventPublisherPort;

    @InjectMocks
    private AccommodationApplicationService accommodationApplicationService;

    @Test
    void createAccommodation_shouldSaveAndPublishEvent() {

        CreateAccommodationCommand command = new CreateAccommodationCommand(
                AccommodationType.APARTMENT,
                "Kyiv",
                "55m2",
                List.of("WiFi"),
                new BigDecimal("100"),
                3
        );

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

        when(accommodationRepositoryPort.save(any())).thenReturn(savedAccommodation);

        Accommodation result = accommodationApplicationService.createAccommodation(command);

        assertEquals(savedAccommodation, result);

        verify(accommodationRepositoryPort).save(any());
        verify(eventPublisherPort).publishAccommodationCreated(savedAccommodation);
    }

    @Test
    void createAccommodation_shouldThrowException_whenCommandNull() {

        assertThrows(
                BusinessValidationException.class,
                () -> accommodationApplicationService.createAccommodation(null)
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

        when(accommodationRepositoryPort.findById(1L))
                .thenReturn(Optional.of(accommodation));

        Accommodation result = accommodationApplicationService.getAccommodationById(1L);

        assertEquals(accommodation, result);
    }

    @Test
    void getAccommodationById_shouldThrow_whenNotFound() {

        when(accommodationRepositoryPort.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundDomainException.class,
                () -> accommodationApplicationService.getAccommodationById(1L)
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

        when(accommodationRepositoryPort.findAll())
                .thenReturn(List.of(available, unavailable));

        List<Accommodation> result = accommodationApplicationService.listAccommodations();

        assertEquals(1, result.size());
        assertEquals(available, result.get(0));
    }

    @Test
    void updateAccommodation_shouldSaveUpdatedAccommodation() {

        UpdateAccommodationCommand command = new UpdateAccommodationCommand(
                1L,
                AccommodationType.HOUSE,
                "Krakow",
                "80m2",
                List.of("WiFi"),
                new BigDecimal("150"),
                2
        );

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

        when(accommodationRepositoryPort.findById(1L))
                .thenReturn(Optional.of(existing));

        when(accommodationRepositoryPort.save(any()))
                .thenReturn(updated);

        Accommodation result =
                accommodationApplicationService.updateAccommodation(command);

        assertEquals(updated, result);

        verify(accommodationRepositoryPort).save(any());
    }

    @Test
    void deleteAccommodation_shouldDelete_whenExists() {

        when(accommodationRepositoryPort.existsById(1L)).thenReturn(true);

        accommodationApplicationService.deleteAccommodation(1L);

        verify(accommodationRepositoryPort).deleteById(1L);
    }

    @Test
    void deleteAccommodation_shouldThrow_whenNotExists() {

        when(accommodationRepositoryPort.existsById(1L)).thenReturn(false);

        assertThrows(
                EntityNotFoundDomainException.class,
                () -> accommodationApplicationService.deleteAccommodation(1L)
        );
    }
}