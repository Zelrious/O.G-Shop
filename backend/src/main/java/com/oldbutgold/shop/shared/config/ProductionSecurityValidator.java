package com.oldbutgold.shop.shared.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Production Security Hardening Validator.
 * Enforces fail-fast startup checks when running in PROD/PRODUCTION environment:
 * 1. Rejects missing, blank, or placeholder JWT signing keys.
 * 2. Rejects missing, blank, or weak fallback database passwords (such as 'root').
 * In non-production environments (DEV, LOCAL, TEST), relaxed developer defaults are permitted.
 */
@Component
public class ProductionSecurityValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ProductionSecurityValidator.class);

    public static final String DEFAULT_PLACEHOLDER_JWT_KEY = "replace-with-at-least-32-random-bytes";
    public static final String DEFAULT_TEST_JWT_KEY = "test-signing-key-with-at-least-thirty-two-bytes";

    private static final Set<String> FORBIDDEN_PROD_JWT_KEYS = Set.of(
            DEFAULT_PLACEHOLDER_JWT_KEY,
            DEFAULT_TEST_JWT_KEY,
            "12345678901234567890123456789012",
            "abcdefghijklmnopqrstuvwxyz123456"
    );

    private static final Set<String> WEAK_PROD_DB_PASSWORDS = Set.of(
            "root",
            "password",
            "admin",
            "123456",
            "postgres",
            "change-me-for-local-only"
    );

    private final Environment environment;

    public ProductionSecurityValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        validateSecurityConfiguration(this.environment);
    }

    public static void validateSecurityConfiguration(Environment env) {
        String appEnv = env.getProperty("app.environment", "PROD").trim().toUpperCase();
        boolean isProduction = "PROD".equals(appEnv) || "PRODUCTION".equals(appEnv);

        if (!isProduction) {
            log.info("Non-production environment ({}) detected. Production credential restrictions relaxed.", appEnv);
            return;
        }

        // 1. JWT Signing Key Validation for Production
        String jwtKey = env.getProperty("app.auth.jwt-signing-key");
        if (jwtKey == null || jwtKey.isBlank()) {
            throw new IllegalStateException("PRODUCTION SECURITY VIOLATION: app.auth.jwt-signing-key (JWT_SIGNING_KEY) must be explicitly configured in PROD environment.");
        }
        String trimmedJwtKey = jwtKey.trim();
        if (FORBIDDEN_PROD_JWT_KEYS.contains(trimmedJwtKey) || trimmedJwtKey.contains("replace-with") || trimmedJwtKey.contains("placeholder")) {
            throw new IllegalStateException("PRODUCTION SECURITY VIOLATION: app.auth.jwt-signing-key cannot use known placeholder or default test key in PROD environment.");
        }
        if (trimmedJwtKey.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("PRODUCTION SECURITY VIOLATION: app.auth.jwt-signing-key must contain at least 32 bytes (256 bits).");
        }

        // 2. Database Password Validation for Production
        String dbPassword = env.getProperty("spring.datasource.password");
        if (dbPassword == null || dbPassword.isBlank()) {
            throw new IllegalStateException("PRODUCTION SECURITY VIOLATION: spring.datasource.password (DB_PASSWORD) must be explicitly set and cannot be blank in PROD environment.");
        }
        String trimmedDbPassword = dbPassword.trim();
        if (WEAK_PROD_DB_PASSWORDS.contains(trimmedDbPassword.toLowerCase())) {
            throw new IllegalStateException("PRODUCTION SECURITY VIOLATION: spring.datasource.password cannot use weak default fallback '" + trimmedDbPassword + "' in PROD environment.");
        }

        log.info("Production security configuration validated successfully for environment {}.", appEnv);
    }
}

