package com.oldbutgold.shop.modules.payment.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class PaymentDtos {
    private PaymentDtos() {

    }

    public record CreateVnPaymentRequest(
        @NotNull @Min(1) Long orderId
    ) {
    }

    public record PaymentUrlResponse(
        String paymentUrl
    ) {

    }

    public record VnPayIpnResponse(
        @JsonProperty("RspCode") String rspCode,
        @JsonProperty("Message") String message
    ) {
         public static VnPayIpnResponse success() {
            return new VnPayIpnResponse("00", "Confirm Success");
        }
        public static VnPayIpnResponse invalidChecksum() {
            return new VnPayIpnResponse("97", "Invalid Checksum");
        }
        public static VnPayIpnResponse orderNotFound() {
            return new VnPayIpnResponse("01", "Order not found");
        }
    }
}
