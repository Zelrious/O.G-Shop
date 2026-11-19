package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.platform.infrastructure.LegacyModerationHistoryCallback;
import com.oldbutgold.shop.shared.PostgresTestSafetyGuard;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verification of V55 Dual-Path Migrations (renumbered from V51) and og70_write_view() convergence.
 *
 * <p>Enforces strict execution against an isolated PostgreSQL test database (resolves host/port/credentials
 * dynamically from OGSHOP_TEST_DB_URL / OGSHOP_TEST_DB_USER / OGSHOP_TEST_DB_PASSWORD, with safe defaults).
 * Verifies:
 * 1. Clean Path A: V1 -> V56 on fresh database (og_shop_v55_clean_test).
 * 2. Upgrade Path B: V1 -> V40 (main baseline), then V41..V56 on (og_shop_v55_upgrade_test).
 * 3. Flyway checksum preservation for V48 and flyway validate success on both paths.
 * 4. 100% convergence (0 diff, matching SHA-256) of og70_write_view() between Path A and Path B.
 * 5. Functional compatibility writes on all 4 repaired branches plus reward_ledger (CREDIT_REFUND & V56 CREDIT_REVIEW).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_ISOLATED", matches = "true")
class V55DualMigrationVerificationPostgresTest {

    private static final String CLEAN_DB_NAME = "og_shop_v55_clean_test";
    private static final String UPGRADE_DB_NAME = "og_shop_v55_upgrade_test";

    private static DataSource cleanDataSource;
    private static DataSource upgradeDataSource;

    private static JdbcTemplate cleanJdbc;
    private static JdbcTemplate upgradeJdbc;

    private static String resolveJdbcUrlForDb(String targetDb) {
        String envUrl = System.getenv(PostgresTestSafetyGuard.ENV_TEST_DB_URL);
        String baseUrl = (envUrl != null && !envUrl.isBlank())
                ? envUrl.trim()
                : "jdbc:postgresql://localhost:55432/postgres";
        int schemeIdx = baseUrl.indexOf("://");
        int pathStart = baseUrl.indexOf('/', schemeIdx + 3);
        int queryStart = baseUrl.indexOf('?', pathStart);
        String prefix = baseUrl.substring(0, pathStart + 1);
        String suffix = queryStart >= 0 ? baseUrl.substring(queryStart) : "";
        return prefix + targetDb + suffix;
    }

    private static String resolveDbUser() {
        String user = System.getenv("OGSHOP_TEST_DB_USER");
        return (user != null && !user.isBlank()) ? user.trim() : "og_shop";
    }

    private static String resolveDbPassword() {
        String pass = System.getenv("OGSHOP_TEST_DB_PASSWORD");
        return pass != null ? pass : "root";
    }

    @BeforeAll
    static void setUp() {
        String cleanJdbcUrl = PostgresTestSafetyGuard.assertSafeTestDatabase(resolveJdbcUrlForDb(CLEAN_DB_NAME), "true");
        String upgradeJdbcUrl = PostgresTestSafetyGuard.assertSafeTestDatabase(resolveJdbcUrlForDb(UPGRADE_DB_NAME), "true");
        String rootJdbcUrl = resolveJdbcUrlForDb("postgres");
        String dbUser = resolveDbUser();
        String dbPass = resolveDbPassword();

        DataSource rootDs = new DriverManagerDataSource(rootJdbcUrl, dbUser, dbPass);
        JdbcTemplate rootJdbc = new JdbcTemplate(rootDs);

        rootJdbc.execute("DROP DATABASE IF EXISTS " + CLEAN_DB_NAME + " WITH (FORCE)");
        rootJdbc.execute("CREATE DATABASE " + CLEAN_DB_NAME + " OWNER " + dbUser);

        rootJdbc.execute("DROP DATABASE IF EXISTS " + UPGRADE_DB_NAME + " WITH (FORCE)");
        rootJdbc.execute("CREATE DATABASE " + UPGRADE_DB_NAME + " OWNER " + dbUser);

        cleanDataSource = new DriverManagerDataSource(cleanJdbcUrl, dbUser, dbPass);
        upgradeDataSource = new DriverManagerDataSource(upgradeJdbcUrl, dbUser, dbPass);

        cleanJdbc = new JdbcTemplate(cleanDataSource);
        upgradeJdbc = new JdbcTemplate(upgradeDataSource);

        cleanJdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        upgradeJdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
    }

