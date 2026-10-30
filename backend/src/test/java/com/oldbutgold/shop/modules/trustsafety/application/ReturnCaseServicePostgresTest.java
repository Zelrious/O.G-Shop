package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
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
import com.oldbutgold.shop.shared.PostgresTestSafetyGuard;
import jakarta.persistence.EntityManager;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration Test for ReturnCaseService (Task 2.2, 2.3, 2.4) on real PostgreSQL.
 *
 * Verifies on real PostgreSQL schema (og_compat views and DB triggers):
 * 1. createReturnRequest triggers og_return_case_money_holds / trg_return_case_holds,
 *    creating money_holds for GOODS_CASH and PLATFORM_GOODS_SUBSIDY.
 * 2. og_order_has_blocking_case returns true when case is OPEN / REVIEWING / RETURN_APPROVED.
 * 3. sellerRespondRound1 handles APPROVE_RETURN and SELLER_DECLINED (first-write wins).
 * 4. ktvDecideRound1 handles APPROVE_RETURN and REJECT_RETURN.
 *    REJECT_RETURN closes money_holds, leaving order in DELIVERED without premature disbursement.
 * 5. withdrawReturnRequest closes case, closes money_holds, and unblocks order.
 * 6. Precondition guards on real PostgreSQL.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
@DisplayName("ReturnCaseServicePostgresTest - Real PostgreSQL Integration Test for Tasks 2.2, 2.3, 2.4")
class ReturnCaseServicePostgresTest {

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
    private ReturnCaseService returnCaseService;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private CaseRoundRepository caseRoundRepository;

    @Autowired
    private CaseEvidenceRepository caseEvidenceRepository;

    @Autowired
    private CaseDecisionRepository caseDecisionRepository;

    @Autowired
    private CaseEventRepository caseEventRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private Long buyerId;
    private Long sellerId;
    private Long ktvId;

    @BeforeEach
    void setUp() {
        buyerId = createTestUser("buyer");
        sellerId = createTestUser("seller");
        ktvId = createTestUser("ktv");
        grantRole(buyerId, "BUYER");
        grantRole(sellerId, "SELLER");
        grantRole(ktvId, "KTV");
    }

    private void grantRole(Long userId, String roleName) {
        jdbcTemplate.update("""
                INSERT INTO og_compat.user_roles(user_id, role_id)
                SELECT ?, role_id FROM og_compat.roles WHERE role_name = ?
                ON CONFLICT DO NOTHING
                """, userId, roleName);
    }

    private Long createTestUser(String prefix) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.users(email, password_hash, full_name, status) VALUES (?, 'fixture_pass', ?, 'ACTIVE') RETURNING user_id",
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

    private record Uc83OrderFixture(Long orderId, UUID cashComponentId, UUID subsidyComponentId) {}

