package com.bookingapp.adapter.out.kafka;

import com.bookingapp.adapter.out.kafka.event.AccommodationCreatedEvent;
import com.bookingapp.adapter.out.kafka.event.BookingCanceledEvent;
import com.bookingapp.adapter.out.kafka.event.BookingCreatedEvent;
import com.bookingapp.adapter.out.kafka.event.BookingExpiredEvent;
import com.bookingapp.adapter.out.kafka.event.PaymentSucceededEvent;
import com.bookingapp.application.port.out.integration.EventPublisherPort;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.infrastructure.config.KafkaTopicsProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class KafkaEventPublisherAdapter implements EventPublisherPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicsProperties kafkaTopicsProperties;

    public KafkaEventPublisherAdapter(
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaTopicsProperties kafkaTopicsProperties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTopicsProperties = kafkaTopicsProperties;
    }

    @Override
    public void publishAccommodationCreated(Accommodation accommodation) {
        kafkaTemplate.send(
                kafkaTopicsProperties.getAccommodationCreated(),
                buildKey(accommodation.getId()),
                new AccommodationCreatedEvent(
                        accommodation.getId(),
                        accommodation.getType().name(),
                        accommodation.getLocation(),
                        accommodation.getDailyRate(),
                        accommodation.getAvailability(),
                        Instant.now()
                )
        );
    }

    @Override
    public void publishBookingCreated(Booking booking) {
        kafkaTemplate.send(
                kafkaTopicsProperties.getBookingCreated(),
                buildKey(booking.getId()),
                new BookingCreatedEvent(
                        booking.getId(),
                        booking.getAccommodationId(),
                        booking.getUserId(),
                        booking.getCheckInDate(),
                        booking.getCheckOutDate(),
                        booking.getStatus().name(),
                        Instant.now()
                )
        );
    }

    @Override
    public void publishBookingCanceled(Booking booking) {
        kafkaTemplate.send(
                kafkaTopicsProperties.getBookingCanceled(),
                buildKey(booking.getId()),
                new BookingCanceledEvent(
                        booking.getId(),
                        booking.getAccommodationId(),
                        booking.getUserId(),
                        Instant.now()
                )
        );
    }

    @Override
    public void publishBookingExpired(Booking booking) {
        kafkaTemplate.send(
                kafkaTopicsProperties.getBookingExpired(),
                buildKey(booking.getId()),
                new BookingExpiredEvent(
                        booking.getId(),
                        booking.getAccommodationId(),
                        booking.getUserId(),
                        Instant.now()
                )
        );
    }

    @Override
    public void publishPaymentSucceeded(Payment payment) {
        kafkaTemplate.send(
                kafkaTopicsProperties.getPaymentSucceeded(),
                buildKey(payment.getId()),
                new PaymentSucceededEvent(
                        payment.getId(),
                        payment.getBookingId(),
                        payment.getSessionId(),
                        payment.getAmountToPay(),
                        Instant.now()
                )
        );
    }

    private String buildKey(Long id) {
        return id == null ? "unknown" : id.toString();
    }
}
