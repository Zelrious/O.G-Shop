package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.identity.api.ProfileDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileAvatarServiceTest {

    @Mock
    private ProfileService profileService;

    @Mock
    private AvatarStoragePort avatarStoragePort;

    private ProfileAvatarService profileAvatarService;
    private byte[] validJpegBytes;

    @BeforeEach
    void setUp() throws IOException {
        profileAvatarService = new ProfileAvatarService(profileService, avatarStoragePort);

        BufferedImage img = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        validJpegBytes = baos.toByteArray();
    }

    @Test
    @DisplayName("Upload avatar thành công cập nhật profile và trả về kết quả")
    void update_ValidAvatar_Success() {
        long userId = 1L;
        ProfileDtos.AvatarProfileRequest request = new ProfileDtos.AvatarProfileRequest("Nguyễn Văn A", "0901234567");
        AvatarStoragePort.StoredAvatar stored = new AvatarStoragePort.StoredAvatar("https://res.cloudinary.com/demo/image/upload/v1/avatar.jpg", "og_shop/avatars/1/uuid-123");

        ProfileDtos.ProfileResponse expectedResponse = new ProfileDtos.ProfileResponse(
                userId, "buyer@example.com", "Nguyễn Văn A", "0901234567",
                stored.secureUrl(), Set.of("BUYER"), null, null, null, false, Instant.now()
        );

        doNothing().when(profileService).checkProfileEditable(userId);
        when(avatarStoragePort.upload(eq(userId), any(AvatarImage.class))).thenReturn(stored);
        when(profileService.updateProfileWithAvatar(userId, request, stored.secureUrl())).thenReturn(expectedResponse);

        ProfileDtos.ProfileResponse actualResponse = profileAvatarService.update(userId, request, validJpegBytes);

        assertThat(actualResponse).isEqualTo(expectedResponse);
        verify(avatarStoragePort, never()).delete(any());
    }

    @Test
    @DisplayName("Tài khoản không được phép sửa thì từ chối trước khi upload lên Cloudinary")
    void update_ProfileNotEditable_ThrowsBeforeUpload() {
        long userId = 2L;
        ProfileDtos.AvatarProfileRequest request = new ProfileDtos.AvatarProfileRequest("Nguyễn Văn B", "0909999999");

        doThrow(new AccessDeniedException("Tài khoản không thể cập nhật hồ sơ."))
                .when(profileService).checkProfileEditable(userId);

        assertThatThrownBy(() -> profileAvatarService.update(userId, request, validJpegBytes))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Tài khoản không thể cập nhật hồ sơ.");

        verify(avatarStoragePort, never()).upload(anyLong(), any());
        verify(profileService, never()).updateProfileWithAvatar(anyLong(), any(), any());
    }

    @Test
    @DisplayName("Cloudinary upload lỗi thì không gọi cập nhật DB và không xóa asset")
    void update_StorageUploadFails_ThrowsWithoutDbUpdate() {
        long userId = 1L;
        ProfileDtos.AvatarProfileRequest request = new ProfileDtos.AvatarProfileRequest("Nguyễn Văn A", "0901234567");

        doNothing().when(profileService).checkProfileEditable(userId);
        when(avatarStoragePort.upload(eq(userId), any(AvatarImage.class)))
                .thenThrow(new AvatarStorageUnavailableException());

        assertThatThrownBy(() -> profileAvatarService.update(userId, request, validJpegBytes))
                .isInstanceOf(AvatarStorageUnavailableException.class);

        verify(profileService, never()).updateProfileWithAvatar(anyLong(), any(), any());
        verify(avatarStoragePort, never()).delete(any());
    }

    @Test
    @DisplayName("Cập nhật DB thất bại thì kích hoạt bù trừ xóa asset mới trên Cloudinary")
    void update_DbSaveFails_CompensatesByDeletingNewAsset() {
        long userId = 1L;
        ProfileDtos.AvatarProfileRequest request = new ProfileDtos.AvatarProfileRequest("Nguyễn Văn A", "0901234567");
        AvatarStoragePort.StoredAvatar stored = new AvatarStoragePort.StoredAvatar(
                "https://res.cloudinary.com/demo/image/upload/v1/avatar.jpg",
                "og_shop/avatars/1/uuid-test-compensate"
        );

        doNothing().when(profileService).checkProfileEditable(userId);
        when(avatarStoragePort.upload(eq(userId), any(AvatarImage.class))).thenReturn(stored);
        when(profileService.updateProfileWithAvatar(userId, request, stored.secureUrl()))
                .thenThrow(new RuntimeException("Database connection timed out during commit"));

        assertThatThrownBy(() -> profileAvatarService.update(userId, request, validJpegBytes))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database connection timed out");

        verify(avatarStoragePort, times(1)).delete(stored);
    }

    @Test
    @DisplayName("Nếu bù trừ xóa asset thất bại thì vẫn ném lỗi gốc của database")
    void update_DbSaveFailsAndCleanupFails_StillThrowsOriginalException() {
        long userId = 1L;
        ProfileDtos.AvatarProfileRequest request = new ProfileDtos.AvatarProfileRequest("Nguyễn Văn A", "0901234567");
        AvatarStoragePort.StoredAvatar stored = new AvatarStoragePort.StoredAvatar(
                "https://res.cloudinary.com/demo/image/upload/v1/avatar.jpg",
                "og_shop/avatars/1/uuid-cleanup-fails"
        );

        doNothing().when(profileService).checkProfileEditable(userId);
        when(avatarStoragePort.upload(eq(userId), any(AvatarImage.class))).thenReturn(stored);
        when(profileService.updateProfileWithAvatar(userId, request, stored.secureUrl()))
                .thenThrow(new RuntimeException("DB error"));
        doThrow(new RuntimeException("Cloudinary destroy failed"))
                .when(avatarStoragePort).delete(stored);

        assertThatThrownBy(() -> profileAvatarService.update(userId, request, validJpegBytes))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB error");
    }
}
