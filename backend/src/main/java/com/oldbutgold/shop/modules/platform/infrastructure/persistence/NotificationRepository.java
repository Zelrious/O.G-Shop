package com.oldbutgold.shop.modules.platform.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.platform.application.PlatformNotificationFacade.Notice;
import com.oldbutgold.shop.modules.platform.application.NotificationDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class NotificationRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public NotificationRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }

    public void enqueue(String eventKey, long productId, List<Notice> notices) {
        if (notices.isEmpty()) return;
        UUID id=UUID.nameUUIDFromBytes(("og-listing:"+eventKey).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        boolean account=notices.getFirst().destination().name().contains("ACCOUNT");
        boolean conversation=notices.getFirst().destination().name().equals("CONVERSATION");
        boolean voucher=notices.getFirst().destination().name().contains("VOUCHER");
        jdbc.update("""
            INSERT INTO public.outbox_events(event_id,aggregate_type,aggregate_id,event_type,payload)
            VALUES(?,?,?,?,?::jsonb) ON CONFLICT(event_id) DO NOTHING
            """,id,voucher?"VOUCHER":conversation?"CONVERSATION":account?"USER":"PRODUCT",Long.toString(productId),voucher?"VOUCHER_NOTIFICATION":conversation?"CHAT_NOTIFICATION":account?"ACCOUNT_NOTIFICATION":"LISTING_NOTIFICATION",encode(Map.of("notices",notices)));
    }

    public int dispatchBatch() {
        var events=jdbc.query("""
            SELECT event_id,payload::text FROM public.outbox_events
            WHERE record_type='outbox_events' AND event_type IN ('LISTING_NOTIFICATION','ACCOUNT_NOTIFICATION','CHAT_NOTIFICATION','VOUCHER_NOTIFICATION')
              AND published_at IS NULL AND available_at<=CURRENT_TIMESTAMP
            ORDER BY occurred_at,event_id LIMIT 50 FOR UPDATE SKIP LOCKED
            """,(rs,row)->Map.entry(rs.getObject(1,UUID.class),rs.getString(2)));
        for(var event:events) {
            for(Notice n:decode(event.getValue())) {
                boolean account=n.destination().name().contains("ACCOUNT");
                boolean conversation=n.destination().name().equals("CONVERSATION");
                boolean voucher=n.destination().name().contains("VOUCHER");
                jdbc.update("""
                    INSERT INTO public.notifications(user_id,type,title,content,reference_type,reference_id,
                        event_id,reference_revision,target_data)
                    VALUES(?,?,?,?,?,?,?,?,?::jsonb)
                    ON CONFLICT(event_id,user_id) WHERE record_type='notifications' AND event_id IS NOT NULL DO NOTHING
                    """,n.recipientId(),n.type(),n.title(),n.content(),voucher?"VOUCHER":conversation?"CONVERSATION":account?"USER":"PRODUCT",n.productId(),event.getKey(),n.revision(),
                        encode(Map.of("destination",n.destination().name(),voucher?"voucherId":conversation?"conversationId":account?"userId":"productId",n.productId())));
            }
            jdbc.update("UPDATE public.outbox_events SET published_at=GREATEST(occurred_at,CURRENT_TIMESTAMP),attempt_count=attempt_count+1,last_error=NULL WHERE event_id=?",event.getKey());
        }
        return events.size();
    }

    public List<NotificationDtos.Item> list(long userId,int page,int size) {
        return jdbc.query("""
            SELECT notification_id AS id,type,title,content,is_read AS read,n.created_at AS "createdAt",
                read_at AS "readAt",
                COALESCE(target_data->>'destination', CASE WHEN n.type IN ('ORDER_REVIEW_RECEIVED','ORDER_REVIEW_UPDATED') THEN 'ORDER_DETAIL' END) AS destination,
                CASE WHEN reference_type='PRODUCT' THEN reference_id END AS "productId",
                CASE WHEN reference_type='USER' THEN reference_id END AS "targetUserId",
                CASE WHEN reference_type='CONVERSATION' THEN reference_id END AS "conversationId",
                o.order_id AS "orderId", CASE WHEN o.buyer_id=n.user_id THEN 'BUYER' WHEN o.seller_id=n.user_id THEN 'SELLER' END AS "orderRole"
            FROM public.notifications n
            LEFT JOIN og_compat.cases c ON n.reference_type='CASE' AND n.target_data->>'destination'='ORDER_DETAIL' AND c.case_id=n.reference_id
            LEFT JOIN og_compat.orders o ON (n.target_data->>'destination'='ORDER_DETAIL' OR n.type IN ('ORDER_REVIEW_RECEIVED','ORDER_REVIEW_UPDATED'))
                AND o.order_id=COALESCE(
                    CASE WHEN n.reference_type='ORDER' THEN n.reference_id END,
                    CASE WHEN (n.target_data->>'orderId') ~ '^[0-9]+$' THEN (n.target_data->>'orderId')::bigint END,
                    c.order_id
                )
                AND n.user_id IN (o.buyer_id,o.seller_id)
            WHERE n.record_type='notifications' AND n.user_id=?
            ORDER BY n.created_at DESC,notification_id DESC LIMIT ? OFFSET ?
            """,(rs,row)->new NotificationDtos.Item(rs.getLong("id"),rs.getString("type"),rs.getString("title"),
                rs.getString("content"),rs.getBoolean("read"),rs.getTimestamp("createdAt").toInstant(),
                rs.getTimestamp("readAt")==null?null:rs.getTimestamp("readAt").toInstant(),rs.getString("destination"),
                rs.getObject("productId",Long.class),rs.getObject("targetUserId",Long.class),rs.getObject("conversationId",Long.class),rs.getObject("orderId",Long.class),rs.getString("orderRole")),userId,size,(long)page*size);
    }
    public long count(long userId,boolean unread) {
        return jdbc.queryForObject("SELECT count(*) FROM public.notifications WHERE record_type='notifications' AND user_id=?"+(unread?" AND NOT is_read":""),Long.class,userId);
    }
    public boolean markRead(long userId,long id) {
        return jdbc.update("""
            UPDATE public.notifications SET is_read=true,read_at=COALESCE(read_at,GREATEST(created_at,CURRENT_TIMESTAMP))
            WHERE record_type='notifications' AND notification_id=? AND user_id=?
            """,id,userId)==1;
    }
    public int markAllRead(long userId) {
        return jdbc.update("""
            UPDATE public.notifications SET is_read=true,read_at=COALESCE(read_at,GREATEST(created_at,CURRENT_TIMESTAMP))
            WHERE record_type='notifications' AND user_id=? AND NOT is_read
            """,userId);
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch(JsonProcessingException e) { throw new IllegalStateException("Cannot encode notification",e); }
    }
    private List<Notice> decode(String value) {
        try { return json.readValue(json.readTree(value).get("notices").toString(),new TypeReference<>(){}); }
        catch(JsonProcessingException e) { throw new IllegalStateException("Cannot decode notification",e); }
    }
}
