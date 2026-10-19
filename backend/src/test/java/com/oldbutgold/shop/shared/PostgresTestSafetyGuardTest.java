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
}
