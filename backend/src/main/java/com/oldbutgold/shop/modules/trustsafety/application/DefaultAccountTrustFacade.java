package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.AccountTrustRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

@Service
public class DefaultAccountTrustFacade implements AccountTrustFacade {
    private final AccountTrustRepository repository;
    public DefaultAccountTrustFacade(AccountTrustRepository repository) { this.repository=repository; }
    @Transactional(readOnly=true) public Reputation reputation(long userId) { return repository.reputation(userId); }
    @Transactional(readOnly=true) public List<CaseSummary> cases(long userId) { return repository.cases(userId); }
    @Transactional(readOnly=true) public List<Outage> outages(long userId) { return repository.outages(userId); }
    @Transactional(readOnly=true) public List<Response> responses(long userId,long caseId) {
        repository.ownedCase(userId,caseId,false).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy hồ sơ trong phạm vi của bạn."));
        return repository.responses(caseId);
    }
    @Transactional public UUID respond(long userId,long caseId,String text,String commandKey) {
        repository.lockCommand(commandKey);
        var prior=repository.command(commandKey);
        if(prior.isPresent()) {
            var p=prior.get();
            if(((Number)p.get("case_id")).longValue()!=caseId || ((Number)p.get("actor_id")).longValue()!=userId || !text.equals(p.get("reason")))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Mã phản hồi đã dùng với nội dung khác.");
            return (UUID)p.get("event_id");
        }
        String state=repository.ownedCase(userId,caseId,true).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy hồ sơ trong phạm vi của bạn."));
        if(Set.of("CLOSED","REJECTED").contains(state)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Hồ sơ đã kết thúc, không nhận phản hồi mới.");
        return repository.respond(userId,caseId,text,commandKey);
    }
    @Transactional(readOnly=true) public boolean confirmedSystemIssue(long userId,long caseId) { return repository.confirmedSystemIssue(userId,caseId); }
    @Transactional(readOnly=true) public boolean confirmedOutage(long caseId,java.time.Instant from,java.time.Instant until) { return repository.confirmedOutage(caseId,from,until); }
    @Transactional public void compensateDeadline(long actor,long userId,long caseId,long systemCaseId,java.time.Instant from,java.time.Instant until,String reason,String key) {
        String state=repository.ownedCase(userId,caseId,true).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không có hồ sơ cũ của tài khoản này."));
        if(Set.of("CLOSED","REJECTED").contains(state)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Hồ sơ đã kết thúc.");
        repository.compensateDeadline(actor,userId,caseId,systemCaseId,from,until,reason,key);
    }
}
