package com.oldbutgold.shop.modules.trustsafety.infrastructure.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LocalEvidenceStorageVerifierTest - Real SHA-256 & Path Traversal Guard")
class LocalEvidenceStorageVerifierTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Computes real SHA-256 hex digest for existing regular file inside storage root")
    void computeSha256_existingFile_returnsExpectedDigest() throws Exception {
        Path subDir = tempDir.resolve("evidence");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("proof.txt"), "hello", StandardCharsets.UTF_8);

        LocalEvidenceStorageVerifier verifier = new LocalEvidenceStorageVerifier(tempDir);
        String digest = verifier.computeSha256("evidence/proof.txt");

        // SHA-256("hello")
        assertThat(digest).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    @DisplayName("Returns null for missing file or blank key")
    void computeSha256_missingOrBlank_returnsNull() throws Exception {
        LocalEvidenceStorageVerifier verifier = new LocalEvidenceStorageVerifier(tempDir);

        assertThat(verifier.computeSha256("evidence/does-not-exist.jpg")).isNull();
        assertThat(verifier.computeSha256("   ")).isNull();
        assertThat(verifier.computeSha256(null)).isNull();
    }

    @Test
    @DisplayName("Rejects path traversal and external URI schemes fail-closed")
    void computeSha256_traversalOrExternalScheme_throwsIOException() {
        LocalEvidenceStorageVerifier verifier = new LocalEvidenceStorageVerifier(tempDir);

        assertThatThrownBy(() -> verifier.computeSha256("../outside.txt"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Path traversal");

        assertThatThrownBy(() -> verifier.computeSha256("s3://bucket/evidence.jpg"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("outside local storage root");

        assertThatThrownBy(() -> verifier.computeSha256("/etc/passwd"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("outside local storage root");
    }
}
