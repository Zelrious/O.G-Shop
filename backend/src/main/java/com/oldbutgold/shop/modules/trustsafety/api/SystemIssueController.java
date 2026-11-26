package com.oldbutgold.shop.modules.trustsafety.api;

import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseDtos;
import com.oldbutgold.shop.modules.trustsafety.application.SystemIssueGovernanceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Authenticated user endpoints for UC64 SYSTEM_ISSUE creation & tracking.
 */
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
@RestController
@RequestMapping("/api/v1/trustsafety/system-issues")
public class SystemIssueController {

    private final SystemIssueGovernanceService systemIssueGovernanceService;

    public SystemIssueController(SystemIssueGovernanceService systemIssueGovernanceService) {
        this.systemIssueGovernanceService = systemIssueGovernanceService;
    }

    @PostMapping
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> createSystemIssue(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReturnCaseDtos.CreateSystemIssueRequest request
    ) {
        long userId = extractUserId(jwt);
        return ResponseEntity.ok(systemIssueGovernanceService.createSystemIssue(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<ReturnCaseDtos.ReturnCaseDetailResponse>> listMySystemIssues(
            @AuthenticationPrincipal Jwt jwt
    ) {
        long userId = extractUserId(jwt);
        return ResponseEntity.ok(systemIssueGovernanceService.listSystemIssuesForUser(userId));
    }

    public record Supplement(java.util.UUID requestId,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=2000) String note) {}

    @PostMapping("/{caseId}/supplements")
    public ReturnCaseDtos.ReturnCaseDetailResponse supplement(@AuthenticationPrincipal Jwt jwt,
            @PathVariable long caseId, @Valid @RequestBody Supplement body) {
        return systemIssueGovernanceService.supplement(extractUserId(jwt), caseId, body.requestId(), body.note().trim());
    }

    private static long extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AccessDeniedException("Yêu cầu đăng nhập.");
        }
        return Long.parseLong(jwt.getSubject());
    }
}

