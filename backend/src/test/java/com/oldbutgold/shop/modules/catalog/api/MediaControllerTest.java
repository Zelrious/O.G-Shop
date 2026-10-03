package com.oldbutgold.shop.modules.catalog.api;

import com.oldbutgold.shop.modules.catalog.application.MediaStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MediaControllerTest {
    @TempDir
    Path tempDir;

    private MediaController controller() {
        return new MediaController(new MediaStorageService(tempDir.resolve("media").toString(), "", "", "", ""));
    }

    @Test
    void publicEndpointServesExistingGeneratedMedia() throws Exception {
        MediaController controller = controller();
        String filename = UUID.randomUUID() + ".png";
        byte[] content = new byte[]{1, 2, 3};
        Files.write(tempDir.resolve("media").resolve(filename), content);
        MockMvc http = MockMvcBuilders.standaloneSetup(controller).build();

        http.perform(get("/api/v1/media/{filename}", filename))
                .andExpect(status().isOk())
                .andExpect(content().bytes(content));
    }

    @Test
    void missingMediaReturnsNotFound() {
        assertThat(controller().getMedia(UUID.randomUUID() + ".png").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void invalidNameReturnsNotFoundInsteadOfReadingOutsideFile() throws Exception {
        MediaController controller = controller();
        Files.writeString(tempDir.resolve("private.png"), "private-data");

        assertThat(controller.getMedia("../private.png").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void directoryWithMediaFilenameIsNotServed() throws Exception {
        MediaController controller = controller();
        String filename = UUID.randomUUID() + ".png";
        Files.createDirectory(tempDir.resolve("media").resolve(filename));

        assertThat(controller.getMedia(filename).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void symlinkToPrivateFileIsNotServed() throws Exception {
        MediaController controller = controller();
        String filename = UUID.randomUUID() + ".png";
        Path privateFile = tempDir.resolve("private.png");
        Files.writeString(privateFile, "private-data");
        try {
            Files.createSymbolicLink(tempDir.resolve("media").resolve(filename), privateFile);
        } catch (FileSystemException | UnsupportedOperationException unavailable) {
            assumeTrue(false, "Filesystem does not permit creating symbolic links");
        }

        assertThat(controller.getMedia(filename).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
