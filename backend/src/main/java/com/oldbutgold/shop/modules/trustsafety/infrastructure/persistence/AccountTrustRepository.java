package com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence;

import com.oldbutgold.shop.modules.trustsafety.application.AccountTrustFacade.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class AccountTrustRepository {
    private final JdbcTemplate jdbc;
    public AccountTrustRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Reputation reputation(long userId) {
        var events=jdbc.query("""
            SELECT 'P:'||entry_id::text AS id,'PENALTY' AS source,-points_delta AS delta,reason,decided_by,created_at
            FROM og_compat.penalty_ledger WHERE user_id=?
            UNION ALL
            SELECT 'R:'||r.review_id::text,'REVIEW',r.rating,'Phục hồi từ đánh giá hợp lệ',NULL,r.created_at
            FROM og_compat.reviews r JOIN og_compat.orders o USING(order_id)
            WHERE r.reviewee_id=? AND r.rating>=3 AND r.deleted_at IS NULL
              AND ((r.reviewer_id=o.buyer_id AND r.reviewee_id=o.seller_id) OR (r.reviewer_id=o.seller_id AND r.reviewee_id=o.buyer_id))
              AND (o.valid_delivered_at IS NOT NULL OR (o.workflow_model='LEGACY_V14' AND o.status='COMPLETED'))
            ORDER BY created_at,id
            """,(rs,n)->new ReputationEvent(rs.getString(1),rs.getString(2),rs.getInt(3),rs.getString(4),rs.getObject(5,Long.class),rs.getTimestamp(6).toInstant()),userId,userId);
        int score=100;
        for(var event:events) score=Math.max(0,Math.min(100,score+event.pointsDelta()));
        return new Reputation(score,events.reversed());
    }
    private static final String PARTICIPANT="(c.created_by=? OR o.buyer_id=? OR o.seller_id=?)";
    public List<CaseSummary> cases(long userId) {
        return jdbc.query("""
            SELECT c.case_id,c.order_id,c.case_type,c.state,c.description,c.created_at,public.og28_response_deadline(c.case_id) AS deadline,
              c.state NOT IN ('CLOSED','REJECTED') AND (o.seller_id IS DISTINCT FROM ? OR c.seller_response_due_at IS NULL
                OR CURRENT_TIMESTAMP<=public.og28_response_deadline(c.case_id)) AS can_respond,
              c.state NOT IN ('CLOSED','REJECTED') AND c.seller_response_due_at IS NOT NULL AND o.seller_id=? AS can_compensate
            FROM og_compat.cases c LEFT JOIN og_compat.orders o USING(order_id) WHERE
            """+PARTICIPANT+" ORDER BY c.created_at DESC,c.case_id DESC LIMIT 50",
            (rs,n)->new CaseSummary(rs.getLong(1),rs.getObject(2,Long.class),rs.getString(3),rs.getString(4),rs.getString(5),rs.getTimestamp(6).toInstant(),
                rs.getTimestamp(7)==null?null:rs.getTimestamp(7).toInstant(),rs.getBoolean(8),rs.getBoolean(9)),userId,userId,userId,userId,userId);
    }
    public Optional<String> ownedCase(long userId,long caseId,boolean lock) {
        return jdbc.query("SELECT c.state FROM public.cases c LEFT JOIN og_compat.orders o USING(order_id) WHERE c.record_type='cases' AND c.case_id=? AND "+PARTICIPANT+(lock?" FOR UPDATE OF c":""),(rs,n)->rs.getString(1),caseId,userId,userId,userId).stream().findFirst();
    }
    public List<Response> responses(long caseId) {
        return jdbc.query("SELECT event_id,actor_id,reason,occurred_at FROM og_compat.case_events WHERE case_id=? AND event_type='SUPPLEMENTED' AND metadata->>'partyResponse'='true' ORDER BY occurred_at,event_id",
            (rs,n)->new Response(rs.getObject(1,UUID.class),rs.getLong(2),rs.getString(3),rs.getTimestamp(4).toInstant()),caseId);
    }
    public Optional<Map<String,Object>> command(String key) { return jdbc.queryForList("SELECT event_id,case_id,actor_id,reason FROM og_compat.case_events WHERE command_key=?",key).stream().findFirst(); }
    public void lockCommand(String key) { jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,28))",Object.class,"case-response:"+key); }
    public UUID respond(long userId,long caseId,String text,String key) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO og_compat.case_events(event_id,case_id,actor_id,event_type,reason,metadata,command_key) VALUES(?,?,?,'SUPPLEMENTED',?,'{\"partyResponse\":true}',?)",id,caseId,userId,text,key);
        return id;
    }
    public boolean confirmedOutage(long caseId,java.time.Instant from,java.time.Instant until) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM og_compat.case_events e WHERE e.case_id=? AND e.event_type='COMPENSATION'
              AND (public.og_has_role(e.actor_id,'KTV') OR public.og_has_role(e.actor_id,'ADMIN'))
              AND (e.metadata->>'affectedFrom')::timestamptz=? AND (e.metadata->>'affectedUntil')::timestamptz=?)
            """,Boolean.class,caseId,java.sql.Timestamp.from(from),java.sql.Timestamp.from(until)));
    }
    public List<Outage> outages(long userId) {
        return jdbc.query("""
            SELECT DISTINCT c.case_id,(e.metadata->>'affectedFrom')::timestamptz AS affected_from,(e.metadata->>'affectedUntil')::timestamptz AS affected_until
            FROM og_compat.cases c LEFT JOIN og_compat.orders o USING(order_id) JOIN og_compat.case_events e ON e.case_id=c.case_id
            WHERE c.case_type='SYSTEM_ISSUE' AND c.state='CLOSED' AND (c.created_by=? OR o.buyer_id=? OR o.seller_id=?)
              AND e.event_type='COMPENSATION' AND (public.og_has_role(e.actor_id,'KTV') OR public.og_has_role(e.actor_id,'ADMIN'))
              AND e.metadata->>'affectedFrom' IS NOT NULL AND e.metadata->>'affectedUntil' IS NOT NULL
              AND EXISTS(SELECT 1 FROM og_compat.case_rounds r JOIN og_compat.case_decisions d USING(round_id) WHERE r.case_id=c.case_id AND d.outcome='SYSTEM_RESOLVED' AND d.is_final)
            ORDER BY c.case_id
            """,(rs,n)->new Outage(rs.getLong(1),rs.getTimestamp(2).toInstant(),rs.getTimestamp(3).toInstant()),userId,userId,userId);
    }
    public void compensateDeadline(long actor,long userId,long caseId,long systemCaseId,java.time.Instant from,java.time.Instant until,String reason,String key) {
        jdbc.update("""
            INSERT INTO og_compat.case_events(event_id,case_id,actor_id,event_type,reason,metadata,command_key)
            VALUES(?,?,?,'DEADLINE_EXTENSION',?,jsonb_build_object('source','ACCOUNT_UC67','userId',?::bigint,
              'systemCaseId',?::bigint,'affectedFrom',?::text,'affectedUntil',?::text),?)
            """,UUID.randomUUID(),caseId,actor,reason,userId,systemCaseId,from.toString(),until.toString(),"ACCOUNT_DEADLINE:"+key);
    }
    public boolean confirmedSystemIssue(long userId,long caseId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM og_compat.cases c LEFT JOIN og_compat.orders o USING(order_id)
            WHERE c.case_id=? AND c.case_type='SYSTEM_ISSUE' AND c.state='CLOSED' AND
              (c.created_by=? OR o.buyer_id=? OR o.seller_id=?) AND EXISTS(
                SELECT 1 FROM og_compat.case_rounds r JOIN og_compat.case_decisions d USING(round_id)
                WHERE r.case_id=c.case_id AND d.outcome='SYSTEM_RESOLVED' AND d.is_final))
            """,Boolean.class,caseId,userId,userId,userId));
    }
}
