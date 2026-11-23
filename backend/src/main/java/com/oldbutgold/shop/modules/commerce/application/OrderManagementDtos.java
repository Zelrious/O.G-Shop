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
            int quantity,
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
            int quantity,
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
            String cancellationReason,
            String trackingNumber,
            String carrier,
            Instant shippedAt,
            Instant deliveredAt
    ) {}

    public record CancelOrderRequest(
            String reason
    ) {}

    public record OrderActionResponse(
            Long orderId,
            String status,
            String message
    ) {}

    public record OrderShipmentActionResponse(
            Long orderId,
            String status,
            String trackingNumber,
            String carrier,
            String message
    ) {}

    public record ConfirmReceivedRequest(
            String videoOption
    ) {
        public static final String VIDEO_OPTION_RECORDED = "VIDEO_RECORDED";
        public static final String VIDEO_OPTION_SKIPPED_AFTER_WARNING = "SKIPPED_AFTER_WARNING";
    }

    public record EarlyCompleteRequest(
            Boolean warningConsent,
            String commandKey
    ) {}

    public record DisbursementOperationDto(
            UUID operationId,
            UUID componentId,
            String componentType,
            BigDecimal amount,
            String state,
            String commandKey
    ) {}

    public record DisbursementResponse(
            Long orderId,
            String status,
            java.util.List<DisbursementOperationDto> operations,
            BigDecimal totalDisbursedAmount,
            String message
    ) {}

    public record OverdueDeliveryBatchResult(
            int totalScanned,
            java.util.List<Long> processedOrderIds,
            java.util.List<Long> skippedOrderIds,
            java.util.List<Long> failedOrderIds
    ) {
        public OverdueDeliveryBatchResult {
            processedOrderIds = processedOrderIds != null ? java.util.List.copyOf(processedOrderIds) : java.util.List.of();
            skippedOrderIds = skippedOrderIds != null ? java.util.List.copyOf(skippedOrderIds) : java.util.List.of();
            failedOrderIds = failedOrderIds != null ? java.util.List.copyOf(failedOrderIds) : java.util.List.of();
        }
    }
}
