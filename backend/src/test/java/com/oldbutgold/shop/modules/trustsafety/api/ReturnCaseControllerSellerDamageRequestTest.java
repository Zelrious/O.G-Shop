package com.oldbutgold.shop.modules.trustsafety.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oldbutgold.shop.modules.trustsafety.application.CaseEvidenceRequestService;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseDtos;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseService;
import com.oldbutgold.shop.modules.trustsafety.application.TrustSafetyBusinessException;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.ReturnReasonCode;
import com.oldbutgold.shop.shared.api.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReturnCaseControllerSellerDamageRequestTest - Controller MockMvc Unit Tests for Task 3.2.2 damage-request")
class ReturnCaseControllerSellerDamageRequestTest {

    @Mock
    private ReturnCaseService returnCaseService;

    @Mock
    private CaseEvidenceRequestService caseEvidenceRequestService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Long sellerId = 9001L;
    private final Long caseId = 100245L;

    @BeforeEach
    void setUp() {
        ReturnCaseController controller = new ReturnCaseController(returnCaseService, caseEvidenceRequestService);

        HandlerMethodArgumentResolver jwtResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                        && parameter.getParameterType().isAssignableFrom(Jwt.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return Jwt.withTokenValue("mock-jwt-token")
                        .header("alg", "none")
                        .claim("sub", sellerId.toString())
                        .claim("roles", List.of("ROLE_SELLER"))
                        .build();
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(jwtResolver)
                .build();
    }

    private ReturnCaseDtos.ReturnCaseDetailResponse createMockDetailResponse() {
        return new ReturnCaseDtos.ReturnCaseDetailResponse(
                caseId,
                555L,
                "RETURN",
                "POST_RETURN",
                ReturnReasonCode.DAMAGED_OR_DEFECTIVE,
                "Lý do trả hàng",
                "Lý do trả hàng chi tiết",
                sellerId,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                Instant.now(),
                Instant.now(),
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                Collections.emptyList()
        );
    }

    private ReturnCaseDtos.EvidenceItemDto createSampleEvidence(String mediaType, String objectKey) {
        return new ReturnCaseDtos.EvidenceItemDto(
                mediaType,
                objectKey,
                "Mô tả bằng chứng",
                "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
                1024L,
                "VIDEO".equalsIgnoreCase(mediaType) ? 30 : null
        );
    }

    @Test
    @DisplayName("API damage-request - Thành công trả về HTTP 200 OK với trạng thái POST_RETURN")
    void submitDamageExemptionRequest_success_returns200Ok() throws Exception {
        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Hàng trả về bị bể màn hình nghiêm trọng, người mua đóng gói không cẩn thận",
                List.of(createSampleEvidence("IMAGE", "evidence/broken_screen.jpg")),
                "CMD_SELLER_DMG_100245"
        );

        when(returnCaseService.submitDamageExemptionRequest(eq(sellerId), eq(caseId), any()))
                .thenReturn(createMockDetailResponse());

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/seller/damage-request", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(caseId))
                .andExpect(jsonPath("$.state").value("POST_RETURN"));
    }

    @Test
    @DisplayName("API damage-request - Lý do quá ngắn (< 5 ký tự) trả về HTTP 400 Bad Request")
    void submitDamageExemptionRequest_invalidReason_tooShort_returns400BadRequest() throws Exception {
        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "abc",
                List.of(createSampleEvidence("IMAGE", "evidence/broken_screen.jpg")),
                "CMD_VALID_KEY"
        );

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/seller/damage-request", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("API damage-request - Lý do để trống hoặc null trả về HTTP 400 Bad Request")
    void submitDamageExemptionRequest_invalidReason_nullOrBlank_returns400BadRequest() throws Exception {
        String invalidJson = "{\"reason\": null, \"evidenceList\": [{\"mediaType\":\"IMAGE\",\"objectKey\":\"e/1.jpg\"}], \"commandKey\": \"CMD_VALID\"}";

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/seller/damage-request", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("API damage-request - Danh sách bằng chứng rỗng trả về HTTP 400 Bad Request")
    void submitDamageExemptionRequest_emptyEvidence_returns400BadRequest() throws Exception {
        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Hàng trả về bị hỏng nặng",
                Collections.emptyList(),
                "CMD_EMPTY_EV"
        );

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/seller/damage-request", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("API damage-request - commandKey vượt quá 150 ký tự trả về HTTP 400 Bad Request")
    void submitDamageExemptionRequest_commandKey_tooLong_returns400BadRequest() throws Exception {
        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Hàng trả về bị hỏng nặng",
                List.of(createSampleEvidence("IMAGE", "evidence/broken.jpg")),
                "CMD_" + "X".repeat(148) // 152 chars > 150
        );

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/seller/damage-request", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("API damage-request - Số lượng ảnh vượt quá 5 trả về HTTP 400 Bad Request")
    void submitDamageExemptionRequest_quotaExceeded_returns400BadRequest() throws Exception {
        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Hàng trả về bị vỡ nhiều vị trí",
                List.of(
                        createSampleEvidence("IMAGE", "e/1.jpg"),
                        createSampleEvidence("IMAGE", "e/2.jpg"),
                        createSampleEvidence("IMAGE", "e/3.jpg"),
                        createSampleEvidence("IMAGE", "e/4.jpg"),
                        createSampleEvidence("IMAGE", "e/5.jpg"),
                        createSampleEvidence("IMAGE", "e/6.jpg")
                ),
                "CMD_TOO_MANY_EV"
        );

        when(returnCaseService.submitDamageExemptionRequest(eq(sellerId), eq(caseId), any()))
                .thenThrow(new IllegalArgumentException("Số lượng ảnh bằng chứng tối đa là 5 ảnh"));

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/seller/damage-request", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("Số lượng ảnh bằng chứng tối đa là 5 ảnh"));
    }

