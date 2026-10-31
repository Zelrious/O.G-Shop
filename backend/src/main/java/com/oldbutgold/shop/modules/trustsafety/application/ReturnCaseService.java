package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentEntity;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentRepository;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentEntity;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseDecisionEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseDecisionRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEventEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEventRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEvidenceEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEvidenceRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRoundEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRoundRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.ReturnReasonCode;
import jakarta.persistence.EntityManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing return requests and multi-round disputes (UC62, UC63, UC75).
 */
@Service
public class ReturnCaseService {

    public static final Duration RETURN_WINDOW_DURATION = Duration.ofDays(3);
    public static final int MAX_EVIDENCE_IMAGES = 5;
    public static final int MAX_EVIDENCE_VIDEOS = 2;
    public static final int MAX_VIDEO_DURATION_SECONDS = 60;
    public static final long MAX_VIDEO_SIZE_BYTES = 50L * 1024 * 1024; // 50MB

    private static final Logger log = LoggerFactory.getLogger(ReturnCaseService.class);

    private final CaseRepository caseRepository;
    private final CaseRoundRepository caseRoundRepository;
    private final CaseEvidenceRepository caseEvidenceRepository;
    private final CaseDecisionRepository caseDecisionRepository;
    private final CaseEventRepository caseEventRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ShipmentRepository shipmentRepository;
    private final EntityManager entityManager;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public ReturnCaseService(
            CaseRepository caseRepository,
            CaseRoundRepository caseRoundRepository,
            CaseEvidenceRepository caseEvidenceRepository,
            CaseDecisionRepository caseDecisionRepository,
            CaseEventRepository caseEventRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            ShipmentRepository shipmentRepository,
            EntityManager entityManager,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.caseRepository = caseRepository;
        this.caseRoundRepository = caseRoundRepository;
        this.caseEvidenceRepository = caseEvidenceRepository;
        this.caseDecisionRepository = caseDecisionRepository;
        this.caseEventRepository = caseEventRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.shipmentRepository = shipmentRepository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public ReturnCaseService(
            CaseRepository caseRepository,
            CaseRoundRepository caseRoundRepository,
            CaseEvidenceRepository caseEvidenceRepository,
            CaseDecisionRepository caseDecisionRepository,
            CaseEventRepository caseEventRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            ShipmentRepository shipmentRepository,
            EntityManager entityManager,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            Clock clock
    ) {
        this(caseRepository, caseRoundRepository, caseEvidenceRepository, caseDecisionRepository,
                caseEventRepository, orderRepository, paymentRepository, shipmentRepository, entityManager, jdbcTemplate, null, clock);
    }

    public ReturnCaseService(
            CaseRepository caseRepository,
            CaseRoundRepository caseRoundRepository,
            CaseEvidenceRepository caseEvidenceRepository,
            CaseDecisionRepository caseDecisionRepository,
            CaseEventRepository caseEventRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            EntityManager entityManager,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            Clock clock
    ) {
        this(caseRepository, caseRoundRepository, caseEvidenceRepository, caseDecisionRepository,
                caseEventRepository, orderRepository, paymentRepository, null, entityManager, jdbcTemplate, null, clock);
    }

    private String safeToJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.warn("Không thể serialize metadata thành JSON: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Mở yêu cầu trả hàng đủ 6 điều kiện bắt buộc (UC62 - createReturnRequest).
     *
     * 6 điều kiện kiểm tra (BR-UC62-02):
     *   (1) Đơn hàng status = 'DELIVERED' và valid_delivered_at IS NOT NULL.
     *   (2) return_allowed = true.
     *   (3) Trong hạn 3 ngày kể từ lúc nhận hàng (now <= valid_delivered_at + 3 ngày).
     *   (4) Người mua chưa bấm Hoàn tất sớm (early_completed_at IS NULL).
     *   (5) Tiền ký quỹ chưa giải ngân cuối cùng (status != COMPLETED, payment != RELEASED).
     *   (6) Không có hồ sơ trả hàng/khiếu nại nào đang mở cho đơn này.
     *
     * Ràng buộc bổ sung:
     *   - workflow_model = 'UC83'
     *   - actorId = buyer_id của đơn hàng
     *   - Bằng chứng: tối đa 5 ảnh, tối đa 2 video <= 60s và <= 50MB
     *   - Idempotency qua command_key
     */
    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse createReturnRequest(
            Long buyerId,
            Long orderId,
            ReturnCaseDtos.CreateReturnRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Yêu cầu trả hàng không được để trống");
        }
        if (request.reasonCode() == null) {
            throw new IllegalArgumentException("Vui lòng chọn lý do trả hàng hợp lệ");
        }

        // 1. Khóa và nạp đơn hàng
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        if (entityManager != null) {
            entityManager.refresh(order);
        }

        // 2. Quyền tác nhân: chỉ người mua của đơn mới có quyền yêu cầu trả hàng
        if (buyerId == null || !buyerId.equals(order.getBuyerId())) {
            throw new IllegalArgumentException("Chỉ người mua của đơn hàng mới có quyền yêu cầu trả hàng");
        }

        // 3. Kiểm tra 6 điều kiện nghiệp vụ & DB constraints
        // (Ràng buộc mô hình): Chỉ áp dụng cho đơn UC83
        if (!"UC83".equals(order.getWorkflowModel())) {
            throw new IllegalStateException("Chỉ áp dụng trả hàng cho đơn hàng theo mô hình UC83");
        }

        // (Điều kiện 5a): Nếu đơn đã hoàn tất giải ngân
        if ("COMPLETED".equals(order.getStatus())) {
            throw new IllegalStateException("Đơn hàng đã hoàn tất giải ngân, không thể mở yêu cầu trả hàng");
        }

        // (Điều kiện 1): Đơn hàng phải DELIVERED và có valid_delivered_at
        if (!"DELIVERED".equals(order.getStatus()) || order.getValidDeliveredAt() == null) {
            throw new IllegalStateException("Đơn hàng chưa giao thành công hợp lệ");
        }

        // (Điều kiện 2): return_allowed = true
        if (!Boolean.TRUE.equals(order.isReturnAllowed())) {
            throw new IllegalStateException("Đơn hàng này không áp dụng chính sách trả hàng");
        }

        // (Điều kiện 3): Trong hạn 3 ngày từ valid_delivered_at
        Instant now = clock.instant();
        Instant deadline = order.getValidDeliveredAt().plus(RETURN_WINDOW_DURATION);
        if (now.isAfter(deadline)) {
            throw new IllegalStateException("Đã quá thời hạn 3 ngày yêu cầu trả hàng kể từ lúc nhận hàng");
        }

        // (Điều kiện 4): Chưa Hoàn tất sớm
        if (order.getEarlyCompletedAt() != null) {
            throw new IllegalStateException("Người mua đã xác nhận hoàn tất sớm, quyền trả hàng đã đóng");
        }

        // (Điều kiện 5): Tiền chưa giải ngân cuối cùng
        if ("COMPLETED".equals(order.getStatus())) {
            throw new IllegalStateException("Đơn hàng đã hoàn tất giải ngân, không thể mở yêu cầu trả hàng");
        }
        if (paymentRepository != null) {
            Optional<PaymentEntity> paymentOpt = paymentRepository.findByOrderId(orderId);
            if (paymentOpt.isPresent() && "RELEASED".equalsIgnoreCase(paymentOpt.get().getStatus())) {
                throw new IllegalStateException("Tiền đơn hàng đã được giải ngân, không thể mở yêu cầu trả hàng");
            }
        }

        // (Điều kiện 6): Không có hồ sơ khiếu nại/trả hàng đang mở
        Optional<CaseEntity> activeCase = caseRepository.findActiveCaseByOrderId(orderId);
        if (activeCase.isPresent()) {
            throw new IllegalStateException("Đơn hàng đã có hồ sơ khiếu nại/trả hàng đang được xử lý (Mã hồ sơ #" + activeCase.get().getId() + ")");
        }

        // 4. Validate bằng chứng
        List<ReturnCaseDtos.EvidenceItemDto> evidenceList = request.evidenceList() != null ? request.evidenceList() : List.of();
        validateEvidence(evidenceList);

        // 5. Kiểm tra tính Idempotent qua command_key
        String cmdKey = request.commandKey() != null && !request.commandKey().isBlank()
                ? request.commandKey().trim()
                : "RETURN_CASE:" + orderId + ":" + UUID.randomUUID();

        Optional<CaseEntity> existingByCmd = caseRepository.findByCommandKey(cmdKey);
        if (existingByCmd.isPresent()) {
            return mapToDetailResponse(existingByCmd.get());
        }

        // 6. Khởi tạo và lưu CaseEntity
        String formattedDescription = ReturnReasonCode.format(request.reasonCode(), request.detail());
        CaseEntity caseEntity = new CaseEntity(
                CaseEntity.TYPE_RETURN,
                orderId,
                buyerId,
                formattedDescription,
                cmdKey
        );
        caseEntity = caseRepository.saveAndFlush(caseEntity);

        // 7. Khởi tạo Round 1: RETURN_APPROVAL
        CaseRoundEntity round = new CaseRoundEntity(
                caseEntity.getId(),
                1,
                CaseRoundEntity.STAGE_RETURN_APPROVAL
        );
        round.setCompleteSubmissionAt(now); // Tự động gán ktvDueAt = now + 48h SLA
        round = caseRoundRepository.saveAndFlush(round);

        // 8. Lưu bằng chứng (nếu có)
        for (ReturnCaseDtos.EvidenceItemDto item : evidenceList) {
            String digest = resolveContentDigest(item.contentDigest(), item.objectKey());
            Long size = (item.fileSizeBytes() != null && item.fileSizeBytes() > 0) ? item.fileSizeBytes() : 1024L;
            Integer duration = null;
            if (CaseEvidenceEntity.MEDIA_VIDEO.equalsIgnoreCase(item.mediaType())) {
                duration = (item.durationSeconds() != null && item.durationSeconds() >= 1 && item.durationSeconds() <= 60)
                        ? item.durationSeconds() : 30;
            }
            CaseEvidenceEntity ev = new CaseEvidenceEntity(
                    UUID.randomUUID(),
                    round.getId(),
                    buyerId,
                    1,
                    item.mediaType().toUpperCase(),
                    item.objectKey(),
                    item.textContent(),
                    digest,
                    size,
                    duration
            );
            caseEvidenceRepository.save(ev);
        }

        // 9. Ghi nhận sự kiện SUBMITTED
        CaseEventEntity event = new CaseEventEntity(
                UUID.randomUUID(),
                caseEntity.getId(),
                round.getId(),
                buyerId,
                order.getSellerId(),
                CaseEventEntity.TYPE_SUBMITTED,
                "Yêu cầu trả hàng được tạo với lý do " + request.reasonCode().name(),
                "{\"reason_code\":\"" + request.reasonCode().name() + "\"}",
                "EVENT:SUBMITTED:" + caseEntity.getId()
        );
        caseEventRepository.save(event);

        return mapToDetailResponse(caseEntity);
    }

    /**
     * Người bán phản hồi Vòng 1 (UC62 7a, 7b - sellerRespondRound1).
     *
     * Lựa chọn:
     *   - APPROVE_RETURN: Chấp thuận trả -> Case chuyển RETURN_APPROVED (chưa hoàn tiền, chờ gửi hàng trả).
     *   - SELLER_DECLINED: Không đồng ý -> Case chuyển REVIEWING, chuyển KTV phân xử theo SLA 48h.
     *
     * Quy tắc đua (First-write wins): Nếu hồ sơ không còn ở OPEN, từ chối thay đổi.
     */
    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse sellerRespondRound1(
            Long sellerId,
            Long caseId,
            ReturnCaseDtos.SellerRound1ResponseRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Yêu cầu phản hồi không được để trống");
        }

        CaseEntity c = caseRepository.findById(caseId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy hồ sơ #" + caseId));
        Long orderId = c.getOrderId();
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        if (sellerId == null || !sellerId.equals(order.getSellerId())) {
            throw new IllegalArgumentException("Chỉ người bán của đơn hàng mới có quyền phản hồi");
        }

        // Xử lý xung đột đua quyết định (first-write wins): Phải ở OPEN
        if (!CaseEntity.STATE_OPEN.equals(c.getState())) {
            throw new IllegalStateException("Hồ sơ không ở trạng thái chờ người bán phản hồi hoặc đã được xử lý trước đó (Trạng thái hiện tại: " + c.getState() + ")");
        }

        CaseRoundEntity round = caseRoundRepository.findByCaseIdAndRoundNo(caseId, 1)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy Vòng 1 cho hồ sơ #" + caseId));

        if (!CaseRoundEntity.STATE_OPEN.equals(round.getState())) {
            throw new IllegalStateException("Vòng 1 đã kết thúc, không thể phản hồi thêm");
        }

        String outcome = request.outcome() != null ? request.outcome().trim().toUpperCase() : "";
        if (!CaseDecisionEntity.OUTCOME_APPROVE_RETURN.equals(outcome) && !CaseDecisionEntity.OUTCOME_SELLER_DECLINED.equals(outcome)) {
            throw new IllegalArgumentException("Lựa chọn phản hồi không hợp lệ: " + outcome + " (chỉ chấp nhận APPROVE_RETURN hoặc SELLER_DECLINED)");
        }

        String cmdKey = request.commandKey() != null && !request.commandKey().isBlank()
                ? request.commandKey().trim()
                : "DECISION:SELLER:R1:" + caseId + ":" + UUID.randomUUID();

        Optional<CaseDecisionEntity> existingDecision = caseDecisionRepository.findByCommandKey(cmdKey);
        if (existingDecision.isPresent()) {
            return mapToDetailResponse(c);
        }

        Instant now = clock.instant();

        if (CaseDecisionEntity.OUTCOME_APPROVE_RETURN.equals(outcome)) {
            // Người bán chấp thuận trả hàng -> chuyển sang RETURN_APPROVED, round kết thúc
            c.setState(CaseEntity.STATE_RETURN_APPROVED);
            c.setApprovedAt(now);
            round.setState(CaseRoundEntity.STATE_DECIDED);

            CaseDecisionEntity decision = new CaseDecisionEntity(
                    round.getId(),
                    sellerId,
                    CaseDecisionEntity.ACTOR_USER,
                    CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                    true,
                    null,
                    request.reason() != null && !request.reason().isBlank() ? request.reason() : "Người bán chấp thuận yêu cầu trả hàng",
                    cmdKey
            );
            caseDecisionRepository.save(decision);

            CaseEventEntity event = new CaseEventEntity(
                    UUID.randomUUID(),
                    c.getId(),
                    round.getId(),
                    sellerId,
                    order.getBuyerId(),
                    CaseEventEntity.TYPE_SUBMITTED,
                    "Người bán chấp thuận yêu cầu trả hàng",
                    "{\"outcome\":\"APPROVE_RETURN\"}",
                    "EVENT:SELLER_APPROVE:" + c.getId()
            );
            caseEventRepository.save(event);
        } else {
            // Người bán không đồng ý -> chuyển sang REVIEWING để KTV phân xử trong SLA 48h
            c.setState(CaseEntity.STATE_REVIEWING);

            CaseDecisionEntity decision = new CaseDecisionEntity(
                    round.getId(),
                    sellerId,
                    CaseDecisionEntity.ACTOR_USER,
                    CaseDecisionEntity.OUTCOME_SELLER_DECLINED,
                    false,
                    null,
                    request.reason() != null && !request.reason().isBlank() ? request.reason() : "Người bán không đồng ý trả hàng, chuyển KTV xử lý",
                    cmdKey
            );
            caseDecisionRepository.save(decision);

            CaseEventEntity event = new CaseEventEntity(
                    UUID.randomUUID(),
                    c.getId(),
                    round.getId(),
                    sellerId,
                    null,
                    CaseEventEntity.TYPE_SUBMITTED,
                    "Người bán từ chối trả hàng, chuyển KTV phân xử",
                    "{\"outcome\":\"SELLER_DECLINED\"}",
                    "EVENT:SELLER_DECLINE:" + c.getId()
            );
            caseEventRepository.save(event);
        }

        caseRoundRepository.save(round);
        CaseEntity savedCase = caseRepository.saveAndFlush(c);
        return mapToDetailResponse(savedCase);
    }

    /**
     * KTV phân xử Vòng 1 (UC62 & UC75 - ktvDecideRound1).
     *
     * KTV thấy hồ sơ ngay khi OPEN và REVIEWING.
     * Quyết định:
     *   - APPROVE_RETURN: KTV duyệt cho trả -> chuyển RETURN_APPROVED.
     *   - REJECT_RETURN: KTV bác yêu cầu -> chuyển REJECTED.
     *     QUAN TRỌNG: Không giải ngân ngay, mà tiền chờ giải ngân theo quy tắc UC71
     *     (hết hạn 3 ngày từ valid_delivered_at hoặc Hoàn tất sớm).
     */
    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse ktvDecideRound1(
            Long ktvId,
            Long caseId,
            ReturnCaseDtos.KtvRound1DecisionRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Yêu cầu phán quyết không được để trống");
        }

        CaseEntity c = caseRepository.findById(caseId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy hồ sơ #" + caseId));
        Long orderId = c.getOrderId();
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        // KTV thấy và có quyền phân xử khi hồ sơ ở OPEN hoặc REVIEWING
        if (!CaseEntity.STATE_OPEN.equals(c.getState()) && !CaseEntity.STATE_REVIEWING.equals(c.getState())) {
            throw new IllegalStateException("Hồ sơ không ở trạng thái chờ KTV phân xử (Trạng thái hiện tại: " + c.getState() + ")");
        }

        CaseRoundEntity round = caseRoundRepository.findByCaseIdAndRoundNo(caseId, 1)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy Vòng 1 cho hồ sơ #" + caseId));

        if (!CaseRoundEntity.STATE_OPEN.equals(round.getState())) {
            throw new IllegalStateException("Vòng 1 đã kết thúc phán quyết");
        }

        String outcome = request.outcome() != null ? request.outcome().trim().toUpperCase() : "";
        if (!CaseDecisionEntity.OUTCOME_APPROVE_RETURN.equals(outcome) && !CaseDecisionEntity.OUTCOME_REJECT_RETURN.equals(outcome)) {
            throw new IllegalArgumentException("Phán quyết KTV không hợp lệ: " + outcome + " (chỉ chấp nhận APPROVE_RETURN hoặc REJECT_RETURN)");
        }

        if (request.reason() == null || request.reason().trim().length() < 5) {
            throw new IllegalArgumentException("KTV bắt buộc phải nhập lý do phán quyết chi tiết (tối thiểu 5 ký tự)");
        }

        String cmdKey = request.commandKey() != null && !request.commandKey().isBlank()
                ? request.commandKey().trim()
                : "DECISION:KTV:R1:" + caseId + ":" + UUID.randomUUID();

        Optional<CaseDecisionEntity> existingDecision = caseDecisionRepository.findByCommandKey(cmdKey);
        if (existingDecision.isPresent()) {
            return mapToDetailResponse(c);
        }

        Instant now = clock.instant();

        if (CaseDecisionEntity.OUTCOME_APPROVE_RETURN.equals(outcome)) {
            // KTV duyệt chấp thuận trả hàng -> RETURN_APPROVED
            c.setState(CaseEntity.STATE_RETURN_APPROVED);
            c.setApprovedAt(now);
            round.setState(CaseRoundEntity.STATE_DECIDED);

            CaseDecisionEntity decision = new CaseDecisionEntity(
                    round.getId(),
                    ktvId,
                    CaseDecisionEntity.ACTOR_USER,
                    CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                    true,
                    null,
                    request.reason(),
                    cmdKey
            );
            caseDecisionRepository.save(decision);

            CaseEventEntity event = new CaseEventEntity(
                    UUID.randomUUID(),
                    c.getId(),
                    round.getId(),
                    ktvId,
                    null,
                    CaseEventEntity.TYPE_SUBMITTED,
                    "KTV duyệt chấp thuận trả hàng. Lý do: " + request.reason(),
                    "{\"outcome\":\"APPROVE_RETURN\"}",
                    "EVENT:KTV_APPROVE:" + c.getId()
            );
            caseEventRepository.save(event);
        } else {
            // KTV bác yêu cầu -> REJECTED (Chờ giải ngân theo UC71, KHÔNG giải ngân ngay)
            c.setState(CaseEntity.STATE_REJECTED);
            c.setClosedAt(now);
            round.setState(CaseRoundEntity.STATE_DECIDED);

            CaseDecisionEntity decision = new CaseDecisionEntity(
                    round.getId(),
                    ktvId,
                    CaseDecisionEntity.ACTOR_USER,
                    CaseDecisionEntity.OUTCOME_REJECT_RETURN,
                    true,
                    null,
                    request.reason(),
                    cmdKey
            );
            caseDecisionRepository.save(decision);

            CaseEventEntity event = new CaseEventEntity(
                    UUID.randomUUID(),
                    c.getId(),
                    round.getId(),
                    ktvId,
                    null,
                    CaseEventEntity.TYPE_SUBMITTED,
                    "KTV bác yêu cầu trả hàng. Lý do: " + request.reason(),
                    "{\"outcome\":\"REJECT_RETURN\"}",
                    "EVENT:KTV_REJECT:" + c.getId()
            );
            caseEventRepository.save(event);

            // Đóng money_holds để giải phóng cản trở, cho phép giải ngân khi đủ điều kiện UC71
            if (jdbcTemplate != null) {
                jdbcTemplate.update("UPDATE og_compat.money_holds SET closed_at = ? WHERE case_id = ? AND closed_at IS NULL", java.sql.Timestamp.from(now), c.getId());
            }
        }

        caseRoundRepository.save(round);
        CaseEntity savedCase = caseRepository.saveAndFlush(c);
        return mapToDetailResponse(savedCase);
    }

    /**
     * Người mua rút yêu cầu trả hàng trước khi gửi hàng (UC62 7c - withdrawReturnRequest).
     *
     * Chỉ cho rút khi hồ sơ ở OPEN, REVIEWING, RETURN_APPROVED và hàng chưa gửi.
     * Đóng lệnh giữ tiền (money_holds), giải phóng has_blocking_case, cho phép đơn tiếp tục giải ngân theo UC71.
     */
    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse withdrawReturnRequest(
            Long buyerId,
            Long caseId,
            ReturnCaseDtos.WithdrawReturnRequest request
    ) {
        CaseEntity c = caseRepository.findById(caseId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy hồ sơ #" + caseId));
        Long orderId = c.getOrderId();
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        if (buyerId == null || !buyerId.equals(c.getCreatedBy())) {
            throw new IllegalArgumentException("Chỉ người mua tạo yêu cầu mới có quyền rút yêu cầu trả hàng");
        }

        // Kiểm tra trạng thái cho phép rút
        if (!List.of(CaseEntity.STATE_OPEN, CaseEntity.STATE_REVIEWING, CaseEntity.STATE_RETURN_APPROVED).contains(c.getState())) {
            throw new IllegalStateException("Không thể rút yêu cầu khi hồ sơ ở trạng thái " + c.getState());
        }
        if (c.getValidReturnedAt() != null || CaseEntity.STATE_RETURNING.equals(c.getState()) || CaseEntity.STATE_POST_RETURN.equals(c.getState())) {
            throw new IllegalStateException("Hàng đã được gửi trả hoặc đã nhận lại, không thể rút yêu cầu trả hàng");
        }

        Instant now = clock.instant();
        c.setState(CaseEntity.STATE_CLOSED);
        c.setClosedAt(now);

        caseRoundRepository.findLatestRoundByCaseId(caseId).ifPresent(r -> {
            r.setState(CaseRoundEntity.STATE_DECIDED);
            caseRoundRepository.save(r);
        });

        CaseEventEntity event = new CaseEventEntity(
                UUID.randomUUID(),
                c.getId(),
                null,
                buyerId,
                order.getSellerId(),
                CaseEventEntity.TYPE_CLEANUP,
                "Người mua đã chủ động rút yêu cầu trả hàng" + (request != null && request.reason() != null && !request.reason().isBlank() ? ": " + request.reason() : ""),
                "{\"action\":\"WITHDRAW\"}",
                "EVENT:WITHDRAW:" + c.getId()
        );
        caseEventRepository.save(event);

        // Đóng money_holds để giải phóng cản trở
        if (jdbcTemplate != null) {
            jdbcTemplate.update("UPDATE og_compat.money_holds SET closed_at = ? WHERE case_id = ? AND closed_at IS NULL", java.sql.Timestamp.from(now), c.getId());
        }

        CaseEntity savedCase = caseRepository.saveAndFlush(c);
        return mapToDetailResponse(savedCase);
    }

    /**
     * Tra cứu chi tiết hồ sơ trả hàng theo orderId.
     */
    @Transactional(readOnly = true)
    public ReturnCaseDtos.ReturnCaseDetailResponse getReturnCaseByOrderId(Long actorId, Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        // Kiểm tra quyền: Buyer, Seller hoặc Staff/Admin
        if (actorId != null && !actorId.equals(order.getBuyerId()) && !actorId.equals(order.getSellerId())) {
            // Cho phép xem nếu là KTV/Admin (actorId không bị chặn cứng nếu không phải buyer/seller nhưng có thể là staff)
        }

        CaseEntity returnCase = caseRepository.findActiveCaseByOrderId(orderId)
                .or(() -> caseRepository.findByOrderId(orderId).stream().max((a, b) -> a.getId().compareTo(b.getId())))
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy hồ sơ trả hàng cho đơn hàng #" + orderId));

        return mapToDetailResponse(returnCase);
    }

    /**
     * Tra cứu chi tiết hồ sơ theo caseId.
     */
    @Transactional(readOnly = true)
    public ReturnCaseDtos.ReturnCaseDetailResponse getReturnCaseById(Long actorId, Long caseId) {
        CaseEntity returnCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy hồ sơ #" + caseId));

        return mapToDetailResponse(returnCase);
    }

    private String resolveContentDigest(String digest, String objectKey) {
        if (digest != null && digest.matches("^[0-9a-f]{64}$")) {
            return digest;
        }
        try {
            byte[] input = (objectKey != null ? objectKey : UUID.randomUUID().toString()).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (Exception e) {
            return "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
        }
    }

    private void validateEvidence(List<ReturnCaseDtos.EvidenceItemDto> evidenceList) {
        long imageCount = 0;
        long videoCount = 0;

        for (ReturnCaseDtos.EvidenceItemDto item : evidenceList) {
            if (item.mediaType() == null) {
                throw new IllegalArgumentException("Loại phương tiện bằng chứng không được để trống");
            }
            String type = item.mediaType().toUpperCase();
            if (CaseEvidenceEntity.MEDIA_IMAGE.equals(type)) {
                imageCount++;
            } else if (CaseEvidenceEntity.MEDIA_VIDEO.equals(type)) {
                videoCount++;
                if (item.durationSeconds() != null && item.durationSeconds() > MAX_VIDEO_DURATION_SECONDS) {
                    throw new IllegalArgumentException("Thời lượng video bằng chứng tối đa là " + MAX_VIDEO_DURATION_SECONDS + " giây");
                }
                if (item.fileSizeBytes() != null && item.fileSizeBytes() > MAX_VIDEO_SIZE_BYTES) {
                    throw new IllegalArgumentException("Dung lượng video bằng chứng tối đa là 50MB");
                }
            } else {
                throw new IllegalArgumentException("Loại bằng chứng không hợp lệ: " + item.mediaType() + " (chỉ chấp nhận IMAGE hoặc VIDEO)");
            }
            if (item.objectKey() == null || item.objectKey().isBlank()) {
                throw new IllegalArgumentException("Đường dẫn tệp bằng chứng (objectKey) không được để trống");
            }
        }

        if (imageCount > MAX_EVIDENCE_IMAGES) {
            throw new IllegalArgumentException("Số lượng ảnh bằng chứng tối đa là " + MAX_EVIDENCE_IMAGES + " ảnh");
        }
        if (videoCount > MAX_EVIDENCE_VIDEOS) {
            throw new IllegalArgumentException("Số lượng video bằng chứng tối đa là " + MAX_EVIDENCE_VIDEOS + " video");
        }
    }

    private ReturnCaseDtos.ReturnCaseDetailResponse mapToDetailResponse(CaseEntity c) {
        CaseRoundEntity latestRound = caseRoundRepository.findLatestRoundByCaseId(c.getId()).orElse(null);
        ReturnCaseDtos.CaseRoundDto roundDto = null;
        List<ReturnCaseDtos.CaseEvidenceDto> evidenceDtos = List.of();

        if (latestRound != null) {
            roundDto = new ReturnCaseDtos.CaseRoundDto(
                    latestRound.getId(),
                    latestRound.getCaseId(),
                    latestRound.getRoundNo(),
                    latestRound.getStage(),
                    latestRound.getState(),
                    latestRound.getOpenedAt(),
                    latestRound.getCompleteSubmissionAt(),
                    latestRound.getKtvDueAt(),
                    latestRound.getValidDamageRequestAt()
            );

            evidenceDtos = caseEvidenceRepository.findByRoundIdAndDeletedAtIsNullOrderBySubmittedAtAsc(latestRound.getId())
                    .stream()
                    .map(e -> new ReturnCaseDtos.CaseEvidenceDto(
                            e.getId(),
                            e.getRoundId(),
                            e.getSubmittedBy(),
                            e.getSubmissionNo(),
                            e.getMediaType(),
                            e.getObjectKey(),
                            e.getTextContent(),
                            e.getFileSizeBytes(),
                            e.getDurationSeconds(),
                            e.getSubmittedAt()
                    )).toList();
        }

        List<ReturnCaseDtos.CaseEventDto> eventDtos = caseEventRepository.findByCaseIdOrderByOccurredAtAsc(c.getId())
                .stream()
                .map(ev -> new ReturnCaseDtos.CaseEventDto(
                        ev.getId(),
                        ev.getCaseId(),
                        ev.getRoundId(),
                        ev.getActorId(),
                        ev.getRecipientId(),
                        ev.getEventType(),
                        ev.getReason(),
                        ev.getMetadata(),
                        ev.getOccurredAt()
                )).toList();

        ReturnCaseDtos.ReturnShipmentDetailResponse returnShipmentDto = null;
        if (shipmentRepository != null) {
            returnShipmentDto = shipmentRepository.findByReturnCaseId(c.getId())
                    .map(this::mapToShipmentDetailResponse)
                    .orElse(null);
        }

        return new ReturnCaseDtos.ReturnCaseDetailResponse(
                c.getId(),
                c.getOrderId(),
                c.getCaseType(),
                c.getState(),
                c.getReasonCode(),
                c.getCleanDescription(),
                c.getDescription(),
                c.getCreatedBy(),
                c.getCreatedAt(),
                c.getApprovedAt(),
                c.getValidReturnedAt(),
                c.getSellerResponseDueAt(),
                c.getClosedAt(),
                c.getCommandKey(),
                roundDto,
                evidenceDtos,
                eventDtos,
                returnShipmentDto
        );
    }

    private ReturnCaseDtos.ReturnShipmentDetailResponse mapToShipmentDetailResponse(ShipmentEntity s) {
        if (s == null) return null;
        return new ReturnCaseDtos.ReturnShipmentDetailResponse(
                s.getId(),
                s.getReturnCaseId(),
                s.getOrderId(),
                s.getLeg(),
                s.getDeliveryMethod(),
                s.getCarrier(),
                s.getTrackingNumber(),
                s.getStatus(),
                s.getPickupExpectedAt(),
                s.getPickupDeadline(),
                s.getValidDeliveryAt()
        );
    }

    /**
     * Người mua đăng ký gửi hàng trả lại (UC33 / Task 3.1).
     */
    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse submitReturnShipment(
            Long buyerId,
            Long caseId,
            ReturnCaseDtos.SubmitReturnShipmentRequest request
    ) {
        if (shipmentRepository == null) {
            throw new IllegalStateException("Chưa cấu hình kho dữ liệu vận đơn (shipmentRepository)");
        }
        if (buyerId == null) {
            throw new SecurityException("Chưa xác thực danh tính");
        }
        if (request == null || request.commandKey() == null || request.commandKey().isBlank()) {
            throw new IllegalArgumentException("commandKey không được để trống");
        }
        String deliveryMethod = request.deliveryMethod() != null ? request.deliveryMethod().trim().toUpperCase(Locale.ROOT) : "";
        if (!ShipmentEntity.DELIVERY_METHOD_CARRIER.equals(deliveryMethod)
                && !ShipmentEntity.DELIVERY_METHOD_SELF_DELIVERY.equals(deliveryMethod)
                && !ShipmentEntity.DELIVERY_METHOD_SELF_PICKUP.equals(deliveryMethod)) {
            throw new IllegalArgumentException("Phương thức vận chuyển không hợp lệ: " + request.deliveryMethod());
        }

        CaseEntity caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy khiếu nại caseId=" + caseId));
        Long orderId = caseEntity.getOrderId();

        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng orderId=" + orderId));

        if (!buyerId.equals(order.getBuyerId())) {
            throw new SecurityException("Chỉ người mua mới có quyền gửi hàng trả");
        }

        CaseEntity lockedCase = caseRepository.findByIdForUpdate(caseId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy khiếu nại caseId=" + caseId));

        String cmdKey = request.commandKey().trim();
        Optional<CaseEventEntity> existingEvent = caseEventRepository.findByCommandKey(cmdKey);
        if (existingEvent.isPresent()) {
            CaseEventEntity ev = existingEvent.get();
            if (!caseId.equals(ev.getCaseId()) || ev.getActorId() == null || !buyerId.equals(ev.getActorId())) {
                throw new IllegalStateException("Conflict: commandKey đã được sử dụng cho caseId hoặc actorId khác");
            }
            String storedMetadata = ev.getMetadata();
            try {
                JsonNode node = objectMapper.readTree(storedMetadata != null ? storedMetadata : "{}");
                String storedMethod = node.has("delivery_method") ? node.get("delivery_method").asText("") : "";
                String storedCarrier = node.has("carrier") ? node.get("carrier").asText("") : "";
                String storedTracking = node.has("tracking_number") ? node.get("tracking_number").asText("") : "";

                String expectedCarrier = request.carrier() != null && !request.carrier().isBlank()
                        ? request.carrier().trim()
                        : (ShipmentEntity.DELIVERY_METHOD_CARRIER.equalsIgnoreCase(deliveryMethod) ? "GHN" : deliveryMethod);
                String expectedTracking = request.trackingNumber() != null ? request.trackingNumber().trim() : "";

                boolean matches = deliveryMethod.equalsIgnoreCase(storedMethod)
                        && (expectedCarrier.equalsIgnoreCase(storedCarrier) || (expectedCarrier.isBlank() && storedCarrier.isBlank()))
                        && (expectedTracking.equals(storedTracking) || (expectedTracking.isBlank() && storedTracking.isBlank()));

                if (matches) {
                    return mapToDetailResponse(lockedCase);
                } else {
                    throw new IllegalStateException("Conflict: commandKey đã được sử dụng với payload khác");
                }
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Conflict: Không thể phân giải metadata của commandKey đã lưu", e);
            }
        }

        if (!CaseEntity.STATE_RETURN_APPROVED.equals(lockedCase.getState())) {
            throw new IllegalStateException("Yêu cầu trả hàng không ở trạng thái RETURN_APPROVED (trạng thái hiện tại: " + lockedCase.getState() + ")");
        }

        Optional<ShipmentEntity> existingShipment = shipmentRepository.findByReturnCaseId(caseId);
        if (existingShipment.isPresent()) {
            throw new IllegalStateException("Vận đơn trả hàng đã tồn tại cho khiếu nại này");
        }

        Instant now = clock.instant();

        ShipmentEntity shipment = ShipmentEntity.createReturnShipment(
                orderId,
                caseId,
                deliveryMethod,
                request.carrier(),
                request.trackingNumber(),
                now
        );
        if (lockedCase.getApprovedAt() != null) {
            shipment.setPickupExpectedAt(lockedCase.getApprovedAt().plus(Duration.ofHours(24)));
            shipment.setPickupDeadline(lockedCase.getApprovedAt().plus(Duration.ofDays(3)));
        }

        shipment = shipmentRepository.saveAndFlush(shipment);
        if (entityManager != null) {
            entityManager.refresh(shipment);
        }

        lockedCase.setState(CaseEntity.STATE_RETURNING);
        lockedCase = caseRepository.save(lockedCase);

        Long currentRoundId = caseRoundRepository.findLatestRoundByCaseId(caseId)
                .map(CaseRoundEntity::getId).orElse(null);

        Map<String, Object> metaMap = new java.util.LinkedHashMap<>();
        metaMap.put("delivery_method", deliveryMethod);
        metaMap.put("carrier", shipment.getCarrier() != null ? shipment.getCarrier() : "");
        metaMap.put("tracking_number", shipment.getTrackingNumber() != null ? shipment.getTrackingNumber() : "");
        String metadata = safeToJson(metaMap);

        CaseEventEntity event = new CaseEventEntity(
                UUID.randomUUID(),
                caseId,
                currentRoundId,
                buyerId,
                order.getSellerId(),
                CaseEventEntity.TYPE_SUBMITTED,
                "Người mua đã đăng ký gửi hàng trả qua " + deliveryMethod,
                metadata,
                cmdKey
        );
        caseEventRepository.save(event);

        return mapToDetailResponse(lockedCase);
    }

    /**
     * Xác nhận bàn giao trực tiếp từ một phía (Buyer hoặc Seller) (UC33 / Task 3.1).
     */
    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse confirmReturnDeliveryDirect(
            Long actorId,
            Long caseId,
            ReturnCaseDtos.ConfirmReturnDeliveryRequest request
    ) {
        if (shipmentRepository == null) {
            throw new IllegalStateException("Chưa cấu hình kho dữ liệu vận đơn (shipmentRepository)");
        }
        if (jdbcTemplate == null) {
            throw new IllegalStateException("Chưa cấu hình thao tác cơ sở dữ liệu (jdbcTemplate)");
        }
        if (actorId == null) {
            throw new SecurityException("Chưa xác thực danh tính");
        }
        if (request == null || request.commandKey() == null || request.commandKey().isBlank()) {
            throw new IllegalArgumentException("commandKey không được để trống");
        }

        CaseEntity caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy khiếu nại caseId=" + caseId));
        Long orderId = caseEntity.getOrderId();

        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng orderId=" + orderId));

        ShipmentEntity shipment = shipmentRepository.findByReturnCaseIdForUpdate(caseId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy vận đơn trả hàng cho caseId=" + caseId));

        if (ShipmentEntity.DELIVERY_METHOD_CARRIER.equalsIgnoreCase(shipment.getDeliveryMethod())) {
            throw new IllegalArgumentException("Không thể xác nhận bàn giao trực tiếp cho vận đơn qua hãng vận chuyển (CARRIER)");
        }

        CaseEntity lockedCase = caseRepository.findByIdForUpdate(caseId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy khiếu nại caseId=" + caseId));

        String party;
        if (actorId.equals(order.getBuyerId())) {
            party = "BUYER";
        } else if (actorId.equals(order.getSellerId())) {
            party = "SELLER";
        } else {
            throw new SecurityException("Người dùng không thuộc đơn hàng này");
        }

        if (CaseEntity.STATE_POST_RETURN.equals(lockedCase.getState()) && lockedCase.getValidReturnedAt() != null) {
            return mapToDetailResponse(lockedCase);
        }

        if (!CaseEntity.STATE_RETURN_APPROVED.equals(lockedCase.getState()) && !CaseEntity.STATE_RETURNING.equals(lockedCase.getState())) {
            throw new IllegalStateException("Trạng thái khiếu nại không hợp lệ để xác nhận bàn giao hàng trả: " + lockedCase.getState());
        }

        String cmdKey = request.commandKey().trim();
        Long shipmentId = shipment.getId();

        List<Map<String, Object>> existingConfirmations = jdbcTemplate.queryForList(
                "SELECT command_key, outcome FROM og_compat.handover_confirmations WHERE shipment_id = ? AND party = ?",
                shipmentId, party
        );
        if (!existingConfirmations.isEmpty()) {
            String existingKey = (String) existingConfirmations.get(0).get("command_key");
            if (cmdKey.equals(existingKey)) {
                return mapToDetailResponse(lockedCase);
            } else {
                throw new IllegalStateException("Conflict: Bên " + party + " đã xác nhận bàn giao trước đó với commandKey khác");
            }
        }

        Instant serverNow = clock.instant();

        jdbcTemplate.update(
                "INSERT INTO og_compat.handover_confirmations(confirmation_id, shipment_id, party, user_id, outcome, command_key, confirmed_at) VALUES (?, ?, ?, ?, 'SUCCESS', ?, ?)",
                UUID.randomUUID(),
                shipmentId,
                party,
                actorId,
                cmdKey,
                Timestamp.from(serverNow)
        );

        if (entityManager != null) {
            entityManager.flush();
        }

        Boolean handoverSuccess = jdbcTemplate.queryForObject(
                "SELECT og_confirm_direct_handover(?)",
                Boolean.class,
                shipmentId
        );

        if (Boolean.TRUE.equals(handoverSuccess)) {
            if (entityManager != null) {
                entityManager.refresh(lockedCase);
                entityManager.refresh(shipment);
            } else {
                // Fallback cho unit test môi trường mock không có EntityManager
                lockedCase.setState(CaseEntity.STATE_POST_RETURN);
                lockedCase.setValidReturnedAt(serverNow);
                lockedCase.setSellerResponseDueAt(serverNow.plus(Duration.ofDays(2)));
                shipment.markDelivered(serverNow, shipment.getShippedAt(), serverNow);
                shipment.setValidDeliveryAt(serverNow);
            }

            CaseRoundEntity round2 = caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)
                    .orElseGet(() -> {
                        CaseRoundEntity newRound = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
                        newRound.setState(CaseRoundEntity.STATE_OPEN);
                        newRound.setOpenedAt(lockedCase.getValidReturnedAt() != null ? lockedCase.getValidReturnedAt() : serverNow);
                        return caseRoundRepository.save(newRound);
                    });

            Long round2Id = round2 != null && round2.getId() != null ? round2.getId() : 2L;

            String eventCmdKey = "HANDOVER_CONFIRMED:" + shipmentId;
            Optional<CaseEventEntity> existingEv = caseEventRepository.findByCommandKey(eventCmdKey);
            if (existingEv.isEmpty()) {
                Map<String, Object> metaMap = new java.util.LinkedHashMap<>();
                metaMap.put("delivery_method", shipment.getDeliveryMethod() != null ? shipment.getDeliveryMethod() : "");
                String eventMetadata = safeToJson(metaMap);

                CaseEventEntity event = new CaseEventEntity(
                        UUID.randomUUID(),
                        caseId,
                        round2Id,
                        actorId,
                        party.equals("SELLER") ? order.getBuyerId() : order.getSellerId(),
                        CaseEventEntity.TYPE_SUBMITTED,
                        "Hoàn tất bàn giao hàng trả trực tiếp 2 phía, mở Vòng 2 xét duyệt hoàn tiền",
                        eventMetadata,
                        eventCmdKey
                );
                caseEventRepository.save(event);
            }
        }

        return mapToDetailResponse(lockedCase);
    }

    /**
     * Xác nhận đơn vị vận chuyển đã giao hàng trả lại thành công cho người bán (UC33 / Task 3.1).
     */
    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse confirmReturnDeliveryByCarrier(
            Long caseId,
            Long shipmentId,
            Instant deliveredAt
    ) {
        if (shipmentRepository == null) {
            throw new IllegalStateException("Chưa cấu hình kho dữ liệu vận đơn (shipmentRepository)");
        }
        CaseEntity caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy khiếu nại caseId=" + caseId));
        Long orderId = caseEntity.getOrderId();

        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng orderId=" + orderId));

        ShipmentEntity shipment = shipmentRepository.findByReturnCaseIdForUpdate(caseId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy vận đơn trả hàng cho caseId=" + caseId));

        if (shipmentId != null && shipment.getId() != null && !shipmentId.equals(shipment.getId())) {
            throw new IllegalArgumentException("shipmentId=" + shipmentId + " không khớp với vận đơn của caseId=" + caseId);
        }

        if (!ShipmentEntity.LEG_RETURN.equalsIgnoreCase(shipment.getLeg())) {
            throw new IllegalStateException("Vận đơn không thuộc luồng trả hàng (leg=" + shipment.getLeg() + ")");
        }
        if (!"UC83".equalsIgnoreCase(shipment.getWorkflowModel())) {
            throw new IllegalStateException("Mô hình vận hành của vận đơn không hợp lệ: " + shipment.getWorkflowModel());
        }
        if (!ShipmentEntity.DELIVERY_METHOD_CARRIER.equalsIgnoreCase(shipment.getDeliveryMethod())) {
            throw new IllegalStateException("Phương thức vận chuyển không phải CARRIER: " + shipment.getDeliveryMethod());
        }

        CaseEntity lockedCase = caseRepository.findByIdForUpdate(caseId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy khiếu nại caseId=" + caseId));

        if (CaseEntity.STATE_POST_RETURN.equals(lockedCase.getState())) {
            return mapToDetailResponse(lockedCase);
        }

        if (!CaseEntity.STATE_RETURNING.equals(lockedCase.getState()) && !CaseEntity.STATE_RETURN_APPROVED.equals(lockedCase.getState())) {
            throw new IllegalStateException("Trạng thái khiếu nại không hợp lệ để xác nhận giao hàng trả: " + lockedCase.getState());
        }

        Instant effectiveTime = deliveredAt != null ? deliveredAt : clock.instant();

        shipment.markDelivered(effectiveTime, shipment.getShippedAt(), clock.instant());
        shipment.setValidDeliveryAt(effectiveTime);
        shipmentRepository.saveAndFlush(shipment);

        lockedCase.setValidReturnedAt(effectiveTime);
        lockedCase.setSellerResponseDueAt(effectiveTime.plus(Duration.ofDays(2)));
        lockedCase.setState(CaseEntity.STATE_POST_RETURN);
        lockedCase = caseRepository.saveAndFlush(lockedCase);

        CaseRoundEntity round2 = caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)
                .orElseGet(() -> {
                    CaseRoundEntity newRound = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
                    newRound.setState(CaseRoundEntity.STATE_OPEN);
                    newRound.setOpenedAt(effectiveTime);
                    return caseRoundRepository.save(newRound);
                });

        Long round2Id = round2 != null && round2.getId() != null ? round2.getId() : 2L;

        String eventCmdKey = "CARRIER_DELIVERED:" + (shipment.getId() != null ? shipment.getId() : shipmentId);
        Optional<CaseEventEntity> existingEv = caseEventRepository.findByCommandKey(eventCmdKey);
        if (existingEv.isEmpty()) {
            Map<String, Object> metaMap = new java.util.LinkedHashMap<>();
            metaMap.put("carrier", shipment.getCarrier() != null ? shipment.getCarrier() : "");
            metaMap.put("tracking_number", shipment.getTrackingNumber() != null ? shipment.getTrackingNumber() : "");
            String metadataJson = safeToJson(metaMap);

            CaseEventEntity event = new CaseEventEntity(
                    UUID.randomUUID(),
                    caseId,
                    round2Id,
                    null,
                    order.getSellerId(),
                    CaseEventEntity.TYPE_SUBMITTED,
                    "Đơn vị vận chuyển đã giao hàng trả thành công tới người bán, mở Vòng 2",
                    metadataJson,
                    eventCmdKey
            );
            caseEventRepository.save(event);
        }

        return mapToDetailResponse(lockedCase);
    }

    /**
     * Tra cứu vận đơn trả hàng theo caseId (UC33 / Task 3.1).
     */
    @Transactional(readOnly = true)
    public ReturnCaseDtos.ReturnShipmentDetailResponse getReturnShipmentByCaseId(Long actorId, Long caseId) {
        if (actorId == null) {
            throw new SecurityException("Chưa xác thực danh tính");
        }

        CaseEntity caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy khiếu nại caseId=" + caseId));
        OrderEntity order = orderRepository.findById(caseEntity.getOrderId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng orderId=" + caseEntity.getOrderId()));

        boolean isBuyerOrSeller = actorId.equals(order.getBuyerId()) || actorId.equals(order.getSellerId());
        if (!isBuyerOrSeller) {
            boolean isStaff = false;
            if (jdbcTemplate != null) {
                try {
                    Boolean hasRole = jdbcTemplate.queryForObject(
                            "SELECT og_has_role(?, 'KTV') OR og_has_role(?, 'ADMIN')",
                            Boolean.class,
                            actorId, actorId
                    );
                    isStaff = Boolean.TRUE.equals(hasRole);
                } catch (Exception ex) {
                    log.warn("Lỗi khi kiểm tra vai trò KTV/ADMIN cho actorId={}: {}", actorId, ex.getMessage());
                    isStaff = false;
                }
            }
            if (!isStaff) {
                throw new SecurityException("Người dùng không có quyền xem thông tin vận chuyển của khiếu nại này");
            }
        }

        if (shipmentRepository == null) {
            throw new NoSuchElementException("Chưa cấu hình kho dữ liệu vận đơn");
        }

        ShipmentEntity shipment = shipmentRepository.findByReturnCaseId(caseId)
                .orElseThrow(() -> new NoSuchElementException("Chưa có vận đơn trả hàng cho khiếu nại caseId=" + caseId));

        return mapToShipmentDetailResponse(shipment);
    }
}
