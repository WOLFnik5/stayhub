package com.bookingapp.web.dto;

import com.bookingapp.domain.model.enums.PaymentStatus;

public record PaymentCancelResponse(
        String message,
        boolean canBeCompletedLater,
        Long paymentId,
        PaymentStatus paymentStatus,
        String sessionId,
        String sessionUrl
) {
}
