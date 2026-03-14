package com.bookingapp.application.model;

public record CreatePaymentSessionCommand(
        Long bookingId
) {
}
