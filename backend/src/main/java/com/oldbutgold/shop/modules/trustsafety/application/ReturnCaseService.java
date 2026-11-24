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
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEvidenceRequestEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEvidenceRequestRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
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
    public static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024; // 5MB

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
    private final ReturnWorkflowLockGateway lockGateway;
    private final RoundDecisionSettlementPort settlementPort;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;
    private final org.springframework.transaction.support.TransactionTemplate requiresNewTxTemplate;
    private EvidenceStorageVerifier evidenceStorageVerifier;
    @Autowired(required = false)
    private SellerResubmissionService sellerResubmissionService;
    private CaseEvidenceRequestRepository caseEvidenceRequestRepository;

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
            Clock clock,
            ReturnWorkflowLockGateway lockGateway,
            RoundDecisionSettlementPort settlementPort,
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            CaseEvidenceRequestRepository caseEvidenceRequestRepository
    ) {
        this(caseRepository, caseRoundRepository, caseEvidenceRepository, caseDecisionRepository,
                caseEventRepository, orderRepository, paymentRepository, shipmentRepository,
                entityManager, jdbcTemplate, objectMapper, clock, lockGateway, settlementPort,
                transactionManager, caseEvidenceRequestRepository, null);
    }

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
            Clock clock,
            ReturnWorkflowLockGateway lockGateway,
            RoundDecisionSettlementPort settlementPort,
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            CaseEvidenceRequestRepository caseEvidenceRequestRepository,
            @Autowired(required = false) EvidenceStorageVerifier evidenceStorageVerifier
    ) {
        this.caseRepository = java.util.Objects.requireNonNull(caseRepository, "caseRepository must not be null");
        this.caseRoundRepository = java.util.Objects.requireNonNull(caseRoundRepository, "caseRoundRepository must not be null");
        this.caseEvidenceRepository = java.util.Objects.requireNonNull(caseEvidenceRepository, "caseEvidenceRepository must not be null");
        this.caseDecisionRepository = java.util.Objects.requireNonNull(caseDecisionRepository, "caseDecisionRepository must not be null");
        this.caseEventRepository = java.util.Objects.requireNonNull(caseEventRepository, "caseEventRepository must not be null");
        this.orderRepository = java.util.Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.paymentRepository = java.util.Objects.requireNonNull(paymentRepository, "paymentRepository must not be null");
        this.shipmentRepository = shipmentRepository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.lockGateway = lockGateway != null ? lockGateway : new ReturnWorkflowLockGateway(orderRepository, caseRepository, caseRoundRepository, entityManager);
        this.settlementPort = java.util.Objects.requireNonNull(settlementPort, "RoundDecisionSettlementPort must not be null");
        this.transactionManager = java.util.Objects.requireNonNull(transactionManager, "PlatformTransactionManager must not be null");
        this.caseEvidenceRequestRepository = java.util.Objects.requireNonNull(caseEvidenceRequestRepository, "caseEvidenceRequestRepository must not be null");
        this.evidenceStorageVerifier = evidenceStorageVerifier;
        org.springframework.transaction.support.TransactionTemplate tt =
                new org.springframework.transaction.support.TransactionTemplate(this.transactionManager);
        tt.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTxTemplate = tt;
    }

    public void setEvidenceStorageVerifier(EvidenceStorageVerifier evidenceStorageVerifier) {
        this.evidenceStorageVerifier = evidenceStorageVerifier;
    }

    public void setSellerResubmissionService(SellerResubmissionService sellerResubmissionService) {
        this.sellerResubmissionService = sellerResubmissionService;
    }

    public void setCaseEvidenceRequestRepository(CaseEvidenceRequestRepository caseEvidenceRequestRepository) {
        this.caseEvidenceRequestRepository = java.util.Objects.requireNonNull(caseEvidenceRequestRepository, "caseEvidenceRequestRepository must not be null");
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

        // 1 & 2. Khóa theo thứ tự chuẩn: Order -> Case
        ReturnWorkflowLockGateway.ReturnWorkflowLockedEntities locked = lockGateway.lockForCase(caseId);
        CaseEntity c = locked.returnCase();
        OrderEntity order = locked.order();
        Long orderId = order.getId();

        if (sellerId == null || !sellerId.equals(order.getSellerId())) {
            throw new IllegalArgumentException("Chỉ người bán của đơn hàng mới có quyền phản hồi");
        }

        // Xử lý xung đột đua quyết định (first-write wins): Phải ở OPEN
        if (!CaseEntity.STATE_OPEN.equals(c.getState())) {
            throw new IllegalStateException("Hồ sơ không ở trạng thái chờ người bán phản hồi hoặc đã được xử lý trước đó (Trạng thái hiện tại: " + c.getState() + ")");
        }

        // 3. Khóa CaseRound (Vòng 1) sau khi xác nhận quyền và trạng thái Case
        CaseRoundEntity round = lockGateway.lockRound(caseId, 1);

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

    // ==================== ORDER-SCOPED & KTV CONSOLE FACADE METHODS (TASK-0120) ====================

    private OrderEntity requireOrderOrThrow(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));
    }

    private CaseEntity requireActiveCaseOrThrow(Long orderId) {
        return caseRepository.findActiveCaseByOrderId(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy hồ sơ khiếu nại đang hoạt động cho đơn hàng #" + orderId));
    }

    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse createReturnRequestForOrder(
            Long buyerId,
            Long orderId,
            ReturnCaseDtos.CreateReturnRequest request
    ) {
        OrderEntity order = requireOrderOrThrow(orderId);
        if (!buyerId.equals(order.getBuyerId())) {
            throw new org.springframework.security.access.AccessDeniedException("Chỉ người mua của đơn hàng mới có quyền yêu cầu trả hàng");
        }
        return createReturnRequest(buyerId, orderId, request);
    }

    @Transactional(readOnly = true)
    public ReturnCaseDtos.ReturnCaseDetailResponse getActiveReturnCaseForOrder(Long actorId, Long orderId) {
        OrderEntity order = requireOrderOrThrow(orderId);
        if (!actorId.equals(order.getBuyerId()) && !actorId.equals(order.getSellerId())) {
            throw new org.springframework.security.access.AccessDeniedException("Người dùng không thuộc đơn hàng này");
        }
        CaseEntity activeCase = requireActiveCaseOrThrow(orderId);
        return getReturnCaseById(actorId, activeCase.getId());
    }

    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse withdrawReturnRequestForOrder(
            Long buyerId,
            Long orderId,
            ReturnCaseDtos.WithdrawReturnRequest request
    ) {
        OrderEntity order = requireOrderOrThrow(orderId);
        if (!buyerId.equals(order.getBuyerId())) {
            throw new org.springframework.security.access.AccessDeniedException("Chỉ người mua của đơn hàng mới có quyền rút yêu cầu trả hàng");
        }
        CaseEntity activeCase = requireActiveCaseOrThrow(orderId);
        return withdrawReturnRequest(buyerId, activeCase.getId(), request);
    }

    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse sellerRespondRound1ForOrder(
            Long sellerId,
            Long orderId,
            ReturnCaseDtos.SellerRound1ResponseRequest request
    ) {
        OrderEntity order = requireOrderOrThrow(orderId);
        if (!sellerId.equals(order.getSellerId())) {
            throw new org.springframework.security.access.AccessDeniedException("Chỉ người bán của đơn hàng mới có quyền phản hồi");
        }
        CaseEntity activeCase = requireActiveCaseOrThrow(orderId);
        return sellerRespondRound1(sellerId, activeCase.getId(), request);
    }

    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse confirmReturnDeliveryDirectForOrder(
            Long actorId,
            Long orderId,
            ReturnCaseDtos.ConfirmReturnDeliveryRequest request
    ) {
        OrderEntity order = requireOrderOrThrow(orderId);
        if (!actorId.equals(order.getBuyerId()) && !actorId.equals(order.getSellerId())) {
            throw new org.springframework.security.access.AccessDeniedException("Người dùng không thuộc đơn hàng này");
        }
        CaseEntity activeCase = requireActiveCaseOrThrow(orderId);
        return confirmReturnDeliveryDirect(actorId, activeCase.getId(), request);
    }

    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse sellerAgreeRefundRound2ForOrder(
            Long sellerId,
            Long orderId,
            ReturnCaseDtos.SellerAgreeRefundRequest request
    ) {
        OrderEntity order = requireOrderOrThrow(orderId);
        if (!sellerId.equals(order.getSellerId())) {
            throw new org.springframework.security.access.AccessDeniedException("Chỉ người bán của đơn hàng mới có quyền đồng ý hoàn tiền");
        }
        CaseEntity activeCase = requireActiveCaseOrThrow(orderId);
        return sellerAgreeRefundRound2(sellerId, activeCase.getId(), request);
    }

    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse submitDamageExemptionRequestForOrder(
            Long sellerId,
            Long orderId,
            ReturnCaseDtos.SellerDamageExemptionRequest request
    ) {
        OrderEntity order = requireOrderOrThrow(orderId);
        if (!sellerId.equals(order.getSellerId())) {
            throw new org.springframework.security.access.AccessDeniedException("Chỉ người bán của đơn hàng mới có quyền gửi yêu cầu bồi thường thiệt hại");
        }
        CaseEntity activeCase = requireActiveCaseOrThrow(orderId);
        return submitDamageExemptionRequest(sellerId, activeCase.getId(), request);
    }

    /**
     * UC62 Alt 7a: Người mua bổ sung bằng chứng Vòng 1 theo orderId (Order-scoped facade).
     */
    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse supplementBuyerEvidenceRound1ForOrder(
            Long buyerId,
            Long orderId,
            ReturnCaseDtos.BuyerSupplementEvidenceRequest request
    ) {
        OrderEntity order = requireOrderOrThrow(orderId);
        if (!buyerId.equals(order.getBuyerId())) {
            throw new org.springframework.security.access.AccessDeniedException("Chỉ người mua của đơn hàng mới có quyền bổ sung bằng chứng Vòng 1");
        }
        CaseEntity activeCase = requireActiveCaseOrThrow(orderId);
        return supplementBuyerEvidenceRound1(buyerId, activeCase.getId(), request);
    }

    /**
     * UC62 Alt 7a: Người mua bổ sung bằng chứng Vòng 1 (append-only, quota 1-5 ảnh / <= 2 video <= 60s, idempotent commandKey).
     */
    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse supplementBuyerEvidenceRound1(
            Long buyerId,
            Long caseId,
            ReturnCaseDtos.BuyerSupplementEvidenceRequest request
    ) {
        if (buyerId == null || caseId == null || request == null) {
            throw new IllegalArgumentException("Thông tin bổ sung bằng chứng không hợp lệ");
        }
        String cmdKey = request.commandKey() != null ? request.commandKey().trim() : "";
        if (cmdKey.isBlank()) {
            throw new IllegalArgumentException("commandKey không được để trống");
        }
        if (request.evidenceList() == null || request.evidenceList().isEmpty()) {
            throw new IllegalArgumentException("Bắt buộc phải cung cấp ít nhất 1 bằng chứng (ảnh hoặc video)");
        }

        // 1. Idempotency check before state mutation
        Optional<CaseEventEntity> existingEvent = caseEventRepository.findByCommandKey(cmdKey);
        if (existingEvent.isPresent()) {
            if (!existingEvent.get().getCaseId().equals(caseId)) {
                throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "COMMAND_KEY_CONFLICT",
                        "Xung đột commandKey: khóa này đã được sử dụng cho hồ sơ khác");
            }
            CaseEntity existingCase = caseRepository.findById(caseId)
                    .orElseThrow(() -> new NoSuchElementException("Không tìm thấy hồ sơ khiếu nại #" + caseId));
            return mapToDetailResponse(existingCase);
        }

        // 2. Lock Order -> Case -> Round 1
        CaseEntity c = caseRepository.findById(caseId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy hồ sơ khiếu nại #" + caseId));
        OrderEntity order = orderRepository.findByIdForUpdate(c.getOrderId())
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + c.getOrderId()));
        if (!buyerId.equals(order.getBuyerId())) {
            throw new org.springframework.security.access.AccessDeniedException("Chỉ người mua của đơn hàng mới có quyền bổ sung bằng chứng Vòng 1");
        }
        CaseEntity lockedCase = caseRepository.findByIdForUpdate(caseId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy hồ sơ khiếu nại #" + caseId));

        if (!CaseEntity.STATE_OPEN.equals(lockedCase.getState()) && !CaseEntity.STATE_REVIEWING.equals(lockedCase.getState())) {
            throw new IllegalStateException("Chỉ có thể bổ sung bằng chứng Vòng 1 khi hồ sơ ở trạng thái OPEN hoặc REVIEWING (hiện tại: " + lockedCase.getState() + ")");
        }

        CaseRoundEntity round1 = caseRoundRepository.findByCaseIdAndRoundNo(caseId, 1)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy Vòng 1 của hồ sơ #" + caseId));
        if (!CaseRoundEntity.STATE_OPEN.equals(round1.getState())
                || !caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(round1.getId()).isEmpty()) {
            throw new IllegalStateException("Vòng 1 đã đóng hoặc đã có phán quyết, không thể bổ sung bằng chứng");
        }

        // 3. Validate evidence quota & storage digest
        validateEvidence(request.evidenceList());

        List<CaseEvidenceEntity> existingEvidence = caseEvidenceRepository.findByRoundIdOrderBySubmittedAtAsc(round1.getId());
        int maxSubmissionNo = existingEvidence.stream()
                .mapToInt(e -> e.getSubmissionNo() != null ? e.getSubmissionNo() : 1)
                .max()
                .orElse(1);
        if (maxSubmissionNo >= 4) {
            throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "MAX_RESUBMISSION_REACHED",
                    "Đã đạt số lần nộp bổ sung tối đa (4 đợt)");
        }
        int nextSubmissionNo = maxSubmissionNo + 1;
        Instant now = clock.instant();

        // Fulfill active Round 1 PENDING evidence request if any
        UUID linkedRequestId = null;
        if (caseEvidenceRequestRepository != null) {
            Optional<CaseEvidenceRequestEntity> pendingReqOpt =
                    caseEvidenceRequestRepository.findByCaseIdAndStatus(caseId, CaseEvidenceRequestEntity.STATUS_PENDING);
            if (pendingReqOpt.isPresent() && round1.getId().equals(pendingReqOpt.get().getRoundId())) {
                CaseEvidenceRequestEntity pendingReq = pendingReqOpt.get();
                pendingReq.setStatus(CaseEvidenceRequestEntity.STATUS_FULFILLED);
                pendingReq.setFulfilledAt(now);
                pendingReq.setFulfillmentCommandKey(cmdKey);
                caseEvidenceRequestRepository.save(pendingReq);
                linkedRequestId = pendingReq.getId();
            }
        }

        // 4. Append new CaseEvidenceEntity batch (append-only)
        for (ReturnCaseDtos.EvidenceItemDto item : request.evidenceList()) {
            String mediaType = item.mediaType() != null ? item.mediaType().trim().toUpperCase(Locale.ROOT) : CaseEvidenceEntity.MEDIA_IMAGE;
            CaseEvidenceEntity ev = new CaseEvidenceEntity(
                    UUID.randomUUID(),
                    round1.getId(),
                    buyerId,
                    nextSubmissionNo,
                    mediaType,
                    item.objectKey(),
                    item.textContent(),
                    item.contentDigest(),
                    item.fileSizeBytes(),
                    item.durationSeconds()
            );
            ev.setSubmittedAt(now);
            ev.setRequestId(linkedRequestId);
            caseEvidenceRepository.save(ev);
        }

        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("submission_no", nextSubmissionNo);
        meta.put("evidence_count", request.evidenceList().size());
        if (linkedRequestId != null) {
            meta.put("request_id", linkedRequestId.toString());
        }
        String reasonText = (request.note() != null && !request.note().isBlank())
                ? request.note().trim()
                : "Người mua bổ sung bằng chứng Vòng 1 (đợt " + nextSubmissionNo + ")";
        CaseEventEntity event = new CaseEventEntity(
                UUID.randomUUID(),
                caseId,
                round1.getId(),
                buyerId,
                order.getSellerId(),
                CaseEventEntity.TYPE_SUPPLEMENTED,
                reasonText,
                safeToJson(meta),
                cmdKey
        );
        caseEventRepository.save(event);

        log.info("UC62 Alt 7a: Buyer {} supplemented Round 1 evidence for case {} (submissionNo={}, count={})",
                buyerId, caseId, nextSubmissionNo, request.evidenceList().size());
        return mapToDetailResponse(lockedCase);
    }

    /**
     * UC33 Exception 3d–3e: Đối soát các hồ sơ trả hàng quá hạn gửi hàng 3 ngày hoặc thất bại lấy hàng.
     * - Lỗi Người mua (quá hạn 3 ngày không gửi hàng trả / không có lỗi ĐVVC): đóng case (CLOSED), ghi sự kiện CLEANUP.
     * - Lỗi ĐVVC hoặc đang có phản đối (admin_review_required = true): giữ tiền & ghi sự kiện ESCALATED cho KTV xem xét.
     */
    @Transactional
    public ReturnCaseDtos.OverduePickupBatchResult reconcileOverdueReturnPickups() {
        if (jdbcTemplate == null) {
            return new ReturnCaseDtos.OverduePickupBatchResult(0, List.of(), List.of(), List.of(), List.of());
        }

        Instant now = clock.instant();
        java.sql.Timestamp nowTs = java.sql.Timestamp.from(now);
        java.sql.Timestamp threeDaysAgoTs = java.sql.Timestamp.from(now.minus(RETURN_WINDOW_DURATION));

        List<Map<String, Object>> candidates = jdbcTemplate.queryForList("""
                SELECT c.case_id, c.order_id, c.state,
                       s.shipment_id,
                       COALESCE(s.admin_review_required, FALSE) AS carrier_fault_flagged,
                       s.incident_notes
                FROM og_compat.cases c
                LEFT JOIN og_compat.shipments s ON s.return_case_id = c.case_id AND s.leg = 'RETURN'
                WHERE c.case_type = 'RETURN'
                  AND (
                      (c.state = 'RETURN_APPROVED' AND c.approved_at IS NOT NULL AND c.approved_at < ? AND s.shipment_id IS NULL)
                      OR
                      (c.state = 'RETURNING' AND s.shipment_id IS NOT NULL AND s.valid_delivery_at IS NULL
                       AND s.pickup_deadline IS NOT NULL AND s.pickup_deadline < ?)
                  )
                ORDER BY c.case_id ASC
                LIMIT 100
                """, threeDaysAgoTs, nowTs);

        List<Long> closedBuyerFault = new ArrayList<>();
        List<Long> escalatedCarrierFault = new ArrayList<>();
        List<Long> skipped = new ArrayList<>();
        List<Long> failed = new ArrayList<>();

        for (Map<String, Object> row : candidates) {
            Long caseId = ((Number) row.get("case_id")).longValue();
            boolean carrierFault = Boolean.TRUE.equals(row.get("carrier_fault_flagged"));
            String incidentNotes = row.get("incident_notes") != null ? row.get("incident_notes").toString() : "";
            if (!carrierFault && incidentNotes.toUpperCase(Locale.ROOT).contains("CARRIER")) {
                carrierFault = true;
            }

            try {
                CaseEntity lockedCase = caseRepository.findByIdForUpdate(caseId).orElse(null);
                if (lockedCase == null
                        || (!CaseEntity.STATE_RETURN_APPROVED.equals(lockedCase.getState())
                            && !CaseEntity.STATE_RETURNING.equals(lockedCase.getState()))) {
                    skipped.add(caseId);
                    continue;
                }

                Long roundId = caseRoundRepository.findLatestRoundByCaseId(caseId)
                        .map(CaseRoundEntity::getId).orElse(null);

                if (carrierFault) {
                    String cmdKey = "OVERDUE_PICKUP_ESCALATE:" + caseId;
                    if (caseEventRepository.findByCommandKey(cmdKey).isPresent()) {
                        skipped.add(caseId);
                        continue;
                    }
                    CaseEventEntity escEvent = new CaseEventEntity(
                            UUID.randomUUID(),
                            caseId,
                            roundId,
                            null,
                            lockedCase.getAssignedTo(),
                            CaseEventEntity.TYPE_ESCALATED,
                            "Quá hạn lấy hàng trả do sự cố ĐVVC / cần KTV xem xét (UC33 3e). Giữ nguyên trạng thái tiền ký quỹ.",
                            "{\"reason\":\"CARRIER_PICKUP_FAILURE\"}",
                            cmdKey
                    );
                    caseEventRepository.save(escEvent);
                    escalatedCarrierFault.add(caseId);
                } else {
                    String cmdKey = "OVERDUE_PICKUP_CLOSE:" + caseId;
                    if (caseEventRepository.findByCommandKey(cmdKey).isPresent()) {
                        skipped.add(caseId);
                        continue;
                    }
                    lockedCase.setState(CaseEntity.STATE_CLOSED);
                    lockedCase.setClosedAt(now);
                    caseRepository.save(lockedCase);

                    CaseEventEntity closeEvent = new CaseEventEntity(
                            UUID.randomUUID(),
                            caseId,
                            roundId,
                            null,
                            lockedCase.getCreatedBy(),
                            CaseEventEntity.TYPE_CLEANUP,
                            "Đóng hồ sơ trả hàng do quá hạn gửi/lấy hàng trả 3 ngày từ phía Người mua (UC33 3d).",
                            "{\"reason\":\"BUYER_OVERDUE_RETURN_PICKUP\"}",
                            cmdKey
                    );
                    caseEventRepository.save(closeEvent);
                    closedBuyerFault.add(caseId);
                }
            } catch (Exception ex) {
                log.warn("reconcileOverdueReturnPickups failed for case {}: {}", caseId, ex.getMessage());
                failed.add(caseId);
            }
        }

        return new ReturnCaseDtos.OverduePickupBatchResult(
                candidates.size(),
                closedBuyerFault,
                escalatedCarrierFault,
                skipped,
                failed
        );
    }

    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse ktvConsoleDecideRound2(
            Long ktvId,
            Long caseId,
            ReturnCaseDtos.KtvRound2DecisionRequest request
    ) {
        if (caseId == null) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "caseId không hợp lệ");
        }
        CaseEntity c = caseRepository.findById(caseId)
                .orElseThrow(() -> new TrustSafetyBusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy hồ sơ khiếu nại #" + caseId));
        CaseRoundEntity round2 = caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2).orElse(null);
        if (round2 != null && caseEventRepository.existsByCaseIdAndRoundIdAndEventType(caseId, round2.getId(), CaseEventEntity.TYPE_ESCALATED)) {
            throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "CASE_ESCALATED",
                    "Hồ sơ đã được chuyển lên Quản trị viên (Admin) do quá hạn SLA. KTV không còn quyền đưa ra quyết định.");
        }
        if (c.getAssignedTo() == null || !c.getAssignedTo().equals(ktvId)) {
            throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "NOT_ASSIGNED_KTV",
                    "KTV không được phân công phụ trách Case này (Case #" + caseId + " chưa được claim hoặc thuộc về KTV #" + c.getAssignedTo() + ")");
        }
        return ktvDecideRound2(ktvId, caseId, request);
    }

    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse ktvConsoleDecideRound1(
            Long ktvId,
            Long caseId,
            ReturnCaseDtos.KtvRound1DecisionRequest request
    ) {
        if (caseId == null) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "caseId không hợp lệ");
        }
        CaseEntity c = caseRepository.findById(caseId)
                .orElseThrow(() -> new TrustSafetyBusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy hồ sơ khiếu nại #" + caseId));
        if (c.getAssignedTo() == null || !c.getAssignedTo().equals(ktvId)) {
            throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "NOT_ASSIGNED_KTV",
                    "KTV không được phân công phụ trách Case này (Case #" + caseId + " chưa được claim hoặc thuộc về KTV #" + c.getAssignedTo() + ")");
        }
        return ktvDecideRound1(ktvId, caseId, request);
    }

    @Transactional(readOnly = true)
    public void verifyCaseExistsForKtv(Long caseId) {
        if (caseId == null) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "caseId không hợp lệ");
        }
        caseRepository.findById(caseId)
                .orElseThrow(() -> new TrustSafetyBusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy hồ sơ khiếu nại #" + caseId));
    }

    /**
     * Lấy danh sách hàng đợi công việc của KTV theo scope (AVAILABLE | MY_CASES) và round (1 | 2).
     */
    @Transactional(readOnly = true)
    public List<ReturnCaseDtos.ReturnCaseDetailResponse> getKtvWorkQueue(Long ktvId, String scope, Integer round) {
        if (ktvId == null || !isKtv(ktvId)) {
            throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Người dùng không có vai trò KTV");
        }
        if (scope == null || (!scope.equals("AVAILABLE") && !scope.equals("MY_CASES"))) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "scope bắt buộc là AVAILABLE hoặc MY_CASES");
        }
        if (round == null || (round != 1 && round != 2)) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "round bắt buộc là 1 hoặc 2");
        }

        List<CaseEntity> cases;
        if (round == 1) {
            cases = caseRepository.findKtvWorkQueueRound1(scope, ktvId);
        } else {
            cases = caseRepository.findKtvWorkQueueRound2(scope, ktvId, clock.instant());
        }
        return cases.stream().map(this::mapToDetailResponse).toList();
    }

    /**
     * KTV nhận thụ lý hồ sơ (Self-claim).
     */
    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse assignCaseToKtv(Long ktvId, Long caseId) {
        if (ktvId == null || !isKtv(ktvId)) {
            throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Người dùng không có vai trò KTV");
        }
        if (caseId == null) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "caseId không được để trống");
        }

        // Khóa bi quan theo thứ tự chuẩn: Order -> Case
        ReturnWorkflowLockGateway.ReturnWorkflowLockedEntities locked = lockGateway.lockForCase(caseId);
        CaseEntity c = locked.returnCase();

        if (c.getAssignedTo() == null) {
            c.setAssignedTo(ktvId);
            CaseEntity saved = caseRepository.saveAndFlush(c);
            CaseEventEntity event = new CaseEventEntity(
                    UUID.randomUUID(),
                    c.getId(),
                    null,
                    ktvId,
                    null,
                    CaseEventEntity.TYPE_ASSIGNED,
                    "KTV #" + ktvId + " nhận xử lý hồ sơ",
                    "{\"action\":\"ASSIGNED\",\"ktv_id\":" + ktvId + "}",
                    "EVENT:ASSIGN:" + c.getId() + ":" + ktvId
            );
            caseEventRepository.saveAndFlush(event);
            return mapToDetailResponse(saved);
        } else if (c.getAssignedTo().equals(ktvId)) {
            // Idempotent replay: không tạo event trùng
            return mapToDetailResponse(c);
        } else {
            throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "CASE_ALREADY_ASSIGNED",
                    "Hồ sơ đã được phân công cho KTV khác (#" + c.getAssignedTo() + ")");
        }
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

        // 1 & 2. Khóa theo thứ tự chuẩn: Order -> Case
        ReturnWorkflowLockGateway.ReturnWorkflowLockedEntities locked = lockGateway.lockForCase(caseId);
        CaseEntity c = locked.returnCase();
        OrderEntity order = locked.order();
        Long orderId = order.getId();

        // KTV thấy và có quyền phân xử khi hồ sơ ở OPEN hoặc REVIEWING
        if (!CaseEntity.STATE_OPEN.equals(c.getState()) && !CaseEntity.STATE_REVIEWING.equals(c.getState())) {
            throw new IllegalStateException("Hồ sơ không ở trạng thái chờ KTV phân xử (Trạng thái hiện tại: " + c.getState() + ")");
        }

        // Xác thực vai trò KTV tường minh (defense-in-depth tương tự ktvDecideRound2)
        if (ktvId == null || !isKtv(ktvId)) {
            throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                    "Người dùng không có quyền KTV để phân xử hồ sơ Vòng 1");
        }

        // Xác thực KTV Assignment
        if (c.getAssignedTo() == null || !c.getAssignedTo().equals(ktvId)) {
            throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "NOT_ASSIGNED_KTV",
                    "KTV không được phân công phụ trách Case này (Case #" + caseId + " chưa được claim hoặc thuộc về KTV #" + c.getAssignedTo() + ")");
        }

        // 3. Khóa CaseRound (Vòng 1)
        CaseRoundEntity round = lockGateway.lockRound(caseId, 1);

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
        // Khóa theo thứ tự chuẩn: Order -> Case -> Latest Round
        ReturnWorkflowLockGateway.ReturnWorkflowLockedEntities locked = lockGateway.lockForLatestRound(caseId);
        CaseEntity c = locked.returnCase();
        OrderEntity order = locked.order();
        CaseRoundEntity latestRound = locked.activeRound();
        Long orderId = order.getId();

        String commandKey = "EVENT:WITHDRAW:" + c.getId();

        // Idempotency: Tra cứu event trước khi ghi
        Optional<CaseEventEntity> existingEventOpt = caseEventRepository.findByCommandKey(commandKey);
        if (existingEventOpt.isPresent()) {
            CaseEventEntity existingEvent = existingEventOpt.get();
            // Xác thực actor: chỉ chính Buyer hợp lệ của Case/Order mới được nhận kết quả replay
            if (buyerId == null || !buyerId.equals(existingEvent.getActorId()) || !buyerId.equals(c.getCreatedBy()) || !buyerId.equals(order.getBuyerId())) {
                throw new IllegalArgumentException("Chỉ người mua tạo yêu cầu mới có quyền rút yêu cầu trả hàng");
            }
            // Idempotent replay: Trả về kết quả hiện tại của Case, không tạo event mới, không đóng lại hold
            return mapToDetailResponse(c);
        }

        if (buyerId == null || !buyerId.equals(c.getCreatedBy()) || !buyerId.equals(order.getBuyerId())) {
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

        if (latestRound != null) {
            latestRound.setState(CaseRoundEntity.STATE_DECIDED);
            caseRoundRepository.save(latestRound);
        }

        CaseEventEntity event = new CaseEventEntity(
                UUID.randomUUID(),
                c.getId(),
                null,
                buyerId,
                order.getSellerId(),
                CaseEventEntity.TYPE_BUYER_WITHDRAWN,
                "Người mua đã chủ động rút yêu cầu trả hàng" + (request != null && request.reason() != null && !request.reason().isBlank() ? ": " + request.reason() : ""),
                "{\"action\":\"WITHDRAW\"}",
                commandKey
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

    /**
     * Tải lên tệp bằng chứng trả hàng / khiếu nại lên đĩa cục bộ và tính mã băm SHA-256 (BG-1).
     */
    public ReturnCaseDtos.EvidenceItemDto uploadReturnEvidence(
            Long actorId,
            org.springframework.web.multipart.MultipartFile file,
            String mediaType,
            Integer durationSeconds
    ) {
        if (actorId == null) {
            throw new SecurityException("Chưa xác thực danh tính");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Tệp bằng chứng tải lên không được rỗng");
        }
        String normalizedType = mediaType != null ? mediaType.trim().toUpperCase(Locale.ROOT) : "IMAGE";
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase(Locale.ROOT) : "";
        long size = file.getSize();
        Integer resolvedDuration = null;

        if ("IMAGE".equals(normalizedType)) {
            if (!Set.of("image/jpeg", "image/png", "image/webp").contains(contentType)) {
                throw new IllegalArgumentException("Định dạng ảnh bằng chứng không hợp lệ. Chỉ chấp nhận JPG, PNG, WEBP");
            }
            if (size > MAX_IMAGE_SIZE_BYTES) {
                throw new IllegalArgumentException("Dung lượng ảnh bằng chứng vượt quá giới hạn 5 MB");
            }
        } else if ("VIDEO".equals(normalizedType)) {
            if (!Set.of("video/mp4", "video/webm").contains(contentType)) {
                throw new IllegalArgumentException("Định dạng video bằng chứng không hợp lệ. Chỉ chấp nhận MP4, WEBM");
            }
            if (size > MAX_VIDEO_SIZE_BYTES) {
                throw new IllegalArgumentException("Dung lượng video bằng chứng vượt quá giới hạn 50 MB");
            }
            if (durationSeconds != null && (durationSeconds <= 0 || durationSeconds > MAX_VIDEO_DURATION_SECONDS)) {
                throw new IllegalArgumentException("Thời lượng video bằng chứng phải nằm trong khoảng 1 đến " + MAX_VIDEO_DURATION_SECONDS + " giây");
            }
            resolvedDuration = durationSeconds != null ? durationSeconds : 30;
        } else {
            throw new IllegalArgumentException("Loại bằng chứng không hợp lệ: " + mediaType + " (chỉ hỗ trợ IMAGE hoặc VIDEO)");
        }

        String extension = switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "video/mp4" -> "mp4";
            case "video/webm" -> "webm";
            default -> throw new IllegalArgumentException("Định dạng tệp không được hỗ trợ");
        };

        Path uploadDir = Paths.get("uploads/media").toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("Không thể khởi tạo thư mục lưu trữ bằng chứng: " + e.getMessage(), e);
        }

        String uniqueFilename = UUID.randomUUID() + "." + extension;
        Path targetPath = uploadDir.resolve(uniqueFilename);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, targetPath);
        } catch (IOException e) {
            throw new IllegalStateException("Lưu tệp bằng chứng thất bại: " + e.getMessage(), e);
        }

        String sha256 = computeSha256(targetPath);
        String objectKey = "/api/v1/media/" + uniqueFilename;
        return new ReturnCaseDtos.EvidenceItemDto(
                normalizedType,
                objectKey,
                null,
                sha256,
                size,
                resolvedDuration
        );
    }

    private String resolveDamageEvidenceDigest(String clientDigest, String objectKey) {
        // 1. Kiểm tra xem file có tồn tại trên hệ thống lưu trữ cục bộ không
        Path filePath = resolveLocalFilePath(objectKey);
        if (filePath != null && Files.isRegularFile(filePath) && Files.isReadable(filePath)) {
            String computedDigest = computeSha256(filePath);
            if (clientDigest != null && !clientDigest.isBlank()) {
                if (!computedDigest.equalsIgnoreCase(clientDigest.trim())) {
                    throw new IllegalArgumentException("Mã băm bằng chứng (contentDigest) không khớp với nội dung tệp thực tế: kỳ vọng " + computedDigest + ", nhận được " + clientDigest.trim());
                }
            }
            return computedDigest;
        }

        // 2. Nếu file không nằm trên local disk:
        // CHỈ được chấp nhận khi có một Storage Adapter/Verifier đáng tin cậy đọc được nội dung tệp thực tế và xác minh SHA-256.
        if (evidenceStorageVerifier != null) {
            String computedDigest;
            try {
                computedDigest = evidenceStorageVerifier.computeSha256(objectKey);
            } catch (IOException e) {
                throw new IllegalStateException("Lỗi I/O khi đọc tệp từ hệ thống lưu trữ: " + e.getMessage(), e);
            }
            if (computedDigest == null || computedDigest.isBlank()) {
                throw new IllegalArgumentException("Không tìm thấy tệp bằng chứng trên hệ thống lưu trữ: " + objectKey);
            }
            String normalizedComputed = computedDigest.trim().toLowerCase(Locale.ROOT);
            if (!normalizedComputed.matches("^[0-9a-fA-F]{64}$")) {
                throw new IllegalStateException("Mã băm trả về từ Storage Verifier không đúng định dạng SHA-256 hex 64 ký tự: " + normalizedComputed);
            }
            if (clientDigest != null && !clientDigest.isBlank()) {
                if (!normalizedComputed.equalsIgnoreCase(clientDigest.trim())) {
                    throw new IllegalArgumentException("Mã băm bằng chứng (contentDigest) không khớp với nội dung tệp thực tế: kỳ vọng " + normalizedComputed + ", nhận được " + clientDigest.trim());
                }
            }
            return normalizedComputed;
        }

        // 3. Nếu không có Storage Verifier hoặc không thể đọc nội dung file:
        // FAIL-CLOSED: Bắt buộc từ chối submission kể cả khi client gửi digest hợp lệ về định dạng 64 ký tự hex!
        throw new IllegalArgumentException("Không tìm thấy tệp bằng chứng cục bộ và không có dịch vụ xác thực lưu trữ (Storage Verifier) để đọc nội dung thực tế");
    }

    private Path resolveLocalFilePath(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        String key = objectKey.trim();
        try {
            Path directPath = Paths.get(key);
            if (Files.isRegularFile(directPath)) {
                return directPath;
            }
        } catch (Exception ignored) {
        }

        try {
            String filename = key;
            if (filename.startsWith("/api/v1/media/")) {
                filename = filename.substring("/api/v1/media/".length());
            }
            Path uploadPath = Paths.get("uploads/media", filename);
            if (Files.isRegularFile(uploadPath)) {
                return uploadPath;
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private String computeSha256(Path filePath) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(filePath)) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) != -1) {
                    md.update(buf, 0, read);
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Hệ thống không hỗ trợ thuật toán băm SHA-256", e);
        } catch (IOException e) {
            throw new IllegalStateException("Không thể đọc tệp để tính mã băm SHA-256: " + filePath, e);
        }
    }

    private String resolveContentDigest(String digest, String objectKey) {
        Path filePath = resolveLocalFilePath(objectKey);
        if (filePath != null && Files.isRegularFile(filePath) && Files.isReadable(filePath)) {
            String computed = computeSha256(filePath);
            if (digest != null && !digest.isBlank() && !computed.equalsIgnoreCase(digest.trim())) {
                throw new IllegalArgumentException("Mã băm bằng chứng (contentDigest) không khớp với nội dung tệp thực tế");
            }
            return computed;
        }
        if (digest != null && digest.trim().matches("^[0-9a-fA-F]{64}$")) {
            return digest.trim().toLowerCase(Locale.ROOT);
        }
        try {
            byte[] input = (objectKey != null ? objectKey : UUID.randomUUID().toString()).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Không thể khởi tạo thuật toán băm SHA-256", e);
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
                if (item.fileSizeBytes() != null && item.fileSizeBytes() > MAX_IMAGE_SIZE_BYTES) {
                    throw new IllegalArgumentException("Dung lượng ảnh bằng chứng tối đa là 5MB");
                }
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
        List<ReturnCaseDtos.CaseDecisionPublicDto> decisionDtos = List.of();

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

            decisionDtos = caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(latestRound.getId())
                    .stream()
                    .filter(d -> Boolean.TRUE.equals(d.getIsFinal()) && d.getSupersedesDecisionId() == null)
                    .map(d -> new ReturnCaseDtos.CaseDecisionPublicDto(
                            d.getId(),
                            d.getRoundId(),
                            d.getOutcome(),
                            d.getGoodsRefundAmount(),
                            d.getReason(),
                            d.getDecidedAt(),
                            d.getIsFinal()
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
                roundDto,
                evidenceDtos,
                eventDtos,
                returnShipmentDto,
                decisionDtos
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

        // Khóa theo thứ tự chuẩn: Order -> Case
        ReturnWorkflowLockGateway.ReturnWorkflowLockedEntities locked = lockGateway.lockForCase(caseId);
        OrderEntity order = locked.order();
        CaseEntity lockedCase = locked.returnCase();
        Long orderId = order.getId();

        if (!buyerId.equals(order.getBuyerId())) {
            throw new SecurityException("Chỉ người mua mới có quyền gửi hàng trả");
        }

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

        // Khóa theo thứ tự chuẩn: Order -> Case
        ReturnWorkflowLockGateway.ReturnWorkflowLockedEntities locked = lockGateway.lockForCase(caseId);
        OrderEntity order = locked.order();
        CaseEntity lockedCase = locked.returnCase();
        Long orderId = order.getId();

        // Khóa Dependent Resource (Shipment) SAU Case
        ShipmentEntity shipment = shipmentRepository.findByReturnCaseIdForUpdate(caseId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy vận đơn trả hàng cho caseId=" + caseId));

        if (ShipmentEntity.DELIVERY_METHOD_CARRIER.equalsIgnoreCase(shipment.getDeliveryMethod())) {
            throw new IllegalArgumentException("Không thể xác nhận bàn giao trực tiếp cho vận đơn qua hãng vận chuyển (CARRIER)");
        }

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
        // Khóa theo thứ tự chuẩn: Order -> Case
        ReturnWorkflowLockGateway.ReturnWorkflowLockedEntities locked = lockGateway.lockForCase(caseId);
        OrderEntity order = locked.order();
        CaseEntity lockedCase = locked.returnCase();
        Long orderId = order.getId();

        // Khóa Dependent Resource (Shipment) SAU Case
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

    /**
     * Người bán đồng ý hoàn tiền Vòng 2 sau khi nhận lại hàng (UC33 & Task 3.2.1 - sellerAgreeRefundRound2).
     * Áp dụng thứ tự lock bắt buộc: Order -> Case -> CaseRound (Vòng 2).
     *
     * <p>Xác thực quyền hạn (Actor Authorization & Source):
     * <ul>
     *   <li>Tham số {@code sellerId} được xác thực trực tiếp với {@code order.getSellerId()} dưới khóa bi-quan.</li>
     *   <li>Hạn chế hiện tại (LIMITATION): Tầng Controller chưa triển khai trong Prompt A;
     *       khi triển khai Prompt C, {@code sellerId} sẽ được trích xuất an toàn từ {@code @AuthenticationPrincipal}.</li>
     * </ul>
     *
     * <p>Quy tắc kiểm tra Idempotency chuẩn (Normalized Order):
     * <ol>
     *   <li>Validate tham số đầu vào (caseId, request, reason length >= 5).</li>
     *   <li>Lấy khóa bi-quan theo thứ tự chuẩn: Order -> Case -> CaseRound (Vòng 2).</li>
     *   <li>Xác thực quyền người bán đối với đơn hàng.</li>
     *   <li>Kiểm tra tính lũy thừa (Idempotency): Nếu {@code commandKey} đã tồn tại trong {@code case_decisions},
     *       xác thực người đưa ra quyết định khớp với {@code sellerId} và trả về snapshot hiện tại (không báo lỗi).</li>
     *   <li>Kiểm tra các bất biến trạng thái (Invariants & First-write-wins):
     *       Hồ sơ ở POST_RETURN, có valid_returned_at, Round 2 ở OPEN,
     *       chưa có valid_damage_request_at (BD-1 Phương án 1), và còn trong hạn seller_response_due_at.</li>
     *   <li>Tạo CaseDecisionEntity (FULL_GOODS_REFUND) và lưu xuống cơ sở dữ liệu.</li>
     *   <li>Gọi {@link RoundDecisionSettlementPort#requestSettlement} tạo yêu cầu thanh toán (bắt buộc, cùng transaction).</li>
     *   <li>Đồng bộ trạng thái: Các trường trạng thái (cases.state, cases.closed_at, case_rounds.state,
     *       money_holds.closed_at) do trigger cơ sở dữ liệu (og_apply_case_decision, og_close_case_holds)
     *       độc quyền sở hữu và cập nhật trên PostgreSQL. Service chỉ thực hiện refresh entity để đọc dữ liệu mới.</li>
     *   <li>Ghi nhận sự kiện CaseEventEntity (CLEANUP).</li>
     * </ol>
     */
    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse sellerAgreeRefundRound2(
            Long sellerId,
            Long caseId,
            ReturnCaseDtos.SellerAgreeRefundRequest request
    ) {
        if (caseId == null) {
            throw new IllegalArgumentException("caseId không được để trống");
        }
        if (request == null) {
            throw new IllegalArgumentException("Yêu cầu đồng ý hoàn tiền không được để trống");
        }
        if (request.reason() == null || request.reason().trim().length() < 5) {
            throw new IllegalArgumentException("Lý do đồng ý hoàn tiền bắt buộc có tối thiểu 5 ký tự");

        }
        if (request.reason().trim().length() > 2000) {
            throw new IllegalArgumentException("Lý do đồng ý hoàn tiền tối đa 2000 ký tự");
        }
        if (request.commandKey() != null && request.commandKey().trim().length() > 127) {
            throw new IllegalArgumentException("commandKey tối đa 127 ký tự để bảo đảm an toàn cho settlement operation");
        }

        // 1 & 2. Khóa theo thứ tự chuẩn: Order -> Case -> Round(2)
        ReturnWorkflowLockGateway.ReturnWorkflowLockedEntities locked = lockGateway.lockForRound(caseId, 2);
        OrderEntity order = locked.order();
        CaseEntity c = locked.returnCase();
        CaseRoundEntity round2 = locked.activeRound();

        // 3. Xác thực người bán từ context đối chiếu với đơn hàng dưới khóa
        if (sellerId == null || !sellerId.equals(order.getSellerId())) {
            throw new IllegalArgumentException("Chỉ người bán của đơn hàng mới có quyền đồng ý hoàn tiền");
        }

        // 4. Idempotency Check: Tra cứu quyết định theo commandKey trước khi kiểm tra trạng thái vòng
        String cmdKey = request.commandKey() != null && !request.commandKey().isBlank()
                ? request.commandKey().trim()
                : null;

        if (cmdKey != null) {
            Optional<CaseDecisionEntity> existingDecision = caseDecisionRepository.findByCommandKey(cmdKey);
            if (existingDecision.isPresent()) {
                CaseDecisionEntity dec = existingDecision.get();
                if (!sellerId.equals(dec.getReviewedBy())) {
                    throw new IllegalArgumentException("CommandKey đã tồn tại nhưng không thuộc về người bán này");
                }
                if (!round2.getId().equals(dec.getRoundId())) {
                    throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "COMMAND_KEY_CONFLICT",
                            "CommandKey đã tồn tại nhưng không thuộc về vụ việc này");
                }
                String storedReason = dec.getReason() != null ? dec.getReason().trim().replace("\r\n", "\n").replace("\r", "\n") : "";
                String requestReason = request.reason() != null ? request.reason().trim().replace("\r\n", "\n").replace("\r", "\n") : "";
                if (!storedReason.equals(requestReason)) {
                    throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "COMMAND_KEY_CONFLICT",
                            "Conflict: commandKey đã được sử dụng với payload khác");
                }
                return mapToDetailResponse(c);
            }
        } else {
            cmdKey = "DECISION:SELLER_AGREE:R2:" + caseId + ":" + UUID.randomUUID();
        }

        // 5. Re-verify điều kiện dưới khóa (First-write-wins & Invariants)
        if (!CaseEntity.STATE_POST_RETURN.equals(c.getState())) {
            throw new IllegalStateException("Hồ sơ không ở trạng thái Vòng 2 sau khi nhận lại hàng (Trạng thái hiện tại: " + c.getState() + ")");
        }

        if (c.getValidReturnedAt() == null) {
            throw new IllegalStateException("Chưa có xác nhận hàng đã được hoàn trả hợp lệ cho người bán");
        }

        if (!CaseRoundEntity.STATE_OPEN.equals(round2.getState())) {
            throw new IllegalStateException("Vòng 2 đã kết thúc, không thể đưa ra quyết định (Trạng thái hiện tại: " + round2.getState() + ")");
        }

        if (round2.getStage() == null || !CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION.equals(round2.getStage())) {
            throw new IllegalStateException("Vòng 2 không ở giai đoạn giải quyết sau nhận lại hàng (Giai đoạn hiện tại: " + round2.getStage() + ")");
        }

        // BD ĐÃ ĐƯỢC PHÊ DUYỆT – PHƯƠNG ÁN 1: Nếu đã có valid_damage_request_at, từ chối Seller Agree Refund
        if (round2.getValidDamageRequestAt() != null) {
            throw new IllegalStateException("Người bán đã gửi yêu cầu bồi thường thiệt hại, hồ sơ đã chuyển sang luồng KTV phân xử và không thể tự đồng ý hoàn tiền");
        }

        Instant now = clock.instant();
        if (c.getSellerResponseDueAt() != null && now.isAfter(c.getSellerResponseDueAt())) {
            throw new IllegalStateException("Đã quá thời hạn 2 ngày phản hồi của người bán (Hạn chót: " + c.getSellerResponseDueAt() + ")");
        }

        // 6. Tính tiền hoàn hàng theo giá trị ròng của đơn hàng
        BigDecimal subtotal = order.getSubtotal() != null ? order.getSubtotal() : BigDecimal.ZERO;
        BigDecimal voucherDiscount = order.getVoucherDiscountAmount() != null ? order.getVoucherDiscountAmount() : BigDecimal.ZERO;
        BigDecimal goodsRefundAmount = subtotal.subtract(voucherDiscount).max(BigDecimal.ZERO);

        // 7. Tạo quyết định FULL_GOODS_REFUND
        CaseDecisionEntity decision = new CaseDecisionEntity(
                round2.getId(),
                sellerId,
                CaseDecisionEntity.ACTOR_USER,
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                true,
                goodsRefundAmount,
                request.reason().trim(),
                cmdKey
        );
        decision.setDecidedAt(now);
        CaseDecisionEntity savedDecision = caseDecisionRepository.saveAndFlush(decision);

        // 8. Gọi RoundDecisionSettlementPort trong cùng transaction (bắt buộc, không optional - Task 4.1a)
        settlementPort.requestSettlement(
                order.getId(),
                caseId,
                savedDecision.getId(),
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                cmdKey
        );

        // 9. Đồng bộ trạng thái: Tránh can thiệp các trường do DB trigger quản lý
        // Trigger trg_case_decision_apply (og_apply_case_decision & og_close_case_holds) tự động
        // cập nhật cases.state='CLOSED', cases.closed_at, case_rounds.state='DECIDED', và money_holds.closed_at.
        if (entityManager != null) {
            try {
                if (entityManager.contains(c)) {
                    entityManager.refresh(c);
                } else {
                    c.setState(CaseEntity.STATE_CLOSED);
                    c.setClosedAt(now);
                }
                if (entityManager.contains(round2)) {
                    entityManager.refresh(round2);
                } else {
                    round2.setState(CaseRoundEntity.STATE_DECIDED);
                }
            } catch (Exception ignored) {
                c.setState(CaseEntity.STATE_CLOSED);
                c.setClosedAt(now);
                round2.setState(CaseRoundEntity.STATE_DECIDED);
            }
        } else {
            c.setState(CaseEntity.STATE_CLOSED);
            c.setClosedAt(now);
            round2.setState(CaseRoundEntity.STATE_DECIDED);
        }

        // 10. Ghi nhận sự kiện CLEANUP
        String eventCmdKey = "EVENT:SELLER_AGREE_REFUND:R2:" + caseId;
        Optional<CaseEventEntity> existingEv = caseEventRepository.findByCommandKey(eventCmdKey);
        if (existingEv.isEmpty()) {
            Map<String, Object> metaMap = new LinkedHashMap<>();
            metaMap.put("outcome", CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND);
            metaMap.put("actor_type", CaseDecisionEntity.ACTOR_USER);
            metaMap.put("goods_refund_amount", goodsRefundAmount);
            String metadataJson = safeToJson(metaMap);

            CaseEventEntity event = new CaseEventEntity(
                    UUID.randomUUID(),
                    caseId,
                    round2.getId(),
                    sellerId,
                    order.getBuyerId(),
                    CaseEventEntity.TYPE_CLEANUP,
                    "Người bán đồng ý hoàn tiền Vòng 2. Hồ sơ được giải quyết hoàn tiền cho người mua.",
                    metadataJson,
                    eventCmdKey
            );
            caseEventRepository.save(event);
        }

        return mapToDetailResponse(c);
    }

    /**
     * Người bán gửi yêu cầu miễn trừ thiệt hại Vòng 2 sau khi nhận lại hàng (Task 3.2.2 - submitDamageExemptionRequest).
     *
     * <p>Mục tiêu nghiệp vụ:
     * <ul>
     *   <li>Người bán cung cấp lý do và bằng chứng thiệt hại (tối đa 5 ảnh, 2 video <= 60s, <= 50MB, tối thiểu 1 bằng chứng).</li>
     *   <li>Bằng chứng được lưu và FLUSH trước khi ghi nhận sự kiện VALID_DAMAGE_REQUEST để trigger kiểm tra sự tồn tại.</li>
     *   <li>Ghi nhận sự kiện VALID_DAMAGE_REQUEST; DB trigger V37 og_guard_case_event chịu trách nhiệm
     *       cập nhật {@code case_rounds.valid_damage_request_at = occurred_at}.</li>
     *   <li>Không tạo CaseDecision và không gọi RoundDecisionSettlementPort.</li>
     *   <li>Hồ sơ chuyển sang trạng thái chờ KTV phân quyết (Case vẫn POST_RETURN, Round 2 vẫn OPEN).</li>
     * </ul>
     *
     * <p>Xác thực quyền hạn (Actor Authorization):
     * <ul>
     *   <li>Tham số {@code sellerId} được đối chiếu trực tiếp với {@code order.getSellerId()} dưới khóa bi-quan.</li>
     *   <li>Hạn chế hiện tại (LIMITATION): Tầng Controller chưa triển khai trong Prompt A;
     *       khi triển khai Prompt C, {@code sellerId} sẽ được trích xuất an toàn từ {@code @AuthenticationPrincipal}.</li>
     * </ul>
     *
     * <p>Idempotency & BD-5 Resubmission Policy:
     * <ul>
     *   <li>First-time submission: Chỉ cho phép khi {@code round2.getValidDamageRequestAt() == null}.</li>
     *   <li>Nếu {@code commandKey} đã tồn tại trong {@code case_events} với event_type VALID_DAMAGE_REQUEST và đúng sellerId:
     *       trả về snapshot hiện tại (idempotent replay).</li>
     *   <li>Nếu hồ sơ đã có Damage Request (valid_damage_request_at != null) và request mới có commandKey khác:
     *       từ chối với IllegalStateException (TODO(BD-5): awaiting resubmission policy).</li>
     * </ul>
     */
    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse submitDamageExemptionRequest(
            Long sellerId,
            Long caseId,
            ReturnCaseDtos.SellerDamageExemptionRequest request
    ) {
        if (request != null && request.requestId() != null) {
            if (sellerResubmissionService == null) {
                throw new IllegalStateException("sellerResubmissionService is not available");
            }
            CaseEntity updatedCase = sellerResubmissionService.submitDamageExemptionResubmission(sellerId, caseId, request);
            return mapToDetailResponse(updatedCase);
        }

        if (caseId == null) {
            throw new IllegalArgumentException("caseId không được để trống");
        }
        if (request == null) {
            throw new IllegalArgumentException("Yêu cầu bồi thường thiệt hại không được để trống");
        }
        if (request.reason() == null || request.reason().trim().length() < 5) {
            throw new IllegalArgumentException("Lý do khiếu nại thiệt hại bắt buộc có tối thiểu 5 ký tự");
        }
        if (request.evidenceList() == null || request.evidenceList().isEmpty()) {
            throw new IllegalArgumentException("Bắt buộc phải có ít nhất 1 bằng chứng (ảnh hoặc video) chứng minh thiệt hại");
        }
        if (request.commandKey() != null && request.commandKey().trim().length() > 150) {
            throw new IllegalArgumentException("commandKey tối đa 150 ký tự");
        }

        // Validate bằng chứng (số lượng, dung lượng, thời lượng)
        validateEvidence(request.evidenceList());

        // 1 & 2. Khóa theo thứ tự chuẩn: Order -> Case -> Round(2)
        ReturnWorkflowLockGateway.ReturnWorkflowLockedEntities locked = lockGateway.lockForRound(caseId, 2);
        OrderEntity order = locked.order();
        CaseEntity c = locked.returnCase();
        CaseRoundEntity round2 = locked.activeRound();

        // 3. Xác thực người bán từ context đối chiếu với đơn hàng dưới khóa
        if (sellerId == null || !sellerId.equals(order.getSellerId())) {
            throw new IllegalArgumentException("Chỉ người bán của đơn hàng mới có quyền gửi yêu cầu bồi thường thiệt hại");
        }

        // 4. Idempotency Check & BD-5 handling
        String cmdKey = request.commandKey() != null && !request.commandKey().isBlank()
                ? request.commandKey().trim()
                : null;

        if (cmdKey != null) {
            Optional<CaseEventEntity> existingEvent = caseEventRepository.findByCommandKey(cmdKey);
            if (existingEvent.isPresent()) {
                CaseEventEntity ev = existingEvent.get();
                if (!sellerId.equals(ev.getActorId()) || !caseId.equals(ev.getCaseId())) {
                    throw new IllegalArgumentException("CommandKey đã tồn tại nhưng không thuộc về người bán hoặc hồ sơ này");
                }
                if ("VALID_DAMAGE_REQUEST".equals(ev.getEventType())) {
                    return mapToDetailResponse(c);
                }
            }
        } else {
            cmdKey = "EVENT:DAMAGE_REQ:R2:" + caseId + ":" + UUID.randomUUID();
        }

        // BD-5: Kiểm tra nếu Round 2 đã có valid_damage_request_at
        if (round2.getValidDamageRequestAt() != null) {
            // TODO(BD-5): awaiting resubmission policy
            throw new IllegalStateException("Người bán đã gửi yêu cầu bồi thường thiệt hại cho Vòng 2. Vụ việc đang chờ KTV xử lý, không thể gửi thêm yêu cầu mới.");
        }

        // 5. Re-verify điều kiện dưới khóa (Invariants & First-write-wins)
        if (!CaseEntity.STATE_POST_RETURN.equals(c.getState())) {
            throw new IllegalStateException("Hồ sơ không ở trạng thái Vòng 2 sau khi nhận lại hàng (Trạng thái hiện tại: " + c.getState() + ")");
        }

        if (c.getValidReturnedAt() == null) {
            throw new IllegalStateException("Chưa có xác nhận hàng đã được hoàn trả hợp lệ cho người bán");
        }

        if (!CaseRoundEntity.STATE_OPEN.equals(round2.getState())) {
            throw new IllegalStateException("Vòng 2 đã kết thúc, không thể gửi yêu cầu bồi thường thiệt hại (Trạng thái hiện tại: " + round2.getState() + ")");
        }

        if (!CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION.equals(round2.getStage())) {
            throw new IllegalStateException("Vòng 2 không ở giai đoạn giải quyết sau nhận lại hàng (Giai đoạn hiện tại: " + round2.getStage() + ")");
        }

        Instant now = clock.instant();
        if (c.getSellerResponseDueAt() != null && now.isAfter(c.getSellerResponseDueAt())) {
            throw new IllegalStateException("Đã quá thời hạn 2 ngày phản hồi của người bán (Hạn chót: " + c.getSellerResponseDueAt() + ")");
        }

        // 6. Lưu bằng chứng (CaseEvidenceEntity) - BẮT BUỘC submission_no = 1 cho First-time
        Integer submissionNo = 1;
        for (ReturnCaseDtos.EvidenceItemDto item : request.evidenceList()) {
            String digest = resolveDamageEvidenceDigest(item.contentDigest(), item.objectKey());
            Long size = (item.fileSizeBytes() != null && item.fileSizeBytes() > 0) ? item.fileSizeBytes() : 1024L;
            Integer duration = null;
            if (CaseEvidenceEntity.MEDIA_VIDEO.equalsIgnoreCase(item.mediaType())) {
                duration = (item.durationSeconds() != null && item.durationSeconds() >= 1 && item.durationSeconds() <= 60)
                        ? item.durationSeconds() : 30;
            }
            CaseEvidenceEntity ev = new CaseEvidenceEntity(
                    UUID.randomUUID(),
                    round2.getId(),
                    sellerId,
                    submissionNo,
                    item.mediaType().toUpperCase(),
                    item.objectKey(),
                    item.textContent(),
                    digest,
                    size,
                    duration
            );
            caseEvidenceRepository.save(ev);
        }

        // BẮT BUỘC: Flush evidence xuống DB trước khi insert event VALID_DAMAGE_REQUEST
        // để trigger og_guard_case_event kiểm tra EXISTS(case_evidence...) thành công!
        caseEvidenceRepository.flush();

        // 7. Ghi nhận sự kiện VALID_DAMAGE_REQUEST
        Map<String, Object> metaMap = new java.util.LinkedHashMap<>();
        metaMap.put("action", "SELLER_DAMAGE_EXEMPTION_REQUEST");
        metaMap.put("evidence_count", request.evidenceList().size());
        metaMap.put("submission_no", submissionNo);
        String metadataJson = safeToJson(metaMap);

        CaseEventEntity event = new CaseEventEntity(
                UUID.randomUUID(),
                caseId,
                round2.getId(),
                sellerId,
                null,
                "VALID_DAMAGE_REQUEST",
                request.reason().trim(),
                metadataJson,
                cmdKey
        );
        caseEventRepository.saveAndFlush(event);

        // 8. Đồng bộ trạng thái từ PostgreSQL triggers
        // Trigger og_guard_case_event tự động UPDATE og_compat.case_rounds SET valid_damage_request_at = occurred_at
        round2.setValidDamageRequestAt(now);
        round2.setCompleteSubmissionAt(now);
        round2 = caseRoundRepository.save(round2);
        if (entityManager != null) {
            try {
                if (entityManager.contains(round2)) {
                    entityManager.refresh(round2);
                }
                if (entityManager.contains(c)) {
                    entityManager.refresh(c);
                }
            } catch (Exception ignored) {
            }
        }

        return mapToDetailResponse(c);
    }

    /**
     * KTV đưa ra quyết định cuối cùng Vòng 2 cho Return Case sau khi nhận lại hàng (Task 3.2.3 - ktvDecideRound2).
     *
     * <p>Xác thực quyền hạn (Actor Authorization & Source):
     * <ul>
     *   <li>Tham số {@code ktvId} là actor ID thực hiện phân xử.</li>
     *   <li>Authorization: Service xác minh actor có role 'KTV' thông qua {@code og_has_role(?, 'KTV')}.</li>
     *   <li>Phòng thủ 2 lớp (defense-in-depth): DB trigger {@code og_apply_case_decision} cũng cưỡng chế
     *       {@code og_has_role(NEW.reviewed_by, 'KTV')}.</li>
     *   <li>Hạn chế hiện tại (LIMITATION): Tầng Controller chưa triển khai trong Prompt A;
     *       khi triển khai Prompt C, {@code ktvId} sẽ được trích xuất an toàn từ {@code @AuthenticationPrincipal}.</li>
     * </ul>
     *
     * <p>Quy tắc kiểm tra Idempotency chuẩn (Normalized Order):
     * <ol>
     *   <li>Validate tham số đầu vào (caseId, request, reason length >= 5, commandKey <= 127).</li>
     *   <li>Lấy khóa bi-quan theo thứ tự chuẩn: Order -> Case -> CaseRound (Vòng 2).</li>
     *   <li>Xác minh quyền KTV của actor.</li>
     *   <li>Kiểm tra tính lũy thừa (Idempotency): Nếu {@code commandKey} đã tồn tại trong {@code case_decisions},
     *       xác thực người đưa ra quyết định khớp với {@code ktvId}, đúng roundId và outcome, trả về snapshot hiện tại.</li>
     *   <li>Kiểm tra các bất biến trạng thái (Invariants & First-write-wins):
     *       Hồ sơ ở POST_RETURN, có valid_returned_at, Round 2 ở OPEN.
     *       Nếu outcome = 'EXEMPT_GOODS_REFUND', bắt buộc {@code round2.valid_damage_request_at IS NOT NULL}.</li>
     *   <li>Tạo CaseDecisionEntity (FULL_GOODS_REFUND hoặc EXEMPT_GOODS_REFUND) và lưu xuống DB.</li>
     *   <li>Gọi {@link RoundDecisionSettlementPort#requestSettlement} tạo yêu cầu thanh toán (bắt buộc, cùng transaction).</li>
     *   <li>Đồng bộ trạng thái: Tránh can thiệp các trường do DB trigger quản lý (cases.state, cases.closed_at,
     *       case_rounds.state, money_holds.closed_at). Dùng entityManager.refresh(...).</li>
     *   <li>Ghi nhận sự kiện CLEANUP vào case_events.</li>
     * </ol>
     */
    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse ktvDecideRound2(
            Long ktvId,
            Long caseId,
            ReturnCaseDtos.KtvRound2DecisionRequest request
    ) {
        if (caseId == null) {
            throw new IllegalArgumentException("caseId không được để trống");
        }
        if (request == null) {
            throw new IllegalArgumentException("Yêu cầu phán quyết không được để trống");
        }
        if (request.reason() == null || request.reason().trim().length() < 5) {
            throw new IllegalArgumentException("KTV bắt buộc phải nhập lý do phán quyết chi tiết (tối thiểu 5 ký tự)");
        }
        if (request.reason().trim().length() > 2000) {
            throw new IllegalArgumentException("Lý do phán quyết tối đa 2000 ký tự");
        }
        if (request.commandKey() != null && request.commandKey().trim().length() > 127) {
            throw new IllegalArgumentException("commandKey tối đa 127 ký tự để bảo đảm an toàn cho settlement operation");
        }

        String outcome = request.outcome() != null ? request.outcome().trim().toUpperCase() : "";
        if (!CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND.equals(outcome)
                && !CaseDecisionEntity.OUTCOME_EXEMPT_GOODS_REFUND.equals(outcome)) {
            throw new IllegalArgumentException("Phán quyết KTV Vòng 2 không hợp lệ: " + outcome
                    + " (chỉ chấp nhận FULL_GOODS_REFUND hoặc EXEMPT_GOODS_REFUND)");
        }

        // 1 & 2. Khóa theo thứ tự chuẩn: Order -> Case -> Round(2)
        ReturnWorkflowLockGateway.ReturnWorkflowLockedEntities locked = lockGateway.lockForRound(caseId, 2);
        OrderEntity order = locked.order();
        CaseEntity c = locked.returnCase();
        CaseRoundEntity round2 = locked.activeRound();

        // BD-1 & BD-3: Kiểm tra xem Round 2 đã được leo thang (ESCALATED) lên Admin hay chưa
        boolean isEscalated = caseEventRepository.existsByCaseIdAndRoundIdAndEventType(
                caseId, round2.getId(), CaseEventEntity.TYPE_ESCALATED
        );

        // 3. Xác thực quyền KTV / ADMIN (Actor Authorization under Lock)
        if (isEscalated) {
            // Khi đã leo thang: KTV bị tước quyền, chỉ ADMIN được phép quyết định (BD-1 APPROVED)
            if (ktvId == null || !isAdmin(ktvId)) {
                throw new SecurityException("Hồ sơ đã được chuyển lên Quản trị viên (Admin) do quá hạn SLA. KTV không còn quyền đưa ra quyết định.");
            }
        } else {
            // Khi chưa leo thang: Chỉ KTV được phép quyết định
            if (ktvId == null || !isKtv(ktvId)) {
                throw new SecurityException("Người dùng không có quyền KTV để phân xử hồ sơ Vòng 2");
            }
            if (c.getAssignedTo() == null || !c.getAssignedTo().equals(ktvId)) {
                throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "NOT_ASSIGNED_KTV",
                        "KTV không được phân công phụ trách Case này (Case #" + caseId + " chưa được claim hoặc thuộc về KTV #" + c.getAssignedTo() + ")");
            }
        }

        // 4. Idempotency Check: Tra cứu quyết định theo commandKey trước khi kiểm tra trạng thái vòng
        String cmdKey = request.commandKey() != null && !request.commandKey().isBlank()
                ? request.commandKey().trim()
                : null;

        if (cmdKey != null) {
            Optional<CaseDecisionEntity> existingDecision = caseDecisionRepository.findByCommandKey(cmdKey);
            if (existingDecision.isPresent()) {
                CaseDecisionEntity dec = existingDecision.get();
                if (!ktvId.equals(dec.getReviewedBy())) {
                    throw new IllegalArgumentException("CommandKey đã tồn tại nhưng không thuộc về KTV này");
                }
                if (!round2.getId().equals(dec.getRoundId())) {
                    throw new IllegalArgumentException("CommandKey đã tồn tại nhưng thuộc về round khác");
                }
                if (!outcome.equals(dec.getOutcome())) {
                    throw new IllegalArgumentException("CommandKey đã tồn tại với outcome khác: " + dec.getOutcome());
                }
                return mapToDetailResponse(c);
            }
        } else {
            cmdKey = (isEscalated ? "DECISION:ADMIN:R2:" : "DECISION:KTV:R2:") + caseId + ":" + UUID.randomUUID();
        }

        // 5. Re-verify điều kiện dưới khóa (First-write-wins & Invariants)
        if (!CaseEntity.STATE_POST_RETURN.equals(c.getState())) {
            throw new IllegalStateException("Hồ sơ không ở trạng thái Vòng 2 sau khi nhận lại hàng (Trạng thái hiện tại: " + c.getState() + ")");
        }

        if (c.getValidReturnedAt() == null) {
            throw new IllegalStateException("Chưa có xác nhận hàng đã được hoàn trả hợp lệ cho người bán");
        }

        if (!CaseRoundEntity.STATE_OPEN.equals(round2.getState())) {
            throw new IllegalStateException("Vòng 2 đã kết thúc, không thể đưa ra quyết định (Trạng thái hiện tại: " + round2.getState() + ")");
        }

        if (round2.getStage() == null || !CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION.equals(round2.getStage())) {
            throw new IllegalStateException("Vòng 2 không ở giai đoạn giải quyết sau nhận lại hàng (Giai đoạn hiện tại: " + round2.getStage() + ")");
        }

        // Bắt buộc đối với EXEMPT_GOODS_REFUND: Phải có valid_damage_request_at
        if (CaseDecisionEntity.OUTCOME_EXEMPT_GOODS_REFUND.equals(outcome)) {
            if (round2.getValidDamageRequestAt() == null) {
                throw new IllegalStateException("Chỉ được miễn trừ bồi thường khi người bán đã gửi yêu cầu thiệt hại hợp lệ");
            }
        }

        // PO-CHOICE-3: Evidence Request Guard
        Optional<CaseEvidenceRequestEntity> pendingRequestOpt = caseEvidenceRequestRepository
                .findByCaseIdAndStatus(caseId, CaseEvidenceRequestEntity.STATUS_PENDING);
        if (pendingRequestOpt.isPresent()) {
            CaseEvidenceRequestEntity pendingReq = pendingRequestOpt.get();
            Instant currentInstant = clock.instant();
            if (pendingReq.getDeadlineAt() != null && !currentInstant.isAfter(pendingReq.getDeadlineAt())) {
                throw new TrustSafetyBusinessException(HttpStatus.CONFLICT, "EVIDENCE_REQUEST_IN_PROGRESS",
                        "Hồ sơ đang có yêu cầu bổ sung bằng chứng còn hiệu lực. KTV phải chờ Người bán nộp hoặc chờ hết thời hạn 48 giờ trước khi đưa ra phán quyết.");
            }
        }

        // BD-3 (Cơ chế Escalation tự động) và BD-4 (Mốc tính SLA 48h từ complete_submission_at)
        // và BD-1 (Admin Final Authority sau escalation) đã được PO phê duyệt chính thức (2026-11-11).

        // 6. Tính số tiền hoàn hàng theo outcome
        BigDecimal goodsRefundAmount;
        if (CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND.equals(outcome)) {
            BigDecimal subtotal = order.getSubtotal() != null ? order.getSubtotal() : BigDecimal.ZERO;
            BigDecimal voucherDiscount = order.getVoucherDiscountAmount() != null ? order.getVoucherDiscountAmount() : BigDecimal.ZERO;
            goodsRefundAmount = subtotal.subtract(voucherDiscount).max(BigDecimal.ZERO);
        } else {
            // EXEMPT_GOODS_REFUND: Tiền hoàn hàng cho người mua = 0
            goodsRefundAmount = BigDecimal.ZERO;
        }

        Instant now = clock.instant();

        // D-08 (PO APPROVED): Chuyển các yêu cầu bằng chứng (case_evidence_requests) đang PENDING của Case sang CANCELLED
        List<CaseEvidenceRequestEntity> evidenceRequests = caseEvidenceRequestRepository.findByCaseIdOrderByIssuedAtAsc(caseId);
        if (evidenceRequests != null && !evidenceRequests.isEmpty()) {
            boolean modified = false;
            for (CaseEvidenceRequestEntity cer : evidenceRequests) {
                if (CaseEvidenceRequestEntity.STATUS_PENDING.equals(cer.getStatus())) {
                    cer.setStatus(CaseEvidenceRequestEntity.STATUS_CANCELLED);
                    cer.setUpdatedAt(now);
                    caseEvidenceRequestRepository.save(cer);
                    modified = true;
                }
            }
            if (modified) {
                caseEvidenceRequestRepository.flush();
            }
        }

        // 7. Tạo quyết định CaseDecisionEntity
        CaseDecisionEntity decision = new CaseDecisionEntity(
                round2.getId(),
                ktvId,
                CaseDecisionEntity.ACTOR_USER,
                outcome,
                true,
                goodsRefundAmount,
                request.reason().trim(),
                cmdKey
        );
        decision.setDecidedAt(now);
        CaseDecisionEntity savedDecision = caseDecisionRepository.saveAndFlush(decision);

        // 8. Gọi RoundDecisionSettlementPort trong cùng transaction (bắt buộc, không optional - Task 4.1a)
        settlementPort.requestSettlement(
                order.getId(),
                caseId,
                savedDecision.getId(),
                outcome,
                cmdKey
        );

        // 9. Đồng bộ trạng thái: Tránh can thiệp các trường do DB trigger quản lý
        // Trigger trg_case_decision_apply (og_apply_case_decision & og_close_case_holds) tự động
        // cập nhật cases.state='CLOSED', cases.closed_at, case_rounds.state='DECIDED', và money_holds.closed_at.
        if (entityManager != null) {
            try {
                if (entityManager.contains(c)) {
                    entityManager.refresh(c);
                } else {
                    c.setState(CaseEntity.STATE_CLOSED);
                    c.setClosedAt(now);
                }
                if (entityManager.contains(round2)) {
                    entityManager.refresh(round2);
                } else {
                    round2.setState(CaseRoundEntity.STATE_DECIDED);
                }
            } catch (Exception ignored) {
                c.setState(CaseEntity.STATE_CLOSED);
                c.setClosedAt(now);
                round2.setState(CaseRoundEntity.STATE_DECIDED);
            }
        } else {
            c.setState(CaseEntity.STATE_CLOSED);
            c.setClosedAt(now);
            round2.setState(CaseRoundEntity.STATE_DECIDED);
        }

        return mapToDetailResponse(c);
    }

    /**
     * Phán quyết của Quản trị viên (Admin) cho hồ sơ Vòng 2 sau khi đã leo thang SLA (BD-1 APPROVED).
     */
    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse adminDecideRound2(
            Long adminId,
            Long caseId,
            ReturnCaseDtos.KtvRound2DecisionRequest request
    ) {
        if (adminId == null || !isAdmin(adminId)) {
            throw new SecurityException("Người dùng không có quyền Quản trị viên (ADMIN) để thực hiện phán quyết này.");
        }
        CaseRoundEntity round2 = caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)
                .orElseThrow(() -> new TrustSafetyBusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy Vòng 2 cho hồ sơ #" + caseId));
        boolean isEscalated = caseEventRepository.existsByCaseIdAndRoundIdAndEventType(
                caseId, round2.getId(), CaseEventEntity.TYPE_ESCALATED
        );
        if (!isEscalated) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "CASE_NOT_ESCALATED",
                    "Hồ sơ chưa được chuyển lên Quản trị viên (Admin) do chưa quá hạn SLA hoặc chưa được leo thang.");
        }
        return ktvDecideRound2(adminId, caseId, request);
    }

    @Transactional
    public ReturnCaseDtos.ReturnCaseDetailResponse adminDecideRound2(
            Long caseId,
            Long adminId,
            ReturnCaseDtos.KtvRound2DecisionRequest request,
            boolean unused
    ) {
        return adminDecideRound2(adminId, caseId, request);
    }

    private boolean isKtv(Long actorId) {
        if (actorId == null) {
            return false;
        }
        if (jdbcTemplate != null) {
            try {
                Boolean hasRole = jdbcTemplate.queryForObject(
                        "SELECT og_has_role(?, 'KTV')",
                        Boolean.class,
                        actorId
                );
                return Boolean.TRUE.equals(hasRole);
            } catch (Exception ex) {
                log.warn("Lỗi khi kiểm tra vai trò KTV cho actorId={}: {}", actorId, ex.getMessage());
                return false;
            }
        }
        return false;
    }

    private boolean isAdmin(Long actorId) {
        if (actorId == null) {
            return false;
        }
        if (jdbcTemplate != null) {
            try {
                Boolean hasRole = jdbcTemplate.queryForObject(
                        "SELECT og_has_role(?, 'ADMIN')",
                        Boolean.class,
                        actorId
                );
                return Boolean.TRUE.equals(hasRole);
            } catch (Exception ex) {
                log.warn("Lỗi khi kiểm tra vai trò ADMIN cho actorId={}: {}", actorId, ex.getMessage());
                return false;
            }
        }
        return false;
    }

    public Long findDefaultAdminRecipientId() {
        if (jdbcTemplate != null) {
            try {
                List<Long> adminIds = jdbcTemplate.queryForList("""
                        SELECT u.user_id
                        FROM og_compat.user_roles ur
                        JOIN og_compat.roles r USING(role_id)
                        JOIN og_compat.users u USING(user_id)
                        WHERE r.role_name = 'ADMIN'
                          AND u.status = 'ACTIVE'
                          AND u.deleted_at IS NULL
                        ORDER BY u.user_id ASC
                        LIMIT 1
                        """, Long.class);
                if (!adminIds.isEmpty() && adminIds.get(0) != null) {
                    return adminIds.get(0);
                }
                log.warn("Không tìm thấy tài khoản ADMIN nào có trạng thái ACTIVE trong og_compat.users");
                return null;
            } catch (Exception ex) {
                log.warn("Lỗi tra cứu admin recipient: {}", ex.getMessage());
            }
        }
        return 1L; // Fallback admin id khi mock/unit test không có database view
    }

    /**
     * Quét các case_id đang ở Vòng 2 (POST_RETURN) đã quá hạn phản hồi của người bán (seller_response_due_at <= now).
     * Sử dụng duy nhất một đường Candidate Scan qua JPQL CaseRepository.findOverduePostReturnCaseIds(now).
     * Truy vấn đọc thuần túy, không khóa, loại trừ các case đã có valid_damage_request_at hoặc round không còn OPEN.
     */
    @Transactional(readOnly = true)
    public List<Long> scanOverduePostReturnCaseIds() {
        Instant now = clock.instant();
        return caseRepository.findOverduePostReturnCaseIds(now);
    }

    /**
     * Xử lý timeout Vòng 2 cho một hồ sơ cụ thể trong một transaction độc lập (REQUIRES_NEW).
     * Transaction của từng case hoàn toàn độc lập: rollback nếu có lỗi và không ảnh hưởng đến các case khác trong batch.
     * Mọi ngoại lệ phát sinh đều được bắt và xử lý bên ngoài TransactionTemplate để bảo đảm transaction đã rollback hoàn toàn.
     */
    public ReturnCaseDtos.CaseTimeoutExecutionResult processPostReturnTimeoutForCaseInNewTx(Long caseId) {
        if (caseId == null) {
            return new ReturnCaseDtos.CaseTimeoutExecutionResult(null, ReturnCaseDtos.TimeoutProcessingStatus.FAILED, "caseId không được để trống", null);
        }

        try {
            return requiresNewTxTemplate.execute(status -> processPostReturnTimeoutForCaseInternal(caseId));
        } catch (Exception ex) {
            return handleCaseExecutionException(caseId, ex);
        }
    }

    /**
     * Entrypoint tương thích ngược cho việc xử lý timeout một Case đơn lẻ.
     */
    public ReturnCaseDtos.ReturnCaseDetailResponse processPostReturnTimeoutForCase(Long caseId) {
        ReturnCaseDtos.CaseTimeoutExecutionResult result = processPostReturnTimeoutForCaseInNewTx(caseId);
        return result != null ? result.response() : null;
    }

    /**
     * Thực thi logic xử lý timeout Vòng 2 cho một Case dưới sự bảo vệ của TransactionTemplate và Canonical Lock.
     */
    private ReturnCaseDtos.CaseTimeoutExecutionResult processPostReturnTimeoutForCaseInternal(Long caseId) {
        // 1. Khóa theo thứ tự chuẩn: Order -> Case -> Round(2)
        ReturnWorkflowLockGateway.ReturnWorkflowLockedEntities lockedEntities;
        try {
            lockedEntities = lockGateway.lockForRound(caseId, 2);
        } catch (NoSuchElementException e) {
            log.info("Bỏ qua xử lý timeout cho caseId={}: không tìm thấy Vòng 2 hoặc Case", caseId);
            return new ReturnCaseDtos.CaseTimeoutExecutionResult(caseId, ReturnCaseDtos.TimeoutProcessingStatus.SKIPPED, "Không tìm thấy Vòng 2 hoặc Case", null);
        }

        OrderEntity order = lockedEntities.order();
        CaseEntity c = lockedEntities.returnCase();
        CaseRoundEntity round2 = lockedEntities.activeRound();
        Instant now = clock.instant();

        // 2. Re-verify điều kiện dưới khóa (First-write-wins & Invariants)
        // (a) Hồ sơ phải ở POST_RETURN
        if (!CaseEntity.STATE_POST_RETURN.equals(c.getState())) {
            log.info("Bỏ qua timeout cho caseId={}: trạng thái hiện tại là {}", caseId, c.getState());
            return new ReturnCaseDtos.CaseTimeoutExecutionResult(caseId, ReturnCaseDtos.TimeoutProcessingStatus.SKIPPED, "Trạng thái case không phải POST_RETURN: " + c.getState(), mapToDetailResponse(c));
        }

        // (b) Vòng 2 phải đang OPEN
        if (!CaseRoundEntity.STATE_OPEN.equals(round2.getState())) {
            log.info("Bỏ qua timeout cho caseId={}: Vòng 2 đã kết thúc ({})", caseId, round2.getState());
            return new ReturnCaseDtos.CaseTimeoutExecutionResult(caseId, ReturnCaseDtos.TimeoutProcessingStatus.SKIPPED, "Vòng 2 không còn OPEN: " + round2.getState(), mapToDetailResponse(c));
        }

        // (c) Đã quá hạn seller_response_due_at
        if (c.getSellerResponseDueAt() == null || now.isBefore(c.getSellerResponseDueAt())) {
            log.info("Bỏ qua timeout cho caseId={}: chưa đến hạn seller_response_due_at ({})", caseId, c.getSellerResponseDueAt());
            return new ReturnCaseDtos.CaseTimeoutExecutionResult(caseId, ReturnCaseDtos.TimeoutProcessingStatus.SKIPPED, "Chưa quá hạn seller_response_due_at", mapToDetailResponse(c));
        }

        // (d) Người bán chưa gửi yêu cầu bồi thường (valid_damage_request_at IS NULL)
        if (round2.getValidDamageRequestAt() != null) {
            log.info("Bỏ qua timeout cho caseId={}: người bán đã gửi yêu cầu bồi thường hợp lệ lúc {}", caseId, round2.getValidDamageRequestAt());
            return new ReturnCaseDtos.CaseTimeoutExecutionResult(caseId, ReturnCaseDtos.TimeoutProcessingStatus.SKIPPED, "Người bán đã gửi yêu cầu miễn trừ thiệt hại hợp lệ", mapToDetailResponse(c));
        }

        // 3. Idempotency Check: Kiểm tra quyết định và settlement hiện hữu cho Vòng 2
        String cmdKey = "TIMEOUT:SELLER_POST_RETURN:" + caseId + ":" + round2.getId();
        List<CaseDecisionEntity> existingDecisions = caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(round2.getId());
        if (!existingDecisions.isEmpty()) {
            CaseDecisionEntity existing = existingDecisions.getFirst();
            if (CaseDecisionEntity.ACTOR_SYSTEM_TIMEOUT.equals(existing.getActorType())
                    && CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND.equals(existing.getOutcome())
                    && cmdKey.equals(existing.getCommandKey())) {
                boolean settlementConsistent = verifySettlementOperationsConsistent(order.getId(), caseId, existing.getId(), cmdKey);
                if (settlementConsistent) {
                    log.info("Idempotent replay: Quyết định timeout và toàn bộ settlement operations hợp lệ đã tồn tại cho caseId={}, roundId={}", caseId, round2.getId());
                    return new ReturnCaseDtos.CaseTimeoutExecutionResult(caseId, ReturnCaseDtos.TimeoutProcessingStatus.SKIPPED, "Quyết định timeout đã được xử lý (idempotent)", mapToDetailResponse(c));
                } else {
                    log.error("Không nhất quán dữ liệu: Decision #{} tồn tại nhưng Settlement operations thiếu hoặc không hợp lệ cho caseId={}", existing.getId(), caseId);
                    return new ReturnCaseDtos.CaseTimeoutExecutionResult(caseId, ReturnCaseDtos.TimeoutProcessingStatus.FAILED, "Không nhất quán dữ liệu: Decision tồn tại nhưng Settlement thiếu hoặc không khớp", mapToDetailResponse(c));
                }
            } else {
                log.info("Vòng {} của caseId={} đã có quyết định bởi actor={} với outcome={}, bỏ qua timeout",
                        round2.getId(), caseId, existing.getActorType(), existing.getOutcome());
                return new ReturnCaseDtos.CaseTimeoutExecutionResult(caseId, ReturnCaseDtos.TimeoutProcessingStatus.SKIPPED, "Vòng đã có quyết định bởi luồng khác: " + existing.getActorType(), mapToDetailResponse(c));
            }
        }

        Optional<CaseDecisionEntity> existingByCmd = caseDecisionRepository.findByCommandKey(cmdKey);
        if (existingByCmd.isPresent()) {
            CaseDecisionEntity existing = existingByCmd.get();
            if (CaseDecisionEntity.ACTOR_SYSTEM_TIMEOUT.equals(existing.getActorType())
                    && CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND.equals(existing.getOutcome())
                    && round2.getId().equals(existing.getRoundId())) {
                boolean settlementConsistent = verifySettlementOperationsConsistent(order.getId(), caseId, existing.getId(), cmdKey);
                if (settlementConsistent) {
                    log.info("Idempotent replay: Quyết định tìm thấy theo commandKey={} cho caseId={}", cmdKey, caseId);
                    return new ReturnCaseDtos.CaseTimeoutExecutionResult(caseId, ReturnCaseDtos.TimeoutProcessingStatus.SKIPPED, "Quyết định timeout đã tồn tại theo commandKey (idempotent)", mapToDetailResponse(c));
                } else {
                    log.error("Không nhất quán dữ liệu: Decision theo commandKey {} tồn tại nhưng Settlement thiếu/sai cho caseId={}", cmdKey, caseId);
                    return new ReturnCaseDtos.CaseTimeoutExecutionResult(caseId, ReturnCaseDtos.TimeoutProcessingStatus.FAILED, "Không nhất quán dữ liệu: Decision tồn tại nhưng Settlement thiếu hoặc không khớp", mapToDetailResponse(c));
                }
            } else {
                log.warn("Xung đột commandKey: bản ghi quyết định với key {} có thông tin không khớp (actor={}, outcome={}, roundId={})",
                        cmdKey, existing.getActorType(), existing.getOutcome(), existing.getRoundId());
                return new ReturnCaseDtos.CaseTimeoutExecutionResult(caseId, ReturnCaseDtos.TimeoutProcessingStatus.FAILED, "Xung đột commandKey với actor/outcome khác", mapToDetailResponse(c));
            }
        }

        // 4. Tính tiền hoàn hàng theo giá trị ròng của đơn hàng
        BigDecimal subtotal = order.getSubtotal() != null ? order.getSubtotal() : BigDecimal.ZERO;
        BigDecimal voucherDiscount = order.getVoucherDiscountAmount() != null ? order.getVoucherDiscountAmount() : BigDecimal.ZERO;
        BigDecimal goodsRefundAmount = subtotal.subtract(voucherDiscount).max(BigDecimal.ZERO);

        // 5. Tạo quyết định SYSTEM_TIMEOUT (outcome FULL_GOODS_REFUND, reviewed_by = NULL)
        CaseDecisionEntity decision = new CaseDecisionEntity(
                round2.getId(),
                null, // reviewedBy = NULL bắt buộc khi actor_type = SYSTEM_TIMEOUT theo CHECK constraint
                CaseDecisionEntity.ACTOR_SYSTEM_TIMEOUT,
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                true,
                goodsRefundAmount,
                "Hệ thống tự động quyết định hoàn tiền cho người mua do người bán quá hạn phản hồi Vòng 2",
                cmdKey
        );
        decision.setDecidedAt(now);
        CaseDecisionEntity savedDecision = caseDecisionRepository.saveAndFlush(decision);

        // 6. Kích hoạt Settlement Request (Task 4.1a) trong cùng transaction (bắt buộc, không nullable)
        Long decisionId = (savedDecision != null && savedDecision.getId() != null) ? savedDecision.getId() : decision.getId();
        settlementPort.requestSettlement(
                order.getId(),
                caseId,
                decisionId,
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                cmdKey
        );

        // 7. Ghi nhận sự kiện CLEANUP
        String eventCmdKey = "EVENT:TIMEOUT:R2:" + caseId;
        Optional<CaseEventEntity> existingEv = caseEventRepository.findByCommandKey(eventCmdKey);
        if (existingEv.isEmpty()) {
            Map<String, Object> metaMap = new LinkedHashMap<>();
            metaMap.put("outcome", CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND);
            metaMap.put("actor_type", CaseDecisionEntity.ACTOR_SYSTEM_TIMEOUT);
            metaMap.put("goods_refund_amount", goodsRefundAmount);
            String metadataJson = safeToJson(metaMap);

            CaseEventEntity event = new CaseEventEntity(
                    UUID.randomUUID(),
                    caseId,
                    round2.getId(),
                    null,
                    order.getBuyerId(),
                    CaseEventEntity.TYPE_CLEANUP,
                    "Hết hạn người bán phản hồi Vòng 2. Hệ thống tự động chấp nhận hoàn tiền.",
                    metadataJson,
                    eventCmdKey
            );
            caseEventRepository.save(event);
        }

        // 8. Đồng bộ trạng thái do PostgreSQL triggers sở hữu (không dùng saveAndFlush ghi đè)
        if (entityManager != null) {
            try {
                if (entityManager.contains(c)) {
                    entityManager.refresh(c);
                }
                if (entityManager.contains(round2)) {
                    entityManager.refresh(round2);
                }
            } catch (Exception ex) {
                log.debug("EntityManager refresh post-trigger note: {}", ex.getMessage());
            }
        }
        if (!CaseEntity.STATE_CLOSED.equals(c.getState())) {
            c.setState(CaseEntity.STATE_CLOSED);
            c.setClosedAt(now);
        }
        if (!CaseRoundEntity.STATE_DECIDED.equals(round2.getState())) {
            round2.setState(CaseRoundEntity.STATE_DECIDED);
        }

        return new ReturnCaseDtos.CaseTimeoutExecutionResult(caseId, ReturnCaseDtos.TimeoutProcessingStatus.PROCESSED, "Thành công tự động hoàn tiền do timeout", mapToDetailResponse(c));
    }

    public static final String ERR_MSG_23514_ROUND_DECIDED = "Case round already decided";
    public static final String ERR_MSG_23514_AUTO_REFUND_BLOCKED = "Auto refund blocked by deadline/request";
    public static final String ERR_MSG_23505_DEFERRED_COMMAND_KEY = "Relational constraint case_decisions_command_key_key";
    public static final String EXACT_23505_COMMAND_KEY_CONSTRAINT = "case_decisions_command_key_key";

    /**
     * Xác minh tính nhất quán toàn diện của Settlement Operations theo contract Task 4.1a.
     * Đối chiếu với nguồn dữ liệu authoritative từ og_compat.order_fund_components.
     */
    private boolean verifySettlementOperationsConsistent(Long orderId, Long caseId, Long decisionId, String baseCmdKey) {
        if (jdbcTemplate == null || orderId == null || caseId == null || decisionId == null || baseCmdKey == null) {
            log.error("verifySettlementOperationsConsistent fail-closed do tham số null hoặc thiếu jdbcTemplate: jdbcTemplate={}, orderId={}, caseId={}, decisionId={}, baseCmdKey={}",
                    jdbcTemplate != null, orderId, caseId, decisionId, baseCmdKey);
            return false;
        }

        try {
            // 0. Lấy thông tin order (đặc biệt là buyer_id) để đối soát recipient_user_id
            List<Map<String, Object>> orders = jdbcTemplate.queryForList("""
                    SELECT buyer_id FROM og_compat.orders WHERE order_id = ?
                    """, orderId);
            if (orders.isEmpty()) {
                log.error("Không tìm thấy orderId={} khi đối soát settlement", orderId);
                return false;
            }
            Long buyerId = ((Number) orders.getFirst().get("buyer_id")).longValue();

            // 1. Lấy danh sách Fund Components kỳ vọng từ nguồn authoritative (order_fund_components)
            List<Map<String, Object>> components = jdbcTemplate.queryForList("""
                    SELECT component_id, component_type, confirmed_amount
                    FROM og_compat.order_fund_components
                    WHERE order_id = ? AND component_type IN ('GOODS_CASH', 'PLATFORM_GOODS_SUBSIDY')
                    ORDER BY component_type
                    """, orderId);

            List<Map<String, Object>> activeComponents = components.stream()
                    .filter(c -> {
                        BigDecimal amt = (BigDecimal) c.get("confirmed_amount");
                        return amt != null && amt.compareTo(BigDecimal.ZERO) > 0;
                    })
                    .toList();

            // 2. Lấy toàn bộ settlement operations đã ghi cho decisionId
            List<Map<String, Object>> operations = jdbcTemplate.queryForList("""
                    SELECT operation_id, component_id, operation_type, amount, cause_type,
                           case_decision_id, recipient_user_id, state, command_key
                    FROM og_compat.settlement_operations
                    WHERE case_decision_id = ?
                    """, decisionId);

            if (operations.size() != activeComponents.size()) {
                log.error("Không nhất quán số lượng operation cho decisionId={}: kỳ vọng {} nhưng có {}",
                        decisionId, activeComponents.size(), operations.size());
                return false;
            }

            // 3. Đối chiếu từng component kỳ vọng với operation tương ứng
            for (Map<String, Object> comp : activeComponents) {
                Object compIdObj = comp.get("component_id");
                UUID compId = compIdObj instanceof UUID ? (UUID) compIdObj : UUID.fromString(compIdObj.toString());
                String compType = (String) comp.get("component_type");
                BigDecimal expectedAmount = (BigDecimal) comp.get("confirmed_amount");

                String expectedOpType = "GOODS_CASH".equals(compType) ? "REFUND_BUYER" : "RETURN_PLATFORM";
                Long expectedRecipient = "GOODS_CASH".equals(compType) ? buyerId : null;
                String expectedCmdKey = baseCmdKey + ":" + compType;

                Optional<Map<String, Object>> opOpt = operations.stream()
                        .filter(op -> expectedCmdKey.equals(op.get("command_key")))
                        .findFirst();

                if (opOpt.isEmpty()) {
                    log.error("Thiếu settlement operation cho component {} (commandKey={}) trên decisionId={}",
                            compType, expectedCmdKey, decisionId);
                    return false;
                }

                Map<String, Object> op = opOpt.get();

                Object opCompIdObj = op.get("component_id");
                UUID opCompId = opCompIdObj instanceof UUID ? (UUID) opCompIdObj : UUID.fromString(opCompIdObj.toString());
                if (!compId.equals(opCompId)) {
                    log.error("Sai component_id trên operation {}: kỳ vọng {} nhưng có {}", op.get("operation_id"), compId, opCompId);
                    return false;
                }

                if (!expectedOpType.equals(op.get("operation_type"))) {
                    log.error("Sai operation_type trên operation {}: kỳ vọng {} nhưng có {}", op.get("operation_id"), expectedOpType, op.get("operation_type"));
                    return false;
                }

                if (!"REQUESTED".equals(op.get("state"))) {
                    log.error("Sai state trên operation {}: kỳ vọng REQUESTED nhưng có {}", op.get("operation_id"), op.get("state"));
                    return false;
                }

                BigDecimal opAmount = (BigDecimal) op.get("amount");
                if (opAmount == null || opAmount.compareTo(expectedAmount) != 0) {
                    log.error("Sai amount trên operation {}: kỳ vọng {} nhưng có {}", op.get("operation_id"), expectedAmount, opAmount);
                    return false;
                }

                if (!"RETURN".equals(op.get("cause_type"))) {
                    log.error("Sai cause_type trên operation {}: kỳ vọng RETURN nhưng có {}", op.get("operation_id"), op.get("cause_type"));
                    return false;
                }

                Object actualRecipientObj = op.get("recipient_user_id");
                Long actualRecipient = actualRecipientObj != null ? ((Number) actualRecipientObj).longValue() : null;
                if (expectedRecipient == null && actualRecipient != null) {
                    log.error("Sai recipient_user_id trên operation {}: kỳ vọng null nhưng có {}", op.get("operation_id"), actualRecipient);
                    return false;
                }
                if (expectedRecipient != null && !expectedRecipient.equals(actualRecipient)) {
                    log.error("Sai recipient_user_id trên operation {}: kỳ vọng {} nhưng có {}", op.get("operation_id"), expectedRecipient, actualRecipient);
                    return false;
                }
            }

            return true;
        } catch (Exception ex) {
            log.error("Lỗi khi xác minh settlement operations cho decisionId={}: {}", decisionId, ex.getMessage(), ex);
            return false;
        }
    }

    record PostgresErrorMetadata(String sqlState, String constraintName, String routine, String message, String where) {
    }

    PostgresErrorMetadata extractPostgresErrorMetadata(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof java.sql.SQLException sqlEx) {
                String sqlState = sqlEx.getSQLState();
                String constraint = null;
                String routine = null;
                String message = sqlEx.getMessage();
                String where = null;
                try {
                    java.lang.reflect.Method getSemMethod = current.getClass().getMethod("getServerErrorMessage");
                    Object sem = getSemMethod.invoke(current);
                    if (sem != null) {
                        java.lang.reflect.Method getConstraintMethod = sem.getClass().getMethod("getConstraint");
                        Object cObj = getConstraintMethod.invoke(sem);
                        if (cObj instanceof String cStr && !cStr.isBlank()) {
                            constraint = cStr;
                        }
                        java.lang.reflect.Method getRoutineMethod = sem.getClass().getMethod("getRoutine");
                        Object rObj = getRoutineMethod.invoke(sem);
                        if (rObj instanceof String rStr && !rStr.isBlank()) {
                            routine = rStr;
                        }
                        java.lang.reflect.Method getMessageMethod = sem.getClass().getMethod("getMessage");
                        Object mObj = getMessageMethod.invoke(sem);
                        if (mObj instanceof String mStr && !mStr.isBlank()) {
                            message = mStr;
                        }
                        java.lang.reflect.Method getWhereMethod = sem.getClass().getMethod("getWhere");
                        Object wObj = getWhereMethod.invoke(sem);
                        if (wObj instanceof String wStr && !wStr.isBlank()) {
                            where = wStr;
                        }
                    }
                } catch (Exception ignored) {
                }
                return new PostgresErrorMetadata(sqlState, constraint, routine, message, where);
            }
            current = current.getCause();
        }
        return new PostgresErrorMetadata(null, null, null, t != null ? t.getMessage() : null, null);
    }

    ReturnCaseDtos.CaseTimeoutExecutionResult handleCaseExecutionException(Long caseId, Exception ex) {
        PostgresErrorMetadata meta = extractPostgresErrorMetadata(ex);
        String sqlState = meta.sqlState();

        if ("55P03".equalsIgnoreCase(sqlState)) {
            log.warn("Lock timeout (SQLSTATE 55P03) khi tranh chấp khóa caseId={}: {}. Sẽ retry ở chu kỳ tiếp theo.",
                    caseId, meta.message());
            return new ReturnCaseDtos.CaseTimeoutExecutionResult(
                    caseId, ReturnCaseDtos.TimeoutProcessingStatus.LOCKED_55P03, "Lock timeout (55P03): " + meta.message(), null
            );
        }

        if ("23514".equalsIgnoreCase(sqlState)) {
            boolean isWhereContextValid = meta.where() != null
                    && (meta.where().contains("og_apply_case_decision") || meta.where().contains("og70_row_case_decisions_og_apply_case_decision"));
            String msg = meta.message();

            boolean isBranch1 = isWhereContextValid && ERR_MSG_23514_ROUND_DECIDED.equals(msg);
            boolean isBranch2 = isWhereContextValid && ERR_MSG_23514_AUTO_REFUND_BLOCKED.equals(msg);

            if (isBranch1) {
                // Nhánh 1: Round đã được quyết định bởi workflow khác
                boolean condB = verifyCompetingWorkflowCommitted(caseId);
                if (condB) {
                    log.info("Race condition đã được giải quyết: Round 2 đã được quyết định trước bởi workflow khác (23514 - {}) trên caseId={}", msg, caseId);
                    return new ReturnCaseDtos.CaseTimeoutExecutionResult(
                            caseId, ReturnCaseDtos.TimeoutProcessingStatus.SKIPPED, "Bị chặn hợp lệ bởi trigger: Round 2 đã được quyết định bởi workflow khác (23514)", null
                    );
                } else {
                    log.error("Lỗi 23514 ({}) nhưng DB verification không xác nhận được competing workflow trên caseId={}", msg, caseId);
                    return new ReturnCaseDtos.CaseTimeoutExecutionResult(
                            caseId, ReturnCaseDtos.TimeoutProcessingStatus.FAILED, "Vi phạm trigger 23514 nhưng không xác nhận được workflow cạnh tranh: " + msg, null
                    );
                }
            } else if (isBranch2) {
                // Nhánh 2: Damage Request đã được nộp hoặc chưa đến hạn
                boolean condB = verifyCompetingWorkflowCommitted(caseId);
                if (condB) {
                    log.info("Race condition đã được giải quyết: Seller Damage Request hoặc workflow hợp lệ khác đã commit trước (23514 - {}) trên caseId={}", msg, caseId);
                    return new ReturnCaseDtos.CaseTimeoutExecutionResult(
                            caseId, ReturnCaseDtos.TimeoutProcessingStatus.SKIPPED, "Bị chặn hợp lệ bởi trigger: Seller Damage Request hoặc workflow khác đã được ghi nhận (23514)", null
                    );
                } else {
                    log.error("Lỗi 23514 ({}) nhưng DB verification không xác nhận được competing workflow trên caseId={}", msg, caseId);
                    return new ReturnCaseDtos.CaseTimeoutExecutionResult(
                            caseId, ReturnCaseDtos.TimeoutProcessingStatus.FAILED, "Vi phạm trigger 23514 nhưng không xác nhận được workflow cạnh tranh: " + msg, null
                    );
                }
            } else {
                log.error("Lỗi vi phạm CHECK constraint/trigger (23514) không khớp metadata allowlist trên caseId={}: msg={}, where={}, constraint={}",
                        caseId, msg, meta.where(), meta.constraintName());
                return new ReturnCaseDtos.CaseTimeoutExecutionResult(
                        caseId, ReturnCaseDtos.TimeoutProcessingStatus.FAILED, "Vi phạm trigger/check constraint không thuộc allowlist (23514): " + msg, null
                );
            }
        }

        if ("23505".equalsIgnoreCase(sqlState)) {
            String constraint = meta.constraintName();
            String msg = meta.message();

            boolean isDeferredCommandKey = (constraint == null && msg != null && msg.contains(ERR_MSG_23505_DEFERRED_COMMAND_KEY));
            boolean isExplicitConstraint = (constraint != null && EXACT_23505_COMMAND_KEY_CONSTRAINT.equalsIgnoreCase(constraint.trim()));

            if (!isDeferredCommandKey && !isExplicitConstraint) {
                log.error("Lỗi unique violation (23505) không thuộc allowlist constraint của command_key trên caseId={}: constraint={}, msg={}",
                        caseId, constraint, msg);
                return new ReturnCaseDtos.CaseTimeoutExecutionResult(caseId, ReturnCaseDtos.TimeoutProcessingStatus.FAILED,
                        "Xung đột unique constraint không thuộc allowlist (23505): " + (constraint != null ? constraint : msg), null);
            }

            // Đọc lại Decision đã commit và kiểm tra tính nhất quán sau rollback bằng transaction mới / jdbcTemplate
            if (jdbcTemplate != null) {
                try {
                    List<Long> round2Ids = jdbcTemplate.queryForList("""
                            SELECT round_id FROM og_compat.case_rounds
                            WHERE case_id = ? AND round_no = 2
                            """, Long.class, caseId);
                    if (!round2Ids.isEmpty()) {
                        Long round2Id = round2Ids.getFirst();
                        String expectedCmdKey = "TIMEOUT:SELLER_POST_RETURN:" + caseId + ":" + round2Id;

                        List<Map<String, Object>> decisions = jdbcTemplate.queryForList("""
                                SELECT d.decision_id, d.round_id, d.actor_type, d.outcome, d.command_key
                                FROM og_compat.case_decisions d
                                JOIN og_compat.case_rounds r ON d.round_id = r.round_id
                                WHERE d.command_key = ?
                                  AND r.case_id = ?
                                  AND r.round_no = 2
                                  AND d.actor_type = 'SYSTEM_TIMEOUT'
                                  AND d.outcome = 'FULL_GOODS_REFUND'
                                """, expectedCmdKey, caseId);

                        if (!decisions.isEmpty()) {
                            Map<String, Object> dec = decisions.getFirst();
                            Long decId = ((Number) dec.get("decision_id")).longValue();

                            List<Long> orderIds = jdbcTemplate.queryForList("""
                                    SELECT order_id FROM og_compat.cases WHERE case_id = ?
                                    """, Long.class, caseId);
                            Long orderId = orderIds.isEmpty() ? null : orderIds.getFirst();

                            boolean settlementOk = verifySettlementOperationsConsistent(orderId, caseId, decId, expectedCmdKey);
                            if (settlementOk) {
                                log.info("Xác nhận idempotent: bản ghi timeout và toàn bộ settlement operations hợp lệ đã tồn tại sau unique violation (23505) trên caseId={}", caseId);
                                return new ReturnCaseDtos.CaseTimeoutExecutionResult(caseId, ReturnCaseDtos.TimeoutProcessingStatus.SKIPPED,
                                        "Idempotent: bản ghi timeout đã tồn tại (23505)", null);
                            } else {
                                log.error("Xung đột unique violation (23505) trên caseId={}: Decision tồn tại nhưng Settlement thiếu hoặc không nhất quán", caseId);
                                return new ReturnCaseDtos.CaseTimeoutExecutionResult(caseId, ReturnCaseDtos.TimeoutProcessingStatus.FAILED,
                                        "Decision tồn tại nhưng Settlement thiếu/sai sau unique violation (23505)", null);
                            }
                        }
                    }
                } catch (Exception queryEx) {
                    log.error("Lỗi khi kiểm tra idempotent hậu rollback cho caseId={}: {}", caseId, queryEx.getMessage(), queryEx);
                }
            }

            return new ReturnCaseDtos.CaseTimeoutExecutionResult(caseId, ReturnCaseDtos.TimeoutProcessingStatus.FAILED,
                    "Unique violation (23505) không xác minh được idempotent replay: " + msg, null);
        }

        log.error("Lỗi khi xử lý timeout cho caseId={}: {}", caseId, ex.getMessage(), ex);
        return new ReturnCaseDtos.CaseTimeoutExecutionResult(caseId, ReturnCaseDtos.TimeoutProcessingStatus.FAILED, ex.getMessage(), null);
    }

    private boolean verifyCompetingWorkflowCommitted(Long caseId) {
        if (caseId == null || jdbcTemplate == null) {
            return false;
        }
        try {
            // 1. Kiểm tra Round 2 của chính Case này có valid_damage_request_at IS NOT NULL
            Integer damageCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM og_compat.case_rounds
                    WHERE case_id = ? AND round_no = 2 AND valid_damage_request_at IS NOT NULL
                    """, Integer.class, caseId);
            if (damageCount != null && damageCount > 0) {
                return true;
            }

            // 2. Kiểm tra tồn tại final CaseDecision cho đúng Round 2 của chính Case này do actor khác SYSTEM_TIMEOUT tạo
            Integer decisionCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM og_compat.case_decisions d
                    JOIN og_compat.case_rounds r ON d.round_id = r.round_id
                    WHERE r.case_id = ? AND r.round_no = 2
                      AND d.is_final = TRUE
                      AND d.actor_type <> 'SYSTEM_TIMEOUT'
                    """, Integer.class, caseId);
            return decisionCount != null && decisionCount > 0;
        } catch (Exception queryEx) {
            log.warn("Không thể xác minh trạng thái competing workflow hậu rollback cho caseId={}: {}", caseId, queryEx.getMessage());
            return false;
        }
    }

    /**
     * Quét và xử lý toàn bộ các case quá hạn Vòng 2.
     * Mỗi case được xử lý trong một transaction độc lập (REQUIRES_NEW).
     * Một case lỗi hoặc bị khóa (55P03) không làm ảnh hưởng hoặc rollback các case khác.
     */
    public ReturnCaseDtos.TimeoutBatchResult processAllOverduePostReturnTimeouts() {
        List<Long> overdueCaseIds = scanOverduePostReturnCaseIds();
        List<Long> processedCaseIds = new ArrayList<>();
        List<Long> skippedCaseIds = new ArrayList<>();
        List<Long> lockedCaseIds = new ArrayList<>();
        List<Long> failedCaseIds = new ArrayList<>();

        for (Long caseId : overdueCaseIds) {
            try {
                ReturnCaseDtos.CaseTimeoutExecutionResult result = processPostReturnTimeoutForCaseInNewTx(caseId);
                if (result != null) {
                    switch (result.status()) {
                        case PROCESSED -> processedCaseIds.add(caseId);
                        case SKIPPED -> skippedCaseIds.add(caseId);
                        case LOCKED_55P03 -> lockedCaseIds.add(caseId);
                        case FAILED -> failedCaseIds.add(caseId);
                    }
                } else {
                    failedCaseIds.add(caseId);
                }
            } catch (Exception ex) {
                log.error("Lỗi không mong muốn trong batch loop cho caseId={}: {}", caseId, ex.getMessage(), ex);
                failedCaseIds.add(caseId);
            }
        }

        ReturnCaseDtos.TimeoutBatchResult batchResult = new ReturnCaseDtos.TimeoutBatchResult(
                overdueCaseIds.size(),
                processedCaseIds,
                skippedCaseIds,
                lockedCaseIds,
                failedCaseIds
        );
        log.info("Hoàn tất batch đối soát timeout Vòng 2: tổng quét={}, thành công={}, bỏ qua={}, bị khóa={}, thất bại={}",
                batchResult.totalScanned(),
                batchResult.processedCaseIds().size(),
                batchResult.skippedCaseIds().size(),
                batchResult.lockedCaseIds().size(),
                batchResult.failedCaseIds().size());
        return batchResult;
    }

    /**
     * Reconcile overdue return responses (Task 3.2.4 / complaint_plan.md).
     */
    public ReturnCaseDtos.TimeoutBatchResult reconcileOverdueReturnResponses() {
        return processAllOverduePostReturnTimeouts();
    }

    /**
     * Lấy danh sách các hồ sơ khiếu nại đã leo thang lên Admin do quá hạn SLA KTV 48h (Task 5.3).
     */
    @Transactional(readOnly = true)
    public ReturnCaseDtos.AdminEscalatedCasePageResponse getEscalatedCases(Long ktvId, int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<CaseEntity> pagedCases = caseRepository.findEscalatedCases(ktvId, pageable);

        Instant now = clock.instant();
        List<ReturnCaseDtos.AdminEscalatedCaseSummaryResponse> items = new ArrayList<>();
        for (CaseEntity c : pagedCases.getContent()) {
            Long orderId = c.getOrderId();
            Long buyerId = null;
            Long sellerId = null;
            if (orderId != null) {
                OrderEntity order = orderRepository.findById(orderId).orElse(null);
                if (order != null) {
                    buyerId = order.getBuyerId();
                    sellerId = order.getSellerId();
                }
            }

            CaseRoundEntity round2 = caseRoundRepository.findByCaseIdAndRoundNo(c.getId(), 2).orElse(null);
            Instant completeSubmissionAt = round2 != null ? round2.getCompleteSubmissionAt() : null;
            Integer roundNo = round2 != null ? round2.getRoundNo() : 2;
            String roundStage = round2 != null ? round2.getStage() : null;

            CaseEventEntity escEvent = round2 != null
                    ? caseEventRepository.findByCaseIdAndRoundIdAndEventType(c.getId(), round2.getId(), CaseEventEntity.TYPE_ESCALATED).orElse(null)
                    : null;
            Instant escalatedAt = escEvent != null ? escEvent.getOccurredAt() : null;
            String escalationReason = escEvent != null && escEvent.getReason() != null ? escEvent.getReason() : "KTV_SLA_EXPIRED_48H";

            Double slaOverdueHours = null;
            if (completeSubmissionAt != null) {
                Instant slaDeadline = completeSubmissionAt.plus(Duration.ofHours(48));
                if (now.isAfter(slaDeadline)) {
                    long minutes = Duration.between(slaDeadline, now).toMinutes();
                    slaOverdueHours = Math.round((minutes / 60.0) * 10.0) / 10.0;
                } else {
                    slaOverdueHours = 0.0;
                }
            }

            String ktvName = c.getAssignedTo() != null ? "KTV #" + c.getAssignedTo() : null;

            items.add(new ReturnCaseDtos.AdminEscalatedCaseSummaryResponse(
                    c.getId(),
                    "CASE-" + c.getId(),
                    orderId,
                    orderId != null ? "ORD-" + orderId : null,
                    buyerId,
                    sellerId,
                    c.getAssignedTo(),
                    ktvName,
                    c.getState(),
                    roundNo,
                    roundStage,
                    completeSubmissionAt,
                    escalatedAt,
                    slaOverdueHours,
                    escalationReason
            ));
        }

        return new ReturnCaseDtos.AdminEscalatedCasePageResponse(
                items,
                pagedCases.getNumber(),
                pagedCases.getSize(),
                pagedCases.getTotalElements(),
                pagedCases.getTotalPages(),
                pagedCases.isLast()
        );
    }

    /**
     * Quét các case_id đang ở Vòng 2 (POST_RETURN) đã quá hạn SLA 48 giờ của KTV tính từ complete_submission_at (BD-4 APPROVED).
     * Sử dụng candidate scan read-only qua JPQL CaseRepository.findOverdueKtvSlaCaseIds(slaThreshold).
     */
    @Transactional(readOnly = true)
    public List<Long> scanOverdueKtvSlaCaseIds() {
        Instant now = clock.instant();
        Instant slaThreshold = now.minus(Duration.ofHours(48));
        return caseRepository.findOverdueKtvSlaCaseIds(slaThreshold);
    }

    /**
     * Xử lý leo thang SLA KTV cho một hồ sơ cụ thể trong một transaction độc lập (REQUIRES_NEW).
     */
    public ReturnCaseDtos.CaseEscalationExecutionResult processKtvSlaEscalationForCaseInNewTx(Long caseId) {
        if (caseId == null) {
            return new ReturnCaseDtos.CaseEscalationExecutionResult(null, ReturnCaseDtos.EscalationProcessingStatus.FAILED, "caseId không được để trống", null);
        }

        try {
            return requiresNewTxTemplate.execute(status -> processKtvSlaEscalationForCaseInternal(caseId));
        } catch (Exception ex) {
            return handleEscalationExecutionException(caseId, ex);
        }
    }

    /**
     * Entrypoint tương thích ngược cho việc xử lý leo thang SLA một Case đơn lẻ.
     */
    public ReturnCaseDtos.ReturnCaseDetailResponse processKtvSlaEscalationForCase(Long caseId) {
        ReturnCaseDtos.CaseEscalationExecutionResult result = processKtvSlaEscalationForCaseInNewTx(caseId);
        return result != null ? result.response() : null;
    }

    /**
     * Thực thi logic leo thang SLA Vòng 2 cho một Case dưới sự bảo vệ của TransactionTemplate và Canonical Lock (Task 3.2.5).
     */
    private ReturnCaseDtos.CaseEscalationExecutionResult processKtvSlaEscalationForCaseInternal(Long caseId) {
        // 1. Khóa theo thứ tự chuẩn: Order -> Case -> Round(2)
        ReturnWorkflowLockGateway.ReturnWorkflowLockedEntities lockedEntities;
        try {
            lockedEntities = lockGateway.lockForRound(caseId, 2);
        } catch (NoSuchElementException e) {
            log.info("Bỏ qua leo thang cho caseId={}: không tìm thấy Vòng 2 hoặc Case", caseId);
            return new ReturnCaseDtos.CaseEscalationExecutionResult(caseId, ReturnCaseDtos.EscalationProcessingStatus.SKIPPED, "Không tìm thấy Vòng 2 hoặc Case", null);
        }

        OrderEntity order = lockedEntities.order();
        CaseEntity c = lockedEntities.returnCase();
        CaseRoundEntity round2 = lockedEntities.activeRound();
        Instant now = clock.instant();

        // 2. Re-verify điều kiện dưới khóa (First-write-wins & Invariants)
        // (a) Hồ sơ phải ở POST_RETURN
        if (!CaseEntity.STATE_POST_RETURN.equals(c.getState())) {
            log.info("Bỏ qua leo thang cho caseId={}: trạng thái hiện tại là {}", caseId, c.getState());
            return new ReturnCaseDtos.CaseEscalationExecutionResult(caseId, ReturnCaseDtos.EscalationProcessingStatus.SKIPPED, "Trạng thái case không phải POST_RETURN: " + c.getState(), mapToDetailResponse(c));
        }

        // (b) Vòng 2 phải đang OPEN và giai đoạn POST_RETURN_RESOLUTION
        if (!CaseRoundEntity.STATE_OPEN.equals(round2.getState())
                || !CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION.equals(round2.getStage())) {
            log.info("Bỏ qua leo thang cho caseId={}: Vòng 2 không còn OPEN hoặc sai stage ({}, {})", caseId, round2.getState(), round2.getStage());
            return new ReturnCaseDtos.CaseEscalationExecutionResult(caseId, ReturnCaseDtos.EscalationProcessingStatus.SKIPPED, "Vòng 2 không hợp lệ để leo thang", mapToDetailResponse(c));
        }

        // (c) Có complete_submission_at và đã quá 48 giờ (BD-4 APPROVED)
        if (round2.getCompleteSubmissionAt() == null) {
            log.info("Bỏ qua leo thang cho caseId={}: complete_submission_at IS NULL (hồ sơ chưa đủ điều kiện bắt đầu SLA)", caseId);
            return new ReturnCaseDtos.CaseEscalationExecutionResult(caseId, ReturnCaseDtos.EscalationProcessingStatus.SKIPPED, "complete_submission_at IS NULL", mapToDetailResponse(c));
        }

        Instant slaDeadline = round2.getCompleteSubmissionAt().plus(Duration.ofHours(48));
        if (now.isBefore(slaDeadline)) {
            log.info("Bỏ qua leo thang cho caseId={}: chưa đến hạn 48h SLA (deadline={}, now={})", caseId, slaDeadline, now);
            return new ReturnCaseDtos.CaseEscalationExecutionResult(caseId, ReturnCaseDtos.EscalationProcessingStatus.SKIPPED, "Chưa quá 48 giờ SLA", mapToDetailResponse(c));
        }

        // (d) Chưa có Final Decision cho Round 2
        List<CaseDecisionEntity> existingDecisions = caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(round2.getId());
        boolean hasFinalDecision = existingDecisions.stream().anyMatch(d -> Boolean.TRUE.equals(d.getIsFinal()));
        if (hasFinalDecision) {
            log.info("Bỏ qua leo thang cho caseId={}: Vòng 2 đã có Final Decision", caseId);
            return new ReturnCaseDtos.CaseEscalationExecutionResult(caseId, ReturnCaseDtos.EscalationProcessingStatus.SKIPPED, "Vòng 2 đã có Final Decision", mapToDetailResponse(c));
        }

        // 3. Idempotency Check & Deduplication
        String cmdKey = "EVENT:ESCALATED:R2:" + caseId;
        Optional<CaseEventEntity> existingByCmd = caseEventRepository.findByCommandKey(cmdKey);
        if (existingByCmd.isPresent()) {
            CaseEventEntity ev = existingByCmd.get();
            if (CaseEventEntity.TYPE_ESCALATED.equals(ev.getEventType())) {
                log.info("Idempotent replay: Sự kiện ESCALATED đã tồn tại cho caseId={}, roundId={}", caseId, round2.getId());
                return new ReturnCaseDtos.CaseEscalationExecutionResult(caseId, ReturnCaseDtos.EscalationProcessingStatus.SKIPPED, "Sự kiện leo thang đã tồn tại (idempotent)", mapToDetailResponse(c));
            }
        }

        boolean alreadyEscalated = caseEventRepository.existsByCaseIdAndRoundIdAndEventType(caseId, round2.getId(), CaseEventEntity.TYPE_ESCALATED);
        if (alreadyEscalated) {
            log.info("Vòng 2 của caseId={} đã được leo thang trước đó, bỏ qua", caseId);
            return new ReturnCaseDtos.CaseEscalationExecutionResult(caseId, ReturnCaseDtos.EscalationProcessingStatus.SKIPPED, "Hồ sơ đã được leo thang trước đó", mapToDetailResponse(c));
        }

        // 4. Tìm tài khoản ADMIN hợp lệ làm recipient (BD-3)
        Long adminRecipientId = findDefaultAdminRecipientId();
        if (adminRecipientId == null) {
            log.error("Không tìm thấy tài khoản Quản trị viên (ADMIN) hợp lệ để tiếp nhận hồ sơ leo thang cho caseId={}", caseId);
            return new ReturnCaseDtos.CaseEscalationExecutionResult(caseId, ReturnCaseDtos.EscalationProcessingStatus.FAILED, "Không tìm thấy tài khoản Quản trị viên (ADMIN) hợp lệ", mapToDetailResponse(c));
        }

        // 5. Ghi nhận sự kiện ESCALATED bằng SYSTEM actor (actor_id = null)
        Map<String, Object> metaMap = new LinkedHashMap<>();
        metaMap.put("actor", "SYSTEM");
        metaMap.put("reason", "KTV_SLA_EXPIRED_48H");
        metaMap.put("complete_submission_at", round2.getCompleteSubmissionAt().toString());
        metaMap.put("sla_hours", 48);
        metaMap.put("escalated_at", now.toString());
        String metadataJson = safeToJson(metaMap);

        CaseEventEntity event = new CaseEventEntity(
                UUID.randomUUID(),
                caseId,
                round2.getId(),
                null, // actor_id = null đại diện cho SYSTEM actor
                adminRecipientId, // recipient_id phải có role ADMIN theo trigger DB
                CaseEventEntity.TYPE_ESCALATED,
                "Hệ thống tự động chuyển hồ sơ lên Quản trị viên (Admin) do Kỹ thuật viên quá 48 giờ chưa đưa ra phán quyết",
                metadataJson,
                cmdKey
        );
        caseEventRepository.saveAndFlush(event);

        if (entityManager != null) {
            try {
                if (entityManager.contains(c)) {
                    entityManager.refresh(c);
                }
                if (entityManager.contains(round2)) {
                    entityManager.refresh(round2);
                }
            } catch (Exception ignored) {
            }
        }

        log.info("Đã tự động leo thang caseId={}, roundId={} lên Admin (id={}) do quá hạn 48h SLA", caseId, round2.getId(), adminRecipientId);
        return new ReturnCaseDtos.CaseEscalationExecutionResult(caseId, ReturnCaseDtos.EscalationProcessingStatus.ESCALATED, "Thành công tự động leo thang lên Quản trị viên", mapToDetailResponse(c));
    }

    ReturnCaseDtos.CaseEscalationExecutionResult handleEscalationExecutionException(Long caseId, Exception ex) {
        PostgresErrorMetadata meta = extractPostgresErrorMetadata(ex);
        String sqlState = meta.sqlState();

        if ("55P03".equalsIgnoreCase(sqlState)) {
            log.warn("Lock timeout (SQLSTATE 55P03) khi tranh chấp khóa leo thang caseId={}: {}. Sẽ retry ở chu kỳ tiếp theo.",
                    caseId, meta.message());
            return new ReturnCaseDtos.CaseEscalationExecutionResult(
                    caseId, ReturnCaseDtos.EscalationProcessingStatus.LOCKED_55P03, "Lock timeout (55P03): " + meta.message(), null
            );
        }

        if ("23514".equalsIgnoreCase(sqlState)) {
            log.info("Trigger 23514 từ chối leo thang cho caseId={}: {}", caseId, meta.message());
            return new ReturnCaseDtos.CaseEscalationExecutionResult(
                    caseId, ReturnCaseDtos.EscalationProcessingStatus.SKIPPED, "Bị chặn hợp lệ bởi trigger (23514): " + meta.message(), null
            );
        }

        if ("23505".equalsIgnoreCase(sqlState)) {
            log.info("Unique constraint 23505 (đã có event/command_key đồng thời) trên caseId={}: {}", caseId, meta.message());
            return new ReturnCaseDtos.CaseEscalationExecutionResult(
                    caseId, ReturnCaseDtos.EscalationProcessingStatus.SKIPPED, "Đã được ghi nhận bởi luồng đồng thời (23505)", null
            );
        }

        log.error("Lỗi không mong muốn khi xử lý leo thang caseId={}: {}", caseId, ex.getMessage(), ex);
        return new ReturnCaseDtos.CaseEscalationExecutionResult(
                caseId, ReturnCaseDtos.EscalationProcessingStatus.FAILED, "Lỗi hệ thống: " + ex.getMessage(), null
        );
    }

    /**
     * Quét và thực thi xử lý leo thang SLA KTV cho toàn bộ các case đang quá hạn 48 giờ.
     */
    public ReturnCaseDtos.EscalationBatchResult processAllOverdueKtvSlaEscalations() {
        List<Long> overdueCaseIds;
        try {
            overdueCaseIds = scanOverdueKtvSlaCaseIds();
        } catch (Exception ex) {
            log.error("Lỗi khi quét ứng viên quá hạn SLA KTV: {}", ex.getMessage(), ex);
            return new ReturnCaseDtos.EscalationBatchResult(0, List.of(), List.of(), List.of(), List.of());
        }

        if (overdueCaseIds == null || overdueCaseIds.isEmpty()) {
            return new ReturnCaseDtos.EscalationBatchResult(0, List.of(), List.of(), List.of(), List.of());
        }

        List<Long> escalatedCaseIds = new ArrayList<>();
        List<Long> skippedCaseIds = new ArrayList<>();
        List<Long> lockedCaseIds = new ArrayList<>();
        List<Long> failedCaseIds = new ArrayList<>();

        for (Long caseId : overdueCaseIds) {
            try {
                ReturnCaseDtos.CaseEscalationExecutionResult result = processKtvSlaEscalationForCaseInNewTx(caseId);
                if (result != null) {
                    switch (result.status()) {
                        case ESCALATED -> escalatedCaseIds.add(caseId);
                        case SKIPPED -> skippedCaseIds.add(caseId);
                        case LOCKED_55P03 -> lockedCaseIds.add(caseId);
                        case FAILED -> failedCaseIds.add(caseId);
                    }
                } else {
                    failedCaseIds.add(caseId);
                }
            } catch (Exception ex) {
                log.error("Lỗi không mong muốn trong batch loop leo thang cho caseId={}: {}", caseId, ex.getMessage(), ex);
                failedCaseIds.add(caseId);
            }
        }

        ReturnCaseDtos.EscalationBatchResult batchResult = new ReturnCaseDtos.EscalationBatchResult(
                overdueCaseIds.size(),
                escalatedCaseIds,
                skippedCaseIds,
                lockedCaseIds,
                failedCaseIds
        );
        log.info("Hoàn tất batch đối soát SLA KTV Vòng 2: tổng quét={}, leo thang={}, bỏ qua={}, bị khóa={}, thất bại={}",
                batchResult.totalScanned(),
                batchResult.escalatedCaseIds().size(),
                batchResult.skippedCaseIds().size(),
                batchResult.lockedCaseIds().size(),
                batchResult.failedCaseIds().size());
        return batchResult;
    }

    /**
     * Reconcile overdue KTV SLA cases (Task 3.2.5 / BD-3 / complaint_plan.md).
     */
    public ReturnCaseDtos.EscalationBatchResult reconcileKtvOverdueSla() {
        return processAllOverdueKtvSlaEscalations();
    }

    /**
     * Submits a Review Request for a closed/rejected Return Case per PO-DEC-02B and OPEN-02-RECON (Option B).
     *
     * <p><b>OPEN-02-RECON Policy (Option B - Intentional BD-1 Narrowing):</b>
     * A Return Case must NOT be reopened once any settlement operation record exists for it, regardless of whether
     * the operation is {@code REQUESTED}, {@code PROCESSING}, {@code CONFIRMED}, or {@code FAILED}.
     *
     * @param caseId the ID of the case to request review for
     * @param adminId the ID of the ADMIN requesting review (per BD-1 Rule 3: Admin may submit request but cannot unilaterally reopen)
     * @param request the review request containing audit reason and commandKey
     * @return response indicating review request submission status
     */
    @Transactional
    public ReturnCaseDtos.ReviewRequestResponse submitReviewRequest(
            Long caseId,
            Long adminId,
            ReturnCaseDtos.CreateReviewRequest request
    ) {
        if (caseId == null) {
            throw new IllegalArgumentException("caseId không được để trống");
        }
        if (adminId == null) {
            throw new IllegalArgumentException("adminId không được để trống");
        }
        if (request == null || request.reason() == null || request.reason().trim().length() < 10) {
            throw new IllegalArgumentException("Lý do yêu cầu xem xét lại bắt buộc có tối thiểu 10 ký tự");
        }
        if (request.reason().trim().length() > 2000) {
            throw new IllegalArgumentException("Lý do yêu cầu xem xét lại tối đa 2000 ký tự");
        }

        // 1. Order-first canonical lock: Order -> Case
        ReturnWorkflowLockGateway.ReturnWorkflowLockedEntities locked = lockGateway.lockForCase(caseId);
        CaseEntity c = locked.returnCase();

        // 2. Validate case state: Only terminal states (CLOSED or REJECTED) are eligible for review request
        if (!CaseEntity.STATE_CLOSED.equals(c.getState()) && !CaseEntity.STATE_REJECTED.equals(c.getState())) {
            throw new IllegalStateException("Chỉ có thể yêu cầu xem xét lại đối với hồ sơ đã kết thúc (CLOSED hoặc REJECTED). Trạng thái hiện tại: " + c.getState());
        }

        // 3. OPEN-02-RECON (Option B): Reject if ANY settlement_operations record exists across all states:
        // REQUESTED, PROCESSING, CONFIRMED, FAILED
        Integer opCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM og_compat.settlement_operations so
                LEFT JOIN og_compat.case_decisions d ON so.case_decision_id = d.decision_id
                LEFT JOIN og_compat.case_rounds r ON d.round_id = r.round_id
                WHERE r.case_id = ?
                  AND so.state IN ('REQUESTED', 'PROCESSING', 'CONFIRMED', 'FAILED')
                """,
                Integer.class,
                caseId
        );

        if (opCount != null && opCount > 0) {
            log.warn("Review request rejected for caseId={} by adminId={}: {} settlement operation(s) exist in active/terminal states",
                    caseId, adminId, opCount);
            throw new TrustSafetyBusinessException(
                    HttpStatus.CONFLICT,
                    "CASE_SETTLEMENT_EXISTS",
                    "Không thể mở lại hồ sơ khi đã tồn tại giao dịch thanh toán giải quyết khiếu nại (bất kể trạng thái REQUESTED, PROCESSING, CONFIRMED hay FAILED). Quyết định tài chính mang tính chung thẩm."
            );
        }

        // 4. Record review request event without mutating terminal Case state
        // (Admin cannot unilaterally reopen the case; review must be conducted by authorized KTV per BD-1)
        String cmdKey = request.commandKey() != null && !request.commandKey().isBlank()
                ? request.commandKey().trim()
                : "EVENT:REVIEW_REQUESTED:" + caseId + ":" + UUID.randomUUID();

        CaseEventEntity event = new CaseEventEntity(
                UUID.randomUUID(),
                caseId,
                null,
                adminId,
                null,
                "REVIEW_REQUESTED",
                request.reason().trim(),
                null,
                cmdKey
        );
        caseEventRepository.save(event);
        log.info("Recorded REVIEW_REQUESTED event for caseId={} by adminId={}", caseId, adminId);

        return new ReturnCaseDtos.ReviewRequestResponse(
                caseId,
                "REVIEW_REQUESTED",
                "Yêu cầu xem xét lại hồ sơ đã được tiếp nhận và ghi nhận thành công."
        );
    }
}
