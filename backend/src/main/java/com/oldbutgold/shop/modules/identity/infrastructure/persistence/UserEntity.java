package com.oldbutgold.shop.modules.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "users", schema = "og_compat")
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "bank_account_number", length = 50)
    private String bankAccountNumber;

    @Column(name = "bank_account_holder", length = 120)
    private String bankAccountHolder;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            schema = "og_compat",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<RoleEntity> roles = new LinkedHashSet<>();

    protected UserEntity() {
    }

    public UserEntity(String email, String passwordHash, String fullName, String phoneNumber, Instant createdAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.status = "ACTIVE";
        this.createdAt = createdAt;
    }

    public void updateProfile(String fullName, String phoneNumber, String avatarUrl, Instant now) {
        if (fullName != null && !fullName.isBlank()) {
            this.fullName = fullName.trim();
        }
        if (phoneNumber != null) {
            this.phoneNumber = phoneNumber.trim().isBlank() ? null : phoneNumber.trim();
        }
        if (avatarUrl != null) {
            this.avatarUrl = avatarUrl.trim().isBlank() ? null : avatarUrl.trim();
        }
        this.updatedAt = now;
    }

    public void updatePassword(String passwordHash, Instant now) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash must not be empty");
        }
        this.passwordHash = passwordHash;
        this.updatedAt = now;
    }

    public void updateBankAccount(String bankName, String bankAccountNumber, String bankAccountHolder, Instant now) {
        this.bankName = bankName != null && !bankName.isBlank() ? bankName.trim() : null;
        this.bankAccountNumber = bankAccountNumber != null && !bankAccountNumber.isBlank() ? bankAccountNumber.trim() : null;
        this.bankAccountHolder = bankAccountHolder != null && !bankAccountHolder.isBlank() ? bankAccountHolder.trim().toUpperCase() : null;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getBankName() { return bankName; }
    public String getBankAccountNumber() { return bankAccountNumber; }
    public String getBankAccountHolder() { return bankAccountHolder; }
    public Set<RoleEntity> getRoles() { return roles; }
}
