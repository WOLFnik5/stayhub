package com.bookingapp.service.dto;

import com.bookingapp.domain.model.enums.PaymentStatus;

public record PaymentCancelResult(
        Long paymentId,
        String sessionId,
        String sessionUrl,
        PaymentStatus paymentStatus,
        boolean canBeCompletedLater,
        String message
) {
}
