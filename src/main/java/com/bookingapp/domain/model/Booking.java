package com.bookingapp.domain.model;

import com.bookingapp.domain.enums.BookingStatus;
import com.bookingapp.domain.exception.BusinessValidationException;
import com.bookingapp.domain.exception.InvalidBookingStateException;

import java.time.LocalDate;
import java.util.Objects;

public final class Booking {

    private final Long id;
    private final LocalDate checkInDate;
    private final LocalDate checkOutDate;
    private final Long accommodationId;
    private final Long userId;
    private final BookingStatus status;

    public Booking(
            Long id,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            Long accommodationId,
            Long userId,
            BookingStatus status
    ) {
        this.id = id;
        this.checkInDate = Objects.requireNonNull(checkInDate, "Booking check-in date must not be null");
        this.checkOutDate = Objects.requireNonNull(checkOutDate, "Booking check-out date must not be null");
        validateDates(this.checkInDate, this.checkOutDate);
        this.accommodationId = Objects.requireNonNull(accommodationId, "Booking accommodation id must not be null");
        this.userId = Objects.requireNonNull(userId, "Booking user id must not be null");
        this.status = Objects.requireNonNull(status, "Booking status must not be null");
    }

    public static Booking createNew(
            LocalDate checkInDate,
            LocalDate checkOutDate,
            Long accommodationId,
            Long userId
    ) {
        return new Booking(null, checkInDate, checkOutDate, accommodationId, userId, BookingStatus.PENDING);
    }

    public Booking confirm() {
        if (status == BookingStatus.CANCELED || status == BookingStatus.EXPIRED) {
            throw new InvalidBookingStateException("Booking cannot be confirmed from state " + status);
        }
        if (status == BookingStatus.CONFIRMED) {
            return this;
        }
        return new Booking(id, checkInDate, checkOutDate, accommodationId, userId, BookingStatus.CONFIRMED);
    }

    public Booking cancel() {
        if (status == BookingStatus.CANCELED) {
            throw new InvalidBookingStateException("Booking is already canceled");
        }
        if (status == BookingStatus.EXPIRED) {
            throw new InvalidBookingStateException("Expired booking cannot be canceled");
        }
        return new Booking(id, checkInDate, checkOutDate, accommodationId, userId, BookingStatus.CANCELED);
    }

    public Booking expire() {
        if (status == BookingStatus.CANCELED) {
            throw new InvalidBookingStateException("Canceled booking cannot be expired");
        }
        if (status == BookingStatus.EXPIRED) {
            throw new InvalidBookingStateException("Booking is already expired");
        }
        return new Booking(id, checkInDate, checkOutDate, accommodationId, userId, BookingStatus.EXPIRED);
    }

    public Booking reschedule(LocalDate checkInDate, LocalDate checkOutDate) {
        if (status == BookingStatus.CANCELED || status == BookingStatus.EXPIRED) {
            throw new InvalidBookingStateException("Only active bookings can be rescheduled");
        }
        return new Booking(id, checkInDate, checkOutDate, accommodationId, userId, status);
    }

    public boolean overlaps(LocalDate otherCheckInDate, LocalDate otherCheckOutDate) {
        Objects.requireNonNull(otherCheckInDate, "Other booking check-in date must not be null");
        Objects.requireNonNull(otherCheckOutDate, "Other booking check-out date must not be null");
        validateDates(otherCheckInDate, otherCheckOutDate);

        return checkInDate.isBefore(otherCheckOutDate) && otherCheckInDate.isBefore(checkOutDate);
    }

    public Long getId() {
        return id;
    }

    public LocalDate getCheckInDate() {
        return checkInDate;
    }

    public LocalDate getCheckOutDate() {
        return checkOutDate;
    }

    public Long getAccommodationId() {
        return accommodationId;
    }

    public Long getUserId() {
        return userId;
    }

    public BookingStatus getStatus() {
        return status;
    }

    private static void validateDates(LocalDate checkInDate, LocalDate checkOutDate) {
        if (!checkInDate.isBefore(checkOutDate)) {
            throw new BusinessValidationException("Booking check-in date must be before check-out date");
        }
    }
}
