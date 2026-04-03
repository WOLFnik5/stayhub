package com.bookingapp.web.controller;

import com.bookingapp.domain.model.Payment;
import com.bookingapp.service.PaymentService;
import com.bookingapp.web.dto.CreatePaymentRequest;
import com.bookingapp.web.dto.PaymentCancelResponse;
import com.bookingapp.web.dto.PaymentResponse;
import com.bookingapp.web.dto.PaymentSessionResult;
import com.bookingapp.web.dto.PaymentSuccessResponse;
import com.bookingapp.web.mapper.PaymentWebMapper;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController implements PaymentApi {

    private final PaymentService paymentService;
    private final PaymentWebMapper paymentWebMapper;

    public PaymentController(
            PaymentService paymentService,
            PaymentWebMapper paymentWebMapper
    ) {
        this.paymentService = paymentService;
        this.paymentWebMapper = paymentWebMapper;
    }

    @Override
    public List<PaymentResponse> getPayments(Long userId) {
        return paymentService.getPayments(paymentWebMapper.toFilterQuery(userId)).stream()
                .map(paymentWebMapper::toResponse)
                .toList();
    }

    @Override
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        PaymentSessionResult paymentSession = paymentService
                .createPaymentSession(request.bookingId());

        return paymentWebMapper.toResponse(paymentSession);
    }

    @Override
    public PaymentSuccessResponse handlePaymentSuccess(String sessionId) {
        Payment payment = paymentService.handlePaymentSuccess(sessionId);
        return paymentWebMapper.toSuccessResponse(payment);
    }

    @Override
    public PaymentCancelResponse handlePaymentCancel(String sessionId, Long bookingId) {
        return paymentWebMapper.toCancelResponse(paymentService.handlePaymentCancel(
                sessionId,
                bookingId
        ));
    }
}
