package com.oldbutgold.shop.modules.communication.application;
import com.oldbutgold.shop.modules.communication.infrastructure.persistence.ChatRepository;
import com.oldbutgold.shop.modules.communication.infrastructure.persistence.OfferRepository;
import com.oldbutgold.shop.modules.communication.infrastructure.persistence.OfferRepository.Stored;
import com.oldbutgold.shop.modules.platform.application.PlatformNotificationFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.oldbutgold.shop.modules.communication.application.ChatService.problem;
@Service
public class DefaultNegotiatedCheckoutFacade implements NegotiatedCheckoutFacade {
    private final ChatService chat;private final ChatRepository conversations;private final OfferRepository offers;private final PlatformNotificationFacade notices;
    public DefaultNegotiatedCheckoutFacade(ChatService chat,ChatRepository conversations,OfferRepository offers,PlatformNotificationFacade notices){this.chat=chat;this.conversations=conversations;this.offers=offers;this.notices=notices;}
    public void requireNotBlocked(long buyer,long seller){chat.requireTradingParties(buyer,seller);if(conversations.blocked(buyer,seller))throw problem(403,"SELLER_BUYER_BLOCKED","Người bán đã chặn giao dịch mới với tài khoản này.");}
    @Transactional public void linkOrder(long actor,long offerId,long orderId){var offer=offers.get(offerId,false).orElseThrow();var c=chat.owned(actor,offer.offer().conversationId(),true);if(c.buyer()!=actor||!Objects.equals(offer.offer().orderId(),orderId))throw problem(409,"OFFER_ORDER_CONFLICT","Đơn hàng không khớp đề nghị.");conversations.linkOrder(c.id(),orderId);notices.enqueue("offer-checkout:"+offerId+":"+orderId,c.id(),List.of(new PlatformNotificationFacade.Notice(c.seller(),"OFFER_ORDERED","Có đơn mới từ thương lượng","Giá đã chấp nhận được dùng cho đơn #"+orderId+". Mở hội thoại để xem đơn.",PlatformNotificationFacade.Destination.CONVERSATION,c.id(),orderId),new PlatformNotificationFacade.Notice(c.buyer(),"OFFER_ORDERED","Đã tạo đơn theo giá thương lượng","Đơn #"+orderId+" đã được ghi nhận theo giá đã chấp nhận.",PlatformNotificationFacade.Destination.CONVERSATION,c.id(),orderId)));}
    private Stored owned(long actor,long product,long id,boolean lock){
        var stored=offers.get(id,lock).orElseThrow(()->problem(404,"OFFER_NOT_FOUND","Không tìm thấy đề nghị trong phạm vi của bạn."));
        var c=chat.owned(actor,stored.offer().conversationId(),false);
        if(c.buyer()!=actor||c.product()!=product)throw problem(404,"OFFER_NOT_FOUND","Không tìm thấy đề nghị đúng người mua và sản phẩm.");
        return stored;
    }
    private Agreement eligible(long actor,long product,Stored saved){
        var offer=saved.offer();var c=chat.owned(actor,offer.conversationId(),false);var p=chat.product(product,false);
        chat.requirePurchase(c,p);
        if(!offer.status().equals("ACCEPTED")||saved.creationKey()==null)throw problem(409,"OFFER_NOT_ACCEPTED","Cần đề nghị đã chấp nhận và có snapshot hiện hành.");
        if(offer.orderId()!=null)throw problem(409,"OFFER_ALREADY_USED","Giá này đã dùng cho một giao dịch. Sau hủy cần gửi đề nghị mới.");
        if(offer.productVersion()!=p.version()||!Objects.equals(saved.revisionId(),p.revisionId()))throw problem(409,"OFFER_LISTING_CHANGED","Tin đã thay đổi kể từ đề nghị. Cần thương lượng lại.");
        return new Agreement(offer.id(),offer.price(),offer.buyerFee(),offer.sellerFee(),offers.policy(offer.id()));
    }
    @Transactional(readOnly=true) public Agreement preview(long actor,long product,long id){return eligible(actor,product,owned(actor,product,id,false));}
    @Transactional public Agreement bind(long actor,long product,long id,String fingerprint){
        Stored saved=owned(actor,product,id,true);var agreement=eligible(actor,product,saved);
        if(saved.checkoutRequest()!=null&&!saved.checkoutRequest().equals(fingerprint))throw problem(409,"OFFER_CHECKOUT_CONFLICT","Đề nghị đã gắn với yêu cầu đặt hàng khác.");
        offers.bindCheckout(id,fingerprint);return agreement;
    }
    @Transactional public Optional<Long> existingCheckout(long actor,long product,long id,String fingerprint){
        var initial=owned(actor,product,id,false);var c=chat.owned(actor,initial.offer().conversationId(),false);
        chat.lockParties(c);chat.product(product,true);chat.owned(actor,c.id(),true);var saved=owned(actor,product,id,true);
        if(saved.offer().orderId()==null)return Optional.empty();
        if(!Objects.equals(saved.checkoutRequest(),fingerprint))throw problem(409,"OFFER_CHECKOUT_CONFLICT","Giá đã dùng cho yêu cầu đặt hàng khác.");
        if(Set.of("CANCELLED","REFUNDED").contains(offers.orderStatus(saved.offer().orderId())))throw problem(409,"OFFER_ALREADY_USED","Đơn đã kết thúc. Muốn mua lại cần đề nghị mới.");
        return Optional.of(saved.offer().orderId());
    }
    @Transactional(readOnly=true)
    public Optional<Agreement> findActiveAcceptedOffer(long actor,long product){
        var cOpt = conversations.findBuyerProductConversation(actor, product);
        if (cOpt.isEmpty()) return Optional.empty();
        long convId = cOpt.get().id();
        var list = offers.storedHistory(convId);
        for (int i = list.size() - 1; i >= 0; i--) {
            var stored = list.get(i);
            if ("ACCEPTED".equals(stored.offer().status()) && stored.offer().orderId() == null) {
                try {
                    return Optional.of(eligible(actor, product, stored));
                } catch (Exception ignored) {
                }
            }
        }
        return Optional.empty();
    }
    @Transactional(readOnly=true)
    public Map<Long, Agreement> findActiveAcceptedOffersForBuyer(long actor){
        var storedList = offers.activeAcceptedOffersForBuyer(actor);
        Map<Long, Agreement> result = new HashMap<>();
        for (var stored : storedList) {
            try {
                var c = chat.owned(actor, stored.offer().conversationId(), false);
                long product = c.product();
                if (result.containsKey(product)) continue;
                result.put(product, eligible(actor, product, stored));
            } catch (Exception ignored) {
            }
        }
        return result;
    }
}
