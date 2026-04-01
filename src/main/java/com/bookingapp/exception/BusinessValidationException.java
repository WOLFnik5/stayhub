package com.bookingapp.exception;

public class BusinessValidationException extends DomainException {

    public BusinessValidationException(String message) {
        super(message);
    }
}
