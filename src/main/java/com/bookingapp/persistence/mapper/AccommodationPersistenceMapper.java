package com.bookingapp.persistence.mapper;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.infrastructure.config.MapStructConfig;
import com.bookingapp.persistence.entity.AccommodationEntity;
import java.util.ArrayList;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(config = MapStructConfig.class)
public interface AccommodationPersistenceMapper {

    @Mapping(target = "amenities", source = "amenities", qualifiedByName = "toEntityAmenities")
    AccommodationEntity toEntity(Accommodation domain);

    @Mapping(target = "amenities", source = "amenities", qualifiedByName = "toDomainAmenities")
    Accommodation toDomain(AccommodationEntity entity);

    @Named("toEntityAmenities")
    default List<String> toEntityAmenities(List<String> amenities) {
        return amenities == null ? new ArrayList<>() : new ArrayList<>(amenities);
    }

    @Named("toDomainAmenities")
    default List<String> toDomainAmenities(List<String> amenities) {
        return amenities == null ? List.of() : List.copyOf(amenities);
    }
}
