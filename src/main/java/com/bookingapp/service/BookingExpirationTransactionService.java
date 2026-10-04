package com.bookingapp.service;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class BookingExpirationTransactionService {

    private final BookingClosureService closureService;

    public BookingExpirationTransactionService(
            BookingClosureService closureService
    ) {
        this.closureService = closureService;
    }

    public Optional<Long> expireIfEligible(Long bookingId, LocalDate businessDate) {
        return closureService.expire(bookingId, businessDate);
    }
}
