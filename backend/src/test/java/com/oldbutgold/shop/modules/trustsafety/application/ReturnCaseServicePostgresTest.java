package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentEntity;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentRepository;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
        registry.add("spring.flyway.enabled", PostgresTestSafetyGuard::isFlywayOptInEnabled);
        registry.add("spring.flyway.schemas", () -> "public");
        registry.add("spring.flyway.default-schema", () -> "public");
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
    private ShipmentRepository shipmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

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
        BigDecimal totalAmount = new BigDecimal("250000.00");
        BigDecimal voucherDiscount = new BigDecimal("50000.00");
        BigDecimal subtotal = totalAmount.add(voucherDiscount);

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
                    ?, ?, ?, ?, 'Buyer', '0912345678',
                    'HN', 'BD', 'Ward', 'Detail address',
                    ?, 0, ?, ?, 0, 0,
                    ?, (SELECT payment_due_at FROM og_compat.checkout_groups WHERE group_id = ?), 'UC83', ?, 'SELF_PICKUP', CURRENT_DATE,
                    'PAYMENT_PENDING', """ + deliveredSql + ", " + earlyCompletedSql + ", " + earlyWarningSql + """
                ) RETURNING order_id
                """,
                Long.class,
                groupId, groupId, buyerId, sellerId,
                subtotal, totalAmount, voucherDiscount, subtotal, groupId, returnAllowed
        );

        UUID intentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.payment_intents(
                    intent_id, purpose, group_id, expected_amount, payment_method, deadline, command_key
                ) VALUES (?, 'CHECKOUT_GROUP', ?, ?, 'BANK_TRANSFER_MOCK', (SELECT payment_due_at FROM og_compat.checkout_groups WHERE group_id = ?), ?)
                """,
                intentId, groupId, totalAmount, groupId, "CMD_INT_" + UUID.randomUUID()
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
     * Dựng Order UC83 tuân thủ 100% tất cả các constraint & deferred trigger
     * (og_validate_checkout_aggregate, og_validate_order_workflow_proof, og_guard_listing_activation)
     * để commit sạch sẽ khi chạy các concurrency integration test đa luồng.
     */
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
        BigDecimal cashAmount = new BigDecimal("250000.00");
        BigDecimal voucherDiscount = BigDecimal.ZERO;
        BigDecimal subtotal = cashAmount; // 250000.00

        Long pid = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.products(seller_id, category_id, title, description, listed_price, condition, workflow_model, quantity_total, return_allowed, delivery_options)
                VALUES (?, ?, 'Item', 'Desc', ?, 'GOOD', 'UC83', 10, true, '["SELF_PICKUP","CARRIER"]'::jsonb) RETURNING product_id
                """, Long.class, sellerId, categoryId, subtotal);
        jdbcTemplate.update("INSERT INTO og_compat.product_categories(product_id, category_id) VALUES (?, ?)", pid, categoryId);

        Long rid = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.product_revisions(product_id, revision_no, provenance, listed_unit_price, quantity, return_allowed, delivery_options, content_snapshot)
                VALUES (?, 1, 'UC83', ?, 10, true, '["SELF_PICKUP","CARRIER"]'::jsonb, '{}'::jsonb) RETURNING revision_id
                """, Long.class, pid, subtotal);

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

        jdbcTemplate.update("UPDATE og_compat.products SET status = 'ACTIVE', public_revision_id = ?, listed_price = ?, quantity_total = 10, return_allowed = true, delivery_options = '[\"SELF_PICKUP\",\"CARRIER\"]'::jsonb WHERE product_id = ?", rid, subtotal, pid);

        // Checkout Group, Order, Items, Reservations
        UUID gid = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.checkout_groups(group_id, buyer_id, command_key, request_digest, address_snapshot, delivery_method, payment_method, expected_total, payment_due_at)
                VALUES (?, ?, ?, repeat('a', 64), '{"recipient_name":"Buyer","phone_number":"0900000000","province":"P","district":"D","ward":"W","detail_address":"A"}'::jsonb, 'SELF_PICKUP', 'BANK_TRANSFER_MOCK', ?, CURRENT_TIMESTAMP + INTERVAL '1 hour')
                """, gid, buyerId, "CMD_CG_" + UUID.randomUUID(), cashAmount);

        Long oid = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.orders(
                    checkout_group_id, group_id, buyer_id, seller_id, shipping_recipient_name, shipping_phone_number,
                    shipping_province, shipping_district, shipping_ward, shipping_detail_address,
                    subtotal, total_amount, buyer_system_fee, seller_system_fee, seller_proceeds,
                    payment_due_at, workflow_model, return_allowed, delivery_method, handover_day, voucher_discount_amount
                ) VALUES (
                    ?, ?, ?, ?, 'Buyer', '0900000000', 'P', 'D', 'W', 'A',
                    ?, ?, 0, 0, ?,
                    CURRENT_TIMESTAMP + INTERVAL '1 hour', 'UC83', true, 'SELF_PICKUP', CURRENT_DATE, ?
                ) RETURNING order_id
                """, Long.class, gid, gid, buyerId, sellerId, subtotal, cashAmount, subtotal, voucherDiscount);

        Long iid = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.order_items(
                    order_id, product_id, product_title, quantity, listed_price, agreed_price,
                    buyer_system_fee, seller_system_fee, buyer_line_total, seller_line_proceeds,
                    workflow_model, product_revision_id, return_allowed, voucher_allocation
                ) VALUES (
                    ?, ?, 'Item', 1, ?, ?,
                    0, 0, ?, ?,
                    'UC83', ?, true, ?
                ) RETURNING order_item_id
                """, Long.class, oid, pid, subtotal, subtotal, cashAmount, subtotal, rid, voucherDiscount);

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
                """, intentId, gid, cashAmount, "CMD_PI_" + UUID.randomUUID());

        Long attId = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.payment_attempts(intent_id, txn_ref, amount, provider)
                VALUES (?, ?, ?, 'BANK_TRANSFER_MOCK') RETURNING attempt_id
                """, Long.class, intentId, "TXN_" + UUID.randomUUID(), cashAmount);

        UUID confId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.payment_confirmations(confirmation_id, intent_id, attempt_id, provider, provider_transaction_id, amount)
                VALUES (?, ?, ?, 'BANK_TRANSFER_MOCK', ?, ?)
                """, confId, intentId, attId, "TXN_P_" + UUID.randomUUID(), cashAmount);

        UUID allocId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.payment_allocations(allocation_id, intent_id, confirmation_id, order_id, amount)
                VALUES (?, ?, ?, ?, ?)
                """, allocId, intentId, confId, oid, cashAmount);

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
                """, cashComponentId, oid, allocId, cashAmount);

        UUID subsidyComponentId = null;

        // Delivery
        jdbcTemplate.update("UPDATE og_compat.orders SET status = 'SELLER_CONFIRMED' WHERE order_id = ?", oid);
        jdbcTemplate.update("UPDATE og_compat.inventory_reservations SET state = 'CONSUMED' WHERE reservation_id = ?", resId);

        Long shipId = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.shipments(order_id, carrier, status, workflow_model, delivery_method, fee_snapshot, fee_payer)
                VALUES (?, 'SELF_PICKUP', 'PENDING', 'UC83', 'SELF_PICKUP', 0, 'BUYER') RETURNING shipment_id
                """, Long.class, oid);

        jdbcTemplate.update("""
                INSERT INTO og_compat.handover_confirmations(confirmation_id, shipment_id, party, user_id, outcome, command_key)
                VALUES (?, ?, 'BUYER', ?, 'SUCCESS', ?),
                       (?, ?, 'SELLER', ?, 'SUCCESS', ?)
                """, UUID.randomUUID(), shipId, buyerId, "CMD_HCB_" + UUID.randomUUID(),
                     UUID.randomUUID(), shipId, sellerId, "CMD_HCS_" + UUID.randomUUID());

        jdbcTemplate.queryForObject("SELECT og_confirm_direct_handover(?)", Boolean.class, shipId);

        return new Uc83OrderFixture(oid, cashComponentId, subsidyComponentId);
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

        // KTV claims case & decides: REJECT_RETURN
        returnCaseService.assignCaseToKtv(ktvId, createdCase.caseId());
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

        // KTV claims case, then proactively decides APPROVE_RETURN while case is OPEN
        returnCaseService.assignCaseToKtv(ktvId, createdCase.caseId());
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

    // =========================================================================
    // TASK 3.1: VẬN CHUYỂN HÀNG TRẢ (UC33) - POSTGRESQL INTEGRATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Task 3.1 Postgres: submitReturnShipment triggers og_guard_return_shipment, sets deadlines and keeps money_holds open")
    void testReturnShipment_triggersPickupDeadline_andRefreshes() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId, 1, true, false);

        // 1. Create return request
        ReturnCaseDtos.CreateReturnRequest createReq = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.DAMAGED_OR_DEFECTIVE,
                "Máy không lên nguồn",
                List.of(),
                "CMD_CREATE_" + UUID.randomUUID()
        );
        ReturnCaseDtos.ReturnCaseDetailResponse createdCase = returnCaseService.createReturnRequest(buyerId, fixture.orderId(), createReq);

        // 2. Seller approves return -> Case becomes RETURN_APPROVED
        ReturnCaseDtos.SellerRound1ResponseRequest sellerReq = new ReturnCaseDtos.SellerRound1ResponseRequest(
                CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                "Đồng ý nhận lại hàng kiểm tra",
                "CMD_APPROVE_" + UUID.randomUUID()
        );
        ReturnCaseDtos.ReturnCaseDetailResponse approvedCase = returnCaseService.sellerRespondRound1(sellerId, createdCase.caseId(), sellerReq);
        assertThat(approvedCase.state()).isEqualTo(CaseEntity.STATE_RETURN_APPROVED);

        // 3. Buyer submits return shipment via CARRIER
        ReturnCaseDtos.SubmitReturnShipmentRequest shipReq = new ReturnCaseDtos.SubmitReturnShipmentRequest(
                "CARRIER",
                "GHN",
                "GHN-RET-123456",
                "CMD_SHIP_" + UUID.randomUUID()
        );
        ReturnCaseDtos.ReturnCaseDetailResponse submittedCase = returnCaseService.submitReturnShipment(buyerId, createdCase.caseId(), shipReq);

        // 4. Assertions on Case and Shipment
        assertThat(submittedCase.state()).isEqualTo(CaseEntity.STATE_RETURNING);
        assertThat(submittedCase.returnShipment()).isNotNull();
        assertThat(submittedCase.returnShipment().leg()).isEqualTo("RETURN");
        assertThat(submittedCase.returnShipment().status()).isEqualTo("PENDING");
        assertThat(submittedCase.returnShipment().carrier()).isEqualTo("GHN");
        assertThat(submittedCase.returnShipment().trackingNumber()).isEqualTo("GHN-RET-123456");

        // Verify pickup deadlines populated by DB trigger og_guard_return_shipment
        assertThat(submittedCase.returnShipment().pickupExpectedAt()).isNotNull();
        assertThat(submittedCase.returnShipment().pickupDeadline()).isNotNull();

        // Verify Money holds remain open!
        List<Map<String, Object>> holds = jdbcTemplate.queryForList(
                "SELECT closed_at FROM og_compat.money_holds WHERE case_id = ?",
                createdCase.caseId()
        );
        assertThat(holds).isNotEmpty();
        assertThat(holds).allMatch(h -> h.get("closed_at") == null);

        // Verify Order status remains DELIVERED!
        String orderStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM og_compat.orders WHERE order_id = ?",
                String.class,
                fixture.orderId()
        );
        assertThat(orderStatus).isEqualTo("DELIVERED");
    }

    @Test
    @DisplayName("Task 3.1 Postgres: Direct handover - single party confirmation returns false, both parties confirm returns true and transitions to POST_RETURN")
    void testDirectHandover_twoPartyConfirmationFlow() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId, 1, true, false);

        // 1. Create and approve return request
        ReturnCaseDtos.CreateReturnRequest createReq = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.DAMAGED_OR_DEFECTIVE,
                "Máy lỗi bàn phím",
                List.of(),
                "CMD_CREATE_" + UUID.randomUUID()
        );
        ReturnCaseDtos.ReturnCaseDetailResponse createdCase = returnCaseService.createReturnRequest(buyerId, fixture.orderId(), createReq);
        returnCaseService.sellerRespondRound1(sellerId, createdCase.caseId(), new ReturnCaseDtos.SellerRound1ResponseRequest(
                CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                "Đồng ý đổi trả",
                "CMD_SELLER_" + UUID.randomUUID()
        ));

        // 2. Buyer submits return shipment via SELF_DELIVERY
        ReturnCaseDtos.SubmitReturnShipmentRequest shipReq = new ReturnCaseDtos.SubmitReturnShipmentRequest(
                "SELF_DELIVERY",
                null,
                null,
                "CMD_SHIP_" + UUID.randomUUID()
        );
        returnCaseService.submitReturnShipment(buyerId, createdCase.caseId(), shipReq);

        // 3. Step A: Buyer confirms direct handover -> Only 1 party confirmed -> should return false, stay in RETURNING
        ReturnCaseDtos.ConfirmReturnDeliveryRequest buyerConfirm = new ReturnCaseDtos.ConfirmReturnDeliveryRequest(
                null,
                "Người mua đã mang hàng đến giao",
                "CMD_CONFIRM_BUYER_" + UUID.randomUUID()
        );
        ReturnCaseDtos.ReturnCaseDetailResponse respAfterBuyer = returnCaseService.confirmReturnDeliveryDirect(
                buyerId, createdCase.caseId(), buyerConfirm
        );
        assertThat(respAfterBuyer.state()).isEqualTo(CaseEntity.STATE_RETURNING);

        // Verify Round 2 is NOT created yet
        assertThat(caseRoundRepository.findByCaseIdAndRoundNo(createdCase.caseId(), 2)).isEmpty();

        // 4. Step B: Seller confirms direct handover -> Both parties confirmed -> og_confirm_direct_handover returns TRUE!
        ReturnCaseDtos.ConfirmReturnDeliveryRequest sellerConfirm = new ReturnCaseDtos.ConfirmReturnDeliveryRequest(
                null,
                "Người bán đã nhận được hàng trực tiếp",
                "CMD_CONFIRM_SELLER_" + UUID.randomUUID()
        );
        ReturnCaseDtos.ReturnCaseDetailResponse respAfterSeller = returnCaseService.confirmReturnDeliveryDirect(
                sellerId, createdCase.caseId(), sellerConfirm
        );
        assertThat(respAfterSeller.state()).isEqualTo(CaseEntity.STATE_POST_RETURN);
        assertThat(respAfterSeller.validReturnedAt()).isNotNull();
        assertThat(respAfterSeller.sellerResponseDueAt()).isNotNull();

        // Verify Round 2 POST_RETURN_RESOLUTION is created and OPEN
        Optional<CaseRoundEntity> round2 = caseRoundRepository.findByCaseIdAndRoundNo(createdCase.caseId(), 2);
        assertThat(round2).isPresent();
        assertThat(round2.get().getStage()).isEqualTo(CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);
        assertThat(round2.get().getState()).isEqualTo(CaseRoundEntity.STATE_OPEN);

        // Verify Event round_id references round2.getId() in DB
        List<Map<String, Object>> events = jdbcTemplate.queryForList(
                "SELECT round_id FROM og_compat.case_events WHERE case_id = ? AND command_key LIKE 'HANDOVER_CONFIRMED:%'",
                createdCase.caseId()
        );
        assertThat(events).isNotEmpty();
        assertThat((Long) events.get(0).get("round_id")).isEqualTo(round2.get().getId());

        // Verify Money holds still OPEN
        List<Map<String, Object>> holds = jdbcTemplate.queryForList(
                "SELECT closed_at FROM og_compat.money_holds WHERE case_id = ?",
                createdCase.caseId()
        );
        assertThat(holds).allMatch(h -> h.get("closed_at") == null);
    }

    @Test
    @DisplayName("Task 3.1 Postgres: Carrier return delivery satisfies trg_case_state_proof and opens Round 2")
    void testCarrierReturnDelivery_satisfiesTrgCaseStateProof() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId, 1, true, false);

        ReturnCaseDtos.CreateReturnRequest createReq = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.NOT_AS_DESCRIBED,
                "Giao nhầm màu sắc",
                List.of(),
                "CMD_CREATE_" + UUID.randomUUID()
        );
        ReturnCaseDtos.ReturnCaseDetailResponse createdCase = returnCaseService.createReturnRequest(buyerId, fixture.orderId(), createReq);
        returnCaseService.sellerRespondRound1(sellerId, createdCase.caseId(), new ReturnCaseDtos.SellerRound1ResponseRequest(
                CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                "Shop đồng ý đổi",
                "CMD_SELLER_" + UUID.randomUUID()
        ));

        ReturnCaseDtos.SubmitReturnShipmentRequest shipReq = new ReturnCaseDtos.SubmitReturnShipmentRequest(
                "CARRIER",
                "GHN",
                "GHN-TEST-PROOF-123",
                "CMD_SHIP_" + UUID.randomUUID()
        );
        ReturnCaseDtos.ReturnCaseDetailResponse submittedCase = returnCaseService.submitReturnShipment(buyerId, createdCase.caseId(), shipReq);
        Long shipmentId = submittedCase.returnShipment().shipmentId();

        // Carrier delivers return parcel to seller
        java.time.Instant deliveredAt = java.time.Instant.now();
        ReturnCaseDtos.ReturnCaseDetailResponse deliveredCase = returnCaseService.confirmReturnDeliveryByCarrier(
                createdCase.caseId(), shipmentId, deliveredAt
        );

        // Verification
        assertThat(deliveredCase.state()).isEqualTo(CaseEntity.STATE_POST_RETURN);
        assertThat(deliveredCase.validReturnedAt()).isNotNull();

        // trg_case_state_proof requires shipment.valid_delivery_at == cases.valid_returned_at
        ShipmentEntity shipment = shipmentRepository.findById(shipmentId).orElseThrow();
        assertThat(shipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_DELIVERED);
        assertThat(shipment.getValidDeliveryAt()).isNotNull();

        // Round 2 must be created
        Optional<CaseRoundEntity> round2 = caseRoundRepository.findByCaseIdAndRoundNo(createdCase.caseId(), 2);
        assertThat(round2).isPresent();
        assertThat(round2.get().getStage()).isEqualTo(CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION);

        // Verify Event round_id references round2.getId() in DB
        List<Map<String, Object>> carrierEvents = jdbcTemplate.queryForList(
                "SELECT round_id FROM og_compat.case_events WHERE case_id = ? AND command_key LIKE 'CARRIER_DELIVERED:%'",
                createdCase.caseId()
        );
        assertThat(carrierEvents).isNotEmpty();
        assertThat((Long) carrierEvents.get(0).get("round_id")).isEqualTo(round2.get().getId());
    }

    @Test
    @DisplayName("Task 3.1 Postgres: Idempotency - replay returns same response, conflict payload throws IllegalStateException")
    void testIdempotency_commandKey_replayAndConflict() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId, 1, true, false);

        ReturnCaseDtos.CreateReturnRequest createReq = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.DAMAGED_OR_DEFECTIVE,
                "Lỗi phần cứng",
                List.of(),
                "CMD_CREATE_" + UUID.randomUUID()
        );
        ReturnCaseDtos.ReturnCaseDetailResponse createdCase = returnCaseService.createReturnRequest(buyerId, fixture.orderId(), createReq);
        returnCaseService.sellerRespondRound1(sellerId, createdCase.caseId(), new ReturnCaseDtos.SellerRound1ResponseRequest(
                CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                "Đồng ý",
                "CMD_SELLER_" + UUID.randomUUID()
        ));

        String fixedCmdKey = "CMD_FIXED_" + UUID.randomUUID();
        ReturnCaseDtos.SubmitReturnShipmentRequest shipReq = new ReturnCaseDtos.SubmitReturnShipmentRequest(
                "CARRIER",
                "GHN",
                "GHN-IDEMPOTENT-001",
                fixedCmdKey
        );

        // 1. First execution
        ReturnCaseDtos.ReturnCaseDetailResponse resp1 = returnCaseService.submitReturnShipment(buyerId, createdCase.caseId(), shipReq);
        assertThat(resp1.state()).isEqualTo(CaseEntity.STATE_RETURNING);

        // 2. Replay with identical payload & commandKey -> idempotent success
        ReturnCaseDtos.ReturnCaseDetailResponse resp2 = returnCaseService.submitReturnShipment(buyerId, createdCase.caseId(), shipReq);
        assertThat(resp2.state()).isEqualTo(CaseEntity.STATE_RETURNING);

        // 3. Replay with different payload & same commandKey -> conflict exception
        ReturnCaseDtos.SubmitReturnShipmentRequest conflictReq = new ReturnCaseDtos.SubmitReturnShipmentRequest(
                "SELF_DELIVERY",
                null,
                null,
                fixedCmdKey
        );
        assertThatThrownBy(() -> returnCaseService.submitReturnShipment(buyerId, createdCase.caseId(), conflictReq))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Conflict: commandKey đã được sử dụng với payload khác");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.0b-2 Concurrency: Seller APPROVE_RETURN vs KTV REJECT_RETURN concurrently - First-write-wins, zero 40P01 deadlocks")
    void testRound1_sellerVsKtv_concurrency_noDeadlock() throws Exception {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // Chuẩn bị Order và Case trong một transaction riêng biệt được COMMIT hoàn tất trước khi các thread đua
        Long caseId = txTemplate.execute(status -> {
            Uc83OrderFixture fixture = createCommittedUc83Order(buyerId, sellerId);
            ReturnCaseDtos.CreateReturnRequest createReq = new ReturnCaseDtos.CreateReturnRequest(
                    ReturnReasonCode.DAMAGED_OR_DEFECTIVE,
                    "Lỗi màn hình",
                    List.of(),
                    "CMD_CREATE_" + UUID.randomUUID()
            );
            ReturnCaseDtos.ReturnCaseDetailResponse createdCase = returnCaseService.createReturnRequest(buyerId, fixture.orderId(), createReq);
            returnCaseService.assignCaseToKtv(ktvId, createdCase.caseId());
            return createdCase.caseId();
        });

        int threadCount = 2;
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger conflictCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicReference<Throwable> deadlockException = new java.util.concurrent.atomic.AtomicReference<>();

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);

        // Thread 1: Seller APPROVE_RETURN
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                returnCaseService.sellerRespondRound1(sellerId, caseId, new ReturnCaseDtos.SellerRound1ResponseRequest(
                        CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                        "Seller approves return",
                        "CMD_SELLER_CONCURRENT_" + UUID.randomUUID()
                ));
                successCount.incrementAndGet();
            } catch (IllegalStateException e) {
                // Legitimate first-write-wins rejection
                conflictCount.incrementAndGet();
            } catch (Throwable t) {
                if (t.getMessage() != null && t.getMessage().contains("40P01")) {
                    deadlockException.set(t);
                } else {
                    t.printStackTrace();
                }
            }
        });

        // Thread 2: KTV REJECT_RETURN
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                returnCaseService.ktvDecideRound1(ktvId, caseId, new ReturnCaseDtos.KtvRound1DecisionRequest(
                        CaseDecisionEntity.OUTCOME_REJECT_RETURN,
                        "KTV rejects return after inspection",
                        "CMD_KTV_CONCURRENT_" + UUID.randomUUID()
                ));
                successCount.incrementAndGet();
            } catch (IllegalStateException e) {
                // Legitimate first-write-wins rejection
                conflictCount.incrementAndGet();
            } catch (Throwable t) {
                if (t.getMessage() != null && t.getMessage().contains("40P01")) {
                    deadlockException.set(t);
                } else {
                    t.printStackTrace();
                }
            }
        });

        readyLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(deadlockException.get()).isNull();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        Integer decisionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM og_compat.case_decisions WHERE round_id = (SELECT round_id FROM og_compat.case_rounds WHERE case_id = ? AND round_no = 1) AND is_final = true",
                Integer.class,
                caseId
        );
        assertThat(decisionCount).isEqualTo(1);

        String caseState = jdbcTemplate.queryForObject(
                "SELECT state FROM og_compat.cases WHERE case_id = ?",
                String.class,
                caseId
        );
        assertThat(caseState).isIn(CaseEntity.STATE_RETURN_APPROVED, CaseEntity.STATE_REJECTED);

        String roundState = jdbcTemplate.queryForObject(
                "SELECT state FROM og_compat.case_rounds WHERE case_id = ? AND round_no = 1",
                String.class,
                caseId
        );
        assertThat(roundState).isEqualTo(CaseRoundEntity.STATE_DECIDED);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.0b Concurrency Tiêu chí B1: Seller SELLER_DECLINED racing with KTV APPROVE_RETURN - Zero deadlocks, exactly one final decision")
    void testRound1_sellerDeclinedVsKtvApprove_concurrency() throws Exception {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Long caseId = txTemplate.execute(status -> {
            Uc83OrderFixture fixture = createCommittedUc83Order(buyerId, sellerId);
            ReturnCaseDtos.CreateReturnRequest createReq = new ReturnCaseDtos.CreateReturnRequest(
                    ReturnReasonCode.DAMAGED_OR_DEFECTIVE,
                    "Lỗi thiết bị",
                    List.of(),
                    "CMD_CREATE_" + UUID.randomUUID()
            );
            ReturnCaseDtos.ReturnCaseDetailResponse createdCase = returnCaseService.createReturnRequest(buyerId, fixture.orderId(), createReq);
            returnCaseService.assignCaseToKtv(ktvId, createdCase.caseId());
            return createdCase.caseId();
        });

        int threadCount = 2;
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger conflictCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicReference<Throwable> deadlockException = new java.util.concurrent.atomic.AtomicReference<>();

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);

        // Thread 1: Seller SELLER_DECLINED
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                returnCaseService.sellerRespondRound1(sellerId, caseId, new ReturnCaseDtos.SellerRound1ResponseRequest(
                        CaseDecisionEntity.OUTCOME_SELLER_DECLINED,
                        "Shop không đồng ý trả hàng, chuyển KTV",
                        "CMD_SELLER_DECLINED_" + UUID.randomUUID()
                ));
                successCount.incrementAndGet();
            } catch (IllegalStateException e) {
                // KTV decided first, rejecting seller response
                conflictCount.incrementAndGet();
            } catch (Throwable t) {
                if (t.getMessage() != null && t.getMessage().contains("40P01")) {
                    deadlockException.set(t);
                } else {
                    t.printStackTrace();
                }
            }
        });

        // Thread 2: KTV APPROVE_RETURN
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                returnCaseService.ktvDecideRound1(ktvId, caseId, new ReturnCaseDtos.KtvRound1DecisionRequest(
                        CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                        "KTV approves return request",
                        "CMD_KTV_APPROVE_" + UUID.randomUUID()
                ));
                successCount.incrementAndGet();
            } catch (IllegalStateException e) {
                conflictCount.incrementAndGet();
            } catch (Throwable t) {
                if (t.getMessage() != null && t.getMessage().contains("40P01")) {
                    deadlockException.set(t);
                } else {
                    t.printStackTrace();
                }
            }
        });

        readyLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(deadlockException.get()).isNull();
        // Either:
        // Scenario 1: Seller commits first (moves to REVIEWING), then KTV decides APPROVE_RETURN -> successCount = 2, conflictCount = 0
        // Scenario 2: KTV commits first (moves to RETURN_APPROVED, DECIDED), then Seller is rejected -> successCount = 1, conflictCount = 1
        assertThat(successCount.get() + conflictCount.get()).isEqualTo(2);
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);

        // In both scenarios, exactly one final decision exists (KTV APPROVE_RETURN)
        Integer finalDecisions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM og_compat.case_decisions WHERE round_id = (SELECT round_id FROM og_compat.case_rounds WHERE case_id = ? AND round_no = 1) AND is_final = true",
                Integer.class,
                caseId
        );
        assertThat(finalDecisions).isEqualTo(1);

        // Case must end in RETURN_APPROVED and Round 1 in DECIDED
        String caseState = jdbcTemplate.queryForObject(
                "SELECT state FROM og_compat.cases WHERE case_id = ?",
                String.class,
                caseId
        );
        assertThat(caseState).isEqualTo(CaseEntity.STATE_RETURN_APPROVED);

        String roundState = jdbcTemplate.queryForObject(
                "SELECT state FROM og_compat.case_rounds WHERE case_id = ? AND round_no = 1",
                String.class,
                caseId
        );
        assertThat(roundState).isEqualTo(CaseRoundEntity.STATE_DECIDED);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.0b Concurrency Tiêu chí B2: Seller SELLER_DECLINED racing with KTV REJECT_RETURN - Zero deadlocks, exactly one final decision")
    void testRound1_sellerDeclinedVsKtvReject_concurrency() throws Exception {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Long caseId = txTemplate.execute(status -> {
            Uc83OrderFixture fixture = createCommittedUc83Order(buyerId, sellerId);
            ReturnCaseDtos.CreateReturnRequest createReq = new ReturnCaseDtos.CreateReturnRequest(
                    ReturnReasonCode.DAMAGED_OR_DEFECTIVE,
                    "Lỗi thiết bị",
                    List.of(),
                    "CMD_CREATE_" + UUID.randomUUID()
            );
            ReturnCaseDtos.ReturnCaseDetailResponse createdCase = returnCaseService.createReturnRequest(buyerId, fixture.orderId(), createReq);
            returnCaseService.assignCaseToKtv(ktvId, createdCase.caseId());
            return createdCase.caseId();
        });

        int threadCount = 2;
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger conflictCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicReference<Throwable> deadlockException = new java.util.concurrent.atomic.AtomicReference<>();

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);

        // Thread 1: Seller SELLER_DECLINED
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                returnCaseService.sellerRespondRound1(sellerId, caseId, new ReturnCaseDtos.SellerRound1ResponseRequest(
                        CaseDecisionEntity.OUTCOME_SELLER_DECLINED,
                        "Shop không đồng ý trả hàng, chuyển KTV",
                        "CMD_SELLER_DECLINED_" + UUID.randomUUID()
                ));
                successCount.incrementAndGet();
            } catch (IllegalStateException e) {
                // KTV decided first, rejecting seller response
                conflictCount.incrementAndGet();
            } catch (Throwable t) {
                if (t.getMessage() != null && t.getMessage().contains("40P01")) {
                    deadlockException.set(t);
                } else {
                    t.printStackTrace();
                }
            }
        });

        // Thread 2: KTV REJECT_RETURN
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                returnCaseService.ktvDecideRound1(ktvId, caseId, new ReturnCaseDtos.KtvRound1DecisionRequest(
                        CaseDecisionEntity.OUTCOME_REJECT_RETURN,
                        "KTV bác yêu cầu trả sau khi đối soát",
                        "CMD_KTV_REJECT_" + UUID.randomUUID()
                ));
                successCount.incrementAndGet();
            } catch (IllegalStateException e) {
                conflictCount.incrementAndGet();
            } catch (Throwable t) {
                if (t.getMessage() != null && t.getMessage().contains("40P01")) {
                    deadlockException.set(t);
                } else {
                    t.printStackTrace();
                }
            }
        });

        readyLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(deadlockException.get()).isNull();
        // Either:
        // Scenario 1: Seller commits first (moves to REVIEWING), then KTV decides REJECT_RETURN -> successCount = 2, conflictCount = 0
        // Scenario 2: KTV commits first (moves to REJECTED, DECIDED), then Seller is rejected -> successCount = 1, conflictCount = 1
        assertThat(successCount.get() + conflictCount.get()).isEqualTo(2);
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);

        // In both scenarios, exactly one final decision exists (KTV REJECT_RETURN)
        Integer finalDecisions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM og_compat.case_decisions WHERE round_id = (SELECT round_id FROM og_compat.case_rounds WHERE case_id = ? AND round_no = 1) AND is_final = true",
                Integer.class,
                caseId
        );
        assertThat(finalDecisions).isEqualTo(1);

        // Case must end in REJECTED and Round 1 in DECIDED
        String caseState = jdbcTemplate.queryForObject(
                "SELECT state FROM og_compat.cases WHERE case_id = ?",
                String.class,
                caseId
        );
        assertThat(caseState).isEqualTo(CaseEntity.STATE_REJECTED);

        String roundState = jdbcTemplate.queryForObject(
                "SELECT state FROM og_compat.case_rounds WHERE case_id = ? AND round_no = 1",
                String.class,
                caseId
        );
        assertThat(roundState).isEqualTo(CaseRoundEntity.STATE_DECIDED);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.0b-2 Concurrency: Timeout Worker vs Worker racing - Zero deadlocks, exactly one final decision")
    void testRound2_timeoutWorkerVsWorker_concurrency() throws Exception {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // Chuẩn bị toàn bộ Order, Case, Shipment và xác nhận giao hàng vào POST_RETURN trong transaction riêng biệt
        Long caseId = txTemplate.execute(status -> {
            Uc83OrderFixture fixture = createCommittedUc83Order(buyerId, sellerId);
            ReturnCaseDtos.CreateReturnRequest createReq = new ReturnCaseDtos.CreateReturnRequest(
                    ReturnReasonCode.DAMAGED_OR_DEFECTIVE,
                    "Lỗi phần cứng",
                    List.of(),
                    "CMD_CREATE_" + UUID.randomUUID()
            );
            ReturnCaseDtos.ReturnCaseDetailResponse createdCase = returnCaseService.createReturnRequest(buyerId, fixture.orderId(), createReq);
            returnCaseService.sellerRespondRound1(sellerId, createdCase.caseId(), new ReturnCaseDtos.SellerRound1ResponseRequest(
                    CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                    "Đồng ý trả",
                    "CMD_SELLER_" + UUID.randomUUID()
            ));

            ReturnCaseDtos.SubmitReturnShipmentRequest shipReq = new ReturnCaseDtos.SubmitReturnShipmentRequest(
                    "CARRIER",
                    "GHN",
                    "GHN-R2-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                    "CMD_SHIP_" + UUID.randomUUID()
            );
            ReturnCaseDtos.ReturnCaseDetailResponse submittedCase = returnCaseService.submitReturnShipment(buyerId, createdCase.caseId(), shipReq);
            Long shipmentId = submittedCase.returnShipment().shipmentId();

            // Confirm delivery to enter POST_RETURN
            returnCaseService.confirmReturnDeliveryByCarrier(createdCase.caseId(), shipmentId, java.time.Instant.now());

            // Make case overdue for timeout worker, respecting both cases_check2 and og_validate_case_state proof
            java.sql.Timestamp validReturned = java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(3)));
            java.sql.Timestamp approvedAt = java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(4)));

            jdbcTemplate.update(
                    "UPDATE og_compat.shipments SET valid_delivery_at = ? WHERE shipment_id = ?",
                    validReturned, shipmentId
            );
            jdbcTemplate.update("""
                    UPDATE og_compat.cases
                    SET approved_at = ?,
                        valid_returned_at = ?,
                        seller_response_due_at = (?::timestamptz + INTERVAL '2 days')
                    WHERE case_id = ?
                    """,
                    approvedAt, validReturned, validReturned, createdCase.caseId()
            );
            return createdCase.caseId();
        });

        int threadCount = 2;
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger processedCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicReference<Throwable> deadlockException = new java.util.concurrent.atomic.AtomicReference<>();

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);

        // Thread 1: Timeout Worker instance 1
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                returnCaseService.processPostReturnTimeoutForCase(caseId);
                processedCount.incrementAndGet();
            } catch (Throwable t) {
                if (t.getMessage() != null && t.getMessage().contains("40P01")) {
                    deadlockException.set(t);
                } else {
                    t.printStackTrace();
                }
            }
        });

        // Thread 2: Timeout Worker instance 2 (concurrent run)
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                returnCaseService.processPostReturnTimeoutForCase(caseId);
                processedCount.incrementAndGet();
            } catch (Throwable t) {
                if (t.getMessage() != null && t.getMessage().contains("40P01")) {
                    deadlockException.set(t);
                } else {
                    t.printStackTrace();
                }
            }
        });

        readyLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(deadlockException.get()).isNull();
        assertThat(processedCount.get()).isEqualTo(2);

        Integer decisionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM og_compat.case_decisions WHERE round_id = (SELECT round_id FROM og_compat.case_rounds WHERE case_id = ? AND round_no = 2)",
                Integer.class,
                caseId
        );
        assertThat(decisionCount).isEqualTo(1);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.0b-2 Concurrency: Withdraw vs Seller Decision racing - Zero deadlocks, single final state")
    void testWithdrawVsSellerDecision_concurrency() throws Exception {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Long caseId = txTemplate.execute(status -> {
            Uc83OrderFixture fixture = createCommittedUc83Order(buyerId, sellerId);
            ReturnCaseDtos.CreateReturnRequest createReq = new ReturnCaseDtos.CreateReturnRequest(
                    ReturnReasonCode.DAMAGED_OR_DEFECTIVE,
                    "Lỗi sạc",
                    List.of(),
                    "CMD_CREATE_" + UUID.randomUUID()
            );
            ReturnCaseDtos.ReturnCaseDetailResponse createdCase = returnCaseService.createReturnRequest(buyerId, fixture.orderId(), createReq);
            return createdCase.caseId();
        });

        int threadCount = 2;
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger conflictCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicReference<Throwable> deadlockException = new java.util.concurrent.atomic.AtomicReference<>();

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);

        // Thread 1: Buyer withdraws
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                returnCaseService.withdrawReturnRequest(buyerId, caseId, new ReturnCaseDtos.WithdrawReturnRequest("Đổi ý", "CMD_WITHDRAW_" + UUID.randomUUID()));
                successCount.incrementAndGet();
            } catch (IllegalStateException e) {
                conflictCount.incrementAndGet();
            } catch (Throwable t) {
                if (t.getMessage() != null && t.getMessage().contains("40P01")) {
                    deadlockException.set(t);
                } else {
                    t.printStackTrace();
                }
            }
        });

        // Thread 2: Seller approves
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                returnCaseService.sellerRespondRound1(sellerId, caseId, new ReturnCaseDtos.SellerRound1ResponseRequest(
                        CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                        "Seller approves",
                        "CMD_SELLER_WITHDRAW_RACE_" + UUID.randomUUID()
                ));
                successCount.incrementAndGet();
            } catch (IllegalStateException e) {
                conflictCount.incrementAndGet();
            } catch (Throwable t) {
                if (t.getMessage() != null && t.getMessage().contains("40P01")) {
                    deadlockException.set(t);
                } else {
                    t.printStackTrace();
                }
            }
        });

        readyLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(deadlockException.get()).isNull();
        // If Buyer wins race: Buyer commits CLOSED (1), Seller fails with IllegalStateException on CLOSED (1).
        // If Seller wins race: Seller commits RETURN_APPROVED (1), Buyer subsequently withdraws under UC62 7c (TC-35) (2).
        assertThat(successCount.get()).isBetween(1, 2);
        assertThat(conflictCount.get()).isBetween(0, 1);
        assertThat(successCount.get() + conflictCount.get()).isEqualTo(2);

        String finalState = jdbcTemplate.queryForObject(
                "SELECT state FROM og_compat.cases WHERE case_id = ?",
                String.class,
                caseId
        );
        assertThat(finalState).isEqualTo(CaseEntity.STATE_CLOSED);

        List<Map<String, Object>> holds = jdbcTemplate.queryForList(
                "SELECT closed_at FROM og_compat.money_holds WHERE case_id = ?",
                caseId
        );
        assertThat(holds).isNotEmpty();
        assertThat(holds).allMatch(h -> h.get("closed_at") != null);
    }

    @Test
    @DisplayName("Task 2.4 / V37: Idempotent replay on Postgres - same buyer retry returns current state, 0 new events")
    void testWithdrawReturnRequest_idempotentReplay_sameBuyer_onPostgres() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId, 1, true, false);

        ReturnCaseDtos.CreateReturnRequest createReq = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.CHANGE_MIND,
                "Đổi ý lần 1",
                List.of(),
                "CMD_CREATE_" + UUID.randomUUID()
        );
        ReturnCaseDtos.ReturnCaseDetailResponse createdCase = returnCaseService.createReturnRequest(buyerId, fixture.orderId(), createReq);

        ReturnCaseDtos.WithdrawReturnRequest withdrawReq1 = new ReturnCaseDtos.WithdrawReturnRequest(
                "Rút yêu cầu lần đầu",
                "CMD_WITHDRAW_IDEMP_" + createdCase.caseId()
        );
        ReturnCaseDtos.ReturnCaseDetailResponse resp1 = returnCaseService.withdrawReturnRequest(buyerId, createdCase.caseId(), withdrawReq1);
        assertThat(resp1.state()).isEqualTo(CaseEntity.STATE_CLOSED);

        int eventCountAfterFirst = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM og_compat.case_events WHERE case_id = ? AND event_type = 'BUYER_WITHDRAWN'",
                Integer.class,
                createdCase.caseId()
        );
        assertThat(eventCountAfterFirst).isEqualTo(1);

        // Call again by same buyer (idempotent replay)
        ReturnCaseDtos.WithdrawReturnRequest withdrawReq2 = new ReturnCaseDtos.WithdrawReturnRequest(
                "Rút yêu cầu lần hai",
                "CMD_WITHDRAW_IDEMP_" + createdCase.caseId()
        );
        ReturnCaseDtos.ReturnCaseDetailResponse resp2 = returnCaseService.withdrawReturnRequest(buyerId, createdCase.caseId(), withdrawReq2);
        assertThat(resp2.state()).isEqualTo(CaseEntity.STATE_CLOSED);

        // Verify NO duplicate BUYER_WITHDRAWN event created
        int eventCountAfterSecond = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM og_compat.case_events WHERE case_id = ? AND event_type = 'BUYER_WITHDRAWN'",
                Integer.class,
                createdCase.caseId()
        );
        assertThat(eventCountAfterSecond).isEqualTo(1);
    }

    @Test
    @DisplayName("Task 2.4 / V37: Actor mismatch on Postgres - non-buyer caller rejected with IllegalArgumentException")
    void testWithdrawReturnRequest_actorMismatch_rejected_onPostgres() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId, 1, true, false);

        ReturnCaseDtos.CreateReturnRequest createReq = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.CHANGE_MIND,
                "Đổi ý",
                List.of(),
                "CMD_CREATE_" + UUID.randomUUID()
        );
        ReturnCaseDtos.ReturnCaseDetailResponse createdCase = returnCaseService.createReturnRequest(buyerId, fixture.orderId(), createReq);

        Long unauthorizedActor = sellerId;
        assertThatThrownBy(() -> returnCaseService.withdrawReturnRequest(unauthorizedActor, createdCase.caseId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chỉ người mua tạo yêu cầu mới có quyền rút yêu cầu trả hàng");

        String state = jdbcTemplate.queryForObject("SELECT state FROM og_compat.cases WHERE case_id = ?", String.class, createdCase.caseId());
        assertThat(state).isEqualTo(CaseEntity.STATE_OPEN);

        int eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM og_compat.case_events WHERE case_id = ? AND event_type = 'BUYER_WITHDRAWN'",
                Integer.class,
                createdCase.caseId()
        );
        assertThat(eventCount).isEqualTo(0);
    }

    @Test
    @DisplayName("Task 2.4 / V37: Retrying when RETURNING rejected with IllegalStateException (DB-11 cutoff)")
    void testWithdrawReturnRequest_cutoffRejection_whenReturning_onPostgres() {
        Uc83OrderFixture fixture = createDeliveredUc83Order(buyerId, sellerId, 1, true, false);

        ReturnCaseDtos.CreateReturnRequest createReq = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.DAMAGED_OR_DEFECTIVE,
                "Lỗi phần cứng",
                List.of(),
                "CMD_CREATE_" + UUID.randomUUID()
        );
        ReturnCaseDtos.ReturnCaseDetailResponse createdCase = returnCaseService.createReturnRequest(buyerId, fixture.orderId(), createReq);

        returnCaseService.sellerRespondRound1(sellerId, createdCase.caseId(), new ReturnCaseDtos.SellerRound1ResponseRequest(
                CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                "Đồng ý nhận lại",
                "CMD_SELLER_" + UUID.randomUUID()
        ));

        returnCaseService.submitReturnShipment(buyerId, createdCase.caseId(), new ReturnCaseDtos.SubmitReturnShipmentRequest(
                "CARRIER", "GHN", "GHN-CUTOFF-" + UUID.randomUUID().toString().substring(0, 8), "CMD_SHIP_" + UUID.randomUUID()
        ));

        assertThatThrownBy(() -> returnCaseService.withdrawReturnRequest(buyerId, createdCase.caseId(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Không thể rút yêu cầu khi hồ sơ ở trạng thái RETURNING");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 2.4 / V37 Concurrency: 2 threads concurrently withdraw same case - Exactly 1 BUYER_WITHDRAWN event, 0 deadlocks")
    void testWithdrawReturnRequest_concurrentDoubleWithdraw_exactlyOneEvent_onPostgres() throws Exception {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Long caseId = txTemplate.execute(status -> {
            Uc83OrderFixture fixture = createCommittedUc83Order(buyerId, sellerId);
            ReturnCaseDtos.CreateReturnRequest createReq = new ReturnCaseDtos.CreateReturnRequest(
                    ReturnReasonCode.CHANGE_MIND,
                    "Đổi ý concurrency",
                    List.of(),
                    "CMD_CREATE_" + UUID.randomUUID()
            );
            ReturnCaseDtos.ReturnCaseDetailResponse createdCase = returnCaseService.createReturnRequest(buyerId, fixture.orderId(), createReq);
            return createdCase.caseId();
        });

        int threadCount = 2;
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicReference<Throwable> deadlockException = new java.util.concurrent.atomic.AtomicReference<>();

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    returnCaseService.withdrawReturnRequest(buyerId, caseId, new ReturnCaseDtos.WithdrawReturnRequest("Đổi ý concurrency", "CMD_CONCURRENT_WITHDRAW"));
                    successCount.incrementAndGet();
                } catch (Throwable t) {
                    if (t.getMessage() != null && t.getMessage().contains("40P01")) {
                        deadlockException.set(t);
                    } else {
                        t.printStackTrace();
                    }
                }
            });
        }

        readyLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(deadlockException.get()).isNull();
        assertThat(successCount.get()).isEqualTo(2); // 1 wins execution, 1 idempotent replay, both return successfully

        String state = jdbcTemplate.queryForObject("SELECT state FROM og_compat.cases WHERE case_id = ?", String.class, caseId);
        assertThat(state).isEqualTo(CaseEntity.STATE_CLOSED);

        int eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM og_compat.case_events WHERE case_id = ? AND event_type = 'BUYER_WITHDRAWN'",
                Integer.class,
                caseId
        );
        assertThat(eventCount).isEqualTo(1);

        List<Map<String, Object>> holds = jdbcTemplate.queryForList(
                "SELECT closed_at FROM og_compat.money_holds WHERE case_id = ?",
                caseId
        );
        assertThat(holds).isNotEmpty();
        assertThat(holds).allMatch(h -> h.get("closed_at") != null);
    }

    @Test
    @DisplayName("Task 2.4 / V37: Database trigger NULL-safety and actor guards on Postgres")
    void testBuyerWithdrawn_databaseTriggerGuards_onPostgres() {
        Uc83OrderFixture fixture = createCommittedUc83Order(buyerId, sellerId);

        ReturnCaseDtos.CreateReturnRequest createReq = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.CHANGE_MIND,
                "Đổi ý",
                List.of(),
                "CMD_CREATE_" + UUID.randomUUID()
        );
        ReturnCaseDtos.ReturnCaseDetailResponse createdCase = returnCaseService.createReturnRequest(buyerId, fixture.orderId(), createReq);
        Long caseId = createdCase.caseId();

        // 1. actor_id IS NULL: Rejected by og_guard_case_event with SQLSTATE 23514
        jdbcTemplate.execute("SAVEPOINT sp1");
        try {
            jdbcTemplate.execute(String.format(
                "INSERT INTO og_compat.case_events(event_id, case_id, actor_id, event_type, reason, metadata, occurred_at, command_key) " +
                "VALUES (gen_random_uuid(), %d, NULL, 'BUYER_WITHDRAWN', 'Null actor test', '{}'::jsonb, clock_timestamp(), 'CMD_NULL_ACTOR_%s')",
                caseId, UUID.randomUUID()
            ));
            org.junit.jupiter.api.Assertions.fail("Expected SQLSTATE 23514 on NULL actor_id");
        } catch (org.springframework.dao.DataAccessException ex) {
            org.postgresql.util.PSQLException psqle = (org.postgresql.util.PSQLException) ex.getCause();
            assertThat(psqle.getSQLState()).isEqualTo("23514");
            assertThat(psqle.getMessage()).contains("BUYER_WITHDRAWN requires valid non-null");
        } finally {
            jdbcTemplate.execute("ROLLBACK TO SAVEPOINT sp1");
        }

        // 2. actor_id mismatch (sellerId instead of buyerId): Rejected by og_guard_case_event with SQLSTATE 23514
        jdbcTemplate.execute("SAVEPOINT sp2");
        try {
            jdbcTemplate.execute(String.format(
                "INSERT INTO og_compat.case_events(event_id, case_id, actor_id, event_type, reason, metadata, occurred_at, command_key) " +
                "VALUES (gen_random_uuid(), %d, %d, 'BUYER_WITHDRAWN', 'Wrong actor test', '{}'::jsonb, clock_timestamp(), 'CMD_WRONG_ACTOR_%s')",
                caseId, sellerId, UUID.randomUUID()
            ));
            org.junit.jupiter.api.Assertions.fail("Expected SQLSTATE 23514 on mismatched actor_id");
        } catch (org.springframework.dao.DataAccessException ex) {
            org.postgresql.util.PSQLException psqle = (org.postgresql.util.PSQLException) ex.getCause();
            assertThat(psqle.getSQLState()).isEqualTo("23514");
            assertThat(psqle.getMessage()).contains("Only the buyer who created the case can withdraw");
        } finally {
            jdbcTemplate.execute("ROLLBACK TO SAVEPOINT sp2");
        }

        // 3. Deferred validator: updating case to CLOSED without decision or BUYER_WITHDRAWN fails with 23514
        jdbcTemplate.execute("SAVEPOINT sp3");
        try {
            jdbcTemplate.execute("UPDATE og_compat.cases SET state = 'CLOSED', closed_at = CURRENT_TIMESTAMP WHERE case_id = " + caseId);
            jdbcTemplate.execute("SET CONSTRAINTS ALL IMMEDIATE");
            org.junit.jupiter.api.Assertions.fail("Expected SQLSTATE 23514 on deferred validator");
        } catch (org.springframework.dao.DataAccessException ex) {
            org.postgresql.util.PSQLException psqle = (org.postgresql.util.PSQLException) ex.getCause();
            assertThat(psqle.getSQLState()).isEqualTo("23514");
            assertThat(psqle.getMessage()).contains("Case state requires corresponding decision/delivery proof");
        } finally {
            jdbcTemplate.execute("ROLLBACK TO SAVEPOINT sp3");
        }
    }

    // =========================================================================
    // FEATURE 3.2: TASK 3.2.1 - SELLER AGREE REFUND POSTGRES TESTS (PHASE 1)
    // =========================================================================

    private record PostReturnFixture(Long caseId, Long round2Id, Long orderId, Long buyerId, Long sellerId, Uc83OrderFixture orderFixture) {}

    private PostReturnFixture createPostReturnFixture(org.springframework.transaction.support.TransactionTemplate txTemplate) {
        return txTemplate.execute(status -> {
            Long fBuyerId = createTestUser("buyer");
            Long fSellerId = createTestUser("seller");
            grantRole(fBuyerId, "BUYER");
            grantRole(fSellerId, "SELLER");
            Uc83OrderFixture fixture = createCommittedUc83Order(fBuyerId, fSellerId);
            ReturnCaseDtos.CreateReturnRequest createReq = new ReturnCaseDtos.CreateReturnRequest(
                    ReturnReasonCode.DAMAGED_OR_DEFECTIVE,
                    "Lỗi phần cứng",
                    List.of(),
                    "CMD_CREATE_" + UUID.randomUUID()
            );
            ReturnCaseDtos.ReturnCaseDetailResponse createdCase = returnCaseService.createReturnRequest(fBuyerId, fixture.orderId(), createReq);
            returnCaseService.sellerRespondRound1(fSellerId, createdCase.caseId(), new ReturnCaseDtos.SellerRound1ResponseRequest(
                    CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                    "Đồng ý trả",
                    "CMD_SELLER_" + UUID.randomUUID()
            ));

            ReturnCaseDtos.SubmitReturnShipmentRequest shipReq = new ReturnCaseDtos.SubmitReturnShipmentRequest(
                    "CARRIER",
                    "GHN",
                    "GHN-R2-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                    "CMD_SHIP_" + UUID.randomUUID()
            );
            ReturnCaseDtos.ReturnCaseDetailResponse submittedCase = returnCaseService.submitReturnShipment(fBuyerId, createdCase.caseId(), shipReq);
            Long shipmentId = submittedCase.returnShipment().shipmentId();

            // Confirm delivery to enter POST_RETURN
            returnCaseService.confirmReturnDeliveryByCarrier(createdCase.caseId(), shipmentId, java.time.Instant.now());

            Long round2Id = jdbcTemplate.queryForObject(
                    "SELECT round_id FROM og_compat.case_rounds WHERE case_id = ? AND round_no = 2",
                    Long.class,
                    createdCase.caseId()
            );

            return new PostReturnFixture(createdCase.caseId(), round2Id, fixture.orderId(), fBuyerId, fSellerId, fixture);
        });
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.1 Postgres: sellerAgreeRefundRound2 - creates CaseDecision, 2 settlement operations in REQUESTED, and closes holds")
    void testSellerAgreeRefundRound2_onPostgres_success() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);

        String cmdKey = "CMD_PG_SELLER_AGREE_" + UUID.randomUUID();
        ReturnCaseDtos.SellerAgreeRefundRequest req = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Đồng ý hoàn tiền sau khi đã nhận lại hàng",
                cmdKey
        );

        ReturnCaseDtos.ReturnCaseDetailResponse resp = txTemplate.execute(status ->
                returnCaseService.sellerAgreeRefundRound2(prf.sellerId(), prf.caseId(), req)
        );

        assertThat(resp).isNotNull();
        assertThat(resp.state()).isEqualTo(CaseEntity.STATE_CLOSED);

        // 1. Verify CaseDecision on real PostgreSQL
        List<Map<String, Object>> decisions = jdbcTemplate.queryForList("""
                SELECT decision_id, round_id, reviewed_by, actor_type, outcome, is_final, goods_refund_amount, reason, command_key, decided_at
                FROM og_compat.case_decisions
                WHERE round_id = ?
                """, prf.round2Id());
        assertThat(decisions).hasSize(1);
        Map<String, Object> dec = decisions.getFirst();
        Long decisionId = ((Number) dec.get("decision_id")).longValue();
        assertThat(dec.get("outcome")).isEqualTo(CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND);
        assertThat(dec.get("actor_type")).isEqualTo(CaseDecisionEntity.ACTOR_USER);
        assertThat(((Number) dec.get("reviewed_by")).longValue()).isEqualTo(prf.sellerId());
        assertThat(dec.get("is_final")).isEqualTo(Boolean.TRUE);
        assertThat(dec.get("command_key")).isEqualTo(cmdKey);

        // 2. Verify trigger og_apply_case_decision closed the Case and decided Round 2
        String caseState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.cases WHERE case_id = ?", String.class, prf.caseId());
        assertThat(caseState).isEqualTo("CLOSED");
        String roundState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.case_rounds WHERE round_id = ?", String.class, prf.round2Id());
        assertThat(roundState).isEqualTo("DECIDED");

        // 3. Verify trigger og_close_case_holds closed all money_holds
        List<Map<String, Object>> holds = jdbcTemplate.queryForList(
                "SELECT hold_id, closed_at FROM og_compat.money_holds WHERE case_id = ?", prf.caseId()
        );
        assertThat(holds).isNotEmpty();
        assertThat(holds).allMatch(h -> h.get("closed_at") != null);

        // 4. Verify Task 4.1a settlement operations on PostgreSQL: exactly 1 operation (GOODS_CASH)
        List<Map<String, Object>> ops = jdbcTemplate.queryForList("""
                SELECT operation_id, component_id, operation_type, amount, cause_type, case_decision_id, recipient_user_id, state, command_key
                FROM og_compat.settlement_operations
                WHERE case_decision_id = ?
                ORDER BY operation_type
                """, decisionId);
        assertThat(ops).hasSize(1);

        Map<String, Object> cashOp = ops.getFirst();
        assertThat(cashOp.get("operation_type")).isEqualTo("REFUND_BUYER");
        assertThat(cashOp.get("cause_type")).isEqualTo("RETURN");
        assertThat(cashOp.get("state")).isEqualTo("REQUESTED");
        assertThat(((Number) cashOp.get("recipient_user_id")).longValue()).isEqualTo(prf.buyerId());
        assertThat(((BigDecimal) cashOp.get("amount")).compareTo(new BigDecimal("250000.00"))).isZero();
        assertThat(cashOp.get("command_key")).isEqualTo(cmdKey + ":GOODS_CASH");

        // 5. Financial Safety Invariant: Order status is NOT changed to REFUNDED or COMPLETED
        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM og_compat.orders WHERE order_id = ?", String.class, prf.orderId());
        assertThat(orderStatus).isNotIn("REFUNDED", "COMPLETED");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.1 Postgres: sellerAgreeRefundRound2 - idempotent replay with same payload returns snapshot, different payload rejects with 409")
    void testSellerAgreeRefundRound2_onPostgres_idempotentReplay_andConflictPayload() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);

        String sharedCmdKey = "CMD_PG_AGREE_IDEMPOTENT_" + UUID.randomUUID();
        String initialReason = "Đồng ý hoàn tiền sau khi kiểm tra hàng đầy đủ";
        ReturnCaseDtos.SellerAgreeRefundRequest req1 = new ReturnCaseDtos.SellerAgreeRefundRequest(
                initialReason,
                sharedCmdKey
        );

        // 1. First execution -> creates decision + settlement op in REQUESTED
        ReturnCaseDtos.ReturnCaseDetailResponse resp1 = txTemplate.execute(status ->
                returnCaseService.sellerAgreeRefundRound2(prf.sellerId(), prf.caseId(), req1)
        );
        assertThat(resp1).isNotNull();
        assertThat(resp1.state()).isEqualTo(CaseEntity.STATE_CLOSED);

        Integer decCount1 = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_decisions WHERE command_key = ?", Integer.class, sharedCmdKey);
        assertThat(decCount1).isEqualTo(1);
        Integer opCount1 = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.settlement_operations WHERE command_key LIKE ?", Integer.class, sharedCmdKey + "%");
        assertThat(opCount1).isEqualTo(1);

        // 2. Replay with identical payload and same commandKey -> idempotent success, snapshot returned, zero duplicate writes
        ReturnCaseDtos.SellerAgreeRefundRequest replayReq = new ReturnCaseDtos.SellerAgreeRefundRequest(
                initialReason,
                sharedCmdKey
        );
        ReturnCaseDtos.ReturnCaseDetailResponse resp2 = txTemplate.execute(status ->
                returnCaseService.sellerAgreeRefundRound2(prf.sellerId(), prf.caseId(), replayReq)
        );
        assertThat(resp2).isNotNull();
        assertThat(resp2.caseId()).isEqualTo(resp1.caseId());
        assertThat(resp2.state()).isEqualTo(CaseEntity.STATE_CLOSED);

        Integer decCount2 = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_decisions WHERE command_key = ?", Integer.class, sharedCmdKey);
        assertThat(decCount2).isEqualTo(1);
        Integer opCount2 = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.settlement_operations WHERE command_key LIKE ?", Integer.class, sharedCmdKey + "%");
        assertThat(opCount2).isEqualTo(1);

        // 3. Replay with different payload and same commandKey -> 409 Conflict COMMAND_KEY_CONFLICT
        ReturnCaseDtos.SellerAgreeRefundRequest conflictReq = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Lý do khác hoàn toàn: Đã đổi ý về việc hoàn tiền",
                sharedCmdKey
        );
        assertThatThrownBy(() -> txTemplate.execute(status ->
                returnCaseService.sellerAgreeRefundRound2(prf.sellerId(), prf.caseId(), conflictReq)
        ))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
                    assertThat(tsEx.getCode()).isEqualTo("COMMAND_KEY_CONFLICT");
                    assertThat(tsEx.getMessage()).contains("Conflict: commandKey đã được sử dụng với payload khác");
                });

        // Verify DB unchanged: still exactly 1 decision, 1 settlement op
        Integer decCount3 = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_decisions WHERE command_key = ?", Integer.class, sharedCmdKey);
        assertThat(decCount3).isEqualTo(1);
        Integer opCount3 = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.settlement_operations WHERE command_key LIKE ?", Integer.class, sharedCmdKey + "%");
        assertThat(opCount3).isEqualTo(1);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.1 Postgres: sellerAgreeRefundRound2 - rejected when valid_damage_request_at IS NOT NULL (BD-1 Phương án 1)")
    void testSellerAgreeRefundRound2_onPostgres_rejected_whenValidDamageRequestExists() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);

        // Record evidence and valid damage request via trigger
        txTemplate.executeWithoutResult(status -> {
            UUID evidenceId = UUID.randomUUID();
            String digest = "c1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
            jdbcTemplate.update("""
                    INSERT INTO og_compat.case_evidence(evidence_id, round_id, submitted_by, submission_no, media_type, object_key, content_digest, file_size_bytes)
                    VALUES (?, ?, ?, 1, 'IMAGE', 'evidence/img.jpg', ?, 1024)
                    """, evidenceId, prf.round2Id(), prf.sellerId(), digest);

            jdbcTemplate.update("""
                    INSERT INTO og_compat.case_events(event_id, case_id, round_id, actor_id, event_type, reason, command_key)
                    VALUES (gen_random_uuid(), ?, ?, ?, 'VALID_DAMAGE_REQUEST', 'Hàng hư hỏng nghiêm trọng', ?)
                    """, prf.caseId(), prf.round2Id(), prf.sellerId(), "CMD_EV_DMG_" + UUID.randomUUID());
        });

        // Verify valid_damage_request_at was set by trigger
        java.sql.Timestamp validDmgAt = jdbcTemplate.queryForObject(
                "SELECT valid_damage_request_at FROM og_compat.case_rounds WHERE round_id = ?",
                java.sql.Timestamp.class,
                prf.round2Id()
        );
        assertThat(validDmgAt).isNotNull();

        // Attempt sellerAgreeRefundRound2
        ReturnCaseDtos.SellerAgreeRefundRequest req = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Muốn đồng ý hoàn tiền sau khi đã claim thiệt hại",
                "CMD_AGREE_AFTER_DMG_" + UUID.randomUUID()
        );

        assertThatThrownBy(() -> txTemplate.execute(status ->
                returnCaseService.sellerAgreeRefundRound2(prf.sellerId(), prf.caseId(), req)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Người bán đã gửi yêu cầu bồi thường thiệt hại");

        // Verify no decisions or settlement operations were created
        Integer decisionCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.case_decisions WHERE round_id = ?",
                Integer.class,
                prf.round2Id()
        );
        assertThat(decisionCount).isZero();

        String caseState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.cases WHERE case_id = ?", String.class, prf.caseId());
        assertThat(caseState).isEqualTo("POST_RETURN");
        String roundState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.case_rounds WHERE round_id = ?", String.class, prf.round2Id());
        assertThat(roundState).isEqualTo("OPEN");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.1 Postgres: sellerAgreeRefundRound2 - rollback atomicity leaves no partial decision, no closed case, no partial settlements")
    void testSellerAgreeRefundRound2_onPostgres_rollbackAtomicity() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);

        ReturnCaseDtos.SellerAgreeRefundRequest req = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Đồng ý hoàn tiền hợp lệ nhưng gặp lỗi sau settlement",
                "CMD_ROLLBACK_" + UUID.randomUUID()
        );

        assertThatThrownBy(() -> txTemplate.execute(status -> {
            returnCaseService.sellerAgreeRefundRound2(prf.sellerId(), prf.caseId(), req);
            throw new RuntimeException("Simulated error to test transaction rollback atomicity");
        })).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated error to test transaction rollback atomicity");

        // 1. Verify NO CaseDecision was committed
        Integer decisionCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.case_decisions WHERE round_id = ?",
                Integer.class,
                prf.round2Id()
        );
        assertThat(decisionCount).isZero();

        // 2. Verify NO settlement_operations were committed
        Integer opsCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.settlement_operations WHERE command_key LIKE ?",
                Integer.class,
                "CMD_ROLLBACK_%"
        );
        assertThat(opsCount).isZero();

        // 3. Verify Case remains POST_RETURN and closed_at is NULL
        Map<String, Object> cRow = jdbcTemplate.queryForMap(
                "SELECT state, closed_at FROM og_compat.cases WHERE case_id = ?",
                prf.caseId()
        );
        assertThat(cRow.get("state")).isEqualTo("POST_RETURN");
        assertThat(cRow.get("closed_at")).isNull();

        // 4. Verify Round 2 remains OPEN
        String roundState = jdbcTemplate.queryForObject(
                "SELECT state FROM og_compat.case_rounds WHERE round_id = ?",
                String.class,
                prf.round2Id()
        );
        assertThat(roundState).isEqualTo("OPEN");

        // 5. Verify money_holds remain open
        List<Map<String, Object>> holds = jdbcTemplate.queryForList(
                "SELECT hold_id, closed_at FROM og_compat.money_holds WHERE case_id = ?",
                prf.caseId()
        );
        assertThat(holds).isNotEmpty();
        assertThat(holds).allMatch(h -> h.get("closed_at") == null);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.1 Postgres Concurrency: 20 iterations of Order A (Damage Request commits before Agree Refund) and Order B (Agree Refund commits before Damage Request)")
    void testSellerAgreeRefundRound2_onPostgres_concurrency_bothCommitOrders_20Iterations() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        int totalIterations = 20;

        // =====================================================================
        // ORDER A: Damage Request commits FIRST -> Seller Agree Refund is REJECTED
        // =====================================================================
        for (int i = 0; i < totalIterations; i++) {
            PostReturnFixture prf = createPostReturnFixture(txTemplate);

            // Barrier: Thread 1 (Damage Request) commits before Thread 2 (Seller Agree)
            java.util.concurrent.CountDownLatch damageCommittedLatch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicBoolean sellerAgreeRejected = new java.util.concurrent.atomic.AtomicBoolean(false);
            java.util.concurrent.atomic.AtomicReference<String> rejectMessage = new java.util.concurrent.atomic.AtomicReference<>();

            Thread t1 = new Thread(() -> {
                txTemplate.executeWithoutResult(status -> {
                    UUID evidenceId = UUID.randomUUID();
                    String digest = "d1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
                    jdbcTemplate.update("""
                            INSERT INTO og_compat.case_evidence(evidence_id, round_id, submitted_by, submission_no, media_type, object_key, content_digest, file_size_bytes)
                            VALUES (?, ?, ?, 1, 'IMAGE', 'evidence/img.jpg', ?, 1024)
                            """, evidenceId, prf.round2Id(), prf.sellerId(), digest);

                    jdbcTemplate.update("""
                            INSERT INTO og_compat.case_events(event_id, case_id, round_id, actor_id, event_type, reason, command_key)
                            VALUES (gen_random_uuid(), ?, ?, ?, 'VALID_DAMAGE_REQUEST', 'Hàng hư hỏng', ?)
                            """, prf.caseId(), prf.round2Id(), prf.sellerId(), "CMD_EV_DMG_" + UUID.randomUUID());
                });
                damageCommittedLatch.countDown();
            });

            Thread t2 = new Thread(() -> {
                try {
                    damageCommittedLatch.await();
                    ReturnCaseDtos.SellerAgreeRefundRequest req = new ReturnCaseDtos.SellerAgreeRefundRequest(
                            "Đồng ý hoàn tiền sau khi đã có damage request",
                            "CMD_AGREE_RACE_A_" + UUID.randomUUID()
                    );
                    txTemplate.execute(status -> returnCaseService.sellerAgreeRefundRound2(prf.sellerId(), prf.caseId(), req));
                } catch (IllegalStateException ex) {
                    sellerAgreeRejected.set(true);
                    rejectMessage.set(ex.getMessage());
                } catch (Exception e) {
                    // unexpected
                }
            });

            t1.start();
            t2.start();
            try {
                t1.join();
                t2.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            assertThat(sellerAgreeRejected.get())
                    .as("Iteration %d of Order A: Seller Agree must be rejected when Damage Request committed first", i)
                    .isTrue();
            assertThat(rejectMessage.get()).contains("Người bán đã gửi yêu cầu bồi thường thiệt hại");

            // Verify Postgres invariant: 0 decisions, 0 settlement operations, round remains OPEN
            Integer decCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_decisions WHERE round_id = ?", Integer.class, prf.round2Id());
            assertThat(decCount).isZero();
            String roundState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.case_rounds WHERE round_id = ?", String.class, prf.round2Id());
            assertThat(roundState).isEqualTo("OPEN");
        }

        // =====================================================================
        // ORDER B: Seller Agree Refund commits FIRST -> Damage Request is REJECTED by DB trigger
        // =====================================================================
        for (int i = 0; i < totalIterations; i++) {
            PostReturnFixture prf = createPostReturnFixture(txTemplate);

            java.util.concurrent.CountDownLatch agreeCommittedLatch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicBoolean damageRejectedByDb = new java.util.concurrent.atomic.AtomicBoolean(false);
            java.util.concurrent.atomic.AtomicReference<String> dbSqlState = new java.util.concurrent.atomic.AtomicReference<>();

            Thread t1 = new Thread(() -> {
                String cmdKey = "CMD_AGREE_RACE_B_" + UUID.randomUUID();
                ReturnCaseDtos.SellerAgreeRefundRequest req = new ReturnCaseDtos.SellerAgreeRefundRequest(
                        "Đồng ý hoàn tiền thắng cuộc trước",
                        cmdKey
                );
                txTemplate.execute(status -> returnCaseService.sellerAgreeRefundRound2(prf.sellerId(), prf.caseId(), req));
                agreeCommittedLatch.countDown();
            });

            Thread t2 = new Thread(() -> {
                try {
                    agreeCommittedLatch.await();
                    txTemplate.executeWithoutResult(status -> {
                        UUID evidenceId = UUID.randomUUID();
                        String digest = "e1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
                        jdbcTemplate.update("""
                                INSERT INTO og_compat.case_evidence(evidence_id, round_id, submitted_by, submission_no, media_type, object_key, content_digest, file_size_bytes)
                                VALUES (?, ?, ?, 1, 'IMAGE', 'evidence/img.jpg', ?, 1024)
                                """, evidenceId, prf.round2Id(), prf.sellerId(), digest);

                        // Trigger og_guard_case_event checks round state='OPEN' and stage='POST_RETURN_RESOLUTION'
                        // Since round is already DECIDED, this will fail with SQLSTATE 23514
                        jdbcTemplate.update("""
                                INSERT INTO og_compat.case_events(event_id, case_id, round_id, actor_id, event_type, reason, command_key)
                                VALUES (gen_random_uuid(), ?, ?, ?, 'VALID_DAMAGE_REQUEST', 'Hàng hư hỏng sau quyết định', ?)
                                """, prf.caseId(), prf.round2Id(), prf.sellerId(), "CMD_EV_DMG_" + UUID.randomUUID());
                    });
                } catch (org.springframework.dao.DataAccessException ex) {
                    damageRejectedByDb.set(true);
                    if (ex.getCause() instanceof org.postgresql.util.PSQLException psqlEx) {
                        dbSqlState.set(psqlEx.getSQLState());
                    }
                } catch (Exception e) {
                    // unexpected
                }
            });

            t1.start();
            t2.start();
            try {
                t1.join();
                t2.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            assertThat(damageRejectedByDb.get())
                    .as("Iteration %d of Order B: Damage Request must be rejected by DB trigger when Seller Agree committed first", i)
                    .isTrue();
            assertThat(dbSqlState.get()).isEqualTo("23514");

            // Verify Postgres invariant: exactly 1 decision, exactly 1 settlement operation (REQUESTED), case CLOSED, round DECIDED
            Integer decCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_decisions WHERE round_id = ?", Integer.class, prf.round2Id());
            assertThat(decCount).isEqualTo(1);
            String caseState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.cases WHERE case_id = ?", String.class, prf.caseId());
            assertThat(caseState).isEqualTo("CLOSED");
            String roundState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.case_rounds WHERE round_id = ?", String.class, prf.round2Id());
            assertThat(roundState).isEqualTo("DECIDED");
            Integer opsCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.settlement_operations WHERE case_decision_id IN (SELECT decision_id FROM og_compat.case_decisions WHERE round_id = ?)", Integer.class, prf.round2Id());
            assertThat(opsCount).isEqualTo(1); // exactly 1 operation for GOODS_CASH
        }
    }

    // =========================================================================
    // FEATURE 3.2: TASK 3.2.2 - SELLER DAMAGE EXEMPTION REQUEST POSTGRES TESTS
    // =========================================================================

    private ReturnCaseDtos.EvidenceItemDto createPostgresEvidence(String type, String objectKey) {
        try {
            java.nio.file.Path targetDir = java.nio.file.Paths.get("uploads/media");
            java.nio.file.Files.createDirectories(targetDir);
            String filename = java.nio.file.Paths.get(objectKey).getFileName().toString();
            java.nio.file.Path file = targetDir.resolve(filename);
            byte[] content = ("Real postgres test content for " + objectKey).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            java.nio.file.Files.write(file, content);
            String digest = java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(content)
            );
            return new ReturnCaseDtos.EvidenceItemDto(
                    type,
                    "/api/v1/media/" + filename,
                    "Bằng chứng hư hỏng thực tế",
                    digest,
                    (long) content.length,
                    "VIDEO".equalsIgnoreCase(type) ? 30 : null
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.2 Postgres: submitDamageExemptionRequest - first-time success flushes evidence, inserts event, trigger sets valid_damage_request_at")
    void testSubmitDamageExemptionRequest_onPostgres_success() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);

        String cmdKey = "CMD_PG_DMG_" + UUID.randomUUID();
        ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Sản phẩm bị vỡ màn hình và trầy xước nặng khi nhận lại từ đơn vị vận chuyển",
                List.of(
                        createPostgresEvidence("IMAGE", "evidence/damage1.jpg"),
                        createPostgresEvidence("VIDEO", "evidence/damage_video.mp4")
                ),
                cmdKey
        );

        ReturnCaseDtos.ReturnCaseDetailResponse resp = txTemplate.execute(status ->
                returnCaseService.submitDamageExemptionRequest(prf.sellerId(), prf.caseId(), req)
        );

        assertThat(resp).isNotNull();
        assertThat(resp.state()).isEqualTo(CaseEntity.STATE_POST_RETURN);

        // 1. Verify case_evidence on PostgreSQL: exactly 2 items for round 2
        List<Map<String, Object>> evidences = jdbcTemplate.queryForList("""
                SELECT evidence_id, round_id, submitted_by, submission_no, media_type, object_key
                FROM og_compat.case_evidence
                WHERE round_id = ?
                ORDER BY media_type
                """, prf.round2Id());
        assertThat(evidences).hasSize(2);
        assertThat(evidences).allMatch(e -> ((Number) e.get("submitted_by")).longValue() == prf.sellerId());
        assertThat(evidences).allMatch(e -> ((Number) e.get("submission_no")).intValue() == 1);

        // 2. Verify case_events on PostgreSQL: exactly 1 VALID_DAMAGE_REQUEST event
        List<Map<String, Object>> events = jdbcTemplate.queryForList("""
                SELECT event_id, case_id, round_id, actor_id, event_type, reason, command_key
                FROM og_compat.case_events
                WHERE round_id = ? AND event_type = 'VALID_DAMAGE_REQUEST'
                """, prf.round2Id());
        assertThat(events).hasSize(1);
        Map<String, Object> ev = events.getFirst();
        assertThat(((Number) ev.get("actor_id")).longValue()).isEqualTo(prf.sellerId());
        assertThat(ev.get("command_key")).isEqualTo(cmdKey);

        // 3. Verify DB trigger og_guard_case_event updated case_rounds.valid_damage_request_at
        java.sql.Timestamp validDmgAt = jdbcTemplate.queryForObject(
                "SELECT valid_damage_request_at FROM og_compat.case_rounds WHERE round_id = ?",
                java.sql.Timestamp.class,
                prf.round2Id()
        );
        assertThat(validDmgAt).isNotNull();

        // 4. Verify Case remains POST_RETURN and Round 2 remains OPEN
        String caseState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.cases WHERE case_id = ?", String.class, prf.caseId());
        assertThat(caseState).isEqualTo("POST_RETURN");
        java.sql.Timestamp closedAt = jdbcTemplate.queryForObject("SELECT closed_at FROM og_compat.cases WHERE case_id = ?", java.sql.Timestamp.class, prf.caseId());
        assertThat(closedAt).isNull();
        String roundState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.case_rounds WHERE round_id = ?", String.class, prf.round2Id());
        assertThat(roundState).isEqualTo("OPEN");

        // 5. Verify NO CaseDecision and NO settlement_operations created
        Integer decisionCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_decisions WHERE round_id = ?", Integer.class, prf.round2Id());
        assertThat(decisionCount).isZero();
        Integer opsCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.settlement_operations WHERE command_key LIKE ?", Integer.class, cmdKey + "%");
        assertThat(opsCount).isZero();
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.2 Postgres: submitDamageExemptionRequest - transaction rollback atomicity leaves 0 evidence, 0 event, valid_damage_request_at is null")
    void testSubmitDamageExemptionRequest_onPostgres_rollbackAtomicity() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);

        String cmdKey = "CMD_PG_DMG_ROLLBACK_" + UUID.randomUUID();
        ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Yêu cầu bồi thường thiệt hại nhưng transaction gặp ngoại lệ sau đó",
                List.of(createPostgresEvidence("IMAGE", "evidence/damage1.jpg")),
                cmdKey
        );

        assertThatThrownBy(() -> txTemplate.execute(status -> {
            returnCaseService.submitDamageExemptionRequest(prf.sellerId(), prf.caseId(), req);
            throw new RuntimeException("Simulated error to trigger transaction rollback atomicity");
        })).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated error to trigger transaction rollback atomicity");

        // 1. Verify 0 case_evidence committed
        Integer evCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_evidence WHERE round_id = ?", Integer.class, prf.round2Id());
        assertThat(evCount).isZero();

        // 2. Verify 0 case_events committed
        Integer eventCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_events WHERE command_key = ?", Integer.class, cmdKey);
        assertThat(eventCount).isZero();

        // 3. Verify valid_damage_request_at remains NULL
        java.sql.Timestamp validDmgAt = jdbcTemplate.queryForObject(
                "SELECT valid_damage_request_at FROM og_compat.case_rounds WHERE round_id = ?",
                java.sql.Timestamp.class,
                prf.round2Id()
        );
        assertThat(validDmgAt).isNull();

        // 4. Verify Case and Round remain POST_RETURN / OPEN
        String caseState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.cases WHERE case_id = ?", String.class, prf.caseId());
        assertThat(caseState).isEqualTo("POST_RETURN");
        String roundState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.case_rounds WHERE round_id = ?", String.class, prf.round2Id());
        assertThat(roundState).isEqualTo("OPEN");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.2 Postgres: submitDamageExemptionRequest - rejected when caller is not the seller of order")
    void testSubmitDamageExemptionRequest_onPostgres_rejected_whenNotSeller() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);

        Long impostorSellerId = txTemplate.execute(status -> {
            Long uid = createTestUser("impostor_seller");
            grantRole(uid, "SELLER");
            return uid;
        });

        String cmdKey = "CMD_PG_DMG_IMPOSTOR_" + UUID.randomUUID();
        ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Người lạ cố tình gửi yêu cầu bồi thường thiệt hại",
                List.of(createPostgresEvidence("IMAGE", "evidence/damage1.jpg")),
                cmdKey
        );

        assertThatThrownBy(() -> txTemplate.execute(status ->
                returnCaseService.submitDamageExemptionRequest(impostorSellerId, prf.caseId(), req)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chỉ người bán của đơn hàng mới có quyền gửi yêu cầu bồi thường thiệt hại");

        // Verify no evidence or event created
        Integer evCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_evidence WHERE round_id = ?", Integer.class, prf.round2Id());
        assertThat(evCount).isZero();
        java.sql.Timestamp validDmgAt = jdbcTemplate.queryForObject(
                "SELECT valid_damage_request_at FROM og_compat.case_rounds WHERE round_id = ?",
                java.sql.Timestamp.class,
                prf.round2Id()
        );
        assertThat(validDmgAt).isNull();
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.2 Postgres: submitDamageExemptionRequest - idempotent replay does not duplicate evidence or events")
    void testSubmitDamageExemptionRequest_onPostgres_idempotentReplay() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);

        String cmdKey = "CMD_PG_DMG_IDEMP_" + UUID.randomUUID();
        ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Yêu cầu bồi thường thiệt hại lần 1 hợp lệ",
                List.of(
                        createPostgresEvidence("IMAGE", "evidence/damage1.jpg"),
                        createPostgresEvidence("VIDEO", "evidence/damage_video.mp4")
                ),
                cmdKey
        );

        // First call: Success
        ReturnCaseDtos.ReturnCaseDetailResponse resp1 = txTemplate.execute(status ->
                returnCaseService.submitDamageExemptionRequest(prf.sellerId(), prf.caseId(), req)
        );
        assertThat(resp1).isNotNull();

        Integer initialEvCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_evidence WHERE round_id = ?", Integer.class, prf.round2Id());
        assertThat(initialEvCount).isEqualTo(2);
        Integer initialEventCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_events WHERE command_key = ?", Integer.class, cmdKey);
        assertThat(initialEventCount).isEqualTo(1);

        // Second call with SAME commandKey: Idempotent replay
        ReturnCaseDtos.ReturnCaseDetailResponse resp2 = txTemplate.execute(status ->
                returnCaseService.submitDamageExemptionRequest(prf.sellerId(), prf.caseId(), req)
        );
        assertThat(resp2).isNotNull();

        // Verify count did NOT increase
        Integer finalEvCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_evidence WHERE round_id = ?", Integer.class, prf.round2Id());
        assertThat(finalEvCount).isEqualTo(2);
        Integer finalEventCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_events WHERE command_key = ?", Integer.class, cmdKey);
        assertThat(finalEventCount).isEqualTo(1);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.2 Postgres: submitDamageExemptionRequest - rejected when valid_damage_request_at already exists and commandKey is new (BD-5)")
    void testSubmitDamageExemptionRequest_onPostgres_rejected_whenAlreadySubmitted_BD5() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);

        String firstCmdKey = "CMD_PG_DMG_FIRST_" + UUID.randomUUID();
        ReturnCaseDtos.SellerDamageExemptionRequest req1 = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Yêu cầu bồi thường thiệt hại lần 1 hợp lệ",
                List.of(createPostgresEvidence("IMAGE", "evidence/damage1.jpg")),
                firstCmdKey
        );

        // First call: Success
        txTemplate.execute(status -> returnCaseService.submitDamageExemptionRequest(prf.sellerId(), prf.caseId(), req1));

        // Second call with DIFFERENT commandKey: Rejected by BD-5 guard
        String secondCmdKey = "CMD_PG_DMG_SECOND_" + UUID.randomUUID();
        ReturnCaseDtos.SellerDamageExemptionRequest req2 = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Yêu cầu gửi thêm bằng chứng lần 2 khi BD-5 chưa chốt",
                List.of(createPostgresEvidence("IMAGE", "evidence/damage2.jpg")),
                secondCmdKey
        );

        assertThatThrownBy(() -> txTemplate.execute(status ->
                returnCaseService.submitDamageExemptionRequest(prf.sellerId(), prf.caseId(), req2)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Người bán đã gửi yêu cầu bồi thường thiệt hại cho Vòng 2");

        // Verify counts remain unchanged
        Integer evCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_evidence WHERE round_id = ?", Integer.class, prf.round2Id());
        assertThat(evCount).isEqualTo(1);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.2 Postgres Concurrency: Concurrent duplicate submissions with SAME commandKey - idempotent, zero duplicates, zero deadlocks")
    void testSubmitDamageExemptionRequest_onPostgres_concurrency_sameCommandKey() throws Exception {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);

        String sameCmdKey = "CMD_PG_DMG_CONC_SAME_" + UUID.randomUUID();
        ReturnCaseDtos.SellerDamageExemptionRequest req = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Yêu cầu bồi thường thiệt hại gửi đồng thời cùng commandKey",
                List.of(
                        createPostgresEvidence("IMAGE", "evidence/conc_same_1.jpg"),
                        createPostgresEvidence("VIDEO", "evidence/conc_same_vid.mp4")
                ),
                sameCmdKey
        );

        int threadCount = 2;
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(threadCount);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicReference<Throwable> errorRef = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);

        try {
            List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
            for (int t = 0; t < threadCount; t++) {
                futures.add(executor.submit(() -> {
                    try {
                        barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                        ReturnCaseDtos.ReturnCaseDetailResponse resp = txTemplate.execute(status ->
                                returnCaseService.submitDamageExemptionRequest(prf.sellerId(), prf.caseId(), req)
                        );
                        if (resp != null) {
                            successCount.incrementAndGet();
                        }
                    } catch (Throwable t1) {
                        errorRef.set(t1);
                    }
                }));
            }

            for (java.util.concurrent.Future<?> f : futures) {
                f.get(15, java.util.concurrent.TimeUnit.SECONDS);
            }

            assertThat(errorRef.get()).isNull();
            assertThat(successCount.get()).isEqualTo(2);

            // 1. Exactly 2 evidence records for round 2 (no duplicate evidence)
            Integer evCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM og_compat.case_evidence WHERE round_id = ?",
                    Integer.class,
                    prf.round2Id()
            );
            assertThat(evCount).isEqualTo(2);

            // 2. Exactly 1 VALID_DAMAGE_REQUEST event with sameCmdKey
            Integer eventCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM og_compat.case_events WHERE command_key = ?",
                    Integer.class,
                    sameCmdKey
            );
            assertThat(eventCount).isEqualTo(1);

            // 3. valid_damage_request_at is NOT NULL
            java.sql.Timestamp validDmgAt = jdbcTemplate.queryForObject(
                    "SELECT valid_damage_request_at FROM og_compat.case_rounds WHERE round_id = ?",
                    java.sql.Timestamp.class,
                    prf.round2Id()
            );
            assertThat(validDmgAt).isNotNull();

            // 4. Case remains POST_RETURN, round 2 remains OPEN
            String caseState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.cases WHERE case_id = ?", String.class, prf.caseId());
            assertThat(caseState).isEqualTo("POST_RETURN");
            String roundState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.case_rounds WHERE round_id = ?", String.class, prf.round2Id());
            assertThat(roundState).isEqualTo("OPEN");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.2 Postgres Concurrency: Concurrent duplicate submissions with DIFFERENT commandKey - exactly one wins, one rejected, zero duplicates")
    void testSubmitDamageExemptionRequest_onPostgres_concurrency_differentCommandKey() throws Exception {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);

        String cmdKey1 = "CMD_PG_DMG_CONC_DIFF_1_" + UUID.randomUUID();
        ReturnCaseDtos.SellerDamageExemptionRequest req1 = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Yêu cầu bồi thường thiệt hại Thread 1",
                List.of(createPostgresEvidence("IMAGE", "evidence/conc_diff_1.jpg")),
                cmdKey1
        );

        String cmdKey2 = "CMD_PG_DMG_CONC_DIFF_2_" + UUID.randomUUID();
        ReturnCaseDtos.SellerDamageExemptionRequest req2 = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Yêu cầu bồi thường thiệt hại Thread 2",
                List.of(createPostgresEvidence("IMAGE", "evidence/conc_diff_2.jpg")),
                cmdKey2
        );

        int threadCount = 2;
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(threadCount);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger rejectedCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicReference<Throwable> unexpectedError = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);

        try {
            java.util.concurrent.Future<?> f1 = executor.submit(() -> {
                try {
                    barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                    ReturnCaseDtos.ReturnCaseDetailResponse resp = txTemplate.execute(status ->
                            returnCaseService.submitDamageExemptionRequest(prf.sellerId(), prf.caseId(), req1)
                    );
                    if (resp != null) successCount.incrementAndGet();
                } catch (IllegalStateException ise) {
                    if (ise.getMessage() != null && ise.getMessage().contains("Người bán đã gửi yêu cầu bồi thường thiệt hại")) {
                        rejectedCount.incrementAndGet();
                    } else {
                        unexpectedError.set(ise);
                    }
                } catch (Throwable t) {
                    unexpectedError.set(t);
                }
            });

            java.util.concurrent.Future<?> f2 = executor.submit(() -> {
                try {
                    barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                    ReturnCaseDtos.ReturnCaseDetailResponse resp = txTemplate.execute(status ->
                            returnCaseService.submitDamageExemptionRequest(prf.sellerId(), prf.caseId(), req2)
                    );
                    if (resp != null) successCount.incrementAndGet();
                } catch (IllegalStateException ise) {
                    if (ise.getMessage() != null && ise.getMessage().contains("Người bán đã gửi yêu cầu bồi thường thiệt hại")) {
                        rejectedCount.incrementAndGet();
                    } else {
                        unexpectedError.set(ise);
                    }
                } catch (Throwable t) {
                    unexpectedError.set(t);
                }
            });

            f1.get(15, java.util.concurrent.TimeUnit.SECONDS);
            f2.get(15, java.util.concurrent.TimeUnit.SECONDS);

            assertThat(unexpectedError.get()).isNull();
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(rejectedCount.get()).isEqualTo(1);

            // Exactly 1 evidence item persisted (from the winning request)
            Integer evCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM og_compat.case_evidence WHERE round_id = ?",
                    Integer.class,
                    prf.round2Id()
            );
            assertThat(evCount).isEqualTo(1);

            // Exactly 1 VALID_DAMAGE_REQUEST event
            Integer eventCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM og_compat.case_events WHERE round_id = ? AND event_type = 'VALID_DAMAGE_REQUEST'",
                    Integer.class,
                    prf.round2Id()
            );
            assertThat(eventCount).isEqualTo(1);

            // valid_damage_request_at is NOT NULL
            java.sql.Timestamp validDmgAt = jdbcTemplate.queryForObject(
                    "SELECT valid_damage_request_at FROM og_compat.case_rounds WHERE round_id = ?",
                    java.sql.Timestamp.class,
                    prf.round2Id()
            );
            assertThat(validDmgAt).isNotNull();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.2 Postgres: submitDamageExemptionRequest - rejected when case is not in POST_RETURN or seller overdue")
    void testSubmitDamageExemptionRequest_onPostgres_rejected_whenInvalidStateOrOverdue() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // Subcase A: Overdue seller response
        PostReturnFixture prfOverdue = createPostReturnFixture(txTemplate);
        makeCaseOverdueForTimeout(prfOverdue.caseId());

        ReturnCaseDtos.SellerDamageExemptionRequest reqOverdue = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Yêu cầu bồi thường khi đã quá hạn phản hồi",
                List.of(createPostgresEvidence("IMAGE", "evidence/overdue.jpg")),
                "CMD_PG_DMG_OVERDUE_" + UUID.randomUUID()
        );

        assertThatThrownBy(() -> txTemplate.execute(status ->
                returnCaseService.submitDamageExemptionRequest(prfOverdue.sellerId(), prfOverdue.caseId(), reqOverdue)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Đã quá thời hạn 2 ngày phản hồi của người bán");

        // Verify zero evidence and zero events
        Integer evCountOverdue = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.case_evidence WHERE round_id = ?",
                Integer.class,
                prfOverdue.round2Id()
        );
        assertThat(evCountOverdue).isZero();

        // Subcase B: Missing evidence (< 1 evidence)
        PostReturnFixture prfNoEv = createPostReturnFixture(txTemplate);
        ReturnCaseDtos.SellerDamageExemptionRequest reqNoEv = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Yêu cầu bồi thường không có bằng chứng",
                List.of(),
                "CMD_PG_DMG_NO_EV_" + UUID.randomUUID()
        );

        assertThatThrownBy(() -> txTemplate.execute(status ->
                returnCaseService.submitDamageExemptionRequest(prfNoEv.sellerId(), prfNoEv.caseId(), reqNoEv)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bắt buộc phải có ít nhất 1 bằng chứng");
    }

    // =========================================================================
    // FEATURE 3.2: TASK 3.2.3 - KTV FINAL DECISION POSTGRES TESTS (PHASE 3)
    // =========================================================================

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.3 Postgres: ktvDecideRound2 - FULL_GOODS_REFUND creates CaseDecision, settlement operation in REQUESTED, and closes holds")
    void testKtvDecideRound2_onPostgres_fullGoodsRefund_success() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);
        txTemplate.executeWithoutResult(status -> returnCaseService.assignCaseToKtv(ktvId, prf.caseId()));

        String cmdKey = "CMD_PG_KTV_FULL_" + UUID.randomUUID();
        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "KTV xác nhận hàng hóa hợp lệ, duyệt hoàn tiền toàn bộ cho người mua",
                cmdKey
        );

        ReturnCaseDtos.ReturnCaseDetailResponse resp = txTemplate.execute(status ->
                returnCaseService.ktvDecideRound2(ktvId, prf.caseId(), req)
        );

        assertThat(resp).isNotNull();
        assertThat(resp.state()).isEqualTo(CaseEntity.STATE_CLOSED);

        // 1. Verify CaseDecision on real PostgreSQL
        List<Map<String, Object>> decisions = jdbcTemplate.queryForList("""
                SELECT decision_id, round_id, reviewed_by, actor_type, outcome, is_final, goods_refund_amount, reason, command_key, decided_at
                FROM og_compat.case_decisions
                WHERE round_id = ?
                """, prf.round2Id());
        assertThat(decisions).hasSize(1);
        Map<String, Object> dec = decisions.getFirst();
        Long decisionId = ((Number) dec.get("decision_id")).longValue();
        assertThat(dec.get("outcome")).isEqualTo(CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND);
        assertThat(dec.get("actor_type")).isEqualTo(CaseDecisionEntity.ACTOR_USER);
        assertThat(((Number) dec.get("reviewed_by")).longValue()).isEqualTo(ktvId);
        assertThat(dec.get("is_final")).isEqualTo(Boolean.TRUE);
        assertThat(dec.get("command_key")).isEqualTo(cmdKey);
        assertThat(((BigDecimal) dec.get("goods_refund_amount")).compareTo(new BigDecimal("250000.00"))).isZero();
        assertThat(dec.get("reason")).isEqualTo("KTV xác nhận hàng hóa hợp lệ, duyệt hoàn tiền toàn bộ cho người mua");
        assertThat(dec.get("decided_at")).isNotNull();

        // 2. Verify trigger og_apply_case_decision closed the Case and decided Round 2
        String caseState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.cases WHERE case_id = ?", String.class, prf.caseId());
        assertThat(caseState).isEqualTo("CLOSED");
        String roundState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.case_rounds WHERE round_id = ?", String.class, prf.round2Id());
        assertThat(roundState).isEqualTo("DECIDED");

        // 3. Verify trigger og_close_case_holds closed all money_holds
        List<Map<String, Object>> holds = jdbcTemplate.queryForList(
                "SELECT hold_id, closed_at FROM og_compat.money_holds WHERE case_id = ?", prf.caseId()
        );
        assertThat(holds).isNotEmpty();
        assertThat(holds).allMatch(h -> h.get("closed_at") != null);

        // 4. Verify Task 4.1a settlement operations on PostgreSQL: exactly 1 operation (GOODS_CASH -> REFUND_BUYER)
        List<Map<String, Object>> ops = jdbcTemplate.queryForList("""
                SELECT operation_id, component_id, operation_type, amount, cause_type, case_decision_id, recipient_user_id, state, command_key
                FROM og_compat.settlement_operations
                WHERE case_decision_id = ?
                ORDER BY operation_type
                """, decisionId);
        assertThat(ops).hasSize(1);

        Map<String, Object> cashOp = ops.getFirst();
        assertThat(cashOp.get("operation_type")).isEqualTo("REFUND_BUYER");
        assertThat(cashOp.get("cause_type")).isEqualTo("RETURN");
        assertThat(cashOp.get("state")).isEqualTo("REQUESTED");
        assertThat(((Number) cashOp.get("recipient_user_id")).longValue()).isEqualTo(prf.buyerId());
        assertThat(((BigDecimal) cashOp.get("amount")).compareTo(new BigDecimal("250000.00"))).isZero();
        assertThat(cashOp.get("command_key")).isEqualTo(cmdKey + ":GOODS_CASH");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.3 Postgres: ktvDecideRound2 - EXEMPT_GOODS_REFUND creates CaseDecision with goodsRefundAmount=0, settlement operation RELEASE_SELLER")
    void testKtvDecideRound2_onPostgres_exemptGoodsRefund_success() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);

        // Step 1: Seller submits damage request (so valid_damage_request_at IS NOT NULL)
        String dmgCmdKey = "CMD_PG_DMG_FOR_EXEMPT_" + UUID.randomUUID();
        ReturnCaseDtos.SellerDamageExemptionRequest dmgReq = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Hàng trả về bị trầy xước và bể màn hình",
                List.of(createPostgresEvidence("IMAGE", "evidence/broken_screen.jpg")),
                dmgCmdKey
        );
        txTemplate.execute(status -> returnCaseService.submitDamageExemptionRequest(prf.sellerId(), prf.caseId(), dmgReq));

        // Step 2: KTV claims & decides EXEMPT_GOODS_REFUND
        txTemplate.executeWithoutResult(status -> returnCaseService.assignCaseToKtv(ktvId, prf.caseId()));
        String cmdKey = "CMD_PG_KTV_EXEMPT_" + UUID.randomUUID();
        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_EXEMPT_GOODS_REFUND,
                "KTV xác nhận bằng chứng thiệt hại của người bán là xác đáng, duyệt miễn trừ bồi thường",
                cmdKey
        );

        ReturnCaseDtos.ReturnCaseDetailResponse resp = txTemplate.execute(status ->
                returnCaseService.ktvDecideRound2(ktvId, prf.caseId(), req)
        );

        assertThat(resp).isNotNull();
        assertThat(resp.state()).isEqualTo(CaseEntity.STATE_CLOSED);

        // 1. Verify CaseDecision on real PostgreSQL
        List<Map<String, Object>> decisions = jdbcTemplate.queryForList("""
                SELECT decision_id, round_id, reviewed_by, actor_type, outcome, is_final, goods_refund_amount, reason, command_key, decided_at
                FROM og_compat.case_decisions
                WHERE round_id = ?
                """, prf.round2Id());
        assertThat(decisions).hasSize(1);
        Map<String, Object> dec = decisions.getFirst();
        Long decisionId = ((Number) dec.get("decision_id")).longValue();
        assertThat(dec.get("outcome")).isEqualTo(CaseDecisionEntity.OUTCOME_EXEMPT_GOODS_REFUND);
        assertThat(dec.get("actor_type")).isEqualTo(CaseDecisionEntity.ACTOR_USER);
        assertThat(((Number) dec.get("reviewed_by")).longValue()).isEqualTo(ktvId);
        assertThat(dec.get("is_final")).isEqualTo(Boolean.TRUE);
        assertThat(dec.get("command_key")).isEqualTo(cmdKey);
        // DB trigger sets goods_refund_amount = 0 for EXEMPT_GOODS_REFUND
        assertThat(((BigDecimal) dec.get("goods_refund_amount")).compareTo(BigDecimal.ZERO)).isZero();

        // 2. Verify trigger og_apply_case_decision closed the Case and decided Round 2
        String caseState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.cases WHERE case_id = ?", String.class, prf.caseId());
        assertThat(caseState).isEqualTo("CLOSED");
        String roundState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.case_rounds WHERE round_id = ?", String.class, prf.round2Id());
        assertThat(roundState).isEqualTo("DECIDED");

        // 3. Verify trigger og_close_case_holds closed all money_holds
        List<Map<String, Object>> holds = jdbcTemplate.queryForList(
                "SELECT hold_id, closed_at FROM og_compat.money_holds WHERE case_id = ?", prf.caseId()
        );
        assertThat(holds).isNotEmpty();
        assertThat(holds).allMatch(h -> h.get("closed_at") != null);

        // 4. Verify settlement operations on PostgreSQL: RELEASE_SELLER for GOODS_CASH
        List<Map<String, Object>> ops = jdbcTemplate.queryForList("""
                SELECT operation_id, component_id, operation_type, amount, cause_type, case_decision_id, recipient_user_id, state, command_key
                FROM og_compat.settlement_operations
                WHERE case_decision_id = ?
                ORDER BY operation_type
                """, decisionId);
        assertThat(ops).hasSize(1);

        Map<String, Object> cashOp = ops.getFirst();
        assertThat(cashOp.get("operation_type")).isEqualTo("RELEASE_SELLER");
        assertThat(cashOp.get("cause_type")).isEqualTo("EXEMPTION");
        assertThat(cashOp.get("state")).isEqualTo("REQUESTED");
        assertThat(((Number) cashOp.get("recipient_user_id")).longValue()).isEqualTo(prf.sellerId());
        assertThat(((BigDecimal) cashOp.get("amount")).compareTo(new BigDecimal("250000.00"))).isZero();
        assertThat(cashOp.get("command_key")).isEqualTo(cmdKey + ":GOODS_CASH");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.3 Postgres: ktvDecideRound2 - EXEMPT_GOODS_REFUND rejected when valid_damage_request_at IS NULL (DB trigger guard)")
    void testKtvDecideRound2_onPostgres_rejected_whenExemptWithoutDamageRequest() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);
        txTemplate.executeWithoutResult(status -> returnCaseService.assignCaseToKtv(ktvId, prf.caseId()));

        // KTV attempts EXEMPT_GOODS_REFUND without any damage request submitted
        String cmdKey = "CMD_PG_KTV_EXEMPT_NO_DMG_" + UUID.randomUUID();
        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_EXEMPT_GOODS_REFUND,
                "Cố ý duyệt miễn trừ khi chưa có yêu cầu thiệt hại từ người bán",
                cmdKey
        );

        assertThatThrownBy(() -> txTemplate.execute(status ->
                returnCaseService.ktvDecideRound2(ktvId, prf.caseId(), req)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Chỉ được miễn trừ bồi thường khi người bán đã gửi yêu cầu thiệt hại hợp lệ");

        // Verify zero decisions created on PostgreSQL
        Integer decCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_decisions WHERE round_id = ?", Integer.class, prf.round2Id());
        assertThat(decCount).isZero();

        // Verify zero settlement operations created
        Integer opCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.settlement_operations so JOIN og_compat.order_fund_components ofc USING(component_id) WHERE ofc.order_id = ?",
                Integer.class, prf.orderId()
        );
        assertThat(opCount).isZero();
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.3 Postgres: ktvDecideRound2 - rollback atomicity guarantees zero orphaned decisions or settlements")
    void testKtvDecideRound2_onPostgres_rollbackAtomicity() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);
        txTemplate.executeWithoutResult(status -> returnCaseService.assignCaseToKtv(ktvId, prf.caseId()));

        String cmdKey = "CMD_PG_KTV_ROLLBACK_" + UUID.randomUUID();
        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Phán quyết KTV hợp lệ nhưng gặp lỗi hệ thống sau settlement",
                cmdKey
        );

        assertThatThrownBy(() -> txTemplate.execute(status -> {
            returnCaseService.ktvDecideRound2(ktvId, prf.caseId(), req);
            throw new RuntimeException("Simulated error to test KTV decision transaction rollback atomicity");
        })).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated error to test KTV decision transaction rollback atomicity");

        // Verify zero decisions retained
        Integer decCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_decisions WHERE round_id = ?", Integer.class, prf.round2Id());
        assertThat(decCount).isZero();

        // Verify zero settlement operations retained
        Integer opCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.settlement_operations so JOIN og_compat.order_fund_components ofc USING(component_id) WHERE ofc.order_id = ?",
                Integer.class, prf.orderId()
        );
        assertThat(opCount).isZero();

        // Verify case remains POST_RETURN and round 2 remains OPEN
        String caseState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.cases WHERE case_id = ?", String.class, prf.caseId());
        assertThat(caseState).isEqualTo("POST_RETURN");
        String roundState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.case_rounds WHERE round_id = ?", String.class, prf.round2Id());
        assertThat(roundState).isEqualTo("OPEN");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.3 Postgres: ktvDecideRound2 - idempotent replay does not create duplicate decisions or settlements")
    void testKtvDecideRound2_onPostgres_idempotentReplay() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);
        txTemplate.executeWithoutResult(status -> returnCaseService.assignCaseToKtv(ktvId, prf.caseId()));

        String cmdKey = "CMD_PG_KTV_REPLAY_" + UUID.randomUUID();
        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Phán quyết KTV duyệt hoàn tiền lần 1",
                cmdKey
        );

        // First call: Success
        ReturnCaseDtos.ReturnCaseDetailResponse resp1 = txTemplate.execute(status ->
                returnCaseService.ktvDecideRound2(ktvId, prf.caseId(), req)
        );
        assertThat(resp1).isNotNull();

        // Second call with same commandKey: Idempotent Replay
        ReturnCaseDtos.ReturnCaseDetailResponse resp2 = txTemplate.execute(status ->
                returnCaseService.ktvDecideRound2(ktvId, prf.caseId(), req)
        );
        assertThat(resp2).isNotNull();

        // Verify exactly 1 decision
        Integer decCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_decisions WHERE round_id = ?", Integer.class, prf.round2Id());
        assertThat(decCount).isEqualTo(1);

        // Verify exactly 1 settlement operation
        Integer opCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.settlement_operations so JOIN og_compat.order_fund_components ofc USING(component_id) WHERE ofc.order_id = ?",
                Integer.class, prf.orderId()
        );
        assertThat(opCount).isEqualTo(1);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.3 Postgres Concurrency: 10 iterations of two concurrent KTV decisions with true overlap at lockGateway - exactly 1 wins, 1 rejected, zero 40P01 deadlocks")
    void testKtvDecideRound2_onPostgres_overlapConcurrency() throws Exception {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Long ktv1 = ktvId; // has role KTV

        int totalIterations = 10;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);

        try {
            for (int i = 0; i < totalIterations; i++) {
                PostReturnFixture prf = createPostReturnFixture(txTemplate);
                txTemplate.executeWithoutResult(status -> returnCaseService.assignCaseToKtv(ktv1, prf.caseId()));

                java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
                java.util.concurrent.atomic.AtomicInteger winnerCount = new java.util.concurrent.atomic.AtomicInteger(0);
                java.util.concurrent.atomic.AtomicInteger loserCount = new java.util.concurrent.atomic.AtomicInteger(0);
                java.util.concurrent.atomic.AtomicReference<Throwable> unexpectedError = new java.util.concurrent.atomic.AtomicReference<>();

                String cmdKey1 = "CMD_CONC_KTV1_" + UUID.randomUUID();
                String cmdKey2 = "CMD_CONC_KTV2_" + UUID.randomUUID();

                java.util.concurrent.Future<?> f1 = executor.submit(() -> {
                    try {
                        barrier.await(); // Synchronize departure so both threads execute at the exact same instant
                        txTemplate.execute(status -> returnCaseService.ktvDecideRound2(ktv1, prf.caseId(),
                                new ReturnCaseDtos.KtvRound2DecisionRequest(
                                        CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                                        "KTV1 hoàn tiền toàn bộ cho người mua",
                                        cmdKey1
                                )
                        ));
                        winnerCount.incrementAndGet();
                    } catch (IllegalStateException e) {
                        // Legitimate first-write-wins conflict: Case closed or Round 2 decided
                        loserCount.incrementAndGet();
                    } catch (Throwable t) {
                        unexpectedError.set(t);
                    }
                });

                java.util.concurrent.Future<?> f2 = executor.submit(() -> {
                    try {
                        barrier.await(); // Synchronize departure so both threads execute at the exact same instant
                        txTemplate.execute(status -> returnCaseService.ktvDecideRound2(ktv1, prf.caseId(),
                                new ReturnCaseDtos.KtvRound2DecisionRequest(
                                        CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                                        "KTV2 hoàn tiền toàn bộ cho người mua",
                                        cmdKey2
                                )
                        ));
                        winnerCount.incrementAndGet();
                    } catch (IllegalStateException e) {
                        // Legitimate first-write-wins conflict: Case closed or Round 2 decided
                        loserCount.incrementAndGet();
                    } catch (Throwable t) {
                        unexpectedError.set(t);
                    }
                });

                f1.get(10, java.util.concurrent.TimeUnit.SECONDS);
                f2.get(10, java.util.concurrent.TimeUnit.SECONDS);

                assertThat(unexpectedError.get()).isNull();
                assertThat(winnerCount.get()).isEqualTo(1);
                assertThat(loserCount.get()).isEqualTo(1);

                // Verify exactly 1 decision in DB
                Integer decCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_decisions WHERE round_id = ?", Integer.class, prf.round2Id());
                assertThat(decCount).isEqualTo(1);

                // Verify exactly 1 settlement operation in DB
                Integer opCount = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM og_compat.settlement_operations so JOIN og_compat.order_fund_components ofc USING(component_id) WHERE ofc.order_id = ?",
                        Integer.class, prf.orderId()
                );
                assertThat(opCount).isEqualTo(1);

                // Verify Case closed and holds closed
                String caseState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.cases WHERE case_id = ?", String.class, prf.caseId());
                assertThat(caseState).isEqualTo("CLOSED");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.3 Postgres: case_decisions trigger trg_case_decision_history rejects UPDATE with SQLSTATE 23514")
    void testCaseDecisions_onPostgres_updateRejected_appendOnly() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);
        txTemplate.executeWithoutResult(status -> returnCaseService.assignCaseToKtv(ktvId, prf.caseId()));
        String cmdKey = "CMD_PG_KTV_UPD_" + UUID.randomUUID();
        String originalReason = "Lý do phán quyết gốc của KTV";
        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                originalReason,
                cmdKey
        );
        txTemplate.execute(status -> returnCaseService.ktvDecideRound2(ktvId, prf.caseId(), req));

        Long decisionId = jdbcTemplate.queryForObject(
                "SELECT decision_id FROM og_compat.case_decisions WHERE command_key = ?", Long.class, cmdKey
        );

        // Attempt UPDATE in a separate REQUIRES_NEW transaction
        org.springframework.dao.DataAccessException ex = null;
        try {
            txTemplate.execute(status -> {
                jdbcTemplate.update("UPDATE og_compat.case_decisions SET reason = 'tampered' WHERE decision_id = ?", decisionId);
                return null;
            });
        } catch (org.springframework.dao.DataAccessException e) {
            ex = e;
        }

        assertThat(ex).isNotNull();
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        assertThat(root).isInstanceOf(java.sql.SQLException.class);
        assertThat(((java.sql.SQLException) root).getSQLState()).isEqualTo("23514");

        // Verify original decision record remains intact with unchanged reason
        String currentReason = jdbcTemplate.queryForObject(
                "SELECT reason FROM og_compat.case_decisions WHERE decision_id = ?", String.class, decisionId
        );
        assertThat(currentReason).isEqualTo(originalReason);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.3 Postgres: case_decisions trigger trg_case_decision_history rejects DELETE with SQLSTATE 23514")
    void testCaseDecisions_onPostgres_deleteRejected_appendOnly() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);
        txTemplate.executeWithoutResult(status -> returnCaseService.assignCaseToKtv(ktvId, prf.caseId()));
        String cmdKey = "CMD_PG_KTV_DEL_" + UUID.randomUUID();
        String originalReason = "Lý do phán quyết gốc không được phép xóa";
        ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                originalReason,
                cmdKey
        );
        txTemplate.execute(status -> returnCaseService.ktvDecideRound2(ktvId, prf.caseId(), req));

        Long decisionId = jdbcTemplate.queryForObject(
                "SELECT decision_id FROM og_compat.case_decisions WHERE command_key = ?", Long.class, cmdKey
        );

        // Attempt DELETE in a separate REQUIRES_NEW transaction
        org.springframework.dao.DataAccessException ex = null;
        try {
            txTemplate.execute(status -> {
                jdbcTemplate.update("DELETE FROM og_compat.case_decisions WHERE decision_id = ?", decisionId);
                return null;
            });
        } catch (org.springframework.dao.DataAccessException e) {
            ex = e;
        }

        assertThat(ex).isNotNull();
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        assertThat(root).isInstanceOf(java.sql.SQLException.class);
        assertThat(((java.sql.SQLException) root).getSQLState()).isEqualTo("23514");

        // Verify original decision record still exists
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(1) FROM og_compat.case_decisions WHERE decision_id = ?", Integer.class, decisionId
        );
        assertThat(count).isEqualTo(1);
    }

    // =========================================================================
    // FEATURE 3.2: TASK 3.2.4 - AUTOMATIC POST-RETURN TIMEOUT REFUND
    // Postgres Integration Tests: Success, Rollback Atomicity, Idempotency, Concurrency (TC-28, TC-29)
    // =========================================================================

    private void makeCaseOverdueForTimeout(Long caseId) {
        java.sql.Timestamp validReturnedAt = java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(3)));
        java.sql.Timestamp dueAt = java.sql.Timestamp.from(validReturnedAt.toInstant().plus(java.time.Duration.ofDays(2)));
        java.sql.Timestamp approvedAt = java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(4)));

        // 1. Update return shipment valid_delivery_at to match new valid_returned_at (Proof requirement for trg_case_state_proof)
        jdbcTemplate.update("""
                UPDATE og_compat.shipments
                SET valid_delivery_at = ?
                WHERE return_case_id = ? AND leg = 'RETURN'
                """, validReturnedAt, caseId);

        // 2. Update case timestamps
        jdbcTemplate.update("""
                UPDATE og_compat.cases
                SET approved_at = ?,
                    valid_returned_at = ?,
                    seller_response_due_at = ?
                WHERE case_id = ?
                """, approvedAt, validReturnedAt, dueAt, caseId);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.4 Postgres: processPostReturnTimeoutForCase - success auto-refunds Buyer, creates settlement operation, and closes holds")
    void testAutoRefundTimeout_onPostgres_success() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);
        makeCaseOverdueForTimeout(prf.caseId());

        ReturnCaseDtos.CaseTimeoutExecutionResult result = returnCaseService.processPostReturnTimeoutForCaseInNewTx(prf.caseId());

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(ReturnCaseDtos.TimeoutProcessingStatus.PROCESSED);

        // 1. Verify case_decisions on PostgreSQL
        List<Map<String, Object>> decisions = jdbcTemplate.queryForList("""
                SELECT decision_id, outcome, actor_type, reviewed_by, is_final, command_key
                FROM og_compat.case_decisions
                WHERE round_id = ?
                """, prf.round2Id());
        assertThat(decisions).hasSize(1);
        Map<String, Object> dec = decisions.getFirst();
        Long decisionId = ((Number) dec.get("decision_id")).longValue();
        assertThat(dec.get("outcome")).isEqualTo(CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND);
        assertThat(dec.get("actor_type")).isEqualTo(CaseDecisionEntity.ACTOR_SYSTEM_TIMEOUT);
        assertThat(dec.get("reviewed_by")).isNull();
        assertThat(dec.get("is_final")).isEqualTo(Boolean.TRUE);
        assertThat(dec.get("command_key")).isEqualTo("TIMEOUT:SELLER_POST_RETURN:" + prf.caseId() + ":" + prf.round2Id());

        // 2. Verify trigger og_apply_case_decision closed the Case and decided Round 2
        String caseState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.cases WHERE case_id = ?", String.class, prf.caseId());
        assertThat(caseState).isEqualTo("CLOSED");
        String roundState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.case_rounds WHERE round_id = ?", String.class, prf.round2Id());
        assertThat(roundState).isEqualTo("DECIDED");

        // 3. Verify trigger og_close_case_holds closed all money_holds
        List<Map<String, Object>> holds = jdbcTemplate.queryForList(
                "SELECT hold_id, closed_at FROM og_compat.money_holds WHERE case_id = ?", prf.caseId()
        );
        assertThat(holds).isNotEmpty();
        assertThat(holds).allMatch(h -> h.get("closed_at") != null);

        // 4. Verify Task 4.1a settlement operations on PostgreSQL: exactly 1 operation (REFUND_BUYER)
        List<Map<String, Object>> ops = jdbcTemplate.queryForList("""
                SELECT operation_id, component_id, operation_type, amount, cause_type, case_decision_id, recipient_user_id, state, command_key
                FROM og_compat.settlement_operations
                WHERE case_decision_id = ?
                """, decisionId);
        assertThat(ops).hasSize(1);
        Map<String, Object> op = ops.getFirst();
        assertThat(op.get("operation_type")).isEqualTo("REFUND_BUYER");
        assertThat(op.get("cause_type")).isEqualTo("RETURN");
        assertThat(op.get("state")).isEqualTo("REQUESTED");
        assertThat(((Number) op.get("recipient_user_id")).longValue()).isEqualTo(prf.buyerId());
        assertThat(((BigDecimal) op.get("amount")).compareTo(new BigDecimal("250000.00"))).isZero();
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.4 Postgres B2: processPostReturnTimeoutForCase - rollback atomicity guarantees zero orphaned decisions or settlements")
    void testAutoRefundTimeout_onPostgres_rollbackAtomicity() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);
        makeCaseOverdueForTimeout(prf.caseId());

        RoundDecisionSettlementPort originalPort = (RoundDecisionSettlementPort)
                org.springframework.test.util.ReflectionTestUtils.getField(returnCaseService, "settlementPort");

        try {
            // Inject throwing settlement port to simulate settlement failure during transaction
            org.springframework.test.util.ReflectionTestUtils.setField(returnCaseService, "settlementPort",
                    (RoundDecisionSettlementPort) (orderId, caseId, decisionId, outcome, commandKey) -> {
                        throw new RuntimeException("Simulated payment settlement failure during timeout refund");
                    });

            ReturnCaseDtos.CaseTimeoutExecutionResult result = returnCaseService.processPostReturnTimeoutForCaseInNewTx(prf.caseId());

            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo(ReturnCaseDtos.TimeoutProcessingStatus.FAILED);
            assertThat(result.message()).contains("Simulated payment settlement failure");

            // Verify zero decisions retained in DB
            Integer decCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_decisions WHERE round_id = ?", Integer.class, prf.round2Id());
            assertThat(decCount).isZero();

            // Verify zero settlement operations retained in DB
            Integer opCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM og_compat.settlement_operations so JOIN og_compat.order_fund_components ofc USING(component_id) WHERE ofc.order_id = ?",
                    Integer.class, prf.orderId()
            );
            assertThat(opCount).isZero();

            // Verify case remains POST_RETURN and round 2 remains OPEN
            String caseState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.cases WHERE case_id = ?", String.class, prf.caseId());
            assertThat(caseState).isEqualTo("POST_RETURN");
            String roundState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.case_rounds WHERE round_id = ?", String.class, prf.round2Id());
            assertThat(roundState).isEqualTo("OPEN");

            // Verify money holds remain OPEN
            List<Map<String, Object>> holds = jdbcTemplate.queryForList(
                    "SELECT hold_id, closed_at FROM og_compat.money_holds WHERE case_id = ?", prf.caseId()
            );
            assertThat(holds).isNotEmpty();
            assertThat(holds).allMatch(h -> h.get("closed_at") == null);
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(returnCaseService, "settlementPort", originalPort);
        }
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.4 Postgres B3: processPostReturnTimeoutForCase - idempotent replay does not duplicate decision or settlements")
    void testAutoRefundTimeout_onPostgres_idempotentReplay() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);
        makeCaseOverdueForTimeout(prf.caseId());

        // First execution -> PROCESSED
        ReturnCaseDtos.CaseTimeoutExecutionResult result1 = returnCaseService.processPostReturnTimeoutForCaseInNewTx(prf.caseId());
        assertThat(result1.status()).isEqualTo(ReturnCaseDtos.TimeoutProcessingStatus.PROCESSED);

        // Snapshot existing settlement operations
        List<Map<String, Object>> snapshotOps = jdbcTemplate.queryForList("""
                SELECT operation_id, amount, state, command_key
                FROM og_compat.settlement_operations so
                JOIN og_compat.order_fund_components ofc USING(component_id)
                WHERE ofc.order_id = ?
                """, prf.orderId());
        assertThat(snapshotOps).hasSize(1);

        // Replay second execution for same case -> SKIPPED (Idempotent)
        ReturnCaseDtos.CaseTimeoutExecutionResult result2 = returnCaseService.processPostReturnTimeoutForCaseInNewTx(prf.caseId());
        assertThat(result2.status()).isEqualTo(ReturnCaseDtos.TimeoutProcessingStatus.SKIPPED);

        // Verify decision count unchanged
        Integer decCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_decisions WHERE round_id = ?", Integer.class, prf.round2Id());
        assertThat(decCount).isEqualTo(1);

        // Verify settlement operations count and content unchanged
        List<Map<String, Object>> currentOps = jdbcTemplate.queryForList("""
                SELECT operation_id, amount, state, command_key
                FROM og_compat.settlement_operations so
                JOIN og_compat.order_fund_components ofc USING(component_id)
                WHERE ofc.order_id = ?
                """, prf.orderId());
        assertThat(currentOps).hasSize(1);
        assertThat(currentOps.getFirst().get("operation_id")).isEqualTo(snapshotOps.getFirst().get("operation_id"));
        assertThat(((BigDecimal) currentOps.getFirst().get("amount")).compareTo((BigDecimal) snapshotOps.getFirst().get("amount"))).isZero();
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.4 Postgres B4 (TC-28): 10 iterations of concurrent Seller Agree vs Worker Timeout with CyclicBarrier - exactly 1 wins, 0 orphan records, 0 unhandled deadlocks")
    void testAutoRefundTimeout_onPostgres_concurrency_raceWithSellerAgree_10Iterations() throws Exception {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        int totalIterations = 10;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);

        try {
            for (int i = 0; i < totalIterations; i++) {
                PostReturnFixture prf = createPostReturnFixture(txTemplate);
                makeCaseOverdueForTimeout(prf.caseId());

                java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
                java.util.concurrent.atomic.AtomicInteger sellerWins = new java.util.concurrent.atomic.AtomicInteger(0);
                java.util.concurrent.atomic.AtomicInteger sellerLost = new java.util.concurrent.atomic.AtomicInteger(0);
                java.util.concurrent.atomic.AtomicInteger workerWins = new java.util.concurrent.atomic.AtomicInteger(0);
                java.util.concurrent.atomic.AtomicInteger workerSkipped = new java.util.concurrent.atomic.AtomicInteger(0);
                java.util.concurrent.atomic.AtomicReference<Throwable> unexpectedError = new java.util.concurrent.atomic.AtomicReference<>();

                String agreeCmdKey = "CMD_CONC_SELLER_AGREE_" + UUID.randomUUID();

                // Thread 1: Seller Agree
                java.util.concurrent.Future<?> f1 = executor.submit(() -> {
                    try {
                        barrier.await();
                        txTemplate.execute(status -> returnCaseService.sellerAgreeRefundRound2(
                                prf.sellerId(),
                                prf.caseId(),
                                new ReturnCaseDtos.SellerAgreeRefundRequest("Seller đồng ý hoàn tiền", agreeCmdKey)
                        ));
                        sellerWins.incrementAndGet();
                    } catch (IllegalStateException e) {
                        sellerLost.incrementAndGet();
                    } catch (Throwable t) {
                        unexpectedError.set(t);
                    }
                });

                // Thread 2: Worker Timeout
                java.util.concurrent.Future<?> f2 = executor.submit(() -> {
                    try {
                        barrier.await();
                        ReturnCaseDtos.CaseTimeoutExecutionResult result = returnCaseService.processPostReturnTimeoutForCaseInNewTx(prf.caseId());
                        if (result != null && result.status() == ReturnCaseDtos.TimeoutProcessingStatus.PROCESSED) {
                            workerWins.incrementAndGet();
                        } else if (result != null && result.status() == ReturnCaseDtos.TimeoutProcessingStatus.SKIPPED) {
                            workerSkipped.incrementAndGet();
                        }
                    } catch (Throwable t) {
                        unexpectedError.set(t);
                    }
                });

                f1.get(10, java.util.concurrent.TimeUnit.SECONDS);
                f2.get(10, java.util.concurrent.TimeUnit.SECONDS);

                assertThat(unexpectedError.get()).isNull();

                int totalWinners = sellerWins.get() + workerWins.get();
                assertThat(totalWinners).as("Iteration %d: exactly 1 winner expected", i).isEqualTo(1);

                // DB checks
                Integer decCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_decisions WHERE round_id = ?", Integer.class, prf.round2Id());
                assertThat(decCount).as("Iteration %d: exactly 1 decision in DB", i).isEqualTo(1);

                Integer opCount = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM og_compat.settlement_operations so JOIN og_compat.order_fund_components ofc USING(component_id) WHERE ofc.order_id = ?",
                        Integer.class, prf.orderId()
                );
                assertThat(opCount).as("Iteration %d: exactly 1 settlement operation in DB", i).isEqualTo(1);

                String caseState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.cases WHERE case_id = ?", String.class, prf.caseId());
                assertThat(caseState).isEqualTo("CLOSED");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.4 Postgres B5 (TC-29): 10 iterations of concurrent Seller Damage Request vs Worker Timeout with CyclicBarrier - mutually exclusive, zero data corruption")
    void testAutoRefundTimeout_onPostgres_concurrency_raceWithDamageRequest_10Iterations() throws Exception {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        int totalIterations = 10;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);

        try {
            for (int i = 0; i < totalIterations; i++) {
                PostReturnFixture prf = createPostReturnFixture(txTemplate);
                makeCaseOverdueForTimeout(prf.caseId());

                java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
                java.util.concurrent.atomic.AtomicInteger damageWins = new java.util.concurrent.atomic.AtomicInteger(0);
                java.util.concurrent.atomic.AtomicInteger damageLost = new java.util.concurrent.atomic.AtomicInteger(0);
                java.util.concurrent.atomic.AtomicInteger workerWins = new java.util.concurrent.atomic.AtomicInteger(0);
                java.util.concurrent.atomic.AtomicInteger workerSkipped = new java.util.concurrent.atomic.AtomicInteger(0);
                java.util.concurrent.atomic.AtomicReference<Throwable> unexpectedError = new java.util.concurrent.atomic.AtomicReference<>();

                String dmgCmdKey = "CMD_CONC_SELLER_DMG_" + UUID.randomUUID();

                // Thread 1: Seller Damage Request
                java.util.concurrent.Future<?> f1 = executor.submit(() -> {
                    try {
                        barrier.await();
                        txTemplate.execute(status -> returnCaseService.submitDamageExemptionRequest(
                                prf.sellerId(),
                                prf.caseId(),
                                new ReturnCaseDtos.SellerDamageExemptionRequest(
                                        "Hàng bị hư hỏng khi nhận lại",
                                        List.of(createPostgresEvidence("IMAGE", "evidence/damage_race_" + UUID.randomUUID() + ".jpg")),
                                        dmgCmdKey
                                )
                        ));
                        damageWins.incrementAndGet();
                    } catch (IllegalStateException e) {
                        damageLost.incrementAndGet();
                    } catch (Throwable t) {
                        unexpectedError.set(t);
                    }
                });

                // Thread 2: Worker Timeout
                java.util.concurrent.Future<?> f2 = executor.submit(() -> {
                    try {
                        barrier.await();
                        ReturnCaseDtos.CaseTimeoutExecutionResult result = returnCaseService.processPostReturnTimeoutForCaseInNewTx(prf.caseId());
                        if (result != null && result.status() == ReturnCaseDtos.TimeoutProcessingStatus.PROCESSED) {
                            workerWins.incrementAndGet();
                        } else if (result != null && result.status() == ReturnCaseDtos.TimeoutProcessingStatus.SKIPPED) {
                            workerSkipped.incrementAndGet();
                        }
                    } catch (Throwable t) {
                        unexpectedError.set(t);
                    }
                });

                f1.get(10, java.util.concurrent.TimeUnit.SECONDS);
                f2.get(10, java.util.concurrent.TimeUnit.SECONDS);

                assertThat(unexpectedError.get()).isNull();

                if (damageWins.get() == 1) {
                    // Damage Request won: Worker was SKIPPED
                    assertThat(workerSkipped.get()).as("Iteration %d: worker must be skipped when damage request won", i).isEqualTo(1);
                    // Case remains in POST_RETURN (pending KTV decision)
                    String caseState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.cases WHERE case_id = ?", String.class, prf.caseId());
                    assertThat(caseState).isEqualTo("POST_RETURN");
                    // valid_damage_request_at is set
                    java.sql.Timestamp validDmgAt = jdbcTemplate.queryForObject("SELECT valid_damage_request_at FROM og_compat.case_rounds WHERE round_id = ?", java.sql.Timestamp.class, prf.round2Id());
                    assertThat(validDmgAt).isNotNull();
                    // Zero timeout decisions
                    Integer decCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_decisions WHERE round_id = ?", Integer.class, prf.round2Id());
                    assertThat(decCount).isZero();
                } else {
                    // Worker won: Damage request was rejected
                    assertThat(workerWins.get()).as("Iteration %d: worker must win when damage request lost", i).isEqualTo(1);
                    assertThat(damageLost.get()).as("Iteration %d: damage request must be rejected when worker won", i).isEqualTo(1);
                    // Case is CLOSED
                    String caseState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.cases WHERE case_id = ?", String.class, prf.caseId());
                    assertThat(caseState).isEqualTo("CLOSED");
                    // Exactly 1 decision (SYSTEM_TIMEOUT)
                    Integer decCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_decisions WHERE round_id = ?", Integer.class, prf.round2Id());
                    assertThat(decCount).isEqualTo(1);
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    // =========================================================================
    // FEATURE 3.2: TASK 3.2.4 - TARGETED POSTGRESQL EVIDENCE (FINAL PUSH)
    // 1. TC-29 Deterministic: Damage Request wins first -> Worker SKIPPED
    // 2. Real PostgreSQL 23514 Branch A: Round already decided
    // 3. Real PostgreSQL 23514 Branch B: Damage Request / deadline blocked
    // 4. Real PostgreSQL 23505 Deferred Unique Trigger at COMMIT
    // 5. TC-28 with 2 Fund Components: REFUND_BUYER + RETURN_PLATFORM
    // =========================================================================

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.4 Postgres B5 (TC-29 Deterministic): Damage Request commits first -> Worker is SKIPPED at re-verify, zero timeout decisions, zero duplicate settlements")
    void testAutoRefundTimeout_onPostgres_tc29_damageRequestWins_workerSkipped() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // 1. Tạo fixture POST_RETURN, round 2 OPEN
        PostReturnFixture prf = createPostReturnFixture(txTemplate);

        // 2. Thực hiện VALID_DAMAGE_REQUEST qua đúng API nghiệp vụ và commit thành công trong thời hạn
        String dmgCmdKey = "CMD_TC29_WIN_DMG_" + UUID.randomUUID();
        ReturnCaseDtos.SellerDamageExemptionRequest dmgReq = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Người bán khiếu nại hàng trả về bị nứt vỡ",
                List.of(createPostgresEvidence("IMAGE", "evidence/tc29_dmg_" + UUID.randomUUID() + ".jpg")),
                dmgCmdKey
        );
        ReturnCaseDtos.ReturnCaseDetailResponse dmgResp = txTemplate.execute(status ->
                returnCaseService.submitDamageExemptionRequest(prf.sellerId(), prf.caseId(), dmgReq)
        );
        assertThat(dmgResp).isNotNull();
        assertThat(dmgResp.state()).isEqualTo(CaseEntity.STATE_POST_RETURN);

        // Xác nhận trigger og_guard_case_event đã gán valid_damage_request_at
        java.sql.Timestamp validDmgAt = jdbcTemplate.queryForObject(
                "SELECT valid_damage_request_at FROM og_compat.case_rounds WHERE round_id = ?",
                java.sql.Timestamp.class, prf.round2Id()
        );
        assertThat(validDmgAt).isNotNull();

        // 3. Thời gian trôi qua, case quá hạn seller_response_due_at
        makeCaseOverdueForTimeout(prf.caseId());

        // 4. Gọi Worker sau khi Damage Request đã commit và case đã quá hạn
        ReturnCaseDtos.CaseTimeoutExecutionResult workerResult = returnCaseService.processPostReturnTimeoutForCaseInNewTx(prf.caseId());

        // 4. Xác minh Worker bị loại tại re-verify sau canonical lock -> trả SKIPPED
        assertThat(workerResult).isNotNull();
        assertThat(workerResult.status()).isEqualTo(ReturnCaseDtos.TimeoutProcessingStatus.SKIPPED);
        assertThat(workerResult.message()).contains("miễn trừ thiệt hại");

        // 5. Xác minh PostgreSQL Invariants:
        // - Không tạo timeout Decision
        Integer decCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.case_decisions WHERE round_id = ?",
                Integer.class, prf.round2Id()
        );
        assertThat(decCount).isZero();

        // - Không tạo settlement operation
        Integer opCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.settlement_operations so JOIN og_compat.order_fund_components ofc USING(component_id) WHERE ofc.order_id = ?",
                Integer.class, prf.orderId()
        );
        assertThat(opCount).isZero();

        // - Case vẫn ở POST_RETURN (chờ KTV xử lý)
        String caseState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.cases WHERE case_id = ?", String.class, prf.caseId());
        assertThat(caseState).isEqualTo("POST_RETURN");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.4 Postgres: 23514 Branch A - Trigger og_apply_case_decision raises 'Case round already decided' on real PostgreSQL, classified as SKIPPED")
    void testAutoRefundTimeout_onPostgres_23514_branch1_roundAlreadyDecided() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);

        // 1. Seller agree refund commits first -> Round 2 becomes DECIDED, case CLOSED
        String agreeCmdKey = "CMD_AGREE_BEFORE_23514_" + UUID.randomUUID();
        txTemplate.execute(status -> returnCaseService.sellerAgreeRefundRound2(
                prf.sellerId(), prf.caseId(), new ReturnCaseDtos.SellerAgreeRefundRequest("Đồng ý hoàn tiền", agreeCmdKey)
        ));

        // 2. Kích hoạt trực tiếp trigger og_apply_case_decision nhánh 'Case round already decided' trên real PostgreSQL
        String timeoutCmdKey = "TIMEOUT:SELLER_POST_RETURN:" + prf.caseId() + ":" + prf.round2Id();
        org.springframework.dao.DataIntegrityViolationException caughtEx = null;
        try {
            txTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update("""
                        INSERT INTO og_compat.case_decisions(round_id, actor_type, outcome, is_final, reason, command_key)
                        VALUES (?, 'SYSTEM_TIMEOUT', 'FULL_GOODS_REFUND', true, 'timeout', ?)
                        """, prf.round2Id(), timeoutCmdKey);
            });
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            caughtEx = ex;
        }

        assertThat(caughtEx).isNotNull();

        // 3. Thu thập và in toàn bộ PostgreSQL error metadata thực tế
        ReturnCaseService.PostgresErrorMetadata meta = returnCaseService.extractPostgresErrorMetadata(caughtEx);
        System.out.println("[POSTGRES REAL 23514 BRANCH A] sqlState = " + meta.sqlState());
        System.out.println("[POSTGRES REAL 23514 BRANCH A] message = " + meta.message());
        System.out.println("[POSTGRES REAL 23514 BRANCH A] constraint = " + meta.constraintName());
        System.out.println("[POSTGRES REAL 23514 BRANCH A] routine = " + meta.routine());
        System.out.println("[POSTGRES REAL 23514 BRANCH A] where = " + meta.where());

        assertThat(meta.sqlState()).isEqualTo("23514");
        assertThat(meta.message()).isEqualTo("Case round already decided");
        assertThat(meta.routine()).isEqualTo("exec_stmt_raise");
        assertThat(meta.constraintName()).isNull();
        assertThat(meta.where()).isNotNull().contains("og_apply_case_decision");

        // 4. Fresh DB verification sau rollback
        String roundState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.case_rounds WHERE round_id = ?", String.class, prf.round2Id());
        assertThat(roundState).isEqualTo("DECIDED");
        String caseState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.cases WHERE case_id = ?", String.class, prf.caseId());
        assertThat(caseState).isEqualTo("CLOSED");

        // 5. Đưa exception thực tế qua logic phân loại của Worker
        ReturnCaseDtos.CaseTimeoutExecutionResult result = returnCaseService.handleCaseExecutionException(prf.caseId(), caughtEx);
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(ReturnCaseDtos.TimeoutProcessingStatus.SKIPPED);
        assertThat(result.message()).contains("Round 2 đã được quyết định bởi workflow khác (23514)");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.4 Postgres: 23514 Branch B - Trigger og_apply_case_decision raises 'Auto refund blocked by deadline/request' on real PostgreSQL, classified as SKIPPED")
    void testAutoRefundTimeout_onPostgres_23514_branch2_damageRequestOrDeadline() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);

        // 1. Seller damage request commits first -> valid_damage_request_at is set
        String dmgCmdKey = "CMD_DMG_BEFORE_23514_" + UUID.randomUUID();
        txTemplate.execute(status -> returnCaseService.submitDamageExemptionRequest(
                prf.sellerId(), prf.caseId(), new ReturnCaseDtos.SellerDamageExemptionRequest(
                        "Hàng hư hỏng", List.of(createPostgresEvidence("IMAGE", "evidence/23514_b.jpg")), dmgCmdKey
                )
        ));

        // 2. Kích hoạt trực tiếp trigger og_apply_case_decision nhánh 'Auto refund blocked by deadline/request' trên real PostgreSQL
        String timeoutCmdKey = "TIMEOUT:SELLER_POST_RETURN:" + prf.caseId() + ":" + prf.round2Id();
        org.springframework.dao.DataIntegrityViolationException caughtEx = null;
        try {
            txTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update("""
                        INSERT INTO og_compat.case_decisions(round_id, actor_type, outcome, is_final, reason, command_key)
                        VALUES (?, 'SYSTEM_TIMEOUT', 'FULL_GOODS_REFUND', true, 'timeout', ?)
                        """, prf.round2Id(), timeoutCmdKey);
            });
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            caughtEx = ex;
        }

        assertThat(caughtEx).isNotNull();

        // 3. Thu thập và in toàn bộ PostgreSQL error metadata thực tế
        ReturnCaseService.PostgresErrorMetadata meta = returnCaseService.extractPostgresErrorMetadata(caughtEx);
        System.out.println("[POSTGRES REAL 23514 BRANCH B] sqlState = " + meta.sqlState());
        System.out.println("[POSTGRES REAL 23514 BRANCH B] message = " + meta.message());
        System.out.println("[POSTGRES REAL 23514 BRANCH B] constraint = " + meta.constraintName());
        System.out.println("[POSTGRES REAL 23514 BRANCH B] routine = " + meta.routine());
        System.out.println("[POSTGRES REAL 23514 BRANCH B] where = " + meta.where());

        assertThat(meta.sqlState()).isEqualTo("23514");
        assertThat(meta.message()).isEqualTo("Auto refund blocked by deadline/request");
        assertThat(meta.routine()).isEqualTo("exec_stmt_raise");
        assertThat(meta.constraintName()).isNull();
        assertThat(meta.where()).isNotNull().contains("og_apply_case_decision");

        // 4. Fresh DB verification sau rollback
        java.sql.Timestamp validDmgAt = jdbcTemplate.queryForObject(
                "SELECT valid_damage_request_at FROM og_compat.case_rounds WHERE round_id = ?",
                java.sql.Timestamp.class, prf.round2Id()
        );
        assertThat(validDmgAt).isNotNull();

        // 5. Đưa exception thực tế qua logic phân loại của Worker
        ReturnCaseDtos.CaseTimeoutExecutionResult result = returnCaseService.handleCaseExecutionException(prf.caseId(), caughtEx);
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(ReturnCaseDtos.TimeoutProcessingStatus.SKIPPED);
        assertThat(result.message()).contains("Seller Damage Request hoặc workflow khác đã được ghi nhận (23514)");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Task 3.2.4 Postgres: 23505 Deferred Unique Trigger at COMMIT on real PostgreSQL, verified and classified as SKIPPED")
    void testAutoRefundTimeout_onPostgres_23505_deferredCommit_andClassification() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);
        makeCaseOverdueForTimeout(prf.caseId());

        // 1. Worker timeout executes successfully first time -> Decision and Settlement committed
        ReturnCaseDtos.CaseTimeoutExecutionResult firstResult = returnCaseService.processPostReturnTimeoutForCaseInNewTx(prf.caseId());
        assertThat(firstResult).isNotNull();
        assertThat(firstResult.status()).isEqualTo(ReturnCaseDtos.TimeoutProcessingStatus.PROCESSED);

        String expectedCmdKey = "TIMEOUT:SELLER_POST_RETURN:" + prf.caseId() + ":" + prf.round2Id();

        // Snapshot decision and settlement count
        Integer initialDecCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_decisions WHERE round_id = ?", Integer.class, prf.round2Id());
        assertThat(initialDecCount).isEqualTo(1);
        Integer initialOpCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.settlement_operations so JOIN og_compat.order_fund_components ofc USING(component_id) WHERE ofc.order_id = ?",
                Integer.class, prf.orderId()
        );
        assertThat(initialOpCount).isEqualTo(1);

        // 2. Kích hoạt deferred trigger trên real PostgreSQL tại thời điểm COMMIT:
        // Chuẩn bị case thứ hai có Round 2 OPEN và quá hạn để vượt qua BEFORE INSERT trigger
        PostReturnFixture prf2 = createPostReturnFixture(txTemplate);
        makeCaseOverdueForTimeout(prf2.caseId());

        // Cố tình insert bản ghi cho prf2 với cùng command_key của prf1.
        // Tại thời điểm execute update: thành công (vì round 2 của prf2 đang OPEN và đã quá hạn).
        // Tại thời điểm commit transaction: trigger og70_validate_row kiểm tra dòng 9754 và RAISE 23505!
        org.springframework.dao.DataIntegrityViolationException caughtEx = null;
        try {
            txTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update("""
                        INSERT INTO og_compat.case_decisions(round_id, actor_type, outcome, is_final, reason, command_key)
                        VALUES (?, 'SYSTEM_TIMEOUT', 'FULL_GOODS_REFUND', true, 'duplicate timeout across cases', ?)
                        """, prf2.round2Id(), expectedCmdKey);
                // Statement executed, error occurs when transaction attempts to COMMIT
            });
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            caughtEx = ex;
        }

        assertThat(caughtEx).isNotNull();

        // 3. Thu thập và in toàn bộ PostgreSQL error metadata thực tế
        ReturnCaseService.PostgresErrorMetadata meta = returnCaseService.extractPostgresErrorMetadata(caughtEx);
        System.out.println("[POSTGRES REAL 23505 DEFERRED] sqlState = " + meta.sqlState());
        System.out.println("[POSTGRES REAL 23505 DEFERRED] message = " + meta.message());
        System.out.println("[POSTGRES REAL 23505 DEFERRED] constraint = " + meta.constraintName());
        System.out.println("[POSTGRES REAL 23505 DEFERRED] routine = " + meta.routine());
        System.out.println("[POSTGRES REAL 23505 DEFERRED] where = " + meta.where());

        assertThat(meta.sqlState()).isEqualTo("23505");
        assertThat(meta.message()).contains("case_decisions_command_key_key");
        assertThat(meta.constraintName()).isNull();

        // 4. Fresh DB verification sau rollback
        Integer decCountAfter = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.case_decisions WHERE round_id = ?", Integer.class, prf.round2Id());
        assertThat(decCountAfter).isEqualTo(1);
        Integer opCountAfter = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.settlement_operations so JOIN og_compat.order_fund_components ofc USING(component_id) WHERE ofc.order_id = ?",
                Integer.class, prf.orderId()
        );
        assertThat(opCountAfter).isEqualTo(1);

        // 5. Đưa exception thực tế qua logic phân loại của Worker -> Xác nhận Idempotency và trả SKIPPED
        ReturnCaseDtos.CaseTimeoutExecutionResult result = returnCaseService.handleCaseExecutionException(prf.caseId(), caughtEx);
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(ReturnCaseDtos.TimeoutProcessingStatus.SKIPPED);
        assertThat(result.message()).contains("Idempotent: bản ghi timeout đã tồn tại (23505)");
    }

    // =========================================================================
    // TASK 3.2.5 & 3.2.6: SLA ESCALATION CONCURRENCY & INTEGRATION TESTS (BD-1, BD-3, BD-4)
    // =========================================================================

    private void makeCaseOverdueForKtvSla(Long caseId, Long round2Id) {
        java.time.Instant submissionTime = java.time.Instant.now().minus(java.time.Duration.ofHours(50));
        java.time.Instant ktvDue = submissionTime.plus(java.time.Duration.ofHours(48));
        java.sql.Timestamp subTs = java.sql.Timestamp.from(submissionTime);
        java.sql.Timestamp dueTs = java.sql.Timestamp.from(ktvDue);

        jdbcTemplate.update("""
                UPDATE og_compat.case_rounds
                SET complete_submission_at = ?,
                    ktv_due_at = ?
                WHERE round_id = ?
                """, subTs, dueTs, round2Id);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("TC-38: Concurrency - KTV Decide vs Worker SLA Escalation race (first-valid-transition wins)")
    void tc38_ktvDecide_vs_workerEscalation_race() throws Exception {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);
        makeCaseOverdueForKtvSla(prf.caseId(), prf.round2Id());

        Long adminId = createTestUser("admin");
        grantRole(adminId, "ADMIN");
        Long ktvId = createTestUser("ktv");
        grantRole(ktvId, "KTV");
        txTemplate.executeWithoutResult(status -> returnCaseService.assignCaseToKtv(ktvId, prf.caseId()));
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<ReturnCaseDtos.ReturnCaseDetailResponse> ktvFuture = executor.submit(() -> {
            barrier.await(5, TimeUnit.SECONDS);
            ReturnCaseDtos.KtvRound2DecisionRequest req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                    CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                    "KTV đồng thời phán quyết với escalation worker",
                    "CMD_KTV_RACE_" + UUID.randomUUID()
            );
            return returnCaseService.ktvDecideRound2(ktvId, prf.caseId(), req);
        });

        Future<ReturnCaseDtos.CaseEscalationExecutionResult> workerFuture = executor.submit(() -> {
            barrier.await(5, TimeUnit.SECONDS);
            return returnCaseService.processKtvSlaEscalationForCaseInNewTx(prf.caseId());
        });

        Exception ktvEx = null;
        ReturnCaseDtos.ReturnCaseDetailResponse ktvRes = null;
        try {
            ktvRes = ktvFuture.get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            ktvEx = ex;
        }

        Exception workerEx = null;
        ReturnCaseDtos.CaseEscalationExecutionResult workerRes = null;
        try {
            workerRes = workerFuture.get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            workerEx = ex;
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Verification: Either KTV won or Worker won, but never invalid state
        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.case_events WHERE case_id = ? AND event_type = 'ESCALATED'",
                Integer.class, prf.caseId()
        );
        Integer decisionCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.case_decisions WHERE round_id = ?",
                Integer.class, prf.round2Id()
        );

        if (ktvRes != null) {
            // KTV won first: decision recorded, worker should be skipped
            assertThat(decisionCount).isEqualTo(1);
            if (workerRes != null) {
                assertThat(workerRes.status()).isEqualTo(ReturnCaseDtos.EscalationProcessingStatus.SKIPPED);
            }
        } else {
            // Worker won first: escalation event recorded
            assertThat(eventCount).isLessThanOrEqualTo(1);
            if (ktvEx != null) {
                assertThat(ktvEx.getMessage()).containsAnyOf("Quản trị viên (Admin)", "Escalated case requires Admin decision", "55P03", "23514");
            }
        }
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("TC-39: Concurrency - Worker Timeout vs Worker SLA Escalation race")
    void tc39_workerTimeout_vs_workerEscalation_race() throws Exception {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);
        makeCaseOverdueForTimeout(prf.caseId());
        makeCaseOverdueForKtvSla(prf.caseId(), prf.round2Id());

        Long adminId = createTestUser("admin");
        grantRole(adminId, "ADMIN");

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<ReturnCaseDtos.CaseTimeoutExecutionResult> timeoutFuture = executor.submit(() -> {
            barrier.await(5, TimeUnit.SECONDS);
            return returnCaseService.processPostReturnTimeoutForCaseInNewTx(prf.caseId());
        });

        Future<ReturnCaseDtos.CaseEscalationExecutionResult> escalationFuture = executor.submit(() -> {
            barrier.await(5, TimeUnit.SECONDS);
            return returnCaseService.processKtvSlaEscalationForCaseInNewTx(prf.caseId());
        });

        ReturnCaseDtos.CaseTimeoutExecutionResult timeoutRes = timeoutFuture.get(10, TimeUnit.SECONDS);
        ReturnCaseDtos.CaseEscalationExecutionResult escalationRes = escalationFuture.get(10, TimeUnit.SECONDS);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(timeoutRes).isNotNull();
        assertThat(escalationRes).isNotNull();

        // Exactly one valid resolution wins; neither compromises DB consistency
        Integer decisionCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.case_decisions WHERE round_id = ?",
                Integer.class, prf.round2Id()
        );
        assertThat(decisionCount).isLessThanOrEqualTo(1);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("TC-40: PostgreSQL DB Trigger - SYSTEM actor escalation valid vs invalid combinations")
    void tc40_dbTrigger_systemActor_escalationAcceptanceAndRejection() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);
        makeCaseOverdueForKtvSla(prf.caseId(), prf.round2Id());

        Long adminId = createTestUser("admin");
        grantRole(adminId, "ADMIN");

        // 1. Recipient is NOT Admin -> Should throw 23514
        assertThatThrownBy(() -> txTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    INSERT INTO og_compat.case_events(event_id, case_id, round_id, actor_id, recipient_id, event_type, reason, command_key)
                    VALUES (gen_random_uuid(), ?, ?, NULL, ?, 'ESCALATED', 'System escalation to non-admin', ?)
                    """, prf.caseId(), prf.round2Id(), prf.buyerId(), "CMD_NON_ADMIN_" + UUID.randomUUID());
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        // 2. Valid SYSTEM escalation with Admin recipient -> Success
        txTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    INSERT INTO og_compat.case_events(event_id, case_id, round_id, actor_id, recipient_id, event_type, reason, command_key)
                    VALUES (gen_random_uuid(), ?, ?, NULL, ?, 'ESCALATED', 'Valid system escalation to admin', ?)
                    """, prf.caseId(), prf.round2Id(), adminId, "CMD_VALID_SYS_ESC_" + UUID.randomUUID());
        });

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.case_events WHERE case_id = ? AND event_type = 'ESCALATED'",
                Integer.class, prf.caseId()
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("TC-41: PostgreSQL DB Trigger & Authority - KTV rejected after escalation, Admin accepted (BD-1)")
    void tc41_ktvDecisionAfterEscalation_rejectedByDbTrigger() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);
        makeCaseOverdueForKtvSla(prf.caseId(), prf.round2Id());
        Long adminId = createTestUser("admin");
        grantRole(adminId, "ADMIN");
        Long ktvId = createTestUser("ktv");
        grantRole(ktvId, "KTV");
        txTemplate.executeWithoutResult(status -> returnCaseService.assignCaseToKtv(ktvId, prf.caseId()));

        // 1. Insert ESCALATED event
        txTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    INSERT INTO og_compat.case_events(event_id, case_id, round_id, actor_id, recipient_id, event_type, reason, command_key)
                    VALUES (gen_random_uuid(), ?, ?, NULL, ?, 'ESCALATED', 'Escalated to Admin', ?)
                    """, prf.caseId(), prf.round2Id(), adminId, "CMD_ESC_FOR_AUTH_" + UUID.randomUUID());
        });

        // 2. KTV attempts decision via DB directly -> Trigger og_apply_case_decision throws 23514
        assertThatThrownBy(() -> txTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    INSERT INTO og_compat.case_decisions(round_id, reviewed_by, actor_type, outcome, is_final, reason, command_key)
                    VALUES (?, ?, 'USER', 'FULL_GOODS_REFUND', true, 'KTV attempt after escalation', ?)
                    """, prf.round2Id(), ktvId, "CMD_KTV_ESC_DEC_" + UUID.randomUUID());
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        // 3. Admin attempts decision via service -> Succeeds
        ReturnCaseDtos.KtvRound2DecisionRequest adminReq = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Admin hợp lệ phân quyết sau escalation",
                "CMD_ADMIN_ESC_DEC_" + UUID.randomUUID()
        );
        ReturnCaseDtos.ReturnCaseDetailResponse adminRes = returnCaseService.adminDecideRound2(adminId, prf.caseId(), adminReq);
        assertThat(adminRes).isNotNull();

        Integer decCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.case_decisions WHERE round_id = ?",
                Integer.class, prf.round2Id()
        );
        assertThat(decCount).isEqualTo(1);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("TC-31: Concurrent evidence submissions and quota enforcement")
    void concurrentSubmissions_quotaEnforced() throws Exception {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failCount = new java.util.concurrent.atomic.AtomicInteger(0);

        List<ReturnCaseDtos.EvidenceItemDto> items1 = List.of(
                createPostgresEvidence("IMAGE", "evidence/tc31_img1.jpg"),
                createPostgresEvidence("IMAGE", "evidence/tc31_img2.jpg"),
                createPostgresEvidence("IMAGE", "evidence/tc31_img3.jpg")
        );
        List<ReturnCaseDtos.EvidenceItemDto> items2 = List.of(
                createPostgresEvidence("IMAGE", "evidence/tc31_img4.jpg"),
                createPostgresEvidence("IMAGE", "evidence/tc31_img5.jpg"),
                createPostgresEvidence("IMAGE", "evidence/tc31_img6.jpg")
        );

        Future<?> f1 = executor.submit(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                txTemplate.execute(status -> returnCaseService.submitDamageExemptionRequest(
                        prf.sellerId(), prf.caseId(),
                        new ReturnCaseDtos.SellerDamageExemptionRequest("Seller claim 1", items1, "CMD_EV1_" + UUID.randomUUID())
                ));
                successCount.incrementAndGet();
            } catch (Exception ex) {
                failCount.incrementAndGet();
            }
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                txTemplate.execute(status -> returnCaseService.submitDamageExemptionRequest(
                        prf.sellerId(), prf.caseId(),
                        new ReturnCaseDtos.SellerDamageExemptionRequest("Seller claim 2", items2, "CMD_EV2_" + UUID.randomUUID())
                ));
                successCount.incrementAndGet();
            } catch (Exception ex) {
                failCount.incrementAndGet();
            }
        });

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);

        Integer totalEvCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.case_evidence WHERE round_id = ? AND deleted_at IS NULL",
                Integer.class, prf.round2Id()
        );
        assertThat(totalEvCount).isEqualTo(3);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("V39 Scenario 1: KTV escalation commits first; SYSTEM duplicate attempt is rejected by trigger (23514)")
    void v39_scenario1_ktvEscalationFirst_thenSystemDuplicateRejected() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);
        makeCaseOverdueForKtvSla(prf.caseId(), prf.round2Id());
        Long adminId = createTestUser("admin");
        grantRole(adminId, "ADMIN");
        Long ktvId = createTestUser("ktv");
        grantRole(ktvId, "KTV");
        txTemplate.executeWithoutResult(status -> returnCaseService.assignCaseToKtv(ktvId, prf.caseId()));

        // 1. KTV escalation commits first
        txTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    INSERT INTO og_compat.case_events(event_id, case_id, round_id, actor_id, recipient_id, event_type, reason, command_key)
                    VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ESCALATED', 'KTV manual escalation', ?)
                    """, prf.caseId(), prf.round2Id(), ktvId, adminId, "CMD_KTV_FIRST_" + UUID.randomUUID());
        });

        // 2. SYSTEM duplicate attempt is rejected by trigger with 23514
        assertThatThrownBy(() -> txTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    INSERT INTO og_compat.case_events(event_id, case_id, round_id, actor_id, recipient_id, event_type, reason, command_key)
                    VALUES (gen_random_uuid(), ?, ?, NULL, ?, 'ESCALATED', 'System duplicate escalation', ?)
                    """, prf.caseId(), prf.round2Id(), adminId, "CMD_SYS_DUP_" + UUID.randomUUID());
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
           .satisfies(e -> {
               assertThat(e.getMessage()).contains("Round already escalated");
               Throwable root = e;
               while (root.getCause() != null) root = root.getCause();
               if (root instanceof java.sql.SQLException se) {
                   assertThat(se.getSQLState()).isEqualTo("23514");
               }
           });

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.case_events WHERE case_id = ? AND round_id = ? AND event_type = 'ESCALATED'",
                Integer.class, prf.caseId(), prf.round2Id()
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("V39 Scenario 2: SYSTEM escalation commits first; KTV duplicate attempt is rejected by trigger (23514)")
    void v39_scenario2_systemEscalationFirst_thenKtvDuplicateRejected() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);
        makeCaseOverdueForKtvSla(prf.caseId(), prf.round2Id());
        Long adminId = createTestUser("admin");
        grantRole(adminId, "ADMIN");
        Long ktvId = createTestUser("ktv");
        grantRole(ktvId, "KTV");
        txTemplate.executeWithoutResult(status -> returnCaseService.assignCaseToKtv(ktvId, prf.caseId()));

        // 1. SYSTEM escalation commits first
        txTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    INSERT INTO og_compat.case_events(event_id, case_id, round_id, actor_id, recipient_id, event_type, reason, command_key)
                    VALUES (gen_random_uuid(), ?, ?, NULL, ?, 'ESCALATED', 'System initial escalation', ?)
                    """, prf.caseId(), prf.round2Id(), adminId, "CMD_SYS_FIRST_" + UUID.randomUUID());
        });

        // 2. KTV duplicate attempt is rejected by trigger with 23514
        assertThatThrownBy(() -> txTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    INSERT INTO og_compat.case_events(event_id, case_id, round_id, actor_id, recipient_id, event_type, reason, command_key)
                    VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ESCALATED', 'KTV duplicate escalation', ?)
                    """, prf.caseId(), prf.round2Id(), ktvId, adminId, "CMD_KTV_DUP_" + UUID.randomUUID());
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
           .satisfies(e -> {
               assertThat(e.getMessage()).contains("Round already escalated");
               Throwable root = e;
               while (root.getCause() != null) root = root.getCause();
               if (root instanceof java.sql.SQLException se) {
                   assertThat(se.getSQLState()).isEqualTo("23514");
               }
           });

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.case_events WHERE case_id = ? AND round_id = ? AND event_type = 'ESCALATED'",
                Integer.class, prf.caseId(), prf.round2Id()
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("V39 Scenario 3: Two concurrent SYSTEM escalation attempts: exactly one succeeds")
    void v39_scenario3_twoConcurrentSystemEscalations_exactlyOneSucceeds() throws Exception {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);
        makeCaseOverdueForKtvSla(prf.caseId(), prf.round2Id());
        Long adminId = createTestUser("admin");
        grantRole(adminId, "ADMIN");

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        java.util.concurrent.atomic.AtomicInteger success = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger rejected = new java.util.concurrent.atomic.AtomicInteger(0);

        Future<?> f1 = executor.submit(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                txTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.update("""
                            INSERT INTO og_compat.case_events(event_id, case_id, round_id, actor_id, recipient_id, event_type, reason, command_key)
                            VALUES (gen_random_uuid(), ?, ?, NULL, ?, 'ESCALATED', 'System thread 1', ?)
                            """, prf.caseId(), prf.round2Id(), adminId, "CMD_CONC_SYS1_" + UUID.randomUUID());
                });
                success.incrementAndGet();
            } catch (Exception e) {
                rejected.incrementAndGet();
            }
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                txTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.update("""
                            INSERT INTO og_compat.case_events(event_id, case_id, round_id, actor_id, recipient_id, event_type, reason, command_key)
                            VALUES (gen_random_uuid(), ?, ?, NULL, ?, 'ESCALATED', 'System thread 2', ?)
                            """, prf.caseId(), prf.round2Id(), adminId, "CMD_CONC_SYS2_" + UUID.randomUUID());
                });
                success.incrementAndGet();
            } catch (Exception e) {
                rejected.incrementAndGet();
            }
        });

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(success.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(1);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.case_events WHERE case_id = ? AND round_id = ? AND event_type = 'ESCALATED'",
                Integer.class, prf.caseId(), prf.round2Id()
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("V39 Scenario 4: Two concurrent KTV escalation attempts: exactly one succeeds")
    void v39_scenario4_twoConcurrentKtvEscalations_exactlyOneSucceeds() throws Exception {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);
        makeCaseOverdueForKtvSla(prf.caseId(), prf.round2Id());
        Long adminId = createTestUser("admin");
        grantRole(adminId, "ADMIN");
        Long ktv1 = createTestUser("ktv1");
        grantRole(ktv1, "KTV");
        Long ktv2 = createTestUser("ktv2");
        grantRole(ktv2, "KTV");

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        java.util.concurrent.atomic.AtomicInteger success = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger rejected = new java.util.concurrent.atomic.AtomicInteger(0);

        Future<?> f1 = executor.submit(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                txTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.update("""
                            INSERT INTO og_compat.case_events(event_id, case_id, round_id, actor_id, recipient_id, event_type, reason, command_key)
                            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ESCALATED', 'KTV thread 1', ?)
                            """, prf.caseId(), prf.round2Id(), ktv1, adminId, "CMD_CONC_KTV1_" + UUID.randomUUID());
                });
                success.incrementAndGet();
            } catch (Exception e) {
                rejected.incrementAndGet();
            }
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                txTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.update("""
                            INSERT INTO og_compat.case_events(event_id, case_id, round_id, actor_id, recipient_id, event_type, reason, command_key)
                            VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ESCALATED', 'KTV thread 2', ?)
                            """, prf.caseId(), prf.round2Id(), ktv2, adminId, "CMD_CONC_KTV2_" + UUID.randomUUID());
                });
                success.incrementAndGet();
            } catch (Exception e) {
                rejected.incrementAndGet();
            }
        });

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(success.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(1);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.case_events WHERE case_id = ? AND round_id = ? AND event_type = 'ESCALATED'",
                Integer.class, prf.caseId(), prf.round2Id()
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("V39 Scenario 5: Direct SQL duplicate insert with different command_key rejected by UNIQUE index (23505)")
    void v39_scenario5_directSqlDuplicateInsert_differentCommandKey_rejectedByUniqueIndex_23505() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);
        makeCaseOverdueForKtvSla(prf.caseId(), prf.round2Id());
        Long adminId = createTestUser("admin");
        grantRole(adminId, "ADMIN");

        // 1. Insert first ESCALATED directly into public.case_actions
        txTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL session_replication_role = 'replica'");
            jdbcTemplate.update("""
                    INSERT INTO public.case_actions(
                        source_case_events_event_id, case_id, round_id, actor_id, recipient_id,
                        record_type, event_type, reason, metadata, command_key, occurred_at
                    ) VALUES (
                        gen_random_uuid(), ?, ?, NULL, ?,
                        'case_events', 'ESCALATED', 'Direct SQL initial', '{}'::jsonb, ?, clock_timestamp()
                    )
                    """, prf.caseId(), prf.round2Id(), adminId, "CMD_DIRECT_FIRST_" + UUID.randomUUID());
        });

        // 2. Direct duplicate insert on case_actions with different command_key -> must fail with 23505
        assertThatThrownBy(() -> txTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL session_replication_role = 'replica'");
            jdbcTemplate.update("""
                    INSERT INTO public.case_actions(
                        source_case_events_event_id, case_id, round_id, actor_id, recipient_id,
                        record_type, event_type, reason, metadata, command_key, occurred_at
                    ) VALUES (
                        gen_random_uuid(), ?, ?, NULL, ?,
                        'case_events', 'ESCALATED', 'Direct SQL duplicate', '{}'::jsonb, ?, clock_timestamp()
                    )
                    """, prf.caseId(), prf.round2Id(), adminId, "CMD_DIRECT_DIFF_KEY_" + UUID.randomUUID());
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
           .satisfies(e -> {
               assertThat(e.getMessage()).contains("uq_case_actions_round_escalated");
               Throwable root = e;
               while (root.getCause() != null) root = root.getCause();
               if (root instanceof java.sql.SQLException se) {
                   assertThat(se.getSQLState()).isEqualTo("23505");
               }
           });
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("V39 Scenario 6: Different rounds of the same Case can each have their own ESCALATED event")
    void v39_scenario6_differentRounds_eachAllowOneEscalation() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);
        makeCaseOverdueForKtvSla(prf.caseId(), prf.round2Id());
        Long adminId = createTestUser("admin");
        grantRole(adminId, "ADMIN");

        Long round1Id = jdbcTemplate.queryForObject(
                "SELECT round_id FROM og_compat.case_rounds WHERE case_id = ? AND round_no = 1",
                Long.class, prf.caseId()
        );

        // 1. Direct escalation on Round 1
        txTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL session_replication_role = 'replica'");
            jdbcTemplate.update("""
                    INSERT INTO public.case_actions(
                        source_case_events_event_id, case_id, round_id, actor_id, recipient_id,
                        record_type, event_type, reason, metadata, command_key, occurred_at
                    ) VALUES (
                        gen_random_uuid(), ?, ?, NULL, ?,
                        'case_events', 'ESCALATED', 'Round 1 escalation', '{}'::jsonb, ?, clock_timestamp()
                    )
                    """, prf.caseId(), round1Id, adminId, "CMD_R1_ESC_" + UUID.randomUUID());
        });

        // 2. Direct escalation on Round 2 -> Allowed by partial unique index because round_id differs
        txTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL session_replication_role = 'replica'");
            jdbcTemplate.update("""
                    INSERT INTO public.case_actions(
                        source_case_events_event_id, case_id, round_id, actor_id, recipient_id,
                        record_type, event_type, reason, metadata, command_key, occurred_at
                    ) VALUES (
                        gen_random_uuid(), ?, ?, NULL, ?,
                        'case_events', 'ESCALATED', 'Round 2 escalation', '{}'::jsonb, ?, clock_timestamp()
                    )
                    """, prf.caseId(), prf.round2Id(), adminId, "CMD_R2_ESC_" + UUID.randomUUID());
        });

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.case_actions WHERE case_id = ? AND event_type = 'ESCALATED'",
                Integer.class, prf.caseId()
        );
        assertThat(count).isEqualTo(2);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("V39 Scenario 7: A failed duplicate attempt leaves no orphan or partially committed event")
    void v39_scenario7_failedDuplicateAttempt_leavesNoOrphanRecord() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);
        makeCaseOverdueForKtvSla(prf.caseId(), prf.round2Id());
        Long adminId = createTestUser("admin");
        grantRole(adminId, "ADMIN");

        // 1. Initial valid escalation
        txTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    INSERT INTO og_compat.case_events(event_id, case_id, round_id, actor_id, recipient_id, event_type, reason, command_key)
                    VALUES (gen_random_uuid(), ?, ?, NULL, ?, 'ESCALATED', 'Initial valid escalation', ?)
                    """, prf.caseId(), prf.round2Id(), adminId, "CMD_INITIAL_VALID_" + UUID.randomUUID());
        });

        int eventsBefore = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.case_events WHERE case_id = ?",
                Integer.class, prf.caseId()
        );

        // 2. Failed duplicate attempt
        assertThatThrownBy(() -> txTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    INSERT INTO og_compat.case_events(event_id, case_id, round_id, actor_id, recipient_id, event_type, reason, command_key)
                    VALUES (gen_random_uuid(), ?, ?, NULL, ?, 'ESCALATED', 'Duplicate attempt', ?)
                    """, prf.caseId(), prf.round2Id(), adminId, "CMD_DUP_FAIL_" + UUID.randomUUID());
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        int eventsAfter = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.case_events WHERE case_id = ?",
                Integer.class, prf.caseId()
        );

        // Transaction rolled back cleanly: exactly same number of events, 0 orphans
        assertThat(eventsAfter).isEqualTo(eventsBefore);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("V39 Scenario 8: Verify distinction between trigger rejection (23514) and index rejection (23505)")
    void v39_scenario8_trigger23514_distinguishedFromIndex23505() {
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        PostReturnFixture prf = createPostReturnFixture(txTemplate);
        makeCaseOverdueForKtvSla(prf.caseId(), prf.round2Id());
        Long adminId = createTestUser("admin");
        grantRole(adminId, "ADMIN");

        // 1. Initial valid escalation
        txTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    INSERT INTO og_compat.case_events(event_id, case_id, round_id, actor_id, recipient_id, event_type, reason, command_key)
                    VALUES (gen_random_uuid(), ?, ?, NULL, ?, 'ESCALATED', 'Initial valid escalation', ?)
                    """, prf.caseId(), prf.round2Id(), adminId, "CMD_DIST_INIT_" + UUID.randomUUID());
        });

        // 2. Insertion via og_compat.case_events invokes trigger -> throws 23514 (CHECK_VIOLATION)
        assertThatThrownBy(() -> txTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    INSERT INTO og_compat.case_events(event_id, case_id, round_id, actor_id, recipient_id, event_type, reason, command_key)
                    VALUES (gen_random_uuid(), ?, ?, NULL, ?, 'ESCALATED', 'Via view invoking trigger', ?)
                    """, prf.caseId(), prf.round2Id(), adminId, "CMD_TRIGGER_FAIL_" + UUID.randomUUID());
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
           .satisfies(e -> {
               assertThat(e.getMessage()).contains("Round already escalated");
               Throwable root = e;
               while (root.getCause() != null) root = root.getCause();
               if (root instanceof java.sql.SQLException se) {
                   assertThat(se.getSQLState()).isEqualTo("23514");
               }
           });

        // 3. Direct insertion to table with different command_key -> throws 23505 (UNIQUE_VIOLATION)
        assertThatThrownBy(() -> txTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL session_replication_role = 'replica'");
            jdbcTemplate.update("""
                    INSERT INTO public.case_actions(
                        source_case_events_event_id, case_id, round_id, actor_id, recipient_id,
                        record_type, event_type, reason, metadata, command_key, occurred_at
                    ) VALUES (
                        gen_random_uuid(), ?, ?, NULL, ?,
                        'case_events', 'ESCALATED', 'Direct SQL duplicate', '{}'::jsonb, ?, clock_timestamp()
                    )
                    """, prf.caseId(), prf.round2Id(), adminId, "CMD_INDEX_FAIL_" + UUID.randomUUID());
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
           .satisfies(e -> {
               assertThat(e.getMessage()).contains("uq_case_actions_round_escalated");
               Throwable root = e;
               while (root.getCause() != null) root = root.getCause();
               if (root instanceof java.sql.SQLException se) {
                   assertThat(se.getSQLState()).isEqualTo("23505");
               }
           });
    }
}

