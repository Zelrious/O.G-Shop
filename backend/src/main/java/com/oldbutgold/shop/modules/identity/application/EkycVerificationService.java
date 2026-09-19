package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.identity.infrastructure.ekyc.EkycClient;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Set;

@Service
public class EkycVerificationService {
    private static final long MAX_IMAGE_BYTES = 4L * 1024 * 1024;
    private static final long MAX_LIVE_FRAME_CHARS = 8L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final EkycClient client;
    private final SellerVerificationWriter writer;

    public EkycVerificationService(EkycClient client, SellerVerificationWriter writer) {
        this.client = client;
        this.writer = writer;
    }

    public EkycClient.OcrResult ocr(byte[] image, String filename, String contentType) {
        validateImage(image, contentType);
        return client.ocr(image, filename, contentType);
    }

    public VerificationResult verify(long userId, byte[] cardImage, String filename,
                                     String contentType, String liveFrameBase64) {
        validateImage(cardImage, contentType);
        if (liveFrameBase64 == null || liveFrameBase64.isBlank()
                || liveFrameBase64.length() > MAX_LIVE_FRAME_CHARS) {
            throw new IllegalArgumentException("Live frame is missing or too large");
        }
        EkycClient.OcrResult ocr = client.ocr(cardImage, filename, contentType);
        String cardBase64 = Base64.getEncoder().encodeToString(cardImage);
        EkycClient.MatchResult match = client.match(cardBase64, liveFrameBase64);
        Long verificationId = null;
        if (match.match()) {
            verificationId = writer.recordVerified(userId, match);
        }
        return new VerificationResult(verificationId, ocr.extractedData(), match);
    }

    private static void validateImage(byte[] image, String contentType) {
        if (image == null || image.length == 0 || image.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Image is empty or exceeds 4 MB");
        }
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported image content type");
        }
    }

    public record VerificationResult(
            Long verificationId,
            EkycClient.ExtractedData extractedData,
            EkycClient.MatchResult match
    ) {
    }
}
