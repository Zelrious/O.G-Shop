package com.oldbutgold.shop.modules.communication.application;
import java.math.BigDecimal;
import java.util.Optional;
public interface NegotiatedCheckoutFacade {
    record Agreement(long offerId,BigDecimal price,BigDecimal buyerFee,BigDecimal sellerFee,long feePolicyId) {}
    void requireNotBlocked(long buyer,long seller);
    Agreement preview(long actor,long product,long offerId);
    Agreement bind(long actor,long product,long offerId,String requestFingerprint);
    Optional<Long> existingCheckout(long actor,long product,long offerId,String requestFingerprint);
    void linkOrder(long actor,long offerId,long orderId);
}
