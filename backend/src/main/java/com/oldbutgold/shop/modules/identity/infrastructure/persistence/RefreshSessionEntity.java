package com.oldbutgold.shop.modules.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_sessions")
public class RefreshSessionEntity {
    @Id
    @Column(name = "session_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_digest", nullable = false, unique = true, length = 64)
    private String tokenDigest;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    @Column(name = "created_by_ip", length = 45)
    private String createdByIp;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    protected RefreshSessionEntity() {
    }

    public RefreshSessionEntity(UUID id, UserEntity user, UUID familyId, String tokenDigest,
                                Instant issuedAt, Instant expiresAt, String createdByIp, String userAgent) {
        this.id = id;
        this.user = user;
        this.familyId = familyId;
        this.tokenDigest = tokenDigest;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.createdByIp = createdByIp;
        this.userAgent = userAgent;
    }

    public UUID getId() { return id; }
    public UserEntity getUser() { return user; }
    public UUID getFamilyId() { return familyId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void consume(Instant at, UUID replacementId) { this.consumedAt = at; this.replacedBy = replacementId; }
    public void revoke(Instant at) { if (this.revokedAt == null) this.revokedAt = at; }
}
