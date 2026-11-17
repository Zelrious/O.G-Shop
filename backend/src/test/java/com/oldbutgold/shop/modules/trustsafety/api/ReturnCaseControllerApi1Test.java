package com.oldbutgold.shop.modules.trustsafety.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oldbutgold.shop.modules.trustsafety.application.CaseEvidenceRequestService;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseDtos;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseService;
import com.oldbutgold.shop.modules.trustsafety.application.TrustSafetyBusinessException;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReturnCaseControllerApi1Test - Controller MockMvc Unit Tests for API 1 (BD-5 Phase 1)")
class ReturnCaseControllerApi1Test {

    @Mock
    private ReturnCaseService returnCaseService;

    @Mock
    private CaseEvidenceRequestService caseEvidenceRequestService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Long ktvId = 8001L;
    private final Long caseId = 100245L;

    @BeforeEach
    void setUp() {
        ReturnCaseController controller = new ReturnCaseController(returnCaseService, caseEvidenceRequestService);

        // Custom resolver to mock @AuthenticationPrincipal Jwt
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
                        .claim("sub", ktvId.toString())
                        .claim("roles", List.of("ROLE_KTV"))
                        .build();
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(jwtResolver)
                .build();
    }

    @Test
    @DisplayName("API 1 POST - Thành công lần đầu trả về HTTP 201 Created")
    void requestEvidence_firstExecution_returns201Created() throws Exception {
        ReturnCaseDtos.KtvRequestEvidenceRequest request = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Hình ảnh cung cấp ở lần 1 bị mờ, không thấy rõ vết rách ở cổ áo",
                List.of("Video quay cận cảnh vết rách"),
                "REQ-EVD-100245-R2-001"
        );

        UUID requestId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
        Instant issuedAt = Instant.parse("2026-11-04T10:15:30Z");
        Instant deadlineAt = Instant.parse("2026-11-06T10:15:30Z");

        ReturnCaseDtos.KtvRequestEvidenceResponse response = new ReturnCaseDtos.KtvRequestEvidenceResponse(
                requestId,
                caseId,
                50012L,
                2,
                ktvId,
                "PENDING",
                request.reason(),
                request.requestedItems(),
                issuedAt,
                deadlineAt,
                request.commandKey()
        );

