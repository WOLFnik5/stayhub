package com.bookingapp.web.controller;

import com.bookingapp.web.dto.AccommodationDetailResponse;
import com.bookingapp.web.dto.AccommodationListResponse;
import com.bookingapp.web.dto.CreateAccommodationRequest;
import com.bookingapp.web.dto.PatchAccommodationRequest;
import com.bookingapp.web.dto.UpdateAccommodationRequest;
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

@RequestMapping("/accommodations")
@Tag(name = "Accommodations", description = "Accommodation catalog management")
public interface AccommodationApi {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create accommodation",
            security = @SecurityRequirement(name = "bearerAuth"))
    AccommodationDetailResponse createAccommodation(
            @Valid @RequestBody CreateAccommodationRequest request
    );

    @GetMapping
    @Operation(summary = "List available accommodations")
    List<AccommodationListResponse> listAccommodations();

    @GetMapping("/{id}")
    @Operation(summary = "Get accommodation by id")
    AccommodationDetailResponse getAccommodationById(@PathVariable("id") Long id);

    @PutMapping("/{id}")
    @Operation(summary = "Replace accommodation",
            security = @SecurityRequirement(name = "bearerAuth"))
    AccommodationDetailResponse updateAccommodation(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateAccommodationRequest request
    );

    @PatchMapping("/{id}")
    @Operation(summary = "Partially update accommodation",
            security = @SecurityRequirement(name = "bearerAuth"))
    AccommodationDetailResponse patchAccommodation(
            @PathVariable("id") Long id,
            @Valid @RequestBody PatchAccommodationRequest request
    );

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete accommodation",
            security = @SecurityRequirement(name = "bearerAuth"))
    void deleteAccommodation(@PathVariable("id") Long id);
}
