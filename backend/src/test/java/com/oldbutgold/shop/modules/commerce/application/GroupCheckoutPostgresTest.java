package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.commerce.application.GroupCheckoutDtos.*;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.*;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.RewardPolicyEntity;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.RewardPolicyRepository;
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
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
@DisplayName("GroupCheckoutPostgresTest - Real PostgreSQL COMMIT Verification of UC83 Group Checkout & Aggregate Triggers")
class GroupCheckoutPostgresTest {

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
    private GroupCheckoutService groupCheckoutService;

    @Autowired
    private CheckoutGroupRepository checkoutGroupRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private InventoryReservationRepository inventoryReservationRepository;

    @Autowired
    private RewardPolicyRepository rewardPolicyRepository;

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
                "INSERT INTO og_compat.users(email, password_hash, full_name) VALUES (?, '{noop}test-pass', 'UC83 Test User') RETURNING user_id",
                Long.class,
                UUID.randomUUID() + "@uc83.test"
        );
        jdbcTemplate.update("INSERT INTO og_compat.user_roles(user_id, role_id) SELECT ?, role_id FROM og_compat.roles WHERE role_name = ?", id, role);
        return id;
    }

    private void ensureSellerApproved(long sellerId) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.seller_profiles WHERE user_id = ?", Integer.class, sellerId);
        if (count == null || count == 0) {
            txTemplate.executeWithoutResult(status -> {
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
            });
        }
    }

    private record CreatedProduct(long productId, long revisionId, BigDecimal price, boolean returnAllowed) {}

    private CreatedProduct createUc83Product(long sellerId, String title, BigDecimal price, int quantity, boolean returnAllowed) {
        ensureSellerApproved(sellerId);

        return txTemplate.execute(status -> {
            Long productId = jdbcTemplate.queryForObject(
                    "INSERT INTO og_compat.products(seller_id, category_id, title, description, listed_price, condition, status, workflow_model, quantity_total, quantity_held, quantity_sold, quantity_unavailable, return_allowed, delivery_options) " +
                    "VALUES (?, (SELECT min(category_id) FROM og_compat.categories), ?, 'UC83 Item Description', ?, 'GOOD', 'DRAFT', 'UC83', ?, 0, 0, 0, ?, '[\"CARRIER\", \"SELF_PICKUP\", \"SELF_DELIVERY\"]'::jsonb) RETURNING product_id",
                    Long.class,
                    sellerId, title, price, quantity, returnAllowed
            );
            jdbcTemplate.update("INSERT INTO og_compat.product_categories(product_id, category_id) SELECT ?, min(category_id) FROM og_compat.categories", productId);

            Long revisionId = jdbcTemplate.queryForObject(
                    "INSERT INTO og_compat.product_revisions(product_id, revision_no, provenance, state, listed_unit_price, quantity, return_allowed, delivery_options, content_snapshot, checklist_snapshot) " +
                    "VALUES (?, 1, 'UC83', 'DRAFT', ?, ?, ?, '[\"CARRIER\", \"SELF_PICKUP\", \"SELF_DELIVERY\"]'::jsonb, '{}'::jsonb, '[]'::jsonb) RETURNING revision_id",
                    Long.class,
                    productId, price, quantity, returnAllowed
            );

            // Add media
            jdbcTemplate.update("INSERT INTO og_compat.revision_media(revision_id, media_type, object_key, content_digest, file_size_bytes, duration_seconds, display_order) " +
                    "VALUES (?, 'IMAGE', 'img1', repeat('a', 64), 100, 0, 0)", revisionId);
            jdbcTemplate.update("INSERT INTO og_compat.revision_media(revision_id, media_type, object_key, content_digest, file_size_bytes, duration_seconds, display_order) " +
                    "VALUES (?, 'VIDEO', 'vid1', repeat('a', 64), 100, 10, 1)", revisionId);

            // Submit revision
            jdbcTemplate.update("UPDATE og_compat.product_revisions SET state = 'SUBMITTED', submitted_at = CURRENT_TIMESTAMP WHERE revision_id = ?", revisionId);
            jdbcTemplate.update("UPDATE og_compat.products SET status = 'PENDING', current_revision_id = ? WHERE product_id = ?", revisionId, productId);

            // Moderate & Assess
            Long decisionId = jdbcTemplate.queryForObject(
                    "INSERT INTO og_compat.product_moderation_decisions(product_id, revision_id, reviewer_id, decision, product_version, command_key) " +
                    "VALUES (?, ?, ?, 'APPROVED', 0, ?) RETURNING decision_id",
                    Long.class, productId, revisionId, ktvId, "MOD-" + UUID.randomUUID()
            );

            Long assessmentId = jdbcTemplate.queryForObject(
                    "INSERT INTO og_compat.listing_fee_assessments(product_id, revision_id, decision_id, policy_id, listed_total, computed_fee, cumulative_before, amount_due, command_key) " +
                    "SELECT ?, ?, ?, policy_id, 1, 0, 0, 0, ? FROM og_compat.listing_fee_policies WHERE effective_until IS NULL RETURNING assessment_id",
                    Long.class, productId, revisionId, decisionId, "FEE-" + UUID.randomUUID()
            );

            BigDecimal due = jdbcTemplate.queryForObject("SELECT amount_due FROM og_compat.listing_fee_assessments WHERE assessment_id = ?", BigDecimal.class, assessmentId);
            if (due != null && due.signum() > 0) {
                UUID chargeId = UUID.randomUUID();
                jdbcTemplate.update("INSERT INTO og_compat.listing_fee_charges(charge_id, assessment_id, amount, command_key) VALUES (?, ?, ?, ?)",
                        chargeId, assessmentId, due, "CHG-" + UUID.randomUUID());
                jdbcTemplate.update("INSERT INTO og_compat.listing_fee_receipts(receipt_id, charge_id, provider, provider_transaction_id, amount) VALUES (?, ?, 'BANK_TRANSFER_MOCK', ?, ?)",
                        UUID.randomUUID(), chargeId, "RCP-" + UUID.randomUUID(), due);
            }

            // Activate product
            jdbcTemplate.update("UPDATE og_compat.products SET status = 'ACTIVE', public_revision_id = ?, current_revision_id = ?, listed_price = ?, quantity_total = ?, return_allowed = ?, delivery_options = '[\"CARRIER\", \"SELF_PICKUP\", \"SELF_DELIVERY\"]'::jsonb WHERE product_id = ?",
                    revisionId, revisionId, price, quantity, returnAllowed, productId);

            return new CreatedProduct(productId, revisionId, price, returnAllowed);
        });
    }

    private ShippingAddressDto testAddress() {
        return new ShippingAddressDto("Nguyen Van A", "0912345678", "TP Ho Chi Minh", "Quan 1", "Phuong Ben Nghe", "123 Nguyen Hue");
    }

    @Test
    @DisplayName("Multi-seller multi-product UC83 checkout partitions by (sellerId, returnAllowed) and commits real DB triggers")
    void multiSellerMultiProductUc83CheckoutWithRealCommit() {
        long buyerId = createUser("BUYER");
        long sellerA = createUser("SELLER");
        long sellerB = createUser("SELLER");

        // Seller A: product 1 (returnAllowed=true), product 2 (returnAllowed=false)
        CreatedProduct p1 = createUc83Product(sellerA, "Seller A Item Returnable", new BigDecimal("100000"), 5, true);
        CreatedProduct p2 = createUc83Product(sellerA, "Seller A Item NonReturnable", new BigDecimal("150000"), 5, false);

        // Seller B: product 3 (returnAllowed=true)
        CreatedProduct p3 = createUc83Product(sellerB, "Seller B Item Returnable", new BigDecimal("200000"), 5, true);

        String commandKey = "GRP-TEST-" + UUID.randomUUID();
        GroupCheckoutRequest request = new GroupCheckoutRequest(
                commandKey,
                testAddress(),
                "CARRIER",
                "GHN",
                "VNPAY",
                null,
                null,
                List.of(
                        new GroupCheckoutItemRequest(p1.productId(), 1, null),
                        new GroupCheckoutItemRequest(p2.productId(), 2, null), // 2 * 150000 = 300000
                        new GroupCheckoutItemRequest(p3.productId(), 1, null)  // 200000
                )
        );

        // Execute inside real transaction that COMMITS to fire deferred triggers
        GroupCheckoutExecutionResult result = txTemplate.execute(status ->
                groupCheckoutService.executeCheckout(buyerId, request)
        );

        assertThat(result).isNotNull();
        assertThat(result.isIdempotentReplay()).isFalse();

        GroupCheckoutResponse response = result.response();
        assertThat(response.groupId()).isNotNull();
        assertThat(response.state()).isEqualTo("READY");
        assertThat(response.orders()).hasSize(3); // 3 partitions!

        // Subtotals (DEC-08 Option A):
        // Seller A:
        //   Order 1 (Primary, returnAllowed=true): 100,000 + 30,000 shipping = 130,000
        //   Order 2 (Secondary, returnAllowed=false): 300,000 + 0 shipping = 300,000
        // Seller B:
        //   Order 3 (Primary, returnAllowed=true): 200,000 + 30,000 shipping = 230,000
        // Total Group Subtotal = 600,000; Total Shipping = 60,000; Total Expected = 660,000
        assertThat(response.groupSubtotal()).isEqualByComparingTo(new BigDecimal("600000"));
        assertThat(response.groupShippingFee()).isEqualByComparingTo(new BigDecimal("60000"));
        assertThat(response.expectedTotal()).isEqualByComparingTo(new BigDecimal("660000"));

        // Verify DB persistence
        CheckoutGroupEntity groupInDb = checkoutGroupRepository.findById(response.groupId()).orElseThrow();
        assertThat(groupInDb.getExpectedTotal()).isEqualByComparingTo(new BigDecimal("660000"));

        List<OrderEntity> ordersInDb = orderRepository.findByCheckoutGroupId(response.groupId());
        assertThat(ordersInDb).hasSize(3);

        for (OrderEntity order : ordersInDb) {
            assertThat(order.getWorkflowModel()).isEqualTo("UC83");
            assertThat(order.getSellerProceeds()).isEqualByComparingTo(order.getSubtotal());
            assertThat(order.getBuyerSystemFee()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(order.getSellerSystemFee()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_PAYMENT_PENDING);

            List<OrderItemEntity> items = orderItemRepository.findByOrderId(order.getId());
            assertThat(items).isNotEmpty();
            for (OrderItemEntity item : items) {
                assertThat(item.getWorkflowModel()).isEqualTo("UC83");
                assertThat(item.getProductRevisionId()).isNotNull();
                assertThat(item.getReturnAllowed()).isEqualTo(order.getReturnAllowed());

                List<InventoryReservationEntity> res = inventoryReservationRepository.findByOrderItemId(item.getId());
                assertThat(res).hasSize(1);
                assertThat(res.get(0).getState()).isEqualTo(InventoryReservationEntity.STATE_HELD);
                assertThat(res.get(0).getQuantity()).isEqualTo(item.getQuantity());
            }
        }
    }

    @Test
    @DisplayName("Idempotent replay returns same response, conflict throws 409")
    void idempotencyReplayAndConflict() {
        long buyerId = createUser("BUYER");
        long sellerId = createUser("SELLER");
        CreatedProduct p = createUc83Product(sellerId, "Idempotency Item", new BigDecimal("100000"), 5, true);

        String commandKey = "IDEMP-" + UUID.randomUUID();
        GroupCheckoutRequest request = new GroupCheckoutRequest(
                commandKey,
                testAddress(),
                "SELF_PICKUP",
                null,
                "VNPAY",
                null,
                null,
                List.of(new GroupCheckoutItemRequest(p.productId(), 1, null))
        );

        // First call
        GroupCheckoutExecutionResult firstResult = txTemplate.execute(status ->
                groupCheckoutService.executeCheckout(buyerId, request)
        );
        assertThat(firstResult.isIdempotentReplay()).isFalse();

        // Second call: exact same payload
        GroupCheckoutExecutionResult replayResult = txTemplate.execute(status ->
                groupCheckoutService.executeCheckout(buyerId, request)
        );
        assertThat(replayResult.isIdempotentReplay()).isTrue();
        assertThat(replayResult.response().groupId()).isEqualTo(firstResult.response().groupId());

        // Third call: same commandKey, different quantity -> CONFLICT (409)
        GroupCheckoutRequest conflictingRequest = new GroupCheckoutRequest(
                commandKey,
                testAddress(),
                "SELF_PICKUP",
                null,
                "VNPAY",
                null,
                null,
                List.of(new GroupCheckoutItemRequest(p.productId(), 2, null))
        );

        assertThatThrownBy(() -> txTemplate.execute(status -> groupCheckoutService.executeCheckout(buyerId, conflictingRequest)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }

    @Test
    @DisplayName("DEC-10.7A: Fail-closed when Coin Redemption Cap is not configured")
    void failClosedWhenCoinCapNotConfigured() {
        long buyerId = createUser("BUYER");
        long sellerId = createUser("SELLER");
        CreatedProduct p = createUc83Product(sellerId, "Coin Item", new BigDecimal("100000"), 5, true);

        groupCheckoutService.setConfiguredMaxRedemptionPercentage(""); // unconfigured

        GroupCheckoutRequest request = new GroupCheckoutRequest(
                "COIN-FAIL-" + UUID.randomUUID(),
                testAddress(),
                "SELF_PICKUP",
                null,
                "VNPAY",
                null,
                1000L,
                List.of(new GroupCheckoutItemRequest(p.productId(), 1, null))
        );

        assertThatThrownBy(() -> txTemplate.execute(status -> groupCheckoutService.executeCheckout(buyerId, request)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEC-10.7A");
    }

    @Test
    @DisplayName("Coin redemption with configured cap succeeds and deferred aggregate triggers pass on real commit")
    void coinRedemptionWithCapAndRealCommit() {
        long buyerId = createUser("BUYER");
        long sellerId = createUser("SELLER");
        CreatedProduct p = createUc83Product(sellerId, "Coin Redeem Item", new BigDecimal("200000"), 5, true);

        // Ensure canonical policy exists
        Long policyId;
        List<Long> pList = jdbcTemplate.queryForList("SELECT policy_id FROM og_compat.reward_policies WHERE policy_code = 'REWARD_CHECKOUT_STANDARD'", Long.class);
        if (pList.isEmpty()) {
            policyId = jdbcTemplate.queryForObject("""
                    INSERT INTO og_compat.reward_policies(policy_code, point_value, policy_snapshot, created_by, created_at)
                    VALUES ('REWARD_CHECKOUT_STANDARD', 1.00, '{"max_redemption_percentage": 50}', ?, NOW())
                    RETURNING policy_id
                    """, Long.class, adminId);
        } else {
            policyId = pList.get(0);
        }

        // Credit buyer with 100,000 coins via reward_ledger
        Integer accCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.reward_accounts WHERE user_id = ?", Integer.class, buyerId);
        if (accCount == null || accCount == 0) {
            jdbcTemplate.update("INSERT INTO og_compat.reward_accounts(user_id) VALUES (?)", buyerId);
        }
        jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(entry_id, user_id, policy_id, entry_type, points_delta, actor_id, reason, command_key)
                VALUES (?, ?, ?, 'CORRECTION', 100000, ?, 'Test coin seed', ?)
                """, UUID.randomUUID(), buyerId, policyId, adminId, "CMD_COIN_SEED_" + UUID.randomUUID());

        groupCheckoutService.setConfiguredMaxRedemptionPercentage("50");
        groupCheckoutService.setCapPoApproved(true);
        groupCheckoutService.setConfiguredCanonicalPolicyCode("REWARD_CHECKOUT_STANDARD");

        try {
            GroupCheckoutRequest request = new GroupCheckoutRequest(
                    "COIN-OK-" + UUID.randomUUID(),
                    testAddress(),
                    "SELF_PICKUP",
                    null,
                    "VNPAY",
                    null,
                    50000L, // 50,000 coins on 200,000 subtotal (within 50% = 100,000 cap)
                    List.of(new GroupCheckoutItemRequest(p.productId(), 1, null))
            );

            GroupCheckoutExecutionResult result = txTemplate.execute(status ->
                    groupCheckoutService.executeCheckout(buyerId, request)
            );

            assertThat(result).isNotNull();
            GroupCheckoutResponse response = result.response();
            assertThat(response.groupPointsDiscount()).isEqualByComparingTo(new BigDecimal("50000"));
            assertThat(response.expectedTotal()).isEqualByComparingTo(new BigDecimal("150000")); // 200000 - 50000
            assertThat(response.rewardPolicyId()).isNotNull();

            // Check database deferred triggers passed
            List<OrderEntity> orders = orderRepository.findByCheckoutGroupId(response.groupId());
            assertThat(orders).hasSize(1);
            assertThat(orders.get(0).getPointsDiscountAmount()).isEqualByComparingTo(new BigDecimal("50000"));
            assertThat(orders.get(0).getTotalAmount()).isEqualByComparingTo(new BigDecimal("150000"));

            List<OrderItemEntity> items = orderItemRepository.findByOrderId(orders.get(0).getId());
            assertThat(items.get(0).getPointsAllocation()).isEqualByComparingTo(new BigDecimal("50000"));
            assertThat(items.get(0).getBuyerLineTotal()).isEqualByComparingTo(new BigDecimal("150000"));

            // DEC-10.4: Verify T1 does NOT debit reward_ledger and does NOT deduct buyer loyalty balance
            Integer debitCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM og_compat.reward_ledger WHERE group_id = ? AND entry_type = 'DEBIT_CHECKOUT'",
                    Integer.class, response.groupId()
            );
            assertThat(debitCount).isEqualTo(0);

            Long currentBalance = jdbcTemplate.queryForObject(
                    "SELECT balance FROM og_compat.reward_accounts WHERE user_id = ?",
                    Long.class, buyerId
            );
            assertThat(currentBalance).isEqualTo(100000L); // Untouched at T1!
        } finally {
            groupCheckoutService.setConfiguredMaxRedemptionPercentage(null);
            groupCheckoutService.setCapPoApproved(false);
            groupCheckoutService.setConfiguredCanonicalPolicyCode(null);
        }
    }

    @Test
    @DisplayName("DEC-08 Option A: Seller with only non-returnable items bears the standard shipping fee")
    void sellerWithOnlyNonReturnableItemsBearsShippingFee() {
        long buyerId = createUser("BUYER");
        long sellerId = createUser("SELLER");
        CreatedProduct nonReturnable = createUc83Product(sellerId, "Only NonReturnable Item", new BigDecimal("120000"), 5, false);

        GroupCheckoutRequest request = new GroupCheckoutRequest(
                "NONRET-SHIP-" + UUID.randomUUID(),
                testAddress(),
                "CARRIER",
                "GHN",
                "VNPAY",
                null,
                null,
                List.of(new GroupCheckoutItemRequest(nonReturnable.productId(), 1, null))
        );

        GroupCheckoutExecutionResult result = txTemplate.execute(status ->
                groupCheckoutService.executeCheckout(buyerId, request)
        );

        assertThat(result).isNotNull();
        GroupCheckoutResponse response = result.response();
        assertThat(response.orders()).hasSize(1);
        assertThat(response.groupSubtotal()).isEqualByComparingTo(new BigDecimal("120000"));
        assertThat(response.groupShippingFee()).isEqualByComparingTo(new BigDecimal("30000"));
        assertThat(response.expectedTotal()).isEqualByComparingTo(new BigDecimal("150000"));

        List<OrderEntity> orders = orderRepository.findByCheckoutGroupId(response.groupId());
        assertThat(orders.get(0).getReturnAllowed()).isFalse();
        assertThat(orders.get(0).getShippingFee()).isEqualByComparingTo(new BigDecimal("30000"));
        assertThat(orders.get(0).getTotalAmount()).isEqualByComparingTo(new BigDecimal("150000"));
    }

    @Test
    @DisplayName("DEC-08: SELF_DELIVERY sets shipping fee to 0 across all partitions")
    void selfDeliverySetsShippingFeeToZero() {
        long buyerId = createUser("BUYER");
        long sellerId = createUser("SELLER");
        CreatedProduct p = createUc83Product(sellerId, "Self Delivery Item", new BigDecimal("150000"), 5, true);

        GroupCheckoutRequest request = new GroupCheckoutRequest(
                "SELFDELIV-" + UUID.randomUUID(),
                testAddress(),
                "SELF_DELIVERY",
                null,
                "VNPAY",
                null,
                null,
                List.of(new GroupCheckoutItemRequest(p.productId(), 1, null))
        );

        GroupCheckoutExecutionResult result = txTemplate.execute(status ->
                groupCheckoutService.executeCheckout(buyerId, request)
        );

        assertThat(result).isNotNull();
        GroupCheckoutResponse response = result.response();
        assertThat(response.groupShippingFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.expectedTotal()).isEqualByComparingTo(new BigDecimal("150000"));

        List<OrderEntity> orders = orderRepository.findByCheckoutGroupId(response.groupId());
        assertThat(orders.get(0).getShippingFee()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
