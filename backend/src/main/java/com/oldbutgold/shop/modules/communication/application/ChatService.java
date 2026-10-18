package com.oldbutgold.shop.modules.communication.application;
import com.oldbutgold.shop.modules.communication.api.ChatDtos.*;
import com.oldbutgold.shop.modules.communication.infrastructure.persistence.ChatRepository;
import com.oldbutgold.shop.modules.communication.infrastructure.persistence.ChatRepository.*;
import com.oldbutgold.shop.modules.communication.infrastructure.persistence.ChatRepository.Thread;
import com.oldbutgold.shop.modules.communication.infrastructure.persistence.OfferRepository;
import com.oldbutgold.shop.modules.catalog.application.ConversationCatalogFacade;
import com.oldbutgold.shop.modules.catalog.application.ConversationCatalogFacade.Product;
import com.oldbutgold.shop.modules.commerce.application.ConversationCommerceFacade;
import com.oldbutgold.shop.modules.identity.application.ConversationIdentityFacade;
import com.oldbutgold.shop.modules.platform.application.PlatformNotificationFacade;
import com.oldbutgold.shop.modules.platform.application.InteractionAuditFacade;
import com.oldbutgold.shop.modules.trustsafety.application.ConversationEvidenceFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import com.oldbutgold.shop.modules.identity.application.RegistrationException;
import java.time.*;
import java.util.*;
@Service
public class ChatService {
    private final InteractionAuditFacade audit;
    private final ChatRepository repository;private final OfferRepository offers;private final ConversationCatalogFacade catalog;
    private final ConversationCommerceFacade commerce;private final ConversationIdentityFacade identity;private final PlatformNotificationFacade notices;private final ConversationEvidenceFacade evidence;private final Clock clock;
    public ChatService(ChatRepository repository,OfferRepository offers,ConversationCatalogFacade catalog,ConversationCommerceFacade commerce,
      ConversationIdentityFacade identity,PlatformNotificationFacade notices,ConversationEvidenceFacade evidence,Clock clock,InteractionAuditFacade audit){this.repository=repository;this.offers=offers;this.catalog=catalog;this.commerce=commerce;this.identity=identity;this.notices=notices;this.evidence=evidence;this.clock=clock;this.audit=audit;}
    public Thread owned(long actor,long id,boolean lock){identity.customer(actor);return repository.owned(actor,id,lock).orElseThrow(()->problem(404,"CONVERSATION_NOT_FOUND","Không có hội thoại trong phạm vi của bạn."));}
    public Product product(long id,boolean lock){return catalog.product(id,lock).orElseThrow(()->problem(404,"CHAT_PRODUCT_NOT_FOUND","Không tìm thấy thông tin sản phẩm."));}
    public void lockParties(Thread c){identity.lockPair(c.buyer(),c.seller());}
    public void requireSending(long actor,Thread c){
        identity.requireChat(actor);
        if(!"ACTIVE".equals(c.status()))throw problem(409,"CONVERSATION_CLOSED","Hội thoại chỉ có thể xem lịch sử.");
        if(repository.blocked(c.buyer(),c.seller())&&!commerce.openObligations(c.buyer(),c.seller(),c.product()))throw problem(403,"SELLER_BUYER_BLOCKED","Người bán đã chặn tương tác mới. Hồ sơ và nghĩa vụ cũ vẫn được giữ.");
    }
    public void requireNegotiation(Thread c,Product p){
        identity.requireChat(c.buyer());identity.requireChat(c.seller());
        requirePurchase(c,p);
        if(!"ACTIVE".equals(c.status()))throw problem(409,"CONVERSATION_CLOSED","Hội thoại chỉ có thể xem lịch sử.");
    }
    public void requireTradingParties(long buyer,long seller){identity.customer(buyer);if(!identity.customer(seller).seller())throw problem(403,"SELLER_REQUIRED","Đối tác không còn quyền bán.");}
    public void requirePurchase(Thread c,Product p){
        identity.requireTrade(c.buyer(),c.seller(),p.requiresEkyc());
        if(p.deleted()||!"ACTIVE".equals(p.status())||p.available()<1)throw problem(409,"PRODUCT_UNAVAILABLE","Sản phẩm không còn đủ điều kiện mua hoặc thương lượng. Bạn vẫn xem được lịch sử chat.");
        if(repository.blocked(c.buyer(),c.seller()))throw problem(403,"SELLER_BUYER_BLOCKED","Quan hệ chặn ngăn thương lượng và mua mới với người bán này.");
    }
    @Transactional(readOnly=true) public Conversations list(long actor,String query,int page,int size){
        identity.customer(actor);String q=query==null?"":query.strip();if(q.length()>100)throw problem(400,"CHAT_QUERY_INVALID","Từ khóa tối đa100 ký tự.");
        int pg=Math.max(0,page),sz=Math.max(1,Math.min(50,size));
        return new Conversations(repository.list(actor,q,pg,sz).stream().map(c->summary(actor,c)).toList(),pg,sz,repository.count(actor,q),repository.unread(actor,0));
    }
    @Transactional public Detail start(long actor,Start request){
        identity.customer(actor);Product p=product(request.productId(),false);long buyer=actor,seller=p.sellerId();
        ConversationCommerceFacade.OrderContext order=null;
        if(request.orderId()!=null){order=commerce.order(actor,request.orderId()).filter(o->o.products().contains(request.productId())).orElseThrow(()->problem(404,"ORDER_CHAT_NOT_FOUND","Đơn hàng không thuộc phạm vi của bạn hoặc không có sản phẩm này."));buyer=order.buyerId();seller=order.sellerId();}
        if(buyer==seller||seller!=p.sellerId()||!identity.customer(buyer).buyer()||!identity.customer(seller).seller())throw problem(400,"CHAT_PARTIES_INVALID","Không thể tạo hội thoại với chính mình hoặc đối tác không hợp lệ.");
        identity.lockPair(buyer,seller);
        var prior=repository.byProduct(buyer,seller,p.id());long id;
        if(prior.isPresent())id=prior.get().id();
        else {
            identity.requireChat(actor);
            if(order==null){identity.requireNewConversation(buyer,seller);p=product(p.id(),true);if(p.deleted()||!"ACTIVE".equals(p.status())||p.available()<1)throw problem(409,"PRODUCT_UNAVAILABLE","Tin không còn mở bán; hãy mở chat từ đơn cũ nếu có.");}
            if(repository.blocked(buyer,seller)&&(order==null||!commerce.openObligations(buyer,seller,p.id())))throw problem(403,"SELLER_BUYER_BLOCKED","Người bán đã chặn tương tác mới.");
            id=repository.create(buyer,seller,p.id(),p.title(),p.price(),p.thumbnail(),clock.instant());
        }
        if(order!=null){owned(actor,id,true);repository.linkOrder(id,order.id());}
        return detail(actor,id,null,50);
    }
    @Transactional(readOnly=true) public Detail detail(long actor,long id,Long before,int size){
        Thread c=owned(actor,id,false);Product p=product(c.product(),false);boolean send=true,trade=true;String why=null;
        try{requireSending(actor,c);}catch(AccessDeniedException|RegistrationException|CommunicationException e){send=false;why=e.getMessage();}
        try{requireNegotiation(c,p);}catch(AccessDeniedException|RegistrationException|CommunicationException e){trade=false;if(why==null)why=e.getMessage();}
        int limit=Math.min(50,Math.max(1,size));var stored=repository.messages(id,before,limit+1);boolean more=stored.size()>limit;
        var slice=stored.stream().limit(limit).map(this::message).toList();var chronological=new ArrayList<>(slice);Collections.reverse(chronological);
        Long next=more? slice.getLast().id():null;
        boolean purchase=true;try{requirePurchase(c,p);}catch(AccessDeniedException|RegistrationException|CommunicationException e){purchase=false;}
        final boolean maySend=send,mayTrade=trade,mayBuy=purchase;
        var history=offers.storedHistory(id).stream().map(saved->{
            Offer o=effective(saved.offer());var actions=new ArrayList<Decision>();boolean snapshot=saved.creationKey()!=null&&o.productVersion()==p.version()&&Objects.equals(saved.revisionId(),p.revisionId());
            if(o.status().equals("PENDING")&&maySend){if(o.proposerId()==actor)actions.add(Decision.WITHDRAW);else{actions.add(Decision.REJECT);if(mayTrade&&snapshot){actions.add(Decision.ACCEPT);actions.add(Decision.COUNTER);}}}
            return o.forUi(o.status(),actions,mayBuy&&snapshot&&actor==c.buyer()&&o.status().equals("ACCEPTED")&&o.orderId()==null);
        }).toList();
        return new Detail(summary(actor,c),p,new Messages(chronological,next),history,repository.peerRead(id,actor),send,trade&&!offers.pending(id),why,repository.quota(id,actor),clock.instant());
    }
    private Offer effective(Offer o){if("PENDING".equals(o.status())&&o.expiresAt()!=null&&!o.expiresAt().isAfter(clock.instant()))return new Offer(o.id(),o.conversationId(),o.proposerId(),o.parentId(),o.price(),"EXPIRED",o.createdAt(),o.expiresAt(),o.respondedAt(),o.respondedBy(),o.version(),o.productVersion(),o.orderId(),o.buyerFee(),o.sellerFee());return o;}
    public Conversation summary(long actor,Thread c){long peer=actor==c.buyer()?c.seller():c.buyer();String name=identity.displayName(peer);return new Conversation(c.id(),c.buyer(),c.seller(),c.product(),c.title(),c.thumbnail(),name,actor==c.seller()?"SELLER":"BUYER",c.activity(),repository.preview(c.id()),repository.unread(actor,c.id()),repository.blocked(c.buyer(),c.seller()));}
    @Transactional public Message send(long actor,long id,Send request){
        Thread initial=owned(actor,id,false);lockParties(initial);Thread c=owned(actor,id,true);
        String text=request.text()==null?"":request.text().strip();if(text.isEmpty()||text.length()>2000)throw problem(400,"CHAT_TEXT_INVALID","Nội dung từ1 đến2000 ký tự.");
        var replay=repository.replay(id,request.clientId());
        if(replay.isPresent()){var m=replay.get();if(m.sender()!=actor||!m.type().equals("TEXT")||!m.content().equals(text)||!Objects.equals(m.reply(),request.replyTo()))throw problem(409,"CHAT_CLIENT_ID_CONFLICT","Mã gửi đã dùng cho nội dung khác.");return message(m);}
        requireSending(actor,c);
        if(request.replyTo()!=null&&repository.message(request.replyTo()).filter(m->m.conversation()==id).isEmpty())throw problem(400,"CHAT_REPLY_INVALID","Tin được trả lời không thuộc hội thoại.");
        long message=repository.append(id,actor,request.clientId(),"TEXT",text,request.replyTo(),null,null,null,null);
        notifyMessage(c,actor,message,"Tin nhắn mới",text.length()>80?text.substring(0,80)+"…":text,"CHAT_MESSAGE");
        return message(repository.message(message).orElseThrow());
    }
    public Message message(StoredMessage m){Media media=null;boolean attachment=Set.of("IMAGE","VIDEO").contains(m.type());boolean managed="CHAT_V31".equals(m.metadata().get("managedBy"));if(attachment){var data=m.metadata();boolean available=managed&&!"EXPIRED".equals(m.mediaStatus())&&(m.expires()==null||m.expires().isAfter(clock.instant())||retained(m));media=new Media(Objects.toString(data.get("mime"),m.type().equals("IMAGE")?"image/jpeg":"video/mp4"),((Number)data.getOrDefault("bytes",0)).longValue(),data.get("duration") instanceof Number n?n.doubleValue():null,available);}
        return new Message(m.id(),m.sender(),m.type(),attachment&&!managed?"":m.content(),m.created(),m.reply(),m.offer(),media);
    }
    private boolean retained(StoredMessage message){Thread c=repository.thread(message.conversation()).orElseThrow();return commerce.retainMedia(c.buyer(),c.seller(),c.product())||evidence.retainsMessage(message.id());}
    public void notifyMessage(Thread c,long actor,long message,String title,String body,String type){long peer=actor==c.buyer()?c.seller():c.buyer();notices.enqueue("chat-message:"+message,c.id(),List.of(new PlatformNotificationFacade.Notice(peer,type,title,body,PlatformNotificationFacade.Destination.CONVERSATION,c.id(),message)));}
    @Transactional public void read(long actor,long id,long message){owned(actor,id,true);repository.read(id,actor,message);}
    @Transactional(readOnly=true) public List<Block> blocks(long actor){if(!identity.customer(actor).seller())throw problem(403,"SELLER_REQUIRED","Chỉ người bán có danh sách chặn.");return repository.blocks(actor);}
    @Transactional(readOnly=true) public List<Map<String,Object>> searchBuyers(long actor,String query){if(!identity.customer(actor).seller())throw problem(403,"SELLER_REQUIRED","Chỉ người bán tìm người mua để quản lý chặn.");String q=query==null?"":query.strip();if(q.length()<2)return List.of();if(q.length()>100)throw problem(400,"CHAT_QUERY_INVALID","Từ khóa quá dài.");return repository.searchBuyers(actor,q);}
    @Transactional public List<Block> block(long actor,BlockInput request){identity.requireBlockManager(actor);if(actor==request.buyerId()||!identity.customer(request.buyerId()).buyer())throw problem(400,"BLOCK_TARGET_INVALID","Người mua cần chặn không hợp lệ.");identity.lockPair(request.buyerId(),actor);String reason=request.reason()==null?null:request.reason().strip();boolean before=repository.blocked(request.buyerId(),actor);String oldReason=repository.blocks(actor).stream().filter(b->b.buyerId()==request.buyerId()).map(b->Objects.toString(b.reason(),"")).findFirst().orElse("");if(before!=request.blocked()||request.blocked()&&!Objects.equals(oldReason,Objects.toString(reason,""))){repository.block(actor,request.buyerId(),reason,request.blocked());audit.blockChanged(actor,request.buyerId(),before,request.blocked(),reason);}return repository.blocks(actor);}
    public static CommunicationException problem(int status,String code,String message){return new CommunicationException(status,code,message);}
}
