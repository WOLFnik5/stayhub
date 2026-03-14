package com.bookingapp.adapter.out.persistence.repository;

import com.bookingapp.adapter.out.persistence.entity.AccommodationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAccommodationRepository extends JpaRepository<AccommodationEntity, Long> {
}
