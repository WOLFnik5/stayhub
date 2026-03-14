package com.bookingapp.adapter.in.web.user;

import com.bookingapp.application.port.in.user.GetCurrentUserProfileUseCase;
import com.bookingapp.application.port.in.user.UpdateCurrentUserProfileUseCase;
import com.bookingapp.application.port.in.user.UpdateUserRoleUseCase;
import com.bookingapp.domain.model.User;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final GetCurrentUserProfileUseCase getCurrentUserProfileUseCase;
    private final UpdateCurrentUserProfileUseCase updateCurrentUserProfileUseCase;
    private final UpdateUserRoleUseCase updateUserRoleUseCase;
    private final UserWebMapper userWebMapper;

    public UserController(
            GetCurrentUserProfileUseCase getCurrentUserProfileUseCase,
            UpdateCurrentUserProfileUseCase updateCurrentUserProfileUseCase,
            UpdateUserRoleUseCase updateUserRoleUseCase,
            UserWebMapper userWebMapper
    ) {
        this.getCurrentUserProfileUseCase = getCurrentUserProfileUseCase;
        this.updateCurrentUserProfileUseCase = updateCurrentUserProfileUseCase;
        this.updateUserRoleUseCase = updateUserRoleUseCase;
        this.userWebMapper = userWebMapper;
    }

    @PutMapping("/{id}/role")
    public UserProfileResponse updateUserRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRoleRequest request
    ) {
        User updatedUser = updateUserRoleUseCase.updateUserRole(
                userWebMapper.toUpdateUserRoleCommand(id, request)
        );
        return userWebMapper.toResponse(updatedUser);
    }

    @GetMapping("/me")
    public UserProfileResponse getCurrentUserProfile() {
        return userWebMapper.toResponse(getCurrentUserProfileUseCase.getCurrentUserProfile());
    }

    @PutMapping("/me")
    public UserProfileResponse updateCurrentUserProfile(
            @Valid @RequestBody UpdateCurrentUserRequest request
    ) {
        /*
         * Password changes are intentionally excluded from profile endpoints to keep
         * this API focused on profile data and avoid mixing credential flows here.
         */
        User updatedUser = updateCurrentUserProfileUseCase.updateCurrentUserProfile(
                userWebMapper.toUpdateProfileCommand(request)
        );
        return userWebMapper.toResponse(updatedUser);
    }

    @PatchMapping("/me")
    public UserProfileResponse patchCurrentUserProfile(
            @Valid @RequestBody PatchCurrentUserRequest request
    ) {
        User currentUser = getCurrentUserProfileUseCase.getCurrentUserProfile();
        User updatedUser = updateCurrentUserProfileUseCase.updateCurrentUserProfile(
                userWebMapper.toPatchProfileCommand(request, currentUser)
        );
        return userWebMapper.toResponse(updatedUser);
    }
}
