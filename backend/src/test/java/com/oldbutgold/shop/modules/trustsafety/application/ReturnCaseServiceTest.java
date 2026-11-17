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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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

    @Mock
    private RoundDecisionSettlementPort settlementPort;

    @Mock
    private CaseEvidenceRequestRepository caseEvidenceRequestRepository;

    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private Clock clock;
    private Instant fixedNow;
    private ReturnCaseService service;

    private Long buyerId = 100L;
    private Long sellerId = 200L;
    private Long orderId = 1001L;
    private Long ktvId = 300L;

    @BeforeEach
    void setUp() {
        fixedNow = Instant.parse("2026-10-31T10:00:00Z");
        clock = Clock.fixed(fixedNow, ZoneId.of("UTC"));

        org.springframework.transaction.TransactionStatus txStatus = mock(org.springframework.transaction.TransactionStatus.class);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(txStatus);

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
                null,
                clock,
                null,
                settlementPort,
                transactionManager,
                caseEvidenceRequestRepository
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
        returnCase.setAssignedTo(ktvId);

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
        returnCase.setAssignedTo(ktvId);

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
        returnCase.setAssignedTo(ktvId);

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

        // Verify event BUYER_WITHDRAWN recorded
        ArgumentCaptor<CaseEventEntity> eventCaptor = ArgumentCaptor.forClass(CaseEventEntity.class);
        verify(caseEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(CaseEventEntity.TYPE_BUYER_WITHDRAWN);
        assertThat(eventCaptor.getValue().getActorId()).isEqualTo(buyerId);

        // Verify money_holds closed
        verify(jdbcTemplate).update(contains("UPDATE og_compat.money_holds SET closed_at"), any(java.sql.Timestamp.class), eq(caseId));
    }

    @Test
    @DisplayName("withdrawReturnRequest: Idempotent replay when called again by same buyer")
    void withdrawReturnRequest_idempotentReplay_sameBuyer() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "[CHANGE_MIND] Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_CLOSED);
        returnCase.setClosedAt(fixedNow);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round = new CaseRoundEntity(caseId, 1, CaseRoundEntity.STAGE_RETURN_APPROVAL);
        round.setId(601L);
        round.setState(CaseRoundEntity.STATE_DECIDED);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findLatestRoundByCaseId(caseId)).thenReturn(Optional.of(round));

        CaseEventEntity existingWithdrawEvent = new CaseEventEntity(
                UUID.randomUUID(),
                caseId,
                null,
                buyerId,
                sellerId,
                CaseEventEntity.TYPE_BUYER_WITHDRAWN,
                "Người mua đã chủ động rút yêu cầu",
                "{\"action\":\"WITHDRAW\"}",
                "EVENT:WITHDRAW:" + caseId
        );
        when(caseEventRepository.findByCommandKey("EVENT:WITHDRAW:" + caseId)).thenReturn(Optional.of(existingWithdrawEvent));

        ReturnCaseDtos.WithdrawReturnRequest request = new ReturnCaseDtos.WithdrawReturnRequest(
                "Thử rút lại lần 2",
                "CMD_WITHDRAW_RETRY"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse response = service.withdrawReturnRequest(buyerId, caseId, request);

        assertThat(response).isNotNull();
        assertThat(response.state()).isEqualTo(CaseEntity.STATE_CLOSED);
        // Verify no new event saved and no money_holds update executed
        verify(caseEventRepository, never()).save(any());
        verify(jdbcTemplate, never()).update(contains("UPDATE og_compat.money_holds SET closed_at"), any(java.sql.Timestamp.class), any());
    }

    @Test
    @DisplayName("withdrawReturnRequest: Reject replay if called by different user")
    void withdrawReturnRequest_replay_rejectDifferentUser() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "[CHANGE_MIND] Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_CLOSED);

        OrderEntity order = createValidDeliveredOrder();
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        CaseEventEntity existingWithdrawEvent = new CaseEventEntity(
                UUID.randomUUID(),
                caseId,
                null,
                buyerId,
                sellerId,
                CaseEventEntity.TYPE_BUYER_WITHDRAWN,
                "Người mua đã chủ động rút yêu cầu",
                "{\"action\":\"WITHDRAW\"}",
                "EVENT:WITHDRAW:" + caseId
        );
        when(caseEventRepository.findByCommandKey("EVENT:WITHDRAW:" + caseId)).thenReturn(Optional.of(existingWithdrawEvent));

        Long attackerId = 999L;
        assertThatThrownBy(() -> service.withdrawReturnRequest(attackerId, caseId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chỉ người mua tạo yêu cầu");
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
                entityManager, jdbcTemplate, null, clock, null, settlementPort, transactionManager,
                caseEvidenceRequestRepository
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
                clock,
                null,
                settlementPort,
                transactionManager,
                caseEvidenceRequestRepository
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
                entityManager, null, null, clock, null, settlementPort, transactionManager,
                caseEvidenceRequestRepository
        );
        assertThatThrownBy(() -> serviceWithoutJdbc.getReturnShipmentByCaseId(strangerId, caseId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Người dùng không có quyền xem thông tin vận chuyển của khiếu nại này");
    }

    @Test
    @DisplayName("Timeout Worker: scanOverduePostReturnCaseIds returns list from repository")
    void scanOverduePostReturnCaseIds_tests() {
        when(caseRepository.findOverduePostReturnCaseIds(any(Instant.class)))
                .thenReturn(List.of(501L, 502L));

        List<Long> ids = service.scanOverduePostReturnCaseIds();
        assertThat(ids).containsExactly(501L, 502L);
    }

    @Test
    @DisplayName("Timeout Worker: processPostReturnTimeoutForCase auto-refunds Buyer when seller is overdue without damage claim")
    void processPostReturnTimeoutForCase_success() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofDays(3)));
        returnCase.setSellerResponseDueAt(fixedNow.minus(Duration.ofHours(1))); // Overdue!

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);
        round2.setValidDamageRequestAt(null);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseDecisionRepository.findByCommandKey(anyString())).thenReturn(Optional.empty());
        when(caseEventRepository.findByCommandKey(anyString())).thenReturn(Optional.empty());
        lenient().when(caseRepository.saveAndFlush(any(CaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(caseDecisionRepository.saveAndFlush(any(CaseDecisionEntity.class))).thenAnswer(inv -> {
            CaseDecisionEntity d = inv.getArgument(0);
            d.setId(888L);
            return d;
        });

        ReturnCaseDtos.ReturnCaseDetailResponse response = service.processPostReturnTimeoutForCase(caseId);

        assertThat(response).isNotNull();
        assertThat(returnCase.getState()).isEqualTo(CaseEntity.STATE_CLOSED);
        assertThat(returnCase.getClosedAt()).isEqualTo(fixedNow);
        assertThat(round2.getState()).isEqualTo(CaseRoundEntity.STATE_DECIDED);

        ArgumentCaptor<CaseDecisionEntity> decisionCaptor = ArgumentCaptor.forClass(CaseDecisionEntity.class);
        verify(caseDecisionRepository).saveAndFlush(decisionCaptor.capture());
        CaseDecisionEntity savedDecision = decisionCaptor.getValue();
        assertThat(savedDecision.getActorType()).isEqualTo(CaseDecisionEntity.ACTOR_SYSTEM_TIMEOUT);
        assertThat(savedDecision.getOutcome()).isEqualTo(CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND);
        assertThat(savedDecision.getReviewedBy()).isNull();
        assertThat(savedDecision.getIsFinal()).isTrue();
        assertThat(savedDecision.getGoodsRefundAmount()).isEqualByComparingTo(new BigDecimal("500000.00"));

        ArgumentCaptor<CaseEventEntity> eventCaptor = ArgumentCaptor.forClass(CaseEventEntity.class);
        verify(caseEventRepository).save(eventCaptor.capture());
        CaseEventEntity savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getEventType()).isEqualTo(CaseEventEntity.TYPE_CLEANUP);
    }

    @Test
    @DisplayName("Timeout Worker: processPostReturnTimeoutForCase skips if seller filed valid damage request")
    void processPostReturnTimeoutForCase_skipsIfDamageRequestExists() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setSellerResponseDueAt(fixedNow.minus(Duration.ofHours(1)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);
        round2.setValidDamageRequestAt(fixedNow.minus(Duration.ofHours(2))); // Damage request present!

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));

        ReturnCaseDtos.ReturnCaseDetailResponse response = service.processPostReturnTimeoutForCase(caseId);

        assertThat(response).isNotNull();
        assertThat(returnCase.getState()).isEqualTo(CaseEntity.STATE_POST_RETURN);
        assertThat(round2.getState()).isEqualTo(CaseRoundEntity.STATE_OPEN);
        verify(caseDecisionRepository, never()).save(any(CaseDecisionEntity.class));
    }

    @Test
    @DisplayName("Timeout Worker: processPostReturnTimeoutForCase skips if deadline not yet passed")
    void processPostReturnTimeoutForCase_skipsIfNotOverdue() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setSellerResponseDueAt(fixedNow.plus(Duration.ofHours(5))); // Not overdue!

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));

        service.processPostReturnTimeoutForCase(caseId);

        verify(caseDecisionRepository, never()).save(any(CaseDecisionEntity.class));
    }

    // =========================================================================
    // FEATURE 3.2: TASK 3.2.1 - SELLER AGREE REFUND (PHASE 1)
    // =========================================================================

    @Test
    @DisplayName("Task 3.2.1: sellerAgreeRefundRound2 - success records decision, requests settlement, and closes case")
    void sellerAgreeRefundRound2_success() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Return requested", "CMD_CASE_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(10)));
        returnCase.setSellerResponseDueAt(fixedNow.plus(Duration.ofHours(38)));

        OrderEntity order = createValidDeliveredOrder(); // subtotal 500k, voucher 0
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseDecisionRepository.findByCommandKey("CMD_AGREE_1")).thenReturn(Optional.empty());
        when(caseDecisionRepository.saveAndFlush(any(CaseDecisionEntity.class))).thenAnswer(invocation -> {
            CaseDecisionEntity d = invocation.getArgument(0);
            d.setId(888L);
            return d;
        });

        ReturnCaseDtos.SellerAgreeRefundRequest req = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Đồng ý hoàn tiền sau khi kiểm tra hàng hóa nguyên vẹn",
                "CMD_AGREE_1"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse response = service.sellerAgreeRefundRound2(sellerId, caseId, req);

        assertThat(response).isNotNull();
        assertThat(returnCase.getState()).isEqualTo(CaseEntity.STATE_CLOSED);
        assertThat(round2.getState()).isEqualTo(CaseRoundEntity.STATE_DECIDED);

        // Verify decision saved with FULL_GOODS_REFUND
        ArgumentCaptor<CaseDecisionEntity> decisionCaptor = ArgumentCaptor.forClass(CaseDecisionEntity.class);
        verify(caseDecisionRepository).saveAndFlush(decisionCaptor.capture());
        CaseDecisionEntity savedDecision = decisionCaptor.getValue();
        assertThat(savedDecision.getOutcome()).isEqualTo(CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND);
        assertThat(savedDecision.getActorType()).isEqualTo(CaseDecisionEntity.ACTOR_USER);
        assertThat(savedDecision.getReviewedBy()).isEqualTo(sellerId);
        assertThat(savedDecision.getIsFinal()).isTrue();
        assertThat(savedDecision.getReason()).contains("Đồng ý hoàn tiền sau khi kiểm tra hàng hóa nguyên vẹn");

        // Verify settlement request called via port (Task 4.1a)
        verify(settlementPort).requestSettlement(
                orderId,
                caseId,
                888L,
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "CMD_AGREE_1"
        );

        // Verify cleanup event recorded
        verify(caseEventRepository).save(any(CaseEventEntity.class));
    }

    @Test
    @DisplayName("Task 3.2.1: sellerAgreeRefundRound2 - rejected when valid_damage_request_at IS NOT NULL (BD-1 Phương án 1)")
    void sellerAgreeRefundRound2_rejected_whenValidDamageRequestExists() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Return requested", "CMD_CASE_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(10)));
        returnCase.setSellerResponseDueAt(fixedNow.plus(Duration.ofHours(38)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);
        // Damage request exists!
        round2.setValidDamageRequestAt(fixedNow.minus(Duration.ofHours(2)));

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));

        ReturnCaseDtos.SellerAgreeRefundRequest req = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Đồng ý hoàn tiền cho người mua",
                "CMD_AGREE_DAMAGE_BLOCKED"
        );

        assertThatThrownBy(() -> service.sellerAgreeRefundRound2(sellerId, caseId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Người bán đã gửi yêu cầu bồi thường thiệt hại");

        verify(caseDecisionRepository, never()).saveAndFlush(any());
        verify(settlementPort, never()).requestSettlement(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Task 3.2.1: sellerAgreeRefundRound2 - rejected when caller is not the seller")
    void sellerAgreeRefundRound2_rejected_whenNotSeller() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Return requested", "CMD_CASE_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);

        OrderEntity order = createValidDeliveredOrder(); // sellerId is 200L
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));

        ReturnCaseDtos.SellerAgreeRefundRequest req = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Đồng ý hoàn tiền hợp lệ",
                "CMD_AGREE_NOT_SELLER"
        );

        Long unauthorizedSeller = 999L;
        assertThatThrownBy(() -> service.sellerAgreeRefundRound2(unauthorizedSeller, caseId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chỉ người bán của đơn hàng mới có quyền đồng ý hoàn tiền");

        verify(caseDecisionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Task 3.2.1: sellerAgreeRefundRound2 - rejected when case not in POST_RETURN")
    void sellerAgreeRefundRound2_rejected_whenNotInPostReturn() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Return requested", "CMD_CASE_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_OPEN); // Not POST_RETURN

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));

        ReturnCaseDtos.SellerAgreeRefundRequest req = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Đồng ý hoàn tiền hợp lệ",
                "CMD_AGREE_BAD_STATE"
        );

        assertThatThrownBy(() -> service.sellerAgreeRefundRound2(sellerId, caseId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hồ sơ không ở trạng thái Vòng 2 sau khi nhận lại hàng");
    }

    @Test
    @DisplayName("Task 3.2.1: sellerAgreeRefundRound2 - rejected when validReturnedAt is null")
    void sellerAgreeRefundRound2_rejected_whenNotReturnedDelivery() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Return requested", "CMD_CASE_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setValidReturnedAt(null); // No valid delivery yet!

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));

        ReturnCaseDtos.SellerAgreeRefundRequest req = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Đồng ý hoàn tiền hợp lệ",
                "CMD_AGREE_NO_DELIVERY"
        );

        assertThatThrownBy(() -> service.sellerAgreeRefundRound2(sellerId, caseId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Chưa có xác nhận hàng đã được hoàn trả hợp lệ cho người bán");
    }

    @Test
    @DisplayName("Task 3.2.1: sellerAgreeRefundRound2 - rejected when round 2 is already decided")
    void sellerAgreeRefundRound2_rejected_whenRoundNotOpen() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Return requested", "CMD_CASE_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(10)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_DECIDED); // Already decided

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));

        ReturnCaseDtos.SellerAgreeRefundRequest req = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Đồng ý hoàn tiền hợp lệ",
                "CMD_AGREE_ROUND_DECIDED"
        );

        assertThatThrownBy(() -> service.sellerAgreeRefundRound2(sellerId, caseId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Vòng 2 đã kết thúc");
    }

    @Test
    @DisplayName("Task 3.2.1: sellerAgreeRefundRound2 - rejected when seller_response_due_at has passed")
    void sellerAgreeRefundRound2_rejected_whenOverdue() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Return requested", "CMD_CASE_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofDays(3)));
        returnCase.setSellerResponseDueAt(fixedNow.minus(Duration.ofHours(1))); // Overdue!

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));

        ReturnCaseDtos.SellerAgreeRefundRequest req = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Đồng ý hoàn tiền nhưng đã quá hạn",
                "CMD_AGREE_OVERDUE"
        );

        assertThatThrownBy(() -> service.sellerAgreeRefundRound2(sellerId, caseId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Đã quá thời hạn 2 ngày phản hồi của người bán");
    }

    @Test
    @DisplayName("Task 3.2.1: sellerAgreeRefundRound2 - rejected when reason is shorter than 5 characters")
    void sellerAgreeRefundRound2_rejected_whenReasonTooShort() {
        Long caseId = 501L;
        ReturnCaseDtos.SellerAgreeRefundRequest req = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "OK  ",
                "CMD_AGREE_SHORT_REASON"
        );

        assertThatThrownBy(() -> service.sellerAgreeRefundRound2(sellerId, caseId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lý do đồng ý hoàn tiền bắt buộc có tối thiểu 5 ký tự");

        verifyNoInteractions(caseRepository);
        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("Task 3.2.1: sellerAgreeRefundRound2 - idempotent replay skips duplicate writes and returns existing state")
    void sellerAgreeRefundRound2_idempotentReplay() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Return requested", "CMD_CASE_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(10)));
        returnCase.setSellerResponseDueAt(fixedNow.plus(Duration.ofHours(38)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        CaseDecisionEntity existingDecision = new CaseDecisionEntity(
                round2.getId(),
                sellerId,
                CaseDecisionEntity.ACTOR_USER,
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                true,
                new BigDecimal("500000.00"),
                "Đồng ý hoàn tiền hợp lệ",
                "CMD_EXISTING_AGREE"
        );

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseDecisionRepository.findByCommandKey("CMD_EXISTING_AGREE")).thenReturn(Optional.of(existingDecision));

        ReturnCaseDtos.SellerAgreeRefundRequest req = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Đồng ý hoàn tiền hợp lệ",
                "CMD_EXISTING_AGREE"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse response = service.sellerAgreeRefundRound2(sellerId, caseId, req);

        assertThat(response).isNotNull();
        verify(caseDecisionRepository, never()).saveAndFlush(any());
        verify(settlementPort, never()).requestSettlement(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Task 3.2.1: sellerAgreeRefundRound2 - idempotent replay succeeds even when case is already CLOSED and round is DECIDED")
    void sellerAgreeRefundRound2_idempotentReplay_whenAlreadyClosed() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Return requested", "CMD_CASE_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_CLOSED);
        returnCase.setClosedAt(fixedNow.minus(Duration.ofHours(2)));
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(10)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_DECIDED);

        CaseDecisionEntity existingDecision = new CaseDecisionEntity(
                round2.getId(),
                sellerId,
                CaseDecisionEntity.ACTOR_USER,
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                true,
                new BigDecimal("500000.00"),
                "Đồng ý hoàn tiền hợp lệ",
                "CMD_EXISTING_AGREE_2"
        );

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseDecisionRepository.findByCommandKey("CMD_EXISTING_AGREE_2")).thenReturn(Optional.of(existingDecision));

        ReturnCaseDtos.SellerAgreeRefundRequest req = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Đồng ý hoàn tiền hợp lệ",
                "CMD_EXISTING_AGREE_2"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse response = service.sellerAgreeRefundRound2(sellerId, caseId, req);

        assertThat(response).isNotNull();
        assertThat(response.state()).isEqualTo(CaseEntity.STATE_CLOSED);
        verify(caseDecisionRepository, never()).saveAndFlush(any());
        verify(settlementPort, never()).requestSettlement(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Task 3.2.1: sellerAgreeRefundRound2 - rejected when commandKey belongs to a different seller")
    void sellerAgreeRefundRound2_idempotentReplay_rejected_whenDifferentSeller() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Return requested", "CMD_CASE_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        CaseDecisionEntity existingDecision = new CaseDecisionEntity(
                round2.getId(),
                999L, // different actor!
                CaseDecisionEntity.ACTOR_USER,
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                true,
                new BigDecimal("500000.00"),
                "Đồng ý hoàn tiền",
                "CMD_EXISTING_DIFF_SELLER"
        );

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseDecisionRepository.findByCommandKey("CMD_EXISTING_DIFF_SELLER")).thenReturn(Optional.of(existingDecision));

        ReturnCaseDtos.SellerAgreeRefundRequest req = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Đồng ý hoàn tiền",
                "CMD_EXISTING_DIFF_SELLER"
        );

        assertThatThrownBy(() -> service.sellerAgreeRefundRound2(sellerId, caseId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CommandKey đã tồn tại nhưng không thuộc về người bán này");
    }

    @Test
    @DisplayName("Task 3.2.1: sellerAgreeRefundRound2 - rejected with 409 COMMAND_KEY_CONFLICT when commandKey matches but payload is different")
    void sellerAgreeRefundRound2_idempotentReplay_rejected_whenDifferentPayload() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Return requested", "CMD_CASE_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        CaseDecisionEntity existingDecision = new CaseDecisionEntity(
                round2.getId(),
                sellerId,
                CaseDecisionEntity.ACTOR_USER,
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                true,
                new BigDecimal("500000.00"),
                "Lý do ban đầu: Đồng ý hoàn tiền",
                "CMD_AGREE_PAYLOAD_DIFF"
        );

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseDecisionRepository.findByCommandKey("CMD_AGREE_PAYLOAD_DIFF")).thenReturn(Optional.of(existingDecision));

        ReturnCaseDtos.SellerAgreeRefundRequest diffPayloadReq = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Lý do khác: Đã đổi ý và thay đổi nội dung hoàn tiền",
                "CMD_AGREE_PAYLOAD_DIFF"
        );

        assertThatThrownBy(() -> service.sellerAgreeRefundRound2(sellerId, caseId, diffPayloadReq))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
                    assertThat(tsEx.getCode()).isEqualTo("COMMAND_KEY_CONFLICT");
                    assertThat(tsEx.getMessage()).contains("Conflict: commandKey đã được sử dụng với payload khác");
                });
        verify(caseDecisionRepository, never()).saveAndFlush(any());
        verify(settlementPort, never()).requestSettlement(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Task 3.2.1: sellerAgreeRefundRound2 - rejected with 409 COMMAND_KEY_CONFLICT when commandKey belongs to a different round/case")
    void sellerAgreeRefundRound2_idempotentReplay_rejected_whenDifferentRound() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Return requested", "CMD_CASE_1");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        CaseDecisionEntity existingDecision = new CaseDecisionEntity(
                888L, // Different round!
                sellerId,
                CaseDecisionEntity.ACTOR_USER,
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                true,
                new BigDecimal("500000.00"),
                "Đồng ý hoàn tiền ở round khác",
                "CMD_AGREE_DIFF_ROUND"
        );

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseDecisionRepository.findByCommandKey("CMD_AGREE_DIFF_ROUND")).thenReturn(Optional.of(existingDecision));

        ReturnCaseDtos.SellerAgreeRefundRequest req = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Đồng ý hoàn tiền ở round khác",
                "CMD_AGREE_DIFF_ROUND"
        );

        assertThatThrownBy(() -> service.sellerAgreeRefundRound2(sellerId, caseId, req))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
                    assertThat(tsEx.getCode()).isEqualTo("COMMAND_KEY_CONFLICT");
                    assertThat(tsEx.getMessage()).contains("CommandKey đã tồn tại nhưng không thuộc về vụ việc này");
                });
        verify(caseDecisionRepository, never()).saveAndFlush(any());
        verify(settlementPort, never()).requestSettlement(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Task 3.2.1: sellerAgreeRefundRound2 - rejected when reason exceeds 2000 characters")
    void sellerAgreeRefundRound2_rejected_whenReasonTooLong() {
        Long caseId = 501L;
        String reason2001 = "A".repeat(2001);
        ReturnCaseDtos.SellerAgreeRefundRequest req = new ReturnCaseDtos.SellerAgreeRefundRequest(
                reason2001,
                "CMD_REASON_TOO_LONG"
        );

        assertThatThrownBy(() -> service.sellerAgreeRefundRound2(sellerId, caseId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lý do đồng ý hoàn tiền tối đa 2000 ký tự");
    }

    // =========================================================================
    // FEATURE 3.2: TASK 3.2.2 - SELLER DAMAGE EXEMPTION REQUEST UNIT TESTS
    // =========================================================================

    @Test
    @DisplayName("Task 3.2.1 Boundary: sellerAgreeRefundRound2 - commandKey boundary 127 chars is valid, 128 chars is rejected")
    void sellerAgreeRefundRound2_boundaryCommandKey_validAndExceeded() {
        Long caseId = 501L;

        // 128 chars -> rejected
        String cmdKey128 = "CMD_" + "A".repeat(124);
        assertThat(cmdKey128.length()).isEqualTo(128);
        ReturnCaseDtos.SellerAgreeRefundRequest req128 = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Đồng ý hoàn tiền với key quá dài",
                cmdKey128
        );
        assertThatThrownBy(() -> service.sellerAgreeRefundRound2(sellerId, caseId, req128))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("commandKey tối đa 127 ký tự");

        // 127 chars -> valid input, proceeds to lock/lookup
        String cmdKey127 = "CMD_" + "A".repeat(123);
        assertThat(cmdKey127.length()).isEqualTo(127);
        ReturnCaseDtos.SellerAgreeRefundRequest req127 = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Đồng ý hoàn tiền với key chuẩn 127 ký tự",
                cmdKey127
        );
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));
        returnCase.setSellerResponseDueAt(fixedNow.plus(Duration.ofHours(40)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseDecisionRepository.findByCommandKey(cmdKey127)).thenReturn(Optional.empty());
        when(caseDecisionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ReturnCaseDtos.ReturnCaseDetailResponse resp = service.sellerAgreeRefundRound2(sellerId, caseId, req127);
        assertThat(resp).isNotNull();
        verify(settlementPort).requestSettlement(eq(orderId), eq(caseId), any(), eq(CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND), eq(cmdKey127));
    }

    private ReturnCaseDtos.EvidenceItemDto createSampleEvidence(String type, String key) {
        try {
            java.nio.file.Path file = java.nio.file.Files.createTempFile("sample-ev-", ".dat");
            file.toFile().deleteOnExit();
            byte[] bytes = ("Sample evidence content for " + key).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            java.nio.file.Files.write(file, bytes);
            String digest = java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            );
            return new ReturnCaseDtos.EvidenceItemDto(
                    type,
                    file.toString(),
                    "Bằng chứng mô tả",
                    digest,
                    (long) bytes.length,
                    CaseEvidenceEntity.MEDIA_VIDEO.equalsIgnoreCase(type) ? 30 : null
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Task 3.2.2: submitDamageExemptionRequest - first-time success records evidence first, then event, and leaves case/round open")
    void submitDamageExemptionRequest_success() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));
        returnCase.setSellerResponseDueAt(fixedNow.plus(Duration.ofHours(40)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));

        String cmdKey = "CMD_DMG_" + UUID.randomUUID();
        when(caseEventRepository.findByCommandKey(cmdKey)).thenReturn(Optional.empty());

        ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Sản phẩm bị nứt vỡ toàn bộ màn hình khi nhận lại",
                List.of(
                        createSampleEvidence("IMAGE", "evidence/img1.jpg"),
                        createSampleEvidence("VIDEO", "evidence/vid1.mp4")
                ),
                cmdKey
        );

        ReturnCaseDtos.ReturnCaseDetailResponse resp = service.submitDamageExemptionRequest(sellerId, caseId, req);

        assertThat(resp).isNotNull();
        // 1. Evidence was saved and flushed before event
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(caseEvidenceRepository, caseEventRepository);
        inOrder.verify(caseEvidenceRepository, org.mockito.Mockito.times(2)).save(any(CaseEvidenceEntity.class));
        inOrder.verify(caseEvidenceRepository).flush();
        inOrder.verify(caseEventRepository).saveAndFlush(any(CaseEventEntity.class));

        // 2. No decision or settlement request created!
        verifyNoInteractions(caseDecisionRepository);
        verifyNoInteractions(settlementPort);

        // 3. Case and round remain OPEN
        assertThat(returnCase.getState()).isEqualTo(CaseEntity.STATE_POST_RETURN);
        assertThat(returnCase.getClosedAt()).isNull();
        assertThat(round2.getState()).isEqualTo(CaseRoundEntity.STATE_OPEN);
        assertThat(round2.getValidDamageRequestAt()).isNotNull();
    }

    @Test
    @DisplayName("Task 3.2.2: submitDamageExemptionRequest - rejected when caller is not the seller of order")
    void submitDamageExemptionRequest_rejected_whenNotSeller() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));

        OrderEntity order = createValidDeliveredOrder(); // sellerId is 1002L
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));

        ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Khiếu nại thiệt hại từ người lạ",
                List.of(createSampleEvidence("IMAGE", "evidence/img1.jpg")),
                "CMD_NOT_SELLER"
        );

        Long impostorId = 9999L;
        assertThatThrownBy(() -> service.submitDamageExemptionRequest(impostorId, caseId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chỉ người bán của đơn hàng mới có quyền gửi yêu cầu bồi thường thiệt hại");

        verify(caseEvidenceRepository, never()).save(any());
        verify(caseEventRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Task 3.2.2: submitDamageExemptionRequest - rejected when case is not in POST_RETURN")
    void submitDamageExemptionRequest_rejected_whenCaseNotPostReturn() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setAssignedTo(ktvId);
        returnCase.setState(CaseEntity.STATE_RETURNING); // not POST_RETURN!

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));

        ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Khiếu nại thiệt hại khi chưa nhận lại hàng",
                List.of(createSampleEvidence("IMAGE", "evidence/img1.jpg")),
                "CMD_NOT_POST_RETURN"
        );

        assertThatThrownBy(() -> service.submitDamageExemptionRequest(sellerId, caseId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hồ sơ không ở trạng thái Vòng 2 sau khi nhận lại hàng");
    }

    @Test
    @DisplayName("Task 3.2.2: submitDamageExemptionRequest - rejected when round 2 is not OPEN")
    void submitDamageExemptionRequest_rejected_whenRoundNotOpen() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_DECIDED); // DECIDED!

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));

        ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Khiếu nại thiệt hại sau khi vòng đã kết thúc",
                List.of(createSampleEvidence("IMAGE", "evidence/img1.jpg")),
                "CMD_ROUND_CLOSED"
        );

        assertThatThrownBy(() -> service.submitDamageExemptionRequest(sellerId, caseId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Vòng 2 đã kết thúc, không thể gửi yêu cầu bồi thường thiệt hại");
    }

    @Test
    @DisplayName("Task 3.2.2: submitDamageExemptionRequest - rejected when valid_returned_at is null")
    void submitDamageExemptionRequest_rejected_whenNoValidReturnedAt() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(null); // null!

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));

        ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Khiếu nại thiệt hại khi chưa có valid_returned_at",
                List.of(createSampleEvidence("IMAGE", "evidence/img1.jpg")),
                "CMD_NO_RETURN_TIME"
        );

        assertThatThrownBy(() -> service.submitDamageExemptionRequest(sellerId, caseId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Chưa có xác nhận hàng đã được hoàn trả hợp lệ cho người bán");
    }

    @Test
    @DisplayName("Task 3.2.2: submitDamageExemptionRequest - rejected when sellerResponseDueAt is passed")
    void submitDamageExemptionRequest_rejected_whenOverdue() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofDays(3)));
        returnCase.setSellerResponseDueAt(fixedNow.minus(Duration.ofHours(1))); // overdue!

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));

        ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Khiếu nại thiệt hại nhưng đã quá hạn 2 ngày",
                List.of(createSampleEvidence("IMAGE", "evidence/img1.jpg")),
                "CMD_DMG_OVERDUE"
        );

        assertThatThrownBy(() -> service.submitDamageExemptionRequest(sellerId, caseId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Đã quá thời hạn 2 ngày phản hồi của người bán");
    }

    @Test
    @DisplayName("Task 3.2.2: submitDamageExemptionRequest - rejected when reason is shorter than 5 characters")
    void submitDamageExemptionRequest_rejected_whenReasonInvalid() {
        Long caseId = 501L;
        ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Hỏng",
                List.of(createSampleEvidence("IMAGE", "evidence/img1.jpg")),
                "CMD_SHORT_REASON"
        );

        assertThatThrownBy(() -> service.submitDamageExemptionRequest(sellerId, caseId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lý do khiếu nại thiệt hại bắt buộc có tối thiểu 5 ký tự");
    }

    @Test
    @DisplayName("Task 3.2.2: submitDamageExemptionRequest - rejected when evidence list is empty or null")
    void submitDamageExemptionRequest_rejected_whenEvidenceEmpty() {
        Long caseId = 501L;

        ReturnCaseDtos.SellerDamageExemptionRequest reqEmpty = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Hàng hóa bị nứt vỡ nghiêm trọng",
                List.of(),
                "CMD_EMPTY_EV"
        );
        assertThatThrownBy(() -> service.submitDamageExemptionRequest(sellerId, caseId, reqEmpty))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bắt buộc phải có ít nhất 1 bằng chứng");

        ReturnCaseDtos.SellerDamageExemptionRequest reqNull = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Hàng hóa bị nứt vỡ nghiêm trọng",
                null,
                "CMD_NULL_EV"
        );
        assertThatThrownBy(() -> service.submitDamageExemptionRequest(sellerId, caseId, reqNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bắt buộc phải có ít nhất 1 bằng chứng");
    }

    @Test
    @DisplayName("Task 3.2.2: submitDamageExemptionRequest - rejected when image count exceeds 5")
    void submitDamageExemptionRequest_rejected_whenImagesExceeded() {
        Long caseId = 501L;
        List<ReturnCaseDtos.EvidenceItemDto> sixImages = List.of(
                createSampleEvidence("IMAGE", "e/1.jpg"),
                createSampleEvidence("IMAGE", "e/2.jpg"),
                createSampleEvidence("IMAGE", "e/3.jpg"),
                createSampleEvidence("IMAGE", "e/4.jpg"),
                createSampleEvidence("IMAGE", "e/5.jpg"),
                createSampleEvidence("IMAGE", "e/6.jpg")
        );
        ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Hàng hóa bị nứt vỡ",
                sixImages,
                "CMD_TOO_MANY_IMAGES"
        );

        assertThatThrownBy(() -> service.submitDamageExemptionRequest(sellerId, caseId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Số lượng ảnh bằng chứng tối đa là 5 ảnh");
    }

    @Test
    @DisplayName("Task 3.2.2: submitDamageExemptionRequest - rejected when video count exceeds 2")
    void submitDamageExemptionRequest_rejected_whenVideosExceeded() {
        Long caseId = 501L;
        List<ReturnCaseDtos.EvidenceItemDto> threeVideos = List.of(
                createSampleEvidence("VIDEO", "e/1.mp4"),
                createSampleEvidence("VIDEO", "e/2.mp4"),
                createSampleEvidence("VIDEO", "e/3.mp4")
        );
        ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Hàng hóa bị nứt vỡ",
                threeVideos,
                "CMD_TOO_MANY_VIDEOS"
        );

        assertThatThrownBy(() -> service.submitDamageExemptionRequest(sellerId, caseId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Số lượng video bằng chứng tối đa là 2 video");
    }

    @Test
    @DisplayName("Task 3.2.2: submitDamageExemptionRequest - rejected when video duration exceeds 60 seconds")
    void submitDamageExemptionRequest_rejected_whenVideoDurationExceeded() {
        Long caseId = 501L;
        ReturnCaseDtos.EvidenceItemDto longVideo = new ReturnCaseDtos.EvidenceItemDto(
                "VIDEO", "e/vid.mp4", "Video bóc hàng",
                "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
                1024L, 61 // 61 seconds > 60!
        );
        ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Hàng hóa bị nứt vỡ",
                List.of(longVideo),
                "CMD_LONG_VIDEO"
        );

        assertThatThrownBy(() -> service.submitDamageExemptionRequest(sellerId, caseId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Thời lượng video bằng chứng tối đa là 60 giây");
    }

    @Test
    @DisplayName("Task 3.2.2: submitDamageExemptionRequest - rejected when video size exceeds 50MB")
    void submitDamageExemptionRequest_rejected_whenVideoSizeExceeded() {
        Long caseId = 501L;
        ReturnCaseDtos.EvidenceItemDto largeVideo = new ReturnCaseDtos.EvidenceItemDto(
                "VIDEO", "e/vid.mp4", "Video bóc hàng",
                "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
                52428801L, // 50MB + 1 byte!
                30
        );
        ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Hàng hóa bị nứt vỡ",
                List.of(largeVideo),
                "CMD_LARGE_VIDEO"
        );

        assertThatThrownBy(() -> service.submitDamageExemptionRequest(sellerId, caseId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dung lượng video bằng chứng tối đa là 50MB");
    }

    @Test
    @DisplayName("Task 3.2.2: submitDamageExemptionRequest - rejected when image size exceeds 5MB")
    void submitDamageExemptionRequest_rejected_whenImageSizeExceeded() {
        Long caseId = 501L;
        ReturnCaseDtos.EvidenceItemDto largeImage = new ReturnCaseDtos.EvidenceItemDto(
                "IMAGE", "e/img.jpg", "Ảnh chụp kiện hàng",
                "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
                5242881L, // 5MB + 1 byte!
                null
        );
        ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Hàng hóa bị nứt vỡ",
                List.of(largeImage),
                "CMD_LARGE_IMAGE"
        );

        assertThatThrownBy(() -> service.submitDamageExemptionRequest(sellerId, caseId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dung lượng ảnh bằng chứng tối đa là 5MB");
    }

    @Test
    @DisplayName("Task 3.2.2: submitDamageExemptionRequest - rejected when round 2 stage is not POST_RETURN_RESOLUTION")
    void submitDamageExemptionRequest_rejected_whenRound2StageNotPostReturnResolution() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_RETURN_APPROVAL); // STAGE_RETURN_APPROVAL!
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));

        ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Khiếu nại thiệt hại khi round2 stage không phải POST_RETURN_RESOLUTION",
                List.of(createSampleEvidence("IMAGE", "evidence/img1.jpg")),
                "CMD_WRONG_STAGE"
        );

        assertThatThrownBy(() -> service.submitDamageExemptionRequest(sellerId, caseId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Vòng 2 không ở giai đoạn giải quyết sau nhận lại hàng");
    }

    // =========================================================================
    // TASK 3.2.2: CONTENT DIGEST VERIFICATION TESTS (7 MANDATORY SCENARIOS)
    // =========================================================================

    @Test
    @DisplayName("Task 3.2.2 ContentDigest 1/7: File local tồn tại và digest khớp -> Thành công")
    void submitDamageExemptionRequest_localFileExists_digestMatches_success() throws Exception {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));
        returnCase.setSellerResponseDueAt(fixedNow.plus(Duration.ofHours(40)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.findByCommandKey("CMD_LOCAL_MATCH")).thenReturn(Optional.empty());

        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("evidence-test-", ".jpg");
        try {
            byte[] fileBytes = "Real damage image content byte stream for verification".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            java.nio.file.Files.write(tempFile, fileBytes);

            String expectedDigest = java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(fileBytes)
            );

            ReturnCaseDtos.EvidenceItemDto evidence = new ReturnCaseDtos.EvidenceItemDto(
                    "IMAGE", tempFile.toString(), "Ảnh kiện hàng", expectedDigest, (long) fileBytes.length, null
            );
            ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                    "Hàng hóa bị nứt vỡ",
                    List.of(evidence),
                    "CMD_LOCAL_MATCH"
            );

            ReturnCaseDtos.ReturnCaseDetailResponse resp = service.submitDamageExemptionRequest(sellerId, caseId, req);
            assertThat(resp).isNotNull();

            ArgumentCaptor<CaseEvidenceEntity> evCaptor = ArgumentCaptor.forClass(CaseEvidenceEntity.class);
            verify(caseEvidenceRepository).save(evCaptor.capture());
            assertThat(evCaptor.getValue().getContentDigest()).isEqualTo(expectedDigest);
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("Task 3.2.2 ContentDigest 2/7: File local tồn tại nhưng digest sai (tampered) -> Từ chối")
    void submitDamageExemptionRequest_localFileExists_digestMismatch_throwsException() throws Exception {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));
        returnCase.setSellerResponseDueAt(fixedNow.plus(Duration.ofHours(40)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.findByCommandKey("CMD_TAMPERED")).thenReturn(Optional.empty());

        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("evidence-tamper-", ".jpg");
        try {
            java.nio.file.Files.write(tempFile, "Original file bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String fakeDigest = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";

            ReturnCaseDtos.EvidenceItemDto evidence = new ReturnCaseDtos.EvidenceItemDto(
                    "IMAGE", tempFile.toString(), "Ảnh kiện hàng", fakeDigest, 1024L, null
            );
            ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                    "Hàng hóa bị nứt vỡ",
                    List.of(evidence),
                    "CMD_TAMPERED"
            );

            assertThatThrownBy(() -> service.submitDamageExemptionRequest(sellerId, caseId, req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("không khớp với nội dung tệp thực tế");
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("Task 3.2.2 ContentDigest (Bổ trợ): File local tồn tại, clientDigest null -> Tự động tính SHA-256 từ nội dung tệp")
    void submitDamageExemptionRequest_withRealLocalFile_nullDigest_autoComputesFromContent() throws Exception {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));
        returnCase.setSellerResponseDueAt(fixedNow.plus(Duration.ofHours(40)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.findByCommandKey("CMD_AUTO_DIGEST")).thenReturn(Optional.empty());

        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("evidence-auto-", ".jpg");
        try {
            byte[] fileBytes = "Auto computed content bytes for damage request".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            java.nio.file.Files.write(tempFile, fileBytes);
            String expectedDigest = java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(fileBytes)
            );

            ReturnCaseDtos.EvidenceItemDto evidence = new ReturnCaseDtos.EvidenceItemDto(
                    "IMAGE", tempFile.toString(), "Ảnh kiện hàng", null, (long) fileBytes.length, null
            );
            ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                    "Hàng hóa bị nứt vỡ",
                    List.of(evidence),
                    "CMD_AUTO_DIGEST"
            );

            ReturnCaseDtos.ReturnCaseDetailResponse resp = service.submitDamageExemptionRequest(sellerId, caseId, req);
            assertThat(resp).isNotNull();

            ArgumentCaptor<CaseEvidenceEntity> evCaptor = ArgumentCaptor.forClass(CaseEvidenceEntity.class);
            verify(caseEvidenceRepository).save(evCaptor.capture());
            assertThat(evCaptor.getValue().getContentDigest()).isEqualTo(expectedDigest);
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("Task 3.2.2 ContentDigest 3/7: File local không tồn tại và không có verifier -> Từ chối (Fail-Closed)")
    void submitDamageExemptionRequest_noLocalFile_noVerifier_throwsException() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));
        returnCase.setSellerResponseDueAt(fixedNow.plus(Duration.ofHours(40)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.findByCommandKey(any())).thenReturn(Optional.empty());

        service.setEvidenceStorageVerifier(null);

        ReturnCaseDtos.EvidenceItemDto missingDigest = new ReturnCaseDtos.EvidenceItemDto(
                "IMAGE", "non_existent_dir/missing.jpg", "Mô tả", null, 1024L, null
        );
        ReturnCaseDtos.SellerDamageExemptionRequest reqNull = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Hàng hóa bị nứt vỡ",
                List.of(missingDigest),
                "CMD_NO_DIGEST_NO_VERIFIER"
        );
        assertThatThrownBy(() -> service.submitDamageExemptionRequest(sellerId, caseId, reqNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không tìm thấy tệp bằng chứng cục bộ và không có dịch vụ xác thực lưu trữ (Storage Verifier)");
    }

    @Test
    @DisplayName("Task 3.2.2 ContentDigest 4/7: File local không tồn tại nhưng client gửi digest hợp lệ về định dạng -> Bắt buộc từ chối khi không có verifier")
    void submitDamageExemptionRequest_noLocalFile_validHexFormatDigest_noVerifier_throwsException() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));
        returnCase.setSellerResponseDueAt(fixedNow.plus(Duration.ofHours(40)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.findByCommandKey(any())).thenReturn(Optional.empty());

        service.setEvidenceStorageVerifier(null);

        // Chuỗi SHA-256 hợp lệ chuẩn 64 ký tự hex
        String validHexSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        ReturnCaseDtos.EvidenceItemDto hexDigestItem = new ReturnCaseDtos.EvidenceItemDto(
                "IMAGE", "non_existent_dir/remote_evidence.jpg", "Mô tả", validHexSha256, 1024L, null
        );
        ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Hàng hóa bị nứt vỡ",
                List.of(hexDigestItem),
                "CMD_VALID_HEX_NO_VERIFIER"
        );

        // Kể cả định dạng hợp lệ, khi không có storage verifier đọc nội dung file thực tế -> PHẢI FAIL-CLOSED BỊ TỪ CHỐI
        assertThatThrownBy(() -> service.submitDamageExemptionRequest(sellerId, caseId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không tìm thấy tệp bằng chứng cục bộ và không có dịch vụ xác thực lưu trữ (Storage Verifier)");
    }

    @Test
    @DisplayName("Task 3.2.2 ContentDigest 5/7: Storage verifier đọc nội dung thực tế và trả về digest khớp -> Thành công")
    void submitDamageExemptionRequest_storageVerifier_contentMatches_success() throws Exception {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));
        returnCase.setSellerResponseDueAt(fixedNow.plus(Duration.ofHours(40)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.findByCommandKey("CMD_VERIFIER_MATCH")).thenReturn(Optional.empty());

        EvidenceStorageVerifier mockVerifier = mock(EvidenceStorageVerifier.class);
        String remoteKey = "s3://og-shop-evidence/damage_item_123.jpg";
        String realSha256 = "1122334455667788990011223344556677889900112233445566778899001122";
        when(mockVerifier.computeSha256(remoteKey)).thenReturn(realSha256);

        service.setEvidenceStorageVerifier(mockVerifier);
        try {
            ReturnCaseDtos.EvidenceItemDto evidence = new ReturnCaseDtos.EvidenceItemDto(
                    "IMAGE", remoteKey, "Ảnh từ hệ thống lưu trữ", realSha256, 2048L, null
            );
            ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                    "Hàng hóa bị nứt vỡ",
                    List.of(evidence),
                    "CMD_VERIFIER_MATCH"
            );

            ReturnCaseDtos.ReturnCaseDetailResponse resp = service.submitDamageExemptionRequest(sellerId, caseId, req);
            assertThat(resp).isNotNull();

            ArgumentCaptor<CaseEvidenceEntity> evCaptor = ArgumentCaptor.forClass(CaseEvidenceEntity.class);
            verify(caseEvidenceRepository).save(evCaptor.capture());
            assertThat(evCaptor.getValue().getContentDigest()).isEqualTo(realSha256);
            verify(mockVerifier).computeSha256(remoteKey);
        } finally {
            service.setEvidenceStorageVerifier(null);
        }
    }

    @Test
    @DisplayName("Task 3.2.2 ContentDigest 6/7: Storage verifier trả digest không khớp -> Từ chối")
    void submitDamageExemptionRequest_storageVerifier_digestMismatch_throwsException() throws Exception {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));
        returnCase.setSellerResponseDueAt(fixedNow.plus(Duration.ofHours(40)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.findByCommandKey("CMD_VERIFIER_MISMATCH")).thenReturn(Optional.empty());

        EvidenceStorageVerifier mockVerifier = mock(EvidenceStorageVerifier.class);
        String remoteKey = "s3://og-shop-evidence/damage_item_tampered.jpg";
        String actualSha256 = "1122334455667788990011223344556677889900112233445566778899001122";
        String mismatchedClientDigest = "8888888888888888888888888888888888888888888888888888888888888888";
        when(mockVerifier.computeSha256(remoteKey)).thenReturn(actualSha256);

        service.setEvidenceStorageVerifier(mockVerifier);
        try {
            ReturnCaseDtos.EvidenceItemDto evidence = new ReturnCaseDtos.EvidenceItemDto(
                    "IMAGE", remoteKey, "Ảnh từ hệ thống lưu trữ", mismatchedClientDigest, 2048L, null
            );
            ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                    "Hàng hóa bị nứt vỡ",
                    List.of(evidence),
                    "CMD_VERIFIER_MISMATCH"
            );

            assertThatThrownBy(() -> service.submitDamageExemptionRequest(sellerId, caseId, req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("không khớp với nội dung tệp thực tế");
        } finally {
            service.setEvidenceStorageVerifier(null);
        }
    }

    @Test
    @DisplayName("Task 3.2.2 ContentDigest 7/7: Storage I/O thất bại -> Fail-closed")
    void submitDamageExemptionRequest_storageVerifier_ioFailure_throwsException() throws Exception {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));
        returnCase.setSellerResponseDueAt(fixedNow.plus(Duration.ofHours(40)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.findByCommandKey("CMD_VERIFIER_IO_ERR")).thenReturn(Optional.empty());

        EvidenceStorageVerifier mockVerifier = mock(EvidenceStorageVerifier.class);
        String remoteKey = "s3://og-shop-evidence/damage_item_io_err.jpg";
        when(mockVerifier.computeSha256(remoteKey)).thenThrow(new java.io.IOException("Connection reset by peer / storage unreachable"));

        service.setEvidenceStorageVerifier(mockVerifier);
        try {
            ReturnCaseDtos.EvidenceItemDto evidence = new ReturnCaseDtos.EvidenceItemDto(
                    "IMAGE", remoteKey, "Ảnh từ hệ thống lưu trữ",
                    "1122334455667788990011223344556677889900112233445566778899001122", 2048L, null
            );
            ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                    "Hàng hóa bị nứt vỡ",
                    List.of(evidence),
                    "CMD_VERIFIER_IO_ERR"
            );

            assertThatThrownBy(() -> service.submitDamageExemptionRequest(sellerId, caseId, req))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Lỗi I/O khi đọc tệp từ hệ thống lưu trữ");
        } finally {
            service.setEvidenceStorageVerifier(null);
        }
    }

    @Test
    @DisplayName("Task 3.2.2: submitDamageExemptionRequest - rejected when evidence objectKey is blank")
    void submitDamageExemptionRequest_rejected_whenEvidenceObjectKeyBlank() {
        Long caseId = 501L;
        ReturnCaseDtos.EvidenceItemDto blankKey = new ReturnCaseDtos.EvidenceItemDto(
                "IMAGE", "  ", "Mô tả",
                "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
                1024L, null
        );
        ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Hàng hóa bị nứt vỡ",
                List.of(blankKey),
                "CMD_BLANK_KEY"
        );

        assertThatThrownBy(() -> service.submitDamageExemptionRequest(sellerId, caseId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Đường dẫn tệp bằng chứng (objectKey) không được để trống");
    }

    @Test
    @DisplayName("Task 3.2.2: submitDamageExemptionRequest - idempotent replay returns current snapshot without duplicate writes")
    void submitDamageExemptionRequest_idempotentReplay_success() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));
        returnCase.setSellerResponseDueAt(fixedNow.plus(Duration.ofHours(40)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        CaseEventEntity existingEvent = new CaseEventEntity(
                UUID.randomUUID(), caseId, round2.getId(), sellerId, null,
                "VALID_DAMAGE_REQUEST", "Hàng hư hỏng", "{}", "CMD_EXISTING_DMG"
        );

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.findByCommandKey("CMD_EXISTING_DMG")).thenReturn(Optional.of(existingEvent));

        ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Hàng hư hỏng nghiêm trọng",
                List.of(createSampleEvidence("IMAGE", "e/1.jpg")),
                "CMD_EXISTING_DMG"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse resp = service.submitDamageExemptionRequest(sellerId, caseId, req);

        assertThat(resp).isNotNull();
        verify(caseEvidenceRepository, never()).save(any());
        verify(caseEventRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Task 3.2.2: submitDamageExemptionRequest - idempotent replay rejected when commandKey belongs to different actor or case")
    void submitDamageExemptionRequest_idempotentReplay_rejected_whenDiffActorOrCase() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        CaseEventEntity existingEvent = new CaseEventEntity(
                UUID.randomUUID(), 888L, round2.getId(), 777L, null, // diff case and actor!
                "VALID_DAMAGE_REQUEST", "Hàng hư hỏng", "{}", "CMD_DIFF_ACTOR_DMG"
        );

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.findByCommandKey("CMD_DIFF_ACTOR_DMG")).thenReturn(Optional.of(existingEvent));

        ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Hàng hư hỏng nghiêm trọng",
                List.of(createSampleEvidence("IMAGE", "e/1.jpg")),
                "CMD_DIFF_ACTOR_DMG"
        );

        assertThatThrownBy(() -> service.submitDamageExemptionRequest(sellerId, caseId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CommandKey đã tồn tại nhưng không thuộc về người bán hoặc hồ sơ này");
    }

    @Test
    @DisplayName("Task 3.2.2: submitDamageExemptionRequest - rejected when valid_damage_request_at already exists (BD-5 pending resubmission policy)")
    void submitDamageExemptionRequest_rejected_whenDamageRequestAlreadyExists_BD5() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);
        round2.setValidDamageRequestAt(fixedNow.minus(Duration.ofHours(2))); // Already exists!

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.findByCommandKey("CMD_NEW_DMG")).thenReturn(Optional.empty());

        ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Muốn gửi thêm bằng chứng thiệt hại lần 2",
                List.of(createSampleEvidence("IMAGE", "e/2.jpg")),
                "CMD_NEW_DMG"
        );

        assertThatThrownBy(() -> service.submitDamageExemptionRequest(sellerId, caseId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Người bán đã gửi yêu cầu bồi thường thiệt hại cho Vòng 2");

        verify(caseEvidenceRepository, never()).save(any());
        verify(caseEventRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Task 3.2.2 Boundary: submitDamageExemptionRequest - commandKey boundary 150 chars is valid, 151 chars is rejected")
    void submitDamageExemptionRequest_boundaryCommandKey_validAndExceeded() {
        Long caseId = 501L;

        // 151 chars -> rejected
        String cmdKey151 = "CMD_" + "B".repeat(147);
        assertThat(cmdKey151.length()).isEqualTo(151);
        ReturnCaseDtos.SellerDamageExemptionRequest req151 = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Yêu cầu bồi thường với key quá 150 ký tự",
                List.of(createSampleEvidence("IMAGE", "e/1.jpg")),
                cmdKey151
        );
        assertThatThrownBy(() -> service.submitDamageExemptionRequest(sellerId, caseId, req151))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("commandKey tối đa 150 ký tự");

        // 150 chars -> valid
        String cmdKey150 = "CMD_" + "B".repeat(146);
        assertThat(cmdKey150.length()).isEqualTo(150);
        ReturnCaseDtos.SellerDamageExemptionRequest req150 = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Yêu cầu bồi thường với key đúng 150 ký tự",
                List.of(createSampleEvidence("IMAGE", "e/1.jpg")),
                cmdKey150
        );

        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));
        returnCase.setSellerResponseDueAt(fixedNow.plus(Duration.ofHours(40)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.findByCommandKey(cmdKey150)).thenReturn(Optional.empty());

        ReturnCaseDtos.ReturnCaseDetailResponse resp = service.submitDamageExemptionRequest(sellerId, caseId, req150);
        assertThat(resp).isNotNull();
        verify(caseEventRepository).saveAndFlush(any());
    }

    // =========================================================================
    // TASK 3.2.3: KTV FINAL DECISION (ktvDecideRound2) UNIT TESTS
    // =========================================================================

    @Test
    @DisplayName("Task 3.2.3: ktvDecideRound2 - FULL_GOODS_REFUND success records decision, requests settlement, and closes case")
    void ktvDecideRound2_fullRefund_success() {
        Long caseId = 501L;
        Long ktvId = 300L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseRoundRepository.findLatestRoundByCaseId(caseId)).thenReturn(Optional.of(round2));
        when(caseDecisionRepository.findByCommandKey(anyString())).thenReturn(Optional.empty());
        when(caseDecisionRepository.saveAndFlush(any(CaseDecisionEntity.class))).thenAnswer(inv -> {
            CaseDecisionEntity d = inv.getArgument(0);
            d.setId(901L);
            return d;
        });
        when(caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(round2.getId())).thenAnswer(inv -> {
            CaseDecisionEntity d = new CaseDecisionEntity(round2.getId(), ktvId, CaseDecisionEntity.ACTOR_USER,
                    CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND, true, new BigDecimal("500000.00"),
                    "Hàng hóa trả lại đúng hiện trạng, KTV quyết định hoàn tiền toàn bộ cho người mua", "CMD_KTV_R2_FULL");
            d.setId(901L);
            return List.of(d);
        });
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV')"), eq(Boolean.class), eq(ktvId)))
                .thenReturn(Boolean.TRUE);

        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Hàng hóa trả lại đúng hiện trạng, KTV quyết định hoàn tiền toàn bộ cho người mua",
                "CMD_KTV_R2_FULL"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse response = service.ktvDecideRound2(ktvId, caseId, req);

        assertThat(response).isNotNull();
        assertThat(returnCase.getState()).isEqualTo(CaseEntity.STATE_CLOSED);
        assertThat(round2.getState()).isEqualTo(CaseRoundEntity.STATE_DECIDED);

        ArgumentCaptor<CaseDecisionEntity> decisionCaptor = ArgumentCaptor.forClass(CaseDecisionEntity.class);
        verify(caseDecisionRepository).saveAndFlush(decisionCaptor.capture());
        CaseDecisionEntity saved = decisionCaptor.getValue();
        assertThat(saved.getOutcome()).isEqualTo(CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND);
        assertThat(saved.getReviewedBy()).isEqualTo(ktvId);
        assertThat(saved.getActorType()).isEqualTo(CaseDecisionEntity.ACTOR_USER);
        assertThat(saved.getGoodsRefundAmount()).isEqualByComparingTo(new BigDecimal("500000.00"));
        assertThat(saved.getIsFinal()).isTrue();

        verify(settlementPort).requestSettlement(
                eq(orderId), eq(caseId), eq(901L), eq(CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND), eq("CMD_KTV_R2_FULL")
        );
        verify(caseEventRepository, never()).save(any(CaseEventEntity.class));
        assertThat(response.decisions()).hasSize(1);
        assertThat(response.decisions().get(0).outcome()).isEqualTo(CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND);
        assertThat(response.decisions().get(0).isFinal()).isTrue();
        assertThat(response.decisions().get(0).reason()).isEqualTo("Hàng hóa trả lại đúng hiện trạng, KTV quyết định hoàn tiền toàn bộ cho người mua");
    }

    @Test
    @DisplayName("Task 3.2.3: ktvDecideRound2 - EXEMPT_GOODS_REFUND success when validDamageRequestAt exists, sets goodsRefundAmount=0, requests settlement")
    void ktvDecideRound2_exemptRefund_success() {
        Long caseId = 501L;
        Long ktvId = 300L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);
        round2.setValidDamageRequestAt(fixedNow.minus(Duration.ofHours(3))); // Valid damage request exists!

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseDecisionRepository.findByCommandKey(anyString())).thenReturn(Optional.empty());
        when(caseDecisionRepository.saveAndFlush(any(CaseDecisionEntity.class))).thenAnswer(inv -> {
            CaseDecisionEntity d = inv.getArgument(0);
            d.setId(902L);
            return d;
        });
        when(caseRoundRepository.findLatestRoundByCaseId(caseId)).thenReturn(Optional.of(round2));
        when(caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(round2.getId())).thenAnswer(inv -> {
            CaseDecisionEntity d = new CaseDecisionEntity(round2.getId(), ktvId, CaseDecisionEntity.ACTOR_USER,
                    CaseDecisionEntity.OUTCOME_EXEMPT_GOODS_REFUND, true, BigDecimal.ZERO,
                    "Người bán đã cung cấp đủ bằng chứng hàng hóa bị hư hại trong quá trình trả hàng, KTV chấp thuận miễn trừ bồi thường", "CMD_KTV_R2_EXEMPT");
            d.setId(902L);
            return List.of(d);
        });
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV')"), eq(Boolean.class), eq(ktvId)))
                .thenReturn(Boolean.TRUE);

        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_EXEMPT_GOODS_REFUND,
                "Người bán đã cung cấp đủ bằng chứng hàng hóa bị hư hại trong quá trình trả hàng, KTV chấp thuận miễn trừ bồi thường",
                "CMD_KTV_R2_EXEMPT"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse response = service.ktvDecideRound2(ktvId, caseId, req);

        assertThat(response).isNotNull();
        assertThat(returnCase.getState()).isEqualTo(CaseEntity.STATE_CLOSED);
        assertThat(round2.getState()).isEqualTo(CaseRoundEntity.STATE_DECIDED);

        ArgumentCaptor<CaseDecisionEntity> decisionCaptor = ArgumentCaptor.forClass(CaseDecisionEntity.class);
        verify(caseDecisionRepository).saveAndFlush(decisionCaptor.capture());
        CaseDecisionEntity saved = decisionCaptor.getValue();
        assertThat(saved.getOutcome()).isEqualTo(CaseDecisionEntity.OUTCOME_EXEMPT_GOODS_REFUND);
        assertThat(saved.getReviewedBy()).isEqualTo(ktvId);
        assertThat(saved.getGoodsRefundAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        verify(settlementPort).requestSettlement(
                eq(orderId), eq(caseId), eq(902L), eq(CaseDecisionEntity.OUTCOME_EXEMPT_GOODS_REFUND), eq("CMD_KTV_R2_EXEMPT")
        );
        verify(caseEventRepository, never()).save(any(CaseEventEntity.class));
        assertThat(response.decisions()).hasSize(1);
        assertThat(response.decisions().get(0).outcome()).isEqualTo(CaseDecisionEntity.OUTCOME_EXEMPT_GOODS_REFUND);
        assertThat(response.decisions().get(0).goodsRefundAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Task 3.2.3: ktvDecideRound2 - EXEMPT_GOODS_REFUND rejected when validDamageRequestAt is null")
    void ktvDecideRound2_exemptRefund_rejected_whenNoValidDamageRequestAt() {
        Long caseId = 501L;
        Long ktvId = 300L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);
        round2.setValidDamageRequestAt(null); // NULL!

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV')"), eq(Boolean.class), eq(ktvId)))
                .thenReturn(Boolean.TRUE);

        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_EXEMPT_GOODS_REFUND,
                "KTV cố gắng miễn trừ nhưng người bán chưa gửi yêu cầu bồi thường",
                "CMD_KTV_R2_NO_DMG"
        );

        assertThatThrownBy(() -> service.ktvDecideRound2(ktvId, caseId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Chỉ được miễn trừ bồi thường khi người bán đã gửi yêu cầu thiệt hại hợp lệ");

        verify(caseDecisionRepository, never()).saveAndFlush(any());
        verify(settlementPort, never()).requestSettlement(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Task 3.2.3: ktvDecideRound2 - rejected when actor does not have KTV role")
    void ktvDecideRound2_rejected_whenNotKtv() {
        Long caseId = 501L;
        Long nonKtvId = 9999L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV')"), eq(Boolean.class), eq(nonKtvId)))
                .thenReturn(Boolean.FALSE);

        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Người dùng không có quyền KTV cố gắng ra quyết định",
                "CMD_NON_KTV"
        );

        assertThatThrownBy(() -> service.ktvDecideRound2(nonKtvId, caseId, req))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Người dùng không có quyền KTV để phân xử hồ sơ Vòng 2");

        verify(caseDecisionRepository, never()).saveAndFlush(any());
        verify(settlementPort, never()).requestSettlement(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Task 3.2.3: ktvDecideRound2 - rejected when case is not in POST_RETURN")
    void ktvDecideRound2_rejected_whenCaseNotPostReturn() {
        Long caseId = 501L;
        Long ktvId = 300L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setAssignedTo(ktvId);
        returnCase.setState(CaseEntity.STATE_RETURNING); // not POST_RETURN!

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV')"), eq(Boolean.class), eq(ktvId)))
                .thenReturn(Boolean.TRUE);

        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Phán quyết khi hàng chưa về tay người bán",
                "CMD_NOT_POST_RETURN"
        );

        assertThatThrownBy(() -> service.ktvDecideRound2(ktvId, caseId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hồ sơ không ở trạng thái Vòng 2 sau khi nhận lại hàng");
    }

    @Test
    @DisplayName("Task 3.2.3: ktvDecideRound2 - rejected when round 2 is not OPEN")
    void ktvDecideRound2_rejected_whenRoundNotOpen() {
        Long caseId = 501L;
        Long ktvId = 300L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_DECIDED); // DECIDED!

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV')"), eq(Boolean.class), eq(ktvId)))
                .thenReturn(Boolean.TRUE);

        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Phán quyết sau khi vòng đã kết thúc",
                "CMD_ROUND_CLOSED"
        );

        assertThatThrownBy(() -> service.ktvDecideRound2(ktvId, caseId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Vòng 2 đã kết thúc, không thể đưa ra quyết định");
    }

    @Test
    @DisplayName("Task 3.2.3: ktvDecideRound2 - rejected when round 2 stage is not POST_RETURN_RESOLUTION")
    void ktvDecideRound2_rejected_whenRound2StageNotPostReturnResolution() {
        Long caseId = 501L;
        Long ktvId = 300L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, "WRONG_STAGE");
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV')"), eq(Boolean.class), eq(ktvId)))
                .thenReturn(Boolean.TRUE);

        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Lý do phán quyết hợp lệ",
                "CMD_WRONG_STAGE"
        );

        assertThatThrownBy(() -> service.ktvDecideRound2(ktvId, caseId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Vòng 2 không ở giai đoạn giải quyết sau nhận lại hàng");
    }

    @Test
    @DisplayName("Task 3.2.3: ktvDecideRound2 - rejected when valid_returned_at is null")
    void ktvDecideRound2_rejected_whenNoValidReturnedAt() {
        Long caseId = 501L;
        Long ktvId = 300L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(null); // null!

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV')"), eq(Boolean.class), eq(ktvId)))
                .thenReturn(Boolean.TRUE);

        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Phán quyết khi chưa xác nhận hoàn trả hợp lệ",
                "CMD_NO_RETURN_TIME"
        );

        assertThatThrownBy(() -> service.ktvDecideRound2(ktvId, caseId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Chưa có xác nhận hàng đã được hoàn trả hợp lệ cho người bán");
    }

    @Test
    @DisplayName("Task 3.2.3: ktvDecideRound2 - rejected when outcome is not FULL_GOODS_REFUND or EXEMPT_GOODS_REFUND")
    void ktvDecideRound2_rejected_whenUnsupportedOutcome() {
        Long caseId = 501L;
        Long ktvId = 300L;

        ReturnCaseDtos.KtvRound2DecisionRequest reqApprove = new ReturnCaseDtos.KtvRound2DecisionRequest(
                "APPROVE_RETURN",
                "Cố tình đưa outcome Vòng 1 vào Vòng 2",
                "CMD_BAD_OUTCOME"
        );

        assertThatThrownBy(() -> service.ktvDecideRound2(ktvId, caseId, reqApprove))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Phán quyết KTV Vòng 2 không hợp lệ");
    }

    @Test
    @DisplayName("Task 3.2.3: ktvDecideRound2 - rejected when reason is too short or null")
    void ktvDecideRound2_rejected_whenReasonTooShortOrNull() {
        Long caseId = 501L;
        Long ktvId = 300L;

        ReturnCaseDtos.KtvRound2DecisionRequest reqShort = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Hủy",
                "CMD_SHORT"
        );
        assertThatThrownBy(() -> service.ktvDecideRound2(ktvId, caseId, reqShort))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("KTV bắt buộc phải nhập lý do phán quyết chi tiết (tối thiểu 5 ký tự)");

        ReturnCaseDtos.KtvRound2DecisionRequest reqNull = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                null,
                "CMD_NULL"
        );
        assertThatThrownBy(() -> service.ktvDecideRound2(ktvId, caseId, reqNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("KTV bắt buộc phải nhập lý do phán quyết chi tiết (tối thiểu 5 ký tự)");
    }

    @Test
    @DisplayName("Task 3.2.3 Boundary: ktvDecideRound2 - reason longer than 2000 chars is rejected")
    void ktvDecideRound2_rejected_whenReasonLongerThan2000Chars() {
        Long caseId = 501L;
        Long ktvId = 300L;

        String reason2001 = "A".repeat(2001);
        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                reason2001,
                "CMD_LONG_REASON"
        );

        assertThatThrownBy(() -> service.ktvDecideRound2(ktvId, caseId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lý do phán quyết tối đa 2000 ký tự");
    }

    @Test
    @DisplayName("Task 3.2.3 Boundary: ktvDecideRound2 - commandKey boundary 127 chars is valid, 128 chars is rejected")
    void ktvDecideRound2_boundaryCommandKey_validAndExceeded() {
        Long caseId = 501L;
        Long ktvId = 300L;

        // 128 chars -> rejected
        String cmdKey128 = "CMD_" + "C".repeat(124);
        assertThat(cmdKey128.length()).isEqualTo(128);
        ReturnCaseDtos.KtvRound2DecisionRequest req128 = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Lý do phán quyết chi tiết của KTV",
                cmdKey128
        );
        assertThatThrownBy(() -> service.ktvDecideRound2(ktvId, caseId, req128))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("commandKey tối đa 127 ký tự");

        // 127 chars -> valid
        String cmdKey127 = "CMD_" + "C".repeat(123);
        assertThat(cmdKey127.length()).isEqualTo(127);
        ReturnCaseDtos.KtvRound2DecisionRequest req127 = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Lý do phán quyết chi tiết của KTV hợp lệ",
                cmdKey127
        );

        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseDecisionRepository.findByCommandKey(cmdKey127)).thenReturn(Optional.empty());
        when(caseDecisionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV')"), eq(Boolean.class), eq(ktvId)))
                .thenReturn(Boolean.TRUE);

        ReturnCaseDtos.ReturnCaseDetailResponse resp = service.ktvDecideRound2(ktvId, caseId, req127);
        assertThat(resp).isNotNull();
        verify(settlementPort).requestSettlement(eq(orderId), eq(caseId), any(), eq(CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND), eq(cmdKey127));
    }

    @Test
    @DisplayName("Task 3.2.3: ktvDecideRound2 - idempotent replay returns current snapshot without duplicate writes")
    void ktvDecideRound2_idempotentReplay_success() {
        Long caseId = 501L;
        Long ktvId = 300L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        CaseDecisionEntity existingDecision = new CaseDecisionEntity(
                round2.getId(), ktvId, CaseDecisionEntity.ACTOR_USER,
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND, true,
                BigDecimal.valueOf(250000.00), "Phán quyết cũ", "CMD_EXISTING_KTV_R2"
        );

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseDecisionRepository.findByCommandKey("CMD_EXISTING_KTV_R2")).thenReturn(Optional.of(existingDecision));
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV')"), eq(Boolean.class), eq(ktvId)))
                .thenReturn(Boolean.TRUE);

        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Gọi lại cùng lệnh phán quyết",
                "CMD_EXISTING_KTV_R2"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse resp = service.ktvDecideRound2(ktvId, caseId, req);

        assertThat(resp).isNotNull();
        verify(caseDecisionRepository, never()).saveAndFlush(any());
        verify(settlementPort, never()).requestSettlement(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Task 3.2.3: ktvDecideRound2 - idempotent replay rejected when commandKey belongs to different KTV")
    void ktvDecideRound2_idempotentReplay_rejected_whenDiffKtv() {
        Long caseId = 501L;
        Long ktvId = 300L;
        Long otherKtvId = 999L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setAssignedTo(ktvId);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);

        CaseDecisionEntity existingDecision = new CaseDecisionEntity(
                round2.getId(), otherKtvId, CaseDecisionEntity.ACTOR_USER, // diff KTV!
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND, true,
                BigDecimal.valueOf(250000.00), "Phán quyết của KTV khác", "CMD_OTHER_KTV"
        );

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseDecisionRepository.findByCommandKey("CMD_OTHER_KTV")).thenReturn(Optional.of(existingDecision));
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV')"), eq(Boolean.class), eq(ktvId)))
                .thenReturn(Boolean.TRUE);

        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Lý do hợp lệ nhưng key của KTV khác",
                "CMD_OTHER_KTV"
        );

        assertThatThrownBy(() -> service.ktvDecideRound2(ktvId, caseId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CommandKey đã tồn tại nhưng không thuộc về KTV này");
    }

    @Test
    @DisplayName("Task 3.2.3: ktvDecideRound2 - idempotent replay rejected when outcome differs")
    void ktvDecideRound2_idempotentReplay_rejected_whenDiffOutcome() {
        Long caseId = 501L;
        Long ktvId = 300L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setAssignedTo(ktvId);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);

        CaseDecisionEntity existingDecision = new CaseDecisionEntity(
                round2.getId(), ktvId, CaseDecisionEntity.ACTOR_USER,
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND, true, // FULL!
                BigDecimal.valueOf(250000.00), "Phán quyết cũ", "CMD_DIFF_OUTCOME"
        );

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseDecisionRepository.findByCommandKey("CMD_DIFF_OUTCOME")).thenReturn(Optional.of(existingDecision));
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV')"), eq(Boolean.class), eq(ktvId)))
                .thenReturn(Boolean.TRUE);

        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_EXEMPT_GOODS_REFUND, // EXEMPT!
                "Lý do hợp lệ nhưng outcome khác",
                "CMD_DIFF_OUTCOME"
        );

        assertThatThrownBy(() -> service.ktvDecideRound2(ktvId, caseId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CommandKey đã tồn tại với outcome khác");
    }

    @Test
    @DisplayName("Task 3.2.3: ktvDecideRound2 - transaction rollback when settlement request fails")
    void ktvDecideRound2_rollbackWhenSettlementFails() {
        Long caseId = 501L;
        Long ktvId = 300L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseDecisionRepository.findByCommandKey(anyString())).thenReturn(Optional.empty());
        when(caseDecisionRepository.saveAndFlush(any())).thenAnswer(inv -> {
            CaseDecisionEntity d = inv.getArgument(0);
            d.setId(905L);
            return d;
        });
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV')"), eq(Boolean.class), eq(ktvId)))
                .thenReturn(Boolean.TRUE);

        doThrow(new RuntimeException("Payment service down"))
                .when(settlementPort).requestSettlement(any(), any(), any(), any(), any());

        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Phán quyết KTV hợp lệ nhưng settlement lỗi",
                "CMD_SETTLE_FAIL"
        );

        assertThatThrownBy(() -> service.ktvDecideRound2(ktvId, caseId, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Payment service down");
    }

    // =========================================================================
    // FEATURE 3.2: TASK 3.2.4 - AUTOMATIC POST-RETURN TIMEOUT REFUND
    // Error Classification & Reconcile Unit Tests
    // =========================================================================

    static class MockServerErrorMessage {
        private final String constraint;
        private final String routine;
        private final String message;
        private final String where;

        public MockServerErrorMessage(String constraint, String routine, String message, String where) {
            this.constraint = constraint;
            this.routine = routine;
            this.message = message;
            this.where = where;
        }

        public String getConstraint() { return constraint; }
        public String getRoutine() { return routine; }
        public String getMessage() { return message; }
        public String getWhere() { return where; }
    }

    static class MockPSQLException extends java.sql.SQLException {
        private final MockServerErrorMessage serverErrorMessage;

        public MockPSQLException(String sqlState, MockServerErrorMessage serverErrorMessage) {
            super(serverErrorMessage != null ? serverErrorMessage.getMessage() : "sql error", sqlState);
            this.serverErrorMessage = serverErrorMessage;
        }

        public Object getServerErrorMessage() {
            return serverErrorMessage;
        }
    }

    @Test
    @DisplayName("Task 3.2.4: handleCaseExecutionException - 23514 branch 1 when competing workflow committed returns SKIPPED")
    void handleCaseExecutionException_23514_branch1_competingWorkflow_skipped() {
        Long caseId = 501L;
        MockPSQLException ex = new MockPSQLException("23514", new MockServerErrorMessage(
                null, "exec_stmt_raise", "Case round already decided",
                "PL/pgSQL function og_apply_case_decision() line 42 at RAISE"
        ));

        // Mock DB verification: decisionCount = 1 (Seller or KTV decided)
        when(jdbcTemplate.queryForObject(contains("valid_damage_request_at IS NOT NULL"), eq(Integer.class), eq(caseId)))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("actor_type <> 'SYSTEM_TIMEOUT'"), eq(Integer.class), eq(caseId)))
                .thenReturn(1);

        ReturnCaseDtos.CaseTimeoutExecutionResult result = service.handleCaseExecutionException(caseId, ex);

        assertThat(result.status()).isEqualTo(ReturnCaseDtos.TimeoutProcessingStatus.SKIPPED);
        assertThat(result.message()).contains("Round 2 đã được quyết định bởi workflow khác");
    }

    @Test
    @DisplayName("Task 3.2.4: handleCaseExecutionException - 23514 branch 1 when no competing workflow in DB returns FAILED (no swallow)")
    void handleCaseExecutionException_23514_branch1_noCompetingWorkflow_failed() {
        Long caseId = 501L;
        MockPSQLException ex = new MockPSQLException("23514", new MockServerErrorMessage(
                null, "exec_stmt_raise", "Case round already decided",
                "PL/pgSQL function og_apply_case_decision() line 42 at RAISE"
        ));

        // Mock DB verification: no competing workflow found
        when(jdbcTemplate.queryForObject(contains("valid_damage_request_at IS NOT NULL"), eq(Integer.class), eq(caseId)))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("actor_type <> 'SYSTEM_TIMEOUT'"), eq(Integer.class), eq(caseId)))
                .thenReturn(0);

        ReturnCaseDtos.CaseTimeoutExecutionResult result = service.handleCaseExecutionException(caseId, ex);

        assertThat(result.status()).isEqualTo(ReturnCaseDtos.TimeoutProcessingStatus.FAILED);
        assertThat(result.message()).contains("không xác nhận được workflow cạnh tranh");
    }

    @Test
    @DisplayName("Task 3.2.4: handleCaseExecutionException - 23514 branch 2 when valid damage request committed returns SKIPPED")
    void handleCaseExecutionException_23514_branch2_damageRequestCommitted_skipped() {
        Long caseId = 501L;
        MockPSQLException ex = new MockPSQLException("23514", new MockServerErrorMessage(
                null, "exec_stmt_raise", "Auto refund blocked by deadline/request",
                "PL/pgSQL function og_apply_case_decision() line 55 at RAISE"
        ));

        // Mock DB verification: valid damage request exists
        when(jdbcTemplate.queryForObject(contains("valid_damage_request_at IS NOT NULL"), eq(Integer.class), eq(caseId)))
                .thenReturn(1);

        ReturnCaseDtos.CaseTimeoutExecutionResult result = service.handleCaseExecutionException(caseId, ex);

        assertThat(result.status()).isEqualTo(ReturnCaseDtos.TimeoutProcessingStatus.SKIPPED);
        assertThat(result.message()).contains("Seller Damage Request hoặc workflow khác đã được ghi nhận");
    }

    @Test
    @DisplayName("Task 3.2.4: handleCaseExecutionException - 23514 branch 2 when no competing workflow in DB returns FAILED")
    void handleCaseExecutionException_23514_branch2_noCompetingWorkflow_failed() {
        Long caseId = 501L;
        MockPSQLException ex = new MockPSQLException("23514", new MockServerErrorMessage(
                null, "exec_stmt_raise", "Auto refund blocked by deadline/request",
                "PL/pgSQL function og_apply_case_decision() line 55 at RAISE"
        ));

        when(jdbcTemplate.queryForObject(contains("valid_damage_request_at IS NOT NULL"), eq(Integer.class), eq(caseId)))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("actor_type <> 'SYSTEM_TIMEOUT'"), eq(Integer.class), eq(caseId)))
                .thenReturn(0);

        ReturnCaseDtos.CaseTimeoutExecutionResult result = service.handleCaseExecutionException(caseId, ex);

        assertThat(result.status()).isEqualTo(ReturnCaseDtos.TimeoutProcessingStatus.FAILED);
        assertThat(result.message()).contains("không xác nhận được workflow cạnh tranh");
    }

    @Test
    @DisplayName("Task 3.2.4: handleCaseExecutionException - 23514 with wrong message returns FAILED")
    void handleCaseExecutionException_23514_wrongMessage_failed() {
        Long caseId = 501L;
        MockPSQLException ex = new MockPSQLException("23514", new MockServerErrorMessage(
                null, "exec_stmt_raise", "Some other check constraint error",
                "PL/pgSQL function og_apply_case_decision() line 42 at RAISE"
        ));

        ReturnCaseDtos.CaseTimeoutExecutionResult result = service.handleCaseExecutionException(caseId, ex);

        assertThat(result.status()).isEqualTo(ReturnCaseDtos.TimeoutProcessingStatus.FAILED);
        assertThat(result.message()).contains("không thuộc allowlist (23514)");
    }

    @Test
    @DisplayName("Task 3.2.4: handleCaseExecutionException - 23514 with wrong where context returns FAILED")
    void handleCaseExecutionException_23514_wrongWhere_failed() {
        Long caseId = 501L;
        MockPSQLException ex = new MockPSQLException("23514", new MockServerErrorMessage(
                null, "exec_stmt_raise", "Case round already decided",
                "PL/pgSQL function unrelated_trigger() line 10"
        ));

        ReturnCaseDtos.CaseTimeoutExecutionResult result = service.handleCaseExecutionException(caseId, ex);

        assertThat(result.status()).isEqualTo(ReturnCaseDtos.TimeoutProcessingStatus.FAILED);
        assertThat(result.message()).contains("không thuộc allowlist (23514)");
    }

    @Test
    @DisplayName("Task 3.2.4: handleCaseExecutionException - 23505 deferred constraint when settlement consistent returns SKIPPED")
    void handleCaseExecutionException_23505_deferred_consistent_skipped() {
        Long caseId = 501L;
        Long round2Id = 702L;
        String cmdKey = "TIMEOUT:SELLER_POST_RETURN:" + caseId + ":" + round2Id;
        UUID compId = UUID.randomUUID();

        MockPSQLException ex = new MockPSQLException("23505", new MockServerErrorMessage(
                null, "exec_stmt_raise", "Relational constraint case_decisions_command_key_key violated", null
        ));

        when(jdbcTemplate.queryForList(contains("FROM og_compat.case_rounds"), eq(Long.class), eq(caseId)))
                .thenReturn(java.util.List.of(round2Id));
        when(jdbcTemplate.queryForList(contains("FROM og_compat.case_decisions d"), any(Object[].class)))
                .thenReturn(java.util.List.of(java.util.Map.of(
                        "decision_id", 888L,
                        "round_id", round2Id,
                        "actor_type", "SYSTEM_TIMEOUT",
                        "outcome", "FULL_GOODS_REFUND",
                        "command_key", cmdKey
                )));
        when(jdbcTemplate.queryForList(contains("SELECT order_id FROM og_compat.cases"), eq(Long.class), eq(caseId)))
                .thenReturn(java.util.List.of(orderId));

        // Mock settlement verification
        when(jdbcTemplate.queryForList(contains("SELECT buyer_id FROM og_compat.orders"), any(Object[].class)))
                .thenReturn(java.util.List.of(java.util.Map.of("buyer_id", buyerId)));
        when(jdbcTemplate.queryForList(contains("FROM og_compat.order_fund_components"), any(Object[].class)))
                .thenReturn(java.util.List.of(java.util.Map.of(
                        "component_id", compId,
                        "component_type", "GOODS_CASH",
                        "confirmed_amount", new BigDecimal("500000.00")
                )));
        when(jdbcTemplate.queryForList(contains("FROM og_compat.settlement_operations"), any(Object[].class)))
                .thenReturn(java.util.List.of(java.util.Map.ofEntries(
                        java.util.Map.entry("operation_id", 999L),
                        java.util.Map.entry("component_id", compId),
                        java.util.Map.entry("operation_type", "REFUND_BUYER"),
                        java.util.Map.entry("amount", new BigDecimal("500000.00")),
                        java.util.Map.entry("state", "REQUESTED"),
                        java.util.Map.entry("recipient_user_id", buyerId),
                        java.util.Map.entry("cause_type", "RETURN"),
                        java.util.Map.entry("command_key", cmdKey + ":GOODS_CASH")
                )));

        ReturnCaseDtos.CaseTimeoutExecutionResult result = service.handleCaseExecutionException(caseId, ex);

        assertThat(result.status()).isEqualTo(ReturnCaseDtos.TimeoutProcessingStatus.SKIPPED);
        assertThat(result.message()).contains("Idempotent: bản ghi timeout đã tồn tại (23505)");
    }

    @Test
    @DisplayName("Task 3.2.4: handleCaseExecutionException - 23505 deferred constraint when settlement missing returns FAILED")
    void handleCaseExecutionException_23505_deferred_missingSettlement_failed() {
        Long caseId = 501L;
        Long round2Id = 702L;
        String cmdKey = "TIMEOUT:SELLER_POST_RETURN:" + caseId + ":" + round2Id;
        UUID compId = UUID.randomUUID();

        MockPSQLException ex = new MockPSQLException("23505", new MockServerErrorMessage(
                null, "exec_stmt_raise", "Relational constraint case_decisions_command_key_key violated", null
        ));

        when(jdbcTemplate.queryForList(contains("FROM og_compat.case_rounds"), eq(Long.class), eq(caseId)))
                .thenReturn(java.util.List.of(round2Id));
        when(jdbcTemplate.queryForList(contains("FROM og_compat.case_decisions d"), any(Object[].class)))
                .thenReturn(java.util.List.of(java.util.Map.of(
                        "decision_id", 888L,
                        "round_id", round2Id,
                        "actor_type", "SYSTEM_TIMEOUT",
                        "outcome", "FULL_GOODS_REFUND",
                        "command_key", cmdKey
                )));
        when(jdbcTemplate.queryForList(contains("SELECT order_id FROM og_compat.cases"), eq(Long.class), eq(caseId)))
                .thenReturn(java.util.List.of(orderId));

        when(jdbcTemplate.queryForList(contains("SELECT buyer_id FROM og_compat.orders"), any(Object[].class)))
                .thenReturn(java.util.List.of(java.util.Map.of("buyer_id", buyerId)));
        // Mock fund component exists, but settlement operations EMPTY
        when(jdbcTemplate.queryForList(contains("FROM og_compat.order_fund_components"), any(Object[].class)))
                .thenReturn(java.util.List.of(java.util.Map.of(
                        "component_id", compId,
                        "component_type", "GOODS_CASH",
                        "confirmed_amount", new BigDecimal("500000.00")
                )));
        when(jdbcTemplate.queryForList(contains("FROM og_compat.settlement_operations"), any(Object[].class)))
                .thenReturn(java.util.List.of()); // Empty settlement!

        ReturnCaseDtos.CaseTimeoutExecutionResult result = service.handleCaseExecutionException(caseId, ex);

        assertThat(result.status()).isEqualTo(ReturnCaseDtos.TimeoutProcessingStatus.FAILED);
        assertThat(result.message()).contains("Decision tồn tại nhưng Settlement thiếu/sai");
    }

    @Test
    @DisplayName("Task 3.2.4: handleCaseExecutionException - 23505 constraint not in allowlist returns FAILED")
    void handleCaseExecutionException_23505_constraintNotInAllowlist_failed() {
        Long caseId = 501L;
        MockPSQLException ex = new MockPSQLException("23505", new MockServerErrorMessage(
                "uk_orders_reference", "exec_stmt_raise", "duplicate key value violates unique constraint", null
        ));

        ReturnCaseDtos.CaseTimeoutExecutionResult result = service.handleCaseExecutionException(caseId, ex);

        assertThat(result.status()).isEqualTo(ReturnCaseDtos.TimeoutProcessingStatus.FAILED);
        assertThat(result.message()).contains("Xung đột unique constraint không thuộc allowlist (23505)");
    }

    @Test
    @DisplayName("Task 3.2.4: handleCaseExecutionException - 55P03 lock timeout returns LOCKED_55P03")
    void handleCaseExecutionException_55P03_locked() {
        Long caseId = 501L;
        MockPSQLException ex = new MockPSQLException("55P03", new MockServerErrorMessage(
                null, null, "canceling statement due to lock timeout", null
        ));

        ReturnCaseDtos.CaseTimeoutExecutionResult result = service.handleCaseExecutionException(caseId, ex);

        assertThat(result.status()).isEqualTo(ReturnCaseDtos.TimeoutProcessingStatus.LOCKED_55P03);
        assertThat(result.message()).contains("Lock timeout (55P03)");
    }

    @Test
    @DisplayName("Task 3.2.4: handleCaseExecutionException - other unexpected error returns FAILED")
    void handleCaseExecutionException_unexpectedException_failed() {
        Long caseId = 501L;
        RuntimeException ex = new RuntimeException("Database connection terminated abnormally");

        ReturnCaseDtos.CaseTimeoutExecutionResult result = service.handleCaseExecutionException(caseId, ex);

        assertThat(result.status()).isEqualTo(ReturnCaseDtos.TimeoutProcessingStatus.FAILED);
        assertThat(result.message()).contains("Database connection terminated abnormally");
    }

    // =========================================================================
    // TASK 3.2.5: KTV SLA ESCALATION (reconcileKtvOverdueSla) & BD-1 / BD-3 / BD-4 TESTS
    // =========================================================================

    @Test
    @DisplayName("Task 3.2.5 / BD-1: ktvDecideRound2 - KTV decision after escalation is rejected with SecurityException")
    void ktvDecideRound2_whenEscalated_andKtvAttempts_rejectedWithSecurityException() {
        Long caseId = 501L;
        Long ktvId = 300L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.existsByCaseIdAndRoundIdAndEventType(caseId, round2.getId(), CaseEventEntity.TYPE_ESCALATED))
                .thenReturn(true);
        // KTV is not admin
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'ADMIN')"), eq(Boolean.class), eq(ktvId)))
                .thenReturn(Boolean.FALSE);

        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "KTV cố gắng ra quyết định sau khi hồ sơ đã leo thang",
                "CMD_KTV_AFTER_ESCALATION"
        );

        assertThatThrownBy(() -> service.ktvDecideRound2(ktvId, caseId, req))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Quản trị viên (Admin)");
        verify(caseDecisionRepository, never()).saveAndFlush(any());
        verify(settlementPort, never()).requestSettlement(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Task 3.2.5 / BD-1: adminDecideRound2 - Admin decision after escalation succeeds and closes case")
    void adminDecideRound2_whenEscalated_adminSucceeds() {
        Long caseId = 501L;
        Long adminId = 1L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseRoundRepository.findLatestRoundByCaseId(caseId)).thenReturn(Optional.of(round2));
        when(caseDecisionRepository.findByCommandKey(anyString())).thenReturn(Optional.empty());
        when(caseEventRepository.existsByCaseIdAndRoundIdAndEventType(caseId, round2.getId(), CaseEventEntity.TYPE_ESCALATED))
                .thenReturn(true);
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'ADMIN')"), eq(Boolean.class), eq(adminId)))
                .thenReturn(Boolean.TRUE);
        when(caseDecisionRepository.saveAndFlush(any(CaseDecisionEntity.class))).thenAnswer(inv -> {
            CaseDecisionEntity d = inv.getArgument(0);
            d.setId(905L);
            return d;
        });
        when(caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(round2.getId())).thenAnswer(inv -> {
            CaseDecisionEntity d = new CaseDecisionEntity(round2.getId(), adminId, CaseDecisionEntity.ACTOR_USER,
                    CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND, true, new BigDecimal("500000.00"),
                    "Admin phán quyết hoàn tiền sau leo thang", "CMD_ADMIN_R2_FULL");
            d.setId(905L);
            return List.of(d);
        });

        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Admin phán quyết hoàn tiền sau leo thang",
                "CMD_ADMIN_R2_FULL"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse response = service.adminDecideRound2(adminId, caseId, req);

        assertThat(response).isNotNull();
        assertThat(returnCase.getState()).isEqualTo(CaseEntity.STATE_CLOSED);
        assertThat(round2.getState()).isEqualTo(CaseRoundEntity.STATE_DECIDED);
        verify(caseDecisionRepository).saveAndFlush(any(CaseDecisionEntity.class));
        verify(settlementPort).requestSettlement(eq(orderId), eq(caseId), eq(905L), eq(CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND), eq("CMD_ADMIN_R2_FULL"));
    }

    @Test
    @DisplayName("Task 3.2.5 / BD-4: scanOverdueKtvSlaCaseIds queries repository with threshold now - 48h")
    void scanOverdueKtvSlaCaseIds_queriesWithThresholdMinus48Hours() {
        when(caseRepository.findOverdueKtvSlaCaseIds(any(Instant.class))).thenReturn(List.of(101L, 102L));

        List<Long> result = service.scanOverdueKtvSlaCaseIds();

        assertThat(result).containsExactly(101L, 102L);
        verify(caseRepository).findOverdueKtvSlaCaseIds(fixedNow.minus(Duration.ofHours(48)));
    }

    @Test
    @DisplayName("Task 3.2.5 / BD-4: processKtvSlaEscalation - case with complete_submission_at IS NULL is skipped")
    void processKtvSlaEscalation_completeSubmissionAtNull_skipped() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);
        round2.setCompleteSubmissionAt(null); // BD-4: NULL -> not eligible for SLA

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));

        ReturnCaseDtos.CaseEscalationExecutionResult result = service.processKtvSlaEscalationForCaseInNewTx(caseId);

        assertThat(result.status()).isEqualTo(ReturnCaseDtos.EscalationProcessingStatus.SKIPPED);
        assertThat(result.message()).contains("complete_submission_at IS NULL");
        verify(caseEventRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Task 3.2.5 / BD-4: processKtvSlaEscalation - case within 48h SLA is skipped")
    void processKtvSlaEscalation_within48Hours_skipped() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);
        round2.setCompleteSubmissionAt(fixedNow.minus(Duration.ofHours(24))); // Only 24h passed

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));

        ReturnCaseDtos.CaseEscalationExecutionResult result = service.processKtvSlaEscalationForCaseInNewTx(caseId);

        assertThat(result.status()).isEqualTo(ReturnCaseDtos.EscalationProcessingStatus.SKIPPED);
        assertThat(result.message()).contains("Chưa quá 48 giờ SLA");
        verify(caseEventRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Task 3.2.5 / BD-3: processKtvSlaEscalation - case past 48h SLA is escalated successfully with SYSTEM actor and ADMIN recipient")
    void processKtvSlaEscalation_past48Hours_escalatedSuccessfully() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);
        round2.setCompleteSubmissionAt(fixedNow.minus(Duration.ofHours(50))); // 50h passed (> 48h)

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseRoundRepository.findLatestRoundByCaseId(caseId)).thenReturn(Optional.of(round2));
        when(caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(round2.getId())).thenReturn(List.of());
        when(caseEventRepository.findByCommandKey(anyString())).thenReturn(Optional.empty());
        when(caseEventRepository.existsByCaseIdAndRoundIdAndEventType(caseId, round2.getId(), CaseEventEntity.TYPE_ESCALATED)).thenReturn(false);
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class))).thenReturn(List.of(1L));

        ReturnCaseDtos.CaseEscalationExecutionResult result = service.processKtvSlaEscalationForCaseInNewTx(caseId);

        assertThat(result.status()).isEqualTo(ReturnCaseDtos.EscalationProcessingStatus.ESCALATED);
        assertThat(result.message()).contains("Thành công tự động leo thang lên Quản trị viên");

        ArgumentCaptor<CaseEventEntity> eventCaptor = ArgumentCaptor.forClass(CaseEventEntity.class);
        verify(caseEventRepository).saveAndFlush(eventCaptor.capture());
        CaseEventEntity captured = eventCaptor.getValue();
        assertThat(captured.getCaseId()).isEqualTo(caseId);
        assertThat(captured.getRoundId()).isEqualTo(round2.getId());
        assertThat(captured.getActorId()).isNull(); // SYSTEM actor has actorId = null
        assertThat(captured.getRecipientId()).isEqualTo(1L); // ADMIN recipient
        assertThat(captured.getEventType()).isEqualTo(CaseEventEntity.TYPE_ESCALATED);
        assertThat(captured.getCommandKey()).isEqualTo("EVENT:ESCALATED:R2:" + caseId);
        assertThat(captured.getMetadata()).contains("KTV_SLA_EXPIRED_48H");
    }

    @Test
    @DisplayName("Task 3.2.5: processKtvSlaEscalation - case with final decision is skipped")
    void processKtvSlaEscalation_hasFinalDecision_skipped() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);
        round2.setCompleteSubmissionAt(fixedNow.minus(Duration.ofHours(50)));

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        CaseDecisionEntity decision = new CaseDecisionEntity(round2.getId(), 300L, CaseDecisionEntity.ACTOR_USER,
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND, true, BigDecimal.TEN, "reason", "cmd");
        when(caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(round2.getId())).thenReturn(List.of(decision));

        ReturnCaseDtos.CaseEscalationExecutionResult result = service.processKtvSlaEscalationForCaseInNewTx(caseId);

        assertThat(result.status()).isEqualTo(ReturnCaseDtos.EscalationProcessingStatus.SKIPPED);
        assertThat(result.message()).contains("Final Decision");
        verify(caseEventRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Task 3.2.5: processKtvSlaEscalation - already escalated case is skipped (idempotent)")
    void processKtvSlaEscalation_alreadyEscalated_idempotentSkipped() {
        Long caseId = 501L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);
        round2.setCompleteSubmissionAt(fixedNow.minus(Duration.ofHours(50)));

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(round2.getId())).thenReturn(List.of());
        when(caseEventRepository.findByCommandKey("EVENT:ESCALATED:R2:" + caseId)).thenReturn(Optional.of(
                new CaseEventEntity(UUID.randomUUID(), caseId, round2.getId(), null, 1L, CaseEventEntity.TYPE_ESCALATED, "Already escalated", "{}", "EVENT:ESCALATED:R2:" + caseId)
        ));

        ReturnCaseDtos.CaseEscalationExecutionResult result = service.processKtvSlaEscalationForCaseInNewTx(caseId);

        assertThat(result.status()).isEqualTo(ReturnCaseDtos.EscalationProcessingStatus.SKIPPED);
        assertThat(result.message()).contains("idempotent");
        verify(caseEventRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Task 3.2.5: handleEscalationExecutionException maps SQLSTATE 55P03, 23514, 23505 correctly")
    void handleEscalationExecutionException_errorMapping() {
        Long caseId = 501L;

        // 55P03
        MockPSQLException ex55P03 = new MockPSQLException("55P03", new MockServerErrorMessage(null, null, "lock timeout", null));
        ReturnCaseDtos.CaseEscalationExecutionResult r55P03 = service.handleEscalationExecutionException(caseId, ex55P03);
        assertThat(r55P03.status()).isEqualTo(ReturnCaseDtos.EscalationProcessingStatus.LOCKED_55P03);

        // 23514
        MockPSQLException ex23514 = new MockPSQLException("23514", new MockServerErrorMessage(null, null, "Escalation needs Admin recipient", null));
        ReturnCaseDtos.CaseEscalationExecutionResult r23514 = service.handleEscalationExecutionException(caseId, ex23514);
        assertThat(r23514.status()).isEqualTo(ReturnCaseDtos.EscalationProcessingStatus.SKIPPED);

        // 23505
        MockPSQLException ex23505 = new MockPSQLException("23505", new MockServerErrorMessage(null, null, "unique key duplicate", null));
        ReturnCaseDtos.CaseEscalationExecutionResult r23505 = service.handleEscalationExecutionException(caseId, ex23505);
        assertThat(r23505.status()).isEqualTo(ReturnCaseDtos.EscalationProcessingStatus.SKIPPED);

        // Generic error
        RuntimeException exGeneric = new RuntimeException("Unexpected IO exception");
        ReturnCaseDtos.CaseEscalationExecutionResult rGeneric = service.handleEscalationExecutionException(caseId, exGeneric);
        assertThat(rGeneric.status()).isEqualTo(ReturnCaseDtos.EscalationProcessingStatus.FAILED);
    }

    @Test
    @DisplayName("Task 3.2.5: reconcileKtvOverdueSla batch loop handles multi-case and isolates failures")
    void reconcileKtvOverdueSla_batchProcessing_isolatesFailures() {
        when(caseRepository.findOverdueKtvSlaCaseIds(any(Instant.class))).thenReturn(List.of(101L, 102L));

        // Case 101 succeeds
        CaseEntity case101 = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_101");
        case101.setId(101L);
        case101.setState(CaseEntity.STATE_POST_RETURN);
        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round101 = new CaseRoundEntity(101L, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round101.setId(701L);
        round101.setState(CaseRoundEntity.STATE_OPEN);
        round101.setCompleteSubmissionAt(fixedNow.minus(Duration.ofHours(50)));

        when(caseRepository.findById(101L)).thenReturn(Optional.of(case101));
        when(caseRoundRepository.findByCaseIdAndRoundNo(101L, 2)).thenReturn(Optional.of(round101));
        when(caseRoundRepository.findLatestRoundByCaseId(101L)).thenReturn(Optional.of(round101));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(701L)).thenReturn(List.of());
        when(caseEventRepository.findByCommandKey("EVENT:ESCALATED:R2:101")).thenReturn(Optional.empty());
        when(caseEventRepository.existsByCaseIdAndRoundIdAndEventType(101L, 701L, CaseEventEntity.TYPE_ESCALATED)).thenReturn(false);
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class))).thenReturn(List.of(1L));

        // Case 102 fails with exception (not found)
        when(caseRepository.findById(102L)).thenReturn(Optional.empty());

        ReturnCaseDtos.EscalationBatchResult batchResult = service.reconcileKtvOverdueSla();

        assertThat(batchResult.totalScanned()).isEqualTo(2);
        assertThat(batchResult.escalatedCaseIds()).containsExactly(101L);
        assertThat(batchResult.skippedCaseIds()).containsExactly(102L);
        assertThat(batchResult.failedCaseIds()).isEmpty();
    }

    // =========================================================================
    // D-08 UNIT TESTS: KTV Final Decision & Evidence Request Cancellation
    // =========================================================================

    @Test
    @DisplayName("D-08 / PO APPROVED: ktvDecideRound2 thành công khi có Request PENDING -> Request chuyển sang CANCELLED")
    void ktvDecideRound2_D08_whenPendingRequestExists_cancelsRequestInSameTx() {
        Long caseId = 501L;
        Long ktvId = 300L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.existsByCaseIdAndRoundIdAndEventType(caseId, round2.getId(), CaseEventEntity.TYPE_ESCALATED))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV')"), eq(Boolean.class), eq(ktvId)))
                .thenReturn(Boolean.TRUE);
        when(caseDecisionRepository.findByCommandKey(anyString())).thenReturn(Optional.empty());

        CaseDecisionEntity savedDecision = new CaseDecisionEntity(round2.getId(), ktvId, CaseDecisionEntity.ACTOR_USER,
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND, true, new BigDecimal("250000.00"), "Lý do hợp lệ", "CMD_DEC_01");
        savedDecision.setId(991L);
        when(caseDecisionRepository.saveAndFlush(any())).thenReturn(savedDecision);

        // Setup active PENDING evidence request
        CaseEvidenceRequestEntity pendingReq = new CaseEvidenceRequestEntity();
        pendingReq.setId(UUID.randomUUID());
        pendingReq.setCaseId(caseId);
        pendingReq.setRoundId(round2.getId());
        pendingReq.setKtvId(ktvId);
        pendingReq.setStatus(CaseEvidenceRequestEntity.STATUS_PENDING);
        pendingReq.setIssuedAt(fixedNow.minus(Duration.ofHours(10)));
        pendingReq.setDeadlineAt(fixedNow.plus(Duration.ofHours(38)));
        pendingReq.setCommandKey("REQ-CMD-001");
        assertThat(pendingReq.getStatus()).isEqualTo(CaseEvidenceRequestEntity.STATUS_PENDING);

        when(caseEvidenceRequestRepository.findByCaseIdOrderByIssuedAtAsc(caseId))
                .thenReturn(List.of(pendingReq));

        ReturnCaseDtos.KtvRound2DecisionRequest decReq = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Đã kiểm tra bằng chứng đầy đủ, duyệt hoàn tiền",
                "CMD_DEC_01"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse resp = service.ktvDecideRound2(ktvId, caseId, decReq);

        assertThat(resp).isNotNull();
        // D-08 Assertion: Request MUST be CANCELLED and updated
        assertThat(pendingReq.getStatus()).isEqualTo(CaseEvidenceRequestEntity.STATUS_CANCELLED);
        assertThat(pendingReq.getUpdatedAt()).isEqualTo(fixedNow);
        verify(caseEvidenceRequestRepository).save(pendingReq);
        verify(caseEvidenceRequestRepository).flush();
        verify(caseDecisionRepository).saveAndFlush(any());
        verify(settlementPort).requestSettlement(eq(orderId), eq(caseId), eq(991L), eq(CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND), eq("CMD_DEC_01"));
    }

    @Test
    @DisplayName("D-08 / PO APPROVED: ktvDecideRound2 giữ nguyên các Request FULFILLED, EXPIRED, CANCELLED")
    void ktvDecideRound2_D08_preservesFulfilledExpiredAndCancelledRequests() {
        Long caseId = 501L;
        Long ktvId = 300L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.existsByCaseIdAndRoundIdAndEventType(caseId, round2.getId(), CaseEventEntity.TYPE_ESCALATED))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV')"), eq(Boolean.class), eq(ktvId)))
                .thenReturn(Boolean.TRUE);
        when(caseDecisionRepository.findByCommandKey(anyString())).thenReturn(Optional.empty());

        CaseDecisionEntity savedDecision = new CaseDecisionEntity(round2.getId(), ktvId, CaseDecisionEntity.ACTOR_USER,
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND, true, new BigDecimal("250000.00"), "Lý do hợp lệ", "CMD_DEC_02");
        savedDecision.setId(992L);
        when(caseDecisionRepository.saveAndFlush(any())).thenReturn(savedDecision);

        CaseEvidenceRequestEntity fulfilledReq = new CaseEvidenceRequestEntity();
        fulfilledReq.setId(UUID.randomUUID());
        fulfilledReq.setCaseId(caseId);
        fulfilledReq.setRoundId(round2.getId());
        fulfilledReq.setKtvId(ktvId);
        fulfilledReq.setReason("Req 1");
        fulfilledReq.setIssuedAt(fixedNow.minus(Duration.ofHours(40)));
        fulfilledReq.setDeadlineAt(fixedNow.plus(Duration.ofHours(8)));
        fulfilledReq.setCommandKey("CMD-1");
        fulfilledReq.setStatus(CaseEvidenceRequestEntity.STATUS_FULFILLED);

        CaseEvidenceRequestEntity expiredReq = new CaseEvidenceRequestEntity();
        expiredReq.setId(UUID.randomUUID());
        expiredReq.setCaseId(caseId);
        expiredReq.setRoundId(round2.getId());
        expiredReq.setKtvId(ktvId);
        expiredReq.setReason("Req 2");
        expiredReq.setIssuedAt(fixedNow.minus(Duration.ofHours(60)));
        expiredReq.setDeadlineAt(fixedNow.minus(Duration.ofHours(12)));
        expiredReq.setCommandKey("CMD-2");
        expiredReq.setStatus(CaseEvidenceRequestEntity.STATUS_EXPIRED);

        CaseEvidenceRequestEntity alreadyCancelledReq = new CaseEvidenceRequestEntity();
        alreadyCancelledReq.setId(UUID.randomUUID());
        alreadyCancelledReq.setCaseId(caseId);
        alreadyCancelledReq.setRoundId(round2.getId());
        alreadyCancelledReq.setKtvId(ktvId);
        alreadyCancelledReq.setReason("Req 3");
        alreadyCancelledReq.setIssuedAt(fixedNow.minus(Duration.ofHours(20)));
        alreadyCancelledReq.setDeadlineAt(fixedNow.plus(Duration.ofHours(28)));
        alreadyCancelledReq.setCommandKey("CMD-3");
        alreadyCancelledReq.setStatus(CaseEvidenceRequestEntity.STATUS_CANCELLED);

        when(caseEvidenceRequestRepository.findByCaseIdOrderByIssuedAtAsc(caseId))
                .thenReturn(List.of(fulfilledReq, expiredReq, alreadyCancelledReq));

        ReturnCaseDtos.KtvRound2DecisionRequest decReq = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Duyệt hoàn tiền, không có pending request",
                "CMD_DEC_02"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse resp = service.ktvDecideRound2(ktvId, caseId, decReq);

        assertThat(resp).isNotNull();
        // Assert non-pending statuses are untouched
        assertThat(fulfilledReq.getStatus()).isEqualTo(CaseEvidenceRequestEntity.STATUS_FULFILLED);
        assertThat(expiredReq.getStatus()).isEqualTo(CaseEvidenceRequestEntity.STATUS_EXPIRED);
        assertThat(alreadyCancelledReq.getStatus()).isEqualTo(CaseEvidenceRequestEntity.STATUS_CANCELLED);
        verify(caseEvidenceRequestRepository, never()).save(any());
        verify(caseEvidenceRequestRepository, never()).flush();
    }

    @Test
    @DisplayName("D-08 / PO APPROVED: Nếu có nhiều Request PENDING của Case -> tất cả đều được chuyển thành CANCELLED")
    void ktvDecideRound2_D08_multiplePendingRequests_allCancelled() {
        Long caseId = 501L;
        Long ktvId = 300L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.existsByCaseIdAndRoundIdAndEventType(caseId, round2.getId(), CaseEventEntity.TYPE_ESCALATED))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV')"), eq(Boolean.class), eq(ktvId)))
                .thenReturn(Boolean.TRUE);
        when(caseDecisionRepository.findByCommandKey(anyString())).thenReturn(Optional.empty());

        CaseDecisionEntity savedDecision = new CaseDecisionEntity(round2.getId(), ktvId, CaseDecisionEntity.ACTOR_USER,
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND, true, new BigDecimal("250000.00"), "Lý do", "CMD_DEC_03");
        savedDecision.setId(993L);
        when(caseDecisionRepository.saveAndFlush(any())).thenReturn(savedDecision);

        CaseEvidenceRequestEntity req1 = new CaseEvidenceRequestEntity();
        req1.setId(UUID.randomUUID());
        req1.setCaseId(caseId);
        req1.setRoundId(round2.getId());
        req1.setKtvId(ktvId);
        req1.setReason("Req 1");
        req1.setIssuedAt(fixedNow.minus(Duration.ofHours(10)));
        req1.setDeadlineAt(fixedNow.plus(Duration.ofHours(38)));
        req1.setCommandKey("CMD-1");
        req1.setStatus(CaseEvidenceRequestEntity.STATUS_PENDING);

        CaseEvidenceRequestEntity req2 = new CaseEvidenceRequestEntity();
        req2.setId(UUID.randomUUID());
        req2.setCaseId(caseId);
        req2.setRoundId(round2.getId());
        req2.setKtvId(ktvId);
        req2.setReason("Req 2");
        req2.setIssuedAt(fixedNow.minus(Duration.ofHours(5)));
        req2.setDeadlineAt(fixedNow.plus(Duration.ofHours(43)));
        req2.setCommandKey("CMD-2");
        req2.setStatus(CaseEvidenceRequestEntity.STATUS_PENDING);

        when(caseEvidenceRequestRepository.findByCaseIdOrderByIssuedAtAsc(caseId))
                .thenReturn(List.of(req1, req2));

        ReturnCaseDtos.KtvRound2DecisionRequest decReq = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Quyết định hoàn tiền cho người mua",
                "CMD_DEC_03"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse resp = service.ktvDecideRound2(ktvId, caseId, decReq);

        assertThat(resp).isNotNull();
        assertThat(req1.getStatus()).isEqualTo(CaseEvidenceRequestEntity.STATUS_CANCELLED);
        assertThat(req2.getStatus()).isEqualTo(CaseEvidenceRequestEntity.STATUS_CANCELLED);
        verify(caseEvidenceRequestRepository).save(req1);
        verify(caseEvidenceRequestRepository).save(req2);
        verify(caseEvidenceRequestRepository).flush();
    }

    @Test
    @DisplayName("D-08 / PO APPROVED: Request quá hạn nhưng vẫn PENDING (D-02 Option A) -> KTV vẫn quyết định được và chuyển CANCELLED")
    void ktvDecideRound2_D08_pastDeadlinePendingRequest_stillCancelled() {
        Long caseId = 501L;
        Long ktvId = 300L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(ktvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(60)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.existsByCaseIdAndRoundIdAndEventType(caseId, round2.getId(), CaseEventEntity.TYPE_ESCALATED))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV')"), eq(Boolean.class), eq(ktvId)))
                .thenReturn(Boolean.TRUE);
        when(caseDecisionRepository.findByCommandKey(anyString())).thenReturn(Optional.empty());

        CaseDecisionEntity savedDecision = new CaseDecisionEntity(round2.getId(), ktvId, CaseDecisionEntity.ACTOR_USER,
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND, true, new BigDecimal("250000.00"), "Lý do", "CMD_DEC_04");
        savedDecision.setId(994L);
        when(caseDecisionRepository.saveAndFlush(any())).thenReturn(savedDecision);

        // Overdue request still PENDING in DB
        CaseEvidenceRequestEntity overduePendingReq = new CaseEvidenceRequestEntity();
        overduePendingReq.setId(UUID.randomUUID());
        overduePendingReq.setCaseId(caseId);
        overduePendingReq.setRoundId(round2.getId());
        overduePendingReq.setKtvId(ktvId);
        overduePendingReq.setReason("Yêu cầu đã quá 48h");
        overduePendingReq.setIssuedAt(fixedNow.minus(Duration.ofHours(50)));
        overduePendingReq.setDeadlineAt(fixedNow.minus(Duration.ofHours(2)));
        overduePendingReq.setCommandKey("CMD-OVERDUE");
        overduePendingReq.setStatus(CaseEvidenceRequestEntity.STATUS_PENDING);

        when(caseEvidenceRequestRepository.findByCaseIdOrderByIssuedAtAsc(caseId))
                .thenReturn(List.of(overduePendingReq));

        ReturnCaseDtos.KtvRound2DecisionRequest decReq = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Quyết định dựa trên chứng từ hiện có dù request đã quá 48h (BD5-18)",
                "CMD_DEC_04"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse resp = service.ktvDecideRound2(ktvId, caseId, decReq);

        assertThat(resp).isNotNull();
        assertThat(overduePendingReq.getStatus()).isEqualTo(CaseEvidenceRequestEntity.STATUS_CANCELLED);
        verify(caseEvidenceRequestRepository).save(overduePendingReq);
    }

    @Test
    @DisplayName("D-08 / PO APPROVED: Command-key replay -> không lặp lại việc cancel request hoặc side effect")
    void ktvDecideRound2_D08_idempotentReplay_doesNotReinvokeCancellation() {
        Long caseId = 501L;
        Long ktvId = 300L;
        String replayCmdKey = "REPLAY-CMD-001";

        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_CASE");
        returnCase.setId(caseId);
        returnCase.setAssignedTo(ktvId);
        returnCase.setState(CaseEntity.STATE_CLOSED);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_DECIDED);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.existsByCaseIdAndRoundIdAndEventType(caseId, round2.getId(), CaseEventEntity.TYPE_ESCALATED))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV')"), eq(Boolean.class), eq(ktvId)))
                .thenReturn(Boolean.TRUE);

        CaseDecisionEntity existingDecision = new CaseDecisionEntity(round2.getId(), ktvId, CaseDecisionEntity.ACTOR_USER,
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND, true, new BigDecimal("250000.00"), "Lý do", replayCmdKey);
        when(caseDecisionRepository.findByCommandKey(replayCmdKey)).thenReturn(Optional.of(existingDecision));

        ReturnCaseDtos.KtvRound2DecisionRequest decReq = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Lý do replay",
                replayCmdKey
        );

        ReturnCaseDtos.ReturnCaseDetailResponse resp = service.ktvDecideRound2(ktvId, caseId, decReq);

        assertThat(resp).isNotNull();
        // Zero interactions with evidence request repo during idempotent replay
        verify(caseEvidenceRequestRepository, never()).findByCaseIdOrderByIssuedAtAsc(any());
        verify(caseDecisionRepository, never()).saveAndFlush(any());
        verify(settlementPort, never()).requestSettlement(any(), any(), any(), any(), any());
    }

    // =========================================================================
    // OPEN-02-RECON (Option B): Review Request Rejection When Settlement Exists
    // =========================================================================

    @Test
    @DisplayName("OPEN-02-RECON: Review Request is REJECTED with 409 CONFLICT when settlement operation is in REQUESTED state")
    void submitReviewRequest_rejected_whenSettlementInRequested() {
        Long caseId = 901L;
        Long adminId = 999L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Return", "CMD_CASE_901");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_CLOSED);

        OrderEntity order = createValidDeliveredOrder();

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // 1 settlement operation exists in REQUESTED state
        when(jdbcTemplate.queryForObject(contains("FROM og_compat.settlement_operations so"), eq(Integer.class), eq(caseId)))
                .thenReturn(1);

        ReturnCaseDtos.CreateReviewRequest req = new ReturnCaseDtos.CreateReviewRequest(
                "Yêu cầu xem xét lại phán quyết của KTV", "CMD_REV_1"
        );

        assertThatThrownBy(() -> service.submitReviewRequest(caseId, adminId, req))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
                    assertThat(tsEx.getCode()).isEqualTo("CASE_SETTLEMENT_EXISTS");
                    assertThat(tsEx.getMessage()).contains("Không thể mở lại hồ sơ khi đã tồn tại giao dịch thanh toán");
                });

        // Verify case state was NOT mutated
        assertThat(returnCase.getState()).isEqualTo(CaseEntity.STATE_CLOSED);
        verify(caseRepository, never()).save(any());
        verify(caseEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("OPEN-02-RECON: Review Request is REJECTED with 409 CONFLICT when settlement operation is in PROCESSING state")
    void submitReviewRequest_rejected_whenSettlementInProcessing() {
        Long caseId = 902L;
        Long adminId = 999L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Return", "CMD_CASE_902");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_CLOSED);

        OrderEntity order = createValidDeliveredOrder();

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        when(jdbcTemplate.queryForObject(contains("FROM og_compat.settlement_operations so"), eq(Integer.class), eq(caseId)))
                .thenReturn(1);

        ReturnCaseDtos.CreateReviewRequest req = new ReturnCaseDtos.CreateReviewRequest(
                "Yêu cầu xem xét lại phán quyết khi đang xử lý ngân hàng", "CMD_REV_2"
        );

        assertThatThrownBy(() -> service.submitReviewRequest(caseId, adminId, req))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
                    assertThat(tsEx.getCode()).isEqualTo("CASE_SETTLEMENT_EXISTS");
                });

        assertThat(returnCase.getState()).isEqualTo(CaseEntity.STATE_CLOSED);
    }

    @Test
    @DisplayName("OPEN-02-RECON: Review Request is REJECTED with 409 CONFLICT when settlement operation is in CONFIRMED state")
    void submitReviewRequest_rejected_whenSettlementInConfirmed() {
        Long caseId = 903L;
        Long adminId = 999L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Return", "CMD_CASE_903");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_CLOSED);

        OrderEntity order = createValidDeliveredOrder();

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        when(jdbcTemplate.queryForObject(contains("FROM og_compat.settlement_operations so"), eq(Integer.class), eq(caseId)))
                .thenReturn(1);

        ReturnCaseDtos.CreateReviewRequest req = new ReturnCaseDtos.CreateReviewRequest(
                "Yêu cầu xem xét lại sau khi đã hoàn tất giải ngân", "CMD_REV_3"
        );

        assertThatThrownBy(() -> service.submitReviewRequest(caseId, adminId, req))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
                    assertThat(tsEx.getCode()).isEqualTo("CASE_SETTLEMENT_EXISTS");
                });

        assertThat(returnCase.getState()).isEqualTo(CaseEntity.STATE_CLOSED);
    }

    @Test
    @DisplayName("OPEN-02-RECON: Review Request is REJECTED with 409 CONFLICT when settlement operation is in FAILED state")
    void submitReviewRequest_rejected_whenSettlementInFailed() {
        Long caseId = 904L;
        Long adminId = 999L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Return", "CMD_CASE_904");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_CLOSED);

        OrderEntity order = createValidDeliveredOrder();

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        when(jdbcTemplate.queryForObject(contains("FROM og_compat.settlement_operations so"), eq(Integer.class), eq(caseId)))
                .thenReturn(1);

        ReturnCaseDtos.CreateReviewRequest req = new ReturnCaseDtos.CreateReviewRequest(
                "Yêu cầu xem xét lại khi settlement gặp lỗi", "CMD_REV_4"
        );

        assertThatThrownBy(() -> service.submitReviewRequest(caseId, adminId, req))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
                    assertThat(tsEx.getCode()).isEqualTo("CASE_SETTLEMENT_EXISTS");
                });

        assertThat(returnCase.getState()).isEqualTo(CaseEntity.STATE_CLOSED);
    }

    @Test
    @DisplayName("OPEN-02-RECON: Review Request is ACCEPTED when NO settlement operations exist for the Case")
    void submitReviewRequest_accepted_whenNoSettlementOperationsExist() {
        Long caseId = 905L;
        Long adminId = 999L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Return", "CMD_CASE_905");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_REJECTED);

        OrderEntity order = createValidDeliveredOrder();

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // 0 settlement operations exist
        when(jdbcTemplate.queryForObject(contains("FROM og_compat.settlement_operations so"), eq(Integer.class), eq(caseId)))
                .thenReturn(0);

        ReturnCaseDtos.CreateReviewRequest req = new ReturnCaseDtos.CreateReviewRequest(
                "Người mua khiếu nại quyết định từ chối do có chứng cứ mới", "CMD_REV_5"
        );

        ReturnCaseDtos.ReviewRequestResponse resp = service.submitReviewRequest(caseId, adminId, req);

        assertThat(resp).isNotNull();
        assertThat(resp.caseId()).isEqualTo(caseId);
        assertThat(resp.status()).isEqualTo("REVIEW_REQUESTED");

        // Verify audit event was saved and Case remained in terminal state (Admin cannot unilaterally reopen)
        verify(caseEventRepository).save(any(CaseEventEntity.class));
        assertThat(returnCase.getState()).isEqualTo(CaseEntity.STATE_REJECTED);
    }

    @Test
    @DisplayName("OPEN-02-RECON: Rejects review request when Case is NOT in terminal state (CLOSED or REJECTED)")
    void submitReviewRequest_rejected_whenCaseNotTerminal() {
        Long caseId = 906L;
        Long adminId = 999L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Return", "CMD_CASE_906");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN); // Not CLOSED or REJECTED!

        OrderEntity order = createValidDeliveredOrder();

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        ReturnCaseDtos.CreateReviewRequest req = new ReturnCaseDtos.CreateReviewRequest(
                "Hồ sơ chưa kết thúc không được yêu cầu xem xét lại", "CMD_REV_6"
        );

        assertThatThrownBy(() -> service.submitReviewRequest(caseId, adminId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Chỉ có thể yêu cầu xem xét lại đối với hồ sơ đã kết thúc");
    }

    @Test
    @DisplayName("OPEN-02-RECON: Rejects review request with invalid inputs")
    void submitReviewRequest_rejectsInvalidInputs() {
        ReturnCaseDtos.CreateReviewRequest validReq = new ReturnCaseDtos.CreateReviewRequest(
                "Lý do hợp lệ có độ dài trên 10 ký tự", "CMD"
        );

        assertThatThrownBy(() -> service.submitReviewRequest(null, 999L, validReq))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("caseId");

        assertThatThrownBy(() -> service.submitReviewRequest(907L, null, validReq))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("adminId");

        ReturnCaseDtos.CreateReviewRequest shortReq = new ReturnCaseDtos.CreateReviewRequest("Ngắn", "CMD");
        assertThatThrownBy(() -> service.submitReviewRequest(907L, 999L, shortReq))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tối thiểu 10 ký tự");
    }

    // =========================================================================
    // TASK 5.2 REMEDIATION: Assignment Guard Tests for Round 1 and Round 2
    // =========================================================================

    @Test
    @DisplayName("Task 5.2 Remediation: ktvDecideRound1 throws 403 NOT_ASSIGNED_KTV when case has assignedTo == null")
    void ktvDecideRound1_unassignedCase_rejected403() {
        Long caseId = 501L;
        Long testKtvId = 300L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "[NOT_AS_DESCRIBED] Detail", "CMD_R1_UNASSIGNED");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_REVIEWING);
        returnCase.setAssignedTo(null);

        OrderEntity order = createValidDeliveredOrder();
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        ReturnCaseDtos.KtvRound1DecisionRequest request = new ReturnCaseDtos.KtvRound1DecisionRequest(
                CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                "KTV xác minh ảnh sản phẩm giao sai màu",
                "CMD_KTV_R1_UNASSIGNED"
        );

        assertThatThrownBy(() -> service.ktvDecideRound1(testKtvId, caseId, request))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(tsEx.getCode()).isEqualTo("NOT_ASSIGNED_KTV");
                });
    }

    @Test
    @DisplayName("Task 5.2 Remediation: ktvDecideRound1 throws 403 NOT_ASSIGNED_KTV when case has assignedTo != ktvId")
    void ktvDecideRound1_differentKtv_rejected403() {
        Long caseId = 501L;
        Long testKtvId = 300L;
        Long otherKtvId = 999L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "[NOT_AS_DESCRIBED] Detail", "CMD_R1_DIFF");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_REVIEWING);
        returnCase.setAssignedTo(otherKtvId);

        OrderEntity order = createValidDeliveredOrder();
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        ReturnCaseDtos.KtvRound1DecisionRequest request = new ReturnCaseDtos.KtvRound1DecisionRequest(
                CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                "KTV xác minh ảnh sản phẩm giao sai màu",
                "CMD_KTV_R1_DIFF"
        );

        assertThatThrownBy(() -> service.ktvDecideRound1(testKtvId, caseId, request))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(tsEx.getCode()).isEqualTo("NOT_ASSIGNED_KTV");
                });
    }

    @Test
    @DisplayName("Task 5.2 Remediation: ktvDecideRound1 succeeds when assignedTo == ktvId")
    void ktvDecideRound1_assignedKtv_success() {
        Long caseId = 501L;
        Long testKtvId = 300L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "[NOT_AS_DESCRIBED] Detail", "CMD_R1_ASSIGNED_OK");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_REVIEWING);
        returnCase.setAssignedTo(testKtvId);

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
                "CMD_KTV_R1_ASSIGNED_OK"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse response = service.ktvDecideRound1(testKtvId, caseId, request);
        assertThat(response).isNotNull();
        assertThat(response.state()).isEqualTo(CaseEntity.STATE_RETURN_APPROVED);
    }

    @Test
    @DisplayName("Task 5.2 Remediation: ktvDecideRound2 throws 403 NOT_ASSIGNED_KTV when non-escalated and assignedTo is null")
    void ktvDecideRound2_unassignedCase_rejected403() {
        Long caseId = 501L;
        Long testKtvId = 300L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_R2_UNASSIGNED");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(null);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.existsByCaseIdAndRoundIdAndEventType(caseId, round2.getId(), CaseEventEntity.TYPE_ESCALATED))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV')"), eq(Boolean.class), eq(testKtvId)))
                .thenReturn(Boolean.TRUE);

        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Lý do phân xử vòng 2",
                "CMD_KTV_R2_UNASSIGNED"
        );

        assertThatThrownBy(() -> service.ktvDecideRound2(testKtvId, caseId, req))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(tsEx.getCode()).isEqualTo("NOT_ASSIGNED_KTV");
                });
    }

    @Test
    @DisplayName("Task 5.2 Remediation: ktvDecideRound2 throws 403 NOT_ASSIGNED_KTV when non-escalated and assignedTo != ktvId")
    void ktvDecideRound2_differentKtv_rejected403() {
        Long caseId = 501L;
        Long testKtvId = 300L;
        Long otherKtvId = 999L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_R2_DIFF");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(otherKtvId);

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.existsByCaseIdAndRoundIdAndEventType(caseId, round2.getId(), CaseEventEntity.TYPE_ESCALATED))
                .thenReturn(false);
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV')"), eq(Boolean.class), eq(testKtvId)))
                .thenReturn(Boolean.TRUE);

        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Lý do phân xử vòng 2",
                "CMD_KTV_R2_DIFF"
        );

        assertThatThrownBy(() -> service.ktvDecideRound2(testKtvId, caseId, req))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(tsEx.getCode()).isEqualTo("NOT_ASSIGNED_KTV");
                });
    }

    @Test
    @DisplayName("Task 5.2 Remediation: ktvDecideRound2 succeeds when non-escalated and assignedTo == ktvId")
    void ktvDecideRound2_assignedKtv_success() {
        Long caseId = 501L;
        Long testKtvId = 300L;
        CaseEntity returnCase = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, buyerId, "Detail", "CMD_R2_ASSIGNED_OK");
        returnCase.setId(caseId);
        returnCase.setState(CaseEntity.STATE_POST_RETURN);
        returnCase.setAssignedTo(testKtvId);
        returnCase.setValidReturnedAt(fixedNow.minus(Duration.ofHours(5)));

        OrderEntity order = createValidDeliveredOrder();
        CaseRoundEntity round2 = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2.setId(702L);
        round2.setState(CaseRoundEntity.STATE_OPEN);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(returnCase));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(caseRoundRepository.findByCaseIdAndRoundNo(caseId, 2)).thenReturn(Optional.of(round2));
        when(caseRoundRepository.findLatestRoundByCaseId(caseId)).thenReturn(Optional.of(round2));
        when(caseDecisionRepository.findByCommandKey("CMD_KTV_R2_ASSIGNED_OK")).thenReturn(Optional.empty());
        when(caseDecisionRepository.saveAndFlush(any(CaseDecisionEntity.class))).thenAnswer(inv -> {
            CaseDecisionEntity d = inv.getArgument(0);
            d.setId(901L);
            return d;
        });
        when(caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(round2.getId())).thenAnswer(inv -> {
            CaseDecisionEntity d = new CaseDecisionEntity(round2.getId(), testKtvId, CaseDecisionEntity.ACTOR_USER,
                    CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND, true, new BigDecimal("500000.00"),
                    "Hàng hóa hợp lệ", "CMD_KTV_R2_ASSIGNED_OK");
            d.setId(901L);
            return List.of(d);
        });
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV')"), eq(Boolean.class), eq(testKtvId)))
                .thenReturn(Boolean.TRUE);

        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Hàng hóa hợp lệ",
                "CMD_KTV_R2_ASSIGNED_OK"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse response = service.ktvDecideRound2(testKtvId, caseId, req);
        assertThat(response).isNotNull();
        assertThat(returnCase.getState()).isEqualTo(CaseEntity.STATE_CLOSED);
        assertThat(round2.getState()).isEqualTo(CaseRoundEntity.STATE_DECIDED);
    }
}
