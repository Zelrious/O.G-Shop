package com.oldbutgold.shop.modules.commerce.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class OrderManagementDtos {

    public record OrderSummaryResponse(
            Long orderId,
            UUID checkoutGroupId,
            Long productId,
            String productTitle,
            String productThumbnail,
            BigDecimal unitPrice,
            short quantity,
            BigDecimal totalAmount,
            String currency,
            String status,
            String paymentStatus,
            Long partnerId,
            String partnerName,
            Instant createdAt,
            Instant paymentDueAt,
            Instant completedAt,
            Instant cancelledAt
    ) {}

    public record OrderDetailResponse(
            Long orderId,
            UUID checkoutGroupId,
            String status,
            Long buyerId,
            String buyerName,
            Long sellerId,
            String sellerName,
            Long productId,
            String productTitle,
            String productThumbnail,
            BigDecimal unitPrice,
            short quantity,
            String shippingRecipientName,
            String shippingPhoneNumber,
            String fullShippingAddress,
            BigDecimal subtotal,
            BigDecimal shippingFee,
            BigDecimal buyerSystemFee,
            BigDecimal sellerSystemFee,
            BigDecimal sellerProceeds,
            BigDecimal voucherDiscountAmount,
            BigDecimal shippingDiscountAmount,
            String appliedVoucherCode,
            BigDecimal totalAmount,
            String currency,
            Long paymentId,
            String paymentMethod,
            String paymentStatus,
            String transactionCode,
            Instant paidAt,
            Instant heldAt,
            Instant paymentDueAt,
            Instant createdAt,
            Instant completedAt,
            Instant cancelledAt,
            String cancellationReason
    ) {}

    public record CancelOrderRequest(
            String reason
    ) {}

    public record OrderActionResponse(
            Long orderId,
            String status,
            String message
    ) {}
}
