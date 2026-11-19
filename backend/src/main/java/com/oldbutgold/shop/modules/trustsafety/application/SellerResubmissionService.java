package com.oldbutgold.shop.modules.trustsafety.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Service managing Seller Damage Exemption Resubmissions (BD-5 Phase 1 / Task 3.2.2).
 * Handles resubmissions where submission_no > 1 and requestId != null.
 *
 * Implements:
 * - Independent batch media quota validation (1-5 images, max 2 videos <= 60s, <= 50MB).
 * - Canonical payload fingerprint computation (D-07 Option 1 PO APPROVED).
 * - State Guard Precedence: Idempotency replay checked BEFORE state guards.
 * - Gate 1 lazy deadline validation (in-memory check; no EXPIRED persistence).
 * - Pessimistic lock hierarchy: Order -> Case -> Round(2).
 * - Fulfillment of CaseEvidenceRequestEntity (status = FULFILLED).
 * - Storing evidence with submission_no and request_id.
 * - Inserting domain event TYPE_SUPPLEMENTED with commandKey.
 */
@Service
public class SellerResubmissionService {

    private static final Logger log = LoggerFactory.getLogger(SellerResubmissionService.class);

    private final ReturnWorkflowLockGateway lockGateway;
    private final CaseEvidenceRequestRepository caseEvidenceRequestRepository;
    private final CaseEvidenceRepository caseEvidenceRepository;
    private final CaseDecisionRepository caseDecisionRepository;
    private final CaseEventRepository caseEventRepository;
    private final CaseRepository caseRepository;
    private final EvidenceStorageVerifier evidenceStorageVerifier;
    private final ObjectMapper objectMapper;
    private Clock clock = Clock.systemUTC();

