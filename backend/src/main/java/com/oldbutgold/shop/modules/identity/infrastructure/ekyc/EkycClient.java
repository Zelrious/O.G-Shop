package com.oldbutgold.shop.modules.identity.infrastructure.ekyc;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oldbutgold.shop.modules.identity.application.EkycUnavailableException;
import com.oldbutgold.shop.shared.config.EkycProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class EkycClient {
    private static final Logger log = LoggerFactory.getLogger(EkycClient.class);
    private final RestClient restClient;

    public EkycClient(EkycProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("X-Internal-Token", properties.internalToken())
                .build();
    }

    public OcrResult ocr(byte[] image, String filename, String contentType) {
        return ocr(image, filename, contentType, null, null, null, null, null, null);
    }

    public OcrResult ocr(byte[] image, String filename, String contentType,
                         byte[] backImage, String backFilename, String backContentType) {
        return ocr(image, filename, contentType, backImage, backFilename, backContentType, null, null, null);
    }

    public OcrResult ocr(byte[] image, String filename, String contentType,
                         byte[] backImage, String backFilename, String backContentType,
                         byte[] portraitImage, String portraitFilename, String portraitContentType) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(contentType));
        body.add("file", new HttpEntity<>(resource(image, filename), partHeaders));

        if (backImage != null && backImage.length > 0) {
            HttpHeaders backPartHeaders = new HttpHeaders();
            backPartHeaders.setContentType(MediaType.parseMediaType(backContentType != null ? backContentType : "image/jpeg"));
            body.add("back_file", new HttpEntity<>(resource(backImage, backFilename != null ? backFilename : "identity-card-back.jpg"), backPartHeaders));
        }

        if (portraitImage != null && portraitImage.length > 0) {
            HttpHeaders portraitPartHeaders = new HttpHeaders();
            portraitPartHeaders.setContentType(MediaType.parseMediaType(portraitContentType != null ? portraitContentType : "image/jpeg"));
            body.add("portrait_file", new HttpEntity<>(resource(portraitImage, portraitFilename != null ? portraitFilename : "portrait-card.jpg"), portraitPartHeaders));
        }

        try {
            OcrResult result = restClient.post()
                    .uri("/api/v1/ekyc/ocr")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(OcrResult.class);
            if (result == null) {
                throw new EkycUnavailableException("eKYC OCR service returned no response");
            }
            if (!"SUCCESS".equals(result.status())) {
                String errorMsg = result.errorMessage() != null && !result.errorMessage().isBlank()
                        ? result.errorMessage()
                        : "eKYC OCR did not complete successfully";
                throw new IllegalArgumentException(errorMsg);
            }
            return result;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (HttpStatusCodeException exception) {
            log.error("eKYC OCR failed with HTTP {}: {}", exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new EkycUnavailableException("eKYC OCR service error: " + exception.getStatusCode(), exception);
        } catch (RestClientException exception) {
            log.error("eKYC OCR service unavailable", exception);
            throw new EkycUnavailableException("eKYC OCR service is unavailable", exception);
        }
    }

    public MatchResult match(String cardImageBase64, String liveFrameBase64) {
        return match(cardImageBase64, liveFrameBase64, null, null);
    }

    public MatchResult match(String cardImageBase64, String liveFrameBase64, String portraitImageBase64) {
        return match(cardImageBase64, liveFrameBase64, portraitImageBase64, null);
    }

    public MatchResult match(String cardImageBase64, String liveFrameBase64, String portraitImageBase64, String sessionId) {
        return match(cardImageBase64, liveFrameBase64, portraitImageBase64, sessionId, null);
    }

    public MatchResult match(String cardImageBase64, String liveFrameBase64, String portraitImageBase64, String sessionId, String attemptId) {
        try {
            java.util.Map<String, Object> reqBody = new java.util.HashMap<>();
            reqBody.put("card_image_base64", cardImageBase64);
            reqBody.put("live_frame_base64", liveFrameBase64);
            reqBody.put("circle_center", new int[]{320, 240});
            reqBody.put("circle_radius", 140);
            if (portraitImageBase64 != null && !portraitImageBase64.isBlank()) {
                reqBody.put("portrait_image_base64", portraitImageBase64);
            }
            if (sessionId != null && !sessionId.isBlank()) {
                reqBody.put("session_id", sessionId);
            }
            if (attemptId != null && !attemptId.isBlank()) {
                reqBody.put("attempt_id", attemptId);
            }

            MatchResult result = restClient.post()
                    .uri("/api/v1/ekyc/match-face")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(reqBody)
                    .retrieve()
                    .body(MatchResult.class);
            if (result == null) {
                throw new EkycUnavailableException("eKYC matching returned no result");
            }
            return result;
        } catch (HttpStatusCodeException exception) {
            log.error("eKYC match-face failed with HTTP {}: {}", exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new EkycUnavailableException("eKYC matching service error: " + exception.getStatusCode(), exception);
        } catch (RestClientException exception) {
            log.error("eKYC matching service unavailable", exception);
            throw new EkycUnavailableException("eKYC matching service is unavailable", exception);
        }
    }

    private static ByteArrayResource resource(byte[] bytes, String filename) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename == null || filename.isBlank() ? "identity-card.jpg" : filename;
            }
        };
    }

    public record OcrResult(
            String status,
            @JsonProperty("error_code") String errorCode,
            @JsonProperty("error_message") String errorMessage,
            @JsonProperty("extracted_data") ExtractedData extractedData
    ) {
        public OcrResult(String status, String errorMessage, ExtractedData extractedData) {
            this(status, null, errorMessage, extractedData);
        }
    }

    public record ExtractedData(
            @JsonProperty("so_cccd") String cccdNumber,
            @JsonProperty("ho_va_ten") String fullName,
            @JsonProperty("ngay_sinh") String dob,
            @JsonProperty("gioi_tinh") String gender,
            @JsonProperty("que_quan") String hometown,
            @JsonProperty("noi_thuong_tru") String address,
            @JsonProperty("ai_standardized") Boolean aiStandardized,
            @JsonProperty("mrz_verified") Boolean mrzVerified
    ) {
        public ExtractedData(String cccdNumber, String fullName, String dob, String gender, String hometown, String address) {
            this(cccdNumber, fullName, dob, gender, hometown, address, false, false);
        }
        public ExtractedData(String cccdNumber, String fullName, String dob, String gender, String hometown, String address, Boolean aiStandardized) {
            this(cccdNumber, fullName, dob, gender, hometown, address, aiStandardized, false);
        }
    }

    public record MatchResult(
            @JsonProperty("match_status") String matchStatus,
            @JsonProperty("is_match") boolean match,
            double distance,
            @JsonProperty("confidence_score") double confidenceScore,
            @JsonProperty("threshold_used") double thresholdUsed,
            @JsonProperty("user_instruction") String userInstruction,
            @JsonProperty("model_name") String modelName,
            @JsonProperty("model_version") String modelVersion,
            @JsonProperty("is_simulated") boolean simulated,
            @JsonProperty("frame_status") String frameStatus,
            @JsonProperty("directional_cue") String directionalCue,
            @JsonProperty("distance_card") Double distanceCard,
            @JsonProperty("distance_portrait") Double distancePortrait,
            @JsonProperty("card_matched") Boolean cardMatched,
            @JsonProperty("portrait_matched") Boolean portraitMatched,
            @JsonProperty("liveness_status") String livenessStatus,
            @JsonProperty("frames_collected") Integer framesCollected
    ) {
        public MatchResult(String matchStatus, boolean match, double distance, double confidenceScore, double thresholdUsed, String userInstruction, String modelName, String modelVersion, boolean simulated) {
            this(matchStatus, match, distance, confidenceScore, thresholdUsed, userInstruction, modelName, modelVersion, simulated, null, null, null, null, null, null, "NOT_CHECKED", 1);
        }

        public MatchResult(String matchStatus, boolean match, double distance, double confidenceScore, double thresholdUsed, String userInstruction, String modelName, String modelVersion, boolean simulated, String frameStatus, String directionalCue) {
            this(matchStatus, match, distance, confidenceScore, thresholdUsed, userInstruction, modelName, modelVersion, simulated, frameStatus, directionalCue, null, null, null, null, "NOT_CHECKED", 1);
        }
    }
}
