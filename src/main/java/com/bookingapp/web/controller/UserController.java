package com.bookingapp.web.controller;

import com.bookingapp.service.UserService;
import com.bookingapp.web.dto.PatchCurrentUserRequest;
import com.bookingapp.web.dto.UpdateCurrentUserRequest;
import com.bookingapp.web.dto.UpdateUserRoleRequest;
import com.bookingapp.web.dto.UserProfileResponse;
import com.bookingapp.web.mapper.UserWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "User profile and role management")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserWebMapper userWebMapper;

    @PutMapping("/{id}/role")
    @Operation(summary = "Update user role", security = @SecurityRequirement(name = "bearerAuth"))
    public UserProfileResponse updateUserRole(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateUserRoleRequest request
    ) {
        return userWebMapper.toResponse(userService.updateUserRole(id, request.role()));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user profile",
            security = @SecurityRequirement(name = "bearerAuth"))
    public UserProfileResponse getCurrentUserProfile() {
        return userWebMapper.toResponse(userService.getCurrentUserProfile());
    }

    @PutMapping("/me")
    @Operation(summary = "Replace current user profile",
            security = @SecurityRequirement(name = "bearerAuth"))
    public UserProfileResponse updateCurrentUserProfile(
            @Valid @RequestBody UpdateCurrentUserRequest request
    ) {
        return userWebMapper.toResponse(userService.updateCurrentUserProfile(request));
    }

    @PatchMapping("/me")
    @Operation(summary = "Partially update current user profile",
            security = @SecurityRequirement(name = "bearerAuth"))
    public UserProfileResponse patchCurrentUserProfile(
            @Valid @RequestBody PatchCurrentUserRequest request
    ) {
        return userWebMapper.toResponse(userService.patchCurrentUserProfile(request));
    }
}
