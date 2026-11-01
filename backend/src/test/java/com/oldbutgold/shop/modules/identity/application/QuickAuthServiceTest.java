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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuickAuthServiceTest {
    private final EkycRepository repository = mock(EkycRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final EkycPrivateStorage storage = mock(EkycPrivateStorage.class);
    private final EkycClient client = mock(EkycClient.class);
    private final Instant now = Instant.parse("2026-10-24T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    private QuickAuthService service;

    @BeforeEach
    void setUp() {
        // Mặc định không có ảnh mẫu phụ: verify chỉ dùng ảnh mẫu gắn với phiên.
        when(repository.findApprovedReferenceAssets(anyLong())).thenReturn(List.of());
        service = new QuickAuthService(repository, userRepository, storage, client, clock);
    }

    @Test
    void init_whenAlreadyVerified_returnsAlreadyVerifiedTrue() {
        long userId = 10L;
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UUID resultId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant expiresAt = now.plus(Duration.ofMinutes(10));
        QuickAuthResultRecord existingResult = new QuickAuthResultRecord(
                resultId, userId, 100L, 500L, now.minusSeconds(60), expiresAt, "cmd-1", sessionId
        );
        when(repository.findValidQuickAuthResult(userId)).thenReturn(Optional.of(existingResult));

        QuickAuthInitResponse res = service.init(userId, "CHANGE_PASSWORD");

        assertThat(res.alreadyVerified()).isTrue();
        assertThat(res.resultId()).isEqualTo(resultId);
        assertThat(res.expiresAt()).isEqualTo(expiresAt);
        verify(repository, never()).createQuickAuthSession(any(), anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void init_whenNoVerifiedEkycProfile_throwsIllegalStateException() {
        long userId = 10L;
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(repository.findValidQuickAuthResult(userId)).thenReturn(Optional.empty());
        when(repository.findApprovedReferenceAsset(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.init(userId, "CHANGE_BANK"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chưa hoàn tất eKYC hoặc mẫu sinh trắc học đã hết hạn");
    }

    @Test
    void init_whenHasValidReference_createsNewSession() {
        long userId = 10L;
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(repository.findValidQuickAuthResult(userId)).thenReturn(Optional.empty());

        AssetRecord refAsset = new AssetRecord(
                500L, 100L, "REFERENCE", "ref/key.jpg", "digest", now, now.plus(Duration.ofDays(60)), null
        );
        when(repository.findApprovedReferenceAsset(userId)).thenReturn(Optional.of(refAsset));

        QuickAuthInitResponse res = service.init(userId, "CHANGE_PHONE");

        assertThat(res.alreadyVerified()).isFalse();
        assertThat(res.sessionId()).isNotNull();
        assertThat(res.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(15)));
        verify(repository).createQuickAuthSession(eq(res.sessionId()), eq(userId), eq(100L), eq(500L), any(), anyString());
    }

    @Test
    void verify_whenSessionNotFound_throwsIllegalArgumentException() {
        UUID sessionId = UUID.randomUUID();
        when(repository.findQuickAuthSession(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(10L, sessionId, "base64image"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Phiên xác thực không tồn tại");
    }

    @Test
    void verify_whenSessionExpired_throwsIllegalStateException() {
        UUID sessionId = UUID.randomUUID();
        QuickAuthSessionRecord expiredSession = new QuickAuthSessionRecord(
                sessionId, 10L, 100L, 500L, 0, null, now.minusSeconds(10), "cmd"
        );
        when(repository.findQuickAuthSession(sessionId)).thenReturn(Optional.of(expiredSession));

        assertThatThrownBy(() -> service.verify(10L, sessionId, "base64image"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Phiên xác thực đã hết hạn");
    }

    @Test
    void verify_whenMaxAttemptsExceeded_throwsIllegalStateException() {
        UUID sessionId = UUID.randomUUID();
        QuickAuthSessionRecord lockedSession = new QuickAuthSessionRecord(
                sessionId, 10L, 100L, 500L, 15, now.minusSeconds(10), now.plusSeconds(300), "cmd"
        );
        when(repository.findQuickAuthSession(sessionId)).thenReturn(Optional.of(lockedSession));

        assertThatThrownBy(() -> service.verify(10L, sessionId, "base64image"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vượt quá giới hạn 15 lần thử");
    }

    @Test
    void verify_whenCooldownActive_throwsIllegalStateException() {
        UUID sessionId = UUID.randomUUID();
        // Last nonmatch was 1 second ago (cooldown requires 3 seconds)
        QuickAuthSessionRecord session = new QuickAuthSessionRecord(
                sessionId, 10L, 100L, 500L, 1, now.minusSeconds(1), now.plusSeconds(300), "cmd"
        );
        when(repository.findQuickAuthSession(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.verify(10L, sessionId, "base64image"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Vui lòng đợi 3 giây");
    }

    @Test
    void verify_whenQualityIssue_throwsIllegalArgumentExceptionWithoutIncrementingAttempts() {
        UUID sessionId = UUID.randomUUID();
        QuickAuthSessionRecord session = new QuickAuthSessionRecord(
                sessionId, 10L, 100L, 500L, 0, null, now.plusSeconds(300), "cmd"
        );
        when(repository.findQuickAuthSession(sessionId)).thenReturn(Optional.of(session));

        AssetRecord refAsset = new AssetRecord(
                500L, 100L, "REFERENCE", "ref/key.jpg", "digest", now, now.plus(Duration.ofDays(60)), null
        );
        when(repository.findAssetById(500L)).thenReturn(Optional.of(refAsset));
        when(storage.read("ref/key.jpg")).thenReturn("sample-ref-bytes".getBytes());

        when(client.match(any(), any(), any(), any(), any())).thenReturn(
                new EkycClient.MatchResult("IMAGE_QUALITY_ISSUE", false, 0.0, 0.0, 0.45, "Ảnh camera quá mờ, vui lòng giữ yên", "ArcFace", "1.0", false)
        );

        assertThatThrownBy(() -> service.verify(10L, sessionId, "base64live"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ảnh camera quá mờ");

        verify(repository, never()).recordQuickAuthAttempt(any(), any(), any(), any(), any());
    }

    @Test
    void verify_whenNonMatch_returnsFailureWithoutExhaustingAttempts() {
        UUID sessionId = UUID.randomUUID();
        QuickAuthSessionRecord session = new QuickAuthSessionRecord(
                sessionId, 10L, 100L, 500L, 0, null, now.plusSeconds(300), "cmd"
        );
        when(repository.findQuickAuthSession(sessionId)).thenReturn(Optional.of(session));

        AssetRecord refAsset = new AssetRecord(
                500L, 100L, "REFERENCE", "ref/key.jpg", "digest", now, now.plus(Duration.ofDays(60)), null
        );
        when(repository.findAssetById(500L)).thenReturn(Optional.of(refAsset));
        when(storage.read("ref/key.jpg")).thenReturn("sample-ref-bytes".getBytes());

        when(client.match(any(), any(), any(), any(), any())).thenReturn(
                new EkycClient.MatchResult("NON_MATCH", false, 0.75, 45.0, 0.45, "Khuôn mặt không khớp", "ArcFace", "1.0", false)
        );

        QuickAuthVerifyResponse res = service.verify(10L, sessionId, "base64live");

        assertThat(res.match()).isFalse();
        assertThat(res.remainingAttempts()).isEqualTo(15);
        assertThat(res.resultId()).isNull();
        verify(repository, never()).recordQuickAuthAttempt(any(), any(), eq("NON_MATCH"), any(), any());
        verify(repository, never()).createQuickAuthResult(any(), any(), anyLong(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void verify_whenMatch_recordsAttemptAndCreatesResult() {
        UUID sessionId = UUID.randomUUID();
        QuickAuthSessionRecord session = new QuickAuthSessionRecord(
                sessionId, 10L, 100L, 500L, 0, null, now.plusSeconds(300), "cmd"
        );
        when(repository.findQuickAuthSession(sessionId)).thenReturn(Optional.of(session));

        AssetRecord refAsset = new AssetRecord(
                500L, 100L, "REFERENCE", "ref/key.jpg", "digest", now, now.plus(Duration.ofDays(60)), null
        );
        when(repository.findAssetById(500L)).thenReturn(Optional.of(refAsset));
        when(storage.read("ref/key.jpg")).thenReturn("sample-ref-bytes".getBytes());

        when(client.match(any(), any(), any(), any(), any())).thenReturn(
                new EkycClient.MatchResult("MATCH", true, 0.22, 92.5, 0.45, "Khớp khuôn mặt", "ArcFace", "1.0", false)
        );

        QuickAuthVerifyResponse res = service.verify(10L, sessionId, "base64live");

        assertThat(res.match()).isTrue();
        assertThat(res.resultId()).isNotNull();
        assertThat(res.confidenceScore()).isEqualTo(92.5);
        verify(repository).recordQuickAuthAttempt(any(), eq(sessionId), eq("MATCH"), eq(now), anyString());
        verify(repository).createQuickAuthResult(
                eq(res.resultId()), eq(sessionId), eq(10L), eq(100L), eq(500L), eq(now), eq(now.plus(Duration.ofMinutes(15))), anyString()
        );
    }

    @Test
    void getStatus_returnsCorrectStatus() {
        long userId = 10L;
        UUID resultId = UUID.randomUUID();
        when(repository.findValidQuickAuthResult(userId)).thenReturn(Optional.of(
                new QuickAuthResultRecord(resultId, userId, 100L, 500L, now, now.plusSeconds(600), "cmd", UUID.randomUUID())
        ));

        QuickAuthStatusResponse res = service.getStatus(userId, "CHANGE_PASSWORD");
        assertThat(res.hasValidResult()).isTrue();
        assertThat(res.resultId()).isEqualTo(resultId);

        when(repository.findValidQuickAuthResult(userId)).thenReturn(Optional.empty());
        QuickAuthStatusResponse resEmpty = service.getStatus(userId, "CHANGE_PASSWORD");
        assertThat(resEmpty.hasValidResult()).isFalse();
        assertThat(resEmpty.resultId()).isNull();
    }
}
