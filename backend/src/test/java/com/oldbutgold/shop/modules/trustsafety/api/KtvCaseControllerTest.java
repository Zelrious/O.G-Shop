package com.oldbutgold.shop.modules.trustsafety.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oldbutgold.shop.modules.trustsafety.application.CaseEvidenceRequestService;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseDtos;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseService;
import com.oldbutgold.shop.modules.trustsafety.application.TrustSafetyBusinessException;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.ReturnReasonCode;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseDecisionEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEventEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEventRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEvidenceRequestEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRoundEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRoundRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KtvCaseControllerTest {

    private ReturnCaseService returnCaseService;
    private CaseEvidenceRequestService caseEvidenceRequestService;
    private CaseRepository caseRepository;
    private CaseRoundRepository caseRoundRepository;
    private CaseEventRepository caseEventRepository;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final Long KTV_ID = 300L;
    private static final Long OTHER_KTV_ID = 400L;
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
        caseRepository = mock(CaseRepository.class);
        caseRoundRepository = mock(CaseRoundRepository.class);
        caseEventRepository = mock(CaseEventRepository.class);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        KtvCaseController controller = new KtvCaseController(
                returnCaseService,
                caseEvidenceRequestService,
                caseRepository,
                caseRoundRepository,
                caseEventRepository
        );

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
                Map.of("sub", String.valueOf(userId), "roles", List.of("KTV"))
        );
        return new TestingAuthenticationToken(jwt, null, List.of(new SimpleGrantedAuthority("ROLE_KTV")));
    }

    private CaseEntity stubCase(Long assignedTo) {
        CaseEntity c = mock(CaseEntity.class);
        when(c.getId()).thenReturn(CASE_ID);
        when(c.getOrderId()).thenReturn(ORDER_ID);
        when(c.getState()).thenReturn(CaseEntity.STATE_OPEN);
        when(c.getAssignedTo()).thenReturn(assignedTo);
        return c;
    }

    private ReturnCaseDtos.ReturnCaseDetailResponse stubDetailResponse(String state) {
        return new ReturnCaseDtos.ReturnCaseDetailResponse(
                CASE_ID,
                ORDER_ID,
                CaseEntity.TYPE_RETURN,
                state,
                ReturnReasonCode.NOT_AS_DESCRIBED,
                "Lý do chi tiết",
                "[NOT_AS_DESCRIBED] Lý do chi tiết",
                10L,
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }

    // =========================================================================
    // ENDPOINT 1: GET /api/v1/ktv/work-queue
    // =========================================================================

    @Test
    @DisplayName("Endpoint 1: getWorkQueue - AVAILABLE Round 1 trả HTTP 200 thành công")
    void getWorkQueue_scopeAvailable_round1_success200() throws Exception {
        ReturnCaseDtos.ReturnCaseDetailResponse item = stubDetailResponse(CaseEntity.STATE_OPEN);
        when(returnCaseService.getKtvWorkQueue(KTV_ID, "AVAILABLE", 1)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/ktv/work-queue")
                        .param("scope", "AVAILABLE")
                        .param("round", "1")
                        .principal(ktvAuthToken(KTV_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].caseId").value(CASE_ID))
                .andExpect(jsonPath("$[0].state").value(CaseEntity.STATE_OPEN));

        verify(returnCaseService).getKtvWorkQueue(KTV_ID, "AVAILABLE", 1);
    }

    @Test
    @DisplayName("Endpoint 1: getWorkQueue - MY_CASES Round 1 trả HTTP 200 thành công")
    void getWorkQueue_scopeMyCases_round1_success200() throws Exception {
        ReturnCaseDtos.ReturnCaseDetailResponse item = stubDetailResponse(CaseEntity.STATE_REVIEWING);
        when(returnCaseService.getKtvWorkQueue(KTV_ID, "MY_CASES", 1)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/ktv/work-queue")
                        .param("scope", "MY_CASES")
                        .param("round", "1")
                        .principal(ktvAuthToken(KTV_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].caseId").value(CASE_ID))
                .andExpect(jsonPath("$[0].state").value(CaseEntity.STATE_REVIEWING));

        verify(returnCaseService).getKtvWorkQueue(KTV_ID, "MY_CASES", 1);
    }

    @Test
    @DisplayName("Endpoint 1: getWorkQueue - AVAILABLE Round 2 trả HTTP 200 thành công")
    void getWorkQueue_scopeAvailable_round2_success200() throws Exception {
        ReturnCaseDtos.ReturnCaseDetailResponse item = stubDetailResponse(CaseEntity.STATE_POST_RETURN);
        when(returnCaseService.getKtvWorkQueue(KTV_ID, "AVAILABLE", 2)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/ktv/work-queue")
                        .param("scope", "AVAILABLE")
                        .param("round", "2")
                        .principal(ktvAuthToken(KTV_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].caseId").value(CASE_ID));

        verify(returnCaseService).getKtvWorkQueue(KTV_ID, "AVAILABLE", 2);
    }

    @Test
    @DisplayName("Endpoint 1: getWorkQueue - MY_CASES Round 2 trả HTTP 200 thành công")
    void getWorkQueue_scopeMyCases_round2_success200() throws Exception {
        ReturnCaseDtos.ReturnCaseDetailResponse item = stubDetailResponse(CaseEntity.STATE_POST_RETURN);
        when(returnCaseService.getKtvWorkQueue(KTV_ID, "MY_CASES", 2)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/ktv/work-queue")
                        .param("scope", "MY_CASES")
                        .param("round", "2")
                        .principal(ktvAuthToken(KTV_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].caseId").value(CASE_ID));

        verify(returnCaseService).getKtvWorkQueue(KTV_ID, "MY_CASES", 2);
    }

    @Test
    @DisplayName("Endpoint 1: getWorkQueue - Thiếu tham số scope trả 400 INVALID_INPUT")
    void getWorkQueue_missingScope_badRequest400() throws Exception {
        mockMvc.perform(get("/api/v1/ktv/work-queue")
                        .param("round", "1")
                        .principal(ktvAuthToken(KTV_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(returnCaseService, never()).getKtvWorkQueue(any(), any(), any());
    }

    @Test
    @DisplayName("Endpoint 1: getWorkQueue - Scope không hợp lệ (ALL) trả 400 INVALID_INPUT")
    void getWorkQueue_invalidScope_badRequest400() throws Exception {
        mockMvc.perform(get("/api/v1/ktv/work-queue")
                        .param("scope", "ALL")
                        .param("round", "1")
                        .principal(ktvAuthToken(KTV_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(returnCaseService, never()).getKtvWorkQueue(any(), any(), any());
    }

    @Test
    @DisplayName("Endpoint 1: getWorkQueue - Thiếu tham số round trả 400 INVALID_INPUT")
    void getWorkQueue_missingRound_badRequest400() throws Exception {
        mockMvc.perform(get("/api/v1/ktv/work-queue")
                        .param("scope", "AVAILABLE")
                        .principal(ktvAuthToken(KTV_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(returnCaseService, never()).getKtvWorkQueue(any(), any(), any());
    }

    @Test
    @DisplayName("Endpoint 1: getWorkQueue - Round không hợp lệ (3) trả 400 INVALID_INPUT")
    void getWorkQueue_invalidRound_badRequest400() throws Exception {
        mockMvc.perform(get("/api/v1/ktv/work-queue")
                        .param("scope", "AVAILABLE")
                        .param("round", "3")
                        .principal(ktvAuthToken(KTV_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(returnCaseService, never()).getKtvWorkQueue(any(), any(), any());
    }

    // =========================================================================
    // ENDPOINT 2: POST /api/v1/ktv/cases/{caseId}/assign
    // =========================================================================

    @Test
    @DisplayName("Endpoint 2: assignCase - Claim Case chưa có ai nhận trả HTTP 200 OK")
    void assignCase_unassigned_success200() throws Exception {
        CaseEntity c = stubCase(null);
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(c));
        ReturnCaseDtos.ReturnCaseDetailResponse response = stubDetailResponse(CaseEntity.STATE_OPEN);
        when(returnCaseService.assignCaseToKtv(KTV_ID, CASE_ID)).thenReturn(response);

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/assign", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(CASE_ID));

        verify(returnCaseService).assignCaseToKtv(KTV_ID, CASE_ID);
    }

    @Test
    @DisplayName("Endpoint 2: assignCase - Replay cùng KTV đã claim trả HTTP 200 idempotent")
    void assignCase_alreadyAssignedToSameKtv_idempotent200() throws Exception {
        CaseEntity c = stubCase(KTV_ID);
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(c));
        ReturnCaseDtos.ReturnCaseDetailResponse response = stubDetailResponse(CaseEntity.STATE_OPEN);
        when(returnCaseService.assignCaseToKtv(KTV_ID, CASE_ID)).thenReturn(response);

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/assign", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(CASE_ID));

        verify(returnCaseService).assignCaseToKtv(KTV_ID, CASE_ID);
    }

    @Test
    @DisplayName("Endpoint 2: assignCase - Case đã bị KTV khác claim trả HTTP 409 CASE_ALREADY_ASSIGNED")
    void assignCase_alreadyAssignedToOtherKtv_conflict409() throws Exception {
        CaseEntity c = stubCase(OTHER_KTV_ID);
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(c));
        when(returnCaseService.assignCaseToKtv(KTV_ID, CASE_ID))
                .thenThrow(new TrustSafetyBusinessException(HttpStatus.CONFLICT, "CASE_ALREADY_ASSIGNED",
                        "Hồ sơ đã được phân công cho KTV khác"));

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/assign", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASE_ALREADY_ASSIGNED"));
    }

    @Test
    @DisplayName("Endpoint 2: assignCase - Case không tồn tại trả HTTP 404 NOT_FOUND")
    void assignCase_caseNotFound_notFound404() throws Exception {
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/assign", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verify(returnCaseService, never()).assignCaseToKtv(any(), any());
    }

    // =========================================================================
    // ENDPOINT 3: POST /api/v1/ktv/cases/{caseId}/decide-round1
    // =========================================================================

    @Test
    @DisplayName("Endpoint 3: decideRound1 - KTV đã claim duyệt chấp thuận trả hàng trả 200 OK")
    void decideRound1_assignedKtv_approve_success200() throws Exception {
        CaseEntity c = stubCase(KTV_ID);
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(c));

        ReturnCaseDtos.KtvRound1DecisionRequest request = new ReturnCaseDtos.KtvRound1DecisionRequest(
                CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                "Ảnh sản phẩm giao sai màu thực tế",
                "CMD-DEC-R1-01"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse response = stubDetailResponse(CaseEntity.STATE_RETURN_APPROVED);
        when(returnCaseService.ktvDecideRound1(KTV_ID, CASE_ID, request)).thenReturn(response);

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/decide-round1", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(CaseEntity.STATE_RETURN_APPROVED));

        verify(returnCaseService).ktvDecideRound1(KTV_ID, CASE_ID, request);
    }

    @Test
    @DisplayName("Endpoint 3: decideRound1 - KTV đã claim bác yêu cầu trả hàng trả 200 OK")
    void decideRound1_assignedKtv_reject_success200() throws Exception {
        CaseEntity c = stubCase(KTV_ID);
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(c));

        ReturnCaseDtos.KtvRound1DecisionRequest request = new ReturnCaseDtos.KtvRound1DecisionRequest(
                CaseDecisionEntity.OUTCOME_REJECT_RETURN,
                "Người mua đổi ý không muốn mua nữa",
                "CMD-DEC-R1-02"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse response = stubDetailResponse(CaseEntity.STATE_REJECTED);
        when(returnCaseService.ktvDecideRound1(KTV_ID, CASE_ID, request)).thenReturn(response);

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/decide-round1", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(CaseEntity.STATE_REJECTED));

        verify(returnCaseService).ktvDecideRound1(KTV_ID, CASE_ID, request);
    }

    @Test
    @DisplayName("Endpoint 3: decideRound1 - Case chưa được claim trả HTTP 403 NOT_ASSIGNED_KTV")
    void decideRound1_unassignedCase_forbidden403() throws Exception {
        CaseEntity c = stubCase(null);
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(c));

        ReturnCaseDtos.KtvRound1DecisionRequest request = new ReturnCaseDtos.KtvRound1DecisionRequest(
                CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                "Lý do phân xử hợp lệ",
                "CMD-DEC-R1-UNASSIGNED"
        );

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/decide-round1", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ASSIGNED_KTV"));

        verify(returnCaseService, never()).ktvDecideRound1(any(), any(), any());
    }

    @Test
    @DisplayName("Endpoint 3: decideRound1 - Case do KTV khác claim trả HTTP 403 NOT_ASSIGNED_KTV")
    void decideRound1_differentKtv_forbidden403() throws Exception {
        CaseEntity c = stubCase(OTHER_KTV_ID);
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(c));

        ReturnCaseDtos.KtvRound1DecisionRequest request = new ReturnCaseDtos.KtvRound1DecisionRequest(
                CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                "Lý do phân xử hợp lệ",
                "CMD-DEC-R1-DIFF"
        );

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/decide-round1", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ASSIGNED_KTV"));

        verify(returnCaseService, never()).ktvDecideRound1(any(), any(), any());
    }

    @Test
    @DisplayName("Endpoint 3: decideRound1 - Case không tồn tại trả 404 NOT_FOUND")
    void decideRound1_caseNotFound_notFound404() throws Exception {
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.empty());

        ReturnCaseDtos.KtvRound1DecisionRequest request = new ReturnCaseDtos.KtvRound1DecisionRequest(
                CaseDecisionEntity.OUTCOME_APPROVE_RETURN,
                "Lý do phân xử hợp lệ",
                "CMD-DEC-R1-404"
        );

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/decide-round1", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verify(returnCaseService, never()).ktvDecideRound1(any(), any(), any());
    }

    @Test
    @DisplayName("Endpoint 3: decideRound1 - Payload không hợp lệ trả 400 Bad Request")
    void decideRound1_invalidInput_badRequest400() throws Exception {
        ReturnCaseDtos.KtvRound1DecisionRequest request = new ReturnCaseDtos.KtvRound1DecisionRequest(
                null,
                "ngắn",
                "CMD"
        );

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/decide-round1", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(returnCaseService, never()).ktvDecideRound1(any(), any(), any());
    }

    // =========================================================================
    // ENDPOINT 4: POST /api/v1/ktv/cases/{caseId}/decide-round2
    // =========================================================================

    @Test
    @DisplayName("Endpoint 4: decideRound2 - FULL_GOODS_REFUND thành công trả HTTP 200 OK")
    void decideRound2_assignedKtv_fullRefund_success200() throws Exception {
        CaseEntity c = stubCase(KTV_ID);
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(c));
        CaseRoundEntity round2 = mock(CaseRoundEntity.class);
        when(round2.getId()).thenReturn(702L);
        when(caseRoundRepository.findByCaseIdAndRoundNo(CASE_ID, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.existsByCaseIdAndRoundIdAndEventType(CASE_ID, 702L, CaseEventEntity.TYPE_ESCALATED))
                .thenReturn(false);

        ReturnCaseDtos.KtvRound2DecisionRequest request = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Hàng trả về đúng hiện trạng ban đầu",
                "CMD-DEC-R2-01"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse response = stubDetailResponse(CaseEntity.STATE_CLOSED);
        when(returnCaseService.ktvDecideRound2(KTV_ID, CASE_ID, request)).thenReturn(response);

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/decide-round2", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(CaseEntity.STATE_CLOSED));

        verify(returnCaseService).ktvDecideRound2(KTV_ID, CASE_ID, request);
    }

    @Test
    @DisplayName("Endpoint 4: decideRound2 - EXEMPT_GOODS_REFUND thành công trả HTTP 200 OK")
    void decideRound2_assignedKtv_exemptRefund_success200() throws Exception {
        CaseEntity c = stubCase(KTV_ID);
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(c));
        CaseRoundEntity round2 = mock(CaseRoundEntity.class);
        when(round2.getId()).thenReturn(702L);
        when(caseRoundRepository.findByCaseIdAndRoundNo(CASE_ID, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.existsByCaseIdAndRoundIdAndEventType(CASE_ID, 702L, CaseEventEntity.TYPE_ESCALATED))
                .thenReturn(false);

        ReturnCaseDtos.KtvRound2DecisionRequest request = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_EXEMPT_GOODS_REFUND,
                "Hàng trả về bị tráo và hỏng nặng",
                "CMD-DEC-R2-02"
        );

        ReturnCaseDtos.ReturnCaseDetailResponse response = stubDetailResponse(CaseEntity.STATE_CLOSED);
        when(returnCaseService.ktvDecideRound2(KTV_ID, CASE_ID, request)).thenReturn(response);

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/decide-round2", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(CaseEntity.STATE_CLOSED));

        verify(returnCaseService).ktvDecideRound2(KTV_ID, CASE_ID, request);
    }

    @Test
    @DisplayName("Endpoint 4: decideRound2 - Case chưa được claim trả HTTP 403 NOT_ASSIGNED_KTV")
    void decideRound2_unassignedCase_forbidden403() throws Exception {
        CaseEntity c = stubCase(null);
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(c));
        CaseRoundEntity round2 = mock(CaseRoundEntity.class);
        when(round2.getId()).thenReturn(702L);
        when(caseRoundRepository.findByCaseIdAndRoundNo(CASE_ID, 2)).thenReturn(Optional.of(round2));

        ReturnCaseDtos.KtvRound2DecisionRequest request = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Hàng trả về đúng hiện trạng ban đầu",
                "CMD-DEC-R2-UNASSIGNED"
        );

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/decide-round2", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ASSIGNED_KTV"));

        verify(returnCaseService, never()).ktvDecideRound2(any(), any(), any());
    }

    @Test
    @DisplayName("Endpoint 4: decideRound2 - Case do KTV khác claim trả HTTP 403 NOT_ASSIGNED_KTV")
    void decideRound2_differentKtv_forbidden403() throws Exception {
        CaseEntity c = stubCase(OTHER_KTV_ID);
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(c));
        CaseRoundEntity round2 = mock(CaseRoundEntity.class);
        when(round2.getId()).thenReturn(702L);
        when(caseRoundRepository.findByCaseIdAndRoundNo(CASE_ID, 2)).thenReturn(Optional.of(round2));

        ReturnCaseDtos.KtvRound2DecisionRequest request = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Hàng trả về đúng hiện trạng ban đầu",
                "CMD-DEC-R2-DIFF"
        );

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/decide-round2", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ASSIGNED_KTV"));

        verify(returnCaseService, never()).ktvDecideRound2(any(), any(), any());
    }

    @Test
    @DisplayName("Endpoint 4: decideRound2 - Round 2 đã bị Escalated trả HTTP 403 CASE_ESCALATED")
    void decideRound2_escalatedCase_forbidden403() throws Exception {
        CaseEntity c = stubCase(KTV_ID);
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(c));
        CaseRoundEntity round2 = mock(CaseRoundEntity.class);
        when(round2.getId()).thenReturn(702L);
        when(caseRoundRepository.findByCaseIdAndRoundNo(CASE_ID, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.existsByCaseIdAndRoundIdAndEventType(CASE_ID, 702L, CaseEventEntity.TYPE_ESCALATED))
                .thenReturn(true);

        ReturnCaseDtos.KtvRound2DecisionRequest request = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Hàng trả về đúng hiện trạng ban đầu",
                "CMD-DEC-R2-ESCALATED"
        );

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/decide-round2", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CASE_ESCALATED"));

        verify(returnCaseService, never()).ktvDecideRound2(any(), any(), any());
    }

    @Test
    @DisplayName("Endpoint 4: decideRound2 - Đang có Evidence Request còn hạn trả 409 EVIDENCE_REQUEST_IN_PROGRESS")
    void decideRound2_activeEvidenceRequest_conflict409() throws Exception {
        CaseEntity c = stubCase(KTV_ID);
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(c));
        CaseRoundEntity round2 = mock(CaseRoundEntity.class);
        when(round2.getId()).thenReturn(702L);
        when(caseRoundRepository.findByCaseIdAndRoundNo(CASE_ID, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.existsByCaseIdAndRoundIdAndEventType(CASE_ID, 702L, CaseEventEntity.TYPE_ESCALATED))
                .thenReturn(false);

        ReturnCaseDtos.KtvRound2DecisionRequest request = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Hàng trả về đúng hiện trạng ban đầu",
                "CMD-DEC-R2-EVIDENCE"
        );

        when(returnCaseService.ktvDecideRound2(KTV_ID, CASE_ID, request))
                .thenThrow(new TrustSafetyBusinessException(HttpStatus.CONFLICT, "EVIDENCE_REQUEST_IN_PROGRESS",
                        "Hồ sơ đang có yêu cầu bổ sung bằng chứng còn hiệu lực"));

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/decide-round2", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVIDENCE_REQUEST_IN_PROGRESS"));
    }

    @Test
    @DisplayName("Endpoint 4: decideRound2 - Case không tồn tại trả 404 NOT_FOUND")
    void decideRound2_caseNotFound_notFound404() throws Exception {
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.empty());

        ReturnCaseDtos.KtvRound2DecisionRequest request = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                "Hàng trả về đúng hiện trạng ban đầu",
                "CMD-DEC-R2-404"
        );

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/decide-round2", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verify(returnCaseService, never()).ktvDecideRound2(any(), any(), any());
    }

    @Test
    @DisplayName("Endpoint 4: decideRound2 - EXEMPT_GOODS_REFUND khi chưa có yêu cầu thiệt hại trả 400 ILLEGAL_STATE")
    void decideRound2_exemptRefundWithoutDamageRequest_badRequest400() throws Exception {
        CaseEntity c = stubCase(KTV_ID);
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(c));
        CaseRoundEntity round2 = mock(CaseRoundEntity.class);
        when(round2.getId()).thenReturn(702L);
        when(caseRoundRepository.findByCaseIdAndRoundNo(CASE_ID, 2)).thenReturn(Optional.of(round2));
        when(caseEventRepository.existsByCaseIdAndRoundIdAndEventType(CASE_ID, 702L, CaseEventEntity.TYPE_ESCALATED))
                .thenReturn(false);

        ReturnCaseDtos.KtvRound2DecisionRequest request = new ReturnCaseDtos.KtvRound2DecisionRequest(
                CaseDecisionEntity.OUTCOME_EXEMPT_GOODS_REFUND,
                "Hàng trả về bị hỏng",
                "CMD-DEC-R2-NO-DAMAGE"
        );

        when(returnCaseService.ktvDecideRound2(KTV_ID, CASE_ID, request))
                .thenThrow(new IllegalStateException("Chỉ được miễn trừ bồi thường khi người bán đã gửi yêu cầu thiệt hại hợp lệ"));

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/decide-round2", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"));
    }

    // =========================================================================
    // ENDPOINT 5: POST /api/v1/ktv/cases/{caseId}/request-evidence
    // =========================================================================

    @Test
    @DisplayName("Endpoint 5: requestEvidence - Tạo mới thành công trả HTTP 201 Created")
    void requestEvidence_assignedKtv_created201() throws Exception {
        CaseEntity c = stubCase(KTV_ID);
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(c));

        ReturnCaseDtos.KtvRequestEvidenceRequest request = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Yêu cầu bổ sung video mở gói hàng",
                List.of("Video unboxing rõ vận đơn"),
                "CMD-REQ-EV-01"
        );

        UUID requestId = UUID.randomUUID();
        ReturnCaseDtos.KtvRequestEvidenceResponse response = new ReturnCaseDtos.KtvRequestEvidenceResponse(
                requestId,
                CASE_ID,
                702L,
                2,
                KTV_ID,
                CaseEvidenceRequestEntity.STATUS_PENDING,
                request.reason(),
                request.requestedItems(),
                Instant.now(),
                Instant.now().plusSeconds(48 * 3600),
                request.commandKey()
        );

        when(caseEvidenceRequestService.requestEvidence(KTV_ID, CASE_ID, request))
                .thenReturn(CaseEvidenceRequestService.KtvRequestEvidenceResult.created(response));

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/request-evidence", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.status").value(CaseEvidenceRequestEntity.STATUS_PENDING));

        verify(caseEvidenceRequestService).requestEvidence(KTV_ID, CASE_ID, request);
    }

    @Test
    @DisplayName("Endpoint 5: requestEvidence - Replay cùng commandKey và payload trả HTTP 200 OK")
    void requestEvidence_assignedKtv_replayed200() throws Exception {
        CaseEntity c = stubCase(KTV_ID);
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(c));

        ReturnCaseDtos.KtvRequestEvidenceRequest request = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Yêu cầu bổ sung video mở gói hàng",
                List.of("Video unboxing rõ vận đơn"),
                "CMD-REQ-EV-REPLAY"
        );

        UUID requestId = UUID.randomUUID();
        ReturnCaseDtos.KtvRequestEvidenceResponse response = new ReturnCaseDtos.KtvRequestEvidenceResponse(
                requestId,
                CASE_ID,
                702L,
                2,
                KTV_ID,
                CaseEvidenceRequestEntity.STATUS_PENDING,
                request.reason(),
                request.requestedItems(),
                Instant.now(),
                Instant.now().plusSeconds(48 * 3600),
                request.commandKey()
        );

        when(caseEvidenceRequestService.requestEvidence(KTV_ID, CASE_ID, request))
                .thenReturn(CaseEvidenceRequestService.KtvRequestEvidenceResult.replayed(response));

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/request-evidence", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId.toString()));

        verify(caseEvidenceRequestService).requestEvidence(KTV_ID, CASE_ID, request);
    }

    @Test
    @DisplayName("Endpoint 5: requestEvidence - Case chưa claim trả HTTP 403 NOT_ASSIGNED_KTV")
    void requestEvidence_unassignedCase_forbidden403() throws Exception {
        CaseEntity c = stubCase(null);
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(c));

        ReturnCaseDtos.KtvRequestEvidenceRequest request = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Yêu cầu bổ sung video mở gói hàng",
                List.of("Video unboxing"),
                "CMD-REQ-EV-UNASSIGNED"
        );

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/request-evidence", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ASSIGNED_KTV"));

        verify(caseEvidenceRequestService, never()).requestEvidence(any(), any(), any());
    }

    @Test
    @DisplayName("Endpoint 5: requestEvidence - Case do KTV khác claim trả HTTP 403 NOT_ASSIGNED_KTV")
    void requestEvidence_differentKtv_forbidden403() throws Exception {
        CaseEntity c = stubCase(OTHER_KTV_ID);
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(c));

        ReturnCaseDtos.KtvRequestEvidenceRequest request = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Yêu cầu bổ sung video mở gói hàng",
                List.of("Video unboxing"),
                "CMD-REQ-EV-DIFF"
        );

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/request-evidence", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ASSIGNED_KTV"));

        verify(caseEvidenceRequestService, never()).requestEvidence(any(), any(), any());
    }

    @Test
    @DisplayName("Endpoint 5: requestEvidence - Đã có request PENDING trả HTTP 409 PENDING_REQUEST_EXISTS")
    void requestEvidence_pendingExists_conflict409() throws Exception {
        CaseEntity c = stubCase(KTV_ID);
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(c));

        ReturnCaseDtos.KtvRequestEvidenceRequest request = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Yêu cầu bổ sung video mở gói hàng",
                List.of("Video unboxing"),
                "CMD-REQ-EV-PENDING"
        );

        when(caseEvidenceRequestService.requestEvidence(KTV_ID, CASE_ID, request))
                .thenThrow(new TrustSafetyBusinessException(HttpStatus.CONFLICT, "PENDING_REQUEST_EXISTS",
                        "Đã tồn tại một yêu cầu bằng chứng ở trạng thái PENDING trên Case này"));

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/request-evidence", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PENDING_REQUEST_EXISTS"));
    }

    @Test
    @DisplayName("Endpoint 5: requestEvidence - Quá hạn sellerResponseDueAt trả HTTP 409 GENERAL_DEADLINE_EXPIRED")
    void requestEvidence_generalDeadlineExpired_conflict409() throws Exception {
        CaseEntity c = stubCase(KTV_ID);
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(c));

        ReturnCaseDtos.KtvRequestEvidenceRequest request = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Yêu cầu bổ sung video mở gói hàng",
                List.of("Video unboxing"),
                "CMD-REQ-EV-EXPIRED"
        );

        when(caseEvidenceRequestService.requestEvidence(KTV_ID, CASE_ID, request))
                .thenThrow(new TrustSafetyBusinessException(HttpStatus.CONFLICT, "GENERAL_DEADLINE_EXPIRED",
                        "Đã quá thời hạn phản hồi chung của Người bán"));

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/request-evidence", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GENERAL_DEADLINE_EXPIRED"));
    }

    @Test
    @DisplayName("Endpoint 5: requestEvidence - Đạt tối đa 4 batches trả HTTP 409 MAX_RESUBMISSION_REACHED")
    void requestEvidence_maxResubmissionReached_conflict409() throws Exception {
        CaseEntity c = stubCase(KTV_ID);
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(c));

        ReturnCaseDtos.KtvRequestEvidenceRequest request = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Yêu cầu bổ sung đợt thứ 5",
                List.of("Thêm video"),
                "CMD-REQ-EV-MAX"
        );

        when(caseEvidenceRequestService.requestEvidence(KTV_ID, CASE_ID, request))
                .thenThrow(new TrustSafetyBusinessException(HttpStatus.CONFLICT, "MAX_RESUBMISSION_REACHED",
                        "Đã đạt số lần nộp bổ sung tối đa (tổng số 4 batches)"));

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/request-evidence", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MAX_RESUBMISSION_REACHED"));
    }

    @Test
    @DisplayName("Endpoint 5: requestEvidence - Trùng commandKey khác payload trả HTTP 409 COMMAND_KEY_CONFLICT")
    void requestEvidence_commandKeyConflict_conflict409() throws Exception {
        CaseEntity c = stubCase(KTV_ID);
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(c));

        ReturnCaseDtos.KtvRequestEvidenceRequest request = new ReturnCaseDtos.KtvRequestEvidenceRequest(
                "Yêu cầu bổ sung video mở gói hàng",
                List.of("Video unboxing"),
                "CMD-REQ-EV-CONFLICT"
        );

        when(caseEvidenceRequestService.requestEvidence(KTV_ID, CASE_ID, request))
                .thenThrow(new TrustSafetyBusinessException(HttpStatus.CONFLICT, "COMMAND_KEY_CONFLICT",
                        "Trùng commandKey nhưng nội dung yêu cầu khác nhau"));

        mockMvc.perform(post("/api/v1/ktv/cases/{caseId}/request-evidence", CASE_ID)
                        .principal(ktvAuthToken(KTV_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_KEY_CONFLICT"));
    }

    // =========================================================================
    // AUTHENTICATION & SECURITY GUARD
    // =========================================================================

    @Test
    @DisplayName("Security: Yêu cầu không có JWT principal trả về 403 ACCESS_DENIED")
    void ktvEndpoint_unauthenticated_accessDenied403() throws Exception {
        mockMvc.perform(get("/api/v1/ktv/work-queue")
                        .param("scope", "AVAILABLE")
                        .param("round", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
