package com.oldbutgold.shop.modules.commerce.infrastructure.persistence;
import com.oldbutgold.shop.modules.commerce.api.VoucherDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.Instant;
import java.util.*;

@Repository
public class VoucherWalletRepository {
    private final JdbcTemplate j;private final ObjectMapper json;
    public VoucherWalletRepository(JdbcTemplate j,ObjectMapper json){this.j=j;this.json=json;}
    private static final String FIELDS="""
        v.voucher_id,r.source_voucher_revisions_revision_id AS revision_id,v.version,v.code,
        coalesce(r.policy_snapshot->>'title',v.title) AS title,coalesce(r.policy_snapshot->>'description',v.description,'') AS description,
        CASE WHEN r.benefit_component='SHIPPING' THEN 'SHIPPING_DISCOUNT' WHEN r.benefit_component='GOODS' THEN 'ORDER_DISCOUNT' ELSE v.voucher_type END AS voucher_type,
        coalesce(r.discount_type,v.discount_type) AS discount_type,coalesce(r.discount_value,v.discount_value) AS discount_value,
        CASE WHEN r.source_voucher_revisions_revision_id IS NOT NULL THEN r.max_discount_amount ELSE v.max_discount_amount END AS max_discount_amount,coalesce(r.min_eligible_amount,v.min_order_amount) AS min_order_amount,
        coalesce(r.scope,'CHECKOUT') AS scope,coalesce(v.total_usage_limit,1000000) AS quantity,
        coalesce(r.valid_from,v.start_time) AS start_time,coalesce(r.valid_until,v.end_time) AS end_time,v.is_active,
        coalesce((r.policy_snapshot->>'selfClaim')::boolean,false) AS self_claim,
        ARRAY(SELECT product_id FROM public.voucher_products WHERE revision_id=r.source_voucher_revisions_revision_id ORDER BY product_id) AS product_ids,
        (SELECT count(*) FROM public.voucher_grants g JOIN public.vouchers p ON p.source_voucher_revisions_revision_id=g.revision_id WHERE p.parent_voucher_id=v.voucher_id) AS issued,
        (SELECT count(*) FROM public.voucher_grants g JOIN public.vouchers p ON p.source_voucher_revisions_revision_id=g.revision_id WHERE p.parent_voucher_id=v.voucher_id AND g.state='REDEEMED') AS used
        """;
    private static final String SELECT="SELECT "+FIELDS+" FROM public.vouchers v LEFT JOIN LATERAL (SELECT * FROM public.vouchers p WHERE p.record_type='voucher_revisions' AND p.parent_voucher_id=v.voucher_id ORDER BY p.revision_no DESC LIMIT 1) r ON true WHERE v.record_type='vouchers'";
    private Campaign map(ResultSet r,int n)throws SQLException {Long[] products=(Long[])r.getArray("product_ids").getArray();return new Campaign(r.getLong("voucher_id"),r.getLong("revision_id"),r.getLong("version"),r.getString("code"),r.getString("title"),r.getString("description"),r.getString("voucher_type"),r.getString("discount_type"),r.getBigDecimal("discount_value"),r.getBigDecimal("max_discount_amount"),r.getBigDecimal("min_order_amount"),r.getString("scope"),List.of(products),r.getInt("quantity"),r.getTimestamp("start_time").toInstant(),r.getTimestamp("end_time").toInstant(),r.getBoolean("is_active"),r.getBoolean("self_claim"),r.getLong("issued"),r.getLong("used"));}
    public List<Campaign> campaigns(){return j.query(SELECT+" ORDER BY v.voucher_id DESC",this::map);}
    public Optional<Campaign> campaign(long id,boolean lock){if(lock)j.query("SELECT voucher_id FROM public.vouchers WHERE voucher_id=? AND record_type='vouchers' FOR UPDATE",(r,n)->r.getLong(1),id);return j.query(SELECT+" AND v.voucher_id=?",this::map,id).stream().findFirst();}
    public void lock(String key){j.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,32))",Object.class,key);}
    public long save(long actor,Long id,Save s){
        String code=s.code().trim().toUpperCase(Locale.ROOT);long campaign;
        if(id==null)campaign=j.queryForObject("INSERT INTO public.vouchers(code,title,description,voucher_type,discount_type,discount_value,max_discount_amount,min_order_amount,total_usage_limit,start_time,end_time,is_active,wallet_enabled) VALUES(?,?,?,?,?,?,?,?,?,?,?, ?,true) RETURNING voucher_id",Long.class,code,s.title().trim(),s.description(),s.voucherType(),s.discountType(),s.discountValue(),s.maxDiscountAmount(),s.minOrderAmount(),s.quantity(),Timestamp.from(s.startTime()),Timestamp.from(s.endTime()),s.active());
        else {campaign=id;j.update("UPDATE public.vouchers SET title=?,description=?,voucher_type=?,discount_type=?,discount_value=?,max_discount_amount=?,min_order_amount=?,total_usage_limit=?,start_time=?,end_time=?,is_active=?,wallet_enabled=true,sponsor_type='PLATFORM',seller_id=NULL,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE voucher_id=?",s.title().trim(),s.description(),s.voucherType(),s.discountType(),s.discountValue(),s.maxDiscountAmount(),s.minOrderAmount(),s.quantity(),Timestamp.from(s.startTime()),Timestamp.from(s.endTime()),s.active(),id);}
        Map<String,Object> snapshot=new LinkedHashMap<>();snapshot.put("title",s.title().trim());snapshot.put("description",s.description()==null?"":s.description());snapshot.put("selfClaim",s.selfClaim());
        long revision=j.queryForObject("""
           INSERT INTO og_compat.voucher_revisions(voucher_id,revision_no,policy_snapshot,scope,claim_limit,per_user_claim_limit,valid_from,valid_until,created_by,benefit_component,discount_type,discount_value,max_discount_amount,min_eligible_amount)
           VALUES(?,(SELECT coalesce(max(revision_no),0)+1 FROM og_compat.voucher_revisions WHERE voucher_id=?),?::jsonb,?,?,1,?,?,?,?,?,?,?,?) RETURNING revision_id
           """,Long.class,campaign,campaign,encode(snapshot),s.scope(),s.quantity(),Timestamp.from(s.startTime()),Timestamp.from(s.endTime()),actor,s.voucherType().equals("SHIPPING_DISCOUNT")?"SHIPPING":"GOODS",s.discountType(),s.discountValue(),s.maxDiscountAmount(),s.minOrderAmount());
        for(long product:s.productIds())j.update("INSERT INTO public.voucher_products(revision_id,product_id) VALUES(?,?)",revision,product);
        return campaign;
    }
    public long received(long campaign,long user){return j.queryForObject("SELECT count(*) FROM public.voucher_grants g JOIN public.vouchers r ON r.source_voucher_revisions_revision_id=g.revision_id WHERE r.parent_voucher_id=? AND g.user_id=?",Long.class,campaign,user);}
    public UUID grant(long actor,long user,Campaign c,String source,String reason,String key,Instant now){UUID id=UUID.randomUUID();j.update("INSERT INTO public.voucher_grants(grant_id,revision_id,user_id,source,issued_by,reason,expires_at,command_key,granted_at) VALUES(?,?,?,?,?,?,?,?,?)",id,c.revisionId(),user,source,actor,reason,Timestamp.from(c.endTime()),key,Timestamp.from(now));event(id,actor,"GRANTED",reason,key+":event");return id;}
    public void event(UUID id,long actor,String type,String reason,String key){j.update("INSERT INTO og_compat.voucher_grant_events(event_id,grant_id,actor_id,event_type,reason,command_key) VALUES(?,?,?,?,?,?)",UUID.randomUUID(),id,actor,type,reason,key);}
    public void revoke(UUID id,long actor,String reason,String key){j.update("UPDATE public.voucher_grants SET state='REVOKED' WHERE grant_id=? AND state='AVAILABLE'",id);event(id,actor,"REVOKED",reason,key);}
    private static final String GRANTS="SELECT g.grant_id,g.user_id,g.state,g.granted_at,g.expires_at,"+FIELDS+" FROM public.voucher_grants g JOIN public.vouchers r ON r.source_voucher_revisions_revision_id=g.revision_id JOIN public.vouchers v ON v.voucher_id=r.parent_voucher_id WHERE g.record_type='voucher_grants'";
    private Grant grantMap(ResultSet r,int n)throws SQLException{return new Grant(r.getObject("grant_id",UUID.class),r.getLong("user_id"),"",r.getString("state"),r.getTimestamp("granted_at").toInstant(),r.getTimestamp("expires_at").toInstant(),map(r,n));}
    public List<Grant> owned(long user){return j.query(GRANTS+" AND g.user_id=? ORDER BY g.granted_at DESC",this::grantMap,user);}
    public Optional<Grant> grant(UUID id,boolean lock){if(lock)j.query("SELECT grant_id FROM public.voucher_grants WHERE grant_id=? FOR UPDATE",(r,n)->r.getObject(1,UUID.class),id);return j.query(GRANTS+" AND g.grant_id=?",this::grantMap,id).stream().findFirst();}
    public Set<Long> recipientIds(long campaign,boolean revocable,Instant now){return new HashSet<>(j.query("SELECT DISTINCT g.user_id FROM public.voucher_grants g JOIN public.vouchers r ON r.source_voucher_revisions_revision_id=g.revision_id WHERE r.parent_voucher_id=?"+(revocable?" AND g.state='AVAILABLE' AND g.expires_at>?":""),(r,n)->r.getLong(1),revocable?new Object[]{campaign,Timestamp.from(now)}:new Object[]{campaign}));}
    public boolean revisionRevoked(long revision){return j.queryForObject("SELECT EXISTS(SELECT 1 FROM og_compat.voucher_revision_revocations WHERE revision_id=?)",Boolean.class,revision);}
    public void bindOrder(long order,UUID grant){j.update("UPDATE public.orders SET wallet_grant_id=? WHERE order_id=?",grant,order);}
    public long points(long user){return j.queryForObject("SELECT coalesce(sum(points_delta),0) FROM public.reward_ledger WHERE user_id=?",Long.class,user);}
    public List<Point> pointsHistory(long user){return j.query("SELECT points_delta,entry_type,reason,created_at FROM public.reward_ledger WHERE user_id=? ORDER BY created_at DESC LIMIT 30",(r,n)->new Point(r.getLong(1),r.getString(2),r.getString(3),r.getTimestamp(4).toInstant()),user);}
    public List<Grant> expiring(Instant until,int offset){return j.query(GRANTS+" AND g.state='AVAILABLE' AND g.expires_at<=? ORDER BY g.expires_at,g.grant_id LIMIT 500 OFFSET ?",this::grantMap,Timestamp.from(until),offset);}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Cannot encode policy",e);}}
}
