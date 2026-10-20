package com.oldbutgold.shop.modules.communication.infrastructure.persistence;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.oldbutgold.shop.modules.communication.api.ChatDtos.*;
import com.oldbutgold.shop.modules.communication.application.CommunicationException;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
@Repository
public class ChatRepository {
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    public ChatRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    public record Thread(long id,long buyer,long seller,long product,String title,String thumbnail,Instant activity,String status) {}
    private Thread mapThread(ResultSet r,int n)throws SQLException{return new Thread(r.getLong("conversation_id"),r.getLong("buyer_id"),r.getLong("seller_id"),r.getLong("product_id"),r.getString("product_title_snapshot"),r.getString("product_thumbnail_snapshot"),r.getTimestamp("last_activity_at").toInstant(),r.getString("status"));}
    public Optional<Thread> owned(long actor,long id,boolean lock){return jdbc.query("SELECT * FROM public.conversations WHERE record_type='conversations' AND conversation_id=? AND ? IN(buyer_id,seller_id)"+(lock?" FOR UPDATE":""),this::mapThread,id,actor).stream().findFirst();}
    public Optional<Thread> byProduct(long buyer,long seller,long product){return jdbc.query("SELECT * FROM public.conversations WHERE record_type='conversations' AND buyer_id=? AND seller_id=? AND product_id=?",this::mapThread,buyer,seller,product).stream().findFirst();}
    public Optional<Thread> findBuyerProductConversation(long buyer,long product){return jdbc.query("SELECT * FROM public.conversations WHERE record_type='conversations' AND buyer_id=? AND product_id=? ORDER BY conversation_id DESC LIMIT 1",this::mapThread,buyer,product).stream().findFirst();}
    public Optional<Thread> thread(long id){return jdbc.query("SELECT * FROM public.conversations WHERE record_type='conversations' AND conversation_id=?",this::mapThread,id).stream().findFirst();}
    public List<Thread> list(long actor,String query,int page,int size){
        return jdbc.query("""
            SELECT c.* FROM public.conversations c WHERE c.record_type='conversations' AND ? IN(c.buyer_id,c.seller_id)
            AND c.product_title_snapshot ILIKE '%'||?||'%' ORDER BY c.last_activity_at DESC,c.conversation_id DESC LIMIT ? OFFSET ?
            """,this::mapThread,actor,query,size,(long)page*size);
    }
    public long count(long actor,String query){return jdbc.queryForObject("SELECT count(*) FROM public.conversations WHERE record_type='conversations' AND ? IN(buyer_id,seller_id) AND product_title_snapshot ILIKE '%'||?||'%'",Long.class,actor,query);}
    public long unread(long actor,long conversation){return jdbc.queryForObject("""
        SELECT count(*) FROM og_compat.messages m JOIN og_compat.conversation_user_state s USING(conversation_id)
        WHERE s.user_id=? AND m.sender_id<>? AND m.deleted_at IS NULL AND m.message_id>COALESCE(s.last_read_message_id,0)
        """+(conversation>0?" AND m.conversation_id=?":""),Long.class,conversation>0?new Object[]{actor,actor,conversation}:new Object[]{actor,actor});}
    public String preview(long id){return jdbc.query("SELECT CASE WHEN message_type='TEXT' THEN left(content,90) WHEN message_type='OFFER' THEN 'Đề nghị trả giá' WHEN message_type='IMAGE' THEN 'Hình ảnh' WHEN message_type='VIDEO' THEN 'Video' ELSE 'Cập nhật hội thoại' END FROM og_compat.messages WHERE conversation_id=? AND deleted_at IS NULL ORDER BY message_id DESC LIMIT 1",(r,n)->r.getString(1),id).stream().findFirst().orElse("");}
    public long create(long buyer,long seller,long product,String title,BigDecimal price,String thumbnail,Instant now){
        long id=jdbc.queryForObject("""
            INSERT INTO og_compat.conversations(buyer_id,seller_id,product_id,product_title_snapshot,product_price_at_start,product_thumbnail_snapshot,currency,last_activity_at)
            VALUES(?,?,?,?,?,?,'VND',?) RETURNING conversation_id
            """,Long.class,buyer,seller,product,title,price,thumbnail,Timestamp.from(now));
        for(long user:new long[]{buyer,seller})jdbc.update("INSERT INTO og_compat.conversation_user_state(conversation_id,user_id) VALUES(?,?)",id,user);
        return id;
    }
    public void linkOrder(long id,long order){if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM og_compat.conversation_order_links WHERE conversation_id=? AND order_id=?)",Boolean.class,id,order)))jdbc.update("INSERT INTO og_compat.conversation_order_links(conversation_id,order_id) VALUES(?,?)",id,order);}
    public boolean blocked(long buyer,long seller){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM public.seller_buyer_blocks WHERE seller_id=? AND buyer_id=? AND unblocked_at IS NULL)",Boolean.class,seller,buyer));}
    public long peerRead(long id,long actor){return jdbc.queryForObject("SELECT COALESCE(max(last_read_message_id),0) FROM og_compat.conversation_user_state WHERE conversation_id=? AND user_id<>?",Long.class,id,actor);}
    public void read(long id,long actor,long message){
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM og_compat.messages WHERE conversation_id=? AND message_id=?)",Boolean.class,id,message)))throw new CommunicationException(400,"READ_CURSOR_INVALID","Tin nhắn không thuộc hội thoại.");
        jdbc.update("UPDATE og_compat.conversation_user_state SET last_read_message_id=?,last_read_at=CURRENT_TIMESTAMP WHERE conversation_id=? AND user_id=? AND COALESCE(last_read_message_id,0)<?",message,id,actor,message);
    }
    public record StoredMessage(long id,long conversation,long sender,String type,String content,Instant created,Long reply,Long offer,String object,Map<String,Object> metadata,String mediaStatus,Instant expires) {}
    private StoredMessage stored(ResultSet r,int n)throws SQLException{
        return new StoredMessage(r.getLong("message_id"),r.getLong("conversation_id"),r.getLong("sender_id"),r.getString("message_type"),r.getString("content"),r.getTimestamp("created_at").toInstant(),r.getObject("reply_to_message_id",Long.class),r.getObject("offer_id",Long.class),r.getString("cloudinary_public_id"),decode(r.getString("media_metadata")),r.getString("media_status"),instant(r,"media_expires_at"));
    }
    public Optional<StoredMessage> message(long id){return jdbc.query("SELECT * FROM og_compat.messages WHERE message_id=? AND deleted_at IS NULL",this::stored,id).stream().findFirst();}
    public Optional<StoredMessage> ownedMessage(long actor,long id){return jdbc.query("SELECT m.* FROM og_compat.messages m JOIN og_compat.conversations c USING(conversation_id) WHERE m.message_id=? AND m.deleted_at IS NULL AND ? IN(c.buyer_id,c.seller_id)",this::stored,id,actor).stream().findFirst();}
    public Optional<StoredMessage> lockMessage(long id){return jdbc.query("SELECT * FROM public.messages WHERE record_type='messages' AND message_id=? FOR UPDATE",this::stored,id).stream().findFirst();}
    public List<Long> cleanupCandidates(long after){return jdbc.query("SELECT message_id FROM public.messages WHERE record_type='messages' AND message_id>? AND media_metadata->>'managedBy'='CHAT_V31' AND coalesce(media_metadata->>'cleanupDone','false')<>'true' AND media_expires_at<=CURRENT_TIMESTAMP ORDER BY message_id LIMIT 50",(r,n)->r.getLong(1),after);}
    public void expireMedia(long id){jdbc.update("UPDATE public.messages SET media_status='EXPIRED' WHERE message_id=?",id);}
    public void cleanedMedia(long id){jdbc.update("UPDATE public.messages SET media_metadata=media_metadata||jsonb_build_object('cleanupDone',true,'cleanedAt',CURRENT_TIMESTAMP::text) WHERE message_id=? AND media_status='EXPIRED'",id);}
    public Optional<StoredMessage> replay(long conversation,UUID client){return jdbc.query("SELECT * FROM og_compat.messages WHERE conversation_id=? AND client_message_id=?",this::stored,conversation,client).stream().findFirst();}
    public List<StoredMessage> messages(long id,Long before,int size){return jdbc.query("SELECT * FROM og_compat.messages WHERE conversation_id=? AND deleted_at IS NULL"+(before==null?"":" AND message_id<?")+" ORDER BY message_id DESC LIMIT ?",this::stored,before==null?new Object[]{id,size}:new Object[]{id,before,size});}
    public long append(long conversation,long actor,UUID client,String type,String content,Long reply,Long offer,String object,Map<String,Object> metadata,Instant expires){
        long id=jdbc.queryForObject("""
            INSERT INTO og_compat.messages(conversation_id,sender_id,client_message_id,message_type,content,reply_to_message_id,offer_id,cloudinary_public_id,media_metadata,media_expires_at)
            VALUES(?,?,?,?,?,?,?,?,?::jsonb,?) RETURNING message_id
            """,Long.class,conversation,actor,client,type,content,reply,offer,object,metadata==null?null:encode(metadata),expires==null?null:Timestamp.from(expires));
        jdbc.update("UPDATE og_compat.conversations SET last_message_id=?,last_activity_at=CURRENT_TIMESTAMP WHERE conversation_id=?",id,conversation);
        return id;
    }
    public Quota quota(long conversation,long actor){
        var values=jdbc.query("SELECT images_sent,videos_sent,total_bytes_sent,quota_date FROM og_compat.chat_media_daily_quotas WHERE conversation_id=? AND sender_id=? AND quota_date=(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Saigon')::date",
            (r,n)->new Quota(r.getInt(1),r.getInt(2),r.getLong(3),5,1,47185920L,r.getDate(4).toString()),conversation,actor);
        return values.stream().findFirst().orElseGet(()->new Quota(0,0,0,5,1,47185920L,jdbc.queryForObject("SELECT (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Saigon')::date::text",String.class)));
    }
    public void consumeQuota(long conversation,long actor,String type,long bytes){
        Quota q=quota(conversation,actor);int images=q.images()+(type.equals("IMAGE")?1:0),videos=q.videos()+(type.equals("VIDEO")?1:0);
        if(images>5||videos>1||q.bytes()+bytes>47185920L)throw new CommunicationException(429,"CHAT_MEDIA_QUOTA","Đã vượt hạn mức media của hội thoại hôm nay.");
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM og_compat.chat_media_daily_quotas WHERE conversation_id=? AND sender_id=? AND quota_date=?::date)",Boolean.class,conversation,actor,q.date())))jdbc.update("INSERT INTO og_compat.chat_media_daily_quotas(conversation_id,sender_id,quota_date,images_sent,videos_sent,total_bytes_sent) VALUES(?,?,?::date,?,?,?)",conversation,actor,q.date(),images,videos,bytes);
        else jdbc.update("UPDATE og_compat.chat_media_daily_quotas SET images_sent=?,videos_sent=?,total_bytes_sent=?,updated_at=CURRENT_TIMESTAMP WHERE conversation_id=? AND sender_id=? AND quota_date=?::date",images,videos,q.bytes()+bytes,conversation,actor,q.date());
    }
    public List<Block> blocks(long seller){return jdbc.query("SELECT b.buyer_id,u.full_name,b.blocked_at,b.reason FROM public.seller_buyer_blocks b JOIN og_compat.users u ON u.user_id=b.buyer_id WHERE b.seller_id=? AND b.unblocked_at IS NULL ORDER BY b.blocked_at DESC",(r,n)->new Block(r.getLong(1),r.getString(2),r.getTimestamp(3).toInstant(),r.getString(4)),seller);}
    public void block(long seller,long buyer,String reason,boolean active){
        if(active)jdbc.update("""
            INSERT INTO public.seller_buyer_blocks(seller_id,buyer_id,reason) VALUES(?,?,?)
            ON CONFLICT(seller_id,buyer_id) DO UPDATE SET blocked_at=CASE WHEN seller_buyer_blocks.unblocked_at IS NULL THEN seller_buyer_blocks.blocked_at ELSE CURRENT_TIMESTAMP END,unblocked_at=NULL,reason=EXCLUDED.reason
            """,seller,buyer,reason);
        else jdbc.update("UPDATE public.seller_buyer_blocks SET unblocked_at=CURRENT_TIMESTAMP WHERE seller_id=? AND buyer_id=? AND unblocked_at IS NULL",seller,buyer);
    }
    public List<Map<String,Object>> searchBuyers(long seller,String query){return jdbc.queryForList("""
        SELECT u.user_id AS "userId",u.full_name AS name FROM og_compat.users u
        WHERE u.user_id<>? AND u.deleted_at IS NULL AND (public.og_has_role(u.user_id,'BUYER') OR public.og_has_role(u.user_id,'SELLER'))
          AND NOT public.og_has_role(u.user_id,'KTV') AND NOT public.og_has_role(u.user_id,'ADMIN')
          AND (u.full_name ILIKE '%'||?||'%' OR u.user_id::text=?) ORDER BY u.user_id DESC LIMIT 20
        """,seller,query,query);}
    public Map<String,Object> decode(String value){try{return value==null?Map.of():json.readValue(value,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("Cannot decode chat metadata",e);}}
    public String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Cannot encode chat metadata",e);}}
    private Instant instant(ResultSet r,String column)throws SQLException{var t=r.getTimestamp(column);return t==null?null:t.toInstant();}
}
