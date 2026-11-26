package com.oldbutgold.shop.modules.trustsafety.api;

import com.oldbutgold.shop.modules.commerce.application.FulfillmentSimulationGuard;
import com.oldbutgold.shop.modules.trustsafety.api.DisputeDtos.*;
import com.oldbutgold.shop.modules.trustsafety.application.BuyerDisputeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/commerce/orders")
public class OrderDisputeController {

    private final BuyerDisputeService buyerDisputeService;
    private final FulfillmentSimulationGuard simulationGuard;

    public OrderDisputeController(BuyerDisputeService buyerDisputeService) {
        this(buyerDisputeService, null);
    }

    @Autowired
    public OrderDisputeController(
            BuyerDisputeService buyerDisputeService,
            @Autowired(required = false) FulfillmentSimulationGuard simulationGuard
    ) {
        this.buyerDisputeService = buyerDisputeService;
        this.simulationGuard = simulationGuard;
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

    @PostMapping("/{orderId}/dispute/return-handover")
    public ResponseEntity<DisputeDetailDto> confirmReturnHandover(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId
    ) {
        long buyerId = extractUserId(jwt);
        return ResponseEntity.ok(buyerDisputeService.confirmReturnHandover(buyerId, orderId));
    }

    /**
     * DEV/TEST-ONLY ADMIN simulation endpoint to mark return shipment as RETURN_DELIVERED.
     * Strictly fails closed when simulationGuard is missing, simulation is disabled, or environment is PROD.
     * Does NOT complete refund settlement or mark the order REFUNDED.
     */
    @PostMapping("/{orderId}/dispute/return-delivered-simulate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DisputeDetailDto> simulateReturnDelivered(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        if (simulationGuard == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN_SIMULATION_IN_PRODUCTION");
        }
        simulationGuard.ensureSimulationAllowed();
        long actorId = extractUserId(jwt);
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return ResponseEntity.ok(buyerDisputeService.confirmReturnDelivered(actorId, orderId, isAdmin));
    }

    @PostMapping("/{orderId}/dispute/confirm-return-received")
    public ResponseEntity<DisputeDetailDto> confirmReturnReceived(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId
    ) {
        long sellerId = extractUserId(jwt);
        return ResponseEntity.ok(buyerDisputeService.confirmReturnReceivedAndRefund(sellerId, orderId));
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
