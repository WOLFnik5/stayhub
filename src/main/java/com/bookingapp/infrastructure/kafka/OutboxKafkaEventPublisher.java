package com.bookingapp.infrastructure.kafka;

import com.bookingapp.infrastructure.persistence.outbox.OutboxEventEntity;
import com.bookingapp.infrastructure.persistence.outbox.OutboxEventJpaRepository;
import com.bookingapp.domain.event.AccommodationCreatedEvent;
import com.bookingapp.domain.event.BookingCanceledEvent;
import com.bookingapp.domain.event.BookingCreatedEvent;
import com.bookingapp.domain.event.BookingExpiredEvent;
import com.bookingapp.domain.event.PaymentSucceededEvent;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.infrastructure.config.KafkaTopicsProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class OutboxKafkaEventPublisher implements KafkaEventPublisher {
    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final KafkaTopicsProperties kafkaTopicsProperties;
    private final ObjectMapper objectMapper;

    public OutboxKafkaEventPublisher(
            OutboxEventJpaRepository outboxEventJpaRepository,
            KafkaTopicsProperties kafkaTopicsProperties,
            ObjectMapper objectMapper
    ) {
        this.outboxEventJpaRepository = outboxEventJpaRepository;
        this.kafkaTopicsProperties = kafkaTopicsProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishAccommodationCreated(Accommodation accommodation) {
        saveOutboxEvent(
                "Accommodation",
                accommodation.getId(),
                "AccommodationCreatedEvent",
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
        saveOutboxEvent(
                "Booking",
                booking.getId(),
                "BookingCreatedEvent",
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
        saveOutboxEvent(
                "Booking",
                booking.getId(),
                "BookingCanceledEvent",
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
        saveOutboxEvent(
                "Booking",
                booking.getId(),
                "BookingExpiredEvent",
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
        saveOutboxEvent(
                "Payment",
                payment.getId(),
                "PaymentSucceededEvent",
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

    private void saveOutboxEvent(
            String aggregateType,
            Long aggregateId,
            String eventType,
            String topic,
            String eventKey,
            Object payloadObject
    ) {
        try {
            String payload = objectMapper.writeValueAsString(payloadObject);

            outboxEventJpaRepository.save(
                    OutboxEventEntity.newEvent(
                            aggregateType,
                            aggregateId,
                            eventType,
                            topic,
                            eventKey,
                            payload
                    )
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload for " + eventType, e);
        }
    }

    private String buildKey(Long id) {
        return id == null ? "unknown" : id.toString();
    }
}
