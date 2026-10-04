package com.oldbutgold.shop.modules.catalog.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

class MediaStorageServiceTest {
    @TempDir
    Path tempDir;

    private MediaStorageService storage() {
        return new MediaStorageService(tempDir.resolve("media").toString(), "", "", "", "");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "../outside.png", "..\\outside.png", "/etc/passwd", "C:\\Windows\\win.ini",
            "\\\\server\\share\\image.png", "subdirectory/image.png", ".env", "image.png",
            "image.png:secret", "%2e%2e%2foutside.png", "..%5coutside.png"
    })
    void rejectsClientControlledPaths(String filename) {
        MediaStorageService storage = storage();

        assertThatThrownBy(() -> storage.load(filename)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parentTraversalCannotResolveExistingOutsideFile() throws Exception {
        MediaStorageService storage = storage();
        Path outside = tempDir.resolve("outside.png");
        Files.writeString(outside, "private-data");

        assertThatThrownBy(() -> storage.load("../outside.png"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readString(outside)).isEqualTo("private-data");
    }

    @Test
    void generatedLegacyJpegNameRemainsLoadableInsideRoot() {
        MediaStorageService storage = storage();
        String filename = UUID.randomUUID() + ".jpeg";

        assertThat(storage.load(filename)).isEqualTo(tempDir.resolve("media").resolve(filename));
    }

    @ParameterizedTest
    @CsvSource({
            "IMAGE,image/jpeg,jpg", "IMAGE,image/png,png", "IMAGE,image/webp,webp",
            "VIDEO,video/mp4,mp4", "VIDEO,video/webm,webm"
    })
    void uploadUsesServerGeneratedNameAndMimeSuffix(String mediaType, String mime, String suffix) throws Exception {
        MediaStorageService storage = storage();
        byte[] content = "fixture-media".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "../../outside.html", mime, content);

        MediaStorageService.StoredMedia stored = storage.store(file, mediaType);
        String filename = stored.mediaUrl().substring("/api/v1/media/".length());

        assertThat(filename).matches("[0-9a-f-]{36}\\." + suffix);
        assertThat(Files.readAllBytes(storage.load(filename))).isEqualTo(content);
        assertThat(Files.exists(tempDir.resolve("outside.html"))).isFalse();
        try (var entries = Files.list(tempDir.resolve("media"))) {
            assertThat(entries.toList()).hasSize(1);
        }
    }

    @Test
    void invalidMimeCannotCreateLocalFile() throws Exception {
        MediaStorageService storage = storage();
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "text/html", new byte[]{1});

        assertThatThrownBy(() -> storage.store(file, "IMAGE")).isInstanceOf(IllegalArgumentException.class);
        try (var entries = Files.list(tempDir.resolve("media"))) {
            assertThat(entries.toList()).isEmpty();
        }
    }

    @Test
    void uploadCannotOverwriteAnExistingFileEvenOnIdCollision() throws Exception {
        MediaStorageService storage = storage();
        UUID existingId = UUID.randomUUID();
        Path existingFile = tempDir.resolve("media").resolve(existingId + ".png");
        Files.writeString(existingFile, "original-data");
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});

        try (var uuid = mockStatic(UUID.class, CALLS_REAL_METHODS)) {
            uuid.when(UUID::randomUUID).thenReturn(existingId);
            assertThatThrownBy(() -> storage.store(file, "IMAGE"))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(FileAlreadyExistsException.class);
        }
        assertThat(Files.readString(existingFile)).isEqualTo("original-data");
    }
}
