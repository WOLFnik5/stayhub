package com.bookingapp.infrastructure.stripe;

import com.bookingapp.domain.exception.PaymentStateException;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.domain.model.User;
import com.bookingapp.domain.service.dto.PaymentSessionResult;
import com.bookingapp.infrastructure.config.StripeProperties;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
                            .setSuccessUrl(buildSuccessUrl(booking))
                            .setCancelUrl(buildCancelUrl(booking))
                            .putMetadata("bookingId", String.valueOf(booking.getId()))
                            .putMetadata("userId", String.valueOf(user.getId()))
                            .addLineItem(buildLineItem(payment, accommodation, booking))
                            .build()
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
        return "open".equalsIgnoreCase(status) || "complete".equalsIgnoreCase(status);
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
                                .setCurrency(stripeProperties.getCurrency())
                                .setUnitAmount(toMinorUnits(payment.getAmountToPay()))
                                .setProductData(
                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                .setName("Booking #" + booking.getId())
                                                .setDescription(
                                                        accommodation.getType()
                                                                + " in "
                                                                + accommodation.getLocation()
                                                )
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
