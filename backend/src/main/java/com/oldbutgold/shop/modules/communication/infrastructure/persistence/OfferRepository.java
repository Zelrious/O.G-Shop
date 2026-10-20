package com.oldbutgold.shop.modules.communication.infrastructure.persistence;
import com.oldbutgold.shop.modules.communication.api.ChatDtos.Offer;
import com.oldbutgold.shop.modules.catalog.application.ConversationCatalogFacade.Product;
import com.oldbutgold.shop.modules.commerce.application.ConversationCommerceFacade.Fees;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
@Repository
public class OfferRepository {
    private final JdbcTemplate jdbc;private final ChatRepository chat;
    public OfferRepository(JdbcTemplate jdbc,ChatRepository chat){this.jdbc=jdbc;this.chat=chat;}
    public record Stored(Offer offer,UUID creationKey,Map<String,Object> creation,UUID decisionKey,Map<String,Object> decision,Long revisionId,String checkoutRequest) {}
    private Stored map(ResultSet r,int n)throws SQLException {
        var o=new Offer(r.getLong("offer_id"),r.getLong("conversation_id"),r.getLong("proposer_id"),r.getObject("parent_offer_id",Long.class),r.getBigDecimal("offered_item_price"),r.getString("status"),r.getTimestamp("created_at").toInstant(),instant(r,"expires_at"),instant(r,"responded_at"),r.getObject("responded_by",Long.class),r.getLong("version"),r.getLong("product_version"),r.getObject("order_id",Long.class),r.getBigDecimal("buyer_system_fee"),r.getBigDecimal("seller_system_fee"));
        return new Stored(o,r.getObject("creation_command",UUID.class),chat.decode(r.getString("creation_request")),r.getObject("decision_command",UUID.class),chat.decode(r.getString("decision_request")),r.getObject("product_revision_id",Long.class),r.getString("checkout_request"));
    }
    private static final String SELECT="SELECT a.*,(SELECT i.order_id FROM og_compat.order_items i WHERE i.accepted_offer_id=a.offer_id LIMIT 1) AS order_id FROM public.offers a WHERE a.record_type='offers'";
    public Optional<Stored> get(long id,boolean lock){return jdbc.query(SELECT+" AND a.offer_id=?"+(lock?" FOR UPDATE OF a":""),this::map,id).stream().findFirst();}
    public Optional<Stored> creation(UUID key){return jdbc.query(SELECT+" AND a.creation_command=?",this::map,key).stream().findFirst();}
    public Optional<Stored> decision(UUID key){return jdbc.query(SELECT+" AND a.decision_command=?",this::map,key).stream().findFirst();}
    public List<Offer> history(long conversation){return jdbc.query(SELECT+" AND a.conversation_id=? ORDER BY a.offer_id",this::map,conversation).stream().map(Stored::offer).toList();}
    public List<Stored> storedHistory(long conversation){return jdbc.query(SELECT+" AND a.conversation_id=? ORDER BY a.offer_id",this::map,conversation);}
    public List<Stored> activeAcceptedOffersForBuyer(long buyerId) {
        return jdbc.query("""
            SELECT a.*, NULL::bigint AS order_id
            FROM public.offers a
            JOIN public.conversations c ON a.conversation_id = c.conversation_id
            WHERE a.record_type='offers' AND c.record_type='conversations'
              AND c.buyer_id = ? AND a.status = 'ACCEPTED'
              AND NOT EXISTS (SELECT 1 FROM og_compat.order_items i WHERE i.accepted_offer_id=a.offer_id)
            ORDER BY a.offer_id DESC
            """, this::map, buyerId);
    }
    public boolean pending(long conversation){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM public.offers WHERE conversation_id=? AND status='PENDING' AND (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP))",Boolean.class,conversation));}
    public List<Offer> expire(long conversation){return jdbc.query("""
        UPDATE public.offers SET status='EXPIRED',responded_at=CURRENT_TIMESTAMP,responded_by=NULL,version=version+1
        WHERE record_type='offers' AND conversation_id=? AND status='PENDING' AND expires_at<=CURRENT_TIMESTAMP
        RETURNING offer_id
        """,(r,n)->get(r.getLong(1),false).orElseThrow().offer(),conversation);}
    public List<Long> expiredConversations(){return jdbc.query("SELECT DISTINCT conversation_id FROM public.offers WHERE record_type='offers' AND status='PENDING' AND expires_at<=CURRENT_TIMESTAMP ORDER BY conversation_id LIMIT 50",(r,n)->r.getLong(1));}
    public long create(long conversation,long actor,UUID key,BigDecimal price,Instant expires,Long parent,Product product,Fees fees,Map<String,Object> request){
        return jdbc.queryForObject("""
            INSERT INTO public.offers(conversation_id,proposer_id,creation_command,creation_request,offered_item_price,expires_at,parent_offer_id,
              fee_policy_id,buyer_system_fee,seller_system_fee,buyer_subtotal,seller_proceeds,currency,product_version,product_revision_id)
            VALUES(?,?,?,?::jsonb,?,?,?,?,?,?,?,?, 'VND',?,?) RETURNING offer_id
            """,Long.class,conversation,actor,key,chat.encode(request),price,expires==null?null:Timestamp.from(expires),parent,
                fees.policyId(),fees.buyerFee(),fees.sellerFee(),price.add(fees.buyerFee()),price.subtract(fees.sellerFee()),product.version(),product.revisionId());
    }
    public void decide(Stored old,long actor,String status,UUID key,Map<String,Object> request){
        jdbc.update("UPDATE public.offers SET status=?,responded_by=?,responded_at=CURRENT_TIMESTAMP,version=version+1,decision_command=?,decision_request=?::jsonb WHERE offer_id=? AND version=?",status,actor,key,chat.encode(request),old.offer().id(),old.offer().version());
    }
    public void bindCheckout(long offer,String fingerprint){jdbc.update("UPDATE public.offers SET checkout_request=? WHERE offer_id=? AND checkout_request IS NULL",fingerprint,offer);}
    public long policy(long offer){return jdbc.queryForObject("SELECT fee_policy_id FROM public.offers WHERE offer_id=?",Long.class,offer);}
    public String orderStatus(long order){return jdbc.queryForObject("SELECT status FROM og_compat.orders WHERE order_id=?",String.class,order);}
    public void lockCommand(UUID key){jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,31))",Object.class,"chat-offer:"+key);}
    private Instant instant(ResultSet r,String name)throws SQLException{var t=r.getTimestamp(name);return t==null?null:t.toInstant();}
}
