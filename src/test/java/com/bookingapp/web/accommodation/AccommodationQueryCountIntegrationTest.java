package com.bookingapp.web.accommodation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookingapp.domain.model.enums.AccommodationType;
import com.bookingapp.web.support.AbstractControllerIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class AccommodationQueryCountIntegrationTest
        extends AbstractControllerIntegrationTest {

    private static final int ACCOMMODATION_COUNT = 20;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void listAccommodations_shouldNotProduceNPlusOneQueries() throws Exception {
        for (int i = 0; i < ACCOMMODATION_COUNT; i++) {
            persistAccommodation(
                    AccommodationType.APARTMENT,
                    "Krakow-" + i,
                    "Apartment-" + i,
                    List.of(
                            "wifi",
                            "parking",
                            "kitchen"
                    ),
                    BigDecimal.valueOf(100 + i),
                    1
            );
        }

        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();

        statistics.clear();

        mockMvc.perform(get("/accommodations"))
                .andExpect(status().isOk());

        long queryCount = statistics.getPrepareStatementCount();

        System.out.println(
                "SQL statements for %d accommodations: %d"
                        .formatted(ACCOMMODATION_COUNT, queryCount)
        );

        assertThat(queryCount)
                .as("Accommodation list should not suffer from N+1 queries")
                .isLessThanOrEqualTo(3);
    }
}