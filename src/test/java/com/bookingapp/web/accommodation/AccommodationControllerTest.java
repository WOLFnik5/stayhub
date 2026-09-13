package com.bookingapp.web.accommodation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.PageResult;
import com.bookingapp.domain.model.enums.AccommodationType;
import com.bookingapp.exception.GlobalExceptionHandler;
import com.bookingapp.service.AccommodationService;
import com.bookingapp.web.ControllerTestSecurityConfig;
import com.bookingapp.web.controller.AccommodationController;
import com.bookingapp.web.dto.CreateAccommodationRequest;
import com.bookingapp.web.mapper.AccommodationWebMapperImpl;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = AccommodationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.bookingapp\\.infrastructure\\.security\\..*"
        )
)
@Import({
        ControllerTestSecurityConfig.class,
        GlobalExceptionHandler.class,
        AccommodationWebMapperImpl.class
})
class AccommodationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccommodationService accommodationService;

    @Test
    void listAccommodationsShouldBePublicAndPaginated() throws Exception {
        Accommodation accommodation = new Accommodation(
                1L,
                AccommodationType.HOUSE,
                "Warsaw",
                "2 rooms",
                List.of("wifi"),
                BigDecimal.valueOf(120),
                2
        );

        PageResult<Accommodation> pageResult = new PageResult<>(
                List.of(accommodation),
                0,
                20,
                1
        );

        when(accommodationService.listAccommodations(0, 20))
                .thenReturn(pageResult);

        mockMvc.perform(get("/accommodations"))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))

                .andExpect(jsonPath("$.content.length()").value(1))

                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].type").value("HOUSE"))
                .andExpect(jsonPath("$.content[0].location").value("Warsaw"))
                .andExpect(jsonPath("$.content[0].size").value("2 rooms"))
                .andExpect(jsonPath("$.content[0].dailyRate").value(120))
                .andExpect(jsonPath("$.content[0].availability").value(2))

                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void listAccommodationsShouldUseProvidedPageAndSize() throws Exception {
        Accommodation accommodation = new Accommodation(
                11L,
                AccommodationType.APARTMENT,
                "Krakow",
                "Studio",
                List.of("wifi"),
                BigDecimal.valueOf(150),
                1
        );

        PageResult<Accommodation> pageResult = new PageResult<>(
                List.of(accommodation),
                1,
                10,
                25
        );

        when(accommodationService.listAccommodations(1, 10))
                .thenReturn(pageResult);

        mockMvc.perform(get("/accommodations")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(11))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void listAccommodationsShouldReturnEmptyPage() throws Exception {
        PageResult<Accommodation> pageResult = new PageResult<>(
                List.of(),
                0,
                20,
                0
        );

        when(accommodationService.listAccommodations(0, 20))
                .thenReturn(pageResult);

        mockMvc.perform(get("/accommodations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void createAccommodationShouldReturnUnauthorizedWhenAnonymous()
            throws Exception {

        mockMvc.perform(post("/accommodations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAccommodationShouldReturnForbiddenForCustomer()
            throws Exception {

        mockMvc.perform(post("/accommodations")
                        .with(user("customer@example.com")
                                .roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAccommodationShouldReturnCreatedJsonForAdmin()
            throws Exception {

        when(accommodationService.createAccommodation(
                any(CreateAccommodationRequest.class)
        )).thenReturn(
                new Accommodation(
                        1L,
                        AccommodationType.HOUSE,
                        "Warsaw",
                        "2 rooms",
                        List.of("wifi", "parking"),
                        BigDecimal.valueOf(120),
                        2
                )
        );

        mockMvc.perform(post("/accommodations")
                        .with(user("admin@example.com")
                                .roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest()))
                .andExpect(status().isCreated())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.type").value("HOUSE"))
                .andExpect(jsonPath("$.location").value("Warsaw"))
                .andExpect(jsonPath("$.amenities[0]").value("wifi"))
                .andExpect(jsonPath("$.dailyRate").value(120));
    }

    @Test
    void updateAccommodationShouldReturnForbiddenForCustomer()
            throws Exception {

        mockMvc.perform(put("/accommodations/1")
                        .with(user("customer@example.com")
                                .roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAccommodationShouldReturnValidationError()
            throws Exception {

        mockMvc.perform(post("/accommodations")
                        .with(user("admin@example.com")
                                .roles("ADMIN"))
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
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
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