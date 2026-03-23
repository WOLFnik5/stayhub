package com.bookingapp.adapter.in.web.user;

import com.bookingapp.adapter.in.web.ControllerTestSecurityConfig;
import com.bookingapp.adapter.in.web.controller.UserController;
import com.bookingapp.adapter.in.web.mapper.UserWebMapper;
import com.bookingapp.application.port.in.user.GetCurrentUserProfileUseCase;
import com.bookingapp.application.port.in.user.UpdateCurrentUserProfileUseCase;
import com.bookingapp.application.port.in.user.UpdateUserRoleUseCase;
import com.bookingapp.adapter.in.web.exception.GlobalExceptionHandler;
import com.bookingapp.domain.enums.UserRole;
import com.bookingapp.domain.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = UserController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.bookingapp\\.infrastructure\\.security\\..*"
        )
)
@Import({ControllerTestSecurityConfig.class, GlobalExceptionHandler.class, UserWebMapper.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetCurrentUserProfileUseCase getCurrentUserProfileUseCase;

    @MockitoBean
    private UpdateCurrentUserProfileUseCase updateCurrentUserProfileUseCase;

    @MockitoBean
    private UpdateUserRoleUseCase updateUserRoleUseCase;

    @Test
    void getCurrentUserProfileShouldReturnUnauthorizedWhenAnonymous() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCurrentUserProfileShouldReturnProfileJson() throws Exception {
        when(getCurrentUserProfileUseCase.getCurrentUserProfile()).thenReturn(
                new User(5L, "user@example.com", "John", "Doe", "encoded", UserRole.CUSTOMER)
        );

        mockMvc.perform(get("/users/me").with(user("user@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void patchCurrentUserProfileShouldReturnUpdatedJson() throws Exception {
        when(getCurrentUserProfileUseCase.getCurrentUserProfile()).thenReturn(
                new User(5L, "user@example.com", "John", "Doe", "encoded", UserRole.CUSTOMER)
        );
        when(updateCurrentUserProfileUseCase.updateCurrentUserProfile(any())).thenReturn(
                new User(5L, "updated@example.com", "Jane", "Doe", "encoded", UserRole.CUSTOMER)
        );

        mockMvc.perform(patch("/users/me")
                        .with(user("user@example.com").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "updated@example.com",
                                  "firstName": "Jane"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.email").value("updated@example.com"))
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        verify(updateCurrentUserProfileUseCase).updateCurrentUserProfile(any());
    }

    @Test
    void updateUserRoleShouldReturnForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(put("/users/10/role")
                        .with(user("user@example.com").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUserRoleShouldReturnUpdatedUserForAdmin() throws Exception {
        when(updateUserRoleUseCase.updateUserRole(any())).thenReturn(
                new User(10L, "user@example.com", "John", "Doe", "encoded", UserRole.ADMIN)
        );

        mockMvc.perform(put("/users/10/role")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void updateCurrentUserProfileShouldReturnValidationError() throws Exception {
        mockMvc.perform(put("/users/me")
                        .with(user("user@example.com").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "",
                                  "firstName": "",
                                  "lastName": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/users/me"));
    }
}
