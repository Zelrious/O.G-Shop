package com.oldbutgold.shop.modules.platform.api;

import com.oldbutgold.shop.modules.platform.application.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service) { this.service=service; }
    @GetMapping public com.oldbutgold.shop.modules.platform.application.NotificationDtos.Page list(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.list(Long.parseLong(jwt.getSubject()),page,size);
    }
    @PostMapping("/{id}/read") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) { service.markRead(Long.parseLong(jwt.getSubject()),id); }
}
