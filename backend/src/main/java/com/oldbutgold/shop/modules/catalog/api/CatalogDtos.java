package com.oldbutgold.shop.modules.catalog.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.CategoryEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductMediaEntity;
import com.oldbutgold.shop.modules.identity.application.IdentityCatalogFacade;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CatalogDtos {
    private CatalogDtos() {
    }

    public record CategoryResponse(
            long categoryId,
            String categoryName,
            String slug,
            String description,
            int displayOrder
    ) {
        public static CategoryResponse from(CategoryEntity entity) {
            return new CategoryResponse(
                    entity.getId(),
                    entity.getCategoryName(),
                    entity.getSlug(),
                    entity.getDescription(),
                    entity.getDisplayOrder()
            );
        }
    }

    public record SellerSummary(
            long sellerId,
            String displayName,
            String trustLabel
    ) {
        public static SellerSummary from(IdentityCatalogFacade.SellerPublicSummary s) {
            return new SellerSummary(s.sellerId(), s.displayName(), s.trustLabel());
        }
    }

    public record ProductMediaResponse(
            long mediaId,
            String mediaType,
            String mediaUrl,
            int displayOrder
    ) {
        public static ProductMediaResponse from(ProductMediaEntity entity) {
            return new ProductMediaResponse(
                    entity.getId(),
                    entity.getMediaType(),
                    entity.getMediaUrl(),
                    entity.getDisplayOrder()
            );
        }
    }

    public record ProductSummaryResponse(
            long productId,
            String title,
            BigDecimal listedPrice,
            String currency,
            String condition,
            String location,
            String thumbnailUrl,
            CategoryResponse category,
            SellerSummary seller,
            Instant createdAt
    ) {
        public static ProductSummaryResponse from(ProductEntity product,
                                                  CategoryEntity category,
                                                  IdentityCatalogFacade.SellerPublicSummary seller,
                                                  String thumbnailUrl) {
            return new ProductSummaryResponse(
                    product.getId(),
                    product.getTitle(),
                    product.getListedPrice(),
                    product.getCurrency(),
                    product.getCondition(),
                    product.getLocation(),
                    thumbnailUrl,
                    CategoryResponse.from(category),
                    SellerSummary.from(seller),
                    product.getCreatedAt()
            );
        }
    }

    public record ProductDetailResponse(
            long productId,
            String title,
            String description,
            BigDecimal listedPrice,
            String currency,
            String condition,
            String usageDuration,
            String defects,
            String repairHistory,
            String includedAccessories,
            String location,
            String thumbnailUrl,
            CategoryResponse category,
            SellerSummary seller,
            List<ProductMediaResponse> media,
            Instant createdAt
    ) {
        public static ProductDetailResponse from(ProductEntity product,
                                                 CategoryEntity category,
                                                 IdentityCatalogFacade.SellerPublicSummary seller,
                                                 List<ProductMediaEntity> mediaList) {
            String thumbnail = mediaList.stream()
                    .filter(m -> "IMAGE".equalsIgnoreCase(m.getMediaType()))
                    .map(ProductMediaEntity::getMediaUrl)
                    .findFirst()
                    .orElse(null);

            return new ProductDetailResponse(
                    product.getId(),
                    product.getTitle(),
                    product.getDescription(),
                    product.getListedPrice(),
                    product.getCurrency(),
                    product.getCondition(),
                    product.getUsageDuration(),
                    product.getDefects(),
                    product.getRepairHistory(),
                    product.getIncludedAccessories(),
                    product.getLocation(),
                    thumbnail,
                    CategoryResponse.from(category),
                    SellerSummary.from(seller),
                    mediaList.stream().map(ProductMediaResponse::from).toList(),
                    product.getCreatedAt()
            );
        }
    }

    public record SellerProductSummaryResponse(
            long productId,
            String title,
            BigDecimal listedPrice,
            String currency,
            String condition,
            String location,
            String status,
            String thumbnailUrl,
            CategoryResponse category,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static SellerProductSummaryResponse from(ProductEntity product,
                                                        CategoryEntity category,
                                                        String thumbnailUrl) {
            return new SellerProductSummaryResponse(
                    product.getId(),
                    product.getTitle(),
                    product.getListedPrice(),
                    product.getCurrency(),
                    product.getCondition(),
                    product.getLocation(),
                    product.getStatus(),
                    thumbnailUrl,
                    CategoryResponse.from(category),
                    product.getVersion(),
                    product.getCreatedAt(),
                    product.getUpdatedAt()
            );
        }
    }

    public record SellerProductDetailResponse(
            long productId,
            String title,
            String description,
            BigDecimal listedPrice,
            String currency,
            String condition,
            String usageDuration,
            String defects,
            String repairHistory,
            String includedAccessories,
            String location,
            String status,
            String thumbnailUrl,
            CategoryResponse category,
            List<ProductMediaResponse> media,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static SellerProductDetailResponse from(ProductEntity product,
                                                       CategoryEntity category,
                                                       List<ProductMediaEntity> mediaList) {
            String thumbnail = mediaList.stream()
                    .filter(m -> "IMAGE".equalsIgnoreCase(m.getMediaType()))
                    .map(ProductMediaEntity::getMediaUrl)
                    .findFirst()
                    .orElse(null);

            return new SellerProductDetailResponse(
                    product.getId(),
                    product.getTitle(),
                    product.getDescription(),
                    product.getListedPrice(),
                    product.getCurrency(),
                    product.getCondition(),
                    product.getUsageDuration(),
                    product.getDefects(),
                    product.getRepairHistory(),
                    product.getIncludedAccessories(),
                    product.getLocation(),
                    product.getStatus(),
                    thumbnail,
                    CategoryResponse.from(category),
                    mediaList.stream().map(ProductMediaResponse::from).toList(),
                    product.getVersion(),
                    product.getCreatedAt(),
                    product.getUpdatedAt()
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record CreateOrUpdateProductRequest(
            @NotNull(message = "Danh mục không được để trống")
            Long categoryId,

            @NotBlank(message = "Tiêu đề không được để trống")
            @Size(max = 200, message = "Tiêu đề không được vượt quá 200 ký tự")
            String title,

            @NotBlank(message = "Mô tả sản phẩm không được để trống")
            @Size(max = 5000, message = "Mô tả sản phẩm không được vượt quá 5000 ký tự")
            String description,

            @NotNull(message = "Giá niêm yết không được để trống")
            @DecimalMin(value = "1.00", message = "Giá niêm yết phải lớn hơn 0")
            BigDecimal listedPrice,

            @NotBlank(message = "Tình trạng sản phẩm không được để trống")
            @Pattern(
                    regexp = "LIKE_NEW|GOOD|FAIR|POOR|FOR_PARTS",
                    message = "Tình trạng phải là: LIKE_NEW, GOOD, FAIR, POOR, FOR_PARTS"
            )
            String condition,

            @Size(max = 100, message = "Thời gian sử dụng không được vượt quá 100 ký tự")
            String usageDuration,

            @Size(max = 2000, message = "Mô tả lỗi không được vượt quá 2000 ký tự")
            String defects,

            @Size(max = 2000, message = "Lịch sử sửa chữa không được vượt quá 2000 ký tự")
            String repairHistory,

            @Size(max = 2000, message = "Phụ kiện đi kèm không được vượt quá 2000 ký tự")
            String includedAccessories,

            @Size(max = 255, message = "Địa điểm không được vượt quá 255 ký tự")
            String location,

            Long version
    ) {
    }

    public record PageResponse<T>(
            List<T> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
    }
}
