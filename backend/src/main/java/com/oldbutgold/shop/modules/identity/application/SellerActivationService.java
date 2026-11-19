package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RoleRepository;
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
    private final EkycRepository ekycRepository;
    private final SellerVerificationProperties properties;
    private final Clock clock;

    public SellerActivationService(UserRepository users, RoleRepository roles,
                                   EkycRepository ekycRepository,
                                   SellerVerificationProperties properties,
                                   Clock clock) {
        this.users = users;
        this.roles = roles;
        this.ekycRepository = ekycRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ActivationResult activate(long userId) {
        if (!properties.isMvpBypass()) {
            throw new SellerActivationDisabledException("Chế độ kích hoạt Seller tức thì hiện không khả dụng.");
        }

        // Require verified eKYC before granting SELLER role
        if (!ekycRepository.hasVerifiedProfile(userId)) {
            throw new SellerActivationDisabledException("Bạn phải hoàn tất xác thực eKYC và được KTV duyệt trước khi đăng ký làm Người bán.");
        }

        UserEntity user = users.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Account is unavailable"));
        if ("UC83".equals(user.getIdentityWorkflow())) {
            throw new SellerActivationDisabledException("Hồ sơ Người bán cần hoàn tất eKYC và được KTV duyệt.");
        }

        Instant now = clock.instant();
        grantSellerRole(user);

        return new ActivationResult(0L, "VERIFIED", "AI_EKYC");
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
