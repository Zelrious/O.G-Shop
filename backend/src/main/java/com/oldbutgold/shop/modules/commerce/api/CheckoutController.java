package com.oldbutgold.shop.modules.commerce.api;

import com.oldbutgold.shop.modules.commerce.application.CheckoutDtos;
import com.oldbutgold.shop.modules.commerce.application.CheckoutService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/commerce")
public class CheckoutController {
    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @GetMapping("/checkout/preview")
    public ResponseEntity<CheckoutDtos.CheckoutPreviewResponse> getCheckoutPreview(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam long productId,
            @RequestParam(required = false) String voucherCode
    ) {
        long buyerId = extractUserId(jwt);
        CheckoutDtos.CheckoutPreviewResponse response = checkoutService.getCheckoutPreview(buyerId, productId, voucherCode);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/checkout/buy-now")
    public ResponseEntity<CheckoutDtos.OrderCreatedResponse> buyNow(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CheckoutDtos.BuyNowRequest request
    ) {
        long buyerId = extractUserId(jwt);
        CheckoutDtos.OrderCreatedResponse response = checkoutService.buyNow(buyerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<CheckoutDtos.OrderCreatedResponse> getOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long orderId
    ) {
        long buyerId = extractUserId(jwt);
        CheckoutDtos.OrderCreatedResponse response = checkoutService.getOrder(buyerId, orderId);
        return ResponseEntity.ok(response);
    }

    private static long extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AccessDeniedException("Yêu cầu đăng nhập để thực hiện mua hàng.");
        }
        return Long.parseLong(jwt.getSubject());
    }
}
