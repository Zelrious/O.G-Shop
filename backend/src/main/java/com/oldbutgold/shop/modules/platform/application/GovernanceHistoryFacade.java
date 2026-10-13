package com.oldbutgold.shop.modules.platform.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface GovernanceHistoryFacade {
    record Entry(long id,long actorId,long targetId,String action,Instant createdAt,
                 Map<String,Object> request,Map<String,Object> before,Map<String,Object> after) {}
    Optional<Entry> command(String key);
    void record(long actorId,long targetId,String action,String key,Map<String,Object> request,
                Map<String,Object> before,Map<String,Object> after);
    List<Entry> history(long targetId,int page,int size);
    long count(long targetId);
    boolean hasCompensation(long targetId,long caseId,String restrictionId);
}
