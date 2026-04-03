package com.bookingapp.persistence.mapper;

import com.bookingapp.domain.model.User;
import com.bookingapp.infrastructure.config.MapStructConfig;
import com.bookingapp.persistence.entity.UserEntity;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface UserPersistenceMapper {

    UserEntity toEntity(User domain);

    User toDomain(UserEntity entity);
}
