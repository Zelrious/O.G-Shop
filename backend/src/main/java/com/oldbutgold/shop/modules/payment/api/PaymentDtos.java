package com.oldbutgold.shop.modules.payment.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class PaymentDtos {
    private PaymentDtos() {}

    public record CreateVnPaymentRequest(
        @NotNull(message = "Mã đơn hàng không được để trống.")
        Long orderId
    ) {}

    public record PaymentUrlResponse(
        String paymentUrl
    ) {}

    public record VnPayIpnResponse(
        @JsonProperty("RspCode") String rspCode,
        @JsonProperty("Message") String message
    ) {
        public static VnPayIpnResponse success() {
            return new VnPayIpnResponse("00", "Confirm Success");
        }
        public static VnPayIpnResponse duplicateAcknowledged() {
            return new VnPayIpnResponse("02", "Order already confirmed");
        }
        public static VnPayIpnResponse invalidAmount() {
            return new VnPayIpnResponse("04", "Invalid Amount");
        }
        public static VnPayIpnResponse invalidChecksum() {
            return new VnPayIpnResponse("97", "Invalid Checksum");
        }
        public static VnPayIpnResponse orderNotFound() {
            return new VnPayIpnResponse("01", "Order not found");
        }
        public static VnPayIpnResponse unknownError() {
            return new VnPayIpnResponse("99", "Unknown error");
        }
    }

    public record VnPayVerifyResponse(
        String status, // SUCCESS, PENDING_CONFIRMATION, UNDER_REVIEW, FAILED, INVALID_SIGNATURE, NOT_FOUND, AMOUNT_MISMATCH
        Long orderId,
        String txnRef,
        String message
    ) {}

    public record AdminReviewDecisionRequest(
        @NotNull(message = "Mã đơn hàng không được để trống.")
        Long orderId,
        @NotBlank(message = "Quyết định thẩm định không được để trống.")
        String decision,
        @NotBlank(message = "Lý do thẩm định không được để trống.")
        String reasonNote,
        String vnpVerificationRef
    ) {}

    public record AdminReviewDecisionResponse(
        Long orderId,
        Long paymentId,
        String orderStatus,
        String paymentStatus,
        String decision,
        String message
    ) {}
}
