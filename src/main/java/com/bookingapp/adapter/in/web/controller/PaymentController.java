package com.bookingapp.adapter.in.web.controller;

import com.bookingapp.adapter.in.web.dto.CreatePaymentRequest;
import com.bookingapp.adapter.in.web.dto.PaymentCancelResponse;
import com.bookingapp.adapter.in.web.dto.PaymentResponse;
import com.bookingapp.adapter.in.web.dto.PaymentSuccessResponse;
import com.bookingapp.adapter.in.web.mapper.PaymentWebMapper;
import com.bookingapp.application.dto.PaymentSession;
import com.bookingapp.application.port.in.payment.CreatePaymentSessionUseCase;
import com.bookingapp.application.port.in.payment.GetPaymentsUseCase;
import com.bookingapp.application.port.in.payment.HandlePaymentCancelUseCase;
import com.bookingapp.application.port.in.payment.HandlePaymentSuccessUseCase;
import com.bookingapp.domain.model.Payment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/payments")
@Tag(name = "Payments", description = "Stripe payment session and callback operations")
public class PaymentController {

    private final CreatePaymentSessionUseCase createPaymentSessionUseCase;
    private final GetPaymentsUseCase getPaymentsUseCase;
    private final HandlePaymentSuccessUseCase handlePaymentSuccessUseCase;
    private final HandlePaymentCancelUseCase handlePaymentCancelUseCase;
    private final PaymentWebMapper paymentWebMapper;

    public PaymentController(
            CreatePaymentSessionUseCase createPaymentSessionUseCase,
            GetPaymentsUseCase getPaymentsUseCase,
            HandlePaymentSuccessUseCase handlePaymentSuccessUseCase,
            HandlePaymentCancelUseCase handlePaymentCancelUseCase,
            PaymentWebMapper paymentWebMapper
    ) {
        this.createPaymentSessionUseCase = createPaymentSessionUseCase;
        this.getPaymentsUseCase = getPaymentsUseCase;
        this.handlePaymentSuccessUseCase = handlePaymentSuccessUseCase;
        this.handlePaymentCancelUseCase = handlePaymentCancelUseCase;
        this.paymentWebMapper = paymentWebMapper;
    }

    @GetMapping
    @Operation(summary = "List payments", security = @SecurityRequirement(name = "bearerAuth"))
    public List<PaymentResponse> getPayments(
            @RequestParam(name = "user_id", required = false) Long userId
    ) {
        return getPaymentsUseCase.getPayments(paymentWebMapper.toFilterQuery(userId)).stream()
                .map(paymentWebMapper::toResponse)
                .toList();
    }

    @PostMapping
    @Operation(summary = "Create payment session for booking", security = @SecurityRequirement(name = "bearerAuth"))
    public PaymentResponse createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        PaymentSession paymentSession = createPaymentSessionUseCase.createPaymentSession(
                paymentWebMapper.toCreatePaymentSessionCommand(request)
        );

        return paymentWebMapper.toResponse(
                paymentSession.paymentId(),
                paymentSession.sessionId(),
                paymentSession.sessionUrl(),
                paymentSession.status(),
                paymentSession.amountToPay(),
                paymentSession.bookingId()
        );
    }

    @GetMapping("/success")
    @Operation(summary = "Handle successful payment callback")
    public PaymentSuccessResponse handlePaymentSuccess(
            @RequestParam(name = "session_id") String sessionId
    ) {
        Payment payment = handlePaymentSuccessUseCase.handlePaymentSuccess(sessionId);
        return paymentWebMapper.toSuccessResponse(payment);
    }

    @GetMapping("/cancel")
    @Operation(summary = "Handle canceled payment callback")
    public PaymentCancelResponse handlePaymentCancel(
            @RequestParam(name = "session_id") String sessionId
    ) {
        return paymentWebMapper.toCancelResponse(handlePaymentCancelUseCase.handlePaymentCancel(sessionId));
    }
}
