package com.oldbutgold.shop.modules.catalog.api;

import com.oldbutgold.shop.modules.catalog.application.CatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CatalogDtos.CategoryResponse>> getCategories() {
        return ResponseEntity.ok(catalogService.getCategories());
    }

    @GetMapping("/products")
    public ResponseEntity<CatalogDtos.PageResponse<CatalogDtos.ProductSummaryResponse>> getProducts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "newest") String sort
    ) {
        return ResponseEntity.ok(catalogService.searchProducts(
                query, categoryId, condition, minPrice, maxPrice, page, size, sort
        ));
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<CatalogDtos.ProductDetailResponse> getProductDetail(@PathVariable long productId) {
        return ResponseEntity.ok(catalogService.getProductDetail(productId));
    }
}
