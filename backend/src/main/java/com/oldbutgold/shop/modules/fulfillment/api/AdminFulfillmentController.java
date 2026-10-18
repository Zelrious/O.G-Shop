package com.oldbutgold.shop.modules.fulfillment.api;

import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.ResolveIncidentRequest;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.ResolveIncidentResponse;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.ShipmentIncidentResponse;
import com.oldbutgold.shop.modules.fulfillment.application.FulfillmentStateService;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentEntity;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/admin/fulfillment")
public class AdminFulfillmentController {

    private final FulfillmentStateService fulfillmentStateService;
    private final ShipmentRepository shipmentRepository;

    public AdminFulfillmentController(
            FulfillmentStateService fulfillmentStateService,
            ShipmentRepository shipmentRepository
    ) {
        this.fulfillmentStateService = fulfillmentStateService;
        this.shipmentRepository = shipmentRepository;
    }

    /**
     * Lấy danh sách hàng đợi các vận đơn gặp sự cố cần Admin can thiệp duyệt thủ công (ADMIN-01).
     */
    @GetMapping("/incidents")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ShipmentIncidentResponse>> getIncidents() {
        return ResponseEntity.ok(fulfillmentStateService.getIncidentQueue());
    }

    /**
     * Admin giải quyết sự cố vận đơn theo orderId.
     */
    @PostMapping("/orders/{orderId}/resolve-incident")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResolveIncidentResponse> resolveIncidentByOrderId(
            @PathVariable Long orderId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ResolveIncidentRequest request
    ) {
        String adminIdentifier = extractAdminIdentifier(jwt);
        return ResponseEntity.ok(fulfillmentStateService.resolveIncident(
                orderId,
                request.action(),
                request.notes(),
                adminIdentifier
        ));
    }

    /**
     * Admin giải quyết sự cố vận đơn theo shipmentId.
     */
    @PostMapping("/incidents/{shipmentId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResolveIncidentResponse> resolveIncidentByShipmentId(
            @PathVariable Long shipmentId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ResolveIncidentRequest request
    ) {
        ShipmentEntity shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy vận đơn #" + shipmentId));
        String adminIdentifier = extractAdminIdentifier(jwt);
        return ResponseEntity.ok(fulfillmentStateService.resolveIncident(
                shipment.getOrderId(),
                request.action(),
                request.notes(),
                adminIdentifier
        ));
    }

    private String extractAdminIdentifier(Jwt jwt) {
        if (jwt == null) {
            return "SYSTEM_ADMIN";
        }
        if (jwt.getClaimAsString("email") != null && !jwt.getClaimAsString("email").isBlank()) {
            return jwt.getClaimAsString("email");
        }
        return jwt.getSubject() != null ? jwt.getSubject() : "ADMIN";
    }
}
