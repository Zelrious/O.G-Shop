package com.oldbutgold.shop.modules.identity.api;

import com.oldbutgold.shop.modules.identity.application.SellerActivationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seller-verification")
public class SellerVerificationController {
    private final SellerActivationService activationService;

    public SellerVerificationController(SellerActivationService activationService) {
        this.activationService = activationService;
    }

    @PostMapping("/activate")
    public SellerActivationResponse activate(@AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        SellerActivationService.ActivationResult result = activationService.activate(userId);
        return new SellerActivationResponse(
                result.verificationId(),
                result.status(),
                result.verificationMethod()
        );
    }

    public record SellerActivationResponse(
            long verificationId,
            String status,
            String verificationMethod
    ) {
    }
}
