package com.oldbutgold.shop.modules.catalog.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.CategoryEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductMediaEntity;
import com.oldbutgold.shop.modules.identity.application.IdentityCatalogFacade;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
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
            boolean requiresBuyerEkyc,
            Instant createdAt,
            List<CategoryResponse> categories
    ) {
        public static ProductSummaryResponse from(ProductEntity product,
                                                  List<CategoryEntity> categories,
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
                    CategoryResponse.from(categories.getFirst()),
                    SellerSummary.from(seller),
                    product.isRequiresBuyerEkyc(),
                    product.getCreatedAt(),
                    categories.stream().map(CategoryResponse::from).toList()
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
            boolean requiresBuyerEkyc,
            Instant createdAt,
            List<CategoryResponse> categories
    ) {
        public static ProductDetailResponse from(ProductEntity product,
                                                 List<CategoryEntity> categories,
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
                    CategoryResponse.from(categories.getFirst()),
                    SellerSummary.from(seller),
                    mediaList.stream().map(ProductMediaResponse::from).toList(),
                    product.isRequiresBuyerEkyc(),
                    product.getCreatedAt(),
                    categories.stream().map(CategoryResponse::from).toList()
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
            boolean requiresBuyerEkyc,
            long version,
            Instant createdAt,
            Instant updatedAt,
            List<CategoryResponse> categories,
            String rejectionReason,
            Instant reviewedAt
    ) {
        public static SellerProductSummaryResponse from(ProductEntity product,
                                                        List<CategoryEntity> categories,
                                                        String thumbnailUrl) {
            return from(product, categories, thumbnailUrl, null, null);
        }

        public static SellerProductSummaryResponse from(ProductEntity product,
                                                        List<CategoryEntity> categories,
                                                        String thumbnailUrl,
                                                        String rejectionReason,
                                                        Instant reviewedAt) {
            return new SellerProductSummaryResponse(
                    product.getId(),
                    product.getTitle(),
                    product.getListedPrice(),
                    product.getCurrency(),
                    product.getCondition(),
                    product.getLocation(),
                    product.getStatus(),
                    thumbnailUrl,
                    CategoryResponse.from(categories.getFirst()),
                    product.isRequiresBuyerEkyc(),
                    product.getVersion(),
                    product.getCreatedAt(),
                    product.getUpdatedAt(),
                    categories.stream().map(CategoryResponse::from).toList(),
                    rejectionReason,
                    reviewedAt
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
            boolean requiresBuyerEkyc,
            long version,
            Instant createdAt,
            Instant updatedAt,
            List<CategoryResponse> categories,
            String rejectionReason,
            Instant reviewedAt
    ) {
        public static SellerProductDetailResponse from(ProductEntity product,
                                                       List<CategoryEntity> categories,
                                                       List<ProductMediaEntity> mediaList) {
            return from(product, categories, mediaList, null, null);
        }

        public static SellerProductDetailResponse from(ProductEntity product,
                                                       List<CategoryEntity> categories,
                                                       List<ProductMediaEntity> mediaList,
                                                       String rejectionReason,
                                                       Instant reviewedAt) {
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
                    CategoryResponse.from(categories.getFirst()),
                    mediaList.stream().map(ProductMediaResponse::from).toList(),
                    product.isRequiresBuyerEkyc(),
                    product.getVersion(),
                    product.getCreatedAt(),
                    product.getUpdatedAt(),
                    categories.stream().map(CategoryResponse::from).toList(),
                    rejectionReason,
                    reviewedAt
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record CreateOrUpdateProductRequest(
            @Positive(message = "Danh mục phải là ID hợp lệ")
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

            Boolean requiresBuyerEkyc,

            Long version,

            @Size(min = 1, message = "Vui lòng chọn ít nhất một danh mục")
            List<@NotNull @Positive Long> categoryIds
    ) {
        public CreateOrUpdateProductRequest(Long categoryId, String title, String description,
                BigDecimal listedPrice, String condition, String usageDuration, String defects,
                String repairHistory, String includedAccessories, String location,
                Boolean requiresBuyerEkyc, Long version) {
            this(categoryId, title, description, listedPrice, condition, usageDuration, defects,
                    repairHistory, includedAccessories, location, requiresBuyerEkyc, version, null);
        }

        @AssertTrue(message = "Vui lòng chọn ít nhất một danh mục")
        @JsonIgnore
        public boolean isCategorySelectionValid() {
            return categoryIds != null ? !categoryIds.isEmpty() : categoryId != null;
        }
    }

    public record MediaUploadResponse(
            long mediaId,
            long productId,
            String mediaType,
            String mediaUrl,
            int displayOrder,
            String thumbnailUrl,
            Integer durationSeconds,
            Long fileSizeBytes
    ) {
        public static MediaUploadResponse from(ProductMediaEntity entity) {
            return new MediaUploadResponse(
                    entity.getId(),
                    entity.getProductId(),
                    entity.getMediaType(),
                    entity.getMediaUrl(),
                    entity.getDisplayOrder(),
                    entity.getThumbnailUrl(),
                    entity.getDurationSeconds(),
                    entity.getFileSizeBytes()
            );
        }
    }

    public record ModerationProductResponse(
            long productId,
            String title,
            String description,
            BigDecimal listedPrice,
            String condition,
            String status,
            Long sellerId,
            CategoryResponse category,
            List<ProductMediaResponse> media,
            boolean requiresBuyerEkyc,
            Instant createdAt,
            List<CategoryResponse> categories,
            long version,
            Instant updatedAt
    ) {
        public ModerationProductResponse(
                long productId, String title, String description, BigDecimal listedPrice,
                String condition, String status, Long sellerId, CategoryResponse category,
                List<ProductMediaResponse> media, boolean requiresBuyerEkyc, Instant createdAt,
                List<CategoryResponse> categories
        ) {
            this(productId, title, description, listedPrice, condition, status, sellerId,
                    category, media, requiresBuyerEkyc, createdAt, categories, 0L, createdAt);
        }
    }

    public record ApproveProductRequest(
            @NotNull(message = "expectedVersion là bắt buộc")
            @Min(value = 0, message = "expectedVersion phải >= 0")
            Long expectedVersion,

            @NotBlank(message = "commandKey là bắt buộc")
            @Size(max = 100, message = "commandKey tối đa 100 ký tự")
            String commandKey
    ) {}

    public record RejectProductRequest(
            @NotBlank(message = "Lý do từ chối không được để trống")
            @Size(max = 500, message = "Lý do tối đa 500 ký tự")
            String reason,

            @NotNull(message = "expectedVersion là bắt buộc")
            @Min(value = 0, message = "expectedVersion phải >= 0")
            Long expectedVersion,

            @NotBlank(message = "commandKey là bắt buộc")
            @Size(max = 100, message = "commandKey tối đa 100 ký tự")
            String commandKey
    ) {
        public RejectProductRequest(String reason, Long expectedVersion, String commandKey) {
            this.reason = reason;
            this.expectedVersion = expectedVersion;
            this.commandKey = commandKey;
        }
    }

    public record ActionResponse(
            long productId,
            String status,
            String message
    ) {}

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
