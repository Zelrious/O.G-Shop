package com.oldbutgold.shop.modules.catalog.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface CatalogCommerceFacade {
    Optional<CommerceProductSummary> getActiveProduct(long productId);

    CommerceProductDetail getProductForCheckoutLock(long productId);

    void reserveProduct(long productId, long orderId, Instant reservedUntil, Instant now);

    void releaseProductReservation(long productId, long orderId, Instant now);

    void markProductSold(long productId, long orderId, Instant now);

    record CommerceProductSummary(
            long productId,
            long sellerId,
            String title,
            BigDecimal listedPrice,
            String condition,
            String status,
            String thumbnailUrl,
            boolean requiresBuyerEkyc
    ) {}

    record CommerceProductDetail(
            long productId,
            long sellerId,
            String title,
            BigDecimal listedPrice,
            String condition,
            String status,
            String thumbnailUrl,
            boolean requiresBuyerEkyc,
            long version
    ) {}
}
