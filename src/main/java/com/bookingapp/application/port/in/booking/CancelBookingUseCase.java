package com.bookingapp.application.port.in.booking;

import com.bookingapp.domain.model.Booking;

public interface CancelBookingUseCase {

    Booking cancelBooking(Long bookingId);
}
