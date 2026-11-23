package com.oldbutgold.shop.modules.payment.application;

import com.oldbutgold.shop.modules.commerce.application.GroupCheckoutDtos.*;
import com.oldbutgold.shop.modules.commerce.application.GroupCheckoutService;
import com.oldbutgold.shop.modules.commerce.application.SimulatedPoApprovalTestFixture;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.*;
import com.oldbutgold.shop.modules.payment.application.GroupPaymentDtos.*;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.*;
import com.oldbutgold.shop.shared.PostgresTestSafetyGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real PostgreSQL Multi-threaded Concurrency Integration Tests for Payment IPN & Coin T2 Lifecycle.
 *
 * Verifies real multi-threaded race conditions against live PostgreSQL 17+:
 * - Case A: Duplicate IPN for the same payment attempt sent simultaneously.
 * - Case B: Two different txnRefs for the same Checkout Group sent simultaneously.
 * - Case C: Coin balance depleted before T2 IPN execution (insufficient coins at T2).
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("GroupPaymentConcurrencyPostgresTest - Real Multi-Threaded PostgreSQL Concurrency Verification")
class GroupPaymentConcurrencyPostgresTest {

    private static final Logger log = LoggerFactory.getLogger(GroupPaymentConcurrencyPostgresTest.class);

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
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private PaymentConfirmationRepository paymentConfirmationRepository;

    @Autowired
    private PaymentReconciliationCaseRepository paymentReconciliationCaseRepository;

