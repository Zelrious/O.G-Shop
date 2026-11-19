package com.oldbutgold.shop.modules.payment.api;

import com.oldbutgold.shop.modules.commerce.application.OrderManagementDtos;
import com.oldbutgold.shop.modules.commerce.application.OrderManagementService;
import com.oldbutgold.shop.modules.payment.application.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/payments")
public class AdminPaymentController {

    private final PaymentService paymentService;
    private final OrderManagementService orderManagementService;

    public AdminPaymentController(PaymentService paymentService, OrderManagementService orderManagementService) {
        this.paymentService = paymentService;
        this.orderManagementService = orderManagementService;
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
}
