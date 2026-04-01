package com.bookingapp.web.booking;

import com.bookingapp.web.dto.CreateBookingRequest;
import com.bookingapp.web.dto.UpdateBookingRequest;
import com.bookingapp.web.support.AbstractControllerIntegrationTest;
import com.bookingapp.domain.model.enums.AccommodationType;
import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingControllerIntegrationTest extends AbstractControllerIntegrationTest {

    @Test
    void createBooking_shouldReturn401WhenAnonymous() throws Exception {
        Accommodation accommodation = persistAccommodation(
                AccommodationType.HOUSE,
                "Warsaw",
                "2 rooms",
                List.of("wifi"),
                BigDecimal.valueOf(120),
                1
        );

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(new CreateBookingRequest(
                                accommodation.getId(),
                                futureDate(10),
                                futureDate(12)
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/bookings"));

        assertThat(jpaBookingRepository.count()).isZero();
    }

    @Test
    void listBookings_shouldReturnOnlyCurrentUsersBookingsForCustomer() throws Exception {
        User currentUser = persistCustomer("booking-customer-list@example.com");
        User otherUser = persistCustomer("booking-customer-list-other@example.com");
        Accommodation accommodation = persistAccommodation(
                AccommodationType.CONDO,
                "Warsaw",
                "Modern condo",
                List.of("wifi"),
                BigDecimal.valueOf(200),
                2
        );
        Booking ownPendingBooking = persistBooking(
                futureDate(8),
                futureDate(10),
                accommodation.getId(),
                currentUser.getId(),
                BookingStatus.PENDING
        );
        persistBooking(
                futureDate(12),
                futureDate(14),
                accommodation.getId(),
                otherUser.getId(),
                BookingStatus.PENDING
        );
        persistBooking(
                futureDate(16),
                futureDate(18),
                accommodation.getId(),
                currentUser.getId(),
                BookingStatus.CANCELED
        );

        mockMvc.perform(get("/bookings")
                        .header("Authorization", authorizationHeader(currentUser))
                        .param("status", "PENDING")
                        .param("user_id", otherUser.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(ownPendingBooking.getId()))
                .andExpect(jsonPath("$[0].userId").value(currentUser.getId()))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void createBooking_shouldPersistForAuthenticatedCustomer() throws Exception {
        User customer = persistCustomer("booking-customer-create@example.com");
        Accommodation accommodation = persistAccommodation(
                AccommodationType.APARTMENT,
                "Warsaw",
                "City center apartment",
                List.of("wifi", "kitchen"),
                BigDecimal.valueOf(250),
                2
        );
        LocalDate checkInDate = futureDate(10);
        LocalDate checkOutDate = futureDate(13);

        mockMvc.perform(post("/bookings")
                        .header("Authorization", authorizationHeader(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(new CreateBookingRequest(
                                accommodation.getId(),
                                checkInDate,
                                checkOutDate
                        ))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.checkInDate").value(checkInDate.toString()))
                .andExpect(jsonPath("$.checkOutDate").value(checkOutDate.toString()))
                .andExpect(jsonPath("$.accommodationId").value(accommodation.getId()))
                .andExpect(jsonPath("$.userId").value(customer.getId()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(jpaBookingRepository.count()).isEqualTo(1);
        Booking savedBooking = bookingRepository.findAllByUserId(customer.getId()).get(0);
        assertThat(savedBooking.getAccommodationId()).isEqualTo(accommodation.getId());
        assertThat(savedBooking.getCheckInDate()).isEqualTo(checkInDate);
        assertThat(savedBooking.getCheckOutDate()).isEqualTo(checkOutDate);
        assertThat(savedBooking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void listMyBookings_shouldReturnPersistedDataForCurrentUser() throws Exception {
        User currentUser = persistCustomer("booking-my-list@example.com");
        User otherUser = persistCustomer("booking-my-list-other@example.com");
        Accommodation accommodation = persistAccommodation(
                AccommodationType.CONDO,
                "Gdansk",
                "Sea apartment",
                List.of("wifi"),
                BigDecimal.valueOf(300),
                3
        );
        Booking ownBooking = persistBooking(futureDate(5), futureDate(7), accommodation.getId(), currentUser.getId(), BookingStatus.PENDING);
        persistBooking(futureDate(8), futureDate(10), accommodation.getId(), otherUser.getId(), BookingStatus.PENDING);

        mockMvc.perform(get("/bookings/my")
                        .header("Authorization", authorizationHeader(currentUser)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(ownBooking.getId()))
                .andExpect(jsonPath("$[0].userId").value(currentUser.getId()))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void listBookings_shouldReturnPersistedDataForAdmin() throws Exception {
        User admin = persistAdmin("booking-admin-list@example.com");
        User customer = persistCustomer("booking-admin-list-customer@example.com");
        Accommodation accommodation = persistAccommodation(
                AccommodationType.HOUSE,
                "Zakopane",
                "Mountain cabin",
                List.of("fireplace"),
                BigDecimal.valueOf(400),
                1
        );
        Booking booking = persistBooking(
                futureDate(15),
                futureDate(18),
                accommodation.getId(),
                customer.getId(),
                BookingStatus.PENDING
        );
        persistBooking(
                futureDate(20),
                futureDate(24),
                accommodation.getId(),
                customer.getId(),
                BookingStatus.CANCELED
        );

        mockMvc.perform(get("/bookings")
                        .header("Authorization", authorizationHeader(admin))
                        .param("user_id", customer.getId().toString())
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(booking.getId()))
                .andExpect(jsonPath("$[0].userId").value(customer.getId()))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void listBookings_shouldReturnAllUsersBookingsForAdminWhenUserFilterIsMissing() throws Exception {
        User admin = persistAdmin("booking-admin-all@example.com");
        User firstCustomer = persistCustomer("booking-admin-all-first@example.com");
        User secondCustomer = persistCustomer("booking-admin-all-second@example.com");
        Accommodation accommodation = persistAccommodation(
                AccommodationType.HOUSE,
                "Krakow",
                "City house",
                List.of("wifi"),
                BigDecimal.valueOf(350),
                1
        );
        Booking firstBooking = persistBooking(
                futureDate(15),
                futureDate(18),
                accommodation.getId(),
                firstCustomer.getId(),
                BookingStatus.PENDING
        );
        Booking secondBooking = persistBooking(
                futureDate(20),
                futureDate(22),
                accommodation.getId(),
                secondCustomer.getId(),
                BookingStatus.CONFIRMED
        );

        mockMvc.perform(get("/bookings")
                        .header("Authorization", authorizationHeader(admin)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(firstBooking.getId()))
                .andExpect(jsonPath("$[1].id").value(secondBooking.getId()));
    }

    @Test
    void getBooking_shouldReturn404WhenNotFound() throws Exception {
        User customer = persistCustomer("booking-not-found@example.com");

        mockMvc.perform(get("/bookings/999999")
                        .header("Authorization", authorizationHeader(customer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.path").value("/bookings/999999"));
    }

    @Test
    void getBooking_shouldReturn403WhenAccessingAnotherUsersBooking() throws Exception {
        User owner = persistCustomer("booking-owner@example.com");
        User intruder = persistCustomer("booking-intruder@example.com");
        Accommodation accommodation = persistAccommodation(
                AccommodationType.APARTMENT,
                "Lodz",
                "Loft",
                List.of("wifi"),
                BigDecimal.valueOf(180),
                1
        );
        Booking booking = persistBooking(
                futureDate(9),
                futureDate(11),
                accommodation.getId(),
                owner.getId(),
                BookingStatus.PENDING
        );

        mockMvc.perform(get("/bookings/{id}", booking.getId())
                        .header("Authorization", authorizationHeader(intruder)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.path").value("/bookings/" + booking.getId()));
    }

    @Test
    void updateBooking_shouldPersistChangedDatesForOwner() throws Exception {
        User customer = persistCustomer("booking-update@example.com");
        Accommodation accommodation = persistAccommodation(
                AccommodationType.APARTMENT,
                "Sopot",
                "Beach flat",
                List.of("balcony"),
                BigDecimal.valueOf(220),
                1
        );
        Booking booking = persistBooking(
                futureDate(7),
                futureDate(10),
                accommodation.getId(),
                customer.getId(),
                BookingStatus.PENDING
        );
        LocalDate newCheckInDate = futureDate(12);
        LocalDate newCheckOutDate = futureDate(15);

        mockMvc.perform(put("/bookings/{id}", booking.getId())
                        .header("Authorization", authorizationHeader(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(new UpdateBookingRequest(newCheckInDate, newCheckOutDate))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(booking.getId()))
                .andExpect(jsonPath("$.checkInDate").value(newCheckInDate.toString()))
                .andExpect(jsonPath("$.checkOutDate").value(newCheckOutDate.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        Booking updatedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updatedBooking.getCheckInDate()).isEqualTo(newCheckInDate);
        assertThat(updatedBooking.getCheckOutDate()).isEqualTo(newCheckOutDate);
    }

    @Test
    void cancelBooking_shouldPersistCanceledStatus() throws Exception {
        User customer = persistCustomer("booking-cancel@example.com");
        Accommodation accommodation = persistAccommodation(
                AccommodationType.HOUSE,
                "Wroclaw",
                "Townhouse",
                List.of("parking"),
                BigDecimal.valueOf(260),
                1
        );
        Booking booking = persistBooking(
                futureDate(6),
                futureDate(9),
                accommodation.getId(),
                customer.getId(),
                BookingStatus.PENDING
        );

        mockMvc.perform(delete("/bookings/{id}", booking.getId())
                        .header("Authorization", authorizationHeader(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(booking.getId()))
                .andExpect(jsonPath("$.status").value("CANCELED"));

        Booking canceledBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(canceledBooking.getStatus()).isEqualTo(BookingStatus.CANCELED);
    }

    @Test
    void createBooking_shouldReturn400ForValidationErrors() throws Exception {
        User customer = persistCustomer("booking-validation@example.com");

        mockMvc.perform(post("/bookings")
                        .header("Authorization", authorizationHeader(customer))
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
                .andExpect(jsonPath("$.path").value("/bookings"));

        assertThat(jpaBookingRepository.count()).isZero();
    }

    @Test
    void createBooking_shouldRejectOverlappingDatesEvenWhenAvailabilityIsGreaterThanOne() throws Exception {
        User firstCustomer = persistCustomer("booking-overlap-first@example.com");
        User secondCustomer = persistCustomer("booking-overlap-second@example.com");
        Accommodation accommodation = persistAccommodation(
                AccommodationType.APARTMENT,
                "Gdynia",
                "Harbor apartment",
                List.of("wifi"),
                BigDecimal.valueOf(240),
                2
        );
        LocalDate checkInDate = futureDate(10);
        LocalDate checkOutDate = futureDate(13);
        persistBooking(checkInDate, checkOutDate, accommodation.getId(), firstCustomer.getId(), BookingStatus.PENDING);

        mockMvc.perform(post("/bookings")
                        .header("Authorization", authorizationHeader(secondCustomer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(new CreateBookingRequest(
                                accommodation.getId(),
                                checkInDate.plusDays(1),
                                checkOutDate.plusDays(1)
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.path").value("/bookings"));
    }

    @Test
    void createBooking_shouldRejectAccommodationWithoutAvailability() throws Exception {
        User customer = persistCustomer("booking-no-availability@example.com");
        Accommodation accommodation = persistAccommodation(
                AccommodationType.HOUSE,
                "Poznan",
                "Quiet house",
                List.of("parking"),
                BigDecimal.valueOf(210),
                0
        );

        mockMvc.perform(post("/bookings")
                        .header("Authorization", authorizationHeader(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(new CreateBookingRequest(
                                accommodation.getId(),
                                futureDate(10),
                                futureDate(12)
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Accommodation is not available for booking"))
                .andExpect(jsonPath("$.path").value("/bookings"));
    }

    @Test
    void cancelBooking_shouldReturn400WhenBookingIsAlreadyCanceled() throws Exception {
        User customer = persistCustomer("booking-cancel-twice@example.com");
        Accommodation accommodation = persistAccommodation(
                AccommodationType.HOUSE,
                "Wroclaw",
                "Townhouse",
                List.of("parking"),
                BigDecimal.valueOf(260),
                1
        );
        Booking booking = persistBooking(
                futureDate(6),
                futureDate(9),
                accommodation.getId(),
                customer.getId(),
                BookingStatus.CANCELED
        );

        mockMvc.perform(delete("/bookings/{id}", booking.getId())
                        .header("Authorization", authorizationHeader(customer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Booking is already canceled"))
                .andExpect(jsonPath("$.path").value("/bookings/" + booking.getId()));
    }
}
