package com.oldbutgold.shop.modules.identity.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvatarImageTest {

    @Test
    @DisplayName("Ảnh JPEG hợp lệ được phân tích thành công")
    void inspect_ValidJpeg_Success() throws IOException {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        byte[] bytes = baos.toByteArray();

        AvatarImage image = AvatarImage.inspect(bytes);

        assertThat(image.format()).isEqualTo("jpg");
        assertThat(image.bytes()).isEqualTo(bytes);
    }

    @Test
    @DisplayName("Ảnh PNG hợp lệ được phân tích thành công")
    void inspect_ValidPng_Success() throws IOException {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        byte[] bytes = baos.toByteArray();

        AvatarImage image = AvatarImage.inspect(bytes);

        assertThat(image.format()).isEqualTo("png");
        assertThat(image.bytes()).isEqualTo(bytes);
    }

    @Test
    @DisplayName("Ảnh WebP VP8L hợp lệ được phân tích thành công")
    void inspect_ValidWebp_Success() {
        byte[] webp = createValidVp8lWebp(10, 10);
        AvatarImage image = AvatarImage.inspect(webp);

        assertThat(image.format()).isEqualTo("webp");
        assertThat(image.bytes()).isEqualTo(webp);
    }

    @Test
    @DisplayName("Mảng byte rỗng ném InvalidAvatarException")
    void inspect_EmptyBytes_ThrowsException() {
        assertThatThrownBy(() -> AvatarImage.inspect(new byte[0]))
                .isInstanceOf(InvalidAvatarException.class)
                .hasMessageContaining("Vui lòng chọn một ảnh đại diện.");

        assertThatThrownBy(() -> AvatarImage.inspect(null))
                .isInstanceOf(InvalidAvatarException.class)
                .hasMessageContaining("Vui lòng chọn một ảnh đại diện.");
    }

    @Test
    @DisplayName("File vượt quá 5 MiB ném InvalidAvatarException")
    void inspect_Exceeds5MiB_ThrowsException() {
        byte[] oversized = new byte[AvatarImage.MAX_BYTES + 1];

        assertThatThrownBy(() -> AvatarImage.inspect(oversized))
                .isInstanceOf(InvalidAvatarException.class)
                .hasMessageContaining("Ảnh đại diện tối đa 5 MiB.");
    }

    @Test
    @DisplayName("Nội dung không phải ảnh (text giả MIME) ném InvalidAvatarException")
    void inspect_FakeImageContent_ThrowsException() {
        byte[] fake = "<html><body>Not an image</body></html>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> AvatarImage.inspect(fake))
                .isInstanceOf(InvalidAvatarException.class)
                .hasMessageContaining("Chọn ảnh JPEG, PNG hoặc WebP tĩnh hợp lệ.");
    }

    @Test
    @DisplayName("Ảnh có kích thước 0 hoặc quá 16 triệu điểm ảnh bị từ chối")
    void validateDimensions_Invalid_ThrowsException() {
        assertThatThrownBy(() -> AvatarImage.validateDimensions(0, 10))
                .isInstanceOf(InvalidAvatarException.class)
                .hasMessageContaining("Ảnh đại diện không hợp lệ hoặc vượt quá 16 triệu điểm ảnh.");

        assertThatThrownBy(() -> AvatarImage.validateDimensions(5000, 5000)) // 25,000,000 > 16,000,000
                .isInstanceOf(InvalidAvatarException.class)
                .hasMessageContaining("Ảnh đại diện không hợp lệ hoặc vượt quá 16 triệu điểm ảnh.");
    }

    private static byte[] createValidVp8lWebp(int width, int height) {
        // Construct a minimal valid VP8L WebP
        long dimensions = ((long) (width - 1) & 16383) | (((long) (height - 1) & 16383) << 14);
        byte[] vp8lData = new byte[]{
                0x2f, // signature
                (byte) (dimensions & 0xFF),
                (byte) ((dimensions >> 8) & 0xFF),
                (byte) ((dimensions >> 16) & 0xFF),
                (byte) ((dimensions >> 24) & 0xFF)
        };
        int vp8lLen = vp8lData.length;
        int padding = vp8lLen % 2;
        int chunkLen = 8 + vp8lLen + padding;
        int totalLen = 12 + chunkLen;
        int riffLen = totalLen - 8;

        byte[] result = new byte[totalLen];
        // "RIFF"
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, result, 0, 4);
        result[4] = (byte) (riffLen & 0xFF);
        result[5] = (byte) ((riffLen >> 8) & 0xFF);
        result[6] = (byte) ((riffLen >> 16) & 0xFF);
        result[7] = (byte) ((riffLen >> 24) & 0xFF);
        // "WEBP"
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, result, 8, 4);
        // "VP8L"
        System.arraycopy("VP8L".getBytes(StandardCharsets.US_ASCII), 0, result, 12, 4);
        result[16] = (byte) (vp8lLen & 0xFF);
        result[17] = (byte) ((vp8lLen >> 8) & 0xFF);
        result[18] = (byte) ((vp8lLen >> 16) & 0xFF);
        result[19] = (byte) ((vp8lLen >> 24) & 0xFF);
        System.arraycopy(vp8lData, 0, result, 20, vp8lLen);
        return result;
    }
}
