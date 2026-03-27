package com.bookingapp.web.user;

import com.bookingapp.web.dto.PatchCurrentUserRequest;
import com.bookingapp.web.dto.UpdateCurrentUserRequest;
import com.bookingapp.web.dto.UpdateUserRoleRequest;
import com.bookingapp.web.support.AbstractControllerIntegrationTest;
import com.bookingapp.domain.enums.UserRole;
import com.bookingapp.domain.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerIntegrationTest extends AbstractControllerIntegrationTest {

    @Test
    void getCurrentUserProfile_shouldReturn401WhenAnonymous() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/users/me"));
    }

    @Test
    void getCurrentUserProfile_shouldReturnPersistedData() throws Exception {
        User customer = persistCustomer("profile-view@example.com");

        mockMvc.perform(get("/users/me")
                        .header("Authorization", authorizationHeader(customer)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(customer.getId()))
                .andExpect(jsonPath("$.email").value("profile-view@example.com"))
                .andExpect(jsonPath("$.firstName").value("Test"))
                .andExpect(jsonPath("$.lastName").value("Customer"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void updateCurrentUserProfile_shouldPersistChanges() throws Exception {
        User customer = persistCustomer("profile-update@example.com");

        mockMvc.perform(put("/users/me")
                        .header("Authorization", authorizationHeader(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(new UpdateCurrentUserRequest(
                                "updated-profile@example.com",
                                "Updated",
                                "Customer"
                        ))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(customer.getId()))
                .andExpect(jsonPath("$.email").value("updated-profile@example.com"))
                .andExpect(jsonPath("$.firstName").value("Updated"))
                .andExpect(jsonPath("$.lastName").value("Customer"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        User updatedUser = userRepository.findById(customer.getId()).orElseThrow();
        assertThat(updatedUser.getEmail()).isEqualTo("updated-profile@example.com");
        assertThat(updatedUser.getFirstName()).isEqualTo("Updated");
        assertThat(updatedUser.getLastName()).isEqualTo("Customer");
    }

    @Test
    void patchCurrentUserProfile_shouldPersistPartialChanges() throws Exception {
        User customer = persistCustomer("profile-patch@example.com");

        mockMvc.perform(patch("/users/me")
                        .header("Authorization", authorizationHeader(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(new PatchCurrentUserRequest(
                                "patched-profile@example.com",
                                "Patched",
                                null
                        ))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(customer.getId()))
                .andExpect(jsonPath("$.email").value("patched-profile@example.com"))
                .andExpect(jsonPath("$.firstName").value("Patched"))
                .andExpect(jsonPath("$.lastName").value("Customer"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        User updatedUser = userRepository.findById(customer.getId()).orElseThrow();
        assertThat(updatedUser.getEmail()).isEqualTo("patched-profile@example.com");
        assertThat(updatedUser.getFirstName()).isEqualTo("Patched");
        assertThat(updatedUser.getLastName()).isEqualTo("Customer");
    }

    @Test
    void updateUserRole_shouldReturn403ForCustomer() throws Exception {
        User customer = persistCustomer("profile-role-customer@example.com");
        User targetUser = persistCustomer("profile-role-target@example.com");

        mockMvc.perform(put("/users/{id}/role", targetUser.getId())
                        .header("Authorization", authorizationHeader(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(new UpdateUserRoleRequest(UserRole.ADMIN))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.path").value("/users/" + targetUser.getId() + "/role"));

        User unchangedUser = userRepository.findById(targetUser.getId()).orElseThrow();
        assertThat(unchangedUser.getRole()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    void updateUserRole_shouldUpdatePersistedRoleForAdmin() throws Exception {
        User admin = persistAdmin("profile-role-admin@example.com");
        User targetUser = persistCustomer("profile-role-change@example.com");

        mockMvc.perform(put("/users/{id}/role", targetUser.getId())
                        .header("Authorization", authorizationHeader(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(new UpdateUserRoleRequest(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(targetUser.getId()))
                .andExpect(jsonPath("$.email").value("profile-role-change@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        User updatedUser = userRepository.findById(targetUser.getId()).orElseThrow();
        assertThat(updatedUser.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void updateUserRole_shouldReturn404WhenNotFound() throws Exception {
        User admin = persistAdmin("profile-role-not-found-admin@example.com");

        mockMvc.perform(put("/users/999999/role")
                        .header("Authorization", authorizationHeader(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(new UpdateUserRoleRequest(UserRole.ADMIN))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.path").value("/users/999999/role"));
    }

    @Test
    void updateCurrentUserProfile_shouldReturn400ForValidationErrors() throws Exception {
        User customer = persistCustomer("profile-validation@example.com");

        mockMvc.perform(put("/users/me")
                        .header("Authorization", authorizationHeader(customer))
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
                .andExpect(jsonPath("$.path").value("/users/me"));
    }
}
