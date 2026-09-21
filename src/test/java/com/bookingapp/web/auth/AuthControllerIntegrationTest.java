package com.bookingapp.web.auth;

import com.bookingapp.web.dto.LoginRequest;
import com.bookingapp.web.dto.RegisterRequest;
import com.bookingapp.web.support.AbstractControllerIntegrationTest;
import com.bookingapp.domain.model.enums.UserRole;
import com.bookingapp.domain.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIntegrationTest extends AbstractControllerIntegrationTest {

    @Test
    void register_shouldBePublicAndPersistCustomer() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(new RegisterRequest(
                                "register-success@example.com",
                                "John",
                                "Doe",
                                DEFAULT_PASSWORD
                        ))))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.email").value("register-success@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        User savedUser = userRepository.findByEmail("register-success@example.com").orElseThrow();
        assertThat(savedUser.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(passwordEncoder.matches(DEFAULT_PASSWORD, savedUser.getPassword())).isTrue();
        assertThat(savedUser.getPassword()).isNotEqualTo(DEFAULT_PASSWORD);
    }

    @Test
    void login_shouldBePublicAndReturnJwtForPersistedUser() throws Exception {
        persistUser(
                "login-success@example.com",
                "Jane",
                "Doe",
                DEFAULT_PASSWORD,
                UserRole.ADMIN
        );

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(new LoginRequest(
                                "login-success@example.com",
                                DEFAULT_PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.email").value("login-success@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void registerAndLogin_shouldCanonicalizeEmailBeforeValidationAndLookup() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":" Registration.Case@Example.COM ","firstName":"John",
                                "lastName":"Doe","password":"%s"}
                                """.formatted(DEFAULT_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("registration.case@example.com"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":" REGISTRATION.CASE@example.com ","password":"%s"}
                                """.formatted(DEFAULT_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("registration.case@example.com"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"REGISTRATION.CASE@EXAMPLE.COM","firstName":"John",
                                "lastName":"Doe","password":"%s"}
                                """.formatted(DEFAULT_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("User with email 'registration.case@example.com' already exists"));
    }

    @Test
    void register_shouldCreateOnlyOneAccountForConcurrentCanonicalDuplicates() throws Exception {
        String firstRequest = """
                {"email":" Concurrent.Email@Example.COM ","firstName":"John",
                "lastName":"Doe","password":"%s"}
                """.formatted(DEFAULT_PASSWORD);
        String secondRequest = firstRequest.replace("Concurrent.Email", "CONCURRENT.EMAIL");

        List<Integer> statuses = runConcurrently(
                () -> submitRegistration(firstRequest),
                () -> submitRegistration(secondRequest)
        );

        assertThat(statuses).contains(201);
        assertThat(statuses).allMatch(status -> status == 201 || status == 400 || status == 409);
        assertThat(userRepository.findByEmail("concurrent.email@example.com")).isPresent();
        assertThat(countEntities("UserEntity")).isEqualTo(1);
    }

    @Test
    void register_shouldReturn400ForValidationErrors() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "firstName": "",
                                  "lastName": "",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/auth/register"));
    }

    @Test
    void register_shouldReturn400WhenEmailAlreadyExists() throws Exception {
        persistCustomer("register-duplicate@example.com");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(new RegisterRequest(
                                "register-duplicate@example.com",
                                "John",
                                "Doe",
                                DEFAULT_PASSWORD
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("User with email 'register-duplicate@example.com' already exists"))
                .andExpect(jsonPath("$.path").value("/auth/register"));

        assertThat(countEntities("UserEntity")).isEqualTo(1);
    }

    @Test
    void login_shouldReturn400ForInvalidCredentials() throws Exception {
        persistCustomer("login-invalid@example.com");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(new LoginRequest(
                                "login-invalid@example.com",
                                "wrong-password"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.path").value("/auth/login"));
    }

    private int submitRegistration(String request) throws Exception {
        return mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private List<Integer> runConcurrently(Callable<Integer> first, Callable<Integer> second)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<Integer> firstFuture = executor.submit(() -> awaitAndCall(first, ready, go));
            Future<Integer> secondFuture = executor.submit(() -> awaitAndCall(second, ready, go));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            return List.of(firstFuture.get(20, TimeUnit.SECONDS),
                    secondFuture.get(20, TimeUnit.SECONDS));
        } finally {
            go.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private int awaitAndCall(Callable<Integer> call, CountDownLatch ready, CountDownLatch go)
            throws Exception {
        ready.countDown();
        assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
        return call.call();
    }
}