    /**
     * Dựng đơn hàng UC83 ở trạng thái DELIVERED đầy đủ payment allocation
     * và fund components (GOODS_CASH + PLATFORM_GOODS_SUBSIDY).
     */
    private Uc83OrderFixture createDeliveredUc83Order(Long buyerId, Long sellerId, int daysDeliveredAgo, boolean returnAllowed, boolean earlyCompleted) {
        BigDecimal subtotal = new BigDecimal("300000.00");
        BigDecimal voucherDiscount = new BigDecimal("50000.00");
        BigDecimal totalAmount = new BigDecimal("250000.00");

        UUID groupId = createCheckoutGroup(buyerId, totalAmount);

        String deliveredSql = (daysDeliveredAgo >= 0) ? "CURRENT_TIMESTAMP - INTERVAL '" + daysDeliveredAgo + " days'" : "NULL";
        String earlyCompletedSql = earlyCompleted ? "CURRENT_TIMESTAMP" : "NULL";
        String earlyWarningSql = earlyCompleted ? "'v1.0'" : "NULL";

        Long orderId = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.orders(
                    checkout_group_id, group_id, buyer_id, seller_id, shipping_recipient_name, shipping_phone_number,
                    shipping_province, shipping_district, shipping_ward, shipping_detail_address,
                    subtotal, shipping_fee, total_amount, voucher_discount_amount, buyer_system_fee, seller_system_fee,
                    seller_proceeds, payment_due_at, workflow_model, return_allowed, delivery_method, handover_day,
                    status, valid_delivered_at, early_completed_at, early_completion_warning_version
                ) VALUES (
                    ?, ?, ?, ?, 'Recipient', '0912345678',
                    'HN', 'BD', 'Ward', 'Detail address',
                    ?, 0, ?, ?, 0, 0,
                    ?, CURRENT_TIMESTAMP + INTERVAL '1 hour', 'UC83', ?, 'SELF_PICKUP', CURRENT_DATE,
                    'PAYMENT_PENDING', """ + deliveredSql + ", " + earlyCompletedSql + ", " + earlyWarningSql + """
                ) RETURNING order_id
                """,
                Long.class,
                groupId, groupId, buyerId, sellerId,
                subtotal, totalAmount, voucherDiscount, subtotal, returnAllowed
        );

        UUID intentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.payment_intents(
                    intent_id, purpose, group_id, expected_amount, payment_method, deadline, command_key
                ) VALUES (?, 'CHECKOUT_GROUP', ?, ?, 'BANK_TRANSFER_MOCK', CURRENT_TIMESTAMP + INTERVAL '1 hour', ?)
                """,
                intentId, groupId, totalAmount, "CMD_INT_" + UUID.randomUUID()
        );

