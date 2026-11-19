package com.oldbutgold.shop.modules.commerce.api;

import com.oldbutgold.shop.modules.commerce.application.CheckoutDtos;
import com.oldbutgold.shop.modules.commerce.application.CheckoutService;
import com.oldbutgold.shop.modules.commerce.application.GroupCheckoutDtos;
import com.oldbutgold.shop.modules.commerce.application.GroupCheckoutService;
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
    private final GroupCheckoutService groupCheckoutService;

    public CheckoutController(CheckoutService checkoutService, GroupCheckoutService groupCheckoutService) {
        this.checkoutService = checkoutService;
        this.groupCheckoutService = groupCheckoutService;
    }

    @GetMapping("/checkout/preview")
    public ResponseEntity<CheckoutDtos.CheckoutPreviewResponse> getCheckoutPreview(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam long productId,
            @RequestParam(required = false) String voucherCode,
            @RequestParam(required = false) Long offerId
    ) {
        long buyerId = extractUserId(jwt);
        CheckoutDtos.CheckoutPreviewResponse response = checkoutService.getCheckoutPreview(buyerId, productId, voucherCode,offerId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/checkout/buy-now")
    public ResponseEntity<CheckoutDtos.OrderCreatedResponse> buyNow(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CheckoutDtos.BuyNowRequest request
    ) {
        long buyerId = extractUserId(jwt);
        CheckoutDtos.BuyNowExecutionResult result = checkoutService.executeBuyNow(buyerId, request);
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.response());
    }

    @PostMapping("/checkout/groups")
    public ResponseEntity<GroupCheckoutDtos.GroupCheckoutResponse> checkoutGroup(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody GroupCheckoutDtos.GroupCheckoutRequest request
    ) {
        long buyerId = extractUserId(jwt);
        GroupCheckoutDtos.GroupCheckoutExecutionResult result = groupCheckoutService.executeCheckout(buyerId, request);
        HttpStatus status = result.isIdempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.response());
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
