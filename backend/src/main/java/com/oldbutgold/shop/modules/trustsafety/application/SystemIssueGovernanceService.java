package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.api.VoucherDtos;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.VoucherWalletRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.*;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Governance service for UC64/UC65/UC76/UC49/UC53 SYSTEM_ISSUE lifecycle,
 * specific Admin escalation & handover (PO-02 Option A), and compensation execution (UC70 voucher / deadline extension).
 */
@Service
public class SystemIssueGovernanceService {

    private final CaseRepository caseRepository;
    private final CaseRoundRepository caseRoundRepository;
    private final CaseDecisionRepository caseDecisionRepository;
    private final CaseEventRepository caseEventRepository;
    private final CaseEvidenceRepository caseEvidenceRepository;
    private final AccountTrustRepository accountTrustRepository;
    private final VoucherWalletRepository voucherWalletRepository;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private Clock clock = Clock.systemUTC();

    @Autowired
    public SystemIssueGovernanceService(
            CaseRepository caseRepository,
            CaseRoundRepository caseRoundRepository,
            CaseDecisionRepository caseDecisionRepository,
            CaseEventRepository caseEventRepository,
            CaseEvidenceRepository caseEvidenceRepository,
            AccountTrustRepository accountTrustRepository,
            VoucherWalletRepository voucherWalletRepository,
            JdbcTemplate jdbc,
            EntityManager entityManager
    ) {
        this.caseRepository = caseRepository;
        this.caseRoundRepository = caseRoundRepository;
        this.caseDecisionRepository = caseDecisionRepository;
        this.caseEventRepository = caseEventRepository;
        this.caseEvidenceRepository = caseEvidenceRepository;
        this.accountTrustRepository = accountTrustRepository;
        this.voucherWalletRepository = voucherWalletRepository;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ReturnCaseDtos.AdminAssigneeOption> listActiveAdmins() {
        return jdbc.query("""
                SELECT u.user_id, COALESCE(u.full_name, 'Admin #' || u.user_id) AS full_name, u.email
                FROM og_compat.users u
                JOIN og_compat.user_roles ur USING(user_id)
                JOIN og_compat.roles r USING(role_id)
                WHERE r.role_name = 'ADMIN'
                  AND u.status = 'ACTIVE'
                  AND u.deleted_at IS NULL
                ORDER BY u.user_id ASC
                """,
                (rs, rowNum) -> new ReturnCaseDtos.AdminAssigneeOption(
                        rs.getLong("user_id"),
                        rs.getString("full_name"),
                        rs.getString("email")
                ));
    }

    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse createSystemIssue(
            Long actorId,
            ReturnCaseDtos.CreateSystemIssueRequest request
    ) {
        if (actorId == null) {
            throw new TrustSafetyBusinessException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Yêu cầu đăng nhập.");
        }
        if (request == null || request.description() == null || request.description().trim().length() < 10) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Mô tả sự cố hệ thống tối thiểu 10 ký tự.");
        }

        validatePrivateText(request.description());
        if (request.evidenceList() != null) {
            request.evidenceList().stream().filter(Objects::nonNull).forEach(e -> validatePrivateText(e.textContent()));
        }
        int refCount = (request.orderId() != null ? 1 : 0)
                + (request.productId() != null ? 1 : 0)
                + (request.listingChargeId() != null ? 1 : 0)
                + (request.paymentIntentId() != null ? 1 : 0);
        if (refCount > 1) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
                    "Hồ sơ SYSTEM_ISSUE chỉ được gắn tối đa 1 đối tượng tham chiếu (cases_check).");
        }

        String cmdKey = request.commandKey() != null && !request.commandKey().isBlank()
                ? request.commandKey().trim()
                : "SYS_ISSUE:" + actorId + ":" + UUID.randomUUID();
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 64))", Object.class, cmdKey);
        Optional<CaseEntity> existingByCmd = caseRepository.findByCommandKey(cmdKey);
        if (existingByCmd.isPresent()) {
            CaseEntity prior = existingByCmd.get();
            if (!CaseEntity.TYPE_SYSTEM_ISSUE.equals(prior.getCaseType()) || !actorId.equals(prior.getCreatedBy())
                    || !Objects.equals(prior.getOrderId(), request.orderId())
                    || !Objects.equals(prior.getProductId(), request.productId())
                    || !Objects.equals(prior.getListingChargeId(), request.listingChargeId())
                    || !Objects.equals(prior.getPaymentIntentId(), request.paymentIntentId())
                    || !prior.getDescription().equals(request.description().trim())) {
                throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "COMMAND_CONFLICT", "Mã yêu cầu đã được sử dụng.");
            }
            var detail = mapSystemCaseDetail(prior);
            // Replays compare the immutable submission, excluding subsequent supplements.
            var original = caseEvidenceRepository.findByRoundIdAndDeletedAtIsNullOrderBySubmittedAtAsc(detail.currentRound().roundId())
                    .stream().filter(e -> e.getSubmissionNo() == 1)
                    .map(e -> new ReturnCaseDtos.EvidenceItemDto(e.getMediaType(), e.getObjectKey(), e.getTextContent(),
                            e.getContentDigest(), e.getFileSizeBytes(), e.getDurationSeconds())).toList();
            if (!original.equals(request.evidenceList() == null ? List.of() : request.evidenceList())) {
                throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "COMMAND_CONFLICT", "Nội dung gửi lại đã thay đổi.");
            }
            return detail;
        }

        boolean authorizedReference = true;
        if (request.orderId() != null) {
            authorizedReference = exists("SELECT EXISTS(SELECT 1 FROM og_compat.orders WHERE order_id=? AND ? IN (buyer_id,seller_id))",
                    request.orderId(), actorId);
        } else if (request.productId() != null) {
            authorizedReference = exists("SELECT EXISTS(SELECT 1 FROM og_compat.products WHERE product_id=? AND seller_id=?)",
                    request.productId(), actorId);
        } else if (request.listingChargeId() != null) {
            authorizedReference = exists("""
                    SELECT EXISTS(SELECT 1 FROM og_compat.listing_fee_charges f
                    JOIN og_compat.listing_fee_assessments a USING(assessment_id)
                    JOIN og_compat.products p USING(product_id) WHERE f.charge_id=? AND p.seller_id=?)
                    """, request.listingChargeId(), actorId);
        } else if (request.paymentIntentId() != null) {
            authorizedReference = exists("""
                    SELECT EXISTS(SELECT 1 FROM og_compat.payment_intents i
                    LEFT JOIN og_compat.checkout_groups g USING(group_id)
                    LEFT JOIN og_compat.listing_fee_charges f ON f.charge_id=i.listing_charge_id
                    LEFT JOIN og_compat.listing_fee_assessments a USING(assessment_id)
                    LEFT JOIN og_compat.products p USING(product_id)
                    WHERE i.intent_id=? AND ? IN (g.buyer_id,p.seller_id))
                    """, request.paymentIntentId(), actorId);
        }
        if (!authorizedReference) {
            throw new TrustSafetyBusinessException(HttpStatus.NOT_FOUND, "REFERENCE_NOT_FOUND", "Không tìm thấy đối tượng liên quan trong tài khoản.");
        }
        CaseEntity c = new CaseEntity(
                CaseEntity.TYPE_SYSTEM_ISSUE,
                request.orderId(),
                actorId,
                request.description().trim(),
                cmdKey
        );
        c.setProductId(request.productId());
        c.setListingChargeId(request.listingChargeId());
        c.setPaymentIntentId(request.paymentIntentId());
        if (refCount == 0) {
            c.setTargetUserId(actorId);
        }
        CaseEntity saved = caseRepository.saveAndFlush(c);

        CaseRoundEntity round1 = new CaseRoundEntity(saved.getId(), 1, CaseRoundEntity.STAGE_SYSTEM_REVIEW);
        CaseRoundEntity savedRound = caseRoundRepository.saveAndFlush(round1);

        CaseEventEntity submitted = new CaseEventEntity(
                UUID.randomUUID(),
                saved.getId(),
                savedRound.getId(),
                actorId,
                null,
                CaseEventEntity.TYPE_SUBMITTED,
                "Người dùng gửi khiếu nại lỗi hệ thống",
                "{\"caseType\":\"SYSTEM_ISSUE\"}",
                "EVENT:SUBMIT:" + saved.getId()
        );
        caseEventRepository.saveAndFlush(submitted);

        if (request.evidenceList() != null && !request.evidenceList().isEmpty()) {
            for (ReturnCaseDtos.EvidenceItemDto item : request.evidenceList()) {
                if (item == null || item.mediaType() == null) continue;
                CaseEvidenceEntity ev = new CaseEvidenceEntity(
                        UUID.randomUUID(),
                        savedRound.getId(),
                        actorId,
                        1,
                        item.mediaType().trim().toUpperCase(),
                        item.objectKey(),
                        item.textContent(),
                        item.contentDigest(),
                        item.fileSizeBytes(),
                        item.durationSeconds()
                );
                caseEvidenceRepository.saveAndFlush(ev);
            }
        }

        return mapSystemCaseDetail(saved);
    }

    @Transactional(readOnly = true)
    public List<ReturnCaseDtos.ReturnCaseDetailResponse> listSystemIssuesForUser(Long userId) {
        List<Long> caseIds = jdbc.queryForList("""
                SELECT case_id FROM og_compat.cases
                WHERE case_type = 'SYSTEM_ISSUE' AND created_by = ?
                ORDER BY created_at DESC, case_id DESC
                """, Long.class, userId);
        return caseIds.stream()
                .map(caseRepository::findById)
                .flatMap(Optional::stream)
                .map(this::mapSystemCaseDetail)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReturnCaseDtos.ReturnCaseDetailResponse> listSystemIssuesForKtv(long ktvId) {
        List<Long> caseIds = jdbc.queryForList("""
                SELECT case_id FROM og_compat.cases
                WHERE case_type = 'SYSTEM_ISSUE' AND (assigned_to IS NULL OR assigned_to=?)
                ORDER BY CASE WHEN state IN ('OPEN','REVIEWING') THEN 0 ELSE 1 END, created_at DESC, case_id DESC
                LIMIT 100
                """, Long.class, ktvId);
        return caseIds.stream()
                .map(caseRepository::findById)
                .flatMap(Optional::stream)
                .map(c -> c.getAssignedTo() != null ? mapSystemCaseDetail(c) : new ReturnCaseDtos.ReturnCaseDetailResponse(
                        c.getId(), c.getOrderId(), c.getCaseType(), c.getState(), null, null, null, null,
                        c.getCreatedAt(), null, null, null, null, null, List.of(), List.of(), null, List.of(), null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReturnCaseDtos.ReturnCaseDetailResponse> listSystemIssuesForAdmin(Long adminId) {
        List<Long> caseIds = jdbc.queryForList("""
                SELECT c.case_id
                FROM og_compat.cases c
                WHERE c.case_type = 'SYSTEM_ISSUE' AND c.assigned_to=?
                  AND EXISTS (
                      SELECT 1 FROM og_compat.case_events e
                      WHERE e.case_id = c.case_id AND e.event_type = 'ESCALATED'
                  )
                ORDER BY CASE WHEN c.state IN ('OPEN','REVIEWING') THEN 0 ELSE 1 END, c.created_at DESC, c.case_id DESC
                LIMIT 100
                """, Long.class, adminId);
        return caseIds.stream()
                .map(caseRepository::findById)
                .flatMap(Optional::stream)
                .map(this::mapSystemCaseDetail)
                .toList();
    }

    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse ktvEscalateCase(
            Long ktvId,
            Long caseId,
            ReturnCaseDtos.KtvEscalateCaseRequest request
    ) {
        if (ktvId == null || !hasRole(ktvId, "KTV")) {
            throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Yêu cầu vai trò KTV.");
        }
        if (request == null || request.recipientAdminId() == null || !hasRole(request.recipientAdminId(), "ADMIN")) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_ADMIN_RECIPIENT",
                    "Người nhận chuyển cấp bắt buộc phải là tài khoản Quản trị viên (ADMIN) hợp lệ.");
        }

        CaseEntity c = caseRepository.findByIdForUpdate(caseId)
                .orElseThrow(() -> new TrustSafetyBusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy hồ sơ #" + caseId));

        if (CaseEntity.STATE_CLOSED.equals(c.getState()) || CaseEntity.STATE_REJECTED.equals(c.getState())) {
            throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "CASE_CLOSED", "Hồ sơ đã đóng, không thể chuyển cấp.");
        }
        if (c.getAssignedTo() == null) {
            c.setAssignedTo(ktvId);
            caseRepository.saveAndFlush(c);
        } else if (!c.getAssignedTo().equals(ktvId)) {
            throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "NOT_ASSIGNED_KTV",
                    "Hồ sơ đang được giao cho KTV #" + c.getAssignedTo());
        }

        CaseRoundEntity round = caseRoundRepository.findLatestRoundByCaseId(caseId)
                .orElseThrow(() -> new TrustSafetyBusinessException(HttpStatus.CONFLICT, "ROUND_NOT_FOUND", "Không tìm thấy vòng xét hiện tại."));
        if (!CaseRoundEntity.STATE_OPEN.equals(round.getState())) {
            throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "ROUND_DECIDED", "Vòng xét đã kết thúc.");
        }

        String cmdKey = request.commandKey() != null && !request.commandKey().isBlank()
                ? request.commandKey().trim()
                : "EVENT:ESCALATED:" + caseId + ":" + round.getId() + ":" + request.recipientAdminId();

        Optional<CaseEventEntity> existingCmd = caseEventRepository.findByCommandKey(cmdKey);
        if (existingCmd.isPresent()) {
            return mapSystemCaseDetail(c);
        }

        if (caseEventRepository.existsByCaseIdAndRoundIdAndEventType(caseId, round.getId(), CaseEventEntity.TYPE_ESCALATED)) {
            throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "ALREADY_ESCALATED", "Vòng xét hiện tại đã được chuyển cấp lên Admin.");
        }

        CaseEventEntity ev = new CaseEventEntity(
                UUID.randomUUID(),
                caseId,
                round.getId(),
                ktvId,
                request.recipientAdminId(),
                CaseEventEntity.TYPE_ESCALATED,
                request.reason().trim(),
                String.format("{\"escalatedByKtvId\":%d,\"targetAdminId\":%d}", ktvId, request.recipientAdminId()),
                cmdKey
        );
        caseEventRepository.saveAndFlush(ev);
        if (CaseEntity.TYPE_SYSTEM_ISSUE.equals(c.getCaseType())) {
            c.setAssignedTo(request.recipientAdminId());
            caseRepository.saveAndFlush(c);
        }
        return mapSystemCaseDetail(c);
    }

    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse adminHandoverCase(
            Long adminId,
            Long caseId,
            ReturnCaseDtos.AdminHandoverRequest request
    ) {
        if (adminId == null || !hasRole(adminId, "ADMIN")) {
            throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Yêu cầu vai trò ADMIN.");
        }
        if (request == null || request.newAdminId() == null || !hasRole(request.newAdminId(), "ADMIN")) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_ADMIN_RECIPIENT",
                    "Người nhận bàn giao bắt buộc phải có vai trò ADMIN.");
        }
        if (adminId.equals(request.newAdminId())) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Không thể tự bàn giao cho chính mình.");
        }

        CaseEntity c = caseRepository.findByIdForUpdate(caseId)
                .orElseThrow(() -> new TrustSafetyBusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy hồ sơ #" + caseId));
        if (CaseEntity.STATE_CLOSED.equals(c.getState()) || CaseEntity.STATE_REJECTED.equals(c.getState())) {
            throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "CASE_CLOSED", "Hồ sơ đã kết thúc, không thể bàn giao.");
        }

        CaseRoundEntity round = caseRoundRepository.findLatestRoundByCaseId(caseId)
                .orElseThrow(() -> new TrustSafetyBusinessException(HttpStatus.CONFLICT, "ROUND_NOT_FOUND", "Không tìm thấy vòng xét hiện tại."));
        if (!caseEventRepository.existsByCaseIdAndRoundIdAndEventType(caseId, round.getId(), CaseEventEntity.TYPE_ESCALATED)) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "CASE_NOT_ESCALATED", "Chỉ có thể bàn giao Admin khi hồ sơ đã được chuyển cấp.");
        }

        Long designated = resolveDesignatedAdminId(caseId, round.getId());
        if (designated != null && !designated.equals(adminId)) {
            throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "NOT_DESIGNATED_ADMIN",
                    "Chỉ Admin đang phụ trách (#" + designated + ") mới có quyền bàn giao hồ sơ.");
        }

        String cmdKey = request.commandKey() != null && !request.commandKey().isBlank()
                ? request.commandKey().trim()
                : "EVENT:HANDOVER:" + caseId + ":" + round.getId() + ":" + UUID.randomUUID();
        if (caseEventRepository.findByCommandKey(cmdKey).isPresent()) {
            return mapSystemCaseDetail(c);
        }

        CaseEventEntity ev = new CaseEventEntity(
                UUID.randomUUID(),
                caseId,
                round.getId(),
                adminId,
                request.newAdminId(),
                CaseEventEntity.TYPE_ASSIGNED,
                request.reason().trim(),
                String.format("{\"action\":\"ADMIN_HANDOVER\",\"fromAdminId\":%d,\"toAdminId\":%d}", adminId, request.newAdminId()),
                cmdKey
        );
        caseEventRepository.saveAndFlush(ev);
        if (CaseEntity.TYPE_SYSTEM_ISSUE.equals(c.getCaseType())) {
            c.setAssignedTo(request.newAdminId());
            caseRepository.saveAndFlush(c);
        }
        return mapSystemCaseDetail(c);
    }

    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse decideSystemIssue(
            Long actorId,
            boolean actorMustBeAdmin,
            Long caseId,
            ReturnCaseDtos.SystemIssueDecisionRequest request
    ) {
        if (actorId == null) {
            throw new TrustSafetyBusinessException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Yêu cầu đăng nhập.");
        }
        String resolution = request != null && request.resolution() != null ? request.resolution().trim().toUpperCase(Locale.ROOT) : "";
        if (!"CONFIRM_ISSUE".equals(resolution) && !"REJECT_ISSUE".equals(resolution)) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
                    "resolution bắt buộc là CONFIRM_ISSUE hoặc REJECT_ISSUE.");
        }
        String reason = request.reason() != null ? request.reason().trim() : "";
        if (reason.length() < 5) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Lý do quyết định tối thiểu 5 ký tự.");
        }

        CaseEntity c = caseRepository.findByIdForUpdate(caseId)
                .orElseThrow(() -> new TrustSafetyBusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy hồ sơ #" + caseId));
        if (!CaseEntity.TYPE_SYSTEM_ISSUE.equals(c.getCaseType())) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_CASE_TYPE", "Hồ sơ không phải loại SYSTEM_ISSUE.");
        }

        CaseRoundEntity round1 = caseRoundRepository.findByCaseIdAndRoundNo(caseId, 1)
                .orElseThrow(() -> new TrustSafetyBusinessException(HttpStatus.CONFLICT, "ROUND_NOT_FOUND", "Không tìm thấy vòng xét SYSTEM_REVIEW."));

        boolean escalated = caseEventRepository.existsByCaseIdAndRoundIdAndEventType(caseId, round1.getId(), CaseEventEntity.TYPE_ESCALATED);
        if (actorMustBeAdmin) {
            if (!hasRole(actorId, "ADMIN")) {
                throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Yêu cầu quyền ADMIN.");
            }
            if (escalated) {
                Long designated = resolveDesignatedAdminId(caseId, round1.getId());
                if (designated != null && !designated.equals(actorId)) {
                    throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "NOT_DESIGNATED_ADMIN",
                            "Chỉ Admin được chỉ định (#" + designated + ") mới được ra quyết định cuối cùng.");
                }
            }
        } else {
            if (!hasRole(actorId, "KTV")) {
                throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Yêu cầu quyền KTV.");
            }
            if (escalated) {
                throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "CASE_ESCALATED",
                        "Hồ sơ lỗi hệ thống đã chuyển cấp cho Admin quyết định.");
            }
            if (c.getAssignedTo() == null) {
                c.setAssignedTo(actorId);
                caseRepository.saveAndFlush(c);
            } else if (!c.getAssignedTo().equals(actorId)) {
                throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "NOT_ASSIGNED_KTV",
                        "Hồ sơ đang được KTV #" + c.getAssignedTo() + " phụ trách.");
            }
        }

        String cmdKey = request.commandKey() != null && !request.commandKey().isBlank()
                ? request.commandKey().trim()
                : "DECISION:SYS:" + caseId + ":" + actorId + ":" + UUID.randomUUID();
        Optional<CaseDecisionEntity> existingByCmd = caseDecisionRepository.findByCommandKey(cmdKey);
        if (existingByCmd.isPresent()) {
            return mapSystemCaseDetail(c);
        }

        if (pendingRequest(caseId) != null) {
            throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "REQUEST_PENDING", "Đang chờ bổ sung bằng chứng.");
        }
        if (!CaseRoundEntity.STATE_OPEN.equals(round1.getState())) {
            throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "ROUND_DECIDED", "Hồ sơ SYSTEM_ISSUE đã có quyết định cuối cùng.");
        }

        Instant now = clock.instant();
        CaseDecisionEntity decision = new CaseDecisionEntity(
                round1.getId(),
                actorId,
                CaseDecisionEntity.ACTOR_USER,
                CaseDecisionEntity.OUTCOME_SYSTEM_RESOLVED,
                true,
                null,
                reason,
                cmdKey
        );
        decision.setDecidedAt(now);
        caseDecisionRepository.saveAndFlush(decision);

        if ("REJECT_ISSUE".equals(resolution)) {
            jdbc.update("""
                    UPDATE og_compat.cases
                    SET state = 'REJECTED',
                        closed_at = COALESCE((SELECT decided_at FROM og_compat.case_decisions WHERE round_id = ? AND is_final = TRUE LIMIT 1), ?)
                    WHERE case_id = ?
                    """, round1.getId(), Timestamp.from(now), caseId);
        }

        entityManager.flush();
        entityManager.refresh(c);
        entityManager.refresh(round1);

        if ("CONFIRM_ISSUE".equals(resolution) && request.affectedFrom() != null && request.affectedUntil() != null) {
            recordOutageWindowMarker(caseId, round1.getId(), actorId, c.getCreatedBy(), request.affectedFrom(), request.affectedUntil(), reason);
        }

        staffConfirmation(actorId, "UC53_SYSTEM_DECISION", "Đã ghi nhận quyết định", reason, caseId,
                actorMustBeAdmin ? "ADMIN_ESCALATED_CASES" : "KTV_COMPLAINTS");
        systemNotification(c, "Kết quả khiếu nại", ("REJECT_ISSUE".equals(resolution) ? "Từ chối: " : "Chấp nhận: ") + reason);
        return mapSystemCaseDetail(c);
    }

    @Transactional
    public ReturnCaseDtos.CaseCompensationResponse executeCompensation(
            Long actorId,
            boolean actorIsAdmin,
            Long caseId,
            ReturnCaseDtos.CaseCompensationRequest request
    ) {
        if (actorId == null || (!hasRole(actorId, "ADMIN") && (!hasRole(actorId, "KTV") || actorIsAdmin))) {
            throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Không có quyền thực thi bồi thường.");
        }
        String compType = request != null && request.compensationType() != null ? request.compensationType().trim().toUpperCase(Locale.ROOT) : "";
        if (!"VOUCHER".equals(compType) && !"DEADLINE_EXTENSION".equals(compType)) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "compensationType phải là VOUCHER hoặc DEADLINE_EXTENSION.");
        }

        CaseEntity c = caseRepository.findByIdForUpdate(caseId)
                .orElseThrow(() -> new TrustSafetyBusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy hồ sơ #" + caseId));
        if (!CaseEntity.TYPE_SYSTEM_ISSUE.equals(c.getCaseType()) || !CaseEntity.STATE_CLOSED.equals(c.getState())) {
            throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "CASE_NOT_CONFIRMED_SYSTEM_ISSUE",
                    "Chỉ được thực thi bồi thường khi hồ sơ SYSTEM_ISSUE đã được xác nhận chấp thuận (state=CLOSED). Trạng thái hiện tại: " + c.getState());
        }

        CaseRoundEntity round1 = caseRoundRepository.findLatestRoundByCaseId(caseId).orElse(null);
        if (round1 != null && caseEventRepository.existsByCaseIdAndRoundIdAndEventType(caseId, round1.getId(), CaseEventEntity.TYPE_ESCALATED)) {
            if (!hasRole(actorId, "ADMIN")) {
                throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "CASE_ESCALATED", "Hồ sơ đã chuyển Admin; KTV không được tự cấp bồi thường.");
            }
            Long designated = resolveDesignatedAdminId(caseId, round1.getId());
            if (designated != null && !designated.equals(actorId)) {
                throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "NOT_DESIGNATED_ADMIN",
                        "Chỉ Admin được chỉ định (#" + designated + ") mới có quyền cấp bồi thường cho hồ sơ này.");
            }
        }

        String cmdKey = request.commandKey() != null && !request.commandKey().isBlank()
                ? request.commandKey().trim()
                : "EVENT:COMP:" + compType + ":" + caseId + ":" + request.recipientId() + ":" + UUID.randomUUID();
        Optional<CaseEventEntity> existing = caseEventRepository.findByCommandKey(cmdKey);
        if (existing.isPresent()) {
            var event = existing.get();
            if (!caseId.equals(event.getCaseId()) || !actorId.equals(event.getActorId())
                    || !Objects.equals(request.recipientId(),event.getRecipientId())
                    || !CaseEventEntity.TYPE_COMPENSATION.equals(event.getEventType())) {
                throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "COMMAND_CONFLICT", "Mã yêu cầu đã được sử dụng.");
            }
            return new ReturnCaseDtos.CaseCompensationResponse(
                    existing.get().getId(), caseId, compType, request.recipientId(), null, "Đã ghi nhận bồi thường trước đó (idempotent)."
            );
        }

        if (!Objects.equals(request.recipientId(), c.getCreatedBy())) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_RECIPIENT", "Người nhận phải là chủ hồ sơ.");
        }
        reserveCompensation(caseId);
        Instant now = clock.instant();
        UUID eventId = UUID.randomUUID();

        if ("VOUCHER".equals(compType)) {
            if (request.voucherId() == null) {
                throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "voucherId bắt buộc khi bồi thường VOUCHER.");
            }
            VoucherDtos.Campaign campaign = voucherWalletRepository.campaign(request.voucherId(), true)
                    .orElseThrow(() -> new TrustSafetyBusinessException(HttpStatus.NOT_FOUND, "VOUCHER_NOT_FOUND", "Không tìm thấy voucher #" + request.voucherId()));
            if (!campaign.active() || voucherWalletRepository.revisionRevoked(campaign.revisionId())) {
                throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "VOUCHER_UNAVAILABLE", "Voucher đã ngừng phát hành hoặc bị thu hồi.");
            }

            UUID grantId;
            try {
                grantId = voucherWalletRepository.grant(
                        actorId, request.recipientId(), campaign, "COMPENSATION", request.reason().trim(),
                        "COMP_GRANT:" + caseId + ":" + request.recipientId() + ":" + campaign.revisionId(), now
                );
            } catch (DataAccessException ex) {
                throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "DUPLICATE_COMPENSATION",
                        "Người dùng đã được cấp voucher bồi thường này hoặc vượt giới hạn nhận cho sự cố #" + caseId);
            }
            String metadataJson = String.format(
                    "{\"compensationType\":\"VOUCHER\",\"targetRef\":\"%d\",\"voucherId\":%d,\"revisionId\":%d,\"grantId\":\"%s\"}",
                    campaign.revisionId(), campaign.id(), campaign.revisionId(), grantId
            );
            try {
                jdbc.update("""
                        INSERT INTO og_compat.case_events(event_id, case_id, round_id, actor_id, recipient_id, event_type, reason, metadata, command_key)
                        VALUES (?, ?, ?, ?, ?, 'COMPENSATION', ?, ?::jsonb, ?)
                        """, eventId, caseId, round1 != null ? round1.getId() : null, actorId, request.recipientId(),
                        request.reason().trim(), metadataJson, cmdKey);
            } catch (DataAccessException ex) {
                throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "DUPLICATE_COMPENSATION",
                        "Người dùng đã được cấp voucher bồi thường này cho sự cố #" + caseId);
            }
            insertNotificationSafely(
                    request.recipientId(),
                    "UC55_COMPENSATION_VOUCHER",
                    "Bạn nhận được voucher bồi thường từ sự cố #" + caseId,
                    String.format("Voucher %s đã được cấp vào ví voucher của bạn. Lý do: %s", campaign.code(), request.reason().trim()),
                    "VOUCHER",
                    campaign.id(),
                    String.format("{\"destination\":\"VOUCHER_WALLET\",\"caseId\":%d,\"grantId\":\"%s\"}", caseId, grantId)
            );
            if (!actorIsAdmin) {
                staffConfirmation(actorId, "UC55_VOUCHER_GRANTED", "Đã cấp voucher " + campaign.code(),
                        request.reason().trim(), caseId, "KTV_VOUCHERS");
            }
            return new ReturnCaseDtos.CaseCompensationResponse(eventId, caseId, compType, request.recipientId(), grantId,
                    "Đã cấp voucher bồi thường (" + campaign.code() + ") qua UC70 thành công.");
        }

        // DEADLINE_EXTENSION branch
        if (request.targetReturnCaseId() == null || request.affectedFrom() == null || request.affectedUntil() == null) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
                    "DEADLINE_EXTENSION yêu cầu targetReturnCaseId, affectedFrom và affectedUntil.");
        }
        String metadataJson = String.format(
                "{\"compensationType\":\"DEADLINE_EXTENSION\",\"targetRef\":\"%d\",\"affectedFrom\":\"%s\",\"affectedUntil\":\"%s\"}",
                request.targetReturnCaseId(), request.affectedFrom(), request.affectedUntil()
        );
        try {
            jdbc.update("""
                    INSERT INTO og_compat.case_events(event_id, case_id, round_id, actor_id, recipient_id, event_type, reason, metadata, command_key)
                    VALUES (?, ?, ?, ?, ?, 'COMPENSATION', ?, ?::jsonb, ?)
                    """, eventId, caseId, round1 != null ? round1.getId() : null, actorId, request.recipientId(),
                    request.reason().trim(), metadataJson, cmdKey);
        } catch (DataAccessException ex) {
            throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "DUPLICATE_COMPENSATION",
                    "Hồ sơ #" + request.targetReturnCaseId() + " đã được gia hạn bù cho sự cố #" + caseId);
        }
        accountTrustRepository.compensateDeadline(
                actorId, request.recipientId(), request.targetReturnCaseId(), caseId,
                request.affectedFrom(), request.affectedUntil(), request.reason().trim(), cmdKey
        );
        return new ReturnCaseDtos.CaseCompensationResponse(eventId, caseId, compType, request.recipientId(), null,
                "Đã gia hạn bù thời gian phản hồi cho hồ sơ #" + request.targetReturnCaseId());
    }

    private void reserveCompensation(long caseId) {
        String origin = jdbc.queryForObject("SELECT public.og_system_issue_origin(c) FROM og_compat.cases c WHERE case_id=?",
                String.class, caseId);
        // The claimant and business object serialize claims from separate tickets.
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,76))", Object.class, origin);
        var windows = jdbc.queryForList("""
                SELECT (metadata->>'affectedFrom')::timestamptz AS start_at, (metadata->>'affectedUntil')::timestamptz AS end_at
                FROM og_compat.case_events WHERE case_id=? AND metadata->>'compensationType'='OUTAGE_WINDOW'
                """, caseId);
        Timestamp start = windows.isEmpty() ? null : (Timestamp) windows.getFirst().get("start_at");
        Timestamp end = windows.isEmpty() ? null : (Timestamp) windows.getFirst().get("end_at");
        // Distinct confirmed outages can concern the same account. An undated claim
        // cannot establish a separate occurrence from an earlier compensation.
        boolean alreadyPaid = exists("""
                SELECT EXISTS(SELECT 1 FROM og_compat.case_events paid
                JOIN og_compat.cases c USING(case_id)
                LEFT JOIN og_compat.case_events w ON w.case_id=c.case_id AND w.metadata->>'compensationType'='OUTAGE_WINDOW'
                WHERE c.case_type='SYSTEM_ISSUE' AND paid.event_type='COMPENSATION'
                  AND paid.metadata->>'compensationType' IN ('VOUCHER','DEADLINE_EXTENSION')
                  AND public.og_system_issue_origin(c)=?
                  AND (?::timestamptz IS NULL OR w.event_id IS NULL
                       OR ((w.metadata->>'affectedFrom')::timestamptz < ? AND (w.metadata->>'affectedUntil')::timestamptz > ?)))
                """, origin, start, end, start);
        String key = start == null ? origin : origin + "/" + start.toInstant() + "/" + end.toInstant();
        if (alreadyPaid || jdbc.update("""
                INSERT INTO public.system_issue_compensation_claims(incident_key,case_id) VALUES (?,?)
                ON CONFLICT (incident_key) DO NOTHING
                """, key, caseId) == 0) {
            throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "DUPLICATE_COMPENSATION", "Sự việc đã được bồi thường.");
        }
    }

    private void recordOutageWindowMarker(Long caseId, Long roundId, Long actorId, Long recipientId, Instant from, Instant until, String reason) {
        if (!from.isBefore(until)) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_WINDOW", "Thời điểm kết thúc phải sau thời điểm bắt đầu.");
        }
        String key = "EVENT:OUTAGE_WINDOW:" + caseId;
        if (caseEventRepository.findByCommandKey(key).isPresent()) {
            return;
        }
        String meta = String.format(
                "{\"compensationType\":\"OUTAGE_WINDOW\",\"targetRef\":\"%s\",\"affectedFrom\":\"%s\",\"affectedUntil\":\"%s\"}",
                from.toString(), from, until
        );
        jdbc.update("""
                INSERT INTO og_compat.case_events(event_id, case_id, round_id, actor_id, recipient_id, event_type, reason, metadata, command_key)
                VALUES (?, ?, ?, ?, ?, 'COMPENSATION', ?, ?::jsonb, ?)
                """, UUID.randomUUID(), caseId, roundId, actorId, recipientId, reason, meta, key);
    }

    private Long resolveDesignatedAdminId(Long caseId, Long roundId) {
        List<CaseEventEntity> events = caseEventRepository.findByCaseIdOrderByOccurredAtAsc(caseId);
        Long latestHandover = null;
        Long escRecipient = null;
        for (CaseEventEntity ev : events) {
            if (!roundId.equals(ev.getRoundId())) continue;
            if (CaseEventEntity.TYPE_ESCALATED.equals(ev.getEventType()) && ev.getRecipientId() != null) {
                escRecipient = ev.getRecipientId();
            } else if (CaseEventEntity.TYPE_ASSIGNED.equals(ev.getEventType())
                    && ev.getActorId() != null && ev.getRecipientId() != null && hasRole(ev.getActorId(), "ADMIN")) {
                latestHandover = ev.getRecipientId();
            }
        }
        return latestHandover != null ? latestHandover : escRecipient;
    }

    private CaseEntity openIssue(long caseId) {
        CaseEntity c = caseRepository.findByIdForUpdate(caseId)
                .filter(row -> CaseEntity.TYPE_SYSTEM_ISSUE.equals(row.getCaseType()))
                .orElseThrow(() -> new TrustSafetyBusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy hồ sơ."));
        if (!Set.of("OPEN", "REVIEWING").contains(c.getState())) {
            throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "CASE_CLOSED", "Hồ sơ đã đóng.");
        }
        return c;
    }

    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse receive(long actor, long caseId) {
        CaseEntity c = openIssue(caseId);
        if (c.getAssignedTo() != null && !c.getAssignedTo().equals(actor)) {
            throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "NOT_ASSIGNED", "Hồ sơ đã được bàn giao.");
        }
        if (!"REVIEWING".equals(c.getState())) {
            c.setAssignedTo(actor);
            c.setState("REVIEWING");
            caseRepository.saveAndFlush(c);
            addProgress(c, actor, "ASSIGNED", "RECEIVED", "Đã tiếp nhận xem xét", "UC64:RECEIVE:" + caseId);
            systemNotification(c, "Đang xem xét khiếu nại", "KTV đã tiếp nhận hồ sơ.");
        }
        return mapSystemCaseDetail(c);
    }

    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse requestInformation(long actor, long caseId, String note) {
        CaseEntity c = openIssue(caseId);
        if (!Objects.equals(c.getAssignedTo(), actor) || !"REVIEWING".equals(c.getState())) {
            throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "NOT_ASSIGNED", "Chỉ KTV đang phụ trách được yêu cầu bổ sung.");
        }
        if (pendingRequest(caseId) != null) {
            throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "REQUEST_PENDING", "Đang chờ người gửi bổ sung.");
        }
        addProgress(c, actor, "SUPPLEMENTED", "INFORMATION_REQUEST", note, "UC64:REQUEST:" + UUID.randomUUID());
        systemNotification(c, "Cần bổ sung khiếu nại", note);
        return mapSystemCaseDetail(c);
    }

    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse supplement(long actor, long caseId, UUID requestId, String note) {
        CaseEntity c = openIssue(caseId);
        if (!Objects.equals(c.getCreatedBy(), actor)) {
            throw new TrustSafetyBusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy hồ sơ.");
        }
        validatePrivateText(note);
        if (requestId == null || !requestId.equals(pendingRequest(caseId))) {
            throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "REQUEST_CLOSED", "Yêu cầu bổ sung đã thay đổi.");
        }
        var round = caseRoundRepository.findByCaseIdAndRoundNo(caseId, 1).orElseThrow();
        int seq = jdbc.queryForObject("SELECT coalesce(max(submission_no),1)+1 FROM og_compat.case_evidence WHERE round_id=?", Integer.class, round.getId());
        caseEvidenceRepository.saveAndFlush(new CaseEvidenceEntity(UUID.randomUUID(), round.getId(), actor, seq,
                "TEXT", null, note, null, null, null));
        addProgress(c, actor, "SUPPLEMENTED", "INFORMATION_RECEIVED", "Đã bổ sung bằng chứng", "UC64:ANSWER:" + requestId);
        return mapSystemCaseDetail(c);
    }

    private UUID pendingRequest(long caseId) {
        return jdbc.queryForList("""
                SELECT e.event_id FROM og_compat.case_events e WHERE e.case_id=?
                AND e.metadata->>'action'='INFORMATION_REQUEST'
                AND NOT EXISTS (SELECT 1 FROM og_compat.case_events a WHERE a.command_key='UC64:ANSWER:' || e.event_id)
                """, UUID.class, caseId).stream().findFirst().orElse(null);
    }

    private void addProgress(CaseEntity c, long actor, String type, String action, String note, String key) {
        jdbc.update("""
                INSERT INTO og_compat.case_events(event_id,case_id,actor_id,event_type,reason,metadata,command_key)
                VALUES (?,?,?,?,?,jsonb_build_object('action',?::text),?)
                """, UUID.randomUUID(), c.getId(), actor, type, note, action, key);
    }

    private static final java.util.regex.Pattern PRIVATE_SECRET = java.util.regex.Pattern.compile(
            "(?iu)(?:\\b(?:otp|mã xác thực)\\s*(?:là\\s*|[:=]\\s*)?[0-9]{4,8}\\b|(?:mật khẩu|password)\\s*(?:là\\s+|[:=]\\s*)\\S+)");
    private static void validatePrivateText(String value) {
        if (value != null && PRIVATE_SECRET.matcher(value).find()) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "PRIVATE_SECRET", "Hãy xóa mật khẩu hoặc mã OTP trước khi gửi.");
        }
    }

    private void staffConfirmation(long actor, String type, String title, String reason, long caseId, String destination) {
        jdbc.update("""
                INSERT INTO og_compat.notifications(user_id,type,title,content,reference_type,reference_id,is_read,created_at,target_data)
                VALUES (?,?,?,?,'CASE',?,false,CURRENT_TIMESTAMP,jsonb_build_object('destination',?::text))
                """, actor,type,title,reason,caseId,destination);
    }

    private void systemNotification(CaseEntity c, String title, String content) {
        jdbc.update("""
                INSERT INTO og_compat.notifications(user_id,type,title,content,reference_type,reference_id,is_read,created_at,target_data)
                VALUES (?,'UC49_SYSTEM_ISSUE',?,?,'CASE',?,false,CURRENT_TIMESTAMP,'{"destination":"SYSTEM_COMPLAINTS"}'::jsonb)
                """, c.getCreatedBy(), title, content, c.getId());
    }

    private boolean exists(String sql, Object... arguments) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, arguments));
    }

    private boolean hasRole(Long userId, String role) {
        if (userId == null) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT og_has_role(?, ?)", Boolean.class, userId, role));
    }

    private void insertNotificationSafely(long userId, String type, String title, String content, String refType, Long refId, String targetData) {
        try {
            jdbc.update("""
                    INSERT INTO og_compat.notifications(user_id, type, title, content, reference_type, reference_id, is_read, created_at, target_data)
                    VALUES (?, ?, ?, ?, ?, ?, false, ?, ?::jsonb)
                    """, userId, type, title, content, refType, refId, Timestamp.from(clock.instant()), targetData);
        } catch (Exception ignored) {
        }
    }

    private ReturnCaseDtos.ReturnCaseDetailResponse mapSystemCaseDetail(CaseEntity c) {
        CaseRoundEntity latestRound = caseRoundRepository.findLatestRoundByCaseId(c.getId()).orElse(null);
        ReturnCaseDtos.CaseRoundDto roundDto = latestRound == null ? null : new ReturnCaseDtos.CaseRoundDto(
                latestRound.getId(), latestRound.getCaseId(), latestRound.getRoundNo(),
                latestRound.getStage(), latestRound.getState(), latestRound.getOpenedAt(),
                latestRound.getCompleteSubmissionAt(), latestRound.getKtvDueAt(), latestRound.getValidDamageRequestAt()
        );
        List<ReturnCaseDtos.CaseEvidenceDto> evList = latestRound == null ? List.of() :
                caseEvidenceRepository.findByRoundIdAndDeletedAtIsNullOrderBySubmittedAtAsc(latestRound.getId()).stream()
                        .map(e -> new ReturnCaseDtos.CaseEvidenceDto(
                                e.getId(), e.getRoundId(), e.getSubmittedBy(), e.getSubmissionNo(),
                                e.getMediaType(), e.getObjectKey(), e.getTextContent(),
                                e.getFileSizeBytes(), e.getDurationSeconds(), e.getSubmittedAt()
                        )).toList();
        List<ReturnCaseDtos.CaseDecisionPublicDto> decList = latestRound == null ? List.of() :
                caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(latestRound.getId()).stream()
                        .map(d -> new ReturnCaseDtos.CaseDecisionPublicDto(
                                d.getId(), d.getRoundId(), d.getOutcome(), d.getGoodsRefundAmount(),
                                d.getReason(), d.getDecidedAt(), d.getIsFinal()
                        )).toList();
        List<ReturnCaseDtos.CaseEventDto> eventList = caseEventRepository.findByCaseIdOrderByOccurredAtAsc(c.getId()).stream()
                .map(ev -> new ReturnCaseDtos.CaseEventDto(
                        ev.getId(), ev.getCaseId(), ev.getRoundId(), ev.getActorId(),
                        ev.getRecipientId(), ev.getEventType(), ev.getReason(), ev.getMetadata(), ev.getOccurredAt()
                )).toList();

        return new ReturnCaseDtos.ReturnCaseDetailResponse(
                c.getId(), c.getOrderId(), c.getCaseType(), c.getState(),
                null, c.getDescription(), c.getDescription(), c.getCreatedBy(),
                c.getCreatedAt(), c.getApprovedAt(), c.getValidReturnedAt(),
                c.getSellerResponseDueAt(), c.getClosedAt(), roundDto,
                evList, eventList, null, decList, c.getAssignedTo()
        );
    }
}

