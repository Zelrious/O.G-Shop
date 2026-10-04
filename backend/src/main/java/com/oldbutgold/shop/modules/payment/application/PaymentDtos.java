package com.oldbutgold.shop.modules.payment.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public class PaymentDtos {

    public record PaymentInfoResponse(
            Long orderId,
            Long paymentId,
            String productTitle,
            String productThumbnail,
            BigDecimal totalAmount,
            String currency,
            String orderStatus,
            String paymentStatus,
            String paymentMethod,
            String transactionCode,
            Instant paymentDueAt,
            long remainingSeconds,
            String qrCodeMockUrl,
            String bankName,
            String accountNumber,
            String accountName,
            String transferContent,
            String recipientName,
            String phoneNumber,
            String fullAddress,
            BigDecimal subtotal,
            BigDecimal shippingFee,
            BigDecimal voucherDiscount,
            Long sellerId
    ) {}

    public record ProcessMockPaymentRequest(
            @NotNull(message = "Mã đơn hàng không được để trống")
            Long orderId,

            @NotBlank(message = "Phương thức thanh toán không được để trống")
            String paymentMethod,

            boolean simulateSuccess
    ) {}

    public record PaymentProcessResultResponse(
            Long paymentId,
            Long orderId,
            String orderStatus,
            String paymentStatus,
            String transactionCode,
            String message
    ) {}
}
