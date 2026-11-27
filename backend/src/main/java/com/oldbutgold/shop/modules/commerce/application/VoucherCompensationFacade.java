package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.commerce.api.VoucherDtos.Campaign;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Module-boundary facade allowing trustsafety to query and grant compensation vouchers
 * without coupling directly to commerce persistence internals.
 */
public interface VoucherCompensationFacade {

    Optional<Campaign> campaign(long voucherId, boolean lock);

    boolean isRevisionRevoked(long revisionId);

    UUID grantCompensationVoucher(
            long actorId,
            long recipientId,
            Campaign campaign,
            String source,
            String reason,
            String commandKey,
            Instant grantedAt
    );
}

