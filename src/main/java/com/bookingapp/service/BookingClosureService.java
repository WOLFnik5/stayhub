package com.bookingapp.service;

import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.domain.model.enums.PaymentStatus;
import com.bookingapp.exception.EntityNotFoundDomainException;
import com.bookingapp.exception.InvalidBookingStateException;
import com.bookingapp.exception.PaymentStateException;
import com.bookingapp.infrastructure.kafka.KafkaEventPublisher;
import com.bookingapp.persistence.BookingRepositoryImpl;
import com.bookingapp.persistence.PaymentRepositoryImpl;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class BookingClosureService {
    private final BookingRepositoryImpl bookings;
    private final PaymentRepositoryImpl payments;
    private final PaymentService paymentService;
    private final KafkaEventPublisher events;
    private final TransactionTemplate transactions;

    public BookingClosureService(BookingRepositoryImpl bookings, PaymentRepositoryImpl payments,
            PaymentService paymentService, KafkaEventPublisher events,
            TransactionTemplate transactions) {
        this.bookings = bookings;
        this.payments = payments;
        this.paymentService = paymentService;
        this.events = events;
        this.transactions = transactions;
    }

    public Booking cancel(Long id) {
        return close(id, true, null);
    }

    public Optional<Long> expire(Long id, LocalDate date) {
        Booking closed = close(id, false, date);
        return closed == null ? Optional.empty() : Optional.of(closed.getId());
    }

    public List<Booking> pendingClosures(long afterId) {
        return bookings.findPendingClosures(afterId, 100);
    }

    public void resume(Booking snapshot) {
        close(snapshot.getId(), snapshot.getStatus() == BookingStatus.CANCELING, LocalDate.now());
    }

    private Booking close(Long id, boolean cancellation, LocalDate date) {
        Booking prepared = transactions.execute(status -> prepare(id, cancellation, date));
        if (prepared == null) {
            return null;
        }
        BookingStatus terminal = cancellation ? BookingStatus.CANCELED : BookingStatus.EXPIRED;
        if (prepared.getStatus() == terminal) {
            return prepared;
        }
        try {
            // Durable intent blocks new checkout/date changes; remote I/O holds no DB lock.
            paymentService.closeCheckoutForBooking(prepared, cancellation);
        } catch (RuntimeException exception) {
            if (cancellation) {
                transactions.executeWithoutResult(status -> restorePaidBooking(id));
            }
            throw exception;
        }
        return transactions.execute(status -> finish(id, cancellation));
    }

    private Booking prepare(Long id, boolean cancellation, LocalDate date) {
        Booking booking = lock(id);
        if (cancellation) {
            if (booking.getStatus() == BookingStatus.CANCELED) {
                return booking;
            }
            if (booking.getStatus() == BookingStatus.EXPIRED
                    || booking.getStatus() == BookingStatus.EXPIRING) {
                throw new InvalidBookingStateException("Expired or expiring booking cannot cancel");
            }
            if (hasPaidPayment(id) && booking.getStatus() != BookingStatus.CANCELING) {
                throw new PaymentStateException(
                        "Paid booking requires a refund before cancellation");
            }
        } else if (booking.getCheckOutDate().isAfter(date)
                || booking.getStatus() == BookingStatus.CANCELED
                || booking.getStatus() == BookingStatus.EXPIRED
                || booking.getStatus() == BookingStatus.CANCELING) {
            return null;
        }
        BookingStatus intent = cancellation ? BookingStatus.CANCELING : BookingStatus.EXPIRING;
        if (booking.getStatus() != intent) {
            booking.setStatus(intent);
            return bookings.save(booking);
        }
        return booking;
    }

    private Booking finish(Long id, boolean cancellation) {
        Booking booking = lock(id);
        BookingStatus terminal = cancellation ? BookingStatus.CANCELED : BookingStatus.EXPIRED;
        if (booking.getStatus() == terminal) {
            return booking;
        }
        BookingStatus intent = cancellation ? BookingStatus.CANCELING : BookingStatus.EXPIRING;
        if (booking.getStatus() != intent || cancellation && hasPaidPayment(id)) {
            throw new PaymentStateException(
                    "Booking state changed; payment requires reconciliation");
        }
        if (payments.findAllByBookingId(id).stream().anyMatch(payment ->
                payment.getStatus() != PaymentStatus.PAID
                        && payment.getStatus() != PaymentStatus.EXPIRED)) {
            throw new PaymentStateException("Checkout closure has not been verified");
        }
        booking.setStatus(terminal);
        Booking saved = bookings.save(booking);
        if (cancellation) {
            events.publishBookingCanceled(saved);
        } else {
            events.publishBookingExpired(saved);
        }
        return saved;
    }

    private void restorePaidBooking(Long id) {
        Booking booking = lock(id);
        if (booking.getStatus() == BookingStatus.CANCELING && hasPaidPayment(id)) {
            booking.setStatus(BookingStatus.CONFIRMED);
            bookings.save(booking);
        }
    }

    private boolean hasPaidPayment(Long id) {
        return payments.findAllByBookingId(id).stream()
                .anyMatch(payment -> payment.getStatus() == PaymentStatus.PAID);
    }

    private Booking lock(Long id) {
        return bookings.findByIdForUpdate(id).orElseThrow(() ->
                new EntityNotFoundDomainException("Booking was not found"));
    }
}
