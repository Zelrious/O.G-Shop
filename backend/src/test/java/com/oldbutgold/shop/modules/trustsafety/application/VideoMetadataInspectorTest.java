package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentRepository;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class VideoMetadataInspectorTest {

    @TempDir
    Path tempDir;

    private ReturnCaseService createService() {
        return new ReturnCaseService(
                mock(CaseRepository.class),
                mock(CaseRoundRepository.class),
                mock(CaseEvidenceRepository.class),
                mock(CaseDecisionRepository.class),
                mock(CaseEventRepository.class),
                mock(OrderRepository.class),
                mock(PaymentRepository.class),
                mock(ShipmentRepository.class),
                mock(EntityManager.class),
                mock(JdbcTemplate.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                Clock.systemUTC(),
                mock(ReturnWorkflowLockGateway.class),
                mock(RoundDecisionSettlementPort.class),
                mock(PlatformTransactionManager.class),
                mock(CaseEvidenceRequestRepository.class)
        );
    }

    static byte[] buildMp4Bytes(int timescale, long duration, boolean version1) {
        byte[] ftyp = new byte[16];
        ByteBuffer.wrap(ftyp).putInt(16).put("ftyp".getBytes(StandardCharsets.US_ASCII))
                .put("isom".getBytes(StandardCharsets.US_ASCII)).putInt(512);

        int mvhdLen = version1 ? 120 : 108;
        byte[] mvhd = new byte[mvhdLen];
        ByteBuffer m = ByteBuffer.wrap(mvhd);
        m.putInt(mvhdLen).put("mvhd".getBytes(StandardCharsets.US_ASCII));
        if (version1) {
            m.put((byte) 1).put(new byte[3]); // version 1 + flags
            m.putLong(0L).putLong(0L); // creation & modification
            m.putInt(timescale).putLong(duration);
        } else {
            m.put((byte) 0).put(new byte[3]); // version 0 + flags
            m.putInt(0).putInt(0); // creation & modification
            m.putInt(timescale).putInt((int) duration);
        }

        byte[] moov = new byte[8 + mvhdLen];
        ByteBuffer mv = ByteBuffer.wrap(moov);
        mv.putInt(moov.length).put("moov".getBytes(StandardCharsets.US_ASCII)).put(mvhd);

        byte[] all = new byte[ftyp.length + moov.length];
        System.arraycopy(ftyp, 0, all, 0, ftyp.length);
        System.arraycopy(moov, 0, all, ftyp.length, moov.length);
        return all;
    }

    static byte[] buildWebmBytes(long timestampScaleNs, double durationTicks) {
        // Minimal EBML header (ID 0x1A45DFA3, size 0) + Segment (0x18538067) + Info (0x1549A966)
        // TimestampScale: ID 0x2AD7B1, 4-byte uint; Duration: ID 0x4489, 8-byte double
        ByteBuffer tsScaleElem = ByteBuffer.allocate(12);
        tsScaleElem.put((byte) 0x2A).put((byte) 0xD7).put((byte) 0xB1);
        tsScaleElem.put((byte) 0x84); // size = 4
        tsScaleElem.putInt((int) timestampScaleNs);

        ByteBuffer durElem = ByteBuffer.allocate(11);
        durElem.put((byte) 0x44).put((byte) 0x89);
        durElem.put((byte) 0x88); // size = 8
        durElem.putDouble(durationTicks);

        int infoPayloadSize = 8 + 11; // TimestampScale (3+1+4=8) + Duration (2+1+8=11)
        ByteBuffer info = ByteBuffer.allocate(4 + 1 + infoPayloadSize);
        info.putInt(0x1549A966);
        info.put((byte) (0x80 | infoPayloadSize));
        info.put(tsScaleElem.array(), 0, 8);
        info.put(durElem.array(), 0, 11);

        int segPayloadSize = info.capacity();
        ByteBuffer out = ByteBuffer.allocate(5 + 5 + segPayloadSize);
        out.putInt(0x1A45DFA3).put((byte) 0x80); // EBML header size 0
        out.putInt(0x18538067).put((byte) (0x80 | segPayloadSize));
        out.put(info.array());
        return out.array();
    }

    @Test
    void inspectsMp4AndWebmAndRejectsMissingOrOver60SecondsRegardlessOfClientDuration() throws Exception {
        Path mp4 = tempDir.resolve("valid.mp4");
        Files.write(mp4, buildMp4Bytes(1000, 45_200, false));
        assertThat(VideoMetadataInspector.extractDurationSeconds(mp4, "video/mp4")).hasValue(46);

        Path mp4v1 = tempDir.resolve("valid-v1.mp4");
        Files.write(mp4v1, buildMp4Bytes(1000, 60_000, true));
        assertThat(VideoMetadataInspector.extractDurationSeconds(mp4v1, "video/mp4")).hasValue(60);

        Path webm = tempDir.resolve("valid.webm");
        Files.write(webm, buildWebmBytes(1_000_000L, 18_500.0d));
        assertThat(VideoMetadataInspector.extractDurationSeconds(webm, "video/webm")).hasValue(19);

        ReturnCaseService service = createService();
        String mp4Key = null;
        String webmKey = null;
        try {
            MockMultipartFile validMp4 = new MockMultipartFile("file", "unbox.mp4", "video/mp4", buildMp4Bytes(1000, 45_200, false));
            ReturnCaseDtos.EvidenceItemDto mp4Dto = service.uploadReturnEvidence(10L, validMp4, "VIDEO", null);
            mp4Key = mp4Dto.objectKey();
            assertThat(mp4Dto.durationSeconds()).isEqualTo(46);

            MockMultipartFile validWebm = new MockMultipartFile("file", "unbox.webm", "video/webm", buildWebmBytes(1_000_000L, 18_500.0d));
            ReturnCaseDtos.EvidenceItemDto webmDto = service.uploadReturnEvidence(10L, validWebm, "VIDEO", null);
            webmKey = webmDto.objectKey();
            assertThat(webmDto.durationSeconds()).isEqualTo(19);

            // Video > 60s MUST be rejected even when client spoofs durationSeconds = 10
            MockMultipartFile overLimitSpoofed = new MockMultipartFile("file", "long.mp4", "video/mp4", buildMp4Bytes(1000, 75_000, false));
            assertThatThrownBy(() -> service.uploadReturnEvidence(10L, overLimitSpoofed, "VIDEO", 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("60");

            // Video lacking duration metadata MUST be rejected rather than defaulting to 30s
            byte[] ftypOnlyMp4 = new byte[16];
            ByteBuffer.wrap(ftypOnlyMp4).putInt(16)
                    .put("ftyp".getBytes(StandardCharsets.US_ASCII))
                    .put("isom".getBytes(StandardCharsets.US_ASCII))
                    .putInt(512);
            MockMultipartFile missingMetadata = new MockMultipartFile("file", "nometa.mp4", "video/mp4", ftypOnlyMp4);
            assertThatThrownBy(() -> service.uploadReturnEvidence(10L, missingMetadata, "VIDEO", 15))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("metadata");
        } finally {
            for (String key : new String[]{mp4Key, webmKey}) {
                if (key != null && key.contains("/")) {
                    String filename = key.substring(key.lastIndexOf('/') + 1);
                    Files.deleteIfExists(java.nio.file.Paths.get("uploads/media", filename));
                    Files.deleteIfExists(java.nio.file.Paths.get("uploads/private-evidence", filename));
                }
            }
        }
    }
}
