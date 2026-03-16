package com.bookingapp.adapter.in.kafka;

import com.bookingapp.application.model.event.AccommodationCreatedEvent;
import com.bookingapp.application.model.event.BookingCanceledEvent;
import com.bookingapp.application.model.event.BookingCreatedEvent;
import com.bookingapp.application.model.event.BookingExpiredEvent;
import com.bookingapp.application.model.event.PaymentSucceededEvent;
import com.bookingapp.application.port.in.notification.SendNotificationUseCase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TelegramEventConsumer {
    private final ObjectMapper objectMapper;
    private final TelegramKafkaMessageFormatter telegramKafkaMessageFormatter;
    private final SendNotificationUseCase sendNotificationUseCase;

    public TelegramEventConsumer(
            ObjectMapper objectMapper,
            TelegramKafkaMessageFormatter telegramKafkaMessageFormatter,
            SendNotificationUseCase sendNotificationUseCase
    ) {
        this.objectMapper = objectMapper;
        this.telegramKafkaMessageFormatter = telegramKafkaMessageFormatter;
        this.sendNotificationUseCase = sendNotificationUseCase;
    }

    @KafkaListener(
            topics = "#{@kafkaTopicsProperties.bookingCreated}",
            groupId = "${spring.kafka.consumer.group-id:booking-app}-telegram",
            containerFactory = "telegramKafkaListenerContainerFactory"
    )
    public void consumeBookingCreated(String payload) {
        BookingCreatedEvent event = readValue(payload, BookingCreatedEvent.class);
        sendNotificationUseCase.sendMessage(telegramKafkaMessageFormatter.formatBookingCreatedEvent(event));
    }

    @KafkaListener(
            topics = "#{@kafkaTopicsProperties.bookingCanceled}",
            groupId = "${spring.kafka.consumer.group-id:booking-app}-telegram",
            containerFactory = "telegramKafkaListenerContainerFactory"
    )
    public void consumeBookingCanceled(String payload) {
        BookingCanceledEvent event = readValue(payload, BookingCanceledEvent.class);
        sendNotificationUseCase.sendMessage(telegramKafkaMessageFormatter.formatBookingCanceledEvent(event));
    }

    @KafkaListener(
            topics = "#{@kafkaTopicsProperties.accommodationCreated}",
            groupId = "${spring.kafka.consumer.group-id:booking-app}-telegram",
            containerFactory = "telegramKafkaListenerContainerFactory"
    )
    public void consumeAccommodationCreated(String payload) {
        AccommodationCreatedEvent event = readValue(payload, AccommodationCreatedEvent.class);
        sendNotificationUseCase.sendMessage(
                telegramKafkaMessageFormatter.formatAccommodationCreatedEvent(event)
        );
    }

    @KafkaListener(
            topics = "#{@kafkaTopicsProperties.paymentSucceeded}",
            groupId = "${spring.kafka.consumer.group-id:booking-app}-telegram",
            containerFactory = "telegramKafkaListenerContainerFactory"
    )
    public void consumePaymentSucceeded(String payload) {
        PaymentSucceededEvent event = readValue(payload, PaymentSucceededEvent.class);
        sendNotificationUseCase.sendMessage(
                telegramKafkaMessageFormatter.formatPaymentSucceededEvent(event)
        );
    }

    @KafkaListener(
            topics = "#{@kafkaTopicsProperties.bookingExpired}",
            groupId = "${spring.kafka.consumer.group-id:booking-app}-telegram",
            containerFactory = "telegramKafkaListenerContainerFactory"
    )
    public void consumeBookingExpired(String payload) {
        BookingExpiredEvent event = readValue(payload, BookingExpiredEvent.class);
        sendNotificationUseCase.sendMessage(
                telegramKafkaMessageFormatter.formatBookingExpiredEvent(event)
        );
    }

    private <T> T readValue(String payload, Class<T> targetType) {
        try {
            return objectMapper.readValue(payload, targetType);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Failed to deserialize Kafka payload to " + targetType.getSimpleName(),
                    exception
            );
        }
    }
}