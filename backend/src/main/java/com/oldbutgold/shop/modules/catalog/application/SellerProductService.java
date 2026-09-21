package com.oldbutgold.shop.modules.catalog.application;

import com.oldbutgold.shop.modules.catalog.api.CatalogDtos;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.CategoryEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.CategoryRepository;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductMediaEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductMediaRepository;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductRepository;
import com.oldbutgold.shop.modules.identity.application.IdentityCatalogFacade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class SellerProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMediaRepository productMediaRepository;
    private final IdentityCatalogFacade identityCatalogFacade;
    private final Clock clock;

    public SellerProductService(ProductRepository productRepository,
                                CategoryRepository categoryRepository,
                                ProductMediaRepository productMediaRepository,
                                IdentityCatalogFacade identityCatalogFacade,
                                Clock clock) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productMediaRepository = productMediaRepository;
        this.identityCatalogFacade = identityCatalogFacade;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CatalogDtos.PageResponse<CatalogDtos.SellerProductSummaryResponse> getSellerProducts(
            long sellerId, int page, int size
    ) {
        ensureSellerActive(sellerId);

        int clampedPage = Math.max(page, 0);
        int clampedSize = Math.min(Math.max(size, 1), 48);
        Pageable pageable = PageRequest.of(clampedPage, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<ProductEntity> productPage = productRepository.findBySellerIdAndDeletedAtIsNull(sellerId, pageable);
        List<ProductEntity> products = productPage.getContent();

        if (products.isEmpty()) {
            return new CatalogDtos.PageResponse<>(
                    List.of(), productPage.getNumber(), productPage.getSize(),
                    productPage.getTotalElements(), productPage.getTotalPages(), productPage.hasNext()
            );
        }

        Set<Long> categoryIds = products.stream().map(ProductEntity::getCategoryId).collect(Collectors.toSet());
        Map<Long, CategoryEntity> categoryMap = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(CategoryEntity::getId, c -> c));

        Set<Long> productIds = products.stream().map(ProductEntity::getId).collect(Collectors.toSet());
        List<ProductMediaEntity> allMedia = productMediaRepository.findByProductIdInOrderByDisplayOrderAscIdAsc(productIds);
        Map<Long, String> thumbnailMap = allMedia.stream()
                .filter(m -> "IMAGE".equalsIgnoreCase(m.getMediaType()))
                .collect(Collectors.toMap(
                        ProductMediaEntity::getProductId,
                        ProductMediaEntity::getMediaUrl,
                        (first, replacement) -> first
                ));

        List<CatalogDtos.SellerProductSummaryResponse> items = products.stream().map(p -> {
            CategoryEntity cat = categoryMap.get(p.getCategoryId());
            String thumb = thumbnailMap.get(p.getId());
            return CatalogDtos.SellerProductSummaryResponse.from(p, cat, thumb);
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

    @Transactional(readOnly = true)
    public CatalogDtos.SellerProductDetailResponse getSellerProduct(long sellerId, long productId) {
        ensureSellerActive(sellerId);
        ProductEntity product = productRepository.findByIdAndSellerIdAndDeletedAtIsNull(productId, sellerId)
                .orElseThrow(ProductNotFoundException::new);

        CategoryEntity category = categoryRepository.findById(product.getCategoryId())
                .orElseThrow(() -> new CategoryNotFoundException("Danh mục của sản phẩm không tồn tại."));

        List<ProductMediaEntity> mediaList = productMediaRepository.findByProductIdOrderByDisplayOrderAscIdAsc(productId);
        return CatalogDtos.SellerProductDetailResponse.from(product, category, mediaList);
    }

    public CatalogDtos.SellerProductDetailResponse createProduct(
            long sellerId, CatalogDtos.CreateOrUpdateProductRequest request
    ) {
        ensureSellerActive(sellerId);
        CategoryEntity category = categoryRepository.findByIdAndActiveTrue(request.categoryId())
                .orElseThrow(() -> new CategoryNotFoundException("Danh mục không tồn tại hoặc đã bị vô hiệu hóa."));

        Instant now = clock.instant();
        ProductEntity product = new ProductEntity(
                sellerId,
                category.getId(),
                request.title().trim(),
                request.description().trim(),
                request.listedPrice(),
                request.condition().trim(),
                request.usageDuration() != null ? request.usageDuration().trim() : null,
                request.defects() != null ? request.defects().trim() : null,
                request.repairHistory() != null ? request.repairHistory().trim() : null,
                request.includedAccessories() != null ? request.includedAccessories().trim() : null,
                request.location() != null ? request.location().trim() : null,
                now
        );

        ProductEntity saved = productRepository.save(product);
        return CatalogDtos.SellerProductDetailResponse.from(saved, category, List.of());
    }

    public CatalogDtos.SellerProductDetailResponse updateProduct(
            long sellerId, long productId, CatalogDtos.CreateOrUpdateProductRequest request
    ) {
        ensureSellerActive(sellerId);
        ProductEntity product = productRepository.findByIdAndSellerIdAndDeletedAtIsNull(productId, sellerId)
                .orElseThrow(ProductNotFoundException::new);

        CategoryEntity category = categoryRepository.findByIdAndActiveTrue(request.categoryId())
                .orElseThrow(() -> new CategoryNotFoundException("Danh mục không tồn tại hoặc đã bị vô hiệu hóa."));

        if (request.version() != null && !request.version().equals(product.getVersion())) {
            throw new ProductVersionConflictException();
        }

        Instant now = clock.instant();
        product.updateDetails(
                category.getId(),
                request.title().trim(),
                request.description().trim(),
                request.listedPrice(),
                request.condition().trim(),
                request.usageDuration() != null ? request.usageDuration().trim() : null,
                request.defects() != null ? request.defects().trim() : null,
                request.repairHistory() != null ? request.repairHistory().trim() : null,
                request.includedAccessories() != null ? request.includedAccessories().trim() : null,
                request.location() != null ? request.location().trim() : null,
                now
        );

        ProductEntity saved = productRepository.save(product);
        List<ProductMediaEntity> mediaList = productMediaRepository.findByProductIdOrderByDisplayOrderAscIdAsc(productId);
        return CatalogDtos.SellerProductDetailResponse.from(saved, category, mediaList);
    }

    public CatalogDtos.SellerProductDetailResponse publishProduct(long sellerId, long productId) {
        ensureSellerActive(sellerId);
        ProductEntity product = productRepository.findByIdAndSellerIdAndDeletedAtIsNull(productId, sellerId)
                .orElseThrow(ProductNotFoundException::new);

        CategoryEntity category = categoryRepository.findById(product.getCategoryId())
                .orElseThrow(() -> new CategoryNotFoundException("Danh mục của sản phẩm không tồn tại."));

        product.publish(clock.instant());
        ProductEntity saved = productRepository.save(product);
        List<ProductMediaEntity> mediaList = productMediaRepository.findByProductIdOrderByDisplayOrderAscIdAsc(productId);
        return CatalogDtos.SellerProductDetailResponse.from(saved, category, mediaList);
    }

    public CatalogDtos.SellerProductDetailResponse hideProduct(long sellerId, long productId) {
        ensureSellerActive(sellerId);
        ProductEntity product = productRepository.findByIdAndSellerIdAndDeletedAtIsNull(productId, sellerId)
                .orElseThrow(ProductNotFoundException::new);

        CategoryEntity category = categoryRepository.findById(product.getCategoryId())
                .orElseThrow(() -> new CategoryNotFoundException("Danh mục của sản phẩm không tồn tại."));

        product.hide(clock.instant());
        ProductEntity saved = productRepository.save(product);
        List<ProductMediaEntity> mediaList = productMediaRepository.findByProductIdOrderByDisplayOrderAscIdAsc(productId);
        return CatalogDtos.SellerProductDetailResponse.from(saved, category, mediaList);
    }

    private void ensureSellerActive(long sellerId) {
        if (!identityCatalogFacade.isSellerActive(sellerId)) {
            throw new SellerRequiredException("Bạn cần kích hoạt quyền Người bán để quản lý tin đăng.");
        }
    }
}
