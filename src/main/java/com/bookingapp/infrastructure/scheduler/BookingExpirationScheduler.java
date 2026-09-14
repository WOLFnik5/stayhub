package com.bookingapp.infrastructure.scheduler;

import com.bookingapp.infrastructure.telegram.TelegramNotificationService;
import com.bookingapp.service.BookingExpirationResult;
import com.bookingapp.service.BookingExpirationService;
import java.time.LocalDate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BookingExpirationScheduler {

    private final BookingExpirationService bookingExpirationService;
    private final TelegramNotificationService telegramNotificationService;

    public BookingExpirationScheduler(
            BookingExpirationService bookingExpirationService,
            TelegramNotificationService telegramNotificationService
    ) {
        this.bookingExpirationService = bookingExpirationService;
        this.telegramNotificationService = telegramNotificationService;
    }

    @Scheduled(cron = "${app.scheduler.booking-expiration.cron:0 0 1 * * *}")
    public void expireBookingsDaily() {
        BookingExpirationResult result = bookingExpirationService.expireBookings(LocalDate.now());
        if (result.expiredCount() == 0 && result.failedBookingIds().isEmpty()) {
            telegramNotificationService.notifyNoExpiredBookingsToday();
        }
    }
}
