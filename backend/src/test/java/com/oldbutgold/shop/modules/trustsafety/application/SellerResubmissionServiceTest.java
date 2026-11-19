package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SellerResubmissionServiceTest - Comprehensive Unit Tests for BD-5 Resubmission (Task 3.2.2)")
class SellerResubmissionServiceTest {

    @Mock
    private ReturnWorkflowLockGateway lockGateway;

    @Mock
    private CaseEvidenceRequestRepository caseEvidenceRequestRepository;

    @Mock
    private CaseEvidenceRepository caseEvidenceRepository;

    @Mock
    private CaseDecisionRepository caseDecisionRepository;

    @Mock
    private CaseEventRepository caseEventRepository;

    @Mock
    private CaseRepository caseRepository;

    private SellerResubmissionService service;

    private final Instant fixedNow = Instant.parse("2026-11-05T12:00:00Z");
    private final Clock clock = Clock.fixed(fixedNow, ZoneId.of("UTC"));

    private final Long caseId = 100245L;
    private final Long round2Id = 50012L;
    private final Long sellerId = 9002L;
    private final Long orderId = 70012L;
    private final UUID requestId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");

    private CaseEntity caseEntity;
    private CaseRoundEntity round2Entity;
    private OrderEntity orderEntity;
    private CaseEvidenceRequestEntity pendingRequestEntity;

    @BeforeEach
    void setUp() {
        service = new SellerResubmissionService(
                lockGateway,
                caseEvidenceRequestRepository,
                caseEvidenceRepository,
                caseDecisionRepository,
                caseEventRepository,
                caseRepository,
                null,
                null
        );
        service.setClock(clock);

        caseEntity = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, 8001L, "Damage dispute", "CMD-CASE-1");
        caseEntity.setId(caseId);
        caseEntity.setState(CaseEntity.STATE_POST_RETURN);
        caseEntity.setValidReturnedAt(fixedNow.minus(2, ChronoUnit.DAYS));
        caseEntity.setSellerResponseDueAt(fixedNow.minus(1, ChronoUnit.HOURS)); // General deadline passed (D-03 Option 3)
        caseEntity.setAssignedTo(8001L);

        round2Entity = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2Entity.setId(round2Id);
        round2Entity.setState(CaseRoundEntity.STATE_OPEN);
        round2Entity.setValidDamageRequestAt(fixedNow.minus(1, ChronoUnit.DAYS)); // Batch 1 already exists

        orderEntity = mock(OrderEntity.class);
        lenient().when(orderEntity.getSellerId()).thenReturn(sellerId);

