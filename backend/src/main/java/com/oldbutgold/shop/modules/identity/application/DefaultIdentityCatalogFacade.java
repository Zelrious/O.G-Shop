package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository;
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
    private final EkycRepository ekycRepository;
    private final AccountAccessService accountAccess;

    public DefaultIdentityCatalogFacade(UserRepository userRepository,
                                        EkycRepository ekycRepository, AccountAccessService accountAccess) {
        this.userRepository = userRepository;
        this.ekycRepository = ekycRepository;
        this.accountAccess = accountAccess;
    }

    @Override public void requireListingAllowed(long userId) { accountAccess.requireAllowed(userId, "NEW_LISTINGS"); }

    @Override
    public boolean isSellerActive(long userId) {
        return userRepository.findById(userId)
                .filter(u -> "ACTIVE".equals(u.getStatus()))
                .filter(u -> u.getRoles().stream().anyMatch(r -> "SELLER".equals(r.getRoleName())))
                .isPresent();
    }

    @Override
    public boolean isModeratorActive(long userId) {
        return userRepository.findById(userId)
                .filter(user -> "ACTIVE".equals(user.getStatus()))
                .map(user -> user.getRoles().stream().anyMatch(role ->
                        "ADMIN".equals(role.getRoleName()) || "KTV".equals(role.getRoleName())))
                .orElse(false);
    }

    @Override public java.util.List<Long> getActiveKtvIds() { return userRepository.findActiveKtvIds(); }

    @Override
    public Optional<SellerPublicSummary> getSellerSummary(long userId) {
        Optional<UserEntity> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        UserEntity user = userOpt.get();
        boolean hasVerifiedEkyc = ekycRepository.hasVerifiedProfile(userId);
        String trustLabel = determineTrustLabel(hasVerifiedEkyc);
        return Optional.of(new SellerPublicSummary(user.getId(), user.getFullName(), trustLabel));
    }

    @Override
    public Map<Long, SellerPublicSummary> getSellerSummaries(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UserEntity> users = userRepository.findAllById(userIds);

        Map<Long, SellerPublicSummary> results = new HashMap<>();
        for (UserEntity user : users) {
            boolean hasVerifiedEkyc = ekycRepository.hasVerifiedProfile(user.getId());
            String trustLabel = determineTrustLabel(hasVerifiedEkyc);
            results.put(user.getId(), new SellerPublicSummary(user.getId(), user.getFullName(), trustLabel));
        }
        return results;
    }

    private String determineTrustLabel(boolean hasVerifiedEkyc) {
        if (hasVerifiedEkyc) {
            return "Đã xác minh eKYC";
        }
        return "Người bán";
    }
}
