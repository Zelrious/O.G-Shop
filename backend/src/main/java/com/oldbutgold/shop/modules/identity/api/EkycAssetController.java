package com.oldbutgold.shop.modules.identity.api;

import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository.AssetRecord;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository.ProfileRecord;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.storage.EkycPrivateStorage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ekyc/assets")
public class EkycAssetController {
    private final EkycRepository repository;
    private final EkycPrivateStorage storage;
    private final UserRepository userRepository;

    public EkycAssetController(
            EkycRepository repository,
            EkycPrivateStorage storage,
            UserRepository userRepository
    ) {
        this.repository = repository;
        this.storage = storage;
        this.userRepository = userRepository;
    }

    @GetMapping("/{assetId}")
    public ResponseEntity<byte[]> getAsset(
            @PathVariable("assetId") long assetId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        long callerId = Long.parseLong(jwt.getSubject());
        AssetRecord asset = repository.findAssetById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found"));

        ProfileRecord profile = repository.findProfileById(asset.profileId())
                .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

        UserEntity caller = userRepository.findById(callerId)
                .orElseThrow(() -> new IllegalArgumentException("Caller not found"));

        boolean isStaff = caller.getRoles().stream()
                .anyMatch(r -> "KTV".equals(r.getRoleName()) || "ADMIN".equals(r.getRoleName()));

        boolean isOwner = profile.userId() == callerId;

        if (!isStaff && !isOwner) {
            throw new AccessDeniedException("Bạn không có quyền truy cập tài liệu này.");
        }

        byte[] bytes = storage.read(asset.objectKey());
        MediaType mediaType = asset.objectKey().endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .contentType(mediaType)
                .body(bytes);
    }
}
