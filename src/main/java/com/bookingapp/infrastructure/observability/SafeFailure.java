package com.bookingapp.infrastructure.observability;

public final class SafeFailure {
    private SafeFailure() {
    }

    public static String describe(Throwable failure) {
        // Exception messages may contain credentials, URLs or response bodies.
        return failure.getClass().getSimpleName();
    }
}
