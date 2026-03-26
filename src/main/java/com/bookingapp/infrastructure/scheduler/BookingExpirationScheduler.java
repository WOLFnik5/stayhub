package com.bookingapp.infrastructure.scheduler;

import com.bookingapp.domain.service.BookingExpirationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class BookingExpirationScheduler {

    private final BookingExpirationService bookingExpirationService;

    public BookingExpirationScheduler(BookingExpirationService bookingExpirationService) {
        this.bookingExpirationService = bookingExpirationService;
    }

    @Scheduled(cron = "${app.scheduler.booking-expiration.cron:0 0 1 * * *}")
    public void expireBookingsDaily() {
        bookingExpirationService.expireBookings(LocalDate.now());
    }
}
