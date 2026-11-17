package com.oldbutgold.shop.modules.payment.application;

import com.oldbutgold.shop.modules.commerce.application.GroupCheckoutDtos.*;
import com.oldbutgold.shop.modules.commerce.application.GroupCheckoutService;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.*;
import com.oldbutgold.shop.modules.payment.application.GroupPaymentDtos.*;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.*;
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
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
@DisplayName("GroupPaymentPostgresTest - End-to-End UC83 Group Payment, Allocations, Fund Components, and Coin T2 Debit with Real COMMIT")
class GroupPaymentPostgresTest {

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
    private GroupPaymentService groupPaymentService;

    @Autowired
    private CheckoutGroupRepository checkoutGroupRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private InventoryReservationRepository inventoryReservationRepository;

    @Autowired
    private PaymentIntentRepository paymentIntentRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private PaymentConfirmationRepository paymentConfirmationRepository;

    @Autowired
    private PaymentAllocationRepository paymentAllocationRepository;

    @Autowired
    private OrderFundComponentRepository orderFundComponentRepository;

    @Autowired
    private RewardLedgerRepository rewardLedgerRepository;

    @Autowired
    private PaymentIpnEventRepository paymentIpnEventRepository;

    @Autowired
    private PaymentReconciliationCaseRepository paymentReconciliationCaseRepository;

