package com.bookingapp.web.payment;

import com.bookingapp.domain.repository.PaymentFilterQuery;
import com.bookingapp.web.dto.CreatePaymentRequest;
import com.bookingapp.web.support.AbstractControllerIntegrationTest;
import com.bookingapp.web.dto.PaymentSessionResult;
import com.bookingapp.domain.model.enums.AccommodationType;
import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.domain.model.enums.PaymentStatus;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.domain.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentControllerIntegrationTest extends AbstractControllerIntegrationTest {

    @Test
    void getPayments_shouldReturn401WhenAnonymous() throws Exception {
        mockMvc.perform(get("/payments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/payments"));
    }

    @Test
    void getPayments_shouldReturnPersistedDataForCurrentUser() throws Exception {
        User currentUser = persistCustomer("payment-list-current@example.com");
        User otherUser = persistCustomer("payment-list-other@example.com");
        Accommodation accommodation = persistAccommodation(
                AccommodationType.CONDO,
                "Warsaw",
                "Premium condo",
                List.of("wifi"),
                BigDecimal.valueOf(180),
                2
        );
        Booking currentUserBooking = persistBooking(
                futureDate(8),
                futureDate(10),
                accommodation.getId(),
                currentUser.getId(),
                BookingStatus.PENDING
        );
        Booking otherBooking = persistBooking(
                futureDate(12),
                futureDate(14),
                accommodation.getId(),
                otherUser.getId(),
                BookingStatus.PENDING
        );
        Payment currentUserPayment = persistPayment(
                PaymentStatus.PENDING,
                currentUserBooking.getId(),
                "https://checkout.example/current",
                "sess_current",
                BigDecimal.valueOf(360)
        );
        persistPayment(
                PaymentStatus.PAID,
                otherBooking.getId(),
                "https://checkout.example/other",
                "sess_other",
                BigDecimal.valueOf(360)
        );

        mockMvc.perform(get("/payments")
                        .header("Authorization", authorizationHeader(currentUser)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(currentUserPayment.getId()))
                .andExpect(jsonPath("$[0].bookingId").value(currentUserBooking.getId()))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].sessionId").value("sess_current"))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void getPayments_shouldReturnAllPaymentsForAdminWhenUserFilterIsMissing() throws Exception {
        User admin = persistAdmin("payment-list-admin@example.com");
        User firstCustomer = persistCustomer("payment-list-admin-first@example.com");
        User secondCustomer = persistCustomer("payment-list-admin-second@example.com");
        Accommodation accommodation = persistAccommodation(
                AccommodationType.CONDO,
                "Warsaw",
                "Premium condo",
                List.of("wifi"),
                BigDecimal.valueOf(180),
                2
        );
        Booking firstBooking = persistBooking(
                futureDate(8),
                futureDate(10),
                accommodation.getId(),
                firstCustomer.getId(),
                BookingStatus.PENDING
        );
        Booking secondBooking = persistBooking(
                futureDate(12),
                futureDate(14),
                accommodation.getId(),
                secondCustomer.getId(),
                BookingStatus.PENDING
        );
        Payment firstPayment = persistPayment(
                PaymentStatus.PENDING,
                firstBooking.getId(),
                "https://checkout.example/admin-first",
                "sess_admin_first",
                BigDecimal.valueOf(360)
        );
        Payment secondPayment = persistPayment(
                PaymentStatus.PAID,
                secondBooking.getId(),
                "https://checkout.example/admin-second",
                "sess_admin_second",
                BigDecimal.valueOf(360)
        );

        mockMvc.perform(get("/payments")
                        .header("Authorization", authorizationHeader(admin)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[1].id").isNumber());

        List<Payment> payments = paymentRepository.findAllByFilter(new PaymentFilterQuery(null));
        assertThat(payments).extracting(Payment::getId)
                .containsExactlyInAnyOrder(firstPayment.getId(), secondPayment.getId());
    }

    @Test
    void getPayments_shouldApplyUserFilterForAdmin() throws Exception {
        User admin = persistAdmin("payment-list-filter-admin@example.com");
        User currentUser = persistCustomer("payment-list-filter-current@example.com");
        User otherUser = persistCustomer("payment-list-filter-other@example.com");
        Accommodation accommodation = persistAccommodation(
                AccommodationType.CONDO,
                "Warsaw",
                "Premium condo",
                List.of("wifi"),
                BigDecimal.valueOf(180),
                2
        );
        Booking currentUserBooking = persistBooking(
                futureDate(8),
                futureDate(10),
                accommodation.getId(),
                currentUser.getId(),
                BookingStatus.PENDING
        );
        Booking otherBooking = persistBooking(
                futureDate(12),
                futureDate(14),
                accommodation.getId(),
                otherUser.getId(),
                BookingStatus.PENDING
        );
        Payment currentUserPayment = persistPayment(
                PaymentStatus.PENDING,
                currentUserBooking.getId(),
                "https://checkout.example/current",
                "sess_current",
                BigDecimal.valueOf(360)
        );
        persistPayment(
                PaymentStatus.PAID,
                otherBooking.getId(),
                "https://checkout.example/other",
                "sess_other",
                BigDecimal.valueOf(360)
        );

        mockMvc.perform(get("/payments")
                        .header("Authorization", authorizationHeader(admin))
                        .param("user_id", currentUser.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(currentUserPayment.getId()))
                .andExpect(jsonPath("$[0].bookingId").value(currentUserBooking.getId()))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void createPayment_shouldPersistSessionForBookingOwner() throws Exception {
        User customer = persistCustomer("payment-create@example.com");
        Accommodation accommodation = persistAccommodation(
                AccommodationType.APARTMENT,
                "Krakow",
                "Old town flat",
                List.of("wifi", "kitchen"),
                BigDecimal.valueOf(200),
                1
        );
        Booking booking = persistBooking(
                futureDate(10),
                futureDate(13),
                accommodation.getId(),
                customer.getId(),
                BookingStatus.PENDING
        );

        when(stripePaymentProvider.createPaymentSession(any(), any(), any(), any()))
                .thenReturn(new PaymentSessionResult(
                        "sess_created",
                        "https://checkout.example/sess_created",
                        null,
                        PaymentStatus.PENDING.name(),
                        booking.getId(),
                        BigDecimal.valueOf(600)
                ));

        mockMvc.perform(post("/payments")
                        .header("Authorization", authorizationHeader(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(new CreatePaymentRequest(booking.getId()))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.bookingId").value(booking.getId()))
                .andExpect(jsonPath("$.sessionId").value("sess_created"))
                .andExpect(jsonPath("$.sessionUrl").value("https://checkout.example/sess_created"))
                .andExpect(jsonPath("$.amountToPay").value(600));

        assertThat(countEntities("PaymentEntity")).isEqualTo(1);
        Payment savedPayment = paymentRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(savedPayment.getSessionId()).isEqualTo("sess_created");
        assertThat(savedPayment.getAmountToPay()).isEqualByComparingTo("600");
    }

    @Test
    void createPayment_shouldReturn403ForDifferentCustomer() throws Exception {
        User owner = persistCustomer("payment-owner@example.com");
        User intruder = persistCustomer("payment-intruder@example.com");
        Accommodation accommodation = persistAccommodation(
                AccommodationType.HOUSE,
                "Gdansk",
                "Beach house",
                List.of("garden"),
                BigDecimal.valueOf(300),
                1
        );
        Booking booking = persistBooking(
                futureDate(7),
                futureDate(9),
                accommodation.getId(),
                owner.getId(),
                BookingStatus.PENDING
        );

        mockMvc.perform(post("/payments")
                        .header("Authorization", authorizationHeader(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(new CreatePaymentRequest(booking.getId()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.path").value("/payments"));

        assertThat(countEntities("PaymentEntity")).isZero();
    }

    @Test
    void createPayment_shouldReturn400ForValidationErrors() throws Exception {
        User customer = persistCustomer("payment-validation@example.com");

        mockMvc.perform(post("/payments")
                        .header("Authorization", authorizationHeader(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bookingId": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/payments"));
    }

    @Test
    void handlePaymentSuccess_shouldBePublicAndPersistPaidStatus() throws Exception {
        User customer = persistCustomer("payment-success@example.com");
        Accommodation accommodation = persistAccommodation(
                AccommodationType.APARTMENT,
                "Poznan",
                "Modern apartment",
                List.of("wifi"),
                BigDecimal.valueOf(150),
                1
        );
        Booking booking = persistBooking(
                futureDate(6),
                futureDate(8),
                accommodation.getId(),
                customer.getId(),
                BookingStatus.PENDING
        );
        Payment payment = persistPayment(
                PaymentStatus.PENDING,
                booking.getId(),
                "https://checkout.example/sess_paid",
                "sess_paid",
                BigDecimal.valueOf(300)
        );
        when(stripePaymentProvider.isPaymentSuccessful("sess_paid")).thenReturn(true);

        mockMvc.perform(get("/payments/success")
                        .param("session_id", "sess_paid"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Payment completed successfully."))
                .andExpect(jsonPath("$.payment.id").value(payment.getId()))
                .andExpect(jsonPath("$.payment.status").value("PAID"))
                .andExpect(jsonPath("$.payment.sessionId").value("sess_paid"));

        Payment paidPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(paidPayment.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void handlePaymentCancel_shouldBePublicAndExpireInactiveSession() throws Exception {
        User customer = persistCustomer("payment-cancel@example.com");
        Accommodation accommodation = persistAccommodation(
                AccommodationType.HOUSE,
                "Lublin",
                "Family house",
                List.of("parking"),
                BigDecimal.valueOf(175),
                1
        );
        Booking booking = persistBooking(
                futureDate(9),
                futureDate(11),
                accommodation.getId(),
                customer.getId(),
                BookingStatus.PENDING
        );
        Payment payment = persistPayment(
                PaymentStatus.PENDING,
                booking.getId(),
                "https://checkout.example/sess_cancel",
                "sess_cancel",
                BigDecimal.valueOf(350)
        );
        when(stripePaymentProvider.isPaymentSessionActive("sess_cancel")).thenReturn(false);

        mockMvc.perform(get("/payments/cancel")
                        .param("session_id", "sess_cancel"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.paymentId").value(payment.getId()))
                .andExpect(jsonPath("$.paymentStatus").value("EXPIRED"))
                .andExpect(jsonPath("$.sessionId").value("sess_cancel"))
                .andExpect(jsonPath("$.canBeCompletedLater").value(false));

        Payment expiredPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(expiredPayment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test
    void handlePaymentCancel_shouldUseBookingIdWhenSessionIdIsNotProvided() throws Exception {
        User customer = persistCustomer("payment-cancel-booking-id@example.com");
        Accommodation accommodation = persistAccommodation(
                AccommodationType.HOUSE,
                "Lublin",
                "Family house",
                List.of("parking"),
                BigDecimal.valueOf(175),
                1
        );
        Booking booking = persistBooking(
                futureDate(9),
                futureDate(11),
                accommodation.getId(),
                customer.getId(),
                BookingStatus.PENDING
        );
        Payment payment = persistPayment(
                PaymentStatus.PENDING,
                booking.getId(),
                "https://checkout.example/sess_cancel_later",
                "sess_cancel_later",
                BigDecimal.valueOf(350)
        );
        when(stripePaymentProvider.isPaymentSessionActive("sess_cancel_later")).thenReturn(true);

        mockMvc.perform(get("/payments/cancel")
                        .param("booking_id", booking.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.paymentId").value(payment.getId()))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.sessionId").value("sess_cancel_later"))
                .andExpect(jsonPath("$.canBeCompletedLater").value(true))
                .andExpect(jsonPath("$.message").value(
                        "Payment was canceled on the provider page. You can pay later using the same session for a limited time."
                ));
    }

    @Test
    void handlePaymentSuccess_shouldReturn404WhenSessionNotFound() throws Exception {
        mockMvc.perform(get("/payments/success")
                        .param("session_id", "missing-session"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.path").value("/payments/success"));
    }
}
