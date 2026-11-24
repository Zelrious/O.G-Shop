package com.oldbutgold.shop.modules.trustsafety.api;

import com.oldbutgold.shop.modules.trustsafety.api.DisputeDtos.*;
import com.oldbutgold.shop.modules.trustsafety.application.BuyerDisputeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/commerce/orders")
public class OrderDisputeController {

    private final BuyerDisputeService buyerDisputeService;

    public OrderDisputeController(BuyerDisputeService buyerDisputeService) {
        this.buyerDisputeService = buyerDisputeService;
    }

    @PostMapping("/{orderId}/dispute")
    public ResponseEntity<DisputeDetailDto> createDispute(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId,
            @Valid @RequestBody CreateDisputeRequest request
    ) {
        long buyerId = extractUserId(jwt);
        CreateDisputeRequest normalized = new CreateDisputeRequest(
                orderId,
                request.reason(),
                request.description(),
                request.buyerEvidenceUrls(),
                request.videoUnboxingUrl()
        );
        return ResponseEntity.ok(buyerDisputeService.createDispute(buyerId, normalized));
    }

    @PostMapping({"/{orderId}/seller-response", "/{orderId}/dispute/seller-response"})
    public ResponseEntity<DisputeDetailDto> submitSellerResponse(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId,
            @Valid @RequestBody SellerDisputeResponseRequest request
    ) {
        long sellerId = extractUserId(jwt);
        return ResponseEntity.ok(buyerDisputeService.submitSellerResponse(sellerId, orderId, request));
    }

    @PostMapping("/{orderId}/dispute/return-shipment")
    public ResponseEntity<DisputeDetailDto> submitReturnShipment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId,
            @RequestBody(required = false) ReturnShipmentRequest request
    ) {
        long buyerId = extractUserId(jwt);
        return ResponseEntity.ok(buyerDisputeService.submitReturnShipment(buyerId, orderId, request));
    }

    @PostMapping("/{orderId}/dispute/confirm-return-received")
    public ResponseEntity<DisputeDetailDto> confirmReturnReceived(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId
    ) {
        long actorId = extractUserId(jwt);
        return ResponseEntity.ok(buyerDisputeService.confirmReturnReceivedAndRefund(actorId, orderId));
    }

    @GetMapping("/{orderId}/dispute")
    public ResponseEntity<DisputeDetailDto> getDisputeForOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId
    ) {
        long userId = extractUserId(jwt);
        return ResponseEntity.ok(buyerDisputeService.getDisputeForOrder(userId, orderId));
    }

    private static long extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AccessDeniedException("Yêu cầu đăng nhập.");
        }
        return Long.parseLong(jwt.getSubject());
    }
}
