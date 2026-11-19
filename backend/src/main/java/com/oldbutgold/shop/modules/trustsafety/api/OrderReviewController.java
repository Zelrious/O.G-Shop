package com.oldbutgold.shop.modules.trustsafety.api;

import com.oldbutgold.shop.modules.trustsafety.api.ReviewDtos.*;
import com.oldbutgold.shop.modules.trustsafety.application.OrderReviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/commerce/orders")
public class OrderReviewController {

    private final OrderReviewService orderReviewService;

    public OrderReviewController(OrderReviewService orderReviewService) {
        this.orderReviewService = orderReviewService;
    }

    @PostMapping("/{orderId}/review")
    public ResponseEntity<ReviewResponse> submitReview(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId,
            @Valid @RequestBody CreateReviewRequest request
    ) {
        long userId = extractUserId(jwt);
        CreateReviewRequest normalized = new CreateReviewRequest(orderId, request.rating(), request.comment());
        return ResponseEntity.ok(orderReviewService.submitReview(userId, normalized));
    }

    @GetMapping("/{orderId}/reviews")
    public ResponseEntity<OrderReviewsResponse> getOrderReviews(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderReviewService.getOrderReviews(orderId));
    }

    @GetMapping("/users/{userId}/reviews")
    public ResponseEntity<UserReviewsSummaryResponse> getUserReviews(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(orderReviewService.getUserReviews(userId, limit));
    }

    private static long extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AccessDeniedException("Yêu cầu đăng nhập.");
        }
        return Long.parseLong(jwt.getSubject());
    }
}
