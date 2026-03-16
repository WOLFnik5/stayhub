package com.bookingapp.application.service.accommodation;

import com.bookingapp.application.model.CreateAccommodationCommand;
import com.bookingapp.application.model.UpdateAccommodationCommand;
import com.bookingapp.application.port.in.accommodation.CreateAccommodationUseCase;
import com.bookingapp.application.port.in.accommodation.DeleteAccommodationUseCase;
import com.bookingapp.application.port.in.accommodation.GetAccommodationByIdUseCase;
import com.bookingapp.application.port.in.accommodation.ListAccommodationsUseCase;
import com.bookingapp.application.port.in.accommodation.UpdateAccommodationUseCase;
import com.bookingapp.application.port.out.integration.EventPublisherPort;
import com.bookingapp.application.port.out.persistence.AccommodationRepositoryPort;
import com.bookingapp.domain.exception.BusinessValidationException;
import com.bookingapp.domain.exception.EntityNotFoundDomainException;
import com.bookingapp.domain.model.Accommodation;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccommodationApplicationService implements CreateAccommodationUseCase,
        GetAccommodationByIdUseCase,
        ListAccommodationsUseCase,
        UpdateAccommodationUseCase,
        DeleteAccommodationUseCase {

    private final AccommodationRepositoryPort accommodationRepositoryPort;
    private final EventPublisherPort eventPublisherPort;

    public AccommodationApplicationService(
            AccommodationRepositoryPort accommodationRepositoryPort,
            EventPublisherPort eventPublisherPort
    ) {
        this.accommodationRepositoryPort = accommodationRepositoryPort;
        this.eventPublisherPort = eventPublisherPort;
    }

    @Override
    @Transactional
    public Accommodation createAccommodation(CreateAccommodationCommand command) {
        if (command == null) {
            throw new BusinessValidationException("Create accommodation command must not be null");
        }

        Accommodation accommodationToSave = Accommodation.createNew(
                command.type(),
                command.location(),
                command.size(),
                command.amenities(),
                command.dailyRate(),
                command.availability()
        );

        Accommodation savedAccommodation = accommodationRepositoryPort.save(accommodationToSave);
        eventPublisherPort.publishAccommodationCreated(savedAccommodation);
        return savedAccommodation;
    }

    @Override
    public Accommodation getAccommodationById(Long accommodationId) {
        return accommodationRepositoryPort.findById(accommodationId)
                .orElseThrow(() -> new EntityNotFoundDomainException(
                        "Accommodation with id '" + accommodationId + "' was not found"
                ));
    }

    @Override
    public List<Accommodation> listAccommodations() {
        return accommodationRepositoryPort.findAll().stream()
                .filter(accommodation -> accommodation.getAvailability() > 0)
                .toList();
    }

    @Override
    @Transactional
    public Accommodation updateAccommodation(UpdateAccommodationCommand command) {
        if (command == null) {
            throw new BusinessValidationException("Update accommodation command must not be null");
        }

        Accommodation existingAccommodation = getAccommodationById(command.accommodationId());

        Accommodation updatedAccommodation = existingAccommodation.updateDetails(
                command.type(),
                command.location(),
                command.size(),
                command.amenities(),
                command.dailyRate(),
                command.availability()
        );

        return accommodationRepositoryPort.save(updatedAccommodation);
    }

    @Override
    @Transactional
    public void deleteAccommodation(Long accommodationId) {
        if (!accommodationRepositoryPort.existsById(accommodationId)) {
            throw new EntityNotFoundDomainException(
                    "Accommodation with id '" + accommodationId + "' was not found"
            );
        }

        accommodationRepositoryPort.deleteById(accommodationId);
    }
}