package com.oldbutgold.shop.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostgresTestSafetyGuardTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("1. Thiếu URL: Ném IllegalStateException khi URL null hoặc rỗng")
    void testRejectsMissingUrl(String url) {
        assertThatThrownBy(() -> PostgresTestSafetyGuard.assertSafeTestDatabase(url, "true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JDBC URL is required and cannot be empty");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"false", "FALSE", "no", "0", "random", " "})
    @DisplayName("2. Thiếu hoặc sai cờ OGSHOP_TEST_DB_ISOLATED: Bị từ chối khi flag không phải 'true'")
    void testRejectsMissingOrFalseIsolationFlag(String flag) {
        String validUrl = "jdbc:postgresql://127.0.0.1:5432/og_shop_test";
        assertThatThrownBy(() -> PostgresTestSafetyGuard.assertSafeTestDatabase(validUrl, flag))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OGSHOP_TEST_DB_ISOLATED=true is required");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "postgresql://127.0.0.1:5432/og_shop_test",
            "jdbc:mysql://127.0.0.1:3306/og_shop_test",
            "jdbc:h2:mem:testdb",
            "http://127.0.0.1:5432/og_shop_test",
            "jdbc:postgresql:not_a_valid_uri"
    })
    @DisplayName("3. URL PostgreSQL sai định dạng hoặc không có tiền tố jdbc:postgresql://")
    void testRejectsMalformedJdbcUrl(String malformedUrl) {
        assertThatThrownBy(() -> PostgresTestSafetyGuard.assertSafeTestDatabase(malformedUrl, "true"))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://localhost:5433/og_shop_test",
            "jdbc:postgresql://127.0.0.1:5433/og_shop_test",
            "jdbc:postgresql://[::1]:5433/og_shop_test",
            "jdbc:postgresql://127.0.0.1:5433/og_shop_test?sslmode=disable"
    })
    @DisplayName("4. Chặn port dev 5433: Cấm tuyệt đối kết nối đến port của container dev")
    void testRejectsDevPort5433(String devPortUrl) {
        assertThatThrownBy(() -> PostgresTestSafetyGuard.assertSafeTestDatabase(devPortUrl, "true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Dev port 5433 is strictly prohibited");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://localhost:5432/og_shop",
            "jdbc:postgresql://127.0.0.1:5432/og_shop_dev",
            "jdbc:postgresql://127.0.0.1:5432/og_shop_development",
            "jdbc:postgresql://127.0.0.1:5432/postgres",
            "jdbc:postgresql://127.0.0.1:5432/master",
            "jdbc:postgresql://127.0.0.1:5432/main",
            "jdbc:postgresql://127.0.0.1:5432/production",
            "jdbc:postgresql://127.0.0.1:5432/og_shop_prod"
    })
    @DisplayName("5. Tên database production/dev đã biết: Bị chặn ngay lập tức")
    void testRejectsKnownDevAndProdDatabaseNames(String prohibitedDbUrl) {
        assertThatThrownBy(() -> PostgresTestSafetyGuard.assertSafeTestDatabase(prohibitedDbUrl, "true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prohibited");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://localhost:5432/customer_database",
            "jdbc:postgresql://127.0.0.1:5432/my_app",
            "jdbc:postgresql://127.0.0.1:5432/staging_data",
            "jdbc:postgresql://127.0.0.1:5432/dev_data",
            "jdbc:postgresql://127.0.0.1:5432/shop_backup"
    })
    @DisplayName("6. Tên database không đáp ứng chính sách test (thiếu hậu tố _test hoặc tiền tố test_)")
    void testRejectsDatabasesViolatingTestNamingPolicy(String nonTestDbUrl) {
        assertThatThrownBy(() -> PostgresTestSafetyGuard.assertSafeTestDatabase(nonTestDbUrl, "true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not satisfy the test naming policy");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://db.production.internal:5432/og_shop_test",
            "jdbc:postgresql://rds.amazonaws.com:5432/og_shop_test",
            "jdbc:postgresql://192.168.1.100:5432/og_shop_test",
            "jdbc:postgresql://10.0.0.5:5432/og_shop_test",
            "jdbc:postgresql://example.com:5432/og_shop_test"
    })
    @DisplayName("7. Host từ xa hoặc chưa xác minh: Bị chặn để ngăn chặn rủi ro dữ liệu ngoài")
    void testRejectsRemoteOrUnverifiedHosts(String remoteHostUrl) {
        assertThatThrownBy(() -> PostgresTestSafetyGuard.assertSafeTestDatabase(remoteHostUrl, "true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not an authorized local or test service host");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://localhost:5432/og_shop_test",
            "jdbc:postgresql://127.0.0.1:5432/og_shop_test",
            "jdbc:postgresql://127.0.0.1:45560/og_shop_test",
            "jdbc:postgresql://[::1]:5432/og_shop_test",
            "jdbc:postgresql://postgres:5432/og_shop_test",
            "jdbc:postgresql://test-db:5432/test_db",
            "jdbc:postgresql://127.0.0.1:5432/og-shop-test",
            "jdbc:postgresql://127.0.0.1:5432/test_isolated",
            "jdbc:postgresql://127.0.0.1:45560/og_shop_test?sslmode=disable"
    })
    @DisplayName("8. URL hợp lệ trỏ đến database test cô lập: Được chấp thuận")
    void testAcceptsValidIsolatedTestDatabaseUrls(String validUrl) {
        String result = PostgresTestSafetyGuard.assertSafeTestDatabase(validUrl, "true");
        assertThat(result).isEqualTo(validUrl);
    }

    @Test
    @DisplayName("9. assertSafeTestEnvironment: Ném ngoại lệ khi môi trường hiện tại chưa cấu hình đúng flag")
    void testAssertSafeTestEnvironmentFailsWhenEnvNotProperlyConfigured() {
        if (System.getenv("OGSHOP_TEST_DB_URL") == null || !"true".equalsIgnoreCase(System.getenv("OGSHOP_TEST_DB_ISOLATED"))) {
            assertThatThrownBy(PostgresTestSafetyGuard::assertSafeTestEnvironment)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("10. resolveAndAssertSafe: Ưu tiên URL chính, fallback URL phụ và kiểm tra an toàn")
    void testResolveAndAssertSafeFallbackAndValidation() {
        String validUrlPrimary = "jdbc:postgresql://127.0.0.1:45560/moderation_test";
        String validUrlFallback = "jdbc:postgresql://127.0.0.1:45560/fallback_test";
        String devPortUrl = "jdbc:postgresql://127.0.0.1:5433/moderation_test";

        // Case 1: Cả 2 biến đều có, chọn biến đầu tiên (primary)
        java.util.Map<String, String> env1 = java.util.Map.of(
                "OGSHOP_MODERATION_TEST_DB_URL", validUrlPrimary,
                "OGSHOP_TEST_DB_URL", validUrlFallback
        );
        String resolved1 = PostgresTestSafetyGuard.resolveAndAssertSafe(env1::get, "true",
                "OGSHOP_MODERATION_TEST_DB_URL", "OGSHOP_TEST_DB_URL");
        assertThat(resolved1).isEqualTo(validUrlPrimary);

        // Case 2: Biến chính rỗng/null, fallback sang biến thứ 2
        java.util.Map<String, String> env2 = java.util.Map.of(
                "OGSHOP_MODERATION_TEST_DB_URL", "   ",
                "OGSHOP_TEST_DB_URL", validUrlFallback
        );
        String resolved2 = PostgresTestSafetyGuard.resolveAndAssertSafe(env2::get, "true",
                "OGSHOP_MODERATION_TEST_DB_URL", "OGSHOP_TEST_DB_URL");
        assertThat(resolved2).isEqualTo(validUrlFallback);

        // Case 3: Biến chính vi phạm cổng dev 5433 -> bị chặn ngay dù fallback hợp lệ
        java.util.Map<String, String> env3 = java.util.Map.of(
                "OGSHOP_MODERATION_TEST_DB_URL", devPortUrl,
                "OGSHOP_TEST_DB_URL", validUrlFallback
        );
        assertThatThrownBy(() -> PostgresTestSafetyGuard.resolveAndAssertSafe(env3::get, "true",
                "OGSHOP_MODERATION_TEST_DB_URL", "OGSHOP_TEST_DB_URL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Dev port 5433 is strictly prohibited");

        // Case 4: Không có biến nào được cấu hình
        java.util.Map<String, String> env4 = java.util.Map.of();
        assertThatThrownBy(() -> PostgresTestSafetyGuard.resolveAndAssertSafe(env4::get, "true",
                "OGSHOP_MODERATION_TEST_DB_URL", "OGSHOP_TEST_DB_URL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("None of the candidate test database environment variables were configured");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "OGSHOP_TEST_DB_URL",
            "OGSHOP_MODERATION_TEST_DB_URL",
            "OGSHOP_CATEGORY_TEST_DB_URL",
            "OGSHOP_UPGRADE_TEST_DB_URL"
    })
    @DisplayName("11. Tất cả 4 biến môi trường (TEST_DB_URL, MODERATION, CATEGORY, UPGRADE) đều được bảo vệ bởi guard")
    void testAllNamedEnvironmentVariablesAreGuarded(String envVarName) {
        String devUrl = "jdbc:postgresql://localhost:5433/og_shop_test";
        String validUrl = "jdbc:postgresql://localhost:5432/og_shop_test";

        java.util.Map<String, String> devEnv = java.util.Map.of(envVarName, devUrl);
        assertThatThrownBy(() -> PostgresTestSafetyGuard.resolveAndAssertSafe(devEnv::get, "true", envVarName))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Dev port 5433 is strictly prohibited");

        java.util.Map<String, String> validEnv = java.util.Map.of(envVarName, validUrl);
        String resolved = PostgresTestSafetyGuard.resolveAndAssertSafe(validEnv::get, "true", envVarName);
        assertThat(resolved).isEqualTo(validUrl);
    }

    @Test
    @DisplayName("12. Flyway opt-in: Mặc định disabled khi không có biến môi trường hoặc giá trị không phải 'true'")
    void testFlywayOptInDefaultDisabled() {
        // null lookup
        assertThat(PostgresTestSafetyGuard.isFlywayOptInEnabled(null)).isFalse();

        // missing / null
        java.util.Map<String, String> emptyEnv = java.util.Map.of();
        assertThat(PostgresTestSafetyGuard.isFlywayOptInEnabled(emptyEnv::get)).isFalse();

        // non-true values
        for (String invalid : new String[]{"", "   ", "false", "FALSE", "0", "no", "yes", "enabled", "1"}) {
            java.util.Map<String, String> env = java.util.Map.of(PostgresTestSafetyGuard.ENV_TEST_ENABLE_FLYWAY, invalid);
            assertThat(PostgresTestSafetyGuard.isFlywayOptInEnabled(env::get))
                    .as("Value '%s' must not enable Flyway", invalid)
                    .isFalse();
        }

        // explicit opt-in
        java.util.Map<String, String> optInEnv = java.util.Map.of(PostgresTestSafetyGuard.ENV_TEST_ENABLE_FLYWAY, "true");
        assertThat(PostgresTestSafetyGuard.isFlywayOptInEnabled(optInEnv::get)).isTrue();

        java.util.Map<String, String> optInCapsEnv = java.util.Map.of(PostgresTestSafetyGuard.ENV_TEST_ENABLE_FLYWAY, "TRUE");
        assertThat(PostgresTestSafetyGuard.isFlywayOptInEnabled(optInCapsEnv::get)).isTrue();
    }

    // =========================================================================
    // Demo Seed Database Safety Guard Tests (Nhóm F - Phase 2 Đợt 3 Fail-Closed)
    // =========================================================================

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("13. Demo Seed Guard: Thiếu URL hoặc URL rỗng bị từ chối")
    void testDemoSeedRejectsMissingUrl(String url) {
        assertThatThrownBy(() -> PostgresTestSafetyGuard.assertSafeDemoSeedDatabase(url, "true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Demo Seed JDBC URL is required");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"false", "FALSE", "no", "0", "random", " "})
    @DisplayName("14. Demo Seed Guard: Thiếu hoặc sai cờ OGSHOP_TEST_DB_ISOLATED bị từ chối")
    void testDemoSeedRejectsMissingIsolationFlag(String flag) {
        String validUrl = "jdbc:postgresql://localhost:55332/og_shop_demo_isolated_test";
        assertThatThrownBy(() -> PostgresTestSafetyGuard.assertSafeDemoSeedDatabase(validUrl, flag))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OGSHOP_TEST_DB_ISOLATED=true is required");
    }

    @Test
    @DisplayName("15. Demo Seed Guard: Thiếu biến môi trường khi resolve bị từ chối fail-closed")
    void testDemoSeedRejectsMissingEnvVariable() {
        java.util.Map<String, String> emptyEnv = java.util.Map.of();
        assertThatThrownBy(() -> PostgresTestSafetyGuard.resolveAndAssertSafeDemoSeed(emptyEnv::get, "true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Demo Seed aborted! Target database URL is missing");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://localhost:55332/og_shop_demo_isolated_test",
            "jdbc:postgresql://127.0.0.1:55332/og_shop_demo_isolated_test"
    })
    @DisplayName("16. Demo Seed Guard: Đúng tuple được phê duyệt (host: localhost/127.0.0.1, port: 55332, db: og_shop_demo_isolated_test) -> PASS")
    void testDemoSeedAcceptsApprovedTargetTuple(String validUrl) {
        String validated = PostgresTestSafetyGuard.assertSafeDemoSeedDatabase(validUrl, "true");
        assertThat(validated).isEqualTo(validUrl);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://remote-db.production.internal:55332/og_shop_demo_isolated_test",
            "jdbc:postgresql://192.168.1.50:55332/og_shop_demo_isolated_test",
            "jdbc:postgresql://postgres:55332/og_shop_demo_isolated_test",
            "jdbc:postgresql://db:55332/og_shop_demo_isolated_test",
            "jdbc:postgresql://test-db:55332/og_shop_demo_isolated_test",
            "jdbc:postgresql://someotherhost:55332/og_shop_demo_isolated_test"
    })
    @DisplayName("17. Demo Seed Guard: Sai host -> REJECT (chỉ chấp nhận loopback host localhost/127.0.0.1)")
    void testDemoSeedRejectsUnapprovedHost(String invalidHostUrl) {
        assertThatThrownBy(() -> PostgresTestSafetyGuard.assertSafeDemoSeedDatabase(invalidHostUrl, "true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not approved for Demo Seed");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://localhost:5432/og_shop_demo_isolated_test",
            "jdbc:postgresql://localhost:55333/og_shop_demo_isolated_test",
            "jdbc:postgresql://localhost:55000/og_shop_demo_isolated_test",
            "jdbc:postgresql://localhost:56000/og_shop_demo_isolated_test"
    })
    @DisplayName("18. Demo Seed Guard: Sai port -> REJECT (chỉ chấp nhận port 55332, không dùng rule >= 55000 làm isolation)")
    void testDemoSeedRejectsUnapprovedPort(String invalidPortUrl) {
        assertThatThrownBy(() -> PostgresTestSafetyGuard.assertSafeDemoSeedDatabase(invalidPortUrl, "true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not the approved Demo Seed port");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://localhost:55332/wrong_name",
            "jdbc:postgresql://localhost:55332/another_db"
    })
    @DisplayName("19. Demo Seed Guard: Sai database name -> REJECT")
    void testDemoSeedRejectsWrongDatabaseName(String wrongDbUrl) {
        assertThatThrownBy(() -> PostgresTestSafetyGuard.assertSafeDemoSeedDatabase(wrongDbUrl, "true"))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://localhost:55332/og_shop_demo_seed_test",
            "jdbc:postgresql://localhost:55332/og_shop_demo_test",
            "jdbc:postgresql://localhost:55332/og_shop_isolated_test",
            "jdbc:postgresql://localhost:55332/random_generic_test",
            "jdbc:postgresql://localhost:55332/customer_orders_test",
            "jdbc:postgresql://localhost:55332/custom_qa_seed_test"
    })
    @DisplayName("20. Demo Seed Guard: Target có tên _test nhưng không đúng tuple -> REJECT (chỉ chấp nhận og_shop_demo_isolated_test)")
    void testDemoSeedRejectsUnapprovedTestNamedDatabases(String nonTupleTestDbUrl) {
        assertThatThrownBy(() -> PostgresTestSafetyGuard.assertSafeDemoSeedDatabase(nonTupleTestDbUrl, "true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not the approved Demo Seed target");
    }

    @Test
    @DisplayName("21. Demo Seed Guard: Custom allowlist ENV không còn khả năng mở rộng target -> REJECT")
    void testDemoSeedRejectsCustomAllowlistExpansion() {
        String customUrl = "jdbc:postgresql://localhost:55332/custom_qa_seed_test";
        java.util.Map<String, String> envWithCustomAllowlist = java.util.Map.of(
                PostgresTestSafetyGuard.ENV_DEMO_SEED_DB_URL, customUrl,
                "OGSHOP_DEMO_SEED_ALLOWLIST", "custom_qa_seed_test,other_test"
        );
        assertThatThrownBy(() -> PostgresTestSafetyGuard.resolveAndAssertSafeDemoSeed(envWithCustomAllowlist::get, "true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not the approved Demo Seed target");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://localhost:5433/og_shop",
            "jdbc:postgresql://localhost:54432/og_shop_bd5_test",
            "jdbc:postgresql://127.0.0.1:54432/og_shop_bd5_test",
            "jdbc:postgresql://localhost:55432/og_shop",
            "jdbc:postgresql://localhost:55332/og_shop",
            "jdbc:postgresql://localhost:55332/og_shop_bd5_test",
            "jdbc:postgresql://localhost:55332/og_shop_dev",
            "jdbc:postgresql://localhost:55332/postgres",
            "jdbc:postgresql://localhost:55332/master"
    })
    @DisplayName("22. Demo Seed Guard: Dev/shared database hoặc dev/shared port bị từ chối")
    void testDemoSeedRejectsDevAndSharedDatabases(String prohibitedUrl) {
        assertThatThrownBy(() -> PostgresTestSafetyGuard.assertSafeDemoSeedDatabase(prohibitedUrl, "true"))
                .isInstanceOf(IllegalStateException.class);
    }

    // =========================================================================
    // Demo Seed Credentials Safety Tests (Nhóm F - Phase 2 Đợt 3)
    // =========================================================================

    @Test
    @DisplayName("23. Demo Seed Credentials: Thiếu username (null) bị từ chối")
    void testDemoSeedCredentialsRejectsNullUsername() {
        assertThatThrownBy(() -> PostgresTestSafetyGuard.assertSafeDemoSeedCredentials(null, "valid_password_123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Username is required and cannot be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    @DisplayName("24. Demo Seed Credentials: Username rỗng hoặc blank bị từ chối")
    void testDemoSeedCredentialsRejectsBlankUsername(String blankUser) {
        assertThatThrownBy(() -> PostgresTestSafetyGuard.assertSafeDemoSeedCredentials(blankUser, "valid_password_123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Username is required and cannot be blank");
    }

    @Test
    @DisplayName("25. Demo Seed Credentials: Thiếu password (null) bị từ chối")
    void testDemoSeedCredentialsRejectsNullPassword() {
        assertThatThrownBy(() -> PostgresTestSafetyGuard.assertSafeDemoSeedCredentials("og_demo_seed_runner", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Password is required and cannot be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    @DisplayName("26. Demo Seed Credentials: Password rỗng hoặc blank bị từ chối")
    void testDemoSeedCredentialsRejectsBlankPassword(String blankPass) {
        assertThatThrownBy(() -> PostgresTestSafetyGuard.assertSafeDemoSeedCredentials("og_demo_seed_runner", blankPass))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Password is required and cannot be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"og_shop", "OG_SHOP", " og_shop "})
    @DisplayName("27. Demo Seed Credentials: Cấm fallback hoặc dùng privileged user 'og_shop'")
    void testDemoSeedCredentialsRejectsOgShopUser(String ogShopUser) {
        assertThatThrownBy(() -> PostgresTestSafetyGuard.assertSafeDemoSeedCredentials(ogShopUser, "valid_password_123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Privileged dev user 'og_shop' is strictly prohibited");
    }

    @ParameterizedTest
    @ValueSource(strings = {"valid_runner", "postgres", "some_user", "root", "dev_user"})
    @DisplayName("28. Demo Seed Credentials: User không phải role được PO phê duyệt bị từ chối")
    void testDemoSeedCredentialsRejectsUnapprovedUser(String unapprovedUser) {
        assertThatThrownBy(() -> PostgresTestSafetyGuard.assertSafeDemoSeedCredentials(unapprovedUser, "valid_password_123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only dedicated isolated role 'og_demo_seed_runner' is approved by PO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"root", "ROOT", " root "})
    @DisplayName("29. Demo Seed Credentials: Cấm fallback hoặc dùng default dev password 'root'")
    void testDemoSeedCredentialsRejectsRootPassword(String rootPass) {
        assertThatThrownBy(() -> PostgresTestSafetyGuard.assertSafeDemoSeedCredentials("og_demo_seed_runner", rootPass))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Default dev password 'root' is strictly prohibited");
    }

    @Test
    @DisplayName("30. Demo Seed Credentials: Resolve từ env thiếu user hoặc pass bị từ chối fail-closed (không fallback ngầm)")
    void testDemoSeedCredentialsResolveFailClosed() {
        java.util.Map<String, String> emptyEnv = java.util.Map.of();
        assertThatThrownBy(() -> PostgresTestSafetyGuard.resolveAndAssertSafeDemoSeedCredentials(emptyEnv::get))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Username is required and cannot be blank");

        java.util.Map<String, String> userOnlyEnv = java.util.Map.of(
                PostgresTestSafetyGuard.ENV_DEMO_SEED_DB_USER, "og_demo_seed_runner"
        );
        assertThatThrownBy(() -> PostgresTestSafetyGuard.resolveAndAssertSafeDemoSeedCredentials(userOnlyEnv::get))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Password is required and cannot be blank");
    }

    @Test
    @DisplayName("31. Demo Seed Credentials: Đúng role 'og_demo_seed_runner' và credentials độc lập hợp lệ được chấp thuận")
    void testDemoSeedCredentialsAcceptsValidIndependentCredentials() {
        var creds = PostgresTestSafetyGuard.assertSafeDemoSeedCredentials("  og_demo_seed_runner  ", "StrongPass!987");
        assertThat(creds.username()).isEqualTo("og_demo_seed_runner");
        assertThat(creds.password()).isEqualTo("StrongPass!987");

        java.util.Map<String, String> validEnv = java.util.Map.of(
                PostgresTestSafetyGuard.ENV_DEMO_SEED_DB_USER, "og_demo_seed_runner",
                PostgresTestSafetyGuard.ENV_DEMO_SEED_DB_PASSWORD, "SecretIsolatedPass456"
        );
        var resolved = PostgresTestSafetyGuard.resolveAndAssertSafeDemoSeedCredentials(validEnv::get);
        assertThat(resolved.username()).isEqualTo("og_demo_seed_runner");
        assertThat(resolved.password()).isEqualTo("SecretIsolatedPass456");
    }

    @Test
    @DisplayName("32. Demo Seed Credentials: toString() che mật khẩu (redacted) và không rò rỉ secret ra log")
    void testDemoSeedCredentialsToStringRedactsPassword() {
        var creds = PostgresTestSafetyGuard.assertSafeDemoSeedCredentials("og_demo_seed_runner", "SuperSecretPass!123");
        String stringRep = creds.toString();
        assertThat(stringRep).contains("***REDACTED***");
        assertThat(stringRep).doesNotContain("SuperSecretPass!123");
    }
}
