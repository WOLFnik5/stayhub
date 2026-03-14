package com.bookingapp.adapter.out.persistence;

import com.bookingapp.adapter.out.persistence.mapper.PaymentPersistenceMapper;
import com.bookingapp.adapter.out.persistence.repository.JpaPaymentRepository;
import com.bookingapp.application.model.PaymentFilterQuery;
import com.bookingapp.application.port.out.persistence.PaymentRepositoryPort;
import com.bookingapp.domain.model.Payment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class PaymentPersistenceAdapter implements PaymentRepositoryPort {

    private final JpaPaymentRepository jpaPaymentRepository;
    private final PaymentPersistenceMapper paymentPersistenceMapper;

    public PaymentPersistenceAdapter(
            JpaPaymentRepository jpaPaymentRepository,
            PaymentPersistenceMapper paymentPersistenceMapper
    ) {
        this.jpaPaymentRepository = jpaPaymentRepository;
        this.paymentPersistenceMapper = paymentPersistenceMapper;
    }

    @Override
    @Transactional
    public Payment save(Payment payment) {
        return paymentPersistenceMapper.toDomain(
                jpaPaymentRepository.save(paymentPersistenceMapper.toEntity(payment))
        );
    }

    @Override
    public Optional<Payment> findById(Long paymentId) {
        return jpaPaymentRepository.findById(paymentId)
                .map(paymentPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Payment> findByBookingId(Long bookingId) {
        return jpaPaymentRepository.findByBookingId(bookingId)
                .map(paymentPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Payment> findBySessionId(String sessionId) {
        return jpaPaymentRepository.findBySessionId(sessionId)
                .map(paymentPersistenceMapper::toDomain);
    }

    @Override
    public List<Payment> findAllByFilter(PaymentFilterQuery query) {
        if (query.userId() == null) {
            return jpaPaymentRepository.findAll().stream()
                    .map(paymentPersistenceMapper::toDomain)
                    .toList();
        }

        return jpaPaymentRepository.findAllByUserId(query.userId()).stream()
                .map(paymentPersistenceMapper::toDomain)
                .toList();
    }
}
