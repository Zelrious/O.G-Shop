package com.oldbutgold.shop.shared.storage;

/**
 * Shared boundary guard queried by the public media controller so migrated or blocked
 * private dispute evidence keys can never be served over permitAll /api/v1/media/** routes.
 */
public interface PublicMediaAccessGuard {
    boolean isBlockedPublicMedia(String filename);
}

