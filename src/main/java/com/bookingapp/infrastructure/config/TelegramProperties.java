package com.bookingapp.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Setter
@Getter
@Validated
@ConfigurationProperties(prefix = "app.telegram")
public class TelegramProperties {

    @NotBlank
    private String botToken;

    @NotBlank
    private String chatId;

    private String baseUrl = "https://api.telegram.org";

}
