package com.bookingapp.infrastructure.stripe;

import com.bookingapp.domain.model.enums.PaymentStatus;

public record VerifiedCheckout(String sessionId, String sessionUrl, PaymentStatus status) {
}
