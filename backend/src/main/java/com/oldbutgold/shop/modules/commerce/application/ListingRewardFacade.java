package com.oldbutgold.shop.modules.commerce.application;

public interface ListingRewardFacade {
    /** Returns true only when this command granted the user's first listing reward. */
    boolean grantFirstListing(long sellerId,long productId);
}
