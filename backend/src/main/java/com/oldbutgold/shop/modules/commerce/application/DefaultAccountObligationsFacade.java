package com.oldbutgold.shop.modules.commerce.application;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.AccountOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Service
public class DefaultAccountObligationsFacade implements AccountObligationsFacade {
    private final AccountOrderRepository repository;
    public DefaultAccountObligationsFacade(AccountOrderRepository repository) { this.repository=repository; }
    @Transactional(readOnly=true) public List<OrderObligation> orders(long userId) { return repository.orders(userId); }
}
