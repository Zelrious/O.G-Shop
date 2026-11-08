package com.oldbutgold.shop.modules.communication.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.communication.api.ChatModerationDtos.*;
import com.oldbutgold.shop.modules.communication.infrastructure.persistence.ChatModerationRepository;
import com.oldbutgold.shop.modules.communication.infrastructure.persistence.ChatRepository;
import com.oldbutgold.shop.modules.platform.application.PlatformNotificationFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class KtvChatModerationService {

    private final ChatModerationRepository repository;
    private final ChatRepository chatRepository;
    private final PlatformNotificationFacade notices;
    private final Clock clock;
    private final ChatMediaService chatMediaService;
    private final ObjectMapper json = new ObjectMapper();

    public KtvChatModerationService(ChatModerationRepository repository,
                                    ChatRepository chatRepository,
                                    PlatformNotificationFacade notices,
                                    Clock clock) {
        this(repository, chatRepository, notices, clock, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public KtvChatModerationService(ChatModerationRepository repository,
                                    ChatRepository chatRepository,
                                    PlatformNotificationFacade notices,
                                    Clock clock,
                                    @org.springframework.beans.factory.annotation.Autowired(required = false) ChatMediaService chatMediaService) {
        this.repository = repository;
        this.chatRepository = chatRepository;
        this.notices = notices;
        this.clock = clock;
        this.chatMediaService = chatMediaService;
    }

    public ChatMediaService.MediaFile readMessageMedia(long messageId) {
        if (chatMediaService == null) {
            throw new CommunicationException(503, "CHAT_MEDIA_SERVICE_UNAVAILABLE", "Dịch vụ tải media chưa sẵn sàng.");
        }
        return chatMediaService.readForModeration(messageId);
    }

    @Transactional(readOnly = true)
    public ReportPageResponse getQueue(String status, String severity, String type, int page, int size) {
        return getQueue(null, status, severity, type, null, null, page, size);
    }

    @Transactional(readOnly = true)
    public ReportPageResponse getQueue(String query, String status, String severity, String type,
                                       Boolean hasPenalty, Integer minViolationScore, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(50, size));
        List<ReportSummaryDto> items = repository.searchReports(query, status, severity, type, hasPenalty, minViolationScore, safePage, safeSize);
        long total = repository.countReports(query, status, severity, type, hasPenalty, minViolationScore);
        int pending = repository.countPendingReports();
        return new ReportPageResponse(items, safePage, safeSize, total, pending);
    }

    @Transactional(readOnly = true)
    public ReportDetailDto getDetail(long reportId) {
        return repository.findReportById(reportId)
            .orElseThrow(() -> new CommunicationException(404, "REPORT_NOT_FOUND", "Không tìm thấy hồ sơ kiểm duyệt tin nhắn."));
    }

    @Transactional
    public ReportDetailDto decide(long reportId, long ktvUserId, ReportDecisionRequest request) {
        ReportDetailDto report = getDetail(reportId);

        String action = request.action() != null ? request.action().toUpperCase().trim() : "DISMISS";
        int penalty = Math.max(0, request.reputationPenalty());
        String note = request.decisionNote() != null ? request.decisionNote().trim() : "Đã xử lý theo quy định UC74";
        Instant now = clock.instant();

        // 1. Update report record
        repository.applyDecision(reportId, action, penalty, note, ktvUserId, now);

        // 2. Deduct reputation points if specified
        if (penalty > 0) {
            repository.applyPenalty(report.offenderId(), penalty, note, ktvUserId);
        }

        // 3. Apply restrictions & send notification
        int days = (request.restrictionDays() != null && request.restrictionDays() > 0)
            ? request.restrictionDays() : 14;

        switch (action) {
            case "RESTRICT_CHAT" -> {
                Instant until = now.plus(Duration.ofDays(days));
                repository.restrictChat(report.offenderId(), until);
            }
            case "LOCK_ACCOUNT" -> {
                Instant until = now.plus(Duration.ofDays(90));
                repository.lockAccount(report.offenderId(), until);
            }
            default -> {}
        }

        if (!"DISMISS".equals(action)) {
            sendSanctionNotification(report.offenderId(), action, penalty, days, note);
        }

        return getDetail(reportId);
    }

    // =========================================================================
    // Chunked Conversation Stream for KTV
    // =========================================================================
    @Transactional(readOnly = true)
    public ConversationChunkResponse getConversationChunk(long conversationId, Long aroundMessageId,
                                                          Long before, Long after, int size) {
        return repository.getConversationChunkForKtv(conversationId, aroundMessageId, before, after, size);
    }

    // =========================================================================
    // User Violation History
    // =========================================================================
    @Transactional(readOnly = true)
    public UserViolationHistoryResponse getUserViolationHistory(long userId) {
        return repository.getUserViolationHistory(userId)
            .orElseThrow(() -> new CommunicationException(404, "USER_NOT_FOUND", "Không tìm thấy người dùng."));
    }

    // =========================================================================
    // Manual Flagging & Direct Sanction
    // =========================================================================
    @Transactional
    public ReportDetailDto manualFlagAndDecide(long conversationId, long messageId, long ktvUserId, ManualFlagRequest request) {
        Map<String, Object> msgInfo = repository.findMessageInfo(messageId)
            .orElseThrow(() -> new CommunicationException(404, "MESSAGE_NOT_FOUND", "Không tìm thấy tin nhắn cần xử lý."));

        long offenderId = ((Number) msgInfo.get("senderId")).longValue();
        long actualConvId = ((Number) msgInfo.get("conversationId")).longValue();

        String rawContent = Objects.toString(msgInfo.get("content"), "");
        String metaStr = (String) msgInfo.get("mediaMetadata");
        if (metaStr != null && !metaStr.isBlank()) {
            try {
                Map<String, Object> meta = json.readValue(metaStr, new TypeReference<>() {});
                if (meta.containsKey("rawContent")) {
                    rawContent = Objects.toString(meta.get("rawContent"), rawContent);
                }
            } catch (Exception ignored) {}
        }

        String action = request.action() != null ? request.action().toUpperCase().trim() : "WARN";
        int penalty = Math.max(0, request.reputationPenalty());
        int days = (request.restrictionDays() != null && request.restrictionDays() > 0)
            ? request.restrictionDays() : 14;
        String note = (request.decisionNote() != null && !request.decisionNote().isBlank())
            ? request.decisionNote().trim() : "KTV gắn cờ vi phạm thủ công (UC56/UC74)";
        String violationType = (request.violationType() != null && !request.violationType().isBlank())
            ? request.violationType().trim() : "HARASSMENT_THREAT";

        Instant now = clock.instant();

        // 1. Deduct points if needed
        if (penalty > 0) {
            repository.applyPenalty(offenderId, penalty, note, ktvUserId);
        }

        // 2. Apply chat restriction or lock
        switch (action) {
            case "RESTRICT_CHAT" -> repository.restrictChat(offenderId, now.plus(Duration.ofDays(days)));
            case "LOCK_ACCOUNT" -> repository.lockAccount(offenderId, now.plus(Duration.ofDays(90)));
        }

        // 3. Build context JSON
        String contextJson = chatRepository.getRecentContextJson(actualConvId, offenderId, rawContent);

        // 4. Create resolved report record
        long reportId = repository.createManualReport(
            actualConvId, messageId, offenderId, ktvUserId, violationType, action, penalty, note, rawContent, contextJson
        );

        // 5. Send notification to offender
        if (!"DISMISS".equals(action)) {
            sendSanctionNotification(offenderId, action, penalty, days, note);
        }

        return getDetail(reportId);
    }

    // =========================================================================
    // Batch Moderation Decision & Pending Violations (UC56 / UC74)
    // =========================================================================
    @Transactional
    public BatchDecisionResponse applyBatchDecision(BatchDecisionRequest request, long ktvUserId) {
        long conversationId = request.conversationId();
        List<Long> messageIds = request.messageIds() != null ? request.messageIds() : List.of();
        List<Long> reportIds = request.reportIds() != null ? request.reportIds() : List.of();

        long offenderId = repository.findOffenderIdForConversation(conversationId, messageIds, reportIds);
        if (offenderId <= 0) {
            throw new CommunicationException(400, "OFFENDER_NOT_FOUND", "Không xác định được đối tượng vi phạm trong cuộc trò chuyện.");
        }

        String action = request.action() != null ? request.action().toUpperCase().trim() : "WARN";
        int penalty = Math.max(0, request.reputationPenalty());
        int days = (request.restrictionDays() != null && request.restrictionDays() > 0)
            ? request.restrictionDays() : 14;
        String note = (request.decisionNote() != null && !request.decisionNote().isBlank())
            ? request.decisionNote().trim() : "Xử lý hàng loạt vi phạm tin nhắn (UC56/UC74)";
        Instant now = clock.instant();

        // 1. Resolve / create batch reports in DB (penalty recorded once on primary report)
        int resolvedCount = repository.resolveBatchReports(
            conversationId, offenderId, messageIds, reportIds, action, penalty, note, ktvUserId, now
        );

        // 2. Deduct reputation penalty ONCE only
        if (penalty > 0) {
            repository.applyPenalty(offenderId, penalty, note, ktvUserId);
        }

        // 3. Apply restrictions if needed
        switch (action) {
            case "RESTRICT_CHAT" -> repository.restrictChat(offenderId, now.plus(Duration.ofDays(days)));
            case "LOCK_ACCOUNT" -> repository.lockAccount(offenderId, now.plus(Duration.ofDays(90)));
            default -> {}
        }

        // 4. Send EXACTLY ONE notification to offender
        if (!"DISMISS".equals(action)) {
            sendSanctionNotification(offenderId, action, penalty, days, note);
        }

        String message = String.format("Đã xử lý thành công %d vi phạm của hội thoại #%d.", resolvedCount, conversationId);
        return new BatchDecisionResponse(resolvedCount, offenderId, action, penalty, message);
    }

    @Transactional(readOnly = true)
    public List<PendingViolationDto> getPendingViolations(long conversationId) {
        return repository.findPendingViolations(conversationId);
    }

    private void sendSanctionNotification(long offenderId, String action, int penalty, int days, String note) {
        String actionText = switch (action) {
            case "RESTRICT_CHAT" -> "Tạm ngưng quyền gửi tin nhắn (" + days + " ngày)";
            case "LOCK_ACCOUNT" -> "Khóa tài khoản vĩnh viễn";
            case "WARN" -> "Cảnh cáo nhắc nhở";
            default -> "Xử lý chế tài";
        };

        String notifBody = "Kỹ thuật viên đã xử phạt tài khoản của bạn do vi phạm quy chuẩn tin nhắn (Biện pháp: "
            + actionText + ", Trừ " + penalty + " điểm uy tín). Lý do: " + note;

        try {
            notices.enqueue(
                "chat-moderation-sanction:" + System.currentTimeMillis() + ":" + offenderId,
                offenderId,
                List.of(new PlatformNotificationFacade.Notice(
                    offenderId,
                    "CHAT_SANCTION",
                    "Cảnh báo vi phạm tin nhắn",
                    notifBody,
                    PlatformNotificationFacade.Destination.ACCOUNT_SELF,
                    offenderId,
                    0
                ))
            );
        } catch (Exception ignored) {
            // Notification enqueue must not break the transaction
        }
    }

    @Transactional(readOnly = true)
    public int getPendingCount() {
        return repository.countPendingReports();
    }
}
