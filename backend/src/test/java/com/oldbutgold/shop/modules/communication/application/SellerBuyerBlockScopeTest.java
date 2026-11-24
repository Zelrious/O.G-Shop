package com.oldbutgold.shop.modules.communication.application;

import com.oldbutgold.shop.modules.catalog.application.ConversationCatalogFacade;
import com.oldbutgold.shop.modules.commerce.application.ConversationCommerceFacade;
import com.oldbutgold.shop.modules.communication.api.ChatDtos;
import com.oldbutgold.shop.modules.communication.infrastructure.persistence.ChatRepository;
import com.oldbutgold.shop.modules.communication.infrastructure.persistence.OfferRepository;
import com.oldbutgold.shop.modules.identity.application.ConversationIdentityFacade;
import com.oldbutgold.shop.modules.platform.application.InteractionAuditFacade;
import com.oldbutgold.shop.modules.platform.application.PlatformNotificationFacade;
import com.oldbutgold.shop.modules.trustsafety.application.ConversationEvidenceFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SellerBuyerBlockScopeTest {

    private final Instant now = Instant.parse("2026-11-24T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    @Test
    @DisplayName("CHAT-only block blocks sending messages but allows purchasing and checkout")
    void chatOnlyBlock_blocksChatAllowsPurchase() {
        var repo = mock(ChatRepository.class);
        var commerce = mock(ConversationCommerceFacade.class);
        var identity = mock(ConversationIdentityFacade.class);
        var chat = new ChatService(
            repo, mock(OfferRepository.class), mock(ConversationCatalogFacade.class), commerce,
            identity, mock(PlatformNotificationFacade.class), mock(ConversationEvidenceFacade.class),
            clock, mock(InteractionAuditFacade.class), mock(TextModerator.class)
        );
        var facade = new DefaultNegotiatedCheckoutFacade(chat, repo, mock(OfferRepository.class), mock(PlatformNotificationFacade.class));

        var thread = new ChatRepository.Thread(10L, 1L, 2L, 50L, "Item", null, now, "ACTIVE");
        var product = new ConversationCatalogFacade.Product(50L, 2L, "Item", BigDecimal.valueOf(100000), null, "ACTIVE", 1L, 1L, 1, true, false, false);

        when(identity.customer(1L)).thenReturn(new ConversationIdentityFacade.Person(1L, "Buyer", false, true));
        when(identity.customer(2L)).thenReturn(new ConversationIdentityFacade.Person(2L, "Seller", true, true));
        when(repo.blockedForChat(1L, 2L)).thenReturn(true);
        when(repo.blockedForPurchase(1L, 2L)).thenReturn(false);
        when(commerce.openObligations(1L, 2L, 50L)).thenReturn(false);

        assertThatThrownBy(() -> chat.requireSending(1L, thread))
            .isInstanceOf(CommunicationException.class)
            .satisfies(ex -> assertThat(((CommunicationException) ex).code()).isEqualTo("SELLER_BUYER_BLOCKED"));

        assertThatCode(() -> chat.requirePurchase(thread, product)).doesNotThrowAnyException();
        assertThatCode(() -> facade.requireNotBlocked(1L, 2L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("PURCHASE-only block allows sending messages but blocks purchasing and checkout")
    void purchaseOnlyBlock_allowsChatBlocksPurchase() {
        var repo = mock(ChatRepository.class);
        var commerce = mock(ConversationCommerceFacade.class);
        var identity = mock(ConversationIdentityFacade.class);
        var chat = new ChatService(
            repo, mock(OfferRepository.class), mock(ConversationCatalogFacade.class), commerce,
            identity, mock(PlatformNotificationFacade.class), mock(ConversationEvidenceFacade.class),
            clock, mock(InteractionAuditFacade.class), mock(TextModerator.class)
        );
        var facade = new DefaultNegotiatedCheckoutFacade(chat, repo, mock(OfferRepository.class), mock(PlatformNotificationFacade.class));

        var thread = new ChatRepository.Thread(10L, 1L, 2L, 50L, "Item", null, now, "ACTIVE");
        var product = new ConversationCatalogFacade.Product(50L, 2L, "Item", BigDecimal.valueOf(100000), null, "ACTIVE", 1L, 1L, 1, true, false, false);

        when(identity.customer(1L)).thenReturn(new ConversationIdentityFacade.Person(1L, "Buyer", false, true));
        when(identity.customer(2L)).thenReturn(new ConversationIdentityFacade.Person(2L, "Seller", true, true));
        when(repo.blockedForChat(1L, 2L)).thenReturn(false);
        when(repo.blockedForPurchase(1L, 2L)).thenReturn(true);

        assertThatCode(() -> chat.requireSending(1L, thread)).doesNotThrowAnyException();

        assertThatThrownBy(() -> chat.requirePurchase(thread, product))
            .isInstanceOf(CommunicationException.class)
            .satisfies(ex -> assertThat(((CommunicationException) ex).code()).isEqualTo("SELLER_BUYER_BLOCKED"));

        assertThatThrownBy(() -> facade.requireNotBlocked(1L, 2L))
            .isInstanceOf(CommunicationException.class)
            .satisfies(ex -> assertThat(((CommunicationException) ex).code()).isEqualTo("SELLER_BUYER_BLOCKED"));
    }

    @Test
    @DisplayName("ChatService.block updates scope when changing an existing block from CHAT to PURCHASE")
    void block_updatesExistingBlockScope() {
        var repo = mock(ChatRepository.class);
        var identity = mock(ConversationIdentityFacade.class);
        var audit = mock(InteractionAuditFacade.class);
        var chat = new ChatService(
            repo, mock(OfferRepository.class), mock(ConversationCatalogFacade.class), mock(ConversationCommerceFacade.class),
            identity, mock(PlatformNotificationFacade.class), mock(ConversationEvidenceFacade.class),
            clock, audit, mock(TextModerator.class)
        );

        when(identity.customer(1L)).thenReturn(new ConversationIdentityFacade.Person(1L, "Buyer", false, true));
        when(repo.blocked(1L, 2L)).thenReturn(true);
        when(repo.blocks(2L)).thenReturn(List.of(new ChatDtos.Block(1L, "Buyer", now, "Spam", "CHAT")));

        chat.block(2L, new ChatDtos.BlockInput(1L, "Spam", true, ChatDtos.BlockScope.PURCHASE));

        verify(repo).block(2L, 1L, "Spam", true, "PURCHASE");
        verify(audit).blockChanged(2L, 1L, true, true, "Spam");
    }
}
