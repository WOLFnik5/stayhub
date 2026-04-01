package com.bookingapp.exception;

public class InvalidBookingStateException extends BusinessValidationException {

    public InvalidBookingStateException(String message) {
        super(message);
    }
}
