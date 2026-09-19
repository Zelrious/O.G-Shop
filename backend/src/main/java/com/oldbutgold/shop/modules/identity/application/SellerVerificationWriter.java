package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.identity.infrastructure.ekyc.EkycClient;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RoleRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.SellerVerificationEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.SellerVerificationMetricEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.SellerVerificationMetricRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.SellerVerificationRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;

@Service
public class SellerVerificationWriter {
    private final UserRepository users;
    private final RoleRepository roles;
    private final SellerVerificationRepository verifications;
    private final SellerVerificationMetricRepository metrics;
    private final Clock clock;

    public SellerVerificationWriter(UserRepository users, RoleRepository roles,
                                    SellerVerificationRepository verifications,
                                    SellerVerificationMetricRepository metrics, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.verifications = verifications;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public long recordVerified(long userId, EkycClient.MatchResult match) {
        UserEntity user = users.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Account is unavailable"));
        var existing = verifications.findFirstByUserIdAndStatus(userId, "VERIFIED");
        if (existing.isPresent()) {
            grantSeller(user);
            return existing.get().getId();
        }
        var now = clock.instant();
        SellerVerificationEntity verification = verifications.save(new SellerVerificationEntity(user, now));
        metrics.save(new SellerVerificationMetricEntity(
                userId,
                verification,
                BigDecimal.valueOf(match.distance()),
                BigDecimal.valueOf(match.thresholdUsed()),
                safeMetadata(match.modelName(), "ArcFace"),
                safeMetadata(match.modelVersion(), "unknown"),
                match.simulated(),
                now
        ));
        grantSeller(user);
        return verification.getId();
    }

    private void grantSeller(UserEntity user) {
        boolean alreadySeller = user.getRoles().stream().anyMatch(role -> "SELLER".equals(role.getRoleName()));
        if (!alreadySeller) {
            user.getRoles().add(roles.findByRoleName("SELLER")
                    .orElseThrow(() -> new IllegalStateException("SELLER role has not been seeded")));
        }
    }

    private static String safeMetadata(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.length() <= 80 ? value : value.substring(0, 80);
    }
}
