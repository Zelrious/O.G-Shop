package com.oldbutgold.shop.modules.communication.api;

import java.time.Instant;
import java.util.List;

public class ChatModerationDtos {

    public record ReportSummaryDto(
        long reportId,
        long conversationId,
        Long flaggedMessageId,
        long offenderId,
        String offenderName,
        String offenderEmail,
        Long reporterId,
        String violationType,
        String severityLevel,
        String status,
        String flaggedContent,
        Instant createdAt,
        int pendingViolationCount
    ) {
        // Overload with 12 params (default pendingViolationCount = 1)
        public ReportSummaryDto(long reportId, long conversationId, Long flaggedMessageId, long offenderId,
                                String offenderName, String offenderEmail, Long reporterId, String violationType,
                                String severityLevel, String status, String flaggedContent, Instant createdAt) {
            this(reportId, conversationId, flaggedMessageId, offenderId, offenderName, offenderEmail, reporterId,
                 violationType, severityLevel, status, flaggedContent, createdAt, 1);
        }

        // Overload without flaggedMessageId for backward compatibility
        public ReportSummaryDto(long reportId, long conversationId, long offenderId, String offenderName,
                                String offenderEmail, Long reporterId, String violationType,
                                String severityLevel, String status, String flaggedContent, Instant createdAt) {
            this(reportId, conversationId, null, offenderId, offenderName, offenderEmail, reporterId,
                 violationType, severityLevel, status, flaggedContent, createdAt, 1);
        }
    }

    public record ReportDetailDto(
        long reportId,
        long conversationId,
        Long flaggedMessageId,
        long offenderId,
        String offenderName,
        String offenderEmail,
        int offenderViolationScore,
        int offenderReputation,
        Instant offenderChatRestrictedUntil,
        Long reporterId,
        String violationType,
        String severityLevel,
        String status,
        String flaggedContent,
        String contextSnippet,
        String decisionAction,
        int reputationPenalty,
        String decisionNote,
        Long resolvedBy,
        String resolverName,
        Instant resolvedAt,
        Instant createdAt
    ) {
        // Overload without flaggedMessageId for backward compatibility
        public ReportDetailDto(long reportId, long conversationId, long offenderId, String offenderName,
                               String offenderEmail, int offenderViolationScore, int offenderReputation,
                               Instant offenderChatRestrictedUntil, Long reporterId, String violationType,
                               String severityLevel, String status, String flaggedContent, String contextSnippet,
                               String decisionAction, int reputationPenalty, String decisionNote,
                               Long resolvedBy, String resolverName, Instant resolvedAt, Instant createdAt) {
            this(reportId, conversationId, null, offenderId, offenderName, offenderEmail, offenderViolationScore,
                 offenderReputation, offenderChatRestrictedUntil, reporterId, violationType, severityLevel,
                 status, flaggedContent, contextSnippet, decisionAction, reputationPenalty, decisionNote,
                 resolvedBy, resolverName, resolvedAt, createdAt);
        }
    }

    public record ReportDecisionRequest(
        String action, // DISMISS, WARN, RESTRICT_CHAT, LOCK_ACCOUNT
        int reputationPenalty, // 0 to 20
        Integer restrictionDays, // 7, 14, 30, 90
        String decisionNote
    ) {}

    public record ReportPageResponse(
        List<ReportSummaryDto> items,
        int page,
        int size,
        long total,
        int pendingCount
    ) {}

    public record KtvMessageItemDto(
        long messageId,
        long conversationId,
        long senderId,
        String senderName,
        String senderAvatarUrl,
        String type,
        String content,
        String maskedContent,
        boolean isMaskedForUsers,
        boolean hasViolation,
        Long flaggedReportId,
        String violationType,
        String severityLevel,
        String moderationStatus,
        Instant createdAt
    ) {
        // Overload without hasViolation for backward compatibility
        public KtvMessageItemDto(long messageId, long conversationId, long senderId, String senderName,
                                 String senderAvatarUrl, String type, String content, String maskedContent,
                                 boolean isMaskedForUsers, Long flaggedReportId, String violationType,
                                 String severityLevel, String moderationStatus, Instant createdAt) {
            this(messageId, conversationId, senderId, senderName, senderAvatarUrl, type, content, maskedContent,
                 isMaskedForUsers, isMaskedForUsers || flaggedReportId != null, flaggedReportId,
                 violationType, severityLevel, moderationStatus, createdAt);
        }
    }

    public record ConversationChunkResponse(
        long conversationId,
        List<KtvMessageItemDto> messages,
        boolean hasOlder,
        boolean hasNewer,
        Long oldestMessageId,
        Long newestMessageId,
        Long pivotMessageId
    ) {}

    public record UserViolationHistoryItemDto(
        long reportId,
        long conversationId,
        Long flaggedMessageId,
        String violationType,
        String severityLevel,
        String status,
        String flaggedContent,
        String decisionAction,
        int reputationPenalty,
        String decisionNote,
        String resolverName,
        Instant resolvedAt,
        Instant createdAt
    ) {}

    public record UserViolationHistoryResponse(
        long userId,
        String fullName,
        String email,
        int reputationScore,
        int chatViolationScore,
        Instant chatRestrictedUntil,
        String accountStatus,
        List<UserViolationHistoryItemDto> reports
    ) {}

    public record ManualFlagRequest(
        String violationType,
        String action, // WARN, RESTRICT_CHAT, LOCK_ACCOUNT, DISMISS
        int reputationPenalty, // 0 to 20
        Integer restrictionDays, // 7, 14, 30
        String decisionNote
    ) {}

    public record BatchDecisionRequest(
        long conversationId,
        List<Long> messageIds,
        List<Long> reportIds,
        String action, // WARN, RESTRICT_CHAT, LOCK_ACCOUNT, DISMISS
        int reputationPenalty, // 0 to 20
        Integer restrictionDays, // 7, 14, 30, 90
        String decisionNote
    ) {}

    public record BatchDecisionResponse(
        int resolvedCount,
        long offenderId,
        String action,
        int reputationPenalty,
        String message
    ) {}

    public record PendingViolationDto(
        long messageId,
        Long reportId,
        String violationType,
        String severityLevel,
        String content,
        Instant createdAt
    ) {}
}
