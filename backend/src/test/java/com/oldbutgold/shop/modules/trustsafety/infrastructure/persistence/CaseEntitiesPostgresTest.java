package com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence;

import com.oldbutgold.shop.shared.PostgresTestSafetyGuard;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Task 1.1b: Real PostgreSQL Integration Test for Multi-Round Return & Dispute Entities & Triggers.
 * <p>
 * Validates:
 * 1. Hibernate ddl-auto=validate against real PostgreSQL schema (og_compat views).
 * 2. SELECT queries across all 5 og_compat case views.
 * 3. Happy path: insert CaseEntity against valid order fixture and verify trigger trg_return_case_holds
 *    creates money_holds for GOODS_CASH and PLATFORM_GOODS_SUBSIDY.
 * 4. Negative tests: 7 tests verifying exact SQLSTATE 23514 and error messages from og_guard_return_case trigger.
 *    Each negative assertion runs with an isolated Savepoint to prevent transaction poisoning (Postgres 25P02).
 * <p>
 * Database Cleanup Strategy:
 * DB triggers explicitly block DELETE on UC83 orders ('UC83 order checkout snapshot retained') and
 * order_fund_components ('Fund component history retained'). Therefore, cleanup is achieved entirely via
 * Spring {@code @Transactional} rollback per test, ensuring zero lingering rows and zero trigger conflicts.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
