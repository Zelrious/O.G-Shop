package com.oldbutgold.shop.modules.payment.api;

import com.oldbutgold.shop.modules.commerce.application.OrderManagementDtos;
import com.oldbutgold.shop.modules.commerce.application.OrderManagementService;
import com.oldbutgold.shop.modules.payment.application.PaymentService;
import com.oldbutgold.shop.modules.payment.application.RefundSettlementService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/payments")
public class AdminPaymentController {

    private final PaymentService paymentService;
    private final OrderManagementService orderManagementService;
    private final RefundSettlementService refundSettlementService;

    public AdminPaymentController(PaymentService paymentService, OrderManagementService orderManagementService) {
        this(paymentService, orderManagementService, null);
    }

    @Autowired
    public AdminPaymentController(
            PaymentService paymentService,
            OrderManagementService orderManagementService,
            RefundSettlementService refundSettlementService
    ) {
        this.paymentService = paymentService;
        this.orderManagementService = orderManagementService;
        this.refundSettlementService = refundSettlementService;
    }

    @PostMapping("/review-decision")
    @PreAuthorize("hasAnyRole('ADMIN', 'KTV')")
    public ResponseEntity<PaymentDtos.AdminReviewDecisionResponse> processReviewDecision(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PaymentDtos.AdminReviewDecisionRequest request
    ) {
        String adminIdentifier = jwt != null ? (jwt.getClaimAsString("email") != null ? jwt.getClaimAsString("email") : jwt.getSubject()) : "SYSTEM_ADMIN";
        return ResponseEntity.ok(paymentService.processAdminReviewDecision(
                adminIdentifier,
                request.orderId(),
                request.decision(),
                request.reasonNote(),
                request.vnpVerificationRef()
        ));
    }

    @PostMapping("/orders/{orderId}/disburse")
    @PreAuthorize("hasAnyRole('ADMIN', 'KTV')")
    public ResponseEntity<OrderManagementDtos.DisbursementResponse> disburseSellerFunds(
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(orderManagementService.disburseSellerFunds(orderId));
    }

    @GetMapping("/orders/{orderId}/settlements")
    @PreAuthorize("hasAnyRole('ADMIN', 'KTV')")
    public ResponseEntity<List<RefundSettlementService.SettlementOperationSummary>> listOrderSettlements(
            @PathVariable Long orderId
    ) {
        if (refundSettlementService == null) {
            throw new IllegalStateException("RefundSettlementService is not configured.");
        }
        return ResponseEntity.ok(refundSettlementService.listSettlementOperationsByOrderId(orderId));
    }

    @PostMapping("/settlements/{operationId}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'KTV')")
    public ResponseEntity<RefundSettlementService.RefundSettlementResult> confirmRefundSettlement(
            @PathVariable UUID operationId
    ) {
        if (refundSettlementService == null) {
            throw new IllegalStateException("RefundSettlementService is not configured.");
        }
        return ResponseEntity.ok(refundSettlementService.confirmRefundSettlement(operationId));
    }
}
