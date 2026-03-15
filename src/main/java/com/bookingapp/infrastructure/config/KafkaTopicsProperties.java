package com.bookingapp.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

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

    public String getBookingCreated() {
        return bookingCreated;
    }

    public void setBookingCreated(String bookingCreated) {
        this.bookingCreated = bookingCreated;
    }

    public String getBookingCanceled() {
        return bookingCanceled;
    }

    public void setBookingCanceled(String bookingCanceled) {
        this.bookingCanceled = bookingCanceled;
    }

    public String getBookingExpired() {
        return bookingExpired;
    }

    public void setBookingExpired(String bookingExpired) {
        this.bookingExpired = bookingExpired;
    }

    public String getAccommodationCreated() {
        return accommodationCreated;
    }

    public void setAccommodationCreated(String accommodationCreated) {
        this.accommodationCreated = accommodationCreated;
    }

    public String getPaymentSucceeded() {
        return paymentSucceeded;
    }

    public void setPaymentSucceeded(String paymentSucceeded) {
        this.paymentSucceeded = paymentSucceeded;
    }
}
