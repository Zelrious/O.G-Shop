package com.oldbutgold.shop.shared;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Safety guard for PostgreSQL integration tests.
 * <p>
 * Ensures tests never accidentally execute against development (e.g. port 5433, db og_shop)
 * or production databases. Enforces structured URL validation, explicit isolation flag,
 * port blocking, host whitelisting, and strict test database naming conventions.
 */
public final class PostgresTestSafetyGuard {

    public static final String ENV_TEST_DB_URL = "OGSHOP_TEST_DB_URL";
    public static final String ENV_TEST_DB_ISOLATED = "OGSHOP_TEST_DB_ISOLATED";
    public static final String ENV_MODERATION_TEST_DB_URL = "OGSHOP_MODERATION_TEST_DB_URL";
    public static final String ENV_CATEGORY_TEST_DB_URL = "OGSHOP_CATEGORY_TEST_DB_URL";
    public static final String ENV_UPGRADE_TEST_DB_URL = "OGSHOP_UPGRADE_TEST_DB_URL";

    public static final int DEV_PORT = 5433;

    private static final Set<String> PROHIBITED_DATABASES = Set.of(
            "og_shop",
            "og_shop_dev",
            "og_shop_development",
            "og_shop_prod",
            "og_shop_production",
            "og_shop_main",
            "postgres",
            "template1",
            "template0",
            "master",
            "main",
            "prod",
            "production",
            "dev",
            "development",
            "staging",
            "stg"
    );

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "localhost",
            "127.0.0.1",
            "::1",
            "0.0.0.0",
            "postgres",
            "test-db",
            "test_db",
            "db"
    );

    private PostgresTestSafetyGuard() {
    }

    /**
     * Validates test environment using {@code System.getenv()}.
     *
     * @return the validated JDBC URL
     */
    public static String assertSafeTestEnvironment() {
        return assertSafeTestDatabase(
                System.getenv(ENV_TEST_DB_URL),
                System.getenv(ENV_TEST_DB_ISOLATED)
        );
    }

    /**
     * Validates the provided JDBC URL with {@code OGSHOP_TEST_DB_ISOLATED} from environment.
     *
     * @param jdbcUrl the JDBC URL to validate
     * @return the validated JDBC URL
     */
    public static String assertSafeTestDatabase(String jdbcUrl) {
        return assertSafeTestDatabase(jdbcUrl, System.getenv(ENV_TEST_DB_ISOLATED));
    }

    /**
     * Resolves the first non-blank URL from the provided environment variable names,
     * and asserts that it points to a safe isolated test database.
     *
     * @param envVarNames candidate environment variable names in priority order
     * @return the validated JDBC URL
     * @throws IllegalStateException if none of the candidates are configured or validation fails
     */
    public static String resolveAndAssertSafeFromEnv(String... envVarNames) {
        return resolveAndAssertSafe(System::getenv, System.getenv(ENV_TEST_DB_ISOLATED), envVarNames);
    }

    /**
     * Resolves the first non-blank URL from the provided candidate variable names using a custom lookup function
     * and isolation flag value, allowing full unit testing without mutating system environment.
     *
     * @param envLookup        function to look up variable value by name
     * @param isolatedEnvValue value of {@code OGSHOP_TEST_DB_ISOLATED}
     * @param envVarNames      candidate environment variable names in priority order
     * @return the validated JDBC URL
     * @throws IllegalStateException if none of the candidates are configured or validation fails
     */
    public static String resolveAndAssertSafe(
            java.util.function.Function<String, String> envLookup,
            String isolatedEnvValue,
            String... envVarNames
    ) {
        for (String envVar : envVarNames) {
            String value = envLookup.apply(envVar);
            if (value != null && !value.isBlank()) {
                return assertSafeTestDatabase(value, isolatedEnvValue);
            }
        }
        throw new IllegalStateException(
                "PostgresTestSafetyGuard: None of the candidate test database environment variables were configured: "
                + java.util.Arrays.toString(envVarNames)
        );
    }

    /**
     * Validates the JDBC URL and isolation flag.
     *
     * @param jdbcUrl          PostgreSQL JDBC URL
     * @param isolatedEnvValue value of {@code OGSHOP_TEST_DB_ISOLATED}
     * @return the validated JDBC URL
     * @throws IllegalStateException if any safety check fails
     */
    public static String assertSafeTestDatabase(String jdbcUrl, String isolatedEnvValue) {
        // 1. URL presence check
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalStateException("PostgresTestSafetyGuard: JDBC URL is required and cannot be empty.");
        }

        // 2. Isolation flag check
        if (isolatedEnvValue == null || !isolatedEnvValue.trim().equalsIgnoreCase("true")) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Test aborted! Environment variable " + ENV_TEST_DB_ISOLATED + "=true is required "
                    + "to confirm execution against an isolated test database. Current value: '" + isolatedEnvValue + "'. "
                    + "This prevents accidental execution against development or production databases."
            );
        }

        String trimmedUrl = jdbcUrl.trim();

        // 3. JDBC prefix check
        if (!trimmedUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Invalid JDBC URL format. Must start with 'jdbc:postgresql://'. Provided: " + jdbcUrl
            );
        }

        // 4. Structured URI parsing
        String cleanUri = trimmedUrl.substring("jdbc:".length());
        URI uri;
        try {
            uri = URI.create(cleanUri);
        } catch (Exception e) {
            throw new IllegalStateException("PostgresTestSafetyGuard: Malformed PostgreSQL JDBC URI: " + jdbcUrl, e);
        }

        if (!"postgresql".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException("PostgresTestSafetyGuard: Unsupported scheme: " + uri.getScheme() + " in " + jdbcUrl);
        }

        // 5. Host validation
        String rawHost = uri.getHost();
        if (rawHost == null || rawHost.isBlank()) {
            throw new IllegalStateException("PostgresTestSafetyGuard: Host is missing in JDBC URL: " + jdbcUrl);
        }
        String host = rawHost.replaceAll("[\\[\\]]", "").toLowerCase(Locale.ROOT);
        if (!ALLOWED_HOSTS.contains(host)) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Host '" + rawHost + "' is not an authorized local or test service host. "
                    + "Remote/unverified hosts are blocked to prevent data corruption. Allowed hosts: " + ALLOWED_HOSTS
            );
        }

        // 6. Port validation
        int port = uri.getPort();
        int effectivePort = (port == -1) ? 5432 : port;
        if (effectivePort == DEV_PORT) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Dev port " + DEV_PORT + " is strictly prohibited! "
                    + "The local development database runs on port " + DEV_PORT + ". "
                    + "Please run tests against an isolated container on a different port."
            );
        }

        // 7. Database name validation
        String path = uri.getPath();
        if (path == null || path.length() <= 1) {
            throw new IllegalStateException("PostgresTestSafetyGuard: Database name is missing in JDBC URL: " + jdbcUrl);
        }
        String dbName = path.substring(1);
        if (dbName.contains("/")) {
            throw new IllegalStateException("PostgresTestSafetyGuard: Invalid database path: " + path);
        }
        String normalizedDb = dbName.toLowerCase(Locale.ROOT);

        // Check prohibited names
        if (PROHIBITED_DATABASES.contains(normalizedDb)) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Database name '" + dbName + "' is prohibited (known dev/prod database). "
                    + "Tests must use a dedicated test database."
            );
        }

        if (normalizedDb.contains("prod") || normalizedDb.contains("production")) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Database name '" + dbName + "' contains 'prod'/'production' and is prohibited."
            );
        }

        // Strict test naming policy: prefer _test suffix
        boolean isTestNamed = normalizedDb.endsWith("_test")
                || normalizedDb.endsWith("-test")
                || normalizedDb.startsWith("test_")
                || normalizedDb.startsWith("test-")
                || normalizedDb.equals("test");

        if (!isTestNamed) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Database name '" + dbName + "' does not satisfy the test naming policy. "
                    + "Test database names must end with '_test' (or start with 'test_'). Example: 'og_shop_test'."
            );
        }

        return jdbcUrl;
    }
}
