package com.oldbutgold.shop.modules.trustsafety.api;

import java.math.BigDecimal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oldbutgold.shop.modules.trustsafety.application.CaseEvidenceRequestService;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseDtos;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseService;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseDecisionEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.ReturnReasonCode;
import com.oldbutgold.shop.shared.api.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReturnCaseControllerKtvDecideRound2Test - Controller MockMvc Unit Tests for Task 3.2.3 decide-round2")
class ReturnCaseControllerKtvDecideRound2Test {

    @Mock
    private ReturnCaseService returnCaseService;

    @Mock
    private CaseEvidenceRequestService caseEvidenceRequestService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Long ktvId = 3001L;
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

    private ReturnCaseDtos.ReturnCaseDetailResponse createMockDetailResponse(String decisionOutcome) {
        ReturnCaseDtos.CaseDecisionPublicDto decisionDto = new ReturnCaseDtos.CaseDecisionPublicDto(
                901L,
                702L,
                decisionOutcome != null ? decisionOutcome : "FULL_GOODS_REFUND",
                new BigDecimal("500000.00"),
                "KTV duyệt hoàn tiền",
                Instant.now(),
                true
        );
        return new ReturnCaseDtos.ReturnCaseDetailResponse(
                caseId,
                555L,
                "RETURN",
                "CLOSED",
                ReturnReasonCode.DAMAGED_OR_DEFECTIVE,
                "Chi tiết lỗi",
                "Lý do trả hàng chi tiết",
                888L,
                Instant.now().minusSeconds(86400),
                Instant.now().minusSeconds(3600),
                Instant.now().minusSeconds(1800),
                Instant.now(),
                Instant.now(),
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                List.of(decisionDto)
        );
    }

    @Test
    @DisplayName("200 OK: KTV ra quyết định FULL_GOODS_REFUND thành công")
    void ktvDecideRound2_fullGoodsRefund_returns200() throws Exception {
        ReturnCaseDtos.KtvRound2DecisionRequest request = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Hàng hóa trả lại đúng hiện trạng, duyệt hoàn tiền toàn bộ",
                "CMD_KTV_FULL_001"
        );

