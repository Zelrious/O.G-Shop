package com.oldbutgold.shop.modules.platform.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.platform.application.GovernanceHistoryFacade.Entry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class GovernanceHistoryRepository {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public GovernanceHistoryRepository(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc;this.json=json; }
    private static final String SELECT="SELECT log_id,user_id,entity_id,action,created_at,command_request::text,old_values::text,new_values::text FROM public.audit_logs WHERE record_type='audit_logs' AND entity_type='ACCOUNT_GOVERNANCE'";
    private List<Entry> query(String clause,Object... args) {
        return jdbc.query(SELECT+clause,(rs,n)->new Entry(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getString(4),rs.getTimestamp(5).toInstant(),decode(rs.getString(6)),decode(rs.getString(7)),decode(rs.getString(8))),args);
    }
    public Optional<Entry> command(String key) { return query(" AND command_key=?",key).stream().findFirst(); }
    public List<Entry> history(long userId,int page,int size) { return query(" AND entity_id=? ORDER BY created_at DESC,log_id DESC LIMIT ? OFFSET ?",userId,size,(long)page*size); }
    public long count(long userId) { return jdbc.queryForObject("SELECT count(*) FROM public.audit_logs WHERE record_type='audit_logs' AND entity_type='ACCOUNT_GOVERNANCE' AND entity_id=?",Long.class,userId); }
    public void record(long actor,long target,String action,String key,Map<String,Object> request,Map<String,Object> before,Map<String,Object> after) {
        jdbc.update("""
            INSERT INTO public.audit_logs(user_id,action,entity_type,entity_id,old_values,new_values,command_key,command_request)
            VALUES(?,?,'ACCOUNT_GOVERNANCE',?,?::jsonb,?::jsonb,?,?::jsonb)
            """,actor,action,target,encode(before),encode(after),key,encode(request));
    }
    public boolean hasCompensation(long userId,long caseId,String restrictionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM public.audit_logs WHERE record_type='audit_logs' AND entity_type='ACCOUNT_GOVERNANCE'
            AND entity_id=? AND action='COMPENSATE' AND command_request->>'systemCaseId'=?
            AND command_request->>'targetCaseId'=?)
            """,Boolean.class,userId,Long.toString(caseId),restrictionId));
    }
    private String encode(Object data) { try { return json.writeValueAsString(data); } catch(Exception e) { throw new IllegalStateException("Cannot encode governance history",e); } }
    private Map<String,Object> decode(String data) { try { return json.readValue(data,new TypeReference<>(){}); } catch(Exception e) { throw new IllegalStateException("Cannot read governance history",e); } }
}
