package com.oldbutgold.shop.modules.commerce.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic, canonical client-input fingerprint for Checkout idempotency (PO-DEC-06 & D-08).
 * Represents client-supplied logical checkout intent, strictly excluding server-calculated
 * financial outputs (prices, discounts, fees, totals).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientCheckoutFingerprint(
        long buyerId,
        long productId,
        int quantity,
        Long addressId,
        NormalizedAddress newAddress,
        Long offerId,
        String voucherCode,
        long coinsToRedeem
) {
    private static final ObjectMapper CANONICAL_MAPPER = JsonMapper.builder()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .build();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NormalizedAddress(
            String recipientName,
            String phoneNumber,
            String province,
            String district,
            String ward,
            String detailAddress,
            Integer ghnProvinceId,
            Integer ghnDistrictId,
            String ghnWardCode
    ) {
        public static NormalizedAddress of(CheckoutDtos.CreateAddressRequest raw) {
            if (raw == null) {
                return null;
            }
            String recipient = trimToNull(raw.recipientName());
            String phone = trimToNull(raw.phoneNumber());
            String province = trimToNull(raw.province());
            String district = trimToNull(raw.district());
            String ward = trimToNull(raw.ward());
            String detail = trimToNull(raw.detailAddress());
            Integer ghnProv = raw.ghnProvinceId() != null && raw.ghnProvinceId() > 0 ? raw.ghnProvinceId() : null;
            Integer ghnDist = raw.ghnDistrictId() != null && raw.ghnDistrictId() > 0 ? raw.ghnDistrictId() : null;
            String ghnWard = trimToNull(raw.ghnWardCode());

            if (recipient == null && phone == null && province == null && district == null && ward == null && detail == null) {
                return null;
            }
            return new NormalizedAddress(recipient, phone, province, district, ward, detail, ghnProv, ghnDist, ghnWard);
        }
    }

    public static ClientCheckoutFingerprint of(long buyerId, CheckoutDtos.BuyNowRequest request) {
        long productId = request.productId() != null ? request.productId() : 0L;
        int quantity = 1; // Buy Now is strictly single-item purchase
        Long addressId = request.addressId() != null && request.addressId() > 0 ? request.addressId() : null;
        NormalizedAddress newAddress = NormalizedAddress.of(request.newAddress());
        Long offerId = request.offerId() != null && request.offerId() > 0 ? request.offerId() : null;
        String voucherCode = normalizeVoucherCode(request.voucherCode());
        long coins = request.coinsToRedeem() != null && request.coinsToRedeem() > 0 ? request.coinsToRedeem() : 0L;

        return new ClientCheckoutFingerprint(
                buyerId,
                productId,
                quantity,
                addressId,
                newAddress,
                offerId,
                voucherCode,
                coins
        );
    }

    public String canonicalJson() {
        try {
            return CANONICAL_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize canonical client checkout fingerprint", e);
        }
    }

    public String sha256Hex() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalJson().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Compares this client fingerprint against reconstructed order attributes from existing database rows.
     * Returns true if all client-controlled inputs match identically; false if any field conflicts.
     */
    public boolean matchesReconstructedOrder(Map<String, Object> orderData) {
        if (orderData == null || orderData.isEmpty()) {
            return false;
        }

        Long existingBuyerId = orderData.get("buyer_id") != null ? ((Number) orderData.get("buyer_id")).longValue() : null;
        if (!Objects.equals(this.buyerId, existingBuyerId)) {
            return false;
        }

        Long existingProductId = orderData.get("product_id") != null ? ((Number) orderData.get("product_id")).longValue() : null;
        if (!Objects.equals(this.productId, existingProductId)) {
            return false;
        }

        Integer existingQuantity = orderData.get("quantity") != null ? ((Number) orderData.get("quantity")).intValue() : 1;
        if (this.quantity != existingQuantity) {
            return false;
        }

        Long existingOfferId = orderData.get("accepted_offer_id") != null ? ((Number) orderData.get("accepted_offer_id")).longValue() : null;
        if (!Objects.equals(this.offerId, existingOfferId)) {
            return false;
        }

        // Coins comparison: points_discount_amount in orders (1 coin = 1 VND per PO-DEC-01)
        BigDecimal existingPoints = (BigDecimal) orderData.get("points_discount_amount");
        long actualCoins = existingPoints != null ? existingPoints.longValue() : 0L;
        if (this.coinsToRedeem != actualCoins) {
            return false;
        }

        // Voucher comparison: voucher_code_snapshot in order_vouchers
        String existingVoucher = normalizeVoucherCode((String) orderData.get("voucher_code_snapshot"));
        if (!Objects.equals(this.voucherCode, existingVoucher)) {
            return false;
        }

        // Address comparison
        Long existingAddressId = orderData.get("source_address_id") != null ? ((Number) orderData.get("source_address_id")).longValue() : null;
        if (this.addressId != null) {
            if (!Objects.equals(this.addressId, existingAddressId)) {
                return false;
            }
        } else if (this.newAddress != null) {
            // Reconstructed shipping fields from orders table
            String recipient = trimToNull((String) orderData.get("shipping_recipient_name"));
            String phone = trimToNull((String) orderData.get("shipping_phone_number"));
            String province = trimToNull((String) orderData.get("shipping_province"));
            String district = trimToNull((String) orderData.get("shipping_district"));
            String ward = trimToNull((String) orderData.get("shipping_ward"));
            String detail = trimToNull((String) orderData.get("shipping_detail_address"));
            Integer ghnProv = orderData.get("shipping_ghn_province_id") != null ? ((Number) orderData.get("shipping_ghn_province_id")).intValue() : null;
            Integer ghnDist = orderData.get("shipping_ghn_district_id") != null ? ((Number) orderData.get("shipping_ghn_district_id")).intValue() : null;
            String ghnWard = trimToNull((String) orderData.get("shipping_ghn_ward_code"));

            if (!Objects.equals(this.newAddress.recipientName(), recipient)
                    || !Objects.equals(this.newAddress.phoneNumber(), phone)
                    || !equalsIgnoreCase(this.newAddress.province(), province)
                    || !equalsIgnoreCase(this.newAddress.district(), district)
                    || !equalsIgnoreCase(this.newAddress.ward(), ward)
                    || !equalsIgnoreCase(this.newAddress.detailAddress(), detail)
                    || !Objects.equals(this.newAddress.ghnProvinceId(), ghnProv)
                    || !Objects.equals(this.newAddress.ghnDistrictId(), ghnDist)
                    || !Objects.equals(this.newAddress.ghnWardCode(), ghnWard)) {
                return false;
            }
        }

        return true;
    }

    private static String trimToNull(String str) {
        if (str == null) return null;
        String trimmed = str.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeVoucherCode(String code) {
        String trimmed = trimToNull(code);
        return trimmed == null ? null : trimmed.toUpperCase();
    }

    private static boolean equalsIgnoreCase(String s1, String s2) {
        if (s1 == null && s2 == null) return true;
        if (s1 == null || s2 == null) return false;
        return s1.trim().equalsIgnoreCase(s2.trim());
    }
}

