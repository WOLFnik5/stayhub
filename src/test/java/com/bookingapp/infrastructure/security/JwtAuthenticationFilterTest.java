package com.bookingapp.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bookingapp.domain.model.User;
import com.bookingapp.domain.model.enums.UserRole;
import com.bookingapp.persistence.UserRepositoryImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void buildsAuthenticationFromCurrentDatabaseUser() throws Exception {
        JwtTokenService tokenService = mock(JwtTokenService.class);
        UserRepositoryImpl repository = mock(UserRepositoryImpl.class);
        RestAuthenticationEntryPoint entryPoint = mock(RestAuthenticationEntryPoint.class);
        User currentUser = new User(7L, "current@example.com", "Current", "User",
                "password-hash", UserRole.ADMIN);
        when(tokenService.parseUserId("token")).thenReturn(7L);
        when(repository.findById(7L)).thenReturn(Optional.of(currentUser));

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                tokenService, repository, entryPoint, objectMapper());
        MockHttpServletRequest request = requestWithToken("token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (request1, response1) -> {
            AuthenticatedUserPrincipal principal = (AuthenticatedUserPrincipal)
                    SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            assertThat(principal.userId()).isEqualTo(7L);
            assertThat(principal.email()).isEqualTo("current@example.com");
            assertThat(principal.role()).isEqualTo(UserRole.ADMIN);
            assertThat(principal.authorities()).extracting("authority")
                    .containsExactly("ROLE_ADMIN");
        });
    }

    @Test
    void tokenParsingFailureReturns401WithoutRepositoryLookup() throws Exception {
        JwtTokenService tokenService = mock(JwtTokenService.class);
        UserRepositoryImpl repository = mock(UserRepositoryImpl.class);
        RestAuthenticationEntryPoint entryPoint = mock(RestAuthenticationEntryPoint.class);
        when(tokenService.parseUserId("bad-token"))
                .thenThrow(new IllegalArgumentException("invalid token"));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                tokenService, repository, entryPoint, objectMapper());
        MockHttpServletRequest request = requestWithToken("bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (request1, response1) -> {
            throw new AssertionError("downstream chain should not run");
        });

        org.mockito.Mockito.verify(entryPoint).commence(
                org.mockito.ArgumentMatchers.same(request),
                org.mockito.ArgumentMatchers.same(response),
                org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    @Test
    void repositoryFailureReturns500AndDoesNotBecome401() throws Exception {
        JwtTokenService tokenService = mock(JwtTokenService.class);
        UserRepositoryImpl repository = mock(UserRepositoryImpl.class);
        RestAuthenticationEntryPoint entryPoint = mock(RestAuthenticationEntryPoint.class);
        when(tokenService.parseUserId("token")).thenReturn(7L);
        when(repository.findById(7L)).thenThrow(
                new DataAccessResourceFailureException("database unavailable"));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                tokenService, repository, entryPoint, objectMapper());
        MockHttpServletRequest request = requestWithToken("token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (request1, response1) -> {
            throw new AssertionError("downstream chain should not run");
        });

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).contains("Authentication service unavailable");
        org.mockito.Mockito.verifyNoInteractions(entryPoint);
    }

    @Test
    void downstreamFailureIsNotConvertedToInvalidJwt() {
        JwtTokenService tokenService = mock(JwtTokenService.class);
        UserRepositoryImpl repository = mock(UserRepositoryImpl.class);
        RestAuthenticationEntryPoint entryPoint = mock(RestAuthenticationEntryPoint.class);
        when(tokenService.parseUserId("token")).thenReturn(7L);
        when(repository.findById(7L)).thenReturn(Optional.of(new User(
                7L, "current@example.com", "Current", "User", "hash", UserRole.CUSTOMER)));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                tokenService, repository, entryPoint, objectMapper());

        assertThatThrownBy(() -> filter.doFilter(
                requestWithToken("token"),
                new MockHttpServletResponse(),
                (request, response) -> {
                    throw new ServletException("downstream failure");
                }))
                .isInstanceOf(ServletException.class)
                .hasMessage("downstream failure");
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        request.setRequestURI("/users/me");
        return request;
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
