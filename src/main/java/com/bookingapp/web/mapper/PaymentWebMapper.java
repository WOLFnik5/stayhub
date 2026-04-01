package com.bookingapp.web.mapper;

import com.bookingapp.domain.model.Payment;
import com.bookingapp.domain.model.enums.PaymentStatus;
import com.bookingapp.service.dto.PaymentCancelResult;
import com.bookingapp.service.dto.PaymentFilterQuery;
import com.bookingapp.web.dto.PaymentCancelResponse;
import com.bookingapp.web.dto.PaymentResponse;
import com.bookingapp.web.dto.PaymentSuccessResponse;
import org.springframework.stereotype.Component;

@Component
public class PaymentWebMapper {

    public PaymentFilterQuery toFilterQuery(Long userId) {
        return new PaymentFilterQuery(userId);
    }

    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getStatus(),
                payment.getBookingId(),
                payment.getSessionUrl(),
                payment.getSessionId(),
                payment.getAmountToPay()
        );
    }

    public PaymentResponse toResponse(
            Long paymentId,
            String sessionId,
            String sessionUrl,
            String status,
            java.math.BigDecimal amountToPay,
            Long bookingId
    ) {
        return new PaymentResponse(
                paymentId,
                PaymentStatus.valueOf(status),
                bookingId,
                sessionUrl,
                sessionId,
                amountToPay
        );
    }

    public PaymentSuccessResponse toSuccessResponse(Payment payment) {
        return new PaymentSuccessResponse(
                "Payment completed successfully.",
                toResponse(payment)
        );
    }

    public PaymentCancelResponse toCancelResponse(PaymentCancelResult result) {
        return new PaymentCancelResponse(
                result.message(),
                result.canBeCompletedLater(),
                result.paymentId(),
                result.paymentStatus(),
                result.sessionId(),
                result.sessionUrl()
        );
    }
}
