package com.oldbutgold.shop.modules.platform.application;

import com.oldbutgold.shop.modules.platform.infrastructure.persistence.GovernanceHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.*;

@Service
public class DefaultGovernanceHistoryFacade implements GovernanceHistoryFacade {
    private final GovernanceHistoryRepository repository;
    public DefaultGovernanceHistoryFacade(GovernanceHistoryRepository repository) { this.repository=repository; }
    @Transactional(readOnly=true) public Optional<Entry> command(String key) { return repository.command(key); }
    @Transactional(propagation=Propagation.MANDATORY) public void record(long actor,long target,String action,String key,Map<String,Object> request,Map<String,Object> before,Map<String,Object> after) { repository.record(actor,target,action,key,request,before,after); }
    @Transactional(readOnly=true) public List<Entry> history(long target,int page,int size) { return repository.history(target,page,size); }
    @Transactional(readOnly=true) public long count(long target) { return repository.count(target); }
    @Transactional(readOnly=true) public boolean hasCompensation(long target,long caseId,String restrictionId) { return repository.hasCompensation(target,caseId,restrictionId); }
}
