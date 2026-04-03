package com.bookingapp.domain.repository;

import com.bookingapp.domain.model.Payment;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(Long paymentId);

    Optional<Payment> findByBookingId(Long bookingId);

    Optional<Payment> findBySessionId(String sessionId);

    List<Payment> findAllByFilter(PaymentFilterQuery query);
}
