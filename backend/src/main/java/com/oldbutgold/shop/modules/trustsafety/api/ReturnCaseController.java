package com.oldbutgold.shop.modules.trustsafety.api;

import com.oldbutgold.shop.modules.trustsafety.application.CaseEvidenceRequestService;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseDtos;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@RestController
@RequestMapping("/api/v1/trustsafety/returns")
public class ReturnCaseController {

    private final ReturnCaseService returnCaseService;
    private final CaseEvidenceRequestService caseEvidenceRequestService;
    private final String environment;
    private final boolean simulationEnabled;

    public ReturnCaseController(ReturnCaseService returnCaseService,
                                CaseEvidenceRequestService caseEvidenceRequestService) {
        this(returnCaseService, caseEvidenceRequestService, "PROD", false);
    }

    @Autowired
    public ReturnCaseController(ReturnCaseService returnCaseService,
                                CaseEvidenceRequestService caseEvidenceRequestService,
                                @Value("${app.environment:PROD}") String environment,
                                @Value("${app.ghn.simulation-enabled:false}") boolean simulationEnabled) {
        this.returnCaseService = returnCaseService;
        this.caseEvidenceRequestService = caseEvidenceRequestService;
        this.environment = (environment != null && !environment.isBlank())
                ? environment.trim().toUpperCase(Locale.ROOT)
                : "PROD";
        this.simulationEnabled = simulationEnabled;
    }

