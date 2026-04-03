package com.bookingapp.infrastructure.persistence;

import com.bookingapp.domain.repository.PaymentFilterQuery;
import com.bookingapp.domain.model.enums.AccommodationType;
import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.domain.model.enums.PaymentStatus;
import com.bookingapp.domain.model.enums.UserRole;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.domain.model.User;
import com.bookingapp.domain.repository.AccommodationRepository;
import com.bookingapp.domain.repository.BookingRepository;
import com.bookingapp.domain.repository.PaymentRepository;
import com.bookingapp.domain.repository.UserRepository;
import com.bookingapp.testsupport.PostgreSqlIntegrationTestSupport;
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
    private UserRepository userRepository;

    @Autowired
    private AccommodationRepository accommodationRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void shouldSaveAndReadUser() {
        User savedUser = userRepository.save(
                new User(
                        null,
                        "guest@example.com",
                        "Jane",
                        "Guest",
                        "encoded-password",
                        UserRole.CUSTOMER
                )
        );

        assertThat(savedUser.getId()).isNotNull();
        assertThat(userRepository.findByEmail("guest@example.com"))
                .isPresent()
                .get()
                .extracting(User::getFirstName, User::getLastName, User::getRole)
                .containsExactly("Jane", "Guest", UserRole.CUSTOMER);
    }

    @Test
    void shouldSaveAccommodationWithAmenities() {
        Accommodation savedAccommodation = accommodationRepository.save(
                new Accommodation(
                        null,
                        AccommodationType.APARTMENT,
                        "Krakow",
                        "45m2",
                        List.of("wifi", "kitchen"),
                        BigDecimal.valueOf(250),
                        3
                )
        );

        assertThat(savedAccommodation.getId()).isNotNull();
        assertThat(accommodationRepository.findById(savedAccommodation.getId()))
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
        User savedUser = userRepository.save(
                new User(
                        null,
                        "booker@example.com",
                        "Alex",
                        "Booker",
                        "encoded-password",
                        UserRole.CUSTOMER
                )
        );
        Accommodation savedAccommodation = accommodationRepository.save(
                new Accommodation(
                        null,
                        AccommodationType.HOUSE,
                        "Gdansk",
                        "3 rooms",
                        List.of("parking"),
                        BigDecimal.valueOf(320),
                        1
                )
        );
        Booking activeBooking = bookingRepository.save(
                new Booking(
                        null,
                        LocalDate.of(2099, 6, 10),
                        LocalDate.of(2099, 6, 15),
                        savedAccommodation.getId(),
                        savedUser.getId(),
                        BookingStatus.PENDING
                )
        );
        bookingRepository.save(
                new Booking(
                        null,
                        LocalDate.of(2099, 6, 10),
                        LocalDate.of(2099, 6, 15),
                        savedAccommodation.getId(),
                        savedUser.getId(),
                        BookingStatus.CANCELED
                )
        );

        assertThat(bookingRepository.existsActiveBookingOverlap(
                savedAccommodation.getId(),
                LocalDate.of(2099, 6, 12),
                LocalDate.of(2099, 6, 18),
                null
        )).isTrue();

        assertThat(bookingRepository.existsActiveBookingOverlap(
                savedAccommodation.getId(),
                LocalDate.of(2099, 6, 12),
                LocalDate.of(2099, 6, 18),
                activeBooking.getId()
        )).isFalse();
    }

    @Test
    void shouldPersistPaymentFlowAndFilterByUser() {
        User savedUser = userRepository.save(
                new User(
                        null,
                        "payer@example.com",
                        "Pat",
                        "Payer",
                        "encoded-password",
                        UserRole.CUSTOMER
                )
        );
        Accommodation savedAccommodation = accommodationRepository.save(
                new Accommodation(
                        null,
                        AccommodationType.CONDO,
                        "Warsaw",
                        "studio",
                        List.of("wifi"),
                        BigDecimal.valueOf(180),
                        1
                )
        );
        Booking savedBooking = bookingRepository.save(
                new Booking(
                        null,
                        LocalDate.of(2099, 7, 1),
                        LocalDate.of(2099, 7, 4),
                        savedAccommodation.getId(),
                        savedUser.getId(),
                        BookingStatus.PENDING
                )
        );

        Payment savedPayment = paymentRepository.save(
                new Payment(
                        null,
                        PaymentStatus.PAID,
                        savedBooking.getId(),
                        "https://checkout.example/sess_123",
                        "sess_123",
                        BigDecimal.valueOf(540)
                )
        );

        assertThat(paymentRepository.findByBookingId(savedBooking.getId()))
                .isPresent()
                .get()
                .extracting(Payment::getStatus, Payment::getSessionId, Payment::getAmountToPay)
                .containsExactly(PaymentStatus.PAID, "sess_123", BigDecimal.valueOf(540));

        assertThat(paymentRepository.findBySessionId("sess_123"))
                .isPresent()
                .get()
                .extracting(Payment::getId)
                .isEqualTo(savedPayment.getId());

        assertThat(paymentRepository.findAllByFilter(new PaymentFilterQuery(savedUser.getId())))
                .singleElement()
                .extracting(Payment::getBookingId, Payment::getStatus)
                .containsExactly(savedBooking.getId(), PaymentStatus.PAID);
    }
}
