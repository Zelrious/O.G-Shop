package com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA Entity mapping to og_compat.case_decisions table/view.
 * Stores decisions made by User (Buyer/Seller/KTV) or System Timeout for each round.
 * Dẫn chứng migration: V19 (line 66-83), V22 (line 2912).
 */
@Entity
@Table(name = "case_decisions", schema = "og_compat")
public class CaseDecisionEntity {

    public static final String ACTOR_USER = "USER";
    public static final String ACTOR_SYSTEM_TIMEOUT = "SYSTEM_TIMEOUT";

    public static final String OUTCOME_APPROVE_RETURN = "APPROVE_RETURN";
    public static final String OUTCOME_REJECT_RETURN = "REJECT_RETURN";
    public static final String OUTCOME_SELLER_DECLINED = "SELLER_DECLINED";
    public static final String OUTCOME_FULL_GOODS_REFUND = "FULL_GOODS_REFUND";
    public static final String OUTCOME_EXEMPT_GOODS_REFUND = "EXEMPT_GOODS_REFUND";
    public static final String OUTCOME_SYSTEM_RESOLVED = "SYSTEM_RESOLVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "decision_id")
    private Long id;

    @Column(name = "round_id", nullable = false)
    private Long roundId;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "actor_type", nullable = false, length = 20)
    private String actorType = ACTOR_USER;

    @Column(name = "outcome", nullable = false, length = 25)
    private String outcome;

    @Column(name = "is_final", nullable = false)
    private Boolean isFinal = true;

    @Column(name = "goods_refund_amount", precision = 19, scale = 2)
    private BigDecimal goodsRefundAmount;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt = Instant.now();

    @Column(name = "supersedes_decision_id")
    private Long supersedesDecisionId;

    @Column(name = "command_key", nullable = false, length = 150, unique = true)
    private String commandKey;

    protected CaseDecisionEntity() {
    }

    public CaseDecisionEntity(Long roundId, Long reviewedBy, String actorType, String outcome,
                              Boolean isFinal, BigDecimal goodsRefundAmount, String reason, String commandKey) {
        this.roundId = roundId;
        this.reviewedBy = reviewedBy;
        this.actorType = actorType;
        this.outcome = outcome;
        this.isFinal = isFinal;
        this.goodsRefundAmount = goodsRefundAmount;
        this.reason = reason;
        this.commandKey = commandKey;
        this.decidedAt = Instant.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRoundId() {
        return roundId;
    }

    public void setRoundId(Long roundId) {
        this.roundId = roundId;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(Long reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public Boolean getIsFinal() {
        return isFinal;
    }

    public void setIsFinal(Boolean aFinal) {
        isFinal = aFinal;
    }

    public BigDecimal getGoodsRefundAmount() {
        return goodsRefundAmount;
    }

    public void setGoodsRefundAmount(BigDecimal goodsRefundAmount) {
        this.goodsRefundAmount = goodsRefundAmount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public Long getSupersedesDecisionId() {
        return supersedesDecisionId;
    }

    public void setSupersedesDecisionId(Long supersedesDecisionId) {
        this.supersedesDecisionId = supersedesDecisionId;
    }

    public String getCommandKey() {
        return commandKey;
    }

    public void setCommandKey(String commandKey) {
        this.commandKey = commandKey;
    }
}
