package com.bookingapp.service;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.domain.model.enums.PaymentStatus;
import com.bookingapp.domain.model.enums.UserRole;
import com.bookingapp.domain.repository.AccommodationRepository;
import com.bookingapp.domain.repository.BookingFilterQuery;
import com.bookingapp.domain.repository.BookingRepository;
import com.bookingapp.domain.repository.PaymentRepository;
import com.bookingapp.exception.BookingConflictException;
import com.bookingapp.exception.BusinessValidationException;
import com.bookingapp.exception.EntityNotFoundDomainException;
import com.bookingapp.exception.ForbiddenOperationException;
import com.bookingapp.exception.InvalidBookingStateException;
import com.bookingapp.infrastructure.kafka.KafkaEventPublisher;
import com.bookingapp.infrastructure.security.CurrentUser;
import com.bookingapp.infrastructure.security.CurrentUserService;
import com.bookingapp.web.dto.PatchBookingRequest;
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
        Accommodation accommodation = getAccommodation(accommodationId);
        validateBookingDates(checkInDate, checkOutDate);
        ensureAccommodationHasAvailability(accommodation);
        ensureNoOverlap(accommodationId, checkInDate, checkOutDate, null);
        CurrentUser currentUser = currentUserService.getCurrentUser();

        Booking bookingToSave = new Booking(
                null,
                checkInDate,
                checkOutDate,
                accommodation.getId(),
                currentUser.id(),
                BookingStatus.PENDING
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
        validateBookingDates(checkInDate, checkOutDate);
        ensureNoOverlap(
                existingBooking.getAccommodationId(),
                checkInDate,
                checkOutDate,
                existingBooking.getId()
        );

        existingBooking.setCheckInDate(checkInDate);
        existingBooking.setCheckOutDate(checkOutDate);
        return bookingRepository.save(existingBooking);
    }

    @Transactional
    public Booking cancelBooking(Long bookingId) {
        Booking existingBooking = findBookingById(bookingId);
        ensureCurrentUserCanAccessBooking(existingBooking);
        ensureBookingCanBeCanceled(existingBooking);

        existingBooking.setStatus(BookingStatus.CANCELED);
        Booking savedBooking = bookingRepository.save(existingBooking);
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

    private void ensureBookingCanBeCanceled(Booking booking) {
        if (booking.getStatus() == BookingStatus.CANCELED) {
            throw new InvalidBookingStateException("Booking is already canceled");
        }
        if (booking.getStatus() == BookingStatus.EXPIRED) {
            throw new InvalidBookingStateException("Expired booking cannot be canceled");
        }
    }

    private void validateBookingDates(LocalDate checkInDate, LocalDate checkOutDate) {
        if (checkInDate == null) {
            throw new BusinessValidationException("Booking check-in date must not be null");
        }
        if (checkOutDate == null) {
            throw new BusinessValidationException("Booking check-out date must not be null");
        }
        if (!checkInDate.isBefore(checkOutDate)) {
            throw new BusinessValidationException(
                    "Booking check-in date must be before check-out date"
            );
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

    @Transactional
    public Booking patchBooking(Long id, PatchBookingRequest request) {
        Booking current = findBookingById(id);
        ensureCurrentUserCanAccessBooking(current);
        ensureBookingCanBeUpdated(current);

        LocalDate checkInDate =
                request.checkInDate() != null ? request.checkInDate() : current.getCheckInDate();
        LocalDate checkOutDate =
                request.checkOutDate() != null ? request.checkOutDate() : current.getCheckOutDate();

        validateBookingDates(checkInDate, checkOutDate);
        ensureNoOverlap(current.getAccommodationId(), checkInDate, checkOutDate, current.getId());

        current.setCheckInDate(checkInDate);
        current.setCheckOutDate(checkOutDate);
        return bookingRepository.save(current);
    }
}
