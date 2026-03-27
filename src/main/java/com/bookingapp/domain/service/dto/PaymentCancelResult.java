package com.bookingapp.domain.service.dto;

import com.bookingapp.domain.enums.PaymentStatus;

public record PaymentCancelResult(
        Long paymentId,
        String sessionId,
        String sessionUrl,
        PaymentStatus paymentStatus,
        boolean canBeCompletedLater,
        String message
) {
}
