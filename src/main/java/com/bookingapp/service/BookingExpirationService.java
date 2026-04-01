package com.bookingapp.service;

import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.repository.BookingRepository;
import com.bookingapp.service.dto.BookingExpirationResult;
import com.bookingapp.infrastructure.kafka.KafkaEventPublisher;
import com.bookingapp.infrastructure.telegram.TelegramNotificationService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookingExpirationService {

    private final BookingRepository bookingRepository;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final TelegramNotificationService telegramNotificationService;

    public BookingExpirationService(
            BookingRepository bookingRepository,
            KafkaEventPublisher kafkaEventPublisher,
            TelegramNotificationService telegramNotificationService
    ) {
        this.bookingRepository = bookingRepository;
        this.kafkaEventPublisher = kafkaEventPublisher;
        this.telegramNotificationService = telegramNotificationService;
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
                .map(Booking::expire)
                .map(bookingRepository::save)
                .peek(kafkaEventPublisher::publishBookingExpired)
                .map(Booking::getId)
                .toList();

        return new BookingExpirationResult(expiredBookingIds.size(), expiredBookingIds);
    }
}
