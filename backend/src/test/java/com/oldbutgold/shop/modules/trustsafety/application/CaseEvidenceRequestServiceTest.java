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
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CaseEvidenceRequestServiceTest - Comprehensive Unit Tests for API 1 (BD-5 Phase 1)")
class CaseEvidenceRequestServiceTest {

    @Mock
    private ReturnWorkflowLockGateway lockGateway;

    @Mock
    private CaseEvidenceRequestRepository caseEvidenceRequestRepository;

    @Mock
    private CaseEvidenceRepository caseEvidenceRepository;

    @Mock
    private CaseDecisionRepository caseDecisionRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private CaseEvidenceRequestService service;

    private final Instant fixedNow = Instant.parse("2026-11-04T10:00:00Z");
    private final Clock clock = Clock.fixed(fixedNow, ZoneId.of("UTC"));

    private final Long caseId = 100245L;
    private final Long round2Id = 50012L;
    private final Long ktvId = 8001L;
    private final Long orderId = 70012L;

    private CaseEntity caseEntity;
    private CaseRoundEntity round2Entity;
    private OrderEntity orderEntity;

    @BeforeEach
    void setUp() {
        service = new CaseEvidenceRequestService(
                lockGateway,
                caseEvidenceRequestRepository,
                caseEvidenceRepository,
                caseDecisionRepository,
                jdbcTemplate,
                null
        );
        service.setClock(clock);

        caseEntity = new CaseEntity(CaseEntity.TYPE_RETURN, orderId, ktvId, "Test description", "CMD-CASE-1");
        caseEntity.setId(caseId);
        caseEntity.setOrderId(orderId);
        caseEntity.setState(CaseEntity.STATE_POST_RETURN);
        caseEntity.setValidReturnedAt(fixedNow.minus(1, ChronoUnit.DAYS));
        caseEntity.setSellerResponseDueAt(fixedNow.plus(2, ChronoUnit.DAYS)); // General deadline in future
        caseEntity.setAssignedTo(ktvId);

        round2Entity = new CaseRoundEntity(caseId, 2, CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        round2Entity.setId(round2Id);
        round2Entity.setState(CaseRoundEntity.STATE_OPEN);
        round2Entity.setValidDamageRequestAt(fixedNow.minus(12, ChronoUnit.HOURS)); // Batch 1 exists

        orderEntity = mock(OrderEntity.class);

        // By default, KTV check passes
        lenient().when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV')"), eq(Boolean.class), eq(ktvId)))
                .thenReturn(Boolean.TRUE);
    }

    private void setupLockGateway() {
        ReturnWorkflowLockGateway.ReturnWorkflowLockedEntities locked =
                new ReturnWorkflowLockGateway.ReturnWorkflowLockedEntities(orderEntity, caseEntity, round2Entity);
        when(lockGateway.lockForRound(caseId, 2)).thenReturn(locked);
    }

    // ==================== 1. AUTHORIZATION TESTS ====================

    @Test
    @DisplayName("API 1 - Từ chối khi ktvId là null (401 AUTHENTICATION_FAILED)")
    void requestEvidence_whenKtvIdNull_throwsUnauthorized() {
        ReturnCaseDtos.KtvRequestEvidenceRequest req = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Lý do yêu cầu bổ sung chi tiết bằng chứng",
                List.of("Ảnh cận cảnh vết rách"),
                "CMD-REQ-001"
        );

        assertThatThrownBy(() -> service.requestEvidence(null, caseId, req))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(tsEx.getCode()).isEqualTo("AUTHENTICATION_FAILED");
                });
    }

    @Test
    @DisplayName("API 1 - Từ chối khi caller không có vai trò KTV (403 ACCESS_DENIED)")
    void requestEvidence_whenCallerNotKtv_throwsForbidden() {
        setupLockGateway();
        when(jdbcTemplate.queryForObject(eq("SELECT og_has_role(?, 'KTV')"), eq(Boolean.class), eq(ktvId)))
                .thenReturn(Boolean.FALSE);

        ReturnCaseDtos.KtvRequestEvidenceRequest req = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Lý do yêu cầu bổ sung chi tiết bằng chứng",
                List.of("Ảnh cận cảnh vết rách"),
                "CMD-REQ-001"
        );

        assertThatThrownBy(() -> service.requestEvidence(ktvId, caseId, req))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(tsEx.getCode()).isEqualTo("ACCESS_DENIED");
                });
    }

    @Test
    @DisplayName("API 1 - Từ chối khi KTV không được phân công cho Case (403 NOT_ASSIGNED_KTV)")
    void requestEvidence_whenKtvNotAssignedToCase_throwsNotAssignedKtv() {
        caseEntity.setAssignedTo(9999L); // Assigned to another KTV
        setupLockGateway();

        ReturnCaseDtos.KtvRequestEvidenceRequest req = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Lý do yêu cầu bổ sung chi tiết bằng chứng",
                List.of("Ảnh cận cảnh vết rách"),
                "CMD-REQ-001"
        );

        assertThatThrownBy(() -> service.requestEvidence(ktvId, caseId, req))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(tsEx.getCode()).isEqualTo("NOT_ASSIGNED_KTV");
                });
    }

    // ==================== 2. ELIGIBILITY CONDITIONS TESTS ====================

    @Test
    @DisplayName("API 1 - Từ chối khi Case không ở trạng thái POST_RETURN (409 CASE_NOT_ELIGIBLE)")
    void requestEvidence_whenCaseNotInPostReturn_throwsConflict() {
        caseEntity.setState(CaseEntity.STATE_RETURNING);
        setupLockGateway();

        ReturnCaseDtos.KtvRequestEvidenceRequest req = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Lý do yêu cầu bổ sung chi tiết bằng chứng",
                null,
                "CMD-REQ-001"
        );

        assertThatThrownBy(() -> service.requestEvidence(ktvId, caseId, req))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(tsEx.getCode()).isEqualTo("CASE_NOT_ELIGIBLE");
                });
    }

    @Test
    @DisplayName("API 1 - Từ chối khi validReturnedAt là null (409 CASE_NOT_ELIGIBLE)")
    void requestEvidence_whenValidReturnedAtIsNull_throwsConflict() {
        caseEntity.setValidReturnedAt(null);
        setupLockGateway();

        ReturnCaseDtos.KtvRequestEvidenceRequest req = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Lý do yêu cầu bổ sung chi tiết bằng chứng",
                null,
                "CMD-REQ-001"
        );

        assertThatThrownBy(() -> service.requestEvidence(ktvId, caseId, req))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(tsEx.getCode()).isEqualTo("CASE_NOT_ELIGIBLE");
                });
    }

    @Test
    @DisplayName("API 1 - D-11: Từ chối khi Round 2 stage không phải là POST_RETURN_RESOLUTION (409 CASE_NOT_ELIGIBLE)")
    void requestEvidence_whenRound2StageNotPostReturnResolution_throwsConflict() {
        round2Entity.setStage(CaseRoundEntity.STAGE_RETURN_APPROVAL);
        setupLockGateway();

        ReturnCaseDtos.KtvRequestEvidenceRequest req = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Lý do yêu cầu bổ sung chi tiết bằng chứng",
                null,
                "CMD-REQ-001"
        );

        assertThatThrownBy(() -> service.requestEvidence(ktvId, caseId, req))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(tsEx.getCode()).isEqualTo("CASE_NOT_ELIGIBLE");
                });
    }

    @Test
    @DisplayName("API 1 - Từ chối khi Round 2 không ở trạng thái OPEN (409 CASE_NOT_ELIGIBLE)")
    void requestEvidence_whenRound2NotOpen_throwsConflict() {
        round2Entity.setState(CaseRoundEntity.STATE_DECIDED);
        setupLockGateway();

        ReturnCaseDtos.KtvRequestEvidenceRequest req = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Lý do yêu cầu bổ sung chi tiết bằng chứng",
                null,
                "CMD-REQ-001"
        );

        assertThatThrownBy(() -> service.requestEvidence(ktvId, caseId, req))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(tsEx.getCode()).isEqualTo("CASE_NOT_ELIGIBLE");
                });
    }

    @Test
    @DisplayName("API 1 - Từ chối khi Người bán chưa gửi Batch 1 validDamageRequestAt == null (409 CASE_NOT_ELIGIBLE)")
    void requestEvidence_whenBatch1Missing_throwsConflict() {
        round2Entity.setValidDamageRequestAt(null);
        setupLockGateway();

        ReturnCaseDtos.KtvRequestEvidenceRequest req = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Lý do yêu cầu bổ sung chi tiết bằng chứng",
                null,
                "CMD-REQ-001"
        );

        assertThatThrownBy(() -> service.requestEvidence(ktvId, caseId, req))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(tsEx.getCode()).isEqualTo("CASE_NOT_ELIGIBLE");
                });
    }

    @Test
    @DisplayName("API 1 - Từ chối khi Vòng 2 đã có Final Decision (409 CASE_NOT_ELIGIBLE)")
    void requestEvidence_whenFinalDecisionAlreadyExists_throwsConflict() {
        setupLockGateway();
        CaseDecisionEntity decision = mock(CaseDecisionEntity.class);
        when(caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(round2Id)).thenReturn(List.of(decision));

        ReturnCaseDtos.KtvRequestEvidenceRequest req = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Lý do yêu cầu bổ sung chi tiết bằng chứng",
                null,
                "CMD-REQ-001"
        );

        assertThatThrownBy(() -> service.requestEvidence(ktvId, caseId, req))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(tsEx.getCode()).isEqualTo("CASE_NOT_ELIGIBLE");
                });
    }

    @Test
    @DisplayName("API 1 - Từ chối khi đã đạt tối đa 4 đợt nộp bằng chứng (409 MAX_RESUBMISSION_REACHED)")
    void requestEvidence_whenMaxSubmissionsReached_throwsConflict() {
        setupLockGateway();
        when(caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(round2Id)).thenReturn(Collections.emptyList());

        CaseEvidenceEntity ev4 = mock(CaseEvidenceEntity.class);
        when(ev4.getSubmissionNo()).thenReturn(4);
        when(caseEvidenceRepository.findByRoundIdOrderBySubmittedAtAsc(round2Id)).thenReturn(List.of(ev4));

        ReturnCaseDtos.KtvRequestEvidenceRequest req = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Lý do yêu cầu bổ sung chi tiết bằng chứng",
                null,
                "CMD-REQ-001"
        );

        assertThatThrownBy(() -> service.requestEvidence(ktvId, caseId, req))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(tsEx.getCode()).isEqualTo("MAX_RESUBMISSION_REACHED");
                });
    }

    @Test
    @DisplayName("API 1 - Từ chối khi Case đã có Request Evidence PENDING khác (409 PENDING_REQUEST_EXISTS)")
    void requestEvidence_whenPendingRequestExists_throwsConflict() {
        setupLockGateway();
        when(caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(round2Id)).thenReturn(Collections.emptyList());
        when(caseEvidenceRepository.findByRoundIdOrderBySubmittedAtAsc(round2Id)).thenReturn(Collections.emptyList());

        CaseEvidenceRequestEntity pendingReq = new CaseEvidenceRequestEntity();
        pendingReq.setStatus(CaseEvidenceRequestEntity.STATUS_PENDING);
        when(caseEvidenceRequestRepository.findByCaseIdAndStatus(caseId, CaseEvidenceRequestEntity.STATUS_PENDING))
                .thenReturn(Optional.of(pendingReq));

        ReturnCaseDtos.KtvRequestEvidenceRequest req = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Lý do yêu cầu bổ sung chi tiết bằng chứng",
                null,
                "CMD-REQ-001"
        );

        assertThatThrownBy(() -> service.requestEvidence(ktvId, caseId, req))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(tsEx.getCode()).isEqualTo("PENDING_REQUEST_EXISTS");
                });
    }

    @Test
    @DisplayName("API 1 - D-03: Từ chối khi đã quá thời hạn phản hồi chung now > seller_response_due_at (409 GENERAL_DEADLINE_EXPIRED)")
    void requestEvidence_whenPastGeneralCaseDeadline_throwsConflict() {
        caseEntity.setSellerResponseDueAt(fixedNow.minus(1, ChronoUnit.MINUTES)); // Overdue
        setupLockGateway();
        when(caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(round2Id)).thenReturn(Collections.emptyList());
        when(caseEvidenceRepository.findByRoundIdOrderBySubmittedAtAsc(round2Id)).thenReturn(Collections.emptyList());
        when(caseEvidenceRequestRepository.findByCaseIdAndStatus(caseId, CaseEvidenceRequestEntity.STATUS_PENDING))
                .thenReturn(Optional.empty());

        ReturnCaseDtos.KtvRequestEvidenceRequest req = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Lý do yêu cầu bổ sung chi tiết bằng chứng",
                null,
                "CMD-REQ-001"
        );

        assertThatThrownBy(() -> service.requestEvidence(ktvId, caseId, req))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(tsEx.getCode()).isEqualTo("GENERAL_DEADLINE_EXPIRED");
                });
    }

    // ==================== 3. SUCCESSFUL CREATION & SIDE EFFECTS ====================

    @Test
    @DisplayName("API 1 - Thành công: Tạo Request Evidence PENDING với 48h deadline và không thay đổi General Deadline")
    void requestEvidence_successfulCreation_sets48HoursDeadline() {
        setupLockGateway();
        when(caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(round2Id)).thenReturn(Collections.emptyList());
        when(caseEvidenceRepository.findByRoundIdOrderBySubmittedAtAsc(round2Id)).thenReturn(Collections.emptyList());
        when(caseEvidenceRequestRepository.findByCaseIdAndStatus(caseId, CaseEvidenceRequestEntity.STATUS_PENDING))
                .thenReturn(Optional.empty());
        when(caseEvidenceRequestRepository.findByCommandKey("CMD-REQ-001")).thenReturn(Optional.empty());

        when(caseEvidenceRequestRepository.save(any(CaseEvidenceRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant originalGeneralDeadline = caseEntity.getSellerResponseDueAt();

        ReturnCaseDtos.KtvRequestEvidenceRequest req = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Video mờ không rõ chi tiết tem niêm phong",
                List.of("Ảnh tem cận cảnh", "Video mở hộp đối soát"),
                "CMD-REQ-001"
        );

        CaseEvidenceRequestService.KtvRequestEvidenceResult result = service.requestEvidence(ktvId, caseId, req);

        assertThat(result.isCreated()).isTrue();
        ReturnCaseDtos.KtvRequestEvidenceResponse resp = result.response();

        assertThat(resp.requestId()).isNotNull();
        assertThat(resp.caseId()).isEqualTo(caseId);
        assertThat(resp.roundId()).isEqualTo(round2Id);
        assertThat(resp.roundNo()).isEqualTo(2);
        assertThat(resp.ktvId()).isEqualTo(ktvId);
        assertThat(resp.status()).isEqualTo("PENDING");
        assertThat(resp.reason()).isEqualTo("Video mờ không rõ chi tiết tem niêm phong");
        assertThat(resp.requestedItems()).containsExactly("Ảnh tem cận cảnh", "Video mở hộp đối soát");
        assertThat(resp.issuedAt()).isEqualTo(fixedNow);
        assertThat(resp.deadlineAt()).isEqualTo(fixedNow.plus(48, ChronoUnit.HOURS));
        assertThat(resp.commandKey()).isEqualTo("CMD-REQ-001");

        // D-03 Verification: General Case Deadline tuyệt đối không bị thay đổi
        assertThat(caseEntity.getSellerResponseDueAt()).isEqualTo(originalGeneralDeadline);

        // Verification entity persisted
        ArgumentCaptor<CaseEvidenceRequestEntity> captor = ArgumentCaptor.forClass(CaseEvidenceRequestEntity.class);
        verify(caseEvidenceRequestRepository).save(captor.capture());
        CaseEvidenceRequestEntity saved = captor.getValue();
        assertThat(saved.getPayloadHash()).isNotEmpty();
        assertThat(saved.getPayloadHash()).hasSize(64);
    }

    // ==================== 4. IDEMPOTENCY & CONCURRENCY TESTS ====================

    @Test
    @DisplayName("API 1 - D-07 Replay: Cùng commandKey và cùng payload trả về Replayed (200 OK)")
    void requestEvidence_whenSameCommandKeyAndSamePayload_replays200() {
        setupLockGateway();

        String reason = "Video mờ không rõ chi tiết tem niêm phong";
        List<String> items = List.of("Ảnh tem cận cảnh", "Video mở hộp đối soát");
        String hash = CaseEvidenceRequestService.computeCanonicalPayloadHash(caseId, reason, items);

        CaseEvidenceRequestEntity existing = new CaseEvidenceRequestEntity(
                UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
                caseId,
                round2Id,
                ktvId,
                reason,
                "[\"Ảnh tem cận cảnh\",\"Video mở hộp đối soát\"]",
                fixedNow.minus(1, ChronoUnit.HOURS),
                fixedNow.plus(47, ChronoUnit.HOURS),
                "CMD-REQ-001",
                hash
        );

        when(caseEvidenceRequestRepository.findByCommandKey("CMD-REQ-001")).thenReturn(Optional.of(existing));

        ReturnCaseDtos.KtvRequestEvidenceRequest req = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                reason,
                items,
                "CMD-REQ-001"
        );

        CaseEvidenceRequestService.KtvRequestEvidenceResult result = service.requestEvidence(ktvId, caseId, req);

        assertThat(result.isCreated()).isFalse(); // Replayed
        assertThat(result.response().requestId()).isEqualTo(UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"));
        assertThat(result.response().commandKey()).isEqualTo("CMD-REQ-001");

        // Không save thêm bản ghi nào
        verify(caseEvidenceRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("API 1 - D-07 Conflict: Cùng commandKey nhưng khác payload trả về 409 COMMAND_KEY_CONFLICT")
    void requestEvidence_whenSameCommandKeyAndDifferentPayload_throwsConflict() {
        setupLockGateway();

        String existingHash = CaseEvidenceRequestService.computeCanonicalPayloadHash(caseId, "Lý do ban đầu đã lưu", null);
        CaseEvidenceRequestEntity existing = new CaseEvidenceRequestEntity(
                UUID.randomUUID(),
                caseId,
                round2Id,
                ktvId,
                "Lý do ban đầu đã lưu",
                null,
                fixedNow.minus(1, ChronoUnit.HOURS),
                fixedNow.plus(47, ChronoUnit.HOURS),
                "CMD-REQ-001",
                existingHash
        );
        when(caseEvidenceRequestRepository.findByCommandKey("CMD-REQ-001")).thenReturn(Optional.of(existing));

        ReturnCaseDtos.KtvRequestEvidenceRequest req = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Lý do khác hoàn toàn so với trước",
                List.of("Ảnh tem"),
                "CMD-REQ-001"
        );

        assertThatThrownBy(() -> service.requestEvidence(ktvId, caseId, req))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(tsEx.getCode()).isEqualTo("COMMAND_KEY_CONFLICT");
                });
    }

    @Test
    @DisplayName("API 1 - Chống rò rỉ xuyên Case: Cùng commandKey cho Case khác nhau trả về 409 COMMAND_KEY_CONFLICT")
    void requestEvidence_whenSameCommandKeyAcrossDifferentCases_throwsConflict() {
        setupLockGateway();

        CaseEvidenceRequestEntity existingOtherCase = new CaseEvidenceRequestEntity(
                UUID.randomUUID(),
                999999L, // Khác caseId
                round2Id,
                ktvId,
                "Lý do khác",
                null,
                fixedNow,
                fixedNow.plus(48, ChronoUnit.HOURS),
                "CMD-REQ-001",
                "dummyhash"
        );
        when(caseEvidenceRequestRepository.findByCommandKey("CMD-REQ-001")).thenReturn(Optional.of(existingOtherCase));

        ReturnCaseDtos.KtvRequestEvidenceRequest req = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Video mờ không rõ chi tiết tem niêm phong",
                null,
                "CMD-REQ-001"
        );

        assertThatThrownBy(() -> service.requestEvidence(ktvId, caseId, req))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(tsEx.getCode()).isEqualTo("COMMAND_KEY_CONFLICT");
                });
    }

    @Test
    @DisplayName("API 1 - Idempotency Precedence: Kiểm tra Idempotency trước State Guards (Replay thành công kể cả khi có PENDING)")
    void requestEvidence_idempotencyCheckedBeforeStateGuards() {
        setupLockGateway();

        String reason = "Video mờ không rõ chi tiết tem niêm phong";
        String hash = CaseEvidenceRequestService.computeCanonicalPayloadHash(caseId, reason, null);

        CaseEvidenceRequestEntity existing = new CaseEvidenceRequestEntity(
                UUID.randomUUID(),
                caseId,
                round2Id,
                ktvId,
                reason,
                null,
                fixedNow.minus(1, ChronoUnit.HOURS),
                fixedNow.plus(47, ChronoUnit.HOURS),
                "CMD-REQ-001",
                hash
        );
        when(caseEvidenceRequestRepository.findByCommandKey("CMD-REQ-001")).thenReturn(Optional.of(existing));

        // State guards would fail if checked: general deadline overdue!
        caseEntity.setSellerResponseDueAt(fixedNow.minus(1, ChronoUnit.DAYS));

        ReturnCaseDtos.KtvRequestEvidenceRequest req = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                reason,
                null,
                "CMD-REQ-001"
        );

        // Phải Replay thành công, không được ném GENERAL_DEADLINE_EXPIRED hay PENDING_REQUEST_EXISTS
        CaseEvidenceRequestService.KtvRequestEvidenceResult result = service.requestEvidence(ktvId, caseId, req);
        assertThat(result.isCreated()).isFalse();
        assertThat(result.response().commandKey()).isEqualTo("CMD-REQ-001");
    }

    // ==================== 5. CANONICAL PAYLOAD HASH TESTS ====================

    @Test
    @DisplayName("API 1 - Băm Canonical Payload: Tất định, sắp xếp requestedItems tăng dần, bỏ qua commandKey")
    void computeCanonicalPayloadHash_isDeterministicAndSortsRequestedItems() {
        String hash1 = CaseEvidenceRequestService.computeCanonicalPayloadHash(
                100L,
                "  Lý do yêu cầu\r\nchi tiết  ",
                List.of("Video unbox", "Ảnh vết rách", "Biên bản đồng kiểm")
        );

        // Đổi thứ tự requestedItems trong mảng và dùng dấu xuống dòng khác
        String hash2 = CaseEvidenceRequestService.computeCanonicalPayloadHash(
                100L,
                "Lý do yêu cầu\nchi tiết",
                List.of("Biên bản đồng kiểm", "Ảnh vết rách", "Video unbox")
        );

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
        assertThat(hash1).matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("API 1 - Băm Canonical Payload: Chuẩn hóa trường rỗng/khoảng trắng thành null theo Contract L649")
    void computeCanonicalPayloadHash_normalizesBlankAndNullReasonIdentically() {
        String hashNull = CaseEvidenceRequestService.computeCanonicalPayloadHash(100L, null, null);
        String hashEmpty = CaseEvidenceRequestService.computeCanonicalPayloadHash(100L, "", null);
        String hashWhitespace = CaseEvidenceRequestService.computeCanonicalPayloadHash(100L, "   \r\n\t  ", null);

        assertThat(hashEmpty).isEqualTo(hashNull);
        assertThat(hashWhitespace).isEqualTo(hashNull);
    }
}
