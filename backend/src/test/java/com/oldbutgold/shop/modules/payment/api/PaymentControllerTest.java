package com.oldbutgold.shop.modules.payment.api;

import com.oldbutgold.shop.modules.payment.application.VnPayPaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean VnPayPaymentService payments;
    @Test void createUrlUsesJwtAndServerOrderId() throws Exception {
        when(payments.createUrl(eq(7L), eq(42L), any())).thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        mvc.perform(post("/api/v1/payments/vnpay-url").with(jwt().jwt(t -> t.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":42}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentUrl").exists());
        verify(payments).createUrl(eq(7L), eq(42L), any());
    }
    @Test void rejectsMissingOrderIdAndAnonymousUser() throws Exception {
        mvc.perform(post("/api/v1/payments/vnpay-url").with(jwt().jwt(t -> t.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":42,\"amountVnd\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/payments/vnpay-url").with(jwt().jwt(t -> t.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderRef\":\"arbitrary\",\"amountVnd\":150000}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/payments/vnpay-url").contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":42}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(payments);
    }
    @Test void ipnIsPublicAndReturnsApplicationAcknowledgement() throws Exception {
        when(payments.handleIpn(anyMap())).thenReturn(PaymentDtos.VnPayIpnResponse.invalidChecksum());
        mvc.perform(get("/api/v1/payments/vnpay-ipn")).andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("97"));
    }
    @Test void doesNotAcknowledgePersistenceFailureAsSuccess() throws Exception {
        when(payments.handleIpn(anyMap())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("Fixture DB failure"));
        mvc.perform(get("/api/v1/payments/vnpay-ipn")).andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("99"));
    }
    @Test void expiredOrderReturnsStateConflict() throws Exception {
        when(payments.createUrl(eq(7L), eq(42L), any())).thenThrow(
                new com.oldbutgold.shop.modules.payment.application.PaymentStateConflictException("Đơn đã hết hạn"));
        mvc.perform(post("/api/v1/payments/vnpay-url").with(jwt().jwt(t -> t.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":42}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_STATE_CONFLICT"));
    }
}
