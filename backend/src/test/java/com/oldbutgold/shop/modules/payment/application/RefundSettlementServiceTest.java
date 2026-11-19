package com.oldbutgold.shop.modules.payment.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("RefundSettlementServiceTest - Unit verification of BLK-05, PO-DEC-01, PO-DEC-02, PO-DEC-04A/B, PO-DEC-06, OPEN-02A, OPEN-02B & D-10")
class RefundSettlementServiceTest {

    private static final ZoneId ASIA_HO_CHI_MINH = ZoneId.of("Asia/Ho_Chi_Minh");

    private JdbcTemplate jdbcTemplate;
    private Clock clock;
    private RefundSettlementService service;
    private Instant now;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        // Fixed instant: 2026-11-15 10:00:00 UTC = 17:00:00 Asia/Ho_Chi_Minh (15 days remaining in Nov)
        now = Instant.parse("2026-11-15T10:00:00Z");
        clock = Clock.fixed(now, ZoneId.of("UTC"));
        service = new RefundSettlementService(jdbcTemplate, clock);
        mockAdminAuthentication(999L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockAdminAuthentication(Long adminId) {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(
                String.valueOf(adminId), "password", "ROLE_ADMIN"
        );
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void mockLookup(UUID opId, String state, Long orderId) {
        RefundSettlementService.OpOrderLookup lookup = new RefundSettlementService.OpOrderLookup(opId, state, orderId);
        when(jdbcTemplate.query(contains("FROM og_compat.settlement_operations so"), any(RowMapper.class), eq(opId)))
                .thenReturn(List.of(lookup));
    }

    private void mockOrderLock(Long orderId) {
        when(jdbcTemplate.query(contains("FROM og_compat.orders WHERE order_id = ? FOR UPDATE"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of(orderId));
    }

    private void mockOpRecord(RefundSettlementService.SettlementOpRecord op) {
        when(jdbcTemplate.query(contains("WHERE operation_id = ?\nFOR UPDATE"), any(RowMapper.class), eq(op.operationId())))
                .thenReturn(List.of(op));
    }

    private void mockCanonicalGroupDebit(Long orderId, UUID groupId, Long buyerId, BigDecimal orderDiscount, BigDecimal groupTotalDiscount, Long groupDeltaCoins) {
        mockCanonicalGroupDebit(orderId, groupId, buyerId, orderDiscount, groupTotalDiscount, groupDeltaCoins, BigDecimal.ONE, "PARALLEL_SPLIT");
    }

    private void mockCanonicalGroupDebit(Long orderId, UUID groupId, Long buyerId, BigDecimal orderDiscount, BigDecimal groupTotalDiscount, Long groupDeltaCoins, BigDecimal pointValue, String workflowModel) {
        RefundSettlementService.OrderGroupContext orderCtx = new RefundSettlementService.OrderGroupContext(
                groupId, buyerId, workflowModel, orderDiscount
        );
        when(jdbcTemplate.query(contains("SELECT group_id, buyer_id, workflow_model, points_discount_amount"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of(orderCtx));

        if (groupId != null && !"LEGACY_V14".equals(workflowModel)) {
            Long pinnedPolicyId = 1L;
            when(jdbcTemplate.query(contains("SELECT reward_policy_id"), any(RowMapper.class), eq(groupId)))
                    .thenReturn(List.of(pinnedPolicyId));

            List<RefundSettlementService.GroupDebitRecord> debits;
            if (groupDeltaCoins != null) {
                debits = List.of(new RefundSettlementService.GroupDebitRecord(
                        UUID.randomUUID(), buyerId, groupDeltaCoins, pinnedPolicyId, pointValue
                ));
            } else {
                debits = List.of();
            }
            when(jdbcTemplate.query(contains("WHERE rl.group_id = ? AND rl.entry_type = 'DEBIT_CHECKOUT'"), any(RowMapper.class), eq(groupId)))
                    .thenReturn(debits);

            List<BigDecimal> discounts;
            if (groupTotalDiscount != null && groupTotalDiscount.compareTo(BigDecimal.ZERO) > 0) {
                discounts = List.of(groupTotalDiscount);
            } else {
                discounts = List.of();
            }
            when(jdbcTemplate.query(contains("SELECT points_discount_amount\nFROM og_compat.orders\nWHERE group_id = ?"), any(RowMapper.class), eq(groupId)))
                    .thenReturn(discounts);

            if (pointValue != null) {
                when(jdbcTemplate.query(contains("SELECT point_value"), any(RowMapper.class), eq(pinnedPolicyId)))
                        .thenReturn(List.of(pointValue));
            } else {
                when(jdbcTemplate.query(contains("SELECT point_value"), any(RowMapper.class), eq(pinnedPolicyId)))
                        .thenReturn(List.of());
            }
        }
    }

    private void mockContext(RefundSettlementService.DecisionOrderContext ctx, UUID opId) {
        when(jdbcTemplate.query(contains("JOIN og_compat.orders o ON c.order_id = o.order_id"), any(RowMapper.class), eq(opId)))
                .thenReturn(List.of(ctx));
        if (ctx.orderId() != null) {
            BigDecimal points = ctx.pointsDiscountAmount() != null ? ctx.pointsDiscountAmount() : BigDecimal.ZERO;
            UUID defaultGroupId = UUID.nameUUIDFromBytes(("group-for-order-" + ctx.orderId()).getBytes());
            Long pointsLong = points.longValue();
            Long delta = points.compareTo(BigDecimal.ZERO) > 0 ? -pointsLong : null;
            mockCanonicalGroupDebit(ctx.orderId(), defaultGroupId, ctx.buyerId(), points, points, delta, BigDecimal.ONE, "PARALLEL_SPLIT");
        }
    }

    // =========================================================================
    // BLK-05 Option A & D-10 Option B: Confirmation & Expiry Tests
    // =========================================================================

    @Test
    @DisplayName("BLK-05 & D-10: Confirms settlement operation and credits coins with D-10 month-end expiry")
    void confirmRefundSettlement_fullGoodsRefund_creditsCoinsAndCalculatesExpiry() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 101L;
        Long orderId = 201L;
        Long buyerId = 301L;

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "REFUND_BUYER", new BigDecimal("200000"),
                "RETURN", decisionId, buyerId, "REQUESTED", "CMD_OP_1"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 501L, orderId, buyerId, new BigDecimal("50000"), new BigDecimal("200000")
        );
        mockContext(ctx, opId);

        // Cumulative approved refundable value across rounds = 200,000 VND
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(so.amount), 0)"), eq(BigDecimal.class), eq(orderId)))
                .thenReturn(new BigDecimal("200000"));

        // Prior confirmed coins = 0
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(points_delta), 0)"), eq(Long.class), eq(orderId)))
                .thenReturn(0L);

        // No pending operations
        when(jdbcTemplate.query(contains("AND so.state IN ('REQUESTED', 'PROCESSING')"), any(RowMapper.class), eq(orderId), eq(opId)))
                .thenReturn(List.of());

        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_accounts"), eq(Integer.class), eq(buyerId)))
                .thenReturn(1);

        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_ledger WHERE command_key = ?"), eq(Integer.class), anyString()))
                .thenReturn(0);

        when(jdbcTemplate.queryForObject(contains("WHERE c.order_id = ? AND so.state <> 'CONFIRMED'"), eq(Integer.class), eq(orderId)))
                .thenReturn(0);

        RefundSettlementService.RefundSettlementResult result = service.confirmRefundSettlement(opId);

        assertThat(result.state()).isEqualTo("CONFIRMED");
        assertThat(result.coinsCredited()).isEqualTo(50000L);

        // Confirmation date is 2026-11-15 (15 days remaining) -> expires 2026-12-01 00:00:00 Asia/Ho_Chi_Minh
        Instant expectedExpiry = ZonedDateTime.of(2026, 12, 1, 0, 0, 0, 0, ASIA_HO_CHI_MINH).toInstant();
        assertThat(result.expiresAt()).isEqualTo(expectedExpiry);

        // Verify update settlement_operations to CONFIRMED
        verify(jdbcTemplate).update(contains("UPDATE og_compat.settlement_operations"), any(), anyString(), eq(opId));

        // Verify insert CREDIT_REFUND with 50,000 coins and structured expires_at
        verify(jdbcTemplate).update(contains("INSERT INTO og_compat.reward_ledger"),
                any(UUID.class), eq(buyerId), eq(50000L), eq(orderId), eq("REFUND:DECISION:" + decisionId), anyString(), eq(Timestamp.from(expectedExpiry))
        );
    }

    @Test
    @DisplayName("OPEN-02A: Two consecutive refund rounds - Round 1 confirms 20,000 coins, Round 2 confirms remaining 30,000 coins")
    void confirmRefundSettlement_twoConsecutiveRounds_cumulativeCapRespected() {
        Long orderId = 202L;
        Long buyerId = 302L;
        BigDecimal debitedCoins = new BigDecimal("50000");
        BigDecimal subtotal = new BigDecimal("500000");

        // Round 1
        UUID op1 = UUID.randomUUID();
        mockLookup(op1, "REQUESTED", orderId);
        mockOrderLock(orderId);
        RefundSettlementService.SettlementOpRecord opRecord1 = new RefundSettlementService.SettlementOpRecord(
                op1, UUID.randomUUID(), "REFUND_BUYER", new BigDecimal("200000"),
                "RETURN", 101L, buyerId, "REQUESTED", "CMD_OP_R1"
        );
        mockOpRecord(opRecord1);
        RefundSettlementService.DecisionOrderContext ctx1 = new RefundSettlementService.DecisionOrderContext(
                101L, "FULL_GOODS_REFUND", true, 501L, orderId, buyerId, debitedCoins, subtotal
        );
        mockContext(ctx1, op1);

        // Cumulative approved refundable value at Round 1 = 200,000 VND
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(so.amount), 0)"), eq(BigDecimal.class), eq(orderId)))
                .thenReturn(new BigDecimal("200000"));
        // Prior confirmed = 0; no pending
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(points_delta), 0)"), eq(Long.class), eq(orderId)))
                .thenReturn(0L);
        when(jdbcTemplate.query(contains("AND so.state IN ('REQUESTED', 'PROCESSING')"), any(RowMapper.class), eq(orderId), eq(op1)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_accounts"), eq(Integer.class), eq(buyerId)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_ledger WHERE command_key = ?"), eq(Integer.class), anyString()))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("WHERE c.order_id = ? AND so.state <> 'CONFIRMED'"), eq(Integer.class), eq(orderId)))
                .thenReturn(1);

        RefundSettlementService.RefundSettlementResult result1 = service.confirmRefundSettlement(op1);
        assertThat(result1.coinsCredited()).isEqualTo(20000L); // floor(200,000 * 50,000 / 500,000) = 20,000

        // Round 2
        UUID op2 = UUID.randomUUID();
        mockLookup(op2, "REQUESTED", orderId);
        RefundSettlementService.SettlementOpRecord opRecord2 = new RefundSettlementService.SettlementOpRecord(
                op2, UUID.randomUUID(), "REFUND_BUYER", new BigDecimal("300000"),
                "RETURN", 102L, buyerId, "REQUESTED", "CMD_OP_R2"
        );
        mockOpRecord(opRecord2);
        RefundSettlementService.DecisionOrderContext ctx2 = new RefundSettlementService.DecisionOrderContext(
                102L, "FULL_GOODS_REFUND", true, 502L, orderId, buyerId, debitedCoins, subtotal
        );
        mockContext(ctx2, op2);

        // Cumulative approved refundable value at Round 2 = 200,000 + 300,000 = 500,000 VND
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(so.amount), 0)"), eq(BigDecimal.class), eq(orderId)))
                .thenReturn(new BigDecimal("500000"));
        // Prior confirmed = 20,000; no pending
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(points_delta), 0)"), eq(Long.class), eq(orderId)))
                .thenReturn(20000L);
        when(jdbcTemplate.query(contains("AND so.state IN ('REQUESTED', 'PROCESSING')"), any(RowMapper.class), eq(orderId), eq(op2)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("WHERE c.order_id = ? AND so.state <> 'CONFIRMED'"), eq(Integer.class), eq(orderId)))
                .thenReturn(0);

        RefundSettlementService.RefundSettlementResult result2 = service.confirmRefundSettlement(op2);
        // Cumulative entitlement = floor(500,000 * 50,000 / 500,000) = 50,000
        // Incremental = 50,000 - 20,000 - 0 = 30,000
        assertThat(result2.coinsCredited()).isEqualTo(30000L);
    }

    @Test
    @DisplayName("OPEN-02A: First-round operation still REQUESTED when next round evaluates -> capacity is reserved")
    void confirmRefundSettlement_firstRoundRequested_subtractsPendingFromCapacity() {
        UUID op2 = UUID.randomUUID();
        Long orderId = 203L;
        Long buyerId = 303L;

        mockLookup(op2, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord2 = new RefundSettlementService.SettlementOpRecord(
                op2, UUID.randomUUID(), "REFUND_BUYER", new BigDecimal("300000"),
                "RETURN", 102L, buyerId, "REQUESTED", "CMD_OP_R2"
        );
        mockOpRecord(opRecord2);

        RefundSettlementService.DecisionOrderContext ctx2 = new RefundSettlementService.DecisionOrderContext(
                102L, "FULL_GOODS_REFUND", true, 502L, orderId, buyerId, new BigDecimal("50000"), new BigDecimal("500000")
        );
        mockContext(ctx2, op2);

        // Cumulative approved refundable value = R1 (200,000 in REQUESTED) + R2 (300,000 in CONFIRMED) = 500,000 VND
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(so.amount), 0)"), eq(BigDecimal.class), eq(orderId)))
                .thenReturn(new BigDecimal("500000"));
        // Prior confirmed = 0
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(points_delta), 0)"), eq(Long.class), eq(orderId)))
                .thenReturn(0L);

        // Operation 1 is still in REQUESTED for 200,000 VND (reserved share = 20,000 coins)
        when(jdbcTemplate.query(contains("AND so.state IN ('REQUESTED', 'PROCESSING')"), any(RowMapper.class), eq(orderId), eq(op2)))
                .thenReturn(List.of(new BigDecimal("200000")));

        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_accounts"), eq(Integer.class), eq(buyerId)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_ledger WHERE command_key = ?"), eq(Integer.class), anyString()))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("WHERE c.order_id = ? AND so.state <> 'CONFIRMED'"), eq(Integer.class), eq(orderId)))
                .thenReturn(1);

        RefundSettlementService.RefundSettlementResult result = service.confirmRefundSettlement(op2);

        // Cumulative entitlement = 50,000; pendingCoin = 20,000; Incremental = 50,000 - 0 - 20,000 = 30,000
        assertThat(result.coinsCredited()).isEqualTo(30000L);
    }

    @Test
    @DisplayName("OPEN-02A: First-round operation in PROCESSING -> subtracted from available capacity")
    void confirmRefundSettlement_firstRoundProcessing_subtractsPendingFromCapacity() {
        UUID op2 = UUID.randomUUID();
        Long orderId = 204L;
        Long buyerId = 304L;

        mockLookup(op2, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord2 = new RefundSettlementService.SettlementOpRecord(
                op2, UUID.randomUUID(), "REFUND_BUYER", new BigDecimal("400000"),
                "RETURN", 102L, buyerId, "REQUESTED", "CMD_OP_R2"
        );
        mockOpRecord(opRecord2);

        RefundSettlementService.DecisionOrderContext ctx2 = new RefundSettlementService.DecisionOrderContext(
                102L, "FULL_GOODS_REFUND", true, 502L, orderId, buyerId, new BigDecimal("50000"), new BigDecimal("500000")
        );
        mockContext(ctx2, op2);

        // Cumulative approved refundable value = 500,000 VND
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(so.amount), 0)"), eq(BigDecimal.class), eq(orderId)))
                .thenReturn(new BigDecimal("500000"));
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(points_delta), 0)"), eq(Long.class), eq(orderId)))
                .thenReturn(0L);

        // Operation 1 in PROCESSING for 250,000 VND (reserved share = 25,000 coins)
        when(jdbcTemplate.query(contains("AND so.state IN ('REQUESTED', 'PROCESSING')"), any(RowMapper.class), eq(orderId), eq(op2)))
                .thenReturn(List.of(new BigDecimal("250000")));

        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_accounts"), eq(Integer.class), eq(buyerId)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_ledger WHERE command_key = ?"), eq(Integer.class), anyString()))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("WHERE c.order_id = ? AND so.state <> 'CONFIRMED'"), eq(Integer.class), eq(orderId)))
                .thenReturn(1);

        RefundSettlementService.RefundSettlementResult result = service.confirmRefundSettlement(op2);

        // Cumulative entitlement = 50,000; pendingCoin = 25,000; Incremental = 50,000 - 0 - 25,000 = 25,000
        assertThat(result.coinsCredited()).isEqualTo(25000L);
    }

    @Test
    @DisplayName("OPEN-02A: First-round operation in FAILED -> NOT subtracted from capacity")
    void confirmRefundSettlement_firstRoundFailed_notSubtractedFromCapacity() {
        UUID op2 = UUID.randomUUID();
        Long orderId = 205L;
        Long buyerId = 305L;

        mockLookup(op2, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord2 = new RefundSettlementService.SettlementOpRecord(
                op2, UUID.randomUUID(), "REFUND_BUYER", new BigDecimal("500000"),
                "RETURN", 102L, buyerId, "REQUESTED", "CMD_OP_R2"
        );
        mockOpRecord(opRecord2);

        RefundSettlementService.DecisionOrderContext ctx2 = new RefundSettlementService.DecisionOrderContext(
                102L, "FULL_GOODS_REFUND", true, 502L, orderId, buyerId, new BigDecimal("50000"), new BigDecimal("500000")
        );
        mockContext(ctx2, op2);

        // Cumulative active refundable value = 500,000 VND (FAILED op is excluded by SQL state IN)
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(so.amount), 0)"), eq(BigDecimal.class), eq(orderId)))
                .thenReturn(new BigDecimal("500000"));
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(points_delta), 0)"), eq(Long.class), eq(orderId)))
                .thenReturn(0L);

        // Query for REQUESTED/PROCESSING returns empty list because earlier operation was FAILED
        when(jdbcTemplate.query(contains("AND so.state IN ('REQUESTED', 'PROCESSING')"), any(RowMapper.class), eq(orderId), eq(op2)))
                .thenReturn(List.of());

        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_accounts"), eq(Integer.class), eq(buyerId)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_ledger WHERE command_key = ?"), eq(Integer.class), anyString()))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("WHERE c.order_id = ? AND so.state <> 'CONFIRMED'"), eq(Integer.class), eq(orderId)))
                .thenReturn(0);

        RefundSettlementService.RefundSettlementResult result = service.confirmRefundSettlement(op2);

        // Op2 gets full 50,000 coins because FAILED operation does not reserve capacity
        assertThat(result.coinsCredited()).isEqualTo(50000L);
    }

    @Test
    @DisplayName("OPEN-02A: Negative incremental entitlement clamped to zero; no negative coins credited")
    void confirmRefundSettlement_negativeIncrementalClampedToZero() {
        UUID op = UUID.randomUUID();
        Long orderId = 206L;
        Long buyerId = 306L;

        mockLookup(op, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                op, UUID.randomUUID(), "REFUND_BUYER", new BigDecimal("100000"),
                "RETURN", 103L, buyerId, "REQUESTED", "CMD_OP_CLAMP"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                103L, "FULL_GOODS_REFUND", true, 503L, orderId, buyerId, new BigDecimal("30000"), new BigDecimal("300000")
        );
        mockContext(ctx, op);

        // Cumulative approved refundable value = 100,000 VND (entitlement = floor(100,000 * 30,000 / 300,000) = 10,000)
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(so.amount), 0)"), eq(BigDecimal.class), eq(orderId)))
                .thenReturn(new BigDecimal("100000"));

        // Already confirmed = 30,000 (exceeds current cumulative entitlement of 10,000 due to earlier round or revision)
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(points_delta), 0)"), eq(Long.class), eq(orderId)))
                .thenReturn(30000L);

        when(jdbcTemplate.query(contains("AND so.state IN ('REQUESTED', 'PROCESSING')"), any(RowMapper.class), eq(orderId), eq(op)))
                .thenReturn(List.of());

        when(jdbcTemplate.queryForObject(contains("WHERE c.order_id = ? AND so.state <> 'CONFIRMED'"), eq(Integer.class), eq(orderId)))
                .thenReturn(0);

        RefundSettlementService.RefundSettlementResult result = service.confirmRefundSettlement(op);

        // Incremental: max(0, 10,000 - 30,000 - 0) = 0
        assertThat(result.coinsCredited()).isEqualTo(0L);
        verify(jdbcTemplate, never()).update(contains("INSERT INTO og_compat.reward_ledger"), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("OPEN-02A: EXEMPT_GOODS_REFUND outcome refunds 0 coins directly and is excluded from cumulative entitlement")
    void confirmRefundSettlement_exemptGoodsRefund_strictlyExcludedFromCumulativeEntitlement() {
        UUID op = UUID.randomUUID();
        Long orderId = 207L;
        Long buyerId = 307L;

        mockLookup(op, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                op, UUID.randomUUID(), "REFUND_BUYER", new BigDecimal("200000"),
                "RETURN", 104L, buyerId, "REQUESTED", "CMD_OP_EXEMPT"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                104L, "EXEMPT_GOODS_REFUND", true, 504L, orderId, buyerId, new BigDecimal("20000"), new BigDecimal("200000")
        );
        mockContext(ctx, op);

        when(jdbcTemplate.queryForObject(contains("WHERE c.order_id = ? AND so.state <> 'CONFIRMED'"), eq(Integer.class), eq(orderId)))
                .thenReturn(0);

        RefundSettlementService.RefundSettlementResult result = service.confirmRefundSettlement(op);

        // Strictly 0 coins refunded for EXEMPT_GOODS_REFUND
        assertThat(result.coinsCredited()).isEqualTo(0L);
        verify(jdbcTemplate, never()).update(contains("INSERT INTO og_compat.reward_ledger"), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("F-01: Serializes on Order lock BEFORE querying alreadyRefunded to prevent race condition")
    void confirmRefundSettlement_locksOrderFirst_beforeReadingAlreadyRefunded() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 110L;
        Long orderId = 210L;
        Long buyerId = 310L;

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "REFUND_BUYER", new BigDecimal("200000"),
                "RETURN", decisionId, buyerId, "REQUESTED", "CMD_OP_LOCK"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 510L, orderId, buyerId, new BigDecimal("40000"), new BigDecimal("200000")
        );
        mockContext(ctx, opId);

        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(so.amount), 0)"), eq(BigDecimal.class), eq(orderId)))
                .thenReturn(new BigDecimal("200000"));
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(points_delta), 0)"), eq(Long.class), eq(orderId)))
                .thenReturn(0L);
        when(jdbcTemplate.query(contains("AND so.state IN ('REQUESTED', 'PROCESSING')"), any(RowMapper.class), eq(orderId), eq(opId)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_accounts"), eq(Integer.class), eq(buyerId)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_ledger WHERE command_key = ?"), eq(Integer.class), anyString()))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("WHERE c.order_id = ? AND so.state <> 'CONFIRMED'"), eq(Integer.class), eq(orderId)))
                .thenReturn(0);

        service.confirmRefundSettlement(opId);

        // Verify strict lock acquisition ordering: Order lock first, then settlement op lock, then cumulative calculation
        InOrder inOrder = inOrder(jdbcTemplate);
        inOrder.verify(jdbcTemplate).query(contains("FROM og_compat.orders WHERE order_id = ? FOR UPDATE"), any(RowMapper.class), eq(orderId));
        inOrder.verify(jdbcTemplate).query(contains("FROM og_compat.settlement_operations\nWHERE operation_id = ?"), any(RowMapper.class), eq(opId));
        inOrder.verify(jdbcTemplate).update(contains("UPDATE og_compat.settlement_operations"), any(), anyString(), eq(opId));
        inOrder.verify(jdbcTemplate).queryForObject(contains("SELECT COALESCE(SUM(so.amount), 0)"), eq(BigDecimal.class), eq(orderId));
        inOrder.verify(jdbcTemplate).update(contains("INSERT INTO og_compat.reward_ledger"), any(UUID.class), eq(buyerId), eq(40000L), eq(orderId), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("D-10 / Structured Expiry: Each refund round stores its own independent structured expires_at timestamp")
    void confirmRefundSettlement_persistsStructuredExpiresAtIndependentPerRound() {
        Long orderId = 220L;
        Long buyerId = 320L;
        BigDecimal debitedCoins = new BigDecimal("50000");
        BigDecimal subtotal = new BigDecimal("500000");

        // --- ROUND 1 at Nov 15 (15 days remaining -> expires 2026-12-01 00:00:00 Asia/Ho_Chi_Minh) ---
        Instant r1Time = Instant.parse("2026-11-15T10:00:00Z");
        RefundSettlementService serviceR1 = new RefundSettlementService(jdbcTemplate, Clock.fixed(r1Time, ZoneId.of("UTC")));

        UUID op1 = UUID.randomUUID();
        mockLookup(op1, "REQUESTED", orderId);
        mockOrderLock(orderId);
        RefundSettlementService.SettlementOpRecord opRecord1 = new RefundSettlementService.SettlementOpRecord(
                op1, UUID.randomUUID(), "REFUND_BUYER", new BigDecimal("200000"),
                "RETURN", 101L, buyerId, "REQUESTED", "CMD_OP_R1"
        );
        mockOpRecord(opRecord1);
        RefundSettlementService.DecisionOrderContext ctx1 = new RefundSettlementService.DecisionOrderContext(
                101L, "FULL_GOODS_REFUND", true, 501L, orderId, buyerId, debitedCoins, subtotal
        );
        mockContext(ctx1, op1);

        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(so.amount), 0)"), eq(BigDecimal.class), eq(orderId)))
                .thenReturn(new BigDecimal("200000"));
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(points_delta), 0)"), eq(Long.class), eq(orderId)))
                .thenReturn(0L);
        when(jdbcTemplate.query(contains("AND so.state IN ('REQUESTED', 'PROCESSING')"), any(RowMapper.class), eq(orderId), eq(op1)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_accounts"), eq(Integer.class), eq(buyerId)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_ledger WHERE command_key = ?"), eq(Integer.class), anyString()))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("WHERE c.order_id = ? AND so.state <> 'CONFIRMED'"), eq(Integer.class), eq(orderId)))
                .thenReturn(1);

        RefundSettlementService.RefundSettlementResult res1 = serviceR1.confirmRefundSettlement(op1);
        Instant expectedExpiryR1 = ZonedDateTime.of(2026, 12, 1, 0, 0, 0, 0, ASIA_HO_CHI_MINH).toInstant();
        assertThat(res1.expiresAt()).isEqualTo(expectedExpiryR1);

        // Verify R1 structured expires_at passed to INSERT
        verify(jdbcTemplate).update(contains("INSERT INTO og_compat.reward_ledger"),
                any(UUID.class), eq(buyerId), eq(20000L), eq(orderId), eq("REFUND:DECISION:101"), anyString(), eq(Timestamp.from(expectedExpiryR1))
        );

        // --- ROUND 2 at Nov 20 (10 days remaining -> expires 2027-01-01 00:00:00 Asia/Ho_Chi_Minh) ---
        Instant r2Time = Instant.parse("2026-11-20T10:00:00Z");
        RefundSettlementService serviceR2 = new RefundSettlementService(jdbcTemplate, Clock.fixed(r2Time, ZoneId.of("UTC")));

        UUID op2 = UUID.randomUUID();
        mockLookup(op2, "REQUESTED", orderId);
        RefundSettlementService.SettlementOpRecord opRecord2 = new RefundSettlementService.SettlementOpRecord(
                op2, UUID.randomUUID(), "REFUND_BUYER", new BigDecimal("300000"),
                "RETURN", 102L, buyerId, "REQUESTED", "CMD_OP_R2"
        );
        mockOpRecord(opRecord2);
        RefundSettlementService.DecisionOrderContext ctx2 = new RefundSettlementService.DecisionOrderContext(
                102L, "FULL_GOODS_REFUND", true, 502L, orderId, buyerId, debitedCoins, subtotal
        );
        mockContext(ctx2, op2);

        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(so.amount), 0)"), eq(BigDecimal.class), eq(orderId)))
                .thenReturn(new BigDecimal("500000"));
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(points_delta), 0)"), eq(Long.class), eq(orderId)))
                .thenReturn(20000L);
        when(jdbcTemplate.query(contains("AND so.state IN ('REQUESTED', 'PROCESSING')"), any(RowMapper.class), eq(orderId), eq(op2)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("WHERE c.order_id = ? AND so.state <> 'CONFIRMED'"), eq(Integer.class), eq(orderId)))
                .thenReturn(0);

        RefundSettlementService.RefundSettlementResult res2 = serviceR2.confirmRefundSettlement(op2);
        Instant expectedExpiryR2 = ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, ASIA_HO_CHI_MINH).toInstant();
        assertThat(res2.expiresAt()).isEqualTo(expectedExpiryR2);

        // Verify R2 structured expires_at passed to INSERT is distinct from R1
        verify(jdbcTemplate).update(contains("INSERT INTO og_compat.reward_ledger"),
                any(UUID.class), eq(buyerId), eq(30000L), eq(orderId), eq("REFUND:DECISION:102"), anyString(), eq(Timestamp.from(expectedExpiryR2))
        );
        assertThat(expectedExpiryR1).isNotEqualTo(expectedExpiryR2);
    }

    @Test
    @DisplayName("OPEN-02A: Retrying an operation after prior execution avoids duplicate Coin credit via commandKey idempotency")
    void confirmRefundSettlement_retryingOperation_avoidsDuplicateCoinCredit() {
        Long orderId = 221L;
        Long buyerId = 321L;
        UUID opId = UUID.randomUUID();

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);
        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "REFUND_BUYER", new BigDecimal("500000"),
                "RETURN", 105L, buyerId, "REQUESTED", "CMD_OP_RETRY"
        );
        mockOpRecord(opRecord);
        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                105L, "FULL_GOODS_REFUND", true, 505L, orderId, buyerId, new BigDecimal("50000"), new BigDecimal("500000")
        );
        mockContext(ctx, opId);

        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(so.amount), 0)"), eq(BigDecimal.class), eq(orderId)))
                .thenReturn(new BigDecimal("500000"));
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(points_delta), 0)"), eq(Long.class), eq(orderId)))
                .thenReturn(0L);
        when(jdbcTemplate.query(contains("AND so.state IN ('REQUESTED', 'PROCESSING')"), any(RowMapper.class), eq(orderId), eq(opId)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_accounts"), eq(Integer.class), eq(buyerId)))
                .thenReturn(1);

        // Simulate that reward_ledger row already exists for this commandKey (e.g. earlier attempt credited coin before failure in another subsystem)
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_ledger WHERE command_key = ?"), eq(Integer.class), eq("REFUND:COIN:105:" + opId)))
                .thenReturn(1);

        when(jdbcTemplate.queryForObject(contains("WHERE c.order_id = ? AND so.state <> 'CONFIRMED'"), eq(Integer.class), eq(orderId)))
                .thenReturn(0);

        RefundSettlementService.RefundSettlementResult res = service.confirmRefundSettlement(opId);

        assertThat(res.state()).isEqualTo("CONFIRMED");
        // No duplicate INSERT executed
        verify(jdbcTemplate, never()).update(contains("INSERT INTO og_compat.reward_ledger"), any(), any(), any(), any(), any(), any(), any());
    }

    // =========================================================================
    // OPEN-02B: Manual Admin Coin Correction Tests
    // =========================================================================

    @Test
    @DisplayName("OPEN-02B: Strict lock ordering and idempotency: advisory lock acquired FIRST, idempotency checked SECOND, account locked THIRD, ledger debited FOURTH")
    void applyAdminCorrection_withinBalance_debitsSuccessfully() {
        Long buyerId = 501L;
        Long adminId = 999L;
        Long pointsDebit = 5000L;
        String reason = "Manual clawback for chargeback refund";
        String cmdKey = "ADMIN_CORR:501:1";

        // Idempotency check: not executed yet in reward_ledger
        when(jdbcTemplate.query(contains("WHERE command_key = ?"), any(RowMapper.class), eq(cmdKey)))
                .thenReturn(List.of());
        // Not executed in audit_logs either
        when(jdbcTemplate.queryForObject(contains("WHERE request_id = ? AND action = 'REWARD_CORRECTION_ABSORBED'"), eq(Integer.class), eq(cmdKey)))
                .thenReturn(0);

        // Available balance = 10,000
        when(jdbcTemplate.queryForObject(contains("FROM og_compat.reward_accounts WHERE user_id = ? FOR UPDATE"), eq(Long.class), eq(buyerId)))
                .thenReturn(10000L);

        RefundSettlementService.RewardCorrectionResult result = service.applyAdminCorrection(
                buyerId, pointsDebit, adminId, reason, cmdKey
        );

        assertThat(result.status()).isEqualTo("APPLIED");
        assertThat(result.requestedDebit()).isEqualTo(5000L);
        assertThat(result.actualDebited()).isEqualTo(5000L);
        assertThat(result.absorbedByPlatform()).isEqualTo(0L);
        assertThat(result.remainingBalance()).isEqualTo(5000L);

        // Verify exact strict lock ordering
        InOrder inOrder = inOrder(jdbcTemplate);
        // 1. Advisory lock on normalized command key
        inOrder.verify(jdbcTemplate).query(contains("SELECT pg_advisory_xact_lock"), any(ResultSetExtractor.class), eq("admin-correction:" + cmdKey));
        // 2. Idempotency checks under advisory lock
        inOrder.verify(jdbcTemplate).query(contains("WHERE command_key = ?"), any(RowMapper.class), eq(cmdKey));
        inOrder.verify(jdbcTemplate).queryForObject(contains("WHERE request_id = ? AND action = 'REWARD_CORRECTION_ABSORBED'"), eq(Integer.class), eq(cmdKey));
        // 3. Lock reward account row
        inOrder.verify(jdbcTemplate).queryForObject(contains("FROM og_compat.reward_accounts WHERE user_id = ? FOR UPDATE"), eq(Long.class), eq(buyerId));
        // 4. Persist negative delta into reward_ledger with entry_type = CORRECTION
        inOrder.verify(jdbcTemplate).update(contains("INSERT INTO og_compat.reward_ledger"),
                any(UUID.class), eq(buyerId), eq(-5000L), eq(adminId), contains(reason), eq(cmdKey)
        );

        // Verify audit_logs is NOT inserted when actualDebit > 0
        verify(jdbcTemplate, never()).update(contains("INSERT INTO og_compat.audit_logs"), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("OPEN-02B: Correction exceeding available balance clamps debit to balance; remainder absorbed by platform")
    void applyAdminCorrection_exceedingBalance_absorbedByPlatform() {
        Long buyerId = 502L;
        Long adminId = 999L;
        Long pointsDebit = 10000L;
        String reason = "Buyer spent coins before dispute reversal";
        String cmdKey = "ADMIN_CORR:502:1";

        when(jdbcTemplate.query(contains("WHERE command_key = ?"), any(RowMapper.class), eq(cmdKey)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("WHERE request_id = ? AND action = 'REWARD_CORRECTION_ABSORBED'"), eq(Integer.class), eq(cmdKey)))
                .thenReturn(0);

        // Available balance = 3,000 (< 10,000 requested)
        when(jdbcTemplate.queryForObject(contains("FROM og_compat.reward_accounts WHERE user_id = ? FOR UPDATE"), eq(Long.class), eq(buyerId)))
                .thenReturn(3000L);

        RefundSettlementService.RewardCorrectionResult result = service.applyAdminCorrection(
                buyerId, pointsDebit, adminId, reason, cmdKey
        );

        assertThat(result.status()).isEqualTo("APPLIED");
        assertThat(result.requestedDebit()).isEqualTo(10000L);
        assertThat(result.actualDebited()).isEqualTo(3000L);
        assertThat(result.absorbedByPlatform()).isEqualTo(7000L);
        assertThat(result.remainingBalance()).isEqualTo(0L);

        // Verify debited amount is strictly 3,000 (not 10,000) and audit reason records absorbed amount
        verify(jdbcTemplate).update(contains("INSERT INTO og_compat.reward_ledger"),
                any(UUID.class), eq(buyerId), eq(-3000L), eq(adminId), contains("[UNRECOVERED_ABSORBED:7000]"), eq(cmdKey)
        );

        // Verify audit_logs is atomically persisted with structured absorption details
        verify(jdbcTemplate).update(contains("INSERT INTO og_compat.audit_logs"),
                eq(adminId), eq(buyerId), contains("\"actualDebited\":3000"), eq(cmdKey), any(Timestamp.class)
        );
    }

    @Test
    @DisplayName("OPEN-02B: When balance is 0, entire correction is absorbed; persisted into og_compat.audit_logs")
    void applyAdminCorrection_zeroBalance_recordsAuditLogAndPreservesNonNegativeBalance() {
        Long buyerId = 503L;
        Long adminId = 999L;
        Long pointsDebit = 8000L;
        String reason = "Account emptied prior to dispute resolution";
        String cmdKey = "ADMIN_CORR:503:1";

        when(jdbcTemplate.query(contains("WHERE command_key = ?"), any(RowMapper.class), eq(cmdKey)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("WHERE request_id = ? AND action = 'REWARD_CORRECTION_ABSORBED'"), eq(Integer.class), eq(cmdKey)))
                .thenReturn(0);

        // Available balance = 0
        when(jdbcTemplate.queryForObject(contains("FROM og_compat.reward_accounts WHERE user_id = ? FOR UPDATE"), eq(Long.class), eq(buyerId)))
                .thenReturn(0L);

        RefundSettlementService.RewardCorrectionResult result = service.applyAdminCorrection(
                buyerId, pointsDebit, adminId, reason, cmdKey
        );

        assertThat(result.status()).isEqualTo("ABSORBED_BY_PLATFORM");
        assertThat(result.actualDebited()).isEqualTo(0L);
        assertThat(result.absorbedByPlatform()).isEqualTo(8000L);
        assertThat(result.remainingBalance()).isEqualTo(0L);

        // Crucial: no negative delta inserted into reward_ledger, preventing check violation 23514
        verify(jdbcTemplate, never()).update(contains("INSERT INTO og_compat.reward_ledger"), any(), any(), any(), any(), any(), any(), any());

        // Verified: Audit entry is persisted into og_compat.audit_logs with all required audit info
        verify(jdbcTemplate).update(contains("INSERT INTO og_compat.audit_logs"),
                eq(adminId), eq(buyerId), contains("\"requestedDebit\":8000"), eq(cmdKey), any(Timestamp.class)
        );
    }

    @Test
    @DisplayName("OPEN-02B: Idempotent replay on identical commandKey via audit_logs when previously fully absorbed")
    void applyAdminCorrection_zeroBalance_idempotentReplayViaAuditLogs() {
        Long buyerId = 503L;
        Long adminId = 999L;
        Long pointsDebit = 8000L;
        String reason = "Account emptied prior to dispute resolution";
        String cmdKey = "ADMIN_CORR:503:ABSORBED_REPLAY";

        when(jdbcTemplate.query(contains("WHERE command_key = ?"), any(RowMapper.class), eq(cmdKey)))
                .thenReturn(List.of());
        // Audit log exists from previous execution
        when(jdbcTemplate.queryForObject(contains("WHERE request_id = ? AND action = 'REWARD_CORRECTION_ABSORBED'"), eq(Integer.class), eq(cmdKey)))
                .thenReturn(1);

        RefundSettlementService.RewardCorrectionResult result = service.applyAdminCorrection(
                buyerId, pointsDebit, adminId, reason, cmdKey
        );

        assertThat(result.status()).isEqualTo("IDEMPOTENT_REPLAY");
        assertThat(result.actualDebited()).isEqualTo(0L);
        assertThat(result.absorbedByPlatform()).isEqualTo(8000L);

        // No new audit log or ledger insert
        verify(jdbcTemplate, never()).update(contains("INSERT INTO og_compat.audit_logs"), any(), any(), any(), any(), any());
        verify(jdbcTemplate, never()).update(contains("INSERT INTO og_compat.reward_ledger"), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("OPEN-02B: Idempotent replay on identical commandKey via reward_ledger")
    void applyAdminCorrection_idempotentReplay() {
        Long buyerId = 504L;
        Long adminId = 999L;
        Long pointsDebit = 5000L;
        String reason = "Duplicate submission";
        String cmdKey = "ADMIN_CORR:504:DUPLICATE";
        UUID previousEntryId = UUID.randomUUID();

        // Previous correction already executed with points_delta = -5000
        RefundSettlementService.ExistingCorrection existingCorrection =
                new RefundSettlementService.ExistingCorrection(previousEntryId, -5000L);
        when(jdbcTemplate.query(contains("WHERE command_key = ?"), any(RowMapper.class), eq(cmdKey)))
                .thenReturn(List.of(existingCorrection));

        RefundSettlementService.RewardCorrectionResult result = service.applyAdminCorrection(
                buyerId, pointsDebit, adminId, reason, cmdKey
        );

        assertThat(result.status()).isEqualTo("IDEMPOTENT_REPLAY");
        assertThat(result.entryId()).isEqualTo(previousEntryId);
        assertThat(result.actualDebited()).isEqualTo(5000L);

        // No new ledger insert executed
        verify(jdbcTemplate, never()).update(contains("INSERT INTO og_compat.reward_ledger"), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("OPEN-02B: Idempotent replay for partial debit via reward_ledger: zero duplicate ledger or audit insertions")
    void applyAdminCorrection_partialDebit_idempotentReplayViaRewardLedger() {
        Long buyerId = 505L;
        Long adminId = 999L;
        Long pointsDebit = 10000L;
        String reason = "Partial debit duplicate submission";
        String cmdKey = "ADMIN_CORR:505:PARTIAL_REPLAY";
        UUID previousEntryId = UUID.randomUUID();

        // Previous correction already executed with points_delta = -3000
        RefundSettlementService.ExistingCorrection existingCorrection =
                new RefundSettlementService.ExistingCorrection(previousEntryId, -3000L);
        when(jdbcTemplate.query(contains("WHERE command_key = ?"), any(RowMapper.class), eq(cmdKey)))
                .thenReturn(List.of(existingCorrection));

        RefundSettlementService.RewardCorrectionResult result = service.applyAdminCorrection(
                buyerId, pointsDebit, adminId, reason, cmdKey
        );

        assertThat(result.status()).isEqualTo("IDEMPOTENT_REPLAY");
        assertThat(result.entryId()).isEqualTo(previousEntryId);
        assertThat(result.actualDebited()).isEqualTo(3000L);
        assertThat(result.absorbedByPlatform()).isEqualTo(7000L);

        // Crucial: No new ledger insert and no new audit log insert executed on replay
        verify(jdbcTemplate, never()).update(contains("INSERT INTO og_compat.reward_ledger"), any(), any(), any(), any(), any(), any(), any());
        verify(jdbcTemplate, never()).update(contains("INSERT INTO og_compat.audit_logs"), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("OPEN-02B: Strict concurrency lock ordering: command advisory lock acquired FIRST, idempotency checked SECOND, account row lock NOT acquired on replay")
    void applyAdminCorrection_serializesOnAdvisoryLock_andReturnsIdempotentReplayOnDuplicateCommandKey() {
        Long buyerId = 506L;
        Long adminId = 999L;
        Long pointsDebit = 5000L;
        String reason = "Duplicate concurrent submission";
        String cmdKey = "ADMIN_CORR:506:CONCURRENT";

        // Duplicate found in audit_logs under lock
        when(jdbcTemplate.query(contains("WHERE command_key = ?"), any(RowMapper.class), eq(cmdKey)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("WHERE request_id = ? AND action = 'REWARD_CORRECTION_ABSORBED'"), eq(Integer.class), eq(cmdKey)))
                .thenReturn(1);

        RefundSettlementService.RewardCorrectionResult result = service.applyAdminCorrection(
                buyerId, pointsDebit, adminId, reason, cmdKey
        );

        assertThat(result.status()).isEqualTo("IDEMPOTENT_REPLAY");
        assertThat(result.actualDebited()).isEqualTo(0L);
        assertThat(result.absorbedByPlatform()).isEqualTo(5000L);

        // Verify strict lock acquisition ordering: command advisory lock first, then idempotency check; account row lock is NEVER acquired on idempotent replay
        InOrder inOrder = inOrder(jdbcTemplate);
        inOrder.verify(jdbcTemplate).query(contains("SELECT pg_advisory_xact_lock"), any(ResultSetExtractor.class), eq("admin-correction:" + cmdKey));
        inOrder.verify(jdbcTemplate).query(contains("WHERE command_key = ?"), any(RowMapper.class), eq(cmdKey));
        inOrder.verify(jdbcTemplate).queryForObject(contains("WHERE request_id = ? AND action = 'REWARD_CORRECTION_ABSORBED'"), eq(Integer.class), eq(cmdKey));
        verify(jdbcTemplate, never()).queryForObject(contains("FROM og_compat.reward_accounts WHERE user_id = ? FOR UPDATE"), eq(Long.class), eq(buyerId));

        // Zero insertions
        verify(jdbcTemplate, never()).update(contains("INSERT INTO og_compat.audit_logs"), any(), any(), any(), any(), any());
        verify(jdbcTemplate, never()).update(contains("INSERT INTO og_compat.reward_ledger"), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("OPEN-02B: Audit JSON serialization safely escapes quotes, backslashes, newlines, tabs, and Unicode, preserving exact reason upon read-back")
    void applyAdminCorrection_auditJsonSerialization_escapesSpecialCharactersAndPreservesExactReason() throws Exception {
        Long buyerId = 507L;
        Long adminId = 999L;
        Long pointsDebit = 10000L;
        // Complex reason with quotes, backslashes, newlines, tabs, and Unicode
        String complexReason = "Clawback for \"chargeback\" dispute:\nPath: C:\\Users\\Admin\\reports\tReason: Tiếng Việt có dấu & emojis 🪙";
        String cmdKey = "ADMIN_CORR:507:SPECIAL_CHARS";

        when(jdbcTemplate.query(contains("WHERE command_key = ?"), any(RowMapper.class), eq(cmdKey)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("WHERE request_id = ? AND action = 'REWARD_CORRECTION_ABSORBED'"), eq(Integer.class), eq(cmdKey)))
                .thenReturn(0);
        // Partial debit: balance = 4000 (< 10000 requested)
        when(jdbcTemplate.queryForObject(contains("FROM og_compat.reward_accounts WHERE user_id = ? FOR UPDATE"), eq(Long.class), eq(buyerId)))
                .thenReturn(4000L);

        // Capture audit JSON string passed to jdbcTemplate.update
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);

        RefundSettlementService.RewardCorrectionResult result = service.applyAdminCorrection(
                buyerId, pointsDebit, adminId, complexReason, cmdKey
        );

        assertThat(result.status()).isEqualTo("APPLIED");
        assertThat(result.actualDebited()).isEqualTo(4000L);
        assertThat(result.absorbedByPlatform()).isEqualTo(6000L);

        // Verify ledger was written atomically with debit = -4000
        verify(jdbcTemplate).update(contains("INSERT INTO og_compat.reward_ledger"),
                any(UUID.class), eq(buyerId), eq(-4000L), eq(adminId), contains("[UNRECOVERED_ABSORBED:6000]"), eq(cmdKey)
        );

        // Verify audit_logs was written with captured JSON payload
        verify(jdbcTemplate).update(contains("INSERT INTO og_compat.audit_logs"),
                eq(adminId), eq(buyerId), jsonCaptor.capture(), eq(cmdKey), any(Timestamp.class)
        );

        String capturedJson = jsonCaptor.getValue();
        assertThat(capturedJson).isNotBlank();

        // 1. Verify captured JSON is strictly valid JSON by parsing with Jackson ObjectMapper
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(capturedJson);

        // 2. Verify all 8 fields are present and structured correctly
        assertThat(root.get("adminId").asLong()).isEqualTo(adminId);
        assertThat(root.get("buyerId").asLong()).isEqualTo(buyerId);
        assertThat(root.get("requestedDebit").asLong()).isEqualTo(pointsDebit);
        assertThat(root.get("actualDebited").asLong()).isEqualTo(4000L);
        assertThat(root.get("absorbedByPlatform").asLong()).isEqualTo(6000L);
        assertThat(root.get("commandKey").asText()).isEqualTo(cmdKey);
        assertThat(root.get("timestamp").asText()).isEqualTo(now.toString());

        // 3. Verify exact reason round-trip: quotes, backslashes, newlines, tabs, and Unicode preserved bit-for-bit
        assertThat(root.get("reason").asText()).isEqualTo(complexReason.trim());
    }

    @Test
    @DisplayName("OPEN-02A: Pending Coin aggregation computes sum of per-operation floored coin amounts and excludes current operation exactly once")
    void confirmRefundSettlement_pendingCoinAggregation_sumsPerOperationFlooredAmounts() {
        UUID currentOp = UUID.randomUUID();
        Long orderId = 215L;
        Long buyerId = 315L;
        BigDecimal debitedCoins = new BigDecimal("50000");
        BigDecimal subtotal = new BigDecimal("500000");

        mockLookup(currentOp, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                currentOp, UUID.randomUUID(), "REFUND_BUYER", new BigDecimal("200000"),
                "RETURN", 108L, buyerId, "REQUESTED", "CMD_OP_PENDING_SUM"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                108L, "FULL_GOODS_REFUND", true, 508L, orderId, buyerId, debitedCoins, subtotal
        );
        mockContext(ctx, currentOp);

        // Cumulative active refundable value across all active rounds = 500,000 VND (currentOp 200,000 + pending 1 150,000 + pending 2 150,000)
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(so.amount), 0)"), eq(BigDecimal.class), eq(orderId)))
                .thenReturn(new BigDecimal("500000"));
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(points_delta), 0)"), eq(Long.class), eq(orderId)))
                .thenReturn(0L);

        // Two distinct pending operations on this order in REQUESTED/PROCESSING (excluding currentOp)
        // Op 1: 150,000 VND -> floor(150,000 * 50,000 / 500,000) = 15,000 coins
        // Op 2: 150,000 VND -> floor(150,000 * 50,000 / 500,000) = 15,000 coins
        // Total pending reserved = 15,000 + 15,000 = 30,000 coins
        when(jdbcTemplate.query(contains("AND so.state IN ('REQUESTED', 'PROCESSING')"), any(RowMapper.class), eq(orderId), eq(currentOp)))
                .thenReturn(List.of(new BigDecimal("150000"), new BigDecimal("150000")));

        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_accounts"), eq(Integer.class), eq(buyerId)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_ledger WHERE command_key = ?"), eq(Integer.class), anyString()))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("WHERE c.order_id = ? AND so.state <> 'CONFIRMED'"), eq(Integer.class), eq(orderId)))
                .thenReturn(1);

        RefundSettlementService.RefundSettlementResult result = service.confirmRefundSettlement(currentOp);

        // Cumulative entitlement = 50,000
        // Confirmed = 0
        // Pending = 15,000 + 15,000 = 30,000
        // Incremental = 50,000 - 0 - 30,000 = 20,000 coins (matches floor(200,000 * 50,000 / 500,000) exactly)
        assertThat(result.coinsCredited()).isEqualTo(20000L);
    }

    @Test
    @DisplayName("OPEN-02B: Rejects invalid inputs (null IDs, negative points, short reason, blank commandKey)")
    void applyAdminCorrection_rejectsInvalidInputs() {
        assertThatThrownBy(() -> service.applyAdminCorrection(null, 1000L, 999L, "Valid reason", "CMD1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("buyerId");

        assertThatThrownBy(() -> service.applyAdminCorrection(505L, 1000L, null, "Valid reason", "CMD1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("adminId");

        assertThatThrownBy(() -> service.applyAdminCorrection(505L, -500L, 999L, "Valid reason", "CMD1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pointsDebit");

        assertThatThrownBy(() -> service.applyAdminCorrection(505L, 1000L, 999L, "abc", "CMD1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least 5 characters");

        assertThatThrownBy(() -> service.applyAdminCorrection(505L, 1000L, 999L, "Valid reason", "   "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("commandKey");
    }

    @Test
    @DisplayName("Throws NoSuchElementException when settlement operation does not exist")
    void confirmRefundSettlement_throwsWhenNotFound() {
        UUID opId = UUID.randomUUID();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.confirmRefundSettlement(opId))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Settlement operation not found");
    }

    // =========================================================================
    // PO-DEC-02E (PO-DEC-08): 100% Coin Full Goods Refund via RETURN_PLATFORM
    // =========================================================================

    @Test
    @DisplayName("PO-DEC-02E: Qualifying 100% Coin FULL_GOODS_REFUND via confirmed RETURN_PLATFORM credits coins and applies D-10 expiry")
    void confirmRefundSettlement_100PercentCoin_fullGoodsRefund_viaReturnPlatform_creditsCoinsAndAppliesD10Expiry() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 701L;
        Long orderId = 801L;
        Long buyerId = 901L;

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        // Op is RETURN_PLATFORM
        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("50000"),
                "RETURN", decisionId, null, "REQUESTED", "DISBURSE:DECISION:701:PLATFORM_GOODS_SUBSIDY"
        );
        mockOpRecord(opRecord);

        // 100% Coin order: subtotal = 50,000, voucher = 0, pointsDiscount = 50,000 -> goodsCash = 0
        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 601L, orderId, buyerId,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000")
        );
        mockContext(ctx, opId);

        // confirmedCoin in reward_ledger = 0
        when(jdbcTemplate.queryForObject(contains("WHERE order_id = ? AND entry_type = 'CREDIT_REFUND'"), eq(Long.class), eq(orderId)))
                .thenReturn(0L);

        // User row lock and reward_accounts
        when(jdbcTemplate.queryForObject(contains("SELECT user_id FROM public.users"), eq(Long.class), eq(buyerId)))
                .thenReturn(buyerId);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_accounts"), eq(Integer.class), eq(buyerId)))
                .thenReturn(1);

        // Idempotency check: not yet recorded
        String expectedCommandKey = "REFUND:COIN:701:" + opId;
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_ledger WHERE command_key = ?"), eq(Integer.class), eq(expectedCommandKey)))
                .thenReturn(0);

        when(jdbcTemplate.queryForObject(contains("WHERE c.order_id = ? AND so.state <> 'CONFIRMED'"), eq(Integer.class), eq(orderId)))
                .thenReturn(0);

        RefundSettlementService.RefundSettlementResult result = service.confirmRefundSettlement(opId);

        assertThat(result.state()).isEqualTo("CONFIRMED");
        assertThat(result.coinsCredited()).isEqualTo(50000L);
        assertThat(result.expiresAt()).isNotNull();

        // Verify credit insert
        verify(jdbcTemplate).update(
                contains("INSERT INTO og_compat.reward_ledger"),
                any(UUID.class), eq(buyerId), eq(50000L), eq(orderId),
                eq("REFUND:DECISION:701"), eq(expectedCommandKey), any(Timestamp.class)
        );
    }

    @Test
    @DisplayName("PO-DEC-02E: Non-qualifying RETURN_PLATFORM on mixed Cash+Coin order does NOT credit coins")
    void confirmRefundSettlement_mixedCashOrder_returnPlatform_doesNotCreditCoin() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 702L;
        Long orderId = 802L;
        Long buyerId = 902L;

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        // Op is RETURN_PLATFORM on subsidy
        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("40000"),
                "RETURN", decisionId, null, "REQUESTED", "DISBURSE:DECISION:702:PLATFORM_GOODS_SUBSIDY"
        );
        mockOpRecord(opRecord);

        // Mixed order: subtotal = 100,000, pointsDiscount = 40,000 -> goodsCash = 60,000 > 0 (NOT 100% Coin)
        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 602L, orderId, buyerId,
                new BigDecimal("40000"), BigDecimal.ZERO, new BigDecimal("100000")
        );
        mockContext(ctx, opId);

        when(jdbcTemplate.queryForObject(contains("WHERE c.order_id = ? AND so.state <> 'CONFIRMED'"), eq(Integer.class), eq(orderId)))
                .thenReturn(1);

        RefundSettlementService.RefundSettlementResult result = service.confirmRefundSettlement(opId);

        assertThat(result.state()).isEqualTo("CONFIRMED");
        assertThat(result.coinsCredited()).isEqualTo(0L);
        verify(jdbcTemplate, never()).update(contains("INSERT INTO og_compat.reward_ledger"), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("PO-DEC-02E: 100% Coin order with EXEMPT_GOODS_REFUND credits zero coins per PO-DEC-02 Rule 4")
    void confirmRefundSettlement_100PercentCoin_exemptOutcome_creditsZeroCoins() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 703L;
        Long orderId = 803L;
        Long buyerId = 903L;

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RELEASE_SELLER", new BigDecimal("50000"),
                "EXEMPTION", decisionId, 999L, "REQUESTED", "DISBURSE:DECISION:703:EXEMPTION"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "EXEMPT_GOODS_REFUND", true, 603L, orderId, buyerId,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000")
        );
        mockContext(ctx, opId);

        when(jdbcTemplate.queryForObject(contains("WHERE c.order_id = ? AND so.state <> 'CONFIRMED'"), eq(Integer.class), eq(orderId)))
                .thenReturn(0);

        RefundSettlementService.RefundSettlementResult result = service.confirmRefundSettlement(opId);

        assertThat(result.state()).isEqualTo("CONFIRMED");
        assertThat(result.coinsCredited()).isEqualTo(0L);
        verify(jdbcTemplate, never()).update(contains("INSERT INTO og_compat.reward_ledger"), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("PO-DEC-02E: 100% Coin order with partial refund outcome fails closed (throws UnsupportedOperationException)")
    void confirmRefundSettlement_100PercentCoin_partialOutcome_failsClosed() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 704L;
        Long orderId = 804L;
        Long buyerId = 904L;

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("25000"),
                "RETURN", decisionId, null, "REQUESTED", "DISBURSE:DECISION:704:PARTIAL"
        );
        mockOpRecord(opRecord);

        // 100% Coin order with partial decision
        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "PARTIAL_GOODS_REFUND", true, 604L, orderId, buyerId,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000")
        );
        mockContext(ctx, opId);

        assertThatThrownBy(() -> service.confirmRefundSettlement(opId))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Partial refund for 100% Coin orders is unsupported");
    }

    @Test
    @DisplayName("PO-DEC-02E: Missing DEBIT_CHECKOUT ledger entry fails closed (throws IllegalStateException)")
    void confirmRefundSettlement_100PercentCoin_missingDebitCheckout_failsClosed() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 705L;
        Long orderId = 805L;
        Long buyerId = 905L;

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("50000"),
                "RETURN", decisionId, null, "REQUESTED", "DISBURSE:DECISION:705"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 605L, orderId, buyerId,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000")
        );
        mockContext(ctx, opId);

        // Missing DEBIT_CHECKOUT (empty list)
        UUID groupId = UUID.nameUUIDFromBytes(("group-for-order-" + orderId).getBytes());
        when(jdbcTemplate.query(contains("WHERE rl.group_id = ? AND rl.entry_type = 'DEBIT_CHECKOUT'"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.confirmRefundSettlement(opId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Canonical DEBIT_CHECKOUT ledger entry missing for group " + groupId);
    }

    @Test
    @DisplayName("PO-DEC-02E: Inconsistent DEBIT_CHECKOUT vs order points discount fails closed")
    void confirmRefundSettlement_100PercentCoin_inconsistentDebitCheckout_failsClosed() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 706L;
        Long orderId = 806L;
        Long buyerId = 906L;

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("50000"),
                "RETURN", decisionId, null, "REQUESTED", "DISBURSE:DECISION:706"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 606L, orderId, buyerId,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000")
        );
        mockContext(ctx, opId);

        // Inconsistent debit (30,000 instead of 50,000)
        UUID groupId = UUID.nameUUIDFromBytes(("group-for-order-" + orderId).getBytes());
        when(jdbcTemplate.query(contains("WHERE rl.group_id = ? AND rl.entry_type = 'DEBIT_CHECKOUT'"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new RefundSettlementService.GroupDebitRecord(UUID.randomUUID(), buyerId, -30000L, 1L, BigDecimal.ONE)));

        assertThatThrownBy(() -> service.confirmRefundSettlement(opId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Inconsistent Group DEBIT_CHECKOUT");
    }

    @Test
    @DisplayName("PO-DEC-02E: Previously confirmed coins are subtracted correctly (caps increment)")
    void confirmRefundSettlement_100PercentCoin_previouslyConfirmedCoins_capsIncrement() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 707L;
        Long orderId = 807L;
        Long buyerId = 907L;

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("50000"),
                "RETURN", decisionId, null, "REQUESTED", "DISBURSE:DECISION:707"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 607L, orderId, buyerId,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000")
        );
        mockContext(ctx, opId);

        // Already confirmed = 30,000
        when(jdbcTemplate.queryForObject(contains("WHERE order_id = ? AND entry_type = 'CREDIT_REFUND'"), eq(Long.class), eq(orderId)))
                .thenReturn(30000L);

        when(jdbcTemplate.queryForObject(contains("SELECT user_id FROM public.users"), eq(Long.class), eq(buyerId)))
                .thenReturn(buyerId);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_accounts"), eq(Integer.class), eq(buyerId)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_ledger WHERE command_key = ?"), eq(Integer.class), anyString()))
                .thenReturn(0);

        RefundSettlementService.RefundSettlementResult result = service.confirmRefundSettlement(opId);

        // Incremental = 50,000 - 30,000 = 20,000
        assertThat(result.coinsCredited()).isEqualTo(20000L);
    }

    @Test
    @DisplayName("PO-DEC-02E: Idempotent replay skips duplicate credit when command key exists")
    void confirmRefundSettlement_100PercentCoin_idempotentReplay_skipsDuplicateCredit() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 708L;
        Long orderId = 808L;
        Long buyerId = 908L;

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("50000"),
                "RETURN", decisionId, null, "REQUESTED", "DISBURSE:DECISION:708"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 608L, orderId, buyerId,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000")
        );
        mockContext(ctx, opId);

        when(jdbcTemplate.queryForObject(contains("WHERE order_id = ? AND entry_type = 'CREDIT_REFUND'"), eq(Long.class), eq(orderId)))
                .thenReturn(0L);

        when(jdbcTemplate.queryForObject(contains("SELECT user_id FROM public.users"), eq(Long.class), eq(buyerId)))
                .thenReturn(buyerId);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_accounts"), eq(Integer.class), eq(buyerId)))
                .thenReturn(1);

        // Command key ALREADY exists in reward_ledger
        String expectedCommandKey = "REFUND:COIN:708:" + opId;
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_ledger WHERE command_key = ?"), eq(Integer.class), eq(expectedCommandKey)))
                .thenReturn(1);

        RefundSettlementService.RefundSettlementResult result = service.confirmRefundSettlement(opId);

        // 0 coins credited due to idempotent replay
        assertThat(result.coinsCredited()).isEqualTo(0L);
        verify(jdbcTemplate, never()).update(contains("INSERT INTO og_compat.reward_ledger"), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("PO-DEC-02E: Reward ledger insert failure propagates exception to trigger rollback")
    void confirmRefundSettlement_100PercentCoin_ledgerInsertFailure_propagatesException() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 709L;
        Long orderId = 809L;
        Long buyerId = 909L;

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("50000"),
                "RETURN", decisionId, null, "REQUESTED", "DISBURSE:DECISION:709"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 609L, orderId, buyerId,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000")
        );
        mockContext(ctx, opId);

        when(jdbcTemplate.queryForObject(contains("WHERE order_id = ? AND entry_type = 'CREDIT_REFUND'"), eq(Long.class), eq(orderId)))
                .thenReturn(0L);

        when(jdbcTemplate.queryForObject(contains("SELECT user_id FROM public.users"), eq(Long.class), eq(buyerId)))
                .thenReturn(buyerId);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_accounts"), eq(Integer.class), eq(buyerId)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_ledger WHERE command_key = ?"), eq(Integer.class), anyString()))
                .thenReturn(0);

        // Induce exception on ledger insert
        when(jdbcTemplate.update(contains("INSERT INTO og_compat.reward_ledger"), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Simulated ledger constraint violation"));

        assertThatThrownBy(() -> service.confirmRefundSettlement(opId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                .hasMessageContaining("Simulated ledger constraint violation");
    }

    @Test
    @DisplayName("PO-DEC-02E / CRIT-1: Duplicate DEBIT_CHECKOUT ledger entry fails closed (throws IllegalStateException)")
    void confirmRefundSettlement_100PercentCoin_duplicateDebitCheckout_failsClosed() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 710L;
        Long orderId = 810L;
        Long buyerId = 910L;

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("50000"),
                "RETURN", decisionId, null, "REQUESTED", "DISBURSE:DECISION:710"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 610L, orderId, buyerId,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000")
        );
        mockContext(ctx, opId);

        // Multiple DEBIT_CHECKOUT entries in ledger (duplicate anomaly)
        UUID groupId = UUID.nameUUIDFromBytes(("group-for-order-" + orderId).getBytes());
        when(jdbcTemplate.query(contains("WHERE rl.group_id = ? AND rl.entry_type = 'DEBIT_CHECKOUT'"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(
                        new RefundSettlementService.GroupDebitRecord(UUID.randomUUID(), buyerId, -50000L, 1L, BigDecimal.ONE),
                        new RefundSettlementService.GroupDebitRecord(UUID.randomUUID(), buyerId, -50000L, 1L, BigDecimal.ONE)
                ));

        assertThatThrownBy(() -> service.confirmRefundSettlement(opId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple DEBIT_CHECKOUT ledger entries found for group " + groupId);
    }

    @Test
    @DisplayName("PO-DEC-02E / CRIT-1: Malformed non-negative DEBIT_CHECKOUT delta fails closed (throws IllegalStateException)")
    void confirmRefundSettlement_100PercentCoin_malformedPositiveDebitCheckoutDelta_failsClosed() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 711L;
        Long orderId = 811L;
        Long buyerId = 911L;

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("50000"),
                "RETURN", decisionId, null, "REQUESTED", "DISBURSE:DECISION:711"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 611L, orderId, buyerId,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000")
        );
        mockContext(ctx, opId);

        // Malformed non-negative (positive) delta in ledger
        UUID groupId = UUID.nameUUIDFromBytes(("group-for-order-" + orderId).getBytes());
        when(jdbcTemplate.query(contains("WHERE rl.group_id = ? AND rl.entry_type = 'DEBIT_CHECKOUT'"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new RefundSettlementService.GroupDebitRecord(UUID.randomUUID(), buyerId, 50000L, 1L, BigDecimal.ONE)));

        assertThatThrownBy(() -> service.confirmRefundSettlement(opId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Malformed non-negative DEBIT_CHECKOUT (points_delta=50000) for group " + groupId);
    }

    @Test
    @DisplayName("PO-DEC-02D / CRIT-7: applyAdminCorrection with unauthenticated caller throws AccessDeniedException")
    void applyAdminCorrection_unauthenticatedCaller_throwsAccessDeniedException() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> service.applyAdminCorrection(501L, 5000L, 999L, "Valid reason", "CMD:UNAUTH"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    @DisplayName("PO-DEC-02D / CRIT-7: applyAdminCorrection with non-admin caller throws AccessDeniedException")
    void applyAdminCorrection_nonAdminCaller_throwsAccessDeniedException() {
        TestingAuthenticationToken nonAdminAuth = new TestingAuthenticationToken(
                "999", "password", "ROLE_USER"
        );
        nonAdminAuth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(nonAdminAuth);

        assertThatThrownBy(() -> service.applyAdminCorrection(501L, 5000L, 999L, "Valid reason", "CMD:NON_ADMIN"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("does not have ADMIN role");
    }

    @Test
    @DisplayName("PO-DEC-02D / CRIT-7: applyAdminCorrection with spoofed adminId throws AccessDeniedException")
    void applyAdminCorrection_spoofedAdminId_throwsAccessDeniedException() {
        // Authenticated as Admin 999
        mockAdminAuthentication(999L);

        // Caller attempts to pass claimedAdminId 888 (spoofing a different Admin)
        assertThatThrownBy(() -> service.applyAdminCorrection(501L, 5000L, 888L, "Valid reason", "CMD:SPOOF"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("claimed adminId 888 does not match authenticated principal");
    }

    @Test
    @DisplayName("F-01 / CRIT-3: Multithreaded concurrent settlements on the same Order conserve order cap and verify Order lock")
    void confirmRefundSettlement_multithreadedConcurrentSettlements_sameOrder_orderLockAndCapConserved() throws Exception {
        UUID op1 = UUID.randomUUID();
        UUID op2 = UUID.randomUUID();
        Long orderId = 205L;
        Long buyerId = 305L;
        Long decisionId = 105L;

        mockLookup(op1, "REQUESTED", orderId);
        mockLookup(op2, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord1 = new RefundSettlementService.SettlementOpRecord(
                op1, UUID.randomUUID(), "REFUND_BUYER", new BigDecimal("450000"),
                "RETURN", decisionId, buyerId, "REQUESTED", "CMD_OP_CONC_1"
        );
        RefundSettlementService.SettlementOpRecord opRecord2 = new RefundSettlementService.SettlementOpRecord(
                op2, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("50000"),
                "RETURN", decisionId, null, "REQUESTED", "CMD_OP_CONC_2"
        );
        mockOpRecord(opRecord1);
        mockOpRecord(opRecord2);

        RefundSettlementService.DecisionOrderContext ctx1 = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 505L, orderId, buyerId, new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("500000")
        );
        RefundSettlementService.DecisionOrderContext ctx2 = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 505L, orderId, buyerId, new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("500000")
        );
        mockContext(ctx1, op1);
        mockContext(ctx2, op2);

        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(so.amount), 0)"), eq(BigDecimal.class), eq(orderId)))
                .thenReturn(new BigDecimal("450000"));
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(SUM(points_delta), 0)"), eq(Long.class), eq(orderId)))
                .thenReturn(0L);
        when(jdbcTemplate.query(contains("AND so.state IN ('REQUESTED', 'PROCESSING')"), any(RowMapper.class), eq(orderId), any(UUID.class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_accounts"), eq(Integer.class), eq(buyerId)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_ledger WHERE command_key = ?"), eq(Integer.class), anyString()))
                .thenReturn(0);

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);

        java.util.concurrent.Callable<RefundSettlementService.RefundSettlementResult> task1 = () -> {
            barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
            return service.confirmRefundSettlement(op1);
        };
        java.util.concurrent.Callable<RefundSettlementService.RefundSettlementResult> task2 = () -> {
            barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
            return service.confirmRefundSettlement(op2);
        };

        java.util.concurrent.Future<RefundSettlementService.RefundSettlementResult> f1 = executor.submit(task1);
        java.util.concurrent.Future<RefundSettlementService.RefundSettlementResult> f2 = executor.submit(task2);

        RefundSettlementService.RefundSettlementResult res1 = f1.get(10, java.util.concurrent.TimeUnit.SECONDS);
        RefundSettlementService.RefundSettlementResult res2 = f2.get(10, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(res1.state()).isEqualTo("CONFIRMED");
        assertThat(res2.state()).isEqualTo("CONFIRMED");
        assertThat(res1.coinsCredited() + res2.coinsCredited()).isLessThanOrEqualTo(50000L);

        // Verify Order lock was queried exclusively for each operation (F-01 deadlock protocol)
        verify(jdbcTemplate, atLeast(2)).query(contains("FROM og_compat.orders WHERE order_id = ? FOR UPDATE"), any(RowMapper.class), eq(orderId));
    }

    // =========================================================================
    // DEC-10.4 Canonical Group Debit & Conservation Invariant Unit Tests
    // =========================================================================

    @Test
    @DisplayName("DEC-10.4: Multi-order group (Order A=60k, Order B=40k, Order C=0k) correctly allocates cap to Order A")
    void confirmRefundSettlement_multiOrderGroup_orderAAllocatedCorrectCap() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 720L;
        Long orderIdA = 820L;
        Long buyerId = 920L;
        UUID groupId = UUID.randomUUID();

        mockLookup(opId, "REQUESTED", orderIdA);
        mockOrderLock(orderIdA);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("60000"),
                "RETURN", decisionId, null, "REQUESTED", "DISBURSE:DECISION:720"
        );
        mockOpRecord(opRecord);

        // Order A: 100% Coin, subtotal = 60,000, pointsDiscount = 60,000
        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 620L, orderIdA, buyerId,
                new BigDecimal("60000"), BigDecimal.ZERO, new BigDecimal("60000")
        );
        when(jdbcTemplate.query(contains("JOIN og_compat.orders o ON c.order_id = o.order_id"), any(RowMapper.class), eq(opId)))
                .thenReturn(List.of(ctx));

        // Group total = 100,000 (Order A: 60,000, Order B: 40,000, Order C: 0)
        RefundSettlementService.OrderGroupContext orderCtx = new RefundSettlementService.OrderGroupContext(
                groupId, buyerId, "PARALLEL_SPLIT", new BigDecimal("60000")
        );
        when(jdbcTemplate.query(contains("SELECT group_id, buyer_id, workflow_model, points_discount_amount"), any(RowMapper.class), eq(orderIdA)))
                .thenReturn(List.of(orderCtx));
        when(jdbcTemplate.query(contains("SELECT reward_policy_id"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(1L));
        when(jdbcTemplate.query(contains("WHERE rl.group_id = ? AND rl.entry_type = 'DEBIT_CHECKOUT'"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new RefundSettlementService.GroupDebitRecord(UUID.randomUUID(), buyerId, -100000L, 1L, BigDecimal.ONE)));
        when(jdbcTemplate.query(contains("SELECT points_discount_amount\nFROM og_compat.orders\nWHERE group_id = ?"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new BigDecimal("60000"), new BigDecimal("40000"), BigDecimal.ZERO));
        when(jdbcTemplate.query(contains("SELECT point_value"), any(RowMapper.class), eq(1L)))
                .thenReturn(List.of(BigDecimal.ONE));

        when(jdbcTemplate.queryForObject(contains("WHERE order_id = ? AND entry_type = 'CREDIT_REFUND'"), eq(Long.class), eq(orderIdA)))
                .thenReturn(0L);
        when(jdbcTemplate.queryForObject(contains("SELECT user_id FROM public.users"), eq(Long.class), eq(buyerId)))
                .thenReturn(buyerId);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_accounts"), eq(Integer.class), eq(buyerId)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_ledger WHERE command_key = ?"), eq(Integer.class), anyString()))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("WHERE c.order_id = ? AND so.state <> 'CONFIRMED'"), eq(Integer.class), eq(orderIdA)))
                .thenReturn(0);

        RefundSettlementService.RefundSettlementResult result = service.confirmRefundSettlement(opId);

        assertThat(result.state()).isEqualTo("CONFIRMED");
        assertThat(result.coinsCredited()).isEqualTo(60000L);
    }

    @Test
    @DisplayName("DEC-10.4: Multi-order group Order C with 0 snapshot returns 0 coins without error when group is consistent")
    void confirmRefundSettlement_multiOrderGroup_orderCZeroSnapshot_returnsZeroCoins() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 721L;
        Long orderIdC = 821L;
        Long buyerId = 921L;
        UUID groupId = UUID.randomUUID();

        mockLookup(opId, "REQUESTED", orderIdC);
        mockOrderLock(orderIdC);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "REFUND_BUYER", new BigDecimal("50000"),
                "RETURN", decisionId, buyerId, "REQUESTED", "DISBURSE:DECISION:721"
        );
        mockOpRecord(opRecord);

        // Order C: subtotal = 50,000, pointsDiscount = 0
        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 621L, orderIdC, buyerId,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("JOIN og_compat.orders o ON c.order_id = o.order_id"), any(RowMapper.class), eq(opId)))
                .thenReturn(List.of(ctx));

        // Group total = 100,000 (Order A: 60k, Order B: 40k, Order C: 0k)
        RefundSettlementService.OrderGroupContext orderCtx = new RefundSettlementService.OrderGroupContext(
                groupId, buyerId, "PARALLEL_SPLIT", BigDecimal.ZERO
        );
        when(jdbcTemplate.query(contains("SELECT group_id, buyer_id, workflow_model, points_discount_amount"), any(RowMapper.class), eq(orderIdC)))
                .thenReturn(List.of(orderCtx));
        when(jdbcTemplate.query(contains("SELECT reward_policy_id"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(1L));
        when(jdbcTemplate.query(contains("WHERE rl.group_id = ? AND rl.entry_type = 'DEBIT_CHECKOUT'"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new RefundSettlementService.GroupDebitRecord(UUID.randomUUID(), buyerId, -100000L, 1L, BigDecimal.ONE)));
        when(jdbcTemplate.query(contains("SELECT points_discount_amount\nFROM og_compat.orders\nWHERE group_id = ?"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new BigDecimal("60000"), new BigDecimal("40000"), BigDecimal.ZERO));
        when(jdbcTemplate.query(contains("SELECT point_value"), any(RowMapper.class), eq(1L)))
                .thenReturn(List.of(BigDecimal.ONE));

        when(jdbcTemplate.queryForObject(contains("WHERE c.order_id = ? AND so.state <> 'CONFIRMED'"), eq(Integer.class), eq(orderIdC)))
                .thenReturn(0);

        RefundSettlementService.RefundSettlementResult result = service.confirmRefundSettlement(opId);

        assertThat(result.state()).isEqualTo("CONFIRMED");
        assertThat(result.coinsCredited()).isEqualTo(0L);
    }

    @Test
    @DisplayName("DEC-10.4: Order C with 0 snapshot fails closed if group has missing or inconsistent debit")
    void confirmRefundSettlement_multiOrderGroup_orderCZeroSnapshot_inconsistentGroup_failsClosed() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 722L;
        Long orderIdC = 822L;
        Long buyerId = 922L;
        UUID groupId = UUID.randomUUID();

        mockLookup(opId, "REQUESTED", orderIdC);
        mockOrderLock(orderIdC);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "REFUND_BUYER", new BigDecimal("50000"),
                "RETURN", decisionId, buyerId, "REQUESTED", "DISBURSE:DECISION:722"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 622L, orderIdC, buyerId,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("JOIN og_compat.orders o ON c.order_id = o.order_id"), any(RowMapper.class), eq(opId)))
                .thenReturn(List.of(ctx));

        // Group total = 100k, but group debit missing
        RefundSettlementService.OrderGroupContext orderCtx = new RefundSettlementService.OrderGroupContext(
                groupId, buyerId, "PARALLEL_SPLIT", BigDecimal.ZERO
        );
        when(jdbcTemplate.query(contains("SELECT group_id, buyer_id, workflow_model, points_discount_amount"), any(RowMapper.class), eq(orderIdC)))
                .thenReturn(List.of(orderCtx));
        when(jdbcTemplate.query(contains("SELECT reward_policy_id"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(1L));
        when(jdbcTemplate.query(contains("WHERE rl.group_id = ? AND rl.entry_type = 'DEBIT_CHECKOUT'"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of());
        when(jdbcTemplate.query(contains("SELECT points_discount_amount\nFROM og_compat.orders\nWHERE group_id = ?"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new BigDecimal("60000"), new BigDecimal("40000")));

        assertThatThrownBy(() -> service.confirmRefundSettlement(opId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Canonical DEBIT_CHECKOUT ledger entry missing for group");
    }

    @Test
    @DisplayName("DEC-10.4: Group with total snapshot = 0 and anomalous debit fails closed")
    void confirmRefundSettlement_groupTotalZero_withAnomalousDebit_failsClosed() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 723L;
        Long orderId = 823L;
        Long buyerId = 923L;
        UUID groupId = UUID.randomUUID();

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "REFUND_BUYER", new BigDecimal("50000"),
                "RETURN", decisionId, buyerId, "REQUESTED", "DISBURSE:DECISION:723"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 623L, orderId, buyerId,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("JOIN og_compat.orders o ON c.order_id = o.order_id"), any(RowMapper.class), eq(opId)))
                .thenReturn(List.of(ctx));

        RefundSettlementService.OrderGroupContext orderCtx = new RefundSettlementService.OrderGroupContext(
                groupId, buyerId, "PARALLEL_SPLIT", BigDecimal.ZERO
        );
        when(jdbcTemplate.query(contains("SELECT group_id, buyer_id, workflow_model, points_discount_amount"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of(orderCtx));
        when(jdbcTemplate.query(contains("SELECT reward_policy_id"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(1L));
        // Anomalous debit exists even though group snapshot is 0
        when(jdbcTemplate.query(contains("WHERE rl.group_id = ? AND rl.entry_type = 'DEBIT_CHECKOUT'"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new RefundSettlementService.GroupDebitRecord(UUID.randomUUID(), buyerId, -50000L, 1L, BigDecimal.ONE)));
        when(jdbcTemplate.query(contains("SELECT points_discount_amount\nFROM og_compat.orders\nWHERE group_id = ?"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(BigDecimal.ZERO));

        assertThatThrownBy(() -> service.confirmRefundSettlement(opId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Anomalous DEBIT_CHECKOUT found for group");
    }

    @Test
    @DisplayName("DEC-10.4: Buyer mismatch between Order and Group DEBIT_CHECKOUT fails closed")
    void confirmRefundSettlement_buyerMismatch_failsClosed() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 724L;
        Long orderId = 824L;
        Long orderBuyerId = 924L;
        Long debitUserId = 999L; // Different user!
        UUID groupId = UUID.randomUUID();

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("50000"),
                "RETURN", decisionId, null, "REQUESTED", "DISBURSE:DECISION:724"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 624L, orderId, orderBuyerId,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("JOIN og_compat.orders o ON c.order_id = o.order_id"), any(RowMapper.class), eq(opId)))
                .thenReturn(List.of(ctx));

        RefundSettlementService.OrderGroupContext orderCtx = new RefundSettlementService.OrderGroupContext(
                groupId, orderBuyerId, "PARALLEL_SPLIT", new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("SELECT group_id, buyer_id, workflow_model, points_discount_amount"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of(orderCtx));
        when(jdbcTemplate.query(contains("SELECT reward_policy_id"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(1L));
        when(jdbcTemplate.query(contains("WHERE rl.group_id = ? AND rl.entry_type = 'DEBIT_CHECKOUT'"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new RefundSettlementService.GroupDebitRecord(UUID.randomUUID(), debitUserId, -50000L, 1L, BigDecimal.ONE)));
        when(jdbcTemplate.query(contains("SELECT points_discount_amount\nFROM og_compat.orders\nWHERE group_id = ?"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new BigDecimal("50000")));

        assertThatThrownBy(() -> service.confirmRefundSettlement(opId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Buyer identity mismatch");
    }

    @Test
    @DisplayName("DEC-10.4: Legacy V14 order with 0 coins returns 0 coins without error")
    void confirmRefundSettlement_legacyV14_zeroCoins_returnsZeroCoins() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 725L;
        Long orderId = 825L;
        Long buyerId = 925L;

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "REFUND_BUYER", new BigDecimal("50000"),
                "RETURN", decisionId, buyerId, "REQUESTED", "DISBURSE:DECISION:725"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 625L, orderId, buyerId,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("JOIN og_compat.orders o ON c.order_id = o.order_id"), any(RowMapper.class), eq(opId)))
                .thenReturn(List.of(ctx));

        // Legacy V14: groupId = null, workflowModel = "LEGACY_V14"
        RefundSettlementService.OrderGroupContext orderCtx = new RefundSettlementService.OrderGroupContext(
                null, buyerId, "LEGACY_V14", BigDecimal.ZERO
        );
        when(jdbcTemplate.query(contains("SELECT group_id, buyer_id, workflow_model, points_discount_amount"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of(orderCtx));
        when(jdbcTemplate.queryForObject(contains("WHERE c.order_id = ? AND so.state <> 'CONFIRMED'"), eq(Integer.class), eq(orderId)))
                .thenReturn(0);

        RefundSettlementService.RefundSettlementResult result = service.confirmRefundSettlement(opId);

        assertThat(result.state()).isEqualTo("CONFIRMED");
        assertThat(result.coinsCredited()).isEqualTo(0L);
    }

    @Test
    @DisplayName("DEC-10.4: Legacy V14 order with positive points discount fails closed")
    void confirmRefundSettlement_legacyV14_positiveCoins_failsClosed() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 726L;
        Long orderId = 826L;
        Long buyerId = 926L;

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("50000"),
                "RETURN", decisionId, null, "REQUESTED", "DISBURSE:DECISION:726"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 626L, orderId, buyerId,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("JOIN og_compat.orders o ON c.order_id = o.order_id"), any(RowMapper.class), eq(opId)))
                .thenReturn(List.of(ctx));

        // Legacy V14 with positive points discount
        RefundSettlementService.OrderGroupContext orderCtx = new RefundSettlementService.OrderGroupContext(
                null, buyerId, "LEGACY_V14", new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("SELECT group_id, buyer_id, workflow_model, points_discount_amount"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of(orderCtx));

        assertThatThrownBy(() -> service.confirmRefundSettlement(opId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Legacy V14 order " + orderId + " has positive points discount");
    }

    @Test
    @DisplayName("DEC-10.4: Converts monetary discount to coins correctly when point_value is not 1.00")
    void confirmRefundSettlement_nonStandardPointValue_convertsCorrectly() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 727L;
        Long orderId = 827L;
        Long buyerId = 927L;
        UUID groupId = UUID.randomUUID();

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("50000"),
                "RETURN", decisionId, null, "REQUESTED", "DISBURSE:DECISION:727"
        );
        mockOpRecord(opRecord);

        // 100% Coin: subtotal = 50,000, pointsDiscount = 50,000
        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 627L, orderId, buyerId,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("JOIN og_compat.orders o ON c.order_id = o.order_id"), any(RowMapper.class), eq(opId)))
                .thenReturn(List.of(ctx));

        // Policy point_value = 2.00 VND per coin -> 50,000 VND / 2.00 = 25,000 coins debited at checkout
        RefundSettlementService.OrderGroupContext orderCtx = new RefundSettlementService.OrderGroupContext(
                groupId, buyerId, "PARALLEL_SPLIT", new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("SELECT group_id, buyer_id, workflow_model, points_discount_amount"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of(orderCtx));
        when(jdbcTemplate.query(contains("SELECT reward_policy_id"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(1L));
        when(jdbcTemplate.query(contains("WHERE rl.group_id = ? AND rl.entry_type = 'DEBIT_CHECKOUT'"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new RefundSettlementService.GroupDebitRecord(UUID.randomUUID(), buyerId, -25000L, 1L, new BigDecimal("2.00"))));
        when(jdbcTemplate.query(contains("SELECT points_discount_amount\nFROM og_compat.orders\nWHERE group_id = ?"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new BigDecimal("50000")));
        when(jdbcTemplate.query(contains("SELECT point_value"), any(RowMapper.class), eq(1L)))
                .thenReturn(List.of(new BigDecimal("2.00")));

        when(jdbcTemplate.queryForObject(contains("WHERE order_id = ? AND entry_type = 'CREDIT_REFUND'"), eq(Long.class), eq(orderId)))
                .thenReturn(0L);
        when(jdbcTemplate.queryForObject(contains("SELECT user_id FROM public.users"), eq(Long.class), eq(buyerId)))
                .thenReturn(buyerId);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_accounts"), eq(Integer.class), eq(buyerId)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_ledger WHERE command_key = ?"), eq(Integer.class), anyString()))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("WHERE c.order_id = ? AND so.state <> 'CONFIRMED'"), eq(Integer.class), eq(orderId)))
                .thenReturn(0);

        RefundSettlementService.RefundSettlementResult result = service.confirmRefundSettlement(opId);

        assertThat(result.state()).isEqualTo("CONFIRMED");
        // 50,000 / 2.00 = 25,000 coins credited!
        assertThat(result.coinsCredited()).isEqualTo(25000L);
    }

    // =========================================================================
    // P0: Buyer ID Validation Unit Tests
    // =========================================================================

    @Test
    @DisplayName("P0 - Buyer ID: Order buyer_id is null fails closed")
    void confirmRefundSettlement_orderBuyerIdNull_failsClosed() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 730L;
        Long orderId = 830L;
        UUID groupId = UUID.randomUUID();

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("50000"),
                "RETURN", decisionId, null, "REQUESTED", "DISBURSE:DECISION:730"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 630L, orderId, null,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("JOIN og_compat.orders o ON c.order_id = o.order_id"), any(RowMapper.class), eq(opId)))
                .thenReturn(List.of(ctx));

        // Order buyer_id is null
        RefundSettlementService.OrderGroupContext orderCtx = new RefundSettlementService.OrderGroupContext(
                groupId, null, "PARALLEL_SPLIT", new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("SELECT group_id, buyer_id, workflow_model, points_discount_amount"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of(orderCtx));

        assertThatThrownBy(() -> service.confirmRefundSettlement(opId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Order buyer_id must not be null");
    }

    @Test
    @DisplayName("P0 - Buyer ID: Group DEBIT_CHECKOUT user_id is null fails closed")
    void confirmRefundSettlement_groupDebitUserIdNull_failsClosed() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 731L;
        Long orderId = 831L;
        Long buyerId = 931L;
        UUID groupId = UUID.randomUUID();

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("50000"),
                "RETURN", decisionId, null, "REQUESTED", "DISBURSE:DECISION:731"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 631L, orderId, buyerId,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("JOIN og_compat.orders o ON c.order_id = o.order_id"), any(RowMapper.class), eq(opId)))
                .thenReturn(List.of(ctx));

        RefundSettlementService.OrderGroupContext orderCtx = new RefundSettlementService.OrderGroupContext(
                groupId, buyerId, "PARALLEL_SPLIT", new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("SELECT group_id, buyer_id, workflow_model, points_discount_amount"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of(orderCtx));
        when(jdbcTemplate.query(contains("SELECT reward_policy_id"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(1L));

        // Group debit user_id is null
        when(jdbcTemplate.query(contains("WHERE rl.group_id = ? AND rl.entry_type = 'DEBIT_CHECKOUT'"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new RefundSettlementService.GroupDebitRecord(UUID.randomUUID(), null, -50000L, 1L, BigDecimal.ONE)));
        when(jdbcTemplate.query(contains("SELECT points_discount_amount\nFROM og_compat.orders\nWHERE group_id = ?"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new BigDecimal("50000")));

        assertThatThrownBy(() -> service.confirmRefundSettlement(opId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user_id is null for group");
    }

    @Test
    @DisplayName("P0 - Buyer ID: Matched buyer_id succeeds cleanly")
    void confirmRefundSettlement_buyerIdMatch_succeeds() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 732L;
        Long orderId = 832L;
        Long buyerId = 932L;
        UUID groupId = UUID.randomUUID();

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("50000"),
                "RETURN", decisionId, null, "REQUESTED", "DISBURSE:DECISION:732"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 632L, orderId, buyerId,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("JOIN og_compat.orders o ON c.order_id = o.order_id"), any(RowMapper.class), eq(opId)))
                .thenReturn(List.of(ctx));

        RefundSettlementService.OrderGroupContext orderCtx = new RefundSettlementService.OrderGroupContext(
                groupId, buyerId, "PARALLEL_SPLIT", new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("SELECT group_id, buyer_id, workflow_model, points_discount_amount"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of(orderCtx));
        when(jdbcTemplate.query(contains("SELECT reward_policy_id"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(1L));

        // Group debit user_id exactly matches buyerId
        when(jdbcTemplate.query(contains("WHERE rl.group_id = ? AND rl.entry_type = 'DEBIT_CHECKOUT'"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new RefundSettlementService.GroupDebitRecord(UUID.randomUUID(), buyerId, -50000L, 1L, BigDecimal.ONE)));
        when(jdbcTemplate.query(contains("SELECT points_discount_amount\nFROM og_compat.orders\nWHERE group_id = ?"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new BigDecimal("50000")));
        when(jdbcTemplate.query(contains("SELECT point_value"), any(RowMapper.class), eq(1L)))
                .thenReturn(List.of(BigDecimal.ONE));

        when(jdbcTemplate.queryForObject(contains("WHERE order_id = ? AND entry_type = 'CREDIT_REFUND'"), eq(Long.class), eq(orderId)))
                .thenReturn(0L);
        when(jdbcTemplate.queryForObject(contains("SELECT user_id FROM public.users"), eq(Long.class), eq(buyerId)))
                .thenReturn(buyerId);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_accounts"), eq(Integer.class), eq(buyerId)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_ledger WHERE command_key = ?"), eq(Integer.class), anyString()))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("WHERE c.order_id = ? AND so.state <> 'CONFIRMED'"), eq(Integer.class), eq(orderId)))
                .thenReturn(0);

        RefundSettlementService.RefundSettlementResult result = service.confirmRefundSettlement(opId);
        assertThat(result.state()).isEqualTo("CONFIRMED");
        assertThat(result.coinsCredited()).isEqualTo(50000L);
    }

    // =========================================================================
    // P0: Reward Policy Pinning & Fallback Elimination Unit Tests
    // =========================================================================

    @Test
    @DisplayName("P0 - Policy Pinning: Missing pinned reward_policy_id on checkout group fails closed")
    void confirmRefundSettlement_groupPinnedPolicyMissing_failsClosed() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 733L;
        Long orderId = 833L;
        Long buyerId = 933L;
        UUID groupId = UUID.randomUUID();

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("50000"),
                "RETURN", decisionId, null, "REQUESTED", "DISBURSE:DECISION:733"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 633L, orderId, buyerId,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("JOIN og_compat.orders o ON c.order_id = o.order_id"), any(RowMapper.class), eq(opId)))
                .thenReturn(List.of(ctx));

        RefundSettlementService.OrderGroupContext orderCtx = new RefundSettlementService.OrderGroupContext(
                groupId, buyerId, "PARALLEL_SPLIT", new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("SELECT group_id, buyer_id, workflow_model, points_discount_amount"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of(orderCtx));

        // Group has null reward_policy_id
        when(jdbcTemplate.query(contains("SELECT reward_policy_id"), any(RowMapper.class), eq(groupId)))
                .thenReturn(java.util.Collections.singletonList((Long) null));

        when(jdbcTemplate.query(contains("WHERE rl.group_id = ? AND rl.entry_type = 'DEBIT_CHECKOUT'"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new RefundSettlementService.GroupDebitRecord(UUID.randomUUID(), buyerId, -50000L, 1L, BigDecimal.ONE)));
        when(jdbcTemplate.query(contains("SELECT points_discount_amount\nFROM og_compat.orders\nWHERE group_id = ?"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new BigDecimal("50000")));

        assertThatThrownBy(() -> service.confirmRefundSettlement(opId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Pinned reward policy missing on checkout group");
    }

    @Test
    @DisplayName("P0 - Policy Pinning: Group DEBIT_CHECKOUT policy_id is null fails closed")
    void confirmRefundSettlement_groupDebitPolicyIdNull_failsClosed() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 734L;
        Long orderId = 834L;
        Long buyerId = 934L;
        UUID groupId = UUID.randomUUID();

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("50000"),
                "RETURN", decisionId, null, "REQUESTED", "DISBURSE:DECISION:734"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 634L, orderId, buyerId,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("JOIN og_compat.orders o ON c.order_id = o.order_id"), any(RowMapper.class), eq(opId)))
                .thenReturn(List.of(ctx));

        RefundSettlementService.OrderGroupContext orderCtx = new RefundSettlementService.OrderGroupContext(
                groupId, buyerId, "PARALLEL_SPLIT", new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("SELECT group_id, buyer_id, workflow_model, points_discount_amount"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of(orderCtx));
        when(jdbcTemplate.query(contains("SELECT reward_policy_id"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(1L));

        // Group debit policy_id is null
        when(jdbcTemplate.query(contains("WHERE rl.group_id = ? AND rl.entry_type = 'DEBIT_CHECKOUT'"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new RefundSettlementService.GroupDebitRecord(UUID.randomUUID(), buyerId, -50000L, null, BigDecimal.ONE)));
        when(jdbcTemplate.query(contains("SELECT points_discount_amount\nFROM og_compat.orders\nWHERE group_id = ?"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new BigDecimal("50000")));

        assertThatThrownBy(() -> service.confirmRefundSettlement(opId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("policy_id is null for group");
    }

    @Test
    @DisplayName("P0 - Policy Pinning: Mismatched ledger policy_id vs group pinned policy_id fails closed")
    void confirmRefundSettlement_ledgerPolicyMismatchWithGroupPolicy_failsClosed() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 735L;
        Long orderId = 835L;
        Long buyerId = 935L;
        UUID groupId = UUID.randomUUID();

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("50000"),
                "RETURN", decisionId, null, "REQUESTED", "DISBURSE:DECISION:735"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 635L, orderId, buyerId,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("JOIN og_compat.orders o ON c.order_id = o.order_id"), any(RowMapper.class), eq(opId)))
                .thenReturn(List.of(ctx));

        RefundSettlementService.OrderGroupContext orderCtx = new RefundSettlementService.OrderGroupContext(
                groupId, buyerId, "PARALLEL_SPLIT", new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("SELECT group_id, buyer_id, workflow_model, points_discount_amount"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of(orderCtx));

        // Group pinned policy is 1L, but debit policy is 999L
        when(jdbcTemplate.query(contains("SELECT reward_policy_id"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(1L));
        when(jdbcTemplate.query(contains("WHERE rl.group_id = ? AND rl.entry_type = 'DEBIT_CHECKOUT'"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new RefundSettlementService.GroupDebitRecord(UUID.randomUUID(), buyerId, -50000L, 999L, BigDecimal.ONE)));
        when(jdbcTemplate.query(contains("SELECT points_discount_amount\nFROM og_compat.orders\nWHERE group_id = ?"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new BigDecimal("50000")));

        assertThatThrownBy(() -> service.confirmRefundSettlement(opId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Reward policy mismatch: group pinned policy_id=1 but reward_ledger policy_id=999");
    }

    @Test
    @DisplayName("P0 - Policy Pinning: Pinned policy record missing from reward_policies fails closed")
    void confirmRefundSettlement_pinnedPolicyNotFoundInPolicies_failsClosed() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 736L;
        Long orderId = 836L;
        Long buyerId = 936L;
        UUID groupId = UUID.randomUUID();

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("50000"),
                "RETURN", decisionId, null, "REQUESTED", "DISBURSE:DECISION:736"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 636L, orderId, buyerId,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("JOIN og_compat.orders o ON c.order_id = o.order_id"), any(RowMapper.class), eq(opId)))
                .thenReturn(List.of(ctx));

        RefundSettlementService.OrderGroupContext orderCtx = new RefundSettlementService.OrderGroupContext(
                groupId, buyerId, "PARALLEL_SPLIT", new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("SELECT group_id, buyer_id, workflow_model, points_discount_amount"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of(orderCtx));
        when(jdbcTemplate.query(contains("SELECT reward_policy_id"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(1L));
        when(jdbcTemplate.query(contains("WHERE rl.group_id = ? AND rl.entry_type = 'DEBIT_CHECKOUT'"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new RefundSettlementService.GroupDebitRecord(UUID.randomUUID(), buyerId, -50000L, 1L, BigDecimal.ONE)));
        when(jdbcTemplate.query(contains("SELECT points_discount_amount\nFROM og_compat.orders\nWHERE group_id = ?"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new BigDecimal("50000")));

        // Policy record not found in reward_policies
        when(jdbcTemplate.query(contains("SELECT point_value"), any(RowMapper.class), eq(1L)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.confirmRefundSettlement(opId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Pinned reward policy 1 not found in reward_policies");
    }

    @Test
    @DisplayName("P0 - Policy Pinning: Pinned policy with point_value <= 0 fails closed (no fallback)")
    void confirmRefundSettlement_pinnedPolicyPointValueZeroOrNegative_failsClosed() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 737L;
        Long orderId = 837L;
        Long buyerId = 937L;
        UUID groupId = UUID.randomUUID();

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("50000"),
                "RETURN", decisionId, null, "REQUESTED", "DISBURSE:DECISION:737"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 637L, orderId, buyerId,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("JOIN og_compat.orders o ON c.order_id = o.order_id"), any(RowMapper.class), eq(opId)))
                .thenReturn(List.of(ctx));

        RefundSettlementService.OrderGroupContext orderCtx = new RefundSettlementService.OrderGroupContext(
                groupId, buyerId, "PARALLEL_SPLIT", new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("SELECT group_id, buyer_id, workflow_model, points_discount_amount"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of(orderCtx));
        when(jdbcTemplate.query(contains("SELECT reward_policy_id"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(1L));
        when(jdbcTemplate.query(contains("WHERE rl.group_id = ? AND rl.entry_type = 'DEBIT_CHECKOUT'"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new RefundSettlementService.GroupDebitRecord(UUID.randomUUID(), buyerId, -50000L, 1L, BigDecimal.ZERO)));
        when(jdbcTemplate.query(contains("SELECT points_discount_amount\nFROM og_compat.orders\nWHERE group_id = ?"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new BigDecimal("50000")));

        // Policy point_value is 0.00
        when(jdbcTemplate.query(contains("SELECT point_value"), any(RowMapper.class), eq(1L)))
                .thenReturn(List.of(BigDecimal.ZERO));

        assertThatThrownBy(() -> service.confirmRefundSettlement(opId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid point_value (0) for pinned reward policy 1");
    }

    @Test
    @DisplayName("P0 - DEC-10.6 POLICY_REQUIRED: Missing policy fails closed without falling back to 1.00")
    void confirmRefundSettlement_noPolicyFallback_doesNotDefaultToOne() {
        UUID opId = UUID.randomUUID();
        Long decisionId = 738L;
        Long orderId = 838L;
        Long buyerId = 938L;
        UUID groupId = UUID.randomUUID();

        mockLookup(opId, "REQUESTED", orderId);
        mockOrderLock(orderId);

        RefundSettlementService.SettlementOpRecord opRecord = new RefundSettlementService.SettlementOpRecord(
                opId, UUID.randomUUID(), "RETURN_PLATFORM", new BigDecimal("50000"),
                "RETURN", decisionId, null, "REQUESTED", "DISBURSE:DECISION:738"
        );
        mockOpRecord(opRecord);

        RefundSettlementService.DecisionOrderContext ctx = new RefundSettlementService.DecisionOrderContext(
                decisionId, "FULL_GOODS_REFUND", true, 638L, orderId, buyerId,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("JOIN og_compat.orders o ON c.order_id = o.order_id"), any(RowMapper.class), eq(opId)))
                .thenReturn(List.of(ctx));

        RefundSettlementService.OrderGroupContext orderCtx = new RefundSettlementService.OrderGroupContext(
                groupId, buyerId, "PARALLEL_SPLIT", new BigDecimal("50000")
        );
        when(jdbcTemplate.query(contains("SELECT group_id, buyer_id, workflow_model, points_discount_amount"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of(orderCtx));
        when(jdbcTemplate.query(contains("SELECT reward_policy_id"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(42L));
        when(jdbcTemplate.query(contains("WHERE rl.group_id = ? AND rl.entry_type = 'DEBIT_CHECKOUT'"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new RefundSettlementService.GroupDebitRecord(UUID.randomUUID(), buyerId, -50000L, 42L, null)));
        when(jdbcTemplate.query(contains("SELECT points_discount_amount\nFROM og_compat.orders\nWHERE group_id = ?"), any(RowMapper.class), eq(groupId)))
                .thenReturn(List.of(new BigDecimal("50000")));

        // Empty policy list -> should NOT fall back to BigDecimal.ONE
        when(jdbcTemplate.query(contains("SELECT point_value"), any(RowMapper.class), eq(42L)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.confirmRefundSettlement(opId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Pinned reward policy 42 not found in reward_policies");
    }
}