        Long attemptId = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.payment_attempts(
                    intent_id, txn_ref, amount, provider
                ) VALUES (?, ?, ?, 'BANK_TRANSFER_MOCK') RETURNING attempt_id
                """,
                Long.class,
                intentId, "TXN_" + UUID.randomUUID(), totalAmount
        );

        UUID confirmationId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.payment_confirmations(
                    confirmation_id, intent_id, attempt_id, provider, provider_transaction_id, amount
                ) VALUES (?, ?, ?, 'BANK_TRANSFER_MOCK', ?, ?)
                """,
                confirmationId, intentId, attemptId, "TXN_PROV_" + UUID.randomUUID(), totalAmount
        );

        UUID allocationId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.payment_allocations(
                    allocation_id, intent_id, confirmation_id, order_id, amount
                ) VALUES (?, ?, ?, ?, ?)
                """,
                allocationId, intentId, confirmationId, orderId, totalAmount
        );

        jdbcTemplate.update("""
                UPDATE og_compat.orders
                SET status = 'DELIVERED',
                    paid_confirmed_at = (SELECT confirmed_at FROM og_compat.payment_confirmations WHERE confirmation_id = ?)
                WHERE order_id = ?
                """,
                confirmationId, orderId
        );

        UUID cashComponentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.order_fund_components(
                    component_id, order_id, allocation_id, component_type, confirmed_amount
                ) VALUES (?, ?, ?, 'GOODS_CASH', ?)
                """,
                cashComponentId, orderId, allocationId, totalAmount
        );

        UUID subsidyComponentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.order_fund_components(
                    component_id, order_id, component_type, confirmed_amount, funding_reference
                ) VALUES (?, ?, 'PLATFORM_GOODS_SUBSIDY', ?, 'VOUCHER_GRANT_ESCROW')
                """,
                subsidyComponentId, orderId, voucherDiscount
        );

        return new Uc83OrderFixture(orderId, cashComponentId, subsidyComponentId);
    }

    @Test
    @DisplayName("Task 2.2: createReturnRequest on real PostgreSQL triggers money_holds and creates full case graph")
    void testCreateReturnRequest_triggersMoneyHoldsOnPostgres() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId, 1, true, false);

        ReturnCaseDtos.CreateReturnRequest request = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.CHANGE_MIND,
                "Sản phẩm còn nguyên tem mác, tôi đổi ý không mua nữa",
                List.of(
                        new ReturnCaseDtos.EvidenceItemDto("IMAGE", "cases/img1.jpg", "Tem mác nguyên vẹn",
                                "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2", 1024L, null),
                        new ReturnCaseDtos.EvidenceItemDto("VIDEO", "cases/vid1.mp4", "Video bóc hộp",
                                "b1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2", 5_000_000L, 30)
                ),
                "CMD_PG_CREATE_" + UUID.randomUUID()
        );

        ReturnCaseDtos.ReturnCaseDetailResponse response = returnCaseService.createReturnRequest(buyerId, fixture.orderId(), request);

        assertThat(response).isNotNull();
        assertThat(response.caseId()).isNotNull();
        assertThat(response.state()).isEqualTo(CaseEntity.STATE_OPEN);
        assertThat(response.reasonCode()).isEqualTo(ReturnReasonCode.CHANGE_MIND);
        assertThat(response.currentRound()).isNotNull();
        assertThat(response.currentRound().roundNo()).isEqualTo(1);
        assertThat(response.currentRound().stage()).isEqualTo(CaseRoundEntity.STAGE_RETURN_APPROVAL);
        assertThat(response.evidence()).hasSize(2);
        assertThat(response.events()).hasSize(1);

        // 1. VERIFY DB TRIGGER trg_return_case_holds automatically created money_holds
        List<Map<String, Object>> holds = jdbcTemplate.queryForList(
                "SELECT hold_id, component_id, source_key, reason, case_id, closed_at FROM og_compat.money_holds WHERE case_id = ?",
                response.caseId()
        );
        assertThat(holds).hasSize(2);
        assertThat(holds).allMatch(h -> h.get("closed_at") == null);
        assertThat(holds).extracting(h -> (UUID) h.get("component_id"))
                .containsExactlyInAnyOrder(fixture.cashComponentId(), fixture.subsidyComponentId());

        // 2. VERIFY DB FUNCTION og_order_has_blocking_case returns TRUE
        Boolean hasBlockingCase = jdbcTemplate.queryForObject(
                "SELECT og_order_has_blocking_case(?)",
                Boolean.class,
                fixture.orderId()
        );
        assertThat(hasBlockingCase).isTrue();
    }

    @Test
    @DisplayName("Task 2.3: sellerRespondRound1 with APPROVE_RETURN moves case to RETURN_APPROVED and keeps money held")
    void testSellerRespondRound1_approveReturn_onPostgres() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId, 1, true, false);

        ReturnCaseDtos.CreateReturnRequest createReq = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.NOT_AS_DESCRIBED,
                "Sản phẩm sai màu",
                List.of(),
                "CMD_CREATE_" + UUID.randomUUID()
        );
        ReturnCaseDtos.ReturnCaseDetailResponse createdCase = returnCaseService.createReturnRequest(buyerId, fixture.orderId(), createReq);

        // Seller responds: APPROVE_RETURN
        ReturnCaseDtos.SellerRound1ResponseRequest sellerReq = new ReturnCaseDtos.SellerRound1ResponseRequest(
                CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                "Người bán đồng ý cho trả lại hàng",
                "CMD_SELLER_RESP_" + UUID.randomUUID()
        );

        ReturnCaseDtos.ReturnCaseDetailResponse sellerResp = returnCaseService.sellerRespondRound1(sellerId, createdCase.caseId(), sellerReq);

        assertThat(sellerResp.state()).isEqualTo(CaseEntity.STATE_RETURN_APPROVED);
        assertThat(sellerResp.approvedAt()).isNotNull();
        assertThat(sellerResp.currentRound().state()).isEqualTo(CaseRoundEntity.STATE_DECIDED);

        // Verify decision persisted in DB
        List<CaseDecisionEntity> decisions = caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(createdCase.currentRound().roundId());
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).getOutcome()).isEqualTo(CaseDecisionEntity.OUTCOME_APPROVE_RETURN);
        assertThat(decisions.get(0).getIsFinal()).isTrue();

        // Money holds must STILL be open (waiting for return shipment)
        List<Map<String, Object>> holds = jdbcTemplate.queryForList(
                "SELECT closed_at FROM og_compat.money_holds WHERE case_id = ?",
                createdCase.caseId()
        );
        assertThat(holds).allMatch(h -> h.get("closed_at") == null);
        assertThat(jdbcTemplate.queryForObject("SELECT og_order_has_blocking_case(?)", Boolean.class, fixture.orderId())).isTrue();
    }

    @Test
    @DisplayName("Task 2.3: sellerRespondRound1 with SELLER_DECLINED moves case to REVIEWING for KTV")
    void testSellerRespondRound1_sellerDeclined_onPostgres() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId, 1, true, false);

        ReturnCaseDtos.CreateReturnRequest createReq = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.DAMAGED_OR_DEFECTIVE,
                "Bị vỡ màn hình",
                List.of(),
                "CMD_CREATE_" + UUID.randomUUID()
        );
        ReturnCaseDtos.ReturnCaseDetailResponse createdCase = returnCaseService.createReturnRequest(buyerId, fixture.orderId(), createReq);

        // Seller responds: SELLER_DECLINED
        ReturnCaseDtos.SellerRound1ResponseRequest sellerReq = new ReturnCaseDtos.SellerRound1ResponseRequest(
                CaseDecisionEntity.OUTCOME_SELLER_DECLINED,
                "Shop đã đóng gói xốp rất kỹ, nghi vấn do vận chuyển",
                "CMD_SELLER_DECLINE_" + UUID.randomUUID()
        );

        ReturnCaseDtos.ReturnCaseDetailResponse sellerResp = returnCaseService.sellerRespondRound1(sellerId, createdCase.caseId(), sellerReq);

        assertThat(sellerResp.state()).isEqualTo(CaseEntity.STATE_REVIEWING);
        assertThat(sellerResp.currentRound().state()).isEqualTo(CaseRoundEntity.STATE_OPEN); // Still open for KTV

        // Verify decision persisted in DB
        List<CaseDecisionEntity> decisions = caseDecisionRepository.findByRoundIdOrderByDecidedAtAsc(createdCase.currentRound().roundId());
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).getOutcome()).isEqualTo(CaseDecisionEntity.OUTCOME_SELLER_DECLINED);
        assertThat(decisions.get(0).getIsFinal()).isFalse();

        // Blocking case still active
        assertThat(jdbcTemplate.queryForObject("SELECT og_order_has_blocking_case(?)", Boolean.class, fixture.orderId())).isTrue();
    }

    @Test
    @DisplayName("Task 2.3: ktvDecideRound1 REJECT_RETURN closes money_holds and unblocks order without premature disbursement")
    void testKtvDecideRound1_rejectReturn_closesHoldsOnPostgres() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId, 1, true, false);

        ReturnCaseDtos.CreateReturnRequest createReq = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.CHANGE_MIND,
                "Đổi ý",
                List.of(),
                "CMD_CREATE_" + UUID.randomUUID()
        );
        ReturnCaseDtos.ReturnCaseDetailResponse createdCase = returnCaseService.createReturnRequest(buyerId, fixture.orderId(), createReq);

        // Seller declined -> REVIEWING
        returnCaseService.sellerRespondRound1(sellerId, createdCase.caseId(), new ReturnCaseDtos.SellerRound1ResponseRequest(
                CaseDecisionEntity.OUTCOME_SELLER_DECLINED,
                "Không đồng ý",
                "CMD_DEC_" + UUID.randomUUID()
        ));

        // KTV decides: REJECT_RETURN
        ReturnCaseDtos.KtvRound1DecisionRequest ktvReq = new ReturnCaseDtos.KtvRound1DecisionRequest(
                CaseDecisionEntity.OUTCOME_REJECT_RETURN,
                "KTV xác minh sản phẩm người mua đã bóc seal kích hoạt sử dụng, bác yêu cầu đổi ý",
                "CMD_KTV_" + UUID.randomUUID()
        );

        ReturnCaseDtos.ReturnCaseDetailResponse ktvResp = returnCaseService.ktvDecideRound1(ktvId, createdCase.caseId(), ktvReq);

        assertThat(ktvResp.state()).isEqualTo(CaseEntity.STATE_REJECTED);
        assertThat(ktvResp.closedAt()).isNotNull();
        assertThat(ktvResp.currentRound().state()).isEqualTo(CaseRoundEntity.STATE_DECIDED);

        // 1. VERIFY money_holds are closed (closed_at IS NOT NULL)
        List<Map<String, Object>> holds = jdbcTemplate.queryForList(
                "SELECT closed_at FROM og_compat.money_holds WHERE case_id = ?",
                createdCase.caseId()
        );
        assertThat(holds).hasSize(2);
        assertThat(holds).allMatch(h -> h.get("closed_at") != null);

        // 2. VERIFY og_order_has_blocking_case is now FALSE
        Boolean hasBlockingCase = jdbcTemplate.queryForObject(
                "SELECT og_order_has_blocking_case(?)",
                Boolean.class,
                fixture.orderId()
        );
        assertThat(hasBlockingCase).isFalse();

        // 3. VERIFY Order is STILL in DELIVERED status (NOT prematurely disbursed/completed!)
        OrderEntity order = orderRepository.findById(fixture.orderId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo("DELIVERED");
    }

    @Test
    @DisplayName("Task 2.4: withdrawReturnRequest closes case and money_holds, unblocking order")
    void testWithdrawReturnRequest_closesHoldsOnPostgres() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId, 1, true, false);

        ReturnCaseDtos.CreateReturnRequest createReq = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.CHANGE_MIND,
                "Đổi ý",
                List.of(),
                "CMD_CREATE_" + UUID.randomUUID()
        );
        ReturnCaseDtos.ReturnCaseDetailResponse createdCase = returnCaseService.createReturnRequest(buyerId, fixture.orderId(), createReq);

        // Buyer withdraws
        ReturnCaseDtos.WithdrawReturnRequest withdrawReq = new ReturnCaseDtos.WithdrawReturnRequest(
                "Tôi đã suy nghĩ lại và quyết định giữ máy",
                "CMD_WITHDRAW_" + UUID.randomUUID()
        );

        ReturnCaseDtos.ReturnCaseDetailResponse withdrawResp = returnCaseService.withdrawReturnRequest(buyerId, createdCase.caseId(), withdrawReq);

        assertThat(withdrawResp.state()).isEqualTo(CaseEntity.STATE_CLOSED);
        assertThat(withdrawResp.closedAt()).isNotNull();

        // 1. Verify money_holds are closed
        List<Map<String, Object>> holds = jdbcTemplate.queryForList(
                "SELECT closed_at FROM og_compat.money_holds WHERE case_id = ?",
                createdCase.caseId()
        );
        assertThat(holds).allMatch(h -> h.get("closed_at") != null);

        // 2. Verify og_order_has_blocking_case is FALSE
        assertThat(jdbcTemplate.queryForObject("SELECT og_order_has_blocking_case(?)", Boolean.class, fixture.orderId())).isFalse();
    }

    @Test
    @DisplayName("Race condition (First-write wins): Seller cannot respond after KTV already decided")
    void testRaceCondition_firstWriteWins_onPostgres() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId, 1, true, false);

        ReturnCaseDtos.CreateReturnRequest createReq = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.NOT_AS_DESCRIBED,
                "Sai hàng",
                List.of(),
                "CMD_CREATE_" + UUID.randomUUID()
        );
        ReturnCaseDtos.ReturnCaseDetailResponse createdCase = returnCaseService.createReturnRequest(buyerId, fixture.orderId(), createReq);

        // KTV proactively decides APPROVE_RETURN while case is OPEN
        returnCaseService.ktvDecideRound1(ktvId, createdCase.caseId(), new ReturnCaseDtos.KtvRound1DecisionRequest(
                CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                "KTV duyệt nhanh do có ảnh đối chiếu rõ ràng",
                "CMD_KTV_" + UUID.randomUUID()
        ));

        // Seller now tries to submit SELLER_DECLINED -> must be rejected by service
        ReturnCaseDtos.SellerRound1ResponseRequest sellerReq = new ReturnCaseDtos.SellerRound1ResponseRequest(
                CaseDecisionEntity.OUTCOME_SELLER_DECLINED,
                "Shop không đồng ý",
                "CMD_SELLER_LATE_" + UUID.randomUUID()
        );

        assertThatThrownBy(() -> returnCaseService.sellerRespondRound1(sellerId, createdCase.caseId(), sellerReq))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hồ sơ không ở trạng thái chờ người bán phản hồi");
    }
}

