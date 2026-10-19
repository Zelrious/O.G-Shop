package com.oldbutgold.shop.modules.fulfillment.api;

import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.CreateShipmentRequest;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.PrintWaybillResponse;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.ShipmentResponse;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.TrackingResponse;
import com.oldbutgold.shop.modules.fulfillment.application.FulfillmentService;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnMasterDataDtos.DistrictDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnMasterDataDtos.ProvinceDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnMasterDataDtos.WardDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class FulfillmentController {

    private final FulfillmentService fulfillmentService;

    public FulfillmentController(FulfillmentService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    // ========================================================================
    // Fulfillment Operations (Seller / Buyer)
    // ========================================================================

    @PostMapping("/fulfillment/orders/{orderId}/create-shipment")
    public ResponseEntity<ShipmentResponse> createShipment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId,
            @RequestBody(required = false) CreateShipmentRequest request
    ) {
        long sellerId = extractUserId(jwt);
        ShipmentResponse response = fulfillmentService.createShipment(sellerId, orderId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/fulfillment/orders/{orderId}/print-waybill")
    public ResponseEntity<PrintWaybillResponse> printWaybill(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId
    ) {
        long sellerId = extractUserId(jwt);
        PrintWaybillResponse response = fulfillmentService.printWaybill(sellerId, orderId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/fulfillment/orders/{orderId}/tracking")
    public ResponseEntity<TrackingResponse> getTracking(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId
    ) {
        long userId = extractUserId(jwt);
        TrackingResponse response = fulfillmentService.getTracking(userId, orderId);
        return ResponseEntity.ok(response);
    }

    // ========================================================================
    // Shipping Master Data (Provinces, Districts, Wards)
    // ========================================================================

    @GetMapping("/shipping/master-data/provinces")
    public ResponseEntity<List<ProvinceDto>> getProvinces() {
        List<ProvinceDto> provinces = fulfillmentService.getProvinces();
        return ResponseEntity.ok(provinces);
    }

    @GetMapping("/shipping/master-data/districts")
    public ResponseEntity<List<DistrictDto>> getDistricts(
            @RequestParam(name = "provinceId", required = false) Integer provinceId,
            @RequestParam(name = "province_id", required = false) Integer provinceIdSnake
    ) {
        Integer targetId = provinceId != null ? provinceId : provinceIdSnake;
        if (targetId == null) {
            throw new IllegalArgumentException("Tham số provinceId hoặc province_id là bắt buộc.");
        }
        List<DistrictDto> districts = fulfillmentService.getDistricts(targetId);
        return ResponseEntity.ok(districts);
    }

    @GetMapping("/shipping/master-data/wards")
    public ResponseEntity<List<WardDto>> getWards(
            @RequestParam(name = "districtId", required = false) Integer districtId,
            @RequestParam(name = "district_id", required = false) Integer districtIdSnake
    ) {
        Integer targetId = districtId != null ? districtId : districtIdSnake;
        if (targetId == null) {
            throw new IllegalArgumentException("Tham số districtId hoặc district_id là bắt buộc.");
        }
        List<WardDto> wards = fulfillmentService.getWards(targetId);
        return ResponseEntity.ok(wards);
    }

    private static long extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AccessDeniedException("Yêu cầu đăng nhập.");
        }
        return Long.parseLong(jwt.getSubject());
    }
}
