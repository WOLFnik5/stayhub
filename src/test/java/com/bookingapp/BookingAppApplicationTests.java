package com.bookingapp;

import com.bookingapp.support.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BookingAppApplicationTests extends PostgreSqlIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
        assertThat(jdbcTemplate).isNotNull();
    }

    @Test
    void liquibaseMigrationsShouldBeApplied() {
        Integer appliedChanges = jdbcTemplate.queryForObject(
                "select count(*) from databasechangelog",
                Integer.class
        );
        Integer usersTableCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name = 'users'
                """,
                Integer.class
        );
        Integer paymentsTableCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name = 'payments'
                """,
                Integer.class
        );

        assertThat(appliedChanges).isGreaterThanOrEqualTo(6);
        assertThat(usersTableCount).isEqualTo(1);
        assertThat(paymentsTableCount).isEqualTo(1);
    }
}
