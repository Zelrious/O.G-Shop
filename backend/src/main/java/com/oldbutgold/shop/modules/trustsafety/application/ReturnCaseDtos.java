package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.ReturnReasonCode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Objects for Return & Dispute cases (UC62, UC63, UC75, UC83).
 */
public final class ReturnCaseDtos {

    private ReturnCaseDtos() {}

    public record EvidenceItemDto(
            String mediaType,
            String objectKey,
            String textContent,
            String contentDigest,
            Long fileSizeBytes,
            Integer durationSeconds
    ) {}

    public record CreateReturnRequest(
            @NotNull(message = "Mã lý do trả hàng không được để trống")
            ReturnReasonCode reasonCode,

            @Size(max = 2000, message = "Chi tiết lý do tối đa 2000 ký tự")
            String detail,

            List<EvidenceItemDto> evidenceList,

            String commandKey
    ) {}

    public record SellerRound1ResponseRequest(
            @NotNull(message = "Lựa chọn phản hồi không được để trống")
            String outcome,

            @Size(max = 2000, message = "Lý do tối đa 2000 ký tự")
            String reason,

            String commandKey
    ) {}

    public record SellerAgreeRefundRequest(
            @NotNull(message = "Lý do đồng ý hoàn tiền không được để trống")
            @Size(min = 5, max = 2000, message = "Lý do đồng ý hoàn tiền từ 5 đến 2000 ký tự")
            String reason,

            @Size(max = 127, message = "commandKey tối đa 127 ký tự để bảo đảm an toàn cho settlement operation")
            String commandKey
    ) {}

    public record SellerDamageExemptionRequest(
            @NotNull(message = "Lý do khiếu nại thiệt hại không được để trống")
            @Size(min = 5, max = 2000, message = "Lý do khiếu nại thiệt hại từ 5 đến 2000 ký tự")
            String reason,

            @NotEmpty(message = "Danh sách bằng chứng không được để trống")
            List<EvidenceItemDto> evidenceList,

            @Size(max = 150, message = "commandKey tối đa 150 ký tự")
            String commandKey,

            UUID requestId
    ) {
        public SellerDamageExemptionRequest(String reason, List<EvidenceItemDto> evidenceList, String commandKey) {
            this(reason, evidenceList, commandKey, null);
        }
    }

    public record KtvRound1DecisionRequest(
            @NotNull(message = "Phán quyết không được để trống")
            String outcome,

            @NotNull(message = "Lý do phán quyết không được để trống")
            @Size(min = 5, max = 2000, message = "Lý do phán quyết từ 5 đến 2000 ký tự")
            String reason,

            String commandKey
    ) {}

    public record KtvRound2DecisionRequest(
            @NotNull(message = "Phán quyết không được để trống")
            String outcome,

            @NotNull(message = "Lý do phán quyết không được để trống")
            @Size(min = 5, max = 2000, message = "Lý do phán quyết từ 5 đến 2000 ký tự")
            String reason,

            @Size(max = 127, message = "commandKey tối đa 127 ký tự để bảo đảm an toàn cho settlement operation")
            String commandKey
    ) {}

    public record KtvRequestEvidenceRequest(
            @jakarta.validation.constraints.NotBlank(message = "Lý do yêu cầu bổ sung không được để trống")
            @Size(min = 10, max = 2000, message = "Lý do yêu cầu bổ sung phải từ 10 đến 2000 ký tự")
            String reason,

            @Size(max = 10, message = "Danh sách mục yêu cầu tối đa 10 mục")
            List<@Size(max = 255, message = "Mỗi mục yêu cầu tối đa 255 ký tự") String> requestedItems,

            @jakarta.validation.constraints.NotBlank(message = "commandKey không được để trống")
            @Size(max = 150, message = "commandKey tối đa 150 ký tự")
            String commandKey
    ) {}

    public record KtvRequestEvidenceResponse(
            UUID requestId,
            Long caseId,
            Long roundId,
            int roundNo,
            Long ktvId,
            String status,
            String reason,
            List<String> requestedItems,
            Instant issuedAt,
            Instant deadlineAt,
            String commandKey
    ) {}

    public record WithdrawReturnRequest(
            @Size(max = 1000, message = "Lý do rút tối đa 1000 ký tự")
            String reason,

            String commandKey
    ) {}

