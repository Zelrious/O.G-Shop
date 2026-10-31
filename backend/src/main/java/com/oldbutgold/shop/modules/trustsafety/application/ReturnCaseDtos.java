package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.ReturnReasonCode;
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
            String commandKey,
            CaseRoundDto currentRound,
            List<CaseEvidenceDto> evidence,
            List<CaseEventDto> events
    ) {}
}

