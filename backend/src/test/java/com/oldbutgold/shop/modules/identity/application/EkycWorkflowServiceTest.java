package com.oldbutgold.shop.modules.identity.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.identity.api.EkycDtos.*;
import com.oldbutgold.shop.modules.identity.infrastructure.ekyc.EkycClient;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository.AssetRecord;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository.ProfileRecord;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RoleEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.storage.EkycPrivateStorage;
import com.oldbutgold.shop.modules.platform.application.PlatformNotificationFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EkycWorkflowServiceTest {
    private final EkycRepository repository = mock(EkycRepository.class);
    private final EkycPrivateStorage storage = mock(EkycPrivateStorage.class);
    private final EkycClient client = mock(EkycClient.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PlatformNotificationFacade notifications = mock(PlatformNotificationFacade.class);
    private final ObjectMapper json = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneOffset.UTC);

    private EkycWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new EkycWorkflowService(repository, storage, client, userRepository, notifications, json, clock);
    }

    @Test
    void getStatusWhenNoProfileReturnsNotStarted() {
        UserEntity user = mock(UserEntity.class);
        when(user.getRoles()).thenReturn(Set.of());
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(repository.findActiveProfile(10L)).thenReturn(Optional.empty());
        when(repository.findLatestProfile(10L)).thenReturn(Optional.empty());

        EkycStatusResponse res = service.getStatus(10L);

        assertThat(res.hasProfile()).isFalse();
        assertThat(res.state()).isEqualTo("NOT_STARTED");
        assertThat(res.canSubmit()).isTrue();
    }

    @Test
    void uploadCccdSucceedsWithFrontImageOnly() {
        byte[] front = new byte[]{1, 2, 3};
        ProfileRecord draft = new ProfileRecord(100L, 10L, 1, "IN_PROGRESS", "{}", "{}", 0, null, Instant.now(), null, null, null, 0, null, null, null, null);
        when(repository.createOrGetDraft(10L)).thenReturn(draft);

        var ocr = new EkycClient.OcrResult("SUCCESS", null, new EkycClient.ExtractedData(
                "001095012345", "NGUYEN VAN A", "01/01/1995", "Nam", "HN", "HN", false, false
        ));
        when(client.ocr(front, "front.jpg", "image/jpeg")).thenReturn(ocr);

        when(storage.store(any(), any())).thenReturn(new EkycPrivateStorage.StoredAsset("ekyc/front.jpg", "digest64", 3));
        when(repository.saveAsset(eq(100L), eq("CARD_FRONT"), anyString(), anyString(), any())).thenReturn(1L);

        EkycOcrResponse res = service.uploadCccd(10L, front, "front.jpg", "image/jpeg", null, null, null);

        assertThat(res.status()).isEqualTo("SUCCESS");
        assertThat(res.ocrData().cccdNumber()).isEqualTo("001095012345");
        assertThat(res.frontAssetId()).isEqualTo(1L);
        assertThat(res.backAssetId()).isNull();
        verify(storage, times(1)).store(any(), any());
    }

    @Test
    void uploadCccdCallsOcrAndStoresPrivateAssets() {
        byte[] front = new byte[]{1, 2, 3};
        byte[] back = new byte[]{4, 5, 6};
        ProfileRecord draft = new ProfileRecord(100L, 10L, 1, "IN_PROGRESS", "{}", "{}", 0, null, Instant.now(), null, null, null, 0, null, null, null, null);
        when(repository.createOrGetDraft(10L)).thenReturn(draft);

        var ocr = new EkycClient.OcrResult("SUCCESS", null, new EkycClient.ExtractedData(
                "001095012345", "NGUYEN VAN A", "01/01/1995", "Nam", "HN", "HN", true, true
        ));
        when(client.ocr(front, "front.jpg", "image/jpeg", back, "back.jpg", "image/jpeg")).thenReturn(ocr);

        when(storage.store(any(), any())).thenReturn(new EkycPrivateStorage.StoredAsset("ekyc/file.jpg", "digest64", 3));
        when(repository.saveAsset(eq(100L), eq("CARD_FRONT"), anyString(), anyString(), any())).thenReturn(1L);
        when(repository.saveAsset(eq(100L), eq("CARD_BACK"), anyString(), anyString(), any())).thenReturn(2L);

        EkycOcrResponse res = service.uploadCccd(10L, front, "front.jpg", "image/jpeg", back, "back.jpg", "image/jpeg");

        assertThat(res.status()).isEqualTo("SUCCESS");
        assertThat(res.ocrData().mrzVerified()).isTrue();
        assertThat(res.ocrData().cccdNumber()).isEqualTo("001095012345");
        assertThat(res.frontAssetId()).isEqualTo(1L);
        assertThat(res.backAssetId()).isEqualTo(2L);
        verify(storage, times(2)).store(any(), any());
    }

    @Test
    void submitVerificationRejectsWhenBackAssetDoesNotBelongToProfile() {
        CccdDataDto data = new CccdDataDto("001095012345", "NGUYEN VAN A", "01/01/1995", "Nam", "HN", "HN");
        EkycSubmitRequest alienBack = new EkycSubmitRequest(data, "base64live", 1L, 99L);
        ProfileRecord draft = new ProfileRecord(100L, 10L, 1, "IN_PROGRESS", "{}", "{}", 0, null, Instant.now(), null, null, null, 0, null, null, null, null);
        when(repository.findActiveProfile(10L)).thenReturn(Optional.of(draft));
        when(repository.findAssetById(99L)).thenReturn(Optional.of(
                new AssetRecord(99L, 200L, "CARD_BACK", "ekyc/other.jpg", "digest-other", Instant.now(), Instant.now().plusSeconds(1000), null)));

        assertThatThrownBy(() -> service.submitVerification(10L, alienBack))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không thuộc hồ sơ eKYC hiện tại");
    }

    @Test
    void submitVerificationRejectsWhenCccdBelongsToAnotherUser() {
        CccdDataDto data = new CccdDataDto("001095012345", "NGUYEN VAN A", "01/01/1995", "Nam", "HN", "HN", false, true);
        EkycSubmitRequest req = new EkycSubmitRequest(data, "base64live", 1L, 2L);

        ProfileRecord draft = new ProfileRecord(100L, 10L, 1, "IN_PROGRESS", "{}", "{}", 0, null, Instant.now(), null, null, null, 0, null, null, null, null);
        when(repository.findActiveProfile(10L)).thenReturn(Optional.of(draft));

        String digest = EkycWorkflowService.computeCccdDigest("001095012345");
        when(repository.findActiveProfile(10L)).thenReturn(Optional.of(draft));
        when(repository.findCccdOwner(digest)).thenReturn(Optional.of(99L)); // Another user
        when(repository.findAssetById(2L)).thenReturn(Optional.of(
                new AssetRecord(2L, 100L, "CARD_BACK", "ekyc/back.jpg", "digest-back", Instant.now(), Instant.now().plusSeconds(1000), null)));

        assertThatThrownBy(() -> service.submitVerification(10L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đã được liên kết với một tài khoản khác");
    }

    @Test
    void submitVerificationWhenFaceDoesNotMatch() {
        CccdDataDto data = new CccdDataDto("001095012345", "NGUYEN VAN A", "01/01/1995", "Nam", "HN", "HN", false, true);
        EkycSubmitRequest req = new EkycSubmitRequest(data, "base64live", 1L, 2L);

        ProfileRecord draft = new ProfileRecord(100L, 10L, 1, "IN_PROGRESS", "{}", "{}", 0, null, Instant.now(), null, null, null, 0, null, null, null, null);
        when(repository.findActiveProfile(10L)).thenReturn(Optional.of(draft));

        String digest = EkycWorkflowService.computeCccdDigest("001095012345");
        when(repository.findCccdOwner(digest)).thenReturn(Optional.empty());

        AssetRecord front = new AssetRecord(1L, 100L, "CARD_FRONT", "ekyc/front.jpg", "digest", Instant.now(), Instant.now().plusSeconds(1000), null);
        AssetRecord back = new AssetRecord(2L, 100L, "CARD_BACK", "ekyc/back.jpg", "digest-back", Instant.now(), Instant.now().plusSeconds(1000), null);
        when(repository.findAssetById(1L)).thenReturn(Optional.of(front));
        when(repository.findAssetById(2L)).thenReturn(Optional.of(back));
        when(storage.read("ekyc/front.jpg")).thenReturn(new byte[]{1, 2, 3});

        var match = new EkycClient.MatchResult("NOT_MATCHED", false, 0.82, 0.0, 0.50, "Khuôn mặt không khớp", "ArcFace", "1.0", false);
        when(client.match(anyString(), anyString())).thenReturn(match);

        when(repository.findAssetById(2L)).thenReturn(Optional.of(
                new AssetRecord(2L, 100L, "CARD_BACK", "ekyc/back.jpg", "digest-back", Instant.now(), Instant.now().plusSeconds(1000), null)));
        EkycSubmitResponse res = service.submitVerification(10L, req);

        assertThat(res.isMatch()).isFalse();
        assertThat(res.distance()).isEqualTo(0.82);
        assertThat(res.message()).contains("Khuôn mặt không khớp");
        verify(repository).recordAttempt(eq(100L), eq("NON_MATCH"), anyMap(), anyString());
        verify(repository, never()).submitProfile(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void submitVerificationWhenFaceMatchesSuccessfully() {
        CccdDataDto data = new CccdDataDto("001095012345", "NGUYEN VAN A", "01/01/1995", "Nam", "HN", "HN", false, true);
        String liveBase64 = Base64.getEncoder().encodeToString(new byte[]{4, 5, 6});
        EkycSubmitRequest req = new EkycSubmitRequest(data, liveBase64, 1L, 2L);

        ProfileRecord draft = new ProfileRecord(100L, 10L, 1, "IN_PROGRESS", "{}", "{}", 0, null, Instant.now(), null, null, null, 0, null, null, null, null);
        when(repository.findActiveProfile(10L)).thenReturn(Optional.of(draft));

        String digest = EkycWorkflowService.computeCccdDigest("001095012345");
        when(repository.findCccdOwner(digest)).thenReturn(Optional.empty());

        AssetRecord front = new AssetRecord(1L, 100L, "CARD_FRONT", "ekyc/front.jpg", "digest", Instant.now(), Instant.now().plusSeconds(1000), null);
        AssetRecord back = new AssetRecord(2L, 100L, "CARD_BACK", "ekyc/back.jpg", "digest-back", Instant.now(), Instant.now().plusSeconds(1000), null);
        when(repository.findAssetById(1L)).thenReturn(Optional.of(front));
        when(repository.findAssetById(2L)).thenReturn(Optional.of(back));
        when(storage.read("ekyc/front.jpg")).thenReturn(new byte[]{1, 2, 3});

        var match = new EkycClient.MatchResult("MATCHED", true, 0.28, 0.92, 0.50, "Khuôn mặt trùng khớp", "ArcFace", "1.0", false);
        when(client.match(anyString(), anyString())).thenReturn(match);

        when(storage.store(any(), any())).thenReturn(new EkycPrivateStorage.StoredAsset("ekyc/stored.jpg", "digest64", 3));
        when(repository.findAssetsByProfileId(100L)).thenReturn(List.of(front));

        when(repository.findAssetById(2L)).thenReturn(Optional.of(
                new AssetRecord(2L, 100L, "CARD_BACK", "ekyc/back.jpg", "digest-back", Instant.now(), Instant.now().plusSeconds(1000), null)));
        EkycSubmitResponse res = service.submitVerification(10L, req);

        assertThat(res.isMatch()).isTrue();
        assertThat(res.state()).isEqualTo("PENDING");
        assertThat(res.distance()).isEqualTo(0.28);
        verify(repository).recordAttempt(eq(100L), eq("MATCH"), anyMap(), anyString());
        verify(repository).registerDocumentDigest(eq(digest), eq(10L), eq(100L));
        verify(repository).submitProfile(eq(100L), eq(digest), anyString(), anyString());
    }

    @Test
    void uploadCccdThrowsWhenOcrRejectsQualityOrFields() {
        byte[] front = new byte[]{1, 2, 3};
        byte[] back = new byte[]{4, 5, 6};
        ProfileRecord draft = new ProfileRecord(100L, 10L, 1, "IN_PROGRESS", "{}", "{}", 0, null, Instant.now(), null, null, null, 0, null, null, null, null);
        when(repository.createOrGetDraft(10L)).thenReturn(draft);

        when(client.ocr(front, "front.jpg", "image/jpeg", back, "back.jpg", "image/jpeg"))
                .thenThrow(new IllegalArgumentException("Ảnh chụp bị mờ hoặc rung tay. Vui lòng giữ chắc camera và chụp lại."));

        assertThatThrownBy(() -> service.uploadCccd(10L, front, "front.jpg", "image/jpeg", back, "back.jpg", "image/jpeg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ảnh chụp bị mờ hoặc rung tay");
        verify(storage, never()).store(any(), any());
    }

    @Test
    void submitVerificationThrowsWhenFaceQualityIsLowWithoutRecordingAttempt() {
        CccdDataDto data = new CccdDataDto("001095012345", "NGUYEN VAN A", "01/01/1995", "Nam", "HN", "HN", false, true);
        String liveBase64 = Base64.getEncoder().encodeToString(new byte[]{4, 5, 6});
        EkycSubmitRequest req = new EkycSubmitRequest(data, liveBase64, 1L, 2L);

        ProfileRecord draft = new ProfileRecord(100L, 10L, 1, "IN_PROGRESS", "{}", "{}", 0, null, Instant.now(), null, null, null, 0, null, null, null, null);
        when(repository.findActiveProfile(10L)).thenReturn(Optional.of(draft));

        String digest = EkycWorkflowService.computeCccdDigest("001095012345");
        when(repository.findCccdOwner(digest)).thenReturn(Optional.empty());

        AssetRecord front = new AssetRecord(1L, 100L, "CARD_FRONT", "ekyc/front.jpg", "digest", Instant.now(), Instant.now().plusSeconds(1000), null);
        AssetRecord back = new AssetRecord(2L, 100L, "CARD_BACK", "ekyc/back.jpg", "digest-back", Instant.now(), Instant.now().plusSeconds(1000), null);
        when(repository.findAssetById(1L)).thenReturn(Optional.of(front));
        when(repository.findAssetById(2L)).thenReturn(Optional.of(back));
        when(storage.read("ekyc/front.jpg")).thenReturn(new byte[]{1, 2, 3});

        var qualityIssue = new EkycClient.MatchResult("IMAGE_QUALITY_ISSUE", false, 1.0, 0.0, 0.50, "Ảnh khuôn mặt bị mờ hoặc thiếu sáng", "ArcFace", "1.0", false);
        when(client.match(anyString(), anyString())).thenReturn(qualityIssue);

        when(repository.findAssetById(2L)).thenReturn(Optional.of(
                new AssetRecord(2L, 100L, "CARD_BACK", "ekyc/back.jpg", "digest-back", Instant.now(), Instant.now().plusSeconds(1000), null)));
        assertThatThrownBy(() -> service.submitVerification(10L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ảnh khuôn mặt bị mờ hoặc thiếu sáng");

        verify(repository, never()).recordAttempt(anyLong(), anyString(), anyMap(), anyString());
        verify(repository, never()).submitProfile(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void getStatusWhenRejectedFetchesRejectionReasonAndLiveAssetId() {
        UserEntity user = mock(UserEntity.class);
        when(user.getRoles()).thenReturn(Set.of());
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        ProfileRecord rejected = new ProfileRecord(
                100L, 10L, 1, "REJECTED", "{\"cccdNumber\":\"001095012345\"}", "{}",
                0, null, Instant.now(), Instant.now(), null, null, 0, "digest", null, null, null
        );
        when(repository.findActiveProfile(10L)).thenReturn(Optional.empty());
        when(repository.findLatestProfile(10L)).thenReturn(Optional.of(rejected));
        when(repository.findRejectionReason(100L)).thenReturn(Optional.of("Ảnh mặt trước CCCD bị lóa sáng đèn flash"));

        AssetRecord front = new AssetRecord(1L, 100L, "CARD_FRONT", "key1", "dig1", Instant.now(), null, null);
        AssetRecord back = new AssetRecord(2L, 100L, "CARD_BACK", "key2", "dig2", Instant.now(), null, null);
        AssetRecord live = new AssetRecord(3L, 100L, "LIVE_FRAME", "key3", "dig3", Instant.now(), null, null);
        when(repository.findAssetsByProfileId(100L)).thenReturn(List.of(front, back, live));

        EkycStatusResponse res = service.getStatus(10L);

        assertThat(res.hasProfile()).isTrue();
        assertThat(res.state()).isEqualTo("REJECTED");
        assertThat(res.rejectionReason()).isEqualTo("Ảnh mặt trước CCCD bị lóa sáng đèn flash");
        assertThat(res.frontAssetId()).isEqualTo(1L);
        assertThat(res.backAssetId()).isEqualTo(2L);
        assertThat(res.liveAssetId()).isEqualTo(3L);
        assertThat(res.canSubmit()).isTrue();
    }

    @Test
    void startOrResume_whenHasVerifiedProfile_andNoQuickAuth_returnsQuickAuthRequired() {
        UserEntity user = mock(UserEntity.class);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(repository.findActiveProfile(10L)).thenReturn(Optional.empty());
        when(repository.hasVerifiedProfile(10L)).thenReturn(true);

        EkycStartResponse res = service.startOrResume(10L);

        assertThat(res.state()).isEqualTo("QUICK_AUTH_REQUIRED");
        assertThat(res.profileId()).isNull();
        verify(repository, never()).createOrGetDraft(anyLong());
    }

    @Test
    void startOrResume_whenHasVerifiedProfile_andInvalidQuickAuth_throwsException() {
        UserEntity user = mock(UserEntity.class);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(repository.findActiveProfile(10L)).thenReturn(Optional.empty());
        when(repository.hasVerifiedProfile(10L)).thenReturn(true);
        when(repository.findValidQuickAuthResult(10L)).thenReturn(Optional.empty());

        java.util.UUID randomId = java.util.UUID.randomUUID();
        assertThatThrownBy(() -> service.startOrResume(10L, randomId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Kết quả xác thực khuôn mặt không hợp lệ hoặc đã hết hạn");
    }

    @Test
    void startOrResume_whenHasVerifiedProfile_andValidQuickAuth_createsDraft() {
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(10L);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(repository.findActiveProfile(10L)).thenReturn(Optional.empty());
        when(repository.hasVerifiedProfile(10L)).thenReturn(true);

        java.util.UUID resultId = java.util.UUID.randomUUID();
        EkycRepository.QuickAuthResultRecord resultRecord = new EkycRepository.QuickAuthResultRecord(
                resultId, 10L, 100L, 500L, Instant.now(), Instant.now().plusSeconds(900), "cmd", java.util.UUID.randomUUID()
        );
        when(repository.findValidQuickAuthResult(10L)).thenReturn(Optional.of(resultRecord));

        ProfileRecord newDraft = new ProfileRecord(
                200L, 10L, 2, "IN_PROGRESS", "{}", "{}", 0, null, Instant.now(), null, null, null, 0, null, null, null, null
        );
        when(repository.createOrGetDraft(10L)).thenReturn(newDraft);

        EkycStartResponse res = service.startOrResume(10L, resultId);

        assertThat(res.state()).isEqualTo("IN_PROGRESS");
        assertThat(res.profileId()).isEqualTo(200L);
        assertThat(res.revisionNo()).isEqualTo(2);
        verify(repository).createOrGetDraft(10L);
    }

    @Test
    void submitVerification_whenReverificationWithValidQuickAuth_succeeds() {
        // Given: user has existing VERIFIED profile, now re-verifying with new CCCD
        CccdDataDto data = new CccdDataDto("001095012345", "NGUYEN VAN A", "01/01/1995", "Nam", "HN", "HN", false, true);
        String liveBase64 = Base64.getEncoder().encodeToString(new byte[]{4, 5, 6});
        UUID validResultId = UUID.randomUUID();
        EkycSubmitRequest req = new EkycSubmitRequest(data, liveBase64, 1L, 2L, validResultId);

        // Profile is revision 2 (re-verification)
        ProfileRecord draft = new ProfileRecord(100L, 10L, 2, "IN_PROGRESS", "{}", "{}", 0, null, Instant.now(), null, null, null, 0, null, null, null, null);
        when(repository.findActiveProfile(10L)).thenReturn(Optional.of(draft));
        when(repository.hasVerifiedProfile(10L)).thenReturn(true); // Has previous VERIFIED profile

        String digest = EkycWorkflowService.computeCccdDigest("001095012345");
        when(repository.findCccdOwner(digest)).thenReturn(Optional.empty());

        AssetRecord front = new AssetRecord(1L, 100L, "CARD_FRONT", "ekyc/front.jpg", "digest", Instant.now(), Instant.now().plusSeconds(1000), null);
        AssetRecord back = new AssetRecord(2L, 100L, "CARD_BACK", "ekyc/back.jpg", "digest-back", Instant.now(), Instant.now().plusSeconds(1000), null);
        when(repository.findAssetById(1L)).thenReturn(Optional.of(front));
        when(repository.findAssetById(2L)).thenReturn(Optional.of(back));
        when(storage.read("ekyc/front.jpg")).thenReturn(new byte[]{1, 2, 3});

        var match = new EkycClient.MatchResult("MATCHED", true, 0.28, 0.92, 0.50, "Khuôn mặt trùng khớp", "ArcFace", "1.0", false);
        when(client.match(anyString(), anyString())).thenReturn(match);

        // Quick auth result is valid
        EkycRepository.QuickAuthResultRecord validResult = new EkycRepository.QuickAuthResultRecord(
                validResultId, 10L, 50L, 60L, Instant.now(), Instant.now().plusSeconds(900), "cmd", UUID.randomUUID()
        );
        when(repository.findValidQuickAuthResult(10L)).thenReturn(Optional.of(validResult));

        when(storage.store(any(), any())).thenReturn(new EkycPrivateStorage.StoredAsset("ekyc/stored.jpg", "digest64", 3));
        when(repository.findAssetsByProfileId(100L)).thenReturn(List.of(front));
        when(repository.findAssetById(2L)).thenReturn(Optional.of(back));

        EkycSubmitResponse res = service.submitVerification(10L, req);

        assertThat(res.isMatch()).isTrue();
        assertThat(res.state()).isEqualTo("PENDING");
        verify(repository).findValidQuickAuthResult(10L);
    }

    @Test
    void submitVerification_whenReverificationWithoutQuickAuth_throwsException() {
        CccdDataDto data = new CccdDataDto("001095012345", "NGUYEN VAN A", "01/01/1995", "Nam", "HN", "HN", false, true);
        String liveBase64 = Base64.getEncoder().encodeToString(new byte[]{4, 5, 6});
        // No quickAuthResultId provided
        EkycSubmitRequest req = new EkycSubmitRequest(data, liveBase64, 1L, 2L, null);

        ProfileRecord draft = new ProfileRecord(100L, 10L, 2, "IN_PROGRESS", "{}", "{}", 0, null, Instant.now(), null, null, null, 0, null, null, null, null);
        when(repository.findActiveProfile(10L)).thenReturn(Optional.of(draft));
        when(repository.hasVerifiedProfile(10L)).thenReturn(true);

        String digest = EkycWorkflowService.computeCccdDigest("001095012345");
        when(repository.findCccdOwner(digest)).thenReturn(Optional.empty());

        AssetRecord front = new AssetRecord(1L, 100L, "CARD_FRONT", "ekyc/front.jpg", "digest", Instant.now(), Instant.now().plusSeconds(1000), null);
        AssetRecord back = new AssetRecord(2L, 100L, "CARD_BACK", "ekyc/back.jpg", "digest-back", Instant.now(), Instant.now().plusSeconds(1000), null);
        when(repository.findAssetById(1L)).thenReturn(Optional.of(front));
        when(repository.findAssetById(2L)).thenReturn(Optional.of(back));
        when(storage.read("ekyc/front.jpg")).thenReturn(new byte[]{1, 2, 3});

        var match = new EkycClient.MatchResult("MATCHED", true, 0.28, 0.92, 0.50, "Khuôn mặt trùng khớp", "ArcFace", "1.0", false);
        when(client.match(anyString(), anyString())).thenReturn(match);

        assertThatThrownBy(() -> service.submitVerification(10L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Làm lại hồ sơ eKYC (đổi CCCD) yêu cầu xác thực nhanh khuôn mặt");
    }

    @Test
    void submitVerification_whenReverificationWithInvalidQuickAuth_throwsException() {
        CccdDataDto data = new CccdDataDto("001095012345", "NGUYEN VAN A", "01/01/1995", "Nam", "HN", "HN", false, true);
        String liveBase64 = Base64.getEncoder().encodeToString(new byte[]{4, 5, 6});
        UUID invalidResultId = UUID.randomUUID();
        EkycSubmitRequest req = new EkycSubmitRequest(data, liveBase64, 1L, 2L, invalidResultId);

        ProfileRecord draft = new ProfileRecord(100L, 10L, 2, "IN_PROGRESS", "{}", "{}", 0, null, Instant.now(), null, null, null, 0, null, null, null, null);
        when(repository.findActiveProfile(10L)).thenReturn(Optional.of(draft));
        when(repository.hasVerifiedProfile(10L)).thenReturn(true);

        String digest = EkycWorkflowService.computeCccdDigest("001095012345");
        when(repository.findCccdOwner(digest)).thenReturn(Optional.empty());

        AssetRecord front = new AssetRecord(1L, 100L, "CARD_FRONT", "ekyc/front.jpg", "digest", Instant.now(), Instant.now().plusSeconds(1000), null);
        AssetRecord back = new AssetRecord(2L, 100L, "CARD_BACK", "ekyc/back.jpg", "digest-back", Instant.now(), Instant.now().plusSeconds(1000), null);
        when(repository.findAssetById(1L)).thenReturn(Optional.of(front));
        when(repository.findAssetById(2L)).thenReturn(Optional.of(back));
        when(storage.read("ekyc/front.jpg")).thenReturn(new byte[]{1, 2, 3});

        var match = new EkycClient.MatchResult("MATCHED", true, 0.28, 0.92, 0.50, "Khuôn mặt trùng khớp", "ArcFace", "1.0", false);
        when(client.match(anyString(), anyString())).thenReturn(match);

        // Quick auth result is different from provided one
        EkycRepository.QuickAuthResultRecord otherResult = new EkycRepository.QuickAuthResultRecord(
                UUID.randomUUID(), 10L, 50L, 60L, Instant.now(), Instant.now().plusSeconds(900), "cmd", UUID.randomUUID()
        );
        when(repository.findValidQuickAuthResult(10L)).thenReturn(Optional.of(otherResult));

        assertThatThrownBy(() -> service.submitVerification(10L, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Kết quả xác thực khuôn mặt không hợp lệ hoặc đã hết hạn");
    }

    @Test
    void submitVerification_firstTimeNoQuickAuthRequired() {
        // First-time eKYC (revision 1) should NOT require quickAuthResultId
        CccdDataDto data = new CccdDataDto("001095012345", "NGUYEN VAN A", "01/01/1995", "Nam", "HN", "HN", false, true);
        String liveBase64 = Base64.getEncoder().encodeToString(new byte[]{4, 5, 6});
        EkycSubmitRequest req = new EkycSubmitRequest(data, liveBase64, 1L, 2L, null);

        ProfileRecord draft = new ProfileRecord(100L, 10L, 1, "IN_PROGRESS", "{}", "{}", 0, null, Instant.now(), null, null, null, 0, null, null, null, null);
        when(repository.findActiveProfile(10L)).thenReturn(Optional.of(draft));
        when(repository.hasVerifiedProfile(10L)).thenReturn(false); // No previous VERIFIED profile

        String digest = EkycWorkflowService.computeCccdDigest("001095012345");
        when(repository.findCccdOwner(digest)).thenReturn(Optional.empty());

        AssetRecord front = new AssetRecord(1L, 100L, "CARD_FRONT", "ekyc/front.jpg", "digest", Instant.now(), Instant.now().plusSeconds(1000), null);
        AssetRecord back = new AssetRecord(2L, 100L, "CARD_BACK", "ekyc/back.jpg", "digest-back", Instant.now(), Instant.now().plusSeconds(1000), null);
        when(repository.findAssetById(1L)).thenReturn(Optional.of(front));
        when(repository.findAssetById(2L)).thenReturn(Optional.of(back));
        when(storage.read("ekyc/front.jpg")).thenReturn(new byte[]{1, 2, 3});

        var match = new EkycClient.MatchResult("MATCHED", true, 0.28, 0.92, 0.50, "Khuôn mặt trùng khớp", "ArcFace", "1.0", false);
        when(client.match(anyString(), anyString())).thenReturn(match);

        when(storage.store(any(), any())).thenReturn(new EkycPrivateStorage.StoredAsset("ekyc/stored.jpg", "digest64", 3));
        when(repository.findAssetsByProfileId(100L)).thenReturn(List.of(front));
        when(repository.findAssetById(2L)).thenReturn(Optional.of(back));

        // Should NOT call findValidQuickAuthResult for first-time eKYC
        EkycSubmitResponse res = service.submitVerification(10L, req);

        assertThat(res.isMatch()).isTrue();
        verify(repository, never()).findValidQuickAuthResult(anyLong());
    }

    @Test
    void submitVerification_withManualCapture_submitsToPendingEvenIfAiMatchIsFalse() {
        CccdDataDto data = new CccdDataDto("001095012345", "NGUYEN VAN A", "01/01/1995", "Nam", "HN", "HN", false, true);
        String liveBase64 = Base64.getEncoder().encodeToString(new byte[]{4, 5, 6});
        EkycSubmitRequest req = new EkycSubmitRequest(data, liveBase64, 1L, 2L, null, null, null, true);

        ProfileRecord draft = new ProfileRecord(100L, 10L, 1, "IN_PROGRESS", "{}", "{}", 0, null, Instant.now(), null, null, null, 0, null, null, null, null);
        when(repository.findActiveProfile(10L)).thenReturn(Optional.of(draft));
        when(repository.hasVerifiedProfile(10L)).thenReturn(false);

        String digest = EkycWorkflowService.computeCccdDigest("001095012345");
        when(repository.findCccdOwner(digest)).thenReturn(Optional.empty());

        AssetRecord front = new AssetRecord(1L, 100L, "CARD_FRONT", "ekyc/front.jpg", "digest", Instant.now(), Instant.now().plusSeconds(1000), null);
        AssetRecord back = new AssetRecord(2L, 100L, "CARD_BACK", "ekyc/back.jpg", "digest-back", Instant.now(), Instant.now().plusSeconds(1000), null);
        when(repository.findAssetById(1L)).thenReturn(Optional.of(front));
        when(repository.findAssetById(2L)).thenReturn(Optional.of(back));
        when(storage.read("ekyc/front.jpg")).thenReturn(new byte[]{1, 2, 3});

        // AI match is FALSE (e.g. distance 0.54 > 0.50), but user used manual capture fallback after 20s
        var nonMatch = new EkycClient.MatchResult("NOT_MATCHED", false, 0.54, 0.40, 0.50, "Khuôn mặt chưa khớp", "ArcFace", "1.0", false);
        when(client.match(anyString(), anyString())).thenReturn(nonMatch);

        when(storage.store(any(), any())).thenReturn(new EkycPrivateStorage.StoredAsset("ekyc/stored.jpg", "digest64", 3));
        when(repository.findAssetsByProfileId(100L)).thenReturn(List.of(front));
        when(repository.findAssetById(2L)).thenReturn(Optional.of(back));

        EkycSubmitResponse res = service.submitVerification(10L, req);

        assertThat(res.isMatch()).isTrue();
        assertThat(res.state()).isEqualTo("PENDING");
        assertThat(res.message()).contains("chuyển cho Kỹ thuật viên (KTV) xét duyệt thủ công");
        verify(repository).recordAttempt(eq(100L), eq("NON_MATCH"), anyMap(), anyString());
        verify(repository).submitProfile(eq(100L), eq(digest), anyString(), anyString());
    }

    @Test
    void uploadCccd_rejectsWhenCardAndPortraitMismatched() {
        byte[] front = new byte[]{1, 2, 3};
        byte[] back = new byte[]{4, 5, 6};
        byte[] portrait = new byte[]{7, 8, 9};

        ProfileRecord draft = new ProfileRecord(100L, 10L, 1, "IN_PROGRESS", "{}", "{}", 0, null, Instant.now(), null, null, null, 0, null, null, null, null);
        when(repository.createOrGetDraft(10L)).thenReturn(draft);

        var ocrError = new EkycClient.OcrResult("ERROR", "Ảnh trên thẻ CCCD và ảnh chân dung không khớp nhau (khoảng cách sai lệch 0.65 > 0.55).", null);
        when(client.ocr(any(), anyString(), anyString(), any(), anyString(), anyString(), any(), anyString(), anyString())).thenReturn(ocrError);

        EkycOcrResponse res = service.uploadCccd(10L, front, "front.jpg", "image/jpeg", back, "back.jpg", "image/jpeg", portrait, "portrait.jpg", "image/jpeg");

        assertThat(res.status()).isEqualTo("ERROR");
        assertThat(res.errorMessage()).contains("không khớp nhau");
        assertThat(res.frontAssetId()).isNull();
        verify(storage, never()).store(any(), any());
    }
}
