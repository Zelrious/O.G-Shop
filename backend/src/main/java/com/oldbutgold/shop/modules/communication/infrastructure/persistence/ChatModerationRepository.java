package com.oldbutgold.shop.modules.communication.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.communication.api.ChatModerationDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class ChatModerationRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public ChatModerationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        ensureReportsTableExists();
    }

    private void ensureReportsTableExists() {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS public.chat_moderation_reports (
                  report_id BIGSERIAL PRIMARY KEY,
                  conversation_id BIGINT NOT NULL,
                  offender_id BIGINT NOT NULL,
                  reporter_id BIGINT NULL,
                  flagged_message_id BIGINT NULL,
                  violation_type VARCHAR(64) NOT NULL,
                  severity_level VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
                  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                  flagged_content TEXT NOT NULL,
                  context_snippet TEXT NOT NULL,
                  decision_action VARCHAR(64) NULL,
                  reputation_penalty INT NOT NULL DEFAULT 0,
                  decision_note TEXT NULL,
                  resolved_by BIGINT NULL,
                  resolved_at TIMESTAMP WITH TIME ZONE NULL,
                  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        } catch (Exception ignored) {}
        try {
            jdbc.execute("ALTER TABLE public.chat_moderation_reports ADD COLUMN IF NOT EXISTS flagged_message_id BIGINT NULL");
        } catch (Exception ignored) {}
    }

    public List<ReportSummaryDto> searchReports(String status, String severity, String type, int page, int size) {
        return searchReports(null, status, severity, type, null, null, page, size);
    }

    public List<ReportSummaryDto> searchReports(String query, String status, String severity, String type,
                                                Boolean hasPenalty, Integer minViolationScore, int page, int size) {
        StringBuilder sql = new StringBuilder("""
            SELECT 
                MAX(r.report_id) AS report_id,
                r.conversation_id,
                MAX(r.flagged_message_id) AS flagged_message_id,
                r.offender_id,
                u.full_name AS offender_name,
                u.email AS offender_email,
                MAX(r.reporter_id) AS reporter_id,
                MAX(r.violation_type) AS violation_type,
                CASE 
                  WHEN MIN(CASE r.severity_level WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END) = 1 THEN 'CRITICAL'
                  WHEN MIN(CASE r.severity_level WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END) = 2 THEN 'HIGH'
                  WHEN MIN(CASE r.severity_level WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END) = 3 THEN 'MEDIUM'
                  ELSE 'LOW'
                END AS severity_level,
                CASE 
                  WHEN MIN(CASE r.status WHEN 'PENDING' THEN 1 WHEN 'RESOLVED' THEN 2 ELSE 3 END) = 1 THEN 'PENDING'
                  WHEN MIN(CASE r.status WHEN 'PENDING' THEN 1 WHEN 'RESOLVED' THEN 2 ELSE 3 END) = 2 THEN 'RESOLVED'
                  ELSE 'DISMISSED'
                END AS status,
                MAX(r.flagged_content) AS flagged_content,
                MAX(r.created_at) AS created_at,
                COUNT(CASE WHEN r.status = 'PENDING' THEN 1 ELSE NULL END) AS pending_violation_count
            FROM public.chat_moderation_reports r
            LEFT JOIN public.users u ON u.user_id = r.offender_id
            WHERE 1=1
            """);

        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, query, status, severity, type, hasPenalty, minViolationScore);

        sql.append("""
             GROUP BY r.conversation_id, r.offender_id, u.full_name, u.email
             ORDER BY CASE 
                        WHEN MIN(CASE r.severity_level WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END) = 1 THEN 1
                        WHEN MIN(CASE r.severity_level WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END) = 2 THEN 2
                        WHEN MIN(CASE r.severity_level WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END) = 3 THEN 3
                        ELSE 4
                      END ASC,
                      MAX(r.created_at) DESC
             LIMIT ? OFFSET ?
            """);
        params.add(size);
        params.add((long) page * size);

        return jdbc.query(sql.toString(), (rs, n) -> new ReportSummaryDto(
            rs.getLong("report_id"),
            rs.getLong("conversation_id"),
            rs.getObject("flagged_message_id", Long.class),
            rs.getLong("offender_id"),
            rs.getString("offender_name") != null ? rs.getString("offender_name") : "Người dùng #" + rs.getLong("offender_id"),
            rs.getString("offender_email") != null ? rs.getString("offender_email") : "N/A",
            rs.getObject("reporter_id", Long.class),
            rs.getString("violation_type"),
            rs.getString("severity_level"),
            rs.getString("status"),
            rs.getString("flagged_content"),
            instant(rs, "created_at"),
            rs.getInt("pending_violation_count")
        ), params.toArray());
    }

    public long countReports(String status, String severity, String type) {
        return countReports(null, status, severity, type, null, null);
    }

    public long countReports(String query, String status, String severity, String type,
                             Boolean hasPenalty, Integer minViolationScore) {
        StringBuilder sql = new StringBuilder("""
            SELECT count(DISTINCT r.conversation_id)
            FROM public.chat_moderation_reports r
            LEFT JOIN public.users u ON u.user_id = r.offender_id
            WHERE 1=1
            """);
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, query, status, severity, type, hasPenalty, minViolationScore);
        Long count = jdbc.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    private void appendFilters(StringBuilder sql, List<Object> params, String query, String status,
                               String severity, String type, Boolean hasPenalty, Integer minViolationScore) {
        if (query != null && !query.isBlank()) {
            String wildcard = "%" + query.trim() + "%";
            sql.append(" AND (u.full_name ILIKE ? OR u.email ILIKE ? OR r.flagged_content ILIKE ?)");
            params.add(wildcard);
            params.add(wildcard);
            params.add(wildcard);
        }
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            sql.append(" AND r.status = ?");
            params.add(status.toUpperCase(Locale.ROOT));
        }
        if (severity != null && !severity.isBlank() && !"ALL".equalsIgnoreCase(severity)) {
            sql.append(" AND r.severity_level = ?");
            params.add(severity.toUpperCase(Locale.ROOT));
        }
        if (type != null && !type.isBlank() && !"ALL".equalsIgnoreCase(type)) {
            sql.append(" AND r.violation_type = ?");
            params.add(type);
        }
        if (Boolean.TRUE.equals(hasPenalty)) {
            sql.append(" AND (r.reputation_penalty > 0 OR r.decision_action IN ('WARN', 'RESTRICT_CHAT', 'LOCK_ACCOUNT'))");
        } else if (Boolean.FALSE.equals(hasPenalty)) {
            sql.append(" AND (r.reputation_penalty = 0 AND (r.decision_action IS NULL OR r.decision_action = 'DISMISS'))");
        }
        if (minViolationScore != null && minViolationScore > 0) {
            sql.append(" AND COALESCE(u.chat_violation_score, 0) >= ?");
            params.add(minViolationScore);
        }
    }

    public int countPendingReports() {
        try {
            Integer count = jdbc.queryForObject("SELECT count(DISTINCT conversation_id) FROM public.chat_moderation_reports WHERE status = 'PENDING'", Integer.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public Optional<ReportDetailDto> findReportById(long reportId) {
        String sql = """
            SELECT r.report_id, r.conversation_id, r.flagged_message_id, r.offender_id,
                   u.full_name AS offender_name, u.email AS offender_email,
                   COALESCE(u.chat_violation_score, 0) AS violation_score,
                   u.chat_restricted_until,
                   r.reporter_id, r.violation_type, r.severity_level, r.status, r.flagged_content, r.context_snippet,
                   r.decision_action, r.reputation_penalty, r.decision_note, r.resolved_by,
                   res.full_name AS resolver_name, r.resolved_at, r.created_at
            FROM public.chat_moderation_reports r
            LEFT JOIN public.users u ON u.user_id = r.offender_id
            LEFT JOIN public.users res ON res.user_id = r.resolved_by
            WHERE r.report_id = ?
            """;

        return jdbc.query(sql, (rs, n) -> {
            long offenderId = rs.getLong("offender_id");
            long conversationId = rs.getLong("conversation_id");
            String flaggedContent = rs.getString("flagged_content");
            String rawSnippet = rs.getString("context_snippet");
            String unmaskedSnippet = unmaskContextSnippet(conversationId, offenderId, flaggedContent, rawSnippet);
            int reputation = getOffenderReputation(offenderId);
            return new ReportDetailDto(
                rs.getLong("report_id"),
                conversationId,
                rs.getObject("flagged_message_id", Long.class),
                offenderId,
                rs.getString("offender_name") != null ? rs.getString("offender_name") : "Người dùng #" + offenderId,
                rs.getString("offender_email") != null ? rs.getString("offender_email") : "N/A",
                rs.getInt("violation_score"),
                reputation,
                instant(rs, "chat_restricted_until"),
                rs.getObject("reporter_id", Long.class),
                rs.getString("violation_type"),
                rs.getString("severity_level"),
                rs.getString("status"),
                flaggedContent,
                unmaskedSnippet,
                rs.getString("decision_action"),
                rs.getInt("reputation_penalty"),
                rs.getString("decision_note"),
                rs.getObject("resolved_by", Long.class),
                rs.getString("resolver_name"),
                instant(rs, "resolved_at"),
                instant(rs, "created_at")
            );
        }, reportId).stream().findFirst();
    }

    private String unmaskContextSnippet(long conversationId, long offenderId, String flaggedContent, String contextSnippet) {
        if (contextSnippet == null || !contextSnippet.contains("***")) {
            return contextSnippet;
        }
        try {
            Map<String, String> unmaskMap = new HashMap<>();
            var logRows = jdbc.query(
                "SELECT original_content, masked_content FROM public.chat_moderation_logs WHERE conversation_id = ? ORDER BY log_id DESC LIMIT 20",
                (rs, i) -> Map.entry(rs.getString("masked_content"), rs.getString("original_content")),
                conversationId
            );
            for (var entry : logRows) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    unmaskMap.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }

            try {
                var metaRows = jdbc.query(
                    "SELECT content, media_metadata->>'rawContent' as raw_content FROM og_compat.messages WHERE conversation_id = ? AND media_metadata->>'rawContent' IS NOT NULL",
                    (rs, i) -> Map.entry(rs.getString("content"), rs.getString("raw_content")),
                    conversationId
                );
                for (var entry : metaRows) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        unmaskMap.putIfAbsent(entry.getKey(), entry.getValue());
                    }
                }
            } catch (Exception ignored) {}

            String result = contextSnippet;
            for (var entry : unmaskMap.entrySet()) {
                if (entry.getKey() != null && !entry.getKey().isBlank()) {
                    result = result.replace("\"" + entry.getKey() + "\"", "\"" + entry.getValue().replace("\"", "\\\"") + "\"");
                }
            }
            if (result.contains("\"***\"") && flaggedContent != null && !flaggedContent.isBlank()) {
                result = result.replace("\"***\"", "\"" + flaggedContent.replace("\"", "\\\"") + "\"");
            }
            return result;
        } catch (Exception e) {
            return contextSnippet;
        }
    }

    public int getOffenderReputation(long userId) {
        try {
            Integer penalties = jdbc.queryForObject(
                "SELECT COALESCE(sum(points_delta), 0) FROM og_compat.penalty_ledger WHERE user_id = ?",
                Integer.class, userId
            );
            return Math.max(0, 100 - (penalties != null ? penalties : 0));
        } catch (Exception e) {
            try {
                Integer penalties = jdbc.queryForObject(
                    "SELECT COALESCE(sum(points_delta), 0) FROM public.penalty_ledger WHERE user_id = ?",
                    Integer.class, userId
                );
                return Math.max(0, 100 - (penalties != null ? penalties : 0));
            } catch (Exception ex) {
                return 100;
            }
        }
    }

    public void applyDecision(long reportId, String action, int reputationPenalty, String decisionNote, long ktvUserId, Instant now) {
        String newStatus = "DISMISS".equalsIgnoreCase(action) ? "DISMISSED" : "RESOLVED";
        jdbc.update("""
            UPDATE public.chat_moderation_reports
            SET status = ?, decision_action = ?, reputation_penalty = ?, decision_note = ?, resolved_by = ?, resolved_at = ?
            WHERE report_id = ?
            """, newStatus, action, reputationPenalty, decisionNote, ktvUserId, Timestamp.from(now), reportId);
    }

    public void applyPenalty(long offenderId, int pointsDelta, String reason, long ktvUserId) {
        if (pointsDelta <= 0) return;
        UUID entryId = UUID.randomUUID();
        try {
            jdbc.update("""
                INSERT INTO og_compat.penalty_ledger(entry_id, user_id, points_delta, reason, decided_by, created_at)
                VALUES(?, ?, ?, ?, ?, clock_timestamp())
                """, entryId, offenderId, pointsDelta, reason, ktvUserId);
        } catch (Exception e) {
            try {
                jdbc.update("""
                    INSERT INTO public.penalty_ledger(entry_id, user_id, points_delta, reason, decided_by, created_at)
                    VALUES(?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """, entryId, offenderId, pointsDelta, reason, ktvUserId);
            } catch (Exception ignored) {
            }
        }
    }

    public void restrictChat(long offenderId, Instant until) {
        jdbc.update("UPDATE public.users SET chat_restricted_until = ? WHERE user_id = ?",
            until == null ? null : Timestamp.from(until), offenderId);
    }

    public void lockAccount(long offenderId, Instant until) {
        jdbc.update("UPDATE public.users SET status = 'LOCKED', chat_restricted_until = ? WHERE user_id = ?",
            until == null ? null : Timestamp.from(until), offenderId);
    }

    // =========================================================================
    // Dynamic Conversation Chunk Stream for KTV (aroundMessageId, before, after)
    // =========================================================================
    public ConversationChunkResponse getConversationChunkForKtv(long conversationId, Long aroundMessageId,
                                                                Long before, Long after, int size) {
        int limit = Math.max(10, Math.min(100, size <= 0 ? 60 : size));

        // 1. Build log unmask lookup map
        Map<String, String> logUnmasks = new HashMap<>();
        try {
            var logRows = jdbc.query(
                "SELECT original_content, masked_content FROM public.chat_moderation_logs WHERE conversation_id = ? ORDER BY log_id DESC LIMIT 50",
                (rs, i) -> Map.entry(rs.getString("masked_content"), rs.getString("original_content")),
                conversationId
            );
            for (var entry : logRows) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    logUnmasks.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception ignored) {}

        List<KtvMessageItemDto> messages = new ArrayList<>();

        if (aroundMessageId != null && aroundMessageId > 0) {
            int half = limit / 2;
            // Fetch before pivot (DESC, then reverse)
            List<KtvMessageItemDto> beforePivot = queryMessageBatch(
                "SELECT m.*, u.full_name, u.avatar_url, r.report_id, r.violation_type, r.severity_level, r.status AS report_status " +
                "FROM og_compat.messages m " +
                "LEFT JOIN public.users u ON u.user_id = m.sender_id " +
                "LEFT JOIN (SELECT flagged_message_id, MAX(report_id) AS report_id, MAX(violation_type) AS violation_type, MAX(severity_level) AS severity_level, " +
                "  CASE WHEN MIN(CASE status WHEN 'PENDING' THEN 1 WHEN 'RESOLVED' THEN 2 ELSE 3 END) = 1 THEN 'PENDING' " +
                "       WHEN MIN(CASE status WHEN 'PENDING' THEN 1 WHEN 'RESOLVED' THEN 2 ELSE 3 END) = 2 THEN 'RESOLVED' " +
                "       ELSE 'DISMISSED' END AS status " +
                "  FROM public.chat_moderation_reports WHERE flagged_message_id IS NOT NULL GROUP BY flagged_message_id) r ON r.flagged_message_id = m.message_id " +
                "WHERE m.conversation_id = ? AND m.deleted_at IS NULL AND m.message_id < ? " +
                "ORDER BY m.message_id DESC LIMIT ?",
                logUnmasks, conversationId, aroundMessageId, half
            );
            Collections.reverse(beforePivot);

            // Fetch pivot itself
            List<KtvMessageItemDto> pivot = queryMessageBatch(
                "SELECT m.*, u.full_name, u.avatar_url, r.report_id, r.violation_type, r.severity_level, r.status AS report_status " +
                "FROM og_compat.messages m " +
                "LEFT JOIN public.users u ON u.user_id = m.sender_id " +
                "LEFT JOIN (SELECT flagged_message_id, MAX(report_id) AS report_id, MAX(violation_type) AS violation_type, MAX(severity_level) AS severity_level, " +
                "  CASE WHEN MIN(CASE status WHEN 'PENDING' THEN 1 WHEN 'RESOLVED' THEN 2 ELSE 3 END) = 1 THEN 'PENDING' " +
                "       WHEN MIN(CASE status WHEN 'PENDING' THEN 1 WHEN 'RESOLVED' THEN 2 ELSE 3 END) = 2 THEN 'RESOLVED' " +
                "       ELSE 'DISMISSED' END AS status " +
                "  FROM public.chat_moderation_reports WHERE flagged_message_id IS NOT NULL GROUP BY flagged_message_id) r ON r.flagged_message_id = m.message_id " +
                "WHERE m.conversation_id = ? AND m.deleted_at IS NULL AND m.message_id = ?",
                logUnmasks, conversationId, aroundMessageId
            );

            // Fetch after pivot (ASC)
            List<KtvMessageItemDto> afterPivot = queryMessageBatch(
                "SELECT m.*, u.full_name, u.avatar_url, r.report_id, r.violation_type, r.severity_level, r.status AS report_status " +
                "FROM og_compat.messages m " +
                "LEFT JOIN public.users u ON u.user_id = m.sender_id " +
                "LEFT JOIN (SELECT flagged_message_id, MAX(report_id) AS report_id, MAX(violation_type) AS violation_type, MAX(severity_level) AS severity_level, " +
                "  CASE WHEN MIN(CASE status WHEN 'PENDING' THEN 1 WHEN 'RESOLVED' THEN 2 ELSE 3 END) = 1 THEN 'PENDING' " +
                "       WHEN MIN(CASE status WHEN 'PENDING' THEN 1 WHEN 'RESOLVED' THEN 2 ELSE 3 END) = 2 THEN 'RESOLVED' " +
                "       ELSE 'DISMISSED' END AS status " +
                "  FROM public.chat_moderation_reports WHERE flagged_message_id IS NOT NULL GROUP BY flagged_message_id) r ON r.flagged_message_id = m.message_id " +
                "WHERE m.conversation_id = ? AND m.deleted_at IS NULL AND m.message_id > ? " +
                "ORDER BY m.message_id ASC LIMIT ?",
                logUnmasks, conversationId, aroundMessageId, half
            );

            messages.addAll(beforePivot);
            messages.addAll(pivot);
            messages.addAll(afterPivot);
        } else if (before != null && before > 0) {
            List<KtvMessageItemDto> olderBatch = queryMessageBatch(
                "SELECT m.*, u.full_name, u.avatar_url, r.report_id, r.violation_type, r.severity_level, r.status AS report_status " +
                "FROM og_compat.messages m " +
                "LEFT JOIN public.users u ON u.user_id = m.sender_id " +
                "LEFT JOIN (SELECT flagged_message_id, MAX(report_id) AS report_id, MAX(violation_type) AS violation_type, MAX(severity_level) AS severity_level, " +
                "  CASE WHEN MIN(CASE status WHEN 'PENDING' THEN 1 WHEN 'RESOLVED' THEN 2 ELSE 3 END) = 1 THEN 'PENDING' " +
                "       WHEN MIN(CASE status WHEN 'PENDING' THEN 1 WHEN 'RESOLVED' THEN 2 ELSE 3 END) = 2 THEN 'RESOLVED' " +
                "       ELSE 'DISMISSED' END AS status " +
                "  FROM public.chat_moderation_reports WHERE flagged_message_id IS NOT NULL GROUP BY flagged_message_id) r ON r.flagged_message_id = m.message_id " +
                "WHERE m.conversation_id = ? AND m.deleted_at IS NULL AND m.message_id < ? " +
                "ORDER BY m.message_id DESC LIMIT ?",
                logUnmasks, conversationId, before, limit
            );
            Collections.reverse(olderBatch);
            messages.addAll(olderBatch);
        } else if (after != null && after > 0) {
            List<KtvMessageItemDto> newerBatch = queryMessageBatch(
                "SELECT m.*, u.full_name, u.avatar_url, r.report_id, r.violation_type, r.severity_level, r.status AS report_status " +
                "FROM og_compat.messages m " +
                "LEFT JOIN public.users u ON u.user_id = m.sender_id " +
                "LEFT JOIN (SELECT flagged_message_id, MAX(report_id) AS report_id, MAX(violation_type) AS violation_type, MAX(severity_level) AS severity_level, " +
                "  CASE WHEN MIN(CASE status WHEN 'PENDING' THEN 1 WHEN 'RESOLVED' THEN 2 ELSE 3 END) = 1 THEN 'PENDING' " +
                "       WHEN MIN(CASE status WHEN 'PENDING' THEN 1 WHEN 'RESOLVED' THEN 2 ELSE 3 END) = 2 THEN 'RESOLVED' " +
                "       ELSE 'DISMISSED' END AS status " +
                "  FROM public.chat_moderation_reports WHERE flagged_message_id IS NOT NULL GROUP BY flagged_message_id) r ON r.flagged_message_id = m.message_id " +
                "WHERE m.conversation_id = ? AND m.deleted_at IS NULL AND m.message_id > ? " +
                "ORDER BY m.message_id ASC LIMIT ?",
                logUnmasks, conversationId, after, limit
            );
            messages.addAll(newerBatch);
        } else {
            // Default: latest chunk (DESC, then reverse)
            List<KtvMessageItemDto> latest = queryMessageBatch(
                "SELECT m.*, u.full_name, u.avatar_url, r.report_id, r.violation_type, r.severity_level, r.status AS report_status " +
                "FROM og_compat.messages m " +
                "LEFT JOIN public.users u ON u.user_id = m.sender_id " +
                "LEFT JOIN (SELECT flagged_message_id, MAX(report_id) AS report_id, MAX(violation_type) AS violation_type, MAX(severity_level) AS severity_level, " +
                "  CASE WHEN MIN(CASE status WHEN 'PENDING' THEN 1 WHEN 'RESOLVED' THEN 2 ELSE 3 END) = 1 THEN 'PENDING' " +
                "       WHEN MIN(CASE status WHEN 'PENDING' THEN 1 WHEN 'RESOLVED' THEN 2 ELSE 3 END) = 2 THEN 'RESOLVED' " +
                "       ELSE 'DISMISSED' END AS status " +
                "  FROM public.chat_moderation_reports WHERE flagged_message_id IS NOT NULL GROUP BY flagged_message_id) r ON r.flagged_message_id = m.message_id " +
                "WHERE m.conversation_id = ? AND m.deleted_at IS NULL " +
                "ORDER BY m.message_id DESC LIMIT ?",
                logUnmasks, conversationId, limit
            );
            Collections.reverse(latest);
            messages.addAll(latest);
        }

        if (messages.isEmpty()) {
            return new ConversationChunkResponse(conversationId, List.of(), false, false, null, null, aroundMessageId);
        }

        long minId = messages.getFirst().messageId();
        long maxId = messages.getLast().messageId();

        boolean hasOlder = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM og_compat.messages WHERE conversation_id = ? AND deleted_at IS NULL AND message_id < ?)",
            Boolean.class, conversationId, minId
        ));

        boolean hasNewer = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM og_compat.messages WHERE conversation_id = ? AND deleted_at IS NULL AND message_id > ?)",
            Boolean.class, conversationId, maxId
        ));

        return new ConversationChunkResponse(conversationId, messages, hasOlder, hasNewer, minId, maxId, aroundMessageId);
    }

    private List<KtvMessageItemDto> queryMessageBatch(String sql, Map<String, String> logUnmasks, Object... params) {
        return jdbc.query(sql, (rs, n) -> {
            long msgId = rs.getLong("message_id");
            long convId = rs.getLong("conversation_id");
            long senderId = rs.getLong("sender_id");
            String senderName = rs.getString("full_name") != null ? rs.getString("full_name") : "Người dùng #" + senderId;
            String senderAvatar = rs.getString("avatar_url");
            String type = rs.getString("message_type");
            String storedContent = rs.getString("content");
            String mediaMetadataStr = rs.getString("media_metadata");

            String rawContent = null;
            String metaViolationType = null;
            String metaSeverityLevel = null;
            String metaModerationStatus = null;
            if (mediaMetadataStr != null && !mediaMetadataStr.isBlank()) {
                try {
                    Map<String, Object> meta = json.readValue(mediaMetadataStr, new TypeReference<>() {});
                    if (meta.containsKey("rawContent")) {
                        rawContent = Objects.toString(meta.get("rawContent"), null);
                    }
                    if (meta.containsKey("violationType")) {
                        metaViolationType = Objects.toString(meta.get("violationType"), null);
                    }
                    if (meta.containsKey("severityLevel")) {
                        metaSeverityLevel = Objects.toString(meta.get("severityLevel"), null);
                    }
                    if (meta.containsKey("moderationStatus")) {
                        metaModerationStatus = Objects.toString(meta.get("moderationStatus"), null);
                    }
                } catch (Exception ignored) {}
            }

            boolean isMasked = false;
            String content = storedContent != null ? storedContent : "";
            String maskedContent = null;

            if (rawContent != null && !rawContent.isBlank()) {
                content = rawContent;
                isMasked = true;
                maskedContent = storedContent;
            } else if (logUnmasks.containsKey(storedContent)) {
                content = logUnmasks.get(storedContent);
                isMasked = true;
                maskedContent = storedContent;
            } else if (storedContent != null && (storedContent.contains("***") || storedContent.contains("[SĐT") || storedContent.contains("[Liên kết"))) {
                isMasked = true;
                maskedContent = storedContent;
                if (!logUnmasks.isEmpty()) {
                    content = logUnmasks.values().iterator().next();
                }
            }

            Long reportId = rs.getObject("report_id", Long.class);
            String reportViolationType = rs.getString("violation_type");
            String reportSeverityLevel = rs.getString("severity_level");
            String reportStatus = rs.getString("report_status");

            boolean hasViolation = reportId != null
                || "MASKED".equalsIgnoreCase(metaModerationStatus)
                || isMasked
                || (mediaMetadataStr != null && mediaMetadataStr.contains("violationType"));

            boolean isMaskedForUsers = isMasked || hasViolation;

            String violationType = reportViolationType;
            if (violationType == null || violationType.isBlank()) {
                violationType = metaViolationType;
            }
            if ((violationType == null || violationType.isBlank()) && hasViolation) {
                violationType = "CONTACT_LEAK";
            }

            String severityLevel = reportSeverityLevel;
            if (severityLevel == null || severityLevel.isBlank()) {
                severityLevel = metaSeverityLevel;
            }
            if ((severityLevel == null || severityLevel.isBlank()) && hasViolation) {
                severityLevel = "MEDIUM";
            }

            String moderationStatus = reportStatus;
            if (moderationStatus == null && metaModerationStatus != null) {
                moderationStatus = metaModerationStatus;
            } else if (moderationStatus == null && hasViolation) {
                moderationStatus = "PENDING";
            }

            return new KtvMessageItemDto(
                msgId, convId, senderId, senderName, senderAvatar, type,
                content, maskedContent, isMaskedForUsers, hasViolation, reportId,
                violationType, severityLevel, moderationStatus,
                instant(rs, "created_at")
            );
        }, params);
    }

    // =========================================================================
    // User Violation History (UC56 / UC74)
    // =========================================================================
    public Optional<UserViolationHistoryResponse> getUserViolationHistory(long userId) {
        String userSql = """
            SELECT user_id, full_name, email, COALESCE(chat_violation_score, 0) AS violation_score,
                   chat_restricted_until, status
            FROM public.users
            WHERE user_id = ?
            """;

        return jdbc.query(userSql, (rs, n) -> {
            String fullName = rs.getString("full_name") != null ? rs.getString("full_name") : "Người dùng #" + userId;
            String email = rs.getString("email") != null ? rs.getString("email") : "N/A";
            int violationScore = rs.getInt("violation_score");
            Instant restrictedUntil = instant(rs, "chat_restricted_until");
            String accountStatus = rs.getString("status") != null ? rs.getString("status") : "ACTIVE";
            int reputation = getOffenderReputation(userId);

            String reportsSql = """
                SELECT r.report_id, r.conversation_id, r.flagged_message_id, r.violation_type, r.severity_level,
                       r.status, r.flagged_content, r.decision_action, r.reputation_penalty, r.decision_note,
                       res.full_name AS resolver_name, r.resolved_at, r.created_at
                FROM public.chat_moderation_reports r
                LEFT JOIN public.users res ON res.user_id = r.resolved_by
                WHERE r.offender_id = ?
                ORDER BY r.created_at DESC
                """;

            List<UserViolationHistoryItemDto> reports = jdbc.query(reportsSql, (rrs, m) -> new UserViolationHistoryItemDto(
                rrs.getLong("report_id"),
                rrs.getLong("conversation_id"),
                rrs.getObject("flagged_message_id", Long.class),
                rrs.getString("violation_type"),
                rrs.getString("severity_level"),
                rrs.getString("status"),
                rrs.getString("flagged_content"),
                rrs.getString("decision_action"),
                rrs.getInt("reputation_penalty"),
                rrs.getString("decision_note"),
                rrs.getString("resolver_name"),
                instant(rrs, "resolved_at"),
                instant(rrs, "created_at")
            ), userId);

            return new UserViolationHistoryResponse(
                userId, fullName, email, reputation, violationScore, restrictedUntil, accountStatus, reports
            );
        }, userId).stream().findFirst();
    }

    // =========================================================================
    // Manual Flagging & Sanction Execution
    // =========================================================================
    public Optional<Map<String, Object>> findMessageInfo(long messageId) {
        String sql = "SELECT message_id, conversation_id, sender_id, content, media_metadata, created_at FROM og_compat.messages WHERE message_id = ?";
        return jdbc.query(sql, (rs, n) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("messageId", rs.getLong("message_id"));
            map.put("conversationId", rs.getLong("conversation_id"));
            map.put("senderId", rs.getLong("sender_id"));
            map.put("content", rs.getString("content"));
            map.put("mediaMetadata", rs.getString("media_metadata"));
            map.put("createdAt", instant(rs, "created_at"));
            return map;
        }, messageId).stream().findFirst();
    }

    public long createManualReport(long conversationId, long messageId, long offenderId, long ktvId,
                                   String violationType, String action, int penalty, String note,
                                   String rawText, String contextJson) {
        ensureReportsTableExists();
        return jdbc.queryForObject("""
            INSERT INTO public.chat_moderation_reports(conversation_id, offender_id, reporter_id,
                                                       flagged_message_id, violation_type, severity_level,
                                                       status, flagged_content, context_snippet,
                                                       decision_action, reputation_penalty, decision_note,
                                                       resolved_by, resolved_at, created_at)
            VALUES(?, ?, ?, ?, ?, 'HIGH', 'RESOLVED', ?, ?, ?, ?, ?, ?, clock_timestamp(), clock_timestamp())
            RETURNING report_id
            """, Long.class, conversationId, offenderId, ktvId, messageId, violationType, rawText,
            contextJson, action, penalty, note, ktvId);
    }

    // =========================================================================
    // Batch Moderation Decision & Pending Violations (UC56 / UC74)
    // =========================================================================
    public Long findOffenderIdForConversation(long conversationId, List<Long> messageIds, List<Long> reportIds) {
        if (reportIds != null && !reportIds.isEmpty()) {
            try {
                String inSql = String.join(",", Collections.nCopies(reportIds.size(), "?"));
                Long offender = jdbc.queryForObject(
                    "SELECT offender_id FROM public.chat_moderation_reports WHERE report_id IN (" + inSql + ") LIMIT 1",
                    Long.class, reportIds.toArray()
                );
                if (offender != null && offender > 0) return offender;
            } catch (Exception ignored) {}
        }
        if (messageIds != null && !messageIds.isEmpty()) {
            try {
                String inSql = String.join(",", Collections.nCopies(messageIds.size(), "?"));
                Long sender = jdbc.queryForObject(
                    "SELECT sender_id FROM og_compat.messages WHERE message_id IN (" + inSql + ") LIMIT 1",
                    Long.class, messageIds.toArray()
                );
                if (sender != null && sender > 0) return sender;
            } catch (Exception ignored) {}
        }
        try {
            Long offender = jdbc.queryForObject(
                "SELECT offender_id FROM public.chat_moderation_reports WHERE conversation_id = ? ORDER BY report_id DESC LIMIT 1",
                Long.class, conversationId
            );
            if (offender != null && offender > 0) return offender;
        } catch (Exception ignored) {}

        try {
            Long buyerId = jdbc.queryForObject(
                "SELECT buyer_id FROM og_compat.conversations WHERE conversation_id = ?",
                Long.class, conversationId
            );
            if (buyerId != null && buyerId > 0) return buyerId;
        } catch (Exception ignored) {}

        return 0L;
    }

    public int resolveBatchReports(long conversationId, long offenderId, List<Long> messageIds, List<Long> reportIds,
                                   String action, int penalty, String note, long ktvUserId, Instant now) {
        ensureReportsTableExists();
        String newStatus = "DISMISS".equalsIgnoreCase(action) ? "DISMISSED" : "RESOLVED";
        Set<Long> processedMessageIds = new HashSet<>();
        int count = 0;

        // 1. Update existing reports explicitly passed in reportIds
        if (reportIds != null && !reportIds.isEmpty()) {
            for (int i = 0; i < reportIds.size(); i++) {
                long rId = reportIds.get(i);
                int repPenalty = (i == 0) ? penalty : 0;
                int updated = jdbc.update("""
                    UPDATE public.chat_moderation_reports
                    SET status = ?, decision_action = ?, reputation_penalty = ?, decision_note = ?, resolved_by = ?, resolved_at = ?
                    WHERE report_id = ?
                    """, newStatus, action, repPenalty, note, ktvUserId, Timestamp.from(now), rId);
                if (updated > 0) count++;
            }
            String inSql = String.join(",", Collections.nCopies(reportIds.size(), "?"));
            jdbc.query(
                "SELECT flagged_message_id FROM public.chat_moderation_reports WHERE report_id IN (" + inSql + ") AND flagged_message_id IS NOT NULL",
                (rs, n) -> processedMessageIds.add(rs.getLong("flagged_message_id")),
                reportIds.toArray()
            );
        }

        // 2. Also resolve any existing pending reports in this conversation matching messageIds
        if (messageIds != null && !messageIds.isEmpty()) {
            for (Long mId : messageIds) {
                if (mId == null || mId <= 0 || processedMessageIds.contains(mId)) continue;
                List<Long> matchedReportIds = jdbc.query(
                    "SELECT report_id FROM public.chat_moderation_reports WHERE conversation_id = ? AND flagged_message_id = ?",
                    (rs, n) -> rs.getLong("report_id"),
                    conversationId, mId
                );
                if (!matchedReportIds.isEmpty()) {
                    for (Long rId : matchedReportIds) {
                        int repPenalty = (count == 0) ? penalty : 0;
                        jdbc.update("""
                            UPDATE public.chat_moderation_reports
                            SET status = ?, decision_action = ?, reputation_penalty = ?, decision_note = ?, resolved_by = ?, resolved_at = ?
                            WHERE report_id = ?
                            """, newStatus, action, repPenalty, note, ktvUserId, Timestamp.from(now), rId);
                        count++;
                    }
                    processedMessageIds.add(mId);
                }
            }

            // 3. For any messageId that had NO report record yet, create a resolved report for audit trail
            for (Long mId : messageIds) {
                if (mId == null || mId <= 0 || processedMessageIds.contains(mId)) continue;
                var msgOpt = findMessageInfo(mId);
                if (msgOpt.isPresent()) {
                    Map<String, Object> msg = msgOpt.get();
                    long senderId = ((Number) msg.get("senderId")).longValue();
                    String raw = Objects.toString(msg.get("content"), "");
                    String metaStr = (String) msg.get("mediaMetadata");
                    String vType = "CONTACT_LEAK";
                    if (metaStr != null && !metaStr.isBlank()) {
                        try {
                            Map<String, Object> meta = json.readValue(metaStr, new TypeReference<>() {});
                            if (meta.containsKey("rawContent")) raw = Objects.toString(meta.get("rawContent"), raw);
                            if (meta.containsKey("violationType")) vType = Objects.toString(meta.get("violationType"), vType);
                        } catch (Exception ignored) {}
                    }
                    int repPenalty = (count == 0) ? penalty : 0;
                    jdbc.update("""
                        INSERT INTO public.chat_moderation_reports(
                            conversation_id, offender_id, reporter_id, flagged_message_id, violation_type,
                            severity_level, status, flagged_content, context_snippet, decision_action,
                            reputation_penalty, decision_note, resolved_by, resolved_at, created_at
                        )
                        VALUES(?, ?, NULL, ?, ?, 'MEDIUM', ?, ?, '[]', ?, ?, ?, ?, ?, clock_timestamp())
                        """, conversationId, senderId > 0 ? senderId : offenderId, mId, vType, newStatus,
                        raw, action, repPenalty, note, ktvUserId, Timestamp.from(now));
                    count++;
                    processedMessageIds.add(mId);
                }
            }
        }

        // 4. Fallback if no specific IDs passed
        if ((reportIds == null || reportIds.isEmpty()) && (messageIds == null || messageIds.isEmpty())) {
            List<Long> allPending = jdbc.query(
                "SELECT report_id FROM public.chat_moderation_reports WHERE conversation_id = ? AND status = 'PENDING'",
                (rs, n) -> rs.getLong("report_id"),
                conversationId
            );
            for (int i = 0; i < allPending.size(); i++) {
                int repPenalty = (i == 0) ? penalty : 0;
                jdbc.update("""
                    UPDATE public.chat_moderation_reports
                    SET status = ?, decision_action = ?, reputation_penalty = ?, decision_note = ?, resolved_by = ?, resolved_at = ?
                    WHERE report_id = ?
                    """, newStatus, action, repPenalty, note, ktvUserId, Timestamp.from(now), allPending.get(i));
                count++;
            }
        }

        return count;
    }

    public List<PendingViolationDto> findPendingViolations(long conversationId) {
        String sql = """
            SELECT 
                COALESCE(r.flagged_message_id, 0) AS message_id,
                r.report_id,
                r.violation_type,
                r.severity_level,
                r.flagged_content,
                r.created_at
            FROM public.chat_moderation_reports r
            WHERE r.conversation_id = ? AND r.status = 'PENDING'
            ORDER BY r.created_at DESC
            """;

        List<PendingViolationDto> reports = new ArrayList<>(jdbc.query(sql, (rs, n) -> new PendingViolationDto(
            rs.getLong("message_id"),
            rs.getObject("report_id", Long.class),
            rs.getString("violation_type"),
            rs.getString("severity_level"),
            rs.getString("flagged_content"),
            instant(rs, "created_at")
        ), conversationId));

        Set<Long> recordedMessageIds = new HashSet<>();
        for (var p : reports) {
            if (p.messageId() > 0) recordedMessageIds.add(p.messageId());
        }

        try {
            String msgSql = """
                SELECT message_id, content, media_metadata, created_at
                FROM og_compat.messages
                WHERE conversation_id = ? AND deleted_at IS NULL AND media_metadata->>'moderationStatus' = 'MASKED'
                ORDER BY message_id DESC
                """;
            List<PendingViolationDto> extraMsgs = jdbc.query(msgSql, (rs, n) -> {
                long mId = rs.getLong("message_id");
                if (recordedMessageIds.contains(mId)) return null;
                String metaStr = rs.getString("media_metadata");
                String raw = rs.getString("content");
                String vType = "CONTACT_LEAK";
                String sev = "MEDIUM";
                if (metaStr != null && !metaStr.isBlank()) {
                    try {
                        Map<String, Object> meta = json.readValue(metaStr, new TypeReference<>() {});
                        if (meta.containsKey("rawContent")) raw = Objects.toString(meta.get("rawContent"), raw);
                        if (meta.containsKey("violationType")) vType = Objects.toString(meta.get("violationType"), vType);
                        if (meta.containsKey("severityLevel")) sev = Objects.toString(meta.get("severityLevel"), sev);
                    } catch (Exception ignored) {}
                }
                return new PendingViolationDto(mId, null, vType, sev, raw, instant(rs, "created_at"));
            }, conversationId);

            for (var m : extraMsgs) {
                if (m != null) reports.add(m);
            }
        } catch (Exception ignored) {}

        return reports;
    }

    private Instant instant(ResultSet r, String column) throws SQLException {
        Timestamp t = r.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }
}
