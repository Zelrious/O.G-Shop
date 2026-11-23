package com.oldbutgold.shop.modules.commerce.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseDtos;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseService;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.ReturnReasonCode;
import com.oldbutgold.shop.shared.api.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ReturnCaseCommerceControllerTest - Pure Service Delegation & Boundary Checks")
class ReturnCaseCommerceControllerTest {

    private ReturnCaseService returnCaseService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final Long ORDER_ID = 1001L;
    private static final Long BUYER_ID = 50L;
    private static final Long SELLER_ID = 60L;
    private static final Long CASE_ID = 2001L;

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
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ReturnCaseCommerceController controller = new ReturnCaseCommerceController(returnCaseService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new TestJwtArgumentResolver())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private TestingAuthenticationToken authToken(Long userId) {
        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS256"),
                Map.of("sub", String.valueOf(userId))
        );
        return new TestingAuthenticationToken(jwt, null);
    }

    private ReturnCaseDtos.ReturnCaseDetailResponse stubResponse(String state) {
        Instant now = Instant.parse("2026-11-23T04:00:00Z");
        return new ReturnCaseDtos.ReturnCaseDetailResponse(
                CASE_ID, ORDER_ID, "RETURN", state,
                ReturnReasonCode.DAMAGED_OR_DEFECTIVE, "Defect", "Desc", BUYER_ID,
                now, null, null, null, null, null, List.of(), List.of()
        );
    }

    @Test
    @DisplayName("POST /return-request delegates to createReturnRequestForOrder")
    void createReturnRequest_delegatesToService() throws Exception {
        when(returnCaseService.createReturnRequestForOrder(eq(BUYER_ID), eq(ORDER_ID), any()))
                .thenReturn(stubResponse("OPEN"));

        var req = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.DAMAGED_OR_DEFECTIVE, "Sản phẩm bị nứt màn hình", List.of(), "CMD_1"
        );

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-request", ORDER_ID)
                        .principal(authToken(BUYER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(CASE_ID))
                .andExpect(jsonPath("$.state").value("OPEN"));

        verify(returnCaseService).createReturnRequestForOrder(eq(BUYER_ID), eq(ORDER_ID), any());
    }

    @Test
    @DisplayName("GET /return-case delegates to getActiveReturnCaseForOrder and propagates 403/404")
    void getReturnCase_delegatesAndPropagatesErrors() throws Exception {
        when(returnCaseService.getActiveReturnCaseForOrder(BUYER_ID, ORDER_ID))
                .thenReturn(stubResponse("OPEN"));
        when(returnCaseService.getActiveReturnCaseForOrder(999L, ORDER_ID))
                .thenThrow(new AccessDeniedException("Người dùng không thuộc đơn hàng này"));
        when(returnCaseService.getActiveReturnCaseForOrder(BUYER_ID, 9999L))
                .thenThrow(new NoSuchElementException("Không tìm thấy đơn hàng #9999"));

        mockMvc.perform(get("/api/v1/commerce/orders/{orderId}/return-case", ORDER_ID)
                        .principal(authToken(BUYER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(CASE_ID));

        mockMvc.perform(get("/api/v1/commerce/orders/{orderId}/return-case", ORDER_ID)
                        .principal(authToken(999L)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/commerce/orders/{orderId}/return-case", 9999L)
                        .principal(authToken(BUYER_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /return-case/withdraw delegates to withdrawReturnRequestForOrder")
    void withdraw_delegatesToService() throws Exception {
        when(returnCaseService.withdrawReturnRequestForOrder(eq(BUYER_ID), eq(ORDER_ID), any()))
                .thenReturn(stubResponse("CLOSED"));

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-case/withdraw", ORDER_ID)
                        .principal(authToken(BUYER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Thỏa thuận xong\",\"commandKey\":\"CMD_W1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CLOSED"));

        verify(returnCaseService).withdrawReturnRequestForOrder(eq(BUYER_ID), eq(ORDER_ID), any());
    }

    @Test
    @DisplayName("POST /return-case/seller-response delegates to sellerRespondRound1ForOrder")
    void sellerRespondRound1_delegatesToService() throws Exception {
        when(returnCaseService.sellerRespondRound1ForOrder(eq(SELLER_ID), eq(ORDER_ID), any()))
                .thenReturn(stubResponse("RETURN_APPROVED"));

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-case/seller-response", ORDER_ID)
                        .principal(authToken(SELLER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"APPROVE_RETURN\",\"reason\":\"Đồng ý trả hàng\",\"commandKey\":\"CMD_S1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RETURN_APPROVED"));

        verify(returnCaseService).sellerRespondRound1ForOrder(eq(SELLER_ID), eq(ORDER_ID), any());
    }

    @Test
    @DisplayName("POST /return-shipment/confirm-direct-handover delegates to confirmReturnDeliveryDirectForOrder")
    void confirmDirectHandover_delegatesToService() throws Exception {
        when(returnCaseService.confirmReturnDeliveryDirectForOrder(eq(SELLER_ID), eq(ORDER_ID), any()))
                .thenReturn(stubResponse("POST_RETURN"));

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-shipment/confirm-direct-handover", ORDER_ID)
                        .principal(authToken(SELLER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otpCode\":\"123456\",\"commandKey\":\"CMD_H1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("POST_RETURN"));

        verify(returnCaseService).confirmReturnDeliveryDirectForOrder(eq(SELLER_ID), eq(ORDER_ID), any());
    }

    @Test
    @DisplayName("POST /return-case/agree-refund delegates to sellerAgreeRefundRound2ForOrder")
    void agreeRefund_delegatesToService() throws Exception {
        when(returnCaseService.sellerAgreeRefundRound2ForOrder(eq(SELLER_ID), eq(ORDER_ID), any()))
                .thenReturn(stubResponse("CLOSED"));

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-case/agree-refund", ORDER_ID)
                        .principal(authToken(SELLER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Hàng đúng, đồng ý hoàn tiền\",\"commandKey\":\"CMD_A1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CLOSED"));

        verify(returnCaseService).sellerAgreeRefundRound2ForOrder(eq(SELLER_ID), eq(ORDER_ID), any());
    }

    @Test
    @DisplayName("POST /return-case/damage-exemption delegates to submitDamageExemptionRequestForOrder")
    void damageExemption_delegatesToService() throws Exception {
        when(returnCaseService.submitDamageExemptionRequestForOrder(eq(SELLER_ID), eq(ORDER_ID), any()))
                .thenReturn(stubResponse("POST_RETURN"));

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-case/damage-exemption", ORDER_ID)
                        .principal(authToken(SELLER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Hàng trả bị tráo đổi\",\"evidenceList\":[{\"mediaType\":\"IMAGE\",\"objectKey\":\"img/1.jpg\"}],\"commandKey\":\"CMD_D1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("POST_RETURN"));

        verify(returnCaseService).submitDamageExemptionRequestForOrder(eq(SELLER_ID), eq(ORDER_ID), any());
    }

    @Test
    @DisplayName("Unauthenticated call returns 403 via AccessDeniedException from extractUserId")
    void unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/commerce/orders/{orderId}/return-case", ORDER_ID))
                .andExpect(status().isForbidden());
    }
}
