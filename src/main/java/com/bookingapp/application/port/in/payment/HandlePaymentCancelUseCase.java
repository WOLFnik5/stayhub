package com.bookingapp.application.port.in.payment;

import com.bookingapp.domain.model.Payment;

public interface HandlePaymentCancelUseCase {

    Payment handlePaymentCancel(String sessionId);
}
