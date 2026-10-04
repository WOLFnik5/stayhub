package com.bookingapp;

import com.bookingapp.testsupport.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.ApplicationContext;
import com.bookingapp.infrastructure.outbox.OutboxKafkaPublisher;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BookingAppContextTest extends PostgreSqlIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertThat(jdbcTemplate).isNotNull();
        assertThat(applicationContext.getBeansOfType(OutboxKafkaPublisher.class)).isEmpty();
    }
}
