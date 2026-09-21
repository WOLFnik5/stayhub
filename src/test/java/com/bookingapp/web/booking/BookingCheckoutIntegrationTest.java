package com.bookingapp.web.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.domain.model.User;
import com.bookingapp.domain.model.enums.AccommodationType;
import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.domain.model.enums.PaymentStatus;
import com.bookingapp.web.dto.CreatePaymentRequest;
import com.bookingapp.web.dto.PaymentSessionResult;
import com.bookingapp.web.dto.UpdateBookingRequest;
import com.bookingapp.web.support.AbstractControllerIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

class BookingCheckoutIntegrationTest extends AbstractControllerIntegrationTest {

    @ParameterizedTest
    @CsvSource({"PUT,PENDING", "PATCH,PENDING", "PUT,PAID", "PATCH,PAID",
            "PUT,EXPIRED", "PATCH,EXPIRED"})
    void shouldPreserveDatesAndAmountAfterCheckout(String method, PaymentStatus status)
            throws Exception {
        User owner = persistCustomer("checkout-dates@example.com");
        User admin = persistAdmin("checkout-dates-admin@example.com");
        Booking booking = createBooking(owner);
        Payment payment = persistPayment(status, booking.getId(),
                "https://checkout.example/fixed", "sess_fixed", BigDecimal.valueOf(100));

        for (User actor : List.of(owner, admin)) {
            assertThat(updateDates(method, booking, authorizationHeader(actor))).isEqualTo(400);
        }

        Booking saved = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(saved.getCheckInDate()).isEqualTo(booking.getCheckInDate());
        assertThat(saved.getCheckOutDate()).isEqualTo(booking.getCheckOutDate());
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getAmountToPay())
                .isEqualByComparingTo("100");
    }

    @ParameterizedTest
    @CsvSource({"PUT,true", "PATCH,true", "PUT,false", "PATCH,false"})
    void shouldSerializeCheckoutAndDateChange(String method, boolean checkoutFirst)
            throws Exception {
        User owner = persistCustomer("checkout-race@example.com");
        Booking booking = createBooking(owner);
        String token = authorizationHeader(owner);
        CountDownLatch firstFinished = new CountDownLatch(1);
        CountDownLatch commitFirst = new CountDownLatch(1);

        when(stripePaymentProvider.createPaymentSession(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    if (checkoutFirst) {
                        firstFinished.countDown();
                        assertThat(commitFirst.await(10, TimeUnit.SECONDS)).isTrue();
                    }
                    Payment payment = invocation.getArgument(0);
                    return new PaymentSessionResult(
                            "sess_race",
                            "https://checkout.example/race",
                            null,
                            "PENDING",
                            booking.getId(),
                            payment.getAmountToPay()
                    );
                });

        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                if (checkoutFirst) {
                    return checkout(booking, token);
                }

                return transactionTemplate.execute(tx -> {
                    try {
                        int status = updateDates(method, booking, token);
                        firstFinished.countDown();
                        assertThat(commitFirst.await(10, TimeUnit.SECONDS)).isTrue();
                        return status;
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                });
            });

            assertThat(firstFinished.await(10, TimeUnit.SECONDS)).isTrue();

            var second = executor.submit(() -> checkoutFirst
                    ? updateDates(method, booking, token)
                    : checkout(booking, token));

            if (checkoutFirst) {
                // The checkout attempt is committed before the external Stripe call.
                // The date update therefore must be rejected by the persisted payment,
                // rather than waiting for a PostgreSQL booking row lock.
                assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(400);

                commitFirst.countDown();

                assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            } else {
                // The date-update transaction still owns the booking row lock.
                // Checkout must wait until that transaction commits.
                awaitBookingLockWait();
                assertThat(second.isDone()).isFalse();

                commitFirst.countDown();

                assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(200);
                assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            commitFirst.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        Booking saved = bookingRepository.findById(booking.getId()).orElseThrow();
        Payment payment = paymentRepository.findByBookingId(booking.getId()).orElseThrow();

        assertThat(saved.getCheckInDate()).isEqualTo(booking.getCheckInDate());
        assertThat(saved.getCheckOutDate()).isEqualTo(checkoutFirst
                ? booking.getCheckOutDate()
                : booking.getCheckInDate().plusDays(10));
        assertThat(payment.getAmountToPay())
                .isEqualByComparingTo(checkoutFirst ? "100" : "1000");
    }

    private Booking createBooking(User owner) {
        var accommodation = persistAccommodation(
                AccommodationType.APARTMENT,
                "Warsaw",
                "Studio",
                List.of("wifi"),
                BigDecimal.valueOf(100),
                1
        );
        return persistBooking(
                futureDate(10),
                futureDate(11),
                accommodation.getId(),
                owner.getId(),
                BookingStatus.PENDING
        );
    }

    private int checkout(Booking booking, String token) throws Exception {
        return mockMvc.perform(post("/payments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(new CreatePaymentRequest(booking.getId()))))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int updateDates(String method, Booking booking, String token) throws Exception {
        return mockMvc.perform(request(
                        HttpMethod.valueOf(method),
                        "/bookings/{id}",
                        booking.getId())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(new UpdateBookingRequest(
                                booking.getCheckInDate(),
                                booking.getCheckInDate().plusDays(10)
                        ))))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private void awaitBookingLockWait() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        while (System.nanoTime() < deadline) {
            Number waiting = (Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*)
                    FROM pg_stat_activity
                    WHERE datname = current_database()
                      AND wait_event_type = 'Lock'
                      AND query LIKE '%bookings%'
                    """).getSingleResult();

            if (waiting.intValue() > 0) {
                return;
            }

            Thread.sleep(20);
        }

        throw new AssertionError("Concurrent request did not wait for the booking lock");
    }
}
