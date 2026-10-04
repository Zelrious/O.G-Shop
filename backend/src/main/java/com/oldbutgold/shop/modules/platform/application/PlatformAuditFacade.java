package com.oldbutgold.shop.modules.platform.application;

import java.util.Map;

public interface PlatformAuditFacade {
    void recordAudit(Long userId, String action, String entityType, Long entityId,
                     Map<String, Object> oldValues, Map<String, Object> newValues,
                     String ipAddress, String requestId);
}
