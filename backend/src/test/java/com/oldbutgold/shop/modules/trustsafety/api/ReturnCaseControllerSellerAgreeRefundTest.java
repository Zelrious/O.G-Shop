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
@DisplayName("ReturnCaseControllerSellerAgreeRefundTest - Controller MockMvc Unit Tests for Task 3.2.1 agree-refund")
class ReturnCaseControllerSellerAgreeRefundTest {

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
                "CLOSED",
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

    @Test
    @DisplayName("API agree-refund - Thành công trả về HTTP 200 OK với trạng thái CLOSED")
    void sellerAgreeRefund_success_returns200Ok() throws Exception {
        ReturnCaseDtos.SellerAgreeRefundRequest request = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Đã nhận lại hàng đầy đủ, đồng ý hoàn tiền cho người mua",
                "CMD_SELLER_AGREE_100245"
        );

        when(returnCaseService.sellerAgreeRefundRound2(eq(sellerId), eq(caseId), any()))
                .thenReturn(createMockDetailResponse());

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/seller/agree-refund", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(caseId))
                .andExpect(jsonPath("$.state").value("CLOSED"));
    }

    @Test
    @DisplayName("API agree-refund - Lý do quá ngắn (< 5 ký tự) trả về HTTP 400 Bad Request")
    void sellerAgreeRefund_invalidReason_tooShort_returns400BadRequest() throws Exception {
        ReturnCaseDtos.SellerAgreeRefundRequest request = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "abc",
                "CMD_KEY_VALID"
        );

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/seller/agree-refund", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("API agree-refund - Lý do để trống hoặc null trả về HTTP 400 Bad Request")
    void sellerAgreeRefund_invalidReason_nullOrBlank_returns400BadRequest() throws Exception {
        String invalidJson = "{\"reason\": null, \"commandKey\": \"CMD_VALID\"}";

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/seller/agree-refund", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("API agree-refund - commandKey vượt quá 127 ký tự trả về HTTP 400 Bad Request")
    void sellerAgreeRefund_commandKey_tooLong_returns400BadRequest() throws Exception {
        ReturnCaseDtos.SellerAgreeRefundRequest request = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Lý do hợp lệ trên 5 ký tự",
                "CMD_" + "X".repeat(125) // 129 chars > 127
        );

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/seller/agree-refund", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("API agree-refund - Cùng commandKey nhưng khác payload trả về HTTP 409 Conflict (COMMAND_KEY_CONFLICT)")
    void sellerAgreeRefund_conflictPayload_returns409Conflict() throws Exception {
        ReturnCaseDtos.SellerAgreeRefundRequest request = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Lý do thay đổi so với lần trước",
                "CMD_SELLER_AGREE_DUPLICATE"
        );

        when(returnCaseService.sellerAgreeRefundRound2(eq(sellerId), eq(caseId), any()))
                .thenThrow(new TrustSafetyBusinessException(
                        HttpStatus.CONFLICT,
                        "COMMAND_KEY_CONFLICT",
                        "Conflict: commandKey đã được sử dụng với payload khác"
                ));

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/seller/agree-refund", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_KEY_CONFLICT"))
                .andExpect(jsonPath("$.message").value("Conflict: commandKey đã được sử dụng với payload khác"));
    }

    @Test
    @DisplayName("API agree-refund - Người gọi không phải người bán đơn hàng trả về HTTP 400 Bad Request")
    void sellerAgreeRefund_unauthorizedSeller_returns400BadRequest() throws Exception {
        ReturnCaseDtos.SellerAgreeRefundRequest request = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Đồng ý hoàn tiền nhưng không phải seller của đơn",
                "CMD_SELLER_UNAUTHORIZED"
        );

        when(returnCaseService.sellerAgreeRefundRound2(eq(sellerId), eq(caseId), any()))
                .thenThrow(new IllegalArgumentException("Chỉ người bán của đơn hàng mới có quyền đồng ý hoàn tiền"));

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/seller/agree-refund", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("Chỉ người bán của đơn hàng mới có quyền đồng ý hoàn tiền"));
    }

    @Test
    @DisplayName("API agree-refund - Case không ở trạng thái hợp lệ (POST_RETURN) trả về HTTP 400 Bad Request (ILLEGAL_STATE)")
    void sellerAgreeRefund_illegalState_notEligible_returns400BadRequest() throws Exception {
        ReturnCaseDtos.SellerAgreeRefundRequest request = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Đồng ý hoàn tiền khi case chưa nhận lại hàng",
                "CMD_SELLER_ILLEGAL_STATE"
        );

        when(returnCaseService.sellerAgreeRefundRound2(eq(sellerId), eq(caseId), any()))
                .thenThrow(new IllegalStateException("Hồ sơ không ở trạng thái Vòng 2 sau khi nhận lại hàng (Trạng thái hiện tại: OPEN)"));

        mockMvc.perform(post("/api/v1/trustsafety/returns/{caseId}/seller/agree-refund", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"))
                .andExpect(jsonPath("$.message").value("Hồ sơ không ở trạng thái Vòng 2 sau khi nhận lại hàng (Trạng thái hiện tại: OPEN)"));
    }
}

