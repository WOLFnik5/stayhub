package com.bookingapp.web.accommodation;

import com.bookingapp.web.dto.CreateAccommodationRequest;
import com.bookingapp.web.dto.UpdateAccommodationRequest;
import com.bookingapp.web.support.AbstractControllerIntegrationTest;
import com.bookingapp.domain.model.enums.AccommodationType;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccommodationControllerIntegrationTest extends AbstractControllerIntegrationTest {

    @Test
    void listAccommodations_shouldBePublicAndReturnPersistedData() throws Exception {
        persistAccommodation(
                AccommodationType.HOUSE,
                "Warsaw",
                "2 rooms",
                List.of("wifi", "parking"),
                BigDecimal.valueOf(120),
                2
        );
        persistAccommodation(
                AccommodationType.APARTMENT,
                "Krakow",
                "Studio",
                List.of("kitchen"),
                BigDecimal.valueOf(80),
                0
        );

        mockMvc.perform(get("/accommodations"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].type").value("HOUSE"))
                .andExpect(jsonPath("$[0].location").value("Warsaw"))
                .andExpect(jsonPath("$[0].size").value("2 rooms"))
                .andExpect(jsonPath("$[0].dailyRate").value(120))
                .andExpect(jsonPath("$[0].availability").value(2))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void getAccommodation_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/accommodations/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/accommodations/999999"));
    }

    @Test
    void createAccommodation_shouldReturn401WhenAnonymous() throws Exception {
        mockMvc.perform(post("/accommodations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(validCreateRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/accommodations"));

        assertThat(countEntities("AccommodationEntity")).isZero();
    }

    @Test
    void createAccommodation_shouldReturn403ForCustomer() throws Exception {
        User customer = persistCustomer("customer-accommodation@example.com");

        mockMvc.perform(post("/accommodations")
                        .header("Authorization", authorizationHeader(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(validCreateRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.path").value("/accommodations"));

        assertThat(countEntities("AccommodationEntity")).isZero();
    }

    @Test
    void createAccommodation_shouldCreateForAdmin() throws Exception {
        User admin = persistAdmin("admin-accommodation@example.com");

        mockMvc.perform(post("/accommodations")
                        .header("Authorization", authorizationHeader(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(validCreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.type").value("HOUSE"))
                .andExpect(jsonPath("$.location").value("Warsaw"))
                .andExpect(jsonPath("$.amenities[0]").value("wifi"))
                .andExpect(jsonPath("$.amenities[1]").value("parking"))
                .andExpect(jsonPath("$.dailyRate").value(120))
                .andExpect(jsonPath("$.availability").value(2));

        assertThat(countEntities("AccommodationEntity")).isEqualTo(1);
        Accommodation savedAccommodation = accommodationRepository.findAll().get(0);
        assertThat(savedAccommodation.getLocation()).isEqualTo("Warsaw");
        assertThat(savedAccommodation.getAmenities()).containsExactly("wifi", "parking");
        assertThat(savedAccommodation.getDailyRate()).isEqualByComparingTo("120");
        assertThat(savedAccommodation.getAvailability()).isEqualTo(2);
    }

    @Test
    void updateAccommodation_shouldPersistChangesForAdmin() throws Exception {
        User admin = persistAdmin("admin-accommodation-update@example.com");
        Accommodation accommodation = persistAccommodation(
                AccommodationType.HOUSE,
                "Warsaw",
                "2 rooms",
                List.of("wifi"),
                BigDecimal.valueOf(120),
                2
        );

        mockMvc.perform(put("/accommodations/{id}", accommodation.getId())
                        .header("Authorization", authorizationHeader(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(new UpdateAccommodationRequest(
                                AccommodationType.CONDO,
                                "Gdansk",
                                "Sea view apartment",
                                List.of("wifi", "spa"),
                                BigDecimal.valueOf(220),
                                4
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accommodation.getId()))
                .andExpect(jsonPath("$.type").value("CONDO"))
                .andExpect(jsonPath("$.location").value("Gdansk"))
                .andExpect(jsonPath("$.amenities[1]").value("spa"))
                .andExpect(jsonPath("$.dailyRate").value(220))
                .andExpect(jsonPath("$.availability").value(4));

        Accommodation updatedAccommodation = accommodationRepository.findById(accommodation.getId()).orElseThrow();
        assertThat(updatedAccommodation.getType()).isEqualTo(AccommodationType.CONDO);
        assertThat(updatedAccommodation.getLocation()).isEqualTo("Gdansk");
        assertThat(updatedAccommodation.getSize()).isEqualTo("Sea view apartment");
        assertThat(updatedAccommodation.getAmenities()).containsExactly("wifi", "spa");
        assertThat(updatedAccommodation.getDailyRate()).isEqualByComparingTo("220");
        assertThat(updatedAccommodation.getAvailability()).isEqualTo(4);
    }

    @Test
    void deleteAccommodation_shouldRemovePersistedEntityForAdmin() throws Exception {
        User admin = persistAdmin("admin-accommodation-delete@example.com");
        Accommodation accommodation = persistAccommodation(
                AccommodationType.HOUSE,
                "Poznan",
                "Loft",
                List.of("wifi"),
                BigDecimal.valueOf(150),
                1
        );

        mockMvc.perform(delete("/accommodations/{id}", accommodation.getId())
                        .header("Authorization", authorizationHeader(admin)))
                .andExpect(status().isNoContent());

        assertThat(entityExists("AccommodationEntity", accommodation.getId())).isFalse();
    }

    @Test
    void createAccommodation_shouldReturn400ForValidationErrors() throws Exception {
        User admin = persistAdmin("admin-accommodation-validation@example.com");

        mockMvc.perform(post("/accommodations")
                        .header("Authorization", authorizationHeader(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": null,
                                  "location": "",
                                  "size": "",
                                  "amenities": [""],
                                  "dailyRate": -1,
                                  "availability": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/accommodations"));

        assertThat(countEntities("AccommodationEntity")).isZero();
    }

    private CreateAccommodationRequest validCreateRequest() {
        return new CreateAccommodationRequest(
                AccommodationType.HOUSE,
                "Warsaw",
                "2 rooms",
                List.of("wifi", "parking"),
                BigDecimal.valueOf(120),
                2
        );
    }
}
