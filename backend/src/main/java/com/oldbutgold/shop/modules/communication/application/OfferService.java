package com.oldbutgold.shop.modules.communication.application;
import com.oldbutgold.shop.modules.communication.api.ChatDtos.*;
import com.oldbutgold.shop.modules.communication.infrastructure.persistence.ChatRepository;
import com.oldbutgold.shop.modules.communication.infrastructure.persistence.ChatRepository.Thread;
import com.oldbutgold.shop.modules.communication.infrastructure.persistence.OfferRepository;
import com.oldbutgold.shop.modules.commerce.application.ConversationCommerceFacade;
import com.oldbutgold.shop.modules.platform.application.PlatformNotificationFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.oldbutgold.shop.modules.communication.application.ChatService.problem;
@Service
public class OfferService {
    private final ChatService chat;private final ChatRepository messages;private final OfferRepository repository;
    private final ConversationCommerceFacade commerce;private final PlatformNotificationFacade notices;private final Clock clock;
    public OfferService(ChatService chat,ChatRepository messages,OfferRepository repository,ConversationCommerceFacade commerce,PlatformNotificationFacade notices,Clock clock){this.chat=chat;this.messages=messages;this.repository=repository;this.commerce=commerce;this.notices=notices;this.clock=clock;}
    private Map<String,Object> normalized(Object value){return messages.decode(messages.encode(value));}
    private Instant expiry(Instant value){if(value==null)return null;Instant end=value.truncatedTo(java.time.temporal.ChronoUnit.MICROS);if(!end.isAfter(clock.instant()))throw problem(400,"OFFER_EXPIRY_INVALID","Hạn phản hồi phải ở tương lai.");return end;}
    private BigDecimal price(BigDecimal value){if(value==null||value.signum()<=0||value.stripTrailingZeros().scale()>0||value.precision()>15)throw problem(400,"OFFER_PRICE_INVALID","Giá đề nghị phải là số nguyên dương, tối đa15 chữ số.");return value.stripTrailingZeros();}
    @Transactional public Offer propose(long actor,long conversation,Proposal body){
        Thread initial=chat.owned(actor,conversation,false);repository.lockCommand(body.clientId());chat.lockParties(initial);
        var product=chat.product(initial.product(),true);Thread c=chat.owned(actor,conversation,true);
        var request=normalized(Map.of("actor",actor,"conversation",conversation,"body",body));
        var replay=repository.creation(body.clientId());
        if(replay.isPresent()){var saved=replay.get();if(!saved.creation().equals(request))throw problem(409,"OFFER_COMMAND_CONFLICT","Mã đề nghị đã dùng cho thao tác khác.");return saved.offer();}
        if(actor!=c.buyer())throw problem(403,"OFFER_BUYER_REQUIRED","Người mua gửi đề nghị đầu tiên; người bán có thể trả giá khác khi phản hồi.");
        chat.requireNegotiation(c,product);expire(c);
        if(repository.pending(conversation))throw problem(409,"OFFER_PENDING_EXISTS","Hội thoại đã có đề nghị đang chờ. Chờ phản hồi hoặc rút đề nghị đó.");
        BigDecimal amount=price(body.price());Instant end=expiry(body.expiresAt());var fees=commerce.fees(amount);
        if(fees.sellerFee().compareTo(amount)>0)throw problem(400,"OFFER_PRICE_INVALID","Giá đề nghị chưa đủ trả phí giao dịch hiện hành.");
        long id=repository.create(c.id(),actor,body.clientId(),amount,end,null,product,fees,request);
        long message=messages.append(c.id(),actor,offerMessageKey(body.clientId()),"OFFER","Đề nghị giá",null,id,null,null,null);
        chat.notifyMessage(c,actor,message,"Đề nghị trả giá mới","Có đề nghị giá mới trong hội thoại của bạn.","OFFER_PROPOSED");
        return repository.get(id,false).orElseThrow().offer();
    }
    @Transactional public Offer decide(long actor,long id,Decide body){
        var lookup=repository.get(id,false).orElseThrow(()->problem(404,"OFFER_NOT_FOUND","Không tìm thấy đề nghị trong phạm vi của bạn."));
        Thread initial=chat.owned(actor,lookup.offer().conversationId(),false);repository.lockCommand(body.commandKey());chat.lockParties(initial);
        var product=chat.product(initial.product(),true);Thread c=chat.owned(actor,initial.id(),true);
        var request=normalized(Map.of("actor",actor,"offerId",id,"body",body));
        var replay=repository.decision(body.commandKey());
        if(replay.isPresent()){var saved=replay.get();if(!saved.decision().equals(request))throw problem(409,"OFFER_COMMAND_CONFLICT","Mã quyết định đã dùng cho thao tác khác.");return body.action()==Decision.COUNTER?repository.creation(body.commandKey()).orElseThrow().offer():saved.offer();}
        expire(c);var old=repository.get(id,true).orElseThrow();Offer offer=old.offer();
        if(offer.version()!=body.expectedVersion())throw problem(409,"OFFER_VERSION_CONFLICT","Đề nghị đã thay đổi. Tải lại hội thoại.");
        if(!offer.status().equals("PENDING"))throw problem(409,"OFFER_STATE_CONFLICT","Đề nghị không còn chờ phản hồi.");
        chat.requireSending(actor,c);
        if(body.action()==Decision.WITHDRAW){if(actor!=offer.proposerId())throw problem(403,"OFFER_ACTION_FORBIDDEN","Chỉ bên gửi được rút đề nghị.");}
        else if(actor==offer.proposerId())throw problem(403,"OFFER_ACTION_FORBIDDEN","Bên gửi không tự phản hồi đề nghị của mình.");
        if(body.action()==Decision.ACCEPT||body.action()==Decision.COUNTER){
            chat.requireNegotiation(c,product);
            if(offer.productVersion()!=product.version()||!Objects.equals(old.revisionId(),product.revisionId()))throw problem(409,"OFFER_LISTING_CHANGED","Tin đã thay đổi kể từ khi đề nghị được gửi. Rút/từ chối rồi gửi đề nghị mới.");
        }
        String state=switch(body.action()){case ACCEPT->"ACCEPTED";case REJECT->"REJECTED";case COUNTER->"COUNTERED";case WITHDRAW->"WITHDRAWN";};
        repository.decide(old,actor,state,body.commandKey(),request);long result=id;
        if(body.action()==Decision.COUNTER){
            BigDecimal amount=price(body.price());Instant end=expiry(body.expiresAt());var fees=commerce.fees(amount);
            if(fees.sellerFee().compareTo(amount)>0)throw problem(400,"OFFER_PRICE_INVALID","Giá trả khác chưa đủ trả phí giao dịch.");
            result=repository.create(c.id(),actor,body.commandKey(),amount,end,id,product,fees,normalized(Map.of("actor",actor,"conversation",c.id(),"counterTo",id,"body",body)));
            long message=messages.append(c.id(),actor,offerMessageKey(body.commandKey()),"OFFER","Đề nghị giá khác",null,result,null,null,null);
            chat.notifyMessage(c,actor,message,"Đề nghị trả giá khác","Đối tác đã đưa ra giá khác.","OFFER_COUNTERED");
        }else{
            long message=messages.append(c.id(),actor,UUID.nameUUIDFromBytes(("offer-decision:"+body.commandKey()).getBytes(java.nio.charset.StandardCharsets.UTF_8)),"SYSTEM",switch(body.action()){case ACCEPT->"Đã chấp nhận giá. Chưa giữ hàng; người mua cần đặt đơn.";case REJECT->"Đã từ chối đề nghị giá.";case WITHDRAW->"Đã rút đề nghị giá.";default->"Đã phản hồi giá.";},null,null,null,null,null);
            noticeBoth(c,"offer-decision:"+id+":"+body.commandKey(),"OFFER_DECIDED","Kết quả thương lượng",state.equals("ACCEPTED")?"Giá đã được chấp nhận. Chưa giữ hàng hoặc tạo đơn; mở hội thoại để đặt mua.":"Đề nghị đã được "+(state.equals("REJECTED")?"từ chối.":"rút lại."),message);
        }
        return repository.get(result,false).orElseThrow().offer();
    }
    public void expire(Thread c){for(Offer o:repository.expire(c.id()))noticeBoth(c,"offer-expired:"+o.id(),"OFFER_EXPIRED","Đề nghị đã hết hạn","Đề nghị chưa được phản hồi trong thời hạn. Bạn có thể tiếp tục trao đổi.",o.version());}
    @Transactional(readOnly=true) public List<Long> expiredConversations(){return repository.expiredConversations();}
    @Transactional public void expireConversation(long conversation){var thread=messages.thread(conversation);if(thread.isPresent()){chat.lockParties(thread.get());messages.thread(conversation).ifPresent(this::expire);}}
    private void noticeBoth(Thread c,String key,String type,String title,String content,long revision){notices.enqueue(key,c.id(),List.of(new PlatformNotificationFacade.Notice(c.buyer(),type,title,content,PlatformNotificationFacade.Destination.CONVERSATION,c.id(),revision),new PlatformNotificationFacade.Notice(c.seller(),type,title,content,PlatformNotificationFacade.Destination.CONVERSATION,c.id(),revision)));}
    private UUID offerMessageKey(UUID client){return UUID.nameUUIDFromBytes(("offer-message:"+client).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
}
