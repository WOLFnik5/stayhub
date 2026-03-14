package com.bookingapp.application.port.out.integration;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;

public interface NotificationPort {

    void notifyAccommodationCreated(Accommodation accommodation);

    void notifyBookingCreated(Booking booking);

    void notifyBookingCanceled(Booking booking);

    void notifyAccommodationReleased(Booking booking, Accommodation accommodation);

    void notifyPaymentSuccessful(Payment payment);

    void notifyNoExpiredBookingsToday();
}
