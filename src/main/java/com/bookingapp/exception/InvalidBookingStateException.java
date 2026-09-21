package com.bookingapp.exception;

public class InvalidBookingStateException extends DomainException {

    public InvalidBookingStateException(String message) {
        super(message);
    }
}
