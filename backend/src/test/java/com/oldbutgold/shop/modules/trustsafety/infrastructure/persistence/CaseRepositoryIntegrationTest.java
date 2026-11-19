package com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository Integration Test cho phương thức CaseRepository.findEscalatedCases(...) (Task 5.3).
 * Kiểm thử trực tiếp câu lệnh JPQL đối với database test thực tế (In-Memory H2 trong test profile),
 * không mock repository và không mock query.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("CaseRepositoryIntegrationTest - Targeted verification for findEscalatedCases")
class CaseRepositoryIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private CaseRepository caseRepository;

    private CaseEntity createCase(String state, Long assignedTo) {
        CaseEntity c = new CaseEntity(
                CaseEntity.TYPE_RETURN,
                1001L,
                2001L,
                "[CHANGE_MIND] Test case for escalation query verification",
                "CMD_CASE_" + UUID.randomUUID()
        );
        c.setState(state);
        c.setAssignedTo(assignedTo);
        entityManager.persist(c);
        return c;
    }

    private CaseRoundEntity createRound(Long caseId, int roundNo, String state, String stage, Instant completeSubmissionAt) {
        CaseRoundEntity r = new CaseRoundEntity(caseId, roundNo, stage);
        r.setState(state);
        r.setCompleteSubmissionAt(completeSubmissionAt);
        entityManager.persist(r);
        return r;
    }

    private CaseEventEntity createEscalatedEvent(Long caseId, Long roundId) {
        CaseEventEntity e = new CaseEventEntity(
                UUID.randomUUID(),
                caseId,
                roundId,
                null,
                1L,
                CaseEventEntity.TYPE_ESCALATED,
                "KTV_SLA_EXPIRED_48H",
                "{}",
                "CMD_EVENT_" + UUID.randomUUID()
        );
        entityManager.persist(e);
        return e;
    }

    private CaseDecisionEntity createDecision(Long roundId, boolean isFinal) {
        CaseDecisionEntity d = new CaseDecisionEntity(
                roundId,
                3001L,
                CaseDecisionEntity.ACTOR_USER,
                CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                isFinal,
                BigDecimal.ZERO,
                "Decision for testing",
                "CMD_DEC_" + UUID.randomUUID()
        );
        entityManager.persist(d);
        return d;
    }

    // =========================================================================
    // GROUP A: ESCALATED case được trả về
    // =========================================================================
    @Nested
    @DisplayName("Group A: ESCALATED case được trả về")
    class GroupATests {

        @Test
        @DisplayName("Case thỏa mãn đầy đủ 5 điều kiện escalation phải xuất hiện trong kết quả")
        void testEscalatedCaseIncluded_success() {
            Instant submissionAt = Instant.now().minus(50, ChronoUnit.HOURS);
            CaseEntity c = createCase(CaseEntity.STATE_POST_RETURN, 901L);
            CaseRoundEntity r = createRound(c.getId(), 2, CaseRoundEntity.STATE_OPEN,
                    CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION, submissionAt);
            createEscalatedEvent(c.getId(), r.getId());
            entityManager.flush();

            Page<CaseEntity> result = caseRepository.findEscalatedCases(null, PageRequest.of(0, 10));

            assertThat(result.getContent())
                    .extracting(CaseEntity::getId)
                    .contains(c.getId());
        }
    }

    // =========================================================================
    // GROUP B: Loại case chưa ESCALATED
    // =========================================================================
    @Nested
    @DisplayName("Group B: Loại case chưa ESCALATED")
    class GroupBTests {

        @Test
        @DisplayName("Case có Round 2 OPEN và stage đúng nhưng chưa có event ESCALATED -> không được trả về")
        void testNonEscalatedCaseExcluded() {
            Instant submissionAt = Instant.now().minus(50, ChronoUnit.HOURS);
            CaseEntity c = createCase(CaseEntity.STATE_POST_RETURN, 901L);
            createRound(c.getId(), 2, CaseRoundEntity.STATE_OPEN,
                    CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION, submissionAt);
            // Không tạo event ESCALATED
            entityManager.flush();

            Page<CaseEntity> result = caseRepository.findEscalatedCases(null, PageRequest.of(0, 10));

            assertThat(result.getContent())
                    .extracting(CaseEntity::getId)
                    .doesNotContain(c.getId());
        }

        @Test
        @DisplayName("Case có state khác POST_RETURN dù có ESCALATED event -> không được trả về")
        void testCaseNotInPostReturnStateExcluded() {
            Instant submissionAt = Instant.now().minus(50, ChronoUnit.HOURS);
            CaseEntity c = createCase(CaseEntity.STATE_CLOSED, 901L);
            CaseRoundEntity r = createRound(c.getId(), 2, CaseRoundEntity.STATE_OPEN,
                    CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION, submissionAt);
            createEscalatedEvent(c.getId(), r.getId());
            entityManager.flush();

            Page<CaseEntity> result = caseRepository.findEscalatedCases(null, PageRequest.of(0, 10));

            assertThat(result.getContent())
                    .extracting(CaseEntity::getId)
                    .doesNotContain(c.getId());
        }

        @Test
        @DisplayName("Round 2 đã DECIDED dù có ESCALATED event -> không được trả về")
        void testRoundNotOpenExcluded() {
            Instant submissionAt = Instant.now().minus(50, ChronoUnit.HOURS);
            CaseEntity c = createCase(CaseEntity.STATE_POST_RETURN, 901L);
            CaseRoundEntity r = createRound(c.getId(), 2, CaseRoundEntity.STATE_DECIDED,
                    CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION, submissionAt);
            createEscalatedEvent(c.getId(), r.getId());
            entityManager.flush();

            Page<CaseEntity> result = caseRepository.findEscalatedCases(null, PageRequest.of(0, 10));

            assertThat(result.getContent())
                    .extracting(CaseEntity::getId)
                    .doesNotContain(c.getId());
        }
    }

    // =========================================================================
    // GROUP C: Loại case đã có final decision
    // =========================================================================
    @Nested
    @DisplayName("Group C: Loại case đã có final decision")
    class GroupCTests {

        @Test
        @DisplayName("Case đã có final decision trên Round 2 -> không xuất hiện trong danh sách")
        void testCaseWithFinalDecisionExcluded() {
            Instant submissionAt = Instant.now().minus(50, ChronoUnit.HOURS);
            CaseEntity c = createCase(CaseEntity.STATE_POST_RETURN, 901L);
            CaseRoundEntity r = createRound(c.getId(), 2, CaseRoundEntity.STATE_OPEN,
                    CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION, submissionAt);
            createEscalatedEvent(c.getId(), r.getId());
            // Tạo final decision cho Round 2
            createDecision(r.getId(), true);
            entityManager.flush();

            Page<CaseEntity> result = caseRepository.findEscalatedCases(null, PageRequest.of(0, 10));

            assertThat(result.getContent())
                    .extracting(CaseEntity::getId)
                    .doesNotContain(c.getId());
        }

        @Test
        @DisplayName("Case chỉ có non-final decision (isFinal = false) -> vẫn xuất hiện trong danh sách")
        void testCaseWithNonFinalDecisionStillIncluded() {
            Instant submissionAt = Instant.now().minus(50, ChronoUnit.HOURS);
            CaseEntity c = createCase(CaseEntity.STATE_POST_RETURN, 901L);
            CaseRoundEntity r = createRound(c.getId(), 2, CaseRoundEntity.STATE_OPEN,
                    CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION, submissionAt);
            createEscalatedEvent(c.getId(), r.getId());
            // Tạo non-final decision
            createDecision(r.getId(), false);
            entityManager.flush();

            Page<CaseEntity> result = caseRepository.findEscalatedCases(null, PageRequest.of(0, 10));

            assertThat(result.getContent())
                    .extracting(CaseEntity::getId)
                    .contains(c.getId());
        }
    }

    // =========================================================================
    // GROUP D: Lọc ktvId
    // =========================================================================
    @Nested
    @DisplayName("Group D: Lọc ktvId")
    class GroupDTests {

        @Test
        @DisplayName("Lọc theo ktvId cụ thể chỉ trả case gán cho KTV đó; ktvId = null trả toàn bộ")
        void testFilterByKtvId() {
            Long ktv1 = 801L;
            Long ktv2 = 802L;

            Instant subAt = Instant.now().minus(50, ChronoUnit.HOURS);

            // Case 1 gán cho KTV 801
            CaseEntity c1 = createCase(CaseEntity.STATE_POST_RETURN, ktv1);
            CaseRoundEntity r1 = createRound(c1.getId(), 2, CaseRoundEntity.STATE_OPEN,
                    CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION, subAt);
            createEscalatedEvent(c1.getId(), r1.getId());

            // Case 2 gán cho KTV 802
            CaseEntity c2 = createCase(CaseEntity.STATE_POST_RETURN, ktv2);
            CaseRoundEntity r2 = createRound(c2.getId(), 2, CaseRoundEntity.STATE_OPEN,
                    CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION, subAt);
            createEscalatedEvent(c2.getId(), r2.getId());

            entityManager.flush();

            // 1. Lọc theo KTV 801
            Page<CaseEntity> resultKtv1 = caseRepository.findEscalatedCases(ktv1, PageRequest.of(0, 10));
            assertThat(resultKtv1.getContent()).extracting(CaseEntity::getId).contains(c1.getId());
            assertThat(resultKtv1.getContent()).extracting(CaseEntity::getId).doesNotContain(c2.getId());

            // 2. Lọc theo KTV 802
            Page<CaseEntity> resultKtv2 = caseRepository.findEscalatedCases(ktv2, PageRequest.of(0, 10));
            assertThat(resultKtv2.getContent()).extracting(CaseEntity::getId).contains(c2.getId());
            assertThat(resultKtv2.getContent()).extracting(CaseEntity::getId).doesNotContain(c1.getId());

            // 3. Không lọc (ktvId = null) -> trả cả 2
            Page<CaseEntity> resultAll = caseRepository.findEscalatedCases(null, PageRequest.of(0, 10));
            assertThat(resultAll.getContent()).extracting(CaseEntity::getId).contains(c1.getId(), c2.getId());
        }
    }

    // =========================================================================
    // GROUP E: Pagination và totalElements
    // =========================================================================
    @Nested
    @DisplayName("Group E: Pagination và totalElements")
    class GroupETests {

        @Test
        @DisplayName("Phân trang đúng kích thước, countQuery chính xác và không nhân bản bản ghi")
        void testPaginationAndTotalElements() {
            Long dedicatedKtv = 9991L;
            Instant subAt = Instant.now().minus(50, ChronoUnit.HOURS);

            // Tạo 5 Case escalated cho cùng 1 KTV riêng để test phân trang cô lập
            CaseEntity c1 = createCase(CaseEntity.STATE_POST_RETURN, dedicatedKtv);
            CaseRoundEntity r1 = createRound(c1.getId(), 2, CaseRoundEntity.STATE_OPEN,
                    CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION, subAt.minus(5, ChronoUnit.HOURS));
            createEscalatedEvent(c1.getId(), r1.getId());

            CaseEntity c2 = createCase(CaseEntity.STATE_POST_RETURN, dedicatedKtv);
            CaseRoundEntity r2 = createRound(c2.getId(), 2, CaseRoundEntity.STATE_OPEN,
                    CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION, subAt.minus(4, ChronoUnit.HOURS));
            createEscalatedEvent(c2.getId(), r2.getId());

            CaseEntity c3 = createCase(CaseEntity.STATE_POST_RETURN, dedicatedKtv);
            CaseRoundEntity r3 = createRound(c3.getId(), 2, CaseRoundEntity.STATE_OPEN,
                    CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION, subAt.minus(3, ChronoUnit.HOURS));
            createEscalatedEvent(c3.getId(), r3.getId());

            CaseEntity c4 = createCase(CaseEntity.STATE_POST_RETURN, dedicatedKtv);
            CaseRoundEntity r4 = createRound(c4.getId(), 2, CaseRoundEntity.STATE_OPEN,
                    CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION, subAt.minus(2, ChronoUnit.HOURS));
            createEscalatedEvent(c4.getId(), r4.getId());

            CaseEntity c5 = createCase(CaseEntity.STATE_POST_RETURN, dedicatedKtv);
            CaseRoundEntity r5 = createRound(c5.getId(), 2, CaseRoundEntity.STATE_OPEN,
                    CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION, subAt.minus(1, ChronoUnit.HOURS));
            createEscalatedEvent(c5.getId(), r5.getId());

            entityManager.flush();

            // Trang 0, size = 2
            Page<CaseEntity> page0 = caseRepository.findEscalatedCases(dedicatedKtv, PageRequest.of(0, 2));
            assertThat(page0.getContent()).hasSize(2);
            assertThat(page0.getTotalElements()).isEqualTo(5);
            assertThat(page0.getTotalPages()).isEqualTo(3);
            assertThat(page0.isFirst()).isTrue();
            assertThat(page0.hasNext()).isTrue();

            // Trang 1, size = 2
            Page<CaseEntity> page1 = caseRepository.findEscalatedCases(dedicatedKtv, PageRequest.of(1, 2));
            assertThat(page1.getContent()).hasSize(2);
            assertThat(page1.getTotalElements()).isEqualTo(5);
            assertThat(page1.hasNext()).isTrue();

            // Trang 2, size = 2
            Page<CaseEntity> page2 = caseRepository.findEscalatedCases(dedicatedKtv, PageRequest.of(2, 2));
            assertThat(page2.getContent()).hasSize(1);
            assertThat(page2.isLast()).isTrue();

            // Kiểm tra tính rời rạc giữa các trang (không trùng lặp Case giữa các trang)
            List<Long> idsPage0 = page0.getContent().stream().map(CaseEntity::getId).toList();
            List<Long> idsPage1 = page1.getContent().stream().map(CaseEntity::getId).toList();
            List<Long> idsPage2 = page2.getContent().stream().map(CaseEntity::getId).toList();

            assertThat(idsPage0).doesNotContainAnyElementsOf(idsPage1);
            assertThat(idsPage0).doesNotContainAnyElementsOf(idsPage2);
            assertThat(idsPage1).doesNotContainAnyElementsOf(idsPage2);
        }
    }

    // =========================================================================
    // GROUP F: SLA sorting
    // =========================================================================
    @Nested
    @DisplayName("Group F: SLA sorting")
    class GroupFTests {

        @Test
        @DisplayName("Sắp xếp theo completeSubmissionAt ASC: hồ sơ nộp bằng chứng sớm nhất (quá hạn lâu nhất) xuất hiện trước")
        void testSlaSorting_earliestCompleteSubmissionFirst() {
            Long dedicatedKtv = 9992L;
            Instant now = Instant.now();

            // Case 1: nộp cách đây 72 giờ (quá hạn 24 giờ) -> ưu tiên số 1
            Instant subAt1 = now.minus(72, ChronoUnit.HOURS);
            CaseEntity c1 = createCase(CaseEntity.STATE_POST_RETURN, dedicatedKtv);
            CaseRoundEntity r1 = createRound(c1.getId(), 2, CaseRoundEntity.STATE_OPEN,
                    CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION, subAt1);
            createEscalatedEvent(c1.getId(), r1.getId());

            // Case 2: nộp cách đây 60 giờ (quá hạn 12 giờ) -> ưu tiên số 2
            Instant subAt2 = now.minus(60, ChronoUnit.HOURS);
            CaseEntity c2 = createCase(CaseEntity.STATE_POST_RETURN, dedicatedKtv);
            CaseRoundEntity r2 = createRound(c2.getId(), 2, CaseRoundEntity.STATE_OPEN,
                    CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION, subAt2);
            createEscalatedEvent(c2.getId(), r2.getId());

            // Case 3: nộp cách đây 49 giờ (quá hạn 1 giờ) -> ưu tiên số 3
            Instant subAt3 = now.minus(49, ChronoUnit.HOURS);
            CaseEntity c3 = createCase(CaseEntity.STATE_POST_RETURN, dedicatedKtv);
            CaseRoundEntity r3 = createRound(c3.getId(), 2, CaseRoundEntity.STATE_OPEN,
                    CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION, subAt3);
            createEscalatedEvent(c3.getId(), r3.getId());

            entityManager.flush();

            Page<CaseEntity> result = caseRepository.findEscalatedCases(dedicatedKtv, PageRequest.of(0, 10));

            List<Long> orderedIds = result.getContent().stream().map(CaseEntity::getId).toList();
            assertThat(orderedIds).containsExactly(c1.getId(), c2.getId(), c3.getId());
        }
    }
}

