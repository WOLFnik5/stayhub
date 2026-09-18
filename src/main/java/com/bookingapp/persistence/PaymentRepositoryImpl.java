package com.bookingapp.persistence;

import com.bookingapp.domain.model.PageResult;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.persistence.entity.PaymentEntity;
import com.bookingapp.persistence.mapper.PaymentPersistenceMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class PaymentRepositoryImpl {

    @PersistenceContext
    private EntityManager entityManager;

    private final PaymentPersistenceMapper paymentPersistenceMapper;

    public PaymentRepositoryImpl(
            PaymentPersistenceMapper paymentPersistenceMapper
    ) {
        this.paymentPersistenceMapper = paymentPersistenceMapper;
    }

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

    public Optional<Payment> findById(Long paymentId) {
        PaymentEntity entity = entityManager.find(PaymentEntity.class, paymentId);
        return Optional.ofNullable(entity)
                .map(paymentPersistenceMapper::toDomain);
    }

    public Optional<Payment> findByBookingId(Long bookingId) {
        TypedQuery<PaymentEntity> query = entityManager.createQuery(
                "SELECT p FROM PaymentEntity p WHERE p.bookingId = :bookingId ORDER BY p.id DESC",
                PaymentEntity.class
        );
        query.setParameter("bookingId", bookingId);
        query.setMaxResults(1);
        return query.getResultStream()
                .findFirst()
                .map(paymentPersistenceMapper::toDomain);
    }

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

    public List<Payment> findAllByBookingId(Long bookingId) {
        return entityManager.createQuery(
                "SELECT p FROM PaymentEntity p WHERE p.bookingId = :id ORDER BY p.id",
                PaymentEntity.class).setParameter("id", bookingId).getResultList().stream()
                .map(paymentPersistenceMapper::toDomain).toList();
    }

    public void flush() {
        entityManager.flush();
    }

    public Payment refresh(Long id) {
        PaymentEntity entity = entityManager.find(PaymentEntity.class, id);
        entityManager.refresh(entity);
        return paymentPersistenceMapper.toDomain(entity);
    }

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

    public PageResult<Payment> findPageByFilter(PaymentFilterQuery query, int page, int size) {
        String where = query.userId() == null ? "" : """
                WHERE EXISTS (
                    SELECT 1
                    FROM BookingEntity b
                    WHERE b.id = p.bookingId
                      AND b.userId = :userId
                )
                """;
        TypedQuery<PaymentEntity> pageQuery = entityManager.createQuery(
                "SELECT p FROM PaymentEntity p " + where + " ORDER BY p.id DESC",
                PaymentEntity.class
        );
        TypedQuery<Long> countQuery = entityManager.createQuery(
                "SELECT COUNT(p) FROM PaymentEntity p " + where,
                Long.class
        );
        if (query.userId() != null) {
            pageQuery.setParameter("userId", query.userId());
            countQuery.setParameter("userId", query.userId());
        }
        List<Payment> content = pageQuery.setFirstResult(page * size).setMaxResults(size)
                .getResultList().stream().map(paymentPersistenceMapper::toDomain).toList();
        return new PageResult<>(content, page, size, countQuery.getSingleResult());
    }
}
