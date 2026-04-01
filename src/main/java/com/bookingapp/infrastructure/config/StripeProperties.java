package com.bookingapp.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
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
    private String secretKey;

    @NotBlank
    private String successUrl;

    @NotBlank
    private String cancelUrl;

    @NotBlank
    private String currency = "usd";

}
