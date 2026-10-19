package com.oldbutgold.shop.modules.fulfillment.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.IncidentResolutionAction;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.ResolveIncidentRequest;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.ResolveIncidentResponse;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.ShipmentIncidentResponse;
import com.oldbutgold.shop.modules.fulfillment.application.FulfillmentStateService;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentRepository;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
class AdminFulfillmentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FulfillmentStateService fulfillmentStateService;

    @MockitoBean
    private ShipmentRepository shipmentRepository;

    @Test
    @DisplayName("1. Chưa xác thực gọi /api/v1/admin/fulfillment/incidents -> 401 Unauthorized")
    void testGetIncidentsUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/fulfillment/incidents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("2. User không có role ADMIN (e.g. BUYER/SELLER) gọi incidents -> 403 Forbidden")
    void testGetIncidentsNonAdminReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/fulfillment/incidents")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("3. KTV không có quyền truy cập admin fulfillment -> 403 Forbidden")
    void testGetIncidentsKtvReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/fulfillment/incidents")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_KTV"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("4. ADMIN gọi incidents -> 200 OK và trả về danh sách sự cố")
    void testGetIncidentsAdminReturns200() throws Exception {
        when(fulfillmentStateService.getIncidentQueue()).thenReturn(List.of(
                new ShipmentIncidentResponse(
                        1L, 100L, "GHN_TRACK_100", "OG-CLIENT-100", "GHN",
                        "PENDING", "damage", "Hàng bị hư hỏng", true, 3,
                        Instant.now(), Instant.now()
                )
        ));

        mockMvc.perform(get("/api/v1/admin/fulfillment/incidents")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(100L))
                .andExpect(jsonPath("$[0].rawGhnStatus").value("damage"))
                .andExpect(jsonPath("$[0].adminReviewRequired").value(true));
    }

    @Test
    @DisplayName("5. ADMIN resolve-incident -> 200 OK và gọi FulfillmentStateService")
    void testResolveIncidentAdminSuccess() throws Exception {
        ResolveIncidentRequest request = new ResolveIncidentRequest(
                IncidentResolutionAction.DISMISS_AND_RESUME,
                "Đã kiểm tra xác nhận an toàn"
        );

        when(fulfillmentStateService.resolveIncident(eq(100L), eq(IncidentResolutionAction.DISMISS_AND_RESUME), any(), any()))
                .thenReturn(new ResolveIncidentResponse(
                        100L, "GHN_TRACK_100", "PENDING", "damage", false,
                        "Notes", "Đã gỡ cờ sự cố"
                ));

        mockMvc.perform(post("/api/v1/admin/fulfillment/orders/100/resolve-incident")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                                .jwt(j -> j.claim("email", "admin@ogshop.vn")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(100L))
                .andExpect(jsonPath("$.adminReviewRequired").value(false))
                .andExpect(jsonPath("$.message").value("Đã gỡ cờ sự cố"));

        verify(fulfillmentStateService).resolveIncident(
                eq(100L),
                eq(IncidentResolutionAction.DISMISS_AND_RESUME),
                eq("Đã kiểm tra xác nhận an toàn"),
                eq("admin@ogshop.vn")
        );
    }
}
