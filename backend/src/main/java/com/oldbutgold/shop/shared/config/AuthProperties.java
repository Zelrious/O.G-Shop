package com.oldbutgold.shop.shared.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        @NotBlank String jwtSigningKey,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @NotBlank String frontendOrigin,
        @NotBlank String refreshCookieName,
        boolean refreshCookieSecure,
        @NotBlank String refreshCookiePath
) {
    public AuthProperties {
        if (jwtSigningKey != null && jwtSigningKey.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT signing key must contain at least 32 bytes");
        }
    }
}
