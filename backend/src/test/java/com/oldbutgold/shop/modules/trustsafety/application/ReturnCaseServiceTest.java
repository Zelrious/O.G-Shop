package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReturnCaseServiceTest - Unit tests for Task 2.2 Return Request (UC62)")
class ReturnCaseServiceTest {

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private CaseRoundRepository caseRoundRepository;

    @Mock
    private CaseEvidenceRepository caseEvidenceRepository;

    @Mock
    private CaseDecisionRepository caseDecisionRepository;

    @Mock
    private CaseEventRepository caseEventRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private Clock clock;
    private Instant fixedNow;
    private ReturnCaseService service;

    private Long buyerId = 100L;
    private Long sellerId = 200L;
    private Long orderId = 1001L;

    @BeforeEach
    void setUp() {
        fixedNow = Instant.parse("2026-10-31T10:00:00Z");
        clock = Clock.fixed(fixedNow, ZoneId.of("UTC"));

        service = new ReturnCaseService(
                caseRepository,
                caseRoundRepository,
                caseEvidenceRepository,
                caseDecisionRepository,
                caseEventRepository,
                orderRepository,
                paymentRepository,
                entityManager,
                jdbcTemplate,
                clock
        );
    }

    private OrderEntity createValidDeliveredOrder() {
        OrderEntity order = new OrderEntity(
                UUID.randomUUID(),
                buyerId,
                sellerId,
                1L,
                "Recipient",
                "0901234567",
                "Hồ Chí Minh",
                "Quận 1",
                "Phường Bến Nghé",
                "123 Lê Lợi",
                new BigDecimal("500000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("500000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "PLATFORM",
                new BigDecimal("500000.00"),
                fixedNow.plus(Duration.ofHours(1)),
                fixedNow
        );

        ReflectionTestUtils.setField(order, "id", orderId);
        ReflectionTestUtils.setField(order, "status", "DELIVERED");
        ReflectionTestUtils.setField(order, "workflowModel", "UC83");
        ReflectionTestUtils.setField(order, "returnAllowed", true);
        ReflectionTestUtils.setField(order, "validDeliveredAt", fixedNow.minus(Duration.ofDays(1))); // 1 day ago < 3 days
        ReflectionTestUtils.setField(order, "earlyCompletedAt", null);

        return order;
    }

    @Test
    @DisplayName("createReturnRequest: Happy path creates case, round 1, evidence and submitted event")
    void createReturnRequest_success() {
        OrderEntity order = createValidDeliveredOrder();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(caseRepository.findActiveCaseByOrderId(orderId)).thenReturn(Optional.empty());
        when(caseRepository.findByCommandKey(any())).thenReturn(Optional.empty());

        when(caseRepository.saveAndFlush(any(CaseEntity.class))).thenAnswer(inv -> {
            CaseEntity c = inv.getArgument(0);
            c.setId(501L);
            return c;
        });

        when(caseRoundRepository.saveAndFlush(any(CaseRoundEntity.class))).thenAnswer(inv -> {
            CaseRoundEntity r = inv.getArgument(0);
            r.setId(601L);
            return r;
        });

        when(caseRoundRepository.findLatestRoundByCaseId(501L)).thenAnswer(inv -> {
            CaseRoundEntity r = new CaseRoundEntity(501L, 1, CaseRoundEntity.STAGE_RETURN_APPROVAL);
            r.setId(601L);
            r.setCompleteSubmissionAt(fixedNow);
            return Optional.of(r);
        });

        List<ReturnCaseDtos.EvidenceItemDto> evidences = List.of(
                new ReturnCaseDtos.EvidenceItemDto("IMAGE", "https://img.example/1.jpg", "Mặt trước", null, 1024L, null),
                new ReturnCaseDtos.EvidenceItemDto("IMAGE", "https://img.example/2.jpg", "Mặt sau", null, 2048L, null),
                new ReturnCaseDtos.EvidenceItemDto("VIDEO", "https://video.example/unboxing.mp4", "Video mở hộp", null, 20_000_000L, 45)
        );

        ReturnCaseDtos.CreateReturnRequest request = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.CHANGE_MIND,
                "Tôi đổi ý không muốn nhận nữa, hàng còn nguyên seal",
                evidences,
                "CMD_REQ_1"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse response = service.createReturnRequest(buyerId, orderId, request);

        assertThat(response).isNotNull();
        assertThat(response.caseId()).isEqualTo(501L);
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.caseType()).isEqualTo(CaseEntity.TYPE_RETURN);
        assertThat(response.state()).isEqualTo(CaseEntity.STATE_OPEN);
        assertThat(response.reasonCode()).isEqualTo(ReturnReasonCode.CHANGE_MIND);
        assertThat(response.cleanDescription()).isEqualTo("Tôi đổi ý không muốn nhận nữa, hàng còn nguyên seal");
        assertThat(response.currentRound()).isNotNull();
        assertThat(response.currentRound().roundNo()).isEqualTo(1);
        assertThat(response.currentRound().stage()).isEqualTo(CaseRoundEntity.STAGE_RETURN_APPROVAL);
        assertThat(response.currentRound().ktvDueAt()).isEqualTo(fixedNow.plus(Duration.ofHours(48)));

        // Verify CaseEntity saved
        ArgumentCaptor<CaseEntity> caseCaptor = ArgumentCaptor.forClass(CaseEntity.class);
        verify(caseRepository).saveAndFlush(caseCaptor.capture());
        CaseEntity savedCase = caseCaptor.getValue();
        assertThat(savedCase.getCreatedBy()).isEqualTo(buyerId);
        assertThat(savedCase.getCommandKey()).isEqualTo("CMD_REQ_1");
        assertThat(savedCase.getDescription()).startsWith("[CHANGE_MIND]");

        // Verify CaseEvidenceEntity saved 3 items
        verify(caseEvidenceRepository, times(3)).save(any(CaseEvidenceEntity.class));

        // Verify CaseEventEntity SUBMITTED saved
        ArgumentCaptor<CaseEventEntity> eventCaptor = ArgumentCaptor.forClass(CaseEventEntity.class);
        verify(caseEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(CaseEventEntity.TYPE_SUBMITTED);
        assertThat(eventCaptor.getValue().getActorId()).isEqualTo(buyerId);
        assertThat(eventCaptor.getValue().getRecipientId()).isEqualTo(sellerId);
    }

    @Test
    @DisplayName("createReturnRequest: Reject if caller is not the buyer of the order")
    void createReturnRequest_rejectIfNotBuyer() {
        OrderEntity order = createValidDeliveredOrder();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        ReturnCaseDtos.CreateReturnRequest request = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.CHANGE_MIND,
                "Detail",
                List.of(),
                null
        );

        Long strangerId = 999L;
        assertThatThrownBy(() -> service.createReturnRequest(strangerId, orderId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chỉ người mua của đơn hàng");
    }

    @Test
    @DisplayName("createReturnRequest: Reject if workflow model is not UC83")
    void createReturnRequest_rejectIfNotUC83() {
        OrderEntity order = createValidDeliveredOrder();
        ReflectionTestUtils.setField(order, "workflowModel", "LEGACY_V14");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        ReturnCaseDtos.CreateReturnRequest request = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.CHANGE_MIND,
                "Detail",
                List.of(),
                null
        );

        assertThatThrownBy(() -> service.createReturnRequest(buyerId, orderId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mô hình UC83");
    }

    @Test
    @DisplayName("createReturnRequest: Reject if order is not DELIVERED or validDeliveredAt is null")
    void createReturnRequest_rejectIfNotDelivered() {
        OrderEntity order = createValidDeliveredOrder();
        ReflectionTestUtils.setField(order, "status", "SHIPPED");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        ReturnCaseDtos.CreateReturnRequest request = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.CHANGE_MIND,
                "Detail",
                List.of(),
                null
        );

        assertThatThrownBy(() -> service.createReturnRequest(buyerId, orderId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chưa giao thành công hợp lệ");
    }

    @Test
    @DisplayName("createReturnRequest: Reject if returnAllowed is false")
    void createReturnRequest_rejectIfReturnNotAllowed() {
        OrderEntity order = createValidDeliveredOrder();
        ReflectionTestUtils.setField(order, "returnAllowed", false);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        ReturnCaseDtos.CreateReturnRequest request = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.CHANGE_MIND,
                "Detail",
                List.of(),
                null
        );

        assertThatThrownBy(() -> service.createReturnRequest(buyerId, orderId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("không áp dụng chính sách trả hàng");
    }

    @Test
    @DisplayName("createReturnRequest: Reject if return window exceeds 3 days from delivery")
    void createReturnRequest_rejectIfExpiredAfter3Days() {
        OrderEntity order = createValidDeliveredOrder();
        // Delivered 4 days ago
        ReflectionTestUtils.setField(order, "validDeliveredAt", fixedNow.minus(Duration.ofDays(4)));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        ReturnCaseDtos.CreateReturnRequest request = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.CHANGE_MIND,
                "Detail",
                List.of(),
                null
        );

        assertThatThrownBy(() -> service.createReturnRequest(buyerId, orderId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("quá thời hạn 3 ngày");
    }

    @Test
    @DisplayName("createReturnRequest: Reject if order has already been early-completed")
    void createReturnRequest_rejectIfEarlyCompleted() {
        OrderEntity order = createValidDeliveredOrder();
        ReflectionTestUtils.setField(order, "earlyCompletedAt", fixedNow.minus(Duration.ofHours(2)));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        ReturnCaseDtos.CreateReturnRequest request = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.CHANGE_MIND,
                "Detail",
                List.of(),
                null
        );

        assertThatThrownBy(() -> service.createReturnRequest(buyerId, orderId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("xác nhận hoàn tất sớm");
    }

    @Test
    @DisplayName("createReturnRequest: Reject if order is already COMPLETED (funds disbursed)")
    void createReturnRequest_rejectIfAlreadyCompleted() {
        OrderEntity order = createValidDeliveredOrder();
        ReflectionTestUtils.setField(order, "status", "COMPLETED");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        ReturnCaseDtos.CreateReturnRequest request = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.CHANGE_MIND,
                "Detail",
                List.of(),
                null
        );

        assertThatThrownBy(() -> service.createReturnRequest(buyerId, orderId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đã hoàn tất giải ngân");
    }

    @Test
    @DisplayName("createReturnRequest: Reject if order already has an active case")
    void createReturnRequest_rejectIfActiveCaseAlreadyExists() {
        OrderEntity order = createValidDeliveredOrder();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        CaseEntity existingCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Old case", "CMD_OLD");
        existingCase.setId(888L);
        when(caseRepository.findActiveCaseByOrderId(orderId)).thenReturn(Optional.of(existingCase));

        ReturnCaseDtos.CreateReturnRequest request = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.CHANGE_MIND,
                "Detail",
                List.of(),
                null
        );

        assertThatThrownBy(() -> service.createReturnRequest(buyerId, orderId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đã có hồ sơ khiếu nại/trả hàng đang được xử lý");
    }

    @Test
    @DisplayName("createReturnRequest: Reject if evidence image count exceeds 5")
    void createReturnRequest_rejectIfTooManyImages() {
        OrderEntity order = createValidDeliveredOrder();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(caseRepository.findActiveCaseByOrderId(orderId)).thenReturn(Optional.empty());

        List<ReturnCaseDtos.EvidenceItemDto> evidences = List.of(
                new ReturnCaseDtos.EvidenceItemDto("IMAGE", "url1", null, null, null, null),
                new ReturnCaseDtos.EvidenceItemDto("IMAGE", "url2", null, null, null, null),
                new ReturnCaseDtos.EvidenceItemDto("IMAGE", "url3", null, null, null, null),
                new ReturnCaseDtos.EvidenceItemDto("IMAGE", "url4", null, null, null, null),
                new ReturnCaseDtos.EvidenceItemDto("IMAGE", "url5", null, null, null, null),
                new ReturnCaseDtos.EvidenceItemDto("IMAGE", "url6", null, null, null, null)
        );

        ReturnCaseDtos.CreateReturnRequest request = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.NOT_AS_DESCRIBED,
                "Detail",
                evidences,
                null
        );

        assertThatThrownBy(() -> service.createReturnRequest(buyerId, orderId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tối đa là 5 ảnh");
    }

    @Test
    @DisplayName("createReturnRequest: Reject if evidence video duration exceeds 60s")
    void createReturnRequest_rejectIfVideoTooLong() {
        OrderEntity order = createValidDeliveredOrder();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(caseRepository.findActiveCaseByOrderId(orderId)).thenReturn(Optional.empty());

        List<ReturnCaseDtos.EvidenceItemDto> evidences = List.of(
                new ReturnCaseDtos.EvidenceItemDto("VIDEO", "url1", null, null, 10_000_000L, 65)
        );

        ReturnCaseDtos.CreateReturnRequest request = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.DAMAGED_OR_DEFECTIVE,
                "Hàng hỏng",
                evidences,
                null
        );

        assertThatThrownBy(() -> service.createReturnRequest(buyerId, orderId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tối đa là 60 giây");
    }

    @Test
    @DisplayName("createReturnRequest: Reject if evidence video size exceeds 50MB")
    void createReturnRequest_rejectIfVideoTooLarge() {
        OrderEntity order = createValidDeliveredOrder();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(caseRepository.findActiveCaseByOrderId(orderId)).thenReturn(Optional.empty());

        List<ReturnCaseDtos.EvidenceItemDto> evidences = List.of(
                new ReturnCaseDtos.EvidenceItemDto("VIDEO", "url1", null, null, 60L * 1024 * 1024, 40)
        );

        ReturnCaseDtos.CreateReturnRequest request = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.DAMAGED_OR_DEFECTIVE,
                "Hàng hỏng",
                evidences,
                null
        );

        assertThatThrownBy(() -> service.createReturnRequest(buyerId, orderId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tối đa là 50MB");
    }

    @Test
    @DisplayName("createReturnRequest: Reject if evidence video count exceeds 2")
    void createReturnRequest_rejectIfTooManyVideos() {
        OrderEntity order = createValidDeliveredOrder();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(caseRepository.findActiveCaseByOrderId(orderId)).thenReturn(Optional.empty());

        List<ReturnCaseDtos.EvidenceItemDto> evidences = List.of(
                new ReturnCaseDtos.EvidenceItemDto("VIDEO", "url1", null, null, 10_000_000L, 20),
                new ReturnCaseDtos.EvidenceItemDto("VIDEO", "url2", null, null, 10_000_000L, 20),
                new ReturnCaseDtos.EvidenceItemDto("VIDEO", "url3", null, null, 10_000_000L, 20)
        );

        ReturnCaseDtos.CreateReturnRequest request = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.DAMAGED_OR_DEFECTIVE,
                "Hàng hỏng",
                evidences,
                null
        );

        assertThatThrownBy(() -> service.createReturnRequest(buyerId, orderId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tối đa là 2 video");
    }

    @Test
    @DisplayName("createReturnRequest: Idempotency by command_key returns existing case")
    void createReturnRequest_idempotent() {
        OrderEntity order = createValidDeliveredOrder();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(caseRepository.findActiveCaseByOrderId(orderId)).thenReturn(Optional.empty());

        CaseEntity existingCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "[CHANGE_MIND] Đã tạo", "CMD_IDEMPOTENT_1");
        existingCase.setId(777L);
        when(caseRepository.findByCommandKey("CMD_IDEMPOTENT_1")).thenReturn(Optional.of(existingCase));

        ReturnCaseDtos.CreateReturnRequest request = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.CHANGE_MIND,
                "Đã tạo",
                List.of(),
                "CMD_IDEMPOTENT_1"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse response = service.createReturnRequest(buyerId, orderId, request);

        assertThat(response).isNotNull();
        assertThat(response.caseId()).isEqualTo(777L);
        verify(caseRepository, never()).saveAndFlush(any());
    }

    // ==================== TASK 2.3: Seller Round 1 & KTV Round 1 Tests ====================

    @Test
    @DisplayName("sellerRespondRound1: Seller approves return -> case becomes RETURN_APPROVED, round DECIDED")
    void sellerRespondRound1_approveReturn_success() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "[CHANGE_MIND] Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_OPEN);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round = new CaseRoundEntity(caseId, 1, CaseRoundEntity.STAGE_RETURN_APPROVAL);
        round.setId(601L);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 1)).thenReturn(Optional.of(round));
        when(caseRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ReturnCaseDtos.SellerRound1ResponseRequest request = new ReturnCaseDtos.SellerRound1ResponseRequest(
                CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                "Đồng ý nhận lại hàng",
                "CMD_SELLER_R1_1"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse response = service.sellerRespondRound1(sellerId, caseId, request);

        assertThat(response).isNotNull();
        assertThat(response.state()).isEqualTo(CaseEntity.STATE_RETURN_APPROVED);
        assertThat(response.approvedAt()).isEqualTo(fixedNow);
        assertThat(round.getState()).isEqualTo(CaseRoundEntity.STATE_DECIDED);

        ArgumentCaptor<CaseDecisionEntity> decisionCaptor = ArgumentCaptor.forClass(CaseDecisionEntity.class);
        verify(caseDecisionRepository).save(decisionCaptor.capture());
        assertThat(decisionCaptor.getValue().getOutcome()).isEqualTo(CaseDecisionEntity.OUTCOME_APPROVE_RETURN);
        assertThat(decisionCaptor.getValue().getIsFinal()).isTrue();
        assertThat(decisionCaptor.getValue().getReviewedBy()).isEqualTo(sellerId);
    }

    @Test
    @DisplayName("sellerRespondRound1: Seller declines return -> case becomes REVIEWING, round stays OPEN for KTV")
    void sellerRespondRound1_sellerDeclined_success() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "[CHANGE_MIND] Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_OPEN);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round = new CaseRoundEntity(caseId, 1, CaseRoundEntity.STAGE_RETURN_APPROVAL);
        round.setId(601L);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 1)).thenReturn(Optional.of(round));
        when(caseRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ReturnCaseDtos.SellerRound1ResponseRequest request = new ReturnCaseDtos.SellerRound1ResponseRequest(
                CaseDecisionEntity.OUTCOME_SELLER_DECLINED,
                "Hàng đã mở seal, không đồng ý nhận lại",
                "CMD_SELLER_R1_2"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse response = service.sellerRespondRound1(sellerId, caseId, request);

        assertThat(response).isNotNull();
        assertThat(response.state()).isEqualTo(CaseEntity.STATE_REVIEWING);
        assertThat(round.getState()).isEqualTo(CaseRoundEntity.STATE_OPEN);

        ArgumentCaptor<CaseDecisionEntity> decisionCaptor = ArgumentCaptor.forClass(CaseDecisionEntity.class);
        verify(caseDecisionRepository).save(decisionCaptor.capture());
        assertThat(decisionCaptor.getValue().getOutcome()).isEqualTo(CaseDecisionEntity.OUTCOME_SELLER_DECLINED);
        assertThat(decisionCaptor.getValue().getIsFinal()).isFalse();
    }

    @Test
    @DisplayName("sellerRespondRound1: Reject if caller is not the seller")
    void sellerRespondRound1_rejectIfNotSeller() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "[CHANGE_MIND] Detail", "CMD_1");
        returnCase.setId(caseId);
        OrderEntity order = createValidDeliveredOrder();

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        ReturnCaseDtos.SellerRound1ResponseRequest request = new ReturnCaseDtos.SellerRound1ResponseRequest(
                CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                "OK",
                null
        );

        assertThatThrownBy(() -> service.sellerRespondRound1(buyerId, caseId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chỉ người bán của đơn hàng mới có quyền phản hồi");
    }

    @Test
    @DisplayName("sellerRespondRound1: First-write wins: Reject if case is not OPEN")
    void sellerRespondRound1_firstWriteWins_rejectIfNotOpen() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "[CHANGE_MIND] Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_RETURN_APPROVED); // Already approved by KTV or earlier

        OrderEntity order = createValidDeliveredOrder();
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        ReturnCaseDtos.SellerRound1ResponseRequest request = new ReturnCaseDtos.SellerRound1ResponseRequest(
                CaseDecisionEntity.OUTCOME_SELLER_DECLINED,
                "Từ chối",
                null
        );

        assertThatThrownBy(() -> service.sellerRespondRound1(sellerId, caseId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hồ sơ không ở trạng thái chờ người bán phản hồi");
    }

    @Test
    @DisplayName("ktvDecideRound1: KTV approves return from REVIEWING -> case becomes RETURN_APPROVED")
    void ktvDecideRound1_approveReturn_success() {
        Long caseId = 501L;
        Long ktvId = 300L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "[NOT_AS_DESCRIBED] Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_REVIEWING);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round = new CaseRoundEntity(caseId, 1, CaseRoundEntity.STAGE_RETURN_APPROVAL);
        round.setId(601L);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 1)).thenReturn(Optional.of(round));
        when(caseRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ReturnCaseDtos.KtvRound1DecisionRequest request = new ReturnCaseDtos.KtvRound1DecisionRequest(
                CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                "KTV xác minh ảnh sản phẩm giao sai màu thực tế",
                "CMD_KTV_R1_1"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse response = service.ktvDecideRound1(ktvId, caseId, request);

        assertThat(response).isNotNull();
        assertThat(response.state()).isEqualTo(CaseEntity.STATE_RETURN_APPROVED);
        assertThat(response.approvedAt()).isEqualTo(fixedNow);
        assertThat(round.getState()).isEqualTo(CaseRoundEntity.STATE_DECIDED);

        ArgumentCaptor<CaseDecisionEntity> decisionCaptor = ArgumentCaptor.forClass(CaseDecisionEntity.class);
        verify(caseDecisionRepository).save(decisionCaptor.capture());
        assertThat(decisionCaptor.getValue().getOutcome()).isEqualTo(CaseDecisionEntity.OUTCOME_APPROVE_RETURN);
        assertThat(decisionCaptor.getValue().getReviewedBy()).isEqualTo(ktvId);
    }

    @Test
    @DisplayName("ktvDecideRound1: KTV rejects return -> case becomes REJECTED, closes money holds, does not disburse immediately")
    void ktvDecideRound1_rejectReturn_success() {
        Long caseId = 501L;
        Long ktvId = 300L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "[CHANGE_MIND] Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_REVIEWING);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round = new CaseRoundEntity(caseId, 1, CaseRoundEntity.STAGE_RETURN_APPROVAL);
        round.setId(601L);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 1)).thenReturn(Optional.of(round));
        when(caseRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ReturnCaseDtos.KtvRound1DecisionRequest request = new ReturnCaseDtos.KtvRound1DecisionRequest(
                CaseDecisionEntity.OUTCOME_REJECT_RETURN,
                "Hàng không đủ điều kiện đổi ý do người mua đã sử dụng",
                "CMD_KTV_R1_REJECT"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse response = service.ktvDecideRound1(ktvId, caseId, request);

        assertThat(response).isNotNull();
        assertThat(response.state()).isEqualTo(CaseEntity.STATE_REJECTED);
        assertThat(response.closedAt()).isEqualTo(fixedNow);
        assertThat(round.getState()).isEqualTo(CaseRoundEntity.STATE_DECIDED);

        // Verify decision recorded
        verify(caseDecisionRepository).save(any(CaseDecisionEntity.class));
        // Verify money_holds closed
        verify(jdbcTemplate).update(contains("UPDATE og_compat.money_holds SET closed_at"), any(java.sql.Timestamp.class), eq(caseId));
    }

    @Test
    @DisplayName("ktvDecideRound1: Reject if reason is blank or too short")
    void ktvDecideRound1_rejectIfReasonBlank() {
        Long caseId = 501L;
        Long ktvId = 300L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "[CHANGE_MIND] Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_REVIEWING);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round = new CaseRoundEntity(caseId, 1, CaseRoundEntity.STAGE_RETURN_APPROVAL);
        round.setId(601L);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 1)).thenReturn(Optional.of(round));

        ReturnCaseDtos.KtvRound1DecisionRequest request = new ReturnCaseDtos.KtvRound1DecisionRequest(
                CaseDecisionEntity.OUTCOME_REJECT_RETURN,
                "", // Blank reason
                null
        );

        assertThatThrownBy(() -> service.ktvDecideRound1(ktvId, caseId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("KTV bắt buộc phải nhập lý do phán quyết chi tiết");
    }

    // ==================== TASK 2.4: Buyer Withdraw Return Request Tests ====================

    @Test
    @DisplayName("withdrawReturnRequest: Buyer withdraws when OPEN -> case CLOSED, money holds closed")
    void withdrawReturnRequest_fromOpen_success() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "[CHANGE_MIND] Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_OPEN);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round = new CaseRoundEntity(caseId, 1, CaseRoundEntity.STAGE_RETURN_APPROVAL);
        round.setId(601L);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findLatestRoundByCaseId(caseId)).thenReturn(Optional.of(round));
        when(caseRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ReturnCaseDtos.WithdrawReturnRequest request = new ReturnCaseDtos.WithdrawReturnRequest(
                "Tôi đã đổi ý và quyết định giữ lại sản phẩm",
                "CMD_WITHDRAW_1"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse response = service.withdrawReturnRequest(buyerId, caseId, request);

        assertThat(response).isNotNull();
        assertThat(response.state()).isEqualTo(CaseEntity.STATE_CLOSED);
        assertThat(response.closedAt()).isEqualTo(fixedNow);
        assertThat(round.getState()).isEqualTo(CaseRoundEntity.STATE_DECIDED);

        // Verify event CLEANUP recorded
        ArgumentCaptor<CaseEventEntity> eventCaptor = ArgumentCaptor.forClass(CaseEventEntity.class);
        verify(caseEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(CaseEventEntity.TYPE_CLEANUP);
        assertThat(eventCaptor.getValue().getActorId()).isEqualTo(buyerId);

        // Verify money_holds closed
        verify(jdbcTemplate).update(contains("UPDATE og_compat.money_holds SET closed_at"), any(java.sql.Timestamp.class), eq(caseId));
    }

    @Test
    @DisplayName("withdrawReturnRequest: Reject if caller is not the buyer who created the case")
    void withdrawReturnRequest_rejectIfNotCreator() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "[CHANGE_MIND] Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_OPEN);

        OrderEntity order = createValidDeliveredOrder();
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        Long otherUser = 999L;
        assertThatThrownBy(() -> service.withdrawReturnRequest(otherUser, caseId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chỉ người mua tạo yêu cầu");
    }

    @Test
    @DisplayName("withdrawReturnRequest: Reject if goods have already been returned/shipped")
    void withdrawReturnRequest_rejectIfAlreadyReturned() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "[CHANGE_MIND] Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_RETURNING);

        OrderEntity order = createValidDeliveredOrder();
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.withdrawReturnRequest(buyerId, caseId, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Không thể rút yêu cầu khi hồ sơ ở trạng thái RETURNING");
    }
}