    @Test
    @Order(1)
    @DisplayName("Path A: Clean migration from V1 to V56 succeeds and validates")
    void testPathACleanMigration() {
        Flyway flyway = Flyway.configure()
                .dataSource(cleanDataSource)
                .schemas("public")
                .defaultSchema("public")
                .callbacks(new LegacyModerationHistoryCallback())
                .load();

        var result = flyway.migrate();
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(56);

        // Flyway validate must pass cleanly
        flyway.validate();

        // Check flyway_schema_history
        Integer totalMigrations = cleanJdbc.queryForObject(
                "SELECT count(*) FROM public.flyway_schema_history WHERE success = true", Integer.class);
        assertThat(totalMigrations).isGreaterThanOrEqualTo(56);

        // V48, V55, V56 must be recorded with success = true
        for (String ver : new String[]{"48", "55", "56"}) {
            Boolean verSuccess = cleanJdbc.queryForObject(
                    "SELECT success FROM public.flyway_schema_history WHERE version = ?", Boolean.class, ver);
            assertThat(verSuccess).as("Version " + ver + " must succeed").isTrue();
        }
    }

    @Test
    @Order(2)
    @DisplayName("Path B: Upgrade migration from main (V1..V40) through V56 succeeds and validates")
    void testPathBUpgradeMigration() {
        // Step 1: Migrate up to V40 (main baseline)
        Flyway flywayV40 = Flyway.configure()
                .dataSource(upgradeDataSource)
                .schemas("public")
                .defaultSchema("public")
                .target("40")
                .callbacks(new LegacyModerationHistoryCallback())
                .load();

        flywayV40.migrate();

        Integer v40Count = upgradeJdbc.queryForObject(
                "SELECT count(*) FROM public.flyway_schema_history WHERE success = true", Integer.class);
        assertThat(v40Count).isEqualTo(40);

        // Step 2: Migrate to latest (V41..V56)
        Flyway flywayLatest = Flyway.configure()
                .dataSource(upgradeDataSource)
                .schemas("public")
                .defaultSchema("public")
                .callbacks(new LegacyModerationHistoryCallback())
                .load();

        var upgradeResult = flywayLatest.migrate();
        assertThat(upgradeResult.migrationsExecuted).isEqualTo(16);

        // Flyway validate must pass cleanly on Path B
        flywayLatest.validate();

        for (String ver : new String[]{"48", "55", "56"}) {
            Boolean verSuccess = upgradeJdbc.queryForObject(
                    "SELECT success FROM public.flyway_schema_history WHERE version = ?", Boolean.class, ver);
            assertThat(verSuccess).as("Upgrade version " + ver + " must succeed").isTrue();
        }
    }

    @Test
    @Order(3)
    @DisplayName("V48 Checksum is preserved and identical across Path A and Path B")
    void testV48ChecksumPreserved() {
        Integer checksumA = cleanJdbc.queryForObject(
                "SELECT checksum FROM public.flyway_schema_history WHERE version = '48'", Integer.class);
        Integer checksumB = upgradeJdbc.queryForObject(
                "SELECT checksum FROM public.flyway_schema_history WHERE version = '48'", Integer.class);

        assertThat(checksumA).isNotNull();
        assertThat(checksumB).isNotNull();
        assertThat(checksumA).isEqualTo(checksumB);
    }

