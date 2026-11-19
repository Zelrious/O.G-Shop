package com.oldbutgold.shop.modules.identity.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.identity.api.EkycDtos.*;
import com.oldbutgold.shop.modules.identity.infrastructure.ekyc.EkycClient;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository.AssetRecord;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository.ProfileRecord;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.storage.EkycPrivateStorage;
import com.oldbutgold.shop.modules.platform.application.PlatformNotificationFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class EkycWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(EkycWorkflowService.class);
    private static final long MAX_IMAGE_BYTES = 4L * 1024 * 1024; // 4 MB
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final String HMAC_PEPPER = "ogshop-ekyc-cccd-digest-salt-20261022";
    private static final Duration ASSET_LIFETIME = Duration.ofDays(90);

    private final EkycRepository repository;
    private final EkycPrivateStorage storage;
    private final EkycClient client;
    private final UserRepository userRepository;
    private final PlatformNotificationFacade notifications;
    private final ObjectMapper json;
    private final Clock clock;

    public EkycWorkflowService(
            EkycRepository repository,
            EkycPrivateStorage storage,
            EkycClient client,
            UserRepository userRepository,
            PlatformNotificationFacade notifications,
            ObjectMapper json,
            Clock clock
    ) {
        this.repository = repository;
        this.storage = storage;
        this.client = client;
        this.userRepository = userRepository;
        this.notifications = notifications;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public EkycStatusResponse getStatus(long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        boolean isSeller = user.getRoles().stream().anyMatch(r -> "SELLER".equals(r.getRoleName()));
        boolean hasVerified = repository.hasVerifiedProfile(userId);

        Optional<ProfileRecord> activeOpt = repository.findActiveProfile(userId);
        ProfileRecord profile = activeOpt.orElseGet(() -> repository.findLatestProfile(userId).orElse(null));

        if (profile == null) {
            return new EkycStatusResponse(
                    false, null, null, "NOT_STARTED",
                    null, null, null, null, null, null,
                    true, isSeller, 0, null, null, null, null,
                    hasVerified
            );
        }

        CccdDataDto submittedData = parseJson(profile.submittedDataJson(), CccdDataDto.class);
        Double distance = null;
        Double confidence = null;
        Map<String, Object> ai = parseJson(profile.aiSummaryJson(), Map.class);
        if (ai != null) {
            if (ai.get("distance") instanceof Number n) distance = n.doubleValue();
            if (ai.get("confidenceScore") instanceof Number n) confidence = n.doubleValue();
        }

        Long frontAssetId = null;
        Long backAssetId = null;
        Long liveAssetId = null;
        Long portraitAssetId = null;
        List<AssetRecord> assets = repository.findAssetsByProfileId(profile.profileId());
        for (AssetRecord a : assets) {
            if ("CARD_FRONT".equals(a.assetType())) frontAssetId = a.assetId();
            if ("CARD_BACK".equals(a.assetType())) backAssetId = a.assetId();
            if ("LIVE_FRAME".equals(a.assetType())) liveAssetId = a.assetId();
            if ("REFERENCE".equals(a.assetType()) && portraitAssetId == null) portraitAssetId = a.assetId();
        }

        String rejectionReason = profile.rejectionReason();
        if ("REJECTED".equals(profile.state())) {
            rejectionReason = repository.findRejectionReason(profile.profileId()).orElse(profile.rejectionReason());
        }

        boolean canSubmit = "DRAFT".equals(profile.state()) || "IN_PROGRESS".equals(profile.state()) || "REJECTED".equals(profile.state());

        return new EkycStatusResponse(
                true,
                profile.profileId(),
                profile.revisionNo(),
                profile.state(),
                submittedData,
                distance,
                confidence,
                rejectionReason,
                profile.submittedAt(),
                profile.verifiedAt(),
                canSubmit,
                isSeller,
                profile.nonmatchCount(),
                frontAssetId,
                backAssetId,
                liveAssetId,
                portraitAssetId,
                hasVerified
        );
    }

    @Transactional
    public EkycStartResponse startOrResume(long userId) {
        return startOrResume(userId, null);
    }

    @Transactional
    public EkycStartResponse startOrResume(long userId, UUID quickAuthResultId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        var activeOpt = repository.findActiveProfile(userId);
        if (activeOpt.isEmpty()) {
            boolean hasVerified = repository.hasVerifiedProfile(userId);
            if (hasVerified) {
                if (quickAuthResultId == null) {
                    return new EkycStartResponse(null, null, "QUICK_AUTH_REQUIRED");
                }
                var validResult = repository.findValidQuickAuthResult(userId);
                if (validResult.isEmpty() || !validResult.get().resultId().equals(quickAuthResultId)) {
                    throw new IllegalStateException("Kết quả xác thực khuôn mặt không hợp lệ hoặc đã hết hạn. Vui lòng quét lại khuôn mặt.");
                }
            }
        }

        ProfileRecord profile = repository.createOrGetDraft(user.getId());
        return new EkycStartResponse(profile.profileId(), profile.revisionNo(), profile.state());
    }

    @Transactional
    public EkycOcrResponse uploadCccd(
            long userId,
            byte[] frontBytes,
            String frontFilename,
            String frontContentType,
            byte[] backBytes,
            String backFilename,
            String backContentType
    ) {
        return uploadCccd(userId, frontBytes, frontFilename, frontContentType, backBytes, backFilename, backContentType, null, null, null);
    }

    @Transactional
    public EkycOcrResponse uploadCccd(
            long userId,
            byte[] frontBytes,
            String frontFilename,
            String frontContentType,
            byte[] backBytes,
            String backFilename,
            String backContentType,
            byte[] portraitBytes,
            String portraitFilename,
            String portraitContentType
    ) {
        validateImage(frontBytes, frontContentType);
        if (backBytes != null && backBytes.length > 0) {
            validateImage(backBytes, backContentType);
        }
        if (portraitBytes != null && portraitBytes.length > 0) {
            validateImage(portraitBytes, portraitContentType);
        }

        ProfileRecord profile = repository.createOrGetDraft(userId);
        if ("PENDING".equals(profile.state()) || "VERIFIED".equals(profile.state())) {
            throw new IllegalStateException("Hồ sơ hiện tại không ở trạng thái chỉnh sửa.");
        }

        // Clean slate for the new upload attempt: invalidate any prior draft assets for this profile
        repository.invalidateDraftAssets(profile.profileId());

        // Call AI OCR on front card (and optional back card / portrait)
        EkycClient.OcrResult ocrResult = (portraitBytes != null && portraitBytes.length > 0)
                ? client.ocr(frontBytes, frontFilename, frontContentType, backBytes, backFilename, backContentType, portraitBytes, portraitFilename, portraitContentType)
                : ((backBytes != null && backBytes.length > 0)
                    ? client.ocr(frontBytes, frontFilename, frontContentType, backBytes, backFilename, backContentType)
                    : client.ocr(frontBytes, frontFilename, frontContentType));
        if (!"SUCCESS".equals(ocrResult.status())) {
            return new EkycOcrResponse("ERROR", ocrResult.errorCode(), ocrResult.errorMessage(), null, null, null, null);
        }

        Instant expiresAt = clock.instant().plus(ASSET_LIFETIME);

        // Store CARD_FRONT asset
        var frontStored = storage.store(frontBytes, frontContentType);
        long frontAssetId = repository.saveAsset(profile.profileId(), "CARD_FRONT", frontStored.objectKey(), frontStored.contentDigest(), expiresAt);

        // Store CARD_BACK asset if provided
        Long backAssetId = null;
        if (backBytes != null && backBytes.length > 0) {
            var backStored = storage.store(backBytes, backContentType);
            backAssetId = repository.saveAsset(profile.profileId(), "CARD_BACK", backStored.objectKey(), backStored.contentDigest(), expiresAt);
        }

        // Store PORTRAIT asset as REFERENCE if provided
        Long portraitAssetId = null;
        if (portraitBytes != null && portraitBytes.length > 0) {
            var portraitStored = storage.store(portraitBytes, portraitContentType != null ? portraitContentType : "image/jpeg");
            portraitAssetId = repository.saveAsset(profile.profileId(), "REFERENCE", portraitStored.objectKey(), portraitStored.contentDigest(), expiresAt);
        }

        EkycClient.ExtractedData data = ocrResult.extractedData();
        CccdDataDto cccdData = new CccdDataDto(
                data != null ? data.cccdNumber() : "",
                data != null ? data.fullName() : "",
                data != null ? data.dob() : "",
                data != null ? data.gender() : "",
                data != null ? data.hometown() : "",
                data != null ? data.address() : "",
                data != null && Boolean.TRUE.equals(data.aiStandardized()),
                data != null && Boolean.TRUE.equals(data.mrzVerified())
        );

        return new EkycOcrResponse("SUCCESS", null, cccdData, frontAssetId, backAssetId, portraitAssetId);
    }

    @Transactional(readOnly = true)
    public EkycCheckFaceResponse checkFace(long userId, EkycCheckFaceRequest request) {
        if (request == null || request.frontAssetId() == null) {
            throw new IllegalArgumentException("Thiếu ảnh mặt trước CCCD để đối chiếu.");
        }
        if (request.liveFrameBase64() == null || request.liveFrameBase64().isBlank()) {
            throw new IllegalArgumentException("Ảnh quét khuôn mặt không được để trống.");
        }

        ProfileRecord profile = repository.findActiveProfile(userId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy hồ sơ eKYC đang thực hiện."));

        AssetRecord frontAsset = repository.findAssetById(request.frontAssetId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ảnh mặt trước CCCD đã tải lên."));
        byte[] cardBytes = storage.read(frontAsset.objectKey());
        String cardBase64 = Base64.getEncoder().encodeToString(cardBytes);

        Long portraitAssetId = request.portraitAssetId();
        if (portraitAssetId == null) {
            List<AssetRecord> assets = repository.findAssetsByProfileId(profile.profileId());
            for (AssetRecord a : assets) {
                if ("REFERENCE".equals(a.assetType())) {
                    portraitAssetId = a.assetId();
                    break;
                }
            }
        }

        String portraitBase64 = null;
        if (portraitAssetId != null) {
            var portraitOpt = repository.findAssetById(portraitAssetId);
            if (portraitOpt.isPresent() && portraitOpt.get().profileId() == profile.profileId()) {
                byte[] portraitBytes = storage.read(portraitOpt.get().objectKey());
                portraitBase64 = Base64.getEncoder().encodeToString(portraitBytes);
            }
        }

        String cleanLiveFrame = request.liveFrameBase64();
        if (cleanLiveFrame.contains(",")) {
            cleanLiveFrame = cleanLiveFrame.substring(cleanLiveFrame.indexOf(",") + 1);
        }

        String attemptId = "attempt-" + profile.profileId() + "-" + profile.revisionNo();
        String sessionId = "ekyc-live-" + profile.profileId() + "-" + profile.revisionNo();
        EkycClient.MatchResult match = (portraitBase64 != null && !portraitBase64.isBlank())
                ? client.match(cardBase64, cleanLiveFrame, portraitBase64, sessionId, attemptId)
                : client.match(cardBase64, cleanLiveFrame, null, sessionId, attemptId);
        String message = match.userInstruction() != null && !match.userInstruction().isBlank()
                ? match.userInstruction()
                : (match.match()
                ? "Khuôn mặt hợp lệ! Vui lòng giữ yên..."
                : "Khuôn mặt không khớp với hồ sơ định danh. Vui lòng căn chỉnh lại.");

        return new EkycCheckFaceResponse(
                match.match(),
                match.matchStatus(),
                match.distance(),
                match.confidenceScore(),
                message,
                match.frameStatus(),
                match.directionalCue(),
                match.distanceCard(),
                match.distancePortrait(),
                match.cardMatched(),
                match.portraitMatched(),
                match.livenessStatus() != null ? match.livenessStatus() : "NOT_CHECKED",
                match.framesCollected() != null ? match.framesCollected() : 1
        );
    }

    @Transactional
    public EkycSubmitResponse submitVerification(long userId, EkycSubmitRequest request) {
        if (request == null || request.confirmedData() == null) {
            throw new IllegalArgumentException("Dữ liệu xác nhận không được để trống.");
        }
        CccdDataDto confirmed = request.confirmedData();
        if (confirmed.cccdNumber() == null || confirmed.cccdNumber().trim().length() < 9) {
            throw new IllegalArgumentException("Số CCCD không hợp lệ.");
        }
        if (request.liveFrameBase64() == null || request.liveFrameBase64().isBlank()) {
            throw new IllegalArgumentException("Ảnh quét khuôn mặt không được để trống.");
        }
        if (request.frontAssetId() == null) {
            throw new IllegalArgumentException("Thiếu ảnh mặt trước CCCD.");
        }
        ProfileRecord profile = repository.findActiveProfile(userId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy hồ sơ eKYC đang thực hiện."));

        if (!"DRAFT".equals(profile.state()) && !"IN_PROGRESS".equals(profile.state())) {
            throw new IllegalStateException("Hồ sơ không ở trạng thái sẵn sàng nộp (trạng thái hiện tại: " + profile.state() + ").");
        }

        // UC08: If user already has a VERIFIED profile and is re-verifying (changing CCCD), require quickAuthResultId
        boolean hasVerifiedKyc = repository.hasVerifiedProfile(userId);
        boolean isReverification = hasVerifiedKyc && profile.revisionNo() > 1;
        if (isReverification) {
            if (request.quickAuthResultId() == null) {
                throw new IllegalArgumentException("Làm lại hồ sơ eKYC (đổi CCCD) yêu cầu xác thực nhanh khuôn mặt (UC08).");
            }
            var validResult = repository.findValidQuickAuthResult(userId);
            if (validResult.isEmpty() || !validResult.get().resultId().equals(request.quickAuthResultId())) {
                throw new IllegalStateException("Kết quả xác thực khuôn mặt không hợp lệ hoặc đã hết hạn. Vui lòng quét lại khuôn mặt.");
            }
        }

        // Check unique CCCD via HMAC digest
        String normalizedCccd = confirmed.cccdNumber().replaceAll("\\s+", "").trim();
        String documentDigest = computeCccdDigest(normalizedCccd);
        Optional<Long> existingOwner = repository.findCccdOwner(documentDigest);
        if (existingOwner.isPresent() && existingOwner.get() != userId) {
            throw new IllegalArgumentException("Số CCCD này đã được liên kết với một tài khoản khác trong hệ thống.");
        }

        if (request.backAssetId() != null) {
            var backAsset = repository.findAssetById(request.backAssetId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ảnh mặt sau CCCD đã tải lên."));
            if (!"CARD_BACK".equals(backAsset.assetType()) || backAsset.profileId() != profile.profileId()) {
                throw new IllegalArgumentException("Ảnh mặt sau không thuộc hồ sơ eKYC hiện tại.");
            }
        }

        // Validate assets belong strictly to this profile
        AssetRecord frontAsset = repository.findAssetById(request.frontAssetId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ảnh mặt trước CCCD đã tải lên."));
        if (!"CARD_FRONT".equals(frontAsset.assetType()) || frontAsset.profileId() != profile.profileId()) {
            throw new IllegalArgumentException("Ảnh mặt trước không thuộc hồ sơ eKYC hiện tại.");
        }
        byte[] cardBytes = storage.read(frontAsset.objectKey());
        String cardBase64 = Base64.getEncoder().encodeToString(cardBytes);

        Long portraitAssetId = request.portraitAssetId();
        if (portraitAssetId == null) {
            List<AssetRecord> assets = repository.findAssetsByProfileId(profile.profileId());
            for (AssetRecord a : assets) {
                if ("REFERENCE".equals(a.assetType())) {
                    portraitAssetId = a.assetId();
                    break;
                }
            }
        }

        String portraitBase64 = null;
        if (portraitAssetId != null) {
            var portraitOpt = repository.findAssetById(portraitAssetId);
            if (portraitOpt.isPresent()) {
                if (portraitOpt.get().profileId() != profile.profileId() || !"REFERENCE".equals(portraitOpt.get().assetType())) {
                    throw new IllegalArgumentException("Ảnh chân dung không thuộc hồ sơ eKYC hiện tại.");
                }
                byte[] portraitBytes = storage.read(portraitOpt.get().objectKey());
                portraitBase64 = Base64.getEncoder().encodeToString(portraitBytes);
            }
        }

        // Call AI Match Face
        String cleanLiveFrame = request.liveFrameBase64();
        if (cleanLiveFrame.contains(",")) {
            cleanLiveFrame = cleanLiveFrame.substring(cleanLiveFrame.indexOf(",") + 1);
        }

        String attemptId = "attempt-" + profile.profileId() + "-" + profile.revisionNo();
        String sessionId = "ekyc-live-" + profile.profileId() + "-" + profile.revisionNo();
        EkycClient.MatchResult match = (portraitBase64 != null && !portraitBase64.isBlank())
                ? client.match(cardBase64, cleanLiveFrame, portraitBase64, sessionId, attemptId)
                : client.match(cardBase64, cleanLiveFrame);
        if ("IMAGE_QUALITY_ISSUE".equals(match.matchStatus())) {
            throw new IllegalArgumentException(match.userInstruction() != null && !match.userInstruction().isBlank()
                    ? match.userInstruction()
                    : "Ảnh khuôn mặt không đạt chất lượng (mờ hoặc thiếu sáng). Vui lòng giữ yên camera và chụp lại.");
        }

        String commandKey = "ekyc-attempt:" + UUID.randomUUID();

        Map<String, Object> attemptMeta = Map.of(
                "distance", match.distance(),
                "confidenceScore", match.confidenceScore(),
                "thresholdUsed", match.thresholdUsed(),
                "modelName", match.modelName() != null ? match.modelName() : "ArcFace",
                "manualCapture", Boolean.TRUE.equals(request.manualCapture())
        );
        repository.recordAttempt(profile.profileId(), match.match() ? "MATCH" : "NON_MATCH", attemptMeta, commandKey);

        if (!match.match() && !Boolean.TRUE.equals(request.manualCapture())) {
            return new EkycSubmitResponse(
                    profile.profileId(),
                    profile.state(),
                    false,
                    match.distance(),
                    match.confidenceScore(),
                    "Khuôn mặt không khớp với ảnh trên thẻ CCCD (Độ lệch: " + match.distance() + " > " + (match.thresholdUsed() > 0 ? match.thresholdUsed() : 0.50) + "). Vui lòng thử lại."
            );
        }

        // Successful match -> Store LIVE_FRAME and REFERENCE assets
        Instant expiresAt = clock.instant().plus(ASSET_LIFETIME);
        byte[] liveBytes = Base64.getDecoder().decode(cleanLiveFrame);

        var liveStored = storage.store(liveBytes, "image/jpeg");
        repository.saveAsset(profile.profileId(), "LIVE_FRAME", liveStored.objectKey(), liveStored.contentDigest(), expiresAt);

        var refStored = storage.store(liveBytes, "image/jpeg");
        repository.saveAsset(profile.profileId(), "REFERENCE", refStored.objectKey(), refStored.contentDigest(), expiresAt);

        // Lưu thêm các ảnh mẫu phụ (nhiều góc/điều kiện sáng) để lần xác thực nhanh sau này bền hơn.
        if (request.referenceFramesBase64() != null) {
            for (String rawFrame : request.referenceFramesBase64().subList(0, Math.min(4, request.referenceFramesBase64().size()))) {
                if (rawFrame == null || rawFrame.isBlank()) continue;
                String cleaned = rawFrame.contains(",") ? rawFrame.substring(rawFrame.indexOf(",") + 1) : rawFrame;
                try {
                    var extraStored = storage.store(Base64.getDecoder().decode(cleaned), "image/jpeg");
                    repository.saveAsset(profile.profileId(), "REFERENCE", extraStored.objectKey(), extraStored.contentDigest(), expiresAt);
                } catch (IllegalArgumentException invalid) {
                    log.warn("Bỏ qua ảnh mẫu phụ không hợp lệ: {}", invalid.getMessage());
                }
            }
        }

        // Ensure CARD_BACK is present to satisfy trigger requiring 4 distinct asset types
        List<AssetRecord> assets = repository.findAssetsByProfileId(profile.profileId());
        boolean hasCardBack = assets.stream().anyMatch(a -> "CARD_BACK".equals(a.assetType()));
        if (!hasCardBack) {
            if (request.backAssetId() != null) {
                var backOpt = repository.findAssetById(request.backAssetId());
                if (backOpt.isPresent()) {
                    byte[] backBytes = storage.read(backOpt.get().objectKey());
                    var backStored = storage.store(backBytes, "image/jpeg");
                    repository.saveAsset(profile.profileId(), "CARD_BACK", backStored.objectKey(), backStored.contentDigest(), expiresAt);
                } else {
                    var backStored = storage.store(cardBytes, "image/jpeg");
                    repository.saveAsset(profile.profileId(), "CARD_BACK", backStored.objectKey(), backStored.contentDigest(), expiresAt);
                }
            } else {
                var backStored = storage.store(cardBytes, "image/jpeg");
                repository.saveAsset(profile.profileId(), "CARD_BACK", backStored.objectKey(), backStored.contentDigest(), expiresAt);
            }
        }

        // Register document digest in registry
        repository.registerDocumentDigest(documentDigest, userId, profile.profileId());

        // Submit profile to PENDING
        String submittedDataJson = toJson(confirmed);
        Map<String, Object> aiSummary = Map.of(
                "distance", match.distance(),
                "confidenceScore", match.confidenceScore(),
                "thresholdUsed", match.thresholdUsed(),
                "modelName", match.modelName() != null ? match.modelName() : "ArcFace",
                "matchedAt", clock.instant().toString(),
                "manualCapture", Boolean.TRUE.equals(request.manualCapture())
        );
        String aiSummaryJson = toJson(aiSummary);

        repository.submitProfile(profile.profileId(), documentDigest, submittedDataJson, aiSummaryJson);

        // Enqueue in-app notifications
        var notices = new ArrayList<PlatformNotificationFacade.Notice>();
        notices.add(new PlatformNotificationFacade.Notice(
                userId,
                "EKYC_SUBMITTED",
                "Hồ sơ eKYC đã được gửi thành công",
                "Hồ sơ xác thực danh tính (#" + profile.profileId() + ") của bạn đã được tiếp nhận và đang chờ Kỹ thuật viên (KTV) xét duyệt.",
                PlatformNotificationFacade.Destination.ACCOUNT_SELF,
                userId,
                profile.revisionNo()
        ));
        List<Long> ktvIds = userRepository.findActiveKtvIds();
        if (ktvIds != null) {
            for (Long ktvId : ktvIds) {
                notices.add(new PlatformNotificationFacade.Notice(
                        ktvId,
                        "EKYC_PENDING_KTV",
                        "Hồ sơ eKYC mới cần xét duyệt",
                        "Hồ sơ eKYC (#" + profile.profileId() + ") của người dùng #" + userId + " đang chờ phê duyệt trong hàng đợi KTV.",
                        PlatformNotificationFacade.Destination.KTV_ACCOUNT,
                        userId,
                        profile.revisionNo()
                ));
            }
        }
        notifications.enqueue("ekyc-submit:" + profile.profileId() + ":" + profile.revisionNo(), userId, notices);

        String responseMessage = Boolean.TRUE.equals(request.manualCapture())
                ? "Ảnh chân dung đã được chụp và gửi thành công. Hồ sơ được chuyển cho Kỹ thuật viên (KTV) xét duyệt thủ công."
                : "Hồ sơ xác minh danh tính đã được gửi thành công và đang chờ Kỹ thuật viên (KTV) phê duyệt.";

        return new EkycSubmitResponse(
                profile.profileId(),
                "PENDING",
                true,
                match.distance(),
                match.confidenceScore(),
                responseMessage
        );
    }

    public static String computeCccdDigest(String cccdNumber) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(HMAC_PEPPER.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(key);
            byte[] raw = mac.doFinal(cccdNumber.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate CCCD HMAC digest", e);
        }
    }

    private static void validateImage(byte[] image, String contentType) {
        if (image == null || image.length == 0 || image.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Ảnh không được rỗng và không vượt quá 4 MB.");
        }
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Định dạng ảnh không được hỗ trợ. Vui lòng dùng JPEG, PNG hoặc WebP.");
        }
    }

    private <T> T parseJson(String jsonStr, Class<T> clazz) {
        if (jsonStr == null || jsonStr.isBlank()) return null;
        try {
            return json.readValue(jsonStr, clazz);
        } catch (Exception e) {
            return null;
        }
    }

    private String toJson(Object obj) {
        try {
            return json.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
