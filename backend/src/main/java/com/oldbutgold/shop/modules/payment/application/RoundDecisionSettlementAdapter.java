package com.oldbutgold.shop.modules.payment.application;

import com.oldbutgold.shop.modules.trustsafety.application.RoundDecisionSettlementPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Payment module adapter implementing {@link RoundDecisionSettlementPort} (Task 4.1a / UC83).
 * Inserts financial settlement operation records into og_compat.settlement_operations
 * in state = 'REQUESTED' within the caller's transaction.
 *
 * <p>Strict transactional boundary:
 * <ul>
 *   <li>Runs in the caller's transaction via {@code Propagation.REQUIRED}.</li>
 *   <li>Maps outcomes strictly (FULL_GOODS_REFUND, EXEMPT_GOODS_REFUND).</li>
 *   <li>Guarantees idempotency via deterministic component command keys.</li>
 *   <li>Does NOT confirm operations or move real funds.</li>
 * </ul>
 */
@Component
public class RoundDecisionSettlementAdapter implements RoundDecisionSettlementPort {

    private static final Logger log = LoggerFactory.getLogger(RoundDecisionSettlementAdapter.class);

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    public RoundDecisionSettlementAdapter(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public RoundDecisionSettlementAdapter(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void requestSettlement(
            Long orderId,
            Long caseId,
            Long decisionId,
            String outcome,
            String commandKey
    ) {
        if (orderId == null || caseId == null || decisionId == null) {
            throw new IllegalArgumentException("orderId, caseId, and decisionId must not be null");
        }
        if (outcome == null || outcome.isBlank()) {
            throw new IllegalArgumentException("outcome must not be blank");
        }
        if (commandKey == null || commandKey.isBlank()) {
            throw new IllegalArgumentException("commandKey must not be blank");
        }

        boolean isFullRefund = "FULL_GOODS_REFUND".equals(outcome);
        boolean isExemptRefund = "EXEMPT_GOODS_REFUND".equals(outcome);
        if (!isFullRefund && !isExemptRefund) {
            throw new IllegalArgumentException("Unsupported settlement outcome: " + outcome);
        }

        log.info("Processing settlement request: orderId={}, caseId={}, decisionId={}, outcome={}, commandKey={}",
                orderId, caseId, decisionId, outcome, commandKey);

        // 1. Lock Order row exclusively to serialize concurrent callers on the same order (Deadlock protocol: Order first)
        List<OrderParticipants> orderList = jdbcTemplate.query(
                "SELECT buyer_id, seller_id FROM og_compat.orders WHERE order_id = ? FOR UPDATE",
                (rs, rowNum) -> new OrderParticipants(rs.getLong("buyer_id"), rs.getLong("seller_id")),
                orderId
        );
        if (orderList.isEmpty()) {
            throw new NoSuchElementException("Order not found with ID: " + orderId);
        }
        OrderParticipants order = orderList.getFirst();

        // 2. Decision Linkage validation: verify decision exists and links to expected case, order, and outcome
        List<DecisionLinkRecord> decisionList = jdbcTemplate.query("""
                SELECT d.decision_id, d.outcome, d.is_final, c.case_id, c.order_id
                FROM og_compat.case_decisions d
                JOIN og_compat.case_rounds r ON d.round_id = r.round_id
                JOIN og_compat.cases c ON r.case_id = c.case_id
                WHERE d.decision_id = ?
                """,
                (rs, rowNum) -> new DecisionLinkRecord(
                        rs.getLong("decision_id"),
                        rs.getString("outcome"),
                        rs.getBoolean("is_final"),
                        rs.getLong("case_id"),
                        rs.getLong("order_id")
                ),
                decisionId
        );
        if (decisionList.isEmpty()) {
            throw new NoSuchElementException("CaseDecision not found with ID: " + decisionId);
        }
        DecisionLinkRecord decision = decisionList.getFirst();
        if (!caseId.equals(decision.caseId())) {
            throw new IllegalArgumentException("Decision #" + decisionId + " belongs to case #" + decision.caseId() + ", not case #" + caseId);
        }
        if (!orderId.equals(decision.orderId())) {
            throw new IllegalArgumentException("Decision #" + decisionId + " belongs to order #" + decision.orderId() + ", not order #" + orderId);
        }
        if (!outcome.equals(decision.outcome())) {
            throw new IllegalArgumentException("Decision #" + decisionId + " outcome '" + decision.outcome() + "' does not match requested outcome '" + outcome + "'");
        }

        // 3. Fetch relevant goods fund components (GOODS_CASH, PLATFORM_GOODS_SUBSIDY)
        List<FundComponentRecord> components = jdbcTemplate.query("""
                SELECT component_id, component_type, confirmed_amount
                FROM og_compat.order_fund_components
                WHERE order_id = ? AND component_type IN ('GOODS_CASH', 'PLATFORM_GOODS_SUBSIDY')
                ORDER BY component_type
                """,
                (rs, rowNum) -> new FundComponentRecord(
                        rs.getObject("component_id", UUID.class),
                        rs.getString("component_type"),
                        rs.getBigDecimal("confirmed_amount")
                ),
                orderId
        );

        Timestamp requestedAt = Timestamp.from(clock.instant());

        for (FundComponentRecord comp : components) {
            BigDecimal amount = comp.confirmedAmount();
            // Amount validation: differentiate amount < 0 (data violation) from amount == 0 (zero checkout per UC83-07)
            if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalStateException("Invalid negative or null fund component amount for "
                        + comp.componentType() + ": " + amount);
            }
            if (amount.compareTo(BigDecimal.ZERO) == 0) {
                log.info("Skipping fund component {} ({}) with zero amount per UC83-07",
                        comp.componentId(), comp.componentType());
                continue;
            }

            String operationType;
            String causeType;
            Long recipientUserId;

            if (isFullRefund) {
                causeType = "RETURN";
                if ("GOODS_CASH".equals(comp.componentType())) {
                    operationType = "REFUND_BUYER";
                    recipientUserId = order.buyerId();
                } else if ("PLATFORM_GOODS_SUBSIDY".equals(comp.componentType())) {
                    operationType = "RETURN_PLATFORM";
                    recipientUserId = null;
                } else {
                    continue;
                }
            } else { // isExemptRefund
                causeType = "EXEMPTION";
                if ("GOODS_CASH".equals(comp.componentType()) || "PLATFORM_GOODS_SUBSIDY".equals(comp.componentType())) {
                    operationType = "RELEASE_SELLER";
                    recipientUserId = order.sellerId();
                } else {
                    continue;
                }
            }

            String opCommandKey = commandKey.endsWith(":" + comp.componentType())
                    ? commandKey
                    : commandKey + ":" + comp.componentType();

            if (opCommandKey.length() > 150) {
                throw new IllegalArgumentException("Generated command_key exceeds maximum length of 150 characters: " + opCommandKey);
            }

            // Idempotency check: skip insertion if already recorded
            Integer existingCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM og_compat.settlement_operations WHERE command_key = ?",
                    Integer.class,
                    opCommandKey
            );
            if (existingCount != null && existingCount > 0) {
                log.info("Settlement operation already exists for commandKey {}, skipping insertion (idempotent)", opCommandKey);
                continue;
            }

            UUID opId = UUID.randomUUID();
            try {
                jdbcTemplate.update("""
                        INSERT INTO og_compat.settlement_operations(
                            operation_id, component_id, operation_type, amount, cause_type,
                            case_decision_id, recipient_user_id, state, requested_at, command_key
                        ) VALUES (
                            ?, ?, ?, ?, ?,
                            ?, ?, 'REQUESTED', ?, ?
                        )
                        """,
                        opId, comp.componentId(), operationType, amount, causeType,
                        decisionId, recipientUserId, requestedAt, opCommandKey
                );
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                // In case of concurrent race condition, check if command_key was inserted by peer transaction
                Integer checkAgain = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM og_compat.settlement_operations WHERE command_key = ?",
                        Integer.class,
                        opCommandKey
                );
                if (checkAgain != null && checkAgain > 0) {
                    log.info("Settlement operation already inserted by peer transaction for commandKey {}, skipping", opCommandKey);
                    continue;
                }
                throw ex;
            }

            log.info("Recorded settlement operation {} (type={}, amount={}, cause={}, state=REQUESTED, commandKey={})",
                    opId, operationType, amount, causeType, opCommandKey);
        }
    }

    record OrderParticipants(Long buyerId, Long sellerId) {}
    record DecisionLinkRecord(Long decisionId, String outcome, Boolean isFinal, Long caseId, Long orderId) {}
    record FundComponentRecord(UUID componentId, String componentType, BigDecimal confirmedAmount) {}
}

