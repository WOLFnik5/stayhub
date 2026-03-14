package com.bookingapp.application.port.out.persistence;

import com.bookingapp.domain.model.Accommodation;

import java.util.List;
import java.util.Optional;

public interface AccommodationRepositoryPort {

    Accommodation save(Accommodation accommodation);

    Optional<Accommodation> findById(Long accommodationId);

    List<Accommodation> findAll();

    boolean existsById(Long accommodationId);

    void deleteById(Long accommodationId);
}
