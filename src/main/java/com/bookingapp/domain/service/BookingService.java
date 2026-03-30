package com.bookingapp.domain.service;

import com.bookingapp.domain.enums.BookingStatus;
import com.bookingapp.domain.enums.PaymentStatus;
import com.bookingapp.domain.enums.UserRole;
import com.bookingapp.domain.exception.BookingConflictException;
import com.bookingapp.domain.exception.BusinessValidationException;
import com.bookingapp.domain.exception.EntityNotFoundDomainException;
import com.bookingapp.domain.exception.ForbiddenOperationException;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.domain.repository.AccommodationRepository;
import com.bookingapp.domain.repository.BookingRepository;
import com.bookingapp.domain.repository.PaymentRepository;
import com.bookingapp.domain.service.dto.BookingFilterQuery;
import com.bookingapp.domain.service.dto.CurrentUser;
import com.bookingapp.infrastructure.kafka.KafkaEventPublisher;
import com.bookingapp.infrastructure.security.CurrentUserService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookingService {

    private final BookingRepository bookingRepository;
    private final AccommodationRepository accommodationRepository;
    private final PaymentRepository paymentRepository;
    private final CurrentUserService currentUserService;
    private final KafkaEventPublisher kafkaEventPublisher;

    public BookingService(
            BookingRepository bookingRepository,
            AccommodationRepository accommodationRepository,
            PaymentRepository paymentRepository,
            CurrentUserService currentUserService,
            KafkaEventPublisher kafkaEventPublisher
    ) {
        this.bookingRepository = bookingRepository;
        this.accommodationRepository = accommodationRepository;
        this.paymentRepository = paymentRepository;
        this.currentUserService = currentUserService;
        this.kafkaEventPublisher = kafkaEventPublisher;
    }

    @Transactional
    public Booking createBooking(
            Long accommodationId,
            LocalDate checkInDate,
            LocalDate checkOutDate
    ) {
        CurrentUser currentUser = currentUserService.getCurrentUser();
        Accommodation accommodation = getAccommodation(accommodationId);
        ensureAccommodationHasAvailability(accommodation);
        ensureNoOverlap(accommodationId, checkInDate, checkOutDate, null);

        Booking bookingToSave = Booking.createNew(
                checkInDate,
                checkOutDate,
                accommodation.getId(),
                currentUser.id()
        );

        Booking savedBooking = bookingRepository.save(bookingToSave);
        kafkaEventPublisher.publishBookingCreated(savedBooking);
        return savedBooking;
    }

    public Booking getBookingById(Long bookingId) {
        Booking booking = findBookingById(bookingId);
        ensureCurrentUserCanAccessBooking(booking);
        return booking;
    }

    public Accommodation getAccommodationByBookingId(Long bookingId) {
        Booking booking = getBookingById(bookingId);
        return getAccommodation(booking.getAccommodationId());
    }

    public List<Booking> listBookings(BookingFilterQuery query) {
        CurrentUser currentUser = currentUserService.getCurrentUser();

        if (currentUser.role() == UserRole.ADMIN) {
            BookingFilterQuery effectiveQuery = query == null ? new BookingFilterQuery(
                    null,
                    null) : query;
            return bookingRepository.findAllByFilter(effectiveQuery);
        }

        List<Booking> ownBookings = bookingRepository.findAllByUserId(currentUser.id());
        if (query == null || query.status() == null) {
            return ownBookings;
        }

        return ownBookings.stream()
                .filter(booking -> booking.getStatus() == query.status())
                .toList();
    }

    public List<Booking> listMyBookings() {
        CurrentUser currentUser = currentUserService.getCurrentUser();
        return bookingRepository.findAllByUserId(currentUser.id());
    }

    @Transactional
    public Booking updateBooking(Long bookingId, LocalDate checkInDate, LocalDate checkOutDate) {
        Booking existingBooking = findBookingById(bookingId);
        ensureCurrentUserCanAccessBooking(existingBooking);
        ensureBookingCanBeUpdated(existingBooking);
        ensureNoOverlap(
                existingBooking.getAccommodationId(),
                checkInDate,
                checkOutDate,
                existingBooking.getId()
        );

        Booking updatedBooking = existingBooking.reschedule(checkInDate, checkOutDate);
        return bookingRepository.save(updatedBooking);
    }

    @Transactional
    public Booking cancelBooking(Long bookingId) {
        Booking existingBooking = findBookingById(bookingId);
        ensureCurrentUserCanAccessBooking(existingBooking);

        Booking canceledBooking = existingBooking.cancel();
        Booking savedBooking = bookingRepository.save(canceledBooking);
        kafkaEventPublisher.publishBookingCanceled(savedBooking);
        return savedBooking;
    }

    private void ensureNoOverlap(
            Long accommodationId,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            Long excludedBookingId
    ) {
        if (bookingRepository.existsActiveBookingOverlap(
                accommodationId, checkInDate, checkOutDate, excludedBookingId
        )) {
            throw new BookingConflictException(
                    "Accommodation is already booked for the selected dates"
            );
        }
    }

    private void ensureAccommodationHasAvailability(Accommodation accommodation) {
        if (accommodation.getAvailability() <= 0) {
            throw new BusinessValidationException("Accommodation is not available for booking");
        }
    }

    private void ensureBookingCanBeUpdated(Booking booking) {
        Payment payment = paymentRepository.findByBookingId(booking.getId()).orElse(null);
        if (payment != null && payment.getStatus() == PaymentStatus.PAID) {
            throw new BusinessValidationException("Paid booking cannot be updated");
        }

        if (booking.getStatus() == BookingStatus.CANCELED
                || booking.getStatus() == BookingStatus.EXPIRED) {
            throw new BusinessValidationException("Only active bookings can be updated");
        }
    }

    private void ensureCurrentUserCanAccessBooking(Booking booking) {
        CurrentUser currentUser = currentUserService.getCurrentUser();
        if (currentUser.role() == UserRole.ADMIN) {
            return;
        }

        if (!currentUser.id().equals(booking.getUserId())) {
            throw new ForbiddenOperationException(
                    "Access denied for booking id '"
                            + booking.getId()
                            + "'"
            );
        }
    }

    private Booking findBookingById(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundDomainException(
                        "Booking with id '"
                                + bookingId
                                + "' was not found")
                );
    }

    private Accommodation getAccommodation(Long accommodationId) {
        return accommodationRepository.findById(accommodationId)
                .orElseThrow(() -> new EntityNotFoundDomainException(
                        "Accommodation with id '"
                                + accommodationId
                                + "' was not found")
                );
    }
}
