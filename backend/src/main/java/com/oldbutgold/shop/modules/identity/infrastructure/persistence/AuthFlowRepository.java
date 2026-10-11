package com.oldbutgold.shop.modules.identity.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.identity.application.RegistrationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;

@Repository
public class AuthFlowRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public AuthFlowRepository(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc;this.json=json; }
    public record Data(Long userId,long version,String googleSubject,String email,String fullName,boolean verified) {
        @Override public String toString() { return "AuthFlowData[redacted]"; }
    }
    public record Challenge(UUID id,String purpose,String email,String digest,String state,Instant expiresAt,
                            Instant resendAt,int attempts,int maxAttempts,Data data) {
        @Override public String toString() { return "AuthFlowChallenge[redacted]"; }
    }
    public void lock(String key) { jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,8201))",Object.class,key); }
    public void lockUser(long id) { jdbc.queryForObject("SELECT user_id FROM public.users WHERE user_id=? AND record_type='users' FOR UPDATE",Long.class,id); }
    public void lockUserByEmail(String email) {
        jdbc.queryForList("SELECT user_id FROM public.users WHERE email=? AND record_type='users' FOR UPDATE",Long.class,email);
    }
    public Optional<Challenge> find(UUID id,boolean locked) {
        return jdbc.query("""
            SELECT challenge_id,purpose,subject_key,code_digest,state,expires_at,next_send_at,attempt_count,max_attempts,auth_data::text
            FROM public.auth_challenges WHERE record_type='auth_challenges' AND challenge_id=?
            """+(locked?" FOR UPDATE":""),(r,n)->new Challenge(r.getObject(1,UUID.class),r.getString(2),r.getString(3),
                r.getString(4),r.getString(5),r.getTimestamp(6).toInstant(),r.getTimestamp(7).toInstant(),r.getInt(8),r.getInt(9),decode(r.getString(10))),id).stream().findFirst();
    }
    public Optional<Challenge> open(String email,String purpose) {
        var ids=jdbc.queryForList("SELECT challenge_id FROM public.auth_challenges WHERE record_type='auth_challenges' AND subject_key=? AND purpose=? AND state='OPEN'",UUID.class,email,purpose);
        return ids.isEmpty()?Optional.empty():find(ids.getFirst(),true);
    }
    public long rate(String email,String requester,Instant now,int emailLimit,int ipLimit) {
        return Math.max(rateColumn("subject_key",email,now,emailLimit),rateColumn("registration_requester_digest",requester,now,ipLimit));
    }
    private long rateColumn(String column,String key,Instant now,int limit) {
        return jdbc.queryForObject("SELECT CASE WHEN count(*)>=? THEN GREATEST(1,CEIL(EXTRACT(EPOCH FROM (min(issued_at)+interval '1 hour'-?::timestamptz)))::bigint) ELSE 0 END FROM public.auth_challenges WHERE record_type='auth_challenges' AND "+column+"=? AND issued_at>?",
                Long.class,limit,Timestamp.from(now),key,Timestamp.from(now.minusSeconds(3600)));
    }
    public void insert(UUID id,String purpose,String email,String digest,Instant now,Instant expires,Instant resend,
                       int max,String requester,Data data) {
        jdbc.update("""
            INSERT INTO public.auth_challenges(challenge_id,user_id,subject_key,purpose,code_digest,issued_at,expires_at,next_send_at,
              max_attempts,command_key,registration_requester_digest,auth_data)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?::jsonb)
            """,id,data.userId(),email,purpose,digest,Timestamp.from(now),Timestamp.from(expires),Timestamp.from(resend),max,"AUTH:"+id,requester,encode(data));
    }
    public void attempt(UUID id) { jdbc.update("UPDATE public.auth_challenges SET attempt_count=attempt_count+1 WHERE challenge_id=? AND record_type='auth_challenges'",id); }
    public void consume(UUID id,Instant now) { jdbc.update("UPDATE public.auth_challenges SET attempt_count=attempt_count+1,state='CONSUMED',consumed_at=?,auth_data=NULL WHERE challenge_id=? AND record_type='auth_challenges'",Timestamp.from(now),id); }
    public void invalidate(UUID id,Instant now) { jdbc.update("UPDATE public.auth_challenges SET state='INVALIDATED',invalidated_at=?,auth_data=NULL WHERE challenge_id=? AND state='OPEN' AND record_type='auth_challenges'",Timestamp.from(now),id); }
    public boolean twoFactor(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM og_compat.user_security_settings WHERE user_id=? AND email_2fa_enabled)",Boolean.class,id));
    }
    public Optional<Long> googleUser(String subject) { return jdbc.queryForList("SELECT user_id FROM public.users WHERE record_type='users' AND google_subject=?",Long.class,subject).stream().findFirst(); }
    public void linkGoogle(long userId,String subject) {
        lock("google:"+subject); lockUser(userId);
        var owner=googleUser(subject);
        if(owner.isPresent() && owner.get()!=userId) throw new RegistrationException("GOOGLE_ALREADY_LINKED","Google đã liên kết với tài khoản khác.",409);
        String existing=jdbc.queryForObject("SELECT google_subject FROM public.users WHERE user_id=? AND record_type='users'",String.class,userId);
        if(existing!=null && !existing.equals(subject)) throw new RegistrationException("GOOGLE_ALREADY_LINKED","Tài khoản đã liên kết với Google khác.",409);
        if(existing==null) {
            jdbc.update("INSERT INTO og_compat.external_identities(user_id,provider,provider_subject) VALUES(?,'GOOGLE',?)",userId,subject);
            jdbc.update("UPDATE public.users SET google_subject=? WHERE user_id=? AND record_type='users'",subject,userId);
        }
    }
    public Optional<String> loginRestriction(long id) {
        return jdbc.queryForList("SELECT reason FROM og_compat.account_restrictions WHERE user_id=? AND scope='LOGIN' AND closed_at IS NULL ORDER BY opened_at DESC LIMIT 1",String.class,id).stream().findFirst();
    }
    public Optional<String> activeRestrictionReason(long id) {
        return jdbc.queryForList("SELECT reason FROM og_compat.account_restrictions WHERE user_id=? AND closed_at IS NULL ORDER BY opened_at DESC LIMIT 1",String.class,id).stream().findFirst();
    }
    public void expire() {
        jdbc.update("""
            WITH expired AS (SELECT challenge_id FROM public.auth_challenges WHERE record_type='auth_challenges'
                AND state='OPEN' AND auth_data IS NOT NULL AND expires_at<=CURRENT_TIMESTAMP
                ORDER BY expires_at LIMIT 100 FOR UPDATE SKIP LOCKED)
            UPDATE public.auth_challenges c SET state='INVALIDATED',invalidated_at=CURRENT_TIMESTAMP,auth_data=NULL
            FROM expired e WHERE c.challenge_id=e.challenge_id
            """);
    }
    private String encode(Data data) { try { return json.writeValueAsString(data); } catch(Exception e) { throw new IllegalStateException("Auth metadata unavailable"); } }
    private Data decode(String value) { try { return value==null?null:json.readValue(value,Data.class); } catch(Exception e) { throw new IllegalStateException("Auth metadata unavailable"); } }
}
