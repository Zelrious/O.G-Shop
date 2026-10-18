package com.oldbutgold.shop.modules.trustsafety.application;
import java.time.Instant;
import java.util.*;
public interface AccountTrustFacade {
    record ReputationEvent(String id,String source,int pointsDelta,String reason,Long decidedBy,Instant createdAt) {}
    record Reputation(int score,List<ReputationEvent> events) {}
    record CaseSummary(long caseId,Long orderId,String type,String state,String summary,Instant createdAt,Instant responseDeadline,boolean canRespond,boolean canCompensate) {}
    record Outage(long systemCaseId,Instant affectedFrom,Instant affectedUntil) {}
    record Response(UUID id,long actorId,String text,Instant createdAt) {}
    Reputation reputation(long userId);
    List<CaseSummary> cases(long userId);
    List<Outage> outages(long userId);
    List<Response> responses(long userId,long caseId);
    UUID respond(long userId,long caseId,String text,String commandKey);
    boolean confirmedSystemIssue(long userId,long caseId);
    boolean confirmedOutage(long caseId,Instant from,Instant until);
    void compensateDeadline(long actorId,long userId,long caseId,long systemCaseId,Instant from,Instant until,String reason,String key);
}
