package com.oldbutgold.shop.modules.catalog.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "product_moderation_decisions")
public class ProductModerationDecisionEntity {
    public static final String DECISION_APPROVED = "APPROVED";
    public static final String DECISION_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "decision_id")
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Column(name = "decision", nullable = false, length = 20)
    private String decision;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "product_version", nullable = false)
    private Long productVersion;

    @Column(name = "command_key", length = 100)
    private String commandKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProductModerationDecisionEntity() {
    }

    public ProductModerationDecisionEntity(Long productId, Long reviewerId, String decision,
                                           String reason, Long productVersion, String commandKey,
                                           Instant createdAt) {
        this.productId = productId;
        this.reviewerId = reviewerId;
        this.decision = decision;
        this.reason = reason;
        this.productVersion = productVersion;
        this.commandKey = commandKey;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public Long getReviewerId() { return reviewerId; }
    public String getDecision() { return decision; }
    public String getReason() { return reason; }
    public Long getProductVersion() { return productVersion; }
    public String getCommandKey() { return commandKey; }
    public Instant getCreatedAt() { return createdAt; }
}
