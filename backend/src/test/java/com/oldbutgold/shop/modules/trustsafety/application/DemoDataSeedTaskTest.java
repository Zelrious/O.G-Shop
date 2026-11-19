package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseDecisionEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRoundEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.ReturnReasonCode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Manual developer seed script targeting local og_shop on port 55432; enable manually only when seeding demo data")
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:55432/og_shop",
        "spring.datasource.username=og_shop",
        "spring.datasource.password=root",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.scheduling.return-reconciliation.enabled=false",
        "app.ghn.shop-id=229762",
        "app.ghn.api-token=test-dummy-token",
        "app.ghn.webhook-secret=test-secret",
        "app.vnpay.tmn-code=DUMMY",
        "app.vnpay.hash-secret=DUMMY"
})
class DemoDataSeedTaskTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ReturnCaseService returnCaseService;

    @Test
    @DisplayName("Seed Demo Case in POST_RETURN state on og_shop (port 55432)")
    void seedDemoData() {
        Long buyerId = 1L;
        Long sellerId = 2L;

        PostReturnResult result = transactionTemplate.execute(status -> {
            jdbcTemplate.update("UPDATE og_compat.users SET identity_workflow = 'UC83' WHERE user_id IN (?, ?)", buyerId, sellerId);

            Long ktvUserId = jdbcTemplate.queryForObject(
                    "SELECT user_id FROM og_compat.user_roles ur JOIN og_compat.roles r USING(role_id) WHERE r.role_name = 'KTV' LIMIT 1",
                    Long.class
            );
            Long categoryId = jdbcTemplate.queryForObject("SELECT min(category_id) FROM og_compat.categories", Long.class);

            // Check if seller profile already exists
            Integer existingSpCount = jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.seller_profiles WHERE user_id = ?", Integer.class, sellerId);
            if (existingSpCount == null || existingSpCount == 0) {
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
                        "INSERT INTO og_compat.addresses(user_id, recipient_name, phone_number, province, district, ward, detail_address) VALUES (?, 'Người bán O.G', '0912345679', 'Hà Nội', 'Cầu Giấy', 'Dịch Vọng', 'Số 123 Cầu Giấy') RETURNING address_id",
                        Long.class, sellerId
                );
                Long spId = jdbcTemplate.queryForObject(
                        "INSERT INTO og_compat.seller_profiles(user_id, state, ekyc_profile_id, pickup_address_id, bank_snapshot) VALUES (?, 'PENDING_REVIEW', ?, ?, '{\"bank_name\":\"Vietcombank\",\"account_number\":\"000000001234\",\"account_holder\":\"NGUOI BAN TEST\"}'::jsonb) RETURNING seller_profile_id",
                        Long.class, sellerId, ekycId, addrId
                );
                jdbcTemplate.update("INSERT INTO og_compat.seller_profile_decisions(seller_profile_id, revision_no, reviewed_by, outcome, profile_snapshot, command_key) VALUES (?, 1, ?, 'APPROVED', '{}'::jsonb, ?)", spId, ktvUserId, "CMD_SPD_" + UUID.randomUUID());
            }

            // Product & Listing
            BigDecimal cashAmount = new BigDecimal("250000.00");
            BigDecimal voucherDiscount = BigDecimal.ZERO;
            BigDecimal subtotal = cashAmount;

            Long pid = jdbcTemplate.queryForObject("""
                    INSERT INTO og_compat.products(seller_id, category_id, title, description, listed_price, condition, workflow_model, quantity_total, return_allowed, delivery_options)
                    VALUES (?, ?, 'Tai nghe Bluetooth Sony WH-1000XM4 (Demo Return)', 'Sản phẩm demo phục vụ kiểm thử luồng khiếu nại trả hàng', ?, 'GOOD', 'UC83', 10, true, '["SELF_PICKUP","CARRIER"]'::jsonb) RETURNING product_id
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

            // Checkout Group, Order, Items
            UUID gid = UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO og_compat.checkout_groups(group_id, buyer_id, command_key, request_digest, address_snapshot, delivery_method, payment_method, expected_total, payment_due_at)
                    VALUES (?, ?, ?, repeat('a', 64), '{"recipient_name":"[TEST] Người mua","phone_number":"0912345678","province":"Hà Nội","district":"Cầu Giấy","ward":"Dịch Vọng","detail_address":"Số 456 Cầu Giấy"}'::jsonb, 'SELF_PICKUP', 'BANK_TRANSFER_MOCK', ?, CURRENT_TIMESTAMP + INTERVAL '1 hour')
                    """, gid, buyerId, "CMD_CG_" + UUID.randomUUID(), cashAmount);

            Long oid = jdbcTemplate.queryForObject("""
                    INSERT INTO og_compat.orders(
                        checkout_group_id, group_id, buyer_id, seller_id, shipping_recipient_name, shipping_phone_number,
                        shipping_province, shipping_district, shipping_ward, shipping_detail_address,
                        subtotal, total_amount, buyer_system_fee, seller_system_fee, seller_proceeds,
                        payment_due_at, workflow_model, return_allowed, delivery_method, handover_day, voucher_discount_amount
                    ) VALUES (
                        ?, ?, ?, ?, '[TEST] Người mua', '0912345678', 'Hà Nội', 'Cầu Giấy', 'Dịch Vọng', 'Số 456 Cầu Giấy',
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
                        ?, ?, 'Tai nghe Bluetooth Sony WH-1000XM4 (Demo Return)', 1, ?, ?,
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

            // Fund component
            UUID cashComponentId = UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO og_compat.order_fund_components(component_id, order_id, allocation_id, component_type, confirmed_amount)
                    VALUES (?, ?, ?, 'GOODS_CASH', ?)
                    """, cashComponentId, oid, allocId, cashAmount);

            // Seller confirm order
            jdbcTemplate.update("UPDATE og_compat.orders SET status = 'SELLER_CONFIRMED' WHERE order_id = ?", oid);
            jdbcTemplate.update("UPDATE og_compat.inventory_reservations SET state = 'CONSUMED' WHERE reservation_id = ?", resId);

            // Shipment & Handover to DELIVERED
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

            // ==================== WORKFLOW RETURN CASE ====================
            // 1. Buyer creates return request (Round 1 OPEN)
            ReturnCaseDtos.CreateReturnRequest createReq = new ReturnCaseDtos.CreateReturnRequest(
                    ReturnReasonCode.DAMAGED_OR_DEFECTIVE,
                    "Tai nghe nhận về bị rè bên trái, không kết nối được bluetooth ổn định. Yêu cầu trả hàng hoàn tiền.",
                    List.of(),
                    "CMD_CREATE_" + UUID.randomUUID()
            );
            ReturnCaseDtos.ReturnCaseDetailResponse createdCase = returnCaseService.createReturnRequest(buyerId, oid, createReq);

            // 2. Seller approves return (Round 1 APPROVED)
            returnCaseService.sellerRespondRound1(sellerId, createdCase.caseId(), new ReturnCaseDtos.SellerRound1ResponseRequest(
                    CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                    "Đồng ý nhận lại hàng kiểm tra",
                    "CMD_SELLER_" + UUID.randomUUID()
            ));

            // 3. Buyer submits return shipment (RETURNING)
            ReturnCaseDtos.SubmitReturnShipmentRequest shipReq = new ReturnCaseDtos.SubmitReturnShipmentRequest(
                    "CARRIER",
                    "GHN",
                    "GHN-DEMO-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                    "CMD_SHIP_" + UUID.randomUUID()
            );
            ReturnCaseDtos.ReturnCaseDetailResponse submittedCase = returnCaseService.submitReturnShipment(buyerId, createdCase.caseId(), shipReq);
            Long shipmentId = submittedCase.returnShipment().shipmentId();

            // 4. Carrier confirms return delivery -> Transitions to POST_RETURN, Round 2 OPEN
            returnCaseService.confirmReturnDeliveryByCarrier(createdCase.caseId(), shipmentId, Instant.now());

            Long round2Id = jdbcTemplate.queryForObject(
                    "SELECT round_id FROM og_compat.case_rounds WHERE case_id = ? AND round_no = 2",
                    Long.class,
                    createdCase.caseId()
            );

            return new PostReturnResult(oid, createdCase.caseId(), round2Id);
        });

        System.out.println("=========================================================");
        System.out.println("DEMO SEED COMPLETED SUCCESSFULLY!");
        System.out.println("Order ID : " + result.orderId());
        System.out.println("Case ID  : " + result.caseId());
        System.out.println("Round 2  : " + result.round2Id());
        System.out.println("=========================================================");

        // Verify case state in database via og_compat views
        String caseState = jdbcTemplate.queryForObject("SELECT state FROM og_compat.cases WHERE case_id = ?", String.class, result.caseId());
        assertThat(caseState).isEqualTo("POST_RETURN");

        String round2State = jdbcTemplate.queryForObject("SELECT state FROM og_compat.case_rounds WHERE round_id = ?", String.class, result.round2Id());
        assertThat(round2State).isEqualTo("OPEN");

        Boolean hasDamageReq = jdbcTemplate.queryForObject("SELECT valid_damage_request_at IS NOT NULL FROM og_compat.case_rounds WHERE round_id = ?", Boolean.class, result.round2Id());
        assertThat(hasDamageReq).isFalse();
    }

    record PostReturnResult(Long orderId, Long caseId, Long round2Id) {}
}
