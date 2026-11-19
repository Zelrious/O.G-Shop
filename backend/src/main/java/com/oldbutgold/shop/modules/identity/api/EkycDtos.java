package com.oldbutgold.shop.modules.identity.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EkycDtos {
    private EkycDtos() {}

    public record CccdDataDto(
            String cccdNumber,
            String fullName,
            String dob,
            String gender,
            String hometown,
            String address,
            Boolean aiStandardized,
            Boolean mrzVerified
    ) {
        public CccdDataDto(String cccdNumber, String fullName, String dob, String gender, String hometown, String address) {
            this(cccdNumber, fullName, dob, gender, hometown, address, false, false);
        }
        public CccdDataDto(String cccdNumber, String fullName, String dob, String gender, String hometown, String address, Boolean aiStandardized) {
            this(cccdNumber, fullName, dob, gender, hometown, address, aiStandardized, false);
        }
    }

    public record EkycStatusResponse(
            boolean hasProfile,
            Long profileId,
            Integer revisionNo,
            String state,
            CccdDataDto submittedData,
            Double matchDistance,
            Double confidenceScore,
            String rejectionReason,
            Instant submittedAt,
            Instant verifiedAt,
            boolean canSubmit,
            boolean isSeller,
            int nonmatchCount,
            Long frontAssetId,
            Long backAssetId,
            Long liveAssetId,
            Long portraitAssetId,
            boolean hasVerifiedProfile
    ) {
        public EkycStatusResponse(
                boolean hasProfile,
                Long profileId,
                Integer revisionNo,
                String state,
                CccdDataDto submittedData,
                Double matchDistance,
                Double confidenceScore,
                String rejectionReason,
                Instant submittedAt,
                Instant verifiedAt,
                boolean canSubmit,
                boolean isSeller,
                int nonmatchCount,
                Long frontAssetId,
                Long backAssetId,
                Long liveAssetId,
                boolean hasVerifiedProfile
        ) {
            this(hasProfile, profileId, revisionNo, state, submittedData, matchDistance, confidenceScore,
                    rejectionReason, submittedAt, verifiedAt, canSubmit, isSeller, nonmatchCount,
                    frontAssetId, backAssetId, liveAssetId, null, hasVerifiedProfile);
        }

        public EkycStatusResponse(
                boolean hasProfile,
                Long profileId,
                Integer revisionNo,
                String state,
                CccdDataDto submittedData,
                Double matchDistance,
                Double confidenceScore,
                String rejectionReason,
                Instant submittedAt,
                Instant verifiedAt,
                boolean canSubmit,
                boolean isSeller,
                int nonmatchCount,
                Long frontAssetId,
                Long backAssetId,
                Long liveAssetId
        ) {
            this(hasProfile, profileId, revisionNo, state, submittedData, matchDistance, confidenceScore,
                    rejectionReason, submittedAt, verifiedAt, canSubmit, isSeller, nonmatchCount,
                    frontAssetId, backAssetId, liveAssetId, null, false);
        }
    }

    public record EkycStartResponse(
            Long profileId,
            Integer revisionNo,
            String state
    ) {}

    public record EkycStartRequest(
            UUID quickAuthResultId
    ) {}

    public record EkycOcrResponse(
            String status,
            String errorCode,
            String errorMessage,
            CccdDataDto ocrData,
            Long frontAssetId,
            Long backAssetId,
            Long portraitAssetId
    ) {
        public EkycOcrResponse(String status, String errorMessage, CccdDataDto ocrData, Long frontAssetId, Long backAssetId, Long portraitAssetId) {
            this(status, null, errorMessage, ocrData, frontAssetId, backAssetId, portraitAssetId);
        }

        public EkycOcrResponse(String status, String errorMessage, CccdDataDto ocrData, Long frontAssetId, Long backAssetId) {
            this(status, null, errorMessage, ocrData, frontAssetId, backAssetId, null);
        }
    }

    public record EkycSubmitRequest(
            CccdDataDto confirmedData,
            String liveFrameBase64,
            Long frontAssetId,
            Long backAssetId,
            UUID quickAuthResultId,
            List<String> referenceFramesBase64,
            Long portraitAssetId,
            Boolean manualCapture
    ) {
        public EkycSubmitRequest(CccdDataDto confirmedData, String liveFrameBase64, Long frontAssetId, Long backAssetId) {
            this(confirmedData, liveFrameBase64, frontAssetId, backAssetId, null, null, null, false);
        }

        public EkycSubmitRequest(CccdDataDto confirmedData, String liveFrameBase64, Long frontAssetId, Long backAssetId, UUID quickAuthResultId) {
            this(confirmedData, liveFrameBase64, frontAssetId, backAssetId, quickAuthResultId, null, null, false);
        }

        public EkycSubmitRequest(CccdDataDto confirmedData, String liveFrameBase64, Long frontAssetId, Long backAssetId, UUID quickAuthResultId, List<String> referenceFramesBase64) {
            this(confirmedData, liveFrameBase64, frontAssetId, backAssetId, quickAuthResultId, referenceFramesBase64, null, false);
        }
    }

    public record EkycSubmitResponse(
            Long profileId,
            String state,
            boolean isMatch,
            double distance,
            double confidenceScore,
            String message
    ) {}

    public record EkycCheckFaceRequest(
            Long frontAssetId,
            String liveFrameBase64,
            Long portraitAssetId
    ) {
        public EkycCheckFaceRequest(Long frontAssetId, String liveFrameBase64) {
            this(frontAssetId, liveFrameBase64, null);
        }
    }

    public record EkycCheckFaceResponse(
            boolean isMatch,
            String matchStatus,
            double distance,
            double confidenceScore,
            String message,
            String frameStatus,
            String directionalCue,
            Double distanceCard,
            Double distancePortrait,
            Boolean cardMatched,
            Boolean portraitMatched,
            String livenessStatus,
            Integer framesCollected
    ) {
        public EkycCheckFaceResponse(boolean isMatch, String matchStatus, double distance, double confidenceScore, String message, String frameStatus, String directionalCue) {
            this(isMatch, matchStatus, distance, confidenceScore, message, frameStatus, directionalCue, null, null, null, null, "NOT_CHECKED", 1);
        }
    }

    public record KtvEkycSummaryDto(
            long profileId,
            long userId,
            String username,
            String fullName,
            String email,
            String phoneNumber,
            int revisionNo,
            String state,
            Instant submittedAt,
            Double matchDistance,
            String maskedCccdNumber
    ) {}

    public record KtvEkycPageResponse(
            List<KtvEkycSummaryDto> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    public record KtvEkycDetailDto(
            long profileId,
            long userId,
            String username,
            String fullName,
            String email,
            String phoneNumber,
            int revisionNo,
            String state,
            Instant submittedAt,
            CccdDataDto submittedData,
            CccdDataDto ocrData,
            Double matchDistance,
            Double confidenceScore,
            Double thresholdUsed,
            String modelName,
            Long frontAssetId,
            Long backAssetId,
            Long liveAssetId,
            Long portraitAssetId,
            // Reference asset from previous verified profile (for comparison)
            Long referenceAssetId,
            String referenceAssetUrl,
            String rejectionReason,
            Instant reviewedAt,
            Long reviewedBy
    ) {
        public KtvEkycDetailDto(
                long profileId, long userId, String username, String fullName, String email, String phoneNumber,
                int revisionNo, String state, Instant submittedAt, CccdDataDto submittedData, CccdDataDto ocrData,
                Double matchDistance, Double confidenceScore, Double thresholdUsed, String modelName,
                Long frontAssetId, Long backAssetId, Long liveAssetId,
                Long referenceAssetId, String referenceAssetUrl, String rejectionReason,
                Instant reviewedAt, Long reviewedBy
        ) {
            this(profileId, userId, username, fullName, email, phoneNumber, revisionNo, state, submittedAt,
                 submittedData, ocrData, matchDistance, confidenceScore, thresholdUsed, modelName,
                 frontAssetId, backAssetId, liveAssetId, null, referenceAssetId, referenceAssetUrl,
                 rejectionReason, reviewedAt, reviewedBy);
        }
    }

    public record KtvEkycApproveRequest(
            @JsonProperty("note")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            String note
    ) {
        @JsonCreator
        public KtvEkycApproveRequest(@JsonProperty("note") String note) {
            this.note = (note != null && !note.isBlank()) ? note : "Đã đối chiếu thông tin hợp lệ";
        }
    }

    public record KtvEkycRejectRequest(
            String reason
    ) {}

    public record KtvEkycDecisionResponse(
            long profileId,
            String outcome,
            String message
    ) {}

    public record QuickAuthInitRequest(
            String purpose
    ) {}

    public record QuickAuthInitResponse(
            boolean alreadyVerified,
            UUID sessionId,
            UUID resultId,
            Instant expiresAt,
            String message
    ) {}

    public record QuickAuthVerifyRequest(
            UUID sessionId,
            String liveFrameBase64
    ) {}

    public record QuickAuthVerifyResponse(
            boolean match,
            UUID resultId,
            Double distance,
            Double confidenceScore,
            int remainingAttempts,
            String message,
            String frameStatus,
            String directionalCue
    ) {}

    public record QuickAuthStatusResponse(
            boolean hasValidResult,
            UUID resultId,
            Instant expiresAt,
            String purpose
    ) {}
}
