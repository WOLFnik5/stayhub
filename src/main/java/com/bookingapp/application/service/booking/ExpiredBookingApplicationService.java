package com.bookingapp.application.service.booking;

import com.bookingapp.application.model.ExpireBookingsCommand;
import com.bookingapp.application.model.ExpireBookingsResult;
import com.bookingapp.application.port.in.booking.ExpireBookingsUseCase;
import com.bookingapp.application.port.out.integration.EventPublisherPort;
import com.bookingapp.application.port.out.integration.NotificationPort;
import com.bookingapp.application.port.out.persistence.AccommodationRepositoryPort;
import com.bookingapp.application.port.out.persistence.BookingRepositoryPort;
import com.bookingapp.domain.exception.BusinessValidationException;
import com.bookingapp.domain.exception.EntityNotFoundDomainException;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ExpiredBookingApplicationService implements ExpireBookingsUseCase {

    private final BookingRepositoryPort bookingRepositoryPort;
    private final AccommodationRepositoryPort accommodationRepositoryPort;
    private final EventPublisherPort eventPublisherPort;
    private final NotificationPort notificationPort;

    public ExpiredBookingApplicationService(
            BookingRepositoryPort bookingRepositoryPort,
            AccommodationRepositoryPort accommodationRepositoryPort,
            EventPublisherPort eventPublisherPort,
            NotificationPort notificationPort
    ) {
        this.bookingRepositoryPort = bookingRepositoryPort;
        this.accommodationRepositoryPort = accommodationRepositoryPort;
        this.eventPublisherPort = eventPublisherPort;
        this.notificationPort = notificationPort;
    }

    @Override
    @Transactional
    public ExpireBookingsResult expireBookings(ExpireBookingsCommand command) {
        if (command == null) {
            throw new BusinessValidationException("Expire bookings command must not be null");
        }

        LocalDate businessDate = command.businessDate() == null ? LocalDate.now() : command.businessDate();
        List<Booking> bookingsToExpire = bookingRepositoryPort.findBookingsToExpire(businessDate);

        if (bookingsToExpire.isEmpty()) {
            notificationPort.notifyNoExpiredBookingsToday();
            return new ExpireBookingsResult(0, List.of());
        }

        List<Long> expiredBookingIds = bookingsToExpire.stream()
                .map(Booking::expire)
                .map(bookingRepositoryPort::save)
                .peek(eventPublisherPort::publishBookingExpired)
                .peek(this::notifyAccommodationRelease)
                .map(Booking::getId)
                .toList();

        return new ExpireBookingsResult(expiredBookingIds.size(), expiredBookingIds);
    }

    private void notifyAccommodationRelease(Booking expiredBooking) {
        Accommodation accommodation = accommodationRepositoryPort.findById(expiredBooking.getAccommodationId())
                .orElseThrow(() -> new EntityNotFoundDomainException(
                        "Accommodation with id '" + expiredBooking.getAccommodationId() + "' was not found"));

        notificationPort.notifyAccommodationReleased(expiredBooking, accommodation);
    }
}
