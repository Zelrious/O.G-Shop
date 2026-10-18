package com.oldbutgold.shop.modules.payment.application;

import com.oldbutgold.shop.modules.payment.infrastructure.persistence.ListingFeeReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;

@Service
@Transactional(propagation=Propagation.MANDATORY)
public class DefaultListingFeePaymentFacade implements ListingFeePaymentFacade {
    private final ListingFeeReceiptRepository repository;
    public DefaultListingFeePaymentFacade(ListingFeeReceiptRepository repository) { this.repository=repository; }
    @Override public boolean isReplay(UUID chargeId,BigDecimal amount,String key) { return repository.isReplay(chargeId,amount,key); }
    @Override public void confirmMock(UUID chargeId,BigDecimal amount,String key) { repository.confirm(chargeId,amount,key); }
}
