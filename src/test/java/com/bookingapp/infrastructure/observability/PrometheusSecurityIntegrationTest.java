package com.bookingapp.infrastructure.observability;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookingapp.web.support.AbstractControllerIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles({"test", "monitoring"})
@TestPropertySource(properties = "app.monitoring.password=local-test-scrape-password")
class PrometheusSecurityIntegrationTest extends AbstractControllerIntegrationTest {
    @Test
    void anonymousAndIncorrectCredentialsCannotScrape() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus")
                .with(httpBasic("stayhub-scraper", "incorrect")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void scraperCanReadRealMetricsAndHistograms() throws Exception {
        mockMvc.perform(get("/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/prometheus")
                .with(httpBasic("stayhub-scraper", "local-test-scrape-password")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("booking_outbox_events")))
                .andExpect(content().string(containsString("http_server_requests_seconds_bucket")))
                .andExpect(content().string(containsString("hikaricp_connections_pending")));
    }

    @Test
    void scraperCredentialsCannotAccessBusinessApiOrAdminMetrics() throws Exception {
        mockMvc.perform(get("/bookings/my")
                .with(httpBasic("stayhub-scraper", "local-test-scrape-password")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics")
                .with(httpBasic("stayhub-scraper", "local-test-scrape-password")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/actuator/prometheus")
                .with(httpBasic("stayhub-scraper", "local-test-scrape-password")))
                .andExpect(status().isForbidden());
    }

    @Test
    void existingJwtAccessAndPublicHealthStillWork() throws Exception {
        var customer = persistCustomer("monitoring-customer@example.com");
        mockMvc.perform(get("/bookings/my").header("Authorization", authorizationHeader(customer)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/health")).andExpect(status().isOk());
    }
}
