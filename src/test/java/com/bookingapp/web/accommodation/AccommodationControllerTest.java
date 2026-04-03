package com.bookingapp.web.accommodation;

import com.bookingapp.web.ControllerTestSecurityConfig;
import com.bookingapp.web.controller.AccommodationController;
import com.bookingapp.web.mapper.AccommodationWebMapperImpl;
import com.bookingapp.service.AccommodationService;
import com.bookingapp.exception.GlobalExceptionHandler;
import com.bookingapp.domain.model.enums.AccommodationType;
import com.bookingapp.domain.model.Accommodation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AccommodationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.bookingapp\\.infrastructure\\.security\\..*"
        )
)
@Import({ControllerTestSecurityConfig.class, GlobalExceptionHandler.class, AccommodationWebMapperImpl.class})
class AccommodationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccommodationService accommodationService;

    @Test
    void listAccommodationsShouldBePublic() throws Exception {
        when(accommodationService.listAccommodations()).thenReturn(List.of(
                new Accommodation(1L, AccommodationType.HOUSE, "Warsaw", "2 rooms", List.of("wifi"), BigDecimal.valueOf(120), 2)
        ));

        mockMvc.perform(get("/accommodations"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].type").value("HOUSE"))
                .andExpect(jsonPath("$[0].location").value("Warsaw"))
                .andExpect(jsonPath("$[0].size").value("2 rooms"))
                .andExpect(jsonPath("$[0].dailyRate").value(120))
                .andExpect(jsonPath("$[0].availability").value(2));
    }

    @Test
    void createAccommodationShouldReturnUnauthorizedWhenAnonymous() throws Exception {
        mockMvc.perform(post("/accommodations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAccommodationShouldReturnForbiddenForCustomer() throws Exception {
        mockMvc.perform(post("/accommodations")
                        .with(user("customer@example.com").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAccommodationShouldReturnCreatedJsonForAdmin() throws Exception {
        when(accommodationService.createAccommodation(
                any(AccommodationType.class),
                anyString(),
                anyString(),
                anyList(),
                any(BigDecimal.class),
                anyInt()
        )).thenReturn(
                new Accommodation(1L, AccommodationType.HOUSE, "Warsaw", "2 rooms", List.of("wifi", "parking"), BigDecimal.valueOf(120), 2)
        );

        mockMvc.perform(post("/accommodations")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest()))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.type").value("HOUSE"))
                .andExpect(jsonPath("$.location").value("Warsaw"))
                .andExpect(jsonPath("$.amenities[0]").value("wifi"))
                .andExpect(jsonPath("$.dailyRate").value(120));
    }

    @Test
    void updateAccommodationShouldReturnForbiddenForCustomer() throws Exception {
        mockMvc.perform(put("/accommodations/1")
                        .with(user("customer@example.com").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAccommodationShouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/accommodations")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "location": "",
                                  "size": "",
                                  "amenities": [],
                                  "dailyRate": -1,
                                  "availability": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/accommodations"));
    }

    private String validCreateRequest() {
        return """
                {
                  "type": "HOUSE",
                  "location": "Warsaw",
                  "size": "2 rooms",
                  "amenities": ["wifi", "parking"],
                  "dailyRate": 120,
                  "availability": 2
                }
                """;
    }
}
