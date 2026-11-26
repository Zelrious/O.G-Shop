package com.oldbutgold.shop.modules.payment.application;

import com.oldbutgold.shop.modules.commerce.api.VoucherDtos;
import com.oldbutgold.shop.modules.commerce.application.GroupCheckoutDtos.GroupCheckoutExecutionResult;
import com.oldbutgold.shop.modules.commerce.application.GroupCheckoutDtos.GroupCheckoutItemRequest;
import com.oldbutgold.shop.modules.commerce.application.GroupCheckoutDtos.GroupCheckoutRequest;
import com.oldbutgold.shop.modules.commerce.application.GroupCheckoutDtos.ShippingAddressDto;
import com.oldbutgold.shop.modules.commerce.application.GroupCheckoutService;
import com.oldbutgold.shop.modules.commerce.application.SimulatedPoApprovalTestFixture;
import com.oldbutgold.shop.modules.commerce.application.VoucherService;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.payment.application.PaymentDtos.PaymentProcessResultResponse;
import com.oldbutgold.shop.modules.payment.application.PaymentDtos.ProcessMockPaymentRequest;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentEntity;
import com.oldbutgold.shop.shared.PostgresTestSafetyGuard;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
@DisplayName("PaymentShippingLedgerPostgresIntegrationTest - V58 Shipping Cash & Subsidy Parity in PaymentService")
class PaymentShippingLedgerPostgresIntegrationTest {

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
    private PaymentService paymentService;

    @Autowired
    private GroupCheckoutService groupCheckoutService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate txTemplate;
    private long adminId;
    private long ktvId;

    @BeforeEach
    void setUp() {
        txTemplate = new TransactionTemplate(transactionManager);
        adminId = createUser("ADMIN");
        ktvId = createUser("KTV");
    }

