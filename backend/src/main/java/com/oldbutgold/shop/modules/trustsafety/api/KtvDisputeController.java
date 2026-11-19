package com.oldbutgold.shop.modules.trustsafety.api;

import com.oldbutgold.shop.modules.trustsafety.api.DisputeDtos.*;
import com.oldbutgold.shop.modules.trustsafety.application.KtvDisputeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ktv/disputes")
public class KtvDisputeController {

    private final KtvDisputeService ktvDisputeService;

    public KtvDisputeController(KtvDisputeService ktvDisputeService) {
        this.ktvDisputeService = ktvDisputeService;
    }

    @GetMapping
    public ResponseEntity<DisputePageResponse> getDisputes(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ktvDisputeService.searchDisputes(status, query, page, size));
    }

    @GetMapping("/{disputeId}")
    public ResponseEntity<DisputeDetailDto> getDisputeDetail(@PathVariable Long disputeId) {
        return ResponseEntity.ok(ktvDisputeService.getDisputeDetail(disputeId));
    }

    @PostMapping("/{disputeId}/decision")
    public ResponseEntity<DisputeDecisionResponse> submitDecision(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long disputeId,
            @Valid @RequestBody DisputeDecisionRequest request
    ) {
        long ktvId = extractUserId(jwt);
        return ResponseEntity.ok(ktvDisputeService.submitDecision(disputeId, ktvId, request));
    }

    private static long extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AccessDeniedException("Yêu cầu quyền KTV đăng nhập.");
        }
        return Long.parseLong(jwt.getSubject());
    }
}
