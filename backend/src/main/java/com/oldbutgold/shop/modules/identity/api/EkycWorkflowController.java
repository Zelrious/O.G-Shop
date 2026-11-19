package com.oldbutgold.shop.modules.identity.api;

import com.oldbutgold.shop.modules.identity.api.EkycDtos.*;
import com.oldbutgold.shop.modules.identity.application.EkycWorkflowService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ekyc")
public class EkycWorkflowController {
    private final EkycWorkflowService service;

    public EkycWorkflowController(EkycWorkflowService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public EkycStatusResponse getStatus(@AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        return service.getStatus(userId);
    }

    @PostMapping("/start")
    public EkycStartResponse startOrResume(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) EkycStartRequest request
    ) {
        long userId = Long.parseLong(jwt.getSubject());
        UUID quickAuthResultId = request != null ? request.quickAuthResultId() : null;
        return service.startOrResume(userId, quickAuthResultId);
    }

    @PostMapping(value = "/upload-cccd", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EkycOcrResponse uploadCccd(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("frontFile") MultipartFile frontFile,
            @RequestPart("backFile") MultipartFile backFile,
            @RequestPart("portraitFile") MultipartFile portraitFile
    ) throws IOException {
        long userId = Long.parseLong(jwt.getSubject());
        if (frontFile == null || frontFile.isEmpty()) {
            throw new IllegalArgumentException("Ảnh CCCD mặt trước không hợp lệ. Vui lòng tải lên ảnh mặt trước.");
        }
        if (backFile == null || backFile.isEmpty()) {
            throw new IllegalArgumentException("Ảnh CCCD mặt sau không hợp lệ. Vui lòng tải lên ảnh mặt sau của thẻ CCCD gắn chip.");
        }
        if (portraitFile == null || portraitFile.isEmpty()) {
            throw new IllegalArgumentException("Ảnh chân dung đưa vào không hợp lệ. Vui lòng tải lên ảnh thẻ chân dung (3x4 hoặc 4x6).");
        }
        byte[] frontBytes = frontFile.getBytes();
        byte[] backBytes = backFile.getBytes();
        String backName = backFile.getOriginalFilename();
        String backType = backFile.getContentType();

        byte[] portraitBytes = portraitFile.getBytes();
        String portraitName = portraitFile.getOriginalFilename();
        String portraitType = portraitFile.getContentType();

        return service.uploadCccd(
                userId,
                frontBytes,
                frontFile.getOriginalFilename(),
                frontFile.getContentType(),
                backBytes,
                backName,
                backType,
                portraitBytes,
                portraitName,
                portraitType
        );
    }

    @PostMapping("/check-face")
    public EkycCheckFaceResponse checkFace(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody EkycCheckFaceRequest request
    ) {
        long userId = Long.parseLong(jwt.getSubject());
        return service.checkFace(userId, request);
    }

    @PostMapping("/submit")
    public EkycSubmitResponse submitVerification(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody EkycSubmitRequest request
    ) {
        long userId = Long.parseLong(jwt.getSubject());
        return service.submitVerification(userId, request);
    }
}
