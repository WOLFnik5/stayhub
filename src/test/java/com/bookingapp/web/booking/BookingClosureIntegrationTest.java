package com.bookingapp.web.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.domain.model.User;
import com.bookingapp.domain.model.enums.AccommodationType;
import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.domain.model.enums.PaymentStatus;
import com.bookingapp.exception.PaymentProviderUnavailableException;
import com.bookingapp.service.BookingClosureService;
import com.bookingapp.service.BookingExpirationService;
import com.bookingapp.service.PaymentService;
import com.bookingapp.web.dto.CreatePaymentRequest;
import com.bookingapp.web.dto.PaymentSessionResult;
import com.bookingapp.web.support.AbstractControllerIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class BookingClosureIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private BookingClosureService closures;
    @Autowired
    private BookingExpirationService expiration;
    @Autowired
    private PaymentService paymentService;
    private User owner;
    private Booking booking;
    private Payment payment;

    @BeforeEach
    void fixtures() {
        owner = persistCustomer("closure@example.com");
        var accommodation = persistAccommodation(AccommodationType.APARTMENT,
                "Warsaw", "Studio", List.of(), BigDecimal.valueOf(100), 1);
        booking = persistBooking(futureDate(10), futureDate(12), accommodation.getId(),
                owner.getId(), BookingStatus.PENDING);
        payment = persistPayment(PaymentStatus.PENDING, booking.getId(),
                "https://checkout.example/session", "sess_closure", BigDecimal.valueOf(200));
    }

    @Test
    void slowStripeShouldHoldIntentButNotDatabaseLock() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(stripePaymentProvider.expireUnpaidSession(payment.getSessionId())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return true;
        });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var cancel = executor.submit(this::cancel);
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            BookingStatus lockedStatus = transactionTemplate.execute(status -> {
                entityManager.createNativeQuery("SET LOCAL lock_timeout = '500ms'").executeUpdate();
                return bookingRepository.findByIdForUpdate(booking.getId()).orElseThrow().getStatus();
            });
            assertThat(lockedStatus).isEqualTo(BookingStatus.CANCELING);
            assertThat(checkout()).isEqualTo(409);
            release.countDown();
            assertThat(cancel.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.CANCELED);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void failedCancellationShouldResumeFromDurableIntent() throws Exception {
        when(stripePaymentProvider.expireUnpaidSession(payment.getSessionId()))
                .thenThrow(new PaymentProviderUnavailableException("ApiConnectionException", null, null))
                .thenReturn(true);
        assertThat(cancel()).isEqualTo(503);
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.CANCELING);
        assertThat(closures.pendingClosures(0)).extracting(Booking::getId).contains(booking.getId());
        closures.resume(savedBooking());
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.CANCELED);
        assertThat(savedPayment().getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verify(kafkaEventPublisher, times(1)).publishBookingCanceled(any());
    }

    @Test
    void expirationShouldPerformRemoteCallOutsideTransactionAndRecoverFailure() {
        when(stripePaymentProvider.expireUnpaidSession(payment.getSessionId()))
                .thenAnswer(call -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    throw new PaymentProviderUnavailableException("ApiConnectionException", null, null);
                }).thenReturn(true);
        assertThat(expiration.expireBookings(booking.getCheckOutDate()).failedBookingIds())
                .contains(booking.getId());
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.EXPIRING);
        assertThat(expiration.expireBookings(booking.getCheckOutDate()).expiredCount()).isEqualTo(1);
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    void webhookDuringCancellationShouldPreventReleaseAndKeepPaidBookingConfirmed() throws Exception {
        when(stripePaymentProvider.isPaymentSuccessful(payment.getSessionId())).thenReturn(true);
        when(stripePaymentProvider.expireUnpaidSession(payment.getSessionId())).thenAnswer(call -> {
            paymentService.handleWebhookSuccess(payment.getSessionId(), payment.getId());
            return true; // A stale closure response must not overwrite the concurrent payment.
        });
        assertThat(cancel()).isEqualTo(409);
        assertThat(savedPayment().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(kafkaEventPublisher, times(1)).publishPaymentSucceeded(any());
    }

    @Test
    void finalEventFailureShouldKeepIntentAndAllowIdempotentRecovery() throws Exception {
        when(stripePaymentProvider.expireUnpaidSession(payment.getSessionId())).thenReturn(true);
        doThrow(new IllegalStateException("Outbox unavailable")).doNothing()
                .when(kafkaEventPublisher).publishBookingCanceled(any());
        assertThat(cancel()).isEqualTo(500);
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.CANCELING);
        assertThat(savedPayment().getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        closures.resume(savedBooking());
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.CANCELED);
        closures.resume(new Booking(booking.getId(), booking.getCheckInDate(), booking.getCheckOutDate(),
                booking.getAccommodationId(), owner.getId(), BookingStatus.CANCELING));
        verify(kafkaEventPublisher, times(2)).publishBookingCanceled(any());
    }

    @Test
    void lostSessionRecoveryShouldNotOverwriteWebhookThatArrivedDuringCreation() throws Exception {
        payment.setSessionId(null);
        payment.setSessionUrl(null);
        paymentRepository.save(payment);
        when(stripePaymentProvider.isPaymentSuccessful("sess_recovered")).thenReturn(true);
        when(stripePaymentProvider.createPaymentSession(any(), any(), any(), any()))
                .thenAnswer(call -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    paymentService.handleWebhookSuccess("sess_recovered", payment.getId());
                    return new PaymentSessionResult("sess_recovered", "https://checkout.example/recovered",
                            payment.getId(), "PENDING", booking.getId(), payment.getAmountToPay());
                });
        assertThat(cancel()).isEqualTo(409);
        assertThat(savedPayment().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(savedPayment().getSessionId()).isEqualTo("sess_recovered");
        assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void concurrentCancellationRetriesShouldFinalizeOnlyOnce() throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        when(stripePaymentProvider.expireUnpaidSession(payment.getSessionId())).thenAnswer(call -> {
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return true;
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(this::cancel);
            var second = executor.submit(this::cancel);
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(savedBooking().getStatus()).isEqualTo(BookingStatus.CANCELED);
            verify(kafkaEventPublisher, times(1)).publishBookingCanceled(any());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private int cancel() throws Exception {
        return mockMvc.perform(delete("/bookings/{id}", booking.getId())
                .header("Authorization", authorizationHeader(owner)))
                .andReturn().getResponse().getStatus();
    }

    private int checkout() throws Exception {
        return mockMvc.perform(post("/payments").header("Authorization", authorizationHeader(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(new CreatePaymentRequest(booking.getId()))))
                .andReturn().getResponse().getStatus();
    }

    private Booking savedBooking() {
        return bookingRepository.findById(booking.getId()).orElseThrow();
    }

    private Payment savedPayment() {
        return paymentRepository.findById(payment.getId()).orElseThrow();
    }
}
