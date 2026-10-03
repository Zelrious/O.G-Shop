package com.oldbutgold.shop.modules.catalog.application;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class MediaStorageService {
    private static final Logger log = LoggerFactory.getLogger(MediaStorageService.class);

    public static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024; // 5 MB per baseline
    public static final long MAX_VIDEO_SIZE_BYTES = 50L * 1024 * 1024; // 50 MB per baseline

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of("video/mp4", "video/webm");
    private static final Pattern LOCAL_MEDIA_FILENAME = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?:jpg|jpeg|png|webp|mp4|webm)");

    private final Path uploadDir;
    private final Cloudinary cloudinary;

    public MediaStorageService(
            @Value("${app.media.upload-dir:uploads/media}") String uploadPath,
            @Value("${app.cloudinary.cloud-name:}") String cloudName,
            @Value("${app.cloudinary.api-key:}") String apiKey,
            @Value("${app.cloudinary.api-secret:}") String apiSecret,
            @Value("${app.cloudinary.url:}") String cloudinaryUrl
    ) {
        this.uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("Không thể khởi tạo thư mục lưu trữ media: " + uploadPath, e);
        }

        Cloudinary c = null;
        if (cloudinaryUrl != null) {
            cloudinaryUrl = cloudinaryUrl.trim();
            if (cloudinaryUrl.startsWith("CLOUDINARY_URL=")) {
                cloudinaryUrl = cloudinaryUrl.substring("CLOUDINARY_URL=".length()).trim();
            }
        }
        if (cloudinaryUrl != null && !cloudinaryUrl.isBlank() && !cloudinaryUrl.contains("your_api_key")) {
            c = new Cloudinary(cloudinaryUrl);
            log.info("Cloudinary storage enabled via CLOUDINARY_URL");
        } else if (cloudName != null && !cloudName.isBlank() && !cloudName.contains("your_cloud_name")
                && apiKey != null && !apiKey.isBlank() && !apiKey.contains("your_api_key")
                && apiSecret != null && !apiSecret.isBlank() && !apiSecret.contains("your_api_secret")) {
            c = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", cloudName.trim(),
                    "api_key", apiKey.trim(),
                    "api_secret", apiSecret.trim()
            ));
            log.info("Cloudinary storage enabled for cloud: {}", cloudName);
        } else {
            log.info("Cloudinary credentials not configured; using local storage at: {}", this.uploadDir);
        }
        this.cloudinary = c;
    }

    public StoredMedia store(MultipartFile file, String mediaType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Tập tin tải lên không được rỗng.");
        }

        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase(Locale.ROOT) : "";
        long size = file.getSize();

        if ("IMAGE".equalsIgnoreCase(mediaType)) {
            if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
                throw new IllegalArgumentException("Định dạng ảnh không được hỗ trợ. Chỉ chấp nhận JPG, PNG, WEBP.");
            }
            if (size > MAX_IMAGE_SIZE_BYTES) {
                throw new IllegalArgumentException("Kích thước ảnh vượt quá giới hạn tối đa 5 MB.");
            }
        } else if ("VIDEO".equalsIgnoreCase(mediaType)) {
            if (!ALLOWED_VIDEO_TYPES.contains(contentType)) {
                throw new IllegalArgumentException("Định dạng video không được hỗ trợ. Chỉ chấp nhận MP4, WEBM.");
            }
            if (size > MAX_VIDEO_SIZE_BYTES) {
                throw new IllegalArgumentException("Kích thước video vượt quá giới hạn tối đa 50 MB.");
            }
        } else {
            throw new IllegalArgumentException("Loại media không hợp lệ: " + mediaType);
        }

        // 1. If Cloudinary is configured, upload to Cloudinary CDN
        if (this.cloudinary != null) {
            try {
                String resourceType = "VIDEO".equalsIgnoreCase(mediaType) ? "video" : "image";
                @SuppressWarnings("rawtypes")
                Map uploadResult = this.cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                        "resource_type", resourceType,
                        "folder", "og_shop/products"
                ));
                String secureUrl = (String) uploadResult.get("secure_url");
                String thumbnailUrl = secureUrl;
                if ("VIDEO".equalsIgnoreCase(mediaType)) {
                    int lastDot = secureUrl.lastIndexOf('.');
                    if (lastDot > 0) {
                        thumbnailUrl = secureUrl.substring(0, lastDot) + ".jpg";
                    }
                }
                Integer duration = null;
                if (uploadResult.get("duration") != null) {
                    duration = ((Number) uploadResult.get("duration")).intValue();
                } else if ("VIDEO".equalsIgnoreCase(mediaType)) {
                    duration = 30;
                }
                return new StoredMedia(secureUrl, thumbnailUrl, size, contentType, duration);
            } catch (Exception e) {
                log.warn("Cloudinary upload failed: {}. Falling back to local storage.", e.getMessage());
            }
        }

        // 2. Local fallback storage
        // File paths use only a server-generated ID and a fixed suffix, never the client filename.
        String extension = switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "video/mp4" -> "mp4";
            case "video/webm" -> "webm";
            default -> throw new IllegalArgumentException("Định dạng media không được hỗ trợ.");
        };
        String uniqueFilename = UUID.randomUUID() + "." + extension;

        Path targetPath = this.uploadDir.resolve(uniqueFilename);

        try (InputStream in = file.getInputStream()) {
            // The default CREATE_NEW behavior also refuses existing files and symbolic links.
            Files.copy(in, targetPath);
        } catch (IOException e) {
            throw new RuntimeException("Lưu trữ tập tin media thất bại: " + e.getMessage(), e);
        }

        String mediaUrl = "/api/v1/media/" + uniqueFilename;
        Integer duration = "VIDEO".equalsIgnoreCase(mediaType) ? 30 : null;

        return new StoredMedia(mediaUrl, mediaUrl, size, contentType, duration);
    }

    public Path load(String filename) {
        if (filename == null || filename.contains("..") || filename.contains("/")
                || filename.contains("\\") || !LOCAL_MEDIA_FILENAME.matcher(filename).matches()) {
            throw new IllegalArgumentException("Tên media không hợp lệ.");
        }
        Path resolvedPath = this.uploadDir.resolve(filename).normalize();
        if (!resolvedPath.startsWith(this.uploadDir) || !this.uploadDir.equals(resolvedPath.getParent())) {
            throw new IllegalArgumentException("Đường dẫn media không hợp lệ.");
        }
        return resolvedPath;
    }

    public record StoredMedia(
            String mediaUrl,
            String thumbnailUrl,
            long fileSizeBytes,
            String mimeType,
            Integer durationSeconds
    ) {}
}
