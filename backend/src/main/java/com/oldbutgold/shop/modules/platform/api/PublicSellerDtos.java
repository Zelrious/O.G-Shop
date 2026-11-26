package com.oldbutgold.shop.modules.platform.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Isolated public read-only DTOs for Public Seller Profile.
 * Strictly whitelisted fields only — never expose email, phone, address, bank, CCCD,
 * penalty history, buyerId, reviewerId, or orderId.
 */
public final class PublicSellerDtos {
    private PublicSellerDtos() {}

    public record PublicSellerStats(
            Double averageRating,
            long totalReviews,
            long activeProductsCount,
            long completedTransactionsCount,
            Map<Integer, Long> ratingDistribution
    ) {}

    public record PublicSellerProfileResponse(
            long sellerId,
            String displayName,
            String avatarUrl,
            boolean ekycVerified,
            Instant joinedAt,
            PublicSellerStats stats
    ) {}

    public record PublicSellerProductSummary(
            long productId,
            String title,
            BigDecimal listedPrice,
            String currency,
            String condition,
            String location,
            String thumbnailUrl,
            String categoryName,
            int quantityAvailable,
            boolean requiresBuyerEkyc,
            Instant createdAt
    ) {}

    public record PublicSellerReviewItem(
            long reviewId,
            String reviewerDisplayName,
            int rating,
            String comment,
            Instant createdAt,
            Long productId,
            String productTitle,
            String productThumbnailUrl
    ) {
        public PublicSellerReviewItem(
                long reviewId,
                String reviewerDisplayName,
                int rating,
                String comment,
                Instant createdAt
        ) {
            this(reviewId, reviewerDisplayName, rating, comment, createdAt, null, null, null);
        }
    }

    public record PublicPageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}
}