@DisplayName("CaseEntitiesPostgresTest - Real PostgreSQL Integration Test (og_compat & triggers)")
class CaseEntitiesPostgresTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String url = PostgresTestSafetyGuard.resolveAndAssertSafeFromEnv("OGSHOP_TEST_DB_URL");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv("OGSHOP_TEST_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("OGSHOP_TEST_DB_PASSWORD"));
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO og_compat,public");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long buyerId;
    private Long sellerId;

    @BeforeEach
    void setUp() {
        buyerId = createTestUser("buyer");
        sellerId = createTestUser("seller");
    }

    private Long createTestUser(String prefix) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.users(email, password_hash, full_name) VALUES (?, 'fixture_pass', ?) RETURNING user_id",
                Long.class,
                prefix + "_" + UUID.randomUUID() + "@example.test",
                "User " + prefix
        );
    }

    private UUID createCheckoutGroup(Long buyerId, BigDecimal expectedTotal) {
        UUID groupId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO checkout_groups(
                    group_id, buyer_id, command_key, request_digest, address_snapshot,
                    delivery_method, payment_method, expected_total, payment_due_at
                ) VALUES (
                    ?, ?, ?, repeat('a', 64),
                    '{"recipient_name":"Buyer","phone_number":"0912345678","province":"HN","district":"BD","ward":"Ward","detail_address":"Detail address"}'::jsonb,
                    'SELF_PICKUP', 'BANK_TRANSFER_MOCK', ?, CURRENT_TIMESTAMP + INTERVAL '1 hour'
                )
                """,
                groupId, buyerId, "CMD_GRP_" + UUID.randomUUID(), expectedTotal
        );
        return groupId;
    }

    private Long createTestOrder(String workflowModel, boolean returnAllowed, String deliveredInterval, boolean earlyCompleted) {
        UUID groupId = null;
        if ("UC83".equals(workflowModel)) {
            groupId = createCheckoutGroup(buyerId, new BigDecimal("300000.00"));
        }

        String deliveredSql = (deliveredInterval != null) ? "CURRENT_TIMESTAMP - INTERVAL '" + deliveredInterval + "'" : "NULL";
        String earlyCompletedSql = earlyCompleted ? "CURRENT_TIMESTAMP" : "NULL";
        String earlyWarningSql = earlyCompleted ? "'WARNING_V1'" : "NULL";

        return jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.orders(
                    checkout_group_id, group_id, buyer_id, seller_id, shipping_recipient_name, shipping_phone_number,
                    shipping_province, shipping_district, shipping_ward, shipping_detail_address,
                    subtotal, shipping_fee, total_amount, buyer_system_fee, seller_system_fee, seller_proceeds,
                    payment_due_at, workflow_model, return_allowed, delivery_method, handover_day,
                    valid_delivered_at, early_completed_at, early_completion_warning_version
                ) VALUES (
                    ?, ?, ?, ?, 'Recipient', '0912345678',
                    'HN', 'BD', 'Ward', 'Detail address',
                    300000, 0, 300000, 0, 0, 300000,
                    CURRENT_TIMESTAMP + INTERVAL '1 hour', ?, ?, 'SELF_PICKUP', CURRENT_DATE,
                    """ + deliveredSql + ", " + earlyCompletedSql + ", " + earlyWarningSql + """
                ) RETURNING order_id
                """,
                Long.class,
                groupId != null ? groupId : UUID.randomUUID(),
                groupId,
                buyerId, sellerId, workflowModel, returnAllowed
        );
    }

    /**
     * Fixture for Happy Path: creates a UC83 order with voucher discount, performs full payment allocation,
     * updates order to PAID_HELD/DELIVERED, and creates GOODS_CASH and PLATFORM_GOODS_SUBSIDY fund components.
     * Fully complies with {@code og_guard_fund_component} and {@code ck_orders_amounts}.
     */
    private record PaidOrderFixture(Long orderId, UUID cashComponentId, UUID subsidyComponentId) {}

    private PaidOrderFixture createPaidOrderWithFundComponents() {
        BigDecimal subtotal = new BigDecimal("300000.00");
        BigDecimal voucherDiscount = new BigDecimal("50000.00");
        BigDecimal totalAmount = new BigDecimal("250000.00");

        UUID groupId = createCheckoutGroup(buyerId, totalAmount);

        Long orderId = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.orders(
                    checkout_group_id, group_id, buyer_id, seller_id, shipping_recipient_name, shipping_phone_number,
                    shipping_province, shipping_district, shipping_ward, shipping_detail_address,
                    subtotal, shipping_fee, total_amount, voucher_discount_amount, buyer_system_fee, seller_system_fee,
                    seller_proceeds, payment_due_at, workflow_model, return_allowed, delivery_method, handover_day,
                    status, valid_delivered_at
                ) VALUES (
                    ?, ?, ?, ?, 'Recipient', '0912345678',
                    'HN', 'BD', 'Ward', 'Detail address',
                    ?, 0, ?, ?, 0, 0,
                    ?, CURRENT_TIMESTAMP + INTERVAL '1 hour', 'UC83', true, 'SELF_PICKUP', CURRENT_DATE,
                    'PAYMENT_PENDING', CURRENT_TIMESTAMP - INTERVAL '1 day'
                ) RETURNING order_id
                """,
                Long.class,
                groupId, groupId, buyerId, sellerId,
                subtotal, totalAmount, voucherDiscount, subtotal
        );

        // 1. payment_intents
        UUID intentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.payment_intents(
                    intent_id, purpose, group_id, expected_amount, payment_method, deadline, command_key
                ) VALUES (?, 'CHECKOUT_GROUP', ?, ?, 'BANK_TRANSFER_MOCK', CURRENT_TIMESTAMP + INTERVAL '1 hour', ?)
                """,
                intentId, groupId, totalAmount, "CMD_INT_" + UUID.randomUUID()
        );

        // 2. payment_attempts
        Long attemptId = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.payment_attempts(
                    intent_id, txn_ref, amount, provider
                ) VALUES (?, ?, ?, 'BANK_TRANSFER_MOCK') RETURNING attempt_id
                """,
                Long.class,
                intentId, "TXN_" + UUID.randomUUID(), totalAmount
        );

        // 3. payment_confirmations
        UUID confirmationId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.payment_confirmations(
                    confirmation_id, intent_id, attempt_id, provider, provider_transaction_id, amount
                ) VALUES (?, ?, ?, 'BANK_TRANSFER_MOCK', ?, ?)
                """,
                confirmationId, intentId, attemptId, "TXN_PROV_" + UUID.randomUUID(), totalAmount
        );

        // 4. payment_allocations (orders must be PAYMENT_PENDING here)
        UUID allocationId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.payment_allocations(
                    allocation_id, intent_id, confirmation_id, order_id, amount
                ) VALUES (?, ?, ?, ?, ?)
                """,
                allocationId, intentId, confirmationId, orderId, totalAmount
        );

        // 5. Update orders to DELIVERED with paid_confirmed_at populated
        jdbcTemplate.update("""
                UPDATE og_compat.orders
                SET status = 'DELIVERED',
                    paid_confirmed_at = (SELECT confirmed_at FROM og_compat.payment_confirmations WHERE confirmation_id = ?)
                WHERE order_id = ?
                """,
                confirmationId, orderId
        );

        // 6. order_fund_components: GOODS_CASH (250,000)
        UUID cashComponentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.order_fund_components(
                    component_id, order_id, allocation_id, component_type, confirmed_amount
                ) VALUES (?, ?, ?, 'GOODS_CASH', ?)
                """,
                cashComponentId, orderId, allocationId, totalAmount
        );

        // 7. order_fund_components: PLATFORM_GOODS_SUBSIDY (50,000)
        UUID subsidyComponentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.order_fund_components(
                    component_id, order_id, component_type, confirmed_amount, funding_reference
                ) VALUES (?, ?, 'PLATFORM_GOODS_SUBSIDY', ?, 'VOUCHER_GRANT_ESCROW')
                """,
                subsidyComponentId, orderId, voucherDiscount
        );

        return new PaidOrderFixture(orderId, cashComponentId, subsidyComponentId);
    }

    @Test
    @DisplayName("Should successfully execute JPQL SELECT across all 5 og_compat views with ddl-auto=validate")
    void testSelectAcrossAllFiveViews() {
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
    @DisplayName("Happy path: Insert CaseEntity creates money_holds via trigger and supports full entity graph")
    void testInsertValidReturnCaseAndVerifyMoneyHoldsTrigger() {
        PaidOrderFixture fixture = createPaidOrderWithFundComponents();

        // 1. Create CaseEntity
        CaseEntity returnCase = new CaseEntity(
                CaseEntity.TYPE_RETURN,
                fixture.orderId(),
                buyerId,
                "[CHANGE_MIND] Product unboxing unopened, buyer changed mind",
                "CMD_PG_CASE_" + UUID.randomUUID()
        );
        entityManager.persist(returnCase);
        entityManager.flush();

        assertThat(returnCase.getId()).isNotNull();
        assertThat(returnCase.getId()).isPositive();

        // Verify trigger og_return_case_money_holds automatically created money_holds
        List<Map<String, Object>> holds = jdbcTemplate.queryForList(
                "SELECT hold_id, component_id, source_key, reason, case_id FROM og_compat.money_holds WHERE case_id = ?",
                returnCase.getId()
        );
        assertThat(holds).hasSize(2);
        assertThat(holds).extracting(m -> m.get("source_key"))
                .containsOnly("RETURN_CASE:" + returnCase.getId());
        assertThat(holds).extracting(m -> (UUID) m.get("component_id"))
                .containsExactlyInAnyOrder(fixture.cashComponentId(), fixture.subsidyComponentId());

        // 2. Create CaseRoundEntity
        CaseRoundEntity round = new CaseRoundEntity(returnCase.getId(), 1, CaseRoundEntity.STAGE_RETURN_APPROVAL);
        entityManager.persist(round);
        entityManager.flush();
        assertThat(round.getId()).isNotNull();

        // 3. Create CaseEvidenceEntity (while round is OPEN, before final decision)
        UUID evidenceId = UUID.randomUUID();
        CaseEvidenceEntity evidence = new CaseEvidenceEntity(
                evidenceId,
                round.getId(),
                buyerId,
                1,
                CaseEvidenceEntity.MEDIA_IMAGE,
                "cases/evidences/real_photo.jpg",
                "Photo of intact package seal",
                "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
                102400L,
                null
        );
        entityManager.persist(evidence);
        entityManager.flush();
        assertThat(evidence.getId()).isEqualTo(evidenceId);

        // 4. Create CaseDecisionEntity (seller reviews evidence and decides; closes round to DECIDED)
        CaseDecisionEntity decision = new CaseDecisionEntity(
                round.getId(),
                sellerId,
                CaseDecisionEntity.ACTOR_USER,
                CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                true,
                new BigDecimal("250000.00"),
                "Seller accepts return request",
                "CMD_PG_DECISION_" + UUID.randomUUID()
        );
        entityManager.persist(decision);
        entityManager.flush();
        assertThat(decision.getId()).isNotNull();

        // 5. Create CaseEventEntity
        UUID eventId = UUID.randomUUID();
        CaseEventEntity event = new CaseEventEntity(
                eventId,
                returnCase.getId(),
                round.getId(),
                buyerId,
                sellerId,
                CaseEventEntity.TYPE_SUBMITTED,
                "Return request initiated with unboxing photos",
                "{\"client_ip\":\"127.0.0.1\"}",
                "CMD_PG_EVENT_" + UUID.randomUUID()
        );
        entityManager.persist(event);
        entityManager.flush();
        assertThat(event.getId()).isEqualTo(eventId);
    }

    @Test
    @DisplayName("Negative Test 1: Trigger rejects when state is not OPEN")
    void testRejectWhenStateNotOpen() {
        Long orderId = createTestOrder("UC83", true, "1 day", false);

        CaseEntity invalidCase = new CaseEntity(
                CaseEntity.TYPE_RETURN,
                orderId,
                buyerId,
                "[CHANGE_MIND] Reason detail",
                "CMD_NEG_1_" + UUID.randomUUID()
        );
        invalidCase.setState(CaseEntity.STATE_REVIEWING); // 'REVIEWING' is valid in CHECK(state) but rejected by trigger

        assertTriggerRejection(invalidCase, "New case starts open");
    }

    @Test
    @DisplayName("Negative Test 2: Trigger rejects when workflow_model is not UC83")
    void testRejectWhenWorkflowModelNotUC83() {
        Long orderId = createTestOrder("LEGACY_V14", true, "1 day", false); // 'LEGACY_V14' is valid in CHECK(workflow_model)

        CaseEntity invalidCase = new CaseEntity(
                CaseEntity.TYPE_RETURN,
                orderId,
                buyerId,
                "[CHANGE_MIND] Reason detail",
                "CMD_NEG_2_" + UUID.randomUUID()
        );

        assertTriggerRejection(invalidCase, "Order has no current right to request return");
    }

    @Test
    @DisplayName("Negative Test 3: Trigger rejects when created_by is not the buyer_id")
    void testRejectWhenCreatedByNotBuyer() {
        Long orderId = createTestOrder("UC83", true, "1 day", false);

        CaseEntity invalidCase = new CaseEntity(
                CaseEntity.TYPE_RETURN,
                orderId,
                sellerId, // created_by is seller, not buyer
                "[CHANGE_MIND] Reason detail",
                "CMD_NEG_3_" + UUID.randomUUID()
        );

        assertTriggerRejection(invalidCase, "Order has no current right to request return");
    }

    @Test
    @DisplayName("Negative Test 4: Trigger rejects when return_allowed is false")
    void testRejectWhenReturnNotAllowed() {
        Long orderId = createTestOrder("UC83", false, "1 day", false); // return_allowed = false

        CaseEntity invalidCase = new CaseEntity(
                CaseEntity.TYPE_RETURN,
                orderId,
                buyerId,
                "[CHANGE_MIND] Reason detail",
                "CMD_NEG_4_" + UUID.randomUUID()
        );

        assertTriggerRejection(invalidCase, "Order has no current right to request return");
    }

    @Test
    @DisplayName("Negative Test 5: Trigger rejects when valid_delivered_at is NULL")
    void testRejectWhenValidDeliveredAtNull() {
        Long orderId = createTestOrder("UC83", true, null, false); // valid_delivered_at = NULL

        CaseEntity invalidCase = new CaseEntity(
                CaseEntity.TYPE_RETURN,
                orderId,
                buyerId,
                "[CHANGE_MIND] Reason detail",
                "CMD_NEG_5_" + UUID.randomUUID()
        );

        assertTriggerRejection(invalidCase, "Order has no current right to request return");
    }

    @Test
    @DisplayName("Negative Test 6: Trigger rejects when order has early_completed_at set")
    void testRejectWhenAlreadyEarlyCompleted() {
        Long orderId = createTestOrder("UC83", true, "1 day", true); // early_completed_at IS NOT NULL

        CaseEntity invalidCase = new CaseEntity(
                CaseEntity.TYPE_RETURN,
                orderId,
                buyerId,
                "[CHANGE_MIND] Reason detail",
                "CMD_NEG_6_" + UUID.randomUUID()
        );

        assertTriggerRejection(invalidCase, "Order has no current right to request return");
    }

    @Test
    @DisplayName("Negative Test 7: Trigger rejects when return request exceeds 3 days from delivery")
    void testRejectWhenReturnWindowExpired() {
        Long orderId = createTestOrder("UC83", true, "4 days", false); // 4 days ago > 3 days window

        CaseEntity invalidCase = new CaseEntity(
                CaseEntity.TYPE_RETURN,
                orderId,
                buyerId,
                "[CHANGE_MIND] Reason detail",
                "CMD_NEG_7_" + UUID.randomUUID()
        );

        assertTriggerRejection(invalidCase, "Order has no current right to request return");
    }

    private void assertTriggerRejection(CaseEntity invalidCase, String expectedMessage) {
        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection -> {
            Savepoint sp = connection.setSavepoint("sp_case_guard");
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO og_compat.cases(
                        case_type, order_id, created_by, description, command_key, state
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                ps.setString(1, invalidCase.getCaseType());
                if (invalidCase.getOrderId() != null) {
                    ps.setLong(2, invalidCase.getOrderId());
                } else {
                    ps.setNull(2, Types.BIGINT);
                }
                ps.setLong(3, invalidCase.getCreatedBy());
                ps.setString(4, invalidCase.getDescription());
                ps.setString(5, invalidCase.getCommandKey());
                ps.setString(6, invalidCase.getState());
                ps.executeUpdate();
                fail("Expected trigger rejection with SQLSTATE 23514 but insert succeeded");
            } catch (SQLException ex) {
                try {
                    connection.rollback(sp);
                } catch (SQLException rollbackEx) {
                    // ignore rollback exception
                }
                assertThat(ex.getSQLState()).isEqualTo("23514");
                assertThat(ex.getMessage()).contains(expectedMessage);
            }
        });
    }
}