    public record CaseRoundDto(
            Long roundId,
            Long caseId,
            Integer roundNo,
            String stage,
            String state,
            Instant openedAt,
            Instant completeSubmissionAt,
            Instant ktvDueAt,
            Instant validDamageRequestAt
    ) {}

    public record CaseEvidenceDto(
            UUID evidenceId,
            Long roundId,
            Long submittedBy,
            Integer submissionNo,
            String mediaType,
            String objectKey,
            String textContent,
            Long fileSizeBytes,
            Integer durationSeconds,
            Instant submittedAt
    ) {}

    public record CaseEventDto(
            UUID eventId,
            Long caseId,
            Long roundId,
            Long actorId,
            Long recipientId,
            String eventType,
            String reason,
            String metadata,
            Instant occurredAt
    ) {}

    public record CaseDecisionDto(
            Long decisionId,
            Long roundId,
            Long reviewedBy,
            String actorType,
            String outcome,
            Boolean isFinal,
            BigDecimal goodsRefundAmount,
            String reason,
            Instant decidedAt
    ) {}

    public record CaseDecisionPublicDto(
            Long decisionId,
            Long roundId,
            String outcome,
            BigDecimal goodsRefundAmount,
            String reason,
            Instant decidedAt,
            Boolean isFinal
    ) {}

    public record SubmitReturnShipmentRequest(
            @NotNull(message = "Phương thức vận chuyển không được để trống")
            String deliveryMethod,

            String carrier,

            String trackingNumber,

            @NotNull(message = "commandKey không được để trống")
            String commandKey
    ) {}

    public record ConfirmReturnDeliveryRequest(
            Instant deliveredAt,
            String note,
            @NotNull(message = "commandKey không được để trống")
            String commandKey
    ) {}

    public record BuyerHandoverConfirmRequest(
            @NotNull(message = "commandKey không được để trống")
            String commandKey
    ) {}

    public record ReturnShipmentDetailResponse(
            Long shipmentId,
            Long caseId,
            Long orderId,
            String leg,
            String deliveryMethod,
            String carrier,
            String trackingNumber,
            String status,
            Instant pickupExpectedAt,
            Instant pickupDeadline,
            Instant validDeliveryAt
    ) {}

    public record ReturnCaseDetailResponse(
            Long caseId,
            Long orderId,
            String caseType,
            String state,
            ReturnReasonCode reasonCode,
            String cleanDescription,
            String fullDescription,
            Long createdBy,
            Instant createdAt,
            Instant approvedAt,
            Instant validReturnedAt,
            Instant sellerResponseDueAt,
            Instant closedAt,
            CaseRoundDto currentRound,
            List<CaseEvidenceDto> evidence,
            List<CaseEventDto> events,
            ReturnShipmentDetailResponse returnShipment,
            List<CaseDecisionPublicDto> decisions
    ) {
        public ReturnCaseDetailResponse {
            evidence = evidence != null ? List.copyOf(evidence) : List.of();
            events = events != null ? List.copyOf(events) : List.of();
            decisions = decisions != null ? List.copyOf(decisions) : List.of();
        }

        public ReturnCaseDetailResponse(
                Long caseId,
                Long orderId,
                String caseType,
                String state,
                ReturnReasonCode reasonCode,
                String cleanDescription,
                String fullDescription,
                Long createdBy,
                Instant createdAt,
                Instant approvedAt,
                Instant validReturnedAt,
                Instant sellerResponseDueAt,
                Instant closedAt,
                CaseRoundDto currentRound,
                List<CaseEvidenceDto> evidence,
                List<CaseEventDto> events
        ) {
            this(caseId, orderId, caseType, state, reasonCode, cleanDescription, fullDescription,
                    createdBy, createdAt, approvedAt, validReturnedAt, sellerResponseDueAt, closedAt,
                    currentRound, evidence, events, null, List.of());
        }

        public ReturnCaseDetailResponse(
                Long caseId,
                Long orderId,
                String caseType,
                String state,
                ReturnReasonCode reasonCode,
                String cleanDescription,
                String fullDescription,
                Long createdBy,
                Instant createdAt,
                Instant approvedAt,
                Instant validReturnedAt,
                Instant sellerResponseDueAt,
                Instant closedAt,
                CaseRoundDto currentRound,
                List<CaseEvidenceDto> evidence,
                List<CaseEventDto> events,
                ReturnShipmentDetailResponse returnShipment
        ) {
            this(caseId, orderId, caseType, state, reasonCode, cleanDescription, fullDescription,
                    createdBy, createdAt, approvedAt, validReturnedAt, sellerResponseDueAt, closedAt,
                    currentRound, evidence, events, returnShipment, List.of());
        }

        @Deprecated
        public ReturnCaseDetailResponse(
                Long caseId,
                Long orderId,
                String caseType,
                String state,
                ReturnReasonCode reasonCode,
                String cleanDescription,
                String fullDescription,
                Long createdBy,
                Instant createdAt,
                Instant approvedAt,
                Instant validReturnedAt,
                Instant sellerResponseDueAt,
                Instant closedAt,
                String deprecatedCommandKey,
                CaseRoundDto currentRound,
                List<CaseEvidenceDto> evidence,
                List<CaseEventDto> events,
                ReturnShipmentDetailResponse returnShipment
        ) {
            this(caseId, orderId, caseType, state, reasonCode, cleanDescription, fullDescription,
                    createdBy, createdAt, approvedAt, validReturnedAt, sellerResponseDueAt, closedAt,
                    currentRound, evidence, events, returnShipment, List.of());
        }
    }

