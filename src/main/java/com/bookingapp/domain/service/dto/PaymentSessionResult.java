package com.bookingapp.domain.service.dto;

import java.math.BigDecimal;

public record PaymentSessionResult(
        String sessionId,
        String sessionUrl,
        Long paymentId,
        String status,
        Long bookingId,
        BigDecimal amountToPay
) {
}