    @Test
    @Order(4)
    @DisplayName("og70_write_view() converges 100% between Clean Path A and Upgrade Path B (0 diff, SHA-256 match)")
    void testFunctionDefinitionConvergence() throws Exception {
        String defA = cleanJdbc.queryForObject(
                "SELECT pg_get_functiondef('public.og70_write_view()'::regprocedure)", String.class);
        String defB = upgradeJdbc.queryForObject(
                "SELECT pg_get_functiondef('public.og70_write_view()'::regprocedure)", String.class);

        assertThat(defA).isNotNull();
        assertThat(defB).isNotNull();

        // Normalize line breaks
        String normA = defA.replace("\r\n", "\n").trim();
        String normB = defB.replace("\r\n", "\n").trim();

        String shaA = computeSha256(normA);
        String shaB = computeSha256(normB);

        assertThat(normA).isEqualTo(normB);
        assertThat(shaA).isEqualTo(shaB);

        // Verify Invariants on converged function definition
        // 1. Defective branches have NO expires_at in INSERT column list
        assertThat(normA).doesNotContain("public.listing_fee_charges (charge_id,assessment_id,amount,state,created_at,command_key,record_type,expires_at)");
        assertThat(normA).doesNotContain("public.payments (source_payment_intents_intent_id,purpose,group_id,listing_charge_id,expected_amount,currency,payment_method,state,deadline,created_at,command_key,record_type,expires_at)");
        assertThat(normA).doesNotContain("public.penalty_ledger (entry_id,user_id,report_id,policy_id,points_delta,adjustment_of,decided_by,reason,created_at,command_key,record_type,expires_at)");
        assertThat(normA).doesNotContain("public.notifications (source_notification_dispatches_dispatch_id,sender_id,event_id,title,content,target_data,created_at,command_key,record_type,expires_at)");

        // 2. Repaired branches have correct INSERT column list
        assertThat(normA).contains("public.listing_fee_charges (charge_id,assessment_id,amount,state,created_at,command_key,record_type) SELECT");
        assertThat(normA).contains("public.payments (source_payment_intents_intent_id,purpose,group_id,listing_charge_id,expected_amount,currency,payment_method,state,deadline,created_at,command_key,record_type) SELECT");
        assertThat(normA).contains("public.penalty_ledger (entry_id,user_id,report_id,policy_id,points_delta,adjustment_of,decided_by,reason,created_at,command_key,record_type) SELECT");
        assertThat(normA).contains("public.notifications (source_notification_dispatches_dispatch_id,sender_id,event_id,title,content,target_data,created_at,command_key,record_type) SELECT");

        // 3. reward_ledger preserves expires_at
        assertThat(normA).contains("public.reward_ledger (entry_id,user_id,policy_id,entry_type,points_delta,order_id,group_id,actor_id,reason,created_at,command_key,record_type,expires_at) SELECT");
        assertThat(normA).contains("''reward_ledger'',r.expires_at FROM jsonb_populate_record(NULL::og_compat.reward_ledger");

        // 4. Prior migrations preserved
        assertThat(normA).contains("case_evidence");
        assertThat(normA).contains("request_id");
        assertThat(normA).contains("shipping_ghn_province_id");
        assertThat(normA).contains("reward_policy_id");
    }

