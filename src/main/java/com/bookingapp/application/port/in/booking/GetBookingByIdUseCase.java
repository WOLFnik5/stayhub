package com.bookingapp.application.port.in.booking;

import com.bookingapp.domain.model.Booking;

public interface GetBookingByIdUseCase {

    Booking getBookingById(Long bookingId);
}
