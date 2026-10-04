package com.bookingapp.infrastructure.migration;

import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public final class DatabaseMigration {
    private DatabaseMigration() {
    }

    public static void main(String[] args) throws LiquibaseException {
        migrate(new StandardEnvironment());
    }

    public static void migrate(Environment environment) throws LiquibaseException {
        DriverManagerDataSource source = new DriverManagerDataSource(
                environment.getRequiredProperty("LIQUIBASE_URL"),
                environment.getRequiredProperty("LIQUIBASE_USERNAME"),
                environment.getRequiredProperty("LIQUIBASE_PASSWORD"));
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(source);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.setDefaultSchema("public");
        liquibase.setLiquibaseSchema("liquibase");
        liquibase.afterPropertiesSet();
    }
}
