package com.oldbutgold.shop.modules.commerce.infrastructure.persistence;

import com.oldbutgold.shop.modules.commerce.application.AccountObligationsFacade.OrderObligation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class AccountOrderRepository {
    private final JdbcTemplate jdbc;
    public AccountOrderRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<OrderObligation> orders(long userId) {
        return jdbc.query("""
            SELECT order_id,CASE WHEN buyer_id=? THEN 'BUYER' ELSE 'SELLER' END AS party,status,total_amount,created_at,
              status NOT IN ('COMPLETED','CANCELLED','REFUNDED') AS open
            FROM og_compat.orders WHERE buyer_id=? OR seller_id=?
            ORDER BY (status NOT IN ('COMPLETED','CANCELLED','REFUNDED')) DESC,created_at DESC,order_id DESC LIMIT 50
            """,(rs,n)->new OrderObligation(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getBigDecimal(4),rs.getTimestamp(5).toInstant(),rs.getBoolean(6)),userId,userId,userId);
    }
}
