package com.bookingapp.infrastructure.kafka;

import com.bookingapp.domain.event.AccommodationCreatedEvent;
import com.bookingapp.domain.event.BookingCanceledEvent;
import com.bookingapp.domain.event.BookingCreatedEvent;
import com.bookingapp.domain.event.BookingExpiredEvent;
import com.bookingapp.domain.event.PaymentSucceededEvent;
import com.bookingapp.infrastructure.observability.FlowTelemetry;
import com.bookingapp.infrastructure.outbox.OutboxKafkaPublisher;
import com.bookingapp.infrastructure.telegram.TelegramMessageFormatter;
import com.bookingapp.infrastructure.telegram.TelegramNotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Span;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Profile("!test")
@Component
public class TelegramEventConsumer {

    private static final String CONSUMER_NAME =
            "telegram-notification";

    private final ObjectMapper objectMapper;
    private final TelegramMessageFormatter telegramMessageFormatter;
    private final TelegramNotificationService telegramNotificationService;
    private final KafkaEventDeduplicationService deduplicationService;
    private final FlowTelemetry telemetry;

    public TelegramEventConsumer(
            ObjectMapper objectMapper,
            TelegramMessageFormatter telegramMessageFormatter,
            TelegramNotificationService telegramNotificationService,
            KafkaEventDeduplicationService deduplicationService,
            FlowTelemetry telemetry
    ) {
        this.objectMapper = objectMapper;
        this.telegramMessageFormatter = telegramMessageFormatter;
        this.telegramNotificationService = telegramNotificationService;
        this.deduplicationService = deduplicationService;
        this.telemetry = telemetry;
    }

    @KafkaListener(
            topics = "#{@kafkaTopicsProperties.bookingCreated}",
            groupId = "${spring.kafka.consumer.group-id:booking-app}-telegram",
            containerFactory = "telegramKafkaListenerContainerFactory"
    )
    public void consumeBookingCreated(
            String payload,
            @Header(OutboxKafkaPublisher.EVENT_ID_HEADER)
            String eventIdHeader
    ) {
        UUID eventId = parseEventId(eventIdHeader);

        if (deduplicationService.isProcessed(
                eventId,
                CONSUMER_NAME
        )) {
            Span.current().setAttribute("consumer.duplicate", true);
            telemetry.count("consumer", "duplicate", 1);
            return;
        }

        BookingCreatedEvent event =
                readValue(payload, BookingCreatedEvent.class);

        telegramNotificationService.sendMessage(
                telegramMessageFormatter
                        .formatBookingCreatedEvent(event)
        );
        deduplicationService.markProcessed(eventId, CONSUMER_NAME);
    }

    @KafkaListener(
            topics = "#{@kafkaTopicsProperties.bookingCanceled}",
            groupId = "${spring.kafka.consumer.group-id:booking-app}-telegram",
            containerFactory = "telegramKafkaListenerContainerFactory"
    )
    public void consumeBookingCanceled(
            String payload,
            @Header(OutboxKafkaPublisher.EVENT_ID_HEADER)
            String eventIdHeader
    ) {
        UUID eventId = parseEventId(eventIdHeader);

        if (deduplicationService.isProcessed(
                eventId,
                CONSUMER_NAME
        )) {
            Span.current().setAttribute("consumer.duplicate", true);
            telemetry.count("consumer", "duplicate", 1);
            return;
        }

        BookingCanceledEvent event =
                readValue(payload, BookingCanceledEvent.class);

        telegramNotificationService.sendMessage(
                telegramMessageFormatter
                        .formatBookingCanceledEvent(event)
        );
        deduplicationService.markProcessed(eventId, CONSUMER_NAME);
    }

    @KafkaListener(
            topics = "#{@kafkaTopicsProperties.accommodationCreated}",
            groupId = "${spring.kafka.consumer.group-id:booking-app}-telegram",
            containerFactory = "telegramKafkaListenerContainerFactory"
    )
    public void consumeAccommodationCreated(
            String payload,
            @Header(OutboxKafkaPublisher.EVENT_ID_HEADER)
            String eventIdHeader
    ) {
        UUID eventId = parseEventId(eventIdHeader);

        if (deduplicationService.isProcessed(
                eventId,
                CONSUMER_NAME
        )) {
            Span.current().setAttribute("consumer.duplicate", true);
            telemetry.count("consumer", "duplicate", 1);
            return;
        }

        AccommodationCreatedEvent event =
                readValue(
                        payload,
                        AccommodationCreatedEvent.class
                );

        telegramNotificationService.sendMessage(
                telegramMessageFormatter
                        .formatAccommodationCreatedEvent(event)
        );
        deduplicationService.markProcessed(eventId, CONSUMER_NAME);
    }

    @KafkaListener(
            topics = "#{@kafkaTopicsProperties.paymentSucceeded}",
            groupId = "${spring.kafka.consumer.group-id:booking-app}-telegram",
            containerFactory = "telegramKafkaListenerContainerFactory"
    )
    public void consumePaymentSucceeded(
            String payload,
            @Header(OutboxKafkaPublisher.EVENT_ID_HEADER)
            String eventIdHeader
    ) {
        UUID eventId = parseEventId(eventIdHeader);

        if (deduplicationService.isProcessed(
                eventId,
                CONSUMER_NAME
        )) {
            Span.current().setAttribute("consumer.duplicate", true);
            telemetry.count("consumer", "duplicate", 1);
            return;
        }

        PaymentSucceededEvent event =
                readValue(payload, PaymentSucceededEvent.class);

        telegramNotificationService.sendMessage(
                telegramMessageFormatter
                        .formatPaymentSucceededEvent(event)
        );
        deduplicationService.markProcessed(eventId, CONSUMER_NAME);
    }

    @KafkaListener(
            topics = "#{@kafkaTopicsProperties.bookingExpired}",
            groupId = "${spring.kafka.consumer.group-id:booking-app}-telegram",
            containerFactory = "telegramKafkaListenerContainerFactory"
    )
    public void consumeBookingExpired(
            String payload,
            @Header(OutboxKafkaPublisher.EVENT_ID_HEADER)
            String eventIdHeader
    ) {
        UUID eventId = parseEventId(eventIdHeader);

        if (deduplicationService.isProcessed(
                eventId,
                CONSUMER_NAME
        )) {
            Span.current().setAttribute("consumer.duplicate", true);
            telemetry.count("consumer", "duplicate", 1);
            return;
        }

        BookingExpiredEvent event =
                readValue(payload, BookingExpiredEvent.class);

        telegramNotificationService.sendMessage(
                telegramMessageFormatter
                        .formatBookingExpiredEvent(event)
        );
        deduplicationService.markProcessed(eventId, CONSUMER_NAME);
    }

    private UUID parseEventId(String eventIdHeader) {
        try {
            return UUID.fromString(eventIdHeader);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Invalid Kafka eventId header: "
                            + eventIdHeader,
                    exception
            );
        }
    }

    private <T> T readValue(
            String payload,
            Class<T> targetType
    ) {
        try {
            return objectMapper.readValue(
                    payload,
                    targetType
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Failed to deserialize Kafka payload to "
                            + targetType.getSimpleName(),
                    exception
            );
        }
    }
}
