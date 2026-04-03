package com.bookingapp.web.mapper;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.infrastructure.config.MapStructConfig;
import com.bookingapp.web.dto.AccommodationDetailResponse;
import com.bookingapp.web.dto.AccommodationListResponse;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface AccommodationWebMapper {

    AccommodationListResponse toListResponse(Accommodation accommodation);

    AccommodationDetailResponse toDetailResponse(Accommodation accommodation);
}
