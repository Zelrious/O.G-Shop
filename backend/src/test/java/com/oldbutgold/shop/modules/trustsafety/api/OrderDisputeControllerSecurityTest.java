package com.oldbutgold.shop.modules.trustsafety.api;

import com.oldbutgold.shop.modules.commerce.application.FulfillmentSimulationGuard;
import com.oldbutgold.shop.modules.trustsafety.api.DisputeDtos.DisputeDetailDto;
import com.oldbutgold.shop.modules.trustsafety.application.BuyerDisputeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("OrderDisputeController Security Matrix: return-delivered-simulate Admin-only & Fail-closed")
class OrderDisputeControllerSecurityTest {

    private static DisputeDetailDto sampleDeliveredDto(Long orderId) {
        Instant now = Instant.parse("2026-11-26T10:00:00Z");
        return new DisputeDetailDto(
                10L, orderId, "ORD-" + orderId,
                201L, "Buyer", "buyer@test.local",
                301L, "Seller", "seller@test.local",
                "Sản phẩm", new BigDecimal("200000"),
                "DAMAGED", "Mô tả",
                List.of(), null, null, List.of(),
                now.plusSeconds(3600), "RETURN_APPROVED", "RETURN_REFUND",
                "OK", new BigDecimal("200000"), 0, 1L, now, now, now,
                "GHN-RET-1", "GHN", "RETURN_DELIVERED", now, now,
                "Seller", "0900000000", "HCM"
        );
    }

    @Nested
    @ActiveProfiles("test")
    @SpringBootTest(properties = {"app.environment=DEV", "app.ghn.simulation-enabled=true"})
    @AutoConfigureMockMvc
    class SimulationEnabledMatrix {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private BuyerDisputeService buyerDisputeService;

        @Test
        @DisplayName("1-3 & 5: Buyer -> 403, Seller -> 403, KTV -> 403, Admin -> 200")
        void roleMatrixWhenSimulationEnabled() throws Exception {
            long orderId = 501L;

            mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/dispute/return-delivered-simulate", orderId)
                            .with(jwt().jwt(t -> t.subject("201")).authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/dispute/return-delivered-simulate", orderId)
                            .with(jwt().jwt(t -> t.subject("301")).authorities(new SimpleGrantedAuthority("ROLE_SELLER"))))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/dispute/return-delivered-simulate", orderId)
                            .with(jwt().jwt(t -> t.subject("401")).authorities(new SimpleGrantedAuthority("ROLE_KTV"))))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(buyerDisputeService);

            when(buyerDisputeService.confirmReturnDelivered(901L, orderId, true))
                    .thenReturn(sampleDeliveredDto(orderId));

            mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/dispute/return-delivered-simulate", orderId)
                            .with(jwt().jwt(t -> t.subject("901")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.returnShipmentStatus").value("RETURN_DELIVERED"));

            verify(buyerDisputeService).confirmReturnDelivered(901L, orderId, true);
        }
    }

    @Nested
    @ActiveProfiles("test")
    @SpringBootTest(properties = {"app.environment=DEV", "app.ghn.simulation-enabled=false"})
    @AutoConfigureMockMvc
    class SimulationDisabledInProdMatrix {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private BuyerDisputeService buyerDisputeService;

        @Test
        @DisplayName("4a: Admin is rejected with 403 over HTTP when simulation flag is disabled")
        void adminRejectedWhenDisabledOverHttp() throws Exception {
            mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/dispute/return-delivered-simulate", 501L)
                            .with(jwt().jwt(t -> t.subject("901")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(buyerDisputeService);
        }
    }

    @Test
    @DisplayName("4b/4c: Controller fails closed with 403 when guard is absent, disabled, or in PROD")
    void controllerFailsClosedWhenGuardMissingOrProd() {
        BuyerDisputeService service = mock(BuyerDisputeService.class);
        OrderDisputeController controller = new OrderDisputeController(service);

        assertThatThrownBy(() -> controller.simulateReturnDelivered(null, null, 501L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("FORBIDDEN_SIMULATION_IN_PRODUCTION");

        // Fails closed when guard is DEV + disabled
        OrderDisputeController disabledController = new OrderDisputeController(
                service, new FulfillmentSimulationGuard("DEV", false));
        assertThatThrownBy(() -> disabledController.simulateReturnDelivered(null, null, 501L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("FORBIDDEN_SIMULATION_IN_PRODUCTION");

        // Fails closed when environment is PROD even if flag is true
        OrderDisputeController prodController = new OrderDisputeController(
                service, new FulfillmentSimulationGuard("PROD", true));
        assertThatThrownBy(() -> prodController.simulateReturnDelivered(null, null, 501L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("FORBIDDEN_SIMULATION_IN_PRODUCTION");
    }
}

