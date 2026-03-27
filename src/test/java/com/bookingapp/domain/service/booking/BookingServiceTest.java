package com.bookingapp.domain.service.booking;

import com.bookingapp.domain.service.dto.BookingFilterQuery;
import com.bookingapp.domain.service.dto.CurrentUser;
import com.bookingapp.domain.service.BookingService;
import com.bookingapp.infrastructure.kafka.KafkaEventPublisher;
import com.bookingapp.domain.repository.AccommodationRepository;
import com.bookingapp.domain.repository.BookingRepository;
import com.bookingapp.domain.repository.PaymentRepository;
import com.bookingapp.infrastructure.security.CurrentUserService;
import com.bookingapp.domain.enums.AccommodationType;
import com.bookingapp.domain.enums.BookingStatus;
import com.bookingapp.domain.enums.PaymentStatus;
import com.bookingapp.domain.enums.UserRole;
import com.bookingapp.domain.exception.BookingConflictException;
import com.bookingapp.domain.exception.BusinessValidationException;
import com.bookingapp.domain.exception.ForbiddenOperationException;
import com.bookingapp.domain.exception.InvalidBookingStateException;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private AccommodationRepository accommodationRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    @InjectMocks
    private BookingService bookingService;

    @Test
    void createBookingShouldPersistPendingBookingForCurrentUser() {
        CurrentUser currentUser = new CurrentUser(15L, "customer@example.com", UserRole.CUSTOMER);
        Accommodation accommodation = new Accommodation(
                3L,
                AccommodationType.HOUSE,
                "Warsaw",
                "2 rooms",
                List.of("wifi"),
                BigDecimal.valueOf(120),
                2
        );
        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(accommodationRepository.findById(3L)).thenReturn(Optional.of(accommodation));
        when(bookingRepository.existsActiveBookingOverlap(eq(3L), any(LocalDate.class), any(LocalDate.class), eq(null)))
                .thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            return new Booking(
                    101L,
                    booking.getCheckInDate(),
                    booking.getCheckOutDate(),
                    booking.getAccommodationId(),
                    booking.getUserId(),
                    booking.getStatus()
            );
        });

        Booking result = bookingService.createBooking(
                3L,
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(8)
        );

        assertThat(result.getId()).isEqualTo(101L);
        assertThat(result.getAccommodationId()).isEqualTo(3L);
        assertThat(result.getUserId()).isEqualTo(15L);
        assertThat(result.getStatus()).isEqualTo(BookingStatus.PENDING);
        verify(kafkaEventPublisher).publishBookingCreated(result);
    }

    @Test
    void createBookingShouldRejectOverlap() {
        CurrentUser currentUser = new CurrentUser(15L, "customer@example.com", UserRole.CUSTOMER);
        Accommodation accommodation = new Accommodation(
                3L,
                AccommodationType.HOUSE,
                "Warsaw",
                "2 rooms",
                List.of("wifi"),
                BigDecimal.valueOf(120),
                2
        );
        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(accommodationRepository.findById(3L)).thenReturn(Optional.of(accommodation));
        when(bookingRepository.existsActiveBookingOverlap(eq(3L), any(LocalDate.class), any(LocalDate.class), eq(null)))
                .thenReturn(true);

        assertThatThrownBy(() -> bookingService.createBooking(
                3L,
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(8)
        ))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("already booked");

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void cancelBookingShouldMarkBookingAsCanceled() {
        CurrentUser currentUser = new CurrentUser(15L, "customer@example.com", UserRole.CUSTOMER);
        Booking existingBooking = new Booking(
                8L,
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(4),
                3L,
                15L,
                BookingStatus.PENDING
        );

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(bookingRepository.findById(8L)).thenReturn(Optional.of(existingBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.cancelBooking(8L);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELED);
        verify(kafkaEventPublisher).publishBookingCanceled(result);
    }

    @Test
    void cancelBookingShouldRejectSecondCancellation() {
        CurrentUser currentUser = new CurrentUser(15L, "customer@example.com", UserRole.CUSTOMER);
        Booking canceledBooking = new Booking(
                8L,
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(4),
                3L,
                15L,
                BookingStatus.CANCELED
        );

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(bookingRepository.findById(8L)).thenReturn(Optional.of(canceledBooking));

        assertThatThrownBy(() -> bookingService.cancelBooking(8L))
                .isInstanceOf(InvalidBookingStateException.class)
                .hasMessageContaining("already canceled");

        verify(bookingRepository, never()).save(any(Booking.class));
        verify(kafkaEventPublisher, never()).publishBookingCanceled(any(Booking.class));
    }

    @Test
    void getBookingByIdShouldReturnBookingForOwner() {
        CurrentUser currentUser = new CurrentUser(15L, "customer@example.com", UserRole.CUSTOMER);
        Booking booking = new Booking(
                8L,
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(4),
                3L,
                15L,
                BookingStatus.PENDING
        );

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(bookingRepository.findById(8L)).thenReturn(Optional.of(booking));

        Booking result = bookingService.getBookingById(8L);

        assertThat(result).isEqualTo(booking);
    }

    @Test
    void getBookingByIdShouldRejectForeignCustomer() {
        CurrentUser currentUser = new CurrentUser(99L, "intruder@example.com", UserRole.CUSTOMER);
        Booking booking = new Booking(
                8L,
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(4),
                3L,
                15L,
                BookingStatus.PENDING
        );

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(bookingRepository.findById(8L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.getBookingById(8L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void listBookingsShouldUseRepositoryFilterForAdmin() {
        CurrentUser admin = new CurrentUser(1L, "admin@example.com", UserRole.ADMIN);
        BookingFilterQuery query = new BookingFilterQuery(15L, BookingStatus.PENDING);
        List<Booking> expected = List.of(
                new Booking(9L, LocalDate.now().plusDays(3), LocalDate.now().plusDays(5), 3L, 15L, BookingStatus.PENDING)
        );

        when(currentUserService.getCurrentUser()).thenReturn(admin);
        when(bookingRepository.findAllByFilter(query)).thenReturn(expected);

        List<Booking> result = bookingService.listBookings(query);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void listBookingsShouldReturnOnlyCurrentUsersFilteredBookingsForCustomer() {
        CurrentUser currentUser = new CurrentUser(15L, "customer@example.com", UserRole.CUSTOMER);
        Booking pendingBooking = new Booking(
                9L,
                LocalDate.now().plusDays(3),
                LocalDate.now().plusDays(5),
                3L,
                15L,
                BookingStatus.PENDING
        );
        Booking canceledBooking = new Booking(
                10L,
                LocalDate.now().plusDays(6),
                LocalDate.now().plusDays(8),
                3L,
                15L,
                BookingStatus.CANCELED
        );

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(bookingRepository.findAllByUserId(15L)).thenReturn(List.of(pendingBooking, canceledBooking));

        List<Booking> result = bookingService.listBookings(new BookingFilterQuery(null, BookingStatus.PENDING));

        assertThat(result).containsExactly(pendingBooking);
    }

    @Test
    void listMyBookingsShouldReturnCurrentUsersBookings() {
        CurrentUser currentUser = new CurrentUser(15L, "customer@example.com", UserRole.CUSTOMER);
        List<Booking> expected = List.of(
                new Booking(9L, LocalDate.now().plusDays(3), LocalDate.now().plusDays(5), 3L, 15L, BookingStatus.PENDING)
        );

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(bookingRepository.findAllByUserId(15L)).thenReturn(expected);

        List<Booking> result = bookingService.listMyBookings();

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void updateBookingShouldSaveRescheduledBooking() {
        CurrentUser currentUser = new CurrentUser(15L, "customer@example.com", UserRole.CUSTOMER);
        Booking existingBooking = new Booking(
                8L,
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(4),
                3L,
                15L,
                BookingStatus.PENDING
        );

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(bookingRepository.findById(8L)).thenReturn(Optional.of(existingBooking));
        when(paymentRepository.findByBookingId(8L)).thenReturn(Optional.empty());
        when(bookingRepository.existsActiveBookingOverlap(eq(3L), any(LocalDate.class), any(LocalDate.class), eq(8L)))
                .thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.updateBooking(
                8L,
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(7)
        );

        assertThat(result.getCheckInDate()).isEqualTo(LocalDate.now().plusDays(5));
        assertThat(result.getCheckOutDate()).isEqualTo(LocalDate.now().plusDays(7));
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void updateBookingShouldRejectPaidBooking() {
        CurrentUser currentUser = new CurrentUser(15L, "customer@example.com", UserRole.CUSTOMER);
        Booking existingBooking = new Booking(
                8L,
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(4),
                3L,
                15L,
                BookingStatus.PENDING
        );
        Payment paidPayment = new Payment(
                100L,
                PaymentStatus.PAID,
                8L,
                "https://checkout.example/sess_paid",
                "sess_paid",
                BigDecimal.valueOf(450)
        );

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(bookingRepository.findById(8L)).thenReturn(Optional.of(existingBooking));
        when(paymentRepository.findByBookingId(8L)).thenReturn(Optional.of(paidPayment));

        assertThatThrownBy(() -> bookingService.updateBooking(
                8L,
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(7)
        ))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Paid booking cannot be updated");
    }
}
