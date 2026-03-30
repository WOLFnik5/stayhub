package com.bookingapp.infrastructure.persistence.repository;

import com.bookingapp.infrastructure.persistence.entity.PaymentEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPaymentRepository extends JpaRepository<PaymentEntity, Long> {

    Optional<PaymentEntity> findByBookingId(Long bookingId);

    Optional<PaymentEntity> findBySessionId(String sessionId);

    @Query("""
            select p
            from PaymentEntity p
            where exists (
                select 1
                from BookingEntity b
                where b.id = p.bookingId
                  and b.userId = :userId
            )
            order by p.id desc
            """)
    List<PaymentEntity> findAllByUserId(@Param("userId") Long userId);
}
