package com.bookingapp.application.port.in.payment;

import com.bookingapp.application.model.CreatePaymentSessionCommand;
import com.bookingapp.application.model.PaymentSession;

public interface CreatePaymentSessionUseCase {

    PaymentSession createPaymentSession(CreatePaymentSessionCommand command);
}