    @Autowired
    private VnPayService vnPayService;

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
                "INSERT INTO og_compat.users(email, password_hash, full_name) VALUES (?, '{noop}test-pass', 'UC83 Payment User') RETURNING user_id",
                Long.class,
                UUID.randomUUID() + "@payment.test"
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
                    "VALUES (?, (SELECT min(category_id) FROM og_compat.categories), ?, 'Payment Test Description', ?, 'GOOD', 'DRAFT', 'UC83', ?, 0, 0, 0, ?, '[\"CARRIER\", \"SELF_PICKUP\", \"SELF_DELIVERY\"]'::jsonb) RETURNING product_id",
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
        return new ShippingAddressDto("Tran Van B", "0987654321", "TP Ho Chi Minh", "Quan 1", "Phuong Ben Nghe", "456 Le Loi");
    }

    private Map<String, String> buildSignedCallback(String txnRef, BigDecimal amount, String code, String status) {
        String scaledAmount = String.valueOf(VnPayService.scaledAmount(amount));
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", vnPayService.merchantCode());
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_Amount", scaledAmount);
        params.put("vnp_ResponseCode", code);
        params.put("vnp_TransactionStatus", status);
        params.put("vnp_TransactionNo", Long.toUnsignedString(UUID.randomUUID().getMostSignificantBits()));
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_BankTranNo", "VNPAY12345");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_PayDate", "20261117000000");
        params.put("vnp_SecureHash", vnPayService.sign(params));
        return params;
    }

    @Test
    @DisplayName("End-to-End UC83 Group Checkout -> Intent -> Attempt -> IPN Success with Real DB COMMIT")
    void fullGroupPaymentEndToEndFlowWithRealCommit() {
        long buyerId = createUser("BUYER");
        long sellerA = createUser("SELLER");
        long sellerB = createUser("SELLER");

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

        // Credit buyer with 200,000 coins via reward_ledger
        Integer accCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.reward_accounts WHERE user_id = ?", Integer.class, buyerId);
        if (accCount == null || accCount == 0) {
            jdbcTemplate.update("INSERT INTO og_compat.reward_accounts(user_id) VALUES (?)", buyerId);
        }
        jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(entry_id, user_id, policy_id, entry_type, points_delta, actor_id, reason, command_key)
                VALUES (?, ?, ?, 'CORRECTION', 200000, ?, 'Test coin seed', ?)
                """, UUID.randomUUID(), buyerId, policyId, adminId, "CMD_COIN_SEED_" + UUID.randomUUID());

        groupCheckoutService.setConfiguredMaxRedemptionPercentage("50");
        groupCheckoutService.setConfiguredCanonicalPolicyCode("REWARD_CHECKOUT_STANDARD");

        try {
            // 2 products from 2 sellers
            CreatedProduct p1 = createUc83Product(sellerA, "Seller A Item", new BigDecimal("100000"), 5, true);
            CreatedProduct p2 = createUc83Product(sellerB, "Seller B Item", new BigDecimal("200000"), 5, true);

            GroupCheckoutRequest checkoutReq = new GroupCheckoutRequest(
                    "GRP-PAY-" + UUID.randomUUID(),
                    testAddress(),
                    "CARRIER",
                    "GHN",
                    "VNPAY",
                    null,
                    50000L, // 50,000 coins discount
                    List.of(
                            new GroupCheckoutItemRequest(p1.productId(), 1, null),
                            new GroupCheckoutItemRequest(p2.productId(), 1, null)
                    )
            );

            // Step 1: Group Checkout with real commit
            GroupCheckoutExecutionResult checkoutResult = txTemplate.execute(status ->
                    groupCheckoutService.executeCheckout(buyerId, checkoutReq)
            );
            assertThat(checkoutResult).isNotNull();
            UUID groupId = checkoutResult.response().groupId();

            // Total: 300,000 subtotal + 60,000 shipping (2 * 30k) - 50,000 coins = 310,000 VND
            BigDecimal expectedTotal = new BigDecimal("310000");
            assertThat(checkoutResult.response().expectedTotal()).isEqualByComparingTo(expectedTotal);

            // DEC-10.4: Verify Coin balance is NOT yet deducted at T1
            Long balanceAtT1 = jdbcTemplate.queryForObject("SELECT balance FROM og_compat.reward_accounts WHERE user_id = ?", Long.class, buyerId);
            assertThat(balanceAtT1).isEqualTo(200000L); // Untouched at T1!
            Integer debitCountAtT1 = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.reward_ledger WHERE group_id = ? AND entry_type = 'DEBIT_CHECKOUT'", Integer.class, groupId);
            assertThat(debitCountAtT1).isEqualTo(0);

            // Step 2: Create Group Payment (Intent + Attempt) with real commit
            GroupPaymentResponse paymentResp = txTemplate.execute(status ->
                    groupPaymentService.createGroupPayment(buyerId, groupId, "127.0.0.1")
            );
            assertThat(paymentResp).isNotNull();
            assertThat(paymentResp.txnRef()).startsWith("GRP_");
            assertThat(paymentResp.amount()).isEqualByComparingTo(expectedTotal);

            // Step 3: Handle VNPay IPN with REAL COMMIT
            Map<String, String> ipnParams = buildSignedCallback(paymentResp.txnRef(), expectedTotal, "00", "00");

            GroupPaymentIpnResult ipnResult = txTemplate.execute(status ->
                    groupPaymentService.processGroupPaymentIpn(ipnParams, "127.0.0.1")
            );
            assertThat(ipnResult).isNotNull();
            assertThat(ipnResult.rspCode()).isEqualTo("00");

            // Step 4: Verify Post-Commit Database State
            // Group is PAID
            CheckoutGroupEntity group = checkoutGroupRepository.findById(groupId).orElseThrow();
            assertThat(group.getState()).isEqualTo(CheckoutGroupEntity.STATE_PAID);

            // Intent is CONFIRMED (updated by trigger og_guard_payment_confirmation)
            PaymentIntentEntity intent = paymentIntentRepository.findById(paymentResp.intentId()).orElseThrow();
            assertThat(intent.getState()).isEqualTo(PaymentIntentEntity.STATE_CONFIRMED);

            // Attempt is SUCCESS
            PaymentAttemptEntity attempt = paymentAttemptRepository.findById(paymentResp.attemptId()).orElseThrow();
            assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptEntity.STATUS_SUCCESS);

            // Confirmation exists
            List<PaymentConfirmationEntity> confirmations = paymentConfirmationRepository.findByIntentId(intent.getIntentId());
            assertThat(confirmations).hasSize(1);
            assertThat(confirmations.get(0).getDisposition()).isEqualTo(PaymentConfirmationEntity.DISPOSITION_ALLOCATABLE);

            // Allocations exist for all orders and match expected total
            List<OrderEntity> orders = orderRepository.findByCheckoutGroupId(groupId);
            assertThat(orders).hasSize(2);
            BigDecimal totalAllocated = BigDecimal.ZERO;
            for (OrderEntity order : orders) {
                // Orders are PAID_HELD
                assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_PAID_HELD);
                assertThat(order.getPaidConfirmedAt()).isNotNull();

                PaymentAllocationEntity alloc = paymentAllocationRepository.findByOrderId(order.getId()).orElseThrow();
                assertThat(alloc.getAmount()).isEqualByComparingTo(order.getTotalAmount());
                totalAllocated = totalAllocated.add(alloc.getAmount());

                // Fund components exist (4 components per order)
                List<OrderFundComponentEntity> components = orderFundComponentRepository.findByOrderId(order.getId());
                assertThat(components).hasSize(4);

                // Reservations are FUNDED
                List<OrderItemEntity> items = orderItemRepository.findByOrderId(order.getId());
                for (OrderItemEntity item : items) {
                    List<InventoryReservationEntity> reservations = inventoryReservationRepository.findByOrderItemId(item.getId());
                    assertThat(reservations).hasSize(1);
                    assertThat(reservations.get(0).getState()).isEqualTo("FUNDED");
                }
            }
            assertThat(totalAllocated).isEqualByComparingTo(expectedTotal);

            // Coin T2 Debit exists and balance is deducted
            Optional<RewardLedgerEntity> debitOpt = rewardLedgerRepository.findByGroupIdAndEntryType(groupId, RewardLedgerEntity.ENTRY_DEBIT_CHECKOUT);
            assertThat(debitOpt).isPresent();
            assertThat(debitOpt.get().getPointsDelta()).isEqualTo(-50000L);

            Long finalBalance = jdbcTemplate.queryForObject("SELECT balance FROM og_compat.reward_accounts WHERE user_id = ?", Long.class, buyerId);
            assertThat(finalBalance).isEqualTo(150000L); // 200,000 - 50,000

            // Step 5: Duplicate IPN callback returns "02" (already confirmed)
            GroupPaymentIpnResult dupResult = txTemplate.execute(status ->
                    groupPaymentService.processGroupPaymentIpn(ipnParams, "127.0.0.1")
            );
            assertThat(dupResult.rspCode()).isEqualTo("02");

        } finally {
            groupCheckoutService.setConfiguredMaxRedemptionPercentage(null);
            groupCheckoutService.setConfiguredCanonicalPolicyCode(null);
        }
    }

    @Test
    @DisplayName("Failed VNPay payment updates attempt to FAILED without altering group, orders, or money")
    void failedPaymentPreservesPendingState() {
        long buyerId = createUser("BUYER");
        long sellerId = createUser("SELLER");

        CreatedProduct p = createUc83Product(sellerId, "Failure Test Item", new BigDecimal("100000"), 5, true);

        GroupCheckoutRequest checkoutReq = new GroupCheckoutRequest(
                "GRP-FAIL-" + UUID.randomUUID(),
                testAddress(),
                "SELF_PICKUP",
                null,
                "VNPAY",
                null,
                null,
                List.of(new GroupCheckoutItemRequest(p.productId(), 1, null))
        );

        GroupCheckoutExecutionResult checkoutResult = txTemplate.execute(status ->
                groupCheckoutService.executeCheckout(buyerId, checkoutReq)
        );
        UUID groupId = checkoutResult.response().groupId();

        GroupPaymentResponse paymentResp = txTemplate.execute(status ->
                groupPaymentService.createGroupPayment(buyerId, groupId, "127.0.0.1")
        );

        // Callback with failed code "24" (Customer cancelled transaction)
        Map<String, String> ipnParams = buildSignedCallback(paymentResp.txnRef(), new BigDecimal("100000"), "24", "02");

        GroupPaymentIpnResult ipnResult = txTemplate.execute(status ->
                groupPaymentService.processGroupPaymentIpn(ipnParams, "127.0.0.1")
        );
        assertThat(ipnResult.rspCode()).isEqualTo("00");

        // Check DB state: attempt is FAILED
        PaymentAttemptEntity attempt = paymentAttemptRepository.findById(paymentResp.attemptId()).orElseThrow();
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptEntity.STATUS_FAILED);

        // Group is still READY
        CheckoutGroupEntity group = checkoutGroupRepository.findById(groupId).orElseThrow();
        assertThat(group.getState()).isEqualTo(CheckoutGroupEntity.STATE_READY);

        // Orders are still PAYMENT_PENDING
        List<OrderEntity> orders = orderRepository.findByCheckoutGroupId(groupId);
        assertThat(orders.get(0).getStatus()).isEqualTo(OrderEntity.STATUS_PAYMENT_PENDING);

        // Reservations are still HELD
        List<OrderItemEntity> items = orderItemRepository.findByOrderId(orders.get(0).getId());
        List<InventoryReservationEntity> res = inventoryReservationRepository.findByOrderItemId(items.get(0).getId());
        assertThat(res.get(0).getState()).isEqualTo(InventoryReservationEntity.STATE_HELD);
    }

    @Test
    @DisplayName("DEC-10.4: Insufficient Coin balance at T2 rejects payment, does not create negative balance, and group remains READY")
    void insufficientCoinAtT2RejectsPaymentAndPreservesBalance() {
        long buyerId = createUser("BUYER");
        long sellerId = createUser("SELLER");

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

        // Buyer initially has 50,000 coins
        jdbcTemplate.update("INSERT INTO og_compat.reward_accounts(user_id) VALUES (?) ON CONFLICT DO NOTHING", buyerId);
        jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(entry_id, user_id, policy_id, entry_type, points_delta, actor_id, reason, command_key)
                VALUES (?, ?, ?, 'CORRECTION', 50000, ?, 'Test seed', ?)
                """, UUID.randomUUID(), buyerId, policyId, adminId, "CMD_SEED_" + UUID.randomUUID());

        CreatedProduct p = createUc83Product(sellerId, "Insufficient Coin Item", new BigDecimal("100000"), 5, true);

        groupCheckoutService.setConfiguredMaxRedemptionPercentage("50");
        groupCheckoutService.setConfiguredCanonicalPolicyCode("REWARD_CHECKOUT_STANDARD");

        try {
            GroupCheckoutRequest checkoutReq = new GroupCheckoutRequest(
                    "GRP-INSUFF-" + UUID.randomUUID(),
                    testAddress(),
                    "SELF_PICKUP",
                    null,
                    "VNPAY",
                    null,
                    50000L, // 50,000 coins
                    List.of(new GroupCheckoutItemRequest(p.productId(), 1, null))
            );

            GroupCheckoutExecutionResult checkoutResult = txTemplate.execute(status ->
                    groupCheckoutService.executeCheckout(buyerId, checkoutReq)
            );
            UUID groupId = checkoutResult.response().groupId();

            GroupPaymentResponse paymentResp = txTemplate.execute(status ->
                    groupPaymentService.createGroupPayment(buyerId, groupId, "127.0.0.1")
            );

            // Simulate coins being spent before IPN arrives (deduct 45,000 coins so balance becomes 5,000 < 50,000)
            jdbcTemplate.update("""
                    INSERT INTO og_compat.reward_ledger(entry_id, user_id, policy_id, entry_type, points_delta, actor_id, reason, command_key)
                    VALUES (?, ?, ?, 'CORRECTION', -45000, ?, 'Admin adjust', ?)
                    """, UUID.randomUUID(), buyerId, policyId, adminId, "CMD_ADJ_" + UUID.randomUUID());

            Long intermediateBalance = jdbcTemplate.queryForObject("SELECT balance FROM og_compat.reward_accounts WHERE user_id = ?", Long.class, buyerId);
            assertThat(intermediateBalance).isEqualTo(5000L);

            // IPN arrives
            Map<String, String> ipnParams = buildSignedCallback(paymentResp.txnRef(), new BigDecimal("50000"), "00", "00");
            GroupPaymentIpnResult ipnResult = txTemplate.execute(status ->
                    groupPaymentService.processGroupPaymentIpn(ipnParams, "127.0.0.1")
            );

            // Must reject with code "04"
            assertThat(ipnResult.rspCode()).isEqualTo("04");

            // Group is NOT PAID, remains READY
            CheckoutGroupEntity group = checkoutGroupRepository.findById(groupId).orElseThrow();
            assertThat(group.getState()).isEqualTo(CheckoutGroupEntity.STATE_READY);

            // Orders are NOT PAID_HELD, remain PAYMENT_PENDING (no fulfillment)
            List<OrderEntity> orders = orderRepository.findByCheckoutGroupId(groupId);
            assertThat(orders.get(0).getStatus()).isEqualTo(OrderEntity.STATUS_PAYMENT_PENDING);

            // Buyer balance is NOT negative and remains 5,000
            Long finalBalance = jdbcTemplate.queryForObject("SELECT balance FROM og_compat.reward_accounts WHERE user_id = ?", Long.class, buyerId);
            assertThat(finalBalance).isEqualTo(5000L);

            // No DEBIT_CHECKOUT was created
            Integer debitCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM og_compat.reward_ledger WHERE group_id = ? AND entry_type = 'DEBIT_CHECKOUT'",
                    Integer.class, groupId
            );
            assertThat(debitCount).isEqualTo(0);

            // Cash payment evidence is preserved in payment_confirmations with disposition RECONCILIATION
            Optional<PaymentConfirmationEntity> confOpt = paymentConfirmationRepository.findByAttemptId(paymentResp.attemptId());
            assertThat(confOpt).isPresent();
            assertThat(confOpt.get().getAmount()).isEqualByComparingTo(new BigDecimal("50000"));
            assertThat(confOpt.get().getDisposition()).isEqualTo(PaymentConfirmationEntity.DISPOSITION_RECONCILIATION);

            // Reconciliation case created for Ops/Accounting to handle cash collected
            String dedupKey = "REC:INSUFFICIENT_COIN:" + paymentResp.intentId();
            Optional<PaymentReconciliationCaseEntity> caseOpt = paymentReconciliationCaseRepository.findByDedupKey(dedupKey);
            assertThat(caseOpt).isPresent();
            assertThat(caseOpt.get().getStatus()).isEqualTo(PaymentReconciliationCaseEntity.STATUS_OPEN);
            assertThat(caseOpt.get().getCaseType()).isEqualTo(PaymentReconciliationCaseEntity.CASE_LATE_SUCCESS_ON_FAILED);

            // Idempotent Retry: Same IPN re-delivered does NOT duplicate confirmation or case, and does not debit
            GroupPaymentIpnResult retryResult = txTemplate.execute(status ->
                    groupPaymentService.processGroupPaymentIpn(ipnParams, "127.0.0.1")
            );
            assertThat(retryResult.rspCode()).isEqualTo("02");
            assertThat(paymentConfirmationRepository.findByIntentId(paymentResp.intentId())).hasSize(1);
            assertThat(paymentReconciliationCaseRepository.findAll().stream()
                    .filter(c -> dedupKey.equals(c.getDedupKey()))
                    .count()).isEqualTo(1);
        } finally {
            groupCheckoutService.setConfiguredMaxRedemptionPercentage(null);
            groupCheckoutService.setConfiguredCanonicalPolicyCode(null);
        }
    }

    @Test
    @DisplayName("Concurrency & Duplicate: Multiple txn_ref for same group does not double debit or double allocate")
    void multipleTxnRefForSameGroupDoesNotDoubleDebitOrFulfill() {
        long buyerId = createUser("BUYER");
        long sellerId = createUser("SELLER");

        CreatedProduct p = createUc83Product(sellerId, "Multi Txn Item", new BigDecimal("100000"), 5, true);

        GroupCheckoutRequest checkoutReq = new GroupCheckoutRequest(
                "GRP-MULTITXN-" + UUID.randomUUID(),
                testAddress(),
                "SELF_PICKUP",
                null,
                "VNPAY",
                null,
                null,
                List.of(new GroupCheckoutItemRequest(p.productId(), 1, null))
        );

        GroupCheckoutExecutionResult checkoutResult = txTemplate.execute(status ->
                groupCheckoutService.executeCheckout(buyerId, checkoutReq)
        );
        UUID groupId = checkoutResult.response().groupId();

        // Create first attempt (e.g. user opens payment page)
        GroupPaymentResponse paymentResp1 = txTemplate.execute(status ->
                groupPaymentService.createGroupPayment(buyerId, groupId, "127.0.0.1")
        );

        // Attempt 1 fails/times out
        txTemplate.execute(status -> {
            PaymentAttemptEntity a1 = paymentAttemptRepository.findById(paymentResp1.attemptId()).orElseThrow();
            a1.markFailed("User cancelled", Instant.now());
            paymentAttemptRepository.saveAndFlush(a1);
            return null;
        });

        // User retries: creates second attempt with new txnRef
        GroupPaymentResponse paymentResp2 = txTemplate.execute(status ->
                groupPaymentService.createGroupPayment(buyerId, groupId, "127.0.0.1")
        );

        assertThat(paymentResp1.txnRef()).isNotEqualTo(paymentResp2.txnRef());

        // Second attempt confirms successfully
        Map<String, String> ipnParams2 = buildSignedCallback(paymentResp2.txnRef(), new BigDecimal("100000"), "00", "00");
        GroupPaymentIpnResult result2 = txTemplate.execute(status ->
                groupPaymentService.processGroupPaymentIpn(ipnParams2, "127.0.0.1")
        );
        assertThat(result2.rspCode()).isEqualTo("00");

        // Group is now PAID
        CheckoutGroupEntity group = checkoutGroupRepository.findById(groupId).orElseThrow();
        assertThat(group.getState()).isEqualTo(CheckoutGroupEntity.STATE_PAID);

        // First attempt arrives afterwards (late IPN for attempt 1)
        Map<String, String> ipnParams1 = buildSignedCallback(paymentResp1.txnRef(), new BigDecimal("100000"), "00", "00");
        GroupPaymentIpnResult result1 = txTemplate.execute(status ->
                groupPaymentService.processGroupPaymentIpn(ipnParams1, "127.0.0.1")
        );
        // Second attempt returns already confirmed
        assertThat(result1.rspCode()).isEqualTo("02");

        // Exactly one confirmation exists for the intent
        List<PaymentConfirmationEntity> confirmations = paymentConfirmationRepository.findByIntentId(paymentResp1.intentId());
        assertThat(confirmations).hasSize(1);
    }

    @Test
    @DisplayName("DEC-10-LATE-PAYMENT: Late payment after deadline recorded as RECONCILIATION without fulfilling orders or marking group PAID")
    void latePaymentAfterDeadlineRecordedAsReconciliation() {
        long buyerId = createUser("BUYER");
        long sellerId = createUser("SELLER");

        CreatedProduct p = createUc83Product(sellerId, "Late Payment Item", new BigDecimal("100000"), 5, true);

        GroupCheckoutRequest checkoutReq = new GroupCheckoutRequest(
                "GRP-LATE-" + UUID.randomUUID(),
                testAddress(),
                "SELF_PICKUP",
                null,
                "VNPAY",
                null,
                null,
                List.of(new GroupCheckoutItemRequest(p.productId(), 1, null))
        );

        GroupCheckoutExecutionResult checkoutResult = txTemplate.execute(status ->
                groupCheckoutService.executeCheckout(buyerId, checkoutReq)
        );
        UUID groupId = checkoutResult.response().groupId();

        GroupPaymentResponse paymentResp = txTemplate.execute(status ->
                groupPaymentService.createGroupPayment(buyerId, groupId, "127.0.0.1")
        );

        // Simulate deadline expiration by setting deadline to 1 hour ago
        jdbcTemplate.execute("SET session_replication_role = 'replica'");
        try {
            jdbcTemplate.update("""
                    UPDATE public.checkout_groups
                    SET created_at = CURRENT_TIMESTAMP - INTERVAL '2 hours',
                        payment_due_at = CURRENT_TIMESTAMP - INTERVAL '1 hour'
                    WHERE group_id = ?
                    """, groupId);
            jdbcTemplate.update("""
                    UPDATE public.orders
                    SET created_at = (SELECT created_at FROM public.checkout_groups WHERE group_id = ?),
                        payment_due_at = (SELECT payment_due_at FROM public.checkout_groups WHERE group_id = ?),
                        handover_day = CURRENT_DATE - INTERVAL '1 day'
                    WHERE group_id = ?
                    """, groupId, groupId, groupId);
            jdbcTemplate.update("""
                    UPDATE public.payments
                    SET created_at = (SELECT created_at FROM public.checkout_groups WHERE group_id = ?),
                        deadline = (SELECT payment_due_at FROM public.checkout_groups WHERE group_id = ?)
                    WHERE source_payment_intents_intent_id = ?
                    """, groupId, groupId, paymentResp.intentId());
        } finally {
            jdbcTemplate.execute("SET session_replication_role = 'origin'");
        }

        // IPN callback arrives after deadline
        Map<String, String> ipnParams = buildSignedCallback(paymentResp.txnRef(), new BigDecimal("100000"), "00", "00");
        GroupPaymentIpnResult ipnResult = txTemplate.execute(status ->
                groupPaymentService.processGroupPaymentIpn(ipnParams, "127.0.0.1")
        );

        // Must be recorded with disposition RECONCILIATION
        assertThat(ipnResult.rspCode()).isEqualTo("00");
        assertThat(ipnResult.disposition()).isEqualTo("RECONCILIATION");

        // 1. Group transitions to EXPIRED (DEC-10-LATE-PAYMENT)
        CheckoutGroupEntity group = checkoutGroupRepository.findById(groupId).orElseThrow();
        assertThat(group.getState()).isEqualTo(CheckoutGroupEntity.STATE_EXPIRED);

        // 2. Orders are NOT PAID_HELD, remain PAYMENT_PENDING (no fulfillment)
        List<OrderEntity> orders = orderRepository.findByCheckoutGroupId(groupId);
        assertThat(orders.get(0).getStatus()).isEqualTo(OrderEntity.STATUS_PAYMENT_PENDING);

        // 3. Reservations are still HELD (not FUNDED)
        List<OrderItemEntity> items = orderItemRepository.findByOrderId(orders.get(0).getId());
        List<InventoryReservationEntity> res = inventoryReservationRepository.findByOrderItemId(items.get(0).getId());
        assertThat(res.get(0).getState()).isEqualTo(InventoryReservationEntity.STATE_HELD);

        // 4. Attempt status transitioned to SUCCESS_ON_EXPIRED_ORDER
        PaymentAttemptEntity attempt = paymentAttemptRepository.findById(paymentResp.attemptId()).orElseThrow();
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptEntity.STATUS_SUCCESS_ON_EXPIRED_ORDER);

        // 5. IPN Event outcome transitioned to APPLIED_EXPIRED_ORDER
        List<PaymentIpnEventEntity> ipnEvents = paymentIpnEventRepository.findByAttemptId(paymentResp.attemptId());
        assertThat(ipnEvents).isNotEmpty();
        assertThat(ipnEvents.get(0).getProcessingOutcome()).isEqualTo(PaymentIpnEventEntity.OUTCOME_APPLIED_EXPIRED_ORDER);

        // 6. Reconciliation Case created with EXPIRED_ORDER
        String dedupKey = "REC:EXPIRED_ORDER:" + paymentResp.intentId();
        Optional<PaymentReconciliationCaseEntity> recCaseOpt = paymentReconciliationCaseRepository.findByDedupKey(dedupKey);
        assertThat(recCaseOpt).isPresent();
        PaymentReconciliationCaseEntity recCase = recCaseOpt.get();
        assertThat(recCase.getCaseType()).isEqualTo(PaymentReconciliationCaseEntity.CASE_EXPIRED_ORDER);
        assertThat(recCase.getStatus()).isEqualTo(PaymentReconciliationCaseEntity.STATUS_OPEN);
        assertThat(recCase.getIntentId()).isEqualTo(paymentResp.intentId());
        assertThat(recCase.getAttemptId()).isEqualTo(paymentResp.attemptId());

        // 7. No Coin debit in reward_ledger
        Optional<RewardLedgerEntity> debitOpt = rewardLedgerRepository.findByGroupIdAndEntryType(groupId, RewardLedgerEntity.ENTRY_DEBIT_CHECKOUT);
        assertThat(debitOpt).isEmpty();

        // 8. Idempotent Retry: Same IPN re-delivered does NOT duplicate reconciliation case
        GroupPaymentIpnResult retryResult = txTemplate.execute(status ->
                groupPaymentService.processGroupPaymentIpn(ipnParams, "127.0.0.1")
        );
        assertThat(retryResult.rspCode()).isEqualTo("02"); // already confirmed / expired
        assertThat(paymentReconciliationCaseRepository.findAll().stream()
                .filter(c -> dedupKey.equals(c.getDedupKey()))
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("DEC-10-LATE-PAYMENT: Payment after 1-hour deadline BUT before handover date is NOT marked EXPIRED_ORDER")
    void paymentAfterOneHourDeadlineBeforeHandoverDayRecordedAsReconciliation() {
        long buyerId = createUser("BUYER");
        long sellerId = createUser("SELLER");

        CreatedProduct p = createUc83Product(sellerId, "Deadline Expiry Item", new BigDecimal("100000"), 5, true);

        GroupCheckoutRequest checkoutReq = new GroupCheckoutRequest(
                "GRP-EXP1H-" + UUID.randomUUID(),
                testAddress(),
                "SELF_PICKUP",
                null,
                "VNPAY",
                null,
                null,
                List.of(new GroupCheckoutItemRequest(p.productId(), 1, null))
        );

        GroupCheckoutExecutionResult checkoutResult = txTemplate.execute(status ->
                groupCheckoutService.executeCheckout(buyerId, checkoutReq)
        );
        UUID groupId = checkoutResult.response().groupId();

        GroupPaymentResponse paymentResp = txTemplate.execute(status ->
                groupPaymentService.createGroupPayment(buyerId, groupId, "127.0.0.1")
        );

        // Simulate 1-hour payment deadline passed, but handover_day is still 2 days in the future
        jdbcTemplate.execute("SET session_replication_role = 'replica'");
        try {
            jdbcTemplate.update("""
                    UPDATE public.checkout_groups
                    SET created_at = CURRENT_TIMESTAMP - INTERVAL '2 hours',
                        payment_due_at = CURRENT_TIMESTAMP - INTERVAL '1 hour'
                    WHERE group_id = ?
                    """, groupId);
            jdbcTemplate.update("""
                    UPDATE public.orders
                    SET created_at = (SELECT created_at FROM public.checkout_groups WHERE group_id = ?),
                        payment_due_at = (SELECT payment_due_at FROM public.checkout_groups WHERE group_id = ?),
                        handover_day = CURRENT_DATE + INTERVAL '2 days'
                    WHERE group_id = ?
                    """, groupId, groupId, groupId);
            jdbcTemplate.update("""
                    UPDATE public.payments
                    SET created_at = (SELECT created_at FROM public.checkout_groups WHERE group_id = ?),
                        deadline = (SELECT payment_due_at FROM public.checkout_groups WHERE group_id = ?)
                    WHERE source_payment_intents_intent_id = ?
                    """, groupId, groupId, paymentResp.intentId());
        } finally {
            jdbcTemplate.execute("SET session_replication_role = 'origin'");
        }

        // IPN arrives
        Map<String, String> ipnParams = buildSignedCallback(paymentResp.txnRef(), new BigDecimal("100000"), "00", "00");
        GroupPaymentIpnResult ipnResult = txTemplate.execute(status ->
                groupPaymentService.processGroupPaymentIpn(ipnParams, "127.0.0.1")
        );

        // Captured with disposition RECONCILIATION
        assertThat(ipnResult.rspCode()).isEqualTo("00");
        assertThat(ipnResult.disposition()).isEqualTo("RECONCILIATION");

        // Group is NOT EXPIRED, remains READY because handover date has NOT passed!
        CheckoutGroupEntity group = checkoutGroupRepository.findById(groupId).orElseThrow();
        assertThat(group.getState()).isEqualTo(CheckoutGroupEntity.STATE_READY);

        // Orders remain PAYMENT_PENDING
        List<OrderEntity> orders = orderRepository.findByCheckoutGroupId(groupId);
        assertThat(orders.get(0).getStatus()).isEqualTo(OrderEntity.STATUS_PAYMENT_PENDING);

        // Attempt is transitioned to SUCCESS_PENDING_REVIEW (not hanging PENDING)
        PaymentAttemptEntity attempt = paymentAttemptRepository.findById(paymentResp.attemptId()).orElseThrow();
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptEntity.STATUS_SUCCESS_PENDING_REVIEW);

        // Exactly one reconciliation case created for countdown expiry
        String expiredOrderDedupKey = "REC:EXPIRED_ORDER:" + paymentResp.intentId();
        assertThat(paymentReconciliationCaseRepository.findByDedupKey(expiredOrderDedupKey)).isEmpty();

        String countdownDedupKey = "REC:COUNTDOWN_EXPIRED:" + paymentResp.intentId();
        Optional<PaymentReconciliationCaseEntity> countdownCaseOpt = paymentReconciliationCaseRepository.findByDedupKey(countdownDedupKey);
        assertThat(countdownCaseOpt).isPresent();
        PaymentReconciliationCaseEntity recCase = countdownCaseOpt.get();
        assertThat(recCase.getStatus()).isEqualTo(PaymentReconciliationCaseEntity.STATUS_OPEN);
        assertThat(recCase.getCaseType()).isEqualTo(PaymentReconciliationCaseEntity.CASE_EXPIRED_ORDER);
        assertThat(recCase.getResolutionNote()).contains("payment_due_at");

        // Duplicate IPN idempotent check: returns "02", no duplicate confirmation or case
        GroupPaymentIpnResult dupResult = txTemplate.execute(status ->
                groupPaymentService.processGroupPaymentIpn(ipnParams, "127.0.0.1")
        );
        assertThat(dupResult.rspCode()).isEqualTo("02");
        assertThat(paymentConfirmationRepository.findByIntentId(paymentResp.intentId())).hasSize(1);
        assertThat(paymentReconciliationCaseRepository.findAll().stream()
                .filter(c -> countdownDedupKey.equals(c.getDedupKey()))
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("PO Decision 1.3 / Case C: Late successful IPN on FAILED attempt preserves payment confirmation and creates LATE_SUCCESS_ON_FAILED case")
    void lateSuccessOnTerminalFailedAttemptPreservesEvidenceAndCreatesReconciliationCase() {
        long buyerId = createUser("BUYER");
        long sellerId = createUser("SELLER");

        CreatedProduct p = createUc83Product(sellerId, "Terminal Attempt Item", new BigDecimal("100000"), 5, true);

        GroupCheckoutRequest checkoutReq = new GroupCheckoutRequest(
                "GRP-TERM-" + UUID.randomUUID(),
                testAddress(),
                "SELF_PICKUP",
                null,
                "VNPAY",
                null,
                null,
                List.of(new GroupCheckoutItemRequest(p.productId(), 1, null))
        );

        GroupCheckoutExecutionResult checkoutResult = txTemplate.execute(status ->
                groupCheckoutService.executeCheckout(buyerId, checkoutReq)
        );
        UUID groupId = checkoutResult.response().groupId();

        GroupPaymentResponse paymentResp = txTemplate.execute(status ->
                groupPaymentService.createGroupPayment(buyerId, groupId, "127.0.0.1")
        );

        // Attempt is marked FAILED (e.g. buyer cancelled or timed out) while group is still READY
        txTemplate.execute(status -> {
            PaymentAttemptEntity attempt = paymentAttemptRepository.findById(paymentResp.attemptId()).orElseThrow();
            attempt.markFailed("Simulated timeout/cancellation", Instant.now());
            paymentAttemptRepository.saveAndFlush(attempt);
            return null;
        });

        PaymentAttemptEntity failedAttempt = paymentAttemptRepository.findById(paymentResp.attemptId()).orElseThrow();
        assertThat(failedAttempt.getStatus()).isEqualTo(PaymentAttemptEntity.STATUS_FAILED);

        // But late successful IPN arrives from VNPay
        Map<String, String> ipnParams = buildSignedCallback(paymentResp.txnRef(), new BigDecimal("100000"), "00", "00");
        GroupPaymentIpnResult ipnResult = txTemplate.execute(status ->
                groupPaymentService.processGroupPaymentIpn(ipnParams, "127.0.0.1")
        );

        // Must acknowledge with "02" and RECONCILIATION
        assertThat(ipnResult.rspCode()).isEqualTo("02");
        assertThat(ipnResult.disposition()).isEqualTo("RECONCILIATION");

        // Payment confirmation must be created with disposition RECONCILIATION, preserving the 100,000 VND cash
        Optional<PaymentConfirmationEntity> confOpt = paymentConfirmationRepository.findByAttemptId(paymentResp.attemptId());
        assertThat(confOpt).isPresent();
        assertThat(confOpt.get().getAmount()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(confOpt.get().getDisposition()).isEqualTo(PaymentConfirmationEntity.DISPOSITION_RECONCILIATION);

        // Reconciliation case created with CASE_LATE_SUCCESS_ON_FAILED
        String dedupKey = "REC:LATE_SUCCESS_ON_FAILED:" + paymentResp.attemptId() + ":" + ipnParams.get("vnp_TransactionNo");
        Optional<PaymentReconciliationCaseEntity> recCaseOpt = paymentReconciliationCaseRepository.findByDedupKey(dedupKey);
        assertThat(recCaseOpt).isPresent();
        PaymentReconciliationCaseEntity recCase = recCaseOpt.get();
        assertThat(recCase.getCaseType()).isEqualTo(PaymentReconciliationCaseEntity.CASE_LATE_SUCCESS_ON_FAILED);
        assertThat(recCase.getStatus()).isEqualTo(PaymentReconciliationCaseEntity.STATUS_OPEN);
        assertThat(recCase.getIntentId()).isEqualTo(paymentResp.intentId());

        // Group is NOT PAID, remains READY
        CheckoutGroupEntity group = checkoutGroupRepository.findById(groupId).orElseThrow();
        assertThat(group.getState()).isEqualTo(CheckoutGroupEntity.STATE_READY);

        // Orders remain PAYMENT_PENDING
        List<OrderEntity> orders = orderRepository.findByCheckoutGroupId(groupId);
        assertThat(orders.get(0).getStatus()).isEqualTo(OrderEntity.STATUS_PAYMENT_PENDING);

        // No Coin debit
        Optional<RewardLedgerEntity> debitOpt = rewardLedgerRepository.findByGroupIdAndEntryType(groupId, RewardLedgerEntity.ENTRY_DEBIT_CHECKOUT);
        assertThat(debitOpt).isEmpty();

        // Duplicate IPN re-delivered does NOT duplicate confirmation or case
        GroupPaymentIpnResult dupResult = txTemplate.execute(status ->
                groupPaymentService.processGroupPaymentIpn(ipnParams, "127.0.0.1")
        );
        assertThat(dupResult.rspCode()).isEqualTo("02");
        assertThat(paymentConfirmationRepository.findByIntentId(paymentResp.intentId())).hasSize(1);
        assertThat(paymentReconciliationCaseRepository.findAll().stream()
                .filter(c -> dedupKey.equals(c.getDedupKey()))
                .count()).isEqualTo(1);
    }
}
