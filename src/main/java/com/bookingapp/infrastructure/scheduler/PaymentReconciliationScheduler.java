package com.bookingapp.infrastructure.scheduler;

import com.bookingapp.domain.model.Payment;
import com.bookingapp.service.PaymentService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.scheduler.payment-reconciliation.enabled",
        havingValue = "true", matchIfMissing = true)
public class PaymentReconciliationScheduler {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(PaymentReconciliationScheduler.class);
    private final PaymentService paymentService;
    private long afterId;

    public PaymentReconciliationScheduler(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.payment-reconciliation.delay-ms:300000}",
            initialDelayString = "${app.scheduler.payment-reconciliation.delay-ms:300000}")
    public void reconcilePayments() {
        List<Payment> candidates = paymentService.reconciliationCandidates(100, afterId);
        if (candidates.isEmpty() && afterId != 0) {
            afterId = 0;
            candidates = paymentService.reconciliationCandidates(100, afterId);
        }
        for (Payment payment : candidates) {
            // Advance even after failure so one processing session cannot starve later attempts.
            afterId = payment.getId();
            try {
                paymentService.reconcileStaleAttempt(payment);
                if (payment.getSessionId() == null) {
                    LOGGER.atWarn().addKeyValue("paymentId", payment.getId())
                            .log("Missing Stripe session requires administrative reconciliation");
                }
            } catch (RuntimeException exception) {
                LOGGER.atWarn().addKeyValue("paymentId", payment.getId())
                        .addKeyValue("failureType", exception.getClass().getSimpleName())
                        .log("Payment reconciliation will be retried");
            }
        }
    }
}
