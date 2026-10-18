package com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.OffsetDateTime;

public final class GhnWebhookDtos {

    private GhnWebhookDtos() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookPayloadDto(
            @JsonProperty("OrderCode") String orderCode,
            @JsonProperty("ClientOrderCode") String clientOrderCode,
            @JsonProperty("Type") String type,
            @JsonProperty("Status") String status,
            @JsonProperty("Time") String time,
            @JsonProperty("Description") String description,
            @JsonProperty("TotalFee") Long totalFee,
            @JsonProperty("CODAmount") Long codAmount,
            @JsonProperty("Weight") Integer weight,
            @JsonProperty("Length") Integer length,
            @JsonProperty("Width") Integer width,
            @JsonProperty("Height") Integer height,
            @JsonProperty("ConvertedWeight") Integer convertedWeight,
            @JsonProperty("Warehouse") String warehouse,
            @JsonProperty("Reason") String reason,
            @JsonProperty("ReasonCode") String reasonCode,
            @JsonProperty("ShipperName") String shipperName,
            @JsonProperty("ShipperPhone") String shipperPhone
    ) {
        public Instant parseTime() {
            if (time == null || time.isBlank()) {
                return null;
            }
            try {
                return Instant.parse(time);
            } catch (Exception e) {
                try {
                    return OffsetDateTime.parse(time).toInstant();
                } catch (Exception e2) {
                    return null;
                }
            }
        }
    }

    public record WebhookAckResponse(
            String status,
            String message
    ) {
        public static WebhookAckResponse ok() {
            return new WebhookAckResponse("OK", "Event received");
        }

        public static WebhookAckResponse duplicate() {
            return new WebhookAckResponse("OK", "Duplicate event acknowledged");
        }
    }
}
