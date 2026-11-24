package com.oldbutgold.shop.modules.trustsafety.infrastructure.storage;

import com.oldbutgold.shop.modules.trustsafety.application.EvidenceStorageVerifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Local filesystem implementation of {@link EvidenceStorageVerifier}.
 *
 * <p>Activated when {@code app.trustsafety.evidence-strict-verify=true}. Reads the actual file
 * bytes under the configured storage root and computes a real SHA-256 digest while rejecting
 * path traversal attempts fail-closed.
 */
@Component
@ConditionalOnProperty(
        name = "app.trustsafety.evidence-strict-verify",
        havingValue = "true",
        matchIfMissing = false
)
public class LocalEvidenceStorageVerifier implements EvidenceStorageVerifier {

    private final Path storageRoot;

    @Autowired
    public LocalEvidenceStorageVerifier(
            @Value("${app.trustsafety.evidence-storage-root:uploads}") String storageRoot
    ) {
        this(Paths.get(storageRoot != null && !storageRoot.isBlank() ? storageRoot : "uploads"));
    }

    public LocalEvidenceStorageVerifier(Path storageRoot) {
        this.storageRoot = storageRoot.toAbsolutePath().normalize();
    }

    @Override
    public String computeSha256(String objectKey) throws IOException {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        String normalizedKey = objectKey.trim().replace('\\', '/');
        if (normalizedKey.startsWith("/") || normalizedKey.contains("://") || normalizedKey.contains("\0")) {
            throw new IOException("Invalid evidence objectKey outside local storage root: " + objectKey);
        }

        Path candidate = storageRoot.resolve(normalizedKey).normalize();
        if (!candidate.startsWith(storageRoot)) {
            throw new IOException("Path traversal detected for evidence objectKey: " + objectKey);
        }

        if (!Files.exists(candidate) || !Files.isRegularFile(candidate)) {
            return null;
        }

        Path realRoot = Files.exists(storageRoot) ? storageRoot.toRealPath() : storageRoot;
        Path realFile = candidate.toRealPath();
        if (!realFile.startsWith(realRoot)) {
            throw new IOException("Symlink escape detected for evidence objectKey: " + objectKey);
        }

        MessageDigest digest = newSha256();
        byte[] buffer = new byte[8192];
        try (InputStream in = Files.newInputStream(realFile)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
