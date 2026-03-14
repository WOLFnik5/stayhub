package com.bookingapp.adapter.out.persistence.repository;

import com.bookingapp.adapter.out.persistence.entity.BookingEntity;
import com.bookingapp.domain.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface JpaBookingRepository extends JpaRepository<BookingEntity, Long> {

    @Query("""
            select b
            from BookingEntity b
            where (:userId is null or b.userId = :userId)
              and (:status is null or b.status = :status)
            order by b.checkInDate asc, b.id desc
            """)
    List<BookingEntity> findAllByFilter(@Param("userId") Long userId, @Param("status") BookingStatus status);

    List<BookingEntity> findAllByUserIdOrderByCheckInDateAscIdDesc(Long userId);

    @Query("""
            select count(b)
            from BookingEntity b
            where b.accommodationId = :accommodationId
              and b.status not in :inactiveStatuses
              and (:excludedBookingId is null or b.id <> :excludedBookingId)
              and b.checkInDate < :checkOutDate
              and :checkInDate < b.checkOutDate
            """)
    long countActiveOverlappingBookings(
            @Param("accommodationId") Long accommodationId,
            @Param("checkInDate") LocalDate checkInDate,
            @Param("checkOutDate") LocalDate checkOutDate,
            @Param("excludedBookingId") Long excludedBookingId,
            @Param("inactiveStatuses") List<BookingStatus> inactiveStatuses
    );

    @Query("""
            select b
            from BookingEntity b
            where b.checkOutDate <= :businessDate
              and b.status not in :inactiveStatuses
            order by b.checkOutDate asc, b.id asc
            """)
    List<BookingEntity> findBookingsToExpire(
            @Param("businessDate") LocalDate businessDate,
            @Param("inactiveStatuses") List<BookingStatus> inactiveStatuses
    );
}
