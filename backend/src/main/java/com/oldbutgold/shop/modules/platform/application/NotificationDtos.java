package com.oldbutgold.shop.modules.platform.application;

import java.time.Instant;
import java.util.List;

public final class NotificationDtos {
    private NotificationDtos() {}
    public record Item(long id,String type,String title,String content,boolean read,Instant createdAt,
                       Instant readAt,String destination,Long productId,Long userId,Long conversationId) {
        public Item(long id,String type,String title,String content,boolean read,Instant createdAt,Instant readAt,String destination,Long productId,Long userId){this(id,type,title,content,read,createdAt,readAt,destination,productId,userId,null);}
    }
    public record Page(List<Item> items,int page,int size,long totalElements,long unreadCount) {}
}
