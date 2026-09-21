package com.bookingapp.web.accommodation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookingapp.domain.model.User;
import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.domain.model.enums.UserRole;
import com.bookingapp.infrastructure.security.JwtTokenService;
import com.bookingapp.testsupport.PostgreSqlLiquibaseIntegrationTestSupport;
import com.bookingapp.web.support.ControllerIntegrationTestConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ControllerIntegrationTestConfiguration.class)
class AccommodationDeleteConstraintIntegrationTest
        extends PostgreSqlLiquibaseIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenService jwtTokenService;

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM payments");
        jdbcTemplate.update("DELETE FROM bookings");
        jdbcTemplate.update("DELETE FROM accommodations");
        jdbcTemplate.update("DELETE FROM users");
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"PENDING", "CANCELED"})
    void deleteAccommodationShouldReturnConflictWhenBookingExists(
            BookingStatus bookingStatus
    ) throws Exception {
        User admin = insertUser(UserRole.ADMIN);
        User customer = insertUser(UserRole.CUSTOMER);
        Long accommodationId = insertAccommodation();
        Long bookingId = jdbcTemplate.queryForObject("""
                INSERT INTO bookings (accommodation_id, user_id, check_in_date, check_out_date, status)
                VALUES (?, ?, CURRENT_DATE + 2, CURRENT_DATE + 4, ?)
                RETURNING id
                """, Long.class, accommodationId, customer.getId(), bookingStatus.name());

        mockMvc.perform(delete("/accommodations/{id}", accommodationId)
                        .header("Authorization", "Bearer " + jwtTokenService.generateToken(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Accommodation cannot be deleted because it has bookings"));

        assertThat(countById("accommodations", accommodationId)).isOne();
        assertThat(countById("bookings", bookingId)).isOne();
    }

    private User insertUser(UserRole role) {
        String email = UUID.randomUUID() + "@example.com";
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO users (email, first_name, last_name, password, role)
                VALUES (?, 'Test', 'User', 'password', ?) RETURNING id
                """, Long.class, email, role.name());
        return new User(id, email, "Test", "User", "password", role);
    }

    private Long insertAccommodation() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO accommodations (type, location, size, daily_rate, availability)
                VALUES ('HOUSE', 'Warsaw', 'Test house', 100, 1) RETURNING id
                """, Long.class);
    }

    private long countById(String table, Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE id = ?", Long.class, id);
    }
}
