package com.bookingapp.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Setter
@Getter
@Component
@Validated
@ConfigurationProperties(prefix = "app.kafka.topics")
public class KafkaTopicsProperties {

    @NotBlank
    private String bookingCreated;

    @NotBlank
    private String bookingCanceled;

    @NotBlank
    private String bookingExpired;

    @NotBlank
    private String accommodationCreated;

    @NotBlank
    private String paymentSucceeded;

}
