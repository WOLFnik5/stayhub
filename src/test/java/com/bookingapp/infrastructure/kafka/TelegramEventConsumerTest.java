package com.bookingapp.infrastructure.kafka;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookingapp.domain.event.BookingCreatedEvent;
import com.bookingapp.infrastructure.telegram.TelegramMessageFormatter;
import com.bookingapp.infrastructure.telegram.TelegramNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelegramEventConsumerTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    @Mock
    private TelegramMessageFormatter telegramMessageFormatter;

    @Mock
    private TelegramNotificationService telegramNotificationService;

    @Mock
    private KafkaEventDeduplicationService deduplicationService;

    @Test
    void consumeBookingCreated_shouldSendNotificationForNewEvent()
            throws Exception {

        UUID eventId = UUID.randomUUID();

        BookingCreatedEvent event =
                new BookingCreatedEvent(
                        1L,
                        2L,
                        3L,
                        LocalDate.now().plusDays(5),
                        LocalDate.now().plusDays(8),
                        "PENDING",
                        Instant.now()
                );

        String payload =
                objectMapper.writeValueAsString(event);

        when(deduplicationService.isProcessed(
                eventId,
                "telegram-notification"
        )).thenReturn(false);

        when(telegramMessageFormatter
                .formatBookingCreatedEvent(event))
                .thenReturn("booking created");

        TelegramEventConsumer consumer =
                new TelegramEventConsumer(
                        objectMapper,
                        telegramMessageFormatter,
                        telegramNotificationService,
                        deduplicationService, new com.bookingapp.infrastructure.observability.FlowTelemetry(
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry())
                );

        consumer.consumeBookingCreated(
                payload,
                eventId.toString()
        );

        verify(telegramNotificationService)
                .sendMessage("booking created");
        verify(deduplicationService).markProcessed(
                eventId,
                "telegram-notification"
        );
    }

    @Test
    void consumeBookingCreated_shouldIgnoreDuplicateEvent()
            throws Exception {

        UUID eventId = UUID.randomUUID();

        BookingCreatedEvent event =
                new BookingCreatedEvent(
                        1L,
                        2L,
                        3L,
                        LocalDate.now().plusDays(5),
                        LocalDate.now().plusDays(8),
                        "PENDING",
                        Instant.now()
                );

        String payload =
                objectMapper.writeValueAsString(event);

        when(deduplicationService.isProcessed(
                eventId,
                "telegram-notification"
        )).thenReturn(true);

        TelegramEventConsumer consumer =
                new TelegramEventConsumer(
                        objectMapper,
                        telegramMessageFormatter,
                        telegramNotificationService,
                        deduplicationService, new com.bookingapp.infrastructure.observability.FlowTelemetry(
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry())
                );

        consumer.consumeBookingCreated(
                payload,
                eventId.toString()
        );

        verify(
                telegramNotificationService,
                never()
        ).sendMessage(org.mockito.ArgumentMatchers.anyString());

        verify(
                telegramMessageFormatter,
                never()
        ).formatBookingCreatedEvent(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void failedTelegramRequest_shouldRemainRetryable() throws Exception {
        UUID eventId = UUID.randomUUID();
        BookingCreatedEvent event = new BookingCreatedEvent(
                1L, 2L, 3L, LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(8), "PENDING", Instant.now()
        );
        String payload = objectMapper.writeValueAsString(event);
        when(deduplicationService.isProcessed(eventId, "telegram-notification"))
                .thenReturn(false);
        when(telegramMessageFormatter.formatBookingCreatedEvent(event))
                .thenReturn("booking created");
        doThrow(new IllegalStateException("Telegram unavailable"))
                .when(telegramNotificationService).sendMessage("booking created");
        TelegramEventConsumer consumer = new TelegramEventConsumer(
                objectMapper, telegramMessageFormatter,
                telegramNotificationService, deduplicationService,
                new com.bookingapp.infrastructure.observability.FlowTelemetry(
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry())
        );

        assertThatThrownBy(() -> consumer.consumeBookingCreated(payload, eventId.toString()))
                .isInstanceOf(IllegalStateException.class);
        verify(deduplicationService, never()).markProcessed(eventId, "telegram-notification");
    }
}
