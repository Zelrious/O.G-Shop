package com.oldbutgold.shop.modules.trustsafety.api;

import com.oldbutgold.shop.modules.trustsafety.application.CaseEvidenceRequestService;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseDtos;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseService;
import com.oldbutgold.shop.modules.trustsafety.application.TrustSafetyBusinessException;
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

/**
 * KTV console endpoints for UC83 return cases.
 * Delegates strictly to application services without accessing repositories directly.
 */
@RestController
@RequestMapping("/api/v1/ktv")
@Validated
public class KtvCaseController {

    private final ReturnCaseService returnCaseService;
    private final CaseEvidenceRequestService caseEvidenceRequestService;

    public KtvCaseController(
            ReturnCaseService returnCaseService,
            CaseEvidenceRequestService caseEvidenceRequestService
    ) {
        this.returnCaseService = returnCaseService;
        this.caseEvidenceRequestService = caseEvidenceRequestService;
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
        returnCaseService.verifyCaseExistsForKtv(caseId);
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
        ReturnCaseDtos.ReturnCaseDetailResponse response = returnCaseService.ktvConsoleDecideRound1(ktvId, caseId, request);
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
        ReturnCaseDtos.ReturnCaseDetailResponse response = returnCaseService.ktvConsoleDecideRound2(ktvId, caseId, request);
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
        CaseEvidenceRequestService.KtvRequestEvidenceResult result =
                caseEvidenceRequestService.requestEvidence(ktvId, caseId, request);

        if (result.isCreated()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(result.response());
        } else {
            return ResponseEntity.ok(result.response());
        }
    }
}
