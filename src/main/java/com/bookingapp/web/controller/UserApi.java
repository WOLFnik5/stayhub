package com.bookingapp.web.controller;

import com.bookingapp.web.dto.PatchCurrentUserRequest;
import com.bookingapp.web.dto.UpdateCurrentUserRequest;
import com.bookingapp.web.dto.UpdateUserRoleRequest;
import com.bookingapp.web.dto.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/users")
@Tag(name = "Users", description = "User profile and role management")
public interface UserApi {

    @PutMapping("/{id}/role")
    @Operation(summary = "Update user role", security = @SecurityRequirement(name = "bearerAuth"))
    UserProfileResponse updateUserRole(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateUserRoleRequest request
    );

    @GetMapping("/me")
    @Operation(summary = "Get current user profile",
            security = @SecurityRequirement(name = "bearerAuth"))
    UserProfileResponse getCurrentUserProfile();

    @PutMapping("/me")
    @Operation(summary = "Replace current user profile",
            security = @SecurityRequirement(name = "bearerAuth"))
    UserProfileResponse updateCurrentUserProfile(
            @Valid @RequestBody UpdateCurrentUserRequest request
    );

    @PatchMapping("/me")
    @Operation(summary = "Partially update current user profile",
            security = @SecurityRequirement(name = "bearerAuth"))
    UserProfileResponse patchCurrentUserProfile(
            @Valid @RequestBody PatchCurrentUserRequest request
    );
}
