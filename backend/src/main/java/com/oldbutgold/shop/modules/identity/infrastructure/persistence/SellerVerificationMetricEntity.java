package com.oldbutgold.shop.modules.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "seller_verification_metrics")
public class SellerVerificationMetricEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "metric_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "verification_id", nullable = false, unique = true)
    private SellerVerificationEntity verification;

    @Column(name = "match_distance", precision = 5, scale = 4)
    private BigDecimal matchDistance;

    @Column(name = "threshold_used", precision = 5, scale = 4)
    private BigDecimal thresholdUsed;

    @Column(name = "model_name", nullable = false, length = 80)
    private String modelName;

    @Column(name = "model_version", nullable = false, length = 80)
    private String modelVersion;

    @Column(name = "is_simulated", nullable = false)
    private boolean simulated;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected SellerVerificationMetricEntity() {
    }

    public SellerVerificationMetricEntity(Long userId, SellerVerificationEntity verification,
                                          BigDecimal matchDistance, BigDecimal thresholdUsed,
                                          String modelName, String modelVersion, boolean simulated,
                                          Instant processedAt) {
        this.userId = userId;
        this.verification = verification;
        this.matchDistance = matchDistance;
        this.thresholdUsed = thresholdUsed;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.simulated = simulated;
        this.processedAt = processedAt;
    }
}
