package com.oldbutgold.shop.modules.commerce.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClientCheckoutFingerprintTest - Canonicalization and Payload Comparison Unit Tests")
class ClientCheckoutFingerprintTest {

    @Test
    @DisplayName("Fingerprint produces identical SHA-256 for semantically identical client payloads")
    void testCanonicalizationIdenticalSha256() {
        CheckoutDtos.BuyNowRequest req1 = new CheckoutDtos.BuyNowRequest(
                1001L, 501L, null, "DISCOUNT10", 88L, 50000L, "CMD-1"
        );
        // req2 has lowercase voucher with whitespace, null address defaults
        CheckoutDtos.BuyNowRequest req2 = new CheckoutDtos.BuyNowRequest(
                1001L, 501L, null, "  discount10  ", 88L, 50000L, "CMD-1"
        );

        ClientCheckoutFingerprint fp1 = ClientCheckoutFingerprint.of(300L, req1);
        ClientCheckoutFingerprint fp2 = ClientCheckoutFingerprint.of(300L, req2);

        assertThat(fp1.sha256Hex()).isEqualTo(fp2.sha256Hex());
        assertThat(fp1.voucherCode()).isEqualTo("DISCOUNT10");
        assertThat(fp2.voucherCode()).isEqualTo("DISCOUNT10");
    }

    @Test
    @DisplayName("Fingerprint normalizes null and zero coinsToRedeem as identical")
    void testCoinNormalization() {
        CheckoutDtos.BuyNowRequest reqNullCoins = new CheckoutDtos.BuyNowRequest(
                1001L, 501L, null, null, null, null, "CMD-1"
        );
        CheckoutDtos.BuyNowRequest reqZeroCoins = new CheckoutDtos.BuyNowRequest(
                1001L, 501L, null, null, null, 0L, "CMD-1"
        );
        CheckoutDtos.BuyNowRequest reqNegCoins = new CheckoutDtos.BuyNowRequest(
                1001L, 501L, null, null, null, -500L, "CMD-1"
        );

        ClientCheckoutFingerprint fpNull = ClientCheckoutFingerprint.of(300L, reqNullCoins);
        ClientCheckoutFingerprint fpZero = ClientCheckoutFingerprint.of(300L, reqZeroCoins);
        ClientCheckoutFingerprint fpNeg = ClientCheckoutFingerprint.of(300L, reqNegCoins);

        assertThat(fpNull.coinsToRedeem()).isEqualTo(0L);
        assertThat(fpZero.coinsToRedeem()).isEqualTo(0L);
        assertThat(fpNeg.coinsToRedeem()).isEqualTo(0L);
        assertThat(fpNull.sha256Hex()).isEqualTo(fpZero.sha256Hex());
        assertThat(fpNull.sha256Hex()).isEqualTo(fpNeg.sha256Hex());
    }

    @Test
    @DisplayName("Fingerprint normalizes null and blank voucherCode as identical")
    void testVoucherCodeNormalization() {
        CheckoutDtos.BuyNowRequest reqNullVoucher = new CheckoutDtos.BuyNowRequest(
                1001L, 501L, null, null, null, 0L, "CMD-1"
        );
        CheckoutDtos.BuyNowRequest reqBlankVoucher = new CheckoutDtos.BuyNowRequest(
                1001L, 501L, null, "   ", null, 0L, "CMD-1"
        );

        ClientCheckoutFingerprint fpNull = ClientCheckoutFingerprint.of(300L, reqNullVoucher);
        ClientCheckoutFingerprint fpBlank = ClientCheckoutFingerprint.of(300L, reqBlankVoucher);

        assertThat(fpNull.voucherCode()).isNull();
        assertThat(fpBlank.voucherCode()).isNull();
        assertThat(fpNull.sha256Hex()).isEqualTo(fpBlank.sha256Hex());
    }

    @Test
    @DisplayName("Fingerprint differs when client-controlled inputs differ")
    void testDifferingClientInputsProduceDifferentHashes() {
        ClientCheckoutFingerprint fpBase = ClientCheckoutFingerprint.of(300L,
                new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 0L, "CMD-1"));

