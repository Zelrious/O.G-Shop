package com.oldbutgold.shop.modules.fulfillment.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.CreateShipmentRequest;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.PrintWaybillResponse;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.ShipmentResponse;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.TrackingResponse;
import com.oldbutgold.shop.modules.fulfillment.application.FulfillmentService;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnMasterDataDtos.DistrictDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnMasterDataDtos.ProvinceDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnMasterDataDtos.WardDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
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
class FulfillmentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FulfillmentService fulfillmentService;

    @Test
    @DisplayName("1. Chưa xác thực không được gọi create-shipment -> 401 Unauthorized")
    void testCreateShipmentUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/fulfillment/orders/1/create-shipment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("2. Chưa xác thực không được gọi print-waybill -> 401 Unauthorized")
    void testPrintWaybillUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/fulfillment/orders/1/print-waybill"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("3. Chưa xác thực không được gọi tracking -> 401 Unauthorized")
    void testTrackingUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/fulfillment/orders/1/tracking"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("4. Master-data provinces là public permitAll -> 200 OK")
    void testMasterDataProvincesPublic() throws Exception {
        when(fulfillmentService.getProvinces()).thenReturn(List.of(
                new ProvinceDto(201, "Hà Nội", "HN")
        ));

        mockMvc.perform(get("/api/v1/shipping/master-data/provinces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ProvinceID").value(201))
                .andExpect(jsonPath("$[0].ProvinceName").value("Hà Nội"));
    }

    @Test
    @DisplayName("5. Master-data districts là public permitAll -> 200 OK")
    void testMasterDataDistrictsPublic() throws Exception {
        when(fulfillmentService.getDistricts(201)).thenReturn(List.of(
                new DistrictDto(1442, 201, "Quận 1", "Q1")
        ));

        mockMvc.perform(get("/api/v1/shipping/master-data/districts?provinceId=201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].DistrictID").value(1442))
                .andExpect(jsonPath("$[0].DistrictName").value("Quận 1"));
    }

    @Test
    @DisplayName("6. Master-data wards là public permitAll -> 200 OK")
    void testMasterDataWardsPublic() throws Exception {
        when(fulfillmentService.getWards(1442)).thenReturn(List.of(
                new WardDto("20101", 1442, "Phường Bến Nghé")
        ));

        mockMvc.perform(get("/api/v1/shipping/master-data/wards?districtId=1442"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].WardCode").value("20101"))
                .andExpect(jsonPath("$[0].WardName").value("Phường Bến Nghé"));
    }

    @Test
    @DisplayName("7. Seller gọi create-shipment có JWT hợp lệ -> 200 OK")
    void testCreateShipmentAuthenticatedReturns200() throws Exception {
        CreateShipmentRequest request = new CreateShipmentRequest(
                "Shop A", "0912345678", "123 Street", "Ward", "District", "Province", 1442,
                1444, "20311", 1000, 20, 15, 10, "Note", null
        );

        ShipmentResponse response = new ShipmentResponse(
                500L, "GHN-ORDER-123", "OG-DEV-500-ABCDEF", "GHN", "PENDING",
                BigDecimal.valueOf(20900), Instant.now(), "ready_to_pick", Instant.now()
        );
        when(fulfillmentService.createShipment(eq(100L), eq(500L), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/fulfillment/orders/500/create-shipment")
                        .with(jwt().jwt(j -> j.subject("100").claim("roles", List.of("SELLER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(500))
                .andExpect(jsonPath("$.trackingNumber").value("GHN-ORDER-123"))
                .andExpect(jsonPath("$.clientOrderCode").value("OG-DEV-500-ABCDEF"));

        verify(fulfillmentService).createShipment(eq(100L), eq(500L), any());
    }

    @Test
    @DisplayName("8. In phiếu A5 có JWT hợp lệ -> 200 OK")
    void testPrintWaybillAuthenticatedReturns200() throws Exception {
        PrintWaybillResponse response = new PrintWaybillResponse(
                500L, "GHN-ORDER-123", "https://dev-online-gateway.ghn.vn/a5/public-api/printA5?token=abc"
        );
        when(fulfillmentService.printWaybill(eq(100L), eq(500L))).thenReturn(response);

        mockMvc.perform(get("/api/v1/fulfillment/orders/500/print-waybill")
                        .with(jwt().jwt(j -> j.subject("100"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(500))
                .andExpect(jsonPath("$.trackingNumber").value("GHN-ORDER-123"))
                .andExpect(jsonPath("$.printUrl").value("https://dev-online-gateway.ghn.vn/a5/public-api/printA5?token=abc"));
    }

    @Test
    @DisplayName("9. Tra cứu tracking có JWT hợp lệ -> 200 OK")
    void testTrackingAuthenticatedReturns200() throws Exception {
        TrackingResponse response = new TrackingResponse(
                500L, "GHN-ORDER-123", "GHN", "PENDING", "ready_to_pick",
                BigDecimal.valueOf(20900), Instant.now(), "Shop Addr", "Buyer Addr"
        );
        when(fulfillmentService.getTracking(eq(200L), eq(500L))).thenReturn(response);

        mockMvc.perform(get("/api/v1/fulfillment/orders/500/tracking")
                        .with(jwt().jwt(j -> j.subject("200"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(500))
                .andExpect(jsonPath("$.trackingNumber").value("GHN-ORDER-123"));
    }

    @Test
    @DisplayName("10. Người dùng không sở hữu Order bị từ chối -> 403 Forbidden")
    void testAccessDeniedReturns403() throws Exception {
        when(fulfillmentService.createShipment(eq(999L), eq(500L), any()))
                .thenThrow(new AccessDeniedException("Bạn không có quyền tạo vận đơn cho đơn hàng này."));

        mockMvc.perform(post("/api/v1/fulfillment/orders/500/create-shipment")
                        .with(jwt().jwt(j -> j.subject("999"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("11. Khi tính năng tạo vận đơn bị khóa ở server -> 409 Conflict")
    void testFeatureLockedReturns409() throws Exception {
        when(fulfillmentService.createShipment(eq(100L), eq(500L), any()))
                .thenThrow(new com.oldbutgold.shop.modules.fulfillment.application.FulfillmentFeatureLockedException("Chức năng tạo vận đơn trực tiếp qua API đang tạm khóa (ADDR-01)."));

        mockMvc.perform(post("/api/v1/fulfillment/orders/500/create-shipment")
                        .with(jwt().jwt(j -> j.subject("100"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FULFILLMENT_FEATURE_LOCKED"));
    }

    @Test
    @DisplayName("12. Webhook public endpoint không yêu cầu JWT auth (chỉ bảo vệ qua secret header)")
    void testWebhookEndpointPermitAllWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/v1/fulfillment/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
