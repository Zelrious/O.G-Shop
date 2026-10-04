package com.oldbutgold.shop.modules.payment.api;

import com.oldbutgold.shop.modules.payment.application.PaymentDtos.PaymentInfoResponse;
import com.oldbutgold.shop.modules.payment.application.PaymentDtos.PaymentProcessResultResponse;
import com.oldbutgold.shop.modules.payment.application.PaymentDtos.ProcessMockPaymentRequest;
import com.oldbutgold.shop.modules.payment.application.PaymentService;
import com.oldbutgold.shop.modules.payment.application.VnPayService;
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
    private final VnPayService vnPayService;

    public PaymentController(PaymentService paymentService, VnPayService vnPayService) {
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
            @Valid @RequestBody PaymentDtos.CreateVnPaymentRequest request,
            HttpServletRequest servletRequest
    ) {
        String clientIp = servletRequest.getRemoteAddr();

        String paymentUrl = vnPayService.createPaymentUrl(
                request.orderRef(),
                request.amountVnd(),
                request.orderInfo(),
                clientIp
        );

        return new PaymentDtos.PaymentUrlResponse(paymentUrl);
    }

    @GetMapping("/vnpay-ipn")
    public PaymentDtos.VnPayIpnResponse handleVnPayIpn(@RequestParam Map<String, String> allParams) {
        boolean isValidChecksum = vnPayService.verifyCallback(allParams);
        if (!isValidChecksum) {
            return PaymentDtos.VnPayIpnResponse.invalidChecksum();
        }

        String responseCode = allParams.get("vnp_ResponseCode");
        if ("00".equals(responseCode)) {
            return PaymentDtos.VnPayIpnResponse.success();
        }

        return PaymentDtos.VnPayIpnResponse.orderNotFound();
    }

    private static long extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AccessDeniedException("Yêu cầu đăng nhập để thực hiện thanh toán.");
        }
        return Long.parseLong(jwt.getSubject());
    }
}
