package com.oldbutgold.shop.modules.communication.api;

import com.oldbutgold.shop.modules.communication.api.ChatModerationDtos.*;
import com.oldbutgold.shop.modules.communication.application.KtvChatModerationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ktv/chat-moderation")
@PreAuthorize("hasRole('KTV')")
public class KtvChatModerationController {

    private final KtvChatModerationService service;

    public KtvChatModerationController(KtvChatModerationService service) {
        this.service = service;
    }

    @GetMapping("/reports")
    public ReportPageResponse getQueue(
        @RequestParam(name = "q", required = false) String query,
        @RequestParam(name = "status", required = false, defaultValue = "PENDING") String status,
        @RequestParam(name = "severity", required = false) String severity,
        @RequestParam(name = "type", required = false) String type,
        @RequestParam(name = "hasPenalty", required = false) Boolean hasPenalty,
        @RequestParam(name = "minScore", required = false) Integer minScore,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "15") int size
    ) {
        return service.getQueue(query, status, severity, type, hasPenalty, minScore, page, size);
    }

    @GetMapping("/reports/{id}")
    public ReportDetailDto getDetail(@PathVariable("id") long id) {
        return service.getDetail(id);
    }

    @PostMapping("/reports/{id}/decision")
    public ReportDetailDto decide(
        @PathVariable("id") long id,
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody ReportDecisionRequest request
    ) {
        long ktvUserId = Long.parseLong(jwt.getSubject());
        return service.decide(id, ktvUserId, request);
    }

    @GetMapping("/conversations/{id}/messages")
    public ConversationChunkResponse getConversationMessages(
        @PathVariable("id") long id,
        @RequestParam(name = "around", required = false) Long aroundMessageId,
        @RequestParam(name = "before", required = false) Long before,
        @RequestParam(name = "after", required = false) Long after,
        @RequestParam(name = "size", defaultValue = "60") int size
    ) {
        return service.getConversationChunk(id, aroundMessageId, before, after, size);
    }

    @GetMapping("/users/{userId}/violation-history")
    public UserViolationHistoryResponse getUserViolationHistory(@PathVariable("userId") long userId) {
        return service.getUserViolationHistory(userId);
    }

    @PostMapping("/messages/{messageId}/manual-flag")
    public ReportDetailDto manualFlagMessage(
        @PathVariable("messageId") long messageId,
        @RequestParam(name = "conversationId", required = false, defaultValue = "0") long conversationId,
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody ManualFlagRequest request
    ) {
        long ktvUserId = Long.parseLong(jwt.getSubject());
        return service.manualFlagAndDecide(conversationId, messageId, ktvUserId, request);
    }

    @PostMapping("/conversations/{convId}/messages/{messageId}/manual-flag")
    public ReportDetailDto manualFlagMessageWithConv(
        @PathVariable("convId") long convId,
        @PathVariable("messageId") long messageId,
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody ManualFlagRequest request
    ) {
        long ktvUserId = Long.parseLong(jwt.getSubject());
        return service.manualFlagAndDecide(convId, messageId, ktvUserId, request);
    }

    @PostMapping("/batch-decision")
    public BatchDecisionResponse batchDecision(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody BatchDecisionRequest request
    ) {
        long ktvUserId = Long.parseLong(jwt.getSubject());
        return service.applyBatchDecision(request, ktvUserId);
    }

    @GetMapping("/conversations/{id}/pending-violations")
    public java.util.List<PendingViolationDto> getPendingViolations(@PathVariable("id") long id) {
        return service.getPendingViolations(id);
    }

    @GetMapping("/messages/{messageId}/media")
    public org.springframework.http.ResponseEntity<byte[]> getMessageMedia(@PathVariable("messageId") long messageId) {
        var file = service.readMessageMedia(messageId);
        return org.springframework.http.ResponseEntity.ok()
            .contentType(org.springframework.http.MediaType.parseMediaType(file.mime()))
            .contentLength(file.bytes().length)
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Disposition", "inline")
            .cacheControl(org.springframework.http.CacheControl.noStore())
            .body(file.bytes());
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return Map.of("pendingCount", service.getPendingCount());
    }
}
