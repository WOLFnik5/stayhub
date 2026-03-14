package com.bookingapp.application.port.in.booking;

import com.bookingapp.application.model.BookingFilterQuery;
import com.bookingapp.domain.model.Booking;

import java.util.List;

public interface ListBookingsUseCase {

    List<Booking> listBookings(BookingFilterQuery query);
}
