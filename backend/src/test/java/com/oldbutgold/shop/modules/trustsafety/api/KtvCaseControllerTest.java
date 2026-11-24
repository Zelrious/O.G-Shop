package com.oldbutgold.shop.modules.trustsafety.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oldbutgold.shop.modules.trustsafety.application.CaseEvidenceRequestService;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseDtos;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseService;
import com.oldbutgold.shop.modules.trustsafety.application.TrustSafetyBusinessException;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseDecisionEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEvidenceRequestEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.ReturnReasonCode;
import com.oldbutgold.shop.shared.api.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("KtvCaseControllerTest - Pure Service Delegation & Guard Verification")
class KtvCaseControllerTest {

    private ReturnCaseService returnCaseService;
    private CaseEvidenceRequestService caseEvidenceRequestService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final Long KTV_ID = 300L;
    private static final Long CASE_ID = 501L;
    private static final Long ORDER_ID = 1001L;

    private static class TestJwtArgumentResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                    && Jwt.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            Principal principal = webRequest.getUserPrincipal();
            if (principal instanceof Authentication auth && auth.getPrincipal() instanceof Jwt jwt) {
                return jwt;
            }
            return null;
        }
    }

    @BeforeEach
    void setUp() {
        returnCaseService = mock(ReturnCaseService.class);
        caseEvidenceRequestService = mock(CaseEvidenceRequestService.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        KtvCaseController controller = new KtvCaseController(returnCaseService, caseEvidenceRequestService);
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new TestJwtArgumentResolver())
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(converter)
                .build();
    }

    private TestingAuthenticationToken ktvAuthToken(Long userId) {
        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS256"),
                Map.of("sub", String.valueOf(userId))
        );
        return new TestingAuthenticationToken(jwt, null, List.of(new SimpleGrantedAuthority("ROLE_KTV")));
    }

    private ReturnCaseDtos.ReturnCaseDetailResponse stubResponse(String state) {
        Instant now = Instant.parse("2026-11-23T04:00:00Z");
        return new ReturnCaseDtos.ReturnCaseDetailResponse(
                CASE_ID, ORDER_ID, "RETURN", state,
                ReturnReasonCode.DAMAGED_OR_DEFECTIVE, "Defect", "Desc", 50L,
                now, null, null, null, null, null, List.of(), List.of()
        );
    }

    @Test
    @DisplayName("GET /api/v1/ktv/work-queue validates scope & round and delegates to service")
    void workQueue_validatesAndDelegates() throws Exception {
        when(returnCaseService.getKtvWorkQueue(KTV_ID, "AVAILABLE", 1))
                .thenReturn(List.of(stubResponse("OPEN")));

        mockMvc.perform(get("/api/v1/ktv/work-queue")
                        .param("scope", "AVAILABLE")
                        .param("round", "1")
                        .principal(ktvAuthToken(KTV_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].caseId").value(CASE_ID));

        mockMvc.perform(get("/api/v1/ktv/work-queue")
                        .param("scope", "INVALID")
                        .param("round", "1")
                        .principal(ktvAuthToken(KTV_ID)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/ktv/work-queue")
                        .param("scope", "MY_CASES")
                        .param("round", "3")
                        .principal(ktvAuthToken(KTV_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/ktv/cases/{caseId}/assign verifies existence via service and assigns")
    void assignCase_verifiesAndDelegates() throws Exception {
        when(returnCaseService.assignCaseToKtv(KTV_ID, CASE_ID)).thenReturn(stubResponse("OPEN"));

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/assign", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(CASE_ID));

        verify(returnCaseService).verifyCaseExistsForKtv(CASE_ID);
        verify(returnCaseService).assignCaseToKtv(KTV_ID, CASE_ID);
    }

    @Test
    @DisplayName("POST /api/v1/ktv/cases/{caseId}/assign returns 404 when case missing")
    void assignCase_notFound_returns404() throws Exception {
        doThrow(new TrustSafetyBusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy"))
                .when(returnCaseService).verifyCaseExistsForKtv(999L);

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/assign", 999L)
                        .principal(ktvAuthToken(KTV_ID)))
                .andExpect(status().isNotFound());

        verify(returnCaseService, never()).assignCaseToKtv(any(), any());
    }

    @Test
    @DisplayName("POST /api/v1/ktv/cases/{caseId}/decide-round1 delegates to ktvConsoleDecideRound1")
    void decideRound1_delegatesToService() throws Exception {
        when(returnCaseService.ktvConsoleDecideRound1(eq(KTV_ID), eq(CASE_ID), any()))
                .thenReturn(stubResponse("RETURN_APPROVED"));

        var req = new ReturnCaseDtos.KtvRound1DecisionRequest(
                CaseDecisionEntity.OUTCOME_APPROVE_RETURN, "Đủ căn cứ cho trả hàng", "CMD_R1"
        );

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/decide-round1", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RETURN_APPROVED"));

        verify(returnCaseService).ktvConsoleDecideRound1(eq(KTV_ID), eq(CASE_ID), any());
    }

    @Test
    @DisplayName("POST /api/v1/ktv/cases/{caseId}/decide-round2 delegates to ktvConsoleDecideRound2 and propagates CASE_ESCALATED 403")
    void decideRound2_delegatesAndPropagatesEscalated403() throws Exception {
        when(returnCaseService.ktvConsoleDecideRound2(eq(KTV_ID), eq(CASE_ID), any()))
                .thenThrow(new TrustSafetyBusinessException(HttpStatus.FORBIDDEN, "CASE_ESCALATED", "Đã chuyển Admin"));

        var req = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND, "Lý do hoàn tiền hợp lệ", "CMD_R2"
        );

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/decide-round2", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        verify(returnCaseService).ktvConsoleDecideRound2(eq(KTV_ID), eq(CASE_ID), any());
    }

    @Test
    @DisplayName("POST /api/v1/ktv/cases/{caseId}/request-evidence returns 201 on created and 200 on replay")
    void requestEvidence_returns201Then200() throws Exception {
        UUID reqId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-23T04:00:00Z");
        var resp = new ReturnCaseDtos.KtvRequestEvidenceResponse(
                reqId, CASE_ID, 200L, 2, KTV_ID, CaseEvidenceRequestEntity.STATUS_PENDING,
                "Cần ảnh rõ hơn của vết hỏng", List.of("Ảnh cận cảnh"), now, now.plusSeconds(172800), "CMD_EV1"
        );
        when(caseEvidenceRequestService.requestEvidence(eq(KTV_ID), eq(CASE_ID), any()))
                .thenReturn(CaseEvidenceRequestService.KtvRequestEvidenceResult.created(resp))
                .thenReturn(CaseEvidenceRequestService.KtvRequestEvidenceResult.replayed(resp));

        var req = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Cần ảnh rõ hơn của vết hỏng", List.of("Ảnh cận cảnh"), "CMD_EV1"
        );

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/request-evidence", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").value(reqId.toString()));

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/request-evidence", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(reqId.toString()));
    }
}
