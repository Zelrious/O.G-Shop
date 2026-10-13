package com.oldbutgold.shop.modules.platform.infrastructure.persistence;
import com.oldbutgold.shop.modules.platform.application.InteractionAuditFacade;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component
public class DefaultInteractionAuditFacade implements InteractionAuditFacade {
    private final JdbcTemplate jdbc;
    public DefaultInteractionAuditFacade(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void blockChanged(long seller,long buyer,boolean before,boolean after,String reason) {
        jdbc.update("""
            INSERT INTO public.audit_logs(user_id,action,entity_type,entity_id,old_values,new_values)
            VALUES(?,?,'SELLER_BUYER_BLOCK',?,jsonb_build_object('blocked',?::boolean),
              jsonb_build_object('blocked',?::boolean,'reason',?::text,'sellerId',?::bigint))
            """,seller,after?"BLOCK":"UNBLOCK",buyer,before,after,reason,seller);
    }
}
