package com.oldbutgold.shop.modules.identity.api;

import com.oldbutgold.shop.modules.identity.api.SellerSettingsDtos.*;
import com.oldbutgold.shop.modules.identity.application.SellerSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/seller/settings")
public class SellerSettingsController {
    private final SellerSettingsService service;

    public SellerSettingsController(SellerSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<SellerSettingsResponse> getSettings(@AuthenticationPrincipal Jwt jwt) {
        long userId = extractUserId(jwt);
        return ResponseEntity.ok(service.getSettings(userId));
    }

    @PutMapping
    public ResponseEntity<SellerSettingsResponse> updateSettings(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateSellerSettingsRequest request
    ) {
        long userId = extractUserId(jwt);
        return ResponseEntity.ok(service.updateSettings(userId, request));
    }

    private static long extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AccessDeniedException("Yêu cầu đăng nhập.");
        }
        return Long.parseLong(jwt.getSubject());
    }
}
