package com.bookingapp.web.booking;

import com.bookingapp.web.ControllerTestSecurityConfig;
import com.bookingapp.web.controller.BookingController;
import com.bookingapp.web.mapper.BookingWebMapper;
import com.bookingapp.domain.service.BookingService;
import com.bookingapp.web.exception.GlobalExceptionHandler;
import com.bookingapp.domain.enums.AccommodationType;
import com.bookingapp.domain.enums.BookingStatus;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = BookingController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.bookingapp\\.infrastructure\\.security\\..*"
        )
)
@Import({ControllerTestSecurityConfig.class, GlobalExceptionHandler.class, BookingWebMapper.class})
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    @Test
    void createBookingShouldReturnUnauthorizedWhenAnonymous() throws Exception {
        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createBookingShouldReturnCreatedBookingForAuthenticatedUser() throws Exception {
        when(bookingService.createBooking(anyLong(), any(LocalDate.class), any(LocalDate.class))).thenReturn(
                new Booking(9L, LocalDate.of(2099, 4, 10), LocalDate.of(2099, 4, 12), 3L, 15L, BookingStatus.PENDING)
        );

        mockMvc.perform(post("/bookings")
                        .with(user("customer@example.com").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.checkInDate").value("2099-04-10"))
                .andExpect(jsonPath("$.checkOutDate").value("2099-04-12"))
                .andExpect(jsonPath("$.accommodationId").value(3))
                .andExpect(jsonPath("$.userId").value(15))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void listBookingsShouldReturnForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get("/bookings").with(user("customer@example.com").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listBookingsShouldReturnFilteredAdminResponse() throws Exception {
        when(bookingService.listBookings(any())).thenReturn(List.of(
                new Booking(9L, LocalDate.of(2099, 4, 10), LocalDate.of(2099, 4, 12), 3L, 15L, BookingStatus.PENDING)
        ));

        mockMvc.perform(get("/bookings")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .param("user_id", "15")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(9))
                .andExpect(jsonPath("$[0].userId").value(15))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void createBookingShouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/bookings")
                        .with(user("customer@example.com").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accommodationId": null,
                                  "checkInDate": null,
                                  "checkOutDate": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/bookings"));
    }

    @Test
    void getBookingByIdShouldReturnDetailedJson() throws Exception {
        when(bookingService.getBookingById(9L)).thenReturn(
                new Booking(9L, LocalDate.of(2099, 4, 10), LocalDate.of(2099, 4, 12), 3L, 15L, BookingStatus.PENDING)
        );
        when(bookingService.getAccommodationByBookingId(9L)).thenReturn(
                new Accommodation(3L, AccommodationType.HOUSE, "Warsaw", "2 rooms", List.of("wifi"), BigDecimal.valueOf(120), 2)
        );

        mockMvc.perform(get("/bookings/9").with(user("customer@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.accommodationId").value(3))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.accommodation.id").value(3))
                .andExpect(jsonPath("$.accommodation.type").value("HOUSE"))
                .andExpect(jsonPath("$.accommodation.location").value("Warsaw"));
    }

    private String validCreateRequest() {
        return """
                {
                  "accommodationId": 3,
                  "checkInDate": "2099-04-10",
                  "checkOutDate": "2099-04-12"
                }
                """;
    }
}
