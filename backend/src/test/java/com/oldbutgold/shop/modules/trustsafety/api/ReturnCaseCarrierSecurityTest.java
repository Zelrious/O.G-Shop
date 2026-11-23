package com.oldbutgold.shop.modules.trustsafety.api;

import com.oldbutgold.shop.modules.trustsafety.application.CaseEvidenceRequestService;
import com.oldbutgold.shop.modules.trustsafety.application.KtvDisputeService;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseDtos;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseService;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.ReturnReasonCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "app.ghn.simulation-enabled=false"
})
@AutoConfigureMockMvc
@DisplayName("Security Regression Tests: ReturnCase carrier simulation & /api/v1/ktv/disputes RBAC ordering")
class ReturnCaseCarrierSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReturnCaseService returnCaseService;

    @MockitoBean
    private KtvDisputeService ktvDisputeService;

    @Test
    @DisplayName("Unauthenticated call to carrier return delivery returns 401")
    void carrierReturnDelivery_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/trustsafety/returns/100/delivery/carrier")
                        .param("shipmentId", "1")
                        .param("deliveredAt", "2026-11-18T10:00:00Z"))
                .andExpect(status().isUnauthorized());

        verify(returnCaseService, never()).confirmReturnDeliveryByCarrier(any(), any(), any());
    }

    @Test
    @DisplayName("BUYER, SELLER, and KTV are rejected with 403 on carrier return delivery simulation")
    void carrierReturnDelivery_nonAdminRoles_return403() throws Exception {
        for (String role : List.of("ROLE_BUYER", "ROLE_SELLER", "ROLE_KTV")) {
            mockMvc.perform(post("/api/v1/trustsafety/returns/100/delivery/carrier")
                            .param("shipmentId", "1")
                            .param("deliveredAt", "2026-11-18T10:00:00Z")
                            .with(jwt().jwt(j -> j.subject("101"))
                                    .authorities(new SimpleGrantedAuthority(role))))
                    .andExpect(status().isForbidden());
        }

        verify(returnCaseService, never()).confirmReturnDeliveryByCarrier(any(), any(), any());
    }

    @Test
    @DisplayName("ADMIN is blocked in PROD / simulationEnabled=false with 403 fail-closed")
    void carrierReturnDelivery_adminInProd_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/trustsafety/returns/100/delivery/carrier")
                        .param("shipmentId", "1")
                        .param("deliveredAt", "2026-11-18T10:00:00Z")
                        .with(jwt().jwt(j -> j.subject("99"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());

        verify(returnCaseService, never()).confirmReturnDeliveryByCarrier(any(), any(), any());
    }

    @Test
    @DisplayName("Direct controller PROD + simulationEnabled=true still blocks fail-closed (403)")
    void carrierReturnDelivery_prodAndEnabled_throws403() {
        ReturnCaseService svc = mock(ReturnCaseService.class);
        CaseEvidenceRequestService evSvc = mock(CaseEvidenceRequestService.class);
        ReturnCaseController prodController = new ReturnCaseController(svc, evSvc, "PROD", true);
        Instant now = Instant.parse("2026-11-18T10:00:00Z");

        assertThatThrownBy(() -> prodController.confirmReturnDeliveryByCarrier(100L, 1L, now))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).isEqualTo("FORBIDDEN_SIMULATION_IN_PRODUCTION");
                });
        verify(svc, never()).confirmReturnDeliveryByCarrier(any(), any(), any());
    }

    @Test
    @DisplayName("Direct controller DEV + simulationEnabled=true permits delivery confirmation")
    void carrierReturnDelivery_devAndEnabled_succeeds() {
        ReturnCaseService svc = mock(ReturnCaseService.class);
        CaseEvidenceRequestService evSvc = mock(CaseEvidenceRequestService.class);
        ReturnCaseController devController = new ReturnCaseController(svc, evSvc, "DEV", true);

        Instant now = Instant.parse("2026-11-18T10:00:00Z");
        ReturnCaseDtos.ReturnCaseDetailResponse stub = new ReturnCaseDtos.ReturnCaseDetailResponse(
                100L, 200L, "RETURN_CASE", "POST_RETURN",
                ReturnReasonCode.DAMAGED_OR_DEFECTIVE, "Defect", "Desc", 10L,
                now, now, now, now, now, null, List.of(), List.of()
        );
        when(svc.confirmReturnDeliveryByCarrier(100L, 1L, now)).thenReturn(stub);

        var response = devController.confirmReturnDeliveryByCarrier(100L, 1L, now);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(svc).confirmReturnDeliveryByCarrier(100L, 1L, now);
    }

    @Test
    @DisplayName("SecurityConfig ordering: ADMIN and KTV can access /api/v1/ktv/disputes/** while ADMIN is blocked from /api/v1/ktv/users")
    void securityConfigOrdering_ktvDisputesAllowsAdminAndKtv_othersRestrictToKtv() throws Exception {
        when(ktvDisputeService.searchDisputes(any(), any(), anyInt(), anyInt()))
                .thenReturn(new com.oldbutgold.shop.modules.trustsafety.api.DisputeDtos.DisputePageResponse(List.of(), 0L, 0, 10));

        mockMvc.perform(get("/api/v1/ktv/disputes")
                        .with(jwt().jwt(j -> j.subject("99"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/ktv/disputes")
                        .with(jwt().jwt(j -> j.subject("300"))
                                .authorities(new SimpleGrantedAuthority("ROLE_KTV"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/ktv/disputes")
                        .with(jwt().jwt(j -> j.subject("101"))
                                .authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ktv/users")
                        .with(jwt().jwt(j -> j.subject("99"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ReturnCaseController KTV endpoints reject non-KTV roles (BUYER, SELLER, ADMIN) with 403")
    void trustsafetyKtvEndpoints_nonKtvRoles_return403() throws Exception {
        String r1Payload = """
                {"outcome":"APPROVE_RETURN","reason":"Valid reason text","commandKey":"CMD_1"}
                """;
        String r2Payload = """
                {"outcome":"FULL_GOODS_REFUND","reason":"Valid reason text","commandKey":"CMD_2"}
                """;
        String reqEvPayload = """
                {"reason":"Need clearer unboxing photo evidence","requestedItems":["Ảnh hộp"],"commandKey":"CMD_3"}
                """;

        for (String role : List.of("ROLE_BUYER", "ROLE_SELLER", "ROLE_ADMIN")) {
            mockMvc.perform(post("/api/v1/trustsafety/returns/100/ktv/decide-round1")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(r1Payload)
                            .with(jwt().jwt(j -> j.subject("101"))
                                    .authorities(new SimpleGrantedAuthority(role))))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/api/v1/trustsafety/returns/100/ktv/decide-round2")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(r2Payload)
                            .with(jwt().jwt(j -> j.subject("101"))
                                    .authorities(new SimpleGrantedAuthority(role))))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/api/v1/trustsafety/returns/100/ktv/request-evidence")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(reqEvPayload)
                            .with(jwt().jwt(j -> j.subject("101"))
                                    .authorities(new SimpleGrantedAuthority(role))))
                    .andExpect(status().isForbidden());
        }

        verify(returnCaseService, never()).ktvDecideRound1(any(), any(), any());
        verify(returnCaseService, never()).ktvDecideRound2(any(), any(), any());
    }
}
