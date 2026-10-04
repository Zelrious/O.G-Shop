package com.oldbutgold.shop.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.seller-verification")
public record SellerVerificationProperties(String mode) {
    public SellerVerificationProperties {
        if (mode == null || mode.isBlank()) {
            mode = "MVP_BYPASS";
        }
    }

    public boolean isMvpBypass() {
        return "MVP_BYPASS".equalsIgnoreCase(mode);
    }
}
