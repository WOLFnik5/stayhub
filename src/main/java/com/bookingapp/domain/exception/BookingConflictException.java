package com.bookingapp.domain.exception;

public class BookingConflictException extends BusinessValidationException {

    public BookingConflictException(String message) {
        super(message);
    }
}
