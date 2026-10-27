package com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test JPQL cho 5 JPA Entity thuộc phân hệ Trả hàng & Tranh chấp nhiều vòng.
 * <p>
 * <b>LƯU Ý QUAN TRỌNG:</b> Test này chạy trên môi trường H2 In-Memory với {@code ddl-auto=update}.
 * Mục đích chỉ là kiểm tra biên dịch (compilation), khởi tạo Entity, quan hệ mapping JPQL và cú pháp JPA.
 * Test này <b>KHÔNG</b> kiểm tra hoặc chứng minh tính tương thích với database schema thật,
 * view {@code og_compat}, hay trigger PostgreSQL (xem Task 1.1b / {@code CaseEntitiesPostgresTest} cho kiểm thử schema thật).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("CaseEntitiesSmokeTest - JPQL mapping and compilation smoke test on H2")
class CaseEntitiesSmokeTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Should successfully execute JPQL SELECT across all 5 case entities")
    void testSelectEntitiesMapping() {
        // Kiểm tra câu lệnh SELECT JPQL cho cả 5 Entity
        List<CaseEntity> cases = entityManager.createQuery("SELECT c FROM CaseEntity c", CaseEntity.class)
                .setMaxResults(1)
                .getResultList();
        assertThat(cases).isNotNull();

        List<CaseRoundEntity> rounds = entityManager.createQuery("SELECT r FROM CaseRoundEntity r", CaseRoundEntity.class)
                .setMaxResults(1)
                .getResultList();
        assertThat(rounds).isNotNull();

        List<CaseDecisionEntity> decisions = entityManager.createQuery("SELECT d FROM CaseDecisionEntity d", CaseDecisionEntity.class)
                .setMaxResults(1)
                .getResultList();
        assertThat(decisions).isNotNull();

        List<CaseEvidenceEntity> evidences = entityManager.createQuery("SELECT e FROM CaseEvidenceEntity e", CaseEvidenceEntity.class)
                .setMaxResults(1)
                .getResultList();
        assertThat(evidences).isNotNull();

        List<CaseEventEntity> events = entityManager.createQuery("SELECT v FROM CaseEventEntity v", CaseEventEntity.class)
                .setMaxResults(1)
                .getResultList();
        assertThat(events).isNotNull();
    }

    @Test
    @DisplayName("Should persist and flush complete sample case graph then rollback")
    void testInsertSampleCaseAndRollback() {
        // 1. Tạo CaseEntity
        CaseEntity sampleCase = new CaseEntity(
                CaseEntity.TYPE_RETURN,
                1001L,
                2001L,
                "[CHANGE_MIND] Người mua đổi ý, chưa mở hộp sản phẩm",
                "CMD_TEST_CASE_" + UUID.randomUUID()
        );
        entityManager.persist(sampleCase);
        entityManager.flush();
        assertThat(sampleCase.getId()).isNotNull();
        assertThat(sampleCase.getReasonCode()).isEqualTo(ReturnReasonCode.CHANGE_MIND);
        assertThat(sampleCase.getCleanDescription()).isEqualTo("Người mua đổi ý, chưa mở hộp sản phẩm");

        // 2. Tạo CaseRoundEntity
        CaseRoundEntity round = new CaseRoundEntity(sampleCase.getId(), 1, CaseRoundEntity.STAGE_RETURN_APPROVAL);
        entityManager.persist(round);
        entityManager.flush();
        assertThat(round.getId()).isNotNull();
        assertThat(round.getCaseId()).isEqualTo(sampleCase.getId());

        // 3. Tạo CaseDecisionEntity
        CaseDecisionEntity decision = new CaseDecisionEntity(
                round.getId(),
                3001L,
                CaseDecisionEntity.ACTOR_USER,
                CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                true,
                new BigDecimal("250000.00"),
                "Người bán đồng ý cho trả hàng",
                "CMD_TEST_DECISION_" + UUID.randomUUID()
        );
        entityManager.persist(decision);
        entityManager.flush();
        assertThat(decision.getId()).isNotNull();

        // 4. Tạo CaseEvidenceEntity
        UUID evidenceId = UUID.randomUUID();
        CaseEvidenceEntity evidence = new CaseEvidenceEntity(
                evidenceId,
                round.getId(),
                2001L,
                1,
                CaseEvidenceEntity.MEDIA_IMAGE,
                "cases/evidences/test_photo.jpg",
                "Ảnh chụp nguyên seal khi nhận hàng",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                204800L,
                null
        );
        entityManager.persist(evidence);
        entityManager.flush();
        assertThat(evidence.getId()).isEqualTo(evidenceId);

        // 5. Tạo CaseEventEntity
        UUID eventId = UUID.randomUUID();
        CaseEventEntity event = new CaseEventEntity(
                eventId,
                sampleCase.getId(),
                round.getId(),
                2001L,
                3001L,
                CaseEventEntity.TYPE_SUBMITTED,
                "Mở hồ sơ yêu cầu trả hàng",
                "{\"client_ip\":\"127.0.0.1\"}",
                "CMD_TEST_EVENT_" + UUID.randomUUID()
        );
        entityManager.persist(event);
        entityManager.flush();
        assertThat(event.getId()).isEqualTo(eventId);
    }
}
