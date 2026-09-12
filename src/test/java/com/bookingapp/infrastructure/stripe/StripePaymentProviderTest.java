package com.bookingapp.infrastructure.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.domain.model.enums.PaymentStatus;
import com.bookingapp.exception.PaymentStateException;
import com.bookingapp.infrastructure.config.StripeProperties;
import com.stripe.StripeClient;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class StripePaymentProviderTest {
    private StripeClient client;
    private StripePaymentProvider provider;
    private Payment payment;
    private Session session;

    @BeforeEach
    void setUp() throws Exception {
        client = mock(StripeClient.class, RETURNS_DEEP_STUBS);
        StripeProperties properties = new StripeProperties();
        properties.setCurrency("usd");
        properties.setSuccessUrl("https://example.com/payments/success");
        properties.setCancelUrl("https://example.com/payments/cancel/return");
        provider = new StripePaymentProvider(client, properties);
        payment = new Payment(123L, PaymentStatus.PENDING, 17L,
                "https://checkout.example/123", "sess_test", BigDecimal.valueOf(200));
        session = new Session();
        session.setId("sess_test");
        session.setStatus("open");
        session.setPaymentStatus("unpaid");
        session.setUrl(payment.getSessionUrl());
        session.setAmountTotal(20000L);
        session.setCurrency("usd");
        session.setMetadata(Map.of("bookingId", "17", "paymentId", "123"));
        when(client.checkout().sessions().retrieve("sess_test")).thenReturn(session);
    }

    @Test
    void checkoutRetriesShouldUseSameKeyAndFixedExpiry() throws Exception {
        when(client.checkout().sessions().create(any(SessionCreateParams.class), any(RequestOptions.class)))
                .thenReturn(session);
        Booking booking = new Booking(17L, LocalDate.now().plusDays(2), LocalDate.now().plusDays(4),
                1L, 5L, BookingStatus.PENDING);
        var user = new com.bookingapp.domain.model.User();
        user.setId(5L);
        provider.createPaymentSession(payment, booking, null, user);
        provider.createPaymentSession(payment, booking, null, user);

        var params = ArgumentCaptor.forClass(SessionCreateParams.class);
        var options = ArgumentCaptor.forClass(RequestOptions.class);
        verify(client.checkout().sessions(), times(2)).create(params.capture(), options.capture());
        assertThat(options.getAllValues()).extracting(RequestOptions::getIdempotencyKey)
                .containsExactly("booking-payment-123", "booking-payment-123");
        assertThat(params.getAllValues()).extracting(SessionCreateParams::getExpiresAt)
                .containsOnly(payment.getCreatedAt().plus(23, ChronoUnit.HOURS).getEpochSecond());
        assertThat(params.getValue().getLineItems().getFirst().getPriceData().getUnitAmount())
                .isEqualTo(20000L);
        assertThat(params.getValue().getMetadata()).containsEntry("paymentId", "123");
        assertThat(params.getValue().getSuccessUrl()).contains("{CHECKOUT_SESSION_ID}");
    }

    @Test
    void expiryMustNotOutliveBooking() throws Exception {
        when(client.checkout().sessions().create(any(SessionCreateParams.class), any(RequestOptions.class)))
                .thenReturn(session);
        // Move creation into the future only for deterministic deadline comparison.
        payment.setCreatedAt(Instant.now().plus(1, ChronoUnit.DAYS));
        Booking booking = new Booking(17L, LocalDate.now(), LocalDate.now().plusDays(1),
                1L, 5L, BookingStatus.PENDING);
        var user = new com.bookingapp.domain.model.User();
        user.setId(5L);
        if (booking.getCheckOutDate().atStartOfDay(ZoneId.systemDefault()).toInstant()
                .isBefore(Instant.now().plus(30, ChronoUnit.MINUTES))) {
            assertThatThrownBy(() -> provider.createPaymentSession(payment, booking, null, user))
                    .isInstanceOf(PaymentStateException.class);
            return;
        }
        provider.createPaymentSession(payment, booking, null, user);
        var params = ArgumentCaptor.forClass(SessionCreateParams.class);
        verify(client.checkout().sessions()).create(params.capture(), any(RequestOptions.class));
        assertThat(params.getValue().getExpiresAt()).isEqualTo(booking.getCheckOutDate()
                .atStartOfDay(ZoneId.systemDefault()).toEpochSecond());
    }

    @ParameterizedTest
    @ValueSource(strings = {"amount", "currency", "booking", "attempt"})
    void mustRejectPaymentWithWrongFinancialData(String mismatch) {
        switch (mismatch) {
            case "amount" -> session.setAmountTotal(1L);
            case "currency" -> session.setCurrency("eur");
            case "booking" -> session.setMetadata(Map.of("bookingId", "999", "paymentId", "123"));
            case "attempt" -> session.setMetadata(Map.of("bookingId", "17", "paymentId", "999"));
            default -> throw new AssertionError(mismatch);
        }
        assertThatThrownBy(() -> provider.validatePayment(payment))
                .isInstanceOf(PaymentStateException.class);
    }

    @Test
    void shouldValidateOriginalSessionMetadataForLegacyPayment() {
        session.setMetadata(Map.of("bookingId", "17"));
        provider.validatePayment(payment);
    }

    @Test
    void shouldCloseOpenCheckoutAndTreatAlreadyExpiredAsClosed() throws Exception {
        Session expired = new Session();
        expired.setStatus("expired");
        when(client.checkout().sessions().expire("sess_test")).thenReturn(expired);
        assertThat(provider.expireUnpaidSession("sess_test")).isTrue();
        session.setStatus("expired");
        assertThat(provider.expireUnpaidSession("sess_test")).isTrue();
        verify(client.checkout().sessions(), times(1)).expire("sess_test");
    }

    @Test
    void shouldNotClosePaidOrProcessingCheckout() throws Exception {
        session.setPaymentStatus("paid");
        assertThat(provider.expireUnpaidSession("sess_test")).isFalse();
        session.setPaymentStatus("unpaid");
        session.setStatus("complete");
        assertThat(provider.isPaymentSessionActive("sess_test")).isFalse();
        assertThat(provider.isPaymentSessionExpired("sess_test")).isFalse();
        assertThatThrownBy(() -> provider.expireUnpaidSession("sess_test"))
                .isInstanceOf(PaymentStateException.class);
        verify(client.checkout().sessions(), org.mockito.Mockito.never()).expire("sess_test");
    }
}
