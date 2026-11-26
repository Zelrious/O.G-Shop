package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.trustsafety.api.ReviewDtos;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.AccountTrustRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real PostgreSQL integration tests for UC61/UC46/UC11/UC22 (PO-01 Option A):
 * 1. Step 6 sequence: 500 (5★) -> spend 400 -> 5★->3★ (-100, waive 400) -> +200 -> comment-only -> 3★->5★ (+100) => 300 xu.
 * 2. User waiver non-recapture sequence: 500 (5★) -> spend 400 -> 5★->3★ (-100, waive 400) -> +200 -> 3★->4★ (+0) -> 4★->3★ (0) => 200 xu.
 * 3. Bidirectional reviews (Buyer -> Seller and Seller -> Buyer), unified CREDIT_REVIEW ledger entries, privacy guard, and UC11 reputation.
 * 4. LEGACY_V14 delivered/completed orders remain reviewable even after DISPUTED / REFUNDED transitions.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
class OrderReviewWalletPostgresIntegrationTest {

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
    private OrderReviewService orderReviewService;

    @Autowired
    private AccountTrustRepository accountTrustRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void fourFiveThreeFiveLeavesThreeRevisionsAndOneSubmitReward() {
        Long order=createDeliveredOrder(buyerId,sellerId,"LEGACY_V14","DELIVERED");
        int[] stars={4,5,3,5};
        long[] deltas={400,100,-500,500};
        long previous=0;
        for (int i=0;i<stars.length;i++) {
            orderReviewService.submitReview(buyerId,new ReviewDtos.CreateReviewRequest(order,stars[i],"Nhận xét lần "+i));
            long current=walletBalance(sellerId);
            assertThat(current-previous).isEqualTo(deltas[i]);
            previous=current;
        }
        assertThat(walletBalance(buyerId)).isEqualTo(500);
        assertThat(previous).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM og_compat.review_revisions h JOIN og_compat.reviews r USING(review_id) WHERE r.order_id=?",Integer.class,order)).isEqualTo(3);
    }

    @Autowired
    private EntityManager entityManager;

    private Long buyerId;
    private Long sellerId;

    @BeforeEach
    void setUp() {
        buyerId = createUser("rev_buyer", "BUYER");
        sellerId = createUser("rev_seller", "SELLER");
        grantRole(sellerId, "BUYER");
    }

    private Long createUser(String prefix, String role) {
        Long userId = jdbc.queryForObject("""
                INSERT INTO og_compat.users(email, password_hash, full_name, status)
                VALUES (?, 'fixture_pass', ?, 'ACTIVE') RETURNING user_id
                """, Long.class, prefix + "_" + UUID.randomUUID() + "@example.test", "User " + prefix);
        grantRole(userId, role);
        return userId;
    }

    private void grantRole(Long userId, String role) {
        jdbc.update("""
                INSERT INTO og_compat.user_roles(user_id, role_id)
                SELECT ?, role_id FROM og_compat.roles WHERE role_name = ?
                ON CONFLICT DO NOTHING
                """, userId, role);
    }

