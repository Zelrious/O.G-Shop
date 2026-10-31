package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentEntity;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentRepository;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
@DisplayName("SellerDisbursementPostgresTest - Verification of Seller Disbursement (UC71)")
class SellerDisbursementPostgresTest {

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
     * Dựng đơn hàng UC83 ở trạng thái DELIVERED đầy đủ payment allocation
     * và fund components (GOODS_CASH + PLATFORM_GOODS_SUBSIDY).
     */
    private Uc83OrderFixture createDeliveredUc83Order(Long buyerId, Long sellerId, int daysDeliveredAgo) {
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
                    'PAYMENT_PENDING', CURRENT_TIMESTAMP - (? || ' days')::INTERVAL
                ) RETURNING order_id
                """,
                Long.class,
                groupId, groupId, buyerId, sellerId,
                subtotal, totalAmount, voucherDiscount, subtotal, daysDeliveredAgo
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

    /**
     * Dựng đơn hàng LEGACY_V14 ở trạng thái DELIVERED kèm Payment ở trạng thái HELD.
     */
    private Long createDeliveredLegacyOrder(Long buyerId, Long sellerId, int daysDeliveredAgo) {
        UUID checkoutGroupId = createCheckoutGroup(buyerId, new BigDecimal("120000.00"));
        Long orderId = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.orders(
                    checkout_group_id, buyer_id, seller_id, shipping_recipient_name, shipping_phone_number,
                    shipping_province, shipping_district, shipping_ward, shipping_detail_address,
                    subtotal, shipping_fee, total_amount, seller_proceeds,
                    voucher_discount_amount, buyer_system_fee, seller_system_fee,
                    payment_due_at, status, workflow_model, valid_delivered_at
                ) VALUES (
                    ?, ?, ?, 'Legacy Buyer', '0912345678',
                    'HN', 'BD', 'Ward', 'Detail address',
                    100000, 20000, 120000, 100000,
                    0, 0, 0,
                    CURRENT_TIMESTAMP + INTERVAL '1 hour', 'DELIVERED', 'LEGACY_V14',
                    CURRENT_TIMESTAMP - (? || ' days')::INTERVAL
                ) RETURNING order_id
                """,
                Long.class,
                checkoutGroupId, buyerId, sellerId, daysDeliveredAgo
        );

        PaymentEntity payment = new PaymentEntity(orderId, new BigDecimal("120000.00"), PaymentEntity.METHOD_VNPAY, Instant.now());
        payment.markHeld("TX_LEGACY_" + UUID.randomUUID(), Instant.now());
        paymentRepository.save(payment);

        return orderId;
    }

    private Long createActiveCase(Long orderId, Long buyerId, Long sellerId) {
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

    @Test
    @DisplayName("Case 1: LEGACY_V14 eligible for disbursement -> order COMPLETED, completed_at != null, payment RELEASED, released_at != null")
    void testLegacyOrder_Eligible_DisbursedSuccessfully() {
        Long orderId = createDeliveredLegacyOrder(buyerId, sellerId, 4);

        var response = orderManagementService.disburseSellerFunds(orderId);

        entityManager.flush();
        entityManager.clear();

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo(OrderEntity.STATUS_COMPLETED);
        assertThat(response.totalDisbursedAmount()).isEqualByComparingTo(new BigDecimal("120000.00"));

        OrderEntity reloadedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloadedOrder.getStatus()).isEqualTo(OrderEntity.STATUS_COMPLETED);
        assertThat(reloadedOrder.getCompletedAt()).isNotNull();

        PaymentEntity reloadedPayment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(reloadedPayment.getStatus()).isEqualTo(PaymentEntity.STATUS_RELEASED);
        assertThat(reloadedPayment.getReleasedAt()).isNotNull();
    }

    @Test
    @DisplayName("Case 2: LEGACY_V14 less than 3 days and not early completed -> throws IllegalStateException, no status change")
    void testLegacyOrder_LessThan3Days_ThrowsException() {
        Long orderId = createDeliveredLegacyOrder(buyerId, sellerId, 1);

        assertThatThrownBy(() -> orderManagementService.disburseSellerFunds(orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Chưa đủ 3 ngày");

        entityManager.flush();
        entityManager.clear();

        OrderEntity reloadedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloadedOrder.getStatus()).isEqualTo(OrderEntity.STATUS_DELIVERED);
        assertThat(reloadedOrder.getCompletedAt()).isNull();

        PaymentEntity reloadedPayment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(reloadedPayment.getStatus()).isEqualTo(PaymentEntity.STATUS_HELD);
        assertThat(reloadedPayment.getReleasedAt()).isNull();
    }

    @Test
    @DisplayName("Case 3: UC83 eligible -> 2 settlement_operations CONFIRMED, released_amount increased, order COMPLETED")
    void testUc83Order_Eligible_DisbursedSuccessfully() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId, 4);

        var response = orderManagementService.disburseSellerFunds(fixture.orderId());

        entityManager.flush();
        entityManager.clear();

        assertThat(response.orderId()).isEqualTo(fixture.orderId());
        assertThat(response.status()).isEqualTo(OrderEntity.STATUS_COMPLETED);
        assertThat(response.operations()).hasSize(2);
        assertThat(response.totalDisbursedAmount()).isEqualByComparingTo(new BigDecimal("300000.00"));

        OrderEntity reloadedOrder = orderRepository.findById(fixture.orderId()).orElseThrow();
        assertThat(reloadedOrder.getStatus()).isEqualTo(OrderEntity.STATUS_COMPLETED);
        assertThat(reloadedOrder.getCompletedAt()).isNotNull();

        // Kiểm tra settlement_operations trong DB
        List<Map<String, Object>> ops = jdbcTemplate.queryForList("""
                SELECT so.operation_type, so.cause_type, so.state, so.amount, ofc.component_type
                FROM settlement_operations so
                JOIN order_fund_components ofc ON ofc.component_id = so.component_id
                WHERE ofc.order_id = ?
                ORDER BY ofc.component_type
                """, fixture.orderId());
        assertThat(ops).hasSize(2);
        for (var op : ops) {
            assertThat(op.get("operation_type")).isEqualTo("RELEASE_SELLER");
            assertThat(op.get("cause_type")).isEqualTo("COMPLETION");
            assertThat(op.get("state")).isEqualTo("CONFIRMED");
        }

        // Kiểm tra trigger DB tự cộng released_amount trong order_fund_components
        BigDecimal cashReleased = jdbcTemplate.queryForObject(
                "SELECT released_amount FROM order_fund_components WHERE component_id = ?",
                BigDecimal.class, fixture.cashComponentId());
        BigDecimal subsidyReleased = jdbcTemplate.queryForObject(
                "SELECT released_amount FROM order_fund_components WHERE component_id = ?",
                BigDecimal.class, fixture.subsidyComponentId());

        assertThat(cashReleased).isEqualByComparingTo(new BigDecimal("250000.00"));
        assertThat(subsidyReleased).isEqualByComparingTo(new BigDecimal("50000.00"));
    }

    @Test
    @DisplayName("Case 4: Replay with same command_key -> still exactly 2 operations, released_amount not doubled")
    void testUc83Order_Replay_IdempotentNoDoubleDisbursement() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId, 4);

        var firstResponse = orderManagementService.disburseSellerFunds(fixture.orderId());
        entityManager.flush();
        entityManager.clear();
        assertThat(firstResponse.status()).isEqualTo(OrderEntity.STATUS_COMPLETED);

        // Replay hành động giải ngân
        var secondResponse = orderManagementService.disburseSellerFunds(fixture.orderId());
        entityManager.flush();
        entityManager.clear();

        assertThat(secondResponse.orderId()).isEqualTo(fixture.orderId());
        assertThat(secondResponse.status()).isEqualTo(OrderEntity.STATUS_COMPLETED);
        assertThat(secondResponse.totalDisbursedAmount()).isEqualByComparingTo(new BigDecimal("300000.00"));

        // Vẫn đúng 2 bản ghi settlement_operations
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM settlement_operations so
                JOIN order_fund_components ofc ON ofc.component_id = so.component_id
                WHERE ofc.order_id = ?
                """, Integer.class, fixture.orderId());
        assertThat(count).isEqualTo(2);

        // released_amount không bị cộng dồn lần 2
        BigDecimal cashReleased = jdbcTemplate.queryForObject(
                "SELECT released_amount FROM order_fund_components WHERE component_id = ?",
                BigDecimal.class, fixture.cashComponentId());
        BigDecimal subsidyReleased = jdbcTemplate.queryForObject(
                "SELECT released_amount FROM order_fund_components WHERE component_id = ?",
                BigDecimal.class, fixture.subsidyComponentId());

        assertThat(cashReleased).isEqualByComparingTo(new BigDecimal("250000.00"));
        assertThat(subsidyReleased).isEqualByComparingTo(new BigDecimal("50000.00"));
    }

    @Test
    @DisplayName("Case 5: Blocking case open -> rejected, no operation created")
    void testUc83Order_BlockingCaseOpen_Rejected() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId, 1);
        createActiveCase(fixture.orderId(), buyerId, sellerId);
        jdbcTemplate.update("UPDATE og_compat.orders SET valid_delivered_at = CURRENT_TIMESTAMP - INTERVAL '4 days' WHERE order_id = ?", fixture.orderId());

        assertThatThrownBy(() -> orderManagementService.disburseSellerFunds(fixture.orderId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("khiếu nại");

        entityManager.flush();
        entityManager.clear();

        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM settlement_operations so
                JOIN order_fund_components ofc ON ofc.component_id = so.component_id
                WHERE ofc.order_id = ?
                """, Integer.class, fixture.orderId());
        assertThat(count).isEqualTo(0);

        OrderEntity reloadedOrder = orderRepository.findById(fixture.orderId()).orElseThrow();
        assertThat(reloadedOrder.getStatus()).isEqualTo(OrderEntity.STATUS_DELIVERED);
    }

    @Test
    @DisplayName("Case 6: Active money hold present -> rejected, no operation created")
    void testUc83Order_ActiveHold_Rejected() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId, 4);

        jdbcTemplate.update("""
                INSERT INTO money_holds(hold_id, component_id, source_key, reason)
                VALUES (gen_random_uuid(), ?, 'MANUAL_HOLD', 'Fixture manual hold')
                """, fixture.cashComponentId());

        assertThatThrownBy(() -> orderManagementService.disburseSellerFunds(fixture.orderId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tạm giữ");

        entityManager.flush();
        entityManager.clear();

        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM settlement_operations so
                JOIN order_fund_components ofc ON ofc.component_id = so.component_id
                WHERE ofc.order_id = ?
                """, Integer.class, fixture.orderId());
        assertThat(count).isEqualTo(0);

        OrderEntity reloadedOrder = orderRepository.findById(fixture.orderId()).orElseThrow();
        assertThat(reloadedOrder.getStatus()).isEqualTo(OrderEntity.STATUS_DELIVERED);
    }

    @Test
    @DisplayName("Case 7: Component with 0 available balance -> skips 0 amount operation")
    void testUc83Order_ZeroBalanceComponent_Skipped() {
        BigDecimal subtotal = new BigDecimal("200000.00");
        BigDecimal totalAmount = new BigDecimal("200000.00");

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
                    ?, 0, ?, 0, 0, 0,
                    ?, CURRENT_TIMESTAMP + INTERVAL '1 hour', 'UC83', true, 'SELF_PICKUP', CURRENT_DATE,
                    'PAYMENT_PENDING', CURRENT_TIMESTAMP - INTERVAL '4 days'
                ) RETURNING order_id
                """,
                Long.class,
                groupId, groupId, buyerId, sellerId,
                subtotal, totalAmount, subtotal
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
                INSERT INTO og_compat.payment_attempts(intent_id, txn_ref, amount, provider)
                VALUES (?, ?, ?, 'BANK_TRANSFER_MOCK') RETURNING attempt_id
                """, Long.class, intentId, "TXN_" + UUID.randomUUID(), totalAmount
        );

        UUID confirmationId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.payment_confirmations(confirmation_id, intent_id, attempt_id, provider, provider_transaction_id, amount)
                VALUES (?, ?, ?, 'BANK_TRANSFER_MOCK', ?, ?)
                """, confirmationId, intentId, attemptId, "TXN_PROV_" + UUID.randomUUID(), totalAmount
        );

        UUID allocationId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.payment_allocations(allocation_id, intent_id, confirmation_id, order_id, amount)
                VALUES (?, ?, ?, ?, ?)
                """, allocationId, intentId, confirmationId, orderId, totalAmount
        );

        jdbcTemplate.update("""
                UPDATE og_compat.orders
                SET status = 'DELIVERED',
                    paid_confirmed_at = (SELECT confirmed_at FROM og_compat.payment_confirmations WHERE confirmation_id = ?)
                WHERE order_id = ?
                """,
                confirmationId, orderId
        );

        // Chỉ tạo 1 component GOODS_CASH (không có subsidy)
        UUID cashComponentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.order_fund_components(
                    component_id, order_id, allocation_id, component_type, confirmed_amount
                ) VALUES (?, ?, ?, 'GOODS_CASH', ?)
                """, cashComponentId, orderId, allocationId, totalAmount
        );

        var response = orderManagementService.disburseSellerFunds(orderId);

        entityManager.flush();
        entityManager.clear();

        // Chỉ đúng 1 operation được tạo (cho GOODS_CASH), không có operation 0đ nào
        assertThat(response.operations()).hasSize(1);
        assertThat(response.operations().getFirst().componentType()).isEqualTo("GOODS_CASH");
        assertThat(response.totalDisbursedAmount()).isEqualByComparingTo(new BigDecimal("200000.00"));

        List<BigDecimal> amounts = jdbcTemplate.queryForList("""
                SELECT so.amount FROM settlement_operations so
                JOIN order_fund_components ofc ON ofc.component_id = so.component_id
                WHERE ofc.order_id = ?
                """, BigDecimal.class, orderId);
        assertThat(amounts).hasSize(1);
        assertThat(amounts.getFirst()).isGreaterThan(BigDecimal.ZERO);
    }
}
