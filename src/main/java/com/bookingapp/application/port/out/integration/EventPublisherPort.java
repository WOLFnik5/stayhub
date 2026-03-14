package com.bookingapp.application.port.out.integration;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;

public interface EventPublisherPort {

    void publishAccommodationCreated(Accommodation accommodation);

    void publishBookingCreated(Booking booking);

    void publishBookingCanceled(Booking booking);

    void publishBookingExpired(Booking booking);

    void publishPaymentSucceeded(Payment payment);
}
