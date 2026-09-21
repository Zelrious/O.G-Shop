package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.identity.infrastructure.persistence.SellerVerificationEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.SellerVerificationRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DefaultIdentityCatalogFacade implements IdentityCatalogFacade {
    private final UserRepository userRepository;
    private final SellerVerificationRepository verificationRepository;

    public DefaultIdentityCatalogFacade(UserRepository userRepository,
                                        SellerVerificationRepository verificationRepository) {
        this.userRepository = userRepository;
        this.verificationRepository = verificationRepository;
    }

    @Override
    public boolean isSellerActive(long userId) {
        return userRepository.findById(userId)
                .filter(u -> "ACTIVE".equals(u.getStatus()))
                .filter(u -> u.getRoles().stream().anyMatch(r -> "SELLER".equals(r.getRoleName())))
                .isPresent();
    }

    @Override
    public Optional<SellerPublicSummary> getSellerSummary(long userId) {
        Optional<UserEntity> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        UserEntity user = userOpt.get();
        Optional<SellerVerificationEntity> verificationOpt =
                verificationRepository.findFirstByUserIdAndStatus(userId, "VERIFIED");
        String trustLabel = determineTrustLabel(verificationOpt);
        return Optional.of(new SellerPublicSummary(user.getId(), user.getFullName(), trustLabel));
    }

    @Override
    public Map<Long, SellerPublicSummary> getSellerSummaries(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UserEntity> users = userRepository.findAllById(userIds);
        List<SellerVerificationEntity> verifications =
                verificationRepository.findByUserIdInAndStatus(userIds, "VERIFIED");
        Map<Long, SellerVerificationEntity> verificationMap = verifications.stream()
                .collect(Collectors.toMap(
                        v -> v.getUser().getId(),
                        v -> v,
                        (existing, replacement) -> existing
                ));

        Map<Long, SellerPublicSummary> results = new HashMap<>();
        for (UserEntity user : users) {
            Optional<SellerVerificationEntity> verificationOpt =
                    Optional.ofNullable(verificationMap.get(user.getId()));
            String trustLabel = determineTrustLabel(verificationOpt);
            results.put(user.getId(), new SellerPublicSummary(user.getId(), user.getFullName(), trustLabel));
        }
        return results;
    }

    private String determineTrustLabel(Optional<SellerVerificationEntity> verificationOpt) {
        if (verificationOpt.isPresent()) {
            String method = verificationOpt.get().getVerificationMethod();
            if ("MVP_BYPASS".equalsIgnoreCase(method)) {
                return "Người bán MVP";
            }
            if ("AI_EKYC".equalsIgnoreCase(method)) {
                return "Đã xác minh eKYC";
            }
            return "Đã kích hoạt quyền bán hàng";
        }
        return "Người bán";
    }
}
