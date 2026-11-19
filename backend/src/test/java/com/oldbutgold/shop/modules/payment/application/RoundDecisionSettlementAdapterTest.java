package com.oldbutgold.shop.modules.payment.application;

import com.oldbutgold.shop.modules.payment.application.RoundDecisionSettlementAdapter.DecisionLinkRecord;
import com.oldbutgold.shop.modules.payment.application.RoundDecisionSettlementAdapter.FundComponentRecord;
import com.oldbutgold.shop.modules.payment.application.RoundDecisionSettlementAdapter.OrderParticipants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoundDecisionSettlementAdapterTest - Unit tests for Task 4.1a settlement request adapter")
class RoundDecisionSettlementAdapterTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private Clock fixedClock;
    private RoundDecisionSettlementAdapter adapter;

    private final Long orderId = 10L;
    private final Long caseId = 20L;
    private final Long decisionId = 30L;
    private final Long buyerId = 100L;
    private final Long sellerId = 200L;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneId.of("UTC"));
        adapter = new RoundDecisionSettlementAdapter(jdbcTemplate, fixedClock);
    }

    @Test
    @DisplayName("Validation: null or blank arguments throw IllegalArgumentException")
    void testValidation_NullOrBlankArguments() {
        assertThatThrownBy(() -> adapter.requestSettlement(null, caseId, decisionId, "FULL_GOODS_REFUND", "CMD_1"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> adapter.requestSettlement(orderId, null, decisionId, "FULL_GOODS_REFUND", "CMD_1"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> adapter.requestSettlement(orderId, caseId, null, "FULL_GOODS_REFUND", "CMD_1"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> adapter.requestSettlement(orderId, caseId, decisionId, null, "CMD_1"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> adapter.requestSettlement(orderId, caseId, decisionId, "   ", "CMD_1"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> adapter.requestSettlement(orderId, caseId, decisionId, "FULL_GOODS_REFUND", null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> adapter.requestSettlement(orderId, caseId, decisionId, "FULL_GOODS_REFUND", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Validation: unsupported outcomes throw IllegalArgumentException")
    void testValidation_UnsupportedOutcomes() {
        assertThatThrownBy(() -> adapter.requestSettlement(orderId, caseId, decisionId, "APPROVE_RETURN", "CMD_1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported settlement outcome: APPROVE_RETURN");

        assertThatThrownBy(() -> adapter.requestSettlement(orderId, caseId, decisionId, "REJECT_RETURN", "CMD_1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported settlement outcome: REJECT_RETURN");

        assertThatThrownBy(() -> adapter.requestSettlement(orderId, caseId, decisionId, "SYSTEM_RESOLVED", "CMD_1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported settlement outcome: SYSTEM_RESOLVED");
    }

    @Test
    @DisplayName("Order existence: throws NoSuchElementException when order not found")
    @SuppressWarnings("unchecked")
    void testOrderNotFound_ThrowsNoSuchElementException() {
        when(jdbcTemplate.query(contains("FROM og_compat.orders"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> adapter.requestSettlement(orderId, caseId, decisionId, "FULL_GOODS_REFUND", "CMD_1"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Order not found with ID: " + orderId);
    }

    @Test
    @DisplayName("Decision linkage: throws NoSuchElementException when decision not found")
    @SuppressWarnings("unchecked")
    void testDecisionNotFound_ThrowsNoSuchElementException() {
        setupOrderMock();
        when(jdbcTemplate.query(contains("FROM og_compat.case_decisions"), any(RowMapper.class), eq(decisionId)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> adapter.requestSettlement(orderId, caseId, decisionId, "FULL_GOODS_REFUND", "CMD_1"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("CaseDecision not found with ID: " + decisionId);
    }

    @Test
    @DisplayName("Decision linkage: throws IllegalArgumentException when decision belongs to wrong case")
    @SuppressWarnings("unchecked")
    void testDecisionMismatchedCase_ThrowsIllegalArgumentException() {
        setupOrderMock();
        when(jdbcTemplate.query(contains("FROM og_compat.case_decisions"), any(RowMapper.class), eq(decisionId)))
                .thenReturn(List.of(new DecisionLinkRecord(decisionId, "FULL_GOODS_REFUND", true, 999L, orderId)));

        assertThatThrownBy(() -> adapter.requestSettlement(orderId, caseId, decisionId, "FULL_GOODS_REFUND", "CMD_1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("belongs to case #999, not case #" + caseId);
    }

    @Test
    @DisplayName("Decision linkage: throws IllegalArgumentException when decision belongs to wrong order")
    @SuppressWarnings("unchecked")
    void testDecisionMismatchedOrder_ThrowsIllegalArgumentException() {
        setupOrderMock();
        when(jdbcTemplate.query(contains("FROM og_compat.case_decisions"), any(RowMapper.class), eq(decisionId)))
                .thenReturn(List.of(new DecisionLinkRecord(decisionId, "FULL_GOODS_REFUND", true, caseId, 888L)));

        assertThatThrownBy(() -> adapter.requestSettlement(orderId, caseId, decisionId, "FULL_GOODS_REFUND", "CMD_1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("belongs to order #888, not order #" + orderId);
    }

    @Test
    @DisplayName("Decision linkage: throws IllegalArgumentException when decision outcome does not match requested outcome")
    @SuppressWarnings("unchecked")
    void testDecisionMismatchedOutcome_ThrowsIllegalArgumentException() {
        setupOrderMock();
        when(jdbcTemplate.query(contains("FROM og_compat.case_decisions"), any(RowMapper.class), eq(decisionId)))
                .thenReturn(List.of(new DecisionLinkRecord(decisionId, "EXEMPT_GOODS_REFUND", true, caseId, orderId)));

        assertThatThrownBy(() -> adapter.requestSettlement(orderId, caseId, decisionId, "FULL_GOODS_REFUND", "CMD_1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outcome 'EXEMPT_GOODS_REFUND' does not match requested outcome 'FULL_GOODS_REFUND'");
    }

    @Test
    @DisplayName("Mapping FULL_GOODS_REFUND: maps GOODS_CASH to REFUND_BUYER and PLATFORM_GOODS_SUBSIDY to RETURN_PLATFORM")
    @SuppressWarnings("unchecked")
    void testFullGoodsRefund_MapsCorrectly() {
        setupOrderMock();
        setupDecisionMock(caseId, orderId, "FULL_GOODS_REFUND");
        UUID cashCompId = UUID.randomUUID();
        UUID subsidyCompId = UUID.randomUUID();

        when(jdbcTemplate.query(contains("FROM og_compat.order_fund_components"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of(
                        new FundComponentRecord(cashCompId, "GOODS_CASH", new BigDecimal("50000.00")),
                        new FundComponentRecord(subsidyCompId, "PLATFORM_GOODS_SUBSIDY", new BigDecimal("10000.00"))
                ));

        when(jdbcTemplate.queryForObject(contains("COUNT(*) FROM og_compat.settlement_operations"), eq(Integer.class), anyString()))
                .thenReturn(0);

        adapter.requestSettlement(orderId, caseId, decisionId, "FULL_GOODS_REFUND", "CMD_DEC_100");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate, times(2)).update(
                contains("INSERT INTO og_compat.settlement_operations"),
                captor.capture(), captor.capture(), captor.capture(), captor.capture(), captor.capture(),
                captor.capture(), captor.capture(), captor.capture(), captor.capture()
        );

        List<Object> allArgs = captor.getAllValues();
        // First insert (GOODS_CASH)
        assertThat(allArgs.get(1)).isEqualTo(cashCompId);
        assertThat(allArgs.get(2)).isEqualTo("REFUND_BUYER");
        assertThat(allArgs.get(3)).isEqualTo(new BigDecimal("50000.00"));
        assertThat(allArgs.get(4)).isEqualTo("RETURN");
        assertThat(allArgs.get(5)).isEqualTo(decisionId);
        assertThat(allArgs.get(6)).isEqualTo(buyerId);
        assertThat(allArgs.get(7)).isEqualTo(Timestamp.from(fixedClock.instant()));
        assertThat(allArgs.get(8)).isEqualTo("CMD_DEC_100:GOODS_CASH");

        // Second insert (PLATFORM_GOODS_SUBSIDY)
        assertThat(allArgs.get(10)).isEqualTo(subsidyCompId);
        assertThat(allArgs.get(11)).isEqualTo("RETURN_PLATFORM");
        assertThat(allArgs.get(12)).isEqualTo(new BigDecimal("10000.00"));
        assertThat(allArgs.get(13)).isEqualTo("RETURN");
        assertThat(allArgs.get(14)).isEqualTo(decisionId);
        assertThat(allArgs.get(15)).isNull();
        assertThat(allArgs.get(16)).isEqualTo(Timestamp.from(fixedClock.instant()));
        assertThat(allArgs.get(17)).isEqualTo("CMD_DEC_100:PLATFORM_GOODS_SUBSIDY");
    }

    @Test
    @DisplayName("Mapping EXEMPT_GOODS_REFUND: maps both GOODS_CASH and PLATFORM_GOODS_SUBSIDY to RELEASE_SELLER with cause EXEMPTION")
    @SuppressWarnings("unchecked")
    void testExemptGoodsRefund_MapsCorrectly() {
        setupOrderMock();
        setupDecisionMock(caseId, orderId, "EXEMPT_GOODS_REFUND");
        UUID cashCompId = UUID.randomUUID();
        UUID subsidyCompId = UUID.randomUUID();

        when(jdbcTemplate.query(contains("FROM og_compat.order_fund_components"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of(
                        new FundComponentRecord(cashCompId, "GOODS_CASH", new BigDecimal("75000.00")),
                        new FundComponentRecord(subsidyCompId, "PLATFORM_GOODS_SUBSIDY", new BigDecimal("25000.00"))
                ));

        when(jdbcTemplate.queryForObject(contains("COUNT(*) FROM og_compat.settlement_operations"), eq(Integer.class), anyString()))
                .thenReturn(0);

        adapter.requestSettlement(orderId, caseId, decisionId, "EXEMPT_GOODS_REFUND", "CMD_DEC_200");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate, times(2)).update(
                contains("INSERT INTO og_compat.settlement_operations"),
                captor.capture(), captor.capture(), captor.capture(), captor.capture(), captor.capture(),
                captor.capture(), captor.capture(), captor.capture(), captor.capture()
        );

        List<Object> allArgs = captor.getAllValues();
        // First insert (GOODS_CASH -> RELEASE_SELLER)
        assertThat(allArgs.get(1)).isEqualTo(cashCompId);
        assertThat(allArgs.get(2)).isEqualTo("RELEASE_SELLER");
        assertThat(allArgs.get(3)).isEqualTo(new BigDecimal("75000.00"));
        assertThat(allArgs.get(4)).isEqualTo("EXEMPTION");
        assertThat(allArgs.get(5)).isEqualTo(decisionId);
        assertThat(allArgs.get(6)).isEqualTo(sellerId);
        assertThat(allArgs.get(8)).isEqualTo("CMD_DEC_200:GOODS_CASH");

        // Second insert (PLATFORM_GOODS_SUBSIDY -> RELEASE_SELLER)
        assertThat(allArgs.get(10)).isEqualTo(subsidyCompId);
        assertThat(allArgs.get(11)).isEqualTo("RELEASE_SELLER");
        assertThat(allArgs.get(12)).isEqualTo(new BigDecimal("25000.00"));
        assertThat(allArgs.get(13)).isEqualTo("EXEMPTION");
        assertThat(allArgs.get(14)).isEqualTo(decisionId);
        assertThat(allArgs.get(15)).isEqualTo(sellerId);
        assertThat(allArgs.get(17)).isEqualTo("CMD_DEC_200:PLATFORM_GOODS_SUBSIDY");
    }

    @Test
    @DisplayName("Amount validation: amount == 0 skips insertion per UC83-07 without error")
    @SuppressWarnings("unchecked")
    void testZeroAmount_Skipped() {
        setupOrderMock();
        setupDecisionMock(caseId, orderId, "FULL_GOODS_REFUND");
        UUID cashCompId = UUID.randomUUID();
        UUID subsidyCompId = UUID.randomUUID();

        when(jdbcTemplate.query(contains("FROM og_compat.order_fund_components"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of(
                        new FundComponentRecord(cashCompId, "GOODS_CASH", new BigDecimal("50000.00")),
                        new FundComponentRecord(subsidyCompId, "PLATFORM_GOODS_SUBSIDY", BigDecimal.ZERO)
                ));

        when(jdbcTemplate.queryForObject(contains("COUNT(*) FROM og_compat.settlement_operations"), eq(Integer.class), anyString()))
                .thenReturn(0);

        adapter.requestSettlement(orderId, caseId, decisionId, "FULL_GOODS_REFUND", "CMD_DEC_300");

        verify(jdbcTemplate, times(1)).update(
                contains("INSERT INTO og_compat.settlement_operations"),
                any(), eq(cashCompId), eq("REFUND_BUYER"), eq(new BigDecimal("50000.00")), eq("RETURN"),
                eq(decisionId), eq(buyerId), any(), eq("CMD_DEC_300:GOODS_CASH")
        );
    }

    @Test
    @DisplayName("Amount validation: amount < 0 throws IllegalStateException (invariant violation, not silently skipped)")
    @SuppressWarnings("unchecked")
    void testNegativeAmount_ThrowsIllegalStateException() {
        setupOrderMock();
        setupDecisionMock(caseId, orderId, "FULL_GOODS_REFUND");
        UUID cashCompId = UUID.randomUUID();

        when(jdbcTemplate.query(contains("FROM og_compat.order_fund_components"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of(
                        new FundComponentRecord(cashCompId, "GOODS_CASH", new BigDecimal("-50000.00"))
                ));

        assertThatThrownBy(() -> adapter.requestSettlement(orderId, caseId, decisionId, "FULL_GOODS_REFUND", "CMD_DEC_301"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid negative or null fund component amount for GOODS_CASH: -50000.00");
    }

    @Test
    @DisplayName("Idempotency: existing command_key skips insertion without error")
    @SuppressWarnings("unchecked")
    void testIdempotency_ExistingCommandKey_Skipped() {
        setupOrderMock();
        setupDecisionMock(caseId, orderId, "FULL_GOODS_REFUND");
        UUID cashCompId = UUID.randomUUID();

        when(jdbcTemplate.query(contains("FROM og_compat.order_fund_components"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of(
                        new FundComponentRecord(cashCompId, "GOODS_CASH", new BigDecimal("50000.00"))
                ));

        when(jdbcTemplate.queryForObject(contains("COUNT(*) FROM og_compat.settlement_operations"), eq(Integer.class), eq("CMD_EXISTING:GOODS_CASH")))
                .thenReturn(1);

        adapter.requestSettlement(orderId, caseId, decisionId, "FULL_GOODS_REFUND", "CMD_EXISTING");

        verify(jdbcTemplate, never()).update(contains("INSERT INTO og_compat.settlement_operations"), any(Object[].class));
    }

    @Test
    @DisplayName("Validation: command_key exceeding 150 chars throws IllegalArgumentException")
    @SuppressWarnings("unchecked")
    void testCommandKeyTooLong_ThrowsIllegalArgumentException() {
        setupOrderMock();
        setupDecisionMock(caseId, orderId, "FULL_GOODS_REFUND");
        String hugeKey = "A".repeat(150);

        when(jdbcTemplate.query(contains("FROM og_compat.order_fund_components"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of(
                        new FundComponentRecord(UUID.randomUUID(), "GOODS_CASH", new BigDecimal("50000.00"))
                ));

        assertThatThrownBy(() -> adapter.requestSettlement(orderId, caseId, decisionId, "FULL_GOODS_REFUND", hugeKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum length of 150");
    }

    @SuppressWarnings("unchecked")
    private void setupOrderMock() {
        when(jdbcTemplate.query(contains("FROM og_compat.orders"), any(RowMapper.class), eq(orderId)))
                .thenReturn(List.of(new OrderParticipants(buyerId, sellerId)));
    }

    @SuppressWarnings("unchecked")
    private void setupDecisionMock(Long cId, Long oId, String expectedOutcome) {
        when(jdbcTemplate.query(contains("FROM og_compat.case_decisions"), any(RowMapper.class), eq(decisionId)))
                .thenReturn(List.of(new DecisionLinkRecord(decisionId, expectedOutcome, true, cId, oId)));
    }
}
