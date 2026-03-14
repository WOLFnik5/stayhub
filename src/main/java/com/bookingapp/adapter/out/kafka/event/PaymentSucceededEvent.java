package com.bookingapp.adapter.out.kafka.event;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentSucceededEvent(
        Long paymentId,
        Long bookingId,
        String sessionId,
        BigDecimal amountToPay,
        Instant paidAt
) {
}
