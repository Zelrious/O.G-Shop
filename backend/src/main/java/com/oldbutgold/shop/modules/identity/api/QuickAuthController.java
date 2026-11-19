package com.oldbutgold.shop.modules.identity.api;

import com.oldbutgold.shop.modules.identity.api.EkycDtos.*;
import com.oldbutgold.shop.modules.identity.application.QuickAuthService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ekyc/quick-auth")
public class QuickAuthController {
    private final QuickAuthService service;

    public QuickAuthController(QuickAuthService service) {
        this.service = service;
    }

    @PostMapping("/init")
    public QuickAuthInitResponse init(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) QuickAuthInitRequest request
    ) {
        long userId = Long.parseLong(jwt.getSubject());
        String purpose = request != null ? request.purpose() : null;
        return service.init(userId, purpose);
    }

    @PostMapping("/verify")
    public QuickAuthVerifyResponse verify(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody QuickAuthVerifyRequest request
    ) {
        long userId = Long.parseLong(jwt.getSubject());
        return service.verify(userId, request.sessionId(), request.liveFrameBase64());
    }

    @GetMapping("/status")
    public QuickAuthStatusResponse getStatus(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(value = "purpose", required = false) String purpose
    ) {
        long userId = Long.parseLong(jwt.getSubject());
        return service.getStatus(userId, purpose);
    }
}
