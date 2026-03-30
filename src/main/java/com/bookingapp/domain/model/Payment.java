package com.bookingapp.domain.model;

import com.bookingapp.domain.enums.PaymentStatus;
import com.bookingapp.domain.exception.BusinessValidationException;
import com.bookingapp.domain.exception.PaymentStateException;
import java.math.BigDecimal;
import java.util.Objects;

public final class Payment {

    private final Long id;
    private final PaymentStatus status;
    private final Long bookingId;
    private final String sessionUrl;
    private final String sessionId;
    private final BigDecimal amountToPay;

    public Payment(
            Long id,
            PaymentStatus status,
            Long bookingId,
            String sessionUrl,
            String sessionId,
            BigDecimal amountToPay
    ) {
        this.id = id;
        this.status = Objects.requireNonNull(status, "Payment status must not be null");
        this.bookingId = Objects.requireNonNull(bookingId, "Payment booking id must not be null");
        this.sessionUrl = normalizeNullable(sessionUrl);
        this.sessionId = normalizeNullable(sessionId);
        this.amountToPay = validateAmount(amountToPay);
    }

    public static Payment createPending(Long bookingId, BigDecimal amountToPay) {
        return new Payment(null, PaymentStatus.PENDING, bookingId, null, null, amountToPay);
    }

    public Payment attachSession(String sessionId, String sessionUrl) {
        return new Payment(id, status, bookingId, requireNonBlank(sessionUrl,
                "Payment session URL must not be blank"),
                requireNonBlank(sessionId, "Payment session id must not be blank"),
                amountToPay);
    }

    public Payment markPaid() {
        if (status == PaymentStatus.PAID) {
            return this;
        }
        if (status == PaymentStatus.EXPIRED) {
            throw new PaymentStateException("Expired payment cannot be marked as paid");
        }
        return new Payment(id, PaymentStatus.PAID, bookingId, sessionUrl, sessionId, amountToPay);
    }

    public Payment expire() {
        if (status == PaymentStatus.PAID) {
            throw new PaymentStateException("Paid payment cannot be expired");
        }
        if (status == PaymentStatus.EXPIRED) {
            return this;
        }
        return new Payment(
                id, PaymentStatus.EXPIRED, bookingId, sessionUrl, sessionId, amountToPay
        );
    }

    public Long getId() {
        return id;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public String getSessionUrl() {
        return sessionUrl;
    }

    public String getSessionId() {
        return sessionId;
    }

    public BigDecimal getAmountToPay() {
        return amountToPay;
    }

    private static BigDecimal validateAmount(BigDecimal amountToPay) {
        Objects.requireNonNull(amountToPay, "Payment amount must not be null");
        if (amountToPay.signum() < 0) {
            throw new BusinessValidationException("Payment amount must not be negative");
        }
        return amountToPay;
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessValidationException(message);
        }
        return value.trim();
    }
}
