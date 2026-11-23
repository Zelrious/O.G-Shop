package com.oldbutgold.shop.modules.payment.api;

import com.oldbutgold.shop.modules.payment.application.RefundSettlementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AdminPaymentControllerSecurityTest - UC83 Settlement Confirmation & Listing RBAC")
class AdminPaymentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefundSettlementService refundSettlementService;

    @Test
    @DisplayName("Unauthenticated requests to settlement endpoints return 401")
    void settlements_unauthenticated_returns401() throws Exception {
        UUID opId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/admin/payments/orders/100/settlements"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/admin/payments/settlements/" + opId + "/confirm"))
                .andExpect(status().isUnauthorized());

        verify(refundSettlementService, never()).listSettlementOperationsByOrderId(any());
        verify(refundSettlementService, never()).confirmRefundSettlement(any());
    }

    @Test
    @DisplayName("BUYER and SELLER are rejected with 403 on settlement endpoints")
    void settlements_nonAdminOrKtv_returns403() throws Exception {
        UUID opId = UUID.randomUUID();

        for (String role : List.of("ROLE_BUYER", "ROLE_SELLER")) {
            mockMvc.perform(get("/api/v1/admin/payments/orders/100/settlements")
                            .with(jwt().jwt(j -> j.subject("101"))
                                    .authorities(new SimpleGrantedAuthority(role))))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/api/v1/admin/payments/settlements/" + opId + "/confirm")
                            .with(jwt().jwt(j -> j.subject("101"))
                                    .authorities(new SimpleGrantedAuthority(role))))
                    .andExpect(status().isForbidden());
        }

        verify(refundSettlementService, never()).listSettlementOperationsByOrderId(any());
        verify(refundSettlementService, never()).confirmRefundSettlement(any());
    }

    @Test
    @DisplayName("ADMIN and KTV can list settlements and confirm settlement operations")
    void settlements_adminAndKtv_succeed() throws Exception {
        UUID opId = UUID.randomUUID();
        UUID compId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-23T04:00:00Z");

        var summary = new RefundSettlementService.SettlementOperationSummary(
                opId, compId, "GOODS_CASH", "REFUND_BUYER", new BigDecimal("150000"),
                "RETURN", 501L, 10L, "REQUESTED", now, null, null, null, "CMD:1"
        );
        var confirmResult = new RefundSettlementService.RefundSettlementResult(
                opId, "CONFIRMED", 5000L, now.plusSeconds(86400), "Settlement operation confirmed successfully"
        );

        when(refundSettlementService.listSettlementOperationsByOrderId(100L)).thenReturn(List.of(summary));
        when(refundSettlementService.confirmRefundSettlement(opId)).thenReturn(confirmResult);

        mockMvc.perform(get("/api/v1/admin/payments/orders/100/settlements")
                        .with(jwt().jwt(j -> j.subject("300"))
                                .authorities(new SimpleGrantedAuthority("ROLE_KTV"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].operationId").value(opId.toString()))
                .andExpect(jsonPath("$[0].state").value("REQUESTED"));

        mockMvc.perform(post("/api/v1/admin/payments/settlements/" + opId + "/confirm")
                        .with(jwt().jwt(j -> j.subject("99"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value(opId.toString()))
                .andExpect(jsonPath("$.state").value("CONFIRMED"))
                .andExpect(jsonPath("$.coinsCredited").value(5000));

        verify(refundSettlementService).listSettlementOperationsByOrderId(100L);
        verify(refundSettlementService).confirmRefundSettlement(opId);
    }
}
