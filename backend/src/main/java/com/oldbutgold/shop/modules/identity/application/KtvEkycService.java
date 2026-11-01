package com.oldbutgold.shop.modules.identity.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.identity.api.EkycDtos.*;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository.AssetRecord;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository.PendingQueueItem;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository.ProfileRecord;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserRepository;
import com.oldbutgold.shop.modules.platform.application.PlatformNotificationFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class KtvEkycService {
    private final EkycRepository repository;
    private final UserRepository userRepository;
    private final PlatformNotificationFacade notifications;
    private final ObjectMapper json;

    public KtvEkycService(
            EkycRepository repository,
            UserRepository userRepository,
            PlatformNotificationFacade notifications,
            ObjectMapper json
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.notifications = notifications;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public KtvEkycPageResponse getQueue(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        List<PendingQueueItem> items = repository.getPendingQueue(safePage, safeSize);
        long totalElements = repository.countPendingQueue();
        int totalPages = (int) Math.ceil((double) totalElements / safeSize);

        List<KtvEkycSummaryDto> dtos = items.stream().map(item -> {
            CccdDataDto submitted = parseJson(item.submittedDataJson(), CccdDataDto.class);
            String maskedCccd = maskCccd(submitted != null ? submitted.cccdNumber() : "");
            Double distance = null;
            Map<String, Object> ai = parseJson(item.aiSummaryJson(), Map.class);
            if (ai != null && ai.get("distance") instanceof Number n) {
                distance = n.doubleValue();
            }
            return new KtvEkycSummaryDto(
                    item.profileId(),
                    item.userId(),
                    item.username(),
                    item.fullName(),
                    item.email(),
                    item.phone(),
                    item.revisionNo(),
                    item.state(),
                    item.submittedAt(),
                    distance,
                    maskedCccd
            );
        }).toList();

        return new KtvEkycPageResponse(dtos, safePage, safeSize, totalElements, totalPages);
    }

    @Transactional(readOnly = true)
    public KtvEkycDetailDto getDetail(long profileId) {
        ProfileRecord profile = repository.findProfileById(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ eKYC #" + profileId));
        UserEntity user = userRepository.findById(profile.userId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin người dùng."));

        CccdDataDto submittedData = parseJson(profile.submittedDataJson(), CccdDataDto.class);
        Double distance = null;
        Double confidence = null;
        Double thresholdUsed = null;
        String modelName = "ArcFace";
        Map<String, Object> ai = parseJson(profile.aiSummaryJson(), Map.class);
        if (ai != null) {
            if (ai.get("distance") instanceof Number n) distance = n.doubleValue();
            if (ai.get("confidenceScore") instanceof Number n) confidence = n.doubleValue();
            if (ai.get("thresholdUsed") instanceof Number n) thresholdUsed = n.doubleValue();
            if (ai.get("modelName") instanceof String s) modelName = s;
        }

        Long frontAssetId = null;
        Long backAssetId = null;
        Long liveAssetId = null;
        Long portraitAssetId = null;
        List<AssetRecord> assets = repository.findAssetsByProfileId(profileId);
        for (AssetRecord a : assets) {
            if ("CARD_FRONT".equals(a.assetType())) frontAssetId = a.assetId();
            if ("CARD_BACK".equals(a.assetType())) backAssetId = a.assetId();
            if ("LIVE_FRAME".equals(a.assetType())) liveAssetId = a.assetId();
            if ("REFERENCE".equals(a.assetType()) && portraitAssetId == null) portraitAssetId = a.assetId();
        }

        // Get reference asset from previous verified profile (for comparison)
        Long referenceAssetId = null;
        String referenceAssetUrl = null;
        if (profile.revisionNo() > 1) {
            var refAsset = repository.findApprovedReferenceAsset(profile.userId());
            if (refAsset.isPresent() && refAsset.get().profileId() != profileId) {
                referenceAssetId = refAsset.get().assetId();
                referenceAssetUrl = "/api/v1/ekyc/assets/" + referenceAssetId;
            }
        }

        return new KtvEkycDetailDto(
                profile.profileId(),
                profile.userId(),
                user.getEmail(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                profile.revisionNo(),
                profile.state(),
                profile.submittedAt(),
                submittedData,
                submittedData, // ocrData
                distance,
                confidence,
                thresholdUsed,
                modelName,
                frontAssetId,
                backAssetId,
                liveAssetId,
                portraitAssetId,
                referenceAssetId,
                referenceAssetUrl,
                profile.rejectionReason(),
                profile.reviewedAt(),
                profile.verifiedBy()
        );
    }

    @Transactional
    public KtvEkycDecisionResponse approve(long profileId, long ktvUserId, String note) {
        ProfileRecord profile = repository.findProfileByIdForUpdate(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ eKYC #" + profileId));

        if (!"PENDING".equals(profile.state())) {
            throw new IllegalStateException("Hồ sơ eKYC #" + profileId + " đã được xử lý bởi một Kỹ thuật viên khác trước đó (trạng thái: " + profile.state() + "). Vui lòng làm mới danh sách.");
        }

        validateKtv(ktvUserId);

        String commandKey = "ekyc-decision:" + UUID.randomUUID();
        repository.recordDecision(profileId, ktvUserId, "VERIFIED", note != null ? note : "Đã đối chiếu thông tin hợp lệ", commandKey);
        repository.grantSellerRole(profile.userId(), ktvUserId, profileId);

        // Enqueue in-app notification to user
        notifications.enqueue("ekyc-approve:" + profileId + ":" + profile.revisionNo(), profile.userId(), List.of(
                new PlatformNotificationFacade.Notice(
                        profile.userId(),
                        "EKYC_APPROVED",
                        "Hồ sơ eKYC đã được phê duyệt",
                        "Chúc mừng! Hồ sơ xác thực danh tính của bạn đã được Kỹ thuật viên phê duyệt. Bạn đã có đầy đủ quyền Người bán (SELLER).",
                        PlatformNotificationFacade.Destination.ACCOUNT_SELF,
                        profile.userId(),
                        profile.revisionNo()
                )
        ));

        return new KtvEkycDecisionResponse(profileId, "VERIFIED", "Đã phê duyệt hồ sơ eKYC và kích hoạt quyền Người bán thành công.");
    }

    @Transactional
    public KtvEkycDecisionResponse reject(long profileId, long ktvUserId, String reason) {
        if (reason == null || reason.trim().isBlank()) {
            throw new IllegalArgumentException("Bắt buộc phải nhập lý do khi từ chối hồ sơ eKYC.");
        }

        ProfileRecord profile = repository.findProfileByIdForUpdate(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ eKYC #" + profileId));

        if (!"PENDING".equals(profile.state())) {
            throw new IllegalStateException("Hồ sơ eKYC #" + profileId + " đã được xử lý bởi một Kỹ thuật viên khác trước đó (trạng thái: " + profile.state() + "). Vui lòng làm mới danh sách.");
        }

        validateKtv(ktvUserId);

        String commandKey = "ekyc-decision:" + UUID.randomUUID();
        repository.recordDecision(profileId, ktvUserId, "REJECTED", reason.trim(), commandKey);

        // Enqueue in-app notification to user
        notifications.enqueue("ekyc-reject:" + profileId + ":" + profile.revisionNo(), profile.userId(), List.of(
                new PlatformNotificationFacade.Notice(
                        profile.userId(),
                        "EKYC_REJECTED",
                        "Hồ sơ eKYC chưa được phê duyệt",
                        "Hồ sơ xác thực danh tính của bạn bị từ chối với lý do: " + reason.trim() + ". Vui lòng kiểm tra lại ảnh và nộp lại hồ sơ.",
                        PlatformNotificationFacade.Destination.ACCOUNT_SELF,
                        profile.userId(),
                        profile.revisionNo()
                )
        ));

        return new KtvEkycDecisionResponse(profileId, "REJECTED", "Đã từ chối hồ sơ eKYC.");
    }

    private void validateKtv(long ktvUserId) {
        UserEntity ktv = userRepository.findById(ktvUserId)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản KTV không tồn tại."));
        boolean isKtv = ktv.getRoles().stream().anyMatch(r -> "KTV".equals(r.getRoleName()));
        if (!isKtv) {
            throw new org.springframework.security.access.AccessDeniedException("Chỉ Kỹ thuật viên (KTV) mới có quyền xét duyệt hồ sơ eKYC.");
        }
    }

    private static String maskCccd(String cccd) {
        if (cccd == null || cccd.isBlank()) return "";
        String clean = cccd.trim();
        if (clean.length() <= 6) return clean;
        int len = clean.length();
        return clean.substring(0, Math.min(6, len - 2)) + "****" + clean.substring(len - 2);
    }

    private <T> T parseJson(String jsonStr, Class<T> clazz) {
        if (jsonStr == null || jsonStr.isBlank()) return null;
        try {
            return json.readValue(jsonStr, clazz);
        } catch (Exception e) {
            return null;
        }
    }
}
