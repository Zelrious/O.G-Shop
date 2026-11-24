package com.oldbutgold.shop.modules.payment.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Optional simulated settlement confirmation scheduler for UC83 return-case settlements.
 * Disabled by default ({@code app.scheduling.settlement-auto-confirm.enabled=false}) so
 * confirmation remains explicitly controlled via {@code AdminPaymentController} unless enabled
 * for sandbox/demo automation.
 */
@Component
public class SettlementReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SettlementReconciliationScheduler.class);
    private static final int DEFAULT_BATCH_LIMIT = 50;

    private final RefundSettlementService refundSettlementService;
    private final boolean autoConfirmEnabled;

    @Autowired
    public SettlementReconciliationScheduler(
            RefundSettlementService refundSettlementService,
            @Value("${app.scheduling.settlement-auto-confirm.enabled:false}") boolean autoConfirmEnabled
    ) {
        this.refundSettlementService = refundSettlementService;
        this.autoConfirmEnabled = autoConfirmEnabled;
    }

    @Scheduled(
            fixedDelayString = "${app.scheduling.settlement-auto-confirm.fixed-delay-ms:60000}",
            initialDelayString = "${app.scheduling.settlement-auto-confirm.initial-delay-ms:60000}"
    )
    public RefundSettlementService.PendingSettlementBatchResult reconcilePendingSettlements() {
        if (!autoConfirmEnabled) {
            log.debug("Settlement auto-confirm scheduler is disabled by feature toggle.");
            return new RefundSettlementService.PendingSettlementBatchResult(0, List.of(), List.of(), List.of());
        }

        List<UUID> pendingIds = refundSettlementService.scanPendingRefundSettlementOperationIds(DEFAULT_BATCH_LIMIT);
        List<UUID> confirmed = new ArrayList<>();
        List<UUID> skipped = new ArrayList<>();
        List<UUID> failed = new ArrayList<>();

        for (UUID operationId : pendingIds) {
            try {
                RefundSettlementService.RefundSettlementResult result =
                        refundSettlementService.confirmRefundSettlement(operationId);
                if (result != null && result.message() != null && result.message().startsWith("Idempotent replay")) {
                    skipped.add(operationId);
                } else {
                    confirmed.add(operationId);
                }
            } catch (Exception ex) {
                log.error("Failed to auto-confirm settlement operation {}: {}", operationId, ex.getMessage(), ex);
                failed.add(operationId);
            }
        }

        if (!pendingIds.isEmpty()) {
            log.info("SettlementReconciliationScheduler executed: scanned={}, confirmed={}, skipped={}, failed={}",
                    pendingIds.size(), confirmed.size(), skipped.size(), failed.size());
        }

        return new RefundSettlementService.PendingSettlementBatchResult(
                pendingIds.size(),
                List.copyOf(confirmed),
                List.copyOf(skipped),
                List.copyOf(failed)
        );
    }
}
