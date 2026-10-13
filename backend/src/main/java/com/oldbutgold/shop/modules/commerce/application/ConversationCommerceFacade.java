package com.oldbutgold.shop.modules.commerce.application;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
public interface ConversationCommerceFacade {
    record OrderContext(long id,long buyerId,long sellerId,List<Long> products,String status) {}
    record Fees(long policyId,BigDecimal buyerFee,BigDecimal sellerFee) {}
    Optional<OrderContext> order(long actor,long orderId);
    boolean openObligations(long buyer,long seller,long productId);
    boolean retainMedia(long buyer,long seller,long productId);
    Fees fees(BigDecimal price);
}
