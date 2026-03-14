package com.bookingapp.application.port.in.payment;

import com.bookingapp.domain.model.Payment;

public interface HandlePaymentSuccessUseCase {

    Payment handlePaymentSuccess(String sessionId);
}
