package com.oldbutgold.shop.modules.identity.api;

import com.oldbutgold.shop.modules.identity.api.EkycDtos.*;
import com.oldbutgold.shop.modules.identity.application.KtvEkycService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ktv/ekyc")
@PreAuthorize("hasRole('KTV')")
public class KtvEkycController {
    private final KtvEkycService service;

    public KtvEkycController(KtvEkycService service) {
        this.service = service;
    }

    @GetMapping
    public KtvEkycPageResponse getQueue(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        return service.getQueue(page, size);
    }

    @GetMapping("/{profileId}")
    public KtvEkycDetailDto getDetail(@PathVariable("profileId") long profileId) {
        return service.getDetail(profileId);
    }

    @PostMapping("/{profileId}/approve")
    public KtvEkycDecisionResponse approve(
            @PathVariable("profileId") long profileId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) KtvEkycApproveRequest request
    ) {
        long ktvUserId = Long.parseLong(jwt.getSubject());
        String note = (request != null && request.note() != null && !request.note().isBlank())
                ? request.note()
                : "Đã đối chiếu thông tin hợp lệ";
        return service.approve(profileId, ktvUserId, note);
    }

    @PostMapping("/{profileId}/reject")
    public KtvEkycDecisionResponse reject(
            @PathVariable("profileId") long profileId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody KtvEkycRejectRequest request
    ) {
        long ktvUserId = Long.parseLong(jwt.getSubject());
        String reason = request != null ? request.reason() : "";
        return service.reject(profileId, ktvUserId, reason);
    }
}
