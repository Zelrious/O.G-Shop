package com.oldbutgold.shop.modules.fulfillment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.GhnProperties;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnWebhookDtos.WebhookAckResponse;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnWebhookDtos.WebhookPayloadDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.GhnWebhookEventEntity;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.GhnWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class GhnWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GhnWebhookService.class);

    private final GhnProperties ghnProperties;
    private final GhnWebhookEventRepository eventRepository;
    private final GhnWebhookProcessor webhookProcessor;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate requiresNewTransactionTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GhnWebhookService(
            GhnProperties ghnProperties,
            GhnWebhookEventRepository eventRepository,
            GhnWebhookProcessor webhookProcessor,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.ghnProperties = ghnProperties;
        this.eventRepository = eventRepository;
        this.webhookProcessor = webhookProcessor;
        this.transactionTemplate = transactionTemplate;
        if (transactionTemplate.getTransactionManager() != null) {
            this.requiresNewTransactionTemplate = new TransactionTemplate(
                    transactionTemplate.getTransactionManager(),
                    new org.springframework.transaction.support.DefaultTransactionDefinition(
                            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
                    )
            );
        } else {
            this.requiresNewTransactionTemplate = transactionTemplate;
        }
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Tiếp nhận và lưu sự kiện webhook bền vững.
     * Trả về ACK nhanh để GHN không bị timeout (ngưỡng 5s).
     */
    public WebhookAckResponse ingestWebhook(String secretHeader, WebhookPayloadDto payload) {
        // 1. Xác thực Secret Header theo cơ chế an toàn constant-time
        verifySecretHeader(secretHeader);

        if (payload == null) {
            throw new IllegalArgumentException("Payload webhook không được để trống.");
        }

        Instant now = clock.instant();

        // 2. Lọc bỏ PII của bưu tá trước khi lưu chuỗi JSON
        String sanitizedPayloadJson = sanitizeAndSerialize(payload);

        // 3. Xây dựng Idempotency Key: (order_code, type, event_time)
        String idempotencyKey = buildIdempotencyKey(payload, sanitizedPayloadJson);

        // 4. Lưu sự kiện thô bền vững vào database
        // 4. Lưu sự kiện thô bền vững vào database trong physical transaction độc lập (REQUIRES_NEW)
        GhnWebhookEventEntity savedEvent;
        try {
            savedEvent = requiresNewTransactionTemplate.execute(status -> {
                if (eventRepository.existsByIdempotencyKey(idempotencyKey)) {
                    log.info("Sự kiện webhook trùng lặp đã tồn tại (idempotencyKey={}), trả về ACK", idempotencyKey);
                    return null;
                }

                GhnWebhookEventEntity entity = new GhnWebhookEventEntity(
                        payload.orderCode(),
                        payload.clientOrderCode(),
                        payload.type() != null ? payload.type() : "UNKNOWN",
                        payload.parseTime(),
                        idempotencyKey,
                        sanitizedPayloadJson,
                        now
                );
                return eventRepository.save(entity);
            });
        } catch (DataIntegrityViolationException dive) {
            log.info("Xung đột trùng lặp idempotencyKey={} khi ghi sự kiện webhook, trả về ACK", idempotencyKey);
            return WebhookAckResponse.duplicate();
        }

        if (savedEvent == null) {
            return WebhookAckResponse.duplicate();
        }

        // 5. Kích hoạt xử lý bất đồng bộ sau khi sự kiện đã được lưu bền vững
        webhookProcessor.processAsync(savedEvent.getId());

        log.info("Đã tiếp nhận và lưu bền vững webhook eventId={}, idempotencyKey={}, orderCode={}",
                savedEvent.getId(), idempotencyKey, payload.orderCode());

        return WebhookAckResponse.ok();
    }

    /**
     * Xác thực header mang bí mật từ GHN.
     * - Khi secret chưa được cấu hình: Fail-closed với 503 Service Unavailable để GHN retry sau.
     * - Khi header thiếu hoặc không khớp: 403 Forbidden.
     */
    public void verifySecretHeader(String candidateSecret) {
        String configuredSecret = ghnProperties.webhookSecret();
        if (configuredSecret == null || configuredSecret.isBlank()) {
            log.error("GHN Webhook Secret chưa được cấu hình trong hệ thống (app.ghn.webhook-secret). Từ chối tiếp nhận (503 retryable).");
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Dịch vụ Webhook chưa sẵn sàng (chưa cấu hình secret xác thực)."
            );
        }

        if (candidateSecret == null || candidateSecret.isBlank()) {
            throw new AccessDeniedException("Thiếu header bí mật xác thực Webhook.");
        }

        byte[] expectedBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = candidateSecret.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
            log.warn("Header bí mật xác thực Webhook không hợp lệ.");
            throw new AccessDeniedException("Xác thực Webhook Secret không hợp lệ.");
        }
    }

    public String buildIdempotencyKey(WebhookPayloadDto payload, String fallbackPayload) {
        String orderCode = payload.orderCode();
        String type = payload.type();
        String time = payload.time();

        // 1. Khi Time có mặt và hợp lệ: dùng (order_code, type, event_time)
        if (isNotBlank(orderCode) && isNotBlank(type) && isNotBlank(time)) {
            return orderCode.trim() + ":" + type.trim() + ":" + time.trim();
        }

        // 2. Khi Time vắng mặt / không hợp lệ: kết hợp fingerprint của sanitized payload (không chứa PII/secret)
        // để không gộp nhầm các sự kiện trạng thái khác nhau của cùng đơn hàng
        String fp = sha256Hex(fallbackPayload);
        String fpShort = fp.length() > 16 ? fp.substring(0, 16) : fp;

        if (isNotBlank(orderCode) && isNotBlank(type)) {
            return orderCode.trim() + ":" + type.trim() + ":FP:" + fpShort;
        }

        // 3. Fallback khi thiếu cả orderCode:
        return "HASH:" + fp;
    }

    private String sanitizeAndSerialize(WebhookPayloadDto payload) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("OrderCode", payload.orderCode());
            map.put("ClientOrderCode", payload.clientOrderCode());
            map.put("Type", payload.type());
            map.put("Status", payload.status());
            map.put("Time", payload.time());
            map.put("Description", payload.description());
            map.put("TotalFee", payload.totalFee());
            map.put("CODAmount", payload.codAmount());
            map.put("Weight", payload.weight());
            map.put("Length", payload.length());
            map.put("Width", payload.width());
            map.put("Height", payload.height());
            map.put("ConvertedWeight", payload.convertedWeight());
            map.put("Warehouse", payload.warehouse());
            map.put("Reason", payload.reason());
            map.put("ReasonCode", payload.reasonCode());
            // Lọc bỏ PII của bưu tá
            map.put("ShipperName", "[REDACTED]");
            map.put("ShipperPhone", "[REDACTED]");
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("Không thể serialize sanitized payload JSON, fallback string: {}", e.getMessage());
            return "{\"OrderCode\":\"" + payload.orderCode() + "\",\"Type\":\"" + payload.type() + "\"}";
        }
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private boolean isNotBlank(String str) {
        return str != null && !str.isBlank();
    }
}
