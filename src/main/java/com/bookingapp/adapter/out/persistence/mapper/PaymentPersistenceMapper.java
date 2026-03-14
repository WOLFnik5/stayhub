package com.bookingapp.adapter.out.persistence.mapper;

import com.bookingapp.adapter.out.persistence.entity.PaymentEntity;
import com.bookingapp.domain.model.Payment;
import org.springframework.stereotype.Component;

@Component
public class PaymentPersistenceMapper {

    public PaymentEntity toEntity(Payment domain) {
        PaymentEntity entity = new PaymentEntity();
        entity.setId(domain.getId());
        entity.setStatus(domain.getStatus());
        entity.setBookingId(domain.getBookingId());
        entity.setSessionUrl(domain.getSessionUrl());
        entity.setSessionId(domain.getSessionId());
        entity.setAmountToPay(domain.getAmountToPay());
        return entity;
    }

    public Payment toDomain(PaymentEntity entity) {
        return new Payment(
                entity.getId(),
                entity.getStatus(),
                entity.getBookingId(),
                entity.getSessionUrl(),
                entity.getSessionId(),
                entity.getAmountToPay()
        );
    }
}