    private long createUser(String role) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.users(email, password_hash, full_name) VALUES (?, '{noop}test-pass', 'V58 Payment Test') RETURNING user_id",
                Long.class,
                UUID.randomUUID() + "@v58pay.test"
        );
        jdbcTemplate.update(
                "INSERT INTO og_compat.user_roles(user_id, role_id) SELECT ?, role_id FROM og_compat.roles WHERE role_name = ?",
                id, role
        );
        return id;
    }

    private long createApprovedSellerProduct(long sellerId, BigDecimal price) {
        return txTemplate.execute(status -> {
            Long profileId = jdbcTemplate.queryForObject(
                    "INSERT INTO og_compat.ekyc_profiles(user_id, revision_no) VALUES (?, 1) RETURNING profile_id",
                    Long.class, sellerId
            );
            String digest = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            jdbcTemplate.update(
                    "INSERT INTO og_compat.identity_document_registry(document_digest, digest_key_version, user_id, first_profile_id) VALUES (?, 'TEST-HMAC', ?, ?)",
                    digest, sellerId, profileId
            );
            for (String type : new String[]{"CARD_FRONT", "CARD_BACK", "LIVE_FRAME", "REFERENCE"}) {
                jdbcTemplate.update(
                        "INSERT INTO og_compat.ekyc_private_assets(profile_id, asset_type, object_key, content_digest, expires_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '2 months')",
                        profileId, type, "private/test/" + UUID.randomUUID(), digest
                );
            }
            jdbcTemplate.update(
                    "UPDATE og_compat.ekyc_profiles SET document_digest = ?, state = 'PENDING', submitted_at = CURRENT_TIMESTAMP WHERE profile_id = ?",
                    digest, profileId
            );
            jdbcTemplate.update(
                    "INSERT INTO og_compat.ekyc_decisions(profile_id, reviewed_by, outcome, command_key) VALUES (?, ?, 'VERIFIED', ?)",
                    profileId, ktvId, "DEC-EKYC-" + UUID.randomUUID()
            );

            Long addressId = jdbcTemplate.queryForObject(
                    "INSERT INTO og_compat.addresses(user_id, recipient_name, phone_number, province, district, ward, detail_address) VALUES (?, 'Seller Pickup', '0901234567', 'HN', 'BD', 'W', 'Street 1') RETURNING address_id",
                    Long.class, sellerId
            );

            Long spId = jdbcTemplate.queryForObject(
                    "INSERT INTO og_compat.seller_profiles(user_id, revision_no, state, ekyc_profile_id, pickup_address_id, bank_snapshot) VALUES (?, 1, 'PENDING_REVIEW', ?, ?, '{\"bank_name\":\"MockBank\",\"account_number\":\"123456789\",\"account_holder\":\"Seller\"}'::jsonb) RETURNING seller_profile_id",
                    Long.class, sellerId, profileId, addressId
            );
            jdbcTemplate.update(
                    "INSERT INTO og_compat.seller_profile_decisions(seller_profile_id, revision_no, reviewed_by, outcome, profile_snapshot, command_key) VALUES (?, 1, ?, 'APPROVED', '{}'::jsonb, ?)",
                    spId, ktvId, "DEC-SP-" + UUID.randomUUID()
            );

            Long productId = jdbcTemplate.queryForObject(
                    "INSERT INTO og_compat.products(seller_id, category_id, title, description, listed_price, condition, status, workflow_model, quantity_total, quantity_held, quantity_sold, quantity_unavailable, return_allowed, delivery_options) " +
                    "VALUES (?, (SELECT min(category_id) FROM og_compat.categories), 'V58 Mock Payment Item', 'Desc', ?, 'GOOD', 'DRAFT', 'UC83', 5, 0, 0, 0, TRUE, '[\"CARRIER\", \"SELF_PICKUP\"]'::jsonb) RETURNING product_id",
                    Long.class, sellerId, price
            );
            jdbcTemplate.update(
                    "INSERT INTO og_compat.product_categories(product_id, category_id) SELECT ?, min(category_id) FROM og_compat.categories",
                    productId
            );
            Long revisionId = jdbcTemplate.queryForObject(
                    "INSERT INTO og_compat.product_revisions(product_id, revision_no, provenance, state, listed_unit_price, quantity, return_allowed, delivery_options, content_snapshot, checklist_snapshot) " +
                    "VALUES (?, 1, 'UC83', 'DRAFT', ?, 5, TRUE, '[\"CARRIER\", \"SELF_PICKUP\"]'::jsonb, '{}'::jsonb, '[]'::jsonb) RETURNING revision_id",
                    Long.class, productId, price
            );
            jdbcTemplate.update(
                    "INSERT INTO og_compat.revision_media(revision_id, media_type, object_key, content_digest, file_size_bytes, duration_seconds, display_order) VALUES (?, 'IMAGE', 'img1', repeat('a', 64), 100, 0, 0)",
                    revisionId
            );
            jdbcTemplate.update(
                    "INSERT INTO og_compat.revision_media(revision_id, media_type, object_key, content_digest, file_size_bytes, duration_seconds, display_order) VALUES (?, 'VIDEO', 'vid1', repeat('a', 64), 100, 10, 1)",
                    revisionId
            );
            jdbcTemplate.update(
                    "UPDATE og_compat.product_revisions SET state = 'SUBMITTED', submitted_at = CURRENT_TIMESTAMP WHERE revision_id = ?",
                    revisionId
            );
            jdbcTemplate.update(
                    "UPDATE og_compat.products SET status = 'PENDING', current_revision_id = ? WHERE product_id = ?",
                    revisionId, productId
            );
            Long decisionId = jdbcTemplate.queryForObject(
                    "INSERT INTO og_compat.product_moderation_decisions(product_id, revision_id, reviewer_id, decision, product_version, command_key) VALUES (?, ?, ?, 'APPROVED', 0, ?) RETURNING decision_id",
                    Long.class, productId, revisionId, ktvId, "MOD-" + UUID.randomUUID()
            );
            Long assessmentId = jdbcTemplate.queryForObject(
                    "INSERT INTO og_compat.listing_fee_assessments(product_id, revision_id, decision_id, policy_id, listed_total, computed_fee, cumulative_before, amount_due, command_key) " +
                    "SELECT ?, ?, ?, policy_id, 1, 0, 0, 0, ? FROM og_compat.listing_fee_policies WHERE effective_until IS NULL RETURNING assessment_id",
                    Long.class, productId, revisionId, decisionId, "FEE-" + UUID.randomUUID()
            );
            BigDecimal due = jdbcTemplate.queryForObject(
                    "SELECT amount_due FROM og_compat.listing_fee_assessments WHERE assessment_id = ?",
                    BigDecimal.class, assessmentId
            );
            if (due != null && due.signum() > 0) {
                UUID chargeId = UUID.randomUUID();
                jdbcTemplate.update(
                        "INSERT INTO og_compat.listing_fee_charges(charge_id, assessment_id, amount, command_key) VALUES (?, ?, ?, ?)",
                        chargeId, assessmentId, due, "CHG-" + UUID.randomUUID()
                );
                jdbcTemplate.update(
                        "INSERT INTO og_compat.listing_fee_receipts(receipt_id, charge_id, provider, provider_transaction_id, amount) VALUES (?, ?, 'BANK_TRANSFER_MOCK', ?, ?)",
                        UUID.randomUUID(), chargeId, "RCP-" + UUID.randomUUID(), due
                );
            }
            jdbcTemplate.update(
                    "UPDATE og_compat.products SET status = 'ACTIVE', public_revision_id = ?, current_revision_id = ? WHERE product_id = ?",
                    revisionId, revisionId, productId
            );
            return productId;
        });
    }

    private Long seedBuyerCoins(long buyerId, long coins) {
        Long policyId;
        List<Long> pList = jdbcTemplate.queryForList(
                "SELECT policy_id FROM og_compat.reward_policies WHERE policy_code = 'REWARD_CHECKOUT_STANDARD'",
                Long.class
        );
        if (pList.isEmpty()) {
            policyId = jdbcTemplate.queryForObject("""
                    INSERT INTO og_compat.reward_policies(policy_code, point_value, policy_snapshot, created_by, created_at)
                    VALUES ('REWARD_CHECKOUT_STANDARD', 1.00, '{"max_redemption_percentage": 100}', ?, NOW())
                    RETURNING policy_id
                    """, Long.class, adminId);
        } else {
            policyId = pList.get(0);
        }

        Integer accCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.reward_accounts WHERE user_id = ?",
                Integer.class, buyerId
        );
        if (accCount == null || accCount == 0) {
            jdbcTemplate.update("INSERT INTO og_compat.reward_accounts(user_id) VALUES (?)", buyerId);
        }
        jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(entry_id, user_id, policy_id, entry_type, points_delta, actor_id, reason, command_key)
                VALUES (?, ?, ?, 'CORRECTION', ?, ?, 'V58 payment ledger seed', ?)
                """, UUID.randomUUID(), buyerId, policyId, coins, adminId, "CMD_V58_PAY_" + UUID.randomUUID());
        return policyId;
    }

    private ShippingAddressDto testAddress() {
        return new ShippingAddressDto(
                "Nguyen Van A",
                "0987654321",
                "TP Ho Chi Minh",
                "Quan 1",
                "Ben Nghe",
                "123 Le Loi"
        );
    }

    private BigDecimal queryComponentAmount(Long orderId, String componentType, boolean expectNullAllocation) {
        String allocPredicate = expectNullAllocation ? "allocation_id IS NULL" : "allocation_id IS NOT NULL";
        return jdbcTemplate.queryForObject(
                "SELECT confirmed_amount FROM og_compat.order_fund_components WHERE order_id = ? AND component_type = ? AND " + allocPredicate,
                BigDecimal.class, orderId, componentType
        );
    }

    @Test
    @DisplayName("Points-only shipping discount subtracts points_shipping_discount_amount from SHIPPING_CASH and credits PLATFORM_SHIPPING_SUBSIDY")
    void processMockPayment_pointsOnlyShippingDiscount_splitsShippingCashAndSubsidy() {
        long buyerId = createUser("BUYER");
        long sellerId = createUser("SELLER");
        long productId = createApprovedSellerProduct(sellerId, new BigDecimal("100000"));
        seedBuyerCoins(buyerId, 200000L);

        groupCheckoutService.setConfiguredMaxRedemptionPercentage("100");
        groupCheckoutService.setConfiguredCanonicalPolicyCode("REWARD_CHECKOUT_STANDARD");

        try (var poApprovalScope = SimulatedPoApprovalTestFixture.openScope(groupCheckoutService)) {
            GroupCheckoutRequest request = new GroupCheckoutRequest(
                    "V58-PAY-POINTS-" + UUID.randomUUID(),
                    testAddress(),
                    "CARRIER",
                    "GHN",
                    "BANK_TRANSFER_MOCK",
                    null,
                    110000L,
                    List.of(new GroupCheckoutItemRequest(productId, 1, null))
            );
            GroupCheckoutExecutionResult checkout = txTemplate.execute(
                    status -> groupCheckoutService.executeCheckout(buyerId, request)
            );
            assertThat(checkout).isNotNull();
            assertThat(checkout.response().expectedTotal()).isEqualByComparingTo("20000");

            OrderEntity order = orderRepository.findByCheckoutGroupId(checkout.response().groupId()).get(0);
            assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_PAYMENT_PENDING);
            assertThat(order.getPointsDiscountAmount()).isEqualByComparingTo("100000");
            assertThat(order.getPointsShippingDiscountAmount()).isEqualByComparingTo("10000");

            PaymentProcessResultResponse result = paymentService.processMockPayment(
                    buyerId, new ProcessMockPaymentRequest(order.getId(), PaymentEntity.METHOD_BANK_TRANSFER, true)
            );
            assertThat(result.orderStatus()).isEqualTo(OrderEntity.STATUS_PAID_HELD);

            assertThat(queryComponentAmount(order.getId(), "SHIPPING_CASH", false)).isEqualByComparingTo("20000");
            assertThat(queryComponentAmount(order.getId(), "PLATFORM_GOODS_SUBSIDY", true)).isEqualByComparingTo("100000");
            assertThat(queryComponentAmount(order.getId(), "PLATFORM_SHIPPING_SUBSIDY", true)).isEqualByComparingTo("10000");
        } finally {
            groupCheckoutService.setConfiguredMaxRedemptionPercentage(null);
            groupCheckoutService.setConfiguredCanonicalPolicyCode(null);
        }
    }

    @Test
    @DisplayName("Combined shipping voucher + points shipping discount writes exact V58 components with NULL allocation_id on subsidies")
    void processMockPayment_combinedShippingVoucherAndPoints_splitsShippingAndGoodsSubsidies() {
        long buyerId = createUser("BUYER");
        long sellerId = createUser("SELLER");
        long productId = createApprovedSellerProduct(sellerId, new BigDecimal("100000"));
        Long policyId = seedBuyerCoins(buyerId, 200000L);

        Instant now = Instant.now();
        String voucherCode = "SHIP12K_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        VoucherDtos.Campaign campaign = voucherService.save(
                adminId,
                null,
                new VoucherDtos.Save(
                        UUID.randomUUID(),
                        0L,
                        voucherCode,
                        "Ship 12k",
                        "Shipping discount test",
                        "SHIPPING_DISCOUNT",
                        "FIXED_AMOUNT",
                        new BigDecimal("12000"),
                        new BigDecimal("12000"),
                        BigDecimal.ZERO,
                        "CHECKOUT",
                        List.of(),
                        10,
                        now.minusSeconds(600),
                        now.plusSeconds(3600),
                        true,
                        true
                )
        );

        VoucherDtos.Grant grant = voucherService.claim(
                buyerId, campaign.id(), new VoucherDtos.Claim(UUID.randomUUID(), campaign.version())
        );

        Long orderId = txTemplate.execute(status -> {
            Map<String, Object> tsRow = jdbcTemplate.queryForMap("""
                    WITH t AS (SELECT clock_timestamp() AS created_ts)
                    SELECT created_ts, created_ts + INTERVAL '1 hour' AS deadline_ts FROM t
                    """);
            Timestamp createdAtTs = (Timestamp) tsRow.get("created_ts");
            Timestamp deadlineTs = (Timestamp) tsRow.get("deadline_ts");
            Date handoverDate = Date.valueOf(LocalDate.now());
            UUID groupId = UUID.randomUUID();
            Long revisionId = jdbcTemplate.queryForObject(
                    "SELECT current_revision_id FROM og_compat.products WHERE product_id = ?",
                    Long.class, productId
            );

            jdbcTemplate.update("""
                    INSERT INTO og_compat.checkout_groups(
                        group_id, buyer_id, command_key, request_digest, address_snapshot,
                        delivery_method, carrier, payment_method, expected_total, state,
                        created_at, payment_due_at, reward_policy_id
                    ) VALUES (
                        ?, ?, ?, repeat('a', 64),
                        '{"recipient_name":"Nguyen Van A","phone_number":"0987654321","province":"TP Ho Chi Minh","district":"Quan 1","ward":"Ben Nghe","detail_address":"123 Le Loi"}'::jsonb,
                        'CARRIER', 'GHN', 'BANK_TRANSFER_MOCK', 110000, 'READY',
                        ?, ?, ?
                    )
                    """, groupId, buyerId, "CMD_CG_" + UUID.randomUUID(), createdAtTs, deadlineTs, policyId);

                    Long insertedOrderId = jdbcTemplate.queryForObject("""
                    INSERT INTO og_compat.orders(
                        checkout_group_id, group_id, buyer_id, seller_id,
                        shipping_recipient_name, shipping_phone_number, shipping_province,
                        shipping_district, shipping_ward, shipping_detail_address,
                        subtotal, shipping_fee, total_amount, voucher_discount_amount,
                        shipping_discount_amount, points_discount_amount, points_shipping_discount_amount,
                        buyer_system_fee, seller_system_fee, seller_proceeds,
                        sponsor_type, status, created_at, payment_due_at,
                        workflow_model, return_allowed, delivery_method, carrier_snapshot, handover_day
                    ) VALUES (
                        ?, ?, ?, ?,
                        'Nguyen Van A', '0987654321', 'TP Ho Chi Minh',
                        'Quan 1', 'Ben Nghe', '123 Le Loi',
                        100000, 30000, 110000, 0,
                        12000, 0, 8000,
                        0, 0, 100000,
                        'PLATFORM', 'PAYMENT_PENDING', ?, ?,
                        'UC83', TRUE, 'CARRIER', 'GHN', ?
                    ) RETURNING order_id
                    """,
                    Long.class,
                    groupId, groupId, buyerId, sellerId,
                    createdAtTs, deadlineTs, handoverDate
            );

            Long orderItemId = jdbcTemplate.queryForObject("""
                    INSERT INTO og_compat.order_items(
                        order_id, product_id, product_title, quantity, listed_price, agreed_price,
                        buyer_system_fee, seller_system_fee, buyer_line_total, seller_line_proceeds,
                        workflow_model, product_revision_id, return_allowed, voucher_allocation, points_allocation
                    ) VALUES (
                        ?, ?, 'V58 Mock Payment Item', 1, 100000, 100000,
                        0, 0, 100000, 100000,
                        'UC83', ?, TRUE, 0, 0
                    ) RETURNING order_item_id
                    """,
                    Long.class,
                    insertedOrderId, productId, revisionId
            );

            jdbcTemplate.update("""
                    INSERT INTO og_compat.inventory_reservations(
                        reservation_id, order_item_id, product_id, quantity, state, expires_at, created_at, command_key
                    ) VALUES (?, ?, ?, 1, 'HELD', ?, ?, ?)
                    """, UUID.randomUUID(), orderItemId, productId, deadlineTs, createdAtTs, "CMD_RES_" + UUID.randomUUID());

            UUID redemptionId = UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO og_compat.checkout_redemptions(
                        redemption_id, group_id, buyer_id, grant_id, policy_snapshot, discount_amount, redeemed_at
                    ) VALUES (?, ?, ?, ?, '{}'::jsonb, 12000, ?)
                    """, redemptionId, groupId, buyerId, grant.id(), createdAtTs);

            jdbcTemplate.update("""
                    INSERT INTO og_compat.discount_allocations(
                        allocation_id, order_item_id, redemption_id, component, amount
                    ) VALUES (?, ?, ?, 'SHIPPING', 12000)
                    """, UUID.randomUUID(), orderItemId, redemptionId);

            return insertedOrderId;
        });

        PaymentProcessResultResponse result = paymentService.processMockPayment(
                buyerId, new ProcessMockPaymentRequest(orderId, PaymentEntity.METHOD_BANK_TRANSFER, true)
        );
        assertThat(result.orderStatus()).isEqualTo(OrderEntity.STATUS_PAID_HELD);

        assertThat(queryComponentAmount(orderId, "GOODS_CASH", false)).isEqualByComparingTo("100000");
        assertThat(queryComponentAmount(orderId, "SHIPPING_CASH", false)).isEqualByComparingTo("10000");
        assertThat(queryComponentAmount(orderId, "PLATFORM_SHIPPING_SUBSIDY", true)).isEqualByComparingTo("20000");
    }
}