    public enum TimeoutProcessingStatus {
        PROCESSED,
        SKIPPED,
        LOCKED_55P03,
        FAILED
    }

    public record CaseTimeoutExecutionResult(
            Long caseId,
            TimeoutProcessingStatus status,
            String message,
            ReturnCaseDetailResponse response
    ) {}

    public record TimeoutBatchResult(
            int totalScanned,
            List<Long> processedCaseIds,
            List<Long> skippedCaseIds,
            List<Long> lockedCaseIds,
            List<Long> failedCaseIds
    ) {
        public TimeoutBatchResult {
            processedCaseIds = processedCaseIds != null ? List.copyOf(processedCaseIds) : List.of();
            skippedCaseIds = skippedCaseIds != null ? List.copyOf(skippedCaseIds) : List.of();
            lockedCaseIds = lockedCaseIds != null ? List.copyOf(lockedCaseIds) : List.of();
            failedCaseIds = failedCaseIds != null ? List.copyOf(failedCaseIds) : List.of();
        }
    }

    public enum EscalationProcessingStatus {
        ESCALATED,
        SKIPPED,
        LOCKED_55P03,
        FAILED
    }

    public record CaseEscalationExecutionResult(
            Long caseId,
            EscalationProcessingStatus status,
            String message,
            ReturnCaseDetailResponse response
    ) {}

    public record EscalationBatchResult(
            int totalScanned,
            List<Long> escalatedCaseIds,
            List<Long> skippedCaseIds,
            List<Long> lockedCaseIds,
            List<Long> failedCaseIds
    ) {
        public EscalationBatchResult {
            escalatedCaseIds = escalatedCaseIds != null ? List.copyOf(escalatedCaseIds) : List.of();
            skippedCaseIds = skippedCaseIds != null ? List.copyOf(skippedCaseIds) : List.of();
            lockedCaseIds = lockedCaseIds != null ? List.copyOf(lockedCaseIds) : List.of();
            failedCaseIds = failedCaseIds != null ? List.copyOf(failedCaseIds) : List.of();
        }
    }

    public record CreateReviewRequest(
            @NotNull(message = "Lý do yêu cầu xem xét lại không được để trống")
            @Size(min = 10, max = 2000, message = "Lý do xem xét lại phải từ 10 đến 2000 ký tự")
            String reason,

            String commandKey
    ) {}

    public record ReviewRequestResponse(
            Long caseId,
            String status,
            String message
    ) {}

    public record AdminEscalatedCaseSummaryResponse(
            Long caseId,
            String caseNumber,
            Long orderId,
            String orderCode,
            Long buyerId,
            Long sellerId,
            Long assignedKtvId,
            String assignedKtvName,
            String caseState,
            Integer roundNo,
            String roundStage,
            Instant completeSubmissionAt,
            Instant escalatedAt,
            Double slaOverdueHours,
            String escalationReason
    ) {}

    public record AdminEscalatedCasePageResponse(
            List<AdminEscalatedCaseSummaryResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean last
    ) {
        public AdminEscalatedCasePageResponse {
            content = content != null ? List.copyOf(content) : List.of();
        }
    }
}

