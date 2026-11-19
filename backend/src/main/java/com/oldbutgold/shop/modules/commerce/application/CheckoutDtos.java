package com.oldbutgold.shop.modules.commerce.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CheckoutDtos {
    private CheckoutDtos() {}

    public record AddressSummaryResponse(
            Long addressId,
            String recipientName,
            String phoneNumber,
            String province,
            String district,
            String ward,
            String detailAddress,
            Integer ghnProvinceId,
            Integer ghnDistrictId,
            String ghnWardCode,
            boolean isDefault
    ) {
        public AddressSummaryResponse(Long addressId, String recipientName, String phoneNumber,
                                      String province, String district, String ward,
                                      String detailAddress, boolean isDefault) {
            this(addressId, recipientName, phoneNumber, province, district, ward, detailAddress, null, null, null, isDefault);
        }

        public boolean hasValidGhnCodes() {
            return ghnDistrictId != null && ghnWardCode != null && !ghnWardCode.isBlank();
        }
    }

    public record CreateAddressRequest(
            @NotBlank(message = "Tên người nhận không được để trống")
            String recipientName,
            @NotBlank(message = "Số điện thoại không được để trống")
            String phoneNumber,
            @NotBlank(message = "Tỉnh/Thành phố không được để trống")
            String province,
            @NotBlank(message = "Quận/Huyện không được để trống")
            String district,
            @NotBlank(message = "Phường/Xã không được để trống")
            String ward,
            @NotBlank(message = "Địa chỉ chi tiết không được để trống")
            String detailAddress,
            Integer ghnProvinceId,
            Integer ghnDistrictId,
            String ghnWardCode,
            boolean isDefault
    ) {
        public CreateAddressRequest(String recipientName, String phoneNumber, String province,
                                    String district, String ward, String detailAddress, boolean isDefault) {
            this(recipientName, phoneNumber, province, district, ward, detailAddress, null, null, null, isDefault);
        }
    }

    public record VoucherSummaryResponse(
            Long voucherId,
            String code,
            String title,
            String voucherType,
            String discountType,
            BigDecimal discountValue,
            BigDecimal maxDiscountAmount,
            BigDecimal minOrderAmount,
            String sponsorType
    ) {}

    public record CheckoutPreviewResponse(
            Long productId,
            String productTitle,
            String thumbnailUrl,
            Long sellerId,
            String sellerName,
            BigDecimal listedPrice,
            BigDecimal shippingFee,
            BigDecimal buyerSystemFee,
            BigDecimal voucherDiscount,
            BigDecimal shippingDiscount,
            BigDecimal totalAmount,
            List<AddressSummaryResponse> addresses,
            AddressSummaryResponse selectedAddress,
            List<VoucherSummaryResponse> availableVouchers,
            VoucherSummaryResponse appliedVoucher,
            BigDecimal agreedPrice,
            Long acceptedOfferId,
            Long eligibleOfferId,
            BigDecimal eligibleOfferPrice,
            String voucherRejectionReason,
            Boolean requiresBuyerEkyc,
            Long buyerCoinBalance,
            Long maxRedeemableCoins,
            BigDecimal coinDiscount
    ) {
        public CheckoutPreviewResponse(
                Long productId, String productTitle, String thumbnailUrl,
                Long sellerId, String sellerName, BigDecimal listedPrice,
                BigDecimal shippingFee, BigDecimal buyerSystemFee, BigDecimal voucherDiscount,
                BigDecimal shippingDiscount, BigDecimal totalAmount, List<AddressSummaryResponse> addresses,
                AddressSummaryResponse selectedAddress, List<VoucherSummaryResponse> availableVouchers,
                VoucherSummaryResponse appliedVoucher, BigDecimal agreedPrice, Long acceptedOfferId
        ) {
            this(productId, productTitle, thumbnailUrl, sellerId, sellerName, listedPrice, shippingFee,
                 buyerSystemFee, voucherDiscount, shippingDiscount, totalAmount, addresses, selectedAddress,
                 availableVouchers, appliedVoucher, agreedPrice, acceptedOfferId, null, null, null, false, 0L, 0L, BigDecimal.ZERO);
        }

        public CheckoutPreviewResponse(
                Long productId, String productTitle, String thumbnailUrl,
                Long sellerId, String sellerName, BigDecimal listedPrice,
                BigDecimal shippingFee, BigDecimal buyerSystemFee, BigDecimal voucherDiscount,
                BigDecimal shippingDiscount, BigDecimal totalAmount, List<AddressSummaryResponse> addresses,
                AddressSummaryResponse selectedAddress, List<VoucherSummaryResponse> availableVouchers,
                VoucherSummaryResponse appliedVoucher, BigDecimal agreedPrice, Long acceptedOfferId,
                Long eligibleOfferId, BigDecimal eligibleOfferPrice, String voucherRejectionReason
        ) {
            this(productId, productTitle, thumbnailUrl, sellerId, sellerName, listedPrice, shippingFee,
                 buyerSystemFee, voucherDiscount, shippingDiscount, totalAmount, addresses, selectedAddress,
                 availableVouchers, appliedVoucher, agreedPrice, acceptedOfferId, eligibleOfferId,
                 eligibleOfferPrice, voucherRejectionReason, false, 0L, 0L, BigDecimal.ZERO);
        }
    }

    public record BuyNowRequest(
            @NotNull(message = "Mã sản phẩm là bắt buộc")
            Long productId,
            Long addressId,
            CreateAddressRequest newAddress,
            String voucherCode,
            Long offerId,
            Long coinsToRedeem,
            @NotBlank(message = "Mã thao tác (commandKey) là bắt buộc.")
            @jakarta.validation.constraints.Size(max = 150, message = "Mã thao tác (commandKey) không được vượt quá 150 ký tự.")
            String commandKey,
            java.util.UUID quickAuthResultId
    ) {
        public BuyNowRequest(Long productId, Long addressId, CreateAddressRequest newAddress, String voucherCode) {
            this(productId, addressId, newAddress, voucherCode, null, 0L, null, null);
        }

        public BuyNowRequest(Long productId, Long addressId, CreateAddressRequest newAddress, String voucherCode, Long offerId) {
            this(productId, addressId, newAddress, voucherCode, offerId, 0L, null, null);
        }

        public BuyNowRequest(Long productId, Long addressId, CreateAddressRequest newAddress, String voucherCode, Long offerId, Long coinsToRedeem) {
            this(productId, addressId, newAddress, voucherCode, offerId, coinsToRedeem, null, null);
        }

        public BuyNowRequest(Long productId, Long addressId, CreateAddressRequest newAddress, String voucherCode, String commandKey) {
            this(productId, addressId, newAddress, voucherCode, null, 0L, commandKey, null);
        }

        public BuyNowRequest(Long productId, Long addressId, CreateAddressRequest newAddress, String voucherCode, Long offerId, Long coinsToRedeem, String commandKey) {
            this(productId, addressId, newAddress, voucherCode, offerId, coinsToRedeem, commandKey, null);
        }
    }

    public record BuyNowExecutionResult(
            OrderCreatedResponse response,
            boolean replayed
    ) {}

    public record OrderCreatedResponse(
            Long orderId,
            String checkoutGroupId,
            Long productId,
            String productTitle,
            String status,
            BigDecimal subtotal,
            BigDecimal shippingFee,
            BigDecimal buyerSystemFee,
            BigDecimal voucherDiscount,
            BigDecimal shippingDiscount,
            BigDecimal pointsDiscountAmount,
            BigDecimal totalAmount,
            String recipientName,
            String phoneNumber,
            String fullAddress,
            Instant paymentDueAt,
            Instant createdAt,
            List<OrderCreatedResponse> orders
    ) {
        public OrderCreatedResponse(
                Long orderId, String checkoutGroupId, Long productId, String productTitle,
                String status, BigDecimal subtotal, BigDecimal shippingFee, BigDecimal buyerSystemFee,
                BigDecimal voucherDiscount, BigDecimal shippingDiscount, BigDecimal pointsDiscountAmount,
                BigDecimal totalAmount, String recipientName, String phoneNumber, String fullAddress,
                Instant paymentDueAt, Instant createdAt
        ) {
            this(orderId, checkoutGroupId, productId, productTitle, status, subtotal, shippingFee,
                 buyerSystemFee, voucherDiscount, shippingDiscount, pointsDiscountAmount, totalAmount,
                 recipientName, phoneNumber, fullAddress, paymentDueAt, createdAt, List.of());
        }

        public OrderCreatedResponse(
                Long orderId, String checkoutGroupId, Long productId, String productTitle,
                String status, BigDecimal subtotal, BigDecimal shippingFee, BigDecimal buyerSystemFee,
                BigDecimal voucherDiscount, BigDecimal shippingDiscount, BigDecimal totalAmount,
                String recipientName, String phoneNumber, String fullAddress,
                Instant paymentDueAt, Instant createdAt
        ) {
            this(orderId, checkoutGroupId, productId, productTitle, status, subtotal, shippingFee,
                 buyerSystemFee, voucherDiscount, shippingDiscount, BigDecimal.ZERO, totalAmount,
                 recipientName, phoneNumber, fullAddress, paymentDueAt, createdAt, List.of());
        }
    }
}
