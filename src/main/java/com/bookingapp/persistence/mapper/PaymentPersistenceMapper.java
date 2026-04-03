package com.bookingapp.persistence.mapper;

import com.bookingapp.domain.model.Payment;
import com.bookingapp.infrastructure.config.MapStructConfig;
import com.bookingapp.persistence.entity.PaymentEntity;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface PaymentPersistenceMapper {

    PaymentEntity toEntity(Payment domain);

    Payment toDomain(PaymentEntity entity);
}
