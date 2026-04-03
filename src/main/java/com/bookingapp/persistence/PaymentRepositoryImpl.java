package com.bookingapp.persistence;

import com.bookingapp.domain.model.Payment;
import com.bookingapp.domain.repository.PaymentFilterQuery;
import com.bookingapp.domain.repository.PaymentRepository;
import com.bookingapp.persistence.entity.PaymentEntity;
import com.bookingapp.persistence.mapper.PaymentPersistenceMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class PaymentRepositoryImpl implements PaymentRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private final PaymentPersistenceMapper paymentPersistenceMapper;

    public PaymentRepositoryImpl(
            PaymentPersistenceMapper paymentPersistenceMapper
    ) {
        this.paymentPersistenceMapper = paymentPersistenceMapper;
    }

    @Override
    @Transactional
    public Payment save(Payment payment) {
        PaymentEntity entity = paymentPersistenceMapper.toEntity(payment);
        if (payment.getId() == null) {
            entityManager.persist(entity);
            entityManager.flush();
            return paymentPersistenceMapper.toDomain(entity);
        } else {
            PaymentEntity merged = entityManager.merge(entity);
            return paymentPersistenceMapper.toDomain(merged);
        }
    }

    @Override
    public Optional<Payment> findById(Long paymentId) {
        PaymentEntity entity = entityManager.find(PaymentEntity.class, paymentId);
        return Optional.ofNullable(entity)
                .map(paymentPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Payment> findByBookingId(Long bookingId) {
        TypedQuery<PaymentEntity> query = entityManager.createQuery(
                "SELECT p FROM PaymentEntity p WHERE p.bookingId = :bookingId",
                PaymentEntity.class
        );
        query.setParameter("bookingId", bookingId);
        return query.getResultStream()
                .findFirst()
                .map(paymentPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Payment> findBySessionId(String sessionId) {
        TypedQuery<PaymentEntity> query = entityManager.createQuery(
                "SELECT p FROM PaymentEntity p WHERE p.sessionId = :sessionId",
                PaymentEntity.class
        );
        query.setParameter("sessionId", sessionId);
        return query.getResultStream()
                .findFirst()
                .map(paymentPersistenceMapper::toDomain);
    }

    @Override
    public List<Payment> findAllByFilter(PaymentFilterQuery query) {
        if (query.userId() == null) {
            TypedQuery<PaymentEntity> jpqlQuery = entityManager.createQuery(
                    "SELECT p FROM PaymentEntity p ORDER BY p.id DESC",
                    PaymentEntity.class
            );
            return jpqlQuery.getResultList().stream()
                    .map(paymentPersistenceMapper::toDomain)
                    .toList();
        }

        TypedQuery<PaymentEntity> jpqlQuery = entityManager.createQuery(
                """
                SELECT p
                FROM PaymentEntity p
                WHERE EXISTS (
                    SELECT 1
                    FROM BookingEntity b
                    WHERE b.id = p.bookingId
                      AND b.userId = :userId
                )
                ORDER BY p.id DESC
                """,
                PaymentEntity.class
        );
        jpqlQuery.setParameter("userId", query.userId());
        return jpqlQuery.getResultList().stream()
                .map(paymentPersistenceMapper::toDomain)
                .toList();
    }
}
