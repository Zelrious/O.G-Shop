package com.oldbutgold.shop.modules.payment.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.payment.application.IpnReceiptService;
import com.oldbutgold.shop.modules.payment.application.PaymentService;
import com.oldbutgold.shop.modules.payment.application.VnPayService;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentIpnRawReceiptEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private VnPayService vnPayService;

    @MockitoBean
    private IpnReceiptService ipnReceiptService;

    @Test
    @DisplayName("IPN webhook endpoint is public and returns 97 on invalid signature")
    void ipnEndpointIsPublicAndAccessibleWithoutLogin() throws Exception {
        PaymentIpnRawReceiptEntity receipt = new PaymentIpnRawReceiptEntity("q", 1, false, false, "hash", "127.0.0.1", java.time.Instant.now());
        java.lang.reflect.Field idField = PaymentIpnRawReceiptEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(receipt, 1L);
        when(ipnReceiptService.createInitialReceipt(any(), any(), any())).thenReturn(receipt);
        when(vnPayService.verifyCallback(any())).thenReturn(false);

        mockMvc.perform(get("/api/v1/payments/vnpay-ipn?vnp_TxnRef=OG_1_123456&vnp_SecureHash=invalid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("97"))
                .andExpect(jsonPath("$.Message").value("Invalid Checksum"));
    }

    @Test
    @DisplayName("Create payment URL rejects unauthenticated requests with 401")
    void createPaymentUrl_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/payments/vnpay-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\": 1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Create payment URL rejects invalid input missing orderId with 400")
    void createPaymentUrl_missingOrderId_returns400() throws Exception {
        var auth = jwt().jwt(token -> token.subject("100"));

        mockMvc.perform(post("/api/v1/payments/vnpay-url")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create payment URL succeeds for authenticated buyer with valid orderId")
    void createPaymentUrl_validRequest_returnsUrl() throws Exception {
        var auth = jwt().jwt(token -> token.subject("100"));
        when(paymentService.createVnPayUrl(eq(100L), eq(1L), any()))
                .thenReturn(new PaymentDtos.PaymentUrlResponse("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=15000000"));

        mockMvc.perform(post("/api/v1/payments/vnpay-url")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentUrl").value(org.hamcrest.Matchers.containsString("vnp_Amount=15000000")));
    }

    @Test
    @DisplayName("Verify Return URL rejects unauthenticated requests with 401")
    void verifyReturnUrl_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/payments/vnpay-verify?vnp_TxnRef=OG_1_123456"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Verify Return URL succeeds for authenticated buyer")
    void verifyReturnUrl_authenticatedBuyer_returnsDto() throws Exception {
        var auth = jwt().jwt(token -> token.subject("100"));
        when(paymentService.verifyVnPayReturn(eq(100L), any()))
                .thenReturn(new PaymentDtos.VnPayVerifyResponse("SUCCESS", 1L, "OG_1_123", "Giao dịch thanh toán thành công."));

        mockMvc.perform(get("/api/v1/payments/vnpay-verify?vnp_TxnRef=OG_1_123")
                        .with(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.txnRef").value("OG_1_123"));
    }

    @Test
    @DisplayName("Admin review decision rejects unauthenticated requests with 401")
    void adminReviewDecision_unauthenticated_returns401() throws Exception {
        PaymentDtos.AdminReviewDecisionRequest request = new PaymentDtos.AdminReviewDecisionRequest(
                1L, "APPROVED_SAFE", "Đã xác minh qua cổng VNPay", "VNP_REF_01"
        );

        mockMvc.perform(post("/api/v1/admin/payments/review-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Admin review decision rejects BUYER and SELLER roles with 403 (Test Case 31)")
    void adminReviewDecision_buyerRole_returns403() throws Exception {
        var buyerAuth = jwt().jwt(token -> token.subject("100"))
                .authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
        var sellerAuth = jwt().jwt(token -> token.subject("200"))
                .authorities(new SimpleGrantedAuthority("ROLE_SELLER"));

        PaymentDtos.AdminReviewDecisionRequest request = new PaymentDtos.AdminReviewDecisionRequest(
                1L, "APPROVED_SAFE", "Đã xác minh qua cổng VNPay", "VNP_REF_01"
        );

        mockMvc.perform(post("/api/v1/admin/payments/review-decision")
                        .with(buyerAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/payments/review-decision")
                        .with(sellerAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin review decision succeeds for ADMIN and KTV roles (Test Case 31)")
    void adminReviewDecision_adminOrKtvRole_returns200() throws Exception {
        var adminAuth = jwt().jwt(token -> token.subject("999"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        var ktvAuth = jwt().jwt(token -> token.subject("888"))
                .authorities(new SimpleGrantedAuthority("ROLE_KTV"));

        PaymentDtos.AdminReviewDecisionRequest request = new PaymentDtos.AdminReviewDecisionRequest(
                1L, "APPROVED_SAFE", "Đã xác minh qua cổng VNPay", "VNP_REF_01"
        );
        PaymentDtos.AdminReviewDecisionResponse response = new PaymentDtos.AdminReviewDecisionResponse(
                1L, 10L, "PAID_HELD", "HELD", "APPROVED_SAFE", "Thẩm định an toàn thành công."
        );

        when(paymentService.processAdminReviewDecision(any(), eq(1L), eq("APPROVED_SAFE"), any(), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/payments/review-decision")
                        .with(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVED_SAFE"))
                .andExpect(jsonPath("$.orderStatus").value("PAID_HELD"))
                .andExpect(jsonPath("$.paymentStatus").value("HELD"));

        mockMvc.perform(post("/api/v1/admin/payments/review-decision")
                        .with(ktvAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVED_SAFE"));
    }
}
