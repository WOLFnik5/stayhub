package com.bookingapp.infrastructure.scheduler;

import com.bookingapp.domain.model.Booking;
import com.bookingapp.service.BookingClosureService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.scheduler.booking-closure.enabled",
        havingValue = "true", matchIfMissing = true)
public class BookingClosureRecoveryScheduler {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(BookingClosureRecoveryScheduler.class);
    private final BookingClosureService service;
    private long afterId;

    public BookingClosureRecoveryScheduler(BookingClosureService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.booking-closure.delay-ms:300000}",
            initialDelayString = "${app.scheduler.booking-closure.delay-ms:300000}")
    public void resumeClosures() {
        List<Booking> candidates = service.pendingClosures(afterId);
        if (candidates.isEmpty() && afterId != 0) {
            afterId = 0;
            candidates = service.pendingClosures(afterId);
        }
        for (Booking booking : candidates) {
            afterId = booking.getId();
            try {
                service.resume(booking);
            } catch (RuntimeException exception) {
                LOGGER.atWarn().addKeyValue("bookingId", booking.getId())
                        .addKeyValue("failureType", exception.getClass().getSimpleName())
                        .log("Booking closure will be retried");
            }
        }
    }
}
