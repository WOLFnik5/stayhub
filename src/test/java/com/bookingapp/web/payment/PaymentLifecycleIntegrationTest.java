package com.bookingapp.web.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.domain.model.User;
import com.bookingapp.domain.model.enums.AccommodationType;
import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.domain.model.enums.PaymentStatus;
import com.bookingapp.exception.PaymentProviderGatewayException;
import com.bookingapp.exception.PaymentProviderUnavailableException;
import com.bookingapp.exception.PaymentStateException;
import com.bookingapp.infrastructure.kafka.OutboxKafkaEventPublisher;
import com.bookingapp.persistence.outbox.OutboxEventJpaRepository;
import com.bookingapp.persistence.outbox.OutboxStatus;
import com.bookingapp.service.BookingExpirationService;
import com.bookingapp.web.dto.CreatePaymentRequest;
import com.bookingapp.web.dto.PaymentSessionResult;
import com.bookingapp.web.support.AbstractControllerIntegrationTest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class PaymentLifecycleIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private BookingExpirationService expirationService;
    @Autowired
    private OutboxKafkaEventPublisher realEventPublisher;
    @Autowired
    private OutboxEventJpaRepository outboxRepository;
    private User owner;
    private Booking booking;

    @AfterEach
    void cleanPaymentOutbox() {
        List<Long> paymentIds = paymentRepository.findAllByBookingId(booking.getId()).stream()
                .map(Payment::getId).toList();
        outboxRepository.deleteAll(outboxRepository.findAll().stream()
                .filter(event -> event.getAggregateType().equals("Payment")
                        && paymentIds.contains(event.getAggregateId())).toList());
    }

    @BeforeEach
    void fixtures() {
        owner = persistCustomer("lifecycle@example.com");
        var accommodation = persistAccommodation(AccommodationType.APARTMENT,
                "Warsaw", "Studio", List.of(), BigDecimal.valueOf(100), 1);
        booking = persistBooking(futureDate(10), futureDate(12), accommodation.getId(),
                owner.getId(), BookingStatus.PENDING);
    }

    private void successfulCheckout() {
        when(stripePaymentProvider.createPaymentSession(any(), any(), any(), any()))
                .thenAnswer(invocation -> session(invocation.getArgument(0)));
    }

    private PaymentSessionResult session(Payment payment) {
        return new PaymentSessionResult("sess_" + payment.getId(),
                "https://checkout.example/" + payment.getId(), payment.getId(), "PENDING",
                payment.getBookingId(), payment.getAmountToPay());
    }

    @Test
    void repeatedConcurrentCheckoutShouldReturnOneSession() throws Exception {
        successfulCheckout();
        assertThat(concurrently(this::checkout, this::checkout)).containsOnly(200);
        assertThat(checkout()).isEqualTo(200);
        assertThat(paymentRepository.findAllByBookingId(booking.getId())).hasSize(1);
        // A concurrent retry can call Stripe twice, but the durable payment ID is its idempotency key.
        verify(stripePaymentProvider, atMost(2)).createPaymentSession(any(), any(), any(), any());
    }

    @Test
    void retryAfterProviderFailureShouldReuseDurableAttempt() throws Exception {
        when(stripePaymentProvider.createPaymentSession(any(), any(), any(), any()))
                .thenThrow(new PaymentProviderUnavailableException("ApiConnectionException", null, null))
                .thenAnswer(invocation -> session(invocation.getArgument(0)));
        assertThat(checkout()).isEqualTo(503);
        Payment unfinished = latest();
        assertThat(unfinished.getSessionId()).isNull();
        assertThat(checkout()).isEqualTo(200);
        assertThat(latest().getId()).isEqualTo(unfinished.getId());
        assertThat(latest().getSessionId()).isEqualTo("sess_" + unfinished.getId());
        assertThat(paymentRepository.findAllByBookingId(booking.getId())).hasSize(1);
    }

    @Test
    void expiredCheckoutShouldKeepHistoryAndCreateNewAttempt() throws Exception {
        successfulCheckout();
        assertThat(checkout()).isEqualTo(200);
        Payment first = latest();
        when(stripePaymentProvider.isPaymentSessionExpired(first.getSessionId())).thenReturn(true);
        assertThat(checkout()).isEqualTo(200);
        Payment second = latest();
        assertThat(second.getSessionId()).isNotEqualTo(first.getSessionId());
        assertThat(paymentRepository.findBySessionId(first.getSessionId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.EXPIRED);
        assertThat(paymentRepository.findAllByBookingId(booking.getId())).hasSize(2);
        assertThat(second.getAmountToPay()).isEqualByComparingTo(first.getAmountToPay());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELED", "EXPIRED", "PAST"})
    void shouldRejectPaymentForInactiveBooking(String state) throws Exception {
        if (state.equals("PAST")) {
            booking.setCheckInDate(futureDate(-3));
            booking.setCheckOutDate(futureDate(-1));
        } else {
            booking.setStatus(BookingStatus.valueOf(state));
        }
        bookingRepository.save(booking);
        assertThat(checkout()).isEqualTo(409);
        assertThat(paymentRepository.findAllByBookingId(booking.getId())).isEmpty();
        verifyNoInteractions(stripePaymentProvider);
    }

    @Test
    void cancellationShouldCloseCheckoutAndPreventAnotherPayment() throws Exception {
        successfulCheckout();
        assertThat(checkout()).isEqualTo(200);
        Payment payment = latest();
        when(stripePaymentProvider.expireUnpaidSession(payment.getSessionId())).thenReturn(true);
        assertThat(cancel()).isEqualTo(200);
        assertThat(latest().getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.CANCELED);
        assertThat(checkout()).isEqualTo(409);
        verify(stripePaymentProvider).expireUnpaidSession(payment.getSessionId());
    }

    @Test
    void cancellationShouldNotReleaseBookingWhenStripeCannotCloseSession() throws Exception {
        successfulCheckout();
        assertThat(checkout()).isEqualTo(200);
        when(stripePaymentProvider.expireUnpaidSession(latest().getSessionId()))
                .thenThrow(new PaymentProviderUnavailableException("ApiConnectionException", null, null));
        assertThat(cancel()).isEqualTo(503);
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.CANCELING);
        assertThat(latest().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void expirationShouldCloseUnpaidCheckout() throws Exception {
        successfulCheckout();
        assertThat(checkout()).isEqualTo(200);
        when(stripePaymentProvider.expireUnpaidSession(latest().getSessionId())).thenReturn(true);
        expirationService.expireBookings(booking.getCheckOutDate());
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(latest().getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(checkout()).isEqualTo(409);
    }

    @ParameterizedTest
    @ValueSource(strings = {"checkout.session.completed", "checkout.session.async_payment_succeeded"})
    void successLandingShouldNotConfirmPaymentAndWebhookShouldDeduplicate(String type) throws Exception {
        successfulCheckout();
        assertThat(checkout()).isEqualTo(200);
        Payment payment = latest();
        when(stripePaymentProvider.isPaymentSuccessful(payment.getSessionId())).thenReturn(true);
        String payload = payload(type, "paid");
        assertThat(mockMvc.perform(get("/payments/success")
                .param("session_id", payment.getSessionId())).andReturn().getResponse().getStatus())
                .isEqualTo(200);
        assertThat(mockMvc.perform(get("/payments/success")
                .param("session_id", payment.getSessionId())).andReturn().getResponse().getStatus())
                .isEqualTo(200);
        assertThat(latest().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.PENDING);
        verifyNoInteractions(kafkaEventPublisher);

        assertThat(webhook(payload, sign(payload, Instant.now().getEpochSecond()))).isEqualTo(200);
        assertThat(latest().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(webhook(payload, sign(payload, Instant.now().getEpochSecond()))).isEqualTo(200);
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(kafkaEventPublisher, times(1)).publishPaymentSucceeded(any());
        assertThat(checkout()).isEqualTo(409);
    }

    @Test
    void webhookAloneShouldConfirmPayment() throws Exception {
        successfulCheckout();
        assertThat(checkout()).isEqualTo(200);
        when(stripePaymentProvider.isPaymentSuccessful(latest().getSessionId())).thenReturn(true);
        String payload = payload("checkout.session.completed", "paid");
        assertThat(webhook(payload, sign(payload, Instant.now().getEpochSecond()))).isEqualTo(200);
        assertThat(latest().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void webhookShouldRecoverSessionAfterLocalSaveFailure() throws Exception {
        when(stripePaymentProvider.createPaymentSession(any(), any(), any(), any()))
                .thenThrow(new PaymentProviderUnavailableException("ApiConnectionException", null, null));
        assertThat(checkout()).isEqualTo(503);
        Payment attempt = latest();
        String sessionId = "sess_recovered";
        when(stripePaymentProvider.isPaymentSuccessful(sessionId)).thenReturn(true);
        String payload = asJson(Map.of("id", "evt_recovered", "object", "event",
                "type", "checkout.session.completed", "data", Map.of("object", Map.of(
                        "id", sessionId, "object", "checkout.session", "payment_status", "paid",
                        "metadata", Map.of("paymentId", attempt.getId().toString())))));
        assertThat(webhook(payload, sign(payload, Instant.now().getEpochSecond()))).isEqualTo(200);
        assertThat(latest().getId()).isEqualTo(attempt.getId());
        assertThat(latest().getSessionId()).isEqualTo(sessionId);
        assertThat(latest().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(kafkaEventPublisher).publishPaymentSucceeded(any());
    }

    @Test
    void checkoutRecoveryShouldConfirmBookingWhenStripeAlreadyReportsPaid() throws Exception {
        successfulCheckout();
        assertThat(checkout()).isEqualTo(200);
        when(stripePaymentProvider.isPaymentSuccessful(latest().getSessionId())).thenReturn(true);
        assertThat(checkout()).isEqualTo(200);
        assertThat(latest().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(kafkaEventPublisher).publishPaymentSucceeded(any());
    }

    @Test
    void simultaneousWebhookRetriesShouldConfirmOnce() throws Exception {
        successfulCheckout();
        assertThat(checkout()).isEqualTo(200);
        when(stripePaymentProvider.isPaymentSuccessful(latest().getSessionId())).thenReturn(true);
        String payload = payload("checkout.session.completed", "paid");
        String signature = sign(payload, Instant.now().getEpochSecond());
        assertThat(concurrently(() -> webhook(payload, signature),
                () -> webhook(payload, signature))).containsOnly(200);
        assertThat(latest().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(kafkaEventPublisher, times(1)).publishPaymentSucceeded(any());
    }

    @Test
    void eventFailureShouldRollBackBothStatusesAndAllowRetry() throws Exception {
        successfulCheckout();
        assertThat(checkout()).isEqualTo(200);
        when(stripePaymentProvider.isPaymentSuccessful(latest().getSessionId())).thenReturn(true);
        AtomicBoolean fail = new AtomicBoolean(true);
        doAnswer(invocation -> {
            realEventPublisher.publishPaymentSucceeded(invocation.getArgument(0));
            if (fail.getAndSet(false)) {
                throw new IllegalStateException("Failure after outbox insert");
            }
            return null;
        }).when(kafkaEventPublisher).publishPaymentSucceeded(any());
        String payload = payload("checkout.session.completed", "paid");
        String signature = sign(payload, Instant.now().getEpochSecond());
        assertThat(webhook(payload, signature)).isEqualTo(500);
        assertThat(latest().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(paymentOutboxCount()).isZero();
        assertThat(webhook(payload, signature)).isEqualTo(200);
        assertThat(latest().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(paymentOutboxCount()).isEqualTo(1);
    }

    @Test
    void confirmationShouldCommitOneRealOutboxEventAcrossRetries() throws Exception {
        successfulCheckout();
        assertThat(checkout()).isEqualTo(200);
        when(stripePaymentProvider.isPaymentSuccessful(latest().getSessionId())).thenReturn(true);
        doAnswer(invocation -> {
            realEventPublisher.publishPaymentSucceeded(invocation.getArgument(0));
            return null;
        }).when(kafkaEventPublisher).publishPaymentSucceeded(any());
        String payload = payload("checkout.session.completed", "paid");
        String signature = sign(payload, Instant.now().getEpochSecond());
        assertThat(webhook(payload, signature)).isEqualTo(200);
        assertThat(webhook(payload, signature)).isEqualTo(200);
        assertThat(latest().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(paymentOutboxCount()).isEqualTo(1);
        assertThat(outboxRepository.findAll().stream()
                .filter(event -> event.getAggregateType().equals("Payment")
                        && event.getAggregateId().equals(latest().getId())).toList())
                .allSatisfy(event -> {
                    assertThat(event.getStatus()).isEqualTo(OutboxStatus.NEW);
                    assertThat(event.getEventType()).isEqualTo("PaymentSucceededEvent");
                });
    }

    private long paymentOutboxCount() {
        Long paymentId = latest().getId();
        return outboxRepository.findAll().stream()
                .filter(event -> event.getAggregateType().equals("Payment")
                        && event.getAggregateId().equals(paymentId)).count();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELED", "EXPIRED"})
    void latePaymentShouldNotReviveTerminalBooking(String status) throws Exception {
        successfulCheckout();
        assertThat(checkout()).isEqualTo(200);
        booking.setStatus(BookingStatus.valueOf(status));
        bookingRepository.save(booking);
        when(stripePaymentProvider.isPaymentSuccessful(latest().getSessionId())).thenReturn(true);
        String payload = payload("checkout.session.completed", "paid");
        String signature = sign(payload, Instant.now().getEpochSecond());
        assertThat(webhook(payload, signature)).isEqualTo(200);
        assertThat(webhook(payload, signature)).isEqualTo(200);
        assertThat(latest().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.valueOf(status));
        verify(kafkaEventPublisher, times(1)).publishPaymentSucceeded(any());
    }

    @Test
    void verifiedRetryShouldRepairLegacyPaidPendingBookingWithoutAnotherEvent() throws Exception {
        successfulCheckout();
        assertThat(checkout()).isEqualTo(200);
        Payment payment = latest();
        payment.setStatus(PaymentStatus.PAID);
        paymentRepository.save(payment);
        when(stripePaymentProvider.isPaymentSuccessful(payment.getSessionId())).thenReturn(true);
        String payload = payload("checkout.session.completed", "paid");
        assertThat(webhook(payload, sign(payload, Instant.now().getEpochSecond()))).isEqualTo(200);
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verifyNoInteractions(kafkaEventPublisher);
    }

    @Test
    void expirationShouldRecordSettlementThenKeepBookingExpired() throws Exception {
        successfulCheckout();
        assertThat(checkout()).isEqualTo(200);
        when(stripePaymentProvider.expireUnpaidSession(latest().getSessionId())).thenReturn(false);
        expirationService.expireBookings(booking.getCheckOutDate());
        assertThat(latest().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.EXPIRED);
        verify(kafkaEventPublisher).publishPaymentSucceeded(any());
        verify(kafkaEventPublisher).publishBookingExpired(any());
    }

    @Test
    void cancellationShouldFailIfPaymentCompletedDuringCheckout() throws Exception {
        successfulCheckout();
        assertThat(checkout()).isEqualTo(200);
        when(stripePaymentProvider.expireUnpaidSession(latest().getSessionId())).thenReturn(false);
        assertThat(cancel()).isEqualTo(409);
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(latest().getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void unresolvedOldAttemptShouldNotCreateAnotherRemoteSession() throws Exception {
        Payment attempt = new Payment(null, PaymentStatus.PENDING, booking.getId(),
                null, null, BigDecimal.valueOf(200));
        attempt.setCreatedAt(Instant.now().minusSeconds(24 * 60 * 60));
        paymentRepository.save(attempt);
        assertThat(checkout()).isEqualTo(409);
        verifyNoInteractions(stripePaymentProvider);
        assertThat(paymentRepository.findAllByBookingId(booking.getId())).hasSize(1);
    }

    @Test
    void webhookShouldRejectMissingInvalidStaleOrTamperedSignature() throws Exception {
        String payload = payload("checkout.session.completed", "paid");
        assertThat(webhook(payload, "t=1,v1=invalid")).isEqualTo(400);
        assertThat(webhook(payload, sign(payload, Instant.now().minusSeconds(600).getEpochSecond())))
                .isEqualTo(400);
        assertThat(webhook(payload + " ", sign(payload, Instant.now().getEpochSecond()))).isEqualTo(400);
        assertThat(mockMvc.perform(post("/payments/webhook").contentType(MediaType.APPLICATION_JSON)
                .content(payload)).andReturn().getResponse().getStatus()).isEqualTo(400);
        verifyNoInteractions(stripePaymentProvider, kafkaEventPublisher);
    }

    @Test
    void webhookShouldIgnoreUnpaidAndUnrelatedEvents() throws Exception {
        for (String payload : List.of(payload("checkout.session.completed", "unpaid"),
                payload("customer.created", "paid"))) {
            assertThat(webhook(payload, sign(payload, Instant.now().getEpochSecond()))).isEqualTo(200);
        }
        verifyNoInteractions(stripePaymentProvider, kafkaEventPublisher);
    }

    @Test
    void webhookShouldReportPaymentStateConflictOnVerificationFailure() throws Exception {
        successfulCheckout();
        assertThat(checkout()).isEqualTo(200);
        when(stripePaymentProvider.isPaymentSuccessful(latest().getSessionId())).thenReturn(true);
        doThrow(new PaymentStateException("Amount mismatch"))
                .when(stripePaymentProvider).validatePayment(any());
        String payload = payload("checkout.session.completed", "paid");
        assertThat(webhook(payload, sign(payload, Instant.now().getEpochSecond()))).isEqualTo(409);
        assertThat(latest().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.PENDING);
        verifyNoInteractions(kafkaEventPublisher);
    }

    @Test
    void webhookShouldRetryTemporaryProviderFailureWithoutDuplicatingOutboxEvent() throws Exception {
        successfulCheckout();
        assertThat(checkout()).isEqualTo(200);
        when(stripePaymentProvider.isPaymentSuccessful(latest().getSessionId())).thenReturn(true);
        doThrow(new PaymentProviderUnavailableException("ApiConnectionException", null, null))
                .doNothing()
                .when(stripePaymentProvider).validatePayment(any());
        String payload = payload("checkout.session.completed", "paid");
        String signature = sign(payload, Instant.now().getEpochSecond());

        assertThat(webhook(payload, signature)).isEqualTo(503);
        assertThat(latest().getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(kafkaEventPublisher);

        assertThat(webhook(payload, signature)).isEqualTo(200);
        assertThat(latest().getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(kafkaEventPublisher, times(1)).publishPaymentSucceeded(any());
    }

    @Test
    void checkoutShouldReturn502ForNonRetryableProviderFailure() throws Exception {
        when(stripePaymentProvider.createPaymentSession(any(), any(), any(), any()))
                .thenThrow(new PaymentProviderGatewayException("ApiException", 400, "req_123"));

        assertThat(checkout()).isEqualTo(502);
    }

    private int checkout() throws Exception {
        return mockMvc.perform(post("/payments").header("Authorization", authorizationHeader(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(new CreatePaymentRequest(booking.getId()))))
                .andReturn().getResponse().getStatus();
    }

    private int cancel() throws Exception {
        return mockMvc.perform(delete("/bookings/{id}", booking.getId())
                .header("Authorization", authorizationHeader(owner)))
                .andReturn().getResponse().getStatus();
    }

    private Payment latest() {
        return paymentRepository.findByBookingId(booking.getId()).orElseThrow();
    }

    private Booking savedBooking() {
        return bookingRepository.findById(booking.getId()).orElseThrow();
    }

    private String payload(String type, String paymentStatus) throws Exception {
        String sessionId = paymentRepository.findByBookingId(booking.getId())
                .map(Payment::getSessionId).orElse("sess_unknown");
        return asJson(Map.of("id", "evt_test", "object", "event", "type", type,
                "data", Map.of("object", Map.of("id", sessionId,
                        "object", "checkout.session", "payment_status", paymentStatus))));
    }

    private String sign(String payload, long timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("whsec_test_only".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(
                mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
    }

    private int webhook(String payload, String signature) throws Exception {
        return mockMvc.perform(post("/payments/webhook").header("Stripe-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andReturn().getResponse().getStatus();
    }

    private List<Integer> concurrently(Callable<Integer> first, Callable<Integer> second)
            throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            var a = executor.submit(() -> awaitCall(first, ready, go));
            var b = executor.submit(() -> awaitCall(second, ready, go));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally {
            go.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private int awaitCall(Callable<Integer> call, CountDownLatch ready, CountDownLatch go)
            throws Exception {
        ready.countDown();
        assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
        return call.call();
    }
}
