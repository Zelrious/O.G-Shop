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
    private ShipmentRepository shipmentRepository;

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
                shipmentRepository,
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
            if (r.getId() == null) {
                r.setId(601L);
            }
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

    // =========================================================================
    // TASK 3.1: VẬN CHUYỂN HÀNG TRẢ (UC33) UNIT TESTS
    // =========================================================================

    @Test
    @DisplayName("Task 3.1: submitReturnShipment - success CARRIER creates shipment and transitions to RETURNING")
    void submitReturnShipment_success_carrier() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "[DEFECTIVE] Hàng lỗi", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_RETURN_APPROVED);
        returnCase.setApprovedAt(fixedNow);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round1 = new CaseRoundEntity(caseId, 1, CaseRoundEntity.STAGE_RETURN_APPROVAL);
        round1.setId(10L);

        ShipmentEntity createdShipment = ShipmentEntity.createReturnShipment(
                orderId, caseId, "CARRIER", "GHN", "GHN-RETURN-001", fixedNow
        );
        createdShipment.setId(901L);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(caseRepository.findByIdForUpdate(caseId)).thenReturn(Optional.of(returnCase));
        when(caseRoundRepository.findLatestRoundByCaseId(caseId)).thenReturn(Optional.of(round1));
        when(shipmentRepository.findByReturnCaseId(caseId)).thenReturn(Optional.empty(), Optional.of(createdShipment));
        when(shipmentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(caseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReturnCaseDtos.SubmitReturnShipmentRequest req = new ReturnCaseDtos.SubmitReturnShipmentRequest(
                "CARRIER", "GHN", "GHN-RETURN-001", "CMD_RETURN_SHIP_1"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse resp = service.submitReturnShipment(buyerId, caseId, req);

        assertThat(resp.state()).isEqualTo(CaseEntity.STATE_RETURNING);
        assertThat(resp.returnShipment()).isNotNull();
        assertThat(resp.returnShipment().deliveryMethod()).isEqualTo("CARRIER");
        assertThat(resp.returnShipment().carrier()).isEqualTo("GHN");
        assertThat(resp.returnShipment().trackingNumber()).isEqualTo("GHN-RETURN-001");
        verify(shipmentRepository).saveAndFlush(any(ShipmentEntity.class));
        verify(caseEventRepository).save(any(CaseEventEntity.class));
    }

    @Test
    @DisplayName("Task 3.1: submitReturnShipment - throws IllegalArgumentException if commandKey is missing")
    void submitReturnShipment_missingCommandKey_throwsIllegalArgumentException() {
        Long caseId = 501L;
        ReturnCaseDtos.SubmitReturnShipmentRequest req = new ReturnCaseDtos.SubmitReturnShipmentRequest(
                "CARRIER", "GHN", "GHN-001", "  "
        );

        assertThatThrownBy(() -> service.submitReturnShipment(buyerId, caseId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("commandKey không được để trống");
    }

    @Test
    @DisplayName("Task 3.1: submitReturnShipment - throws IllegalStateException if case is not RETURN_APPROVED")
    void submitReturnShipment_wrongState_throwsIllegalStateException() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_OPEN);

        OrderEntity order = createValidDeliveredOrder();

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(caseRepository.findByIdForUpdate(caseId)).thenReturn(Optional.of(returnCase));

        ReturnCaseDtos.SubmitReturnShipmentRequest req = new ReturnCaseDtos.SubmitReturnShipmentRequest(
                "CARRIER", "GHN", "GHN-001", "CMD_123"
        );

        assertThatThrownBy(() -> service.submitReturnShipment(buyerId, caseId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RETURN_APPROVED");
    }

    @Test
    @DisplayName("Task 3.1: submitReturnShipment - throws SecurityException if caller is not the buyer")
    void submitReturnShipment_unauthorizedActor_throwsSecurityException() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_RETURN_APPROVED);

        OrderEntity order = createValidDeliveredOrder();

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        Long strangerId = 9999L;
        ReturnCaseDtos.SubmitReturnShipmentRequest req = new ReturnCaseDtos.SubmitReturnShipmentRequest(
                "CARRIER", "GHN", "GHN-001", "CMD_123"
        );

        assertThatThrownBy(() -> service.submitReturnShipment(strangerId, caseId, req))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Chỉ người mua");
    }

    @Test
    @DisplayName("Task 3.1: submitReturnShipment - idempotent replay with identical payload succeeds")
    void submitReturnShipment_idempotentReplay() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_RETURN_APPROVED);

        OrderEntity order = createValidDeliveredOrder();

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(caseRepository.findByIdForUpdate(caseId)).thenReturn(Optional.of(returnCase));

        CaseEventEntity existingEvent = new CaseEventEntity(
                UUID.randomUUID(), caseId, 1L, buyerId, sellerId,
                CaseEventEntity.TYPE_SUBMITTED, "Reason",
                "{\"delivery_method\":\"CARRIER\",\"carrier\":\"GHN\",\"tracking_number\":\"GHN-001\"}",
                "CMD_SAME"
        );
        when(caseEventRepository.findByCommandKey("CMD_SAME")).thenReturn(Optional.of(existingEvent));

        ReturnCaseDtos.SubmitReturnShipmentRequest req = new ReturnCaseDtos.SubmitReturnShipmentRequest(
                "CARRIER", "GHN", "GHN-001", "CMD_SAME"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse resp = service.submitReturnShipment(buyerId, caseId, req);
        assertThat(resp).isNotNull();
        verify(shipmentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Task 3.1: submitReturnShipment - throws IllegalStateException if same commandKey used with different payload")
    void submitReturnShipment_conflictPayload() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_RETURN_APPROVED);

        OrderEntity order = createValidDeliveredOrder();

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(caseRepository.findByIdForUpdate(caseId)).thenReturn(Optional.of(returnCase));

        CaseEventEntity existingEvent = new CaseEventEntity(
                UUID.randomUUID(), caseId, 1L, buyerId, sellerId,
                CaseEventEntity.TYPE_SUBMITTED, "Reason",
                "{\"delivery_method\":\"CARRIER\"}",
                "CMD_CONFLICT"
        );
        when(caseEventRepository.findByCommandKey("CMD_CONFLICT")).thenReturn(Optional.of(existingEvent));

        ReturnCaseDtos.SubmitReturnShipmentRequest req = new ReturnCaseDtos.SubmitReturnShipmentRequest(
                "SELF_DELIVERY", null, null, "CMD_CONFLICT"
        );

        assertThatThrownBy(() -> service.submitReturnShipment(buyerId, caseId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Conflict: commandKey đã được sử dụng với payload khác");
    }

    @Test
    @DisplayName("Task 3.1: submitReturnShipment - throws IllegalStateException if same commandKey used for different actor or case")
    void submitReturnShipment_conflictActorOrCase() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_RETURN_APPROVED);

        OrderEntity order = createValidDeliveredOrder();

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(caseRepository.findByIdForUpdate(caseId)).thenReturn(Optional.of(returnCase));

        CaseEventEntity existingEvent = new CaseEventEntity(
                UUID.randomUUID(), 999L, 1L, 888L, sellerId,
                CaseEventEntity.TYPE_SUBMITTED, "Reason",
                "{\"delivery_method\":\"CARRIER\"}",
                "CMD_DIFF_CASE"
        );
        when(caseEventRepository.findByCommandKey("CMD_DIFF_CASE")).thenReturn(Optional.of(existingEvent));

        ReturnCaseDtos.SubmitReturnShipmentRequest req = new ReturnCaseDtos.SubmitReturnShipmentRequest(
                "CARRIER", null, null, "CMD_DIFF_CASE"
        );

        assertThatThrownBy(() -> service.submitReturnShipment(buyerId, caseId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Conflict: commandKey đã được sử dụng cho caseId hoặc actorId khác");

        assertThatThrownBy(() -> service.submitReturnShipment(null, caseId, req))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Chưa xác thực danh tính");
    }

    @Test
    @DisplayName("Task 3.1: confirmReturnDeliveryDirect - single party confirmation returns false, case stays in RETURNING")
    void confirmReturnDeliveryDirect_singleParty_staysInReturning() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_RETURNING);

        OrderEntity order = createValidDeliveredOrder();
        ShipmentEntity returnShipment = ShipmentEntity.createReturnShipment(orderId, caseId, "SELF_DELIVERY", null, null, fixedNow);
        returnShipment.setId(801L);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByReturnCaseIdForUpdate(caseId)).thenReturn(Optional.of(returnShipment));
        when(caseRepository.findByIdForUpdate(caseId)).thenReturn(Optional.of(returnCase));
        when(jdbcTemplate.queryForObject(eq("SELECT og_confirm_direct_handover(?)"), eq(Boolean.class), any()))
                .thenReturn(Boolean.FALSE);

        ReturnCaseDtos.ConfirmReturnDeliveryRequest req = new ReturnCaseDtos.ConfirmReturnDeliveryRequest(
                fixedNow, "Buyer delivered", "CMD_CONFIRM_BUYER"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse resp = service.confirmReturnDeliveryDirect(buyerId, caseId, req);

        assertThat(resp.state()).isEqualTo(CaseEntity.STATE_RETURNING);
        verify(caseRoundRepository, never()).save(any());

        // Verify null actor fails immediately
        assertThatThrownBy(() -> service.confirmReturnDeliveryDirect(null, caseId, req))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Chưa xác thực danh tính");
    }

    @Test
    @DisplayName("Task 3.1: confirmReturnDeliveryDirect - guard clauses against CARRIER and invalid case states without side effects")
    void confirmReturnDeliveryDirect_guardClauses() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_RETURNING);

        OrderEntity order = createValidDeliveredOrder();
        ShipmentEntity carrierShipment = ShipmentEntity.createReturnShipment(orderId, caseId, "CARRIER", "GHN", "GHN-RET-123", fixedNow);
        carrierShipment.setId(801L);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByReturnCaseIdForUpdate(caseId)).thenReturn(Optional.of(carrierShipment));

        ReturnCaseDtos.ConfirmReturnDeliveryRequest req = new ReturnCaseDtos.ConfirmReturnDeliveryRequest(
                fixedNow, "Buyer delivered", "CMD_GUARD_1"
        );

        // 1. Guard against CARRIER shipment method
        assertThatThrownBy(() -> service.confirmReturnDeliveryDirect(buyerId, caseId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không thể xác nhận bàn giao trực tiếp cho vận đơn qua hãng vận chuyển (CARRIER)");

        // 2. Guard against invalid case states (e.g. STATE_OPEN)
        carrierShipment.setDeliveryMethod(ShipmentEntity.DELIVERY_METHOD_SELF_DELIVERY);
        returnCase.setState(CaseEntity.STATE_OPEN);
        when(caseRepository.findByIdForUpdate(caseId)).thenReturn(Optional.of(returnCase));

        assertThatThrownBy(() -> service.confirmReturnDeliveryDirect(buyerId, caseId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Trạng thái khiếu nại không hợp lệ để xác nhận bàn giao hàng trả");

        // Verify NO side effects: no handover confirmation inserted and no DB function invoked
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        verify(jdbcTemplate, never()).queryForObject(eq("SELECT og_confirm_direct_handover(?)"), eq(Boolean.class), any());
    }

    @Test
    @DisplayName("Task 3.1: confirmReturnDeliveryDirect - both parties confirmation returns true, transitions to POST_RETURN and opens Round 2 with round2Id")
    void confirmReturnDeliveryDirect_bothParties_transitionsToPostReturn() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_RETURNING);

        OrderEntity order = createValidDeliveredOrder();
        ShipmentEntity returnShipment = ShipmentEntity.createReturnShipment(orderId, caseId, "SELF_DELIVERY", null, null, fixedNow);
        returnShipment.setId(801L);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByReturnCaseIdForUpdate(caseId)).thenReturn(Optional.of(returnShipment));
        when(caseRepository.findByIdForUpdate(caseId)).thenReturn(Optional.of(returnCase));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.empty());
        CaseRoundEntity savedRound2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        savedRound2.setId(602L);
        when(caseRoundRepository.save(any())).thenReturn(savedRound2);
        when(jdbcTemplate.queryForObject(eq("SELECT og_confirm_direct_handover(?)"), eq(Boolean.class), any()))
                .thenReturn(Boolean.TRUE);
        doAnswer(inv -> {
            returnCase.setState(CaseEntity.STATE_POST_RETURN);
            returnCase.setValidReturnedAt(fixedNow);
            return null;
        }).when(entityManager).refresh(returnCase);

        ReturnCaseDtos.ConfirmReturnDeliveryRequest req = new ReturnCaseDtos.ConfirmReturnDeliveryRequest(
                fixedNow.plus(Duration.ofDays(10)), "Seller received", "CMD_CONFIRM_SELLER"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse resp = service.confirmReturnDeliveryDirect(sellerId, caseId, req);

        assertThat(resp.state()).isEqualTo(CaseEntity.STATE_POST_RETURN);
        assertThat(resp.validReturnedAt()).isNotNull();
        verify(caseRoundRepository).save(argThat(r -> r.getRoundNo() == 2 && CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION.equals(r.getStage())));
        // Verify Round 2 Event uses round2.getId() (602L), NOT hardcoded 2L
        verify(caseEventRepository).save(argThat(ev -> Long.valueOf(602L).equals(ev.getRoundId())));
    }

    @Test
    @DisplayName("Task 3.1: confirmReturnDeliveryDirect - succeeds when case is in RETURN_APPROVED state")
    void confirmReturnDeliveryDirect_returnApprovedState_succeeds() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_RETURN_APPROVED);

        OrderEntity order = createValidDeliveredOrder();
        ShipmentEntity returnShipment = ShipmentEntity.createReturnShipment(orderId, caseId, "SELF_DELIVERY", null, null, fixedNow);
        returnShipment.setId(801L);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByReturnCaseIdForUpdate(caseId)).thenReturn(Optional.of(returnShipment));
        when(caseRepository.findByIdForUpdate(caseId)).thenReturn(Optional.of(returnCase));
        when(jdbcTemplate.queryForObject(eq("SELECT og_confirm_direct_handover(?)"), eq(Boolean.class), any()))
                .thenReturn(Boolean.FALSE);

        ReturnCaseDtos.ConfirmReturnDeliveryRequest req = new ReturnCaseDtos.ConfirmReturnDeliveryRequest(
                fixedNow, "Buyer handed over", "CMD_CONFIRM_APPROVED"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse resp = service.confirmReturnDeliveryDirect(buyerId, caseId, req);

        // Case was in RETURN_APPROVED, single party confirmed -> should not throw IllegalStateException, guard satisfied!
        assertThat(resp).isNotNull();
        assertThat(resp.state()).isEqualTo(CaseEntity.STATE_RETURN_APPROVED);
    }

    @Test
    @DisplayName("Task 3.1: fail-fast when shipmentRepository is null in submit, confirmDirect, confirmCarrier")
    void failFast_whenShipmentRepositoryIsNull() {
        ReturnCaseService serviceWithoutShipmentRepo = new ReturnCaseService(
                caseRepository, caseRoundRepository, caseEvidenceRepository, caseDecisionRepository,
                caseEventRepository, orderRepository, paymentRepository, null,
                entityManager, jdbcTemplate, null, clock
        );

        ReturnCaseDtos.SubmitReturnShipmentRequest submitReq = new ReturnCaseDtos.SubmitReturnShipmentRequest(
                "CARRIER", "GHN", "TRACK123", "CMD_1"
        );
        assertThatThrownBy(() -> serviceWithoutShipmentRepo.submitReturnShipment(buyerId, 501L, submitReq))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Chưa cấu hình kho dữ liệu vận đơn (shipmentRepository)");

        ReturnCaseDtos.ConfirmReturnDeliveryRequest confirmReq = new ReturnCaseDtos.ConfirmReturnDeliveryRequest(
                fixedNow, "Note", "CMD_2"
        );
        assertThatThrownBy(() -> serviceWithoutShipmentRepo.confirmReturnDeliveryDirect(buyerId, 501L, confirmReq))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Chưa cấu hình kho dữ liệu vận đơn (shipmentRepository)");

        assertThatThrownBy(() -> serviceWithoutShipmentRepo.confirmReturnDeliveryByCarrier(501L, 801L, fixedNow))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Chưa cấu hình kho dữ liệu vận đơn (shipmentRepository)");
    }

    @Test
    @DisplayName("Task 3.1: confirmReturnDeliveryDirect - fail-fast when jdbcTemplate is null")
    void confirmReturnDeliveryDirect_failFast_whenJdbcTemplateIsNull() {
        ReturnCaseService serviceWithoutJdbc = new ReturnCaseService(
                caseRepository,
                caseRoundRepository,
                caseEvidenceRepository,
                caseDecisionRepository,
                caseEventRepository,
                orderRepository,
                paymentRepository,
                shipmentRepository,
                entityManager,
                null,
                null,
                clock
        );

        ReturnCaseDtos.ConfirmReturnDeliveryRequest confirmReq = new ReturnCaseDtos.ConfirmReturnDeliveryRequest(
                fixedNow, "Bàn giao trực tiếp", "CMD_CONFIRM_FAILFAST_JDBC"
        );

        assertThatThrownBy(() -> serviceWithoutJdbc.confirmReturnDeliveryDirect(buyerId, 501L, confirmReq))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jdbcTemplate");

        // Xác minh không có side effect hoặc truy vấn repository không cần thiết
        verifyNoInteractions(caseRepository);
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(shipmentRepository);
        verifyNoInteractions(caseRoundRepository);
        verifyNoInteractions(caseEventRepository);
    }

    @Test
    @DisplayName("Task 3.1: confirmReturnDeliveryByCarrier - carrier delivery transitions case to POST_RETURN and opens Round 2 with round2Id")
    void confirmReturnDeliveryByCarrier_success() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_RETURNING);

        OrderEntity order = createValidDeliveredOrder();
        ShipmentEntity returnShipment = ShipmentEntity.createReturnShipment(orderId, caseId, "CARRIER", "GHN", "GHN-RET-123", fixedNow);
        returnShipment.setId(802L);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByReturnCaseIdForUpdate(caseId)).thenReturn(Optional.of(returnShipment));
        when(caseRepository.findByIdForUpdate(caseId)).thenReturn(Optional.of(returnCase));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.empty());
        CaseRoundEntity savedRound2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        savedRound2.setId(602L);
        when(caseRoundRepository.save(any())).thenReturn(savedRound2);
        when(caseRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        Instant deliveredAt = fixedNow.plus(Duration.ofHours(48));
        ReturnCaseDtos.ReturnCaseDetailResponse resp = service.confirmReturnDeliveryByCarrier(caseId, returnShipment.getId(), deliveredAt);

        assertThat(resp.state()).isEqualTo(CaseEntity.STATE_POST_RETURN);
        assertThat(resp.validReturnedAt()).isEqualTo(deliveredAt);
        assertThat(resp.sellerResponseDueAt()).isEqualTo(deliveredAt.plus(Duration.ofDays(2)));
        assertThat(returnShipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_DELIVERED);
        assertThat(returnShipment.getValidDeliveryAt()).isEqualTo(deliveredAt);
        verify(caseRoundRepository).save(argThat(r -> r.getRoundNo() == 2 && r.getOpenedAt().equals(deliveredAt)));
        // Verify Round 2 Event uses round2.getId() (602L), NOT hardcoded 2L
        verify(caseEventRepository).save(argThat(ev -> Long.valueOf(602L).equals(ev.getRoundId())));
    }

    @Test
    @DisplayName("Task 3.1: confirmReturnDeliveryByCarrier - validations on shipmentId, properties, and case state")
    void confirmReturnDeliveryByCarrier_validations() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_RETURNING);

        OrderEntity order = createValidDeliveredOrder();
        ShipmentEntity returnShipment = ShipmentEntity.createReturnShipment(orderId, caseId, "CARRIER", "GHN", "GHN-RET-123", fixedNow);
        returnShipment.setId(802L);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByReturnCaseIdForUpdate(caseId)).thenReturn(Optional.of(returnShipment));
        when(caseRepository.findByIdForUpdate(caseId)).thenReturn(Optional.of(returnCase));

        // 1. Mismatched shipmentId
        assertThatThrownBy(() -> service.confirmReturnDeliveryByCarrier(caseId, 9999L, fixedNow))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không khớp với vận đơn");

        // 2. Invalid shipment leg
        returnShipment.setLeg("OUTBOUND");
        assertThatThrownBy(() -> service.confirmReturnDeliveryByCarrier(caseId, returnShipment.getId(), fixedNow))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Vận đơn không thuộc luồng trả hàng");
        returnShipment.setLeg(ShipmentEntity.LEG_RETURN);

        // 3. Invalid case state
        returnCase.setState(CaseEntity.STATE_OPEN);
        assertThatThrownBy(() -> service.confirmReturnDeliveryByCarrier(caseId, returnShipment.getId(), fixedNow))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Trạng thái khiếu nại không hợp lệ");
    }

    @Test
    @DisplayName("Task 3.1: getReturnShipmentByCaseId - allows buyer, seller, KTV, and Admin; denies stranger, null actor, and jdbcTemplate null")
    void getReturnShipmentByCaseId_tests() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_1");
        returnCase.setId(caseId);

        OrderEntity order = createValidDeliveredOrder();
        ShipmentEntity returnShipment = ShipmentEntity.createReturnShipment(orderId, caseId, "CARRIER", "GHN", "GHN-RET-123", fixedNow);
        returnShipment.setId(802L);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByReturnCaseId(caseId)).thenReturn(Optional.of(returnShipment));

        // 1. Buyer query -> success
        ReturnCaseDtos.ReturnShipmentDetailResponse buyerResp = service.getReturnShipmentByCaseId(buyerId, caseId);
        assertThat(buyerResp.shipmentId()).isEqualTo(802L);

        // 2. Seller query -> success
        ReturnCaseDtos.ReturnShipmentDetailResponse sellerResp = service.getReturnShipmentByCaseId(sellerId, caseId);
        assertThat(sellerResp.shipmentId()).isEqualTo(802L);

        // 3. KTV query -> role check returns true -> success
        Long ktvId = 8881L;
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV') OR og_has_role(?, 'ADMIN')"), eq(Boolean.class), eq(ktvId), eq(ktvId)))
                .thenReturn(Boolean.TRUE);
        ReturnCaseDtos.ReturnShipmentDetailResponse ktvResp = service.getReturnShipmentByCaseId(ktvId, caseId);
        assertThat(ktvResp.shipmentId()).isEqualTo(802L);

        // 4. Admin query -> role check returns true -> success
        Long adminId = 8882L;
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV') OR og_has_role(?, 'ADMIN')"), eq(Boolean.class), eq(adminId), eq(adminId)))
                .thenReturn(Boolean.TRUE);
        ReturnCaseDtos.ReturnShipmentDetailResponse adminResp = service.getReturnShipmentByCaseId(adminId, caseId);
        assertThat(adminResp.shipmentId()).isEqualTo(802L);

        // 5. Null actor -> throws SecurityException
        assertThatThrownBy(() -> service.getReturnShipmentByCaseId(null, caseId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Chưa xác thực danh tính");

        // 6. Stranger query -> role check returns false -> throws SecurityException
        Long strangerId = 9999L;
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV') OR og_has_role(?, 'ADMIN')"), eq(Boolean.class), eq(strangerId), eq(strangerId)))
                .thenReturn(Boolean.FALSE);
        assertThatThrownBy(() -> service.getReturnShipmentByCaseId(strangerId, caseId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Người dùng không có quyền xem thông tin vận chuyển của khiếu nại này");

        // 7. DB error during role check -> fail-closed -> throws SecurityException
        Long dbErrUser = 7777L;
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV') OR og_has_role(?, 'ADMIN')"), eq(Boolean.class), eq(dbErrUser), eq(dbErrUser)))
                .thenThrow(new RuntimeException("DB Connection Timeout"));
        assertThatThrownBy(() -> service.getReturnShipmentByCaseId(dbErrUser, caseId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Người dùng không có quyền xem thông tin vận chuyển của khiếu nại này");

        // 8. Service with null jdbcTemplate -> fail-closed -> throws SecurityException for non-buyer/seller
        ReturnCaseService serviceWithoutJdbc = new ReturnCaseService(
                caseRepository, caseRoundRepository, caseEvidenceRepository, caseDecisionRepository,
                caseEventRepository, orderRepository, paymentRepository, shipmentRepository,
                entityManager, null, null, clock
        );
        assertThatThrownBy(() -> serviceWithoutJdbc.getReturnShipmentByCaseId(strangerId, caseId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Người dùng không có quyền xem thông tin vận chuyển của khiếu nại này");
    }
}
