package com.oldbutgold.shop.modules.payment.application;

import com.oldbutgold.shop.shared.PostgresTestSafetyGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
@DisplayName("RefundSettlementPostgresTest - PostgreSQL integration test for D-08 / BLK-05, F-01 & OPEN-02B")
class RefundSettlementPostgresTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String url = PostgresTestSafetyGuard.resolveAndAssertSafeFromEnv("OGSHOP_TEST_DB_URL");
        String user = System.getenv("OGSHOP_TEST_DB_USER");
        if ("og_shop".equalsIgnoreCase(user != null ? user.trim() : "")) {
            throw new IllegalStateException("Security Guard: RefundSettlementPostgresTest must NOT execute under privileged role 'og_shop'. It must execute under 'og_test_runner'.");
        }
        String pass = System.getenv("OGSHOP_TEST_DB_PASSWORD");

        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> (user != null && !user.isBlank()) ? user : "og_test_runner");
        registry.add("spring.datasource.password", () -> pass != null ? pass : "");
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO og_compat,public");
        registry.add("spring.flyway.enabled", PostgresTestSafetyGuard::isFlywayOptInEnabled);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @BeforeAll
    static void checkDatabaseConnection() {
        PostgresTestSafetyGuard.resolveAndAssertSafeFromEnv("OGSHOP_TEST_DB_URL");
    }

    @Autowired
    private RefundSettlementService refundSettlementService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDownSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAdmin(Long adminId) {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(
                String.valueOf(adminId), "password", "ROLE_ADMIN"
        );
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Long createAdminUser() {
        String email = "admin_" + UUID.randomUUID() + "@example.test";
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.users(email, password_hash, full_name) VALUES (?, '{noop}test-pass', 'Admin') RETURNING user_id",
                Long.class, email
        );
        jdbcTemplate.update("INSERT INTO og_compat.user_roles(user_id, role_id) SELECT ?, role_id FROM og_compat.roles WHERE role_name = 'ADMIN'", id);
        authenticateAdmin(id);
        return id;
    }

    private Long createBuyerUser() {
        String email = "buyer_" + UUID.randomUUID() + "@example.test";
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.users(email, password_hash, full_name) VALUES (?, '{noop}test-pass', 'Buyer') RETURNING user_id",
                Long.class, email
        );
        jdbcTemplate.update("INSERT INTO og_compat.user_roles(user_id, role_id) SELECT ?, role_id FROM og_compat.roles WHERE role_name = 'BUYER'", id);
        jdbcTemplate.update("INSERT INTO og_compat.reward_accounts(user_id, balance) VALUES (?, 0)", id);
        return id;
    }

    private record OrderFixture(Long orderId, UUID cashComponentId) {}

    private OrderFixture createDeliveredOrderWithAllocation(
            Long buyerId, Long sellerId, long cashAmount, long pointsAmount
    ) {
        long subtotal = cashAmount + pointsAmount;
        long totalAmount = cashAmount;
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
                    status, points_discount_amount, sponsor_type, valid_delivered_at, return_due_at
                ) VALUES (
                    ?, ?, ?, ?, 'Recipient', '0912345678',
                    'HN', 'BD', 'Ward', 'Detail address',
                    ?, 0, ?, 0, 0, 0,
                    ?, CURRENT_TIMESTAMP + INTERVAL '1 hour', 'UC83', true, 'SELF_PICKUP', CURRENT_DATE,
                    'PAYMENT_PENDING', ?, 'PLATFORM', CURRENT_TIMESTAMP - INTERVAL '1 day', (CURRENT_TIMESTAMP - INTERVAL '1 day') + INTERVAL '3 days'
                ) RETURNING order_id
                """,
                Long.class,
                groupId, groupId, buyerId, sellerId,
                subtotal, totalAmount, subtotal,
                pointsAmount
        );

        UUID cashCompId = null;
        if (totalAmount > 0) {
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

            cashCompId = UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO og_compat.order_fund_components(
                        component_id, order_id, allocation_id, component_type, confirmed_amount
                    ) VALUES (?, ?, ?, 'GOODS_CASH', ?)
                    """,
                    cashCompId, orderId, allocationId, cashAmount
            );
        } else {
            jdbcTemplate.update("""
                    UPDATE og_compat.orders
                    SET status = 'DELIVERED',
                        paid_confirmed_at = CURRENT_TIMESTAMP
                    WHERE order_id = ?
                    """,
                    orderId
            );
        }

        jdbcTemplate.update("UPDATE og_compat.checkout_groups SET state = 'PAID' WHERE group_id = ?", groupId);

        if (pointsAmount > 0) {
            fundBuyerCoinsAndInsertGroupDebit(buyerId, groupId, pointsAmount);
        }

        return new OrderFixture(orderId, cashCompId);
    }

    private Long ensureAnyRewardPolicyId(Long existingAdminId) {
        // Resolve an existing policy or lazily insert the canonical policy so rolled-back @Transactional tests remain order-independent.
        List<Long> pList = jdbcTemplate.queryForList("SELECT policy_id FROM og_compat.reward_policies WHERE policy_code = 'REWARD_CHECKOUT_STANDARD'", Long.class);
        if (!pList.isEmpty()) {
            return pList.getFirst();
        }
        List<Long> anyList = jdbcTemplate.queryForList("SELECT policy_id FROM og_compat.reward_policies LIMIT 1", Long.class);
        if (!anyList.isEmpty()) {
            return anyList.getFirst();
        }
        Long creatorId = existingAdminId != null ? existingAdminId : createAdminUser();
        return jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.reward_policies(policy_code, point_value, policy_snapshot, created_by) " +
                "VALUES ('REWARD_CHECKOUT_STANDARD', 1.00, '{\"type\":\"STANDARD\"}'::jsonb, ?) RETURNING policy_id",
                Long.class, creatorId
        );
    }

    private void fundBuyerCoinsAndInsertGroupDebit(Long buyerId, UUID groupId, long pointsAmount) {
        Long adminId = createAdminUser();
        jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(
                    entry_id, user_id, entry_type, points_delta, actor_id, reason, command_key
                ) VALUES (?, ?, 'CORRECTION', ?, ?, 'Fixture initial reward balance', ?)
                """,
                UUID.randomUUID(), buyerId, pointsAmount, adminId, "CMD_INIT_BAL_" + UUID.randomUUID()
        );

        Long policyId = ensureAnyRewardPolicyId(adminId);

        // DEC-10-POLICY-PINNING: Pin reward_policy_id on checkout_groups at T1
        jdbcTemplate.update("UPDATE og_compat.checkout_groups SET reward_policy_id = ? WHERE group_id = ?", policyId, groupId);

        jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(
                    entry_id, user_id, policy_id, entry_type, points_delta, group_id, reason, command_key
                ) VALUES (?, ?, ?, 'DEBIT_CHECKOUT', ?, ?, 'Group checkout debit', ?)
                """,
                UUID.randomUUID(), buyerId, policyId, -pointsAmount, groupId, "CMD_DEBIT_GRP_" + UUID.randomUUID()
        );
    }

    private Long createOrder(Long buyerId, Long sellerId, String status, long pointsDiscountAmount) {
        UUID groupId = UUID.randomUUID();
        long subtotal = pointsDiscountAmount > 0 ? pointsDiscountAmount : 500000L;
        long totalAmount = pointsDiscountAmount > 0 ? 0L : 500000L;
        return jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.orders(
                    checkout_group_id, group_id, buyer_id, seller_id, shipping_recipient_name, shipping_phone_number,
                    shipping_province, shipping_district, shipping_ward, shipping_detail_address,
                    subtotal, shipping_fee, total_amount, voucher_discount_amount, buyer_system_fee, seller_system_fee,
                    seller_proceeds, payment_due_at, workflow_model, return_allowed, delivery_method, handover_day,
                    status, points_discount_amount, sponsor_type, completed_at, valid_delivered_at, return_due_at
                ) VALUES (
                    ?, ?, ?, ?, 'Recipient', '0912345678',
                    'HCM', 'Q1', 'Ben Nghe', '123 Test St',
                    ?, 0, ?, 0, 0, 0,
                    ?, CURRENT_TIMESTAMP + INTERVAL '1 hour', 'UC83', true, 'SELF_PICKUP', CURRENT_DATE,
                    ?, ?, 'PLATFORM', CASE WHEN ? = 'COMPLETED' THEN CURRENT_TIMESTAMP ELSE NULL END,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '3 days'
                ) RETURNING order_id
                """,
                Long.class,
                groupId, groupId, buyerId, sellerId,
                subtotal, totalAmount,
                subtotal,
                status, pointsDiscountAmount, status
        );
    }

    private Long createCaseAndDecision(Long orderId, Long buyerId, Long sellerId) {
        Long caseId = 991000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100000L);
        Long roundId = 992000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100000L);
        Long decisionId = 993000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100000L);

        jdbcTemplate.update("""
                INSERT INTO og_compat.cases(
                    case_id, case_type, order_id, created_by, description, state, command_key, created_at,
                    approved_at, valid_returned_at, seller_response_due_at
                ) VALUES (
                    ?, 'RETURN', ?, ?, 'Dispute return', 'OPEN', ?, CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '2 days'
                )
                """, caseId, orderId, buyerId, "CASE:" + UUID.randomUUID());

        jdbcTemplate.update("""
                INSERT INTO og_compat.case_rounds(
                    round_id, case_id, round_no, stage, state, opened_at
                ) VALUES (?, ?, 1, 'POST_RETURN_RESOLUTION', 'OPEN', CURRENT_TIMESTAMP)
                """, roundId, caseId);

        jdbcTemplate.update("""
                INSERT INTO og_compat.case_decisions(
                    decision_id, round_id, actor_type, reviewed_by, outcome, is_final, reason, command_key, decided_at
                ) VALUES (?, ?, 'USER', ?, 'FULL_GOODS_REFUND', true, 'Full refund approved', ?, CURRENT_TIMESTAMP)
                """, decisionId, roundId, sellerId, "DEC:" + UUID.randomUUID());

        jdbcTemplate.update("""
                UPDATE og_compat.cases SET state = 'CLOSED', closed_at = CURRENT_TIMESTAMP WHERE case_id = ?
                """, caseId);

        return decisionId;
    }

    @Test
    @DisplayName("BLK-05: confirmRefundSettlement credits refund coins with structured expires_at on real PostgreSQL")
    void testConfirmRefundSettlement_postgresIntegration() {
        // Test requires live postgres container; skipped automatically if OGSHOP_TEST_DB_URL is not set
        assertThat(refundSettlementService).isNotNull();
        assertThat(jdbcTemplate).isNotNull();

        Long buyerId = createBuyerUser();
        Long sellerId = createBuyerUser();
        OrderFixture fixture = createDeliveredOrderWithAllocation(buyerId, sellerId, 480000L, 20000L);
        Long orderId = fixture.orderId();
        Long decisionId = createCaseAndDecision(orderId, buyerId, sellerId);
        UUID componentId = fixture.cashComponentId();
        UUID opId = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO og_compat.settlement_operations (
                    operation_id, component_id, case_decision_id, operation_type, amount, cause_type, state, recipient_user_id, command_key
                ) VALUES (?, ?, ?, 'REFUND_BUYER', 480000, 'RETURN', 'REQUESTED', ?, ?)
                """, opId, componentId, decisionId, buyerId, "CMD:OP:" + opId);

        RefundSettlementService.RefundSettlementResult result = refundSettlementService.confirmRefundSettlement(opId);

        assertThat(result).isNotNull();
        assertThat(result.state()).isEqualTo("CONFIRMED");
        assertThat(result.coinsCredited()).isEqualTo(19200L);
        assertThat(result.expiresAt()).isNotNull();

        // Verify row in reward_ledger has structured expires_at populated
        Map<String, Object> ledgerRow = jdbcTemplate.queryForMap(
                "SELECT entry_type, points_delta, expires_at FROM og_compat.reward_ledger WHERE order_id = ? AND entry_type = 'CREDIT_REFUND'",
                orderId
        );
        assertThat(ledgerRow.get("entry_type")).isEqualTo("CREDIT_REFUND");
        assertThat(((Number) ledgerRow.get("points_delta")).longValue()).isEqualTo(19200L);
        assertThat(ledgerRow.get("expires_at")).isNotNull();
    }

    @Test
    @DisplayName("PO-DEC-02E: 100% Coin order confirmRefundSettlement via RETURN_PLATFORM credits coins and sets D-10 expiry on real PostgreSQL")
    void testConfirmRefundSettlement_100PercentCoin_fullGoodsRefund_postgresIntegration() {
        assertThat(refundSettlementService).isNotNull();
        assertThat(jdbcTemplate).isNotNull();

        Long buyerId = createBuyerUser();
        Long sellerId = createBuyerUser();
        long coinAmount = 50000L;

        // 1. Setup 100% Coin delivered order (cashAmount = 0, pointsAmount = 50,000)
        OrderFixture fixture = createDeliveredOrderWithAllocation(buyerId, sellerId, 0L, coinAmount);
        Long orderId = fixture.orderId();
        Long decisionId = createCaseAndDecision(orderId, buyerId, sellerId);

        // 2. Component for 100% Coin order is PLATFORM_GOODS_SUBSIDY
        UUID subsidyCompId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.order_fund_components(
                    component_id, order_id, component_type, confirmed_amount, funding_reference
                ) VALUES (?, ?, 'PLATFORM_GOODS_SUBSIDY', ?, 'VOUCHER_GRANT_ESCROW')
                """,
                subsidyCompId, orderId, coinAmount
        );

        // 3. Settlement operation is RETURN_PLATFORM
        UUID opId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.settlement_operations (
                    operation_id, component_id, case_decision_id, operation_type, amount, cause_type, state, recipient_user_id, command_key
                ) VALUES (?, ?, ?, 'RETURN_PLATFORM', ?, 'RETURN', 'REQUESTED', NULL, ?)
                """, opId, subsidyCompId, decisionId, coinAmount, "CMD:OP:100COIN:" + opId);

        // 4. Confirm refund settlement
        RefundSettlementService.RefundSettlementResult result = refundSettlementService.confirmRefundSettlement(opId);

        // 5. Assertions
        assertThat(result).isNotNull();
        assertThat(result.state()).isEqualTo("CONFIRMED");
        assertThat(result.coinsCredited()).isEqualTo(coinAmount);
        assertThat(result.expiresAt()).isNotNull();

        // Exact PO D-10 Option B Expiry Assertions
        Instant expectedExpiry = D10ExpiryCalculator.calculateExpiry(Instant.now());
        assertThat(result.expiresAt()).isEqualTo(expectedExpiry);

        // Verify structural D-10 calendar rules
        ZonedDateTime zdt = result.expiresAt().atZone(D10ExpiryCalculator.ASIA_HO_CHI_MINH);
        assertThat(zdt.toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(zdt.getDayOfMonth()).isEqualTo(1);

        ZonedDateTime nowZdt = ZonedDateTime.now(D10ExpiryCalculator.ASIA_HO_CHI_MINH);
        int remainingDays = nowZdt.toLocalDate().lengthOfMonth() - nowZdt.getDayOfMonth();
        if (remainingDays >= 15) {
            assertThat(zdt.toLocalDate()).isEqualTo(nowZdt.toLocalDate().withDayOfMonth(1).plusMonths(1));
        } else {
            assertThat(zdt.toLocalDate()).isEqualTo(nowZdt.toLocalDate().withDayOfMonth(1).plusMonths(2));
        }

        // Verify credit in reward_ledger has exact matching expires_at
        Map<String, Object> ledgerRow = jdbcTemplate.queryForMap(
                "SELECT entry_type, points_delta, expires_at FROM og_compat.reward_ledger WHERE order_id = ? AND entry_type = 'CREDIT_REFUND'",
                orderId
        );
        assertThat(ledgerRow.get("entry_type")).isEqualTo("CREDIT_REFUND");
        assertThat(((Number) ledgerRow.get("points_delta")).longValue()).isEqualTo(coinAmount);
        assertThat(ledgerRow.get("expires_at")).isNotNull();

        Object rawExpiresAt = ledgerRow.get("expires_at");
        Instant dbExpiresInstant;
        if (rawExpiresAt instanceof Timestamp ts) {
            dbExpiresInstant = ts.toInstant();
        } else if (rawExpiresAt instanceof OffsetDateTime odt) {
            dbExpiresInstant = odt.toInstant();
        } else {
            dbExpiresInstant = ((Timestamp) rawExpiresAt).toInstant();
        }
        assertThat(dbExpiresInstant).isEqualTo(expectedExpiry);

        // 6. Idempotent replay check
        RefundSettlementService.RefundSettlementResult replay = refundSettlementService.confirmRefundSettlement(opId);
        assertThat(replay.state()).isEqualTo("CONFIRMED");
        assertThat(replay.coinsCredited()).isEqualTo(0L);
    }

    @Test
    @DisplayName("P1 / DEC-10.4: confirmRefundSettlement rolls back atomically when reward_ledger write fails, preserving REQUESTED state and permitting retry")
    void testConfirmRefundSettlement_ledgerFailure_rollsBackTransactionAtomically() {
        assertThat(refundSettlementService).isNotNull();
        assertThat(jdbcTemplate).isNotNull();

        Long buyerId = createBuyerUser();
        Long sellerId = createBuyerUser();
        long coinAmount = 50000L;

        // 1. Setup 100% Coin delivered order (cashAmount = 0, pointsAmount = 50,000)
        OrderFixture fixture = createDeliveredOrderWithAllocation(buyerId, sellerId, 0L, coinAmount);
        Long orderId = fixture.orderId();
        Long decisionId = createCaseAndDecision(orderId, buyerId, sellerId);

        // 2. Component for 100% Coin order is PLATFORM_GOODS_SUBSIDY
        UUID subsidyCompId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.order_fund_components(
                    component_id, order_id, component_type, confirmed_amount, funding_reference
                ) VALUES (?, ?, 'PLATFORM_GOODS_SUBSIDY', ?, 'VOUCHER_GRANT_ESCROW')
                """,
                subsidyCompId, orderId, coinAmount
        );

        // 3. Settlement operation in REQUESTED state
        UUID opId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.settlement_operations (
                    operation_id, component_id, case_decision_id, operation_type, amount, cause_type, state, recipient_user_id, command_key
                ) VALUES (?, ?, ?, 'RETURN_PLATFORM', ?, 'RETURN', 'REQUESTED', NULL, ?)
                """, opId, subsidyCompId, decisionId, coinAmount, "CMD:OP:ROLLBACK:" + opId);

        // 4. Induce error on reward_ledger insertion:
        // Fund buyer's reward balance to Long.MAX_VALUE via Admin CORRECTION ledger entry,
        // so that the DB trigger og_apply_reward_entry fails with "ERROR: bigint out of range"
        // when confirmRefundSettlement attempts to add points_delta (50,000).
        Long adminId = createAdminUser();
        jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(
                    entry_id, user_id, entry_type, points_delta, actor_id, reason, command_key
                ) VALUES (?, ?, 'CORRECTION', ?, ?, 'Overflow test fixture', ?)
                """,
                UUID.randomUUID(), buyerId, Long.MAX_VALUE, adminId, "CMD:MAX:BAL:" + UUID.randomUUID()
        );

        // 5. Attempt confirmRefundSettlement under a savepoint.
        // The ledger failure triggers an atomic rollback to the savepoint, unwinding the partial state changes
        // (including the transition to CONFIRMED) while restoring transaction validity for subsequent verification.
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            java.sql.Savepoint sp = connection.setSavepoint("rollback_boundary");
            try {
                refundSettlementService.confirmRefundSettlement(opId);
                org.junit.jupiter.api.Assertions.fail("Expected confirmRefundSettlement to fail due to balance overflow");
            } catch (Exception ex) {
                assertThat(ex).isInstanceOf(DataAccessException.class);
                assertThat(ex.getMessage()).contains("bigint out of range");
                try {
                    connection.rollback(sp);
                } catch (java.sql.SQLException rollbackEx) {
                    throw new RuntimeException("Failed to rollback to savepoint", rollbackEx);
                }
            }
            return null;
        });

        // 6. Assert atomic rollback:
        // 6a. settlement_operations state remains REQUESTED (NOT CONFIRMED)
        String opStateAfterFail = jdbcTemplate.queryForObject(
                "SELECT state FROM og_compat.settlement_operations WHERE operation_id = ?",
                String.class, opId
        );
        assertThat(opStateAfterFail).isEqualTo("REQUESTED");

        // 6b. No partial CREDIT_REFUND in reward_ledger
        Integer creditRefundCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.reward_ledger WHERE order_id = ? AND entry_type = 'CREDIT_REFUND'",
                Integer.class, orderId
        );
        assertThat(creditRefundCount).isEqualTo(0);

        // 6c. reward_accounts balance has NOT increased beyond Long.MAX_VALUE
        Long balanceAfterFail = jdbcTemplate.queryForObject(
                "SELECT balance FROM og_compat.reward_accounts WHERE user_id = ?",
                Long.class, buyerId
        );
        assertThat(balanceAfterFail).isEqualTo(Long.MAX_VALUE);

        // 7. Clear the error condition: reset balance to 0 via Admin CORRECTION ledger entry
        jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(
                    entry_id, user_id, entry_type, points_delta, actor_id, reason, command_key
                ) VALUES (?, ?, 'CORRECTION', ?, ?, 'Reset balance fixture', ?)
                """,
                UUID.randomUUID(), buyerId, -Long.MAX_VALUE, adminId, "CMD:RESET:BAL:" + UUID.randomUUID()
        );

        // 8. Retry settlement confirmation - must now SUCCEED cleanly
        RefundSettlementService.RefundSettlementResult retryResult = refundSettlementService.confirmRefundSettlement(opId);
        assertThat(retryResult).isNotNull();
        assertThat(retryResult.state()).isEqualTo("CONFIRMED");
        assertThat(retryResult.coinsCredited()).isEqualTo(coinAmount);

        // Verify DB state after successful retry
        String opStateAfterSuccess = jdbcTemplate.queryForObject(
                "SELECT state FROM og_compat.settlement_operations WHERE operation_id = ?",
                String.class, opId
        );
        assertThat(opStateAfterSuccess).isEqualTo("CONFIRMED");

        Integer creditRefundCountAfterSuccess = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.reward_ledger WHERE order_id = ? AND entry_type = 'CREDIT_REFUND'",
                Integer.class, orderId
        );
        assertThat(creditRefundCountAfterSuccess).isEqualTo(1);

        Long finalBalance = jdbcTemplate.queryForObject(
                "SELECT balance FROM og_compat.reward_accounts WHERE user_id = ?",
                Long.class, buyerId
        );
        assertThat(finalBalance).isEqualTo(coinAmount);
    }

    @Test
    @DisplayName("F-01: Settlement confirmation enforces order-level coin cap and idempotent replay under lock")
    void testConcurrentRefundSettlement_orderCapNeverExceeded() {
        // Guarded by @EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
        assertThat(refundSettlementService).isNotNull();
        assertThat(jdbcTemplate).isNotNull();

        Long buyerId = createBuyerUser();
        Long sellerId = createBuyerUser();
        OrderFixture fixture = createDeliveredOrderWithAllocation(buyerId, sellerId, 450000L, 50000L);
        Long orderId = fixture.orderId();
        Long decisionId = createCaseAndDecision(orderId, buyerId, sellerId);
        UUID comp = fixture.cashComponentId();
        UUID opId = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO og_compat.settlement_operations (
                    operation_id, component_id, case_decision_id, operation_type, amount, cause_type, state, recipient_user_id, command_key
                ) VALUES (?, ?, ?, 'REFUND_BUYER', 450000, 'RETURN', 'REQUESTED', ?, ?)
                """,
                opId, comp, decisionId, buyerId, "CMD:OP:" + opId);

        // 1. First confirmation credits floor(450,000 * 50,000 / 500,000) = 45,000 coins
        RefundSettlementService.RefundSettlementResult r1 = refundSettlementService.confirmRefundSettlement(opId);
        assertThat(r1).isNotNull();
        assertThat(r1.state()).isEqualTo("CONFIRMED");
        assertThat(r1.coinsCredited()).isEqualTo(45000L);

        // 2. Second confirmation for same operation under lock returns idempotent replay (0 coins)
        RefundSettlementService.RefundSettlementResult r2 = refundSettlementService.confirmRefundSettlement(opId);
        assertThat(r2).isNotNull();
        assertThat(r2.state()).isEqualTo("CONFIRMED");
        assertThat(r2.coinsCredited()).isEqualTo(0L);
        assertThat(r2.message()).contains("Idempotent replay");

        // Verify ledger entries in PostgreSQL: exactly 1 row with 45,000 coins
        Long ledgerRefundTotal = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(points_delta), 0) FROM og_compat.reward_ledger WHERE order_id = ? AND entry_type = 'CREDIT_REFUND'",
                Long.class,
                orderId
        );
        assertThat(ledgerRefundTotal).isEqualTo(45000L);
    }

    private record MixedOrderFixture(Long orderId, UUID cashCompId, UUID subsidyCompId) {}

    private MixedOrderFixture createMixedOrderWithFundComponents(
            Long buyerId, Long sellerId, long cashAmount, long pointsAmount
    ) {
        long subtotal = cashAmount + pointsAmount;
        long totalAmount = cashAmount;
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
                    status, points_discount_amount, sponsor_type, valid_delivered_at, return_due_at
                ) VALUES (
                    ?, ?, ?, ?, 'Recipient', '0912345678',
                    'HN', 'BD', 'Ward', 'Detail address',
                    ?, 0, ?, 0, 0, 0,
                    ?, CURRENT_TIMESTAMP + INTERVAL '1 hour', 'UC83', true, 'SELF_PICKUP', CURRENT_DATE,
                    'PAYMENT_PENDING', ?, 'PLATFORM', CURRENT_TIMESTAMP - INTERVAL '1 day', (CURRENT_TIMESTAMP - INTERVAL '1 day') + INTERVAL '3 days'
                ) RETURNING order_id
                """,
                Long.class,
                groupId, groupId, buyerId, sellerId,
                subtotal, totalAmount, subtotal,
                pointsAmount
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

        UUID subsidyCompId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.order_fund_components(
                    component_id, order_id, component_type, confirmed_amount, funding_reference
                ) VALUES (?, ?, 'PLATFORM_GOODS_SUBSIDY', ?, 'VOUCHER_GRANT_ESCROW')
                """,
                subsidyCompId, orderId, pointsAmount
        );

        jdbcTemplate.update("UPDATE og_compat.checkout_groups SET state = 'PAID' WHERE group_id = ?", groupId);

        if (pointsAmount > 0) {
            fundBuyerCoinsAndInsertGroupDebit(buyerId, groupId, pointsAmount);
        }

        return new MixedOrderFixture(orderId, cashCompId, subsidyCompId);
    }

    @Test
    @DisplayName("F-01 / CRIT-3: Sequential PostgreSQL verification of two distinct settlement operations on the same Order conserving cumulative coin cap")
    void testTwoDistinctSettlementOperationsSameOrder_sequentialPostgresVerification_orderCapConserved() {
        assertThat(refundSettlementService).isNotNull();
        assertThat(jdbcTemplate).isNotNull();

        Long buyerId = createBuyerUser();
        Long sellerId = createBuyerUser();

        // Total subtotal 500k: Cash 450k, Coins 50k
        MixedOrderFixture fixture = createMixedOrderWithFundComponents(buyerId, sellerId, 450000L, 50000L);
        Long orderId = fixture.orderId();
        Long decisionId = createCaseAndDecision(orderId, buyerId, sellerId);

        UUID opId1 = UUID.randomUUID();
        UUID opId2 = UUID.randomUUID();

        // 2 distinct settlement operations on the same order:
        // Op 1: REFUND_BUYER on GOODS_CASH (450,000)
        jdbcTemplate.update("""
                INSERT INTO og_compat.settlement_operations (
                    operation_id, component_id, case_decision_id, operation_type, amount, cause_type, state, recipient_user_id, command_key
                ) VALUES (?, ?, ?, 'REFUND_BUYER', 450000, 'RETURN', 'REQUESTED', ?, ?)
                """, opId1, fixture.cashCompId(), decisionId, buyerId, "CMD:CONC:" + opId1);

        // Op 2: RETURN_PLATFORM on PLATFORM_GOODS_SUBSIDY (50,000)
        jdbcTemplate.update("""
                INSERT INTO og_compat.settlement_operations (
                    operation_id, component_id, case_decision_id, operation_type, amount, cause_type, state, recipient_user_id, command_key
                ) VALUES (?, ?, ?, 'RETURN_PLATFORM', 50000, 'RETURN', 'REQUESTED', NULL, ?)
                """, opId2, fixture.subsidyCompId(), decisionId, "CMD:CONC:" + opId2);

        // Execute settlement confirmations for both distinct operations on the same order
        RefundSettlementService.RefundSettlementResult res1 = refundSettlementService.confirmRefundSettlement(opId1);
        RefundSettlementService.RefundSettlementResult res2 = refundSettlementService.confirmRefundSettlement(opId2);

        assertThat(res1.state()).isEqualTo("CONFIRMED");
        assertThat(res2.state()).isEqualTo("CONFIRMED");

        // Verify total coins credited in reward_ledger for this order
        Long totalCoinsCredited = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(points_delta), 0) FROM og_compat.reward_ledger WHERE order_id = ? AND entry_type = 'CREDIT_REFUND'",
                Long.class, orderId
        );

        // Cap of 50,000 must strictly be conserved!
        assertThat(totalCoinsCredited).isNotNull();
        assertThat(totalCoinsCredited).isLessThanOrEqualTo(50000L);
        assertThat(res1.coinsCredited() + res2.coinsCredited()).isEqualTo(totalCoinsCredited);
    }

    // =========================================================================
    // Phase C & E: Targeted PostgreSQL verification for CORRECTION & CREDIT_REFUND
    // =========================================================================

    @Test
    @DisplayName("C-1 & E-1: CORRECTION with policy_id = NULL and valid Admin actor succeeds")
    void testCorrection_nullPolicyId_validAdmin_succeeds() {
        Long adminId = createAdminUser();
        Long buyerId = createBuyerUser();
        UUID entryId = UUID.randomUUID();
        String cmdKey = "CORR:TEST:" + UUID.randomUUID();

        // Credit initial 100 points via admin correction
        int rows = jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(
                    entry_id, user_id, entry_type, points_delta, actor_id, reason, command_key
                ) VALUES (?, ?, 'CORRECTION', ?, ?, ?, ?)
                """, entryId, buyerId, 100L, adminId, "Initial credit", cmdKey);

        assertThat(rows).isEqualTo(1);

        // Verify public.reward_ledger has policy_id NULL
        Map<String, Object> ledger = jdbcTemplate.queryForMap(
                "SELECT policy_id, points_delta, actor_id FROM public.reward_ledger WHERE entry_id = ?",
                entryId
        );
        assertThat(ledger.get("policy_id")).isNull();
        assertThat(((Number) ledger.get("points_delta")).longValue()).isEqualTo(100L);
        assertThat(((Number) ledger.get("actor_id")).longValue()).isEqualTo(adminId);

        // Verify balance updated in reward_accounts
        Long balance = jdbcTemplate.queryForObject(
                "SELECT balance FROM og_compat.reward_accounts WHERE user_id = ?",
                Long.class, buyerId
        );
        assertThat(balance).isEqualTo(100L);
    }

    @Test
    @DisplayName("C-2: CORRECTION with non-admin actor_id is rejected by database trigger")
    void testCorrection_nonAdminActor_rejected() {
        Long nonAdminId = createBuyerUser();
        Long buyerId = createBuyerUser();
        UUID entryId = UUID.randomUUID();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(
                    entry_id, user_id, entry_type, points_delta, actor_id, reason, command_key
                ) VALUES (?, ?, 'CORRECTION', ?, ?, ?, ?)
                """, entryId, buyerId, 50L, nonAdminId, "Unauthorized credit", "CMD:" + UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Reward source/balance not eligible");
    }

    @Test
    @DisplayName("C-3a: CORRECTION with NULL reason is rejected")
    void testCorrection_nullReason_rejected() {
        Long adminId = createAdminUser();
        Long buyerId = createBuyerUser();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(
                    entry_id, user_id, entry_type, points_delta, actor_id, reason, command_key
                ) VALUES (?, ?, 'CORRECTION', ?, ?, ?, ?)
                """, UUID.randomUUID(), buyerId, 50L, adminId, null, "CMD:" + UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("C-3b: CORRECTION with whitespace-only reason is rejected")
    void testCorrection_blankReason_rejected() {
        Long adminId = createAdminUser();
        Long buyerId = createBuyerUser();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(
                    entry_id, user_id, entry_type, points_delta, actor_id, reason, command_key
                ) VALUES (?, ?, 'CORRECTION', ?, ?, ?, ?)
                """, UUID.randomUUID(), buyerId, 50L, adminId, "   ", "CMD:" + UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Constraint violation in reward_ledger");
    }

    @Test
    @DisplayName("C-4a: CORRECTION with NULL command_key is rejected")
    void testCorrection_nullCommandKey_rejected() {
        Long adminId = createAdminUser();
        Long buyerId = createBuyerUser();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(
                    entry_id, user_id, entry_type, points_delta, actor_id, reason, command_key
                ) VALUES (?, ?, 'CORRECTION', ?, ?, ?, ?)
                """, UUID.randomUUID(), buyerId, 50L, adminId, "Valid reason", null))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("C-4b: CORRECTION with blank command_key is rejected")
    void testCorrection_blankCommandKey_rejected() {
        Long adminId = createAdminUser();
        Long buyerId = createBuyerUser();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(
                    entry_id, user_id, entry_type, points_delta, actor_id, reason, command_key
                ) VALUES (?, ?, 'CORRECTION', ?, ?, ?, ?)
                """, UUID.randomUUID(), buyerId, 50L, adminId, "Valid reason", "   "))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Constraint violation in reward_ledger");
    }

    @Test
    @DisplayName("C-5: CORRECTION with points_delta = 0 is rejected")
    void testCorrection_zeroPointsDelta_rejected() {
        Long adminId = createAdminUser();
        Long buyerId = createBuyerUser();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(
                    entry_id, user_id, entry_type, points_delta, actor_id, reason, command_key
                ) VALUES (?, ?, 'CORRECTION', 0, ?, ?, ?)
                """, UUID.randomUUID(), buyerId, adminId, "Zero delta", "CMD:" + UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Constraint violation in reward_ledger");
    }

    @Test
    @DisplayName("C-6: CREDIT_REFUND with valid expires_at succeeds")
    void testCreditRefund_validExpiresAt_succeeds() {
        Long buyerId = createBuyerUser();
        Long sellerId = createBuyerUser();
        Long orderId = createOrder(buyerId, sellerId, "COMPLETED", 10000);

        Timestamp expiresAt = Timestamp.from(Instant.now().plus(30, ChronoUnit.DAYS));
        int rows = jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(
                    entry_id, user_id, entry_type, points_delta, order_id, reason, command_key, expires_at
                ) VALUES (?, ?, 'CREDIT_REFUND', 100, ?, 'REFUND:VALID', ?, ?)
                """, UUID.randomUUID(), buyerId, orderId, "CR:VALID:" + UUID.randomUUID(), expiresAt);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("C-7: CREDIT_REFUND missing expires_at is rejected")
    void testCreditRefund_missingExpiresAt_rejected() {
        Long buyerId = createBuyerUser();
        Long sellerId = createBuyerUser();
        Long orderId = createOrder(buyerId, sellerId, "COMPLETED", 10000);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(
                    entry_id, user_id, entry_type, points_delta, order_id, reason, command_key, expires_at
                ) VALUES (?, ?, 'CREDIT_REFUND', 100, ?, 'REFUND:INVALID', ?, NULL)
                """, UUID.randomUUID(), buyerId, orderId, "CR:INVALID:" + UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("missing expires_at");
    }

    @Test
    @DisplayName("C-8a: CREDIT_COMPLETION still requires policy_id")
    void testCreditCompletion_requiresPolicyId() {
        Long buyerId = createBuyerUser();
        Long sellerId = createBuyerUser();
        Long orderId = createOrder(buyerId, sellerId, "COMPLETED", 0);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(
                    entry_id, user_id, policy_id, entry_type, points_delta, order_id, reason, command_key
                ) VALUES (?, ?, NULL, 'CREDIT_COMPLETION', 50, ?, 'Completion', ?)
                """, UUID.randomUUID(), buyerId, orderId, "CC:" + UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Required field in reward_ledger");
    }

    @Test
    @DisplayName("C-8b: DEBIT_CHECKOUT with non-negative delta is rejected by preserved validator")
    void testDebitCheckout_nonNegativeDelta_rejected() {
        Long buyerId = createBuyerUser();
        Long policyId = ensureAnyRewardPolicyId(null);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(
                    entry_id, user_id, policy_id, entry_type, points_delta, group_id, reason, command_key
                ) VALUES (?, ?, ?, 'DEBIT_CHECKOUT', 50, ?, 'Invalid positive delta', ?)
                """, UUID.randomUUID(), buyerId, policyId, UUID.randomUUID(), "DC:" + UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class)
                .satisfies(e -> assertThat(e.getMessage()).containsAnyOf("Constraint reward_ledger_points_delta_check", "Invalid debit checkout entry"));
    }

    // =========================================================================
    // Phase E: applyAdminCorrection() runtime verification
    // =========================================================================

    @Test
    @DisplayName("E-2: applyAdminCorrection() Full Debit debits account")
    void testApplyAdminCorrection_fullDebit() {
        Long adminId = createAdminUser();
        Long buyerId = createBuyerUser();
        String cmdKey = "ADMIN:CORR:FULL:" + UUID.randomUUID();

        // Seed 100 points
        jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(
                    entry_id, user_id, entry_type, points_delta, actor_id, reason, command_key
                ) VALUES (?, ?, 'CORRECTION', 100, ?, 'Seed 100', ?)
                """, UUID.randomUUID(), buyerId, adminId, "SEED:" + UUID.randomUUID());

        // Full debit: requested 50, available 100 -> debited 50, absorbed 0
        RefundSettlementService.RewardCorrectionResult res =
                refundSettlementService.applyAdminCorrection(buyerId, 50L, adminId, "Dispute chargeback", cmdKey);

        assertThat(res.actualDebited()).isEqualTo(50L);
        assertThat(res.absorbedByPlatform()).isEqualTo(0L);

        // Ledger row created with -50 points
        Long ledgerDelta = jdbcTemplate.queryForObject(
                "SELECT points_delta FROM og_compat.reward_ledger WHERE command_key = ?",
                Long.class, cmdKey
        );
        assertThat(ledgerDelta).isEqualTo(-50L);

        // Balance updated to 50
        Long balance = jdbcTemplate.queryForObject(
                "SELECT balance FROM og_compat.reward_accounts WHERE user_id = ?",
                Long.class, buyerId
        );
        assertThat(balance).isEqualTo(50L);
    }

    @Test
    @DisplayName("E-3: applyAdminCorrection() Partial Debit debits available points and audits platform absorption")
    void testApplyAdminCorrection_partialDebit() {
        Long adminId = createAdminUser();
        Long buyerId = createBuyerUser();
        String cmdKey = "ADMIN:CORR:PARTIAL:" + UUID.randomUUID();

        // Seed 20 points
        jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(
                    entry_id, user_id, entry_type, points_delta, actor_id, reason, command_key
                ) VALUES (?, ?, 'CORRECTION', 20, ?, 'Seed 20', ?)
                """, UUID.randomUUID(), buyerId, adminId, "SEED:" + UUID.randomUUID());

        // Partial debit: requested 50, available 20 -> debited 20, absorbed 30
        RefundSettlementService.RewardCorrectionResult res =
                refundSettlementService.applyAdminCorrection(buyerId, 50L, adminId, "Partial debit absorption", cmdKey);

        assertThat(res.actualDebited()).isEqualTo(20L);
        assertThat(res.absorbedByPlatform()).isEqualTo(30L);

        // Ledger has -20
        Long ledgerDelta = jdbcTemplate.queryForObject(
                "SELECT points_delta FROM og_compat.reward_ledger WHERE command_key = ?",
                Long.class, cmdKey
        );
        assertThat(ledgerDelta).isEqualTo(-20L);

        // Balance becomes 0
        Long balance = jdbcTemplate.queryForObject(
                "SELECT balance FROM og_compat.reward_accounts WHERE user_id = ?",
                Long.class, buyerId
        );
        assertThat(balance).isEqualTo(0L);

        // Audit log shows absorbedByPlatform: 30
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT * FROM og_compat.audit_logs WHERE action = 'REWARD_CORRECTION_PARTIAL_ABSORBED' AND entity_id = ? ORDER BY created_at DESC LIMIT 1",
                buyerId
        );
        assertThat(audit.get("new_values").toString())
                .contains("\"requestedDebit\": 50")
                .contains("\"actualDebited\": 20")
                .contains("\"absorbedByPlatform\": 30");
    }

    @Test
    @DisplayName("E-4: applyAdminCorrection() Full Absorption writes audit log without 0-delta ledger row")
    void testApplyAdminCorrection_fullAbsorption() {
        Long adminId = createAdminUser();
        Long buyerId = createBuyerUser();
        String cmdKey = "ADMIN:CORR:ABSORB:" + UUID.randomUUID();

        // Balance is 0. Requested 50 -> debited 0, absorbed 50.
        RefundSettlementService.RewardCorrectionResult res =
                refundSettlementService.applyAdminCorrection(buyerId, 50L, adminId, "Full absorption", cmdKey);

        assertThat(res.actualDebited()).isEqualTo(0L);
        assertThat(res.absorbedByPlatform()).isEqualTo(50L);

        // No ledger row created for commandKey
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.reward_ledger WHERE command_key = ?",
                Integer.class, cmdKey
        );
        assertThat(count).isEqualTo(0);

        // Audit log records full platform absorption
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT * FROM og_compat.audit_logs WHERE action = 'REWARD_CORRECTION_ABSORBED' AND entity_id = ? ORDER BY created_at DESC LIMIT 1",
                buyerId
        );
        assertThat(audit.get("new_values").toString())
                .contains("\"requestedDebit\": 50")
                .contains("\"actualDebited\": 0")
                .contains("\"absorbedByPlatform\": 50");
    }

    @Test
    @DisplayName("E-5: applyAdminCorrection() Idempotent replay does not duplicate ledger or audit rows")
    void testApplyAdminCorrection_idempotentReplay() {
        Long adminId = createAdminUser();
        Long buyerId = createBuyerUser();
        String cmdKey = "ADMIN:CORR:IDEMP:" + UUID.randomUUID();

        // Seed 100 points
        jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(
                    entry_id, user_id, entry_type, points_delta, actor_id, reason, command_key
                ) VALUES (?, ?, 'CORRECTION', 100, ?, 'Seed 100', ?)
                """, UUID.randomUUID(), buyerId, adminId, "SEED:" + UUID.randomUUID());

        // First execution
        RefundSettlementService.RewardCorrectionResult r1 =
                refundSettlementService.applyAdminCorrection(buyerId, 40L, adminId, "Idempotent test", cmdKey);
        assertThat(r1.actualDebited()).isEqualTo(40L);

        // Second execution with same command_key
        RefundSettlementService.RewardCorrectionResult r2 =
                refundSettlementService.applyAdminCorrection(buyerId, 40L, adminId, "Idempotent test", cmdKey);
        assertThat(r2.status()).isEqualTo("IDEMPOTENT_REPLAY");

        // Exactly 1 ledger row for cmdKey
        Integer ledgerCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.reward_ledger WHERE command_key = ?",
                Integer.class, cmdKey
        );
        assertThat(ledgerCount).isEqualTo(1);

        // Balance is 60 (only debited once)
        Long balance = jdbcTemplate.queryForObject(
                "SELECT balance FROM og_compat.reward_accounts WHERE user_id = ?",
                Long.class, buyerId
        );
        assertThat(balance).isEqualTo(60L);
    }

    // =========================================================================
    // Phase G: Spring Proxy Method Security Verification (@EnableMethodSecurity)
    // =========================================================================

    @Test
    @DisplayName("CRIT-7 / SecurityConfig: Spring CGLIB proxy enforces @PreAuthorize(\"hasRole('ADMIN')\") when caller is unauthenticated")
    void testApplyAdminCorrection_springProxyMethodSecurity_unauthenticatedCaller_rejected() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> refundSettlementService.applyAdminCorrection(100L, 50L, 999L, "Valid reason", "CMD:METHSEC_UNAUTH"))
                .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
    }

    @Test
    @DisplayName("CRIT-7 / SecurityConfig: Spring CGLIB proxy enforces @PreAuthorize(\"hasRole('ADMIN')\") when caller has ROLE_USER")
    void testApplyAdminCorrection_springProxyMethodSecurity_nonAdminCaller_rejected() {
        TestingAuthenticationToken userAuth = new TestingAuthenticationToken("999", "pass", "ROLE_USER");
        userAuth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(userAuth);

        assertThatThrownBy(() -> refundSettlementService.applyAdminCorrection(100L, 50L, 999L, "Valid reason", "CMD:METHSEC_USER"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
}


