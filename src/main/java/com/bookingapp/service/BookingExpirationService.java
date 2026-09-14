package com.bookingapp.service;

import com.bookingapp.domain.model.Booking;
import com.bookingapp.persistence.BookingRepositoryImpl;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BookingExpirationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookingExpirationService.class);

    private final BookingRepositoryImpl bookingRepository;
    private final BookingExpirationTransactionService transactionService;

    public BookingExpirationService(
            BookingRepositoryImpl bookingRepository,
            BookingExpirationTransactionService transactionService
    ) {
        this.bookingRepository = bookingRepository;
        this.transactionService = transactionService;
    }

    public BookingExpirationResult expireBookings(LocalDate businessDate) {
        LocalDate effectiveDate = businessDate == null ? LocalDate.now() : businessDate;
        List<Booking> bookingsToExpire = bookingRepository.findBookingsToExpire(effectiveDate);

        if (bookingsToExpire.isEmpty()) {
            return new BookingExpirationResult(0, List.of());
        }

        List<Long> expiredBookingIds = new ArrayList<>();
        List<Long> failedBookingIds = new ArrayList<>();
        for (Booking booking : bookingsToExpire) {
            try {
                transactionService.expireIfEligible(booking.getId(), effectiveDate)
                        .ifPresent(expiredBookingIds::add);
            } catch (RuntimeException exception) {
                failedBookingIds.add(booking.getId());
                LOGGER.error("Failed to expire booking {}", booking.getId(), exception);
            }
        }

        return new BookingExpirationResult(
                expiredBookingIds.size(), List.copyOf(expiredBookingIds),
                List.copyOf(failedBookingIds)
        );
    }
}
