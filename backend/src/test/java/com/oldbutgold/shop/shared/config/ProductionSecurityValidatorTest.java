package com.oldbutgold.shop.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecurityValidatorTest {

    @Test
    @DisplayName("1. PROD + missing JWT_SIGNING_KEY -> Fails Fast")
    void prodEnvironment_missingJwtKey_failsFast() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("app.environment", "PROD");
        env.setProperty("spring.datasource.password", "strong-production-db-password-1234");
        // No jwt key set

        assertThatThrownBy(() -> ProductionSecurityValidator.validateSecurityConfiguration(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PRODUCTION SECURITY VIOLATION: app.auth.jwt-signing-key (JWT_SIGNING_KEY) must be explicitly configured");
    }

    @Test
    @DisplayName("2. PROD + placeholder JWT_SIGNING_KEY -> Fails Fast")
    void prodEnvironment_placeholderJwtKey_failsFast() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("app.environment", "PROD");
        env.setProperty("spring.datasource.password", "strong-production-db-password-1234");
        env.setProperty("app.auth.jwt-signing-key", ProductionSecurityValidator.DEFAULT_PLACEHOLDER_JWT_KEY);

        assertThatThrownBy(() -> ProductionSecurityValidator.validateSecurityConfiguration(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.auth.jwt-signing-key cannot use known placeholder");
    }

    @Test
    @DisplayName("3. PROD + missing DB_PASSWORD -> Fails Fast")
    void prodEnvironment_missingDbPassword_failsFast() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("app.environment", "PROD");
        env.setProperty("app.auth.jwt-signing-key", "my-super-secret-production-jwt-key-256-bits-long!");
        // No db password set

        assertThatThrownBy(() -> ProductionSecurityValidator.validateSecurityConfiguration(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.password (DB_PASSWORD) must be explicitly set and cannot be blank");
    }

    @Test
    @DisplayName("4. PROD + weak fallback DB_PASSWORD ('root') -> Fails Fast")
    void prodEnvironment_rootDbPassword_failsFast() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("app.environment", "PROD");
        env.setProperty("app.auth.jwt-signing-key", "my-super-secret-production-jwt-key-256-bits-long!");
        env.setProperty("spring.datasource.password", "root");

        assertThatThrownBy(() -> ProductionSecurityValidator.validateSecurityConfiguration(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot use weak default fallback 'root'");
    }

    @Test
    @DisplayName("5. PROD + valid JWT key and strong DB_PASSWORD -> Validation Succeeds")
    void prodEnvironment_validCredentials_succeeds() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("app.environment", "PROD");
        env.setProperty("app.auth.jwt-signing-key", "my-super-secret-production-jwt-key-256-bits-long!");
        env.setProperty("spring.datasource.password", "secure-strong-db-password-for-production-use-only!");

        assertThatCode(() -> ProductionSecurityValidator.validateSecurityConfiguration(env))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("6. DEV / LOCAL environment -> Relaxes production credential constraints")
    void nonProdEnvironment_relaxesConstraints() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("app.environment", "DEV");
        env.setProperty("app.auth.jwt-signing-key", ProductionSecurityValidator.DEFAULT_TEST_JWT_KEY);
        env.setProperty("spring.datasource.password", "root");

        assertThatCode(() -> ProductionSecurityValidator.validateSecurityConfiguration(env))
                .doesNotThrowAnyException();
    }
}

