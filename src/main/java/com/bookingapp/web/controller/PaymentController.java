package com.bookingapp.web.controller;

import com.bookingapp.domain.model.Payment;
import com.bookingapp.service.PaymentService;
import com.bookingapp.web.dto.CreatePaymentRequest;
import com.bookingapp.web.dto.PaymentCancelResponse;
import com.bookingapp.web.dto.PaymentResponse;
import com.bookingapp.web.dto.PaymentSessionResult;
import com.bookingapp.web.dto.PaymentSuccessResponse;
import com.bookingapp.web.mapper.PaymentWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
@Tag(name = "Payments", description = "Stripe payment session and callback operations")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentWebMapper paymentWebMapper;

    @GetMapping
    @Operation(summary = "List payments", security = @SecurityRequirement(name = "bearerAuth"))
    public List<PaymentResponse> getPayments(
            @RequestParam(name = "user_id", required = false) Long userId
    ) {
        return paymentService.getPayments(paymentWebMapper.toFilterQuery(userId)).stream()
                .map(paymentWebMapper::toResponse)
                .toList();
    }

    @PostMapping
    @Operation(summary = "Create payment session for booking",
            security = @SecurityRequirement(name = "bearerAuth"))
    public PaymentResponse createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        PaymentSessionResult paymentSession = paymentService
                .createPaymentSession(request.bookingId());

        return paymentWebMapper.toResponse(paymentSession);
    }

    @GetMapping("/success")
    @Operation(summary = "Handle successful payment callback")
    public PaymentSuccessResponse handlePaymentSuccess(
            @RequestParam(name = "session_id") String sessionId
    ) {
        Payment payment = paymentService.handlePaymentSuccess(sessionId);
        return paymentWebMapper.toSuccessResponse(payment);
    }

    @GetMapping("/cancel/return")
    @Operation(summary = "Return from canceled checkout without exposing payment details")
    public Map<String, String> paymentCancelReturn() {
        return Map.of("message", "You returned from checkout. "
                + "Sign in to view your booking and payment options.");
    }

    @GetMapping("/cancel")
    @Operation(summary = "Check canceled checkout for an accessible booking",
            security = @SecurityRequirement(name = "bearerAuth"))
    public PaymentCancelResponse handlePaymentCancel(
            @RequestParam(name = "session_id", required = false) String sessionId,
            @RequestParam(name = "booking_id", required = false) Long bookingId
    ) {
        return paymentWebMapper.toCancelResponse(paymentService.handlePaymentCancel(
                sessionId,
                bookingId
        ));
    }
}
