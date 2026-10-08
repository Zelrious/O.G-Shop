package com.oldbutgold.shop.modules.catalog.application;

import com.oldbutgold.shop.modules.catalog.api.CatalogDtos;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.CategoryEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.CategoryRepository;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductMediaEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductMediaRepository;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductRepository;
import com.oldbutgold.shop.modules.identity.application.IdentityCatalogFacade;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CatalogService {
    private static final Set<String> ALLOWED_SORTS = Set.of("newest", "price_asc", "price_desc");

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductMediaRepository productMediaRepository;
    private final IdentityCatalogFacade identityCatalogFacade;

    public CatalogService(CategoryRepository categoryRepository,
                          ProductRepository productRepository,
                          ProductMediaRepository productMediaRepository,
                          IdentityCatalogFacade identityCatalogFacade) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.productMediaRepository = productMediaRepository;
        this.identityCatalogFacade = identityCatalogFacade;
    }

    public List<CatalogDtos.CategoryResponse> getCategories() {
        return categoryRepository.findByActiveTrueOrderByDisplayOrderAscCategoryNameAsc().stream()
                .map(CatalogDtos.CategoryResponse::from)
                .toList();
    }

    public CatalogDtos.PageResponse<CatalogDtos.ProductSummaryResponse> searchProducts(
            String query,
            Long categoryId,
            String condition,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            int page,
            int size,
            String sort
    ) {
        return searchProductsMulti(
                query,
                categoryId != null ? List.of(categoryId) : null,
                condition != null ? List.of(condition) : null,
                minPrice,
                maxPrice,
                page,
                size,
                sort
        );
    }

    public CatalogDtos.PageResponse<CatalogDtos.ProductSummaryResponse> searchProductsMulti(
            String query,
            List<Long> filterCategoryIds,
            List<String> filterConditions,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            int page,
            int size,
            String sort
    ) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("Giá tối thiểu (minPrice) không được lớn hơn giá tối đa (maxPrice).");
        }

        if (filterConditions != null) {
            for (String cond : filterConditions) {
                if (cond != null && !cond.isBlank() && !ProductEntity.ALLOWED_CONDITIONS.contains(cond.trim())) {
                    throw new IllegalArgumentException("Tình trạng sản phẩm không hợp lệ: " + cond);
                }
            }
        }

        String sortKey = (sort == null || sort.isBlank()) ? "newest" : sort.trim().toLowerCase();
        if (!ALLOWED_SORTS.contains(sortKey)) {
            throw new IllegalArgumentException("Tùy chọn sắp xếp không hợp lệ: " + sort);
        }

        Sort sortOrder = switch (sortKey) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "listedPrice").and(Sort.by(Sort.Direction.ASC, "id"));
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "listedPrice").and(Sort.by(Sort.Direction.DESC, "id"));
            default -> Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        };

        int clampedPage = Math.max(page, 0);
        int clampedSize = Math.min(Math.max(size, 1), 48);
        Pageable pageable = PageRequest.of(clampedPage, clampedSize, sortOrder);

        Specification<ProductEntity> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), ProductEntity.STATUS_ACTIVE));
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (query != null && !query.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + query.trim().toLowerCase() + "%"));
            }
            if (filterCategoryIds != null && !filterCategoryIds.isEmpty()) {
                predicates.add(cb.or(
                        filterCategoryIds.stream()
                                .map(cid -> cb.isMember(cid, root.<Set<Long>>get("categoryIds")))
                                .toArray(Predicate[]::new)
                ));
            }
            if (filterConditions != null && !filterConditions.isEmpty()) {
                List<String> validConds = filterConditions.stream()
                        .filter(c -> c != null && !c.isBlank())
                        .map(String::trim)
                        .toList();
                if (!validConds.isEmpty()) {
                    predicates.add(root.get("condition").in(validConds));
                }
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("listedPrice"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("listedPrice"), maxPrice));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ProductEntity> productPage = productRepository.findAll(spec, pageable);
        List<ProductEntity> products = productPage.getContent();

        if (products.isEmpty()) {
            return new CatalogDtos.PageResponse<>(
                    List.of(), productPage.getNumber(), productPage.getSize(),
                    productPage.getTotalElements(), productPage.getTotalPages(), productPage.hasNext()
            );
        }

        Set<Long> categoryIds = products.stream().flatMap(p -> p.getCategoryIds().stream()).collect(Collectors.toSet());
        Map<Long, CategoryEntity> categoryMap = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(CategoryEntity::getId, c -> c));

        Set<Long> sellerIds = products.stream().map(ProductEntity::getSellerId).collect(Collectors.toSet());
        Map<Long, IdentityCatalogFacade.SellerPublicSummary> sellerMap =
                identityCatalogFacade.getSellerSummaries(sellerIds);

        Set<Long> productIds = products.stream().map(ProductEntity::getId).collect(Collectors.toSet());
        List<ProductMediaEntity> allMedia = productMediaRepository.findByProductIdInOrderByDisplayOrderAscIdAsc(productIds);
        Map<Long, String> thumbnailMap = allMedia.stream()
                .filter(m -> "IMAGE".equalsIgnoreCase(m.getMediaType()))
                .collect(Collectors.toMap(
                        ProductMediaEntity::getProductId,
                        ProductMediaEntity::getMediaUrl,
                        (first, replacement) -> first
                ));

        List<CatalogDtos.ProductSummaryResponse> items = products.stream().map(p -> {
            IdentityCatalogFacade.SellerPublicSummary seller = sellerMap.getOrDefault(
                    p.getSellerId(),
                    new IdentityCatalogFacade.SellerPublicSummary(p.getSellerId(), "Người bán", "Người bán")
            );
            String thumbnail = thumbnailMap.get(p.getId());
            return CatalogDtos.ProductSummaryResponse.from(p, CategoryRepository.orderedForProduct(p, categoryMap), seller, thumbnail);
        }).toList();

        return new CatalogDtos.PageResponse<>(
                items,
                productPage.getNumber(),
                productPage.getSize(),
                productPage.getTotalElements(),
                productPage.getTotalPages(),
                productPage.hasNext()
        );
    }

    public CatalogDtos.ProductDetailResponse getProductDetail(long productId) {
        ProductEntity product = productRepository.findByIdAndStatusAndDeletedAtIsNull(productId, ProductEntity.STATUS_ACTIVE)
                .orElseThrow(ProductNotFoundException::new);

        List<CategoryEntity> categories = categoryRepository.findForProduct(product);

        IdentityCatalogFacade.SellerPublicSummary seller = identityCatalogFacade.getSellerSummary(product.getSellerId())
                .orElse(new IdentityCatalogFacade.SellerPublicSummary(product.getSellerId(), "Người bán", "Người bán"));

        List<ProductMediaEntity> mediaList =
                productMediaRepository.findByProductIdOrderByDisplayOrderAscIdAsc(productId);

        return CatalogDtos.ProductDetailResponse.from(product, categories, seller, mediaList);
    }
}
