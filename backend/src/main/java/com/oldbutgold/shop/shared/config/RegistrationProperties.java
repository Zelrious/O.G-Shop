package com.oldbutgold.shop.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.registration")
public record RegistrationProperties(Duration otpTtl, Duration draftTtl, Duration resendCooldown, int maxAttempts,
                                     int emailHourlyLimit, int ipHourlyLimit, boolean mailEnabled, String from) {
    public RegistrationProperties {
        if (otpTtl == null || otpTtl.compareTo(Duration.ofMinutes(1)) < 0
                || otpTtl.compareTo(Duration.ofMinutes(15)) > 0
                || draftTtl == null || draftTtl.compareTo(otpTtl) <= 0 || draftTtl.compareTo(Duration.ofHours(1)) > 0
                || resendCooldown == null || resendCooldown.compareTo(Duration.ofSeconds(30)) < 0
                || resendCooldown.compareTo(otpTtl) >= 0 || maxAttempts < 1 || maxAttempts > 15
                || emailHourlyLimit < 1 || ipHourlyLimit < emailHourlyLimit
                || from == null || from.isBlank() || from.contains("\r") || from.contains("\n")) {
            throw new IllegalArgumentException("Invalid registration OTP configuration");
        }
    }
}
