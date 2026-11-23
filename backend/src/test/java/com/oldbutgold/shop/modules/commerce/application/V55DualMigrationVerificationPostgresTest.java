package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.platform.infrastructure.LegacyModerationHistoryCallback;
import com.oldbutgold.shop.shared.PostgresTestSafetyGuard;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verification of V55–V57 Dual-Path Migrations and og70_write_view() / og70_dispatch() convergence.
 *
 * <p>OPT-IN EXECUTION ONLY:
 * This verification drops and recreates dedicated migration test databases.
 * It is strictly isolated from the default backend regression test suite via:
 * 1. Maven profile 'migration-verification' (excluded from default surefire).
 * 2. JUnit tag 'migration-verification'.
 * 3. Environment gate OGSHOP_MIGRATION_VERIFY_ENABLED=true.
 * 4. Dedicated instance configuration via OGSHOP_MIGRATION_VERIFY_HOST_PORT.
 * 5. Strict safety guard preventing execution against shared DB (54432), dev DB (5433),
 *    protected DBs, or the active backend integration-test DB (OGSHOP_TEST_DB_URL).
 *
 * <p>Verifies:
 * 1. Clean Path A: V1 -> V57 on fresh database (og_shop_v55_clean_verify_test).
 * 2. Upgrade Path B: V1 -> V40 (main baseline), then V41..V57 on (og_shop_v55_upgrade_verify_test).
 * 3. Flyway checksum preservation for V48, V49, V57 and flyway validate success on both paths.
 * 4. 100% convergence (0 diff, matching SHA-256) of og70_write_view() AND og70_dispatch() between Path A and Path B.
 * 5. Functional compatibility writes on all 4 repaired branches plus reward_ledger (CREDIT_REFUND & V56 CREDIT_REVIEW).
 */
@Tag("migration-verification")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "OGSHOP_MIGRATION_VERIFY_ENABLED", matches = "true")
class V55DualMigrationVerificationPostgresTest {

    public static final String ENV_MIGRATION_VERIFY_ENABLED = "OGSHOP_MIGRATION_VERIFY_ENABLED";
    public static final String ENV_MIGRATION_VERIFY_HOST_PORT = "OGSHOP_MIGRATION_VERIFY_HOST_PORT";
    public static final String ENV_MIGRATION_VERIFY_USER = "OGSHOP_MIGRATION_VERIFY_USER";
    public static final String ENV_MIGRATION_VERIFY_PASSWORD = "OGSHOP_MIGRATION_VERIFY_PASSWORD";

    private static final String CLEAN_DB_NAME = "og_shop_v55_clean_verify_test";
    private static final String UPGRADE_DB_NAME = "og_shop_v55_upgrade_verify_test";

    private static DataSource cleanDataSource;
    private static DataSource upgradeDataSource;

    private static JdbcTemplate cleanJdbc;
    private static JdbcTemplate upgradeJdbc;

