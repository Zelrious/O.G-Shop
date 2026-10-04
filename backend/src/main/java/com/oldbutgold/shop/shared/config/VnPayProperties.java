package com.oldbutgold.shop.shared.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated 
@ConfigurationProperties(prefix = "app.vnpay")
public record VnPayProperties(
    @NotBlank String payUrl,
    @NotBlank String tmnCode,
    @NotBlank String hashSecret,
    @NotBlank String ipnUrl,
    @NotBlank String returnUrl
){}