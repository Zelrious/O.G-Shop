package com.oldbutgold.shop.modules.platform.infrastructure;

import org.flywaydb.core.Flyway;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Explicit migration entry point; starts no web server, scheduler or business workflow. */
public final class SchemaMigrationCli {
    private SchemaMigrationCli() {}

    public static void main(String[] args) {
        String action = args.length >= 1 ? args[0] : "";
        if (!action.equals("validate") && !action.equals("migrate")
                && !action.equals("provision-reward-policy") && !action.equals("verify-reward-policy")) {
            throw new IllegalArgumentException("Usage: SchemaMigrationCli validate|migrate|provision-reward-policy|verify-reward-policy [adminId]");
        }

        if (action.equals("provision-reward-policy")) {
            long adminId = resolveAdminId(args);
            provisionRewardPolicy(adminId);
            return;
        }

        if (action.equals("verify-reward-policy")) {
            verifyRewardPolicy();
            return;
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

    private static void provisionRewardPolicy(long adminId) {
        String url = required("OGSHOP_MIGRATION_DB_URL");
        String user = required("OGSHOP_MIGRATION_DB_USER");
        String pass = required("OGSHOP_MIGRATION_DB_PASSWORD");

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             PreparedStatement stmt = conn.prepareStatement("SELECT public.og_provision_canonical_reward_policy(?)")) {
            stmt.setLong(1, adminId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long policyId = rs.getLong(1);
                    System.out.printf("[PROVISION SUCCESS] Canonical Reward Policy provisioned: policy_id=%d by admin_id=%d%n",
                            policyId, adminId);
                } else {
                    throw new IllegalStateException("Provisioning returned no result row.");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to provision canonical reward policy: " + e.getMessage(), e);
        }
    }

    private static void verifyRewardPolicy() {
        String url = required("OGSHOP_MIGRATION_DB_URL");
        String user = required("OGSHOP_MIGRATION_DB_USER");
        String pass = required("OGSHOP_MIGRATION_DB_PASSWORD");

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT policy_id, policy_code, point_value, created_by FROM og_compat.reward_policies WHERE policy_code = 'REWARD_CHECKOUT_STANDARD'")) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long policyId = rs.getLong("policy_id");
                    BigDecimal pointValue = rs.getBigDecimal("point_value");
                    long createdBy = rs.getLong("created_by");
                    if (pointValue == null || pointValue.compareTo(new BigDecimal("1.00")) != 0) {
                        System.err.printf("[VERIFICATION FAILED] Invalid point_value: %s (expected 1.00)%n", pointValue);
                        System.exit(1);
                    }
                    System.out.printf("[VERIFICATION SUCCESS] Canonical Reward Policy verified: policy_id=%d, policy_code=REWARD_CHECKOUT_STANDARD, point_value=%s, created_by=%d%n",
                            policyId, pointValue, createdBy);
                } else {
                    System.err.println("[VERIFICATION FAILED] Canonical Reward Policy 'REWARD_CHECKOUT_STANDARD' not found in database.");
                    System.exit(1);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to verify canonical reward policy: " + e.getMessage(), e);
        }
    }

    public static long resolveAdminId(String[] args) {
        String raw = args.length >= 2 ? args[1] : System.getenv("OGSHOP_REWARD_POLICY_ADMIN_ID");
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Missing required Admin ID: pass as second argument or via environment variable OGSHOP_REWARD_POLICY_ADMIN_ID");
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid Admin ID format (must be numeric bigint): " + raw, e);
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
