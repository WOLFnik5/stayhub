package com.bookingapp.infrastructure.kafka;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookingapp.domain.event.AccommodationCreatedEvent;
import com.bookingapp.domain.event.BookingCanceledEvent;
import com.bookingapp.domain.event.BookingCreatedEvent;
import com.bookingapp.domain.event.BookingExpiredEvent;
import com.bookingapp.domain.event.PaymentSucceededEvent;
import com.bookingapp.infrastructure.telegram.TelegramNotificationService;
import com.bookingapp.infrastructure.kafka.TelegramEventConsumer;
import com.bookingapp.infrastructure.kafka.TelegramEventMessageFormatter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelegramEventConsumerTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private TelegramEventMessageFormatter telegramEventMessageFormatter;

    @Mock
    private TelegramNotificationService telegramNotificationService;

    @Test
    void consumeBookingCreated_shouldDeserializePayloadFormatMessageAndSendNotification() throws Exception {
        TelegramEventConsumer consumer = new TelegramEventConsumer(
                objectMapper,
                telegramEventMessageFormatter,
                telegramNotificationService
        );

        BookingCreatedEvent event = new BookingCreatedEvent(
                1L,
                2L,
                3L,
                LocalDate.of(2026, 3, 20),
                LocalDate.of(2026, 3, 25),
                "PENDING",
                Instant.parse("2026-03-16T10:15:30Z")
        );

        String payload = objectMapper.writeValueAsString(event);
        when(telegramEventMessageFormatter.formatBookingCreatedEvent(event))
                .thenReturn("booking created message");

        consumer.consumeBookingCreated(payload);

        verify(telegramEventMessageFormatter).formatBookingCreatedEvent(event);
        verify(telegramNotificationService).sendMessage("booking created message");
    }

    @Test
    void consumeBookingCanceled_shouldDeserializePayloadFormatMessageAndSendNotification() throws Exception {
        TelegramEventConsumer consumer = new TelegramEventConsumer(
                objectMapper,
                telegramEventMessageFormatter,
                telegramNotificationService
        );

        BookingCanceledEvent event = new BookingCanceledEvent(
                10L,
                11L,
                12L,
                Instant.parse("2026-03-16T10:15:30Z")
        );

        String payload = objectMapper.writeValueAsString(event);
        when(telegramEventMessageFormatter.formatBookingCanceledEvent(event))
                .thenReturn("booking canceled message");

        consumer.consumeBookingCanceled(payload);

        verify(telegramEventMessageFormatter).formatBookingCanceledEvent(event);
        verify(telegramNotificationService).sendMessage("booking canceled message");
    }

    @Test
    void consumeAccommodationCreated_shouldDeserializePayloadFormatMessageAndSendNotification() throws Exception {
        TelegramEventConsumer consumer = new TelegramEventConsumer(
                objectMapper,
                telegramEventMessageFormatter,
                telegramNotificationService
        );

        AccommodationCreatedEvent event = new AccommodationCreatedEvent(
                100L,
                "APARTMENT",
                "Kyiv, Ukraine",
                new BigDecimal("120.00"),
                5,
                Instant.parse("2026-03-16T10:15:30Z")
        );

        String payload = objectMapper.writeValueAsString(event);
        when(telegramEventMessageFormatter.formatAccommodationCreatedEvent(event))
                .thenReturn("accommodation created message");

        consumer.consumeAccommodationCreated(payload);

        verify(telegramEventMessageFormatter).formatAccommodationCreatedEvent(event);
        verify(telegramNotificationService).sendMessage("accommodation created message");
    }

    @Test
    void consumePaymentSucceeded_shouldDeserializePayloadFormatMessageAndSendNotification() throws Exception {
        TelegramEventConsumer consumer = new TelegramEventConsumer(
                objectMapper,
                telegramEventMessageFormatter,
                telegramNotificationService
        );

        PaymentSucceededEvent event = new PaymentSucceededEvent(
                200L,
                201L,
                "cs_test_123",
                new BigDecimal("250.50"),
                Instant.parse("2026-03-16T10:15:30Z")
        );

        String payload = objectMapper.writeValueAsString(event);
        when(telegramEventMessageFormatter.formatPaymentSucceededEvent(event))
                .thenReturn("payment succeeded message");

        consumer.consumePaymentSucceeded(payload);

        verify(telegramEventMessageFormatter).formatPaymentSucceededEvent(event);
        verify(telegramNotificationService).sendMessage("payment succeeded message");
    }

    @Test
    void consumeBookingExpired_shouldDeserializePayloadFormatMessageAndSendNotification() throws Exception {
        TelegramEventConsumer consumer = new TelegramEventConsumer(
                objectMapper,
                telegramEventMessageFormatter,
                telegramNotificationService
        );

        BookingExpiredEvent event = new BookingExpiredEvent(
                300L,
                301L,
                302L,
                Instant.parse("2026-03-16T10:15:30Z")
        );

        String payload = objectMapper.writeValueAsString(event);
        when(telegramEventMessageFormatter.formatBookingExpiredEvent(event))
                .thenReturn("booking expired message");

        consumer.consumeBookingExpired(payload);

        verify(telegramEventMessageFormatter).formatBookingExpiredEvent(event);
        verify(telegramNotificationService).sendMessage("booking expired message");
    }
}
