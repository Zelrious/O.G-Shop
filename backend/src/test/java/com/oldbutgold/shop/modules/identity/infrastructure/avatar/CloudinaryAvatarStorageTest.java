package com.oldbutgold.shop.modules.identity.infrastructure.avatar;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.oldbutgold.shop.modules.identity.application.AvatarImage;
import com.oldbutgold.shop.modules.identity.application.AvatarStoragePort;
import com.oldbutgold.shop.modules.identity.application.AvatarStorageUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloudinaryAvatarStorageTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private CloudinaryAvatarStorage storage;

    @BeforeEach
    void setUp() {
        storage = new CloudinaryAvatarStorage(cloudinary);
    }

    @Test
    @DisplayName("Khi Cloudinary chưa được cấu hình, upload và delete ném AvatarStorageUnavailableException")
    void unconfiguredCloudinary_ThrowsException() {
        CloudinaryAvatarStorage unconfigured = new CloudinaryAvatarStorage(null);

        AvatarImage image = new AvatarImage(new byte[]{1, 2, 3}, "jpg");
        assertThatThrownBy(() -> unconfigured.upload(1L, image))
                .isInstanceOf(AvatarStorageUnavailableException.class);

        AvatarStoragePort.StoredAvatar avatar = new AvatarStoragePort.StoredAvatar("https://example.com/a.jpg", "og_shop/avatars/1/" + UUID.randomUUID());
        assertThatThrownBy(() -> unconfigured.delete(avatar))
                .isInstanceOf(AvatarStorageUnavailableException.class);
    }

    @Test
    @DisplayName("Upload ảnh hợp lệ nhận metadata đúng từ Cloudinary và trả về StoredAvatar")
    @SuppressWarnings("unchecked")
    void upload_ValidMetadata_Success() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);

        ArgumentCaptor<Map<String, Object>> optionsCaptor = ArgumentCaptor.forClass(Map.class);
        when(uploader.upload(any(byte[].class), optionsCaptor.capture())).thenAnswer(invocation -> {
            Map<String, Object> opts = optionsCaptor.getValue();
            String publicId = (String) opts.get("public_id");
            return Map.of(
                    "public_id", publicId,
                    "secure_url", "https://res.cloudinary.com/og_shop/image/upload/v123/" + publicId + ".jpg",
                    "resource_type", "image",
                    "format", "jpg",
                    "width", 400,
                    "height", 400
            );
        });

        AvatarImage image = new AvatarImage(new byte[]{1, 2, 3}, "jpg");
        AvatarStoragePort.StoredAvatar stored = storage.upload(123L, image);

        assertThat(stored.publicId()).startsWith("og_shop/avatars/123/");
        assertThat(stored.secureUrl()).startsWith("https://res.cloudinary.com/og_shop/image/upload/v123/");
    }

    @Test
    @DisplayName("Upload thất bại khi Cloudinary gặp sự cố I/O")
    void upload_CloudinaryError_ThrowsUnavailable() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new IOException("Connection reset by peer"));

        AvatarImage image = new AvatarImage(new byte[]{1, 2, 3}, "jpg");
        assertThatThrownBy(() -> storage.upload(1L, image))
                .isInstanceOf(AvatarStorageUnavailableException.class);
    }

    @Test
    @DisplayName("Metadata trả về từ Cloudinary không hợp lệ thì xóa asset vừa upload và ném lỗi")
    @SuppressWarnings("unchecked")
    void upload_InvalidMetadata_CompensatesAndDeleteAsset() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);

        ArgumentCaptor<Map<String, Object>> optionsCaptor = ArgumentCaptor.forClass(Map.class);
        when(uploader.upload(any(byte[].class), optionsCaptor.capture())).thenAnswer(invocation -> {
            Map<String, Object> opts = optionsCaptor.getValue();
            String publicId = (String) opts.get("public_id");
            return Map.of(
                    "public_id", publicId,
                    "secure_url", "http://insecure-url.com/fake.jpg", // HTTP instead of HTTPS
                    "resource_type", "image",
                    "format", "jpg",
                    "width", 400,
                    "height", 400
            );
        });
        when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of("result", "ok"));

        AvatarImage image = new AvatarImage(new byte[]{1, 2, 3}, "jpg");
        assertThatThrownBy(() -> storage.upload(1L, image))
                .isInstanceOf(AvatarStorageUnavailableException.class);

        verify(uploader, times(1)).destroy(anyString(), anyMap());
    }

    @Test
    @DisplayName("Xóa asset hợp lệ gọi destroy trên Cloudinary")
    void delete_ValidPublicId_CallsDestroy() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        String publicId = "og_shop/avatars/1/" + UUID.randomUUID();
        when(uploader.destroy(eq(publicId), anyMap())).thenReturn(Map.of("result", "ok"));

        storage.delete(new AvatarStoragePort.StoredAvatar("https://example.com/a.jpg", publicId));

        verify(uploader, times(1)).destroy(eq(publicId), anyMap());
    }

    @Test
    @DisplayName("Xóa asset với publicId không đúng định dạng bị từ chối")
    void delete_InvalidPublicIdPattern_ThrowsException() {
        // Attack attempt with directory traversal or unexpected namespace
        AvatarStoragePort.StoredAvatar invalidAvatar = new AvatarStoragePort.StoredAvatar(
                "https://example.com/a.jpg",
                "other_folder/hack_id"
        );

        assertThatThrownBy(() -> storage.delete(invalidAvatar))
                .isInstanceOf(AvatarStorageUnavailableException.class);

        verifyNoInteractions(cloudinary);
    }
}
