package com.oldbutgold.shop.shared.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("app.google")
public record GoogleAuthProperties(String clientId) {
    public GoogleAuthProperties { clientId=clientId==null?"":clientId.strip(); }
    public boolean enabled() { return !clientId.isBlank(); }
}
