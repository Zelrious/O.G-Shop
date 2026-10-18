package com.oldbutgold.shop.modules.commerce.infrastructure.persistence;
import com.oldbutgold.shop.modules.commerce.application.ConversationCommerceFacade;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.*;
import java.util.*;
@Service
public class DefaultConversationCommerceFacade implements ConversationCommerceFacade {
    private final JdbcTemplate jdbc;
    public DefaultConversationCommerceFacade(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public Optional<OrderContext> order(long actor,long id) {
        return jdbc.query("SELECT order_id,buyer_id,seller_id,status FROM og_compat.orders WHERE order_id=? AND ? IN(buyer_id,seller_id)",
            (r,n)->new OrderContext(r.getLong(1),r.getLong(2),r.getLong(3),jdbc.query("SELECT product_id FROM og_compat.order_items WHERE order_id=? ORDER BY order_item_id",(row,i)->row.getLong(1),id),r.getString(4)),id,actor).stream().findFirst();
    }
    public boolean openObligations(long buyer,long seller,long product) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM og_compat.orders o JOIN og_compat.order_items i USING(order_id)
              WHERE o.buyer_id=? AND o.seller_id=? AND i.product_id=?
                AND (o.status NOT IN('COMPLETED','CANCELLED','REFUNDED') OR EXISTS(
                  SELECT 1 FROM og_compat.cases c WHERE c.order_id=o.order_id AND c.state NOT IN('CLOSED','REJECTED'))))
            """,Boolean.class,buyer,seller,product));
    }
    public Fees fees(BigDecimal price) {
        return jdbc.query("""
            SELECT fee_policy_id,seller_fixed_fee,seller_fee_rate FROM og_compat.system_fee_policies
            WHERE status='ACTIVE' ORDER BY effective_from DESC LIMIT 1
            """,(r,n)->new Fees(r.getLong(1),BigDecimal.ZERO,r.getBigDecimal(2).add(price.multiply(r.getBigDecimal(3)).setScale(0,RoundingMode.HALF_UP)))).stream()
            .findFirst().orElseThrow(()->new IllegalStateException("Chưa có chính sách phí giao dịch."));
    }
    public boolean retainMedia(long buyer,long seller,long product) {
        return openObligations(buyer,seller,product)||Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM og_compat.orders o JOIN og_compat.order_items i USING(order_id)
              WHERE o.buyer_id=? AND o.seller_id=? AND i.product_id=? AND o.status IN('COMPLETED','CANCELLED','REFUNDED')
                AND GREATEST(coalesce(o.completed_at,o.updated_at,o.created_at),coalesce(o.cancelled_at,o.updated_at,o.created_at))>CURRENT_TIMESTAMP-INTERVAL '7 days')
            """,Boolean.class,buyer,seller,product));
    }
}
