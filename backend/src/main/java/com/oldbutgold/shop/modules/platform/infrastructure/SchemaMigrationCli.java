package com.oldbutgold.shop.modules.platform.infrastructure;

import org.flywaydb.core.Flyway;

/** Explicit migration entry point; starts no web server, scheduler or business workflow. */
public final class SchemaMigrationCli {
    private SchemaMigrationCli() {}

    public static void main(String[] args) {
        String action = args.length == 1 ? args[0] : "";
        if (!action.equals("validate") && !action.equals("migrate")) {
            throw new IllegalArgumentException("Usage: SchemaMigrationCli validate|migrate");
        }
        var flyway = Flyway.configure()
                .dataSource(required("OGSHOP_MIGRATION_DB_URL"), required("OGSHOP_MIGRATION_DB_USER"),
                        required("OGSHOP_MIGRATION_DB_PASSWORD"))
                .locations("classpath:db/migration")
                .initSql("SET lock_timeout = '10s'; SET statement_timeout = '120s'")
                .callbacks(new LegacyModerationHistoryCallback())
                .ignoreMigrationPatterns("*:pending")
                .load();
        flyway.validate();
        if (action.equals("migrate")) {
            var result = flyway.migrate();
            flyway.validate();
            System.out.printf("Migration complete: %d applied, schema version %s%n",
                    result.migrationsExecuted, result.targetSchemaVersion);
        } else {
            System.out.println("Applied migration checksums validated; pending migrations are allowed.");
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing environment variable: " + name);
        }
        return value;
    }
}
