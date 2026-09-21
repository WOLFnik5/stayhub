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
@ConfigurationProperties(prefix = "app.stripe")
public class StripeProperties {

    @NotBlank
    @Pattern(regexp = "^(?!sk_test_replace_me$).+$",
            message = "secretKey must not use the template placeholder")
    private String secretKey;

    @NotBlank
    private String webhookSecret;

    @NotBlank
    private String successUrl;

    @NotBlank
    private String cancelUrl;

    @NotBlank
    private String currency = "usd";

}
