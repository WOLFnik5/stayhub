package com.bookingapp.application.port.in.payment;

import com.bookingapp.application.model.PaymentFilterQuery;
import com.bookingapp.domain.model.Payment;

import java.util.List;

public interface GetPaymentsUseCase {

    List<Payment> getPayments(PaymentFilterQuery query);
}