        when(caseEvidenceRequestService.requestEvidence(eq(ktvId), eq(caseId), any()))
                .thenReturn(CaseEvidenceRequestService.KtvRequestEvidenceResult.created(response));

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/ktv/request-evidence", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.caseId").value(caseId))
                .andExpect(jsonPath("$.roundNo").value(2))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reason").value(request.reason()))
                .andExpect(jsonPath("$.commandKey").value(request.commandKey()))
                .andExpect(jsonPath("$.deadlineAt").value(deadlineAt.toString()));
    }

    @Test
    @DisplayName("API 1 POST - Idempotent replay trả về HTTP 200 OK")
    void requestEvidence_idempotentReplay_returns200Ok() throws Exception {
        ReturnCaseDtos.KtvRequestEvidenceRequest request = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Hình ảnh cung cấp ở lần 1 bị mờ, không thấy rõ vết rách ở cổ áo",
                List.of("Video quay cận cảnh vết rách"),
                "REQ-EVD-100245-R2-001"
        );

        UUID requestId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
        ReturnCaseDtos.KtvRequestEvidenceResponse response = new ReturnCaseDtos.KtvRequestEvidenceResponse(
                requestId,
                caseId,
                50012L,
                2,
                ktvId,
                "PENDING",
                request.reason(),
                request.requestedItems(),
                Instant.now(),
                Instant.now().plusSeconds(172800),
                request.commandKey()
        );

        when(caseEvidenceRequestService.requestEvidence(eq(ktvId), eq(caseId), any()))
                .thenReturn(CaseEvidenceRequestService.KtvRequestEvidenceResult.replayed(response));

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/ktv/request-evidence", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("API 1 POST - Thiếu commandKey trả về HTTP 400 Bad Request INVALID_INPUT")
    void requestEvidence_missingCommandKey_returns400() throws Exception {
        ReturnCaseDtos.KtvRequestEvidenceRequest request = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Hình ảnh cung cấp ở lần 1 bị mờ, không thấy rõ vết rách ở cổ áo",
                Collections.emptyList(),
                "" // Blank commandKey
        );

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/ktv/request-evidence", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("commandKey"));
    }

    @Test
    @DisplayName("API 1 POST - Lý do quá ngắn (< 10 ký tự) trả về HTTP 400 Bad Request INVALID_INPUT")
    void requestEvidence_reasonTooShort_returns400() throws Exception {
        ReturnCaseDtos.KtvRequestEvidenceRequest request = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Quá ngắn", // < 10 chars
                Collections.emptyList(),
                "CMD-001"
        );

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/ktv/request-evidence", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
    }

    @Test
    @DisplayName("API 1 POST - Sai KTV phụ trách trả về HTTP 403 Forbidden NOT_ASSIGNED_KTV")
    void requestEvidence_notAssignedKtv_returns403() throws Exception {
        ReturnCaseDtos.KtvRequestEvidenceRequest request = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Hình ảnh cung cấp ở lần 1 bị mờ, không thấy rõ vết rách ở cổ áo",
                Collections.emptyList(),
                "CMD-001"
        );

        when(caseEvidenceRequestService.requestEvidence(eq(ktvId), eq(caseId), any()))
                .thenThrow(new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "NOT_ASSIGNED_KTV", "KTV không phụ trách Case này"));

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/ktv/request-evidence", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ASSIGNED_KTV"));
    }

    @Test
    @DisplayName("API 1 POST - Không đủ điều kiện nghiệp vụ trả về HTTP 409 Conflict CASE_NOT_ELIGIBLE")
    void requestEvidence_caseNotEligible_returns409() throws Exception {
        ReturnCaseDtos.KtvRequestEvidenceRequest request = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Hình ảnh cung cấp ở lần 1 bị mờ, không thấy rõ vết rách ở cổ áo",
                Collections.emptyList(),
                "CMD-001"
        );

        when(caseEvidenceRequestService.requestEvidence(eq(ktvId), eq(caseId), any()))
                .thenThrow(new TrustSafetyBusinessException(HttpStatus.CONFLICT, "CASE_NOT_ELIGIBLE", "Case không ở POST_RETURN"));

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/ktv/request-evidence", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASE_NOT_ELIGIBLE"));
    }

    @Test
    @DisplayName("API 1 POST - Quá hạn phản hồi chung trả về HTTP 409 Conflict GENERAL_DEADLINE_EXPIRED")
    void requestEvidence_generalDeadlineExpired_returns409() throws Exception {
        ReturnCaseDtos.KtvRequestEvidenceRequest request = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Hình ảnh cung cấp ở lần 1 bị mờ, không thấy rõ vết rách ở cổ áo",
                Collections.emptyList(),
                "CMD-001"
        );

        when(caseEvidenceRequestService.requestEvidence(eq(ktvId), eq(caseId), any()))
                .thenThrow(new TrustSafetyBusinessException(HttpStatus.CONFLICT, "GENERAL_DEADLINE_EXPIRED", "Quá hạn phản hồi chung"));

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/ktv/request-evidence", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GENERAL_DEADLINE_EXPIRED"));
    }

    @Test
    @DisplayName("API 1 POST - Đang có yêu cầu PENDING khác trả về HTTP 409 Conflict PENDING_REQUEST_EXISTS")
    void requestEvidence_pendingRequestExists_returns409() throws Exception {
        ReturnCaseDtos.KtvRequestEvidenceRequest request = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Hình ảnh cung cấp ở lần 1 bị mờ, không thấy rõ vết rách ở cổ áo",
                Collections.emptyList(),
                "CMD-001"
        );

        when(caseEvidenceRequestService.requestEvidence(eq(ktvId), eq(caseId), any()))
                .thenThrow(new TrustSafetyBusinessException(HttpStatus.CONFLICT, "PENDING_REQUEST_EXISTS", "Đã có yêu cầu PENDING"));

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/ktv/request-evidence", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PENDING_REQUEST_EXISTS"));
    }

    @Test
    @DisplayName("API 1 POST - Trùng commandKey khác payload trả về HTTP 409 Conflict COMMAND_KEY_CONFLICT")
    void requestEvidence_commandKeyConflict_returns409() throws Exception {
        ReturnCaseDtos.KtvRequestEvidenceRequest request = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Hình ảnh cung cấp ở lần 1 bị mờ, không thấy rõ vết rách ở cổ áo",
                Collections.emptyList(),
                "CMD-001"
        );

        when(caseEvidenceRequestService.requestEvidence(eq(ktvId), eq(caseId), any()))
                .thenThrow(new TrustSafetyBusinessException(HttpStatus.CONFLICT, "COMMAND_KEY_CONFLICT", "Xung đột payload"));

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/ktv/request-evidence", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_KEY_CONFLICT"));
    }
}
