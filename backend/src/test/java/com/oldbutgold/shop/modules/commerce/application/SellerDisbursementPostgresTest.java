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
        registry.add("spring.flyway.enabled", PostgresTestSafetyGuard::isFlywayOptInEnabled);
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
    private Long ktvId;

    @BeforeEach
    void setUp() {
        buyerId = createTestUser("buyer");
        sellerId = createTestUser("seller");
        ktvId = createTestUser("ktv");
        grantRole(buyerId, "BUYER");
        grantRole(sellerId, "SELLER");
        grantRole(ktvId, "KTV");

        Boolean isKtv = jdbcTemplate.queryForObject("SELECT og_has_role(?, 'KTV')", Boolean.class, ktvId);
        assertThat(isKtv).isTrue();
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
                    status, valid_delivered_at, version, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, 'Recipient', '0912345678',
                    'HN', 'BD', 'Ward', 'Detail address',
                    ?, 0, ?, ?, 0, 0,
                    ?, CURRENT_TIMESTAMP + INTERVAL '1 hour', 'UC83', true, 'SELF_PICKUP', CURRENT_DATE,
                    'PAYMENT_PENDING', CURRENT_TIMESTAMP - (? || ' days')::INTERVAL,
                    0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
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

        int affected = jdbcTemplate.update("""
                UPDATE og_compat.orders
                SET status = 'DELIVERED',
                    paid_confirmed_at = (SELECT confirmed_at FROM og_compat.payment_confirmations WHERE confirmation_id = ?)
                WHERE order_id = ?
                """,
                confirmationId, orderId
        );
        if (affected == 0) {
            affected = jdbcTemplate.update("""
                    UPDATE public.orders
                    SET status = 'DELIVERED',
                        paid_confirmed_at = (SELECT confirmed_at FROM og_compat.payment_confirmations WHERE confirmation_id = ?)
                    WHERE order_id = ?
                    """,
                    confirmationId, orderId
            );
        }
        assertThat(affected).as("Order must be updated to DELIVERED").isGreaterThan(0);
        String finalStatus = jdbcTemplate.queryForObject("SELECT status FROM og_compat.orders WHERE order_id = ?", String.class, orderId);
        assertThat(finalStatus).as("Order status in database must be DELIVERED").isEqualTo("DELIVERED");

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
                    payment_due_at, status, workflow_model, valid_delivered_at,
                    version, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, 'Legacy Buyer', '0912345678',
                    'HN', 'BD', 'Ward', 'Detail address',
                    100000, 20000, 120000, 100000,
                    0, 0, 0,
                    CURRENT_TIMESTAMP + INTERVAL '1 hour', 'DELIVERED', 'LEGACY_V14',
                    CURRENT_TIMESTAMP - (? || ' days')::INTERVAL,
                    0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
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

    private record ReturnCaseOrderFixture(Long orderId, Long caseId, UUID cashComponentId, UUID subsidyComponentId) {}

    /**
     * Shared helper tạo đơn hàng UC83 và mở RETURN Case hợp lệ qua view og_compat.cases:
     * 1. Đơn hàng ban đầu được tạo với valid_delivered_at cách 1 ngày trước (trong cửa sổ 3 ngày
     *    để thỏa mãn trigger og70_row_cases_og_guard_return_case).
     * 2. Mở RETURN Case qua og_compat.cases (kích hoạt trigger kiểm tra quyền mở Case và tự động sinh money_holds).
     * 3. Cập nhật valid_delivered_at thành 4 ngày trước để đáp ứng điều kiện thời hạn giao hàng > 3 ngày của UC71 disburseSellerFunds.
     */
    private ReturnCaseOrderFixture createDeliveredOrderWithReturnCase(Long buyerId, Long sellerId) {
        Uc83OrderFixture orderFixture = createDeliveredUc83Order(buyerId, sellerId, 1);
        Long orderId = orderFixture.orderId();

        Long caseId = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.cases(case_type, order_id, created_by, description, state, command_key)
                VALUES ('RETURN', ?, ?, 'Return request', 'OPEN', ?) RETURNING case_id
                """, Long.class, orderId, buyerId, "CMD_CASE_" + UUID.randomUUID());

        int validDeliveredRows = jdbcTemplate.update("UPDATE og_compat.orders SET valid_delivered_at = CURRENT_TIMESTAMP - INTERVAL '4 days' WHERE order_id = ?", orderId);
        if (validDeliveredRows == 0) {
            jdbcTemplate.update("UPDATE public.orders SET valid_delivered_at = CURRENT_TIMESTAMP - INTERVAL '4 days' WHERE order_id = ?", orderId);
        }

        return new ReturnCaseOrderFixture(orderId, caseId, orderFixture.cashComponentId(), orderFixture.subsidyComponentId());
    }

    /**
     * Shared helper tạo và xác nhận giao nhận trực tiếp hai chiều cho leg RETURN của shipment.
     * Kiểm tra chặt chẽ các bất biến nghiệp vụ và validator cases_check2:
     * - approved_at IS NOT NULL trước khi tạo shipment và trước khi giao hàng.
     * - confirmed_at > approved_at (được đảm bảo qua confirmed_at = CURRENT_TIMESTAMP + INTERVAL '1 minute').
     * - delivered >= approved_at trước khi gọi og_confirm_direct_handover.
     * - Sau khi cập nhật: state = 'POST_RETURN', valid_returned_at = delivered,
     *   và seller_response_due_at = valid_returned_at + INTERVAL '2 days'.
     */
    private Long createAndConfirmReturnShipment(Long orderId, Long caseId, Long buyerId, Long sellerId) {
        // 1. Kiểm tra precondition: approved_at IS NOT NULL
        Instant approvedAt = jdbcTemplate.queryForObject(
                "SELECT approved_at FROM og_compat.cases WHERE case_id = ?", Instant.class, caseId);
        assertThat(approvedAt).as("Case must have approved_at before return shipment").isNotNull();

        // 2. Tạo shipment leg = 'RETURN' qua og_compat.shipments
        Long shipmentId = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.shipments(order_id, carrier, status, workflow_model, leg, return_case_id, delivery_method, fee_snapshot, fee_payer)
                VALUES (?, 'SELF_PICKUP', 'PENDING', 'UC83', 'RETURN', ?, 'SELF_PICKUP', 0, 'BUYER') RETURNING shipment_id
                """, Long.class, orderId, caseId);

        // 3. Ghi nhận handover confirmations với confirmed_at sau approved_at
        for (String party : List.of("BUYER", "SELLER")) {
            jdbcTemplate.update("""
                    INSERT INTO og_compat.handover_confirmations(
                        confirmation_id, shipment_id, party, user_id, outcome, confirmed_at, command_key
                    ) VALUES (?, ?, ?, ?, 'SUCCESS', CURRENT_TIMESTAMP + INTERVAL '1 minute', ?)
                    """, UUID.randomUUID(), shipmentId, party, "BUYER".equals(party) ? buyerId : sellerId, "CMD_HO_" + UUID.randomUUID());
        }

        // 4. Kiểm tra dữ liệu trước khi gọi og_confirm_direct_handover(): delivered >= approved_at
        Instant delivered = jdbcTemplate.queryForObject("""
                SELECT MAX(confirmed_at) FROM og_compat.handover_confirmations WHERE shipment_id = ?
                """, Instant.class, shipmentId);
        assertThat(delivered).as("Delivered timestamp must be present").isNotNull();
        assertThat(delivered).as("delivered must be >= approved_at").isAfterOrEqualTo(approvedAt);

        // 5. Xác nhận direct handover
        Boolean confirmed = jdbcTemplate.queryForObject("SELECT og_confirm_direct_handover(?)", Boolean.class, shipmentId);
        assertThat(confirmed).as("og_confirm_direct_handover must return true").isTrue();

        // 6. Kiểm tra dữ liệu Case sau khi cập nhật:
        //    - state = 'POST_RETURN'
        //    - valid_returned_at IS NOT NULL
        //    - seller_response_due_at = valid_returned_at + INTERVAL '2 days'
        Boolean invariantHolds = jdbcTemplate.queryForObject("""
                SELECT (state = 'POST_RETURN' AND valid_returned_at IS NOT NULL
                        AND seller_response_due_at = valid_returned_at + INTERVAL '2 days')
                FROM og_compat.cases WHERE case_id = ?
                """, Boolean.class, caseId);
        assertThat(invariantHolds).as("Post-return constraint cases_check2 must be satisfied").isTrue();

        return shipmentId;
    }

    /**
     * Helper thực hiện quyết định APPROVE_RETURN ở Round 1 bởi KTV qua view og_compat.case_decisions.
     * Kích hoạt trigger og_apply_case_decision cập nhật approved_at trên og_compat.cases.
     */
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

    /**
     * Helper thực hiện quyết định REJECT_RETURN ở Round 1 bởi KTV qua view og_compat.case_decisions.
     * Kích hoạt trigger og_apply_case_decision cập nhật state = 'REJECTED' và đóng holds.
     */
    private void rejectReturnRound1(Long caseId, Long reviewerKtvId) {
        Long r1 = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.case_rounds(case_id, round_no, stage)
                VALUES (?, 1, 'RETURN_APPROVAL') RETURNING round_id
                """, Long.class, caseId);
        jdbcTemplate.update("""
                INSERT INTO og_compat.case_decisions(round_id, reviewed_by, actor_type, outcome, is_final, reason, command_key)
                VALUES (?, ?, 'USER', 'REJECT_RETURN', true, 'KTV rejects return request', ?)
                """, r1, reviewerKtvId, "CMD_DEC_" + UUID.randomUUID());
    }

    /**
     * Helper thực hiện quyết định FULL_GOODS_REFUND ở Round 2 bởi KTV qua view og_compat.case_decisions.
     * Kích hoạt trigger og_apply_case_decision cập nhật state = 'CLOSED' và đóng holds.
     */
    private void resolveRound2FullRefund(Long caseId, Long reviewerKtvId) {
        Long r2 = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.case_rounds(case_id, round_no, stage)
                VALUES (?, 2, 'POST_RETURN_RESOLUTION') RETURNING round_id
                """, Long.class, caseId);
        jdbcTemplate.update("""
                INSERT INTO og_compat.case_decisions(round_id, reviewed_by, actor_type, outcome, is_final, reason, command_key)
                VALUES (?, ?, 'USER', 'FULL_GOODS_REFUND', true, 'KTV full refund to buyer', ?)
                """, r2, reviewerKtvId, "CMD_DEC_" + UUID.randomUUID());
    }

    /**
     * Helper thực hiện quyết định EXEMPT_GOODS_REFUND ở Round 2 bởi KTV qua view og_compat.case_decisions.
     * Cập nhật valid_damage_request_at trên case_rounds và đóng Case.
     */
    private void resolveRound2ExemptRefund(Long caseId, Long reviewerKtvId) {
        Long r2 = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.case_rounds(case_id, round_no, stage)
                VALUES (?, 2, 'POST_RETURN_RESOLUTION') RETURNING round_id
                """, Long.class, caseId);
        jdbcTemplate.update("UPDATE og_compat.case_rounds SET valid_damage_request_at = CURRENT_TIMESTAMP WHERE round_id = ?", r2);
        jdbcTemplate.update("""
                INSERT INTO og_compat.case_decisions(round_id, reviewed_by, actor_type, outcome, is_final, reason, command_key)
                VALUES (?, ?, 'USER', 'EXEMPT_GOODS_REFUND', true, 'KTV exempt goods refund', ?)
                """, r2, reviewerKtvId, "CMD_DEC_" + UUID.randomUUID());
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
        int blockingUpdateRows = jdbcTemplate.update("UPDATE og_compat.orders SET valid_delivered_at = CURRENT_TIMESTAMP - INTERVAL '4 days' WHERE order_id = ?", fixture.orderId());
        if (blockingUpdateRows == 0) {
            jdbcTemplate.update("UPDATE public.orders SET valid_delivered_at = CURRENT_TIMESTAMP - INTERVAL '4 days' WHERE order_id = ?", fixture.orderId());
        }

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
                    status, valid_delivered_at, version, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, 'Recipient', '0912345678',
                    'HN', 'BD', 'Ward', 'Detail address',
                    ?, 0, ?, 0, 0, 0,
                    ?, CURRENT_TIMESTAMP + INTERVAL '1 hour', 'UC83', true, 'SELF_PICKUP', CURRENT_DATE,
                    'PAYMENT_PENDING', CURRENT_TIMESTAMP - INTERVAL '4 days',
                    0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
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

        int affectedZero = jdbcTemplate.update("""
                UPDATE og_compat.orders
                SET status = 'DELIVERED',
                    paid_confirmed_at = (SELECT confirmed_at FROM og_compat.payment_confirmations WHERE confirmation_id = ?)
                WHERE order_id = ?
                """,
                confirmationId, orderId
        );
        if (affectedZero == 0) {
            affectedZero = jdbcTemplate.update("""
                    UPDATE public.orders
                    SET status = 'DELIVERED',
                        paid_confirmed_at = (SELECT confirmed_at FROM og_compat.payment_confirmations WHERE confirmation_id = ?)
                    WHERE order_id = ?
                    """,
                    confirmationId, orderId
            );
        }
        assertThat(affectedZero).as("Order must be updated to DELIVERED").isGreaterThan(0);
        String finalStatusZero = jdbcTemplate.queryForObject("SELECT status FROM og_compat.orders WHERE order_id = ?", String.class, orderId);
        assertThat(finalStatusZero).as("Order status in database must be DELIVERED").isEqualTo("DELIVERED");

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

    @Test
    @DisplayName("Financial Safety Guard: UC83 order with final FULL_GOODS_REFUND decision blocks disburseSellerFunds")
    void testFinancialSafetyGuard_FullGoodsRefund_BlocksDisbursement() {
        ReturnCaseOrderFixture fixture = createDeliveredOrderWithReturnCase(buyerId, sellerId);
        Long orderId = fixture.orderId();
        Long caseId = fixture.caseId();

        // 1. Round 1: KTV APPROVE_RETURN
        approveReturnRound1(caseId, ktvId);

        // 2. Return shipment & direct handover confirmation
        createAndConfirmReturnShipment(orderId, caseId, buyerId, sellerId);

        // 3. Round 2: KTV FULL_GOODS_REFUND
        resolveRound2FullRefund(caseId, ktvId);

        // Verify trigger closed the holds and case
        Boolean hasBlocking = jdbcTemplate.queryForObject("SELECT og_order_has_blocking_case(?)", Boolean.class, orderId);
        assertThat(hasBlocking).isFalse();
        Integer activeHolds = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM og_compat.money_holds mh
                JOIN og_compat.order_fund_components ofc ON ofc.component_id = mh.component_id
                WHERE ofc.order_id = ? AND mh.closed_at IS NULL
                """, Integer.class, orderId);
        assertThat(activeHolds).isZero();

        // 4. Attempt disburseSellerFunds -> must be rejected by Financial Safety Guard
        assertThatThrownBy(() -> orderManagementService.disburseSellerFunds(orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FULL_GOODS_REFUND/EXEMPT_GOODS_REFUND");

        entityManager.flush();
        entityManager.clear();

        // Order remains DELIVERED
        OrderEntity reloadedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloadedOrder.getStatus()).isEqualTo(OrderEntity.STATUS_DELIVERED);
        assertThat(reloadedOrder.getCompletedAt()).isNull();

        // No settlement operations created, released_amount remains 0
        List<BigDecimal> amounts = jdbcTemplate.queryForList("""
                SELECT so.amount FROM og_compat.settlement_operations so
                JOIN og_compat.order_fund_components ofc ON ofc.component_id = so.component_id
                WHERE ofc.order_id = ?
                """, BigDecimal.class, orderId);
        assertThat(amounts).isEmpty();

        BigDecimal releasedAmount = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(released_amount), 0) FROM og_compat.order_fund_components WHERE order_id = ?
                """, BigDecimal.class, orderId);
        assertThat(releasedAmount).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Financial Safety Guard: UC83 order with final EXEMPT_GOODS_REFUND decision blocks disburseSellerFunds")
    void testFinancialSafetyGuard_ExemptGoodsRefund_BlocksDisbursement() {
        ReturnCaseOrderFixture fixture = createDeliveredOrderWithReturnCase(buyerId, sellerId);
        Long orderId = fixture.orderId();
        Long caseId = fixture.caseId();

        // 1. Round 1: KTV APPROVE_RETURN
        approveReturnRound1(caseId, ktvId);

        // 2. Return shipment & direct handover confirmation
        createAndConfirmReturnShipment(orderId, caseId, buyerId, sellerId);

        // 3. Round 2: KTV EXEMPT_GOODS_REFUND (after valid_damage_request_at)
        resolveRound2ExemptRefund(caseId, ktvId);

        // 4. Attempt disburseSellerFunds -> must be rejected by Financial Safety Guard
        assertThatThrownBy(() -> orderManagementService.disburseSellerFunds(orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FULL_GOODS_REFUND/EXEMPT_GOODS_REFUND");

        entityManager.flush();
        entityManager.clear();

        OrderEntity reloadedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloadedOrder.getStatus()).isEqualTo(OrderEntity.STATUS_DELIVERED);
        assertThat(reloadedOrder.getCompletedAt()).isNull();

        List<BigDecimal> amounts = jdbcTemplate.queryForList("""
                SELECT so.amount FROM og_compat.settlement_operations so
                JOIN og_compat.order_fund_components ofc ON ofc.component_id = so.component_id
                WHERE ofc.order_id = ?
                """, BigDecimal.class, orderId);
        assertThat(amounts).isEmpty();
    }

    @Test
    @DisplayName("Financial Safety Guard: UC83 order with REJECT_RETURN allows disbursement when UC71 conditions are met")
    void testFinancialSafetyGuard_RejectReturn_AllowsDisbursement() {
        ReturnCaseOrderFixture fixture = createDeliveredOrderWithReturnCase(buyerId, sellerId);
        Long orderId = fixture.orderId();
        Long caseId = fixture.caseId();

        // 1. Round 1: REJECT_RETURN decided by verified KTV user
        rejectReturnRound1(caseId, ktvId);

        // 2. Call disburseSellerFunds -> Should succeed because REJECT_RETURN is not in ('FULL_GOODS_REFUND', 'EXEMPT_GOODS_REFUND')
        var response = orderManagementService.disburseSellerFunds(orderId);

        entityManager.flush();
        entityManager.clear();

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo(OrderEntity.STATUS_COMPLETED);
        assertThat(response.totalDisbursedAmount()).isEqualByComparingTo(new BigDecimal("300000.00"));

        OrderEntity reloadedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloadedOrder.getStatus()).isEqualTo(OrderEntity.STATUS_COMPLETED);
        assertThat(reloadedOrder.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Financial Safety Guard: Trigger closed holds and og_order_has_blocking_case=false, guard still blocks")
    void testFinancialSafetyGuard_HoldClosedByTrigger_GuardStillBlocks() {
        ReturnCaseOrderFixture fixture = createDeliveredOrderWithReturnCase(buyerId, sellerId);
        Long orderId = fixture.orderId();
        Long caseId = fixture.caseId();

        // 1. Round 1: KTV APPROVE_RETURN
        approveReturnRound1(caseId, ktvId);

        // 2. Return shipment & direct handover confirmation
        createAndConfirmReturnShipment(orderId, caseId, buyerId, sellerId);

        // 3. Round 2: KTV FULL_GOODS_REFUND
        resolveRound2FullRefund(caseId, ktvId);

        // Explicitly verify the root bug condition:
        // Trigger closed the holds, blocking case check is false
        Boolean hasBlockingCase = jdbcTemplate.queryForObject("SELECT og_order_has_blocking_case(?)", Boolean.class, orderId);
        assertThat(hasBlockingCase).isFalse();

        Boolean hasActiveHold = jdbcTemplate.queryForObject("""
                SELECT EXISTS(
                    SELECT 1 FROM og_compat.money_holds mh
                    JOIN og_compat.order_fund_components ofc ON ofc.component_id = mh.component_id
                    WHERE ofc.order_id = ? AND mh.closed_at IS NULL
                )
                """, Boolean.class, orderId);
        assertThat(hasActiveHold).isFalse();

        // The guard must block here even though hasBlockingCase == false and hasActiveHold == false
        assertThatThrownBy(() -> orderManagementService.disburseSellerFunds(orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FULL_GOODS_REFUND/EXEMPT_GOODS_REFUND");
    }

    @Test
    @DisplayName("Financial Safety Guard: Repeated calls after block maintain stable state and do not create stray records")
    void testFinancialSafetyGuard_RepeatedCalls_RemainStable() {
        ReturnCaseOrderFixture fixture = createDeliveredOrderWithReturnCase(buyerId, sellerId);
        Long orderId = fixture.orderId();
        Long caseId = fixture.caseId();

        // 1. Round 1: KTV APPROVE_RETURN
        approveReturnRound1(caseId, ktvId);

        // 2. Return shipment & direct handover confirmation
        createAndConfirmReturnShipment(orderId, caseId, buyerId, sellerId);

        // 3. Round 2: KTV FULL_GOODS_REFUND
        resolveRound2FullRefund(caseId, ktvId);

        // Call 1
        assertThatThrownBy(() -> orderManagementService.disburseSellerFunds(orderId))
                .isInstanceOf(IllegalStateException.class);

        // Call 2
        assertThatThrownBy(() -> orderManagementService.disburseSellerFunds(orderId))
                .isInstanceOf(IllegalStateException.class);

        // Call 3
        assertThatThrownBy(() -> orderManagementService.disburseSellerFunds(orderId))
                .isInstanceOf(IllegalStateException.class);

        entityManager.flush();
        entityManager.clear();

        // Verify zero settlement_operations
        List<BigDecimal> amounts = jdbcTemplate.queryForList("""
                SELECT so.amount FROM og_compat.settlement_operations so
                JOIN og_compat.order_fund_components ofc ON ofc.component_id = so.component_id
                WHERE ofc.order_id = ?
                """, BigDecimal.class, orderId);
        assertThat(amounts).isEmpty();

        // Order is still DELIVERED
        OrderEntity reloadedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloadedOrder.getStatus()).isEqualTo(OrderEntity.STATUS_DELIVERED);
    }
}
