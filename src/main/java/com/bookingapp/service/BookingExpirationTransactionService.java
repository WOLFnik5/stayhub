package com.bookingapp.service;

import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.infrastructure.kafka.KafkaEventPublisher;
import com.bookingapp.persistence.BookingRepositoryImpl;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingExpirationTransactionService {

    private final BookingRepositoryImpl bookingRepository;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final PaymentService paymentService;

    public BookingExpirationTransactionService(
            BookingRepositoryImpl bookingRepository,
            KafkaEventPublisher kafkaEventPublisher,
            PaymentService paymentService
    ) {
        this.bookingRepository = bookingRepository;
        this.kafkaEventPublisher = kafkaEventPublisher;
        this.paymentService = paymentService;
    }

    @Transactional
    public Optional<Long> expireIfEligible(Long bookingId, LocalDate businessDate) {
        Optional<Booking> locked = bookingRepository.findByIdForUpdate(bookingId);
        if (locked.isEmpty()) {
            return Optional.empty();
        }

        Booking booking = locked.get();
        if (booking.getCheckOutDate().isAfter(businessDate)
                || booking.getStatus() == BookingStatus.CANCELED
                || booking.getStatus() == BookingStatus.EXPIRED) {
            return Optional.empty();
        }

        paymentService.closeCheckoutForBooking(booking, false);
        booking.setStatus(BookingStatus.EXPIRED);
        Booking saved = bookingRepository.save(booking);
        kafkaEventPublisher.publishBookingExpired(saved);
        return Optional.of(saved.getId());
    }
}
