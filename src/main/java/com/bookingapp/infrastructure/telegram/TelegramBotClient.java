package com.bookingapp.infrastructure.telegram;

import com.bookingapp.infrastructure.config.TelegramProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class TelegramBotClient {

    private final RestClient telegramRestClient;
    private final TelegramProperties telegramProperties;

    public TelegramBotClient(
            RestClient telegramRestClient,
            TelegramProperties telegramProperties
    ) {
        this.telegramRestClient = telegramRestClient;
        this.telegramProperties = telegramProperties;
    }

    public void sendMessage(String message) {
        telegramRestClient.post()
                .uri("/bot{token}/sendMessage", telegramProperties.getBotToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "chat_id", telegramProperties.getChatId(),
                        "text", message
                ))
                .retrieve()
                .toBodilessEntity();
    }
}
