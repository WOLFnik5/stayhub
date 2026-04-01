package com.bookingapp.infrastructure.persistence.entity;

import com.bookingapp.domain.model.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payments_session_id", columnNames = "session_id")
        },
        indexes = {
                @Index(name = "idx_payments_booking_id", columnList = "booking_id"),
                @Index(name = "idx_payments_session_id", columnList = "session_id")
        }
)
@Getter
@Setter
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private PaymentStatus status;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "session_url")
    private String sessionUrl;

    @Column(name = "session_id", unique = true)
    private String sessionId;

    @Column(name = "amount_to_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountToPay;
}