    @Test
    @Order(5)
    @DisplayName("Functional Test: All 4 repaired branches and reward_ledger no longer fail with 'column expires_at does not exist'")
    void testFunctionalCompatibilityWrites() {
        // Setup initial admin user for sender / actor roles
        Long adminUserId = 999901L;
        cleanJdbc.update(
                "INSERT INTO og_compat.users (user_id, email, password_hash, full_name) " +
                "VALUES (?, ?, '{noop}pass', 'Test Admin User')",
                adminUserId, "admin_v51_" + UUID.randomUUID() + "@example.com");
        cleanJdbc.update(
                "INSERT INTO og_compat.user_roles (user_id, role_id) " +
                "SELECT ?, role_id FROM og_compat.roles WHERE role_name = 'ADMIN'",
                adminUserId);

        // Setup regular buyer user
        Long buyerUserId = 999902L;
        cleanJdbc.update(
                "INSERT INTO og_compat.users (user_id, email, password_hash, full_name) " +
                "VALUES (?, ?, '{noop}pass', 'Test Buyer User')",
                buyerUserId, "buyer_v51_" + UUID.randomUUID() + "@example.com");

        // 1. Branch: notification_dispatches -> public.notifications (Full end-to-end write)
        UUID eventId = UUID.randomUUID();
        cleanJdbc.update(
                "INSERT INTO og_compat.outbox_events (event_id, aggregate_type, aggregate_id, event_type, payload) " +
                "VALUES (?, 'SYSTEM', 'SYS1', 'DISPATCH', '{}'::jsonb)",
                eventId);

        UUID dispatchId = UUID.randomUUID();
        cleanJdbc.update(
                "INSERT INTO og_compat.notification_dispatches (dispatch_id, sender_id, event_id, title, content, target_data, command_key) " +
                "VALUES (?, ?, ?, 'V51 Notification', 'Test notification dispatch', '{}'::jsonb, ?)",
                dispatchId, adminUserId, eventId, "dispatch_" + UUID.randomUUID());

        Map<String, Object> notifRow = cleanJdbc.queryForMap(
                "SELECT source_notification_dispatches_dispatch_id, title FROM public.notifications WHERE source_notification_dispatches_dispatch_id = ?",
                dispatchId);
        assertThat(notifRow.get("source_notification_dispatches_dispatch_id")).isEqualTo(dispatchId);
        assertThat(notifRow.get("title")).isEqualTo("V51 Notification");

        // 2. Branch: listing_fee_charges -> verify column 'expires_at' error is eliminated
        assertThatThrownBy(() -> cleanJdbc.update(
                "INSERT INTO og_compat.listing_fee_charges (charge_id, assessment_id, amount, state, command_key) " +
                "VALUES (?, 1, 1000, 'PENDING', ?)",
                UUID.randomUUID(), "charge_" + UUID.randomUUID()))
                .hasMessageNotContaining("expires_at")
                .hasMessageContaining("Listing fee charge must equal positive assessment due");

        // 3. Branch: payment_intents -> verify column 'expires_at' error is eliminated
        assertThatThrownBy(() -> cleanJdbc.update(
                "INSERT INTO og_compat.payment_intents (intent_id, purpose, group_id, expected_amount, currency, payment_method, state, deadline, command_key) " +
                "VALUES (?, 'CHECKOUT_GROUP', ?, 50000, 'VND', 'COD', 'PENDING', NOW() + INTERVAL '1 day', ?)",
                UUID.randomUUID(), UUID.randomUUID(), "intent_" + UUID.randomUUID()))
                .hasMessageNotContaining("expires_at")
                .hasMessageContaining("Payment amount/deadline/method differs from checkout");

        // 4. Branch: penalty_ledger -> verify column 'expires_at' error is eliminated
        assertThatThrownBy(() -> cleanJdbc.update(
                "INSERT INTO og_compat.penalty_ledger (entry_id, user_id, report_id, policy_id, points_delta, decided_by, reason, command_key) " +
                "VALUES (?, ?, NULL, 1, -10, ?, 'Test penalty', ?)",
                UUID.randomUUID(), buyerUserId, adminUserId, "penalty_" + UUID.randomUUID()))
                .hasMessageNotContaining("expires_at")
                .hasMessageContaining("Penalty requires confirmed KTV violation/correction");

        // 5. Branch: reward_ledger -> verify expires_at is required and preserved
        cleanJdbc.update(
                "INSERT INTO og_compat.reward_accounts (user_id, balance, version) VALUES (?, 0, 0)",
                buyerUserId);

        assertThatThrownBy(() -> cleanJdbc.update(
                "INSERT INTO og_compat.reward_ledger (entry_id, user_id, policy_id, entry_type, points_delta, order_id, actor_id, reason, command_key) " +
                "VALUES (?, ?, 1, 'CREDIT_REFUND', 200, 1, ?, 'Test refund', ?)",
                UUID.randomUUID(), buyerUserId, adminUserId, "reward_no_exp_" + UUID.randomUUID()))
                .hasMessageContaining("missing expires_at");

        // 6. Branch: reward_ledger V56 CREDIT_REVIEW -> no longer blocked by legacy entry_type_check; requires order_id
        assertThatThrownBy(() -> cleanJdbc.update(
                "INSERT INTO og_compat.reward_ledger (entry_id, user_id, entry_type, points_delta, order_id, reason, command_key) " +
                "VALUES (?, ?, 'CREDIT_REVIEW', 500, NULL, 'Review bonus', ?)",
                UUID.randomUUID(), buyerUserId, "review_no_order_" + UUID.randomUUID()))
                .hasMessageNotContaining("reward_ledger_entry_type_check")
                .hasMessageContaining("Required field in reward_ledger CREDIT_REVIEW");
    }

    private static String computeSha256(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
