package com.oldbutgold.shop.modules.catalog.api;

import com.oldbutgold.shop.modules.catalog.application.SellerProductService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@RequestMapping("/api/v1/moderation/products")
public class ModerationController {
    private final SellerProductService sellerProductService;

    public ModerationController(SellerProductService sellerProductService) {
        this.sellerProductService = sellerProductService;
    }

    @GetMapping
    public ResponseEntity<CatalogDtos.PageResponse<CatalogDtos.ModerationProductResponse>> getPendingProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(sellerProductService.getPendingProducts(page, size));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<CatalogDtos.ModerationProductResponse> getModerationDetail(
            @PathVariable long productId
    ) {
        return ResponseEntity.ok(sellerProductService.getModerationDetail(productId));
    }

    @PostMapping("/{productId}/approve")
    public ResponseEntity<CatalogDtos.ActionResponse> approveProduct(
            @PathVariable long productId,
            @Valid @RequestBody CatalogDtos.ApproveProductRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        long reviewerId = extractUserId(jwt);
        return ResponseEntity.ok(sellerProductService.approveProduct(
                reviewerId, productId, request.expectedVersion(), request.commandKey()
        ));
    }

    @PostMapping("/{productId}/reject")
    public ResponseEntity<CatalogDtos.ActionResponse> rejectProduct(
            @PathVariable long productId,
            @Valid @RequestBody CatalogDtos.RejectProductRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        long reviewerId = extractUserId(jwt);
        return ResponseEntity.ok(sellerProductService.rejectProduct(
                reviewerId, productId, request.reason(), request.expectedVersion(), request.commandKey()
        ));
    }

    private static long extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Yêu cầu xác thực người dùng.");
        }
        return Long.parseLong(jwt.getSubject());
    }
}
