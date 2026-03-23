package com.bookingapp.adapter.in.kafka;

import com.bookingapp.domain.event.AccommodationCreatedEvent;
import com.bookingapp.domain.event.BookingCanceledEvent;
import com.bookingapp.domain.event.BookingCreatedEvent;
import com.bookingapp.domain.event.BookingExpiredEvent;
import com.bookingapp.domain.event.PaymentSucceededEvent;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
public class TelegramKafkaMessageFormatter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    public String formatBookingCreatedEvent(BookingCreatedEvent event) {
        return """
                Booking created
                Booking ID: %d
                User ID: %d
                Accommodation ID: %d
                Check-in: %s
                Check-out: %s
                Status: %s
                Created at: %s
                """.formatted(
                event.bookingId(),
                event.userId(),
                event.accommodationId(),
                event.checkInDate().format(DATE_FORMATTER),
                event.checkOutDate().format(DATE_FORMATTER),
                event.status(),
                event.createdAt()
        );
    }

    public String formatBookingCanceledEvent(BookingCanceledEvent event) {
        return """
                Booking canceled
                Booking ID: %d
                User ID: %d
                Accommodation ID: %d
                Canceled at: %s
                """.formatted(
                event.bookingId(),
                event.userId(),
                event.accommodationId(),
                event.canceledAt()
        );
    }

    public String formatAccommodationCreatedEvent(AccommodationCreatedEvent event) {
        return """
                Accommodation created
                Accommodation ID: %d
                Type: %s
                Location: %s
                Daily rate: %s
                Availability: %d
                Created at: %s
                """.formatted(
                event.accommodationId(),
                event.type(),
                event.location(),
                event.dailyRate(),
                event.availability(),
                event.createdAt()
        );
    }

    public String formatPaymentSucceededEvent(PaymentSucceededEvent event) {
        return """
                Payment succeeded
                Payment ID: %d
                Booking ID: %d
                Session ID: %s
                Amount: %s
                Paid at: %s
                """.formatted(
                event.paymentId(),
                event.bookingId(),
                event.sessionId(),
                event.amountToPay(),
                event.paidAt()
        );
    }

    public String formatBookingExpiredEvent(BookingExpiredEvent event) {
        return """
                Booking expired and accommodation released
                Booking ID: %d
                Accommodation ID: %d
                User ID: %d
                Expired at: %s
                """.formatted(
                event.bookingId(),
                event.accommodationId(),
                event.userId(),
                event.expiredAt()
        );
    }
}
