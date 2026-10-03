package com.oldbutgold.shop.modules.identity.infrastructure.avatar;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.oldbutgold.shop.modules.identity.application.AvatarImage;
import com.oldbutgold.shop.modules.identity.application.AvatarStoragePort;
import com.oldbutgold.shop.modules.identity.application.AvatarStorageUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CloudinaryAvatarStorage implements AvatarStoragePort {
    private static final String PREFIX = "og_shop/avatars/";
    private final Cloudinary cloudinary;

    @Autowired
    public CloudinaryAvatarStorage(
            @Value("${app.cloudinary.cloud-name:}") String cloudName,
            @Value("${app.cloudinary.api-key:}") String apiKey,
            @Value("${app.cloudinary.api-secret:}") String apiSecret,
            @Value("${app.cloudinary.url:}") String cloudinaryUrl) {
        Cloudinary configured = null;
        String url = cloudinaryUrl == null ? "" : cloudinaryUrl.trim();
        if (url.startsWith("CLOUDINARY_URL=")) url = url.substring("CLOUDINARY_URL=".length()).trim();
        try {
            if (!url.isBlank() && !url.contains("your_api_key")) {
                configured = new Cloudinary(url);
            } else if (present(cloudName) && present(apiKey) && present(apiSecret)) {
                configured = new Cloudinary(ObjectUtils.asMap("cloud_name", cloudName.trim(), "api_key", apiKey.trim(),
                        "api_secret", apiSecret.trim(), "secure", true));
            }
        } catch (RuntimeException ignored) {
            // Misconfiguration must not expose credentials or break unrelated profile reads.
        }
        this.cloudinary = configured;
    }

    CloudinaryAvatarStorage(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @Override
    public StoredAvatar upload(long userId, AvatarImage image) {
        if (cloudinary == null) throw new AvatarStorageUnavailableException();
        String publicId = PREFIX + userId + "/" + UUID.randomUUID();
        Map<?, ?> result;
        try {
            result = cloudinary.uploader().upload(image.bytes(), ObjectUtils.asMap(
                    "public_id", publicId, "resource_type", "image", "overwrite", false,
                    "allowed_formats", List.of("jpg", "png", "webp")));
        } catch (IOException | RuntimeException exception) {
            throw new AvatarStorageUnavailableException();
        }
        try {
            String url = (String) result.get("secure_url");
            URI uri = URI.create(url);
            if (!publicId.equals(result.get("public_id")) || !"image".equals(result.get("resource_type"))
                    || !image.format().equals(result.get("format")) || !"https".equals(uri.getScheme())
                    || uri.getHost() == null || uri.getUserInfo() != null || url.length() > 500) {
                throw new IllegalArgumentException("Invalid upload metadata");
            }
            AvatarImage.validateDimensions(((Number) result.get("width")).longValue(), ((Number) result.get("height")).longValue());
            return new StoredAvatar(url, publicId);
        } catch (RuntimeException invalidMetadata) {
            try {
                delete(new StoredAvatar("", publicId));
            } catch (RuntimeException ignored) {
                // Preserve the original failure, never delete an ID supplied by the response.
            }
            throw new AvatarStorageUnavailableException();
        }
    }

    @Override
    public void delete(StoredAvatar avatar) {
        if (cloudinary == null || avatar == null || avatar.publicId() == null
                || !avatar.publicId().matches("og_shop/avatars/[1-9][0-9]*/[0-9a-f-]{36}")) {
            throw new AvatarStorageUnavailableException();
        }
        try {
            Map<?, ?> result = cloudinary.uploader().destroy(avatar.publicId(), ObjectUtils.asMap("resource_type", "image", "invalidate", true));
            if (!List.of("ok", "not found").contains(result.get("result"))) throw new AvatarStorageUnavailableException();
        } catch (IOException | RuntimeException exception) {
            throw new AvatarStorageUnavailableException();
        }
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank() && !value.contains("your_");
    }
}
