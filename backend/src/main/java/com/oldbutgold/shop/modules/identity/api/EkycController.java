package com.oldbutgold.shop.modules.identity.api;

import com.oldbutgold.shop.modules.identity.application.EkycVerificationService;
import com.oldbutgold.shop.modules.identity.infrastructure.ekyc.EkycClient;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/ekyc")
public class EkycController {
    private final EkycVerificationService service;

    public EkycController(EkycVerificationService service) {
        this.service = service;
    }

    @PostMapping(value = "/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EkycClient.OcrResult ocr(@RequestPart("file") MultipartFile file) throws IOException {
        return service.ocr(file.getBytes(), file.getOriginalFilename(), file.getContentType());
    }

    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EkycVerificationService.VerificationResult verify(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("cardFile") MultipartFile cardFile,
            @RequestPart("liveFrame") String liveFrame
    ) throws IOException {
        return service.verify(
                Long.parseLong(jwt.getSubject()),
                cardFile.getBytes(),
                cardFile.getOriginalFilename(),
                cardFile.getContentType(),
                liveFrame
        );
    }
}
