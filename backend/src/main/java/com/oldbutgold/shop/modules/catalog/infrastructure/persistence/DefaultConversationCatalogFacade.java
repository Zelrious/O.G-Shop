package com.oldbutgold.shop.modules.catalog.infrastructure.persistence;
import com.oldbutgold.shop.modules.catalog.application.ConversationCatalogFacade;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Optional;
@Service
public class DefaultConversationCatalogFacade implements ConversationCatalogFacade {
    private final JdbcTemplate jdbc;
    public DefaultConversationCatalogFacade(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public Optional<Product> product(long id,boolean lock) {
        return jdbc.query("""
            SELECT p.product_id,p.seller_id,p.title,p.listed_price,p.status,p.version,p.public_revision_id,
              GREATEST(0,p.quantity_available) AS available,p.return_allowed,p.requires_buyer_ekyc,p.deleted_at,
              (SELECT m.media_url FROM og_compat.product_media m WHERE m.product_id=p.product_id AND m.media_type='IMAGE' ORDER BY m.display_order LIMIT 1) AS thumbnail
            FROM public.products p WHERE p.record_type='products' AND p.product_id=?
            """+(lock?" FOR UPDATE OF p":""),(r,n)->new Product(r.getLong(1),r.getLong(2),r.getString(3),r.getBigDecimal(4),r.getString("thumbnail"),r.getString(5),r.getLong(6),r.getObject(7,Long.class),r.getInt(8),r.getBoolean(9),r.getBoolean(10),r.getTimestamp(11)!=null),id).stream().findFirst();
    }
}
