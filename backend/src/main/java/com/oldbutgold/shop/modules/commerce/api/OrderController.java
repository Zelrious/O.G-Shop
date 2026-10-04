package com.oldbutgold.shop.modules.commerce.api;

import com.oldbutgold.shop.modules.commerce.application.OrderManagementDtos;
import com.oldbutgold.shop.modules.commerce.application.OrderManagementService;
import org.springframework.data.domain.Page;
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

@RestController
@RequestMapping("/api/v1/commerce")
public class OrderController {
    private final OrderManagementService orderManagementService;

    public OrderController(OrderManagementService orderManagementService) {
        this.orderManagementService = orderManagementService;
    }

    // --- Buyer Endpoints ---

    @GetMapping("/orders")
    public ResponseEntity<Page<OrderManagementDtos.OrderSummaryResponse>> getBuyerOrders(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        long buyerId = extractUserId(jwt);
        return ResponseEntity.ok(orderManagementService.getBuyerOrders(buyerId, status, page, size));
    }

    @GetMapping("/orders/{orderId}/detail")
    public ResponseEntity<OrderManagementDtos.OrderDetailResponse> getBuyerOrderDetail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId
    ) {
        long buyerId = extractUserId(jwt);
        return ResponseEntity.ok(orderManagementService.getBuyerOrderDetail(buyerId, orderId));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<OrderManagementDtos.OrderActionResponse> cancelBuyerOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId,
            @RequestBody(required = false) OrderManagementDtos.CancelOrderRequest request
    ) {
        long buyerId = extractUserId(jwt);
        String reason = request != null ? request.reason() : null;
        return ResponseEntity.ok(orderManagementService.cancelBuyerOrder(buyerId, orderId, reason));
    }

    // --- Seller Endpoints ---

    @GetMapping("/seller/orders")
    public ResponseEntity<Page<OrderManagementDtos.OrderSummaryResponse>> getSellerOrders(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        long sellerId = extractUserId(jwt);
        return ResponseEntity.ok(orderManagementService.getSellerOrders(sellerId, status, page, size));
    }

    @GetMapping("/seller/orders/{orderId}/detail")
    public ResponseEntity<OrderManagementDtos.OrderDetailResponse> getSellerOrderDetail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId
    ) {
        long sellerId = extractUserId(jwt);
        return ResponseEntity.ok(orderManagementService.getSellerOrderDetail(sellerId, orderId));
    }

    @PostMapping("/seller/orders/{orderId}/confirm")
    public ResponseEntity<OrderManagementDtos.OrderActionResponse> confirmSellerOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId
    ) {
        long sellerId = extractUserId(jwt);
        return ResponseEntity.ok(orderManagementService.confirmSellerOrder(sellerId, orderId));
    }

    private static long extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AccessDeniedException("Yêu cầu đăng nhập.");
        }
        return Long.parseLong(jwt.getSubject());
    }
}