        pendingRequestEntity = new CaseEvidenceRequestEntity(
                requestId,
                caseId,
                round2Id,
                8001L,
                "Yeu cau bo sung anh ro net",
                "[\"Anh ro net\"]",
                fixedNow.minus(1, ChronoUnit.DAYS),
                fixedNow.plus(1, ChronoUnit.DAYS), // 48h deadline still active!
                "CMD-REQ-001",
                "payloadhash001"
        );
        pendingRequestEntity.setStatus(CaseEvidenceRequestEntity.STATUS_PENDING);
    }

    private void setupLockGateway() {
        ReturnWorkflowLockGateway.ReturnWorkflowLockedEntities locked =
                new ReturnWorkflowLockGateway.ReturnWorkflowLockedEntities(orderEntity, caseEntity, round2Entity);
        when(lockGateway.lockForRound(caseId, 2)).thenReturn(locked);
    }

    private List<ReturnCaseDtos.EvidenceItemDto> createSampleEvidenceList() {
        return List.of(
                new ReturnCaseDtos.EvidenceItemDto("IMAGE", "evidence/img1.jpg", "Vết rách cổ áo", "hash1", 2048L, null),
                new ReturnCaseDtos.EvidenceItemDto("IMAGE", "evidence/img2.jpg", "Tem niêm phong", "hash2", 1024L, null),
                new ReturnCaseDtos.EvidenceItemDto("VIDEO", "evidence/vid1.mp4", "Video mở gói", "hash3", 10485760L, 30)
        );
    }

    @Test
    @DisplayName("TC-01: Resubmission thành công (submission_no = 2) cập nhật FULFILLED và domain event TYPE_SUPPLEMENTED")
    void testResubmissionSuccess_submissionNo2() {
        setupLockGateway();
        when(caseEvidenceRequestRepository.findByFulfillmentCommandKey("CMD-RESUB-001")).thenReturn(Optional.empty());
        when(caseEvidenceRequestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequestEntity));

        CaseEvidenceEntity batch1Evidence = new CaseEvidenceEntity(
                UUID.randomUUID(), round2Id, sellerId, 1, "IMAGE", "evidence/batch1.jpg", null, "h0", 1024L, null
        );
        lenient().when(caseEvidenceRepository.findByRoundIdOrderBySubmittedAtAsc(round2Id)).thenReturn(List.of(batch1Evidence));
        when(caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(round2Id)).thenReturn(Collections.emptyList());

        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Đã bổ sung video và ảnh cận cảnh theo yêu cầu KTV",
                createSampleEvidenceList(),
                "CMD-RESUB-001",
                requestId
        );

        CaseEntity result = service.submitDamageExemptionResubmission(sellerId, caseId, request);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(caseId);

        // Verify request updated to FULFILLED
        assertThat(pendingRequestEntity.getStatus()).isEqualTo(CaseEvidenceRequestEntity.STATUS_FULFILLED);
        assertThat(pendingRequestEntity.getFulfilledAt()).isEqualTo(fixedNow);
        assertThat(pendingRequestEntity.getFulfillmentCommandKey()).isEqualTo("CMD-RESUB-001");
        assertThat(pendingRequestEntity.getFulfillmentPayloadHash()).isNotBlank();
        verify(caseEvidenceRequestRepository).save(pendingRequestEntity);

        // Verify 3 evidence items saved with submission_no = 2 and requestId
        ArgumentCaptor<CaseEvidenceEntity> evidenceCaptor = ArgumentCaptor.forClass(CaseEvidenceEntity.class);
        verify(caseEvidenceRepository, times(3)).save(evidenceCaptor.capture());
        List<CaseEvidenceEntity> savedEvidences = evidenceCaptor.getAllValues();
        for (CaseEvidenceEntity ev : savedEvidences) {
            assertThat(ev.getSubmissionNo()).isEqualTo(2);
            assertThat(ev.getRequestId()).isEqualTo(requestId);
            assertThat(ev.getSubmittedBy()).isEqualTo(sellerId);
        }

        // Verify domain event TYPE_SUPPLEMENTED recorded
        ArgumentCaptor<CaseEventEntity> eventCaptor = ArgumentCaptor.forClass(CaseEventEntity.class);
        verify(caseEventRepository).save(eventCaptor.capture());
        CaseEventEntity savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getEventType()).isEqualTo(CaseEventEntity.TYPE_SUPPLEMENTED);
        assertThat(savedEvent.getCommandKey()).isEqualTo("CMD-RESUB-001");
        assertThat(savedEvent.getActorId()).isEqualTo(sellerId);
        assertThat(savedEvent.getMetadata()).contains("SELLER_RESUBMISSION_FULFILLED");
        assertThat(savedEvent.getMetadata()).contains("\"submission_no\":2");
    }

    @Test
    @DisplayName("TC-02: Idempotent replay - Cùng commandKey và cùng payload trả về kết quả cũ 200 không tạo mới")
    void testIdempotentReplay_sameKey_samePayload() {
        setupLockGateway();
        List<ReturnCaseDtos.EvidenceItemDto> evidenceList = createSampleEvidenceList();
        String expectedHash = SellerResubmissionService.computeCanonicalResubmissionPayloadHash(
                requestId, "Đã bổ sung video và ảnh cận cảnh", evidenceList
        );

        CaseEvidenceRequestEntity fulfilledEntity = new CaseEvidenceRequestEntity();
        fulfilledEntity.setCaseId(caseId);
        fulfilledEntity.setFulfillmentCommandKey("CMD-RESUB-REPLAY");
        fulfilledEntity.setFulfillmentPayloadHash(expectedHash);
        fulfilledEntity.setStatus(CaseEvidenceRequestEntity.STATUS_FULFILLED);

        when(caseEvidenceRequestRepository.findByFulfillmentCommandKey("CMD-RESUB-REPLAY"))
                .thenReturn(Optional.of(fulfilledEntity));

        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Đã bổ sung video và ảnh cận cảnh",
                evidenceList,
                "CMD-RESUB-REPLAY",
                requestId
        );

        CaseEntity result = service.submitDamageExemptionResubmission(sellerId, caseId, request);

        assertThat(result).isNotNull();
        // Zero writes performed
        verify(caseEvidenceRepository, never()).save(any());
        verify(caseEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-03: Conflict - Cùng commandKey nhưng khác payload bị từ chối 409 COMMAND_KEY_CONFLICT")
    void testConflict_sameKey_differentPayload() {
        setupLockGateway();
        CaseEvidenceRequestEntity fulfilledEntity = new CaseEvidenceRequestEntity();
        fulfilledEntity.setCaseId(caseId);
        fulfilledEntity.setFulfillmentCommandKey("CMD-RESUB-CONFLICT");
        fulfilledEntity.setFulfillmentPayloadHash("differenthash1234567890abcdef1234567890abcdef1234567890abcdef12345678");
        fulfilledEntity.setStatus(CaseEvidenceRequestEntity.STATUS_FULFILLED);

        when(caseEvidenceRequestRepository.findByFulfillmentCommandKey("CMD-RESUB-CONFLICT"))
                .thenReturn(Optional.of(fulfilledEntity));

        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Nội dung thay đổi",
                createSampleEvidenceList(),
                "CMD-RESUB-CONFLICT",
                requestId
        );

        assertThatThrownBy(() -> service.submitDamageExemptionResubmission(sellerId, caseId, request))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException be = (TrustSafetyBusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getCode()).isEqualTo("COMMAND_KEY_CONFLICT");
                });
    }

    @Test
    @DisplayName("TC-04: Xung đột xuyên Case - commandKey đã dùng cho Case khác bị từ chối 409 COMMAND_KEY_CONFLICT")
    void testConflict_crossCase_commandKey() {
        setupLockGateway();
        CaseEvidenceRequestEntity crossCaseEntity = new CaseEvidenceRequestEntity();
        crossCaseEntity.setCaseId(999999L); // Different case
        crossCaseEntity.setFulfillmentCommandKey("CMD-CROSS-CASE");
        crossCaseEntity.setFulfillmentPayloadHash("hash123");

        when(caseEvidenceRequestRepository.findByFulfillmentCommandKey("CMD-CROSS-CASE"))
                .thenReturn(Optional.of(crossCaseEntity));

        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Nộp cho case này",
                createSampleEvidenceList(),
                "CMD-CROSS-CASE",
                requestId
        );

        assertThatThrownBy(() -> service.submitDamageExemptionResubmission(sellerId, caseId, request))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException be = (TrustSafetyBusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getCode()).isEqualTo("COMMAND_KEY_CONFLICT");
                });
    }

    @Test
    @DisplayName("TC-05: Thiếu commandKey khi nộp bổ sung bị từ chối 400 COMMAND_KEY_REQUIRED")
    void testMissingCommandKey_rejected() {
        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Ly do hop le",
                createSampleEvidenceList(),
                null, // Missing commandKey
                requestId
        );

        assertThatThrownBy(() -> service.submitDamageExemptionResubmission(sellerId, caseId, request))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException be = (TrustSafetyBusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getCode()).isEqualTo("COMMAND_KEY_REQUIRED");
                });
    }

    @Test
    @DisplayName("TC-06: Người bán không sở hữu đơn hàng bị từ chối 403 SELLER_OWNERSHIP_MISMATCH")
    void testSellerOwnershipMismatch_rejected() {
        setupLockGateway();
        Long differentSellerId = 7777L;

        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Ly do hop le",
                createSampleEvidenceList(),
                "CMD-KEY-001",
                requestId
        );

        assertThatThrownBy(() -> service.submitDamageExemptionResubmission(differentSellerId, caseId, request))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException be = (TrustSafetyBusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(be.getCode()).isEqualTo("SELLER_OWNERSHIP_MISMATCH");
                });
    }

    @Test
    @DisplayName("TC-07: Không tìm thấy requestId tương ứng bị từ chối 404 NOT_FOUND")
    void testRequestNotFound_rejected() {
        setupLockGateway();
        when(caseEvidenceRequestRepository.findByFulfillmentCommandKey("CMD-KEY-001")).thenReturn(Optional.empty());
        when(caseEvidenceRequestRepository.findById(requestId)).thenReturn(Optional.empty());

        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Ly do hop le",
                createSampleEvidenceList(),
                "CMD-KEY-001",
                requestId
        );

        assertThatThrownBy(() -> service.submitDamageExemptionResubmission(sellerId, caseId, request))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException be = (TrustSafetyBusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(be.getCode()).isEqualTo("NOT_FOUND");
                });
    }

    @Test
    @DisplayName("TC-08: Request không ở trạng thái PENDING bị từ chối 409 REQUEST_NOT_PENDING")
    void testRequestNotPending_rejected() {
        setupLockGateway();
        when(caseEvidenceRequestRepository.findByFulfillmentCommandKey("CMD-KEY-001")).thenReturn(Optional.empty());
        pendingRequestEntity.setStatus(CaseEvidenceRequestEntity.STATUS_FULFILLED);
        when(caseEvidenceRequestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequestEntity));

        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Ly do hop le",
                createSampleEvidenceList(),
                "CMD-KEY-001",
                requestId
        );

        assertThatThrownBy(() -> service.submitDamageExemptionResubmission(sellerId, caseId, request))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException be = (TrustSafetyBusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getCode()).isEqualTo("REQUEST_NOT_PENDING");
                });
    }

    @Test
    @DisplayName("TC-09: Quá hạn 48 giờ (Gate 1 Invariant: Lazy check in-memory, KHÔNG persist EXPIRED xuống DB) từ chối 409 REQUEST_EXPIRED")
    void testLazyExpiration_gate1Invariant_noPersistence() {
        setupLockGateway();
        when(caseEvidenceRequestRepository.findByFulfillmentCommandKey("CMD-KEY-001")).thenReturn(Optional.empty());
        // Set deadline in past
        pendingRequestEntity.setDeadlineAt(fixedNow.minus(1, ChronoUnit.HOURS));
        when(caseEvidenceRequestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequestEntity));

        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Ly do hop le",
                createSampleEvidenceList(),
                "CMD-KEY-001",
                requestId
        );

        assertThatThrownBy(() -> service.submitDamageExemptionResubmission(sellerId, caseId, request))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException be = (TrustSafetyBusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getCode()).isEqualTo("REQUEST_EXPIRED");
                });

        // GATE 1 INVARIANT: Do NOT persist EXPIRED to DB!
        assertThat(pendingRequestEntity.getStatus()).isEqualTo(CaseEvidenceRequestEntity.STATUS_PENDING);
        verify(caseEvidenceRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-10: Vượt hạn mức media batch (6 ảnh > 5 ảnh) bị từ chối 400 MEDIA_QUOTA_EXCEEDED")
    void testMediaQuota_exceedImages_rejected() {
        List<ReturnCaseDtos.EvidenceItemDto> sixImages = List.of(
                new ReturnCaseDtos.EvidenceItemDto("IMAGE", "k1", null, null, 10L, null),
                new ReturnCaseDtos.EvidenceItemDto("IMAGE", "k2", null, null, 10L, null),
                new ReturnCaseDtos.EvidenceItemDto("IMAGE", "k3", null, null, 10L, null),
                new ReturnCaseDtos.EvidenceItemDto("IMAGE", "k4", null, null, 10L, null),
                new ReturnCaseDtos.EvidenceItemDto("IMAGE", "k5", null, null, 10L, null),
                new ReturnCaseDtos.EvidenceItemDto("IMAGE", "k6", null, null, 10L, null)
        );

        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Ly do hop le",
                sixImages,
                "CMD-KEY-001",
                requestId
        );

        assertThatThrownBy(() -> service.submitDamageExemptionResubmission(sellerId, caseId, request))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException be = (TrustSafetyBusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getCode()).isEqualTo("MEDIA_QUOTA_EXCEEDED");
                });
    }

    @Test
    @DisplayName("TC-11: Vượt hạn mức video batch (3 videos > 2 videos) bị từ chối 400 MEDIA_QUOTA_EXCEEDED")
    void testMediaQuota_exceedVideos_rejected() {
        List<ReturnCaseDtos.EvidenceItemDto> threeVideos = List.of(
                new ReturnCaseDtos.EvidenceItemDto("IMAGE", "k1", null, null, 10L, null),
                new ReturnCaseDtos.EvidenceItemDto("VIDEO", "v1", null, null, 1000L, 20),
                new ReturnCaseDtos.EvidenceItemDto("VIDEO", "v2", null, null, 1000L, 20),
                new ReturnCaseDtos.EvidenceItemDto("VIDEO", "v3", null, null, 1000L, 20)
        );

        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Ly do hop le",
                threeVideos,
                "CMD-KEY-001",
                requestId
        );

        assertThatThrownBy(() -> service.submitDamageExemptionResubmission(sellerId, caseId, request))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException be = (TrustSafetyBusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getCode()).isEqualTo("MEDIA_QUOTA_EXCEEDED");
                });
    }

    @Test
    @DisplayName("TC-12: Video vượt thời lượng 60s bị từ chối 400 VIDEO_CONSTRAINT_VIOLATED")
    void testVideoConstraint_durationExceeded_rejected() {
        List<ReturnCaseDtos.EvidenceItemDto> longVideo = List.of(
                new ReturnCaseDtos.EvidenceItemDto("IMAGE", "k1", null, null, 10L, null),
                new ReturnCaseDtos.EvidenceItemDto("VIDEO", "v1", null, null, 1000L, 65) // 65 seconds
        );

        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Ly do hop le",
                longVideo,
                "CMD-KEY-001",
                requestId
        );

        assertThatThrownBy(() -> service.submitDamageExemptionResubmission(sellerId, caseId, request))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException be = (TrustSafetyBusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getCode()).isEqualTo("VIDEO_CONSTRAINT_VIOLATED");
                });
    }

    @Test
    @DisplayName("TC-13: Video vượt dung lượng 50MB bị từ chối 400 VIDEO_CONSTRAINT_VIOLATED")
    void testVideoConstraint_sizeExceeded_rejected() {
        List<ReturnCaseDtos.EvidenceItemDto> heavyVideo = List.of(
                new ReturnCaseDtos.EvidenceItemDto("IMAGE", "k1", null, null, 10L, null),
                new ReturnCaseDtos.EvidenceItemDto("VIDEO", "v1", null, null, 60_000_000L, 30) // 60MB
        );

        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Ly do hop le",
                heavyVideo,
                "CMD-KEY-001",
                requestId
        );

        assertThatThrownBy(() -> service.submitDamageExemptionResubmission(sellerId, caseId, request))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException be = (TrustSafetyBusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getCode()).isEqualTo("VIDEO_CONSTRAINT_VIOLATED");
                });
    }

    @Test
    @DisplayName("TC-14: Đã đạt tối đa 4 đợt nộp bằng chứng bị từ chối 409 MAX_RESUBMISSION_REACHED")
    void testMaxResubmissionsReached_rejected() {
        setupLockGateway();
        when(caseEvidenceRequestRepository.findByFulfillmentCommandKey("CMD-KEY-001")).thenReturn(Optional.empty());
        when(caseEvidenceRequestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequestEntity));

        CaseEvidenceEntity batch4Evidence = new CaseEvidenceEntity(
                UUID.randomUUID(), round2Id, sellerId, 4, "IMAGE", "evidence/b4.jpg", null, "h4", 1024L, null
        );
        when(caseEvidenceRepository.findByRoundIdOrderBySubmittedAtAsc(round2Id)).thenReturn(List.of(batch4Evidence));

        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Nộp đợt 5",
                createSampleEvidenceList(),
                "CMD-KEY-001",
                requestId
        );

        assertThatThrownBy(() -> service.submitDamageExemptionResubmission(sellerId, caseId, request))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException be = (TrustSafetyBusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getCode()).isEqualTo("MAX_RESUBMISSION_REACHED");
                });
    }

    @Test
    @DisplayName("TC-15: Đã có phán quyết cuối cùng (Final Decision) bị từ chối 409 CASE_FINAL_DECIDED (BD5-27)")
    void testCaseFinalDecided_rejected() {
        setupLockGateway();
        when(caseEvidenceRequestRepository.findByFulfillmentCommandKey("CMD-KEY-001")).thenReturn(Optional.empty());
        when(caseEvidenceRequestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequestEntity));

        CaseEvidenceEntity batch1Evidence = new CaseEvidenceEntity(
                UUID.randomUUID(), round2Id, sellerId, 1, "IMAGE", "evidence/b1.jpg", null, "h1", 1024L, null
        );
        lenient().when(caseEvidenceRepository.findByRoundIdOrderBySubmittedAtAsc(round2Id)).thenReturn(List.of(batch1Evidence));

        CaseDecisionEntity decision = mock(CaseDecisionEntity.class);
        when(caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(round2Id)).thenReturn(List.of(decision));

        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Nộp sau khi KTV đã phán quyết",
                createSampleEvidenceList(),
                "CMD-KEY-001",
                requestId
        );

        assertThatThrownBy(() -> service.submitDamageExemptionResubmission(sellerId, caseId, request))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException be = (TrustSafetyBusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getCode()).isEqualTo("CASE_FINAL_DECIDED");
                });
    }

    @Test
    @DisplayName("TC-16: Canonical hash tất định - Danh sách bằng chứng truyền vào theo thứ tự khác nhau vẫn cho ra cùng hash")
    void testCanonicalHashDeterminism_differentItemOrder_sameHash() {
        ReturnCaseDtos.EvidenceItemDto itemA = new ReturnCaseDtos.EvidenceItemDto("IMAGE", "k_a.jpg", "A", "hashA", 100L, null);
        ReturnCaseDtos.EvidenceItemDto itemB = new ReturnCaseDtos.EvidenceItemDto("VIDEO", "k_b.mp4", "B", "hashB", 200L, 20);
        ReturnCaseDtos.EvidenceItemDto itemC = new ReturnCaseDtos.EvidenceItemDto("IMAGE", "k_c.jpg", "C", "hashC", 300L, null);

        List<ReturnCaseDtos.EvidenceItemDto> order1 = List.of(itemA, itemB, itemC);
        List<ReturnCaseDtos.EvidenceItemDto> order2 = List.of(itemC, itemA, itemB);

        String hash1 = SellerResubmissionService.computeCanonicalResubmissionPayloadHash(requestId, "Ly do test", order1);
        String hash2 = SellerResubmissionService.computeCanonicalResubmissionPayloadHash(requestId, "Ly do test", order2);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("TC-17: Nộp bổ sung thành công khi General Case Deadline đã qua nhưng Request còn hiệu lực (D-03 Option 3 PO APPROVED)")
    void testResubmissionSucceeds_whenGeneralCaseDeadlinePassed_butRequestStillActive() {
        setupLockGateway();
        // Case sellerResponseDueAt is in the past: fixedNow - 1 hour (set in setUp)
        assertThat(caseEntity.getSellerResponseDueAt()).isBefore(fixedNow);
        // Request deadlineAt is in the future: fixedNow + 1 day
        assertThat(pendingRequestEntity.getDeadlineAt()).isAfter(fixedNow);

        when(caseEvidenceRequestRepository.findByFulfillmentCommandKey("CMD-IN-TIME")).thenReturn(Optional.empty());
        when(caseEvidenceRequestRepository.findById(requestId)).thenReturn(Optional.of(pendingRequestEntity));

        CaseEvidenceEntity batch1Evidence = new CaseEvidenceEntity(
                UUID.randomUUID(), round2Id, sellerId, 1, "IMAGE", "evidence/b1.jpg", null, "h1", 1024L, null
        );
        lenient().when(caseEvidenceRepository.findByRoundIdOrderBySubmittedAtAsc(round2Id)).thenReturn(List.of(batch1Evidence));
        when(caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(round2Id)).thenReturn(Collections.emptyList());

        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Bổ sung kịp thời hạn 48h của Request",
                createSampleEvidenceList(),
                "CMD-IN-TIME",
                requestId
        );

        CaseEntity result = service.submitDamageExemptionResubmission(sellerId, caseId, request);

        assertThat(result).isNotNull();
        assertThat(pendingRequestEntity.getStatus()).isEqualTo(CaseEvidenceRequestEntity.STATUS_FULFILLED);
    }
}
