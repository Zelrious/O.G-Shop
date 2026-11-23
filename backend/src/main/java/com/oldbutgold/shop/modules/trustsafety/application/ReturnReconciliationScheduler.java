package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.application.OrderManagementDtos;
import com.oldbutgold.shop.modules.commerce.application.OrderManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled background job for reconciling overdue post-return cases (Task 3.2.4 / UC63 / UC75)
 * and overdue delivered orders seller disbursement (Task 3.3 / UC71).
 */
@Component
@ConditionalOnProperty(name = "app.scheduling.return-reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class ReturnReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReturnReconciliationScheduler.class);

    private final ReturnCaseService returnCaseService;
    private final OrderManagementService orderManagementService;
    private final boolean overdueDisbursementEnabled;
    private final boolean overdueReturnPickupEnabled;

    @Autowired
    public ReturnReconciliationScheduler(
            ReturnCaseService returnCaseService,
            OrderManagementService orderManagementService,
            @Value("${app.scheduling.overdue-delivery-disbursement.enabled:false}") boolean overdueDisbursementEnabled,
            @Value("${app.scheduling.overdue-return-pickup.enabled:false}") boolean overdueReturnPickupEnabled
    ) {
        this.returnCaseService = returnCaseService;
        this.orderManagementService = orderManagementService;
        this.overdueDisbursementEnabled = overdueDisbursementEnabled;
        this.overdueReturnPickupEnabled = overdueReturnPickupEnabled;
    }

    public ReturnReconciliationScheduler(
            ReturnCaseService returnCaseService,
            OrderManagementService orderManagementService,
            boolean overdueDisbursementEnabled
    ) {
        this(returnCaseService, orderManagementService, overdueDisbursementEnabled, false);
    }

    public ReturnReconciliationScheduler(
            ReturnCaseService returnCaseService,
            OrderManagementService orderManagementService
    ) {
        this(returnCaseService, orderManagementService, true, false);
    }

    @Scheduled(
            fixedDelayString = "${app.scheduling.return-reconciliation.fixed-delay-ms:60000}",
            initialDelayString = "${app.scheduling.return-reconciliation.initial-delay-ms:60000}"
    )
    public void reconcileOverdueReturnResponses() {
        try {
            ReturnCaseDtos.TimeoutBatchResult result = returnCaseService.reconcileOverdueReturnResponses();
            if (result != null && result.totalScanned() > 0) {
                log.info("ReturnReconciliationScheduler executed: scanned={}, processed={}, skipped={}, locked={}, failed={}",
                        result.totalScanned(),
                        result.processedCaseIds().size(),
                        result.skippedCaseIds().size(),
                        result.lockedCaseIds().size(),
                        result.failedCaseIds().size());
            }
        } catch (Exception ex) {
            log.error("ReturnReconciliationScheduler encountered unhandled batch error: {}", ex.getMessage(), ex);
        }
    }

    @Scheduled(
            fixedDelayString = "${app.scheduling.ktv-sla-escalation.fixed-delay-ms:60000}",
            initialDelayString = "${app.scheduling.ktv-sla-escalation.initial-delay-ms:60000}"
    )
    public void reconcileKtvOverdueSla() {
        try {
            ReturnCaseDtos.EscalationBatchResult result = returnCaseService.reconcileKtvOverdueSla();
            if (result != null && result.totalScanned() > 0) {
                log.info("KtvSlaEscalation executed: scanned={}, escalated={}, skipped={}, locked={}, failed={}",
                        result.totalScanned(),
                        result.escalatedCaseIds().size(),
                        result.skippedCaseIds().size(),
                        result.lockedCaseIds().size(),
                        result.failedCaseIds().size());
            }
        } catch (Exception ex) {
            log.error("ReturnReconciliationScheduler encountered unhandled SLA escalation error: {}", ex.getMessage(), ex);
        }
    }

    @Scheduled(
            fixedDelayString = "${app.scheduling.overdue-delivery-disbursement.fixed-delay-ms:60000}",
            initialDelayString = "${app.scheduling.overdue-delivery-disbursement.initial-delay-ms:60000}"
    )
    public void reconcileOverdueDeliveries() {
        if (!overdueDisbursementEnabled) {
            log.debug("Overdue delivery seller disbursement scheduler is disabled by feature toggle.");
            return;
        }
        try {
            OrderManagementDtos.OverdueDeliveryBatchResult result = orderManagementService.reconcileOverdueDeliveries();
            if (result != null && result.totalScanned() > 0) {
                log.info("Overdue delivery seller disbursement executed: scanned={}, processed={}, skipped={}, failed={}",
                        result.totalScanned(),
                        result.processedOrderIds().size(),
                        result.skippedOrderIds().size(),
                        result.failedOrderIds().size());
            }
        } catch (Exception ex) {
            log.error("ReturnReconciliationScheduler encountered unhandled overdue delivery disbursement error: {}", ex.getMessage(), ex);
        }
    }

    @Scheduled(
            fixedDelayString = "${app.scheduling.overdue-return-pickup.fixed-delay-ms:60000}",
            initialDelayString = "${app.scheduling.overdue-return-pickup.initial-delay-ms:60000}"
    )
    public void reconcileOverdueReturnPickups() {
        if (!overdueReturnPickupEnabled) {
            log.debug("Overdue return pickup reconciliation scheduler is disabled by feature toggle.");
            return;
        }
        try {
            ReturnCaseDtos.OverduePickupBatchResult result = returnCaseService.reconcileOverdueReturnPickups();
            if (result != null && result.totalScanned() > 0) {
                log.info("Overdue return pickup reconciliation executed: scanned={}, closedBuyerFault={}, escalatedCarrierFault={}, skipped={}, failed={}",
                        result.totalScanned(),
                        result.closedBuyerFaultCaseIds().size(),
                        result.escalatedCarrierFaultCaseIds().size(),
                        result.skippedCaseIds().size(),
                        result.failedCaseIds().size());
            }
        } catch (Exception ex) {
            log.error("ReturnReconciliationScheduler encountered unhandled overdue return pickup error: {}", ex.getMessage(), ex);
        }
    }
}

