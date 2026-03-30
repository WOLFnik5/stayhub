package com.bookingapp.infrastructure.persistence.mapper;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.infrastructure.persistence.entity.AccommodationEntity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AccommodationPersistenceMapper {

    public AccommodationEntity toEntity(Accommodation domain) {
        AccommodationEntity entity = new AccommodationEntity();
        entity.setId(domain.getId());
        entity.setType(domain.getType());
        entity.setLocation(domain.getLocation());
        entity.setSize(domain.getSize());
        entity.setAmenities(new ArrayList<>(domain.getAmenities()));
        entity.setDailyRate(domain.getDailyRate());
        entity.setAvailability(domain.getAvailability());
        return entity;
    }

    public Accommodation toDomain(AccommodationEntity entity) {
        return new Accommodation(
                entity.getId(),
                entity.getType(),
                entity.getLocation(),
                entity.getSize(),
                copyAmenities(entity.getAmenities()),
                entity.getDailyRate(),
                entity.getAvailability()
        );
    }

    private List<String> copyAmenities(List<String> amenities) {
        return amenities == null ? List.of() : List.copyOf(amenities);
    }
}
