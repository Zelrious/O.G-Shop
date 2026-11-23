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
    public static final String ENV_TEST_ENABLE_FLYWAY = "OGSHOP_TEST_ENABLE_FLYWAY";
    public static final String ENV_MODERATION_TEST_DB_URL = "OGSHOP_MODERATION_TEST_DB_URL";
    public static final String ENV_CATEGORY_TEST_DB_URL = "OGSHOP_CATEGORY_TEST_DB_URL";
    public static final String ENV_UPGRADE_TEST_DB_URL = "OGSHOP_UPGRADE_TEST_DB_URL";
    public static final String ENV_DEMO_SEED_DB_URL = "OGSHOP_DEMO_SEED_DB_URL";
    public static final String ENV_DEMO_SEED_DB_USER = "OGSHOP_DEMO_SEED_DB_USER";
    public static final String ENV_DEMO_SEED_DB_PASSWORD = "OGSHOP_DEMO_SEED_DB_PASSWORD";

    public static final int DEV_PORT = 5433;
    public static final int SHARED_PORT = 54432;
    public static final int LEGACY_DEMO_PORT = 55432;

    public static final String SHARED_TEST_DB = "og_shop_bd5_test";

    // Exact Approved Target Tuple for Demo Seed (Policy Gate Contract)
    public static final Set<String> APPROVED_DEMO_SEED_HOSTS = Set.of("localhost", "127.0.0.1");
    public static final int APPROVED_DEMO_SEED_PORT = 55332;
    public static final String APPROVED_DEMO_SEED_DB = "og_shop_demo_isolated_test";
    public static final String APPROVED_DEMO_SEED_USER = "og_demo_seed_runner";

    private static final Set<String> PROHIBITED_DATABASES = Set.of(
            "og_shop",
            "og_shop_dev",
            "og_shop_development",
            "og_shop_prod",
            "og_shop_production",
            "og_shop_main",
            "og_shop_bd5_test",
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
     * Determines whether Flyway migrations are allowed to run during test execution.
     * <p>
     * Hardening invariant: Flyway is DISABLED by default in test execution to prevent
     * accidental, automatic DDL migrations against the test database.
     * It requires explicit opt-in via {@code OGSHOP_TEST_ENABLE_FLYWAY=true}.
     *
     * @param envLookup function to look up environment variable by name
     * @return {@code true} ONLY if {@code OGSHOP_TEST_ENABLE_FLYWAY} is explicitly "true" (case-insensitive);
     *         {@code false} otherwise.
     */
    public static boolean isFlywayOptInEnabled(java.util.function.Function<String, String> envLookup) {
        if (envLookup == null) {
            return false;
        }
        String val = envLookup.apply(ENV_TEST_ENABLE_FLYWAY);
        return "true".equalsIgnoreCase(val != null ? val.trim() : null);
    }

    /**
     * Determines whether Flyway migrations are allowed to run during test execution
     * using the system environment.
     *
     * @return {@code true} ONLY if {@code OGSHOP_TEST_ENABLE_FLYWAY=true} in system environment;
     *         {@code false} otherwise.
     */
    public static boolean isFlywayOptInEnabled() {
        return isFlywayOptInEnabled(System::getenv);
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

    /**
     * Immutable credentials holder for Demo Seed execution.
     */
    public record DemoSeedCredentials(String username, String password) {
        @Override
        public String toString() {
            return "DemoSeedCredentials[username=" + username + ", password=***REDACTED***]";
        }
    }

    /**
     * Asserts that Demo Seed credentials are provided, non-blank, and strictly fail-closed.
     * Prevents any fallback or usage of privileged dev credentials (og_shop/root).
     *
     * @param username the configured username
     * @param password the configured password
     * @return validated DemoSeedCredentials
     * @throws IllegalStateException if username or password is missing, blank, or matches prohibited defaults
     */
    public static DemoSeedCredentials assertSafeDemoSeedCredentials(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Demo Seed aborted! Username is required and cannot be blank. "
                    + "Please configure " + ENV_DEMO_SEED_DB_USER + " with an isolated test role (fallback to 'og_shop' is strictly prohibited)."
            );
        }
        String trimmedUser = username.trim();
        if ("og_shop".equalsIgnoreCase(trimmedUser)) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Demo Seed aborted! Privileged dev user 'og_shop' is strictly prohibited for demo seed. "
                    + "Dedicated isolated test credentials must be used."
            );
        }
        if (!APPROVED_DEMO_SEED_USER.equalsIgnoreCase(trimmedUser)) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Demo Seed aborted! User '" + username + "' is not approved. "
                    + "Only dedicated isolated role '" + APPROVED_DEMO_SEED_USER + "' is approved by PO."
            );
        }

        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Demo Seed aborted! Password is required and cannot be blank. "
                    + "Please configure " + ENV_DEMO_SEED_DB_PASSWORD + " (fallback to default password is strictly prohibited)."
            );
        }
        String trimmedPass = password.trim();
        if ("root".equalsIgnoreCase(trimmedPass)) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Demo Seed aborted! Default dev password 'root' is strictly prohibited for demo seed."
            );
        }

        return new DemoSeedCredentials(trimmedUser, password);
    }

    /**
     * Resolves and asserts safety of Demo Seed credentials using an environment lookup function.
     *
     * @param envLookup function to query environment variables
     * @return validated DemoSeedCredentials
     */
    public static DemoSeedCredentials resolveAndAssertSafeDemoSeedCredentials(java.util.function.Function<String, String> envLookup) {
        if (envLookup == null) {
            throw new IllegalStateException("PostgresTestSafetyGuard: Environment lookup function is required.");
        }
        String user = envLookup.apply(ENV_DEMO_SEED_DB_USER);
        if (user == null || user.isBlank()) {
            user = envLookup.apply("DEMO_SEED_DB_USER");
        }
        String pass = envLookup.apply(ENV_DEMO_SEED_DB_PASSWORD);
        if (pass == null || pass.isBlank()) {
            pass = envLookup.apply("DEMO_SEED_DB_PASSWORD");
        }
        return assertSafeDemoSeedCredentials(user, pass);
    }

    /**
     * Resolves and asserts safety of Demo Seed credentials from system environment.
     *
     * @return validated DemoSeedCredentials
     */
    public static DemoSeedCredentials resolveAndAssertSafeDemoSeedCredentialsFromEnv() {
        return resolveAndAssertSafeDemoSeedCredentials(System::getenv);
    }

    /**
     * Asserts that the provided JDBC URL strictly matches the approved Demo Seed target tuple:
     * (host: localhost/127.0.0.1, port: 55332, database: og_shop_demo_isolated_test).
     * <p>
     * Strict criteria (Policy Gate):
     * 1. URL must be present, non-blank, and start with 'jdbc:postgresql://'.
     * 2. Isolation confirmation (OGSHOP_TEST_DB_ISOLATED=true) is mandatory.
     * 3. Host must strictly match approved local loopback hosts (APPROVED_DEMO_SEED_HOSTS: localhost, 127.0.0.1).
     * 4. Port must strictly match APPROVED_DEMO_SEED_PORT (55332); prohibited ports (5433, 54432, 55432) and any other port are rejected.
     * 5. Database name must strictly match APPROVED_DEMO_SEED_DB (og_shop_demo_isolated_test); prohibited databases, generic test databases, and custom allowlists are rejected.
     *
     * @param jdbcUrl          the PostgreSQL JDBC URL to validate
     * @param isolatedEnvValue value of OGSHOP_TEST_DB_ISOLATED
     * @return the validated JDBC URL
     * @throws IllegalStateException if any validation check fails
     */
    public static String assertSafeDemoSeedDatabase(
            String jdbcUrl,
            String isolatedEnvValue
    ) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalStateException("PostgresTestSafetyGuard: Demo Seed JDBC URL is required and cannot be empty.");
        }

        if (isolatedEnvValue == null || !isolatedEnvValue.trim().equalsIgnoreCase("true")) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Demo Seed aborted! Environment variable " + ENV_TEST_DB_ISOLATED + "=true is required "
                    + "to confirm execution against an isolated database. Current value: '" + isolatedEnvValue + "'."
            );
        }

        String trimmedUrl = jdbcUrl.trim();
        if (!trimmedUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Invalid JDBC URL format for Demo Seed. Must start with 'jdbc:postgresql://'. Provided: " + jdbcUrl
            );
        }

        String cleanUri = trimmedUrl.substring("jdbc:".length());
        URI uri;
        try {
            uri = URI.create(cleanUri);
        } catch (Exception e) {
            throw new IllegalStateException("PostgresTestSafetyGuard: Malformed PostgreSQL JDBC URI for Demo Seed: " + jdbcUrl, e);
        }

        if (!"postgresql".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException("PostgresTestSafetyGuard: Unsupported scheme for Demo Seed: " + uri.getScheme() + " in " + jdbcUrl);
        }

        String rawHost = uri.getHost();
        if (rawHost == null || rawHost.isBlank()) {
            throw new IllegalStateException("PostgresTestSafetyGuard: Host is missing in JDBC URL for Demo Seed: " + jdbcUrl);
        }
        String host = rawHost.replaceAll("[\\[\\]]", "").toLowerCase(Locale.ROOT);
        if (!APPROVED_DEMO_SEED_HOSTS.contains(host)) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Host '" + rawHost + "' is not approved for Demo Seed. "
                    + "Only approved local loopback hosts are accepted: " + APPROVED_DEMO_SEED_HOSTS
            );
        }

        int port = uri.getPort();
        int effectivePort = (port == -1) ? 5432 : port;
        if (effectivePort == DEV_PORT) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Dev port " + DEV_PORT + " is strictly prohibited for demo seed!"
            );
        }
        if (effectivePort == SHARED_PORT) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Shared DB port " + SHARED_PORT + " is strictly prohibited for demo seed!"
            );
        }
        if (effectivePort == LEGACY_DEMO_PORT) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Legacy/dev port " + LEGACY_DEMO_PORT + " is strictly prohibited for demo seed! "
                    + "Please run demo seed against a dedicated isolated container."
            );
        }
        if (effectivePort != APPROVED_DEMO_SEED_PORT) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Port " + effectivePort + " is not the approved Demo Seed port! "
                    + "Demo Seed policy requires exact approved port " + APPROVED_DEMO_SEED_PORT + "."
            );
        }

        String path = uri.getPath();
        if (path == null || path.length() <= 1) {
            throw new IllegalStateException("PostgresTestSafetyGuard: Database name is missing in JDBC URL for Demo Seed: " + jdbcUrl);
        }
        String dbName = path.substring(1);
        if (dbName.contains("/")) {
            throw new IllegalStateException("PostgresTestSafetyGuard: Invalid database path for Demo Seed: " + path);
        }
        String normalizedDb = dbName.toLowerCase(Locale.ROOT);

        if (PROHIBITED_DATABASES.contains(normalizedDb) || normalizedDb.equals(SHARED_TEST_DB)) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Database name '" + dbName + "' is prohibited (known dev/shared/prod database). "
                    + "Demo seed must target a dedicated isolated database."
            );
        }

        if (normalizedDb.contains("prod") || normalizedDb.contains("production")) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Database name '" + dbName + "' contains 'prod'/'production' and is prohibited."
            );
        }

        boolean isTestNamed = normalizedDb.endsWith("_test")
                || normalizedDb.endsWith("-test")
                || normalizedDb.startsWith("test_")
                || normalizedDb.startsWith("test-")
                || normalizedDb.equals("test");

        if (!isTestNamed) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Database name '" + dbName + "' does not satisfy the test naming policy. "
                    + "Demo seed database names must end with '_test' (or start with 'test_')."
            );
        }

        // Exact approved tuple enforcement: database name must match APPROVED_DEMO_SEED_DB
        if (!normalizedDb.equals(APPROVED_DEMO_SEED_DB)) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Database name '" + dbName + "' is not the approved Demo Seed target! "
                    + "Only the exact approved database '" + APPROVED_DEMO_SEED_DB + "' is accepted. "
                    + "Generic test databases and custom allowlists are strictly rejected."
            );
        }

        return jdbcUrl;
    }

    /**
     * Deprecated compatibility overload. Custom allowlists are completely ignored per PO policy.
     */
    @Deprecated
    public static String assertSafeDemoSeedDatabase(
            String jdbcUrl,
            String isolatedEnvValue,
            Set<String> customAllowlist
    ) {
        return assertSafeDemoSeedDatabase(jdbcUrl, isolatedEnvValue);
    }

    /**
     * Resolves and asserts safety of Demo Seed target database using environment lookup.
     *
     * @param envLookup        lookup function for environment variables
     * @param isolatedEnvValue value of OGSHOP_TEST_DB_ISOLATED
     * @return validated safe JDBC URL
     */
    public static String resolveAndAssertSafeDemoSeed(
            java.util.function.Function<String, String> envLookup,
            String isolatedEnvValue
    ) {
        if (envLookup == null) {
            throw new IllegalStateException("PostgresTestSafetyGuard: Environment lookup function is required.");
        }
        String url = envLookup.apply(ENV_DEMO_SEED_DB_URL);
        if (url == null || url.isBlank()) {
            url = envLookup.apply("DEMO_SEED_DB_URL");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "PostgresTestSafetyGuard: Demo Seed aborted! Target database URL is missing. "
                    + "Please configure " + ENV_DEMO_SEED_DB_URL + " (or DEMO_SEED_DB_URL) with the approved isolated database."
            );
        }

        return assertSafeDemoSeedDatabase(url, isolatedEnvValue);
    }

    /**
     * Resolves and asserts safety of Demo Seed target database using System environment.
     *
     * @return validated safe JDBC URL
     */
    public static String resolveAndAssertSafeDemoSeedFromEnv() {
        return resolveAndAssertSafeDemoSeed(System::getenv, System.getenv(ENV_TEST_DB_ISOLATED));
    }
}
