package com.bookingapp.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bookingapp.domain.enums.AccommodationType;
import com.bookingapp.domain.exception.BusinessValidationException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccommodationTest {

    @Test
    void createNew_shouldCreateAccommodationWithNullId() {
        Accommodation accommodation = Accommodation.createNew(
                AccommodationType.APARTMENT,
                "Kyiv",
                "55m2",
                List.of("WiFi", "Kitchen"),
                new BigDecimal("120.00"),
                5
        );

        assertEquals(null, accommodation.getId());
        assertEquals(AccommodationType.APARTMENT, accommodation.getType());
        assertEquals("Kyiv", accommodation.getLocation());
        assertEquals("55m2", accommodation.getSize());
        assertEquals(List.of("WiFi", "Kitchen"), accommodation.getAmenities());
        assertEquals(new BigDecimal("120.00"), accommodation.getDailyRate());
        assertEquals(5, accommodation.getAvailability());
    }

    @Test
    void constructor_shouldTrimLocationAndSizeAndAmenities() {
        Accommodation accommodation = new Accommodation(
                1L,
                AccommodationType.HOUSE,
                "  Lviv  ",
                "  80m2  ",
                List.of("  WiFi  ", " Parking "),
                new BigDecimal("200.00"),
                2
        );

        assertEquals("Lviv", accommodation.getLocation());
        assertEquals("80m2", accommodation.getSize());
        assertEquals(List.of("WiFi", "Parking"), accommodation.getAmenities());
    }

    @Test
    void constructor_shouldReturnEmptyAmenities_whenAmenitiesNull() {
        Accommodation accommodation = new Accommodation(
                1L,
                AccommodationType.HOUSE,
                "Lviv",
                "80m2",
                null,
                new BigDecimal("200.00"),
                2
        );

        assertEquals(List.of(), accommodation.getAmenities());
    }

    @Test
    void constructor_shouldThrowException_whenTypeIsNull() {
        assertThrows(
                NullPointerException.class,
                () -> new Accommodation(
                        1L,
                        null,
                        "Lviv",
                        "80m2",
                        List.of("WiFi"),
                        new BigDecimal("200.00"),
                        2
                )
        );
    }

    @Test
    void constructor_shouldThrowException_whenLocationIsBlank() {
        assertThrows(
                BusinessValidationException.class,
                () -> new Accommodation(
                        1L,
                        AccommodationType.APARTMENT,
                        "   ",
                        "80m2",
                        List.of("WiFi"),
                        new BigDecimal("200.00"),
                        2
                )
        );
    }

    @Test
    void constructor_shouldThrowException_whenSizeIsBlank() {
        assertThrows(
                BusinessValidationException.class,
                () -> new Accommodation(
                        1L,
                        AccommodationType.APARTMENT,
                        "Kyiv",
                        "   ",
                        List.of("WiFi"),
                        new BigDecimal("200.00"),
                        2
                )
        );
    }

    @Test
    void constructor_shouldThrowException_whenAmenityIsBlank() {
        assertThrows(
                BusinessValidationException.class,
                () -> new Accommodation(
                        1L,
                        AccommodationType.APARTMENT,
                        "Kyiv",
                        "55m2",
                        List.of("WiFi", " "),
                        new BigDecimal("200.00"),
                        2
                )
        );
    }

    @Test
    void constructor_shouldThrowException_whenDailyRateIsNegative() {
        assertThrows(
                BusinessValidationException.class,
                () -> new Accommodation(
                        1L,
                        AccommodationType.APARTMENT,
                        "Kyiv",
                        "55m2",
                        List.of("WiFi"),
                        new BigDecimal("-1.00"),
                        2
                )
        );
    }

    @Test
    void constructor_shouldThrowException_whenAvailabilityIsNegative() {
        assertThrows(
                BusinessValidationException.class,
                () -> new Accommodation(
                        1L,
                        AccommodationType.APARTMENT,
                        "Kyiv",
                        "55m2",
                        List.of("WiFi"),
                        new BigDecimal("100.00"),
                        -1
                )
        );
    }

    @Test
    void updateDetails_shouldReturnNewUpdatedAccommodation() {
        Accommodation existing = new Accommodation(
                1L,
                AccommodationType.APARTMENT,
                "Kyiv",
                "55m2",
                List.of("WiFi"),
                new BigDecimal("100.00"),
                2
        );

        Accommodation updated = existing.updateDetails(
                AccommodationType.HOUSE,
                "Krakow",
                "100m2",
                List.of("Parking", "Kitchen"),
                new BigDecimal("250.00"),
                4
        );

        assertEquals(1L, updated.getId());
        assertEquals(AccommodationType.HOUSE, updated.getType());
        assertEquals("Krakow", updated.getLocation());
        assertEquals("100m2", updated.getSize());
        assertEquals(List.of("Parking", "Kitchen"), updated.getAmenities());
        assertEquals(new BigDecimal("250.00"), updated.getDailyRate());
        assertEquals(4, updated.getAvailability());
    }

    @Test
    void decreaseAvailability_shouldDecreaseAvailability() {
        Accommodation accommodation = new Accommodation(
                1L,
                AccommodationType.APARTMENT,
                "Kyiv",
                "55m2",
                List.of("WiFi"),
                new BigDecimal("100.00"),
                5
        );

        Accommodation updated = accommodation.decreaseAvailability(2);

        assertEquals(3, updated.getAvailability());
    }

    @Test
    void decreaseAvailability_shouldThrowException_whenUnitsLessThanOrEqualToZero() {
        Accommodation accommodation = new Accommodation(
                1L,
                AccommodationType.APARTMENT,
                "Kyiv",
                "55m2",
                List.of("WiFi"),
                new BigDecimal("100.00"),
                5
        );

        assertThrows(
                BusinessValidationException.class,
                () -> accommodation.decreaseAvailability(0)
        );
    }

    @Test
    void decreaseAvailability_shouldThrowException_whenResultWouldBeNegative() {
        Accommodation accommodation = new Accommodation(
                1L,
                AccommodationType.APARTMENT,
                "Kyiv",
                "55m2",
                List.of("WiFi"),
                new BigDecimal("100.00"),
                1
        );

        assertThrows(
                BusinessValidationException.class,
                () -> accommodation.decreaseAvailability(2)
        );
    }

    @Test
    void increaseAvailability_shouldIncreaseAvailability() {
        Accommodation accommodation = new Accommodation(
                1L,
                AccommodationType.APARTMENT,
                "Kyiv",
                "55m2",
                List.of("WiFi"),
                new BigDecimal("100.00"),
                5
        );

        Accommodation updated = accommodation.increaseAvailability(3);

        assertEquals(8, updated.getAvailability());
    }

    @Test
    void increaseAvailability_shouldThrowException_whenUnitsLessThanOrEqualToZero() {
        Accommodation accommodation = new Accommodation(
                1L,
                AccommodationType.APARTMENT,
                "Kyiv",
                "55m2",
                List.of("WiFi"),
                new BigDecimal("100.00"),
                5
        );

        assertThrows(
                BusinessValidationException.class,
                () -> accommodation.increaseAvailability(-1)
        );
    }
}