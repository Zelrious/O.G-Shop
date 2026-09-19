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
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class EkycClient {
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
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(contentType));
        body.add("file", new HttpEntity<>(resource(image, filename), partHeaders));
        try {
            OcrResult result = restClient.post()
                    .uri("/api/v1/ekyc/ocr")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(OcrResult.class);
            if (result == null || !"SUCCESS".equals(result.status())) {
                throw new EkycUnavailableException("eKYC OCR did not complete successfully");
            }
            return result;
        } catch (RestClientException exception) {
            throw new EkycUnavailableException("eKYC OCR service is unavailable", exception);
        }
    }

    public MatchResult match(String cardImageBase64, String liveFrameBase64) {
        try {
            MatchResult result = restClient.post()
                    .uri("/api/v1/ekyc/match-face")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "card_image_base64", cardImageBase64,
                            "live_frame_base64", liveFrameBase64,
                            "circle_center", new int[]{320, 240},
                            "circle_radius", 140
                    ))
                    .retrieve()
                    .body(MatchResult.class);
            if (result == null) {
                throw new EkycUnavailableException("eKYC matching returned no result");
            }
            return result;
        } catch (RestClientException exception) {
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
            @JsonProperty("error_message") String errorMessage,
            @JsonProperty("extracted_data") ExtractedData extractedData
    ) {
    }

    public record ExtractedData(
            @JsonProperty("so_cccd") String cccdNumber,
            @JsonProperty("ho_va_ten") String fullName,
            @JsonProperty("ngay_sinh") String dob,
            @JsonProperty("gioi_tinh") String gender,
            @JsonProperty("que_quan") String hometown,
            @JsonProperty("noi_thuong_tru") String address
    ) {
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
            @JsonProperty("is_simulated") boolean simulated
    ) {
    }
}
