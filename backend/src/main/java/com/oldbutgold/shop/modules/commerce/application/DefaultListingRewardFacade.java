package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.ListingRewardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation=Propagation.MANDATORY)
public class DefaultListingRewardFacade implements ListingRewardFacade {
    private final ListingRewardRepository repository;
    public DefaultListingRewardFacade(ListingRewardRepository repository) { this.repository=repository; }
    @Override public boolean grantFirstListing(long sellerId,long productId) { return repository.grant(sellerId,productId); }
}
