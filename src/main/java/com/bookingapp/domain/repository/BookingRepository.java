package com.bookingapp.domain.repository;

import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.service.dto.BookingFilterQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookingRepository {

    Booking save(Booking booking);

    Optional<Booking> findById(Long bookingId);

    List<Booking> findAllByFilter(BookingFilterQuery query);

    List<Booking> findAllByUserId(Long userId);

    boolean existsActiveBookingOverlap(
            Long accommodationId,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            Long excludedBookingId
    );

    List<Booking> findBookingsToExpire(LocalDate businessDate);
}
