package com.oldbutgold.shop.modules.trustsafety.api;

import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseDtos;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseService;
import com.oldbutgold.shop.modules.trustsafety.application.TrustSafetyBusinessException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller dành riêng cho Quản trị viên (ADMIN) giám sát và xử lý tranh chấp khiếu nại (Task 5.3).
 */
@RestController
@RequestMapping("/api/v1/admin/cases")
@Validated
public class AdminDisputeController {

    private final ReturnCaseService returnCaseService;

    public AdminDisputeController(ReturnCaseService returnCaseService) {
        this.returnCaseService = returnCaseService;
    }

    private static long extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AccessDeniedException("Yêu cầu đăng nhập.");
        }
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new AccessDeniedException("Phiên đăng nhập không hợp lệ.");
        }
    }

    /**
     * Endpoint A — Lấy danh sách các Return Case bị leo thang do quá hạn SLA 48h của KTV.
     * GET /api/v1/admin/cases/escalated
     */
    @GetMapping("/escalated")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReturnCaseDtos.AdminEscalatedCasePageResponse> getEscalatedCases(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "ktvId", required = false) Long ktvId
    ) {
        if (page < 0) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Chỉ số trang (page) phải >= 0");
        }
        if (size < 1 || size > 100) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_SIZE", "Kích thước trang (size) phải từ 1 đến 100");
        }
        ReturnCaseDtos.AdminEscalatedCasePageResponse response = returnCaseService.getEscalatedCases(ktvId, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint B — Admin đưa ra quyết định cuối cùng cho Case Vòng 2 đã leo thang SLA (BD-1 APPROVED).
     * POST /api/v1/admin/cases/{caseId}/decide-round2
     */
    @PostMapping("/{caseId}/decide-round2")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> decideRound2(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long caseId,
            @Valid @RequestBody ReturnCaseDtos.KtvRound2DecisionRequest request
    ) {
        long adminId = extractUserId(jwt);
        ReturnCaseDtos.ReturnCaseDetailResponse response = returnCaseService.adminDecideRound2(adminId, caseId, request);
        return ResponseEntity.ok(response);
    }
}

