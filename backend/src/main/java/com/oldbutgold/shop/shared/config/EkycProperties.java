package com.oldbutgold.shop.shared.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.ekyc")
public record EkycProperties(
        @NotBlank String baseUrl,
        @NotBlank String internalToken,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {
}
