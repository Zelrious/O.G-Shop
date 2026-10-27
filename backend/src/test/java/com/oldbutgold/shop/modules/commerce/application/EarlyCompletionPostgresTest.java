package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentEntity;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentRepository;
import com.oldbutgold.shop.shared.PostgresTestSafetyGuard;
import jakarta.persistence.EntityManager;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task 2.1 Verification Test on Real PostgreSQL:
 * <p>
 * Verifies Early Completion (UC27) and Confirm Received behaviors according to specification:
 * - Group 1: Direct invocation of stored procedure {@code og_early_complete_order} via {@link JdbcTemplate}
 *   asserting SQLSTATE 23514 guards and successful early completion event recording.
 * - Group 2: Invocation via {@link OrderManagementService}:
 *   - {@code confirmReceived} is idempotent, sets received_at, keeps order in DELIVERED status without disbursing funds.
 *   - Legacy {@code completeBuyerOrder} (POST /complete) strictly blocks UC83 orders with HTTP 400 guidance while preserving LEGACY_V14 flow.
 *   - {@code earlyCompleteOrder} validates Java-level guards and triggers {@code og_early_complete_order},
 *     verifying the CRITICAL INVARIANT: order status REMAINS 'DELIVERED', completed_at REMAINS null, and funds REMAIN undisbursed.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
@DisplayName("EarlyCompletionPostgresTest - Verification of Early Complete & Confirm Received")
class EarlyCompletionPostgresTest {

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
    private OrderManagementService orderManagementService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

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

    private record Uc83OrderFixture(Long orderId, UUID cashComponentId, UUID subsidyComponentId) {}

    /**
     * Creates a fully valid UC83 DELIVERED order with complete payment allocation
     * and fund components (GOODS_CASH + PLATFORM_GOODS_SUBSIDY).
     * Complies with ck_orders_amounts, og_guard_fund_component, and valid_delivered_at.
     */
    private Uc83OrderFixture createDeliveredUc83Order(Long buyerId, Long sellerId) {
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

    private Long createDeliveredLegacyOrder(Long buyerId, Long sellerId) {
        // og25_validate_existing_row requires checkout_group_id (it has no DB default).
        UUID checkoutGroupId = createCheckoutGroup(buyerId, new BigDecimal("120000.00"));
        Long orderId = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.orders(
                    checkout_group_id, buyer_id, seller_id, shipping_recipient_name, shipping_phone_number,
                    shipping_province, shipping_district, shipping_ward, shipping_detail_address,
                    subtotal, shipping_fee, total_amount, seller_proceeds,
                    voucher_discount_amount, buyer_system_fee, seller_system_fee,
                    payment_due_at, status, workflow_model
                ) VALUES (
                    ?, ?, ?, 'Legacy Buyer', '0912345678',
                    'HN', 'BD', 'Ward', 'Detail address',
                    100000, 20000, 120000, 100000,
                    0, 0, 0,
                    CURRENT_TIMESTAMP + INTERVAL '1 hour', 'DELIVERED', 'LEGACY_V14'
                ) RETURNING order_id
                """,
                Long.class,
                checkoutGroupId, buyerId, sellerId
        );

        PaymentEntity payment = new PaymentEntity(orderId, new BigDecimal("120000.00"), PaymentEntity.METHOD_VNPAY, Instant.now());
        payment.markHeld("TX_LEGACY_" + UUID.randomUUID(), Instant.now());
        paymentRepository.save(payment);

        return orderId;
    }

    private Long createActiveCase(Long orderId, Long buyerId, Long sellerId) {
        // Matches the live og_compat.cases schema (V19/V22): case_type, created_by, description, command_key.
        return jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.cases(
                    case_type, order_id, created_by, description, state, command_key
                ) VALUES (
                    'RETURN', ?, ?, 'Fixture active case', 'OPEN', ?
                ) RETURNING case_id
                """,
                Long.class,
                orderId, buyerId, "CMD_CASE_" + UUID.randomUUID()
        );
    }

    private void createActiveMoneyHold(Long caseId, UUID componentId) {
        jdbcTemplate.update("""
                INSERT INTO og_compat.money_holds(
                    hold_id, component_id, source_key, reason, case_id
                ) VALUES (
                    gen_random_uuid(), ?, 'FIXTURE_HOLD', 'Hold for open case', ?
                )
                """,
                componentId, caseId
        );
    }

