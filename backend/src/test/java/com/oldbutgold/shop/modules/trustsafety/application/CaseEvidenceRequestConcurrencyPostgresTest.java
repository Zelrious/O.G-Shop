package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseDecisionEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEvidenceRequestEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEvidenceRequestRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRoundEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRoundRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.ReturnReasonCode;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real PostgreSQL Multi-threaded Concurrency Integration Test for API 1 (BD-5 Phase 1 - D-07 Option 1).
 *
 * Verifies real multi-threaded race conditions against live PostgreSQL 17+:
 * - Scenario A: Same commandKey + same payload -> Exactly 1 created (201), other replayed (200), same requestId.
 * - Scenario B: Same commandKey + different payload -> Exactly 1 created, other rejected with 409 COMMAND_KEY_CONFLICT.
 * - Scenario C: Different commandKey + same Case -> Exactly 1 PENDING created, other rejected with 409 PENDING_REQUEST_EXISTS.
 *
 * Technical Requirements:
 * 1. Independent PostgreSQL connections checked out from HikariCP pool.
 * 2. Real temporal overlap synchronized via CyclicBarrier.
 * 3. Proves pessimistic write lock serialization (Order -> Case -> Round 2).
 * 4. Verifies database final state consistency with zero orphan or corrupt records.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("CaseEvidenceRequestConcurrencyPostgresTest - Real PostgreSQL Concurrency Tests (D-07 Option 1)")
class CaseEvidenceRequestConcurrencyPostgresTest {

    private static final Logger log = LoggerFactory.getLogger(CaseEvidenceRequestConcurrencyPostgresTest.class);

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
    private CaseEvidenceRequestService service;

    @Autowired
    private SellerResubmissionService resubmissionService;

    @Autowired
    private ReturnCaseService returnCaseService;

    @Autowired
    private CaseEvidenceRequestRepository requestRepository;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private CaseRoundRepository caseRoundRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ExecutorService executor;
    private Long ktvId;
    private Long sellerId;
    private Long buyerId;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        ktvId = createTestUser("ktv");
        sellerId = createTestUser("seller");
        buyerId = createTestUser("buyer");

