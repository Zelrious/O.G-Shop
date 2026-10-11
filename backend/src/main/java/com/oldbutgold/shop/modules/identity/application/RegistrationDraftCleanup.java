package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RegistrationRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name="app.registration.cleanup-enabled", havingValue="true")
public class RegistrationDraftCleanup {
    private final RegistrationRepository repository;
    private final com.oldbutgold.shop.modules.identity.infrastructure.persistence.AuthFlowRepository authFlows;
    public RegistrationDraftCleanup(RegistrationRepository repository,com.oldbutgold.shop.modules.identity.infrastructure.persistence.AuthFlowRepository authFlows) {
        this.repository = repository;this.authFlows=authFlows;
    }

    @Scheduled(fixedDelay=60000, initialDelay=60000)
    @Transactional
    public void expireDrafts() { repository.expireDrafts();authFlows.expire(); }
}
