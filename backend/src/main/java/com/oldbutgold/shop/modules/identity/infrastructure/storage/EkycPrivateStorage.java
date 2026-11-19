package com.oldbutgold.shop.modules.identity.infrastructure.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class EkycPrivateStorage {
    private static final Logger log = LoggerFactory.getLogger(EkycPrivateStorage.class);
    private static final long MAX_ASSET_BYTES = 10L * 1024 * 1024; // 10 MB

    public record StoredAsset(String objectKey, String contentDigest, long bytes) {}

    private final Path root;

    public EkycPrivateStorage(@Value("${app.ekyc.upload-dir:uploads/ekyc-private}") String path) {
        this.root = Paths.get(path).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot initialize eKYC private asset directory at " + root, e);
        }
    }

    public StoredAsset store(byte[] data, String mimeType) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Asset data cannot be empty");
        }
        if (data.length > MAX_ASSET_BYTES) {
            throw new IllegalArgumentException("Asset exceeds maximum allowed size (10 MB)");
        }
        String extension = switch (mimeType != null ? mimeType.toLowerCase() : "") {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String filename = UUID.randomUUID() + extension;
        Path targetPath = root.resolve(filename).normalize();
        if (!targetPath.startsWith(root)) {
            throw new SecurityException("Invalid asset storage path");
        }

        try {
            Files.write(targetPath, data, StandardOpenOption.CREATE_NEW);
            String digest = computeSha256(data);
            return new StoredAsset("ekyc/" + filename, digest, data.length);
        } catch (IOException e) {
            log.error("Failed to store private eKYC asset", e);
            throw new IllegalStateException("Failed to store private eKYC asset", e);
        }
    }

    public byte[] read(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Object key cannot be blank");
        }
        String filename = objectKey.startsWith("ekyc/") ? objectKey.substring(5) : objectKey;
        if (!filename.matches("^[0-9a-f-]{36}\\.(jpg|jpeg|png|webp)$")) {
            throw new IllegalArgumentException("Invalid object key format");
        }
        Path assetPath = root.resolve(filename).normalize();
        if (!assetPath.startsWith(root) || !Files.exists(assetPath)) {
            throw new IllegalArgumentException("Asset not found");
        }
        try {
            return Files.readAllBytes(assetPath);
        } catch (IOException e) {
            log.error("Failed to read private eKYC asset: {}", objectKey, e);
            throw new IllegalStateException("Could not read asset", e);
        }
    }

    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return;
        String filename = objectKey.startsWith("ekyc/") ? objectKey.substring(5) : objectKey;
        if (!filename.matches("^[0-9a-f-]{36}\\.(jpg|jpeg|png|webp)$")) return;
        Path assetPath = root.resolve(filename).normalize();
        if (assetPath.startsWith(root)) {
            try {
                Files.deleteIfExists(assetPath);
            } catch (IOException e) {
                log.warn("Failed to delete private eKYC asset: {}", objectKey, e);
            }
        }
    }

    public static String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm not available", e);
        }
    }
}
