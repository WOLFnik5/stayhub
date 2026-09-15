package com.bookingapp.infrastructure.kafka;

import com.bookingapp.domain.event.AccommodationCreatedEvent;
import com.bookingapp.domain.event.BookingCanceledEvent;
import com.bookingapp.domain.event.BookingCreatedEvent;
import com.bookingapp.domain.event.BookingExpiredEvent;
import com.bookingapp.domain.event.PaymentSucceededEvent;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.infrastructure.config.KafkaTopicsProperties;
import com.bookingapp.infrastructure.observability.CorrelationContext;
import com.bookingapp.infrastructure.observability.FlowTracing;
import com.bookingapp.persistence.outbox.OutboxEventEntity;
import com.bookingapp.persistence.outbox.OutboxEventJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.SpanKind;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class OutboxKafkaEventPublisher implements KafkaEventPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxKafkaEventPublisher.class);
    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final KafkaTopicsProperties kafkaTopicsProperties;
    private final ObjectMapper objectMapper;
    private final FlowTracing tracing;

    public OutboxKafkaEventPublisher(
            OutboxEventJpaRepository outboxEventJpaRepository,
            KafkaTopicsProperties kafkaTopicsProperties,
            ObjectMapper objectMapper,
            FlowTracing tracing
    ) {
        this.outboxEventJpaRepository = outboxEventJpaRepository;
        this.kafkaTopicsProperties = kafkaTopicsProperties;
        this.objectMapper = objectMapper;
        this.tracing = tracing;
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

            OutboxEventEntity storedEvent =
                    OutboxEventEntity.newEvent(
                            aggregateType,
                            aggregateId,
                            eventType,
                            topic,
                            eventKey,
                            payload
                    );
            storedEvent.setCorrelationId(CorrelationContext.currentOrNew());
            try (CorrelationContext ignored = CorrelationContext.open(
                    storedEvent.getCorrelationId(), storedEvent.getId().toString());
                    var trace = tracing.start("outbox.enqueue", SpanKind.INTERNAL)) {
                var headers = tracing.headers();
                storedEvent.setTraceParent(headers.get("traceparent"));
                storedEvent.setTraceState(headers.get("tracestate"));
                trace.tag("event.type", eventType);
                try {
                    outboxEventJpaRepository.save(storedEvent);
                } catch (RuntimeException exception) {
                    trace.error(exception);
                    throw exception;
                }
            }
            logAfterCommit(storedEvent);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload for "
                    + eventType, e);
        }
    }

    private String buildKey(Long id) {
        return id == null ? "unknown" : id.toString();
    }

    private void logAfterCommit(OutboxEventEntity event) {
        Runnable log = () -> {
            try (CorrelationContext ignored = CorrelationContext.open(
                    event.getCorrelationId(), event.getId().toString())) {
                LOGGER.atInfo().addKeyValue("stage", "outbox")
                        .addKeyValue("outcome", "committed")
                        .addKeyValue("eventType", event.getEventType())
                        .log("Outbox event committed");
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronization synchronization = new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.run();
                }
            };
            TransactionSynchronizationManager.registerSynchronization(synchronization);
        } else {
            log.run();
        }
    }
}
