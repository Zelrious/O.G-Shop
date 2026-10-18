package com.oldbutgold.shop.modules.platform.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="app.notifications.dispatch-enabled",havingValue="true",matchIfMissing=true)
public class NotificationDispatcher {
    private final NotificationService service;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(NotificationDispatcher.class);
    public NotificationDispatcher(NotificationService service) { this.service=service; }
    @Scheduled(fixedDelayString="${app.notifications.dispatch-delay-ms:5000}")
    public void dispatch() {
        try { service.dispatchBatch(); }
        catch(RuntimeException e) { LOG.warn("In-app notification delivery deferred ({})",e.getClass().getSimpleName()); }
    }
}
