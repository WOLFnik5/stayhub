package com.bookingapp.web.controller;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.service.AccommodationService;
import com.bookingapp.web.dto.AccommodationDetailResponse;
import com.bookingapp.web.dto.AccommodationListResponse;
import com.bookingapp.web.dto.CreateAccommodationRequest;
import com.bookingapp.web.dto.PatchAccommodationRequest;
import com.bookingapp.web.dto.UpdateAccommodationRequest;
import com.bookingapp.web.mapper.AccommodationWebMapper;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccommodationController implements AccommodationApi {

    private final AccommodationService accommodationService;
    private final AccommodationWebMapper accommodationWebMapper;

    public AccommodationController(
            AccommodationService accommodationService,
            AccommodationWebMapper accommodationWebMapper
    ) {
        this.accommodationService = accommodationService;
        this.accommodationWebMapper = accommodationWebMapper;
    }

    @Override
    public AccommodationDetailResponse createAccommodation(CreateAccommodationRequest request) {
        Accommodation createdAccommodation = accommodationService.createAccommodation(
                request.type(),
                request.location(),
                request.size(),
                request.amenities(),
                request.dailyRate(),
                request.availability()
        );
        return accommodationWebMapper.toDetailResponse(createdAccommodation);
    }

    @Override
    public List<AccommodationListResponse> listAccommodations() {
        return accommodationService.listAccommodations().stream()
                .map(accommodationWebMapper::toListResponse)
                .toList();
    }

    @Override
    public AccommodationDetailResponse getAccommodationById(Long id) {
        return accommodationWebMapper
                .toDetailResponse(accommodationService.getAccommodationById(id));
    }

    @Override
    public AccommodationDetailResponse updateAccommodation(
            Long id,
            UpdateAccommodationRequest request
    ) {
        Accommodation updatedAccommodation = accommodationService.updateAccommodation(
                id,
                request.type(),
                request.location(),
                request.size(),
                request.amenities(),
                request.dailyRate(),
                request.availability()
        );
        return accommodationWebMapper.toDetailResponse(updatedAccommodation);
    }

    @Override
    public AccommodationDetailResponse patchAccommodation(
            Long id,
            PatchAccommodationRequest request
    ) {
        Accommodation updatedAccommodation = accommodationService.patchAccommodation(id, request);
        return accommodationWebMapper.toDetailResponse(updatedAccommodation);
    }

    @Override
    public void deleteAccommodation(Long id) {
        accommodationService.deleteAccommodation(id);
    }
}
