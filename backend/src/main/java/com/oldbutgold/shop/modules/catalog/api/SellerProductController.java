package com.oldbutgold.shop.modules.catalog.api;

import com.oldbutgold.shop.modules.catalog.application.SellerProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seller/products")
public class SellerProductController {
    private final SellerProductService sellerProductService;

    public SellerProductController(SellerProductService sellerProductService) {
        this.sellerProductService = sellerProductService;
    }

    @GetMapping
    public ResponseEntity<CatalogDtos.PageResponse<CatalogDtos.SellerProductSummaryResponse>> getSellerProducts(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        long sellerId = extractUserId(jwt);
        return ResponseEntity.ok(sellerProductService.getSellerProducts(sellerId, page, size));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<CatalogDtos.SellerProductDetailResponse> getSellerProduct(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long productId
    ) {
        long sellerId = extractUserId(jwt);
        return ResponseEntity.ok(sellerProductService.getSellerProduct(sellerId, productId));
    }

    @PostMapping
    public ResponseEntity<CatalogDtos.SellerProductDetailResponse> createProduct(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CatalogDtos.CreateOrUpdateProductRequest request
    ) {
        long sellerId = extractUserId(jwt);
        CatalogDtos.SellerProductDetailResponse response = sellerProductService.createProduct(sellerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{productId}")
    public ResponseEntity<CatalogDtos.SellerProductDetailResponse> updateProduct(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long productId,
            @Valid @RequestBody CatalogDtos.CreateOrUpdateProductRequest request
    ) {
        long sellerId = extractUserId(jwt);
        return ResponseEntity.ok(sellerProductService.updateProduct(sellerId, productId, request));
    }

    @PostMapping("/{productId}/publish")
    public ResponseEntity<CatalogDtos.SellerProductDetailResponse> publishProduct(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long productId
    ) {
        long sellerId = extractUserId(jwt);
        return ResponseEntity.ok(sellerProductService.publishProduct(sellerId, productId));
    }

    @PostMapping("/{productId}/submit")
    public ResponseEntity<CatalogDtos.SellerProductDetailResponse> submitProduct(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long productId
    ) {
        long sellerId = extractUserId(jwt);
        return ResponseEntity.ok(sellerProductService.submitProduct(sellerId, productId));
    }

    @PostMapping("/{productId}/media")
    public ResponseEntity<CatalogDtos.MediaUploadResponse> uploadMedia(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long productId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "mediaType", defaultValue = "IMAGE") String mediaType
    ) {
        long sellerId = extractUserId(jwt);
        CatalogDtos.MediaUploadResponse response = sellerProductService.uploadMedia(sellerId, productId, file, mediaType);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{productId}/media/{mediaId}")
    public ResponseEntity<Void> deleteMedia(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long productId,
            @PathVariable long mediaId
    ) {
        long sellerId = extractUserId(jwt);
        sellerProductService.deleteMedia(sellerId, productId, mediaId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{productId}/hide")
    public ResponseEntity<CatalogDtos.SellerProductDetailResponse> hideProduct(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long productId
    ) {
        long sellerId = extractUserId(jwt);
        return ResponseEntity.ok(sellerProductService.hideProduct(sellerId, productId));
    }

    private static long extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Yêu cầu xác thực người dùng.");
        }
        return Long.parseLong(jwt.getSubject());
    }
}
