package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.identity.api.EkycDtos.*;
import com.oldbutgold.shop.modules.identity.infrastructure.ekyc.EkycClient;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository.AssetRecord;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository.QuickAuthResultRecord;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository.QuickAuthSessionRecord;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.storage.EkycPrivateStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class QuickAuthService {
    private static final Duration SESSION_LIFETIME = Duration.ofMinutes(15);
    private static final Duration RESULT_LIFETIME = Duration.ofMinutes(15);
    private static final int MAX_NONMATCH_ATTEMPTS = 15;
    private static final Duration COOLDOWN_DURATION = Duration.ofSeconds(3);

    private final EkycRepository repository;
    private final UserRepository userRepository;
    private final EkycPrivateStorage storage;
    private final EkycClient client;
    private final Clock clock;

    public QuickAuthService(
            EkycRepository repository,
            UserRepository userRepository,
            EkycPrivateStorage storage,
            EkycClient client,
            Clock clock
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.storage = storage;
        this.client = client;
        this.clock = clock;
    }

    @Transactional
    public QuickAuthInitResponse init(long userId, String purpose) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));

        // Alternative Flow 2a: Check if user already has a valid quick auth result
        Optional<QuickAuthResultRecord> validResult = repository.findValidQuickAuthResult(user.getId());
        if (validResult.isPresent()) {
            QuickAuthResultRecord res = validResult.get();
            return new QuickAuthInitResponse(
                    true,
                    res.sessionId(),
                    res.resultId(),
                    res.expiresAt(),
                    "Đã có kết quả xác thực sinh trắc học hợp lệ."
            );
        }

        // BR-UC08-02 & BR-UC08-03: Must have an approved, active REFERENCE asset
        AssetRecord refAsset = repository.findApprovedReferenceAsset(user.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Tài khoản chưa hoàn tất eKYC hoặc mẫu sinh trắc học đã hết hạn (sau 3 tháng). Vui lòng thực hiện xác thực eKYC trước."
                ));

        Instant now = clock.instant();
        Instant sessionExpiresAt = now.plus(SESSION_LIFETIME);
        if (refAsset.expiresAt() != null && sessionExpiresAt.isAfter(refAsset.expiresAt())) {
            sessionExpiresAt = refAsset.expiresAt();
        }

        UUID sessionId = UUID.randomUUID();
        String commandKey = "quick-auth-session:" + sessionId;

        repository.createQuickAuthSession(
                sessionId,
                user.getId(),
                refAsset.profileId(),
                refAsset.assetId(),
                sessionExpiresAt,
                commandKey
        );

        return new QuickAuthInitResponse(
                false,
                sessionId,
                null,
                sessionExpiresAt,
                "Phiên xác thực đã được khởi tạo. Vui lòng nhìn thẳng vào camera."
        );
    }

    @Transactional
    public QuickAuthVerifyResponse verify(long userId, UUID sessionId, String liveFrameBase64) {
        if (sessionId == null) {
            throw new IllegalArgumentException("Mã phiên xác thực không được để trống.");
        }
        if (liveFrameBase64 == null || liveFrameBase64.isBlank()) {
            throw new IllegalArgumentException("Ảnh camera không được để trống.");
        }

        QuickAuthSessionRecord session = repository.findQuickAuthSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Phiên xác thực không tồn tại."));

        if (session.userId() != userId) {
            throw new IllegalArgumentException("Phiên xác thực không hợp lệ với người dùng hiện tại.");
        }

        Instant now = clock.instant();
        if (session.expiresAt().isBefore(now)) {
            throw new IllegalStateException("Phiên xác thực đã hết hạn. Vui lòng tạo phiên mới.");
        }

        if (session.nonmatchCount() >= MAX_NONMATCH_ATTEMPTS) {
            throw new IllegalStateException("Phiên xác thực đã vượt quá giới hạn 15 lần thử. Vui lòng tạo phiên mới.");
        }

        // BR-UC08-04: Cooldown 3 seconds
        if (session.lastNonmatchAt() != null && now.isBefore(session.lastNonmatchAt().plus(COOLDOWN_DURATION))) {
            throw new IllegalStateException("Vui lòng đợi 3 giây trước khi thực hiện lần thử tiếp theo.");
        }

        AssetRecord refAsset = repository.findAssetById(session.referenceAssetId())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy ảnh tham chiếu eKYC."));

        byte[] refBytes = storage.read(refAsset.objectKey());

        String cleanLiveFrame = liveFrameBase64;
        if (cleanLiveFrame.contains(",")) {
            cleanLiveFrame = cleanLiveFrame.substring(cleanLiveFrame.indexOf(",") + 1);
        }

        // Tối ưu đối chiếu đa khung hình giống quy trình eKYC:
        // Lấy ảnh mặt trước CCCD và ảnh chân dung (REFERENCE / LIVE_FRAME) để đối chiếu kép và kích hoạt Multi-Frame Buffer
        List<AssetRecord> referenceAssets = repository.findApprovedReferenceAssets(userId);
        if (referenceAssets.isEmpty()) {
            referenceAssets = List.of(refAsset);
        }

        AssetRecord cardAsset = referenceAssets.stream()
                .filter(a -> "CARD_FRONT".equals(a.assetType()))
                .findFirst()
                .orElse(null);
        AssetRecord portraitAsset = referenceAssets.stream()
                .filter(a -> "REFERENCE".equals(a.assetType()) || "LIVE_FRAME".equals(a.assetType()))
                .findFirst()
                .orElse(null);

        String cardBase64;
        String portraitBase64 = null;
        AssetRecord bestAsset = refAsset;

        if (cardAsset != null && portraitAsset != null) {
            byte[] cardBytes = cardAsset.assetId() == refAsset.assetId() ? refBytes : storage.read(cardAsset.objectKey());
            byte[] portBytes = portraitAsset.assetId() == refAsset.assetId() ? refBytes : storage.read(portraitAsset.objectKey());
            cardBase64 = Base64.getEncoder().encodeToString(cardBytes);
            portraitBase64 = Base64.getEncoder().encodeToString(portBytes);
            bestAsset = portraitAsset;
        } else if (cardAsset != null) {
            byte[] cardBytes = cardAsset.assetId() == refAsset.assetId() ? refBytes : storage.read(cardAsset.objectKey());
            cardBase64 = Base64.getEncoder().encodeToString(cardBytes);
            bestAsset = cardAsset;
        } else {
            cardBase64 = Base64.getEncoder().encodeToString(refBytes);
            bestAsset = refAsset;
        }

        String clientAttemptId = "quick-auth-" + sessionId;
        EkycClient.MatchResult match = client.match(
                cardBase64,
                cleanLiveFrame,
                portraitBase64,
                sessionId.toString(),
                clientAttemptId
        );

        if (match == null) {
            throw new IllegalStateException("Không so khớp được ảnh mẫu sinh trắc học. Vui lòng thử lại.");
        }

        if ("IMAGE_QUALITY_ISSUE".equals(match.matchStatus())) {
            throw new IllegalArgumentException(
                    match.userInstruction() != null && !match.userInstruction().isBlank()
                            ? match.userInstruction()
                            : "Ảnh không đạt chất lượng (mờ hoặc thiếu sáng). Vui lòng căn chỉnh lại camera."
            );
        }

        UUID attemptId = UUID.randomUUID();
        String attemptCmdKey = "quick-auth-attempt:" + attemptId;

        if (!match.match()) {
            String instruction = match.userInstruction() != null && !match.userInstruction().isBlank()
                    ? match.userInstruction()
                    : "Vui lòng nhìn thẳng và căn chỉnh khuôn mặt vào giữa khung hình.";
            return new QuickAuthVerifyResponse(
                    false,
                    null,
                    match.distance(),
                    match.confidenceScore(),
                    MAX_NONMATCH_ATTEMPTS,
                    instruction,
                    match.frameStatus(),
                    match.directionalCue()
            );
        }

        // Successful match
        repository.recordQuickAuthAttempt(attemptId, sessionId, "MATCH", now, attemptCmdKey);

        UUID resultId = UUID.randomUUID();
        Instant resultExpiresAt = now.plus(RESULT_LIFETIME);
        if (bestAsset.expiresAt() != null && resultExpiresAt.isAfter(bestAsset.expiresAt())) {
            resultExpiresAt = bestAsset.expiresAt();
        }

        String resultCmdKey = "quick-auth-result:" + resultId;
        repository.createQuickAuthResult(
                resultId,
                sessionId,
                userId,
                bestAsset.profileId(),
                bestAsset.assetId(),
                now,
                resultExpiresAt,
                resultCmdKey
        );

        return new QuickAuthVerifyResponse(
                true,
                resultId,
                match.distance(),
                match.confidenceScore(),
                MAX_NONMATCH_ATTEMPTS,
                "Xác thực khuôn mặt thành công!",
                match.frameStatus(),
                match.directionalCue()
        );
    }

    @Transactional(readOnly = true)
    public QuickAuthStatusResponse getStatus(long userId, String purpose) {
        Optional<QuickAuthResultRecord> validResult = repository.findValidQuickAuthResult(userId);
        return validResult.map(res -> new QuickAuthStatusResponse(
                true,
                res.resultId(),
                res.expiresAt(),
                purpose
        )).orElseGet(() -> new QuickAuthStatusResponse(
                false,
                null,
                null,
                purpose
        ));
    }
}
