package com.oldbutgold.shop.modules.communication.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oldbutgold.shop.modules.catalog.application.ConversationCatalogFacade;
import com.oldbutgold.shop.modules.commerce.application.AccountObligationsFacade;
import com.oldbutgold.shop.modules.commerce.application.ConversationCommerceFacade;
import com.oldbutgold.shop.modules.communication.api.ChatDtos;
import com.oldbutgold.shop.modules.communication.infrastructure.persistence.ChatModerationRepository;
import com.oldbutgold.shop.modules.communication.infrastructure.persistence.ChatRepository;
import com.oldbutgold.shop.modules.communication.infrastructure.persistence.OfferRepository;
import com.oldbutgold.shop.modules.identity.api.AccountManagementDtos;
import com.oldbutgold.shop.modules.identity.application.AccountManagementService;
import com.oldbutgold.shop.modules.identity.application.ConversationIdentityFacade;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.AccountManagementRepository;
import com.oldbutgold.shop.modules.platform.application.GovernanceHistoryFacade;
import com.oldbutgold.shop.modules.platform.application.InteractionAuditFacade;
import com.oldbutgold.shop.modules.platform.application.PlatformNotificationFacade;
import com.oldbutgold.shop.modules.trustsafety.application.AccountTrustFacade;
import com.oldbutgold.shop.modules.trustsafety.application.ConversationEvidenceFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatModerationPrivacyAndSyncTest {

    private final Instant now = Instant.parse("2026-11-24T06:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    @Test
    @DisplayName("KTV readMessageMedia: blocks access to unflagged/unreported message media with 403 MEDIA_ACCESS_DENIED")
    void readMessageMedia_blocksUnflaggedMessage() {
        var modRepo = mock(ChatModerationRepository.class);
        var chatRepo = mock(ChatRepository.class);
        var notices = mock(PlatformNotificationFacade.class);
        var mediaService = mock(ChatMediaService.class);
        var service = new KtvChatModerationService(modRepo, chatRepo, notices, clock, mediaService);

        when(modRepo.isMessageReportedOrFlagged(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.readMessageMedia(999L))
            .isInstanceOf(CommunicationException.class)
            .satisfies(ex -> {
                var ce = (CommunicationException) ex;
                assertThat(ce.status()).isEqualTo(403);
                assertThat(ce.code()).isEqualTo("MEDIA_ACCESS_DENIED");
            });

        verify(mediaService, never()).readForModeration(anyLong());
    }

    @Test
    @DisplayName("KTV readMessageMedia: allows flagged/reported message to proceed to media lookup")
    void readMessageMedia_allowsFlaggedMessage() {
        var modRepo = mock(ChatModerationRepository.class);
        var chatRepo = mock(ChatRepository.class);
        var notices = mock(PlatformNotificationFacade.class);
        var mediaService = mock(ChatMediaService.class);
        var service = new KtvChatModerationService(modRepo, chatRepo, notices, clock, mediaService);

        var expectedFile = new ChatMediaService.MediaFile(new byte[]{1, 2, 3}, "image/jpeg");
        when(modRepo.isMessageReportedOrFlagged(101L)).thenReturn(true);
        when(mediaService.readForModeration(101L)).thenReturn(expectedFile);

        var actual = service.readMessageMedia(101L);

        assertThat(actual).isSameAs(expectedFile);
        verify(mediaService).readForModeration(101L);
    }

    @Test
    @DisplayName("ChatService.reportMessage: creates USER_REPORTED moderation report with empty context_snippet")
    void reportMessage_createsUserReport() {
        var chatRepo = mock(ChatRepository.class);
        var offers = mock(OfferRepository.class);
        var catalog = mock(ConversationCatalogFacade.class);
        var commerce = mock(ConversationCommerceFacade.class);
        var identity = mock(ConversationIdentityFacade.class);
        var notices = mock(PlatformNotificationFacade.class);
        var evidence = mock(ConversationEvidenceFacade.class);
        var audit = mock(InteractionAuditFacade.class);
        var moderator = mock(TextModerator.class);
        var service = new ChatService(chatRepo, offers, catalog, commerce, identity, notices, evidence, clock, audit, moderator);

        var thread = new ChatRepository.Thread(10L, 1L, 2L, 50L, "Vintage Watch", null, now, "ACTIVE");
        var peerMsg = new ChatRepository.StoredMessage(500L, 10L, 2L, "TEXT", "Chuyển khoản ngoài sàn đi bạn", now, null, null, null, Map.of(), null, null);

        when(chatRepo.owned(1L, 10L, false)).thenReturn(Optional.of(thread));
        when(chatRepo.message(500L)).thenReturn(Optional.of(peerMsg));
        when(chatRepo.hasActiveUserReport(500L, 1L)).thenReturn(false);

        ChatDtos.ReportResult result = service.reportMessage(1L, 10L, 500L, new ChatDtos.ReportMessage("Dụ giao dịch ngoài sàn"));

        assertThat(result.status()).isEqualTo("REPORTED");
        assertThat(result.messageId()).isEqualTo(500L);
        verify(chatRepo).createModerationReport(
            eq(10L),
            eq(2L),
            eq(1L),
            eq(500L),
            eq("USER_REPORTED"),
            eq("MEDIUM"),
            eq("PENDING"),
            eq("[Lý do báo cáo: Dụ giao dịch ngoài sàn] Chuyển khoản ngoài sàn đi bạn"),
            eq("[]")
        );
    }

    @Test
    @DisplayName("ChatService.reportMessage: rejects self-report and handles duplicate report idempotently")
    void reportMessage_rejectsSelfAndIdempotentDuplicate() {
        var chatRepo = mock(ChatRepository.class);
        var service = new ChatService(
            chatRepo,
            mock(OfferRepository.class),
            mock(ConversationCatalogFacade.class),
            mock(ConversationCommerceFacade.class),
            mock(ConversationIdentityFacade.class),
            mock(PlatformNotificationFacade.class),
            mock(ConversationEvidenceFacade.class),
            clock,
            mock(InteractionAuditFacade.class),
            mock(TextModerator.class)
        );

        var thread = new ChatRepository.Thread(10L, 1L, 2L, 50L, "Vintage Watch", null, now, "ACTIVE");
        var ownMsg = new ChatRepository.StoredMessage(501L, 10L, 1L, "TEXT", "Tin của mình", now, null, null, null, Map.of(), null, null);
        var peerMsg = new ChatRepository.StoredMessage(502L, 10L, 2L, "TEXT", "Tin của đối tác", now, null, null, null, Map.of(), null, null);

        when(chatRepo.owned(1L, 10L, false)).thenReturn(Optional.of(thread));
        when(chatRepo.message(501L)).thenReturn(Optional.of(ownMsg));
        when(chatRepo.message(502L)).thenReturn(Optional.of(peerMsg));
        when(chatRepo.hasActiveUserReport(502L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.reportMessage(1L, 10L, 501L, new ChatDtos.ReportMessage("test")))
            .isInstanceOf(CommunicationException.class)
            .satisfies(ex -> {
                var ce = (CommunicationException) ex;
                assertThat(ce.status()).isEqualTo(400);
                assertThat(ce.code()).isEqualTo("CANNOT_REPORT_OWN_MESSAGE");
            });

        ChatDtos.ReportResult dup = service.reportMessage(1L, 10L, 502L, null);
        assertThat(dup.status()).isEqualTo("ALREADY_REPORTED");
        verify(chatRepo, never()).createModerationReport(anyLong(), anyLong(), any(), any(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("AccountManagementService: clears chat_restricted_until on ACTIVATE and LIFT(CHAT), preserves on LIFT(BUY_ALL)")
    void accountManagement_syncsChatRestrictionClear() {
        var repo = mock(AccountManagementRepository.class);
        var obligations = mock(AccountObligationsFacade.class);
        var trust = mock(AccountTrustFacade.class);
        var history = mock(GovernanceHistoryFacade.class);
        var notices = mock(PlatformNotificationFacade.class);
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new AccountManagementService(repo, obligations, trust, history, notices, mapper, clock);

        UUID chatRestrictionId = UUID.randomUUID();
        UUID buyRestrictionId = UUID.randomUUID();
        var chatRestriction = new AccountManagementDtos.Restriction(
            chatRestrictionId, "CHAT", 99L, "Spam", now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(6)), null, false
        );
        var buyRestriction = new AccountManagementDtos.Restriction(
            buyRestrictionId, "BUY_ALL", 99L, "Policy", now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(6)), null, false
        );

        var lockedState = new AccountManagementRepository.State(
            20L, "User", "u@test.com", "0900000000", List.of("BUYER"), "LOCKED", "LOCKED", 1L, null, "Violation", List.of(), now
        );
        var activeWithRestrictions = new AccountManagementRepository.State(
            20L, "User", "u@test.com", "0900000000", List.of("BUYER"), "ACTIVE", "ACTIVE", 2L, null, null, List.of(chatRestriction, buyRestriction), now
        );

        when(repo.activeKtv(99L)).thenReturn(true);
        when(history.command(anyString())).thenReturn(Optional.empty());
        when(repo.legacy(20L)).thenReturn(List.of());
        when(repo.snapshot(anyLong(), any(), any(), any(), any())).thenReturn(Map.of("version", 2L));

        // 1. ACTIVATE -> clears chat_restricted_until (7-arg update with activate=true, clearChat=true)
        when(repo.customer(20L, true)).thenReturn(Optional.of(lockedState));
        service.command(99L, 20L, new AccountManagementDtos.Command(
            AccountManagementDtos.Action.ACTIVATE, 1L, "cmd-activate", "Mở khóa tài khoản",
            null, null, null, null, null, null, null, null
        ));
        verify(repo).update(eq(lockedState), eq("ACTIVE"), isNull(), isNull(), anyList(), eq(true), eq(true));

        // 2. LIFT CHAT -> clears chat_restricted_until (7-arg update with activate=false, clearChat=true)
        when(repo.customer(20L, true)).thenReturn(Optional.of(activeWithRestrictions));
        service.command(99L, 20L, new AccountManagementDtos.Command(
            AccountManagementDtos.Action.LIFT, 2L, "cmd-lift-chat", "Gỡ hạn chế chat",
            null, chatRestrictionId, null, null, null, null, null, null
        ));
        verify(repo).update(eq(activeWithRestrictions), eq("ACTIVE"), isNull(), isNull(), anyList(), eq(false), eq(true));

        // 3. LIFT BUY_ALL -> does NOT clear chat_restricted_until (6-arg update with activate=false)
        service.command(99L, 20L, new AccountManagementDtos.Command(
            AccountManagementDtos.Action.LIFT, 2L, "cmd-lift-buy", "Gỡ hạn chế mua",
            null, buyRestrictionId, null, null, null, null, null, null
        ));
        verify(repo).update(eq(activeWithRestrictions), eq("ACTIVE"), isNull(), isNull(), anyList(), eq(false));
    }
}
