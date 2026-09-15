package com.bookingapp.web.health;

import com.bookingapp.web.support.AbstractControllerIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthControllerIntegrationTest extends AbstractControllerIntegrationTest {

    @Test
    void metricsRequireAdminAndIncludeCorrelationOnDeniedRequests() throws Exception {
        mockMvc.perform(get("/actuator/metrics").header("X-Correlation-ID", "metrics-check"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("X-Correlation-ID", "metrics-check"));
        var customer = persistCustomer("metrics-customer@example.com");
        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", authorizationHeader(customer)))
                .andExpect(status().isForbidden());
        var admin = persistAdmin("metrics-admin@example.com");
        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", authorizationHeader(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/metrics/booking.outbox.events")
                        .header("Authorization", authorizationHeader(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void healthShouldBeAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
