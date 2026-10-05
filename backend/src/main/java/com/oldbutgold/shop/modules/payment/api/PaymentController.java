package com.oldbutgold.shop.modules.payment.api;

import com.oldbutgold.shop.modules.payment.application.PaymentDtos.PaymentInfoResponse;
import com.oldbutgold.shop.modules.payment.application.PaymentDtos.PaymentProcessResultResponse;
import com.oldbutgold.shop.modules.payment.application.PaymentDtos.ProcessMockPaymentRequest;
import com.oldbutgold.shop.modules.payment.application.PaymentService;
import com.oldbutgold.shop.modules.payment.application.VnPayPaymentService;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final VnPayPaymentService vnPayService;

    public PaymentController(PaymentService paymentService, VnPayPaymentService vnPayService) {
        this.paymentService = paymentService;
        this.vnPayService = vnPayService;
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<PaymentInfoResponse> getPaymentInfo(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId
    ) {
        long buyerId = extractUserId(jwt);
        return ResponseEntity.ok(paymentService.getPaymentInfo(buyerId, orderId));
    }

    @PostMapping("/mock-process")
    public ResponseEntity<PaymentProcessResultResponse> processMockPayment(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProcessMockPaymentRequest request
    ) {
        long buyerId = extractUserId(jwt);
        return ResponseEntity.ok(paymentService.processMockPayment(buyerId, request));
    }

    @PostMapping("/vnpay-url")
    public PaymentDtos.PaymentUrlResponse createPaymentUrlResponse(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PaymentDtos.CreateVnPaymentRequest request,
            HttpServletRequest servletRequest
    ) {
        String clientIp = servletRequest.getRemoteAddr();

        String paymentUrl = vnPayService.createUrl(extractUserId(jwt), request.orderId(), clientIp);

        return new PaymentDtos.PaymentUrlResponse(paymentUrl);
    }

    @GetMapping("/vnpay-ipn")
    public PaymentDtos.VnPayIpnResponse handleVnPayIpn(@RequestParam Map<String, String> allParams) {
        try {
            return vnPayService.handleIpn(allParams);
        } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException error) {
            return new PaymentDtos.VnPayIpnResponse("99", "Persistence failed; retry notification");
        }
    }

    private static long extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AccessDeniedException("Yêu cầu đăng nhập để thực hiện thanh toán.");
        }
        return Long.parseLong(jwt.getSubject());
    }
}
