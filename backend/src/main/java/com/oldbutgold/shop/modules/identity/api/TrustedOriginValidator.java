package com.oldbutgold.shop.modules.identity.api;

import com.oldbutgold.shop.shared.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class TrustedOriginValidator {
    private final AuthProperties properties;

    public TrustedOriginValidator(AuthProperties properties) {
        this.properties = properties;
    }

    public void validate(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (!properties.frontendOrigin().equals(origin)) {
            throw new AccessDeniedException("Untrusted request origin");
        }
    }
}
