package com.oldbutgold.shop.modules.trustsafety.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.shared.storage.PublicMediaAccessGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Private evidence storage manager (Commit 3):
 * - Enforces magic-byte verification and server-side video duration inspection on upload.
 * - Stores uploaded dispute/return evidence in private storage outside /api/v1/media/**.
 * - Tracks uploader ownership and enforces attachment ownership across UC83, legacy disputes, and Seller R2 resubmissions.
 * - Supports append-only legacy migration (V22 keeps og_compat.case_evidence immutable) via evidence_storage_map and evidence_blocked_public_keys.
 */
@Service
public class EvidenceStorageService implements PublicMediaAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(EvidenceStorageService.class);

    public static final String PRIVATE_EVIDENCE_PREFIX = "/api/v1/trustsafety/returns/evidence/";
    public static final String LEGACY_PUBLIC_PREFIX = "/api/v1/media/";
    public static final int MAX_HOURLY_UPLOADS_PER_USER = 30;

    private static final Pattern SAFE_PRIVATE_FILENAME = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?:jpg|png|webp|mp4|webm)");
    private static final Pattern SAFE_SIMPLE_FILENAME = Pattern.compile("[A-Za-z0-9._-]+");

    private final JdbcTemplate jdbc;
    private final Path privateEvidenceDir;
    private final Path legacyPublicMediaDir;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public EvidenceStorageService(
            @Autowired(required = false) JdbcTemplate jdbc,
            @Value("${app.trustsafety.evidence-storage-root:uploads}") String storageRoot,
            @Value("${app.media.upload-dir:uploads/media}") String publicMediaDir
    ) {
        this.jdbc = jdbc;
        Path root = Paths.get(storageRoot != null && !storageRoot.isBlank() ? storageRoot : "uploads")
                .toAbsolutePath().normalize();
        this.privateEvidenceDir = root.resolve("evidence").normalize();
        this.legacyPublicMediaDir = Paths.get(publicMediaDir != null && !publicMediaDir.isBlank() ? publicMediaDir : "uploads/media")
                .toAbsolutePath().normalize();
    }

    public ReturnCaseDtos.EvidenceItemDto storePrivateEvidence(
            Long actorId,
            MultipartFile file,
            String mediaType
    ) {
        if (actorId == null) {
            throw new AccessDeniedException("Chưa xác thực danh tính");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Tệp bằng chứng tải lên không được rỗng");
        }

        if (jdbc != null) {
            try {
                Long recentUploads = jdbc.queryForObject("""
                        SELECT COUNT(*)
                        FROM public.evidence_uploads
                        WHERE uploader_id = ?
                          AND created_at >= CURRENT_TIMESTAMP - INTERVAL '1 hour'
                        """, Long.class, actorId);
                if (recentUploads != null && recentUploads >= MAX_HOURLY_UPLOADS_PER_USER) {
                    throw new TrustSafetyBusinessException(HttpStatus.TOO_MANY_REQUESTS, "EVIDENCE_UPLOAD_RATE_LIMIT",
                            "Bạn đã tải lên quá nhiều tệp bằng chứng trong 1 giờ qua. Vui lòng thử lại sau.");
                }
            } catch (DataAccessException ex) {
                // Skip rate limit gracefully if running against a test schema lacking V63
                log.debug("Evidence upload rate-limit lookup skipped: {}", ex.getMessage());
            }
        }

        String normalizedType = mediaType != null ? mediaType.trim().toUpperCase(Locale.ROOT) : "IMAGE";
        String declaredMime = file.getContentType() != null ? file.getContentType().toLowerCase(Locale.ROOT).trim() : "";
        long size = file.getSize();

        if ("IMAGE".equals(normalizedType)) {
            if (!Set.of("image/jpeg", "image/png", "image/webp").contains(declaredMime)) {
                throw new IllegalArgumentException("Định dạng ảnh bằng chứng không hợp lệ. Chỉ chấp nhận JPG, PNG, WEBP");
            }
            if (size > ReturnCaseService.MAX_IMAGE_SIZE_BYTES) {
                throw new IllegalArgumentException("Dung lượng ảnh bằng chứng vượt quá giới hạn 5 MB");
            }
        } else if ("VIDEO".equals(normalizedType)) {
            if (!Set.of("video/mp4", "video/webm").contains(declaredMime)) {
                throw new IllegalArgumentException("Định dạng video bằng chứng không hợp lệ. Chỉ chấp nhận MP4, WEBM");
            }
            if (size > ReturnCaseService.MAX_VIDEO_SIZE_BYTES) {
                throw new IllegalArgumentException("Dung lượng video bằng chứng vượt quá giới hạn 50 MB");
            }
        } else {
            throw new IllegalArgumentException("Loại bằng chứng không hợp lệ: " + mediaType + " (chỉ hỗ trợ IMAGE hoặc VIDEO)");
        }

        ensurePrivateDir();
        Path tempFile;
        try {
            tempFile = Files.createTempFile(privateEvidenceDir, "upload-", ".tmp");
        } catch (IOException e) {
            throw new IllegalStateException("Không thể tạo tệp tạm bằng chứng: " + e.getMessage(), e);
        }

        try {
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            String detectedMime = detectMagicMime(tempFile);
            if (detectedMime == null || !detectedMime.equals(declaredMime)) {
                throw new IllegalArgumentException("Nội dung tệp không khớp với định dạng khai báo (" + declaredMime + ").");
            }

            Integer resolvedDuration = null;
            if ("VIDEO".equals(normalizedType)) {
                OptionalInt inspectedDuration = VideoMetadataInspector.extractDurationSeconds(tempFile, detectedMime);
                if (inspectedDuration.isEmpty()) {
                    throw new IllegalArgumentException("Không thể xác định thời lượng từ metadata của video bằng chứng.");
                }
                int actualDuration = inspectedDuration.getAsInt();
                if (actualDuration <= 0 || actualDuration > ReturnCaseService.MAX_VIDEO_DURATION_SECONDS) {
                    throw new IllegalArgumentException("Thời lượng video bằng chứng phải nằm trong khoảng 1 đến "
                            + ReturnCaseService.MAX_VIDEO_DURATION_SECONDS + " giây (thực tế: " + actualDuration + " giây)");
                }
                resolvedDuration = actualDuration;
            }

            String extension = switch (detectedMime) {
                case "image/jpeg" -> "jpg";
                case "image/png" -> "png";
                case "image/webp" -> "webp";
                case "video/mp4" -> "mp4";
                case "video/webm" -> "webm";
                default -> throw new IllegalArgumentException("Định dạng tệp không được hỗ trợ");
            };

            String uniqueFilename = UUID.randomUUID() + "." + extension;
            Path targetPath = privateEvidenceDir.resolve(uniqueFilename).normalize();
            Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);

            long storedSize = Files.size(targetPath);
            String sha256 = computeSha256(targetPath);

            if (jdbc != null) {
                jdbc.update("""
                        INSERT INTO public.evidence_uploads(filename, uploader_id, media_type, content_type, sha256, size_bytes)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, uniqueFilename, actorId, normalizedType, detectedMime, sha256, storedSize);
            }

            return new ReturnCaseDtos.EvidenceItemDto(
                    normalizedType,
                    PRIVATE_EVIDENCE_PREFIX + uniqueFilename,
                    null,
                    sha256,
                    storedSize,
                    resolvedDuration
            );
        } catch (IOException e) {
            throw new IllegalStateException("Lưu tệp bằng chứng thất bại: " + e.getMessage(), e);
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {}
        }
    }

    /**
     * Validates attachment ownership before a file key is linked to any return/dispute record
     * (UC83 create/supplement/exemption, Seller R2 resubmission, or legacy dispute create/response).
     */
    public String verifyAttachmentOwnershipAndResolve(Long actorId, String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return rawKey;
        }
        String key = rawKey.trim();
        if (key.contains("..") || key.contains("\\")) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_EVIDENCE_KEY",
                    "Khóa tệp bằng chứng không hợp lệ.");
        }

        String pathKey = extractPathIfLocalUrl(key);
        if (pathKey.startsWith(PRIVATE_EVIDENCE_PREFIX)) {
            String filename = pathKey.substring(PRIVATE_EVIDENCE_PREFIX.length());
            if (!SAFE_PRIVATE_FILENAME.matcher(filename).matches()) {
                throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_EVIDENCE_KEY",
                        "Khóa tệp bằng chứng riêng tư không hợp lệ.");
            }
            if (jdbc != null) {
                List<Long> owners = jdbc.queryForList(
                        "SELECT uploader_id FROM public.evidence_uploads WHERE filename = ?",
                        Long.class, filename
                );
                if (owners.isEmpty()) {
                    throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_EVIDENCE_KEY",
                            "Không tìm thấy tệp bằng chứng hợp lệ cho khóa đã gửi.");
                }
                Long ownerId = owners.getFirst();
                if (actorId == null || !ownerId.equals(actorId)) {
                    throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "EVIDENCE_OWNERSHIP_MISMATCH",
                            "Bạn không có quyền sử dụng tệp bằng chứng do tài khoản khác tải lên.");
                }
            }
            return PRIVATE_EVIDENCE_PREFIX + filename;
        }

        if (pathKey.startsWith(LEGACY_PUBLIC_PREFIX)) {
            String filename = pathKey.substring(LEGACY_PUBLIC_PREFIX.length());
            if (filename.isBlank() || filename.contains("/") || !SAFE_SIMPLE_FILENAME.matcher(filename).matches()) {
                throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_EVIDENCE_KEY",
                        "Khóa tệp bằng chứng không hợp lệ.");
            }
            if (jdbc != null) {
                List<String> mapped = jdbc.queryForList(
                        "SELECT private_filename FROM public.evidence_storage_map WHERE legacy_key = ?",
                        String.class, LEGACY_PUBLIC_PREFIX + filename
                );
                if (!mapped.isEmpty()) {
                    String mappedPrivate = mapped.getFirst();
                    if (!canAccessMappedEvidence(actorId, mappedPrivate, LEGACY_PUBLIC_PREFIX + filename)) {
                        throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "EVIDENCE_OWNERSHIP_MISMATCH",
                                "Bạn không có quyền sử dụng tệp bằng chứng đã chuyển sang kho riêng tư.");
                    }
                    return PRIVATE_EVIDENCE_PREFIX + mappedPrivate;
                }
                if (isExplicitlyBlockedKey(LEGACY_PUBLIC_PREFIX + filename)) {
                    throw new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "EVIDENCE_OWNERSHIP_MISMATCH",
                            "Tệp bằng chứng công khai đã bị khóa truy cập trực tiếp.");
                }
            }
            return LEGACY_PUBLIC_PREFIX + filename;
        }

        // Reject partial/malformed paths attempting to reference private evidence routes
        if (pathKey.contains("/trustsafety/returns/evidence") || pathKey.startsWith("/")) {
            throw new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "INVALID_EVIDENCE_KEY",
                    "Khóa tệp bằng chứng không hợp lệ.");
        }

        return key;
    }

    /**
     * Resolves an evidence objectKey to its readable file Path on disk (checking private evidence storage first,
     * including legacy mapped files, before falling back to legacy public storage).
     */
    public Path resolvePrivateFilePath(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        String key = objectKey.trim();
        if (key.contains("..") || key.contains("\\")) {
            return null;
        }
        String pathKey = extractPathIfLocalUrl(key);

        if (pathKey.startsWith(PRIVATE_EVIDENCE_PREFIX)) {
            String filename = pathKey.substring(PRIVATE_EVIDENCE_PREFIX.length());
            if (!SAFE_PRIVATE_FILENAME.matcher(filename).matches()) {
                return null;
            }
            Path resolved = privateEvidenceDir.resolve(filename).normalize();
            if (resolved.startsWith(privateEvidenceDir) && Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS) && Files.isReadable(resolved)) {
                return resolved;
            }
            return null;
        }

        if (pathKey.startsWith(LEGACY_PUBLIC_PREFIX)) {
            String filename = pathKey.substring(LEGACY_PUBLIC_PREFIX.length());
            if (filename.isBlank() || filename.contains("/") || !SAFE_SIMPLE_FILENAME.matcher(filename).matches()) {
                return null;
            }
            if (jdbc != null) {
                try {
                    List<String> mapped = jdbc.queryForList(
                            "SELECT private_filename FROM public.evidence_storage_map WHERE legacy_key = ?",
                            String.class, LEGACY_PUBLIC_PREFIX + filename
                    );
                    if (!mapped.isEmpty()) {
                        Path mappedPath = privateEvidenceDir.resolve(mapped.getFirst()).normalize();
                        if (mappedPath.startsWith(privateEvidenceDir)
                                && Files.isRegularFile(mappedPath, LinkOption.NOFOLLOW_LINKS)
                                && Files.isReadable(mappedPath)) {
                            return mappedPath;
                        }
                    }
                } catch (DataAccessException ignored) {}
            }
            Path publicPath = legacyPublicMediaDir.resolve(filename).normalize();
            if (publicPath.startsWith(legacyPublicMediaDir) && Files.isRegularFile(publicPath, LinkOption.NOFOLLOW_LINKS) && Files.isReadable(publicPath)) {
                return publicPath;
            }
            return null;
        }

        try {
            Path direct = Paths.get(key);
            if (!direct.isAbsolute()) {
                Path candidatePrivate = privateEvidenceDir.resolve(direct.getFileName().toString()).normalize();
                if (candidatePrivate.startsWith(privateEvidenceDir) && Files.isRegularFile(candidatePrivate, LinkOption.NOFOLLOW_LINKS)) {
                    return candidatePrivate;
                }
            } else if (Files.isRegularFile(direct, LinkOption.NOFOLLOW_LINKS) && Files.isReadable(direct)) {
                return direct;
            }
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * Authorizes and loads a private evidence file for GET /api/v1/trustsafety/returns/evidence/{filename}.
     */
    public Path loadAuthorizedEvidence(Long actorId, boolean isStaff, String filename) {
        if (actorId == null) {
            throw new AccessDeniedException("Yêu cầu đăng nhập để xem bằng chứng.");
        }
        if (filename == null || !SAFE_PRIVATE_FILENAME.matcher(filename).matches()) {
            throw new IllegalArgumentException("Tên tệp bằng chứng không hợp lệ.");
        }
        Path filePath = privateEvidenceDir.resolve(filename).normalize();
        if (!filePath.startsWith(privateEvidenceDir) || !privateEvidenceDir.equals(filePath.getParent())
                || !Files.isRegularFile(filePath, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(filePath)) {
            throw new NoSuchElementException("Không tìm thấy tệp bằng chứng.");
        }
        if (isStaff) {
            return filePath;
        }
        if (!canActorReadPrivateEvidence(actorId, filename)) {
            throw new AccessDeniedException("Bạn không có quyền truy cập tệp bằng chứng này.");
        }
        return filePath;
    }

    /**
     * Read-time translation of legacy /api/v1/media/... references to authenticated private endpoints
     * without mutating append-only og_compat.case_evidence (V22).
     */
    public String toPrivateEvidenceUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || jdbc == null) {
            return objectKey;
        }
        String normalized = normalizeLegacyKey(objectKey);
        if (normalized == null || !normalized.startsWith(LEGACY_PUBLIC_PREFIX)) {
            return objectKey;
        }
        try {
            List<String> mapped = jdbc.queryForList(
                    "SELECT private_filename FROM public.evidence_storage_map WHERE legacy_key = ?",
                    String.class, normalized
            );
            if (!mapped.isEmpty()) {
                return PRIVATE_EVIDENCE_PREFIX + mapped.getFirst();
            }
        } catch (DataAccessException ignored) {}
        return objectKey;
    }

    public List<String> toPrivateEvidenceUrls(List<String> urls) {
        if (urls == null || urls.isEmpty() || jdbc == null) {
            return urls;
        }
        return urls.stream().map(this::toPrivateEvidenceUrl).toList();
    }

    /**
     * PublicMediaAccessGuard implementation consulted by MediaController before serving /api/v1/media/{filename}.
     */
    @Override
    public boolean isBlockedPublicMedia(String filename) {
        if (filename == null || filename.isBlank() || jdbc == null) {
            return false;
        }
        String clean = filename.trim();
        String legacyKey = LEGACY_PUBLIC_PREFIX + clean;
        try {
            Boolean blocked = jdbc.queryForObject("""
                    SELECT (
                        EXISTS (SELECT 1 FROM public.evidence_blocked_public_keys WHERE legacy_key = ?)
                        OR EXISTS (SELECT 1 FROM public.evidence_storage_map WHERE legacy_key = ? OR private_filename = ?)
                        OR EXISTS (SELECT 1 FROM public.evidence_uploads WHERE filename = ?)
                    )
                    """, Boolean.class, legacyKey, legacyKey, clean, clean);
            return Boolean.TRUE.equals(blocked);
        } catch (DataAccessException ex) {
            return false;
        }
    }

    /**
     * Idempotent legacy evidence migration:
     * 1. Locks all legacy evidence keys in evidence_blocked_public_keys FIRST (blocking /api/v1/media/** even before copy).
     * 2. Copies files safely into private evidence storage, verifies SHA-256 & size, records evidence_storage_map.
     * 3. Cleans up any leftover public files on disk, including leftovers from an interrupted prior run.
     */
    public LegacyMigrationReport migrateLegacyEvidenceFiles() {
        ensurePrivateDir();
        Set<String> legacyKeys = collectReferencedLegacyMediaKeys();
        int blockedCount = 0;
        for (String key : legacyKeys) {
            int inserted = jdbc.update("""
                    INSERT INTO public.evidence_blocked_public_keys(legacy_key)
                    VALUES (?)
                    ON CONFLICT (legacy_key) DO NOTHING
                    """, key);
            blockedCount += inserted;
        }

        int migrated = 0;
        int cleanedDuplicates = 0;
        int alreadyMigrated = 0;
        List<String> missingKeys = new ArrayList<>();

        for (String legacyKey : legacyKeys) {
            String legacyFilename = legacyKey.substring(LEGACY_PUBLIC_PREFIX.length());
            if (legacyFilename.isBlank() || legacyFilename.contains("/") || legacyFilename.contains("..")
                    || !SAFE_SIMPLE_FILENAME.matcher(legacyFilename).matches()) {
                missingKeys.add(legacyKey);
                continue;
            }

            List<Map<String, Object>> existingMaps = jdbc.queryForList(
                    "SELECT private_filename, sha256, size_bytes FROM public.evidence_storage_map WHERE legacy_key = ?",
                    legacyKey
            );

            Path publicPath = legacyPublicMediaDir.resolve(legacyFilename).normalize();
            String ext = extractAllowedExtension(legacyFilename);
            String privateFilename = existingMaps.isEmpty()
                    ? UUID.randomUUID() + "." + ext
                    : existingMaps.getFirst().get("private_filename").toString();
            Path privatePath = privateEvidenceDir.resolve(privateFilename).normalize();

            boolean hasPrivate = Files.isRegularFile(privatePath, LinkOption.NOFOLLOW_LINKS);
            boolean hasPublic = Files.isRegularFile(publicPath, LinkOption.NOFOLLOW_LINKS);

            if (hasPrivate) {
                try {
                    String sha = computeSha256(privatePath);
                    long sz = Files.size(privatePath);
                    if (existingMaps.isEmpty()) {
                        jdbc.update("""
                                INSERT INTO public.evidence_storage_map(legacy_key, private_filename, sha256, size_bytes)
                                VALUES (?, ?, ?, ?)
                                ON CONFLICT (legacy_key) DO NOTHING
                                """, legacyKey, privateFilename, sha, sz);
                    }
                    if (hasPublic) {
                        Files.deleteIfExists(publicPath);
                        cleanedDuplicates++;
                    } else {
                        alreadyMigrated++;
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("Lỗi đối soát tệp đã chuyển: " + legacyKey, e);
                }
                continue;
            }

            if (!hasPublic) {
                missingKeys.add(legacyKey);
                continue;
            }

            Path tempCopy = null;
            try {
                tempCopy = Files.createTempFile(privateEvidenceDir, "migrate-", ".tmp");
                Files.copy(publicPath, tempCopy, StandardCopyOption.REPLACE_EXISTING);
                String srcSha = computeSha256(publicPath);
                long srcSize = Files.size(publicPath);
                String dstSha = computeSha256(tempCopy);
                long dstSize = Files.size(tempCopy);
                if (!srcSha.equals(dstSha) || srcSize != dstSize) {
                    throw new IllegalStateException("Kiểm tra toàn vẹn SHA-256 thất bại khi chuyển " + legacyKey);
                }
                Files.move(tempCopy, privatePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                jdbc.update("""
                        INSERT INTO public.evidence_storage_map(legacy_key, private_filename, sha256, size_bytes)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (legacy_key) DO NOTHING
                        """, legacyKey, privateFilename, dstSha, dstSize);
                Files.deleteIfExists(publicPath);
                migrated++;
            } catch (IOException e) {
                throw new IllegalStateException("Không thể di chuyển tệp bằng chứng cũ " + legacyKey + ": " + e.getMessage(), e);
            } finally {
                if (tempCopy != null) {
                    try { Files.deleteIfExists(tempCopy); } catch (IOException ignored) {}
                }
            }
        }

        return new LegacyMigrationReport(legacyKeys.size(), blockedCount, migrated, cleanedDuplicates, alreadyMigrated, missingKeys);
    }

    private Set<String> collectReferencedLegacyMediaKeys() {
        Set<String> keys = new LinkedHashSet<>();
        if (jdbc == null) {
            return keys;
        }
        for (String raw : jdbc.queryForList(
                "SELECT object_key FROM og_compat.case_evidence WHERE object_key IS NOT NULL AND deleted_at IS NULL",
                String.class)) {
            String normalized = normalizeLegacyKey(raw);
            if (normalized != null && normalized.startsWith(LEGACY_PUBLIC_PREFIX)) {
                keys.add(normalized);
            }
        }
        try {
            List<Map<String, Object>> disputeRows = jdbc.queryForList(
                    "SELECT video_unboxing_url, buyer_evidence_urls, seller_evidence_urls FROM public.dispute_cases");
            for (Map<String, Object> row : disputeRows) {
                addNormalizedKey(keys, row.get("video_unboxing_url") != null ? row.get("video_unboxing_url").toString() : null);
                for (String url : parseJsonUrlList(row.get("buyer_evidence_urls"))) {
                    addNormalizedKey(keys, url);
                }
                for (String url : parseJsonUrlList(row.get("seller_evidence_urls"))) {
                    addNormalizedKey(keys, url);
                }
            }
        } catch (DataAccessException ignored) {}
        return keys;
    }

    private void addNormalizedKey(Set<String> keys, String raw) {
        String norm = normalizeLegacyKey(raw);
        if (norm != null && norm.startsWith(LEGACY_PUBLIC_PREFIX)) {
            keys.add(norm);
        }
    }

    private List<String> parseJsonUrlList(Object value) {
        if (value == null) return List.of();
        String s = value.toString().trim();
        if (s.isEmpty() || !s.startsWith("[")) return List.of();
        try {
            return json.readValue(s, new TypeReference<>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private boolean canActorReadPrivateEvidence(Long actorId, String privateFilename) {
        if (jdbc == null) {
            return false;
        }
        String privateUrl = PRIVATE_EVIDENCE_PREFIX + privateFilename;
        String legacyKey = findLegacyKeyForPrivateFilename(privateFilename);
        if (canAccessMappedEvidence(actorId, privateFilename, legacyKey)) {
            return true;
        }
        Boolean caseEvidenceAllowed = legacyKey != null
                ? jdbc.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1 FROM og_compat.case_evidence ev
                            JOIN og_compat.case_rounds cr ON cr.round_id = ev.round_id
                            JOIN og_compat.cases c ON c.case_id = cr.case_id
                            LEFT JOIN og_compat.orders o ON o.order_id = c.order_id
                            WHERE ev.deleted_at IS NULL
                              AND (ev.object_key = ? OR ev.object_key = ?)
                              AND (? = c.created_by OR ? = o.buyer_id OR ? = o.seller_id OR ? = ev.submitted_by)
                        )
                        """, Boolean.class, privateUrl, legacyKey, actorId, actorId, actorId, actorId)
                : jdbc.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1 FROM og_compat.case_evidence ev
                            JOIN og_compat.case_rounds cr ON cr.round_id = ev.round_id
                            JOIN og_compat.cases c ON c.case_id = cr.case_id
                            LEFT JOIN og_compat.orders o ON o.order_id = c.order_id
                            WHERE ev.deleted_at IS NULL
                              AND ev.object_key = ?
                              AND (? = c.created_by OR ? = o.buyer_id OR ? = o.seller_id OR ? = ev.submitted_by)
                        )
                        """, Boolean.class, privateUrl, actorId, actorId, actorId, actorId);
        return Boolean.TRUE.equals(caseEvidenceAllowed) || canAccessLegacyDisputeEvidence(actorId, privateUrl, legacyKey);
    }

    private boolean canAccessMappedEvidence(Long actorId, String privateFilename, String legacyKey) {
        if (jdbc == null || actorId == null) {
            return false;
        }
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM public.evidence_uploads WHERE filename = ? AND uploader_id = ?)",
                Boolean.class, privateFilename, actorId
        )) || (legacyKey != null && Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM og_compat.case_evidence ev
                    JOIN og_compat.case_rounds cr ON cr.round_id = ev.round_id
                    JOIN og_compat.cases c ON c.case_id = cr.case_id
                    LEFT JOIN og_compat.orders o ON o.order_id = c.order_id
                    WHERE ev.deleted_at IS NULL
                      AND ev.object_key = ?
                      AND (? = c.created_by OR ? = o.buyer_id OR ? = o.seller_id OR ? = ev.submitted_by)
                )
                """, Boolean.class, legacyKey, actorId, actorId, actorId, actorId)))
                || canAccessLegacyDisputeEvidence(actorId, PRIVATE_EVIDENCE_PREFIX + privateFilename, legacyKey);
    }

    private boolean canAccessLegacyDisputeEvidence(Long actorId, String privateUrl, String legacyKey) {
        if (jdbc == null || actorId == null) {
            return false;
        }
        try {
            String privPattern = "%" + privateUrl + "%";
            if (legacyKey != null) {
                String legacyPattern = "%" + legacyKey + "%";
                return Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1 FROM public.dispute_cases d
                            WHERE (? IN (d.buyer_id, d.seller_id))
                              AND (
                                  d.video_unboxing_url = ?
                                  OR d.buyer_evidence_urls LIKE ?
                                  OR d.seller_evidence_urls LIKE ?
                                  OR d.video_unboxing_url = ?
                                  OR d.buyer_evidence_urls LIKE ?
                                  OR d.seller_evidence_urls LIKE ?
                              )
                        )
                        """, Boolean.class, actorId, privateUrl, privPattern, privPattern,
                        legacyKey, legacyPattern, legacyPattern));
            }
            return Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1 FROM public.dispute_cases d
                        WHERE (? IN (d.buyer_id, d.seller_id))
                          AND (
                              d.video_unboxing_url = ?
                              OR d.buyer_evidence_urls LIKE ?
                              OR d.seller_evidence_urls LIKE ?
                          )
                    )
                    """, Boolean.class, actorId, privateUrl, privPattern, privPattern));
        } catch (DataAccessException ignored) {
            return false;
        }
    }

    private String findLegacyKeyForPrivateFilename(String privateFilename) {
        if (jdbc == null) return null;
        try {
            List<String> keys = jdbc.queryForList(
                    "SELECT legacy_key FROM public.evidence_storage_map WHERE private_filename = ?",
                    String.class, privateFilename
            );
            return keys.isEmpty() ? null : keys.getFirst();
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private boolean isExplicitlyBlockedKey(String legacyKey) {
        try {
            return Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM public.evidence_blocked_public_keys WHERE legacy_key = ?)",
                    Boolean.class, legacyKey));
        } catch (DataAccessException ex) {
            return false;
        }
    }

    private static String normalizeLegacyKey(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String path = extractPathIfLocalUrl(raw.trim());
        return path.startsWith(LEGACY_PUBLIC_PREFIX) ? path : null;
    }

    private static String extractPathIfLocalUrl(String key) {
        if (key.startsWith("http://") || key.startsWith("https://")) {
            try {
                URI uri = URI.create(key);
                if (uri.getPath() != null && (uri.getPath().startsWith(PRIVATE_EVIDENCE_PREFIX)
                        || uri.getPath().startsWith(LEGACY_PUBLIC_PREFIX))) {
                    return uri.getPath();
                }
            } catch (Exception ignored) {}
        }
        return key;
    }

    private static String extractAllowedExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        String ext = dot > 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        return switch (ext) {
            case "jpeg", "jpg" -> "jpg";
            case "png" -> "png";
            case "webp" -> "webp";
            case "mp4" -> "mp4";
            case "webm" -> "webm";
            default -> "png";
        };
    }

    private void ensurePrivateDir() {
        try {
            Files.createDirectories(privateEvidenceDir);
        } catch (IOException e) {
            throw new IllegalStateException("Không thể khởi tạo thư mục lưu trữ bằng chứng riêng tư: " + e.getMessage(), e);
        }
    }

    static String detectMagicMime(Path filePath) throws IOException {
        byte[] header = new byte[12];
        int n;
        try (InputStream in = Files.newInputStream(filePath)) {
            n = in.readNBytes(header, 0, 12);
        }
        if (n >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (n >= 8 && (header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47
                && header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A) {
            return "image/png";
        }
        if (n >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "image/webp";
        }
        if (n >= 8 && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p') {
            return "video/mp4";
        }
        if (n >= 4 && (header[0] & 0xFF) == 0x1A && header[1] == 0x45 && (header[2] & 0xFF) == 0xDF && (header[3] & 0xFF) == 0xA3) {
            return "video/webm";
        }
        return null;
    }

    private static String computeSha256(Path filePath) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(filePath)) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) != -1) {
                    md.update(buf, 0, read);
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Hệ thống không hỗ trợ thuật toán SHA-256", e);
        }
    }

    public record LegacyMigrationReport(
            int totalReferencedKeys,
            int newlyBlockedCount,
            int migratedCount,
            int cleanedDuplicateCount,
            int alreadyMigratedCount,
            List<String> missingKeys
    ) {}
}

