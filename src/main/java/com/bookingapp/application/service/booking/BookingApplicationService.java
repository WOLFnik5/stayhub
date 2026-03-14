package com.bookingapp.application.service.booking;

import com.bookingapp.application.model.BookingFilterQuery;
import com.bookingapp.application.model.CreateBookingCommand;
import com.bookingapp.application.model.CurrentUser;
import com.bookingapp.application.model.UpdateBookingCommand;
import com.bookingapp.application.port.in.booking.CancelBookingUseCase;
import com.bookingapp.application.port.in.booking.CreateBookingUseCase;
import com.bookingapp.application.port.in.booking.GetBookingByIdUseCase;
import com.bookingapp.application.port.in.booking.ListBookingsUseCase;
import com.bookingapp.application.port.in.booking.ListMyBookingsUseCase;
import com.bookingapp.application.port.in.booking.UpdateBookingUseCase;
import com.bookingapp.application.port.out.integration.EventPublisherPort;
import com.bookingapp.application.port.out.integration.NotificationPort;
import com.bookingapp.application.port.out.persistence.AccommodationRepositoryPort;
import com.bookingapp.application.port.out.persistence.BookingRepositoryPort;
import com.bookingapp.application.port.out.persistence.PaymentRepositoryPort;
import com.bookingapp.application.port.out.security.CurrentUserProviderPort;
import com.bookingapp.domain.enums.BookingStatus;
import com.bookingapp.domain.enums.PaymentStatus;
import com.bookingapp.domain.enums.UserRole;
import com.bookingapp.domain.exception.BookingConflictException;
import com.bookingapp.domain.exception.BusinessValidationException;
import com.bookingapp.domain.exception.EntityNotFoundDomainException;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class BookingApplicationService implements
        CreateBookingUseCase,
        GetBookingByIdUseCase,
        ListBookingsUseCase,
        ListMyBookingsUseCase,
        UpdateBookingUseCase,
        CancelBookingUseCase {

    private final BookingRepositoryPort bookingRepositoryPort;
    private final AccommodationRepositoryPort accommodationRepositoryPort;
    private final PaymentRepositoryPort paymentRepositoryPort;
    private final CurrentUserProviderPort currentUserProviderPort;
    private final EventPublisherPort eventPublisherPort;
    private final NotificationPort notificationPort;

    public BookingApplicationService(
            BookingRepositoryPort bookingRepositoryPort,
            AccommodationRepositoryPort accommodationRepositoryPort,
            PaymentRepositoryPort paymentRepositoryPort,
            CurrentUserProviderPort currentUserProviderPort,
            EventPublisherPort eventPublisherPort,
            NotificationPort notificationPort
    ) {
        this.bookingRepositoryPort = bookingRepositoryPort;
        this.accommodationRepositoryPort = accommodationRepositoryPort;
        this.paymentRepositoryPort = paymentRepositoryPort;
        this.currentUserProviderPort = currentUserProviderPort;
        this.eventPublisherPort = eventPublisherPort;
        this.notificationPort = notificationPort;
    }

    @Override
    @Transactional
    public Booking createBooking(CreateBookingCommand command) {
        if (command == null) {
            throw new BusinessValidationException("Create booking command must not be null");
        }

        CurrentUser currentUser = currentUserProviderPort.getCurrentUser();
        Accommodation accommodation = getAccommodation(command.accommodationId());
        ensureAccommodationHasAvailability(accommodation);
        ensureNoOverlap(command.accommodationId(), command.checkInDate(), command.checkOutDate(), null);

        /*
         * Bookings start in PENDING state so payment success can explicitly drive
         * the business transition instead of silently confirming during creation.
         */
        Booking bookingToSave = Booking.createNew(
                command.checkInDate(),
                command.checkOutDate(),
                accommodation.getId(),
                currentUser.id()
        );

        Booking savedBooking = bookingRepositoryPort.save(bookingToSave);
        eventPublisherPort.publishBookingCreated(savedBooking);
        notificationPort.notifyBookingCreated(savedBooking);
        return savedBooking;
    }

    @Override
    public Booking getBookingById(Long bookingId) {
        Booking booking = findBookingById(bookingId);
        ensureCurrentUserCanAccessBooking(booking);
        return booking;
    }

    @Override
    public List<Booking> listBookings(BookingFilterQuery query) {
        CurrentUser currentUser = currentUserProviderPort.getCurrentUser();

        if (currentUser.role() == UserRole.ADMIN) {
            BookingFilterQuery effectiveQuery = query == null ? new BookingFilterQuery(null, null) : query;
            return bookingRepositoryPort.findAllByFilter(effectiveQuery);
        }

        List<Booking> ownBookings = bookingRepositoryPort.findAllByUserId(currentUser.id());
        if (query == null || query.status() == null) {
            return ownBookings;
        }

        return ownBookings.stream()
                .filter(booking -> booking.getStatus() == query.status())
                .toList();
    }

    @Override
    public List<Booking> listMyBookings() {
        CurrentUser currentUser = currentUserProviderPort.getCurrentUser();
        return bookingRepositoryPort.findAllByUserId(currentUser.id());
    }

    @Override
    @Transactional
    public Booking updateBooking(UpdateBookingCommand command) {
        if (command == null) {
            throw new BusinessValidationException("Update booking command must not be null");
        }

        Booking existingBooking = findBookingById(command.bookingId());
        ensureCurrentUserCanAccessBooking(existingBooking);
        ensureBookingCanBeUpdated(existingBooking);
        ensureNoOverlap(
                existingBooking.getAccommodationId(),
                command.checkInDate(),
                command.checkOutDate(),
                existingBooking.getId()
        );

        Booking updatedBooking = existingBooking.reschedule(command.checkInDate(), command.checkOutDate());
        return bookingRepositoryPort.save(updatedBooking);
    }

    @Override
    @Transactional
    public Booking cancelBooking(Long bookingId) {
        Booking existingBooking = findBookingById(bookingId);
        ensureCurrentUserCanAccessBooking(existingBooking);

        Booking canceledBooking = existingBooking.cancel();
        Booking savedBooking = bookingRepositoryPort.save(canceledBooking);
        eventPublisherPort.publishBookingCanceled(savedBooking);
        notificationPort.notifyBookingCanceled(savedBooking);
        return savedBooking;
    }

    private void ensureNoOverlap(Long accommodationId, LocalDate checkInDate, LocalDate checkOutDate, Long excludedBookingId) {
        if (bookingRepositoryPort.existsActiveBookingOverlap(accommodationId, checkInDate, checkOutDate, excludedBookingId)) {
            throw new BookingConflictException("Accommodation is already booked for the selected dates");
        }
    }

    private void ensureAccommodationHasAvailability(Accommodation accommodation) {
        if (accommodation.getAvailability() <= 0) {
            throw new BusinessValidationException("Accommodation is not available for booking");
        }
    }

    private void ensureBookingCanBeUpdated(Booking booking) {
        Payment payment = paymentRepositoryPort.findByBookingId(booking.getId()).orElse(null);
        if (payment != null && payment.getStatus() == PaymentStatus.PAID) {
            throw new BusinessValidationException("Paid booking cannot be updated");
        }

        if (booking.getStatus() == BookingStatus.CANCELED || booking.getStatus() == BookingStatus.EXPIRED) {
            throw new BusinessValidationException("Only active bookings can be updated");
        }
    }

    private void ensureCurrentUserCanAccessBooking(Booking booking) {
        CurrentUser currentUser = currentUserProviderPort.getCurrentUser();
        if (currentUser.role() == UserRole.ADMIN) {
            return;
        }

        if (!currentUser.id().equals(booking.getUserId())) {
            throw new BusinessValidationException("Access denied for booking id '" + booking.getId() + "'");
        }
    }

    private Booking findBookingById(Long bookingId) {
        return bookingRepositoryPort.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundDomainException("Booking with id '" + bookingId + "' was not found"));
    }

    private Accommodation getAccommodation(Long accommodationId) {
        return accommodationRepositoryPort.findById(accommodationId)
                .orElseThrow(() -> new EntityNotFoundDomainException(
                        "Accommodation with id '" + accommodationId + "' was not found"));
    }
}
