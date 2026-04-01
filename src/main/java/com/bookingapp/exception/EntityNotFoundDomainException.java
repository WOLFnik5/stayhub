package com.bookingapp.exception;

public class EntityNotFoundDomainException extends DomainException {

    public EntityNotFoundDomainException(String message) {
        super(message);
    }
}
