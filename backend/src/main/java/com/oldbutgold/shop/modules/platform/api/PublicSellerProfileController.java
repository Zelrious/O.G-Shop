package com.oldbutgold.shop.modules.platform.api;

import com.oldbutgold.shop.modules.platform.api.PublicSellerDtos.*;
import com.oldbutgold.shop.modules.platform.application.PublicSellerProfileService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/sellers")
public class PublicSellerProfileController {

    private final PublicSellerProfileService service;

    public PublicSellerProfileController(PublicSellerProfileService service) {
        this.service = service;
    }

    @GetMapping("/{sellerId}")
    public ResponseEntity<?> getPublicProfile(@PathVariable long sellerId) {
        return service.getPublicProfile(sellerId)
                .<ResponseEntity<?>>map(body -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore().mustRevalidate())
                        .body(body))
                .orElseGet(PublicSellerProfileController::notFound);
    }

    @GetMapping("/{sellerId}/products")
    public ResponseEntity<?> getPublicProducts(
            @PathVariable long sellerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "newest") String sort
    ) {
        return service.getPublicProducts(sellerId, page, size, sort)
                .<ResponseEntity<?>>map(body -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore().mustRevalidate())
                        .body(body))
                .orElseGet(PublicSellerProfileController::notFound);
    }

    @GetMapping("/{sellerId}/reviews")
    public ResponseEntity<?> getPublicReviews(
            @PathVariable long sellerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer rating
    ) {
        return service.getPublicReviews(sellerId, page, size, rating)
                .<ResponseEntity<?>>map(body -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore().mustRevalidate())
                        .body(body))
                .orElseGet(PublicSellerProfileController::notFound);
    }

    private static ResponseEntity<Map<String, Object>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "code", "SELLER_PROFILE_NOT_FOUND",
                        "message", "Hồ sơ người bán không tồn tại hoặc hiện không khả dụng.",
                        "requestId", UUID.randomUUID().toString(),
                        "fieldErrors", List.of()
                ));
    }
}