    @BeforeAll
    static void setUp() {
        String hostPort = System.getenv(ENV_MIGRATION_VERIFY_HOST_PORT);
        if (hostPort == null || hostPort.isBlank()) {
            throw new IllegalStateException(
                    "Migration verification aborted: Dedicated PostgreSQL host:port must be configured via environment variable "
                    + ENV_MIGRATION_VERIFY_HOST_PORT + " (e.g. localhost:55332). "
                    + "Automatic fallback to default ports is strictly prohibited."
            );
        }
        hostPort = hostPort.trim();

        String dbUser = System.getenv(ENV_MIGRATION_VERIFY_USER);
        if (dbUser == null || dbUser.isBlank()) {
            dbUser = "og_shop";
        }
        String dbPass = System.getenv(ENV_MIGRATION_VERIFY_PASSWORD);
        if (dbPass == null || dbPass.isBlank()) {
            dbPass = "postgres";
        }

        String cleanJdbcUrl = "jdbc:postgresql://" + hostPort + "/" + CLEAN_DB_NAME;
        String upgradeJdbcUrl = "jdbc:postgresql://" + hostPort + "/" + UPGRADE_DB_NAME;

        assertSafeMigrationVerificationEnvironment(hostPort, cleanJdbcUrl, upgradeJdbcUrl);

        DataSource rootDs = new DriverManagerDataSource("jdbc:postgresql://" + hostPort + "/postgres", dbUser, dbPass);
        JdbcTemplate rootJdbc = new JdbcTemplate(rootDs);

        String systemIdentifier = rootJdbc.queryForObject("SELECT system_identifier FROM pg_control_system()", String.class);
        if (!"7709359968475385894".equals(systemIdentifier)) {
            throw new IllegalStateException("PostgreSQL system_identifier mismatch! Expected 7709359968475385894, found: " + systemIdentifier);
        }

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

    /**
     * Safety guard ensuring migration verification never executes against:
     * 1. Development database (port 5433).
     * 2. Shared test database (port 54432, og_shop_bd5_test).
     * 3. Protected databases or the active backend integration-test database.
     */
    static void assertSafeMigrationVerificationEnvironment(String targetHostPort, String cleanUrl, String upgradeUrl) {
        if (!CLEAN_DB_NAME.equals("og_shop_v55_clean_verify_test") || !UPGRADE_DB_NAME.equals("og_shop_v55_upgrade_verify_test")) {
            throw new IllegalStateException("Migration verification aborted: Unauthorized verification database names!");
        }

        Set<String> protectedDbs = Set.of(
                "og_shop_v51_clean_test",
                "og_shop_v51_upgrade_test",
                "og_shop_v51_clean_verify_test",
                "og_shop_v51_upgrade_verify_test",
                "og_shop_demo_isolated_test",
                "og_shop_isolated_test",
                "og_shop_backend_regression_clean_test",
                "og_shop_bd5_test",
                "og_shop",
                "postgres"
        );
        if (protectedDbs.contains(CLEAN_DB_NAME.toLowerCase(Locale.ROOT)) || protectedDbs.contains(UPGRADE_DB_NAME.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Migration verification aborted: Attempting to target protected database!");
        }

        if (targetHostPort.contains(":54432") || cleanUrl.contains("54432") || cleanUrl.contains("og_shop_bd5_test")) {
            throw new IllegalStateException("Migration verification aborted: Execution against shared database (port 54432) is strictly prohibited!");
        }
        if (targetHostPort.contains(":5433") || cleanUrl.contains("5433")) {
            throw new IllegalStateException("Migration verification aborted: Execution against development database (port 5433) is strictly prohibited!");
        }

        PostgresTestSafetyGuard.assertSafeTestDatabase(cleanUrl, "true");
        PostgresTestSafetyGuard.assertSafeTestDatabase(upgradeUrl, "true");

        String backendTestUrl = System.getenv(PostgresTestSafetyGuard.ENV_TEST_DB_URL);
        if (backendTestUrl != null && !backendTestUrl.isBlank()) {
            try {
                URI backendUri = URI.create(backendTestUrl.trim().substring("jdbc:".length()));
                String backendPath = backendUri.getPath();
                if (backendPath != null && backendPath.length() > 1) {
                    String backendDb = backendPath.substring(1).toLowerCase(Locale.ROOT);
                    if (CLEAN_DB_NAME.equalsIgnoreCase(backendDb) || UPGRADE_DB_NAME.equalsIgnoreCase(backendDb)) {
                        throw new IllegalStateException(
                                "Migration verification aborted: Target database collides with active backend test database '" + backendDb + "'!"
                        );
                    }
                }
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("Path A: Clean migration from V1 to V57 succeeds and validates")
    void testPathACleanMigration() {
        Flyway flyway = Flyway.configure()
                .dataSource(cleanDataSource)
                .schemas("public")
                .defaultSchema("public")
                .callbacks(new LegacyModerationHistoryCallback())
                .load();

        var result = flyway.migrate();
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(57);

        // Flyway validate must pass cleanly
        flyway.validate();

        // Check flyway_schema_history
        Integer totalMigrations = cleanJdbc.queryForObject(
                "SELECT count(*) FROM public.flyway_schema_history WHERE success = true", Integer.class);
        assertThat(totalMigrations).isGreaterThanOrEqualTo(57);

        // V48, V49, V55, V56, V57 must be recorded with success = true
        for (String ver : new String[]{"48", "49", "55", "56", "57"}) {
            Boolean verSuccess = cleanJdbc.queryForObject(
                    "SELECT success FROM public.flyway_schema_history WHERE version = ?", Boolean.class, ver);
            assertThat(verSuccess).as("Version " + ver + " must succeed").isTrue();
        }
    }

    @Test
    @Order(2)
    @DisplayName("Path B: Upgrade migration from main (V1..V40) through V57 succeeds and validates")
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

        // Step 2: Migrate to latest (V41..V57)
        Flyway flywayLatest = Flyway.configure()
                .dataSource(upgradeDataSource)
                .schemas("public")
                .defaultSchema("public")
                .callbacks(new LegacyModerationHistoryCallback())
                .load();

        var upgradeResult = flywayLatest.migrate();
        assertThat(upgradeResult.migrationsExecuted).isEqualTo(17);

        // Flyway validate must pass cleanly on Path B
        flywayLatest.validate();

        for (String ver : new String[]{"48", "49", "55", "56", "57"}) {
            Boolean verSuccess = upgradeJdbc.queryForObject(
                    "SELECT success FROM public.flyway_schema_history WHERE version = ?", Boolean.class, ver);
            assertThat(verSuccess).as("Upgrade version " + ver + " must succeed").isTrue();
        }
    }

    @Test
    @Order(3)
    @DisplayName("V48, V49, and V57 Checksums are preserved and identical across Path A and Path B")
    void testV48ChecksumPreserved() {
        for (String ver : new String[]{"48", "49", "57"}) {
            Integer checksumA = cleanJdbc.queryForObject(
                    "SELECT checksum FROM public.flyway_schema_history WHERE version = ?", Integer.class, ver);
            Integer checksumB = upgradeJdbc.queryForObject(
                    "SELECT checksum FROM public.flyway_schema_history WHERE version = ?", Integer.class, ver);

            assertThat(checksumA).as("Path A checksum for V" + ver).isNotNull();
            assertThat(checksumB).as("Path B checksum for V" + ver).isNotNull();
            assertThat(checksumA).as("Checksum parity for V" + ver).isEqualTo(checksumB);
        }
    }

    @Test
    @Order(4)
    @DisplayName("og70_write_view() and og70_dispatch() converge 100% between Clean Path A and Upgrade Path B (0 diff, SHA-256 match)")
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

        // Verify og70_dispatch() convergence and V57 expires_at forwarding invariants
        String dispatchA = cleanJdbc.queryForObject(
                "SELECT pg_get_functiondef('public.og70_dispatch()'::regprocedure)", String.class);
        String dispatchB = upgradeJdbc.queryForObject(
                "SELECT pg_get_functiondef('public.og70_dispatch()'::regprocedure)", String.class);

        assertThat(dispatchA).isNotNull();
        assertThat(dispatchB).isNotNull();

        String normDispatchA = dispatchA.replace("\r\n", "\n").trim();
        String normDispatchB = dispatchB.replace("\r\n", "\n").trim();

        assertThat(normDispatchA).isEqualTo(normDispatchB);
        assertThat(computeSha256(normDispatchA)).isEqualTo(computeSha256(normDispatchB));

        // V57 invariant checks on og70_dispatch() reward_ledger branch
        assertThat(normDispatchA).contains("'expires_at',n->'expires_at'");
        assertThat(normDispatchA).contains("'expires_at',o->'expires_at'");
        assertThat(normDispatchA).contains("IF TG_OP<>'DELETE' THEN n:=jsonb_set(n,ARRAY['expires_at'],coalesce(result->'expires_at','null'::jsonb),true); END IF;");
        assertThat(normDispatchA).contains("IF TG_OP<>'DELETE' THEN PERFORM og70_validate_row('reward_ledger',result); END IF;");

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
