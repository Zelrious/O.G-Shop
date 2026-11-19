package com.oldbutgold.shop.modules.trustsafety.application;

/**
 * Outbound application port for requesting settlement operations on return case decisions (Task 4.1a / UC83).
 * Enables Trust & Safety workflows (Feature 3.2) to request financial settlement operations
 * without directly coupling to internal payment implementations.
 */
public interface RoundDecisionSettlementPort {

    /**
     * Records settlement request operations in REQUESTED state for the given case decision.
     * Must be executed within the same transaction as the CaseDecision creation.
     *
     * @param orderId ID of the order
     * @param caseId ID of the return case
     * @param decisionId ID of the case decision
     * @param outcome outcome of the decision (e.g. FULL_GOODS_REFUND, EXEMPT_GOODS_REFUND)
     * @param commandKey unique command key for idempotency
     */
    void requestSettlement(
            Long orderId,
            Long caseId,
            Long decisionId,
            String outcome,
            String commandKey
    );
}

