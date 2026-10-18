package com.oldbutgold.shop.modules.catalog.application;
import java.math.BigDecimal;
import java.util.Optional;
public interface ConversationCatalogFacade {
    record Product(long id,long sellerId,String title,BigDecimal price,String thumbnail,String status,long version,Long revisionId,int available,boolean returnAllowed,boolean requiresEkyc,boolean deleted) {}
    Optional<Product> product(long id,boolean lock);
}
