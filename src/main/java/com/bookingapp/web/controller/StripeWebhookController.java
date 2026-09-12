package com.bookingapp.web.controller;

import com.bookingapp.exception.BusinessValidationException;
import com.bookingapp.exception.DomainException;
import com.bookingapp.infrastructure.config.StripeProperties;
import com.bookingapp.service.PaymentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StripeWebhookController {
    private final StripeProperties properties;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public StripeWebhookController(StripeProperties properties, PaymentService paymentService,
                                   ObjectMapper objectMapper) {
        this.properties = properties;
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/payments/webhook")
    public ResponseEntity<Void> receive(@RequestBody String payload,
                                       @RequestHeader(value = "Stripe-Signature", required = false)
                                       String signature) {
        if (signature == null || signature.isBlank()) {
            throw new BusinessValidationException("Missing Stripe webhook signature");
        }
        Event event;
        JsonNode session;
        try {
            event = Webhook.constructEvent(payload, signature, properties.getWebhookSecret());
            if (event == null || event.getType() == null) {
                throw new BusinessValidationException("Missing Stripe event type");
            }
            session = objectMapper.readTree(payload).path("data").path("object");
        } catch (SignatureVerificationException | JsonProcessingException
                 | RuntimeException exception) {
            throw new BusinessValidationException("Invalid Stripe webhook");
        }
        if (!("checkout.session.completed".equals(event.getType())
                || "checkout.session.async_payment_succeeded".equals(event.getType()))
                || !"paid".equals(session.path("payment_status").asText())) {
            return ResponseEntity.ok().build();
        }
        String sessionId = session.path("id").asText();
        if (sessionId.isBlank()) {
            throw new BusinessValidationException("Missing Stripe session id");
        }
        Long paymentId = null;
        String paymentReference = session.path("metadata").path("paymentId").asText();
        if (!paymentReference.isBlank()) {
            try {
                paymentId = Long.valueOf(paymentReference);
            } catch (NumberFormatException exception) {
                throw new BusinessValidationException("Invalid Stripe payment reference");
            }
        }
        try {
            paymentService.handleWebhookSuccess(sessionId, paymentId);
        } catch (DomainException exception) {
            // Stripe retries non-2xx responses, including callbacks racing the local commit.
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok().build();
    }
}
