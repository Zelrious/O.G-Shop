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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real PostgreSQL Integration Test for API 1 (BD-5 Phase 1).
 *
 * Verifies on real PostgreSQL 17+:
 * 1. Flyway migration V38 successfully executed.
 * 2. public.case_evidence_requests table schema, UUID/BIGINT types, JSONB, and TIMESTAMPTZ.
 * 3. Foreign key constraints to cases, case_actions (case_rounds identity), and users.
 * 4. Unique constraints on command_key and partial unique index on case_id WHERE status = 'PENDING'.
 * 5. og_compat.case_evidence_requests compatibility view.
 * 6. Transaction rollback cleanliness.
 * 7. Canonical payload hash idempotency replay and conflict detection.
 * 8. D-03 Option 3 general deadline check under real pessimistic locks.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
@DisplayName("CaseEvidenceRequestPostgresTest - Real PostgreSQL Integration Test for API 1 (BD-5)")
class CaseEvidenceRequestPostgresTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String url = PostgresTestSafetyGuard.resolveAndAssertSafeFromEnv("OGSHOP_TEST_DB_URL");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv("OGSHOP_TEST_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("OGSHOP_TEST_DB_PASSWORD"));
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO public,og_compat");
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

    private Long ktvId;
    private Long sellerId;
    private Long buyerId;

    @BeforeEach
    void setUpUsers() {
        ktvId = createTestUser("ktv");
        sellerId = createTestUser("seller");
        buyerId = createTestUser("buyer");

        grantRole(ktvId, "KTV");
        grantRole(sellerId, "SELLER");
        grantRole(buyerId, "BUYER");
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

    private record Uc83OrderFixture(Long orderId, UUID cashComponentId, UUID subsidyComponentId) {}

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
        jdbcTemplate.update("""
                UPDATE public.shipments
                SET valid_delivery_at = (SELECT valid_delivered_at FROM og_compat.orders WHERE order_id = ?),
                    status = 'DELIVERED',
                    delivered_at = (SELECT valid_delivered_at FROM og_compat.orders WHERE order_id = ?),
                    shipped_at = COALESCE(shipped_at, initiated_at)
                WHERE shipment_id = ?
                """, oid, oid, shipId);

        return new Uc83OrderFixture(oid, cashComponentId, null);
    }

    private record TestFixture(Long orderId, Long caseId, Long round2Id, Long sellerId) {}

    private TestFixture createEligibleCaseFixture(boolean isExpired) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        record Stage1(Long buyerId, Long sellerId, Uc83OrderFixture fixture) {}
        Stage1 s1 = tx.execute(status -> {
            Long fBuyerId = createTestUser("buyer");
            Long fSellerId = createTestUser("seller");
            grantRole(fBuyerId, "BUYER");
            grantRole(fSellerId, "SELLER");
            Uc83OrderFixture fixture = createCommittedUc83Order(fBuyerId, fSellerId);
            return new Stage1(fBuyerId, fSellerId, fixture);
        });

        record Stage2(Long caseId) {}
        Stage2 s2 = tx.execute(status -> {
            ReturnCaseDtos.CreateReturnRequest createReq = new ReturnCaseDtos.CreateReturnRequest(
                    ReturnReasonCode.DAMAGED_OR_DEFECTIVE,
                    "Lỗi phần cứng",
                    List.of(),
                    "CMD_CREATE_" + UUID.randomUUID()
            );
            ReturnCaseDtos.ReturnCaseDetailResponse createdCase = returnCaseService.createReturnRequest(s1.buyerId(), s1.fixture().orderId(), createReq);
            returnCaseService.sellerRespondRound1(s1.sellerId(), createdCase.caseId(), new ReturnCaseDtos.SellerRound1ResponseRequest(
                    CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                    "Đồng ý trả",
                    "CMD_SELLER_" + UUID.randomUUID()
            ));
            return new Stage2(createdCase.caseId());
        });

        record Stage3(Long shipmentId) {}
        Stage3 s3 = tx.execute(status -> {
            ReturnCaseDtos.SubmitReturnShipmentRequest shipReq = new ReturnCaseDtos.SubmitReturnShipmentRequest(
                    "CARRIER",
                    "GHN",
                    "GHN-R2-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                    "CMD_SHIP_" + UUID.randomUUID()
            );
            ReturnCaseDtos.ReturnCaseDetailResponse submittedCase = returnCaseService.submitReturnShipment(s1.buyerId(), s2.caseId(), shipReq);
            return new Stage3(submittedCase.returnShipment().shipmentId());
        });

        tx.executeWithoutResult(status -> {
            returnCaseService.confirmReturnDeliveryByCarrier(s2.caseId(), s3.shipmentId(), Instant.now());
        });

        return tx.execute(status -> {
            Long round2Id = jdbcTemplate.queryForObject(
                    "SELECT round_id FROM og_compat.case_rounds WHERE case_id = ? AND round_no = 2",
                    Long.class,
                    s2.caseId()
            );

            // Assign KTV to case
            jdbcTemplate.update("UPDATE og_compat.cases SET assigned_to = ? WHERE case_id = ?", ktvId, s2.caseId());

            // Set valid_damage_request_at for Round 2 (Batch 1 submitted)
            jdbcTemplate.update("UPDATE og_compat.case_rounds SET valid_damage_request_at = CURRENT_TIMESTAMP WHERE round_id = ?", round2Id);

            // If isExpired = true, make deadline expired respecting cases_check2 and og_validate_case_state
            if (isExpired) {
                Timestamp validReturned = Timestamp.from(Instant.now().minus(3, ChronoUnit.DAYS));
                Timestamp approvedAt = Timestamp.from(Instant.now().minus(4, ChronoUnit.DAYS));
                jdbcTemplate.update("UPDATE public.shipments SET valid_delivery_at = ? WHERE shipment_id = ?", validReturned, s3.shipmentId());
                jdbcTemplate.update("""
                        UPDATE og_compat.cases
                        SET approved_at = ?,
                            valid_returned_at = ?,
                            seller_response_due_at = (?::timestamptz + INTERVAL '2 days')
                        WHERE case_id = ?
                        """, approvedAt, validReturned, validReturned, s2.caseId());
            }

            return new TestFixture(s1.fixture().orderId(), s2.caseId(), round2Id, s1.sellerId());
        });
    }

    // ==================== 1. FLYWAY MIGRATION & SCHEMA VERIFICATION ====================

    @Test
    @DisplayName("PostgreSQL: Flyway migration V42, V46 và V47 đã thực thi thành công")
    void testFlywayMigration_v42_appliedSuccessfully() {
        Map<String, Object> v42Record = jdbcTemplate.queryForMap(
                "SELECT installed_rank, version, description, success FROM public.flyway_schema_history WHERE version = '42'"
        );
        assertThat(v42Record).isNotNull();
        assertThat(v42Record.get("version")).isEqualTo("42");
        assertThat(v42Record.get("success")).isEqualTo(true);
        assertThat(v42Record.get("description").toString()).containsIgnoringCase("bd5");

        Map<String, Object> v46Record = jdbcTemplate.queryForMap(
                "SELECT installed_rank, version, description, success FROM public.flyway_schema_history WHERE version = '46'"
        );
        assertThat(v46Record).isNotNull();
        assertThat(v46Record.get("version")).isEqualTo("46");
        assertThat(v46Record.get("success")).isEqualTo(true);
        assertThat(v46Record.get("description").toString()).containsIgnoringCase("hardening");

        Map<String, Object> v47Record = jdbcTemplate.queryForMap(
                "SELECT installed_rank, version, description, success FROM public.flyway_schema_history WHERE version = '47'"
        );
        assertThat(v47Record).isNotNull();
        assertThat(v47Record.get("version")).isEqualTo("47");
        assertThat(v47Record.get("success")).isEqualTo(true);
        assertThat(v47Record.get("description").toString()).containsIgnoringCase("request id");
    }

    @Test
    @DisplayName("PostgreSQL: Kiểm tra cấu trúc bảng public.case_evidence_requests và view og_compat")
    void testSchemaAndTableDefinition() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList("""
                SELECT column_name, data_type, is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'case_evidence_requests'
                ORDER BY ordinal_position
                """);

        assertThat(columns).isNotEmpty();
        Map<String, String> colTypes = columns.stream()
                .collect(java.util.stream.Collectors.toMap(
                        m -> (String) m.get("column_name"),
                        m -> (String) m.get("data_type")
                ));

        assertThat(colTypes.get("request_id")).isEqualTo("uuid");
        assertThat(colTypes.get("case_id")).isEqualTo("bigint");
        assertThat(colTypes.get("round_id")).isEqualTo("bigint");
        assertThat(colTypes.get("ktv_id")).isEqualTo("bigint");
        assertThat(colTypes.get("status")).isEqualTo("character varying");
        assertThat(colTypes.get("reason")).isEqualTo("text");
        assertThat(colTypes.get("requested_items")).isEqualTo("jsonb");
        assertThat(colTypes.get("issued_at")).isEqualTo("timestamp with time zone");
        assertThat(colTypes.get("deadline_at")).isEqualTo("timestamp with time zone");
        assertThat(colTypes.get("command_key")).isEqualTo("character varying");
        assertThat(colTypes.get("payload_hash")).isEqualTo("character");

        // Verify og_compat view exists
        Integer viewCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.views WHERE table_schema = 'og_compat' AND table_name = 'case_evidence_requests'",
                Integer.class
        );
        assertThat(viewCount).isEqualTo(1);
    }

    @Test
    @DisplayName("PostgreSQL: Kiểm tra Foreign Keys và ràng buộc toàn vẹn")
    void testForeignKeys() {
        List<Map<String, Object>> fks = jdbcTemplate.queryForList("""
                SELECT
                    con.conname AS constraint_name,
                    con.confdeltype::text AS confdeltype,
                    string_agg(src_att.attname, ',' ORDER BY src_k.src_ord) AS source_columns,
                    tgt_ns.nspname AS foreign_table_schema,
                    tgt_tbl.relname AS foreign_table_name,
                    string_agg(tgt_att.attname, ',' ORDER BY tgt_k.tgt_ord) AS foreign_columns
                FROM pg_constraint con
                JOIN pg_class src_tbl ON src_tbl.oid = con.conrelid
                JOIN pg_namespace src_ns ON src_ns.oid = src_tbl.relnamespace
                JOIN pg_class tgt_tbl ON tgt_tbl.oid = con.confrelid
                JOIN pg_namespace tgt_ns ON tgt_ns.oid = tgt_tbl.relnamespace
                JOIN LATERAL unnest(con.conkey) WITH ORDINALITY AS src_k(attnum, src_ord) ON TRUE
                JOIN LATERAL unnest(con.confkey) WITH ORDINALITY AS tgt_k(attnum, tgt_ord) ON tgt_k.tgt_ord = src_k.src_ord
                JOIN pg_attribute src_att ON src_att.attrelid = con.conrelid AND src_att.attnum = src_k.attnum
                JOIN pg_attribute tgt_att ON tgt_att.attrelid = con.confrelid AND tgt_att.attnum = tgt_k.attnum
                WHERE con.contype = 'f'
                    AND src_ns.nspname = 'public'
                    AND src_tbl.relname = 'case_evidence_requests'
                GROUP BY con.oid, con.conname, con.confdeltype, tgt_ns.nspname, tgt_tbl.relname
                ORDER BY con.conname
                """);

        assertThat(fks).hasSize(3);

        Map<String, Map<String, Object>> fkBySourceColumns = fks.stream()
                .collect(java.util.stream.Collectors.toMap(
                        m -> (String) m.get("source_columns"),
                        java.util.function.Function.identity()
                ));

        assertThat(fkBySourceColumns).containsOnlyKeys("case_id", "ktv_id", "round_id");

        Map<String, Object> caseFk = fkBySourceColumns.get("case_id");
        assertThat(caseFk.get("foreign_table_schema")).isEqualTo("public");
        assertThat(caseFk.get("foreign_table_name")).isEqualTo("cases");
        assertThat(caseFk.get("foreign_columns")).isEqualTo("case_id");
        assertThat(caseFk.get("confdeltype")).isEqualTo("r");

        Map<String, Object> ktvFk = fkBySourceColumns.get("ktv_id");
        assertThat(ktvFk.get("foreign_table_schema")).isEqualTo("public");
        assertThat(ktvFk.get("foreign_table_name")).isEqualTo("users");
        assertThat(ktvFk.get("foreign_columns")).isEqualTo("user_id");
        assertThat(ktvFk.get("confdeltype")).isEqualTo("r");

        Map<String, Object> roundFk = fkBySourceColumns.get("round_id");
        assertThat(roundFk.get("foreign_table_schema")).isEqualTo("public");
        assertThat(roundFk.get("foreign_table_name")).isEqualTo("case_actions");
        assertThat(roundFk.get("foreign_columns")).isEqualTo("source_case_rounds_round_id");
        assertThat(roundFk.get("confdeltype")).isEqualTo("r");
    }

    // ==================== 2. CONSTRAINT & CONCURRENCY TESTS ====================

    @Test
    @DisplayName("PostgreSQL: Ràng buộc Unique CommandKey bảo vệ chống trùng lặp tại tầng DB")
    void testCommandKeyUniqueConstraint() {
        TestFixture fix1 = createEligibleCaseFixture(false);
        TestFixture fix2 = createEligibleCaseFixture(false);
        String cmdKey = "TEST-CMD-" + UUID.randomUUID();

        // 1. Insert direct valid request on case 1
        jdbcTemplate.update("""
                INSERT INTO public.case_evidence_requests(
                    case_id, round_id, ktv_id, status, reason, deadline_at, command_key, payload_hash
                ) VALUES (
                    ?, ?, ?, 'PENDING', 'Reason 1', CURRENT_TIMESTAMP + INTERVAL '48 hours', ?, repeat('a', 64)
                )
                """, fix1.caseId(), fix1.round2Id(), ktvId, cmdKey);

        // 2. Insert second request on case 2 with same command_key -> Expect uq_case_evidence_requests_cmd_key violation
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO public.case_evidence_requests(
                    case_id, round_id, ktv_id, status, reason, deadline_at, command_key, payload_hash
                ) VALUES (
                    ?, ?, ?, 'PENDING', 'Reason 2', CURRENT_TIMESTAMP + INTERVAL '48 hours', ?, repeat('b', 64)
                )
                """, fix2.caseId(), fix2.round2Id(), ktvId, cmdKey))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_case_evidence_requests_cmd_key");
    }

    @Test
    @DisplayName("PostgreSQL: Partial Unique Index cấm 2 PENDING requests trên cùng một Case")
    void testSinglePendingRequestPerCase_partialUniqueIndex() {
        TestFixture fix = createEligibleCaseFixture(false);

        // 1. Insert 1st PENDING request
        jdbcTemplate.update("""
                INSERT INTO public.case_evidence_requests(
                    case_id, round_id, ktv_id, status, reason, deadline_at, command_key, payload_hash
                ) VALUES (
                    ?, ?, ?, 'PENDING', 'Reason 1', CURRENT_TIMESTAMP + INTERVAL '48 hours', ?, repeat('a', 64)
                )
                """, fix.caseId(), fix.round2Id(), ktvId, "CMD_PENDING_1_" + UUID.randomUUID());

        // 2. Insert 2nd PENDING request for same case_id -> Expect partial unique index violation
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO public.case_evidence_requests(
                    case_id, round_id, ktv_id, status, reason, deadline_at, command_key, payload_hash
                ) VALUES (
                    ?, ?, ?, 'PENDING', 'Reason 2', CURRENT_TIMESTAMP + INTERVAL '48 hours', ?, repeat('b', 64)
                )
                """, fix.caseId(), fix.round2Id(), ktvId, "CMD_PENDING_2_" + UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("idx_case_evidence_requests_pending_unique");
    }

    @Test
    @DisplayName("PostgreSQL: Transaction rollback bảo đảm sạch sẽ, không để lại bản ghi request rác")
    void testTransactionRollback_leavesNoOrphanRecord() {
        TestFixture fix = createEligibleCaseFixture(false);
        String cmdKey = "TEST-ROLLBACK-" + UUID.randomUUID();

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> txTemplate.execute(status -> {
            jdbcTemplate.update("""
                    INSERT INTO public.case_evidence_requests(
                        case_id, round_id, ktv_id, status, reason, deadline_at, command_key, payload_hash
                    ) VALUES (
                        ?, ?, ?, 'PENDING', 'Rollback test', CURRENT_TIMESTAMP + INTERVAL '48 hours', ?, repeat('f', 64)
                    )
                    """, fix.caseId(), fix.round2Id(), ktvId, cmdKey);

            throw new RuntimeException("Simulated failure triggering rollback");
        })).isInstanceOf(RuntimeException.class).hasMessageContaining("Simulated failure");

        // Verify no record remains
        Optional<CaseEvidenceRequestEntity> req = requestRepository.findByCommandKey(cmdKey);
        assertThat(req).isEmpty();
    }

    // ==================== 3. END-TO-END SERVICE & LOCKING INTEGRATION ====================

    @Test
    @DisplayName("PostgreSQL: requestEvidence thực thi trọn vẹn: 201 Created -> 200 Replay -> 409 Conflict")
    void testRequestEvidence_endToEndUnderPostgres() {
        TestFixture fix = createEligibleCaseFixture(false);
        String cmdKey = "REQ-EVD-" + fix.caseId() + "-R2-001";

        ReturnCaseDtos.KtvRequestEvidenceRequest request1 = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Ảnh cung cấp ở lần 1 bị nhòe và thiếu góc chụp tổng thể",
                List.of("Ảnh chụp góc rộng", "Video quay chi tiết lỗi"),
                cmdKey
        );

        // 1. First execution -> HTTP 201 CREATED
        CaseEvidenceRequestService.KtvRequestEvidenceResult res1 = service.requestEvidence(ktvId, fix.caseId(), request1);
        assertThat(res1.isCreated()).isTrue();
        assertThat(res1.response().status()).isEqualTo("PENDING");
        assertThat(res1.response().commandKey()).isEqualTo(cmdKey);
        assertThat(res1.response().roundNo()).isEqualTo(2);

        // Verify DB record
        Optional<CaseEvidenceRequestEntity> dbEntity = requestRepository.findByCommandKey(cmdKey);
        assertThat(dbEntity).isPresent();
        assertThat(dbEntity.get().getStatus()).isEqualTo("PENDING");
        assertThat(dbEntity.get().getDeadlineAt()).isAfter(Instant.now().plus(47, ChronoUnit.HOURS));

        // 2. Idempotent replay with exact same payload -> HTTP 200 OK (isCreated = false)
        CaseEvidenceRequestService.KtvRequestEvidenceResult res2 = service.requestEvidence(ktvId, fix.caseId(), request1);
        assertThat(res2.isCreated()).isFalse();
        assertThat(res2.response().requestId()).isEqualTo(res1.response().requestId());

        // 3. CommandKey conflict with modified payload -> 409 COMMAND_KEY_CONFLICT
        ReturnCaseDtos.KtvRequestEvidenceRequest requestDifferentPayload = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Lý do khác hoàn toàn so với yêu cầu ban đầu",
                List.of("Ảnh khác"),
                cmdKey
        );

        assertThatThrownBy(() -> service.requestEvidence(ktvId, fix.caseId(), requestDifferentPayload))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(tsEx.getCode()).isEqualTo("COMMAND_KEY_CONFLICT");
                });

        // 4. Attempt to create another PENDING request on same case with new key -> 409 PENDING_REQUEST_EXISTS
        ReturnCaseDtos.KtvRequestEvidenceRequest requestNewKey = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Yêu cầu thêm lần nữa trong khi đợt trước chưa hoàn thành",
                List.of("Thêm video"),
                "REQ-EVD-" + fix.caseId() + "-R2-NEW"
        );

        assertThatThrownBy(() -> service.requestEvidence(ktvId, fix.caseId(), requestNewKey))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(tsEx.getCode()).isEqualTo("PENDING_REQUEST_EXISTS");
                });
    }

    @Test
    @DisplayName("PostgreSQL: D-03 Option 3 - Từ chối phát hành khi quá General Case Deadline (now > seller_response_due_at)")
    void testRequestEvidence_whenPastGeneralDeadline_throwsConflictUnderPostgres() {
        // Create case with general deadline in the past
        TestFixture fix = createEligibleCaseFixture(true);
        String cmdKey = "REQ-EVD-EXPIRED-" + UUID.randomUUID();

        ReturnCaseDtos.KtvRequestEvidenceRequest req = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Yêu cầu bổ sung khi đã quá thời hạn chung",
                List.of("Ảnh chi tiết"),
                cmdKey
        );

        assertThatThrownBy(() -> service.requestEvidence(ktvId, fix.caseId(), req))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(tsEx.getCode()).isEqualTo("GENERAL_DEADLINE_EXPIRED");
                });

        // Verify no request was written to DB
        assertThat(requestRepository.findByCommandKey(cmdKey)).isEmpty();
    }

    @Test
    @DisplayName("PostgreSQL: BD-5 Resubmission - Nộp bổ sung đợt 2 hoàn tất Request FULFILLED và lưu bằng chứng liên kết request_id")
    void testPostgresResubmission_success_batch2_fulfillsRequestAndPersistsEvidence() {
        TestFixture fix = createEligibleCaseFixture(false);
        String reqCmdKey = "REQ-EVD-RESUB-" + UUID.randomUUID();

        // 1. KTV phát hành Request Evidence chính thức (API 1)
        ReturnCaseDtos.KtvRequestEvidenceRequest issueReq = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Yêu cầu video mở kiện hàng đối soát bưu tá",
                List.of("Video mở kiện", "Ảnh tem vận đơn"),
                reqCmdKey
        );
        CaseEvidenceRequestService.KtvRequestEvidenceResult issueResult = service.requestEvidence(ktvId, fix.caseId(), issueReq);
        assertThat(issueResult.isCreated()).isTrue();
        UUID requestId = issueResult.response().requestId();

        // 2. Seller nộp bổ sung (Batch 2) với commandKey hợp lệ (API 3)
        String resubCmdKey = "RESUB-CMD-" + UUID.randomUUID();
        byte[] imageContent = "RAW_JPEG_IMAGE_CONTENT_SAMPLE_FOR_TEM_VAN_DON_BD5_RESUBMISSION".getBytes(StandardCharsets.UTF_8);
        byte[] videoContent = "RAW_MP4_VIDEO_STREAM_BYTES_SAMPLE_FOR_UNBOX_RECORDING_BD5_RESUBMISSION".getBytes(StandardCharsets.UTF_8);
        String imgDigest = sha256Hex(imageContent);
        String vidDigest = sha256Hex(videoContent);
        assertThat(imgDigest).matches("^[0-9a-f]{64}$");
        assertThat(vidDigest).matches("^[0-9a-f]{64}$");
        assertThat(imgDigest).isNotEqualTo(vidDigest);

        ReturnCaseDtos.SellerDamageExemptionRequest resubRequest = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Đã bổ sung đầy đủ video unbox và ảnh tem vận đơn theo yêu cầu",
                List.of(
                        new ReturnCaseDtos.EvidenceItemDto("IMAGE", "evidence/tem_vandon.jpg", "Tem vận đơn", imgDigest, 2048L, null),
                        new ReturnCaseDtos.EvidenceItemDto("VIDEO", "evidence/video_unbox.mp4", "Video unbox", vidDigest, 5242880L, 25)
                ),
                resubCmdKey,
                requestId
        );

        CaseEntity updatedCase = resubmissionService.submitDamageExemptionResubmission(fix.sellerId(), fix.caseId(), resubRequest);
        assertThat(updatedCase).isNotNull();

        // 3. Xác minh trực tiếp trong PostgreSQL: case_evidence_requests
        Map<String, Object> reqRow = jdbcTemplate.queryForMap(
                "SELECT status, fulfillment_command_key, fulfillment_payload_hash, fulfilled_at FROM public.case_evidence_requests WHERE request_id = ?",
                requestId
        );
        assertThat(reqRow.get("status")).isEqualTo("FULFILLED");
        assertThat(reqRow.get("fulfillment_command_key")).isEqualTo(resubCmdKey);
        assertThat(reqRow.get("fulfillment_payload_hash")).isNotNull();
        assertThat(reqRow.get("fulfilled_at")).isNotNull();

        // 4. Xác minh trực tiếp trong PostgreSQL: case_evidence có submission_no = 2 và request_id
        List<Map<String, Object>> evidences = jdbcTemplate.queryForList(
                "SELECT submission_no, request_id, media_type FROM og_compat.case_evidence WHERE round_id = ? AND submission_no = 2 ORDER BY media_type",
                fix.round2Id()
        );
        assertThat(evidences).hasSize(2);
        for (Map<String, Object> ev : evidences) {
            assertThat(ev.get("submission_no")).isEqualTo(2);
            assertThat(ev.get("request_id")).isEqualTo(requestId);
        }

        // 5. Xác minh trực tiếp trong PostgreSQL: case_events có event_type = SUPPLEMENTED
        List<Map<String, Object>> events = jdbcTemplate.queryForList(
                "SELECT event_type, command_key, actor_id FROM og_compat.case_events WHERE round_id = ? AND command_key = ?",
                fix.round2Id(), resubCmdKey
        );
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().get("event_type")).isEqualTo("SUPPLEMENTED");
        assertThat(events.getFirst().get("actor_id")).isEqualTo(fix.sellerId());

        // 6. Idempotent replay trên PostgreSQL với cùng key và cùng payload
        CaseEntity replayCase = resubmissionService.submitDamageExemptionResubmission(fix.sellerId(), fix.caseId(), resubRequest);
        assertThat(replayCase).isNotNull();

        // 7. Thử nộp lại cùng key nhưng khác payload -> 409 COMMAND_KEY_CONFLICT
        ReturnCaseDtos.SellerDamageExemptionRequest conflictRequest = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Nội dung bị sửa đổi sau khi đã submit",
                List.of(
                        new ReturnCaseDtos.EvidenceItemDto("IMAGE", "evidence/tem_vandon.jpg", "Tem vận đơn", imgDigest, 2048L, null)
                ),
                resubCmdKey,
                requestId
        );
        assertThatThrownBy(() -> resubmissionService.submitDamageExemptionResubmission(fix.sellerId(), fix.caseId(), conflictRequest))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(tsEx.getCode()).isEqualTo("COMMAND_KEY_CONFLICT");
                });
    }

    // ==================== D-08 & BD-5 ACCEPTANCE GAP CLOSURE ====================

    @Test
    @DisplayName("PO-CHOICE-3 + D-08: Khi còn hạn 48h -> chặn KTV với 409 EVIDENCE_REQUEST_IN_PROGRESS; khi quá hạn -> KTV chốt vòng 2 và chuyển PENDING sang CANCELLED cùng transaction")
    void testD08_ktvDecideRound2_cancelsPendingEvidenceRequestInSameTx() {
        TestFixture fix = createEligibleCaseFixture(false);
        String reqCmdKey = "CMD_REQ_" + UUID.randomUUID();
        ReturnCaseDtos.KtvRequestEvidenceRequest reqDto = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Cần bổ sung video unbox",
                List.of("VIDEO"),
                reqCmdKey
        );
        CaseEvidenceRequestService.KtvRequestEvidenceResult reqResp = service.requestEvidence(ktvId, fix.caseId(), reqDto);
        UUID requestId = reqResp.response().requestId();

        // 1. Verify request is PENDING
        String statusBefore = jdbcTemplate.queryForObject(
                "SELECT status FROM og_compat.case_evidence_requests WHERE request_id = ?",
                String.class, requestId);
        assertThat(statusBefore).isEqualTo("PENDING");

        // 2. PO-CHOICE-3: While PENDING request is still within 48h deadline, ktvDecideRound2 must be rejected with 409
        String blockedCmdKey = "CMD_DEC_BLOCKED_" + UUID.randomUUID();
        ReturnCaseDtos.KtvRound2DecisionRequest blockedReq = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Thử phán quyết khi yêu cầu bổ sung bằng chứng vẫn còn hạn",
                blockedCmdKey
        );
        assertThatThrownBy(() -> returnCaseService.ktvDecideRound2(ktvId, fix.caseId(), blockedReq))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .satisfies(ex -> {
                    TrustSafetyBusinessException tsEx = (TrustSafetyBusinessException) ex;
                    assertThat(tsEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(tsEx.getCode()).isEqualTo("EVIDENCE_REQUEST_IN_PROGRESS");
                });

        String statusAfterBlock = jdbcTemplate.queryForObject(
                "SELECT status FROM og_compat.case_evidence_requests WHERE request_id = ?",
                String.class, requestId);
        assertThat(statusAfterBlock).isEqualTo("PENDING");

        // 3. Advance request past 48h deadline (UC75 Alt 4a: "hết hạn thì xét nội dung đã nhận")
        jdbcTemplate.update("""
                UPDATE og_compat.case_evidence_requests
                SET issued_at = issued_at - INTERVAL '49 hours',
                    deadline_at = CURRENT_TIMESTAMP - INTERVAL '1 hour'
                WHERE request_id = ?
                """, requestId);

        // 4. KTV executes Round 2 Final Decision after deadline -> succeeds & D-08 cancels PENDING request
        String decCmdKey = "CMD_DEC_" + UUID.randomUUID();
        ReturnCaseDtos.KtvRound2DecisionRequest decReq = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Duyệt hoàn tiền toàn bộ sau khi xem xét case",
                decCmdKey
        );
        ReturnCaseDtos.ReturnCaseDetailResponse decResp = returnCaseService.ktvDecideRound2(ktvId, fix.caseId(), decReq);
        assertThat(decResp).isNotNull();

        // 5. Verify in PostgreSQL that request is now CANCELLED in og_compat view and physical table
        Map<String, Object> reqAfter = jdbcTemplate.queryForMap(
                "SELECT status, updated_at FROM og_compat.case_evidence_requests WHERE request_id = ?",
                requestId);
        assertThat(reqAfter.get("status")).isEqualTo("CANCELLED");
        assertThat(reqAfter.get("updated_at")).isNotNull();

        String physStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM public.case_evidence_requests WHERE request_id = ?",
                String.class, requestId);
        assertThat(physStatus).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("PO-CHOICE-3 + D-08: Khi request quá hạn bị hủy trong KTV Final Decision nhưng transaction rollback -> Request vẫn giữ nguyên PENDING trên PostgreSQL")
    void testD08_ktvDecideRound2_rollbackRevertsRequestCancellation() {
        TestFixture fix = createEligibleCaseFixture(false);
        String reqCmdKey = "CMD_REQ_" + UUID.randomUUID();
        ReturnCaseDtos.KtvRequestEvidenceRequest reqDto = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Cần bổ sung ảnh tem",
                List.of("IMAGE"),
                reqCmdKey
        );
        CaseEvidenceRequestService.KtvRequestEvidenceResult reqResp = service.requestEvidence(ktvId, fix.caseId(), reqDto);
        UUID requestId = reqResp.response().requestId();

        // 1. Verify request is PENDING & move past deadline so PO-CHOICE-3 permits decision
        String statusBefore = jdbcTemplate.queryForObject(
                "SELECT status FROM og_compat.case_evidence_requests WHERE request_id = ?",
                String.class, requestId);
        assertThat(statusBefore).isEqualTo("PENDING");

        jdbcTemplate.update("""
                UPDATE og_compat.case_evidence_requests
                SET issued_at = issued_at - INTERVAL '49 hours',
                    deadline_at = CURRENT_TIMESTAMP - INTERVAL '1 hour'
                WHERE request_id = ?
                """, requestId);

        // 2. Execute ktvDecideRound2 inside a tx and deliberately force rollback
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            tx.execute(status -> {
                String decCmdKey = "CMD_DEC_" + UUID.randomUUID();
                ReturnCaseDtos.KtvRound2DecisionRequest decReq = new ReturnCaseDtos.KtvRound2DecisionRequest(
                        CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                        "Thử nghiệm rollback",
                        decCmdKey
                );
                returnCaseService.ktvDecideRound2(ktvId, fix.caseId(), decReq);
                throw new RuntimeException("Simulated Settlement Failure to test atomic rollback");
            });
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("Simulated Settlement Failure");
        }

        // 3. Verify request in PostgreSQL REMAINS PENDING after rollback
        String statusAfter = jdbcTemplate.queryForObject(
                "SELECT status FROM og_compat.case_evidence_requests WHERE request_id = ?",
                String.class, requestId);
        assertThat(statusAfter).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("BD5-11 / DB AUDIT: case_evidence trigger từ chối UPDATE và DELETE với SQLSTATE 23514 (append-only)")
    void testCaseEvidence_appendOnlyEnforced_BD5_11() {
        TestFixture fix = createEligibleCaseFixture(false);
        UUID evId = UUID.randomUUID();
        byte[] imgContent = "test image for bd5-11 append-only assertion".getBytes(StandardCharsets.UTF_8);
        String imgDigest = sha256Hex(imgContent);

        jdbcTemplate.update("""
                INSERT INTO og_compat.case_evidence(evidence_id, round_id, submitted_by, submission_no, media_type, object_key, text_content, content_digest, file_size_bytes)
                VALUES (?, ?, ?, 1, 'IMAGE', 'evidence/test.jpg', 'Tem vandon', ?, 1024)
                """, evId, fix.round2Id(), fix.sellerId(), imgDigest);

        // Attempt UPDATE on public.case_evidence -> must fail with 23514 (append-only trigger)
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE public.case_evidence SET text_content = 'Hacked' WHERE evidence_id = ?", evId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> {
                    assertThat(ex.getMessage()).contains("case_evidence is append-only");
                    Throwable root = org.springframework.core.NestedExceptionUtils.getMostSpecificCause(ex);
                    assertThat(root).isInstanceOf(java.sql.SQLException.class);
                    assertThat(((java.sql.SQLException) root).getSQLState()).isEqualTo("23514");
                });

        // Attempt DELETE on public.case_evidence -> must fail with 23514 (append-only trigger)
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM public.case_evidence WHERE evidence_id = ?", evId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> {
                    assertThat(ex.getMessage()).contains("case_evidence is append-only");
                    Throwable root = org.springframework.core.NestedExceptionUtils.getMostSpecificCause(ex);
                    assertThat(root).isInstanceOf(java.sql.SQLException.class);
                    assertThat(((java.sql.SQLException) root).getSQLState()).isEqualTo("23514");
                });
    }

    @Test
    @DisplayName("BD5-26: Reassign case (thay đổi assigned_to) không làm thay đổi deadline_at của evidence request")
    void testCaseEvidenceRequest_reassignmentPreservesDeadline_BD5_26() {
        TestFixture fix = createEligibleCaseFixture(false);
        String reqCmdKey = "CMD_REQ_" + UUID.randomUUID();
        ReturnCaseDtos.KtvRequestEvidenceRequest reqDto = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Yêu cầu bổ sung chứng từ",
                List.of("IMAGE"),
                reqCmdKey
        );
        CaseEvidenceRequestService.KtvRequestEvidenceResult reqResp = service.requestEvidence(ktvId, fix.caseId(), reqDto);
        UUID requestId = reqResp.response().requestId();

        Timestamp originalDeadline = jdbcTemplate.queryForObject(
                "SELECT deadline_at FROM og_compat.case_evidence_requests WHERE request_id = ?",
                Timestamp.class, requestId);
        assertThat(originalDeadline).isNotNull();

        // Reassign case to another KTV
        Long anotherKtvId = createTestUser("ktv2");
        grantRole(anotherKtvId, "KTV");
        jdbcTemplate.update("UPDATE og_compat.cases SET assigned_to = ? WHERE case_id = ?", anotherKtvId, fix.caseId());

        // Verify assigned_to updated
        Long currentAssigned = jdbcTemplate.queryForObject(
                "SELECT assigned_to FROM og_compat.cases WHERE case_id = ?",
                Long.class, fix.caseId());
        assertThat(currentAssigned).isEqualTo(anotherKtvId);

        // Verify request deadline_at is exactly preserved
        Timestamp afterDeadline = jdbcTemplate.queryForObject(
                "SELECT deadline_at FROM og_compat.case_evidence_requests WHERE request_id = ?",
                Timestamp.class, requestId);
        assertThat(afterDeadline).isEqualTo(originalDeadline);
    }
}
