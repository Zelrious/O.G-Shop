package com.oldbutgold.shop.modules.fulfillment.api;

import com.oldbutgold.shop.modules.fulfillment.application.GhnWebhookService;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnWebhookDtos.WebhookAckResponse;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnWebhookDtos.WebhookPayloadDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fulfillment")
public class GhnWebhookController {

    private final GhnWebhookService webhookService;

    public GhnWebhookController(GhnWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /**
     * Endpoint tiếp nhận Webhook callback từ GHN.
     * Xác thực qua secret header (X-Webhook-Secret hoặc X-GHN-Webhook-Secret).
     */
    @PostMapping("/webhook")
    public ResponseEntity<WebhookAckResponse> handleWebhook(
            @RequestHeader(name = "X-Webhook-Secret", required = false) String secretHeader1,
            @RequestHeader(name = "X-GHN-Webhook-Secret", required = false) String secretHeader2,
            @RequestBody WebhookPayloadDto payload
    ) {
        String secret = (secretHeader1 != null && !secretHeader1.isBlank()) ? secretHeader1 : secretHeader2;
        WebhookAckResponse response = webhookService.ingestWebhook(secret, payload);
        return ResponseEntity.ok(response);
    }
}
