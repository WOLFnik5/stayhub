package com.bookingapp.infrastructure.stripe;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.domain.model.User;
import com.bookingapp.exception.PaymentStateException;
import com.bookingapp.infrastructure.config.StripeProperties;
import com.bookingapp.web.dto.PaymentSessionResult;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class StripePaymentProvider {

    private static final String CHECKOUT_SESSION_ID_PLACEHOLDER = "{CHECKOUT_SESSION_ID}";

    private final StripeClient stripeClient;
    private final StripeProperties stripeProperties;

    public StripePaymentProvider(
            StripeClient stripeClient,
            StripeProperties stripeProperties
    ) {
        this.stripeClient = stripeClient;
        this.stripeProperties = stripeProperties;
    }

    public PaymentSessionResult createPaymentSession(
            Payment payment,
            Booking booking,
            Accommodation accommodation,
            User user
    ) {
        try {
            Session session = stripeClient.checkout().sessions().create(
                    SessionCreateParams.builder()
                            .setMode(SessionCreateParams.Mode.PAYMENT)
                            .setExpiresAt(checkoutDeadline(payment, booking))
                            .setSuccessUrl(buildSuccessUrl(booking))
                            .setCancelUrl(buildCancelUrl(booking))
                            .putMetadata("bookingId", String.valueOf(booking.getId()))
                            .putMetadata("paymentId", String.valueOf(payment.getId()))
                            .putMetadata("userId", String.valueOf(user.getId()))
                            .addLineItem(buildLineItem(payment, accommodation, booking))
                            .build(),
                    RequestOptions.builder()
                            .setIdempotencyKey("booking-payment-" + payment.getId()).build()
            );

            return new PaymentSessionResult(
                    session.getId(),
                    session.getUrl(),
                    payment.getId(),
                    payment.getStatus().name(),
                    payment.getBookingId(),
                    payment.getAmountToPay()
            );
        } catch (StripeException exception) {
            throw new PaymentStateException("Failed to create Stripe checkout session");
        }
    }

    public boolean isPaymentSuccessful(String sessionId) {
        Session session = retrieveSession(sessionId);
        return "paid".equalsIgnoreCase(session.getPaymentStatus());
    }

    public boolean isPaymentSessionActive(String sessionId) {
        Session session = retrieveSession(sessionId);
        String status = session.getStatus();
        return "open".equalsIgnoreCase(status);
    }

    private long checkoutDeadline(Payment payment, Booking booking) {
        Instant bookingDeadline = booking.getCheckOutDate()
                .atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant attemptDeadline = payment.getCreatedAt().plus(23, ChronoUnit.HOURS);
        Instant deadline = bookingDeadline.isBefore(attemptDeadline)
                ? bookingDeadline : attemptDeadline;
        if (deadline.isBefore(Instant.now().plus(30, ChronoUnit.MINUTES))) {
            throw new PaymentStateException("Too late to create or recover this checkout session");
        }
        return deadline.getEpochSecond();
    }

    public boolean isPaymentSessionExpired(String sessionId) {
        return "expired".equalsIgnoreCase(retrieveSession(sessionId).getStatus());
    }

    public void validatePayment(Payment payment) {
        Session session = retrieveSession(payment.getSessionId());
        String currency = payment.getCurrency() == null
                ? stripeProperties.getCurrency() : payment.getCurrency();
        if (!Long.valueOf(toMinorUnits(payment.getAmountToPay())).equals(session.getAmountTotal())
                || !currency.equalsIgnoreCase(session.getCurrency())
                || session.getMetadata() == null
                || !String.valueOf(payment.getBookingId())
                        .equals(session.getMetadata().get("bookingId"))
                || (session.getMetadata().containsKey("paymentId")
                        && !String.valueOf(payment.getId())
                                .equals(session.getMetadata().get("paymentId")))) {
            throw new PaymentStateException("Stripe payment does not match the stored payment");
        }
    }

    public boolean expireUnpaidSession(String sessionId) {
        Session session = retrieveSession(sessionId);
        if ("paid".equalsIgnoreCase(session.getPaymentStatus())) {
            return false;
        }
        if ("expired".equalsIgnoreCase(session.getStatus())) {
            return true;
        }
        if (!"open".equalsIgnoreCase(session.getStatus())) {
            throw new PaymentStateException("Checkout payment is processing; retry later");
        }
        try {
            Session expired = stripeClient.checkout().sessions().expire(sessionId);
            if (!"expired".equalsIgnoreCase(expired.getStatus())) {
                throw new PaymentStateException("Stripe checkout has not expired");
            }
            return true;
        } catch (StripeException exception) {
            throw new PaymentStateException("Unable to close Stripe checkout; retry later");
        }
    }

    private SessionCreateParams.LineItem buildLineItem(
            Payment payment,
            Accommodation accommodation,
            Booking booking
    ) {
        return SessionCreateParams.LineItem.builder()
                .setQuantity(1L)
                .setPriceData(
                        SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(payment.getCurrency() == null
                                        ? stripeProperties.getCurrency() : payment.getCurrency())
                                .setUnitAmount(toMinorUnits(payment.getAmountToPay()))
                                .setProductData(
                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                .setName("Booking #" + booking.getId())
                                                .build()
                                )
                                .build()
                )
                .build();
    }

    private Session retrieveSession(String sessionId) {
        try {
            return stripeClient.checkout().sessions().retrieve(sessionId);
        } catch (StripeException exception) {
            throw new PaymentStateException("Failed to retrieve Stripe checkout session");
        }
    }

    private String buildSuccessUrl(Booking booking) {
        return UriComponentsBuilder.fromUriString(stripeProperties.getSuccessUrl())
                .queryParam("session_id", CHECKOUT_SESSION_ID_PLACEHOLDER)
                .queryParam("booking_id", booking.getId())
                .build(false)
                .toUriString();
    }

    private String buildCancelUrl(Booking booking) {
        return UriComponentsBuilder.fromUriString(stripeProperties.getCancelUrl())
                .queryParam("booking_id", booking.getId())
                .build(false)
                .toUriString();
    }

    private long toMinorUnits(BigDecimal amount) {
        return amount
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();
    }
}
