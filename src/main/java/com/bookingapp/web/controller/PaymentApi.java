package com.bookingapp.web.controller;

import com.bookingapp.web.dto.CreatePaymentRequest;
import com.bookingapp.web.dto.PaymentCancelResponse;
import com.bookingapp.web.dto.PaymentResponse;
import com.bookingapp.web.dto.PaymentSuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping("/payments")
@Tag(name = "Payments", description = "Stripe payment session and callback operations")
public interface PaymentApi {

    @GetMapping
    @Operation(summary = "List payments", security = @SecurityRequirement(name = "bearerAuth"))
    List<PaymentResponse> getPayments(
            @RequestParam(name = "user_id", required = false) Long userId
    );

    @PostMapping
    @Operation(summary = "Create payment session for booking",
            security = @SecurityRequirement(name = "bearerAuth"))
    PaymentResponse createPayment(@Valid @RequestBody CreatePaymentRequest request);

    @GetMapping("/success")
    @Operation(summary = "Handle successful payment callback")
    PaymentSuccessResponse handlePaymentSuccess(
            @RequestParam(name = "session_id") String sessionId
    );

    @GetMapping("/cancel")
    @Operation(summary = "Handle canceled payment callback")
    PaymentCancelResponse handlePaymentCancel(
            @RequestParam(name = "session_id", required = false) String sessionId,
            @RequestParam(name = "booking_id", required = false) Long bookingId
    );
}
