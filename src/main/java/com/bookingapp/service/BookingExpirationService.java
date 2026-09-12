package com.bookingapp.service;

import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.exception.InvalidBookingStateException;
import com.bookingapp.infrastructure.kafka.KafkaEventPublisher;
import com.bookingapp.infrastructure.telegram.TelegramNotificationService;
import com.bookingapp.persistence.BookingRepositoryImpl;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookingExpirationService {

    private final BookingRepositoryImpl bookingRepository;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final TelegramNotificationService telegramNotificationService;
    private final PaymentService paymentService;

    public BookingExpirationService(
            BookingRepositoryImpl bookingRepository,
            KafkaEventPublisher kafkaEventPublisher,
            TelegramNotificationService telegramNotificationService,
            PaymentService paymentService
    ) {
        this.bookingRepository = bookingRepository;
        this.kafkaEventPublisher = kafkaEventPublisher;
        this.telegramNotificationService = telegramNotificationService;
        this.paymentService = paymentService;
    }

    @Transactional
    public BookingExpirationResult expireBookings(LocalDate businessDate) {
        LocalDate effectiveDate = businessDate == null ? LocalDate.now() : businessDate;
        List<Booking> bookingsToExpire = bookingRepository.findBookingsToExpire(effectiveDate);

        if (bookingsToExpire.isEmpty()) {
            telegramNotificationService.notifyNoExpiredBookingsToday();
            return new BookingExpirationResult(0, List.of());
        }

        List<Long> expiredBookingIds = bookingsToExpire.stream()
                .map(booking -> bookingRepository.findByIdForUpdate(booking.getId()))
                .flatMap(java.util.Optional::stream)
                .filter(booking -> !booking.getCheckOutDate().isAfter(effectiveDate))
                .filter(booking -> booking.getStatus() != BookingStatus.CANCELED
                        && booking.getStatus() != BookingStatus.EXPIRED)
                .map(this::expireBooking)
                .map(bookingRepository::save)
                .peek(kafkaEventPublisher::publishBookingExpired)
                .map(Booking::getId)
                .toList();

        return new BookingExpirationResult(expiredBookingIds.size(), expiredBookingIds);
    }

    private Booking expireBooking(Booking booking) {
        paymentService.closeCheckoutForBooking(booking, false);
        if (booking.getStatus() == BookingStatus.CANCELED) {
            throw new InvalidBookingStateException("Canceled booking cannot be expired");
        }
        if (booking.getStatus() == BookingStatus.EXPIRED) {
            throw new InvalidBookingStateException("Booking is already expired");
        }
        booking.setStatus(BookingStatus.EXPIRED);
        return booking;
    }
}