    private static long extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AccessDeniedException("Yêu cầu đăng nhập.");
        }
        return Long.parseLong(jwt.getSubject());
    }

    // ==================== BUYER ENDPOINTS ====================

    /**
     * Tải lên tệp bằng chứng trả hàng / khiếu nại (BG-1)
     * POST /api/v1/trustsafety/returns/evidence/upload
     */
    @PostMapping(value = "/evidence/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReturnCaseDtos.EvidenceItemDto> uploadEvidence(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "mediaType", defaultValue = "IMAGE") String mediaType,
            @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds
    ) {
        long actorId = extractUserId(jwt);
        ReturnCaseDtos.EvidenceItemDto response = returnCaseService.uploadReturnEvidence(actorId, file, mediaType, durationSeconds);
        return ResponseEntity.ok(response);
    }

    /**
     * Tạo yêu cầu trả hàng/khiếu nại (UC62)
     * POST /api/v1/trustsafety/returns/{orderId}
     */
    @PostMapping("/{orderId}")
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> createReturnRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId,
            @RequestBody ReturnCaseDtos.CreateReturnRequest request
    ) {
        long buyerId = extractUserId(jwt);
        ReturnCaseDtos.ReturnCaseDetailResponse response = returnCaseService.createReturnRequest(buyerId, orderId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Xem chi tiết Case theo caseId (Buyer/Seller/KTV)
     * GET /api/v1/trustsafety/returns/{caseId}
     */
    @GetMapping("/{caseId}")
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> getReturnCaseById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long caseId
    ) {
        long actorId = extractUserId(jwt);
        ReturnCaseDtos.ReturnCaseDetailResponse response = returnCaseService.getReturnCaseById(actorId, caseId);
        return ResponseEntity.ok(response);
    }

    /**
     * Xem chi tiết Case theo orderId (Buyer/Seller)
     * GET /api/v1/trustsafety/returns/order/{orderId}
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> getReturnCaseByOrderId(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId
    ) {
        long actorId = extractUserId(jwt);
        ReturnCaseDtos.ReturnCaseDetailResponse response = returnCaseService.getReturnCaseByOrderId(actorId, orderId);
        return ResponseEntity.ok(response);
    }

    /**
     * Người mua rút yêu cầu trả hàng (UC62 7c)
     * POST /api/v1/trustsafety/returns/{caseId}/buyer/withdraw
     */
    @PostMapping("/{caseId}/buyer/withdraw")
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> withdrawReturnRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long caseId,
            @RequestBody(required = false) ReturnCaseDtos.WithdrawReturnRequest request
    ) {
        long buyerId = extractUserId(jwt);
        ReturnCaseDtos.ReturnCaseDetailResponse response = returnCaseService.withdrawReturnRequest(buyerId, caseId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Người mua đăng ký gửi hàng trả (UC33)
     * POST /api/v1/trustsafety/returns/{caseId}/shipment
     */
    @PostMapping("/{caseId}/shipment")
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> submitReturnShipment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long caseId,
            @RequestBody ReturnCaseDtos.SubmitReturnShipmentRequest request
    ) {
        long buyerId = extractUserId(jwt);
        ReturnCaseDtos.ReturnCaseDetailResponse response = returnCaseService.submitReturnShipment(buyerId, caseId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Xác nhận bàn giao trực tiếp (Buyer hoặc Seller) (UC33)
     * POST /api/v1/trustsafety/returns/{caseId}/delivery/confirm
     */
    @PostMapping("/{caseId}/delivery/confirm")
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> confirmReturnDeliveryDirect(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long caseId,
            @RequestBody ReturnCaseDtos.ConfirmReturnDeliveryRequest request
    ) {
        long actorId = extractUserId(jwt);
        ReturnCaseDtos.ReturnCaseDetailResponse response = returnCaseService.confirmReturnDeliveryDirect(actorId, caseId, request);
        return ResponseEntity.ok(response);
    }

    // ==================== SELLER ENDPOINTS ====================

    /**
     * Người bán phản hồi Vòng 1 (APPROVE_RETURN / SELLER_DECLINED)
     * POST /api/v1/trustsafety/returns/{caseId}/seller/respond
     */
    @PostMapping("/{caseId}/seller/respond")
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> sellerRespondRound1(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long caseId,
            @RequestBody ReturnCaseDtos.SellerRound1ResponseRequest request
    ) {
        long sellerId = extractUserId(jwt);
        ReturnCaseDtos.ReturnCaseDetailResponse response = returnCaseService.sellerRespondRound1(sellerId, caseId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Người bán đồng ý hoàn tiền Vòng 2 (Task 3.2.1 - sellerAgreeRefundRound2)
     * POST /api/v1/trustsafety/returns/{caseId}/seller/agree-refund
     */
    @PostMapping("/{caseId}/seller/agree-refund")
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> sellerAgreeRefundRound2(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long caseId,
            @Valid @RequestBody ReturnCaseDtos.SellerAgreeRefundRequest request
    ) {
        long sellerId = extractUserId(jwt);
        ReturnCaseDtos.ReturnCaseDetailResponse response = returnCaseService.sellerAgreeRefundRound2(sellerId, caseId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Người bán gửi yêu cầu miễn trừ thiệt hại Vòng 2 (Task 3.2.2)
     * POST /api/v1/trustsafety/returns/{caseId}/seller/damage-request
     */
    @PostMapping("/{caseId}/seller/damage-request")
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> submitDamageExemptionRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long caseId,
            @Valid @RequestBody ReturnCaseDtos.SellerDamageExemptionRequest request
    ) {
        long sellerId = extractUserId(jwt);
        ReturnCaseDtos.ReturnCaseDetailResponse response = returnCaseService.submitDamageExemptionRequest(sellerId, caseId, request);
        return ResponseEntity.ok(response);
    }

    // ==================== KTV ENDPOINTS ====================

    /**
     * KTV phát hành yêu cầu bổ sung bằng chứng thiệt hại Vòng 2 (BD-5 Phase 1 - API 1)
     * POST /api/v1/trustsafety/returns/{caseId}/ktv/request-evidence
     */
    @PostMapping("/{caseId}/ktv/request-evidence")
    @PreAuthorize("hasRole('KTV')")
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

    /**
     * KTV yêu cầu Người mua bổ sung bằng chứng ở Vòng 1 (UC62 Alt 7a)
     * POST /api/v1/trustsafety/returns/{caseId}/ktv/request-round1-evidence
     */
    @PostMapping("/{caseId}/ktv/request-round1-evidence")
    @PreAuthorize("hasRole('KTV')")
    public ResponseEntity<ReturnCaseDtos.KtvRequestEvidenceResponse> ktvRequestRound1Evidence(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long caseId,
            @Valid @RequestBody ReturnCaseDtos.KtvRequestEvidenceRequest request
    ) {
        long ktvId = extractUserId(jwt);
        CaseEvidenceRequestService.KtvRequestEvidenceResult result =
                caseEvidenceRequestService.requestRound1Evidence(ktvId, caseId, request);
        if (result.isCreated()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(result.response());
        }
        return ResponseEntity.ok(result.response());
    }

    /**
     * KTV phân xử Vòng 1 (APPROVE_RETURN / REJECT_RETURN)
     * POST /api/v1/trustsafety/returns/{caseId}/ktv/decide-round1
     */
    @PostMapping("/{caseId}/ktv/decide-round1")
    @PreAuthorize("hasRole('KTV')")
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> ktvDecideRound1(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long caseId,
            @RequestBody ReturnCaseDtos.KtvRound1DecisionRequest request
    ) {
        long ktvId = extractUserId(jwt);
        ReturnCaseDtos.ReturnCaseDetailResponse response = returnCaseService.ktvDecideRound1(ktvId, caseId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * KTV quyết định cuối Vòng 2 (FULL_GOODS_REFUND / EXEMPT_GOODS_REFUND) (Task 3.2.3)
     * POST /api/v1/trustsafety/returns/{caseId}/ktv/decide-round2
     */
    @PostMapping("/{caseId}/ktv/decide-round2")
    @PreAuthorize("hasRole('KTV')")
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> ktvDecideRound2(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long caseId,
            @Valid @RequestBody ReturnCaseDtos.KtvRound2DecisionRequest request
    ) {
        long ktvId = extractUserId(jwt);
        ReturnCaseDtos.ReturnCaseDetailResponse response = returnCaseService.ktvDecideRound2(ktvId, caseId, request);
        return ResponseEntity.ok(response);
    }

    // ==================== SYSTEM/CARRIER ENDPOINTS ====================

    /**
     * Carrier xác nhận giao hàng trả thành công (UC33 — simulation-only DEV endpoint, ADMIN role)
     * POST /api/v1/trustsafety/returns/{caseId}/delivery/carrier
     */
    @PostMapping("/{caseId}/delivery/carrier")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> confirmReturnDeliveryByCarrier(
            @PathVariable Long caseId,
            @RequestParam Long shipmentId,
            @RequestParam java.time.Instant deliveredAt
    ) {
        if ("PROD".equals(this.environment) || !this.simulationEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN_SIMULATION_IN_PRODUCTION");
        }
        ReturnCaseDtos.ReturnCaseDetailResponse response = returnCaseService.confirmReturnDeliveryByCarrier(caseId, shipmentId, deliveredAt);
        return ResponseEntity.ok(response);
    }

    /**
     * Tra cứu vận đơn trả hàng
     * GET /api/v1/trustsafety/returns/{caseId}/shipment
     */
    @GetMapping("/{caseId}/shipment")
    public ResponseEntity<ReturnCaseDtos.ReturnShipmentDetailResponse> getReturnShipmentByCaseId(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long caseId
    ) {
        long actorId = extractUserId(jwt);
        ReturnCaseDtos.ReturnShipmentDetailResponse response = returnCaseService.getReturnShipmentByCaseId(actorId, caseId);
        return ResponseEntity.ok(response);
    }
}