        grantRole(ktvId, "KTV");
        grantRole(sellerId, "SELLER");
        grantRole(buyerId, "BUYER");
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private Long createTestUser(String prefix) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.users(email, password_hash, full_name, status) VALUES (?, 'fixture_pass', ?, 'ACTIVE') RETURNING user_id",
                Long.class,
                prefix + "_" + UUID.randomUUID() + "@example.test",
                "User " + prefix
        );
    }

    private void grantRole(Long userId, String roleName) {
        jdbcTemplate.update("""
                INSERT INTO og_compat.user_roles(user_id, role_id)
                SELECT ?, role_id FROM og_compat.roles WHERE role_name = ?
                ON CONFLICT DO NOTHING
                """, userId, roleName);
    }

    private static String sha256Hex(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record Uc83OrderFixture(Long orderId, java.util.UUID cashComponentId, java.util.UUID subsidyComponentId) {}

    private Uc83OrderFixture createCommittedUc83Order(Long fBuyerId, Long fSellerId) {
        grantRole(fSellerId, "BUYER");
        jdbcTemplate.update("UPDATE og_compat.users SET identity_workflow = 'UC83' WHERE user_id IN (?, ?)", fBuyerId, fSellerId);

        Long ktvUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM og_compat.user_roles ur JOIN og_compat.roles r USING(role_id) WHERE r.role_name = 'KTV' LIMIT 1",
                Long.class
        );
        Long categoryId = jdbcTemplate.queryForObject("SELECT min(category_id) FROM og_compat.categories", Long.class);

        // Seller eKYC
        Long ekycId = jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.ekyc_profiles(user_id, revision_no) VALUES (?, 1) RETURNING profile_id",
                Long.class, fSellerId
        );
        String hDigest = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update(
                "INSERT INTO og_compat.identity_document_registry(document_digest, digest_key_version, user_id, first_profile_id) VALUES (?, 'TEST-HMAC', ?, ?)",
                hDigest, fSellerId, ekycId
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
                Long.class, fSellerId
        );
        Long spId = jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.seller_profiles(user_id, state, ekyc_profile_id, pickup_address_id, bank_snapshot) VALUES (?, 'PENDING_REVIEW', ?, ?, '{\"bank_name\":\"Mock\",\"account_number\":\"FAKE\",\"account_holder\":\"Seller\"}'::jsonb) RETURNING seller_profile_id",
                Long.class, fSellerId, ekycId, addrId
        );
        jdbcTemplate.update("INSERT INTO og_compat.seller_profile_decisions(seller_profile_id, revision_no, reviewed_by, outcome, profile_snapshot, command_key) VALUES (?, 1, ?, 'APPROVED', '{}'::jsonb, ?)", spId, ktvUserId, "CMD_SPD_" + UUID.randomUUID());

        // Product & Listing
        BigDecimal cashAmount = new BigDecimal("250000.00");
        BigDecimal subtotal = cashAmount;

        Long pid = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.products(seller_id, category_id, title, description, listed_price, condition, workflow_model, quantity_total, return_allowed, delivery_options)
                VALUES (?, ?, 'Item', 'Desc', ?, 'GOOD', 'UC83', 10, true, '["SELF_PICKUP","CARRIER"]'::jsonb) RETURNING product_id
                """, Long.class, fSellerId, categoryId, subtotal);
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
                """, gid, fBuyerId, "CMD_CG_" + UUID.randomUUID(), cashAmount);

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
                """, Long.class, gid, gid, fBuyerId, fSellerId, subtotal, cashAmount, subtotal);

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
                """, Long.class, oid, pid, subtotal, subtotal, cashAmount, subtotal, rid);

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
                """, UUID.randomUUID(), shipId, fBuyerId, "CMD_HCB_" + UUID.randomUUID(),
                     UUID.randomUUID(), shipId, fSellerId, "CMD_HCS_" + UUID.randomUUID());

        jdbcTemplate.queryForObject("SELECT og_confirm_direct_handover(?)", Boolean.class, shipId);

        return new Uc83OrderFixture(oid, cashComponentId, null);
    }

    private record TestFixture(Long orderId, Long caseId, Long round2Id, Long sellerId) {}

    private TestFixture createEligibleCaseFixture(boolean isExpired) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx.execute(status -> {
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
            returnCaseService.confirmReturnDeliveryByCarrier(createdCase.caseId(), shipmentId, Instant.now());

            Long round2Id = jdbcTemplate.queryForObject(
                    "SELECT round_id FROM og_compat.case_rounds WHERE case_id = ? AND round_no = 2",
                    Long.class,
                    createdCase.caseId()
            );

            // Assign KTV to case
            jdbcTemplate.update("UPDATE og_compat.cases SET assigned_to = ? WHERE case_id = ?", ktvId, createdCase.caseId());

            // Set valid_damage_request_at for Round 2 (Batch 1 submitted)
            jdbcTemplate.update("UPDATE og_compat.case_rounds SET valid_damage_request_at = CURRENT_TIMESTAMP WHERE round_id = ?", round2Id);

            // If isExpired = true, make deadline expired respecting cases_check2 and og_validate_case_state
            if (isExpired) {
                Timestamp validReturned = Timestamp.from(Instant.now().minus(3, ChronoUnit.DAYS));
                Timestamp approvedAt = Timestamp.from(Instant.now().minus(4, ChronoUnit.DAYS));
                jdbcTemplate.update("UPDATE og_compat.shipments SET valid_delivery_at = ? WHERE shipment_id = ?", validReturned, shipmentId);
                jdbcTemplate.update("""
                        UPDATE og_compat.cases
                        SET approved_at = ?,
                            valid_returned_at = ?,
                            seller_response_due_at = (?::timestamptz + INTERVAL '2 days')
                        WHERE case_id = ?
                        """, approvedAt, validReturned, validReturned, createdCase.caseId());
            }

            return new TestFixture(fixture.orderId(), createdCase.caseId(), round2Id, fSellerId);
        });
    }

    // =========================================================================
    // SCENARIO A: CÙNG commandKey, CÙNG payload (D-07 Option 1)
    // =========================================================================

    @Test
    @DisplayName("Scenario A: Concurrent Same commandKey + Same payload -> Exactly 1 Created (201), other Replayed (200), same requestId")
    void testScenarioA_sameCommandKey_samePayload_concurrent() throws Exception {
        int iterations = 3;
        for (int iter = 1; iter <= iterations; iter++) {
            TestFixture fix = createEligibleCaseFixture(false);
            String sharedCmdKey = "CONC-SCENARIO-A-" + fix.caseId() + "-ITER-" + iter;
            ReturnCaseDtos.KtvRequestEvidenceRequest request = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                    "Ảnh chụp ở đợt 1 bị lóa và mờ, yêu cầu cung cấp ảnh rõ tem niêm phong",
                    List.of("Ảnh chụp cận cảnh tem", "Video góc rộng"),
                    sharedCmdKey
            );

            CyclicBarrier barrier = new CyclicBarrier(2);
            AtomicReference<CaseEvidenceRequestService.KtvRequestEvidenceResult> res1 = new AtomicReference<>();
            AtomicReference<CaseEvidenceRequestService.KtvRequestEvidenceResult> res2 = new AtomicReference<>();
            AtomicReference<Throwable> err1 = new AtomicReference<>();
            AtomicReference<Throwable> err2 = new AtomicReference<>();
            AtomicLong start1 = new AtomicLong();
            AtomicLong end1 = new AtomicLong();
            AtomicLong start2 = new AtomicLong();
            AtomicLong end2 = new AtomicLong();

            Future<?> f1 = executor.submit(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    start1.set(System.currentTimeMillis());
                    res1.set(service.requestEvidence(ktvId, fix.caseId(), request));
                } catch (Throwable t) {
                    err1.set(t);
                } finally {
                    end1.set(System.currentTimeMillis());
                }
            });

            Future<?> f2 = executor.submit(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    start2.set(System.currentTimeMillis());
                    res2.set(service.requestEvidence(ktvId, fix.caseId(), request));
                } catch (Throwable t) {
                    err2.set(t);
                } finally {
                    end2.set(System.currentTimeMillis());
                }
            });

            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);

            // Log execution metrics proving overlap and concurrency
            long overlapMs = Math.min(end1.get(), end2.get()) - Math.max(start1.get(), start2.get());
            log.info("Scenario A Iteration {} Metrics: Thread1 [{} -> {}, dur={}ms], Thread2 [{} -> {}, dur={}ms], Overlap={}ms",
                    iter, start1.get(), end1.get(), (end1.get() - start1.get()),
                    start2.get(), end2.get(), (end2.get() - start2.get()), overlapMs);

            // 1. Neither thread threw an unexpected error
            assertThat(err1.get()).as("Thread 1 should not throw in Scenario A").isNull();
            assertThat(err2.get()).as("Thread 2 should not throw in Scenario A").isNull();

            CaseEvidenceRequestService.KtvRequestEvidenceResult r1 = res1.get();
            CaseEvidenceRequestService.KtvRequestEvidenceResult r2 = res2.get();
            assertThat(r1).isNotNull();
            assertThat(r2).isNotNull();

            // 2. Exactly one thread created (201) and the other replayed (200)
            boolean exactlyOneCreated = (r1.isCreated() && !r2.isCreated()) || (!r1.isCreated() && r2.isCreated());
            assertThat(exactlyOneCreated)
                    .as("Exactly one transaction must be CREATED (201) and the other REPLAYED (200)")
                    .isTrue();

            // 3. Both results return the identical requestId and status PENDING
            assertThat(r1.response().requestId()).isEqualTo(r2.response().requestId());
            assertThat(r1.response().status()).isEqualTo("PENDING");
            assertThat(r2.response().status()).isEqualTo("PENDING");
            assertThat(r1.response().commandKey()).isEqualTo(sharedCmdKey);
            assertThat(r2.response().commandKey()).isEqualTo(sharedCmdKey);

            // 4. Database verification: Exactly 1 record in public.case_evidence_requests
            Integer countInDb = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM public.case_evidence_requests WHERE case_id = ?",
                    Integer.class,
                    fix.caseId()
            );
            assertThat(countInDb).as("Database must contain exactly 1 request record for the Case").isEqualTo(1);

            CaseEvidenceRequestEntity dbEntity = requestRepository.findByCommandKey(sharedCmdKey).orElseThrow();
            assertThat(dbEntity.getStatus()).isEqualTo("PENDING");
            assertThat(dbEntity.getCaseId()).isEqualTo(fix.caseId());
            assertThat(dbEntity.getId()).isEqualTo(r1.response().requestId());
        }
    }

    // =========================================================================
    // SCENARIO B: CÙNG commandKey, KHÁC payload (D-07 Option 1)
    // =========================================================================

    @Test
    @DisplayName("Scenario B: Concurrent Same commandKey + Different payload -> Exactly 1 Created, other 409 COMMAND_KEY_CONFLICT")
    void testScenarioB_sameCommandKey_differentPayload_concurrent() throws Exception {
        int iterations = 3;
        for (int iter = 1; iter <= iterations; iter++) {
            TestFixture fix = createEligibleCaseFixture(false);
            String sharedCmdKey = "CONC-SCENARIO-B-" + fix.caseId() + "-ITER-" + iter;

            ReturnCaseDtos.KtvRequestEvidenceRequest requestA = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                    "Payload A: Yêu cầu ảnh chụp góc nghiêng chi tiết tem niêm phong",
                    List.of("Ảnh góc nghiêng"),
                    sharedCmdKey
            );
            ReturnCaseDtos.KtvRequestEvidenceRequest requestB = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                    "Payload B: Yêu cầu video toàn cảnh quá trình kiểm tra ngoại quan",
                    List.of("Video toàn cảnh"),
                    sharedCmdKey
            );

            CyclicBarrier barrier = new CyclicBarrier(2);
            AtomicReference<CaseEvidenceRequestService.KtvRequestEvidenceResult> res1 = new AtomicReference<>();
            AtomicReference<CaseEvidenceRequestService.KtvRequestEvidenceResult> res2 = new AtomicReference<>();
            AtomicReference<Throwable> err1 = new AtomicReference<>();
            AtomicReference<Throwable> err2 = new AtomicReference<>();
            AtomicLong start1 = new AtomicLong();
            AtomicLong end1 = new AtomicLong();
            AtomicLong start2 = new AtomicLong();
            AtomicLong end2 = new AtomicLong();

            Future<?> f1 = executor.submit(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    start1.set(System.currentTimeMillis());
                    res1.set(service.requestEvidence(ktvId, fix.caseId(), requestA));
                } catch (Throwable t) {
                    err1.set(t);
                } finally {
                    end1.set(System.currentTimeMillis());
                }
            });

            Future<?> f2 = executor.submit(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    start2.set(System.currentTimeMillis());
                    res2.set(service.requestEvidence(ktvId, fix.caseId(), requestB));
                } catch (Throwable t) {
                    err2.set(t);
                } finally {
                    end2.set(System.currentTimeMillis());
                }
            });

            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);

            long overlapMs = Math.min(end1.get(), end2.get()) - Math.max(start1.get(), start2.get());
            log.info("Scenario B Iteration {} Metrics: Thread1 [{} -> {}, dur={}ms], Thread2 [{} -> {}, dur={}ms], Overlap={}ms",
                    iter, start1.get(), end1.get(), (end1.get() - start1.get()),
                    start2.get(), end2.get(), (end2.get() - start2.get()), overlapMs);

            // Exactly one thread succeeds with isCreated == true, the other thread throws 409 COMMAND_KEY_CONFLICT
            boolean t1Won = (res1.get() != null && res1.get().isCreated() && err2.get() instanceof TrustSafetyBusinessException);
            boolean t2Won = (res2.get() != null && res2.get().isCreated() && err1.get() instanceof TrustSafetyBusinessException);

            assertThat(t1Won ^ t2Won)
                    .as("Exactly one thread must win with 201 Created, and the other must fail with conflict")
                    .isTrue();

            Throwable losingError = t1Won ? err2.get() : err1.get();
            assertThat(losingError).isInstanceOf(TrustSafetyBusinessException.class);
            TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) losingError;
            assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(tsEx.getCode()).isEqualTo("COMMAND_KEY_CONFLICT");

            // Database verification: Exactly 1 record created with sharedCmdKey
            Integer countInDb = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM public.case_evidence_requests WHERE case_id = ?",
                    Integer.class,
                    fix.caseId()
            );
            assertThat(countInDb).as("Database must contain exactly 1 record").isEqualTo(1);

            CaseEvidenceRequestEntity dbEntity = requestRepository.findByCommandKey(sharedCmdKey).orElseThrow();
            assertThat(dbEntity.getStatus()).isEqualTo("PENDING");
            assertThat(dbEntity.getCaseId()).isEqualTo(fix.caseId());
            // Verify payload matches the winning thread's payload
            if (t1Won) {
                assertThat(dbEntity.getReason()).isEqualTo(requestA.reason());
            } else {
                assertThat(dbEntity.getReason()).isEqualTo(requestB.reason());
            }
        }
    }

    // =========================================================================
    // SCENARIO C: KHÁC commandKey, CÙNG Case (D-07 Option 1 / BD5-08)
    // =========================================================================

    @Test
    @DisplayName("Scenario C: Concurrent Different commandKey + Same Case -> Exactly 1 PENDING Created, other 409 PENDING_REQUEST_EXISTS")
    void testScenarioC_differentCommandKey_sameCase_concurrent() throws Exception {
        int iterations = 3;
        for (int iter = 1; iter <= iterations; iter++) {
            TestFixture fix = createEligibleCaseFixture(false);
            String cmdKey1 = "CONC-SCENARIO-C-KEY1-" + fix.caseId() + "-ITER-" + iter;
            String cmdKey2 = "CONC-SCENARIO-C-KEY2-" + fix.caseId() + "-ITER-" + iter;

            ReturnCaseDtos.KtvRequestEvidenceRequest request1 = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                    "Yêu cầu từ KTV 1: Hình ảnh chi tiết linh kiện bị tháo rời",
                    List.of("Ảnh linh kiện"),
                    cmdKey1
            );
            ReturnCaseDtos.KtvRequestEvidenceRequest request2 = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                    "Yêu cầu từ KTV 2: Video toàn cảnh bo mạch chính",
                    List.of("Video bo mạch"),
                    cmdKey2
            );

            CyclicBarrier barrier = new CyclicBarrier(2);
            AtomicReference<CaseEvidenceRequestService.KtvRequestEvidenceResult> res1 = new AtomicReference<>();
            AtomicReference<CaseEvidenceRequestService.KtvRequestEvidenceResult> res2 = new AtomicReference<>();
            AtomicReference<Throwable> err1 = new AtomicReference<>();
            AtomicReference<Throwable> err2 = new AtomicReference<>();
            AtomicLong start1 = new AtomicLong();
            AtomicLong end1 = new AtomicLong();
            AtomicLong start2 = new AtomicLong();
            AtomicLong end2 = new AtomicLong();

            Future<?> f1 = executor.submit(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    start1.set(System.currentTimeMillis());
                    res1.set(service.requestEvidence(ktvId, fix.caseId(), request1));
                } catch (Throwable t) {
                    err1.set(t);
                } finally {
                    end1.set(System.currentTimeMillis());
                }
            });

            Future<?> f2 = executor.submit(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    start2.set(System.currentTimeMillis());
                    res2.set(service.requestEvidence(ktvId, fix.caseId(), request2));
                } catch (Throwable t) {
                    err2.set(t);
                } finally {
                    end2.set(System.currentTimeMillis());
                }
            });

            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);

            long overlapMs = Math.min(end1.get(), end2.get()) - Math.max(start1.get(), start2.get());
            log.info("Scenario C Iteration {} Metrics: Thread1 [{} -> {}, dur={}ms], Thread2 [{} -> {}, dur={}ms], Overlap={}ms",
                    iter, start1.get(), end1.get(), (end1.get() - start1.get()),
                    start2.get(), end2.get(), (end2.get() - start2.get()), overlapMs);

            // Exactly one thread succeeds with isCreated == true, the other throws 409 PENDING_REQUEST_EXISTS
            boolean t1Won = (res1.get() != null && res1.get().isCreated() && err2.get() instanceof TrustSafetyBusinessException);
            boolean t2Won = (res2.get() != null && res2.get().isCreated() && err1.get() instanceof TrustSafetyBusinessException);

            assertThat(t1Won ^ t2Won)
                    .as("Exactly one thread must succeed, and the other must fail with PENDING_REQUEST_EXISTS")
                    .isTrue();

            Throwable losingError = t1Won ? err2.get() : err1.get();
            assertThat(losingError).isInstanceOf(TrustSafetyBusinessException.class);
            TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) losingError;
            assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(tsEx.getCode()).isEqualTo("PENDING_REQUEST_EXISTS");

            // Database verification: Exactly 1 PENDING record exists for this Case
            Integer pendingCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM public.case_evidence_requests WHERE case_id = ? AND status = 'PENDING'",
                    Integer.class,
                    fix.caseId()
            );
            assertThat(pendingCount).as("Partial unique index / guard must guarantee exactly 1 PENDING record").isEqualTo(1);

            // Total records for this Case must be 1 (the loser was rejected and did not write to DB)
            Integer totalCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM public.case_evidence_requests WHERE case_id = ?",
                    Integer.class,
                    fix.caseId()
            );
            assertThat(totalCount).as("Losing transaction must not leave any orphan record").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Scenario D: Concurrency Resubmission - Hai luồng nộp cùng commandKey và payload đồng thời: 1 thực thi, 1 replay hợp lệ")
    void scenarioD_concurrentResubmission_sameKey_samePayload_exactlyOneFulfillment() throws Exception {
        TestFixture fix = createEligibleCaseFixture(false);
        String reqCmdKey = "REQ-CONCUR-" + UUID.randomUUID();

        // 1. Issue KTV request
        ReturnCaseDtos.KtvRequestEvidenceRequest issueReq = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Yêu cầu bổ sung video chi tiết",
                List.of("Video mở gói"),
                reqCmdKey
        );
        CaseEvidenceRequestService.KtvRequestEvidenceResult issueRes = service.requestEvidence(ktvId, fix.caseId(), issueReq);
        UUID requestId = issueRes.response().requestId();

        // 2. Prepare identical resubmission request
        String resubCmdKey = "RESUB-CONCUR-" + UUID.randomUUID();
        byte[] concurImgContent = "RAW_JPEG_IMAGE_CONTENT_SAMPLE_FOR_CONCURRENT_SUBMISSION_ITEM_A".getBytes(StandardCharsets.UTF_8);
        byte[] concurVidContent = "RAW_MP4_VIDEO_STREAM_BYTES_SAMPLE_FOR_CONCURRENT_SUBMISSION_ITEM_B".getBytes(StandardCharsets.UTF_8);
        String concurImgDigest = sha256Hex(concurImgContent);
        String concurVidDigest = sha256Hex(concurVidContent);
        assertThat(concurImgDigest).matches("^[0-9a-f]{64}$");
        assertThat(concurVidDigest).matches("^[0-9a-f]{64}$");
        assertThat(concurImgDigest).isNotEqualTo(concurVidDigest);

        ReturnCaseDtos.SellerDamageExemptionRequest resubReq = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Đã bổ sung video theo yêu cầu",
                List.of(
                        new ReturnCaseDtos.EvidenceItemDto("IMAGE", "evidence/img_concur.jpg", "Ảnh tem", concurImgDigest, 1024L, null),
                        new ReturnCaseDtos.EvidenceItemDto("VIDEO", "evidence/vid_concur.mp4", "Video mở kiện", concurVidDigest, 2048000L, 20)
                ),
                resubCmdKey,
                requestId
        );

        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicReference<CaseEntity> res1 = new AtomicReference<>();
        AtomicReference<CaseEntity> res2 = new AtomicReference<>();
        AtomicReference<Throwable> err1 = new AtomicReference<>();
        AtomicReference<Throwable> err2 = new AtomicReference<>();

        Future<?> f1 = executor.submit(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                res1.set(resubmissionService.submitDamageExemptionResubmission(fix.sellerId(), fix.caseId(), resubReq));
            } catch (Throwable t) {
                err1.set(t);
            }
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                res2.set(resubmissionService.submitDamageExemptionResubmission(fix.sellerId(), fix.caseId(), resubReq));
            } catch (Throwable t) {
                err2.set(t);
            }
        });

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);

        // Both threads must succeed without error (one fulfilled, one replayed)
        assertThat(err1.get()).as("Thread 1 should not fail").isNull();
        assertThat(err2.get()).as("Thread 2 should not fail").isNull();
        assertThat(res1.get()).isNotNull();
        assertThat(res2.get()).isNotNull();

        // Verify PostgreSQL final state: Request status is FULFILLED with commandKey
        Map<String, Object> reqRow = jdbcTemplate.queryForMap(
                "SELECT status, fulfillment_command_key FROM public.case_evidence_requests WHERE request_id = ?",
                requestId
        );
        assertThat(reqRow.get("status")).isEqualTo("FULFILLED");
        assertThat(reqRow.get("fulfillment_command_key")).isEqualTo(resubCmdKey);

        // Verify exactly 2 evidence rows inserted for submission_no = 2 (no duplicate insertion from replay)
        Integer evidenceCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.case_evidence WHERE round_id = ? AND submission_no = 2",
                Integer.class,
                fix.round2Id()
        );
        assertThat(evidenceCount).as("Must contain exactly 2 evidence items, no duplicate creation from second thread").isEqualTo(2);

        // Verify exactly 1 SUPPLEMENTED event created
        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.case_events WHERE round_id = ? AND command_key = ?",
                Integer.class,
                fix.round2Id(), resubCmdKey
        );
        assertThat(eventCount).as("Must contain exactly 1 event").isEqualTo(1);
    }

    // ==================== SCENARIO E: CONCURRENT DECISION & RESUBMISSION (D-08) ====================

    @Test
    @DisplayName("Scenario E: Concurrency giữa KTV Final Decision (D-08) và Seller Resubmission (BD-5) -> Nhất quán, không mất dữ liệu, không deadlock")
    void scenarioE_concurrentDecisionAndResubmission_lockHierarchyMaintained_noDeadlock() throws Exception {
        TestFixture fix = createEligibleCaseFixture(false);

        // 1. KTV phát hành Request PENDING
        String reqCmdKey = "CMD_REQ_SCENARIO_E_" + UUID.randomUUID();
        ReturnCaseDtos.KtvRequestEvidenceRequest reqDto = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Yêu cầu bổ sung video chi tiết",
                List.of("Video mở gói chi tiết"),
                reqCmdKey
        );
        CaseEvidenceRequestService.KtvRequestEvidenceResult reqResp = service.requestEvidence(ktvId, fix.caseId(), reqDto);
        UUID requestId = reqResp.response().requestId();

        // 2. Chuẩn bị request cho cả 2 thread
        String decCmdKey = "CMD_DEC_SCENARIO_E_" + UUID.randomUUID();
        ReturnCaseDtos.KtvRound2DecisionRequest decReq = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "KTV chốt hoàn tiền",
                decCmdKey
        );

        byte[] imgContent = "image test content for scenario e concurrency".getBytes(StandardCharsets.UTF_8);
        String imgDigest = sha256Hex(imgContent);
        byte[] vidContent = "video test content for scenario e concurrency".getBytes(StandardCharsets.UTF_8);
        String vidDigest = sha256Hex(vidContent);
        String resubCmdKey = "CMD_RESUB_SCENARIO_E_" + UUID.randomUUID();
        ReturnCaseDtos.SellerDamageExemptionRequest resubReq = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Seller giải trình và cung cấp ảnh và video",
                List.of(
                        new ReturnCaseDtos.EvidenceItemDto("IMAGE", "evidence/box_front.jpg", "Ảnh kiện hàng", imgDigest, 2048L, null),
                        new ReturnCaseDtos.EvidenceItemDto("VIDEO", "evidence/box_open.mp4", "Video mở gói", vidDigest, 10485760L, 15)
                ),
                resubCmdKey,
                requestId
        );

        // 3. Concurrency barrier
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicReference<Object> decisionResult = new AtomicReference<>();
        AtomicReference<Object> resubResult = new AtomicReference<>();
        AtomicReference<Throwable> decisionErr = new AtomicReference<>();
        AtomicReference<Throwable> resubErr = new AtomicReference<>();

        Future<?> fDecision = executor.submit(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                decisionResult.set(returnCaseService.ktvDecideRound2(ktvId, fix.caseId(), decReq));
            } catch (Throwable t) {
                decisionErr.set(t);
            }
        });

        Future<?> fResub = executor.submit(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                resubResult.set(resubmissionService.submitDamageExemptionResubmission(fix.sellerId(), fix.caseId(), resubReq));
            } catch (Throwable t) {
                resubErr.set(t);
            }
        });

        fDecision.get(15, TimeUnit.SECONDS);
        fResub.get(15, TimeUnit.SECONDS);

        // 4. Assert: Không bị deadlock (40P01 / 55P03) và hành vi đúng theo PO-CHOICE-3
        assertThat(resubErr.get()).as("Seller resubmission must succeed cleanly without deadlock").isNull();
        if (decisionErr.get() != null) {
            assertThat(decisionErr.get().getMessage()).doesNotContain("deadlock").doesNotContain("lock timeout");
            assertThat(decisionErr.get()).isInstanceOf(TrustSafetyBusinessException.class);
            assertThat(((TrustSafetyBusinessException) decisionErr.get()).getCode()).isEqualTo("EVIDENCE_REQUEST_IN_PROGRESS");
        }

        // 5. Kiểm tra tính toàn vẹn trạng thái trong PostgreSQL:
        // Dù KTV chạy trước (bị PO-CHOICE-3 chặn 409) hay Seller chạy trước (FULFILLED rồi KTV quyết định), request đều FULFILLED và evidence submission 2 được lưu
        String finalStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM og_compat.case_evidence_requests WHERE request_id = ?",
                String.class, requestId);
        assertThat(finalStatus).isEqualTo("FULFILLED");

        // Case phải có quyết định nếu decision thành công
        if (decisionResult.get() != null) {
            Integer decisionCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM og_compat.case_decisions WHERE round_id = ?",
                    Integer.class, fix.round2Id());
            assertThat(decisionCount).isGreaterThanOrEqualTo(1);
        }

        // Evidence resubmission luôn thành công đầy đủ 2 items (1 IMAGE + 1 VIDEO)
        assertThat(resubResult.get()).isNotNull();
        Integer evCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM og_compat.case_evidence WHERE round_id = ? AND submission_no = 2",
                Integer.class, fix.round2Id());
        assertThat(evCount).isEqualTo(2);
    }
}

