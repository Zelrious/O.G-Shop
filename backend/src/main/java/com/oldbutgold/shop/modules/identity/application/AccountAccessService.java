package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.identity.infrastructure.persistence.AccountManagementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountAccessService {
    private final AccountManagementRepository repository;
    public AccountAccessService(AccountManagementRepository repository) { this.repository=repository; }
    @Transactional(readOnly=true) public boolean restricted(long id,String scope) { return repository.restricted(id,scope); }
    @Transactional(readOnly=true,noRollbackFor=RegistrationException.class) public void requireAllowed(long id,String scope) {
        if(repository.restricted(id,scope)) throw new RegistrationException("ACCOUNT_ACTION_RESTRICTED",
            "Tài khoản đang bị hạn chế thao tác này. Bạn vẫn có thể xem lịch sử và xử lý nghĩa vụ của đơn cũ.",403);
    }
}
