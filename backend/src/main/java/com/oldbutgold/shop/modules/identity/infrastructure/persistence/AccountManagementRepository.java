package com.oldbutgold.shop.modules.identity.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.identity.api.AccountManagementDtos.Restriction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.Instant;
import java.util.*;

@Repository
public class AccountManagementRepository {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public AccountManagementRepository(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc;this.json=json; }
    public record State(long id,String name,String email,String phone,List<String> roles,String authenticationStatus,
                        String state,long version,Instant until,String reason,List<Restriction> restrictions,Instant createdAt) {}
    private static final String CUSTOMER="""
        u.record_type='users' AND u.deleted_at IS NULL
        AND EXISTS(SELECT 1 FROM og_compat.user_roles ur JOIN og_compat.roles r USING(role_id) WHERE ur.user_id=u.user_id AND r.role_name IN ('BUYER','SELLER'))
        AND NOT EXISTS(SELECT 1 FROM og_compat.user_roles ur JOIN og_compat.roles r USING(role_id) WHERE ur.user_id=u.user_id AND r.role_name IN ('ADMIN','KTV'))
        """;
    private static final String SELECT="""
        SELECT u.*,ARRAY(SELECT role_name FROM og_compat.user_roles ur JOIN og_compat.roles r USING(role_id) WHERE ur.user_id=u.user_id ORDER BY role_name) AS roles
        FROM public.users u WHERE
        """;
    public Optional<State> customer(long id,boolean lock) {
        return jdbc.query(SELECT+CUSTOMER+" AND u.user_id=?"+(lock?" FOR UPDATE OF u":""),this::map,id).stream().findFirst();
    }
    public boolean activeKtv(long id) { return Boolean.TRUE.equals(jdbc.queryForObject("""
        SELECT EXISTS(SELECT 1 FROM public.users u JOIN og_compat.user_roles ur USING(user_id) JOIN og_compat.roles r USING(role_id)
        WHERE u.record_type='users' AND u.user_id=? AND u.status='ACTIVE' AND u.deleted_at IS NULL AND r.role_name='KTV')
        """,Boolean.class,id)); }
    public void lockCommand(String key) { jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,28))",Object.class,"account-command:"+key); }
    public List<State> search(String query,String status,String role,int page,int size) {
        return jdbc.query(SELECT+CUSTOMER+filters()+" ORDER BY u.user_id DESC LIMIT ? OFFSET ?",this::map,
            query,query,query,status,status,role,role,size,(long)page*size);
    }
    public long count(String query,String status,String role) {
        return jdbc.queryForObject("SELECT count(*) FROM public.users u WHERE "+CUSTOMER+filters(),Long.class,query,query,query,status,status,role,role);
    }
    private String filters() { return """
        AND (u.full_name ILIKE '%'||?||'%' OR u.email ILIKE '%'||?||'%' OR coalesce(u.phone_number,'') ILIKE '%'||?||'%')
        AND (?='ALL' OR CASE WHEN public.og28_effective_state(u.user_id)<>'ACTIVE' THEN public.og28_effective_state(u.user_id) ELSE u.status END=?)
        AND (?='ALL' OR EXISTS(SELECT 1 FROM og_compat.user_roles ur JOIN og_compat.roles r USING(role_id) WHERE ur.user_id=u.user_id AND r.role_name=?))
        """; }
    public boolean restricted(long id,String scope) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT public.og28_restricted(?,?)",Boolean.class,id,scope)); }
    public List<Restriction> legacy(long id) {
        return jdbc.query("SELECT * FROM og_compat.account_restrictions WHERE user_id=? ORDER BY opened_at DESC",(rs,n)->new Restriction(rs.getObject("restriction_id",UUID.class),rs.getString("scope"),rs.getLong("imposed_by"),rs.getString("reason"),instant(rs,"opened_at"),null,instant(rs,"closed_at"),true),id);
    }
    public void liftLegacy(long id,UUID restrictionId,Instant now) { jdbc.update("UPDATE og_compat.account_restrictions SET closed_at=? WHERE user_id=? AND restriction_id=? AND closed_at IS NULL",Timestamp.from(now),id,restrictionId); }
    public void update(State old,String state,Instant until,String reason,List<Restriction> restrictions,boolean activateAuthentication) {
        int changed=jdbc.update("""
            UPDATE public.users SET governance_version=governance_version+1,governance_state=?,governance_until=?,governance_reason=?,
              governance_restrictions=?::jsonb,status=CASE WHEN ? THEN 'ACTIVE' ELSE status END,updated_at=CURRENT_TIMESTAMP
            WHERE record_type='users' AND user_id=? AND governance_version=?
            """,state,until==null?null:Timestamp.from(until),reason,encode(restrictions),activateAuthentication,old.id(),old.version());
        if(changed!=1) throw new IllegalStateException("Account version changed while locked");
    }
    private State map(ResultSet rs,int row) throws SQLException {
        try {
            return new State(rs.getLong("user_id"),rs.getString("full_name"),rs.getString("email"),rs.getString("phone_number"),
                Arrays.asList((String[])rs.getArray("roles").getArray()),rs.getString("status"),rs.getString("governance_state"),rs.getLong("governance_version"),
                instant(rs,"governance_until"),rs.getString("governance_reason"),json.readValue(rs.getString("governance_restrictions"),new TypeReference<>(){}),instant(rs,"created_at"));
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Cannot read account restrictions",e); }
    }
    public Map<String,Object> snapshot(long version,String state,Instant until,String reason,List<Restriction> restrictions) {
        Map<String,Object> result=new LinkedHashMap<>();result.put("version",version);result.put("state",state);
        result.put("until",until==null?null:until.toString());result.put("reason",reason);
        result.put("restrictions",json.convertValue(restrictions,new TypeReference<List<Map<String,Object>>>(){}));return result;
    }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException("Cannot store account restrictions",e); } }
    private static Instant instant(ResultSet rs,String column)throws SQLException { var t=rs.getTimestamp(column);return t==null?null:t.toInstant(); }
}
