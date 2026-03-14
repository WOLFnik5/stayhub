package com.bookingapp.domain.exception;

public class InvalidBookingStateException extends BusinessValidationException {

    public InvalidBookingStateException(String message) {
        super(message);
    }
}
