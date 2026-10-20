package com.oldbutgold.shop.modules.platform.application;

import com.oldbutgold.shop.modules.platform.infrastructure.persistence.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

@Service
public class NotificationService implements PlatformNotificationFacade {
    private final NotificationRepository repository;
    public NotificationService(NotificationRepository repository) { this.repository=repository; }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void enqueue(String eventKey,long productId,List<Notice> notices) { repository.enqueue(eventKey,productId,notices); }
    @Transactional public int dispatchBatch() { return repository.dispatchBatch(); }
    @Transactional(readOnly=true)
    public NotificationDtos.Page list(long userId,int page,int size) {
        int p=Math.max(0,page),s=Math.min(50,Math.max(1,size));
        return new NotificationDtos.Page(repository.list(userId,p,s),p,s,
            repository.count(userId,false),repository.count(userId,true));
    }
    @Transactional public void markRead(long userId,long id) {
        if(!repository.markRead(userId,id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy thông báo.");
    }
    @Transactional public void markAllRead(long userId) {
        repository.markAllRead(userId);
    }
}
