package com.bookingapp.application.port.in.booking;

import com.bookingapp.application.model.ExpireBookingsCommand;
import com.bookingapp.application.model.ExpireBookingsResult;

public interface ExpireBookingsUseCase {

    ExpireBookingsResult expireBookings(ExpireBookingsCommand command);
}
