package com.bookingapp.domain.service.booking;

import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.repository.BookingRepository;
import com.bookingapp.service.BookingExpirationService;
import com.bookingapp.service.dto.BookingExpirationResult;
import com.bookingapp.infrastructure.kafka.KafkaEventPublisher;
import com.bookingapp.infrastructure.telegram.TelegramNotificationService;
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
    private BookingRepository bookingRepository;

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    @Mock
    private TelegramNotificationService telegramNotificationService;

    @InjectMocks
    private BookingExpirationService bookingExpirationService;

    @Test
    void expireBookingsShouldReturnEmptyResultAndNotifyWhenNothingExpires() {
        LocalDate businessDate = LocalDate.of(2026, 4, 1);
        when(bookingRepository.findBookingsToExpire(businessDate)).thenReturn(List.of());

        BookingExpirationResult result = bookingExpirationService.expireBookings(businessDate);

        assertThat(result.expiredCount()).isZero();
        assertThat(result.expiredBookingIds()).isEmpty();
        verify(telegramNotificationService).notifyNoExpiredBookingsToday();
        verify(kafkaEventPublisher, never()).publishBookingExpired(any(Booking.class));
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
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingExpirationResult result = bookingExpirationService.expireBookings(businessDate);

        assertThat(result.expiredCount()).isEqualTo(2);
        assertThat(result.expiredBookingIds()).containsExactly(10L, 11L);
        verify(bookingRepository, times(2)).save(any(Booking.class));
        verify(kafkaEventPublisher, times(2)).publishBookingExpired(any(Booking.class));
        verify(telegramNotificationService, never()).notifyNoExpiredBookingsToday();
    }
}