        when(returnCaseService.ktvDecideRound2(eq(ktvId), eq(caseId), any(ReturnCaseDtos.KtvRound2DecisionRequest.class)))
                .thenReturn(createMockDetailResponse(CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND));

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/ktv/decide-round2", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(caseId))
                .andExpect(jsonPath("$.state").value("CLOSED"));
    }

    @Test
    @DisplayName("200 OK: KTV ra quyết định EXEMPT_GOODS_REFUND thành công")
    void ktvDecideRound2_exemptGoodsRefund_returns200() throws Exception {
        ReturnCaseDtos.KtvRound2DecisionRequest request = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_EXEMPT_GOODS_REFUND,
                "Người bán cung cấp đủ bằng chứng hàng hóa hư hại, chấp thuận miễn trừ bồi thường",
                "CMD_KTV_EXEMPT_001"
        );

        when(returnCaseService.ktvDecideRound2(eq(ktvId), eq(caseId), any(ReturnCaseDtos.KtvRound2DecisionRequest.class)))
                .thenReturn(createMockDetailResponse(CaseDecisionEntity.OUTCOME_EXEMPT_GOODS_REFUND));

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/ktv/decide-round2", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(caseId))
                .andExpect(jsonPath("$.state").value("CLOSED"));
    }

    @Test
    @DisplayName("400 Bad Request: outcome null hoặc blank")
    void ktvDecideRound2_missingOutcome_returns400() throws Exception {
        ReturnCaseDtos.KtvRound2DecisionRequest request = new ReturnCaseDtos.KtvRound2DecisionRequest(
                null,
                "Lý do hợp lệ nhưng thiếu outcome",
                "CMD_NULL_OUTCOME"
        );

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/ktv/decide-round2", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("400 Bad Request: lý do null hoặc ngắn hơn 5 ký tự")
    void ktvDecideRound2_reasonTooShort_returns400() throws Exception {
        ReturnCaseDtos.KtvRound2DecisionRequest request = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Hỏng",
                "CMD_SHORT_REASON"
        );

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/ktv/decide-round2", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("400 Bad Request: commandKey dài quá 127 ký tự")
    void ktvDecideRound2_commandKeyExceeded_returns400() throws Exception {
        String longKey = "A".repeat(128);
        ReturnCaseDtos.KtvRound2DecisionRequest request = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Lý do hợp lệ chi tiết cho phán quyết",
                longKey
        );

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/ktv/decide-round2", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("403 Forbidden: người dùng không có role KTV (SecurityException)")
    void ktvDecideRound2_unauthorizedActor_returns403() throws Exception {
        ReturnCaseDtos.KtvRound2DecisionRequest request = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Người dùng không có quyền KTV cố gắng ra quyết định",
                "CMD_UNAUTH"
        );

        when(returnCaseService.ktvDecideRound2(eq(ktvId), eq(caseId), any(ReturnCaseDtos.KtvRound2DecisionRequest.class)))
                .thenThrow(new SecurityException("Người dùng không có quyền KTV để phân xử hồ sơ Vòng 2"));

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/ktv/decide-round2", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("Người dùng không có quyền KTV để phân xử hồ sơ Vòng 2"));
    }

    @Test
    @DisplayName("400 Bad Request: hồ sơ không ở trạng thái POST_RETURN (IllegalStateException)")
    void ktvDecideRound2_caseNotPostReturn_returns400() throws Exception {
        ReturnCaseDtos.KtvRound2DecisionRequest request = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Phán quyết khi hồ sơ chưa ở POST_RETURN",
                "CMD_NOT_POST_RETURN"
        );

        when(returnCaseService.ktvDecideRound2(eq(ktvId), eq(caseId), any(ReturnCaseDtos.KtvRound2DecisionRequest.class)))
                .thenThrow(new IllegalStateException("Hồ sơ không ở trạng thái Vòng 2 sau khi nhận lại hàng (Trạng thái hiện tại: RETURNING)"));

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/ktv/decide-round2", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"))
                .andExpect(jsonPath("$.message").value(containsString("Hồ sơ không ở trạng thái Vòng 2")));
    }

    @Test
    @DisplayName("400 Bad Request: Vòng 2 đã kết thúc phán quyết (IllegalStateException)")
    void ktvDecideRound2_roundNotOpen_returns400() throws Exception {
        ReturnCaseDtos.KtvRound2DecisionRequest request = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Phán quyết khi vòng 2 đã kết thúc",
                "CMD_ROUND_CLOSED"
        );

        when(returnCaseService.ktvDecideRound2(eq(ktvId), eq(caseId), any(ReturnCaseDtos.KtvRound2DecisionRequest.class)))
                .thenThrow(new IllegalStateException("Vòng 2 đã kết thúc, không thể đưa ra quyết định (Trạng thái hiện tại: DECIDED)"));

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/ktv/decide-round2", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"))
                .andExpect(jsonPath("$.message").value(containsString("Vòng 2 đã kết thúc")));
    }

    @Test
    @DisplayName("400 Bad Request: Miễn trừ bồi thường khi chưa có yêu cầu thiệt hại từ người bán")
    void ktvDecideRound2_exemptWithoutDamageRequest_returns400() throws Exception {
        ReturnCaseDtos.KtvRound2DecisionRequest request = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_EXEMPT_GOODS_REFUND,
                "KTV cố gắng miễn trừ bồi thường nhưng chưa có yêu cầu thiệt hại",
                "CMD_NO_DMG"
        );

        when(returnCaseService.ktvDecideRound2(eq(ktvId), eq(caseId), any(ReturnCaseDtos.KtvRound2DecisionRequest.class)))
                .thenThrow(new IllegalStateException("Chỉ được miễn trừ bồi thường khi người bán đã gửi yêu cầu thiệt hại hợp lệ"));

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/ktv/decide-round2", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"))
                .andExpect(jsonPath("$.message").value(containsString("Chỉ được miễn trừ bồi thường khi người bán đã gửi yêu cầu thiệt hại")));
    }

    @Test
    @DisplayName("200 OK: Idempotent Replay trả về snapshot hiện tại")
    void ktvDecideRound2_idempotentReplay_returns200() throws Exception {
        ReturnCaseDtos.KtvRound2DecisionRequest request = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Gọi lại cùng lệnh phán quyết",
                "CMD_IDEMP_REPLAY"
        );

        when(returnCaseService.ktvDecideRound2(eq(ktvId), eq(caseId), any(ReturnCaseDtos.KtvRound2DecisionRequest.class)))
                .thenReturn(createMockDetailResponse(CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND));

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/ktv/decide-round2", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(caseId));
    }
}

