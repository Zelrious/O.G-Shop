package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.RegistrationCommerceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class DefaultRegistrationCommerceFacade implements RegistrationCommerceFacade {
    private final RegistrationCommerceRepository repository;
    public DefaultRegistrationCommerceFacade(RegistrationCommerceRepository repository) { this.repository = repository; }

    @Override public void grantRegistrationReward(long userId) { repository.creditRegistration(userId); }

    @Override public AddressPair createSellerAddresses(long userId, String name, String phone,
                                                       Address pickup, Address returns) {
        long pickupId = repository.createAddress(userId, name, phone, pickup, true);
        long returnId = pickup.equals(returns) ? pickupId : repository.createAddress(userId, name, phone, returns, false);
        return new AddressPair(pickupId, returnId);
    }
}
