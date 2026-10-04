package com.bookingapp.web.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.PageResult;
import com.bookingapp.domain.model.User;
import com.bookingapp.domain.model.enums.AccommodationType;
import com.bookingapp.domain.model.enums.UserRole;
import com.bookingapp.exception.GlobalExceptionHandler;
import com.bookingapp.infrastructure.security.AuthRateLimiter;
import com.bookingapp.infrastructure.security.JwtTokenService;
import com.bookingapp.persistence.UserRepositoryImpl;
import com.bookingapp.service.AccommodationService;
import com.bookingapp.service.AuthService;
import com.bookingapp.service.BookingService;
import com.bookingapp.service.PaymentService;
import com.bookingapp.service.UserService;
import com.bookingapp.web.controller.AccommodationController;
import com.bookingapp.web.controller.AuthController;
import com.bookingapp.web.controller.BookingController;
import com.bookingapp.web.controller.PaymentController;
import com.bookingapp.web.controller.UserController;
import com.bookingapp.web.dto.CreateAccommodationRequest;
import com.bookingapp.web.mapper.AccommodationWebMapperImpl;
import com.bookingapp.web.mapper.AuthWebMapperImpl;
import com.bookingapp.web.mapper.BookingWebMapperImpl;
import com.bookingapp.web.mapper.PaymentWebMapperImpl;
import com.bookingapp.web.mapper.UserWebMapperImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApiInputValidationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UserRepositoryImpl userRepository;
    private AccommodationService accommodationService;
    private UserService userService;
    private BookingService bookingService;
    private PaymentService paymentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepositoryImpl.class);
        accommodationService = mock(AccommodationService.class);
        userService = mock(UserService.class);
        bookingService = mock(BookingService.class);
        paymentService = mock(PaymentService.class);
        JwtTokenService tokens = mock(JwtTokenService.class);
        when(tokens.generateToken(any())).thenReturn("test-token");
        AuthService authService = new AuthService(userRepository, passwordEncoder, tokens);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AuthController(authService, new AuthWebMapperImpl(), mock(AuthRateLimiter.class)),
                new AccommodationController(accommodationService, new AccommodationWebMapperImpl()),
                new UserController(userService, new UserWebMapperImpl()),
                new BookingController(bookingService, new BookingWebMapperImpl()),
                new PaymentController(paymentService, new PaymentWebMapperImpl()))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @MethodSource("acceptedPasswords")
    void registrationShouldHashSupportedPasswordsWithoutTruncation(String password) throws Exception {
        when(userRepository.save(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registration(password))))
                .andExpect(status().isCreated());

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(passwordEncoder.matches(password, savedUser.getValue().getPassword())).isTrue();
        assertThat(savedUser.getValue().getPassword()).isNotEqualTo(password);
    }

    @ParameterizedTest
    @MethodSource("oversizedPasswords")
    void registrationShouldRejectPasswordsBeyondBcryptByteLimit(String password) throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registration(password))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("72 UTF-8 bytes")));
        verifyNoInteractions(userRepository);
    }

    @ParameterizedTest
    @MethodSource("oversizedPasswords")
    void loginShouldRejectOversizedPasswordsBeforeAccountLookup(String password) throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "user@example.com", "password", password))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("72 UTF-8 bytes")));
        verifyNoInteractions(userRepository);
    }

    @ParameterizedTest
    @MethodSource("acceptedPasswords")
    void loginShouldAcceptBcryptBoundaryPasswords(String password) throws Exception {
        User user = new User(1L, "user@example.com", "John", "Doe",
                passwordEncoder.encode(password), UserRole.CUSTOMER);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", user.getEmail(), "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("test-token"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "       "})
    void registrationShouldRejectEmptyOrShortPasswords(String password) throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registration(password))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"email\":\"SECRET_INPUT\",\"password\":{}}", ""})
    void unreadableBodiesShouldReturnSafeBadRequest(String body) throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/auth/register"))
                .andExpect(jsonPath("$.message").value("Malformed or unreadable request body"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("SECRET_INPUT"))));
        verifyNoInteractions(userRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/accommodations/not-a-number", "/accommodations?page=abc",
            "/accommodations?size=2147483648", "/bookings?status=NOT_A_STATUS"})
    void invalidPathAndQueryTypesShouldReturnBadRequest(String uri) throws Exception {
        mockMvc.perform(get(uri)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("has an invalid value")));
        verifyNoInteractions(accommodationService, bookingService);
    }

    @Test
    void unsupportedMethodShouldPreserveAllowHeader() throws Exception {
        mockMvc.perform(put("/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("POST")))
                .andExpect(jsonPath("$.status").value(405));
        verifyNoInteractions(userRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/plain", "application/xml"})
    void unsupportedContentTypeShouldReturn415(String contentType) throws Exception {
        mockMvc.perform(post("/auth/register").contentType(contentType).content("not-json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        verifyNoInteractions(userRepository);
    }

    @Test
    void unsupportedAcceptShouldReturn406() throws Exception {
        when(accommodationService.listAccommodations(0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0));
        mockMvc.perform(get("/accommodations").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.status").value(406));
    }

    @Test
    void unknownEndpointShouldReturn404() throws Exception {
        mockMvc.perform(get("/unknown-endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unexpectedServiceFailureShouldRemainSafe500() throws Exception {
        when(userRepository.existsByEmail("user@example.com"))
                .thenThrow(new IllegalStateException("PRIVATE_DATABASE_DETAIL"));
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registration("password123"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unexpected error occurred"));
    }

    @ParameterizedTest
    @MethodSource("oversizedUserFields")
    void userTextLimitsShouldBeEnforcedForRegistrationPutAndPatch(
            String field, String value) throws Exception {
        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH)) {
            Map<String, Object> payload = new HashMap<>(registration("password123"));
            payload.put(field, value);
            String uri = method == HttpMethod.POST ? "/auth/register" : "/users/me";
            if (method != HttpMethod.POST) {
                payload.remove("password");
            }
            mockMvc.perform(MockMvcRequestBuilders.request(method, uri)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString(field)));
        }
        verifyNoInteractions(userRepository, userService);
    }

    @ParameterizedTest
    @MethodSource("invalidAccommodationFields")
    void accommodationLimitsShouldBeEnforcedForCreatePutAndPatch(
            String field, Object value) throws Exception {
        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH)) {
            Map<String, Object> payload = new HashMap<>(accommodation());
            payload.put(field, value);
            String uri = method == HttpMethod.POST ? "/accommodations" : "/accommodations/1";
            mockMvc.perform(MockMvcRequestBuilders.request(method, uri)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString(field)));
        }
        verifyNoInteractions(accommodationService);
    }

    @Test
    void accommodationShouldAcceptDatabaseTextAndMoneyBoundaries() throws Exception {
        Map<String, Object> payload = new HashMap<>(accommodation());
        payload.put("location", "a".repeat(255));
        payload.put("size", "b".repeat(255));
        payload.put("amenities", List.of("c".repeat(255)));
        payload.put("dailyRate", new BigDecimal("9999999999.99"));
        when(accommodationService.createAccommodation(any())).thenReturn(new Accommodation(
                1L, AccommodationType.HOUSE, "Warsaw", "Studio", List.of("wifi"),
                new BigDecimal("9999999999.99"), 1));
        mockMvc.perform(post("/accommodations").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());
        ArgumentCaptor<CreateAccommodationRequest> request =
                ArgumentCaptor.forClass(CreateAccommodationRequest.class);
        verify(accommodationService).createAccommodation(request.capture());
        assertThat(request.getValue().dailyRate()).isEqualByComparingTo("9999999999.99");
    }

    @Test
    void registrationShouldAcceptDatabaseTextBoundaries() throws Exception {
        Map<String, Object> payload = new HashMap<>(registration("password123"));
        payload.put("email", "a".repeat(64) + "@" + "b".repeat(63)
                + "." + "c".repeat(63) + "." + "d".repeat(62));
        payload.put("firstName", "a".repeat(255));
        payload.put("lastName", "b".repeat(255));
        when(userRepository.save(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void createBookingAndPaymentShouldRejectNonpositiveIdentifiers(long id) throws Exception {
        mockMvc.perform(post("/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accommodationId", id,
                                "checkInDate", LocalDate.now().plusDays(1).toString(),
                                "checkOutDate", LocalDate.now().plusDays(2).toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("accommodationId")));
        mockMvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("bookingId", id))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("bookingId")));
        verifyNoInteractions(bookingService, paymentService);
    }

    @Test
    void patchShouldContinueAcceptingOmittedFields() throws Exception {
        when(userService.patchCurrentUserProfile(any())).thenReturn(new User(
                1L, "user@example.com", "John", "Doe", "encoded", UserRole.CUSTOMER));
        when(accommodationService.patchAccommodation(org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenReturn(new Accommodation(1L, AccommodationType.HOUSE,
                        "Warsaw", "Studio", List.of("wifi"), BigDecimal.TEN, 1));
        mockMvc.perform(MockMvcRequestBuilders.patch("/users/me")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.patch("/accommodations/1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    private static Stream<String> acceptedPasswords() {
        return Stream.of("password", "a".repeat(72), "я".repeat(36), "🔑".repeat(18));
    }

    private static Stream<String> oversizedPasswords() {
        return Stream.of("a".repeat(73), "я".repeat(37), "🔑".repeat(19), "a".repeat(1000));
    }

    private static Stream<Arguments> oversizedUserFields() {
        return Stream.of(Arguments.of("email", "a".repeat(64) + "@" + "b".repeat(63)
                        + "." + "c".repeat(63) + "." + "d".repeat(63)),
                Arguments.of("firstName", "a".repeat(256)),
                Arguments.of("lastName", "b".repeat(256)));
    }

    private static Stream<Arguments> invalidAccommodationFields() {
        return Stream.of(Arguments.of("location", "a".repeat(256)),
                Arguments.of("size", "b".repeat(256)),
                Arguments.of("amenities", List.of("c".repeat(256))),
                Arguments.of("amenities", List.of(" ")),
                Arguments.of("dailyRate", new BigDecimal("10000000000.00")),
                Arguments.of("dailyRate", new BigDecimal("1.001")),
                Arguments.of("dailyRate", new BigDecimal("-0.01")));
    }

    private static Map<String, Object> registration(String password) {
        return Map.of("email", "user@example.com", "firstName", "John", "lastName", "Doe",
                "password", password);
    }

    private static Map<String, Object> accommodation() {
        return Map.of("type", "HOUSE", "location", "Warsaw", "size", "Studio",
                "amenities", List.of("wifi"), "dailyRate", new BigDecimal("100.00"),
                "availability", 1);
    }
}
