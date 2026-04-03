package com.bookingapp.web.controller;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.service.AccommodationService;
import com.bookingapp.web.dto.AccommodationDetailResponse;
import com.bookingapp.web.dto.AccommodationListResponse;
import com.bookingapp.web.dto.CreateAccommodationRequest;
import com.bookingapp.web.dto.PatchAccommodationRequest;
import com.bookingapp.web.dto.UpdateAccommodationRequest;
import com.bookingapp.web.mapper.AccommodationWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accommodations")
@Tag(name = "Accommodations", description = "Accommodation catalog management")
public class AccommodationController {

    private final AccommodationService accommodationService;
    private final AccommodationWebMapper accommodationWebMapper;

    public AccommodationController(
            AccommodationService accommodationService,
            AccommodationWebMapper accommodationWebMapper
    ) {
        this.accommodationService = accommodationService;
        this.accommodationWebMapper = accommodationWebMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create accommodation",
            security = @SecurityRequirement(name = "bearerAuth"))
    public AccommodationDetailResponse createAccommodation(
            @Valid @RequestBody CreateAccommodationRequest request
    ) {
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

    @GetMapping
    @Operation(summary = "List available accommodations")
    public List<AccommodationListResponse> listAccommodations() {
        return accommodationService.listAccommodations().stream()
                .map(accommodationWebMapper::toListResponse)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get accommodation by id")
    public AccommodationDetailResponse getAccommodationById(@PathVariable("id") Long id) {
        return accommodationWebMapper
                .toDetailResponse(accommodationService.getAccommodationById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace accommodation",
            security = @SecurityRequirement(name = "bearerAuth"))
    public AccommodationDetailResponse updateAccommodation(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateAccommodationRequest request
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

    @PatchMapping("/{id}")
    @Operation(summary = "Partially update accommodation",
            security = @SecurityRequirement(name = "bearerAuth"))
    public AccommodationDetailResponse patchAccommodation(
            @PathVariable("id") Long id,
            @Valid @RequestBody PatchAccommodationRequest request
    ) {
        Accommodation updatedAccommodation = accommodationService.patchAccommodation(id, request);
        return accommodationWebMapper.toDetailResponse(updatedAccommodation);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete accommodation",
            security = @SecurityRequirement(name = "bearerAuth"))
    public void deleteAccommodation(@PathVariable("id") Long id) {
        accommodationService.deleteAccommodation(id);
    }
}
