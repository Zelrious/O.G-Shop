package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RoleRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.SellerVerificationEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.SellerVerificationRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserRepository;
import com.oldbutgold.shop.shared.config.SellerVerificationProperties;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class SellerActivationService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final SellerVerificationRepository verifications;
    private final SellerVerificationProperties properties;
    private final Clock clock;

    public SellerActivationService(UserRepository users, RoleRepository roles,
                                   SellerVerificationRepository verifications,
                                   SellerVerificationProperties properties,
                                   Clock clock) {
        this.users = users;
        this.roles = roles;
        this.verifications = verifications;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ActivationResult activate(long userId) {
        if (!properties.isMvpBypass()) {
            throw new SellerActivationDisabledException("Chế độ kích hoạt Seller tức thì hiện không khả dụng.");
        }

        UserEntity user = users.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Account is unavailable"));

        Instant now = clock.instant();
        grantSellerRole(user);

        var existingVerified = verifications.findFirstByUserIdAndStatus(userId, "VERIFIED");
        if (existingVerified.isPresent()) {
            SellerVerificationEntity v = existingVerified.get();
            return new ActivationResult(v.getId(), v.getStatus(), v.getVerificationMethod());
        }

        var existingPending = verifications.findFirstByUserIdAndStatus(userId, "PENDING");
        if (existingPending.isPresent()) {
            SellerVerificationEntity v = existingPending.get();
            v.upgradeToMvpBypass(now);
            return new ActivationResult(v.getId(), v.getStatus(), v.getVerificationMethod());
        }

        SellerVerificationEntity newVerification = verifications.save(
                new SellerVerificationEntity(user, "MVP_BYPASS", now)
        );
        return new ActivationResult(newVerification.getId(), newVerification.getStatus(), newVerification.getVerificationMethod());
    }

    private void grantSellerRole(UserEntity user) {
        boolean alreadySeller = user.getRoles().stream()
                .anyMatch(role -> "SELLER".equals(role.getRoleName()));
        if (!alreadySeller) {
            user.getRoles().add(roles.findByRoleName("SELLER")
                    .orElseThrow(() -> new IllegalStateException("SELLER role has not been seeded")));
        }
    }

    public record ActivationResult(long verificationId, String status, String verificationMethod) {
    }
}
