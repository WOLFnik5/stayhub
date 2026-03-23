package com.bookingapp.adapter.in.web.controller;

import com.bookingapp.adapter.in.web.dto.AccommodationDetailResponse;
import com.bookingapp.adapter.in.web.dto.AccommodationListResponse;
import com.bookingapp.adapter.in.web.mapper.AccommodationWebMapper;
import com.bookingapp.adapter.in.web.dto.CreateAccommodationRequest;
import com.bookingapp.adapter.in.web.dto.PatchAccommodationRequest;
import com.bookingapp.adapter.in.web.dto.UpdateAccommodationRequest;
import com.bookingapp.application.port.in.accommodation.CreateAccommodationUseCase;
import com.bookingapp.application.port.in.accommodation.DeleteAccommodationUseCase;
import com.bookingapp.application.port.in.accommodation.GetAccommodationByIdUseCase;
import com.bookingapp.application.port.in.accommodation.ListAccommodationsUseCase;
import com.bookingapp.application.port.in.accommodation.UpdateAccommodationUseCase;
import com.bookingapp.domain.model.Accommodation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.util.List;

@RestController
@RequestMapping("/accommodations")
@Tag(name = "Accommodations", description = "Accommodation catalog management")
public class AccommodationController {

    private final CreateAccommodationUseCase createAccommodationUseCase;
    private final GetAccommodationByIdUseCase getAccommodationByIdUseCase;
    private final ListAccommodationsUseCase listAccommodationsUseCase;
    private final UpdateAccommodationUseCase updateAccommodationUseCase;
    private final DeleteAccommodationUseCase deleteAccommodationUseCase;
    private final AccommodationWebMapper accommodationWebMapper;

    public AccommodationController(
            CreateAccommodationUseCase createAccommodationUseCase,
            GetAccommodationByIdUseCase getAccommodationByIdUseCase,
            ListAccommodationsUseCase listAccommodationsUseCase,
            UpdateAccommodationUseCase updateAccommodationUseCase,
            DeleteAccommodationUseCase deleteAccommodationUseCase,
            AccommodationWebMapper accommodationWebMapper
    ) {
        this.createAccommodationUseCase = createAccommodationUseCase;
        this.getAccommodationByIdUseCase = getAccommodationByIdUseCase;
        this.listAccommodationsUseCase = listAccommodationsUseCase;
        this.updateAccommodationUseCase = updateAccommodationUseCase;
        this.deleteAccommodationUseCase = deleteAccommodationUseCase;
        this.accommodationWebMapper = accommodationWebMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create accommodation", security = @SecurityRequirement(name = "bearerAuth"))
    public AccommodationDetailResponse createAccommodation(@Valid @RequestBody CreateAccommodationRequest request) {
        Accommodation createdAccommodation = createAccommodationUseCase.createAccommodation(
                accommodationWebMapper.toCreateCommand(request)
        );
        return accommodationWebMapper.toDetailResponse(createdAccommodation);
    }

    @GetMapping
    @Operation(summary = "List available accommodations")
    public List<AccommodationListResponse> listAccommodations() {
        return listAccommodationsUseCase.listAccommodations().stream()
                .map(accommodationWebMapper::toListResponse)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get accommodation by id")
    public AccommodationDetailResponse getAccommodationById(@PathVariable Long id) {
        return accommodationWebMapper.toDetailResponse(getAccommodationByIdUseCase.getAccommodationById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace accommodation", security = @SecurityRequirement(name = "bearerAuth"))
    public AccommodationDetailResponse updateAccommodation(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAccommodationRequest request
    ) {
        Accommodation updatedAccommodation = updateAccommodationUseCase.updateAccommodation(
                accommodationWebMapper.toUpdateCommand(id, request)
        );
        return accommodationWebMapper.toDetailResponse(updatedAccommodation);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Partially update accommodation", security = @SecurityRequirement(name = "bearerAuth"))
    public AccommodationDetailResponse patchAccommodation(
            @PathVariable Long id,
            @Valid @RequestBody PatchAccommodationRequest request
    ) {
        Accommodation currentAccommodation = getAccommodationByIdUseCase.getAccommodationById(id);
        Accommodation updatedAccommodation = updateAccommodationUseCase.updateAccommodation(
                accommodationWebMapper.toPatchCommand(id, request, currentAccommodation)
        );
        return accommodationWebMapper.toDetailResponse(updatedAccommodation);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete accommodation", security = @SecurityRequirement(name = "bearerAuth"))
    public void deleteAccommodation(@PathVariable Long id) {
        deleteAccommodationUseCase.deleteAccommodation(id);
    }
}
