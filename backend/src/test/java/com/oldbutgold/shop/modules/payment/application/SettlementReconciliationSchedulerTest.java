package com.oldbutgold.shop.modules.payment.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementReconciliationSchedulerTest - Optional UC83 settlement auto-confirm")
class SettlementReconciliationSchedulerTest {

    @Mock
    private RefundSettlementService refundSettlementService;

    @Test
    @DisplayName("Disabled toggle skips scanning and confirming pending settlements")
    void reconcilePendingSettlements_whenDisabled_skipsExecution() {
        SettlementReconciliationScheduler scheduler =
                new SettlementReconciliationScheduler(refundSettlementService, false);

        var result = scheduler.reconcilePendingSettlements();

        assertThat(result.totalScanned()).isZero();
        verify(refundSettlementService, never()).scanPendingRefundSettlementOperationIds(50);
    }

    @Test
    @DisplayName("Enabled toggle confirms pending operations, classifies replay as skipped, and isolates errors")
    void reconcilePendingSettlements_whenEnabled_processesBatchSafely() {
        UUID opConfirmed = UUID.randomUUID();
        UUID opReplay = UUID.randomUUID();
        UUID opFailed = UUID.randomUUID();

        when(refundSettlementService.scanPendingRefundSettlementOperationIds(50))
                .thenReturn(List.of(opConfirmed, opReplay, opFailed));
        when(refundSettlementService.confirmRefundSettlement(opConfirmed))
                .thenReturn(new RefundSettlementService.RefundSettlementResult(opConfirmed, "CONFIRMED", 1000L, "Settlement operation confirmed successfully"));
        when(refundSettlementService.confirmRefundSettlement(opReplay))
                .thenReturn(new RefundSettlementService.RefundSettlementResult(opReplay, "CONFIRMED", 0L, "Idempotent replay: operation already confirmed"));
        when(refundSettlementService.confirmRefundSettlement(opFailed))
                .thenThrow(new IllegalStateException("Transient lock error"));

        SettlementReconciliationScheduler scheduler =
                new SettlementReconciliationScheduler(refundSettlementService, true);

        var result = scheduler.reconcilePendingSettlements();

        assertThat(result.totalScanned()).isEqualTo(3);
        assertThat(result.confirmedOperationIds()).containsExactly(opConfirmed);
        assertThat(result.skippedOperationIds()).containsExactly(opReplay);
        assertThat(result.failedOperationIds()).containsExactly(opFailed);
    }
}