    @Autowired
    public SellerResubmissionService(
            ReturnWorkflowLockGateway lockGateway,
            CaseEvidenceRequestRepository caseEvidenceRequestRepository,
            CaseEvidenceRepository caseEvidenceRepository,
            CaseDecisionRepository caseDecisionRepository,
            CaseEventRepository caseEventRepository,
            CaseRepository caseRepository,
            @Autowired(required = false) EvidenceStorageVerifier evidenceStorageVerifier,
            @Autowired(required = false) ObjectMapper objectMapper
    ) {
        this.lockGateway = lockGateway;
        this.caseEvidenceRequestRepository = caseEvidenceRequestRepository;
        this.caseEvidenceRepository = caseEvidenceRepository;
        this.caseDecisionRepository = caseDecisionRepository;
        this.caseEventRepository = caseEventRepository;
        this.caseRepository = caseRepository;
        this.evidenceStorageVerifier = evidenceStorageVerifier;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * Executes Seller damage exemption resubmission for an existing KTV request.
     *
     * @param sellerId authenticated seller user ID
     * @param caseId return case ID
     * @param request resubmission request with requestId != null
     * @return updated CaseEntity under lock
     */
    @Transactional
    public CaseEntity submitDamageExemptionResubmission(
            Long sellerId,
            Long caseId,
            ReturnCaseDtos.SellerDamageExemptionRequest request
    ) {
        if (sellerId == null) {
            throw new TrustSafetyBusinessException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Yêu cầu đăng nhập");
        }
        if (caseId == null || caseId < 1) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "caseId không hợp lệ");
        }
        if (request == null) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Yêu cầu bồi thường thiệt hại không được để trống");
        }
        if (request.requestId() == null) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "requestId không được để trống khi nộp bổ sung");
        }

        // 1. Validate commandKey (D-07: Mandatory for resubmission)
        String cmdKey = request.commandKey() != null ? request.commandKey().trim() : "";
        if (cmdKey.isBlank()) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "COMMAND_KEY_REQUIRED",
                    "commandKey không được để trống khi nộp bổ sung");
        }
        if (cmdKey.length() > 150) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "commandKey tối đa 150 ký tự");
        }

        // 2. Validate reason
        String reason = request.reason() != null ? request.reason().trim() : "";
        if (reason.length() < 5 || reason.length() > 2000) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
                    "Lý do khiếu nại thiệt hại phải từ 5 đến 2000 ký tự");
        }

        // 3. Validate evidence list and quota (BD5-10 Option A: 1-5 images, max 2 videos <= 60s, <= 50MB)
        List<ReturnCaseDtos.EvidenceItemDto> evidenceList = request.evidenceList();
        if (evidenceList == null || evidenceList.isEmpty()) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
                    "Bắt buộc phải có ít nhất 1 bằng chứng (ảnh hoặc video) chứng minh thiệt hại");
        }
        validateBatchMediaQuota(evidenceList);

        // 4. Compute canonical payload hash (Section 6.3 / P0-2)
        String computedHash = computeCanonicalResubmissionPayloadHash(request.requestId(), reason, evidenceList);

        // 5. Lock canonical order: Order -> Case -> Round(2)
        ReturnWorkflowLockGateway.ReturnWorkflowLockedEntities locked = lockGateway.lockForRound(caseId, 2);
        OrderEntity order = locked.order();
        CaseEntity c = locked.returnCase();
        CaseRoundEntity round2 = locked.activeRound();

        // 6. Check Seller Ownership
        if (!sellerId.equals(order.getSellerId())) {
            throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "SELLER_OWNERSHIP_MISMATCH",
                    "Chỉ người bán của đơn hàng mới có quyền gửi yêu cầu bồi thường thiệt hại");
        }

        // 7. State Guard Precedence: Idempotency Check BEFORE state guards
        Optional<CaseEvidenceRequestEntity> existingByCmd = caseEvidenceRequestRepository.findByFulfillmentCommandKey(cmdKey);
        if (existingByCmd.isPresent()) {
            CaseEvidenceRequestEntity existing = existingByCmd.get();
            // Anti-leak check across cases
            if (!existing.getCaseId().equals(caseId)) {
                throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "COMMAND_KEY_CONFLICT",
                        "Xung đột commandKey: khóa này đã được sử dụng cho một hồ sơ khiếu nại khác");
            }
            if (existing.getFulfillmentPayloadHash() != null && existing.getFulfillmentPayloadHash().equalsIgnoreCase(computedHash)) {
                log.info("API 3 Resubmission idempotent replay hit for caseId={}, commandKey={}", caseId, cmdKey);
                return c;
            } else {
                throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "COMMAND_KEY_CONFLICT",
                        "Trùng commandKey nhưng nội dung nộp bổ sung khác nhau");
            }
        }

        // 8. Find and Validate Request Evidence
        CaseEvidenceRequestEntity req = caseEvidenceRequestRepository.findById(request.requestId())
                .orElseThrow(() -> new TrustSafetyBusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy yêu cầu bằng chứng với requestId: " + request.requestId()));

        if (!req.getCaseId().equals(caseId)) {
            throw new TrustSafetyBusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                    "Yêu cầu bằng chứng không thuộc hồ sơ khiếu nại này");
        }

        if (!CaseEvidenceRequestEntity.STATUS_PENDING.equals(req.getStatus())) {
            throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "REQUEST_NOT_PENDING",
                    "Yêu cầu bằng chứng không ở trạng thái PENDING (Trạng thái hiện tại: " + req.getStatus() + ")");
        }

        // 9. Lazy Expiration Check (Gate 1 Invariant: in-memory check only, NO EXPIRED persistence)
        Instant now = clock.instant();
        if (now.isAfter(req.getDeadlineAt())) {
            throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "REQUEST_EXPIRED",
                    "Đã hết thời hạn 48 giờ nộp bổ sung bằng chứng (Hạn chót: " + req.getDeadlineAt() + ")");
        }

        // 10. Verify Case and Round 2 State
        if (!CaseEntity.STATE_POST_RETURN.equals(c.getState()) || c.getValidReturnedAt() == null) {
            throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "CASE_STATE_CONFLICT",
                    "Hồ sơ không ở trạng thái Vòng 2 sau khi nhận lại hàng (Trạng thái hiện tại: " + c.getState() + ")");
        }

        if (round2 == null || round2.getRoundNo() == null || round2.getRoundNo() != 2) {
            throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "CASE_STATE_CONFLICT",
                    "Không tìm thấy Vòng 2 của hồ sơ khiếu nại");
        }

        if (!CaseRoundEntity.STATE_OPEN.equals(round2.getState()) ||
                !CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION.equals(round2.getStage())) {
            throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "CASE_STATE_CONFLICT",
                    "Vòng 2 đã kết thúc hoặc không ở giai đoạn giải quyết sau nhận lại hàng");
        }

        // 11. Verify No Final Decision
        if (!caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(round2.getId()).isEmpty()) {
            throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "CASE_FINAL_DECIDED",
                    "Vòng 2 đã có phán quyết cuối cùng, không thể gửi nộp bổ sung (BD5-27)");
        }

        // 12. Compute Next Submission Number (max + 1, must be <= 4)
        List<CaseEvidenceEntity> existingEvidences = caseEvidenceRepository.findByRoundIdOrderBySubmittedAtAsc(round2.getId());
        int maxSubmissionNo = existingEvidences.stream()
                .mapToInt(e -> e.getSubmissionNo() != null ? e.getSubmissionNo() : 1)
                .max()
                .orElse(1);
        int nextSubmissionNo = maxSubmissionNo + 1;
        if (nextSubmissionNo > 4) {
            throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "MAX_RESUBMISSION_REACHED",
                    "Đã đạt số lần nộp bổ sung tối đa (tổng số 4 batches)");
        }

        // 13. Persist Evidence Items linked to request_id and nextSubmissionNo
        try {
            for (ReturnCaseDtos.EvidenceItemDto item : evidenceList) {
                String digest = resolveDamageEvidenceDigest(item.contentDigest(), item.objectKey());
                Long size = (item.fileSizeBytes() != null && item.fileSizeBytes() > 0) ? item.fileSizeBytes() : 1024L;
                Integer duration = null;
                if (CaseEvidenceEntity.MEDIA_VIDEO.equalsIgnoreCase(item.mediaType())) {
                    duration = item.durationSeconds();
                }

                CaseEvidenceEntity ev = new CaseEvidenceEntity(
                        UUID.randomUUID(),
                        round2.getId(),
                        sellerId,
                        nextSubmissionNo,
                        item.mediaType().toUpperCase(),
                        item.objectKey().trim(),
                        item.textContent() != null ? item.textContent().trim() : null,
                        digest,
                        size,
                        duration
                );
                ev.setRequestId(req.getId());
                caseEvidenceRepository.save(ev);
            }
            caseEvidenceRepository.flush();

            // 14. Update Request Entity: status = FULFILLED
            req.setStatus(CaseEvidenceRequestEntity.STATUS_FULFILLED);
            req.setFulfilledAt(now);
            req.setFulfillmentCommandKey(cmdKey);
            req.setFulfillmentPayloadHash(computedHash);
            caseEvidenceRequestRepository.save(req);

            // 15. Insert Domain Event: TYPE_SUPPLEMENTED
            Map<String, Object> metaMap = new LinkedHashMap<>();
            metaMap.put("action", "SELLER_RESUBMISSION_FULFILLED");
            metaMap.put("submission_no", nextSubmissionNo);
            metaMap.put("request_id", req.getId().toString());
            metaMap.put("evidence_count", evidenceList.size());
            String metadataJson = safeToJson(metaMap);

            CaseEventEntity event = new CaseEventEntity(
                    UUID.randomUUID(),
                    caseId,
                    round2.getId(),
                    sellerId,
                    null,
                    CaseEventEntity.TYPE_SUPPLEMENTED,
                    reason,
                    metadataJson,
                    cmdKey
            );
            caseEventRepository.save(event);

            log.info("API 3 Resubmission fulfilled successfully: caseId={}, requestId={}, submissionNo={}, commandKey={}",
                    caseId, req.getId(), nextSubmissionNo, cmdKey);

            return c;

        } catch (DataIntegrityViolationException ex) {
            // Handle race condition on partial unique index uq_case_evidence_requests_fulfill_cmd_key
            log.warn("DataIntegrityViolationException during resubmission fulfillment for cmdKey={}: {}", cmdKey, ex.getMessage());
            Optional<CaseEvidenceRequestEntity> raceCheck = caseEvidenceRequestRepository.findByFulfillmentCommandKey(cmdKey);
            if (raceCheck.isPresent()) {
                CaseEvidenceRequestEntity raceEntity = raceCheck.get();
                if (!raceEntity.getCaseId().equals(caseId)) {
                    throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "COMMAND_KEY_CONFLICT",
                            "Xung đột commandKey: khóa này đã được sử dụng cho một hồ sơ khiếu nại khác");
                }
                if (raceEntity.getFulfillmentPayloadHash() != null && raceEntity.getFulfillmentPayloadHash().equalsIgnoreCase(computedHash)) {
                    return c;
                }
            }
            throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "COMMAND_KEY_CONFLICT",
                    "Xung đột giao dịch đồng thời hoặc trùng commandKey với nội dung khác");
        }
    }

    /**
     * Validates media quota for an independent resubmission batch (BD5-10 Option A).
     * Rule: 1-5 images, max 2 videos (each video <= 60s, <= 50MB).
     */
    public void validateBatchMediaQuota(List<ReturnCaseDtos.EvidenceItemDto> evidenceList) {
        int imageCount = 0;
        int videoCount = 0;

        for (ReturnCaseDtos.EvidenceItemDto item : evidenceList) {
            if (item == null) {
                throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Mục bằng chứng không được null");
            }
            String mediaType = item.mediaType() != null ? item.mediaType().trim().toUpperCase() : "";
            if (!CaseEvidenceEntity.MEDIA_IMAGE.equals(mediaType) && !CaseEvidenceEntity.MEDIA_VIDEO.equals(mediaType)) {
                throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
                        "Loại đa phương tiện không hợp lệ: " + item.mediaType() + " (chỉ chấp nhận IMAGE hoặc VIDEO)");
            }
            if (item.objectKey() == null || item.objectKey().trim().isBlank()) {
                throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "objectKey không được để trống");
            }

            if (CaseEvidenceEntity.MEDIA_IMAGE.equals(mediaType)) {
                imageCount++;
            } else {
                videoCount++;
                if (item.durationSeconds() == null || item.durationSeconds() < 1 || item.durationSeconds() > 60) {
                    throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "VIDEO_CONSTRAINT_VIOLATED",
                            "Thời lượng video phải từ 1 đến 60 giây (durationSeconds=" + item.durationSeconds() + ")");
                }
                if (item.fileSizeBytes() == null || item.fileSizeBytes() <= 0 || item.fileSizeBytes() > 52428800L) {
                    throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "VIDEO_CONSTRAINT_VIOLATED",
                            "Dung lượng video tối đa 50MB (52428800 bytes). Thực tế: " + item.fileSizeBytes() + " bytes");
                }
            }
        }

        if (imageCount < 1 || imageCount > 5) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "MEDIA_QUOTA_EXCEEDED",
                    "Mỗi đợt nộp yêu cầu từ 1 đến 5 hình ảnh (Số lượng ảnh gửi: " + imageCount + ")");
        }
        if (videoCount > 2) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "MEDIA_QUOTA_EXCEEDED",
                    "Mỗi đợt nộp tối đa 2 video (Số lượng video gửi: " + videoCount + ")");
        }
    }

    /**
     * Computes the canonical payload hash (SHA-256) for API 3 Resubmission per Section 6.3 / P0-2.
     *
     * Fields hashed: requestId, reason, evidenceList.
     * commandKey is the lookup key and does NOT participate in hash.
     * evidenceList items are sorted by the 6-tier composite comparator:
     * 1. objectKey asc
     * 2. mediaType asc (IMAGE before VIDEO)
     * 3. contentDigest asc (coalesce null to "")
     * 4. textContent asc (coalesce null to "")
     * 5. fileSizeBytes asc (coalesce null to 0)
     * 6. durationSeconds asc (coalesce null to 0)
     */
    public static String computeCanonicalResubmissionPayloadHash(
            UUID requestId,
            String reason,
            List<ReturnCaseDtos.EvidenceItemDto> evidenceList
    ) {
        String normalizedRequestId = requestId != null ? requestId.toString().trim().toLowerCase() : "null";
        String normalizedReason = reason != null
                ? reason.trim().replace("\r\n", "\n").replace("\r", "\n")
                : "";

        List<ReturnCaseDtos.EvidenceItemDto> sortedList = new ArrayList<>();
        if (evidenceList != null) {
            sortedList.addAll(evidenceList);
            Comparator<ReturnCaseDtos.EvidenceItemDto> tierComparator = Comparator
                    .comparing((ReturnCaseDtos.EvidenceItemDto e) -> e.objectKey() != null ? e.objectKey().trim() : "", Comparator.naturalOrder())
                    .thenComparing(e -> e.mediaType() != null ? e.mediaType().trim().toUpperCase() : "", Comparator.naturalOrder())
                    .thenComparing(e -> e.contentDigest() != null ? e.contentDigest().trim().toLowerCase() : "", Comparator.naturalOrder())
                    .thenComparing(e -> {
                        String t = e.textContent() != null ? e.textContent().trim().replace("\r\n", "\n").replace("\r", "\n") : null;
                        return t != null ? t : "";
                    }, Comparator.naturalOrder())
                    .thenComparing(e -> e.fileSizeBytes() != null ? e.fileSizeBytes() : 0L, Comparator.naturalOrder())
                    .thenComparing(e -> e.durationSeconds() != null ? e.durationSeconds() : 0, Comparator.naturalOrder());
            sortedList.sort(tierComparator);
        }

        // Build canonical minified JSON with alphabetical keys: evidenceList, reason, requestId
        StringBuilder sb = new StringBuilder();
        sb.append("{\"evidenceList\":[");
        for (int i = 0; i < sortedList.size(); i++) {
            if (i > 0) sb.append(",");
            ReturnCaseDtos.EvidenceItemDto item = sortedList.get(i);
            String itemDigest = (item.contentDigest() != null && !item.contentDigest().trim().isEmpty())
                    ? item.contentDigest().trim().toLowerCase()
                    : null;
            Integer itemDuration = item.durationSeconds();
            Long itemSize = item.fileSizeBytes();
            String itemMediaType = item.mediaType() != null ? item.mediaType().trim().toUpperCase() : "";
            String itemKey = item.objectKey() != null ? item.objectKey().trim() : "";
            String itemText = (item.textContent() != null && !item.textContent().trim().isEmpty())
                    ? item.textContent().trim().replace("\r\n", "\n").replace("\r", "\n")
                    : null;

            // Alphabetical inside item: contentDigest, durationSeconds, fileSizeBytes, mediaType, objectKey, textContent
            sb.append("{\"contentDigest\":").append(escapeJsonOrNull(itemDigest));
            sb.append(",\"durationSeconds\":").append(itemDuration != null ? itemDuration.toString() : "null");
            sb.append(",\"fileSizeBytes\":").append(itemSize != null ? itemSize.toString() : "null");
            sb.append(",\"mediaType\":").append(escapeJsonOrNull(itemMediaType));
            sb.append(",\"objectKey\":").append(escapeJsonOrNull(itemKey));
            sb.append(",\"textContent\":").append(escapeJsonOrNull(itemText));
            sb.append("}");
        }
        sb.append("],\"reason\":").append(escapeJsonOrNull(normalizedReason));
        sb.append(",\"requestId\":").append(escapeJsonOrNull(normalizedRequestId));
        sb.append("}");

        return sha256Hex(sb.toString());
    }

    private String resolveDamageEvidenceDigest(String clientDigest, String objectKey) {
        Path filePath = resolveLocalFilePath(objectKey);
        if (filePath != null && Files.isRegularFile(filePath) && Files.isReadable(filePath)) {
            String computedDigest = computeSha256(filePath);
            if (clientDigest != null && !clientDigest.isBlank()) {
                if (!computedDigest.equalsIgnoreCase(clientDigest.trim())) {
                    throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
                            "Mã băm bằng chứng (contentDigest) không khớp với nội dung tệp thực tế");
                }
            }
            return computedDigest;
        }

        if (evidenceStorageVerifier != null) {
            try {
                String computed = evidenceStorageVerifier.computeSha256(objectKey);
                if (computed != null) {
                    if (clientDigest != null && !clientDigest.isBlank() && !computed.equalsIgnoreCase(clientDigest.trim())) {
                        throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
                                "Mã băm bằng chứng (contentDigest) không khớp với storage SHA-256");
                    }
                    return computed;
                }
            } catch (IOException e) {
                log.warn("Lỗi khi đọc storage verifier cho objectKey={}: {}", objectKey, e.getMessage());
            }
        }

        if (clientDigest != null && !clientDigest.isBlank()) {
            return clientDigest.trim().toLowerCase();
        }
        return sha256Hex(objectKey != null ? objectKey : UUID.randomUUID().toString());
    }

    private Path resolveLocalFilePath(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return null;
        try {
            Path path = Paths.get(objectKey);
            if (path.isAbsolute()) return path;
            return Paths.get(System.getProperty("user.dir", ".")).resolve(objectKey).normalize();
        } catch (Exception e) {
            return null;
        }
    }

    private String computeSha256(Path filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] hash = digest.digest(fileBytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString().toLowerCase();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi tính toán SHA-256 từ tệp: " + filePath, e);
        }
    }

    private static String escapeJsonOrNull(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String safeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}

