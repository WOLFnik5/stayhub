package com.bookingapp.persistence.inbox;

import java.io.Serializable;
import java.util.UUID;

public record ProcessedEventId(
        UUID eventId,
        String consumer
) implements Serializable {
}
