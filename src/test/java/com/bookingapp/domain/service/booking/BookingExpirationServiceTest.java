package com.bookingapp.domain.service.booking;

import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.persistence.BookingRepositoryImpl;
import com.bookingapp.service.BookingExpirationService;
import com.bookingapp.service.BookingExpirationResult;
import com.bookingapp.service.BookingExpirationTransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingExpirationServiceTest {

    @Mock
    private BookingRepositoryImpl bookingRepository;

    @Mock
    private BookingExpirationTransactionService transactionService;

    @InjectMocks
    private BookingExpirationService bookingExpirationService;

    @Test
    void expireBookingsShouldReturnEmptyResultAndNotifyWhenNothingExpires() {
        LocalDate businessDate = LocalDate.of(2026, 4, 1);
        when(bookingRepository.findBookingsToExpire(businessDate)).thenReturn(List.of());

        BookingExpirationResult result = bookingExpirationService.expireBookings(businessDate);

        assertThat(result.expiredCount()).isZero();
        assertThat(result.expiredBookingIds()).isEmpty();
        verify(transactionService, never()).expireIfEligible(any(), any());
    }

    @Test
    void expireBookingsShouldSaveExpiredBookingsAndPublishEvents() {
        LocalDate businessDate = LocalDate.of(2026, 4, 1);
        Booking firstBooking = new Booking(
                10L,
                LocalDate.of(2026, 3, 20),
                LocalDate.of(2026, 3, 22),
                5L,
                15L,
                BookingStatus.PENDING
        );
        Booking secondBooking = new Booking(
                11L,
                LocalDate.of(2026, 3, 21),
                LocalDate.of(2026, 3, 23),
                6L,
                16L,
                BookingStatus.CONFIRMED
        );

        when(bookingRepository.findBookingsToExpire(businessDate)).thenReturn(List.of(firstBooking, secondBooking));
        when(transactionService.expireIfEligible(10L, businessDate))
                .thenReturn(java.util.Optional.of(10L));
        when(transactionService.expireIfEligible(11L, businessDate))
                .thenReturn(java.util.Optional.of(11L));

        BookingExpirationResult result = bookingExpirationService.expireBookings(businessDate);

        assertThat(result.expiredCount()).isEqualTo(2);
        assertThat(result.expiredBookingIds()).containsExactly(10L, 11L);
        verify(transactionService, times(2)).expireIfEligible(any(), any());
    }

    @Test
    void expireBookingsShouldSkipBookingRescheduledAfterCandidateSelection() {
        LocalDate date = LocalDate.of(2026, 4, 1);
        Booking stale = new Booking(10L, date.minusDays(3), date, 5L, 15L, BookingStatus.PENDING);
        Booking current = new Booking(10L, date.plusDays(5), date.plusDays(8),
                5L, 15L, BookingStatus.PENDING);
        when(bookingRepository.findBookingsToExpire(date)).thenReturn(List.of(stale));
        when(transactionService.expireIfEligible(10L, date))
                .thenReturn(java.util.Optional.empty());

        BookingExpirationResult result = bookingExpirationService.expireBookings(date);

        assertThat(result.expiredCount()).isZero();
        verify(transactionService).expireIfEligible(10L, date);
    }

    @Test
    void expireBookingsShouldContinueWhenOneBookingFails() {
        LocalDate date = LocalDate.of(2026, 4, 1);
        Booking first = new Booking(10L, date.minusDays(3), date.minusDays(1),
                5L, 15L, BookingStatus.PENDING);
        Booking second = new Booking(11L, date.minusDays(2), date,
                6L, 16L, BookingStatus.CONFIRMED);
        when(bookingRepository.findBookingsToExpire(date)).thenReturn(List.of(first, second));
        when(transactionService.expireIfEligible(10L, date))
                .thenThrow(new IllegalStateException("payment provider unavailable"));
        when(transactionService.expireIfEligible(11L, date))
                .thenReturn(java.util.Optional.of(11L));

        BookingExpirationResult result = bookingExpirationService.expireBookings(date);

        assertThat(result.expiredBookingIds()).containsExactly(11L);
        assertThat(result.failedBookingIds()).containsExactly(10L);
        verify(transactionService, times(2)).expireIfEligible(any(), any());
    }
}
