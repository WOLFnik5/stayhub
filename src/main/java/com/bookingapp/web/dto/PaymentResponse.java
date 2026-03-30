package com.bookingapp.web.dto;

import com.bookingapp.domain.enums.PaymentStatus;
import java.math.BigDecimal;

public record PaymentResponse(
        Long id,
        PaymentStatus status,
        Long bookingId,
        String sessionUrl,
        String sessionId,
        BigDecimal amountToPay
) {
}
