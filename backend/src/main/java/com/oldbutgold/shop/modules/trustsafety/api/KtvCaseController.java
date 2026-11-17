package com.oldbutgold.shop.modules.trustsafety.api;

import com.oldbutgold.shop.modules.trustsafety.application.CaseEvidenceRequestService;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseDtos;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseService;
import com.oldbutgold.shop.modules.trustsafety.application.TrustSafetyBusinessException;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEventEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEventRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRoundEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRoundRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/ktv")
@Validated
public class KtvCaseController {

    private final ReturnCaseService returnCaseService;
    private final CaseEvidenceRequestService caseEvidenceRequestService;
    private final CaseRepository caseRepository;
    private final CaseRoundRepository caseRoundRepository;
    private final CaseEventRepository caseEventRepository;

    public KtvCaseController(
            ReturnCaseService returnCaseService,
            CaseEvidenceRequestService caseEvidenceRequestService,
            CaseRepository caseRepository,
            CaseRoundRepository caseRoundRepository,
            CaseEventRepository caseEventRepository
    ) {
        this.returnCaseService = returnCaseService;
        this.caseEvidenceRequestService = caseEvidenceRequestService;
        this.caseRepository = caseRepository;
        this.caseRoundRepository = caseRoundRepository;
        this.caseEventRepository = caseEventRepository;
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

    private CaseEntity findCaseOrThrow(Long caseId) {
        if (caseId == null) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "caseId không hợp lệ");
        }
        return caseRepository.findById(caseId)
                .orElseThrow(() -> new TrustSafetyBusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy hồ sơ khiếu nại #" + caseId));
    }

    /**
     * Endpoint 1 — Work Queue
     * GET /api/v1/ktv/work-queue?scope=AVAILABLE|MY_CASES&round=1|2
     */
    @GetMapping("/work-queue")
    public ResponseEntity<List<ReturnCaseDtos.ReturnCaseDetailResponse>> getWorkQueue(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "round", required = false) Integer round
    ) {
        long ktvId = extractUserId(jwt);
        if (scope == null || (!scope.equals("AVAILABLE") && !scope.equals("MY_CASES"))) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
                    "scope bắt buộc là AVAILABLE hoặc MY_CASES");
        }
        if (round == null || (round != 1 && round != 2)) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
                    "round bắt buộc là 1 hoặc 2");
        }

        List<ReturnCaseDtos.ReturnCaseDetailResponse> queue = returnCaseService.getKtvWorkQueue(ktvId, scope, round);
        return ResponseEntity.ok(queue);
    }

    /**
     * Endpoint 2 — Claim Case
     * POST /api/v1/ktv/cases/{caseId}/assign
     */
    @PostMapping("/cases/{caseId}/assign")
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> assignCase(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long caseId
    ) {
        long ktvId = extractUserId(jwt);
        findCaseOrThrow(caseId);
        ReturnCaseDtos.ReturnCaseDetailResponse response = returnCaseService.assignCaseToKtv(ktvId, caseId);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint 3 — Decide Round 1
     * POST /api/v1/ktv/cases/{caseId}/decide-round1
     */
    @PostMapping("/cases/{caseId}/decide-round1")
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> decideRound1(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long caseId,
            @Valid @RequestBody ReturnCaseDtos.KtvRound1DecisionRequest request
    ) {
        long ktvId = extractUserId(jwt);
        CaseEntity c = findCaseOrThrow(caseId);

        // Bắt buộc claim trước khi quyết định
        if (c.getAssignedTo() == null || !c.getAssignedTo().equals(ktvId)) {
            throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "NOT_ASSIGNED_KTV",
                    "KTV không được phân công phụ trách Case này (Case #" + caseId + " chưa được claim hoặc thuộc về KTV #" + c.getAssignedTo() + ")");
        }

        ReturnCaseDtos.ReturnCaseDetailResponse response = returnCaseService.ktvDecideRound1(ktvId, caseId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint 4 — Decide Round 2
     * POST /api/v1/ktv/cases/{caseId}/decide-round2
     */
    @PostMapping("/cases/{caseId}/decide-round2")
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> decideRound2(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long caseId,
            @Valid @RequestBody ReturnCaseDtos.KtvRound2DecisionRequest request
    ) {
        long ktvId = extractUserId(jwt);
        CaseEntity c = findCaseOrThrow(caseId);

        // Kiểm tra xem Round 2 có bị leo thang (ESCALATED) lên Admin hay không
        CaseRoundEntity round2 = caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2).orElse(null);
        if (round2 != null && caseEventRepository.existsByCaseIdAndRoundIdAndEventType(caseId, round2.getId(), CaseEventEntity.TYPE_ESCALATED)) {
            throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "CASE_ESCALATED",
                    "Hồ sơ đã được chuyển lên Quản trị viên (Admin) do quá hạn SLA. KTV không còn quyền đưa ra quyết định.");
        }

        // Bắt buộc claim trước khi quyết định
        if (c.getAssignedTo() == null || !c.getAssignedTo().equals(ktvId)) {
            throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "NOT_ASSIGNED_KTV",
                    "KTV không được phân công phụ trách Case này (Case #" + caseId + " chưa được claim hoặc thuộc về KTV #" + c.getAssignedTo() + ")");
        }

        ReturnCaseDtos.ReturnCaseDetailResponse response = returnCaseService.ktvDecideRound2(ktvId, caseId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint 5 — Request Evidence
     * POST /api/v1/ktv/cases/{caseId}/request-evidence
     */
    @PostMapping("/cases/{caseId}/request-evidence")
    public ResponseEntity<ReturnCaseDtos.KtvRequestEvidenceResponse> requestEvidence(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long caseId,
            @Valid @RequestBody ReturnCaseDtos.KtvRequestEvidenceRequest request
    ) {
        long ktvId = extractUserId(jwt);
        CaseEntity c = findCaseOrThrow(caseId);

        // Bắt buộc claim trước khi yêu cầu bằng chứng
        if (c.getAssignedTo() == null || !c.getAssignedTo().equals(ktvId)) {
            throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "NOT_ASSIGNED_KTV",
                    "KTV không được phân công phụ trách Case này (Case #" + caseId + " chưa được claim hoặc thuộc về KTV #" + c.getAssignedTo() + ")");
        }

        CaseEvidenceRequestService.KtvRequestEvidenceResult result =
                caseEvidenceRequestService.requestEvidence(ktvId, caseId, request);

        if (result.isCreated()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(result.response());
        } else {
            return ResponseEntity.ok(result.response());
        }
    }
}
