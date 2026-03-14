package com.bookingapp.application.port.in.booking;

import com.bookingapp.application.model.UpdateBookingCommand;
import com.bookingapp.domain.model.Booking;

public interface UpdateBookingUseCase {

    Booking updateBooking(UpdateBookingCommand command);
}
