package com.oldbutgold.shop.modules.identity.application;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface IdentityCatalogFacade {
    boolean isSellerActive(long userId);

    Optional<SellerPublicSummary> getSellerSummary(long userId);

    Map<Long, SellerPublicSummary> getSellerSummaries(Collection<Long> userIds);

    record SellerPublicSummary(long sellerId, String displayName, String trustLabel) {
    }
}
