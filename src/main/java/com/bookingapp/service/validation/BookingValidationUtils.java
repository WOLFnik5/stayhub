package com.bookingapp.service.validation;

import com.bookingapp.exception.BusinessValidationException;
import java.time.LocalDate;

public final class BookingValidationUtils {

    private BookingValidationUtils() {
    }

    public static void validateBookingDates(LocalDate checkInDate, LocalDate checkOutDate) {
        if (checkInDate == null) {
            throw new BusinessValidationException("Booking check-in date must not be null");
        }
        if (checkOutDate == null) {
            throw new BusinessValidationException("Booking check-out date must not be null");
        }
        if (!checkInDate.isBefore(checkOutDate)) {
            throw new BusinessValidationException(
                    "Booking check-in date must be before check-out date"
            );
        }
    }
}
