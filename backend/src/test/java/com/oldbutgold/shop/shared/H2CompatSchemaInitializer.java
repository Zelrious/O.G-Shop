package com.oldbutgold.shop.shared;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Test-only H2 schema bootstrapper (replaces runtime DDL previously in DisputeRepository).
 * Skips automatically when running against real PostgreSQL (which uses Flyway V1..V64).
 */
@Component
@Profile("test")
public class H2CompatSchemaInitializer implements InitializingBean {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public H2CompatSchemaInitializer(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        String product = jdbcTemplate.execute((ConnectionCallback<String>) conn ->
                conn.getMetaData().getDatabaseProductName());
        if (product != null && "H2".equalsIgnoreCase(product.trim())) {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                    new ClassPathResource("h2-compat-schema.sql"));
            populator.execute(dataSource);
        }
    }
}

