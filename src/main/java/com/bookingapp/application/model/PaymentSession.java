package com.bookingapp.application.model;

public record PaymentSession(
        String sessionId,
        String sessionUrl
) {
}
