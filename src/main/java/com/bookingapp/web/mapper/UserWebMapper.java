package com.bookingapp.web.mapper;

import com.bookingapp.domain.model.User;
import com.bookingapp.infrastructure.config.MapStructConfig;
import com.bookingapp.web.dto.UserProfileResponse;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface UserWebMapper {

    UserProfileResponse toResponse(User user);
}