    private void assertSqlState23514(ThrowingCallable callable, String expectedSnippet) {
        assertThatThrownBy(callable)
                .isInstanceOf(DataAccessException.class)
                .satisfies(ex -> {
                    Throwable root = ((DataAccessException) ex).getRootCause();
                    assertThat(root).isInstanceOf(SQLException.class);
                    SQLException sqlEx = (SQLException) root;
                    assertThat(sqlEx.getSQLState()).isEqualTo("23514");
                    if (expectedSnippet != null && !expectedSnippet.isBlank()) {
                        assertThat(sqlEx.getMessage()).contains(expectedSnippet);
                    }
                });
    }

    // =========================================================================
    // GROUP 1: Direct Stored Procedure Invocation via JdbcTemplate
    // =========================================================================

    @Test
    @DisplayName("Group 1.1: og_early_complete_order throws SQLSTATE 23514 when warning consent is missing or empty")
    void testSpDirect_MissingConsent_ThrowsSqlState23514() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId);

        assertSqlState23514(
                () -> jdbcTemplate.queryForObject(
                        "SELECT og_early_complete_order(?, ?, '', ?)",
                        Boolean.class,
                        fixture.orderId(), buyerId, "CMD_KEY_" + UUID.randomUUID()
                ),
                "Early completion needs Buyer warning consent and no blockers"
        );

        assertSqlState23514(
                () -> jdbcTemplate.queryForObject(
                        "SELECT og_early_complete_order(?, ?, '   ', ?)",
                        Boolean.class,
                        fixture.orderId(), buyerId, "CMD_KEY_" + UUID.randomUUID()
                ),
                "Early completion needs Buyer warning consent and no blockers"
        );
    }

    @Test
    @DisplayName("Group 1.2: og_early_complete_order throws SQLSTATE 23514 when order has active case")
    void testSpDirect_ActiveCase_ThrowsSqlState23514() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId);
        createActiveCase(fixture.orderId(), buyerId, sellerId);

        assertSqlState23514(
                () -> jdbcTemplate.queryForObject(
                        "SELECT og_early_complete_order(?, ?, 'v1.0', ?)",
                        Boolean.class,
                        fixture.orderId(), buyerId, "CMD_KEY_" + UUID.randomUUID()
                ),
                "Early completion needs Buyer warning consent and no blockers"
        );
    }

    @Test
    @DisplayName("Group 1.3: og_early_complete_order throws SQLSTATE 23514 when order has unclosed money_hold")
    void testSpDirect_OpenMoneyHold_ThrowsSqlState23514() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId);
        Long caseId = createActiveCase(fixture.orderId(), buyerId, sellerId);
        createActiveMoneyHold(caseId, fixture.cashComponentId());

        assertSqlState23514(
                () -> jdbcTemplate.queryForObject(
                        "SELECT og_early_complete_order(?, ?, 'v1.0', ?)",
                        Boolean.class,
                        fixture.orderId(), buyerId, "CMD_KEY_" + UUID.randomUUID()
                ),
                "Early completion needs Buyer warning consent and no blockers"
        );
    }

    // Each expected SQL error aborts the PostgreSQL transaction, so every sub-scenario
    // needs its own test method (fresh transaction) to avoid SQLSTATE 25P02.

    @Test
    @DisplayName("Group 1.4a: og_early_complete_order throws SQLSTATE 23514 when order status is not DELIVERED")
    void testSpDirect_NotDelivered_ThrowsSqlState23514() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId);

        jdbcTemplate.update("UPDATE og_compat.orders SET status = 'SELLER_CONFIRMED' WHERE order_id = ?", fixture.orderId());
        assertSqlState23514(
                () -> jdbcTemplate.queryForObject(
                        "SELECT og_early_complete_order(?, ?, 'v1.0', ?)",
                        Boolean.class,
                        fixture.orderId(), buyerId, "CMD_KEY_" + UUID.randomUUID()
                ),
                "Early completion needs Buyer warning consent and no blockers"
        );
    }

    @Test
    @DisplayName("Group 1.4b: og_early_complete_order throws SQLSTATE 23514 when valid_delivered_at is NULL")
    void testSpDirect_MissingValidDeliveredAt_ThrowsSqlState23514() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId);

        jdbcTemplate.update("UPDATE og_compat.orders SET valid_delivered_at = NULL WHERE order_id = ?", fixture.orderId());
        assertSqlState23514(
                () -> jdbcTemplate.queryForObject(
                        "SELECT og_early_complete_order(?, ?, 'v1.0', ?)",
                        Boolean.class,
                        fixture.orderId(), buyerId, "CMD_KEY_" + UUID.randomUUID()
                ),
                "Early completion needs Buyer warning consent and no blockers"
        );
    }

    @Test
    @DisplayName("Group 1.5: og_early_complete_order succeeds: records early_completed_at, EARLY_COMPLETION_ACCEPTED event, and is idempotent")
    void testSpDirect_Success_UpdatesEarlyCompletedAtAndInsertsOrderEvent() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId);
        String commandKey = "CMD_DIRECT_OK_" + UUID.randomUUID();

        Boolean success = jdbcTemplate.queryForObject(
                "SELECT og_early_complete_order(?, ?, 'v1.0', ?)",
                Boolean.class,
                fixture.orderId(), buyerId, commandKey
        );
        assertThat(success).isTrue();

        Map<String, Object> orderRow = jdbcTemplate.queryForMap(
                "SELECT early_completed_at, early_completion_warning_version, status FROM og_compat.orders WHERE order_id = ?",
                fixture.orderId()
        );
        assertThat(orderRow.get("early_completed_at")).isNotNull();
        assertThat(orderRow.get("early_completion_warning_version")).isEqualTo("v1.0");
        assertThat(orderRow.get("status")).isEqualTo("DELIVERED");

        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.order_events WHERE order_id = ? AND event_type = 'EARLY_COMPLETION_ACCEPTED' AND command_key = ?",
                Integer.class,
                fixture.orderId(), commandKey
        );
        assertThat(eventCount).isEqualTo(1);

        // Idempotency check: replay same commandKey returns TRUE and does not duplicate event
        Boolean replaySuccess = jdbcTemplate.queryForObject(
                "SELECT og_early_complete_order(?, ?, 'v1.0', ?)",
                Boolean.class,
                fixture.orderId(), buyerId, commandKey
        );
        assertThat(replaySuccess).isTrue();

        Integer replayCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.order_events WHERE order_id = ? AND event_type = 'EARLY_COMPLETION_ACCEPTED' AND command_key = ?",
                Integer.class,
                fixture.orderId(), commandKey
        );
        assertThat(replayCount).isEqualTo(1);
    }

    // =========================================================================
    // GROUP 2: Invocation via OrderManagementService & Business Exception Mapping
    // =========================================================================

    @Test
    @DisplayName("Group 2.1: confirmReceived is idempotent, sets received_at, keeps order in DELIVERED status without disbursing funds")
    void testService_ConfirmReceived_Idempotent_KeepsDeliveredWithoutDisbursement() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId);

        // First call
        var resp1 = orderManagementService.confirmReceived(buyerId, fixture.orderId());
        assertThat(resp1.orderId()).isEqualTo(fixture.orderId());
        assertThat(resp1.status()).isEqualTo(OrderEntity.STATUS_DELIVERED);

        entityManager.flush();
        entityManager.clear();

        OrderEntity o1 = orderRepository.findById(fixture.orderId()).orElseThrow();
        Instant firstReceivedAt = o1.getReceivedAt();
        assertThat(firstReceivedAt).isNotNull();
        assertThat(o1.getStatus()).isEqualTo(OrderEntity.STATUS_DELIVERED);
        assertThat(o1.getCompletedAt()).isNull();

        // Second call (idempotency check)
        var resp2 = orderManagementService.confirmReceived(buyerId, fixture.orderId());
        assertThat(resp2.orderId()).isEqualTo(fixture.orderId());

        entityManager.flush();
        entityManager.clear();

        OrderEntity o2 = orderRepository.findById(fixture.orderId()).orElseThrow();
        assertThat(o2.getReceivedAt()).isEqualTo(firstReceivedAt);
        assertThat(o2.getStatus()).isEqualTo(OrderEntity.STATUS_DELIVERED);
        assertThat(o2.getCompletedAt()).isNull();

        // Verify funds remain undisbursed
        BigDecimal releasedTotal = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(released_amount), 0) FROM og_compat.order_fund_components WHERE order_id = ?",
                BigDecimal.class,
                fixture.orderId()
        );
        assertThat(releasedTotal).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Group 2.2: completeBuyerOrder (POST /complete) rejects UC83 orders with HTTP 400 guidance")
    void testService_CompleteBuyerOrder_RejectsUc83OrderWithClearGuidance() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId);

        assertThatThrownBy(() -> orderManagementService.completeBuyerOrder(buyerId, fixture.orderId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UC83")
                .hasMessageContaining("confirm-received")
                .hasMessageContaining("early-complete");
    }

    @Test
    @DisplayName("Group 2.3: completeBuyerOrder preserves legacy behavior for LEGACY_V14 orders")
    void testService_CompleteBuyerOrder_AllowsLegacyV14Order() {
        Long legacyOrderId = createDeliveredLegacyOrder(buyerId, sellerId);

        var resp = orderManagementService.completeBuyerOrder(buyerId, legacyOrderId);
        assertThat(resp.orderId()).isEqualTo(legacyOrderId);
        assertThat(resp.status()).isEqualTo(OrderEntity.STATUS_COMPLETED);

        entityManager.flush();
        entityManager.clear();

        OrderEntity order = orderRepository.findById(legacyOrderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_COMPLETED);
        assertThat(order.getCompletedAt()).isNotNull();

        PaymentEntity payment = paymentRepository.findByOrderId(legacyOrderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentEntity.STATUS_RELEASED);
        assertThat(payment.getReleasedAt()).isNotNull();
    }

    @Test
    @DisplayName("Group 2.4: earlyCompleteOrder throws IllegalArgumentException when warningConsent is missing")
    void testService_EarlyCompleteOrder_MissingConsent_ThrowsIllegalArgumentException() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId);

        assertThatThrownBy(() -> orderManagementService.earlyCompleteOrder(
                buyerId, fixture.orderId(), new OrderManagementDtos.EarlyCompleteRequest(false, "CMD_1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("warningConsent");

        assertThatThrownBy(() -> orderManagementService.earlyCompleteOrder(
                buyerId, fixture.orderId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("warningConsent");
    }

    @Test
    @DisplayName("Group 2.5: earlyCompleteOrder throws IllegalStateException when order has active case")
    void testService_EarlyCompleteOrder_ActiveCase_ThrowsIllegalStateException() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId);
        createActiveCase(fixture.orderId(), buyerId, sellerId);

        assertThatThrownBy(() -> orderManagementService.earlyCompleteOrder(
                buyerId, fixture.orderId(), new OrderManagementDtos.EarlyCompleteRequest(true, "CMD_2")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("khiếu nại");
    }

    @Test
    @DisplayName("Group 2.6: earlyCompleteOrder succeeds: CRITICAL INVARIANT: order status REMAINS DELIVERED and funds REMAIN undisbursed")
    void testService_EarlyCompleteOrder_Success_ChotChanDeliveredAndNotDisbursed() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId);
        String commandKey = "CMD_SERVICE_OK_" + UUID.randomUUID();

        var response = orderManagementService.earlyCompleteOrder(
                buyerId, fixture.orderId(), new OrderManagementDtos.EarlyCompleteRequest(true, commandKey)
        );

        assertThat(response.orderId()).isEqualTo(fixture.orderId());
        assertThat(response.status()).isEqualTo(OrderEntity.STATUS_DELIVERED);

        // MANDATORY: Flush + Clear L1 cache to test DB persistence and re-read from fresh DB SELECT
        entityManager.flush();
        entityManager.clear();

        OrderEntity reloaded = orderRepository.findById(fixture.orderId()).orElseThrow();

        // 1. Assert early_completed_at and warning_version populated
        assertThat(reloaded.getEarlyCompletedAt()).isNotNull();
        assertThat(reloaded.getEarlyCompletionWarningVersion()).isEqualTo(OrderManagementService.EARLY_COMPLETION_WARNING_VERSION);

        // 2. CRITICAL INVARIANT: Order status REMAINS DELIVERED and completedAt is null
        assertThat(reloaded.getStatus()).isEqualTo(OrderEntity.STATUS_DELIVERED);
        assertThat(reloaded.getCompletedAt()).isNull();

        // 3. CRITICAL INVARIANT: Funds REMAIN undisbursed (released_amount == 0 for all fund components)
        BigDecimal totalReleased = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(released_amount), 0) FROM og_compat.order_fund_components WHERE order_id = ?",
                BigDecimal.class,
                fixture.orderId()
        );
        assertThat(totalReleased).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
