package com.bookingapp.domain.repository;

import com.bookingapp.domain.model.Accommodation;
import java.util.List;
import java.util.Optional;

public interface AccommodationRepository {

    Accommodation save(Accommodation accommodation);

    Optional<Accommodation> findById(Long accommodationId);

    List<Accommodation> findAll();

    boolean existsById(Long accommodationId);

    void deleteById(Long accommodationId);
}
