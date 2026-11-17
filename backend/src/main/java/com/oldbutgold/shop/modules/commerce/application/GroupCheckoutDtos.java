package com.oldbutgold.shop.modules.commerce.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class GroupCheckoutDtos {

    public record ShippingAddressDto(
            @NotBlank String recipientName,
            @NotBlank String phoneNumber,
            @NotBlank String province,
            @NotBlank String district,
            @NotBlank String ward,
            @NotBlank String detailAddress
    ) {}

    public record GroupCheckoutItemRequest(
            @NotNull Long productId,
            @Min(1) int quantity,
            Long acceptedOfferId
    ) {}

    public record GroupCheckoutRequest(
            @NotBlank String commandKey,
            @NotNull @Valid ShippingAddressDto shippingAddress,
            @NotBlank String deliveryMethod,
            String carrier,
            @NotBlank String paymentMethod,
            String voucherCode,
            Long coinsRequested,
            @NotEmpty List<@Valid GroupCheckoutItemRequest> items
    ) {}

    public record OrderItemResponse(
            Long orderItemId,
            Long productId,
            String productTitle,
            int quantity,
            BigDecimal listedPrice,
            BigDecimal agreedPrice,
            BigDecimal voucherAllocation,
            BigDecimal pointsAllocation,
            BigDecimal buyerLineTotal,
            BigDecimal sellerLineProceeds,
            Long productRevisionId,
            Boolean returnAllowed
    ) {}

    public record OrderResponse(
            Long orderId,
            Long sellerId,
            Boolean returnAllowed,
            String deliveryMethod,
            String carrier,
            LocalDate handoverDay,
            BigDecimal subtotal,
            BigDecimal shippingFee,
            BigDecimal voucherDiscountAmount,
            BigDecimal pointsDiscountAmount,
            BigDecimal totalAmount,
            BigDecimal sellerProceeds,
            String status,
            List<OrderItemResponse> items
    ) {}

    public record GroupCheckoutResponse(
            UUID groupId,
            Long buyerId,
            String commandKey,
            String state,
            String deliveryMethod,
            String carrier,
            String paymentMethod,
            BigDecimal expectedTotal,
            BigDecimal groupSubtotal,
            BigDecimal groupShippingFee,
            BigDecimal groupVoucherDiscount,
            BigDecimal groupPointsDiscount,
            Long coinsUsed,
            Instant createdAt,
            Instant paymentDueAt,
            Long rewardPolicyId,
            List<OrderResponse> orders
    ) {}

    public record GroupCheckoutExecutionResult(
            GroupCheckoutResponse response,
            boolean isIdempotentReplay
    ) {}
}

