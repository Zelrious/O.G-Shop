package com.oldbutgold.shop.modules.commerce.api;

import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseDtos;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Order-scoped REST endpoints for UC83 return cases.
 * Delegates strictly to {@link ReturnCaseService} without accessing repositories directly.
 */
@RestController
@RequestMapping("/api/v1/commerce/orders/{orderId}")
public class ReturnCaseCommerceController {

    private final ReturnCaseService returnCaseService;

    public ReturnCaseCommerceController(ReturnCaseService returnCaseService) {
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
     * 1. Tạo yêu cầu trả hàng / khiếu nại (Buyer)
     * POST /api/v1/commerce/orders/{orderId}/return-request
     */
    @PostMapping("/return-request")
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> createReturnRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId,
            @Valid @RequestBody ReturnCaseDtos.CreateReturnRequest request
    ) {
        long buyerId = extractUserId(jwt);
        ReturnCaseDtos.ReturnCaseDetailResponse response =
                returnCaseService.createReturnRequestForOrder(buyerId, orderId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 2. Tra cứu chi tiết hồ sơ trả hàng (Buyer / Seller - chỉ active case per R2)
     * GET /api/v1/commerce/orders/{orderId}/return-case
     */
    @GetMapping("/return-case")
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> getReturnCase(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId
    ) {
        long actorId = extractUserId(jwt);
        ReturnCaseDtos.ReturnCaseDetailResponse response =
                returnCaseService.getActiveReturnCaseForOrder(actorId, orderId);
        return ResponseEntity.ok(response);
    }

    /**
     * 3. Người mua rút yêu cầu trả hàng (Buyer)
     * POST /api/v1/commerce/orders/{orderId}/return-case/withdraw
     */
    @PostMapping("/return-case/withdraw")
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> withdrawReturnRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId,
            @RequestBody(required = false) ReturnCaseDtos.WithdrawReturnRequest request
    ) {
        long buyerId = extractUserId(jwt);
        ReturnCaseDtos.ReturnCaseDetailResponse response =
                returnCaseService.withdrawReturnRequestForOrder(buyerId, orderId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 4. Người bán phản hồi Vòng 1 (Seller)
     * POST /api/v1/commerce/orders/{orderId}/return-case/seller-response
     */
    @PostMapping("/return-case/seller-response")
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> sellerRespondRound1(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId,
            @Valid @RequestBody ReturnCaseDtos.SellerRound1ResponseRequest request
    ) {
        long sellerId = extractUserId(jwt);
        ReturnCaseDtos.ReturnCaseDetailResponse response =
                returnCaseService.sellerRespondRound1ForOrder(sellerId, orderId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 5. Xác nhận bàn giao trực tiếp hai bên (Buyer hoặc Seller)
     * POST /api/v1/commerce/orders/{orderId}/return-shipment/confirm-direct-handover
     */
    @PostMapping("/return-shipment/confirm-direct-handover")
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> confirmReturnDeliveryDirect(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId,
            @Valid @RequestBody ReturnCaseDtos.ConfirmReturnDeliveryRequest request
    ) {
        long actorId = extractUserId(jwt);
        ReturnCaseDtos.ReturnCaseDetailResponse response =
                returnCaseService.confirmReturnDeliveryDirectForOrder(actorId, orderId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 6. Người bán đồng ý hoàn tiền Vòng 2 (Seller)
     * POST /api/v1/commerce/orders/{orderId}/return-case/agree-refund
     */
    @PostMapping("/return-case/agree-refund")
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> sellerAgreeRefundRound2(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId,
            @Valid @RequestBody ReturnCaseDtos.SellerAgreeRefundRequest request
    ) {
        long sellerId = extractUserId(jwt);
        ReturnCaseDtos.ReturnCaseDetailResponse response =
                returnCaseService.sellerAgreeRefundRound2ForOrder(sellerId, orderId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 7. Người bán đề nghị miễn trừ thiệt hại Vòng 2 (Seller)
     * POST /api/v1/commerce/orders/{orderId}/return-case/damage-exemption
     */
    @PostMapping("/return-case/damage-exemption")
    public ResponseEntity<ReturnCaseDtos.ReturnCaseDetailResponse> submitDamageExemptionRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId,
            @Valid @RequestBody ReturnCaseDtos.SellerDamageExemptionRequest request
    ) {
        long sellerId = extractUserId(jwt);
        ReturnCaseDtos.ReturnCaseDetailResponse response =
                returnCaseService.submitDamageExemptionRequestForOrder(sellerId, orderId, request);
        return ResponseEntity.ok(response);
    }
}
