package com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence;
import com.oldbutgold.shop.modules.trustsafety.application.ConversationEvidenceFacade;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
@Repository
public class DefaultConversationEvidenceFacade implements ConversationEvidenceFacade {
    private final JdbcTemplate jdbc;
    public DefaultConversationEvidenceFacade(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public boolean retainsMessage(long message){
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM public.cases c WHERE c.message_id=? AND
              ((c.record_type='cases' AND c.state NOT IN('CLOSED','REJECTED'))
               OR (c.record_type='reports' AND c.status NOT IN('RESOLVED','REJECTED'))
               OR coalesce(c.closed_at,c.resolved_at)>CURRENT_TIMESTAMP-INTERVAL '7 days'
               OR (c.record_type='reports' AND coalesce(c.closed_at,c.resolved_at) IS NULL)))
            """,Boolean.class,message));
    }
}
