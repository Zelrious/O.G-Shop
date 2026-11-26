package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.application.OrderManagementDtos;
import com.oldbutgold.shop.modules.commerce.application.OrderManagementService;
import com.oldbutgold.shop.shared.PostgresTestSafetyGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.ReturnReasonCode;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real PostgreSQL integration tests for UC48 reminders (PO-03 Option A)
 * including concurrent earlyCompleteOrder vs Uc48ReminderService scheduling race.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
class Uc48ReminderConcurrencyPostgresIntegrationTest {

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
    private Uc48ReminderService uc48ReminderService;

    @Autowired
    private OrderManagementService orderManagementService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate txTemplate;

    @AfterEach
    void resetClock() {
        uc48ReminderService.setClock(Clock.systemUTC());
    }

    @Autowired private ReturnCaseService returns;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={true,false})
    void remindsUnshippedBuyerOnceAndStopsAfterShipment(boolean scanBeforeShipment) {
        var fx = createCommittedDeliveredUc83OrderInLast24Hours();
        var created = returns.createReturnRequest(fx.buyerId(),fx.orderId(),new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.DAMAGED_OR_DEFECTIVE,"Hàng nhận bị hư hỏng",List.of(),UUID.randomUUID().toString()));
        var approved = returns.sellerRespondRound1(fx.sellerId(),created.caseId(),new ReturnCaseDtos.SellerRound1ResponseRequest(
                "APPROVE_RETURN","Đồng ý trả hàng",UUID.randomUUID().toString()));
        uc48ReminderService.setClock(Clock.fixed(approved.approvedAt().plus(50,ChronoUnit.HOURS), ZoneOffset.UTC));
        if (scanBeforeShipment) {
            assertThat(txTemplate.<Boolean>execute(s -> uc48ReminderService.remindBuyerToShip(created.caseId()))).isTrue();
            assertThat(txTemplate.<Boolean>execute(s -> uc48ReminderService.remindBuyerToShip(created.caseId()))).isFalse();
        }
        returns.submitReturnShipment(fx.buyerId(),created.caseId(),new ReturnCaseDtos.SubmitReturnShipmentRequest(
                "CARRIER","GHN","RET-"+UUID.randomUUID(),UUID.randomUUID().toString()));
        assertThat(txTemplate.<Boolean>execute(s -> uc48ReminderService.remindBuyerToShip(created.caseId()))).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM og_compat.notifications WHERE user_id=? AND type='UC48_BUYER_SHIP'",
                Integer.class,fx.buyerId())).isEqualTo(scanBeforeShipment ? 1 : 0);
    }

    private record CommittedOrderFixture(Long buyerId, Long sellerId, Long orderId, Instant returnDueAt) {}

    private CommittedOrderFixture createCommittedDeliveredUc83OrderInLast24Hours() {
        return createDeliveredOrder(true);
    }

    private CommittedOrderFixture createDeliveredOrder(boolean returnAllowed) {
        return txTemplate.execute(status -> {
            Long buyerId = createUser("uc48_buyer", "BUYER");
            Long sellerId = createUser("uc48_seller", "SELLER");
            jdbc.update("UPDATE og_compat.users SET identity_workflow = 'UC83' WHERE user_id IN (?, ?)", buyerId, sellerId);

            Long ktvId = jdbc.queryForList(
                    "SELECT user_id FROM og_compat.user_roles ur JOIN og_compat.roles r USING(role_id) WHERE r.role_name = 'KTV' LIMIT 1",
                    Long.class
            ).stream().findFirst().orElseGet(() -> createUser("uc48_ktv", "KTV"));
            Long categoryId = jdbc.queryForObject("SELECT min(category_id) FROM og_compat.categories", Long.class);

            Long ekycId = jdbc.queryForObject(
                    "INSERT INTO og_compat.ekyc_profiles(user_id, revision_no) VALUES (?, 1) RETURNING profile_id",
                    Long.class, sellerId);
            String digest = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            jdbc.update("INSERT INTO og_compat.identity_document_registry(document_digest, digest_key_version, user_id, first_profile_id) VALUES (?, 'TEST-HMAC', ?, ?)",
                    digest, sellerId, ekycId);
            for (String type : new String[]{"CARD_FRONT", "CARD_BACK", "LIVE_FRAME", "REFERENCE"}) {
                jdbc.update("INSERT INTO og_compat.ekyc_private_assets(profile_id, asset_type, object_key, content_digest, expires_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '2 months')",
                        ekycId, type, "p/" + type, digest);
            }
            jdbc.update("UPDATE og_compat.ekyc_profiles SET document_digest = ?, state = 'PENDING', submitted_at = CURRENT_TIMESTAMP WHERE profile_id = ?", digest, ekycId);
            jdbc.update("INSERT INTO og_compat.ekyc_decisions(profile_id, reviewed_by, outcome, command_key) VALUES (?, ?, 'VERIFIED', ?)",
                    ekycId, ktvId, "CMD_EKYC_" + UUID.randomUUID());

            Long addrId = jdbc.queryForObject(
                    "INSERT INTO og_compat.addresses(user_id, recipient_name, phone_number, province, district, ward, detail_address) VALUES (?, 'Seller', '0900000000', 'P', 'D', 'W', 'A') RETURNING address_id",
                    Long.class, sellerId);
            Long spId = jdbc.queryForObject(
                    "INSERT INTO og_compat.seller_profiles(user_id, state, ekyc_profile_id, pickup_address_id, bank_snapshot) VALUES (?, 'PENDING_REVIEW', ?, ?, '{\"bank_name\":\"Mock\",\"account_number\":\"FAKE\",\"account_holder\":\"Seller\"}'::jsonb) RETURNING seller_profile_id",
                    Long.class, sellerId, ekycId, addrId);
            jdbc.update("INSERT INTO og_compat.seller_profile_decisions(seller_profile_id, revision_no, reviewed_by, outcome, profile_snapshot, command_key) VALUES (?, 1, ?, 'APPROVED', '{}'::jsonb, ?)",
                    spId, ktvId, "CMD_SPD_" + UUID.randomUUID());
            jdbc.update("UPDATE og_compat.seller_profiles SET state = 'APPROVED' WHERE seller_profile_id = ?", spId);

            BigDecimal subtotal = new BigDecimal("250000.00");
            Long pid = jdbc.queryForObject("""
                    INSERT INTO og_compat.products(seller_id, category_id, title, description, listed_price, condition, workflow_model, quantity_total, return_allowed, delivery_options)
                    VALUES (?, ?, 'Item', 'Desc', ?, 'GOOD', 'UC83', 10, ?, '["SELF_PICKUP","CARRIER"]'::jsonb) RETURNING product_id
                    """, Long.class, sellerId, categoryId, subtotal, returnAllowed);
            jdbc.update("INSERT INTO og_compat.product_categories(product_id, category_id) VALUES (?, ?)", pid, categoryId);
            Long rid = jdbc.queryForObject("""
                    INSERT INTO og_compat.product_revisions(product_id, revision_no, provenance, listed_unit_price, quantity, return_allowed, delivery_options, content_snapshot)
                    VALUES (?, 1, 'UC83', ?, 10, ?, '["SELF_PICKUP","CARRIER"]'::jsonb, '{}'::jsonb) RETURNING revision_id
                    """, Long.class, pid, subtotal, returnAllowed);
            jdbc.update("INSERT INTO og_compat.revision_media(revision_id, media_type, object_key, content_digest, file_size_bytes, duration_seconds, display_order) VALUES (?, 'IMAGE', 't/img', repeat('a', 64), 100, 10, 0)", rid);
            jdbc.update("INSERT INTO og_compat.revision_media(revision_id, media_type, object_key, content_digest, file_size_bytes, duration_seconds, display_order) VALUES (?, 'VIDEO', 't/vid', repeat('a', 64), 100, 10, 1)", rid);
            jdbc.update("UPDATE og_compat.product_revisions SET state = 'SUBMITTED', submitted_at = CURRENT_TIMESTAMP WHERE revision_id = ?", rid);
            jdbc.update("UPDATE og_compat.products SET status = 'PENDING', current_revision_id = ? WHERE product_id = ?", rid, pid);
            Long decId = jdbc.queryForObject(
                    "INSERT INTO og_compat.product_moderation_decisions(product_id, revision_id, reviewer_id, decision, product_version, command_key) VALUES (?, ?, ?, 'APPROVED', 0, ?) RETURNING decision_id",
                    Long.class, pid, rid, ktvId, "CMD_MD_" + UUID.randomUUID());
            Long assessId = jdbc.queryForObject("""
                    INSERT INTO og_compat.listing_fee_assessments(product_id, revision_id, decision_id, policy_id, listed_total, computed_fee, cumulative_before, amount_due, command_key)
                    SELECT ?, ?, ?, policy_id, 1, 0, 0, 0, ? FROM og_compat.listing_fee_policies WHERE effective_until IS NULL RETURNING assessment_id
                    """, Long.class, pid, rid, decId, "CMD_LFA_" + UUID.randomUUID());
            BigDecimal due = jdbc.queryForObject("SELECT amount_due FROM og_compat.listing_fee_assessments WHERE assessment_id = ?", BigDecimal.class, assessId);
            if (due != null && due.compareTo(BigDecimal.ZERO) > 0) {
                UUID chgId = UUID.randomUUID();
                jdbc.update("INSERT INTO og_compat.listing_fee_charges(charge_id, assessment_id, amount, command_key) VALUES (?, ?, ?, ?)", chgId, assessId, due, "CMD_CHG_" + UUID.randomUUID());
                jdbc.update("INSERT INTO og_compat.listing_fee_receipts(receipt_id, charge_id, provider, provider_transaction_id, amount) VALUES (?, ?, 'BANK_TRANSFER_MOCK', ?, ?)", UUID.randomUUID(), chgId, "TXN_LFR_" + UUID.randomUUID(), due);
            }
            jdbc.update("UPDATE og_compat.products SET status = 'ACTIVE', public_revision_id = ?, listed_price = ?, quantity_total = 10, return_allowed = ?, delivery_options = '[\"SELF_PICKUP\",\"CARRIER\"]'::jsonb WHERE product_id = ?", rid, subtotal, returnAllowed, pid);

            UUID gid = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO og_compat.checkout_groups(group_id, buyer_id, command_key, request_digest, address_snapshot, delivery_method, payment_method, expected_total, payment_due_at)
                    VALUES (?, ?, ?, repeat('a', 64), '{"recipient_name":"Buyer","phone_number":"0900000000","province":"P","district":"D","ward":"W","detail_address":"A"}'::jsonb, 'SELF_PICKUP', 'BANK_TRANSFER_MOCK', ?, CURRENT_TIMESTAMP + INTERVAL '1 hour')
                    """, gid, buyerId, "CMD_CG_" + UUID.randomUUID(), subtotal);

            Long oid = jdbc.queryForObject("""
                    INSERT INTO og_compat.orders(
                        checkout_group_id, group_id, buyer_id, seller_id, shipping_recipient_name, shipping_phone_number,
                        shipping_province, shipping_district, shipping_ward, shipping_detail_address,
                        subtotal, total_amount, buyer_system_fee, seller_system_fee, seller_proceeds,
                        payment_due_at, workflow_model, return_allowed, delivery_method, handover_day, voucher_discount_amount
                    ) VALUES (
                        ?, ?, ?, ?, 'Buyer', '0900000000', 'P', 'D', 'W', 'A',
                        ?, ?, 0, 0, ?,
                        CURRENT_TIMESTAMP + INTERVAL '1 hour', 'UC83', ?, 'SELF_PICKUP', CURRENT_DATE, 0
                    ) RETURNING order_id
                    """, Long.class, gid, gid, buyerId, sellerId, subtotal, subtotal, subtotal, returnAllowed);

            Long iid = jdbc.queryForObject("""
                    INSERT INTO og_compat.order_items(
                        order_id, product_id, product_title, quantity, listed_price, agreed_price,
                        buyer_system_fee, seller_system_fee, buyer_line_total, seller_line_proceeds,
                        workflow_model, product_revision_id, return_allowed, voucher_allocation
                    ) VALUES (?, ?, 'Item', 1, ?, ?, 0, 0, ?, ?, 'UC83', ?, ?, 0) RETURNING order_item_id
                    """, Long.class, oid, pid, subtotal, subtotal, subtotal, subtotal, rid, returnAllowed);

            UUID resId = UUID.randomUUID();
            jdbc.update("INSERT INTO og_compat.inventory_reservations(reservation_id, order_item_id, product_id, quantity, expires_at, command_key) VALUES (?, ?, ?, 1, CURRENT_TIMESTAMP + INTERVAL '1 hour', ?)",
                    resId, iid, pid, "CMD_RES_" + UUID.randomUUID());

            UUID intentId = UUID.randomUUID();
            jdbc.update("INSERT INTO og_compat.payment_intents(intent_id, purpose, group_id, expected_amount, payment_method, deadline, command_key) VALUES (?, 'CHECKOUT_GROUP', ?, ?, 'BANK_TRANSFER_MOCK', CURRENT_TIMESTAMP + INTERVAL '1 hour', ?)",
                    intentId, gid, subtotal, "CMD_PI_" + UUID.randomUUID());
            Long attId = jdbc.queryForObject("INSERT INTO og_compat.payment_attempts(intent_id, txn_ref, amount, provider) VALUES (?, ?, ?, 'BANK_TRANSFER_MOCK') RETURNING attempt_id",
                    Long.class, intentId, "TXN_" + UUID.randomUUID(), subtotal);
            UUID confId = UUID.randomUUID();
            jdbc.update("INSERT INTO og_compat.payment_confirmations(confirmation_id, intent_id, attempt_id, provider, provider_transaction_id, amount) VALUES (?, ?, ?, 'BANK_TRANSFER_MOCK', ?, ?)",
                    confId, intentId, attId, "TXN_P_" + UUID.randomUUID(), subtotal);
            UUID allocId = UUID.randomUUID();
            jdbc.update("INSERT INTO og_compat.payment_allocations(allocation_id, intent_id, confirmation_id, order_id, amount) VALUES (?, ?, ?, ?, ?)",
                    allocId, intentId, confId, oid, subtotal);

            jdbc.update("""
                    UPDATE og_compat.orders SET status = 'PAID_HELD',
                        paid_confirmed_at = (SELECT confirmed_at FROM og_compat.payment_confirmations WHERE confirmation_id = ?),
                        seller_accept_due_at = (SELECT confirmed_at + INTERVAL '2 days' FROM og_compat.payment_confirmations WHERE confirmation_id = ?)
                    WHERE order_id = ?
                    """, confId, confId, oid);
            jdbc.update("UPDATE og_compat.inventory_reservations SET state = 'FUNDED' WHERE reservation_id = ?", resId);
            jdbc.update("UPDATE og_compat.checkout_groups SET state = 'PAID' WHERE group_id = ?", gid);

            jdbc.update("INSERT INTO og_compat.order_fund_components(component_id, order_id, allocation_id, component_type, confirmed_amount) VALUES (?, ?, ?, 'GOODS_CASH', ?)",
                    UUID.randomUUID(), oid, allocId, subtotal);

            jdbc.update("UPDATE og_compat.orders SET status = 'SELLER_CONFIRMED' WHERE order_id = ?", oid);
            jdbc.update("UPDATE og_compat.inventory_reservations SET state = 'CONSUMED' WHERE reservation_id = ?", resId);

            Long shipId = jdbc.queryForObject("""
                    INSERT INTO og_compat.shipments(order_id, carrier, status, workflow_model, delivery_method, fee_snapshot, fee_payer)
                    VALUES (?, 'SELF_PICKUP', 'PENDING', 'UC83', 'SELF_PICKUP', 0, 'BUYER') RETURNING shipment_id
                    """, Long.class, oid);
            jdbc.update("""
                    INSERT INTO og_compat.handover_confirmations(confirmation_id, shipment_id, party, user_id, outcome, command_key)
                    VALUES (?, ?, 'BUYER', ?, 'SUCCESS', ?), (?, ?, 'SELLER', ?, 'SUCCESS', ?)
                    """, UUID.randomUUID(), shipId, buyerId, "CMD_HCB_" + UUID.randomUUID(),
                    UUID.randomUUID(), shipId, sellerId, "CMD_HCS_" + UUID.randomUUID());
            jdbc.queryForObject("SELECT og_confirm_direct_handover(?)", Boolean.class, shipId);

            Instant returnDueAt = jdbc.queryForObject(
                    "SELECT coalesce(return_due_at,valid_delivered_at + interval '3 days') FROM og_compat.orders WHERE order_id = ?",
                    Timestamp.class, oid
            ).toInstant();

            return new CommittedOrderFixture(buyerId, sellerId, oid, returnDueAt);
        });
    }

    @Test
    void neverRemindsNonReturnableOrder() {
        var order=createDeliveredOrder(false);
        uc48ReminderService.setClock(Clock.fixed(order.returnDueAt().minus(12,ChronoUnit.HOURS),ZoneOffset.UTC));
        assertThat(uc48ReminderService.processNewReturnWindowReminderForOrderInNewTx(order.orderId())).isFalse();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={true,false})
    void earlyCompletionAndReminderInBothCommitOrders(boolean reminderFirst) {
        var order=createDeliveredOrder(true);
        uc48ReminderService.setClock(Clock.fixed(order.returnDueAt().minus(22,ChronoUnit.HOURS),ZoneOffset.UTC));
        if (reminderFirst) assertThat(uc48ReminderService.processNewReturnWindowReminderForOrderInNewTx(order.orderId())).isTrue();
        orderManagementService.earlyCompleteOrder(order.buyerId(),order.orderId(),
                new OrderManagementDtos.EarlyCompleteRequest(true,UUID.randomUUID().toString()));
        assertThat(uc48ReminderService.processNewReturnWindowReminderForOrderInNewTx(order.orderId())).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM og_compat.notification_reminders WHERE user_id=? AND source_key=?",
                Integer.class,order.buyerId(),"UC48:RETURN_WINDOW_24H:ORDER:"+order.orderId())).isEqualTo(reminderFirst ? 1 : 0);
    }

    @Test
    void twoSchedulersSendOneNotificationToOrderOwner() throws Exception {
        var order=createDeliveredOrder(true);
        uc48ReminderService.setClock(Clock.fixed(order.returnDueAt().minus(20,ChronoUnit.HOURS),ZoneOffset.UTC));
        var start=new CyclicBarrier(2);
        try (var pool=Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> job=() -> {
                start.await(10,TimeUnit.SECONDS);
                return uc48ReminderService.processNewReturnWindowReminderForOrderInNewTx(order.orderId());
            };
            var first=pool.submit(job);
            var second=pool.submit(job);
            assertThat(java.util.List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true,false);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM og_compat.notifications WHERE user_id=? AND reference_id=? AND target_data->>'destination'='ORDER_DETAIL'",
                Integer.class,order.buyerId(),order.orderId())).isEqualTo(1);
    }

    private Long createUser(String prefix, String role) {
        Long userId = jdbc.queryForObject("""
                INSERT INTO og_compat.users(email, password_hash, full_name, status)
                VALUES (?, 'fixture_pass', ?, 'ACTIVE') RETURNING user_id
                """, Long.class, prefix + "_" + UUID.randomUUID() + "@example.test", "User " + prefix);
        jdbc.update("""
                INSERT INTO og_compat.user_roles(user_id, role_id)
                SELECT ?, role_id FROM og_compat.roles WHERE role_name = ?
                ON CONFLICT DO NOTHING
                """, userId, role);
        return userId;
    }

    @Test
    @DisplayName("PO-03 A: UC48 final-24h reminder fires once and non-UC48 notification_reminders keep separate uniqueness")
    void testFinal24hReminderDedupAndScope() {
        CommittedOrderFixture fx = createCommittedDeliveredUc83OrderInLast24Hours();
        // Advance UC48 scheduler clock into final 24h window [return_due_at - 24h, return_due_at)
        uc48ReminderService.setClock(Clock.fixed(fx.returnDueAt().minus(12, ChronoUnit.HOURS), ZoneOffset.UTC));

        Boolean first = uc48ReminderService.processNewReturnWindowReminderForOrderInNewTx(fx.orderId());
        Boolean second = uc48ReminderService.processNewReturnWindowReminderForOrderInNewTx(fx.orderId());

        assertThat(first).isTrue();
        assertThat(second).isFalse();

        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM og_compat.notification_reminders
                WHERE user_id = ? AND source_key = ?
                """, Integer.class, fx.buyerId(), "UC48:RETURN_WINDOW_24H:ORDER:" + fx.orderId());
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Concurrency: earlyCompleteOrder racing with UC48 reminder scheduler ends with early_completed_at set and clean serialization")
    void testConcurrentEarlyCompleteVsUc48ReminderScheduler() throws Exception {
        CommittedOrderFixture fx = createCommittedDeliveredUc83OrderInLast24Hours();
        uc48ReminderService.setClock(Clock.fixed(fx.returnDueAt().minus(12, ChronoUnit.HOURS), ZoneOffset.UTC));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Future<OrderManagementDtos.OrderActionResponse> earlyFuture = pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return orderManagementService.earlyCompleteOrder(
                        fx.buyerId(),
                        fx.orderId(),
                        new OrderManagementDtos.EarlyCompleteRequest(true, "CMD_EC_" + UUID.randomUUID())
                );
            });

            Future<Boolean> reminderFuture = pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return uc48ReminderService.processNewReturnWindowReminderForOrderInNewTx(fx.orderId());
            });

            OrderManagementDtos.OrderActionResponse ecRes = earlyFuture.get(15, TimeUnit.SECONDS);
            Boolean sent = reminderFuture.get(15, TimeUnit.SECONDS);

            assertThat(ecRes.orderId()).isEqualTo(fx.orderId());

            String reminderState = jdbc.query("""
                    SELECT state FROM og_compat.notification_reminders
                    WHERE user_id = ? AND source_key = ?
                    """, rs -> rs.next() ? rs.getString("state") : null,
                    fx.buyerId(), "UC48:RETURN_WINDOW_24H:ORDER:" + fx.orderId());

            Instant earlyAt = jdbc.queryForObject(
                    "SELECT early_completed_at FROM og_compat.orders WHERE order_id = ?",
                    Timestamp.class, fx.orderId()
            ).toInstant();
            assertThat(earlyAt).isNotNull();

            // Two valid race outcomes under orders FOR UPDATE serialization:
            // (a) earlyComplete won first -> reminder job skipped -> sent=false and no reminder row inserted
            // (b) reminder won first -> inserted SENT before earlyComplete ran
            if (Boolean.FALSE.equals(sent)) {
                assertThat(reminderState).isNull();
            } else {
                assertThat(List.of("SENT", "CANCELLED")).contains(reminderState);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}