    private Long createDeliveredOrder(Long bId, Long sId, String workflowModel, String status) {
        BigDecimal total = new BigDecimal("250000.00");
        UUID groupId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO checkout_groups(
                    group_id, buyer_id, command_key, request_digest, address_snapshot,
                    delivery_method, payment_method, expected_total, payment_due_at
                ) VALUES (
                    ?, ?, ?, repeat('a', 64),
                    '{"recipient_name":"Buyer","phone_number":"0912345678","province":"HN","district":"BD","ward":"Ward","detail_address":"Addr"}'::jsonb,
                    'SELF_PICKUP', 'BANK_TRANSFER_MOCK', ?, CURRENT_TIMESTAMP + INTERVAL '1 hour'
                )
                """, groupId, bId, "CMD_REV_GRP_" + UUID.randomUUID(), total);

        Long orderId = jdbc.queryForObject("""
                INSERT INTO og_compat.orders(
                    checkout_group_id, group_id, buyer_id, seller_id, shipping_recipient_name, shipping_phone_number,
                    shipping_province, shipping_district, shipping_ward, shipping_detail_address,
                    subtotal, shipping_fee, total_amount, voucher_discount_amount, buyer_system_fee, seller_system_fee,
                    seller_proceeds, payment_due_at, workflow_model, return_allowed, delivery_method, handover_day,
                    status, valid_delivered_at
                ) VALUES (
                    ?, ?, ?, ?, 'Buyer', '0912345678', 'HN', 'BD', 'Ward', 'Addr',
                    ?, 0, ?, 0, 0, 0, ?, CURRENT_TIMESTAMP + INTERVAL '1 hour', ?, true, 'SELF_PICKUP', CURRENT_DATE,
                    'PAYMENT_PENDING', CURRENT_TIMESTAMP - INTERVAL '1 day'
                ) RETURNING order_id
                """, Long.class, groupId, groupId, bId, sId, total, total, total, workflowModel);

        jdbc.update("UPDATE og_compat.orders SET status = ? WHERE order_id = ?", status, orderId);
        return orderId;
    }

    private long walletBalance(long userId) {
        Long b = jdbc.queryForObject(
                "SELECT balance FROM og_compat.reward_accounts WHERE user_id = ?",
                Long.class, userId
        );
        return b != null ? b : 0L;
    }

    private void adjustUnrelatedWallet(long userId, long helperOrderId, long delta, String reason) {
        jdbc.update("""
                INSERT INTO og_compat.reward_ledger(entry_id, user_id, entry_type, points_delta, order_id, reason, command_key)
                VALUES (?, ?, 'CREDIT_REVIEW', ?, ?, ?, ?)
                """, UUID.randomUUID(), userId, delta, helperOrderId, reason, "TEST_ADJ_" + UUID.randomUUID());
    }


    @Test
    @DisplayName("PO-01 A Step 6: 5★ (+500) -> spend 400 -> 3★ (-100, waive 400) -> +200 -> comment-only (0) -> 5★ (+100) => 300 xu")
    void testStep6SequenceYields300Coins() {
        Long orderId = createDeliveredOrder(buyerId, sellerId, "UC83", "DELIVERED");
        Long helperOrderId = createDeliveredOrder(buyerId, sellerId, "UC83", "DELIVERED");

        // 1. Buyer rates Seller 5* -> Seller wallet = 500 (U=500, W=0), Buyer wallet = 500
        orderReviewService.submitReview(buyerId, new ReviewDtos.CreateReviewRequest(orderId, 5, "Đóng gói chuẩn, giao đúng mô tả."));
        entityManager.flush();
        assertThat(walletBalance(sellerId)).isEqualTo(500L);
        assertThat(walletBalance(buyerId)).isEqualTo(500L);

        // 2. Seller spends 400 -> wallet = 100
        adjustUnrelatedWallet(sellerId, helperOrderId, -400L, "Seller tiêu 400 xu");
        assertThat(walletBalance(sellerId)).isEqualTo(100L);

        // 3. Buyer edits 5* -> 3* -> claw back 100, waive 400 -> wallet = 0 (U=0, W=400)
        orderReviewService.submitReview(buyerId, new ReviewDtos.CreateReviewRequest(orderId, 3, "Hạ xuống 3 sao để kiểm tra thu hồi xu."));
        assertThat(walletBalance(sellerId)).isEqualTo(0L);
        Map<String, Object> revAfter3 = jdbc.queryForMap(
                "SELECT review_id, rating, recipient_unwaived_coins, recipient_waived_coins FROM public.reviews WHERE record_type='reviews' AND order_id=? AND reviewer_id=? AND deleted_at IS NULL",
                orderId, buyerId);
        assertThat(((Number) revAfter3.get("rating")).intValue()).isEqualTo(3);

        // 4. Seller earns +200 -> wallet = 200
        adjustUnrelatedWallet(sellerId, helperOrderId, 200L, "Seller kiếm thêm 200 xu");
        assertThat(walletBalance(sellerId)).isEqualTo(200L);

        // 5. Buyer edits comment-only (still 3*) -> no point change -> wallet = 200
        orderReviewService.submitReview(buyerId, new ReviewDtos.CreateReviewRequest(orderId, 3, "Sửa nội dung nhận xét riêng."));
        assertThat(walletBalance(sellerId)).isEqualTo(200L);
        Map<String, Object> revAfter5 = jdbc.queryForMap(
                "SELECT review_id, rating, recipient_unwaived_coins, recipient_waived_coins FROM public.reviews WHERE record_type='reviews' AND order_id=? AND reviewer_id=? AND deleted_at IS NULL",
                orderId, buyerId);
        assertThat(revAfter5)
                .withFailMessage("After step 5 expected rating=3,U=0,W=400, got %s", revAfter5)
                .satisfies(m -> {
                    assertThat(((Number) m.get("rating")).intValue()).isEqualTo(3);
                    assertThat(((Number) m.get("recipient_unwaived_coins")).longValue()).isEqualTo(0L);
                    assertThat(((Number) m.get("recipient_waived_coins")).longValue()).isEqualTo(400L);
                });

        // 6. Buyer raises 3* -> 5* -> target=500, ceiling=(U+W)=400 -> +100 -> wallet = 300
        orderReviewService.submitReview(buyerId, new ReviewDtos.CreateReviewRequest(orderId, 5, "Tăng lại lên 5 sao."));
        List<Map<String, Object>> ledgerRows = jdbc.queryForList(
                "SELECT points_delta, command_key, reason FROM og_compat.reward_ledger WHERE order_id = ? ORDER BY created_at, entry_id",
                orderId);
        Map<String, Object> revState = jdbc.queryForMap(
                "SELECT review_id, rating, recipient_unwaived_coins, recipient_waived_coins FROM public.reviews WHERE record_type='reviews' AND order_id=? AND reviewer_id=? AND deleted_at IS NULL",
                orderId, buyerId);
        assertThat(walletBalance(sellerId))
                .withFailMessage("Expected 300 xu, got %s; revState=%s; ledger=%s", walletBalance(sellerId), revState, ledgerRows)
                .isEqualTo(300L);

        // Verify all ledger entries for orderId use unified CREDIT_REVIEW type
        List<String> entryTypes = jdbc.queryForList(
                "SELECT DISTINCT entry_type FROM og_compat.reward_ledger WHERE order_id = ?",
                String.class, orderId);
        assertThat(entryTypes).containsExactly("CREDIT_REVIEW");
    }

    @Test
    @DisplayName("PO-01 A Waiver Non-Recapture: 5★ (+500) -> spend 400 -> 3★ (-100, W=400) -> +200 -> 4★ (+0, U=0, W=400) -> 3★ (0) => 200 xu")
    void testWaivedShortfallCountsTowardCeilingAndIsNeverClawedBack() {
        Long orderId = createDeliveredOrder(buyerId, sellerId, "UC83", "DELIVERED");
        Long helperOrderId = createDeliveredOrder(buyerId, sellerId, "UC83", "DELIVERED");

        // 1. 5* -> +500 xu (U=500, W=0)
        orderReviewService.submitReview(buyerId, new ReviewDtos.CreateReviewRequest(orderId, 5, "Rất tốt."));
        assertThat(walletBalance(sellerId)).isEqualTo(500L);

        // 2. Spend 400 xu -> wallet = 100
        adjustUnrelatedWallet(sellerId, helperOrderId, -400L, "Seller tiêu 400 xu");
        assertThat(walletBalance(sellerId)).isEqualTo(100L);

        // 3. 5* -> 3* -> deduct 100, waive 400 -> wallet = 0, U=0, W=400
        orderReviewService.submitReview(buyerId, new ReviewDtos.CreateReviewRequest(orderId, 3, "Hạ xuống 3 sao."));
        assertThat(walletBalance(sellerId)).isEqualTo(0L);

        // 4. Earn +200 xu -> wallet = 200
        adjustUnrelatedWallet(sellerId, helperOrderId, 200L, "Seller nhận thêm 200 xu");
        assertThat(walletBalance(sellerId)).isEqualTo(200L);

        // 5. Raise 3* -> 4*: target=400, (U+W)=400 -> credit = max(0, 400 - 400) = 0 -> wallet stays 200
        orderReviewService.submitReview(buyerId, new ReviewDtos.CreateReviewRequest(orderId, 4, "Tăng lên 4 sao."));
        assertThat(walletBalance(sellerId)).isEqualTo(200L);

        // 6. Drop 4* -> 3*: target=0, askBack = min(U=0, max(0, 400 - 0)) = 0 -> NO clawback -> wallet stays 200
        orderReviewService.submitReview(buyerId, new ReviewDtos.CreateReviewRequest(orderId, 3, "Hạ lại xuống 3 sao."));
        assertThat(walletBalance(sellerId)).isEqualTo(200L);

        Map<String, Object> coinState = jdbc.queryForMap(
                "SELECT recipient_unwaived_coins, recipient_waived_coins FROM public.reviews WHERE record_type='reviews' AND order_id=? AND reviewer_id=? AND deleted_at IS NULL",
                orderId, buyerId);
        assertThat(((Number) coinState.get("recipient_unwaived_coins")).longValue()).isEqualTo(0L);
        assertThat(((Number) coinState.get("recipient_waived_coins")).longValue()).isEqualTo(400L);
    }

    @Test
    @DisplayName("Bidirectional reviews (Buyer->Seller & Seller->Buyer), PII privacy guard, UC11 reputation, and LEGACY_V14 after REFUNDED")
    void testBidirectionalReviewsPrivacyAndLegacyRefunded() {
        Long legacyOrderId = createDeliveredOrder(buyerId, sellerId, "LEGACY_V14", "REFUNDED");

        // PII privacy check rejects phone/email in comment
        assertThatThrownBy(() -> orderReviewService.submitReview(
                buyerId, new ReviewDtos.CreateReviewRequest(legacyOrderId, 5, "Liên hệ 0912345678 hoặc a@b.com")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("số điện thoại hoặc email cá nhân");

        // Buyer -> Seller 4* (+500 Buyer submitter, +400 Seller recipient)
        orderReviewService.submitReview(buyerId, new ReviewDtos.CreateReviewRequest(legacyOrderId, 4, "Sản phẩm đúng mô tả."));
        // Seller -> Buyer 5* (+500 Seller submitter, +500 Buyer recipient)
        orderReviewService.submitReview(sellerId, new ReviewDtos.CreateReviewRequest(legacyOrderId, 5, "Người mua thanh toán và nhận hàng nhanh."));

        assertThat(walletBalance(buyerId)).isEqualTo(1000L); // +500 submit + 500 receive
        assertThat(walletBalance(sellerId)).isEqualTo(900L); // +400 receive + 500 submit

        var sellerRep = accountTrustRepository.reputation(sellerId);
        var buyerRep = accountTrustRepository.reputation(buyerId);
        assertThat(sellerRep.score()).isEqualTo(100);
        assertThat(sellerRep.events())
                .anySatisfy(e -> {
                    assertThat(e.source()).isEqualTo("REVIEW");
                    assertThat(e.pointsDelta()).isEqualTo(4);
                });
        assertThat(buyerRep.score()).isEqualTo(100);
        assertThat(buyerRep.events())
                .anySatisfy(e -> {
                    assertThat(e.source()).isEqualTo("REVIEW");
                    assertThat(e.pointsDelta()).isEqualTo(5);
                });
    }
}

