package com.oldbutgold.shop.modules.payment.application;

import com.oldbutgold.shop.shared.config.VnPayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VnPayServiceTest {

    private VnPayService service;

    @BeforeEach
    void setUp() {
        // Khởi tạo đối tượng cấu hình giả lập để test
        VnPayProperties properties = new VnPayProperties(
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "OHS7QVO0",
                "DQDOLAPXJMYBHGFVAXENPIZYXBBCCIWO",
                "http://localhost:8080/api/v1/payments/vnpay-ipn",
                "http://localhost:5173/payment/vnpay-return"
        );
        service = new VnPayService(properties);
    }

    @Test
    void createPaymentUrlGeneratesValidUrlWithCorrectParams() {
        String paymentUrl = service.createPaymentUrl("ORDER_123", 100_000L, "Thanh toan test", "127.0.0.1");

        // Kiểm tra xem URL sinh ra có đúng cấu trúc VNPAY yêu cầu không
        assertThat(paymentUrl).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(paymentUrl).contains("vnp_TmnCode=OHS7QVO0");
        assertThat(paymentUrl).contains("vnp_Amount=10000000"); // 100.000 x 100 = 10.000.000
        assertThat(paymentUrl).contains("vnp_TxnRef=ORDER_123");
        assertThat(paymentUrl).contains("vnp_SecureHash=");
    }

    @Test
    void createPaymentUrlRejectsNonPositiveAmount() {
        // Kiểm tra số tiền <= 0 phải bị chặn lại
        assertThatThrownBy(() -> service.createPaymentUrl("ORDER_123", 0L, "Test", "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifyCallbackReturnsTrueForValidSignature() {
        // 1. Giả lập 1 URL thanh toán hợp lệ được sinh ra
        String paymentUrl = service.createPaymentUrl("ORDER_999", 50_000L, "Nap tien", "127.0.0.1");

        // 2. Tách query string thành Map để mô phỏng dữ liệu VNPAY gửi về
        URI uri = URI.create(paymentUrl);
        Map<String, String> params = new HashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=");
            params.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }

        // 3. Kiểm tra chữ ký hợp lệ
        boolean isValid = service.verifyCallback(params);
        assertThat(isValid).isTrue();
    }

    @Test
    void verifyCallbackReturnsFalseWhenDataIsTampered() {
        // 1. Giả lập 1 URL thanh toán hợp lệ
        String paymentUrl = service.createPaymentUrl("ORDER_999", 50_000L, "Nap tien", "127.0.0.1");

        URI uri = URI.create(paymentUrl);
        Map<String, String> params = new HashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=");
            params.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }

        // 2. Kẻ gian cố tình sửa số tiền từ 50.000 (gửi 5.000.000) thành 100đ (gửi 10.000)
        params.put("vnp_Amount", "10000");

        // 3. Hệ thống phải phát hiện ra chữ ký bị sai và trả về false
        boolean isValid = service.verifyCallback(params);
        assertThat(isValid).isFalse();
    }
}
