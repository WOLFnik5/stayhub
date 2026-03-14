package com.bookingapp.application.port.out.persistence;

import com.bookingapp.application.model.BookingFilterQuery;
import com.bookingapp.domain.model.Booking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookingRepositoryPort {

    Booking save(Booking booking);

    Optional<Booking> findById(Long bookingId);

    List<Booking> findAllByFilter(BookingFilterQuery query);

    List<Booking> findAllByUserId(Long userId);

    boolean existsActiveBookingOverlap(Long accommodationId, LocalDate checkInDate, LocalDate checkOutDate, Long excludedBookingId);

    List<Booking> findBookingsToExpire(LocalDate businessDate);
}
