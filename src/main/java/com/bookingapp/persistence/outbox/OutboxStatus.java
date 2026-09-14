package com.bookingapp.persistence.outbox;

public enum OutboxStatus {
    NEW,
    PROCESSING,
    SENT,
    FAILED,
    DEAD
}
