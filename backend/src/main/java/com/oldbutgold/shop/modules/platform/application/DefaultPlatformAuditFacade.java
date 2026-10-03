package com.oldbutgold.shop.modules.platform.application;

import com.oldbutgold.shop.modules.platform.infrastructure.persistence.AuditLogEntity;
import com.oldbutgold.shop.modules.platform.infrastructure.persistence.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;

@Service
public class DefaultPlatformAuditFacade implements PlatformAuditFacade {
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public DefaultPlatformAuditFacade(AuditLogRepository auditLogRepository, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAudit(Long userId, String action, String entityType, Long entityId,
                            Map<String, Object> oldValues, Map<String, Object> newValues,
                            String ipAddress, String requestId) {
        AuditLogEntity auditLog = new AuditLogEntity(
                userId,
                action,
                entityType,
                entityId,
                oldValues,
                newValues,
                requestId,
                clock.instant()
        );
        auditLogRepository.save(auditLog);
    }
}
