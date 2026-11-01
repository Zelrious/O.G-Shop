package com.oldbutgold.shop.modules.identity.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.identity.api.EkycDtos.*;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository.AssetRecord;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository.PendingQueueItem;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository.ProfileRecord;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RoleEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserRepository;
import com.oldbutgold.shop.modules.platform.application.PlatformNotificationFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KtvEkycServiceTest {

    @Mock
    private EkycRepository repository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlatformNotificationFacade notifications;

    private final ObjectMapper json = new ObjectMapper();

    private KtvEkycService ktvEkycService;

    @BeforeEach
    void setUp() {
        ktvEkycService = new KtvEkycService(repository, userRepository, notifications, json);
    }

    private UserEntity createKtvUser(long userId) {
        UserEntity ktv = mock(UserEntity.class);
        RoleEntity role = mock(RoleEntity.class);
        when(role.getRoleName()).thenReturn("KTV");
        when(ktv.getRoles()).thenReturn(Set.of(role));
        return ktv;
    }

    private UserEntity createNormalUser(long userId) {
        UserEntity user = mock(UserEntity.class);
        when(user.getRoles()).thenReturn(Collections.emptySet());
        return user;
    }

    private ProfileRecord createProfile(long profileId, long userId, String state, String submittedJson, String aiJson) {
        return new ProfileRecord(
                profileId, userId, 1, state,
                submittedJson, aiJson, 0, null,
                Instant.now(), Instant.now(), null, null,
                1L, null, null, null, null
        );
    }

    @Test
    @DisplayName("getQueue: should return paged pending items with masked CCCD")
    void getQueue_success() {
        PendingQueueItem item = new PendingQueueItem(
                100L, 10L, "buyer1", "Trần Văn Bảo", "bao@test.vn", "0901234567",
                1, "PENDING", Instant.now(),
                "{\"cccdNumber\":\"001095012345\",\"fullName\":\"Trần Văn Bảo\"}",
                "{\"distance\":0.23,\"confidenceScore\":0.95}"
        );
        when(repository.getPendingQueue(0, 20)).thenReturn(List.of(item));
        when(repository.countPendingQueue()).thenReturn(1L);

        KtvEkycPageResponse result = ktvEkycService.getQueue(0, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.items().get(0).fullName()).isEqualTo("Trần Văn Bảo");
        assertThat(result.items().get(0).maskedCccdNumber()).contains("****");
    }

    @Test
    @DisplayName("getDetail: should throw IllegalArgumentException when profile not found")
    void getDetail_notFound() {
        when(repository.findProfileById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ktvEkycService.getDetail(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không tìm thấy");
    }

    @Test
    @DisplayName("getDetail: should return full detail including assets")
    void getDetail_success() {
        ProfileRecord profile = createProfile(
                100L, 10L, "PENDING",
                "{\"cccdNumber\":\"001095012345\",\"fullName\":\"Trần Văn Bảo\"}",
                "{\"distance\":0.23,\"confidenceScore\":0.95}"
        );
        UserEntity user = mock(UserEntity.class);
        when(user.getEmail()).thenReturn("bao@test.vn");
        when(user.getFullName()).thenReturn("Trần Văn Bảo");
        when(user.getPhoneNumber()).thenReturn("0901234567");

        when(repository.findProfileById(100L)).thenReturn(Optional.of(profile));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(repository.findAssetsByProfileId(100L)).thenReturn(List.of(
                new AssetRecord(1L, 100L, "CARD_FRONT", "uploads/f.jpg", "sha", Instant.now(), null, null),
                new AssetRecord(2L, 100L, "CARD_BACK", "uploads/b.jpg", "sha", Instant.now(), null, null),
                new AssetRecord(3L, 100L, "LIVE_FRAME", "uploads/l.jpg", "sha", Instant.now(), null, null)
        ));

        KtvEkycDetailDto detail = ktvEkycService.getDetail(100L);

        assertThat(detail.profileId()).isEqualTo(100L);
        assertThat(detail.frontAssetId()).isEqualTo(1L);
        assertThat(detail.backAssetId()).isEqualTo(2L);
        assertThat(detail.liveAssetId()).isEqualTo(3L);
        assertThat(detail.submittedData().cccdNumber()).isEqualTo("001095012345");
    }

    @Test
    @DisplayName("approve: should grant SELLER role and record decision")
    void approve_success() {
        ProfileRecord profile = createProfile(
                100L, 10L, "PENDING",
                "{\"cccdNumber\":\"001095012345\"}", null
        );
        UserEntity ktv = createKtvUser(2L);
        when(repository.findProfileByIdForUpdate(100L)).thenReturn(Optional.of(profile));
        when(userRepository.findById(2L)).thenReturn(Optional.of(ktv));

        KtvEkycDecisionResponse response = ktvEkycService.approve(100L, 2L, "Hồ sơ chuẩn xác");

        assertThat(response.profileId()).isEqualTo(100L);
        assertThat(response.outcome()).isEqualTo("VERIFIED");
        verify(repository).recordDecision(eq(100L), eq(2L), eq("VERIFIED"), eq("Hồ sơ chuẩn xác"), anyString());
        verify(repository).grantSellerRole(10L, 2L, 100L);
    }

    @Test
    @DisplayName("approve: should reject if caller is not KTV")
    void approve_notKtv_throws() {
        ProfileRecord profile = createProfile(100L, 10L, "PENDING", "{}", null);
        UserEntity normalUser = createNormalUser(5L);
        when(repository.findProfileByIdForUpdate(100L)).thenReturn(Optional.of(profile));
        when(userRepository.findById(5L)).thenReturn(Optional.of(normalUser));

        assertThatThrownBy(() -> ktvEkycService.approve(100L, 5L, "Duyệt"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Chỉ Kỹ thuật viên");
    }

    @Test
    @DisplayName("approve: should reject if caller is ADMIN without KTV role")
    void approve_adminUserWithoutKtv_throws() {
        ProfileRecord profile = createProfile(100L, 10L, "PENDING", "{}", null);
        UserEntity admin = mock(UserEntity.class);
        RoleEntity role = mock(RoleEntity.class);
        when(role.getRoleName()).thenReturn("ADMIN");
        when(admin.getRoles()).thenReturn(Set.of(role));

        when(repository.findProfileByIdForUpdate(100L)).thenReturn(Optional.of(profile));
        when(userRepository.findById(4L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> ktvEkycService.approve(100L, 4L, "Duyệt"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Chỉ Kỹ thuật viên");
    }

    @Test
    @DisplayName("approve: should throw IllegalStateException when profile was already processed by another KTV")
    void approve_concurrencyConflict_whenAlreadyVerifiedByOtherKtv_throws() {
        ProfileRecord alreadyVerified = createProfile(100L, 10L, "VERIFIED", "{}", null);
        when(repository.findProfileByIdForUpdate(100L)).thenReturn(Optional.of(alreadyVerified));

        assertThatThrownBy(() -> ktvEkycService.approve(100L, 2L, "Duyệt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đã được xử lý bởi một Kỹ thuật viên khác trước đó");
    }

    @Test
    @DisplayName("reject: should throw IllegalArgumentException when reason is blank")
    void reject_blankReason_throws() {
        assertThatThrownBy(() -> ktvEkycService.reject(100L, 2L, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bắt buộc phải nhập lý do");
    }

    @Test
    @DisplayName("reject: should record REJECTED decision without granting seller role")
    void reject_success() {
        ProfileRecord profile = createProfile(100L, 10L, "PENDING", "{}", null);
        UserEntity ktv = createKtvUser(2L);
        when(repository.findProfileByIdForUpdate(100L)).thenReturn(Optional.of(profile));
        when(userRepository.findById(2L)).thenReturn(Optional.of(ktv));

        KtvEkycDecisionResponse response = ktvEkycService.reject(100L, 2L, "Ảnh CCCD bị mờ góc chip");

        assertThat(response.profileId()).isEqualTo(100L);
        assertThat(response.outcome()).isEqualTo("REJECTED");
        verify(repository).recordDecision(eq(100L), eq(2L), eq("REJECTED"), eq("Ảnh CCCD bị mờ góc chip"), anyString());
        verify(repository, never()).grantSellerRole(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("reject: should throw IllegalStateException when profile was already processed by another KTV")
    void reject_concurrencyConflict_whenAlreadyRejectedByOtherKtv_throws() {
        ProfileRecord alreadyRejected = createProfile(100L, 10L, "REJECTED", "{}", null);
        when(repository.findProfileByIdForUpdate(100L)).thenReturn(Optional.of(alreadyRejected));

        assertThatThrownBy(() -> ktvEkycService.reject(100L, 2L, "Lý do từ chối"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đã được xử lý bởi một Kỹ thuật viên khác trước đó");
    }
}
