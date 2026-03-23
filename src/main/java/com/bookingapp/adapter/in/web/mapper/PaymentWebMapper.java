package com.bookingapp.adapter.in.web.mapper;

import com.bookingapp.adapter.in.web.dto.CreatePaymentRequest;
import com.bookingapp.adapter.in.web.dto.PaymentCancelResponse;
import com.bookingapp.adapter.in.web.dto.PaymentResponse;
import com.bookingapp.adapter.in.web.dto.PaymentSuccessResponse;
import com.bookingapp.application.dto.CreatePaymentSessionCommand;
import com.bookingapp.application.dto.PaymentCancelResult;
import com.bookingapp.application.dto.PaymentFilterQuery;
import com.bookingapp.domain.model.Payment;
import org.springframework.stereotype.Component;

@Component
public class PaymentWebMapper {

    public CreatePaymentSessionCommand toCreatePaymentSessionCommand(CreatePaymentRequest request) {
        return new CreatePaymentSessionCommand(request.bookingId());
    }

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
                com.bookingapp.domain.enums.PaymentStatus.valueOf(status),
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
