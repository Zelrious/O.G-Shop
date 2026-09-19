package com.oldbutgold.shop.modules.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "seller_verifications")
public class SellerVerificationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "verification_method", nullable = false, length = 30)
    private String verificationMethod;

    @Column(nullable = false, length = 20)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "document_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> documentData;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected SellerVerificationEntity() {
    }

    public SellerVerificationEntity(UserEntity user, Instant now) {
        this.user = user;
        this.verificationMethod = "AI_EKYC";
        this.status = "VERIFIED";
        this.documentData = Map.of();
        this.submittedAt = now;
        this.reviewedAt = now;
    }

    public Long getId() { return id; }
    public UserEntity getUser() { return user; }
    public String getStatus() { return status; }
}
