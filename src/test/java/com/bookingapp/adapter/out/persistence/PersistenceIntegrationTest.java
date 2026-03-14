package com.bookingapp.adapter.out.persistence;

import com.bookingapp.application.model.PaymentFilterQuery;
import com.bookingapp.domain.enums.AccommodationType;
import com.bookingapp.domain.enums.BookingStatus;
import com.bookingapp.domain.enums.PaymentStatus;
import com.bookingapp.domain.enums.UserRole;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.domain.model.User;
import com.bookingapp.support.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PersistenceIntegrationTest extends PostgreSqlIntegrationTestSupport {

    @Autowired
    private UserPersistenceAdapter userPersistenceAdapter;

    @Autowired
    private AccommodationPersistenceAdapter accommodationPersistenceAdapter;

    @Autowired
    private BookingPersistenceAdapter bookingPersistenceAdapter;

    @Autowired
    private PaymentPersistenceAdapter paymentPersistenceAdapter;

    @Test
    void shouldSaveAndReadUser() {
        User savedUser = userPersistenceAdapter.save(
                User.createNew(
                        "guest@example.com",
                        "Jane",
                        "Guest",
                        "encoded-password",
                        UserRole.CUSTOMER
                )
        );

        assertThat(savedUser.getId()).isNotNull();
        assertThat(userPersistenceAdapter.findByEmail("guest@example.com"))
                .isPresent()
                .get()
                .extracting(User::getFirstName, User::getLastName, User::getRole)
                .containsExactly("Jane", "Guest", UserRole.CUSTOMER);
    }

    @Test
    void shouldSaveAccommodationWithAmenities() {
        Accommodation savedAccommodation = accommodationPersistenceAdapter.save(
                Accommodation.createNew(
                        AccommodationType.APARTMENT,
                        "Krakow",
                        "45m2",
                        List.of("wifi", "kitchen"),
                        BigDecimal.valueOf(250),
                        3
                )
        );

        assertThat(savedAccommodation.getId()).isNotNull();
        assertThat(accommodationPersistenceAdapter.findById(savedAccommodation.getId()))
                .isPresent()
                .get()
                .extracting(
                        Accommodation::getType,
                        Accommodation::getLocation,
                        Accommodation::getAmenities,
                        Accommodation::getDailyRate,
                        Accommodation::getAvailability
                )
                .containsExactly(
                        AccommodationType.APARTMENT,
                        "Krakow",
                        List.of("wifi", "kitchen"),
                        BigDecimal.valueOf(250),
                        3
                );
    }

    @Test
    void shouldDetectActiveBookingOverlapAndIgnoreInactiveOrExcludedBookings() {
        User savedUser = userPersistenceAdapter.save(
                User.createNew("booker@example.com", "Alex", "Booker", "encoded-password", UserRole.CUSTOMER)
        );
        Accommodation savedAccommodation = accommodationPersistenceAdapter.save(
                Accommodation.createNew(
                        AccommodationType.HOUSE,
                        "Gdansk",
                        "3 rooms",
                        List.of("parking"),
                        BigDecimal.valueOf(320),
                        1
                )
        );
        Booking activeBooking = bookingPersistenceAdapter.save(
                Booking.createNew(
                        LocalDate.of(2099, 6, 10),
                        LocalDate.of(2099, 6, 15),
                        savedAccommodation.getId(),
                        savedUser.getId()
                )
        );
        bookingPersistenceAdapter.save(
                new Booking(
                        null,
                        LocalDate.of(2099, 6, 10),
                        LocalDate.of(2099, 6, 15),
                        savedAccommodation.getId(),
                        savedUser.getId(),
                        BookingStatus.CANCELED
                )
        );

        assertThat(bookingPersistenceAdapter.existsActiveBookingOverlap(
                savedAccommodation.getId(),
                LocalDate.of(2099, 6, 12),
                LocalDate.of(2099, 6, 18),
                null
        )).isTrue();

        assertThat(bookingPersistenceAdapter.existsActiveBookingOverlap(
                savedAccommodation.getId(),
                LocalDate.of(2099, 6, 12),
                LocalDate.of(2099, 6, 18),
                activeBooking.getId()
        )).isFalse();
    }

    @Test
    void shouldPersistPaymentFlowAndFilterByUser() {
        User savedUser = userPersistenceAdapter.save(
                User.createNew("payer@example.com", "Pat", "Payer", "encoded-password", UserRole.CUSTOMER)
        );
        Accommodation savedAccommodation = accommodationPersistenceAdapter.save(
                Accommodation.createNew(
                        AccommodationType.CONDO,
                        "Warsaw",
                        "studio",
                        List.of("wifi"),
                        BigDecimal.valueOf(180),
                        1
                )
        );
        Booking savedBooking = bookingPersistenceAdapter.save(
                Booking.createNew(
                        LocalDate.of(2099, 7, 1),
                        LocalDate.of(2099, 7, 4),
                        savedAccommodation.getId(),
                        savedUser.getId()
                )
        );

        Payment savedPayment = paymentPersistenceAdapter.save(
                Payment.createPending(savedBooking.getId(), BigDecimal.valueOf(540))
                        .attachSession("sess_123", "https://checkout.example/sess_123")
                        .markPaid()
        );

        assertThat(paymentPersistenceAdapter.findByBookingId(savedBooking.getId()))
                .isPresent()
                .get()
                .extracting(Payment::getStatus, Payment::getSessionId, Payment::getAmountToPay)
                .containsExactly(PaymentStatus.PAID, "sess_123", BigDecimal.valueOf(540));

        assertThat(paymentPersistenceAdapter.findBySessionId("sess_123"))
                .isPresent()
                .get()
                .extracting(Payment::getId)
                .isEqualTo(savedPayment.getId());

        assertThat(paymentPersistenceAdapter.findAllByFilter(new PaymentFilterQuery(savedUser.getId())))
                .singleElement()
                .extracting(Payment::getBookingId, Payment::getStatus)
                .containsExactly(savedBooking.getId(), PaymentStatus.PAID);
    }
}
