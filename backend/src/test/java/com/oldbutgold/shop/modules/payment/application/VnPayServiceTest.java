package com.oldbutgold.shop.modules.payment.application;

import com.oldbutgold.shop.shared.config.VnPayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VnPayServiceTest {

    private VnPayService service;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        VnPayProperties properties = new VnPayProperties(
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "OHS7QVO0",
                "local-test-only-hmac-key-please-do-not-use-live",
                "http://localhost:8080/api/v1/payments/vnpay-ipn",
                "http://localhost:5173/payment/vnpay-return"
        );
        // Fixed instant: 2026-10-05T08:00:00Z -> In Asia/Ho_Chi_Minh (+07:00), it is 2026-10-05 15:00:00
        fixedClock = Clock.fixed(Instant.parse("2026-10-05T08:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        service = new VnPayService(properties, fixedClock);
    }

    @Test
    void createPaymentUrlGeneratesValidUrlWithCorrectParamsAndDates() {
        String paymentUrl = service.createPaymentUrl("ORDER_123", 100_000L, "Thanh toan test", "127.0.0.1");

        // Kiểm tra xem URL sinh ra có đúng cấu trúc VNPAY yêu cầu không
        assertThat(paymentUrl).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(paymentUrl).contains("vnp_TmnCode=OHS7QVO0");
        assertThat(paymentUrl).contains("vnp_Amount=10000000"); // 100.000 x 100 = 10.000.000
        assertThat(paymentUrl).contains("vnp_TxnRef=ORDER_123");
        assertThat(paymentUrl).contains("vnp_CreateDate=20261005150000");
        assertThat(paymentUrl).contains("vnp_ExpireDate=20261005151500");
        assertThat(paymentUrl).contains("vnp_SecureHash=");
    }

    @Test
    void createPaymentUrlRejectsNonPositiveAmount() {
        assertThatThrownBy(() -> service.createPaymentUrl("ORDER_123", 0L, "Test", "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifyCallbackReturnsTrueForValidSignature() {
        String paymentUrl = service.createPaymentUrl("ORDER_999", 50_000L, "Nap tien", "127.0.0.1");

        URI uri = URI.create(paymentUrl);
        Map<String, String> params = new HashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=");
            params.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }

        boolean isValid = service.verifyCallback(params);
        assertThat(isValid).isTrue();
    }

    @Test
    void verifyCallbackReturnsFalseWhenDataIsTampered() {
        String paymentUrl = service.createPaymentUrl("ORDER_999", 50_000L, "Nap tien", "127.0.0.1");

        URI uri = URI.create(paymentUrl);
        Map<String, String> params = new HashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=");
            params.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }

        params.put("vnp_Amount", "10000");

        boolean isValid = service.verifyCallback(params);
        assertThat(isValid).isFalse();
    }
}
