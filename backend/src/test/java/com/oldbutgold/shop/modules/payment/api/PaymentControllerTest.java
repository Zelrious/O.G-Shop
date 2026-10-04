package com.oldbutgold.shop.modules.payment.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser // Giả lập người dùng đã đăng nhập
    void createPaymentUrlReturnsSuccessWithValidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/payments/vnpay-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderRef": "TEST_ORDER_001",
                                  "amountVnd": 150000,
                                  "orderInfo": "Thanh toan don test"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentUrl").exists())
                .andExpect(jsonPath("$.paymentUrl").value(org.hamcrest.Matchers.containsString("vnp_Amount=15000000")));
    }

    @Test
    @WithMockUser
    void createPaymentUrlRejectsAmountBelowMinimum() throws Exception {
        // Test kiểm tra xem @Min(1000) có chặn số tiền 500đ không
        mockMvc.perform(post("/api/v1/payments/vnpay-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderRef": "TEST_ORDER_001",
                                  "amountVnd": 500,
                                  "orderInfo": "Thanh toan qua it"
                                }
                                """))
                .andExpect(status().isBadRequest()); // Phải trả về mã lỗi 400
    }

    @Test
    void ipnEndpointIsPublicAndAccessibleWithoutLogin() throws Exception {
        // Test kiểm tra xem server VNPAY gọi vào IPN không cần đăng nhập có được không
        mockMvc.perform(get("/api/v1/payments/vnpay-ipn"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("97")); // Không có chữ ký thì trả về 97 (Invalid Checksum)
    }
}
