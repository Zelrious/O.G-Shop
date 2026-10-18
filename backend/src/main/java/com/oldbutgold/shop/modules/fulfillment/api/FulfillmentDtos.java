package com.oldbutgold.shop.modules.fulfillment.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class FulfillmentDtos {

    private FulfillmentDtos() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateShipmentRequest(
            String fromName,
            String fromPhone,
            String fromAddress,
            String fromWardName,
            String fromDistrictName,
            String fromProvinceName,
            Integer fromDistrictId,
            Integer toDistrictId,
            String toWardCode,
            Integer weight,
            Integer length,
            Integer width,
            Integer height,
            String note,
            List<ItemInfo> items
    ) {
        public record ItemInfo(
                String name,
                String code,
                int quantity,
                int price,
                Integer weight,
                Integer length,
                Integer width,
                Integer height
        ) {}
    }

    public record ShipmentResponse(
            Long orderId,
            String trackingNumber,
            String clientOrderCode,
            String carrier,
            String status,
            BigDecimal shippingFee,
            Instant expectedDeliveryAt,
            String rawGhnStatus,
            Instant createdAt
    ) {}

    public record PrintWaybillResponse(
            Long orderId,
            String trackingNumber,
            String printUrl
    ) {}

    public record TrackingResponse(
            Long orderId,
            String trackingNumber,
            String carrier,
            String status,
            String rawGhnStatus,
            BigDecimal shippingFee,
            Instant expectedDeliveryAt,
            String fromAddress,
            String toAddress
    ) {}

    public record ShipmentIncidentResponse(
            Long id,
            Long orderId,
            String trackingNumber,
            String clientOrderCode,
            String carrier,
            String status,
            String rawGhnStatus,
            String incidentNotes,
            boolean adminReviewRequired,
            int reconciliationAttempts,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public enum IncidentResolutionAction {
        DISMISS_AND_RESUME,
        MARK_DELIVERED,
        MARK_RETURNED,
        MARK_FAILED
    }

    public record ResolveIncidentRequest(
            @NotNull(message = "Hành động giải quyết không được để trống")
            IncidentResolutionAction action,
            String notes
    ) {}

    public record ResolveIncidentResponse(
            Long orderId,
            String trackingNumber,
            String status,
            String rawGhnStatus,
            boolean adminReviewRequired,
            String incidentNotes,
            String message
    ) {}
}
