package com.oldbutgold.shop.modules.communication.application;

import com.oldbutgold.shop.modules.communication.api.ChatModerationDtos.*;
import com.oldbutgold.shop.modules.communication.infrastructure.persistence.ChatModerationRepository;
import com.oldbutgold.shop.modules.communication.infrastructure.persistence.ChatRepository;
import com.oldbutgold.shop.modules.platform.application.PlatformNotificationFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KtvChatModerationServiceTest {

    private ChatModerationRepository repository;
    private ChatRepository chatRepository;
    private PlatformNotificationFacade notices;
    private Clock clock;
    private KtvChatModerationService service;

    private final Instant now = Instant.parse("2026-11-08T10:00:00Z");

    @BeforeEach
    void setUp() {
        repository = mock(ChatModerationRepository.class);
        chatRepository = mock(ChatRepository.class);
        notices = mock(PlatformNotificationFacade.class);
        clock = Clock.fixed(now, ZoneId.of("UTC"));
        service = new KtvChatModerationService(repository, chatRepository, notices, clock);
    }

    @Test
    @DisplayName("getQueue: filters reports with search query, severity, penalty and pagination")
    void getQueue_success() {
        var summary = new ReportSummaryDto(
            1L, 10L, 100L, 20L, "Nguyễn Văn A", "a@example.com", null,
            "PHONE_LEAK", "MEDIUM", "PENDING", "0901234567", now
        );
        when(repository.searchReports(eq("Nguyễn"), eq("PENDING"), eq("MEDIUM"), isNull(), eq(true), eq(2), eq(0), eq(15)))
            .thenReturn(List.of(summary));
        when(repository.countReports(eq("Nguyễn"), eq("PENDING"), eq("MEDIUM"), isNull(), eq(true), eq(2)))
            .thenReturn(1L);
        when(repository.countPendingReports()).thenReturn(5);

        ReportPageResponse res = service.getQueue("Nguyễn", "PENDING", "MEDIUM", null, true, 2, 0, 15);

        assertThat(res.items()).hasSize(1);
        assertThat(res.total()).isEqualTo(1L);
        assertThat(res.pendingCount()).isEqualTo(5);
        assertThat(res.items().getFirst().offenderName()).isEqualTo("Nguyễn Văn A");
    }

    @Test
    @DisplayName("getConversationChunk: delegates correctly to repository")
    void getConversationChunk_delegation() {
        var msg = new KtvMessageItemDto(
            101L, 10L, 20L, "User A", null, "TEXT", "Alo bạn", null, false, null, null, null, null, now
        );
        var expectedChunk = new ConversationChunkResponse(10L, List.of(msg), true, false, 101L, 101L, 101L);

        when(repository.getConversationChunkForKtv(10L, 101L, null, null, 60))
            .thenReturn(expectedChunk);

        ConversationChunkResponse res = service.getConversationChunk(10L, 101L, null, null, 60);
        assertThat(res.messages()).hasSize(1);
        assertThat(res.hasOlder()).isTrue();
        assertThat(res.hasNewer()).isFalse();
        assertThat(res.pivotMessageId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("getUserViolationHistory: returns full user history or throws 404")
    void getUserViolationHistory() {
        var historyItem = new UserViolationHistoryItemDto(
            1L, 10L, 101L, "HARASSMENT_THREAT", "HIGH", "RESOLVED", "tao chém chết",
            "RESTRICT_CHAT", 15, "Cấm chat 7 ngày", "KTV Minh", now, now
        );
        var history = new UserViolationHistoryResponse(
            20L, "Nguyễn Văn Vi Phạm", "vipham@example.com", 75, 4, now.plusSeconds(86400 * 7), "ACTIVE", List.of(historyItem)
        );

        when(repository.getUserViolationHistory(20L)).thenReturn(Optional.of(history));
        when(repository.getUserViolationHistory(999L)).thenReturn(Optional.empty());

        UserViolationHistoryResponse res = service.getUserViolationHistory(20L);
        assertThat(res.fullName()).isEqualTo("Nguyễn Văn Vi Phạm");
        assertThat(res.reports()).hasSize(1);
        assertThat(res.reports().getFirst().reputationPenalty()).isEqualTo(15);

        assertThatThrownBy(() -> service.getUserViolationHistory(999L))
            .isInstanceOf(CommunicationException.class)
            .hasMessageContaining("Không tìm thấy người dùng");
    }

    @Test
    @DisplayName("manualFlagAndDecide: flags message, applies penalty, chat restriction, and enqueues notification")
    void manualFlagAndDecide_success() {
        Map<String, Object> msgInfo = Map.of(
            "messageId", 505L,
            "conversationId", 12L,
            "senderId", 30L,
            "content", "tao chém chết mày",
            "mediaMetadata", "{\"rawContent\":\"tao chém chết mày\"}",
            "createdAt", now
        );

        when(repository.findMessageInfo(505L)).thenReturn(Optional.of(msgInfo));
        when(chatRepository.getRecentContextJson(eq(12L), eq(30L), anyString())).thenReturn("[]");
        when(repository.createManualReport(eq(12L), eq(505L), eq(30L), eq(10L), eq("HARASSMENT_THREAT"), eq("RESTRICT_CHAT"), eq(15), anyString(), anyString(), anyString()))
            .thenReturn(99L);

        var detail = new ReportDetailDto(
            99L, 12L, 505L, 30L, "Kẻ Đe Dọa", "threat@example.com", 2, 85,
            now.plusSeconds(86400 * 7), 10L, "HARASSMENT_THREAT", "HIGH", "RESOLVED",
            "tao chém chết mày", "[]", "RESTRICT_CHAT", 15, "Đe dọa vũ lực",
            10L, "KTV Minh", now, now
        );
        when(repository.findReportById(99L)).thenReturn(Optional.of(detail));

        var req = new ManualFlagRequest("HARASSMENT_THREAT", "RESTRICT_CHAT", 15, 7, "Đe dọa vũ lực");
        ReportDetailDto res = service.manualFlagAndDecide(12L, 505L, 10L, req);

        assertThat(res.reportId()).isEqualTo(99L);
        verify(repository).applyPenalty(eq(30L), eq(15), eq("Đe dọa vũ lực"), eq(10L));
        verify(repository).restrictChat(eq(30L), any(Instant.class));

        // Verify in-app notification sent
        ArgumentCaptor<List<PlatformNotificationFacade.Notice>> captor = ArgumentCaptor.forClass(List.class);
        verify(notices).enqueue(anyString(), eq(30L), captor.capture());
        var capturedNotices = captor.getValue();
        assertThat(capturedNotices).hasSize(1);
        assertThat(capturedNotices.getFirst().recipientId()).isEqualTo(30L);
        assertThat(capturedNotices.getFirst().content()).contains("Trừ 15 điểm uy tín");
        assertThat(capturedNotices.getFirst().destination()).isEqualTo(PlatformNotificationFacade.Destination.ACCOUNT_SELF);
    }

    @Test
    @DisplayName("applyBatchDecision: applies sanction once for all batch messages, sends 1 notification")
    void applyBatchDecision_success() {
        var req = new BatchDecisionRequest(
            12L,
            List.of(501L, 502L),
            List.of(101L, 102L),
            "RESTRICT_CHAT",
            10,
            14,
            "Gửi nhiều số điện thoại vi phạm"
        );

        when(repository.findOffenderIdForConversation(12L, List.of(501L, 502L), List.of(101L, 102L))).thenReturn(30L);
        when(repository.resolveBatchReports(eq(12L), eq(30L), eq(List.of(501L, 502L)), eq(List.of(101L, 102L)),
            eq("RESTRICT_CHAT"), eq(10), eq("Gửi nhiều số điện thoại vi phạm"), eq(999L), any(Instant.class)))
            .thenReturn(2);

        BatchDecisionResponse res = service.applyBatchDecision(req, 999L);

        assertThat(res.resolvedCount()).isEqualTo(2);
        assertThat(res.offenderId()).isEqualTo(30L);
        assertThat(res.reputationPenalty()).isEqualTo(10);

        // Penalty applied ONCE only
        verify(repository, times(1)).applyPenalty(eq(30L), eq(10), anyString(), eq(999L));
        verify(repository, times(1)).restrictChat(eq(30L), any(Instant.class));

        // Exactly 1 notification enqueued
        ArgumentCaptor<List<PlatformNotificationFacade.Notice>> captor = ArgumentCaptor.forClass(List.class);
        verify(notices).enqueue(anyString(), eq(30L), captor.capture());
        var capturedNotices = captor.getValue();
        assertThat(capturedNotices).hasSize(1);
        assertThat(capturedNotices.getFirst().recipientId()).isEqualTo(30L);
        assertThat(capturedNotices.getFirst().content()).contains("Trừ 10 điểm uy tín");
    }

    @Test
    @DisplayName("getPendingViolations: returns pending items for conversation")
    void getPendingViolations_success() {
        var pending = new PendingViolationDto(501L, 101L, "PHONE_LEAK", "MEDIUM", "0901234567", now);
        when(repository.findPendingViolations(12L)).thenReturn(List.of(pending));

        var res = service.getPendingViolations(12L);
        assertThat(res).hasSize(1);
        assertThat(res.getFirst().messageId()).isEqualTo(501L);
    }
}

