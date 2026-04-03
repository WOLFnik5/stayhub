package com.bookingapp.persistence.outbox;

public enum OutboxStatus {
    NEW,
    SENT,
    FAILED,
    DEAD
}
