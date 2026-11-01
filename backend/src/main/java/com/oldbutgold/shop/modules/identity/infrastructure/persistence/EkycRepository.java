package com.oldbutgold.shop.modules.identity.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class EkycRepository {
    private static final Logger log = LoggerFactory.getLogger(EkycRepository.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public EkycRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record ProfileRecord(
            long profileId,
            long userId,
            int revisionNo,
            String state,
            String submittedDataJson,
            String aiSummaryJson,
            int nonmatchCount,
            Instant lastNonmatchAt,
            Instant createdAt,
            Instant submittedAt,
            Instant verifiedAt,
            Instant referenceExpiresAt,
            long version,
            String documentDigest,
            String rejectionReason,
            Instant reviewedAt,
            Long verifiedBy
    ) {}

    public record AssetRecord(
            long assetId,
            long profileId,
            String assetType,
            String objectKey,
            String contentDigest,
            Instant capturedAt,
            Instant expiresAt,
            Instant deletedAt
    ) {}

    public record QuickAuthSessionRecord(
            UUID sessionId,
            long userId,
            long profileId,
            long referenceAssetId,
            int nonmatchCount,
            Instant lastNonmatchAt,
            Instant expiresAt,
            String commandKey
    ) {}

    public record QuickAuthResultRecord(
            UUID resultId,
            long userId,
            long profileId,
            long referenceAssetId,
            Instant matchedAt,
            Instant expiresAt,
            String commandKey,
            UUID sessionId
    ) {}

    public Optional<ProfileRecord> findActiveProfile(long userId) {
        String sql = """
            SELECT * FROM public.ekyc_profiles
            WHERE user_id = ? AND record_type = 'ekyc_profiles'
              AND state IN ('DRAFT', 'IN_PROGRESS', 'PENDING')
            ORDER BY revision_no DESC LIMIT 1
            """;
        return queryProfile(sql, userId);
    }

    public Optional<ProfileRecord> findLatestProfile(long userId) {
        String sql = """
            SELECT * FROM public.ekyc_profiles
            WHERE user_id = ? AND record_type = 'ekyc_profiles'
            ORDER BY revision_no DESC LIMIT 1
            """;
        return queryProfile(sql, userId);
    }

    public boolean hasVerifiedProfile(long userId) {
        String sql = """
            SELECT count(*) FROM public.ekyc_profiles
            WHERE user_id = ? AND record_type = 'ekyc_profiles' AND state = 'VERIFIED'
            """;
        try {
            Integer count = jdbc.queryForObject(sql, Integer.class, userId);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public Optional<ProfileRecord> findProfileById(long profileId) {
        String sql = """
            SELECT * FROM public.ekyc_profiles
            WHERE profile_id = ? AND record_type = 'ekyc_profiles'
            """;
        return queryProfile(sql, profileId);
    }

    public Optional<ProfileRecord> findProfileByIdForUpdate(long profileId) {
        String sql = """
            SELECT * FROM public.ekyc_profiles
            WHERE profile_id = ? AND record_type = 'ekyc_profiles'
            FOR UPDATE
            """;
        return queryProfile(sql, profileId);
    }

    public ProfileRecord createOrGetDraft(long userId) {
        var active = findActiveProfile(userId);
        if (active.isPresent()) {
            return active.get();
        }
        String sql = """
            INSERT INTO public.ekyc_profiles(user_id, revision_no, state, record_type)
            SELECT ?, COALESCE(max(revision_no), 0) + 1, 'IN_PROGRESS', 'ekyc_profiles'
            FROM public.ekyc_profiles WHERE user_id = ?
            RETURNING profile_id
            """;
        Long profileId = jdbc.queryForObject(sql, Long.class, userId, userId);
        if (profileId == null) {
            throw new IllegalStateException("Failed to create eKYC profile");
        }
        return findProfileById(profileId).orElseThrow();
    }

    public void invalidateDraftAssets(long profileId) {
        String sql = """
            UPDATE public.ekyc_private_assets
            SET deleted_at = CURRENT_TIMESTAMP
            WHERE profile_id = ? AND deleted_at IS NULL
            """;
        jdbc.update(sql, profileId);
    }

    public long saveAsset(long profileId, String assetType, String objectKey, String contentDigest, Instant expiresAt) {
        String sql = """
            INSERT INTO public.ekyc_private_assets(profile_id, asset_type, object_key, content_digest, captured_at, expires_at, record_type)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, 'ekyc_private_assets')
            RETURNING asset_id
            """;
        Long id = jdbc.queryForObject(sql, Long.class, profileId, assetType, objectKey, contentDigest, Timestamp.from(expiresAt));
        if (id == null) {
            throw new IllegalStateException("Failed to save private asset record");
        }
        return id;
    }

    public Optional<AssetRecord> findAssetById(long assetId) {
        String sql = """
            SELECT * FROM public.ekyc_private_assets
            WHERE asset_id = ? AND record_type = 'ekyc_private_assets' AND deleted_at IS NULL
            """;
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, this::mapAsset, assetId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<AssetRecord> findAssetsByProfileId(long profileId) {
        String sql = """
            SELECT * FROM public.ekyc_private_assets
            WHERE profile_id = ? AND record_type = 'ekyc_private_assets' AND deleted_at IS NULL
            ORDER BY asset_id ASC
            """;
        return jdbc.query(sql, this::mapAsset, profileId);
    }

    public Optional<Long> findCccdOwner(String documentDigest) {
        String sql = "SELECT user_id FROM public.identity_document_registry WHERE document_digest = ?";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, Long.class, documentDigest));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void registerDocumentDigest(String documentDigest, long userId, long firstProfileId) {
        String sql = """
            INSERT INTO public.identity_document_registry(document_digest, digest_key_version, user_id, first_profile_id, registered_at, record_type)
            VALUES (?, 'HMAC-V1', ?, ?, CURRENT_TIMESTAMP, 'identity_document_registry')
            ON CONFLICT (document_digest) DO NOTHING
            """;
        jdbc.update(sql, documentDigest, userId, firstProfileId);
    }

    public void recordAttempt(long profileId, String result, Map<String, Object> metadata, String commandKey) {
        String jsonStr;
        try {
            jsonStr = json.writeValueAsString(metadata != null ? metadata : Map.of());
        } catch (JsonProcessingException e) {
            jsonStr = "{}";
        }
        String sql = """
            INSERT INTO og_compat.ekyc_verification_attempts(profile_id, result, model_metadata, command_key)
            VALUES (?, ?, ?::jsonb, ?)
            """;
        jdbc.update(sql, profileId, result, jsonStr, commandKey);
    }

    public void submitProfile(long profileId, String documentDigest, String submittedDataJson, String aiSummaryJson) {
        String sql = """
            UPDATE public.ekyc_profiles
            SET document_digest = ?,
                submitted_data = ?::jsonb,
                ai_summary = ?::jsonb,
                state = 'PENDING',
                submitted_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE profile_id = ? AND state IN ('DRAFT', 'IN_PROGRESS')
            """;
        int updated = jdbc.update(sql, documentDigest, submittedDataJson, aiSummaryJson, profileId);
        if (updated != 1) {
            throw new IllegalStateException("Hồ sơ không ở trạng thái sẵn sàng nộp hoặc đã được cập nhật bởi tiến trình khác.");
        }
    }

    public void recordDecision(long profileId, long ktvUserId, String outcome, String reason, String commandKey) {
        String sql = """
            INSERT INTO og_compat.ekyc_decisions(profile_id, reviewed_by, outcome, reason, decided_at, command_key)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
            """;
        jdbc.update(sql, profileId, ktvUserId, outcome, reason, commandKey);
    }

    public void grantSellerRole(long userId) {
        grantSellerRole(userId, 0L, 0L);
    }

    public void grantSellerRole(long userId, long ktvUserId, long profileId) {
        try {
            // 1. Kiểm tra nếu tài khoản đã có quyền SELLER thì không cần làm lại
            Boolean alreadySeller = jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM og_compat.user_roles ur JOIN og_compat.roles r ON ur.role_id = r.role_id WHERE ur.user_id = ? AND r.role_name = 'SELLER')",
                    Boolean.class, userId
            );
            if (Boolean.TRUE.equals(alreadySeller)) {
                return;
            }

            // 2. Kiểm tra luồng định danh (identity_workflow) của người dùng
            String identityWorkflow = jdbc.queryForObject(
                    "SELECT identity_workflow FROM public.users WHERE user_id = ? AND record_type = 'users'",
                    String.class, userId
            );

            if (!"UC83".equals(identityWorkflow)) {
                // Luồng Legacy: Cho phép chèn trực tiếp vào user_roles
                String sql = """
                    INSERT INTO og_compat.user_roles(user_id, role_id)
                    SELECT ?, role_id FROM og_compat.roles WHERE role_name = 'SELLER'
                    ON CONFLICT DO NOTHING
                    """;
                jdbc.update(sql, userId);
                return;
            }

            // 3. Luồng UC83 Canonical: Gán quyền SELLER cần có quyết định phê duyệt seller_profiles
            long effectiveKtvId = ktvUserId;
            if (effectiveKtvId <= 0) {
                Long defaultKtv = jdbc.query(
                        "SELECT u.user_id FROM og_compat.users u JOIN og_compat.user_roles ur ON u.user_id = ur.user_id JOIN og_compat.roles r ON ur.role_id = r.role_id WHERE r.role_name = 'KTV' LIMIT 1",
                        (rs, rowNum) -> rs.getLong("user_id")
                ).stream().findFirst().orElse(null);
                effectiveKtvId = defaultKtv != null ? defaultKtv : 7L;
            }

            long resolvedProfileId = profileId;
            if (resolvedProfileId <= 0) {
                Long latestVerified = jdbc.query(
                        "SELECT profile_id FROM public.ekyc_profiles WHERE user_id = ? AND record_type = 'ekyc_profiles' AND state = 'VERIFIED' ORDER BY revision_no DESC, profile_id DESC LIMIT 1",
                        (rs, rowNum) -> rs.getLong("profile_id"),
                        userId
                ).stream().findFirst().orElse(null);
                if (latestVerified != null) resolvedProfileId = latestVerified;
            }

            // Lấy địa chỉ lấy hàng khả dụng
            Long pickupAddressId = jdbc.query(
                    "SELECT address_id FROM public.addresses WHERE user_id = ? AND deleted_at IS NULL ORDER BY is_default DESC, address_id ASC LIMIT 1",
                    (rs, rowNum) -> rs.getLong("address_id"),
                    userId
            ).stream().findFirst().orElse(null);

            // Lấy thông tin tài khoản ngân hàng từ users
            Map<String, Object> bankRow = jdbc.queryForList(
                    "SELECT bank_name, bank_account_number, bank_account_holder FROM public.users WHERE user_id = ?",
                    userId
            ).stream().findFirst().orElse(Map.of());

            String bankName = (String) bankRow.get("bank_name");
            String accountNumber = (String) bankRow.get("bank_account_number");
            String accountHolder = (String) bankRow.get("bank_account_holder");

            // Tìm seller_profile hiện có
            List<Map<String, Object>> existingProfiles = jdbc.queryForList(
                    "SELECT seller_profile_id, revision_no, state, ekyc_profile_id, pickup_address_id, bank_snapshot FROM og_compat.seller_profiles WHERE user_id = ? FOR UPDATE",
                    userId
            );

            Long sellerProfileId = null;
            int revisionNo = 1;

            if (existingProfiles.isEmpty()) {
                if (pickupAddressId != null && bankName != null && accountNumber != null && accountHolder != null) {
                    String bankSnapshotJson;
                    try {
                        bankSnapshotJson = json.writeValueAsString(Map.of(
                                "bank_name", bankName.trim(),
                                "account_number", accountNumber.trim(),
                                "account_holder", accountHolder.trim().toUpperCase()
                        ));
                    } catch (Exception e) {
                        bankSnapshotJson = "{}";
                    }
                    String insertSql = """
                        INSERT INTO og_compat.seller_profiles(user_id, revision_no, state, ekyc_profile_id, pickup_address_id, bank_snapshot, updated_at)
                        VALUES (?, 1, 'PENDING_REVIEW', ?, ?, ?::jsonb, CURRENT_TIMESTAMP)
                        RETURNING seller_profile_id
                        """;
                    sellerProfileId = jdbc.queryForObject(insertSql, Long.class, userId, resolvedProfileId > 0 ? resolvedProfileId : null, pickupAddressId, bankSnapshotJson);
                    revisionNo = 1;
                }
            } else {
                Map<String, Object> sp = existingProfiles.get(0);
                sellerProfileId = ((Number) sp.get("seller_profile_id")).longValue();
                revisionNo = ((Number) sp.get("revision_no")).intValue();
                Long existingPickup = sp.get("pickup_address_id") instanceof Number n ? n.longValue() : pickupAddressId;

                String bankSnapshotJson;
                try {
                    bankSnapshotJson = json.writeValueAsString(Map.of(
                            "bank_name", bankName != null && !bankName.isBlank() ? bankName.trim() : "O.G Bank",
                            "account_number", accountNumber != null && !accountNumber.isBlank() ? accountNumber.trim() : "00000000",
                            "account_holder", accountHolder != null && !accountHolder.isBlank() ? accountHolder.trim().toUpperCase() : "SELLER"
                    ));
                } catch (Exception e) {
                    bankSnapshotJson = "{}";
                }

                if (existingPickup != null) {
                    String updateSql = """
                        UPDATE og_compat.seller_profiles
                        SET ekyc_profile_id = COALESCE(?, ekyc_profile_id),
                            pickup_address_id = COALESCE(pickup_address_id, ?),
                            bank_snapshot = CASE WHEN jsonb_typeof(bank_snapshot) = 'object' AND bank_snapshot ? 'account_number' THEN bank_snapshot ELSE ?::jsonb END,
                            state = 'PENDING_REVIEW',
                            updated_at = CURRENT_TIMESTAMP
                        WHERE seller_profile_id = ?
                        """;
                    jdbc.update(updateSql, resolvedProfileId > 0 ? resolvedProfileId : null, existingPickup, bankSnapshotJson, sellerProfileId);
                } else {
                    sellerProfileId = null;
                }
            }

            if (sellerProfileId != null) {
                Boolean alreadyApproved = jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM og_compat.seller_profile_decisions WHERE seller_profile_id = ? AND revision_no = ? AND outcome = 'APPROVED')",
                        Boolean.class, sellerProfileId, revisionNo
                );
                if (!Boolean.TRUE.equals(alreadyApproved)) {
                    Boolean decisionExists = jdbc.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM og_compat.seller_profile_decisions WHERE seller_profile_id = ? AND revision_no = ?)",
                            Boolean.class, sellerProfileId, revisionNo
                    );
                    if (Boolean.TRUE.equals(decisionExists)) {
                        revisionNo++;
                        jdbc.update("UPDATE og_compat.seller_profiles SET revision_no = ?, state = 'PENDING_REVIEW', updated_at = CURRENT_TIMESTAMP WHERE seller_profile_id = ?", revisionNo, sellerProfileId);
                    }
                    String cmdKey = "seller-decision:" + UUID.randomUUID();
                    String decisionSql = """
                        INSERT INTO og_compat.seller_profile_decisions(seller_profile_id, revision_no, reviewed_by, outcome, profile_snapshot, reason, decided_at, command_key)
                        VALUES (?, ?, ?, 'APPROVED', '{}'::jsonb, 'KTV phê duyệt hồ sơ eKYC', CURRENT_TIMESTAMP, ?)
                        """;
                    jdbc.update(decisionSql, sellerProfileId, revisionNo, effectiveKtvId, cmdKey);
                }
            } else {
                log.info("Người dùng #{} chưa có đủ địa chỉ lấy hàng hoặc tài khoản ngân hàng để tạo seller_profile trong luồng UC83; quyền SELLER sẽ kích hoạt khi hoàn thiện hồ sơ.", userId);
            }
        } catch (Exception e) {
            log.warn("Không thể kích hoạt tự động quyền Người bán cho người dùng #{}: {}", userId, e.getMessage());
        }
    }

    public record PendingQueueItem(
            long profileId,
            long userId,
            String username,
            String fullName,
            String email,
            String phone,
            int revisionNo,
            String state,
            Instant submittedAt,
            String submittedDataJson,
            String aiSummaryJson
    ) {}

    public List<PendingQueueItem> getPendingQueue(int page, int size) {
        String sql = """
            SELECT p.profile_id, p.user_id, u.email AS username, u.full_name, u.email, u.phone_number,
                   p.revision_no, p.state, p.submitted_at, p.submitted_data, p.ai_summary
            FROM public.ekyc_profiles p
            JOIN public.users u ON p.user_id = u.user_id
            WHERE p.record_type = 'ekyc_profiles' AND p.state = 'PENDING'
            ORDER BY p.submitted_at ASC
            LIMIT ? OFFSET ?
            """;
        return jdbc.query(sql, (rs, rowNum) -> new PendingQueueItem(
                rs.getLong("profile_id"),
                rs.getLong("user_id"),
                rs.getString("username"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("phone_number"),
                rs.getInt("revision_no"),
                rs.getString("state"),
                instant(rs, "submitted_at"),
                rs.getString("submitted_data"),
                rs.getString("ai_summary")
        ), size, (long) page * size);
    }

    public long countPendingQueue() {
        String sql = "SELECT count(*) FROM public.ekyc_profiles WHERE record_type = 'ekyc_profiles' AND state = 'PENDING'";
        Long count = jdbc.queryForObject(sql, Long.class);
        return count != null ? count : 0L;
    }

    public Optional<String> findRejectionReason(long profileId) {
        String sql = """
            SELECT reason FROM og_compat.ekyc_decisions
            WHERE profile_id = ? AND outcome = 'REJECTED'
            ORDER BY decided_at DESC LIMIT 1
            """;
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, String.class, profileId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<QuickAuthResultRecord> findValidQuickAuthResult(long userId) {
        String sql = """
            SELECT * FROM og_compat.quick_auth_results
            WHERE user_id = ? AND expires_at > CURRENT_TIMESTAMP
            ORDER BY matched_at DESC LIMIT 1
            """;
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, this::mapQuickAuthResult, userId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<AssetRecord> findApprovedReferenceAsset(long userId) {
        String sql = """
            SELECT a.* FROM public.ekyc_private_assets a
            JOIN public.ekyc_profiles p ON a.profile_id = p.profile_id
            WHERE p.user_id = ? AND p.record_type = 'ekyc_profiles'
              AND p.state = 'VERIFIED'
              AND (p.reference_expires_at IS NULL OR p.reference_expires_at > CURRENT_TIMESTAMP)
              AND a.asset_type IN ('REFERENCE', 'LIVE_FRAME', 'CARD_FRONT')
              AND a.deleted_at IS NULL
              AND (a.expires_at IS NULL OR a.expires_at > CURRENT_TIMESTAMP)
            ORDER BY CASE a.asset_type WHEN 'REFERENCE' THEN 1 WHEN 'LIVE_FRAME' THEN 2 ELSE 3 END,
                     p.revision_no DESC, a.asset_id DESC LIMIT 1
            """;
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, this::mapAsset, userId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /** Tất cả ảnh mẫu sinh trắc học khả dụng: ưu tiên REFERENCE, rồi LIVE_FRAME, rồi CARD_FRONT. */
    public List<AssetRecord> findApprovedReferenceAssets(long userId) {
        String sql = """
            SELECT a.* FROM public.ekyc_private_assets a
            JOIN public.ekyc_profiles p ON a.profile_id = p.profile_id
            WHERE p.user_id = ? AND p.record_type = 'ekyc_profiles'
              AND p.state = 'VERIFIED'
              AND (p.reference_expires_at IS NULL OR p.reference_expires_at > CURRENT_TIMESTAMP)
              AND a.asset_type IN ('REFERENCE', 'LIVE_FRAME', 'CARD_FRONT')
              AND a.deleted_at IS NULL
              AND (a.expires_at IS NULL OR a.expires_at > CURRENT_TIMESTAMP)
            ORDER BY CASE a.asset_type WHEN 'REFERENCE' THEN 1 WHEN 'LIVE_FRAME' THEN 2 ELSE 3 END,
                     p.revision_no DESC, a.asset_id DESC
            """;
        return jdbc.query(sql, this::mapAsset, userId);
    }

    public void createQuickAuthSession(
            UUID sessionId,
            long userId,
            long profileId,
            long referenceAssetId,
            Instant expiresAt,
            String commandKey
    ) {
        String sql = """
            INSERT INTO og_compat.quick_auth_sessions(session_id, user_id, profile_id, reference_asset_id, nonmatch_count, expires_at, command_key)
            VALUES (?, ?, ?, ?, 0, ?, ?)
            """;
        jdbc.update(sql, sessionId, userId, profileId, referenceAssetId, Timestamp.from(expiresAt), commandKey);
    }

    public Optional<QuickAuthSessionRecord> findQuickAuthSession(UUID sessionId) {
        String sql = "SELECT * FROM og_compat.quick_auth_sessions WHERE session_id = ?";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, this::mapQuickAuthSession, sessionId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void recordQuickAuthAttempt(
            UUID attemptId,
            UUID sessionId,
            String result,
            Instant attemptedAt,
            String commandKey
    ) {
        String sql = """
            INSERT INTO og_compat.quick_auth_attempts(attempt_id, session_id, result, attempted_at, command_key)
            VALUES (?, ?, ?, ?, ?)
            """;
        jdbc.update(sql, attemptId, sessionId, result, Timestamp.from(attemptedAt), commandKey);
    }

    public void createQuickAuthResult(
            UUID resultId,
            UUID sessionId,
            long userId,
            long profileId,
            long referenceAssetId,
            Instant matchedAt,
            Instant expiresAt,
            String commandKey
    ) {
        String sql = """
            INSERT INTO og_compat.quick_auth_results(result_id, user_id, profile_id, reference_asset_id, matched_at, expires_at, command_key, session_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        jdbc.update(sql, resultId, userId, profileId, referenceAssetId, Timestamp.from(matchedAt), Timestamp.from(expiresAt), commandKey, sessionId);
    }

    public Optional<QuickAuthResultRecord> findQuickAuthResultById(UUID resultId) {
        String sql = "SELECT * FROM og_compat.quick_auth_results WHERE result_id = ?";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, this::mapQuickAuthResult, resultId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private Optional<ProfileRecord> queryProfile(String sql, Object... params) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, this::mapProfile, params));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private ProfileRecord mapProfile(ResultSet rs, int rowNum) throws SQLException {
        return new ProfileRecord(
                rs.getLong("profile_id"),
                rs.getLong("user_id"),
                rs.getInt("revision_no"),
                rs.getString("state"),
                rs.getString("submitted_data"),
                rs.getString("ai_summary"),
                rs.getInt("nonmatch_count"),
                instant(rs, "last_nonmatch_at"),
                instant(rs, "created_at"),
                instant(rs, "submitted_at"),
                instant(rs, "verified_at"),
                instant(rs, "reference_expires_at"),
                rs.getLong("version"),
                rs.getString("document_digest"),
                rs.getString("rejection_reason"),
                instant(rs, "reviewed_at"),
                rs.getObject("verified_by") != null ? rs.getLong("verified_by") : null
        );
    }

    private AssetRecord mapAsset(ResultSet rs, int rowNum) throws SQLException {
        return new AssetRecord(
                rs.getLong("asset_id"),
                rs.getLong("profile_id"),
                rs.getString("asset_type"),
                rs.getString("object_key"),
                rs.getString("content_digest"),
                instant(rs, "captured_at"),
                instant(rs, "expires_at"),
                instant(rs, "deleted_at")
        );
    }

    private static Instant instant(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts != null ? ts.toInstant() : null;
    }

    private QuickAuthSessionRecord mapQuickAuthSession(ResultSet rs, int rowNum) throws SQLException {
        return new QuickAuthSessionRecord(
                getUuid(rs, "session_id"),
                rs.getLong("user_id"),
                rs.getLong("profile_id"),
                rs.getLong("reference_asset_id"),
                rs.getInt("nonmatch_count"),
                instant(rs, "last_nonmatch_at"),
                instant(rs, "expires_at"),
                rs.getString("command_key")
        );
    }

    private QuickAuthResultRecord mapQuickAuthResult(ResultSet rs, int rowNum) throws SQLException {
        return new QuickAuthResultRecord(
                getUuid(rs, "result_id"),
                rs.getLong("user_id"),
                rs.getLong("profile_id"),
                rs.getLong("reference_asset_id"),
                instant(rs, "matched_at"),
                instant(rs, "expires_at"),
                rs.getString("command_key"),
                getUuid(rs, "session_id")
        );
    }

    private static UUID getUuid(ResultSet rs, String col) throws SQLException {
        Object obj = rs.getObject(col);
        if (obj == null) return null;
        if (obj instanceof UUID u) return u;
        return UUID.fromString(obj.toString());
    }

    public void syncSellerBankSnapshot(long userId, String bankName, String accountNumber, String accountHolder, Instant now) {
        try {
            String snapshotJson = json.writeValueAsString(Map.of(
                    "bank_name", bankName,
                    "account_number", accountNumber,
                    "account_holder", accountHolder
            ));
            jdbc.update("""
                UPDATE public.seller_profiles
                SET bank_snapshot = ?::jsonb,
                    updated_at = ?
                WHERE user_id = ? AND record_type = 'seller_profiles'
                """, snapshotJson, Timestamp.from(now), userId);
        } catch (Exception ignored) {
        }
    }
}
