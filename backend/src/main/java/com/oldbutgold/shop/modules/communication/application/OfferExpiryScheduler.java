package com.oldbutgold.shop.modules.communication.application;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component
@ConditionalOnProperty(name="app.chat.expiry-enabled",havingValue="true",matchIfMissing=true)
public class OfferExpiryScheduler {
    private final OfferService offers;
    public OfferExpiryScheduler(OfferService offers){this.offers=offers;}
    @Scheduled(fixedDelayString="${app.chat.expiry-delay-ms:30000}") public void expire(){for(long id:offers.expiredConversations())offers.expireConversation(id);}
}
