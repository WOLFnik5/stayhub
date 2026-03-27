package com.bookingapp.infrastructure.telegram;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
public class TelegramMessageFormatter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    public String formatBookingCreated(Booking booking) {
        return """
                Booking created
                Booking ID: %d
                User ID: %d
                Accommodation ID: %d
                Check-in: %s
                Check-out: %s
                Status: %s
                """.formatted(
                booking.getId(),
                booking.getUserId(),
                booking.getAccommodationId(),
                booking.getCheckInDate().format(DATE_FORMATTER),
                booking.getCheckOutDate().format(DATE_FORMATTER),
                booking.getStatus().name()
        );
    }

    public String formatBookingCanceled(Booking booking) {
        return """
                Booking canceled
                Booking ID: %d
                User ID: %d
                Accommodation ID: %d
                """.formatted(
                booking.getId(),
                booking.getUserId(),
                booking.getAccommodationId()
        );
    }

    public String formatAccommodationCreated(Accommodation accommodation) {
        return """
                Accommodation created
                Accommodation ID: %d
                Type: %s
                Location: %s
                Daily rate: %s
                Availability: %d
                """.formatted(
                accommodation.getId(),
                accommodation.getType().name(),
                accommodation.getLocation(),
                accommodation.getDailyRate(),
                accommodation.getAvailability()
        );
    }

    public String formatAccommodationReleased(Booking booking, Accommodation accommodation) {
        return """
                Booking expired and accommodation released
                Booking ID: %d
                Accommodation ID: %d
                User ID: %d
                Location: %s
                """.formatted(
                booking.getId(),
                accommodation.getId(),
                booking.getUserId(),
                accommodation.getLocation()
        );
    }

    public String formatPaymentSuccessful(Payment payment) {
        return """
                Payment succeeded
                Payment ID: %d
                Booking ID: %d
                Amount: %s
                """.formatted(
                payment.getId(),
                payment.getBookingId(),
                payment.getAmountToPay()
        );
    }
}
