package com.oldbutgold.shop.modules.payment.application;

import com.oldbutgold.shop.shared.PostgresTestSafetyGuard;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
@DisplayName("RoundDecisionSettlementPostgresTest - PostgreSQL integration test for Task 4.1a")
class RoundDecisionSettlementPostgresTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String url = PostgresTestSafetyGuard.resolveAndAssertSafeFromEnv("OGSHOP_TEST_DB_URL");
        String user = System.getenv("OGSHOP_TEST_DB_USER");
        String pass = System.getenv("OGSHOP_TEST_DB_PASSWORD");

        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> (user != null && !user.isBlank()) ? user : "og_test_runner");
        registry.add("spring.datasource.password", () -> pass != null ? pass : "");
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO og_compat,public");
        registry.add("spring.flyway.enabled", PostgresTestSafetyGuard::isFlywayOptInEnabled);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @BeforeAll
    static void printDatabaseInfo() {
        String url = PostgresTestSafetyGuard.resolveAndAssertSafeFromEnv("OGSHOP_TEST_DB_URL");
        String user = System.getenv("OGSHOP_TEST_DB_USER");
        String dbName = URI.create(url.substring(5)).getPath().replaceFirst("^/", "");
        System.out.println("=================================================================");
        System.out.println("[POSTGRES TEST AUDIT] Target JDBC URL: " + url);
        System.out.println("[POSTGRES TEST AUDIT] Target Database: " + dbName);
        System.out.println("[POSTGRES TEST AUDIT] Target User: " + user);
        System.out.println("=================================================================");
    }

    @Autowired
    private RoundDecisionSettlementAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

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

    @Test
    @DisplayName("Case 1: FULL_GOODS_REFUND creates REQUESTED settlement operations and updates reserved_amount")
    void testFullGoodsRefund_CreatesRequestedSettlementOperations() {
        Uc83OrderFixture orderFixture = createDeliveredUc83Order(
                buyerId, sellerId, new BigDecimal("80000.00"), new BigDecimal("20000.00")
        );
        Long orderId = orderFixture.orderId();

        Long caseId = openReturnCase(orderId, buyerId);
        approveReturnRound1(caseId, ktvId);
        createAndConfirmReturnShipment(orderId, caseId, buyerId, sellerId);

        Long decisionId = resolveRound2FullRefund(caseId, ktvId);

        String commandKey = "CMD_SETTLE_R2_FULL_" + UUID.randomUUID();

        // When: Request settlement
        adapter.requestSettlement(orderId, caseId, decisionId, "FULL_GOODS_REFUND", commandKey);

        // Then: Settlement operations created in REQUESTED state
        List<Map<String, Object>> ops = jdbcTemplate.queryForList("""
                SELECT operation_id, component_id, operation_type, amount, cause_type,
                       case_decision_id, recipient_user_id, state, command_key
                FROM og_compat.settlement_operations
                WHERE case_decision_id = ?
                ORDER BY operation_type
                """, decisionId);

        assertThat(ops).hasSize(2);

        // Op 1: REFUND_BUYER for GOODS_CASH
        Map<String, Object> refundOp = ops.stream()
                .filter(o -> "REFUND_BUYER".equals(o.get("operation_type")))
                .findFirst()
                .orElseThrow();
        assertThat(refundOp.get("component_id")).isEqualTo(orderFixture.cashComponentId());
        assertThat(new BigDecimal(refundOp.get("amount").toString())).isEqualByComparingTo("80000.00");
        assertThat(refundOp.get("cause_type")).isEqualTo("RETURN");
        assertThat(refundOp.get("recipient_user_id")).isEqualTo(buyerId);
        assertThat(refundOp.get("state")).isEqualTo("REQUESTED");
        assertThat(refundOp.get("command_key")).isEqualTo(commandKey + ":GOODS_CASH");

        // Op 2: RETURN_PLATFORM for PLATFORM_GOODS_SUBSIDY
        Map<String, Object> platformOp = ops.stream()
                .filter(o -> "RETURN_PLATFORM".equals(o.get("operation_type")))
                .findFirst()
                .orElseThrow();
        assertThat(platformOp.get("component_id")).isEqualTo(orderFixture.subsidyComponentId());
        assertThat(new BigDecimal(platformOp.get("amount").toString())).isEqualByComparingTo("20000.00");
        assertThat(platformOp.get("cause_type")).isEqualTo("RETURN");
        assertThat(platformOp.get("recipient_user_id")).isNull();
        assertThat(platformOp.get("state")).isEqualTo("REQUESTED");
        assertThat(platformOp.get("command_key")).isEqualTo(commandKey + ":PLATFORM_GOODS_SUBSIDY");

        // Then: Trigger og_guard_settlement_operation incremented reserved_amount, refunded/released remain 0
        BigDecimal cashReserved = jdbcTemplate.queryForObject(
                "SELECT reserved_amount FROM og_compat.order_fund_components WHERE component_id = ?",
                BigDecimal.class, orderFixture.cashComponentId()
        );
        BigDecimal cashRefunded = jdbcTemplate.queryForObject(
                "SELECT refunded_amount FROM og_compat.order_fund_components WHERE component_id = ?",
                BigDecimal.class, orderFixture.cashComponentId()
        );
        BigDecimal cashReleased = jdbcTemplate.queryForObject(
                "SELECT released_amount FROM og_compat.order_fund_components WHERE component_id = ?",
                BigDecimal.class, orderFixture.cashComponentId()
        );
        assertThat(cashReserved).isEqualByComparingTo("80000.00");
        assertThat(cashRefunded).isEqualByComparingTo("0.00");
        assertThat(cashReleased).isEqualByComparingTo("0.00");

        // Then: Order status remains DELIVERED (no real money movement or order transition in 4.1a)
        String orderStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM og_compat.orders WHERE order_id = ?",
                String.class, orderId
        );
        assertThat(orderStatus).isEqualTo("DELIVERED");
    }

    @Test
    @DisplayName("Case 2: EXEMPT_GOODS_REFUND creates RELEASE_SELLER operations with cause EXEMPTION")
    void testExemptGoodsRefund_CreatesRequestedSettlementOperations() {
        Uc83OrderFixture orderFixture = createDeliveredUc83Order(
                buyerId, sellerId, new BigDecimal("70000.00"), new BigDecimal("30000.00")
        );
        Long orderId = orderFixture.orderId();

        Long caseId = openReturnCase(orderId, buyerId);
        approveReturnRound1(caseId, ktvId);
        createAndConfirmReturnShipment(orderId, caseId, buyerId, sellerId);

        Long decisionId = resolveRound2ExemptRefund(caseId, ktvId);

        String commandKey = "CMD_SETTLE_R2_EXEMPT_" + UUID.randomUUID();

        // When: Request settlement
        adapter.requestSettlement(orderId, caseId, decisionId, "EXEMPT_GOODS_REFUND", commandKey);

        // Then: Both settlement operations are RELEASE_SELLER with cause EXEMPTION
        List<Map<String, Object>> ops = jdbcTemplate.queryForList("""
                SELECT operation_id, component_id, operation_type, amount, cause_type,
                       case_decision_id, recipient_user_id, state, command_key
                FROM og_compat.settlement_operations
                WHERE case_decision_id = ?
                ORDER BY component_id
                """, decisionId);

        assertThat(ops).hasSize(2);
        for (Map<String, Object> op : ops) {
            assertThat(op.get("operation_type")).isEqualTo("RELEASE_SELLER");
            assertThat(op.get("cause_type")).isEqualTo("EXEMPTION");
            assertThat(op.get("recipient_user_id")).isEqualTo(sellerId);
            assertThat(op.get("state")).isEqualTo("REQUESTED");
        }

        // Check reserved amount updated on both components
        BigDecimal cashReserved = jdbcTemplate.queryForObject(
                "SELECT reserved_amount FROM og_compat.order_fund_components WHERE component_id = ?",
                BigDecimal.class, orderFixture.cashComponentId()
        );
        BigDecimal subsidyReserved = jdbcTemplate.queryForObject(
                "SELECT reserved_amount FROM og_compat.order_fund_components WHERE component_id = ?",
                BigDecimal.class, orderFixture.subsidyComponentId()
        );
        assertThat(cashReserved).isEqualByComparingTo("70000.00");
        assertThat(subsidyReserved).isEqualByComparingTo("30000.00");
    }

    @Test
    @DisplayName("Case 3: Idempotent replay with same command key does not duplicate operations")
    void testIdempotentReplay_DoesNotDuplicate() {
        Uc83OrderFixture orderFixture = createDeliveredUc83Order(
                buyerId, sellerId, new BigDecimal("60000.00"), new BigDecimal("40000.00")
        );
        Long orderId = orderFixture.orderId();

        Long caseId = openReturnCase(orderId, buyerId);
        approveReturnRound1(caseId, ktvId);
        createAndConfirmReturnShipment(orderId, caseId, buyerId, sellerId);
        Long decisionId = resolveRound2FullRefund(caseId, ktvId);

        String commandKey = "CMD_SETTLE_R2_IDEMP_" + UUID.randomUUID();

        // First call
        adapter.requestSettlement(orderId, caseId, decisionId, "FULL_GOODS_REFUND", commandKey);
        int countFirst = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM og_compat.settlement_operations WHERE case_decision_id = ?",
                Integer.class, decisionId
        );
        assertThat(countFirst).isEqualTo(2);

        // Second call (replay with exact same commandKey)
        adapter.requestSettlement(orderId, caseId, decisionId, "FULL_GOODS_REFUND", commandKey);
        int countSecond = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM og_compat.settlement_operations WHERE case_decision_id = ?",
                Integer.class, decisionId
        );
        assertThat(countSecond).isEqualTo(2);

        // Verify reserved amount not double-counted
        BigDecimal cashReserved = jdbcTemplate.queryForObject(
                "SELECT reserved_amount FROM og_compat.order_fund_components WHERE component_id = ?",
                BigDecimal.class, orderFixture.cashComponentId()
        );
        assertThat(cashReserved).isEqualByComparingTo("60000.00");
    }

    @Test
    @DisplayName("Case 4: Transaction rollback guarantees atomic rollback of settlement operations")
    void testTransactionRollback_GuaranteesAtomicity() {
        Uc83OrderFixture orderFixture = createDeliveredUc83Order(
                buyerId, sellerId, new BigDecimal("50000.00"), new BigDecimal("50000.00")
        );
        Long orderId = orderFixture.orderId();

        Long caseId = openReturnCase(orderId, buyerId);
        approveReturnRound1(caseId, ktvId);
        createAndConfirmReturnShipment(orderId, caseId, buyerId, sellerId);
        Long decisionId = resolveRound2FullRefund(caseId, ktvId);

        String commandKey = "CMD_SETTLE_R2_ROLLBACK_" + UUID.randomUUID();

        // When: settlement requested in transaction
        adapter.requestSettlement(orderId, caseId, decisionId, "FULL_GOODS_REFUND", commandKey);

        // Then: settlement operations exist prior to rollback
        int countBeforeRollback = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM og_compat.settlement_operations WHERE case_decision_id = ?",
                Integer.class, decisionId
        );
        assertThat(countBeforeRollback).isEqualTo(2);

        // When: transaction rolls back
        TestTransaction.flagForRollback();
        TestTransaction.end();

        // Then: after rollback in fresh transaction, operations AND decision are rolled back
        TestTransaction.start();
        int countAfterRollback = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM og_compat.settlement_operations WHERE case_decision_id = ?",
                Integer.class, decisionId
        );
        assertThat(countAfterRollback).isEqualTo(0);

        int decisionCountAfterRollback = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM og_compat.case_decisions WHERE decision_id = ?",
                Integer.class, decisionId
        );
        assertThat(decisionCountAfterRollback).isEqualTo(0);
    }

    @Test
    @DisplayName("Case 5: Unsupported outcome throws IllegalArgumentException and creates 0 records")
    void testUnsupportedOutcome_ThrowsException() {
        Uc83OrderFixture orderFixture = createDeliveredUc83Order(
                buyerId, sellerId, new BigDecimal("50000.00"), new BigDecimal("50000.00")
        );
        Long orderId = orderFixture.orderId();

        Long caseId = openReturnCase(orderId, buyerId);
        approveReturnRound1(caseId, ktvId);
        createAndConfirmReturnShipment(orderId, caseId, buyerId, sellerId);
        Long decisionId = resolveRound2FullRefund(caseId, ktvId);

        assertThatThrownBy(() -> adapter.requestSettlement(
                orderId, caseId, decisionId, "APPROVE_RETURN", "CMD_INVALID_" + UUID.randomUUID()
        )).isInstanceOf(IllegalArgumentException.class);

        int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM og_compat.settlement_operations WHERE case_decision_id = ?",
                Integer.class, decisionId
        );
        assertThat(count).isEqualTo(0);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Case 6: Concurrent idempotency - two threads running same commandKey create exactly 1 set of operations")
    void testConcurrentIdempotency_NoDuplicates() throws Exception {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // Chuẩn bị Order, Case, Shipment, Decision trong transaction riêng biệt được commit
        record SetupData(Long orderId, Long caseId, Long decisionId, UUID cashComponentId) {}
        SetupData setup = txTemplate.execute(status -> {
            Uc83OrderFixture fixture = createCommittedUc83Order(buyerId, sellerId);
            Long orderId = fixture.orderId();
            Long caseId = openReturnCase(orderId, buyerId);
            approveReturnRound1(caseId, ktvId);
            createAndConfirmReturnShipment(orderId, caseId, buyerId, sellerId);
            Long decisionId = resolveRound2FullRefund(caseId, ktvId);
            return new SetupData(orderId, caseId, decisionId, fixture.cashComponentId());
        });
        assertThat(setup).isNotNull();

        String commandKey = "CMD_SETTLE_CONCURRENT_" + UUID.randomUUID();
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger errorCount = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    adapter.requestSettlement(setup.orderId(), setup.caseId(), setup.decisionId(), "FULL_GOODS_REFUND", commandKey);
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        // Check: Exactly 1 operation exists for GOODS_CASH, no duplicate created
        int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM og_compat.settlement_operations WHERE case_decision_id = ?",
                Integer.class, setup.decisionId()
        );
        assertThat(count).isEqualTo(1);

        // Check: reserved amount is not double-counted (250000.00)
        BigDecimal cashReserved = jdbcTemplate.queryForObject(
                "SELECT reserved_amount FROM og_compat.order_fund_components WHERE component_id = ?",
                BigDecimal.class, setup.cashComponentId()
        );
        assertThat(cashReserved).isEqualByComparingTo("250000.00");
    }

    @Test
    @DisplayName("Case 7: Amount validation - negative amount is rejected by database check constraint")
    void testNegativeAmount_ThrowsIllegalStateException() {
        Uc83OrderFixture orderFixture = createDeliveredUc83Order(
                buyerId, sellerId, new BigDecimal("50000.00"), BigDecimal.ZERO
        );

        // Verify that database enforces CHECK(confirmed_amount >= 0) and rejects negative amounts
        UUID negCompId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO og_compat.order_fund_components(
                    component_id, order_id, component_type, confirmed_amount, funding_reference
                ) VALUES (?, ?, 'PLATFORM_GOODS_SUBSIDY', -10000.00, 'TEST')
                """, negCompId, orderFixture.orderId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Case 8: Amount validation - zero amount component skipped per UC83-07")
    void testZeroAmount_Skipped() {
        Uc83OrderFixture orderFixture = createDeliveredUc83Order(
                buyerId, sellerId, new BigDecimal("50000.00"), BigDecimal.ZERO
        );
        Long orderId = orderFixture.orderId();
        Long caseId = openReturnCase(orderId, buyerId);
        approveReturnRound1(caseId, ktvId);
        createAndConfirmReturnShipment(orderId, caseId, buyerId, sellerId);
        Long decisionId = resolveRound2FullRefund(caseId, ktvId);

        adapter.requestSettlement(orderId, caseId, decisionId, "FULL_GOODS_REFUND", "CMD_ZERO_" + UUID.randomUUID());

        // Exactly 1 operation created (for cash component only; 0.00 subsidy component was skipped)
        List<Map<String, Object>> ops = jdbcTemplate.queryForList(
                "SELECT operation_type, amount, component_id FROM og_compat.settlement_operations WHERE case_decision_id = ?",
                decisionId
        );
        assertThat(ops).hasSize(1);
        assertThat(ops.getFirst().get("operation_type")).isEqualTo("REFUND_BUYER");
        assertThat(ops.getFirst().get("component_id")).isEqualTo(orderFixture.cashComponentId());
    }

    @Test
    @DisplayName("Case 9: Decision linkage - rejects orphaned or mismatched decisions")
    void testDecisionLinkage_RejectsMismatchedDecision() {
        Uc83OrderFixture orderFixture1 = createDeliveredUc83Order(
                buyerId, sellerId, new BigDecimal("50000.00"), new BigDecimal("50000.00")
        );
        Long orderId1 = orderFixture1.orderId();
        Long caseId1 = openReturnCase(orderId1, buyerId);
        approveReturnRound1(caseId1, ktvId);
        createAndConfirmReturnShipment(orderId1, caseId1, buyerId, sellerId);
        Long decisionId1 = resolveRound2FullRefund(caseId1, ktvId);

        Uc83OrderFixture orderFixture2 = createDeliveredUc83Order(
                buyerId, sellerId, new BigDecimal("40000.00"), new BigDecimal("40000.00")
        );
        Long orderId2 = orderFixture2.orderId();
        Long caseId2 = openReturnCase(orderId2, buyerId);

        // 1. Non-existent decision
        assertThatThrownBy(() -> adapter.requestSettlement(
                orderId1, caseId1, 9999999L, "FULL_GOODS_REFUND", "CMD_INVALID_" + UUID.randomUUID()
        )).isInstanceOf(java.util.NoSuchElementException.class);

        // 2. Decision belongs to caseId1, but passed with caseId2
        assertThatThrownBy(() -> adapter.requestSettlement(
                orderId1, caseId2, decisionId1, "FULL_GOODS_REFUND", "CMD_MISCASE_" + UUID.randomUUID()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("belongs to case #" + caseId1);

        // 3. Decision belongs to orderId1, but passed with orderId2
        assertThatThrownBy(() -> adapter.requestSettlement(
                orderId2, caseId1, decisionId1, "FULL_GOODS_REFUND", "CMD_MISORDER_" + UUID.randomUUID()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("belongs to order #" + orderId1);

        // 4. Decision outcome is FULL_GOODS_REFUND, but requested as EXEMPT_GOODS_REFUND
        assertThatThrownBy(() -> adapter.requestSettlement(
                orderId1, caseId1, decisionId1, "EXEMPT_GOODS_REFUND", "CMD_MISOUTCOME_" + UUID.randomUUID()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outcome 'FULL_GOODS_REFUND' does not match requested outcome 'EXEMPT_GOODS_REFUND'");
    }

    // Helper fixtures
    private record Uc83OrderFixture(Long orderId, UUID cashComponentId, UUID subsidyComponentId) {}

    private Uc83OrderFixture createCommittedUc83Order(Long buyerId, Long sellerId) {
        grantRole(sellerId, "BUYER");
        jdbcTemplate.update("UPDATE og_compat.users SET identity_workflow = 'UC83' WHERE user_id IN (?, ?)", buyerId, sellerId);

        Long ktvUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM og_compat.user_roles ur JOIN og_compat.roles r USING(role_id) WHERE r.role_name = 'KTV' LIMIT 1",
                Long.class
        );
        Long categoryId = jdbcTemplate.queryForObject("SELECT min(category_id) FROM og_compat.categories", Long.class);

        // Seller eKYC
        Long ekycId = jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.ekyc_profiles(user_id, revision_no) VALUES (?, 1) RETURNING profile_id",
                Long.class, sellerId
        );
        String hDigest = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update(
                "INSERT INTO og_compat.identity_document_registry(document_digest, digest_key_version, user_id, first_profile_id) VALUES (?, 'TEST-HMAC', ?, ?)",
                hDigest, sellerId, ekycId
        );
        for (String type : new String[]{"CARD_FRONT", "CARD_BACK", "LIVE_FRAME", "REFERENCE"}) {
            jdbcTemplate.update(
                    "INSERT INTO og_compat.ekyc_private_assets(profile_id, asset_type, object_key, content_digest, expires_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '2 months')",
                    ekycId, type, "p/" + type, hDigest
            );
        }
        jdbcTemplate.update("UPDATE og_compat.ekyc_profiles SET document_digest = ?, state = 'PENDING', submitted_at = CURRENT_TIMESTAMP WHERE profile_id = ?", hDigest, ekycId);
        jdbcTemplate.update("INSERT INTO og_compat.ekyc_decisions(profile_id, reviewed_by, outcome, command_key) VALUES (?, ?, 'VERIFIED', ?)", ekycId, ktvUserId, "CMD_EKYC_" + UUID.randomUUID());

        // Seller Profile & Address
        Long addrId = jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.addresses(user_id, recipient_name, phone_number, province, district, ward, detail_address) VALUES (?, 'Seller', '0900000000', 'P', 'D', 'W', 'A') RETURNING address_id",
                Long.class, sellerId
        );
        Long spId = jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.seller_profiles(user_id, state, ekyc_profile_id, pickup_address_id, bank_snapshot) VALUES (?, 'PENDING_REVIEW', ?, ?, '{\"bank_name\":\"Mock\",\"account_number\":\"FAKE\",\"account_holder\":\"Seller\"}'::jsonb) RETURNING seller_profile_id",
                Long.class, sellerId, ekycId, addrId
        );
        jdbcTemplate.update("INSERT INTO og_compat.seller_profile_decisions(seller_profile_id, revision_no, reviewed_by, outcome, profile_snapshot, command_key) VALUES (?, 1, ?, 'APPROVED', '{}'::jsonb, ?)", spId, ktvUserId, "CMD_SPD_" + UUID.randomUUID());

        // Product & Listing
        BigDecimal price = new BigDecimal("250000.00");
        Long pid = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.products(seller_id, category_id, title, description, listed_price, condition, workflow_model, quantity_total, return_allowed, delivery_options)
                VALUES (?, ?, 'Item', 'Desc', ?, 'GOOD', 'UC83', 10, true, '["SELF_PICKUP","CARRIER"]'::jsonb) RETURNING product_id
                """, Long.class, sellerId, categoryId, price);
        jdbcTemplate.update("INSERT INTO og_compat.product_categories(product_id, category_id) VALUES (?, ?)", pid, categoryId);

        Long rid = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.product_revisions(product_id, revision_no, provenance, listed_unit_price, quantity, return_allowed, delivery_options, content_snapshot)
                VALUES (?, 1, 'UC83', ?, 10, true, '["SELF_PICKUP","CARRIER"]'::jsonb, '{}'::jsonb) RETURNING revision_id
                """, Long.class, pid, price);

        jdbcTemplate.update("INSERT INTO og_compat.revision_media(revision_id, media_type, object_key, content_digest, file_size_bytes, duration_seconds, display_order) VALUES (?, 'IMAGE', 't/img', repeat('a', 64), 100, 10, 0)", rid);
        jdbcTemplate.update("INSERT INTO og_compat.revision_media(revision_id, media_type, object_key, content_digest, file_size_bytes, duration_seconds, display_order) VALUES (?, 'VIDEO', 't/vid', repeat('a', 64), 100, 10, 1)", rid);

        jdbcTemplate.update("UPDATE og_compat.product_revisions SET state = 'SUBMITTED', submitted_at = CURRENT_TIMESTAMP WHERE revision_id = ?", rid);
        jdbcTemplate.update("UPDATE og_compat.products SET status = 'PENDING', current_revision_id = ? WHERE product_id = ?", rid, pid);

        Long decId = jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.product_moderation_decisions(product_id, revision_id, reviewer_id, decision, product_version, command_key) VALUES (?, ?, ?, 'APPROVED', 0, ?) RETURNING decision_id",
                Long.class, pid, rid, ktvUserId, "CMD_MD_" + UUID.randomUUID()
        );

        Long assessId = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.listing_fee_assessments(product_id, revision_id, decision_id, policy_id, listed_total, computed_fee, cumulative_before, amount_due, command_key)
                SELECT ?, ?, ?, policy_id, 1, 0, 0, 0, ? FROM og_compat.listing_fee_policies WHERE effective_until IS NULL RETURNING assessment_id
                """, Long.class, pid, rid, decId, "CMD_LFA_" + UUID.randomUUID());

        BigDecimal due = jdbcTemplate.queryForObject("SELECT amount_due FROM og_compat.listing_fee_assessments WHERE assessment_id = ?", BigDecimal.class, assessId);
        if (due != null && due.compareTo(BigDecimal.ZERO) > 0) {
            UUID chargeId = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO og_compat.listing_fee_charges(charge_id, assessment_id, amount, command_key) VALUES (?, ?, ?, ?)", chargeId, assessId, due, "CMD_CHG_" + UUID.randomUUID());
            jdbcTemplate.update("INSERT INTO og_compat.listing_fee_receipts(receipt_id, charge_id, provider, provider_transaction_id, amount) VALUES (?, ?, 'BANK_TRANSFER_MOCK', ?, ?)", UUID.randomUUID(), chargeId, "TXN_LFR_" + UUID.randomUUID(), due);
        }

        jdbcTemplate.update("UPDATE og_compat.products SET status = 'ACTIVE', public_revision_id = ?, listed_price = ?, quantity_total = 10, return_allowed = true, delivery_options = '[\"SELF_PICKUP\",\"CARRIER\"]'::jsonb WHERE product_id = ?", rid, price, pid);

        // Checkout Group, Order, Items, Reservations
        UUID gid = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.checkout_groups(group_id, buyer_id, command_key, request_digest, address_snapshot, delivery_method, payment_method, expected_total, payment_due_at)
                VALUES (?, ?, ?, repeat('a', 64), '{"recipient_name":"Buyer","phone_number":"0900000000","province":"P","district":"D","ward":"W","detail_address":"A"}'::jsonb, 'SELF_PICKUP', 'BANK_TRANSFER_MOCK', ?, CURRENT_TIMESTAMP + INTERVAL '1 hour')
                """, gid, buyerId, "CMD_CG_" + UUID.randomUUID(), price);

        Long oid = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.orders(
                    checkout_group_id, group_id, buyer_id, seller_id, shipping_recipient_name, shipping_phone_number,
                    shipping_province, shipping_district, shipping_ward, shipping_detail_address,
                    subtotal, total_amount, buyer_system_fee, seller_system_fee, seller_proceeds,
                    payment_due_at, workflow_model, return_allowed, delivery_method, handover_day, voucher_discount_amount
                ) VALUES (
                    ?, ?, ?, ?, 'Buyer', '0900000000', 'P', 'D', 'W', 'A',
                    ?, ?, 0, 0, ?,
                    CURRENT_TIMESTAMP + INTERVAL '1 hour', 'UC83', true, 'SELF_PICKUP', CURRENT_DATE, 0
                ) RETURNING order_id
                """, Long.class, gid, gid, buyerId, sellerId, price, price, price);

        Long iid = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.order_items(
                    order_id, product_id, product_title, quantity, listed_price, agreed_price,
                    buyer_system_fee, seller_system_fee, buyer_line_total, seller_line_proceeds,
                    workflow_model, product_revision_id, return_allowed, voucher_allocation
                ) VALUES (
                    ?, ?, 'Item', 1, ?, ?,
                    0, 0, ?, ?,
                    'UC83', ?, true, 0
                ) RETURNING order_item_id
                """, Long.class, oid, pid, price, price, price, price, rid);

        UUID resId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.inventory_reservations(reservation_id, order_item_id, product_id, quantity, expires_at, command_key)
                VALUES (?, ?, ?, 1, CURRENT_TIMESTAMP + INTERVAL '1 hour', ?)
                """, resId, iid, pid, "CMD_RES_" + UUID.randomUUID());

        // Payment
        UUID intentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.payment_intents(intent_id, purpose, group_id, expected_amount, payment_method, deadline, command_key)
                VALUES (?, 'CHECKOUT_GROUP', ?, ?, 'BANK_TRANSFER_MOCK', CURRENT_TIMESTAMP + INTERVAL '1 hour', ?)
                """, intentId, gid, price, "CMD_PI_" + UUID.randomUUID());

        Long attId = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.payment_attempts(intent_id, txn_ref, amount, provider)
                VALUES (?, ?, ?, 'BANK_TRANSFER_MOCK') RETURNING attempt_id
                """, Long.class, intentId, "TXN_" + UUID.randomUUID(), price);

        UUID confId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.payment_confirmations(confirmation_id, intent_id, attempt_id, provider, provider_transaction_id, amount, disposition)
                VALUES (?, ?, ?, 'BANK_TRANSFER_MOCK', ?, ?, 'ALLOCATABLE')
                """, confId, intentId, attId, "TXN_P_" + UUID.randomUUID(), price);

        UUID allocId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.payment_allocations(allocation_id, intent_id, confirmation_id, order_id, amount)
                VALUES (?, ?, ?, ?, ?)
                """, allocId, intentId, confId, oid, price);

        jdbcTemplate.update("""
                UPDATE og_compat.orders SET status = 'PAID_HELD',
                    paid_confirmed_at = (SELECT confirmed_at FROM og_compat.payment_confirmations WHERE confirmation_id = ?),
                    seller_accept_due_at = (SELECT confirmed_at + INTERVAL '2 days' FROM og_compat.payment_confirmations WHERE confirmation_id = ?)
                WHERE order_id = ?
                """, confId, confId, oid);
        jdbcTemplate.update("UPDATE og_compat.inventory_reservations SET state = 'FUNDED' WHERE reservation_id = ?", resId);
        jdbcTemplate.update("UPDATE og_compat.checkout_groups SET state = 'PAID' WHERE group_id = ?", gid);

        // Funds
        UUID cashComponentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.order_fund_components(component_id, order_id, allocation_id, component_type, confirmed_amount)
                VALUES (?, ?, ?, 'GOODS_CASH', ?)
                """, cashComponentId, oid, allocId, price);

        // Delivery
        jdbcTemplate.update("UPDATE og_compat.orders SET status = 'SELLER_CONFIRMED' WHERE order_id = ?", oid);
        jdbcTemplate.update("UPDATE og_compat.inventory_reservations SET state = 'CONSUMED' WHERE reservation_id = ?", resId);

        Long shipId = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.shipments(order_id, carrier, status, workflow_model, leg, delivery_method, fee_snapshot, fee_payer)
                VALUES (?, 'SELF_PICKUP', 'PENDING', 'UC83', 'OUTBOUND', 'SELF_PICKUP', 0, 'BUYER') RETURNING shipment_id
                """, Long.class, oid);

        jdbcTemplate.update("""
                INSERT INTO og_compat.handover_confirmations(confirmation_id, shipment_id, party, user_id, outcome, confirmed_at, command_key)
                VALUES (?, ?, 'BUYER', ?, 'SUCCESS', CURRENT_TIMESTAMP, ?),
                       (?, ?, 'SELLER', ?, 'SUCCESS', CURRENT_TIMESTAMP, ?)
                """, UUID.randomUUID(), shipId, buyerId, "CMD_HCB_" + UUID.randomUUID(),
                     UUID.randomUUID(), shipId, sellerId, "CMD_HCS_" + UUID.randomUUID());

        jdbcTemplate.queryForObject("SELECT og_confirm_direct_handover(?)", Boolean.class, shipId);

        java.sql.Timestamp now = new java.sql.Timestamp(System.currentTimeMillis());
        jdbcTemplate.update("UPDATE public.shipments SET valid_delivery_at = ?, status = 'DELIVERED', shipped_at = ?, delivered_at = ? WHERE shipment_id = ?", now, now, now, shipId);
        jdbcTemplate.update("UPDATE og_compat.orders SET status = 'DELIVERED', valid_delivered_at = ?, return_due_at = (?::timestamptz + INTERVAL '3 days') WHERE order_id = ?", now, now, oid);

        return new Uc83OrderFixture(oid, cashComponentId, null);
    }

    private Uc83OrderFixture createDeliveredUc83Order(
            Long buyerId, Long sellerId, BigDecimal cashAmount, BigDecimal subsidyAmount
    ) {
        BigDecimal subtotal = cashAmount.add(subsidyAmount);
        BigDecimal voucherDiscount = subsidyAmount;
        BigDecimal totalAmount = cashAmount;
        UUID groupId = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO checkout_groups(
                    group_id, buyer_id, command_key, request_digest, address_snapshot,
                    delivery_method, payment_method, expected_total, payment_due_at
                ) VALUES (
                    ?, ?, ?, repeat('a', 64),
                    '{"recipient_name":"Recipient","phone_number":"0912345678","province":"HN","district":"BD","ward":"Ward","detail_address":"Detail address"}'::jsonb,
                    'SELF_PICKUP', 'BANK_TRANSFER_MOCK', ?, CURRENT_TIMESTAMP + INTERVAL '1 hour'
                )
                """,
                groupId, buyerId, "CMD_GRP_" + UUID.randomUUID(), totalAmount
        );

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

        UUID cashCompId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.order_fund_components(
                    component_id, order_id, allocation_id, component_type, confirmed_amount
                ) VALUES (?, ?, ?, 'GOODS_CASH', ?)
                """,
                cashCompId, orderId, allocationId, cashAmount
        );

        UUID subsidyCompId = null;
        if (subsidyAmount != null && subsidyAmount.compareTo(BigDecimal.ZERO) > 0) {
            subsidyCompId = UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO og_compat.order_fund_components(
                        component_id, order_id, component_type, confirmed_amount, funding_reference
                    ) VALUES (?, ?, 'PLATFORM_GOODS_SUBSIDY', ?, 'VOUCHER_GRANT_ESCROW')
                    """,
                    subsidyCompId, orderId, subsidyAmount
            );
        }

        return new Uc83OrderFixture(orderId, cashCompId, subsidyCompId);
    }

    private Long openReturnCase(Long orderId, Long buyerId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.cases(case_type, order_id, created_by, description, state, command_key)
                VALUES ('RETURN', ?, ?, 'Return request fixture', 'OPEN', ?) RETURNING case_id
                """,
                Long.class,
                orderId, buyerId, "CMD_CASE_" + UUID.randomUUID()
        );
    }

    private void approveReturnRound1(Long caseId, Long reviewerKtvId) {
        Long r1 = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.case_rounds(case_id, round_no, stage)
                VALUES (?, 1, 'RETURN_APPROVAL') RETURNING round_id
                """, Long.class, caseId);
        jdbcTemplate.update("""
                INSERT INTO og_compat.case_decisions(round_id, reviewed_by, actor_type, outcome, is_final, reason, command_key)
                VALUES (?, ?, 'USER', 'APPROVE_RETURN', true, 'KTV approves return', ?)
                """, r1, reviewerKtvId, "CMD_DEC_" + UUID.randomUUID());
    }

    private void createAndConfirmReturnShipment(Long orderId, Long caseId, Long buyerId, Long sellerId) {
        Long shipmentId = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.shipments(order_id, carrier, status, workflow_model, leg, return_case_id, delivery_method, fee_snapshot, fee_payer)
                VALUES (?, 'SELF_PICKUP', 'PENDING', 'UC83', 'RETURN', ?, 'SELF_PICKUP', 0, 'BUYER') RETURNING shipment_id
                """, Long.class, orderId, caseId);

        for (String party : List.of("BUYER", "SELLER")) {
            jdbcTemplate.update("""
                    INSERT INTO og_compat.handover_confirmations(
                        confirmation_id, shipment_id, party, user_id, outcome, confirmed_at, command_key
                    ) VALUES (?, ?, ?, ?, 'SUCCESS', CURRENT_TIMESTAMP + INTERVAL '1 minute', ?)
                    """, UUID.randomUUID(), shipmentId, party, "BUYER".equals(party) ? buyerId : sellerId, "CMD_HO_" + UUID.randomUUID());
        }

        jdbcTemplate.queryForObject("SELECT og_confirm_direct_handover(?)", Boolean.class, shipmentId);

        java.sql.Timestamp deliveredAt = jdbcTemplate.queryForObject(
                "SELECT valid_returned_at FROM og_compat.cases WHERE case_id = ?",
                java.sql.Timestamp.class, caseId
        );
        if (deliveredAt != null) {
            jdbcTemplate.update(
                    "UPDATE public.shipments SET valid_delivery_at = ?, status = 'DELIVERED', shipped_at = ?, delivered_at = ? WHERE shipment_id = ?",
                    deliveredAt, deliveredAt, deliveredAt, shipmentId
            );
        }
    }

    private Long resolveRound2FullRefund(Long caseId, Long reviewerKtvId) {
        Long r2 = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.case_rounds(case_id, round_no, stage)
                VALUES (?, 2, 'POST_RETURN_RESOLUTION') RETURNING round_id
                """, Long.class, caseId);
        return jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.case_decisions(round_id, reviewed_by, actor_type, outcome, is_final, reason, command_key)
                VALUES (?, ?, 'USER', 'FULL_GOODS_REFUND', true, 'KTV full refund to buyer', ?) RETURNING decision_id
                """, Long.class, r2, reviewerKtvId, "CMD_DEC_" + UUID.randomUUID());
    }

    private Long resolveRound2ExemptRefund(Long caseId, Long reviewerKtvId) {
        Long r2 = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.case_rounds(case_id, round_no, stage)
                VALUES (?, 2, 'POST_RETURN_RESOLUTION') RETURNING round_id
                """, Long.class, caseId);
        jdbcTemplate.update("UPDATE og_compat.case_rounds SET valid_damage_request_at = CURRENT_TIMESTAMP WHERE round_id = ?", r2);
        return jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.case_decisions(round_id, reviewed_by, actor_type, outcome, is_final, reason, command_key)
                VALUES (?, ?, 'USER', 'EXEMPT_GOODS_REFUND', true, 'KTV exempt goods refund', ?) RETURNING decision_id
                """, Long.class, r2, reviewerKtvId, "CMD_DEC_" + UUID.randomUUID());
    }

    private Long createTestUser(String prefix) {
        String email = prefix + "_" + UUID.randomUUID() + "@test.local";
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.users(email, password_hash, full_name, status) VALUES (?, '{noop}fixture', ?, 'ACTIVE') RETURNING user_id",
                Long.class, email, "User " + prefix
        );
        jdbcTemplate.update("INSERT INTO og_compat.reward_accounts(user_id, balance) VALUES (?, 0)", id);
        return id;
    }

    private void grantRole(Long userId, String roleName) {
        jdbcTemplate.update(
                "INSERT INTO og_compat.user_roles(user_id, role_id) VALUES (?, (SELECT role_id FROM og_compat.roles WHERE role_name = ?))",
                userId, roleName
        );
    }
}
