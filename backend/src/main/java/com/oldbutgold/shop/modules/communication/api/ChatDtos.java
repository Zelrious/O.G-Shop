package com.oldbutgold.shop.modules.communication.api;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import com.oldbutgold.shop.modules.catalog.application.ConversationCatalogFacade.Product;
public final class ChatDtos {
    private ChatDtos(){}
    public record Start(@NotNull @Positive Long productId,Long orderId) {}
    public record Send(@NotNull UUID clientId,@NotBlank @Size(max=2000) String text,Long replyTo) {}
    public record Read(@Positive long messageId) {}
    public record Proposal(@NotNull UUID clientId,@NotNull @DecimalMin("1") @Digits(integer=15,fraction=0) BigDecimal price,Instant expiresAt) {}
    public enum Decision { ACCEPT,REJECT,COUNTER,WITHDRAW }
    public record Decide(@NotNull UUID commandKey,@NotNull Decision action,@Min(0) long expectedVersion,
                         @DecimalMin("1") @Digits(integer=15,fraction=0) BigDecimal price,Instant expiresAt) {}
    public record Conversation(long id,long buyerId,long sellerId,long productId,String title,String thumbnail,
                               String partnerName,String role,Instant lastActivity,String preview,long unread,boolean blocked) {}
    public record Conversations(List<Conversation> items,int page,int size,long total,long unread) {}
    public record Media(String mime,long bytes,Double duration,boolean available) {}
    public record Message(long id,long senderId,String type,String content,Instant createdAt,Long replyTo,Long offerId,Media media) {}
    public record Messages(List<Message> items,Long nextBefore) {}
    public record Offer(long id,long conversationId,long proposerId,Long parentId,BigDecimal price,String status,
                        Instant createdAt,Instant expiresAt,Instant respondedAt,Long respondedBy,long version,
                        long productVersion,Long orderId,BigDecimal buyerFee,BigDecimal sellerFee,List<Decision> allowedActions,boolean canCheckout) {
        public Offer(long id,long conversationId,long proposerId,Long parentId,BigDecimal price,String status,Instant createdAt,Instant expiresAt,Instant respondedAt,Long respondedBy,long version,long productVersion,Long orderId,BigDecimal buyerFee,BigDecimal sellerFee){this(id,conversationId,proposerId,parentId,price,status,createdAt,expiresAt,respondedAt,respondedBy,version,productVersion,orderId,buyerFee,sellerFee,List.of(),false);}
        public Offer forUi(String state,List<Decision> actions,boolean checkout){return new Offer(id,conversationId,proposerId,parentId,price,state,createdAt,expiresAt,respondedAt,respondedBy,version,productVersion,orderId,buyerFee,sellerFee,actions,checkout);}
    }
    public record Quota(int images,int videos,long bytes,int maxImages,int maxVideos,long maxBytes,String date) {}
    public record Detail(Conversation conversation,Product product,Messages messages,List<Offer> offers,
                         long peerReadMessageId,boolean canSend,boolean canOffer,String restrictionReason,Quota quota,Instant serverTime) {}
    public record Block(long buyerId,String name,Instant blockedAt,String reason) {}
    public record BlockInput(@Positive long buyerId,@Size(max=500) String reason,boolean blocked) {}
}
