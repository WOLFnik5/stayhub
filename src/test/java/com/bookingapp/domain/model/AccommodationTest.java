package com.bookingapp.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bookingapp.domain.model.enums.AccommodationType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccommodationTest {

    @Test
    void constructorShouldAssignAllFields() {
        Accommodation accommodation = new Accommodation(
                1L,
                AccommodationType.APARTMENT,
                "Kyiv",
                "55m2",
                List.of("WiFi", "Kitchen"),
                new BigDecimal("120.00"),
                5
        );

        assertEquals(1L, accommodation.getId());
        assertEquals(AccommodationType.APARTMENT, accommodation.getType());
        assertEquals("Kyiv", accommodation.getLocation());
        assertEquals("55m2", accommodation.getSize());
        assertEquals(List.of("WiFi", "Kitchen"), accommodation.getAmenities());
        assertEquals(new BigDecimal("120.00"), accommodation.getDailyRate());
        assertEquals(5, accommodation.getAvailability());
    }

    @Test
    void settersShouldUpdateFields() {
        Accommodation accommodation = new Accommodation();

        accommodation.setId(2L);
        accommodation.setType(AccommodationType.HOUSE);
        accommodation.setLocation("Lviv");
        accommodation.setSize("80m2");
        accommodation.setAmenities(List.of("Parking"));
        accommodation.setDailyRate(new BigDecimal("200.00"));
        accommodation.setAvailability(3);

        assertEquals(2L, accommodation.getId());
        assertEquals(AccommodationType.HOUSE, accommodation.getType());
        assertEquals("Lviv", accommodation.getLocation());
        assertEquals("80m2", accommodation.getSize());
        assertEquals(List.of("Parking"), accommodation.getAmenities());
        assertEquals(new BigDecimal("200.00"), accommodation.getDailyRate());
        assertEquals(3, accommodation.getAvailability());
    }
}
