package com.bookingapp.web.auth;

import com.bookingapp.web.controller.AuthController;
import com.bookingapp.web.dto.AuthenticationResult;
import com.bookingapp.web.dto.LoginRequest;
import com.bookingapp.web.dto.RegisterRequest;
import com.bookingapp.web.mapper.AuthWebMapperImpl;
import com.bookingapp.service.AuthService;
import com.bookingapp.exception.GlobalExceptionHandler;
import com.bookingapp.domain.model.enums.UserRole;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.bookingapp\\.infrastructure\\.security\\..*"
        )
)
@Import({GlobalExceptionHandler.class, AuthWebMapperImpl.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void registerShouldReturnCreatedAuthResponse() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(
                new AuthenticationResult("jwt-token", 1L, "user@example.com", UserRole.CUSTOMER)
        );

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "firstName": "John",
                                  "lastName": "Doe",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void loginShouldReturnAuthResponse() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(
                new AuthenticationResult("login-token", 7L, "admin@example.com", UserRole.ADMIN)
        );

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "admin@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").value("login-token"))
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.email").value("admin@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    void loginShouldReturnValidationErrorForInvalidPayload() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/auth/login"));
    }
}
