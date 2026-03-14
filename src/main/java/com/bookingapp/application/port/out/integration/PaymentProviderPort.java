package com.bookingapp.application.port.out.integration;

import com.bookingapp.application.model.PaymentSession;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.domain.model.User;

public interface PaymentProviderPort {

    PaymentSession createPaymentSession(Payment payment, Booking booking, Accommodation accommodation, User user);
}
