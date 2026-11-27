package com.oldbutgold.shop.modules.trustsafety.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class DisputeDtos {

    public record DisputeSummaryDto(
            Long disputeId,
            Long orderId,
            String orderCode,
            Long buyerId,
            String buyerName,
            Long sellerId,
            String sellerName,
            String productTitle,
            BigDecimal disputeAmount,
            String reason,
            String description,
            String status,
            String verdictAction,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record DisputeDetailDto(
            Long disputeId,
            Long orderId,
            String orderCode,
            Long buyerId,
            String buyerName,
            String buyerEmail,
            Long sellerId,
            String sellerName,
            String sellerEmail,
            String productTitle,
            BigDecimal disputeAmount,
            String reason,
            String description,
            List<String> buyerEvidenceUrls,
            String videoUnboxingUrl,
            String sellerResponse,
            List<String> sellerEvidenceUrls,
            Instant sellerResponseDueAt,
            String status,
            String verdictAction,
            String verdictNotes,
            BigDecimal refundAmount,
            Integer penaltyPoints,
            Long resolvedBy,
            Instant resolvedAt,
            Instant createdAt,
            Instant updatedAt,
            String returnTrackingCode,
            String returnCarrier,
            String returnShipmentStatus,
            Instant returnHandedOverAt,
            Instant returnDeliveredAt,
            String returnRecipientName,
            String returnRecipientPhone,
            String returnRecipientAddress
    ) {
        public DisputeDetailDto(
                Long disputeId,
                Long orderId,
                String orderCode,
                Long buyerId,
                String buyerName,
                String buyerEmail,
                Long sellerId,
                String sellerName,
                String sellerEmail,
                String productTitle,
                BigDecimal disputeAmount,
                String reason,
                String description,
                List<String> buyerEvidenceUrls,
                String videoUnboxingUrl,
                String sellerResponse,
                List<String> sellerEvidenceUrls,
                Instant sellerResponseDueAt,
                String status,
                String verdictAction,
                String verdictNotes,
                BigDecimal refundAmount,
                Integer penaltyPoints,
                Long resolvedBy,
                Instant resolvedAt,
                Instant createdAt,
                Instant updatedAt
        ) {
            this(disputeId, orderId, orderCode, buyerId, buyerName, buyerEmail,
                    sellerId, sellerName, sellerEmail, productTitle, disputeAmount,
                    reason, description, buyerEvidenceUrls, videoUnboxingUrl,
                    sellerResponse, sellerEvidenceUrls, sellerResponseDueAt,
                    status, verdictAction, verdictNotes, refundAmount, penaltyPoints,
                    resolvedBy, resolvedAt, createdAt, updatedAt,
                    null, null, null, null, null, null, null, null);
        }

        public DisputeDetailDto(
                Long disputeId,
                Long orderId,
                String orderCode,
                Long buyerId,
                String buyerName,
                String buyerEmail,
                Long sellerId,
                String sellerName,
                String sellerEmail,
                String productTitle,
                BigDecimal disputeAmount,
                String reason,
                String description,
                List<String> buyerEvidenceUrls,
                String videoUnboxingUrl,
                String sellerResponse,
                List<String> sellerEvidenceUrls,
                Instant sellerResponseDueAt,
                String status,
                String verdictAction,
                String verdictNotes,
                BigDecimal refundAmount,
                Integer penaltyPoints,
                Long resolvedBy,
                Instant resolvedAt,
                Instant createdAt,
                Instant updatedAt,
                String returnTrackingCode,
                String returnCarrier,
                String returnShipmentStatus
        ) {
            this(disputeId, orderId, orderCode, buyerId, buyerName, buyerEmail,
                    sellerId, sellerName, sellerEmail, productTitle, disputeAmount,
                    reason, description, buyerEvidenceUrls, videoUnboxingUrl,
                    sellerResponse, sellerEvidenceUrls, sellerResponseDueAt,
                    status, verdictAction, verdictNotes, refundAmount, penaltyPoints,
                    resolvedBy, resolvedAt, createdAt, updatedAt,
                    returnTrackingCode, returnCarrier, returnShipmentStatus,
                    null, null, null, null, null);
        }
    }

    public record ReturnShipmentRequest(
            String trackingCode,
            String carrier
    ) {}

    public record DisputeDecisionRequest(
            @NotBlank String verdictAction,
            @NotBlank String verdictNotes,
            Integer penaltyPoints
    ) {
        @JsonCreator
        public DisputeDecisionRequest(
                @JsonProperty("verdictAction") String verdictAction,
                @JsonProperty("verdictNotes") String verdictNotes,
                @JsonProperty("penaltyPoints") Integer penaltyPoints
        ) {
            this.verdictAction = verdictAction;
            this.verdictNotes = verdictNotes;
            this.penaltyPoints = penaltyPoints != null ? penaltyPoints : 0;
        }
    }

    public record DisputeDecisionResponse(
            Long disputeId,
            String status,
            String verdictAction,
            String message
    ) {}

    public record CreateDisputeRequest(
            @NotNull Long orderId,
            @NotBlank String reason,
            @NotBlank String description,
            List<String> buyerEvidenceUrls,
            String videoUnboxingUrl
    ) {}

    public record SellerDisputeResponseRequest(
            @NotBlank String sellerResponse,
            List<String> sellerEvidenceUrls
    ) {}

    public record DisputePageResponse(
            List<DisputeSummaryDto> items,
            long total,
            int page,
            int size
    ) {}
}
