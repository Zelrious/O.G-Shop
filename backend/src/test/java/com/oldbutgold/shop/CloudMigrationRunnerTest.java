package com.oldbutgold.shop;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class CloudMigrationRunnerTest {

    @Test
    @Disabled("Manual utility to run Flyway on Supabase Cloud DB")
    void runFlywayOnSupabase() {
        OgShopApplication.loadDotEnv();
        System.out.println("Starting Flyway migration on Supabase Cloud DB...");
        String url = System.getProperty("CLOUD_DB_URL", System.getenv("CLOUD_DB_URL"));
        if (url == null) url = System.getProperty("DB_URL", System.getenv("DB_URL"));

        String username = System.getProperty("CLOUD_DB_USERNAME", System.getenv("CLOUD_DB_USERNAME"));
        if (username == null) username = System.getProperty("DB_USERNAME", System.getenv("DB_USERNAME"));

        String password = System.getProperty("CLOUD_DB_PASSWORD", System.getenv("CLOUD_DB_PASSWORD"));
        if (password == null) password = System.getProperty("DB_PASSWORD", System.getenv("DB_PASSWORD"));

        if (url == null || username == null || password == null || password.isBlank()) {
            throw new IllegalStateException("CLOUD_DB_URL, CLOUD_DB_USERNAME, and CLOUD_DB_PASSWORD are required in .env!");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .load();

        MigrateResult result = flyway.migrate();
        System.out.println(">>> FLYWAY SUCCESS! Migrations executed: " + result.migrationsExecuted);
        System.out.println(">>> Target schema version: " + result.targetSchemaVersion);
    }
}

