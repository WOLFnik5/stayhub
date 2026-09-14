package com.bookingapp.service;

import static com.bookingapp.service.validation.BookingValidationUtils.validateBookingDates;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.domain.model.enums.PaymentStatus;
import com.bookingapp.domain.model.enums.UserRole;
import com.bookingapp.exception.BookingConflictException;
import com.bookingapp.exception.BusinessValidationException;
import com.bookingapp.exception.EntityNotFoundDomainException;
import com.bookingapp.exception.ForbiddenOperationException;
import com.bookingapp.exception.InvalidBookingStateException;
import com.bookingapp.infrastructure.kafka.KafkaEventPublisher;
import com.bookingapp.infrastructure.security.CurrentUser;
import com.bookingapp.infrastructure.security.CurrentUserService;
import com.bookingapp.persistence.AccommodationRepositoryImpl;
import com.bookingapp.persistence.BookingFilterQuery;
import com.bookingapp.persistence.BookingRepositoryImpl;
import com.bookingapp.persistence.PaymentRepositoryImpl;
import com.bookingapp.web.dto.BookingDetail;
import com.bookingapp.web.dto.CreateBookingRequest;
import com.bookingapp.web.dto.PatchBookingRequest;
import com.bookingapp.web.dto.UpdateBookingRequest;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookingService {

    private final BookingRepositoryImpl bookingRepository;
    private final AccommodationRepositoryImpl accommodationRepository;
    private final PaymentRepositoryImpl paymentRepository;
    private final CurrentUserService currentUserService;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final PaymentService paymentService;

    public BookingService(
            BookingRepositoryImpl bookingRepository,
            AccommodationRepositoryImpl accommodationRepository,
            PaymentRepositoryImpl paymentRepository,
            CurrentUserService currentUserService,
            KafkaEventPublisher kafkaEventPublisher,
            PaymentService paymentService
    ) {
        this.bookingRepository = bookingRepository;
        this.accommodationRepository = accommodationRepository;
        this.paymentRepository = paymentRepository;
        this.currentUserService = currentUserService;
        this.kafkaEventPublisher = kafkaEventPublisher;
        this.paymentService = paymentService;
    }

    @Transactional
    public Booking createBooking(CreateBookingRequest request) {
        Accommodation accommodation = getAccommodationForUpdate(request.accommodationId());
        validateBookingDates(request.checkInDate(), request.checkOutDate());
        ensureAccommodationHasAvailability(accommodation);
        ensureCapacityAvailable(accommodation, request.checkInDate(), request.checkOutDate(), null);
        CurrentUser currentUser = currentUserService.getCurrentUser();

        Booking bookingToSave = new Booking(
                null,
                request.checkInDate(),
                request.checkOutDate(),
                accommodation.getId(),
                currentUser.id(),
                BookingStatus.PENDING
        );

        Booking savedBooking = bookingRepository.save(bookingToSave);
        kafkaEventPublisher.publishBookingCreated(savedBooking);
        return savedBooking;
    }

    public BookingDetail getBookingDetail(Long bookingId) {
        Booking booking = findBookingById(bookingId);
        ensureCurrentUserCanAccessBooking(booking);
        Accommodation accommodation = getAccommodation(booking.getAccommodationId());
        return new BookingDetail(booking, accommodation);
    }

    public Booking getBookingById(Long bookingId) {
        Booking booking = findBookingById(bookingId);
        ensureCurrentUserCanAccessBooking(booking);
        return booking;
    }

    public List<Booking> listBookings(BookingFilterQuery query) {
        CurrentUser currentUser = currentUserService.getCurrentUser();

        if (currentUser.role() == UserRole.ADMIN) {
            BookingFilterQuery effectiveQuery = query == null
                    ? new BookingFilterQuery(null, null)
                    : query;
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
    public Booking updateBooking(Long bookingId, UpdateBookingRequest request) {
        Booking existingBooking = findBookingForUpdate(bookingId);
        ensureCurrentUserCanAccessBooking(existingBooking);
        ensureBookingCanBeUpdated(existingBooking);
        Accommodation accommodation = getAccommodationForUpdate(
                existingBooking.getAccommodationId());
        validateBookingDates(request.checkInDate(), request.checkOutDate());
        ensureCapacityAvailable(
                accommodation,
                request.checkInDate(),
                request.checkOutDate(),
                existingBooking.getId()
        );

        existingBooking.setCheckInDate(request.checkInDate());
        existingBooking.setCheckOutDate(request.checkOutDate());
        return bookingRepository.save(existingBooking);
    }

    @Transactional
    public Booking cancelBooking(Long bookingId) {
        Booking existingBooking = findBookingForUpdate(bookingId);
        ensureCurrentUserCanAccessBooking(existingBooking);
        ensureBookingCanBeCanceled(existingBooking);
        paymentService.closeCheckoutForBooking(existingBooking, true);

        existingBooking.setStatus(BookingStatus.CANCELED);
        Booking savedBooking = bookingRepository.save(existingBooking);
        kafkaEventPublisher.publishBookingCanceled(savedBooking);
        return savedBooking;
    }

    @Transactional
    public Booking patchBooking(Long id, PatchBookingRequest request) {
        Booking current = findBookingForUpdate(id);
        ensureCurrentUserCanAccessBooking(current);
        ensureBookingCanBeUpdated(current);
        Accommodation accommodation = getAccommodationForUpdate(current.getAccommodationId());

        LocalDate checkInDate = request.checkInDate() != null
                ? request.checkInDate()
                : current.getCheckInDate();
        LocalDate checkOutDate = request.checkOutDate() != null
                ? request.checkOutDate()
                : current.getCheckOutDate();

        validateBookingDates(checkInDate, checkOutDate);
        ensureCapacityAvailable(accommodation, checkInDate, checkOutDate, current.getId());

        current.setCheckInDate(checkInDate);
        current.setCheckOutDate(checkOutDate);
        return bookingRepository.save(current);
    }

    private void ensureCapacityAvailable(
            Accommodation accommodation,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            Long excludedBookingId
    ) {
        long overlaps = bookingRepository.countActiveBookingOverlaps(
                accommodation.getId(), checkInDate, checkOutDate, excludedBookingId
        );
        if (overlaps >= accommodation.getAvailability()) {
            throw new BookingConflictException(
                    "Accommodation capacity is exhausted for the selected dates"
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
        if (payment != null) {
            throw new BusinessValidationException(
                    "Booking dates cannot be changed after checkout has been created");
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

    private Booking findBookingForUpdate(Long bookingId) {
        return bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new EntityNotFoundDomainException(
                        "Booking with id '" + bookingId + "' was not found"));
    }

    private Accommodation getAccommodation(Long accommodationId) {
        return accommodationRepository.findById(accommodationId)
                .orElseThrow(() -> new EntityNotFoundDomainException(
                        "Accommodation with id '"
                                + accommodationId
                                + "' was not found")
                );
    }

    private Accommodation getAccommodationForUpdate(Long accommodationId) {
        return accommodationRepository.findByIdForUpdate(accommodationId)
                .orElseThrow(() -> new EntityNotFoundDomainException(
                        "Accommodation with id '"
                                + accommodationId
                                + "' was not found")
                );
    }
}
