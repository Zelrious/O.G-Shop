package com.oldbutgold.shop.modules.payment.api;

import com.oldbutgold.shop.modules.payment.application.PaymentDtos;
import com.oldbutgold.shop.modules.payment.application.PaymentService;
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

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<PaymentDtos.PaymentInfoResponse> getPaymentInfo(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId
    ) {
        long buyerId = extractUserId(jwt);
        return ResponseEntity.ok(paymentService.getPaymentInfo(buyerId, orderId));
    }

    @PostMapping("/mock-process")
    public ResponseEntity<PaymentDtos.PaymentProcessResultResponse> processMockPayment(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PaymentDtos.ProcessMockPaymentRequest request
    ) {
        long buyerId = extractUserId(jwt);
        return ResponseEntity.ok(paymentService.processMockPayment(buyerId, request));
    }

    private static long extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AccessDeniedException("Yêu cầu đăng nhập để thực hiện thanh toán.");
        }
        return Long.parseLong(jwt.getSubject());
    }
}
