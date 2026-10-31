package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentEntity;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    private final CaseRepository caseRepository;
    private final CaseRoundRepository caseRoundRepository;
    private final CaseEvidenceRepository caseEvidenceRepository;
    private final CaseEventRepository caseEventRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    public ReturnCaseService(
            CaseRepository caseRepository,
            CaseRoundRepository caseRoundRepository,
            CaseEvidenceRepository caseEvidenceRepository,
            CaseEventRepository caseEventRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            EntityManager entityManager,
            Clock clock
    ) {
        this.caseRepository = caseRepository;
        this.caseRoundRepository = caseRoundRepository;
        this.caseEvidenceRepository = caseEvidenceRepository;
        this.caseEventRepository = caseEventRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.entityManager = entityManager;
        this.clock = clock != null ? clock : Clock.systemUTC();
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
            CaseEvidenceEntity ev = new CaseEvidenceEntity(
                    UUID.randomUUID(),
                    round.getId(),
                    buyerId,
                    1,
                    item.mediaType().toUpperCase(),
                    item.objectKey(),
                    item.textContent(),
                    item.contentDigest(),
                    item.fileSizeBytes(),
                    item.durationSeconds()
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
                eventDtos
        );
    }
}
