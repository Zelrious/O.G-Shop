package com.oldbutgold.shop.modules.fulfillment.infrastructure.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated 
@ConfigurationProperties (prefix = "app.ghn")
public record GhnProperties(
    @DefaultValue("https://dev-online-gateway.ghn.vn/shiip/public-api/") String baseUrl,
    @NotBlank String apiToken,
    @NotNull @Positive Integer shopId,
    @DefaultValue("PT5S") Duration connectTimeout,
    @DefaultValue("PT10S") Duration readTimeout,
    @DefaultValue("") String webhookSecret,
    @DefaultValue ReconciliationProperties reconciliation
) {
    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public GhnProperties(
            String baseUrl,
            String apiToken,
            Integer shopId,
            Duration connectTimeout,
            Duration readTimeout,
            String webhookSecret,
            ReconciliationProperties reconciliation
    ) {
        this.baseUrl = baseUrl;
        this.apiToken = apiToken;
        this.shopId = shopId;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.webhookSecret = webhookSecret != null ? webhookSecret : "";
        this.reconciliation = reconciliation != null ? reconciliation : new ReconciliationProperties(true, "0 */30 * * * *", 50);
    }

    public GhnProperties(String baseUrl, String apiToken, Integer shopId, Duration connectTimeout, Duration readTimeout) {
        this(baseUrl, apiToken, shopId, connectTimeout, readTimeout, "", new ReconciliationProperties(true, "0 */30 * * * *", 50));
    }

    public GhnProperties(String baseUrl, String apiToken, Integer shopId, Duration connectTimeout, Duration readTimeout, String webhookSecret) {
        this(baseUrl, apiToken, shopId, connectTimeout, readTimeout, webhookSecret, new ReconciliationProperties(true, "0 */30 * * * *", 50));
    }

    public record ReconciliationProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("0 */30 * * * *") String cron,
        @DefaultValue("50") int batchSize,
        @DefaultValue("60000") long untrackedDelayMs
    ) {
        public ReconciliationProperties(boolean enabled, String cron, int batchSize) {
            this(enabled, cron, batchSize, 60000L);
        }
    }
}
