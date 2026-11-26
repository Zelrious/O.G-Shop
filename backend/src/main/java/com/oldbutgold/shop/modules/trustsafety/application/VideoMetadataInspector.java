package com.oldbutgold.shop.modules.trustsafety.application;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.OptionalInt;

/**
 * Extracts verified video duration directly from MP4 (ISO BMFF mvhd) and WebM (EBML Segment/Info)
 * container metadata rather than trusting client-supplied durationSeconds or falling back to defaults.
 */
final class VideoMetadataInspector {

    private static final int BOX_FTYP = 0x66747970; // "ftyp"
    private static final int BOX_MOOV = 0x6D6F6F76; // "moov"
    private static final int BOX_MVHD = 0x6D766864; // "mvhd"

    private static final long EBML_HEADER_ID = 0x1A45DFA3L;
    private static final long EBML_SEGMENT_ID = 0x18538067L;
    private static final long EBML_INFO_ID = 0x1549A966L;
    private static final long EBML_TIMESTAMP_SCALE_ID = 0x2AD7B1L;
    private static final long EBML_DURATION_ID = 0x4489L;
    private static final long EBML_UNKNOWN_SIZE = -2L;

    private VideoMetadataInspector() {}

    static OptionalInt extractDurationSeconds(Path videoPath, String contentType) {
        if (videoPath == null || contentType == null) {
            return OptionalInt.empty();
        }
        try (FileChannel channel = FileChannel.open(videoPath, StandardOpenOption.READ)) {
            if ("video/mp4".equalsIgnoreCase(contentType)) {
                return extractMp4DurationSeconds(channel);
            }
            if ("video/webm".equalsIgnoreCase(contentType)) {
                return extractWebmDurationSeconds(channel);
            }
            return OptionalInt.empty();
        } catch (IOException | RuntimeException ex) {
            return OptionalInt.empty();
        }
    }

    private static OptionalInt extractMp4DurationSeconds(FileChannel channel) throws IOException {
        long fileSize = channel.size();
        if (fileSize < 16) {
            return OptionalInt.empty();
        }

        // Require ISO BMFF ftyp box header at offset 4
        ByteBuffer firstHeader = readFully(channel, 0, 8);
        if (firstHeader == null || firstHeader.getInt(4) != BOX_FTYP) {
            return OptionalInt.empty();
        }

        long pos = 0;
        while (pos + 8 <= fileSize) {
            Mp4Box box = readMp4Box(channel, pos, fileSize);
            if (box == null) {
                break;
            }
            if (box.type == BOX_MOOV) {
                return findMvhdDuration(channel, box.payloadStart, box.end);
            }
            pos = box.end;
        }
        return OptionalInt.empty();
    }

    private static OptionalInt findMvhdDuration(FileChannel channel, long start, long end) throws IOException {
        long pos = start;
        while (pos + 8 <= end) {
            Mp4Box box = readMp4Box(channel, pos, end);
            if (box == null) {
                break;
            }
            if (box.type == BOX_MVHD) {
                long payloadLen = box.end - box.payloadStart;
                if (payloadLen < 20) {
                    return OptionalInt.empty();
                }
                int readLen = (int) Math.min(32L, payloadLen);
                ByteBuffer buf = readFully(channel, box.payloadStart, readLen);
                if (buf == null) {
                    return OptionalInt.empty();
                }
                int version = buf.get(0) & 0xFF;
                long timescale;
                long duration;
                if (version == 0 && readLen >= 20) {
                    timescale = Integer.toUnsignedLong(buf.getInt(12));
                    duration = Integer.toUnsignedLong(buf.getInt(16));
                    if (duration == 0xFFFFFFFFL) {
                        return OptionalInt.empty();
                    }
                } else if (version == 1 && readLen >= 32) {
                    timescale = Integer.toUnsignedLong(buf.getInt(20));
                    duration = buf.getLong(24);
                    if (duration < 0) {
                        return OptionalInt.empty();
                    }
                } else {
                    return OptionalInt.empty();
                }
                if (timescale <= 0 || duration <= 0) {
                    return OptionalInt.empty();
                }
                double seconds = (double) duration / (double) timescale;
                if (!Double.isFinite(seconds) || seconds <= 0d) {
                    return OptionalInt.empty();
                }
                return OptionalInt.of(seconds >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.ceil(seconds));
            }
            pos = box.end;
        }
        return OptionalInt.empty();
    }

    private static Mp4Box readMp4Box(FileChannel channel, long pos, long limit) throws IOException {
        ByteBuffer hdr = readFully(channel, pos, 8);
        if (hdr == null) {
            return null;
        }
        long boxSize = Integer.toUnsignedLong(hdr.getInt(0));
        int type = hdr.getInt(4);
        long headerSize = 8;
        if (boxSize == 1L) {
            if (pos + 16 > limit) {
                return null;
            }
            ByteBuffer ext = readFully(channel, pos + 8, 8);
            if (ext == null) {
                return null;
            }
            boxSize = ext.getLong(0);
            headerSize = 16;
        } else if (boxSize == 0L) {
            boxSize = limit - pos;
        }
        if (boxSize < headerSize || pos + boxSize > limit) {
            return null;
        }
        return new Mp4Box(type, pos + headerSize, pos + boxSize);
    }

