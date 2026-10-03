package com.oldbutgold.shop.modules.identity.application;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;

public record AvatarImage(byte[] bytes, String format) {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final long MAX_PIXELS = 16_000_000;
    private static final byte[] PNG_SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    public static AvatarImage inspect(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new InvalidAvatarException("Vui lòng chọn một ảnh đại diện.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new InvalidAvatarException("Ảnh đại diện tối đa 5 MiB.");
        }
        if (bytes.length >= 12 && tag(bytes, 0).equals("RIFF") && tag(bytes, 8).equals("WEBP")) {
            validateWebp(bytes);
            return new AvatarImage(bytes, "webp");
        }
        boolean png = bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8), PNG_SIGNATURE);
        boolean jpeg = bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216
                && (bytes[2] & 255) == 255;
        if (!png && !jpeg) {
            throw invalidImage();
        }
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalidImage();
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                validateDimensions(reader.getWidth(0), reader.getHeight(0));
                if (reader.read(0) == null) throw invalidImage();
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidImage();
        }
        return new AvatarImage(bytes, png ? "png" : "jpg");
    }

    // Java's built-in ImageIO does not decode WebP. Check its container/frame header here;
    // the Cloudinary image upload must also decode it and return validated image metadata.
    private static void validateWebp(byte[] bytes) {
        if (littleEndian(bytes, 4, 4) + 8 != bytes.length) throw invalidImage();
        int offset = 12;
        int frames = 0;
        while (offset + 8 <= bytes.length) {
            String chunk = tag(bytes, offset);
            long length = littleEndian(bytes, offset + 4, 4);
            if (length > bytes.length - offset - 8) throw invalidImage();
            int start = offset + 8;
            if (chunk.equals("VP8X")) {
                if (length != 10 || (bytes[start] & 2) != 0) throw invalidImage();
                validateDimensions(littleEndian(bytes, start + 4, 3) + 1, littleEndian(bytes, start + 7, 3) + 1);
            } else if (chunk.equals("VP8 ")) {
                if (length < 10 || (bytes[start] & 1) != 0 || (bytes[start + 3] & 255) != 157
                        || bytes[start + 4] != 1 || bytes[start + 5] != 42) throw invalidImage();
                validateDimensions(littleEndian(bytes, start + 6, 2) & 16383, littleEndian(bytes, start + 8, 2) & 16383);
                frames++;
            } else if (chunk.equals("VP8L")) {
                if (length < 5 || bytes[start] != 47) throw invalidImage();
                long dimensions = littleEndian(bytes, start + 1, 4);
                if ((dimensions >>> 29) != 0) throw invalidImage();
                validateDimensions((dimensions & 16383) + 1, ((dimensions >>> 14) & 16383) + 1);
                frames++;
            }
            offset += 8 + (int) length + (int) (length % 2);
        }
        if (offset != bytes.length || frames != 1) throw invalidImage();
    }

    public static void validateDimensions(long width, long height) {
        if (width <= 0 || height <= 0 || width > MAX_PIXELS || height > MAX_PIXELS
                || width * height > MAX_PIXELS) {
            throw new InvalidAvatarException("Ảnh đại diện không hợp lệ hoặc vượt quá 16 triệu điểm ảnh.");
        }
    }

    private static long littleEndian(byte[] bytes, int offset, int count) {
        long value = 0;
        for (int i = 0; i < count; i++) value |= (long) (bytes[offset + i] & 255) << (i * 8);
        return value;
    }

    private static String tag(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }

    private static InvalidAvatarException invalidImage() {
        return new InvalidAvatarException("Chọn ảnh JPEG, PNG hoặc WebP tĩnh hợp lệ.");
    }
}
