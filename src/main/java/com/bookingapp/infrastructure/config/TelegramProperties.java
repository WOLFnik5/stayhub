package com.bookingapp.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
    @Pattern(regexp = "^(?!replace-with-bot-token$).+$",
            message = "botToken must not use the template placeholder")
    private String botToken;

    @NotBlank
    @Pattern(regexp = "^(?!replace-with-chat-id$).+$",
            message = "chatId must not use the template placeholder")
    private String chatId;

    private String baseUrl = "https://api.telegram.org";

}
