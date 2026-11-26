package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.commerce.api.VoucherDtos.Campaign;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.VoucherWalletRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class DefaultVoucherCompensationFacade implements VoucherCompensationFacade {

    private final VoucherWalletRepository voucherWalletRepository;

    public DefaultVoucherCompensationFacade(VoucherWalletRepository voucherWalletRepository) {
        this.voucherWalletRepository = voucherWalletRepository;
    }

    @Override
    public Optional<Campaign> campaign(long voucherId, boolean lock) {
        return voucherWalletRepository.campaign(voucherId, lock);
    }

    @Override
    public boolean isRevisionRevoked(long revisionId) {
        return voucherWalletRepository.revisionRevoked(revisionId);
    }

    @Override
    public UUID grantCompensationVoucher(
            long actorId,
            long recipientId,
            Campaign campaign,
            String source,
            String reason,
            String commandKey,
            Instant grantedAt
    ) {
        return voucherWalletRepository.grant(actorId, recipientId, campaign, source, reason, commandKey, grantedAt);
    }
}