    @Autowired
    private RewardLedgerRepository rewardLedgerRepository;

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
        groupCheckoutService.setConfiguredMaxRedemptionPercentage("50");
        groupCheckoutService.setConfiguredCanonicalPolicyCode("REWARD_CHECKOUT_STANDARD");
    }

    @AfterEach
    void tearDown() {
        groupCheckoutService.setConfiguredMaxRedemptionPercentage(null);
        groupCheckoutService.setConfiguredCanonicalPolicyCode(null);
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

    private Long ensureCanonicalRewardPolicyId() {
        // Idempotent provisioning so tests do not depend on prior test classes committing reward_policies.
        return jdbcTemplate.queryForObject(
                "SELECT public.og_provision_canonical_reward_policy(?)",
                Long.class,
                adminId
        );
    }

    private void seedCoins(long userId, long points) {
        Long policyId = ensureCanonicalRewardPolicyId();
        Integer accCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.reward_accounts WHERE user_id = ?", Integer.class, userId);
        if (accCount == null || accCount == 0) {
            jdbcTemplate.update("INSERT INTO og_compat.reward_accounts(user_id) VALUES (?)", userId);
        }
        jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(entry_id, user_id, policy_id, entry_type, points_delta, actor_id, reason, command_key)
                VALUES (?, ?, ?, 'CORRECTION', ?, ?, 'Test coin seed', ?)
                """, UUID.randomUUID(), userId, policyId, points, adminId, "CMD_SEED_" + UUID.randomUUID());
    }

    private void adjustCoins(long userId, long delta) {
        Long policyId = ensureCanonicalRewardPolicyId();
        jdbcTemplate.update("""
                INSERT INTO og_compat.reward_ledger(entry_id, user_id, policy_id, entry_type, points_delta, actor_id, reason, command_key)
                VALUES (?, ?, ?, 'CORRECTION', ?, ?, 'Admin adjust', ?)
                """, UUID.randomUUID(), userId, policyId, delta, adminId, "CMD_ADJ_" + UUID.randomUUID());
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
        return new ShippingAddressDto("Tran Van Concurrency", "0987654321", "TP Ho Chi Minh", "Quan 1", "Phuong Ben Nghe", "456 Le Loi");
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
    @DisplayName("Case A — Duplicate IPN: Two concurrent IPNs for the same attempt result in exactly 1 confirmation & 1 debit")
    void caseA_concurrentDuplicateIpnDoesNotDoubleDebitOrConfirm() throws Exception {
        try (var poApprovalScope = SimulatedPoApprovalTestFixture.openScope(groupCheckoutService)) {
            long buyerId = createUser("BUYER");
            long sellerId = createUser("SELLER");

            // Give buyer 100,000 coins
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.execute(status -> {
                seedCoins(buyerId, 100000L);
                return null;
            });

            CreatedProduct prod = createUc83Product(sellerId, "Concurrent Item A", new BigDecimal("200000"), 5, true);

            // T1 Checkout with 50,000 coins redemption
            GroupCheckoutRequest checkoutReq = new GroupCheckoutRequest(
                    "GRP-CONCUR-A-" + UUID.randomUUID(),
                    testAddress(),
                    "SELF_PICKUP",
                    null,
                    "VNPAY",
                    null,
                    50000L,
                    List.of(new GroupCheckoutItemRequest(prod.productId(), 1, null))
            );

            GroupCheckoutExecutionResult checkoutResult = tx.execute(status ->
                    groupCheckoutService.executeCheckout(buyerId, checkoutReq)
            );
            UUID groupId = checkoutResult.response().groupId();

            GroupPaymentResponse paymentResp = tx.execute(status ->
                    groupPaymentService.createGroupPayment(buyerId, groupId, "127.0.0.1")
            );

            Map<String, String> ipnParams = buildSignedCallback(paymentResp.txnRef(), new BigDecimal("150000"), "00", "00");

            // Setup real concurrency with 2 threads and CyclicBarrier
            int threads = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CyclicBarrier barrier = new CyclicBarrier(threads);
            List<Future<GroupPaymentIpnResult>> futures = new ArrayList<>();

            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    TransactionTemplate threadTx = new TransactionTemplate(transactionManager);
                    return threadTx.execute(status ->
                            groupPaymentService.processGroupPaymentIpn(ipnParams, "127.0.0.1")
                    );
                }));
            }

            List<GroupPaymentIpnResult> results = new ArrayList<>();
            for (Future<GroupPaymentIpnResult> future : futures) {
                results.add(future.get(15, TimeUnit.SECONDS));
            }
            executor.shutdown();

            // One must succeed ("00"), other can be either "00" or already confirmed ("02")
            assertThat(results).anyMatch(r -> "00".equals(r.rspCode()));

            // Exactly 1 payment confirmation
            List<PaymentConfirmationEntity> confirmations = paymentConfirmationRepository.findByIntentId(paymentResp.intentId());
            assertThat(confirmations).hasSize(1);

            // Exactly 1 DEBIT_CHECKOUT in reward_ledger
            Optional<RewardLedgerEntity> debitOpt = rewardLedgerRepository.findByGroupIdAndEntryType(groupId, RewardLedgerEntity.ENTRY_DEBIT_CHECKOUT);
            assertThat(debitOpt).isPresent();
            assertThat(debitOpt.get().getPointsDelta()).isEqualTo(-50000L);

            // Final buyer balance is exactly 50,000 (deducted exactly once, no double debit)
            Long balance = jdbcTemplate.queryForObject("SELECT balance FROM og_compat.reward_accounts WHERE user_id = ?", Long.class, buyerId);
            assertThat(balance).isEqualTo(50000L);

            // Group is PAID
            CheckoutGroupEntity group = checkoutGroupRepository.findById(groupId).orElseThrow();
            assertThat(group.getState()).isEqualTo(CheckoutGroupEntity.STATE_PAID);

            // Orders are PAID_HELD
            List<OrderEntity> orders = orderRepository.findByCheckoutGroupId(groupId);
            assertThat(orders.get(0).getStatus()).isEqualTo(OrderEntity.STATUS_PAID_HELD);
        }
    }

    @Test
    @DisplayName("Case B — Two different txnRefs for same Group: Only 1 confirms, exactly 1 debit recorded")
    void caseB_twoDifferentTxnRefsSameGroupConcurrentOnlyOneWins() throws Exception {
        try (var poApprovalScope = SimulatedPoApprovalTestFixture.openScope(groupCheckoutService)) {
            long buyerId = createUser("BUYER");
            long sellerId = createUser("SELLER");

            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.execute(status -> {
                seedCoins(buyerId, 100000L);
                return null;
            });

            CreatedProduct prod = createUc83Product(sellerId, "Concurrent Item B", new BigDecimal("100000"), 5, true);

            GroupCheckoutRequest checkoutReq = new GroupCheckoutRequest(
                    "GRP-CONCUR-B-" + UUID.randomUUID(),
                    testAddress(),
                    "SELF_PICKUP",
                    null,
                    "VNPAY",
                    null,
                    20000L,
                    List.of(new GroupCheckoutItemRequest(prod.productId(), 1, null))
            );

            GroupCheckoutExecutionResult checkoutResult = tx.execute(status ->
                    groupCheckoutService.executeCheckout(buyerId, checkoutReq)
            );
            UUID groupId = checkoutResult.response().groupId();

            // First attempt created
            GroupPaymentResponse paymentResp1 = tx.execute(status ->
                    groupPaymentService.createGroupPayment(buyerId, groupId, "127.0.0.1")
            );

            // Mark attempt 1 as failed/cancelled to allow attempt 2 creation under uq_intent_pending_attempt
            tx.execute(status -> {
                PaymentAttemptEntity a1 = paymentAttemptRepository.findById(paymentResp1.attemptId()).orElseThrow();
                a1.markFailed("Cancelled by user", Instant.now());
                paymentAttemptRepository.saveAndFlush(a1);
                return null;
            });

            // Second attempt created with different txnRef
            GroupPaymentResponse paymentResp2 = tx.execute(status ->
                    groupPaymentService.createGroupPayment(buyerId, groupId, "127.0.0.1")
            );
            assertThat(paymentResp1.txnRef()).isNotEqualTo(paymentResp2.txnRef());

            Map<String, String> ipnParams1 = buildSignedCallback(paymentResp1.txnRef(), new BigDecimal("80000"), "00", "00");
            Map<String, String> ipnParams2 = buildSignedCallback(paymentResp2.txnRef(), new BigDecimal("80000"), "00", "00");

            // Concurrent execution of both callbacks
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CyclicBarrier barrier = new CyclicBarrier(2);

            Future<GroupPaymentIpnResult> future1 = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                TransactionTemplate threadTx = new TransactionTemplate(transactionManager);
                return threadTx.execute(status ->
                        groupPaymentService.processGroupPaymentIpn(ipnParams1, "127.0.0.1")
                );
            });

            Future<GroupPaymentIpnResult> future2 = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                TransactionTemplate threadTx = new TransactionTemplate(transactionManager);
                return threadTx.execute(status ->
                        groupPaymentService.processGroupPaymentIpn(ipnParams2, "127.0.0.1")
                );
            });

            GroupPaymentIpnResult res1 = future1.get(15, TimeUnit.SECONDS);
            GroupPaymentIpnResult res2 = future2.get(15, TimeUnit.SECONDS);
            executor.shutdown();

            // Exactly one confirmed
            List<PaymentConfirmationEntity> confirmations = paymentConfirmationRepository.findByIntentId(paymentResp1.intentId());
            assertThat(confirmations).hasSize(1);

            // Exactly one DEBIT_CHECKOUT in reward_ledger
            Optional<RewardLedgerEntity> debitOpt = rewardLedgerRepository.findByGroupIdAndEntryType(groupId, RewardLedgerEntity.ENTRY_DEBIT_CHECKOUT);
            assertThat(debitOpt).isPresent();
            assertThat(debitOpt.get().getPointsDelta()).isEqualTo(-20000L);

            // Buyer balance is exactly 80,000 (100k - 20k)
            Long balance = jdbcTemplate.queryForObject("SELECT balance FROM og_compat.reward_accounts WHERE user_id = ?", Long.class, buyerId);
            assertThat(balance).isEqualTo(80000L);

            // Group is PAID
            CheckoutGroupEntity group = checkoutGroupRepository.findById(groupId).orElseThrow();
            assertThat(group.getState()).isEqualTo(CheckoutGroupEntity.STATE_PAID);
        }
    }

    @Test
    @DisplayName("Case C — Coin balance depleted before T2: Rejects payment, does not mark PAID, preserves balance")
    void caseC_insufficientCoinAtT2RejectsPaymentAndPreservesBalance() {
        try (var poApprovalScope = SimulatedPoApprovalTestFixture.openScope(groupCheckoutService)) {
            long buyerId = createUser("BUYER");
            long sellerId = createUser("SELLER");

            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.execute(status -> {
                seedCoins(buyerId, 100000L);
                return null;
            });

            CreatedProduct prod = createUc83Product(sellerId, "Insufficient Coin Item", new BigDecimal("100000"), 5, true);

            // T1 Checkout with 50,000 coins redemption (valid at T1)
            GroupCheckoutRequest checkoutReq = new GroupCheckoutRequest(
                    "GRP-INSUF-" + UUID.randomUUID(),
                    testAddress(),
                    "SELF_PICKUP",
                    null,
                    "VNPAY",
                    null,
                    50000L,
                    List.of(new GroupCheckoutItemRequest(prod.productId(), 1, null))
            );

            GroupCheckoutExecutionResult checkoutResult = tx.execute(status ->
                    groupCheckoutService.executeCheckout(buyerId, checkoutReq)
            );
            UUID groupId = checkoutResult.response().groupId();

            GroupPaymentResponse paymentResp = tx.execute(status ->
                    groupPaymentService.createGroupPayment(buyerId, groupId, "127.0.0.1")
            );

            // Before T2 IPN arrives: simulate buyer spent coins elsewhere, depleting balance to 10,000 (< 50,000)
            tx.execute(status -> {
                adjustCoins(buyerId, -90000L);
                return null;
            });

            Long currentBalance = jdbcTemplate.queryForObject("SELECT balance FROM og_compat.reward_accounts WHERE user_id = ?", Long.class, buyerId);
            assertThat(currentBalance).isEqualTo(10000L);

            // IPN arrives at T2
            Map<String, String> ipnParams = buildSignedCallback(paymentResp.txnRef(), new BigDecimal("50000"), "00", "00");
            GroupPaymentIpnResult ipnResult = tx.execute(status ->
                    groupPaymentService.processGroupPaymentIpn(ipnParams, "127.0.0.1")
            );

            // T2 rejects payment due to insufficient coin balance
            assertThat(ipnResult.rspCode()).isEqualTo("04");

            // Attempt is marked FAILED
            PaymentAttemptEntity attempt = paymentAttemptRepository.findById(paymentResp.attemptId()).orElseThrow();
            assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptEntity.STATUS_FAILED);

            // Group is NOT PAID, remains READY
            CheckoutGroupEntity group = checkoutGroupRepository.findById(groupId).orElseThrow();
            assertThat(group.getState()).isEqualTo(CheckoutGroupEntity.STATE_READY);

            // Orders are NOT PAID_HELD, remain PAYMENT_PENDING
            List<OrderEntity> orders = orderRepository.findByCheckoutGroupId(groupId);
            assertThat(orders.get(0).getStatus()).isEqualTo(OrderEntity.STATUS_PAYMENT_PENDING);

            // No DEBIT_CHECKOUT was recorded for this group
            Optional<RewardLedgerEntity> debitOpt = rewardLedgerRepository.findByGroupIdAndEntryType(groupId, RewardLedgerEntity.ENTRY_DEBIT_CHECKOUT);
            assertThat(debitOpt).isEmpty();

            // Balance remains 10,000 (NOT negative)
            Long postBalance = jdbcTemplate.queryForObject("SELECT balance FROM og_compat.reward_accounts WHERE user_id = ?", Long.class, buyerId);
            assertThat(postBalance).isEqualTo(10000L);

            // Cash payment evidence is preserved in payment_confirmations with disposition RECONCILIATION
            Optional<PaymentConfirmationEntity> confOpt = paymentConfirmationRepository.findByAttemptId(paymentResp.attemptId());
            assertThat(confOpt).isPresent();
            assertThat(confOpt.get().getAmount()).isEqualByComparingTo(new BigDecimal("50000"));
            assertThat(confOpt.get().getDisposition()).isEqualTo(PaymentConfirmationEntity.DISPOSITION_RECONCILIATION);

            // Manual reconciliation case is created for Ops/Accounting
            String dedupKey = "REC:INSUFFICIENT_COIN:" + paymentResp.intentId();
            Optional<PaymentReconciliationCaseEntity> caseOpt = paymentReconciliationCaseRepository.findByDedupKey(dedupKey);
            assertThat(caseOpt).isPresent();
            assertThat(caseOpt.get().getStatus()).isEqualTo(PaymentReconciliationCaseEntity.STATUS_OPEN);
            assertThat(caseOpt.get().getCaseType()).isEqualTo(PaymentReconciliationCaseEntity.CASE_LATE_SUCCESS_ON_FAILED);

            // Duplicate IPN callback returns "02" without duplicating confirmation or case
            GroupPaymentIpnResult dupResult = tx.execute(status ->
                    groupPaymentService.processGroupPaymentIpn(ipnParams, "127.0.0.1")
            );
            assertThat(dupResult.rspCode()).isEqualTo("02");
            assertThat(paymentConfirmationRepository.findByIntentId(paymentResp.intentId())).hasSize(1);
            assertThat(paymentReconciliationCaseRepository.findAll().stream()
                    .filter(c -> dedupKey.equals(c.getDedupKey()))
                    .count()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Case D — Concurrent IPN on FAILED attempt: Two concurrent callbacks for same failed attempt create exactly one confirmation and one reconciliation case")
    void caseD_concurrentIpnOnFailedAttemptCreatesExactlyOneConfirmationAndCase() throws Exception {
        long buyerId = createUser("BUYER");
        long sellerId = createUser("SELLER");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CreatedProduct prod = createUc83Product(sellerId, "Concurrent Failed Item", new BigDecimal("100000"), 5, true);

        GroupCheckoutRequest checkoutReq = new GroupCheckoutRequest(
                "GRP-FAILCONC-" + UUID.randomUUID(),
                testAddress(),
                "SELF_PICKUP",
                null,
                "VNPAY",
                null,
                null,
                List.of(new GroupCheckoutItemRequest(prod.productId(), 1, null))
        );

        GroupCheckoutExecutionResult checkoutResult = tx.execute(status ->
                groupCheckoutService.executeCheckout(buyerId, checkoutReq)
        );
        UUID groupId = checkoutResult.response().groupId();

        GroupPaymentResponse paymentResp = tx.execute(status ->
                groupPaymentService.createGroupPayment(buyerId, groupId, "127.0.0.1")
        );

        // Attempt is marked FAILED before callbacks arrive
        tx.execute(status -> {
            PaymentAttemptEntity a = paymentAttemptRepository.findById(paymentResp.attemptId()).orElseThrow();
            a.markFailed("Simulated timeout", Instant.now());
            paymentAttemptRepository.saveAndFlush(a);
            return null;
        });

        Map<String, String> ipnParams = buildSignedCallback(paymentResp.txnRef(), new BigDecimal("100000"), "00", "00");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);

        Future<GroupPaymentIpnResult> future1 = executor.submit(() -> {
            barrier.await(5, TimeUnit.SECONDS);
            TransactionTemplate threadTx = new TransactionTemplate(transactionManager);
            return threadTx.execute(status ->
                    groupPaymentService.processGroupPaymentIpn(ipnParams, "127.0.0.1")
            );
        });

        Future<GroupPaymentIpnResult> future2 = executor.submit(() -> {
            barrier.await(5, TimeUnit.SECONDS);
            TransactionTemplate threadTx = new TransactionTemplate(transactionManager);
            return threadTx.execute(status ->
                    groupPaymentService.processGroupPaymentIpn(ipnParams, "127.0.0.1")
            );
        });

        GroupPaymentIpnResult res1 = future1.get(15, TimeUnit.SECONDS);
        GroupPaymentIpnResult res2 = future2.get(15, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one confirmation created with RECONCILIATION
        List<PaymentConfirmationEntity> confirmations = paymentConfirmationRepository.findByIntentId(paymentResp.intentId());
        assertThat(confirmations).hasSize(1);
        assertThat(confirmations.get(0).getDisposition()).isEqualTo(PaymentConfirmationEntity.DISPOSITION_RECONCILIATION);

        // Exactly one reconciliation case created with CASE_LATE_SUCCESS_ON_FAILED
        String dedupKey = "REC:LATE_SUCCESS_ON_FAILED:" + paymentResp.attemptId() + ":" + ipnParams.get("vnp_TransactionNo");
        Optional<PaymentReconciliationCaseEntity> caseOpt = paymentReconciliationCaseRepository.findByDedupKey(dedupKey);
        assertThat(caseOpt).isPresent();
        assertThat(caseOpt.get().getStatus()).isEqualTo(PaymentReconciliationCaseEntity.STATUS_OPEN);
        assertThat(caseOpt.get().getCaseType()).isEqualTo(PaymentReconciliationCaseEntity.CASE_LATE_SUCCESS_ON_FAILED);

        // Group is NOT PAID, remains READY
        CheckoutGroupEntity group = checkoutGroupRepository.findById(groupId).orElseThrow();
        assertThat(group.getState()).isEqualTo(CheckoutGroupEntity.STATE_READY);

        // Orders remain PAYMENT_PENDING
        List<OrderEntity> orders = orderRepository.findByCheckoutGroupId(groupId);
        assertThat(orders.get(0).getStatus()).isEqualTo(OrderEntity.STATUS_PAYMENT_PENDING);

        // No Coin debit
        Optional<RewardLedgerEntity> debitOpt = rewardLedgerRepository.findByGroupIdAndEntryType(groupId, RewardLedgerEntity.ENTRY_DEBIT_CHECKOUT);
        assertThat(debitOpt).isEmpty();
    }
}

