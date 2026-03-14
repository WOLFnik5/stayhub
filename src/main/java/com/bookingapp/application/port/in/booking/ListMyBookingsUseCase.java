package com.bookingapp.application.port.in.booking;

import com.bookingapp.domain.model.Booking;

import java.util.List;

public interface ListMyBookingsUseCase {

    List<Booking> listMyBookings();
}
