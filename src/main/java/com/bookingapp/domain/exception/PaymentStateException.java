package com.bookingapp.domain.exception;

public class PaymentStateException extends BusinessValidationException {

    public PaymentStateException(String message) {
        super(message);
    }
}
