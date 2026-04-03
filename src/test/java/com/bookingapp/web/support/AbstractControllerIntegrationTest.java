package com.bookingapp.web.support;

import com.bookingapp.domain.repository.AccommodationRepository;
import com.bookingapp.domain.repository.BookingRepository;
import com.bookingapp.infrastructure.kafka.KafkaEventPublisher;
import com.bookingapp.domain.repository.PaymentRepository;
import com.bookingapp.domain.repository.UserRepository;
import com.bookingapp.domain.model.enums.AccommodationType;
import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.domain.model.enums.PaymentStatus;
import com.bookingapp.domain.model.enums.UserRole;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.domain.model.User;
import com.bookingapp.infrastructure.security.JwtTokenService;
import com.bookingapp.infrastructure.stripe.StripePaymentProvider;
import com.bookingapp.testsupport.PostgreSqlIntegrationTestSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ControllerIntegrationTestConfiguration.class)
public abstract class AbstractControllerIntegrationTest extends PostgreSqlIntegrationTestSupport {

    protected static final String DEFAULT_PASSWORD = "Password123!";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JwtTokenService jwtTokenService;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected AccommodationRepository accommodationRepository;

    @Autowired
    protected BookingRepository bookingRepository;

    @Autowired
    protected PaymentRepository paymentRepository;

    @PersistenceContext
    protected EntityManager entityManager;

    @Autowired
    protected KafkaEventPublisher kafkaEventPublisher;

    @Autowired
    protected StripePaymentProvider stripePaymentProvider;

    @BeforeEach
    void resetExternalMocks() {
        Mockito.reset(kafkaEventPublisher, stripePaymentProvider);
    }

    @AfterEach
    @Transactional
    void cleanDatabase() {
        entityManager.createQuery("DELETE FROM PaymentEntity").executeUpdate();
        entityManager.createQuery("DELETE FROM BookingEntity").executeUpdate();
        entityManager.createQuery("DELETE FROM AccommodationEntity").executeUpdate();
        entityManager.createQuery("DELETE FROM UserEntity").executeUpdate();
    }

    protected User persistAdmin(String email) {
        return persistUser(email, "Admin", "User", DEFAULT_PASSWORD, UserRole.ADMIN);
    }

    protected User persistCustomer(String email) {
        return persistUser(email, "Test", "Customer", DEFAULT_PASSWORD, UserRole.CUSTOMER);
    }

    protected User persistUser(String email, String firstName, String lastName, String rawPassword, UserRole role) {
        return userRepository.save(
                new User(
                        null,
                        email,
                        firstName,
                        lastName,
                        passwordEncoder.encode(rawPassword),
                        role
                )
        );
    }

    protected Accommodation persistAccommodation(
            AccommodationType type,
            String location,
            String size,
            List<String> amenities,
            BigDecimal dailyRate,
            int availability
    ) {
        return accommodationRepository.save(
                new Accommodation(null, type, location, size, amenities, dailyRate, availability)
        );
    }

    protected Booking persistBooking(
            LocalDate checkInDate,
            LocalDate checkOutDate,
            Long accommodationId,
            Long userId,
            BookingStatus status
    ) {
        return bookingRepository.save(
                new Booking(null, checkInDate, checkOutDate, accommodationId, userId, status)
        );
    }

    protected Payment persistPayment(
            PaymentStatus status,
            Long bookingId,
            String sessionUrl,
            String sessionId,
            BigDecimal amountToPay
    ) {
        return paymentRepository.save(
                new Payment(null, status, bookingId, sessionUrl, sessionId, amountToPay)
        );
    }

    protected String authorizationHeader(User user) {
        return "Bearer " + jwtTokenService.generateToken(user);
    }

    protected String asJson(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    protected LocalDate futureDate(int daysFromNow) {
        return LocalDate.now().plusDays(daysFromNow);
    }

    protected long countEntities(String entityName) {
        return entityManager.createQuery(
                "SELECT COUNT(e) FROM " + entityName + " e",
                Long.class
        ).getSingleResult();
    }

    protected boolean entityExists(String entityName, Long id) {
        Long count = entityManager.createQuery(
                "SELECT COUNT(e) FROM " + entityName + " e WHERE e.id = :id",
                Long.class
        ).setParameter("id", id).getSingleResult();
        return count > 0;
    }
}
