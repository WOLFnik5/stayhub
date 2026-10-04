package com.bookingapp.web.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.domain.model.User;
import com.bookingapp.domain.model.enums.AccommodationType;
import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.domain.model.enums.PaymentStatus;
import com.bookingapp.exception.PaymentStateException;
import com.bookingapp.infrastructure.stripe.VerifiedCheckout;
import com.bookingapp.service.PaymentService;
import com.bookingapp.web.dto.CreatePaymentRequest;
import com.bookingapp.web.dto.PaymentSessionResult;
import com.bookingapp.web.support.AbstractControllerIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PaymentReconciliationIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private PaymentService paymentService;
    private User owner;
    private User admin;
    private Booking booking;
    private Payment attempt;

    @BeforeEach
    void fixtures() {
        owner = persistCustomer("reconcile@example.com");
        admin = persistAdmin("reconcile-admin@example.com");
        var accommodation = persistAccommodation(AccommodationType.APARTMENT,
                "Warsaw", "Studio", List.of(), BigDecimal.valueOf(100), 1);
        booking = persistBooking(futureDate(3), futureDate(5), accommodation.getId(),
                owner.getId(), BookingStatus.PENDING);
        Payment old = new Payment(null, PaymentStatus.PENDING, booking.getId(),
                null, null, BigDecimal.valueOf(200));
        old.setCreatedAt(Instant.now().minusSeconds(24 * 3600));
        attempt = paymentRepository.save(old);
    }

    @Test
    void oldMissingSessionShouldBecomeVisibleAndBlockCheckout() throws Exception {
        assertThat(paymentService.reconciliationCandidates(100)).extracting(Payment::getId)
                .contains(attempt.getId());
        paymentService.reconcileStaleAttempt(attempt);
        assertThat(saved().getStatus()).isEqualTo(PaymentStatus.RECONCILIATION_REQUIRED);
        assertThat(paymentService.reconciliationCandidates(100)).isEmpty();
        assertThat(checkout()).isEqualTo(409);
        assertThat(paymentRepository.findAllByBookingId(booking.getId())).hasSize(1);
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING);
        verifyNoInteractions(stripePaymentProvider);
    }

    @Test
    void recentMissingSessionShouldRemainRetryable() {
        attempt.setCreatedAt(Instant.now());
        paymentRepository.save(attempt);
        paymentService.reconcileStaleAttempt(attempt);
        assertThat(saved().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(paymentService.reconciliationCandidates(100)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PAID", "EXPIRED", "PENDING"})
    void adminShouldRecoverOnlyAfterProviderVerification(PaymentStatus result) throws Exception {
        paymentService.reconcileStaleAttempt(attempt);
        when(stripePaymentProvider.inspectCheckout(any(), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new VerifiedCheckout("sess_found", "https://checkout.example/found", result);
        });
        assertThat(reconcile(admin, "sess_found")).isEqualTo(200);
        assertThat(saved().getStatus()).isEqualTo(result);
        assertThat(saved().getSessionId()).isEqualTo("sess_found");
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(result == PaymentStatus.PAID
                        ? BookingStatus.CONFIRMED : BookingStatus.PENDING);
    }

    @Test
    void expiredVerifiedAttemptShouldAllowSafeNewCheckout() throws Exception {
        paymentService.reconcileStaleAttempt(attempt);
        when(stripePaymentProvider.inspectCheckout(any(), any())).thenReturn(
                new VerifiedCheckout("sess_found", null, PaymentStatus.EXPIRED));
        assertThat(reconcile(admin, "sess_found")).isEqualTo(200);
        when(stripePaymentProvider.createPaymentSession(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Payment next = invocation.getArgument(0);
                    return new PaymentSessionResult("sess_new", "https://checkout.example/new",
                            next.getId(), "PENDING", booking.getId(), next.getAmountToPay());
                });
        assertThat(checkout()).isEqualTo(200);
        assertThat(paymentRepository.findAllByBookingId(booking.getId())).hasSize(2);
        assertThat(saved().getStatus()).isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test
    void customerAndAnonymousShouldNotReconcilePayments() throws Exception {
        assertThat(reconcile(owner, "sess_found")).isEqualTo(403);
        assertThat(mockMvc.perform(post("/payments/{id}/reconcile", attempt.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("sessionId", "sess_found"))))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
        verifyNoInteractions(stripePaymentProvider);
    }

    @Test
    void invalidProviderEvidenceShouldLeaveAttemptBlocked() throws Exception {
        paymentService.reconcileStaleAttempt(attempt);
        when(stripePaymentProvider.inspectCheckout(any(), any()))
                .thenThrow(new PaymentStateException("Amount mismatch"));
        assertThat(reconcile(admin, "sess_wrong")).isEqualTo(409);
        assertThat(saved().getStatus()).isEqualTo(PaymentStatus.RECONCILIATION_REQUIRED);
        assertThat(saved().getSessionId()).isNull();
    }

    @Test
    void backgroundShouldRecoverMissedWebhookForKnownSession() {
        attempt.setSessionId("sess_known");
        paymentRepository.save(attempt);
        when(stripePaymentProvider.inspectCheckout(any(), any())).thenReturn(
                new VerifiedCheckout("sess_known", null, PaymentStatus.PAID));
        paymentService.reconcileStaleAttempt(attempt);
        assertThat(saved().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void staleInspectionShouldNotOverwriteConcurrentWebhook() throws Exception {
        attempt.setSessionId("sess_known");
        paymentRepository.save(attempt);
        when(stripePaymentProvider.isPaymentSuccessful("sess_known")).thenReturn(true);
        when(stripePaymentProvider.inspectCheckout(any(), any())).thenAnswer(invocation -> {
            paymentService.handleWebhookSuccess("sess_known", attempt.getId());
            return new VerifiedCheckout("sess_known", null, PaymentStatus.EXPIRED);
        });
        assertThat(reconcile(admin, "sess_known")).isEqualTo(200);
        assertThat(saved().getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void suppliedSessionShouldNotReplaceExistingSession() throws Exception {
        attempt.setSessionId("sess_known");
        paymentRepository.save(attempt);
        assertThat(reconcile(admin, "sess_other")).isEqualTo(409);
        assertThat(saved().getSessionId()).isEqualTo("sess_known");
        verifyNoInteractions(stripePaymentProvider);
    }

    private int reconcile(User actor, String sessionId) throws Exception {
        return mockMvc.perform(post("/payments/{id}/reconcile", attempt.getId())
                .header("Authorization", authorizationHeader(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("sessionId", sessionId))))
                .andReturn().getResponse().getStatus();
    }

    private int checkout() throws Exception {
        return mockMvc.perform(post("/payments").header("Authorization", authorizationHeader(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(new CreatePaymentRequest(booking.getId()))))
                .andReturn().getResponse().getStatus();
    }

    private Payment saved() {
        return paymentRepository.findById(attempt.getId()).orElseThrow();
    }
}
