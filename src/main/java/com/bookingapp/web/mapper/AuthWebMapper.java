package com.bookingapp.web.mapper;

import com.bookingapp.infrastructure.config.MapStructConfig;
import com.bookingapp.web.dto.AuthResponse;
import com.bookingapp.web.dto.AuthenticationResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface AuthWebMapper {

    @Mapping(target = "tokenType", constant = "Bearer")
    AuthResponse toResponse(AuthenticationResult result);
}