    @Test
    @DisplayName("API damage-request - Người gọi không phải người bán đơn hàng trả về HTTP 400 Bad Request")
    void submitDamageExemptionRequest_unauthorizedSeller_returns400BadRequest() throws Exception {
        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Khiếu nại thiệt hại nhưng không phải người bán của đơn",
                List.of(createSampleEvidence("IMAGE", "evidence/broken.jpg")),
                "CMD_SELLER_UNAUTHORIZED"
        );

        when(returnCaseService.submitDamageExemptionRequest(eq(sellerId), eq(caseId), any()))
                .thenThrow(new IllegalArgumentException("Chỉ người bán của đơn hàng mới có quyền gửi yêu cầu bồi thường thiệt hại"));

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/seller/damage-request", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("Chỉ người bán của đơn hàng mới có quyền gửi yêu cầu bồi thường thiệt hại"));
    }

    @Test
    @DisplayName("API damage-request - Đã gửi yêu cầu trước đó (BD-5 guard) trả về HTTP 400 Bad Request (ILLEGAL_STATE)")
    void submitDamageExemptionRequest_alreadySubmitted_BD5_returns400BadRequest() throws Exception {
        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Cố gắng gửi thêm bằng chứng thiệt hại lần 2",
                List.of(createSampleEvidence("IMAGE", "evidence/broken_more.jpg")),
                "CMD_NEW_ATTEMPT"
        );

        when(returnCaseService.submitDamageExemptionRequest(eq(sellerId), eq(caseId), any()))
                .thenThrow(new IllegalStateException("Người bán đã gửi yêu cầu bồi thường thiệt hại cho Vòng 2. Vụ việc đang chờ KTV xử lý, không thể gửi thêm yêu cầu mới."));

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/seller/damage-request", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"))
                .andExpect(jsonPath("$.message").value("Người bán đã gửi yêu cầu bồi thường thiệt hại cho Vòng 2. Vụ việc đang chờ KTV xử lý, không thể gửi thêm yêu cầu mới."));
    }

    @Test
    @DisplayName("API damage-request - Quá hạn 2 ngày phản hồi của người bán trả về HTTP 400 Bad Request (ILLEGAL_STATE)")
    void submitDamageExemptionRequest_overdue_returns400BadRequest() throws Exception {
        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Khiếu nại thiệt hại nhưng đã quá hạn 2 ngày",
                List.of(createSampleEvidence("IMAGE", "evidence/broken.jpg")),
                "CMD_OVERDUE"
        );

        when(returnCaseService.submitDamageExemptionRequest(eq(sellerId), eq(caseId), any()))
                .thenThrow(new IllegalStateException("Đã quá thời hạn 2 ngày phản hồi của người bán (Hạn chót: 2026-11-09T00:00:00Z)"));

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/seller/damage-request", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"))
                .andExpect(jsonPath("$.message").value("Đã quá thời hạn 2 ngày phản hồi của người bán (Hạn chót: 2026-11-09T00:00:00Z)"));
    }

    @Test
    @DisplayName("API damage-request - Cùng commandKey nhưng khác payload trả về HTTP 409 Conflict (COMMAND_KEY_CONFLICT)")
    void submitDamageExemptionRequest_conflictPayload_returns409Conflict() throws Exception {
        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Lý do thay đổi so với lần trước",
                List.of(createSampleEvidence("IMAGE", "evidence/broken.jpg")),
                "CMD_SELLER_DMG_DUPLICATE"
        );

        when(returnCaseService.submitDamageExemptionRequest(eq(sellerId), eq(caseId), any()))
                .thenThrow(new TrustSafetyBusinessException(
                        HttpStatus.CONFLICT,
                        "COMMAND_KEY_CONFLICT",
                        "Conflict: commandKey đã được sử dụng với payload khác"
                ));

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/seller/damage-request", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_KEY_CONFLICT"))
                .andExpect(jsonPath("$.message").value("Conflict: commandKey đã được sử dụng với payload khác"));
    }
}