    private static OptionalInt extractWebmDurationSeconds(FileChannel channel) throws IOException {
        long fileSize = channel.size();
        if (fileSize < 12) {
            return OptionalInt.empty();
        }
        channel.position(0);
        long firstId = readEbmlId(channel, fileSize);
        if (firstId != EBML_HEADER_ID) {
            return OptionalInt.empty();
        }
        long firstSize = readEbmlSize(channel, fileSize);
        if (firstSize < 0) {
            return OptionalInt.empty();
        }
        long pos = channel.position() + firstSize;
        while (pos < fileSize) {
            channel.position(pos);
            long id = readEbmlId(channel, fileSize);
            if (id < 0) {
                break;
            }
            long size = readEbmlSize(channel, fileSize);
            if (size == -1L) {
                break;
            }
            long payloadStart = channel.position();
            long end = size == EBML_UNKNOWN_SIZE ? fileSize : Math.min(fileSize, payloadStart + size);
            if (end < payloadStart) {
                break;
            }
            if (id == EBML_SEGMENT_ID) {
                return findWebmSegmentInfoDuration(channel, payloadStart, end);
            }
            pos = end;
        }
        return OptionalInt.empty();
    }

    private static OptionalInt findWebmSegmentInfoDuration(FileChannel channel, long start, long end) throws IOException {
        long pos = start;
        while (pos < end) {
            channel.position(pos);
            long id = readEbmlId(channel, end);
            if (id < 0) {
                break;
            }
            long size = readEbmlSize(channel, end);
            if (size == -1L) {
                break;
            }
            long payloadStart = channel.position();
            long elemEnd = size == EBML_UNKNOWN_SIZE ? end : Math.min(end, payloadStart + size);
            if (elemEnd < payloadStart) {
                break;
            }
            if (id == EBML_INFO_ID) {
                return readWebmInfoDuration(channel, payloadStart, elemEnd);
            }
            pos = elemEnd;
        }
        return OptionalInt.empty();
    }

    private static OptionalInt readWebmInfoDuration(FileChannel channel, long start, long end) throws IOException {
        long timestampScaleNs = 1_000_000L; // Matroska/WebM default: 1ms per tick
        Double durationTicks = null;

        long pos = start;
        while (pos < end) {
            channel.position(pos);
            long id = readEbmlId(channel, end);
            if (id < 0) {
                break;
            }
            long size = readEbmlSize(channel, end);
            if (size < 0) {
                break;
            }
            long payloadStart = channel.position();
            long elemEnd = payloadStart + size;
            if (elemEnd > end) {
                break;
            }
            if (id == EBML_TIMESTAMP_SCALE_ID && size >= 1 && size <= 8) {
                ByteBuffer buf = readFully(channel, payloadStart, (int) size);
                if (buf != null) {
                    long scale = 0;
                    for (int i = 0; i < size; i++) {
                        scale = (scale << 8) | (buf.get(i) & 0xFFL);
                    }
                    if (scale > 0) {
                        timestampScaleNs = scale;
                    }
                }
            } else if (id == EBML_DURATION_ID && (size == 4 || size == 8)) {
                ByteBuffer buf = readFully(channel, payloadStart, (int) size);
                if (buf != null) {
                    durationTicks = (size == 4)
                            ? (double) Float.intBitsToFloat(buf.getInt(0))
                            : Double.longBitsToDouble(buf.getLong(0));
                }
            }
            pos = elemEnd;
        }

        if (durationTicks == null || !Double.isFinite(durationTicks) || durationTicks <= 0d || timestampScaleNs <= 0L) {
            return OptionalInt.empty();
        }
        double seconds = durationTicks * (timestampScaleNs / 1_000_000_000.0d);
        if (!Double.isFinite(seconds) || seconds <= 0d) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(seconds >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.ceil(seconds));
    }

    private static long readEbmlId(FileChannel channel, long limit) throws IOException {
        long pos = channel.position();
        if (pos >= limit) {
            return -1L;
        }
        ByteBuffer b0Buf = readFully(channel, pos, 1);
        if (b0Buf == null) {
            return -1L;
        }
        int b0 = b0Buf.get(0) & 0xFF;
        if (b0 == 0) {
            return -1L;
        }
        int extra = Integer.numberOfLeadingZeros(b0) - 24;
        if (extra < 0 || extra > 3 || pos + 1 + extra > limit) {
            return -1L;
        }
        long id = b0;
        if (extra > 0) {
            ByteBuffer rest = readFully(channel, pos + 1, extra);
            if (rest == null) {
                return -1L;
            }
            for (int i = 0; i < extra; i++) {
                id = (id << 8) | (rest.get(i) & 0xFFL);
            }
        }
        channel.position(pos + 1 + extra);
        return id;
    }

    private static long readEbmlSize(FileChannel channel, long limit) throws IOException {
        long pos = channel.position();
        if (pos >= limit) {
            return -1L;
        }
        ByteBuffer b0Buf = readFully(channel, pos, 1);
        if (b0Buf == null) {
            return -1L;
        }
        int b0 = b0Buf.get(0) & 0xFF;
        if (b0 == 0) {
            return -1L;
        }
        int extra = Integer.numberOfLeadingZeros(b0) - 24;
        if (extra < 0 || extra > 7 || pos + 1 + extra > limit) {
            return -1L;
        }
        int mask = (1 << (7 - extra)) - 1;
        long val = b0 & mask;
        if (extra > 0) {
            ByteBuffer rest = readFully(channel, pos + 1, extra);
            if (rest == null) {
                return -1L;
            }
            for (int i = 0; i < extra; i++) {
                val = (val << 8) | (rest.get(i) & 0xFFL);
            }
        }
        channel.position(pos + 1 + extra);
        long unknownSentinel = (1L << (7 * (extra + 1))) - 1L;
        return val == unknownSentinel ? EBML_UNKNOWN_SIZE : val;
    }

    private static ByteBuffer readFully(FileChannel channel, long position, int length) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(length);
        long offset = position;
        while (buf.hasRemaining()) {
            int n = channel.read(buf, offset);
            if (n < 0) {
                return null;
            }
            offset += n;
        }
        buf.flip();
        return buf;
    }

    private record Mp4Box(int type, long payloadStart, long end) {}
}