        // Different product
        ClientCheckoutFingerprint fpDiffProduct = ClientCheckoutFingerprint.of(300L,
                new CheckoutDtos.BuyNowRequest(1002L, 501L, null, null, null, 0L, "CMD-1"));
        assertThat(fpBase.sha256Hex()).isNotEqualTo(fpDiffProduct.sha256Hex());

        // Different address
        ClientCheckoutFingerprint fpDiffAddress = ClientCheckoutFingerprint.of(300L,
                new CheckoutDtos.BuyNowRequest(1001L, 502L, null, null, null, 0L, "CMD-1"));
        assertThat(fpBase.sha256Hex()).isNotEqualTo(fpDiffAddress.sha256Hex());

        // Different offer
        ClientCheckoutFingerprint fpDiffOffer = ClientCheckoutFingerprint.of(300L,
                new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, 99L, 0L, "CMD-1"));
        assertThat(fpBase.sha256Hex()).isNotEqualTo(fpDiffOffer.sha256Hex());

        // Different coins
        ClientCheckoutFingerprint fpDiffCoins = ClientCheckoutFingerprint.of(300L,
                new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 10000L, "CMD-1"));
        assertThat(fpBase.sha256Hex()).isNotEqualTo(fpDiffCoins.sha256Hex());

        // Different voucher
        ClientCheckoutFingerprint fpDiffVoucher = ClientCheckoutFingerprint.of(300L,
                new CheckoutDtos.BuyNowRequest(1001L, 501L, null, "SUMMER", null, 0L, "CMD-1"));
        assertThat(fpBase.sha256Hex()).isNotEqualTo(fpDiffVoucher.sha256Hex());

        // Different buyer
        ClientCheckoutFingerprint fpDiffBuyer = ClientCheckoutFingerprint.of(999L,
                new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 0L, "CMD-1"));
        assertThat(fpBase.sha256Hex()).isNotEqualTo(fpDiffBuyer.sha256Hex());
    }

    @Test
    @DisplayName("matchesReconstructedOrder accurately detects matches and conflicts")
    void testMatchesReconstructedOrder() {
        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(
                1001L, 501L, null, "PROMO10", 88L, 50000L, "CMD-1"
        );
        ClientCheckoutFingerprint fp = ClientCheckoutFingerprint.of(300L, request);

        Map<String, Object> matchingOrder = Map.of(
                "buyer_id", 300L,
                "product_id", 1001L,
                "quantity", 1,
                "source_address_id", 501L,
                "accepted_offer_id", 88L,
                "voucher_code_snapshot", "PROMO10",
                "points_discount_amount", new BigDecimal("50000"),
                "subtotal", new BigDecimal("500000") // Server-derived, ignored by fingerprint
        );

        assertThat(fp.matchesReconstructedOrder(matchingOrder)).isTrue();

        // Mismatched coins
        Map<String, Object> diffCoins = new java.util.HashMap<>(matchingOrder);
        diffCoins.put("points_discount_amount", new BigDecimal("20000"));
        assertThat(fp.matchesReconstructedOrder(diffCoins)).isFalse();

        // Mismatched offer
        Map<String, Object> diffOffer = new java.util.HashMap<>(matchingOrder);
        diffOffer.put("accepted_offer_id", 99L);
        assertThat(fp.matchesReconstructedOrder(diffOffer)).isFalse();

        // Mismatched voucher
        Map<String, Object> diffVoucher = new java.util.HashMap<>(matchingOrder);
        diffVoucher.put("voucher_code_snapshot", "OTHER20");
        assertThat(fp.matchesReconstructedOrder(diffVoucher)).isFalse();

        // Server-derived price change (e.g. subtotal or fees) must NOT cause false conflict
        Map<String, Object> diffSubtotal = new java.util.HashMap<>(matchingOrder);
        diffSubtotal.put("subtotal", new BigDecimal("9999999"));
        assertThat(fp.matchesReconstructedOrder(diffSubtotal)).isTrue();
    }
}

