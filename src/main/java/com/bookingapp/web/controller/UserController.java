package com.bookingapp.web.controller;

import com.bookingapp.domain.model.User;
import com.bookingapp.service.UserService;
import com.bookingapp.web.dto.PatchCurrentUserRequest;
import com.bookingapp.web.dto.UpdateCurrentUserRequest;
import com.bookingapp.web.dto.UpdateUserRoleRequest;
import com.bookingapp.web.dto.UserProfileResponse;
import com.bookingapp.web.mapper.UserWebMapper;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController implements UserApi {

    private final UserService userService;
    private final UserWebMapper userWebMapper;

    public UserController(
            UserService userService,
            UserWebMapper userWebMapper
    ) {
        this.userService = userService;
        this.userWebMapper = userWebMapper;
    }

    @Override
    public UserProfileResponse updateUserRole(Long id, UpdateUserRoleRequest request) {
        User updatedUser = userService.updateUserRole(id, request.role());
        return userWebMapper.toResponse(updatedUser);
    }

    @Override
    public UserProfileResponse getCurrentUserProfile() {
        return userWebMapper.toResponse(userService.getCurrentUserProfile());
    }

    @Override
    public UserProfileResponse updateCurrentUserProfile(UpdateCurrentUserRequest request) {
        User updatedUser = userService.updateCurrentUserProfile(
                request.email(),
                request.firstName(),
                request.lastName()
        );
        return userWebMapper.toResponse(updatedUser);
    }

    @Override
    public UserProfileResponse patchCurrentUserProfile(PatchCurrentUserRequest request) {
        User updatedUser = userService.patchCurrentUserProfile(request);
        return userWebMapper.toResponse(updatedUser);
    }
}
