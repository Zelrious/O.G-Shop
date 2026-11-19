package com.oldbutgold.shop.modules.commerce.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseDtos;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseService;
import com.oldbutgold.shop.modules.trustsafety.application.TrustSafetyBusinessException;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.ReturnReasonCode;
import com.oldbutgold.shop.shared.api.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import java.util.Optional;

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

class ReturnCaseCommerceControllerTest {

    private ReturnCaseService returnCaseService;
    private OrderRepository orderRepository;
    private CaseRepository caseRepository;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final Long ORDER_ID = 1001L;
    private static final Long BUYER_ID = 50L;
    private static final Long SELLER_ID = 60L;
    private static final Long STRANGER_ID = 999L;
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
        orderRepository = mock(OrderRepository.class);
        caseRepository = mock(CaseRepository.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ReturnCaseCommerceController controller = new ReturnCaseCommerceController(
                returnCaseService,
                orderRepository,
                caseRepository
        );

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

    private OrderEntity stubOrder(Long buyerId, Long sellerId) {
        OrderEntity order = mock(OrderEntity.class);
        when(order.getId()).thenReturn(ORDER_ID);
        when(order.getBuyerId()).thenReturn(buyerId);
        when(order.getSellerId()).thenReturn(sellerId);
        return order;
    }

    private CaseEntity stubCase(Long caseId) {
        CaseEntity caseEntity = mock(CaseEntity.class);
        when(caseEntity.getId()).thenReturn(caseId);
        when(caseEntity.getOrderId()).thenReturn(ORDER_ID);
        return caseEntity;
    }

    private ReturnCaseDtos.ReturnCaseDetailResponse dummyDetailResponse() {
        return new ReturnCaseDtos.ReturnCaseDetailResponse(
                CASE_ID,
                ORDER_ID,
                "RETURN",
                "OPEN",
                ReturnReasonCode.CHANGE_MIND,
                "clean",
                "full",
                BUYER_ID,
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

    // ==================== ENDPOINT 1: POST /return-request ====================

    @Test
    @DisplayName("Endpoint 1: Tạo return-request thành công (200 OK) khi đúng Buyer")
    void createReturnRequest_validBuyer_success200() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(returnCaseService.createReturnRequest(eq(BUYER_ID), eq(ORDER_ID), any()))
                .thenReturn(dummyDetailResponse());

        ReturnCaseDtos.CreateReturnRequest request = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.CHANGE_MIND,
                "Không vừa cỡ",
                List.of(),
                "CMD-RET-1"
        );

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-request", ORDER_ID)
                        .principal(authToken(BUYER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(CASE_ID));

        verify(returnCaseService).createReturnRequest(eq(BUYER_ID), eq(ORDER_ID), any());
    }

    @Test
    @DisplayName("Endpoint 1: Tạo return-request bị từ chối 403 khi actor không phải Buyer (B2 rule)")
    void createReturnRequest_nonBuyer_forbidden403() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        ReturnCaseDtos.CreateReturnRequest request = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.CHANGE_MIND,
                "Không vừa cỡ",
                List.of(),
                "CMD-RET-1"
        );

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-request", ORDER_ID)
                        .principal(authToken(STRANGER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(returnCaseService, never()).createReturnRequest(any(), any(), any());
    }

    @Test
    @DisplayName("Endpoint 1: Tạo return-request trả về 400 Bad Request khi thiếu reasonCode")
    void createReturnRequest_missingReasonCode_badRequest400() throws Exception {
        String invalidJson = """
                {
                    "detail": "Không có lý do",
                    "commandKey": "CMD-1"
                }
                """;

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-request", ORDER_ID)
                        .principal(authToken(BUYER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("Endpoint 1: Tạo return-request trả về 404 khi không tìm thấy đơn hàng")
    void createReturnRequest_orderNotFound_404() throws Exception {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        ReturnCaseDtos.CreateReturnRequest request = new ReturnCaseDtos.CreateReturnRequest(
                ReturnReasonCode.CHANGE_MIND,
                "Chi tiết",
                List.of(),
                "CMD-1"
        );

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-request", ORDER_ID)
                        .principal(authToken(BUYER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ==================== ENDPOINT 2: GET /return-case (R2 rule) ====================

    @Test
    @DisplayName("Endpoint 2: GET return-case thành công (200 OK) khi có active case và actor là Buyer")
    void getReturnCase_validBuyer_activeCase_success200() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        CaseEntity activeCase = stubCase(CASE_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(caseRepository.findActiveCaseByOrderId(ORDER_ID)).thenReturn(Optional.of(activeCase));
        when(returnCaseService.getReturnCaseById(BUYER_ID, CASE_ID)).thenReturn(dummyDetailResponse());

        mockMvc.perform(get("/api/v1/commerce/orders/{orderId}/return-case", ORDER_ID)
                        .principal(authToken(BUYER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(CASE_ID));

        verify(returnCaseService).getReturnCaseById(BUYER_ID, CASE_ID);
    }

    @Test
    @DisplayName("Endpoint 2: GET return-case thành công (200 OK) khi có active case và actor là Seller")
    void getReturnCase_validSeller_activeCase_success200() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        CaseEntity activeCase = stubCase(CASE_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(caseRepository.findActiveCaseByOrderId(ORDER_ID)).thenReturn(Optional.of(activeCase));
        when(returnCaseService.getReturnCaseById(SELLER_ID, CASE_ID)).thenReturn(dummyDetailResponse());

        mockMvc.perform(get("/api/v1/commerce/orders/{orderId}/return-case", ORDER_ID)
                        .principal(authToken(SELLER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(CASE_ID));
    }

    @Test
    @DisplayName("Endpoint 2: GET return-case trả về 403 khi actor không thuộc đơn hàng")
    void getReturnCase_stranger_forbidden403() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        mockMvc.perform(get("/api/v1/commerce/orders/{orderId}/return-case", ORDER_ID)
                        .principal(authToken(STRANGER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(caseRepository, never()).findActiveCaseByOrderId(any());
    }

    @Test
    @DisplayName("Endpoint 2: GET return-case trả về 404 khi không có active case (R2 rule: không fallback lịch sử)")
    void getReturnCase_noActiveCase_notFound404() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(caseRepository.findActiveCaseByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/commerce/orders/{orderId}/return-case", ORDER_ID)
                        .principal(authToken(BUYER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verify(returnCaseService, never()).getReturnCaseById(any(), any());
    }

    // ==================== ENDPOINT 3: POST /return-case/withdraw ====================

    @Test
    @DisplayName("Endpoint 3: Rút yêu cầu thành công (200 OK) khi đúng Buyer và có active case")
    void withdrawReturnRequest_validBuyer_success200() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        CaseEntity activeCase = stubCase(CASE_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(caseRepository.findActiveCaseByOrderId(ORDER_ID)).thenReturn(Optional.of(activeCase));
        when(returnCaseService.withdrawReturnRequest(eq(BUYER_ID), eq(CASE_ID), any()))
                .thenReturn(dummyDetailResponse());

        ReturnCaseDtos.WithdrawReturnRequest request = new ReturnCaseDtos.WithdrawReturnRequest(
                "Đã tự thỏa thuận",
                "CMD-W-1"
        );

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-case/withdraw", ORDER_ID)
                        .principal(authToken(BUYER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(CASE_ID));
    }

    @Test
    @DisplayName("Endpoint 3: Rút yêu cầu thành công khi request body là null (Optional body)")
    void withdrawReturnRequest_optionalBodyNull_success200() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        CaseEntity activeCase = stubCase(CASE_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(caseRepository.findActiveCaseByOrderId(ORDER_ID)).thenReturn(Optional.of(activeCase));
        when(returnCaseService.withdrawReturnRequest(eq(BUYER_ID), eq(CASE_ID), any()))
                .thenReturn(dummyDetailResponse());

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-case/withdraw", ORDER_ID)
                        .principal(authToken(BUYER_ID)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Endpoint 3: Rút yêu cầu trả về 403 khi actor là Seller (chỉ Buyer được rút)")
    void withdrawReturnRequest_seller_forbidden403() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-case/withdraw", ORDER_ID)
                        .principal(authToken(SELLER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(returnCaseService, never()).withdrawReturnRequest(any(), any(), any());
    }

    @Test
    @DisplayName("Endpoint 3: Rút yêu cầu trả về 404 khi không có active case")
    void withdrawReturnRequest_noActiveCase_notFound404() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(caseRepository.findActiveCaseByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-case/withdraw", ORDER_ID)
                        .principal(authToken(BUYER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ==================== ENDPOINT 4: POST /return-case/seller-response ====================

    @Test
    @DisplayName("Endpoint 4: Seller phản hồi Vòng 1 thành công (200 OK) khi đúng Seller")
    void sellerRespondRound1_validSeller_success200() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        CaseEntity activeCase = stubCase(CASE_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(caseRepository.findActiveCaseByOrderId(ORDER_ID)).thenReturn(Optional.of(activeCase));
        when(returnCaseService.sellerRespondRound1(eq(SELLER_ID), eq(CASE_ID), any()))
                .thenReturn(dummyDetailResponse());

        ReturnCaseDtos.SellerRound1ResponseRequest request = new ReturnCaseDtos.SellerRound1ResponseRequest(
                "APPROVE_RETURN",
                "Đồng ý nhận lại hàng",
                "CMD-RESP-1"
        );

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-case/seller-response", ORDER_ID)
                        .principal(authToken(SELLER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Endpoint 4: Seller phản hồi Vòng 1 trả về 403 khi actor là Buyer")
    void sellerRespondRound1_buyer_forbidden403() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        ReturnCaseDtos.SellerRound1ResponseRequest request = new ReturnCaseDtos.SellerRound1ResponseRequest(
                "APPROVE_RETURN",
                "Đồng ý nhận lại hàng",
                "CMD-RESP-1"
        );

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-case/seller-response", ORDER_ID)
                        .principal(authToken(BUYER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(returnCaseService, never()).sellerRespondRound1(any(), any(), any());
    }

    @Test
    @DisplayName("Endpoint 4: Seller phản hồi Vòng 1 trả về 404 khi không có active case")
    void sellerRespondRound1_noActiveCase_notFound404() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(caseRepository.findActiveCaseByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        ReturnCaseDtos.SellerRound1ResponseRequest request = new ReturnCaseDtos.SellerRound1ResponseRequest(
                "APPROVE_RETURN",
                "Đồng ý nhận lại hàng",
                "CMD-RESP-404"
        );

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-case/seller-response", ORDER_ID)
                        .principal(authToken(SELLER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verify(returnCaseService, never()).sellerRespondRound1(any(), any(), any());
    }

    // ==================== ENDPOINT 5: POST /confirm-direct-handover ====================

    @Test
    @DisplayName("Endpoint 5: Xác nhận bàn giao trực tiếp thành công (200 OK) khi actor là Buyer")
    void confirmReturnDeliveryDirect_buyer_success200() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        CaseEntity activeCase = stubCase(CASE_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(caseRepository.findActiveCaseByOrderId(ORDER_ID)).thenReturn(Optional.of(activeCase));
        when(returnCaseService.confirmReturnDeliveryDirect(eq(BUYER_ID), eq(CASE_ID), any()))
                .thenReturn(dummyDetailResponse());

        ReturnCaseDtos.ConfirmReturnDeliveryRequest request = new ReturnCaseDtos.ConfirmReturnDeliveryRequest(
                Instant.now(),
                "Giao nhận thành công",
                "CMD-HANDOVER-1"
        );

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-shipment/confirm-direct-handover", ORDER_ID)
                        .principal(authToken(BUYER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Endpoint 5: Xác nhận bàn giao trực tiếp thành công (200 OK) khi actor là Seller")
    void confirmReturnDeliveryDirect_seller_success200() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        CaseEntity activeCase = stubCase(CASE_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(caseRepository.findActiveCaseByOrderId(ORDER_ID)).thenReturn(Optional.of(activeCase));
        when(returnCaseService.confirmReturnDeliveryDirect(eq(SELLER_ID), eq(CASE_ID), any()))
                .thenReturn(dummyDetailResponse());

        ReturnCaseDtos.ConfirmReturnDeliveryRequest request = new ReturnCaseDtos.ConfirmReturnDeliveryRequest(
                Instant.now(),
                "Đã nhận hàng trả",
                "CMD-HANDOVER-2"
        );

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-shipment/confirm-direct-handover", ORDER_ID)
                        .principal(authToken(SELLER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Endpoint 5: Xác nhận bàn giao trực tiếp trả về 403 khi actor là Stranger")
    void confirmReturnDeliveryDirect_stranger_forbidden403() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        ReturnCaseDtos.ConfirmReturnDeliveryRequest request = new ReturnCaseDtos.ConfirmReturnDeliveryRequest(
                Instant.now(),
                "Ghi chú",
                "CMD-HANDOVER-3"
        );

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-shipment/confirm-direct-handover", ORDER_ID)
                        .principal(authToken(STRANGER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Endpoint 5: Bàn giao trực tiếp trả về 409 khi có COMMAND_KEY_CONFLICT từ service")
    void confirmReturnDeliveryDirect_commandKeyConflict_409() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        CaseEntity activeCase = stubCase(CASE_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(caseRepository.findActiveCaseByOrderId(ORDER_ID)).thenReturn(Optional.of(activeCase));
        when(returnCaseService.confirmReturnDeliveryDirect(eq(BUYER_ID), eq(CASE_ID), any()))
                .thenThrow(new TrustSafetyBusinessException(HttpStatus.CONFLICT, "COMMAND_KEY_CONFLICT", "Xung đột khóa lệnh"));

        ReturnCaseDtos.ConfirmReturnDeliveryRequest request = new ReturnCaseDtos.ConfirmReturnDeliveryRequest(
                Instant.now(),
                "Ghi chú",
                "CMD-HANDOVER-CONFLICT"
        );

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-shipment/confirm-direct-handover", ORDER_ID)
                        .principal(authToken(BUYER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_KEY_CONFLICT"));
    }

    @Test
    @DisplayName("Endpoint 5: Bàn giao trực tiếp trả về 404 khi không có active case")
    void confirmReturnDeliveryDirect_noActiveCase_notFound404() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(caseRepository.findActiveCaseByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        ReturnCaseDtos.ConfirmReturnDeliveryRequest request = new ReturnCaseDtos.ConfirmReturnDeliveryRequest(
                Instant.now(),
                "Ghi chú",
                "CMD-HANDOVER-404"
        );

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-shipment/confirm-direct-handover", ORDER_ID)
                        .principal(authToken(BUYER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verify(returnCaseService, never()).confirmReturnDeliveryDirect(any(), any(), any());
    }

    // ==================== ENDPOINT 6: POST /return-case/agree-refund ====================

    @Test
    @DisplayName("Endpoint 6: Seller đồng ý hoàn tiền Vòng 2 thành công (200 OK) khi đúng Seller")
    void sellerAgreeRefundRound2_validSeller_success200() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        CaseEntity activeCase = stubCase(CASE_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(caseRepository.findActiveCaseByOrderId(ORDER_ID)).thenReturn(Optional.of(activeCase));
        when(returnCaseService.sellerAgreeRefundRound2(eq(SELLER_ID), eq(CASE_ID), any()))
                .thenReturn(dummyDetailResponse());

        ReturnCaseDtos.SellerAgreeRefundRequest request = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Đồng ý hoàn tiền sau kiểm tra",
                "CMD-AGREE-1"
        );

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-case/agree-refund", ORDER_ID)
                        .principal(authToken(SELLER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Endpoint 6: Seller đồng ý hoàn tiền trả về 403 khi actor là Buyer")
    void sellerAgreeRefundRound2_buyer_forbidden403() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        ReturnCaseDtos.SellerAgreeRefundRequest request = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Đồng ý hoàn tiền sau kiểm tra",
                "CMD-AGREE-1"
        );

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-case/agree-refund", ORDER_ID)
                        .principal(authToken(BUYER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(returnCaseService, never()).sellerAgreeRefundRound2(any(), any(), any());
    }

    @Test
    @DisplayName("Endpoint 6: Seller đồng ý hoàn tiền trả về 404 khi không có active case")
    void sellerAgreeRefundRound2_noActiveCase_notFound404() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(caseRepository.findActiveCaseByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        ReturnCaseDtos.SellerAgreeRefundRequest request = new ReturnCaseDtos.SellerAgreeRefundRequest(
                "Đồng ý hoàn tiền sau kiểm tra",
                "CMD-AGREE-404"
        );

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-case/agree-refund", ORDER_ID)
                        .principal(authToken(SELLER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verify(returnCaseService, never()).sellerAgreeRefundRound2(any(), any(), any());
    }

    // ==================== ENDPOINT 7: POST /return-case/damage-exemption ====================

    @Test
    @DisplayName("Endpoint 7: Seller đề nghị miễn trừ thiệt hại thành công (200 OK) khi đúng Seller")
    void submitDamageExemptionRequest_validSeller_success200() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        CaseEntity activeCase = stubCase(CASE_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(caseRepository.findActiveCaseByOrderId(ORDER_ID)).thenReturn(Optional.of(activeCase));
        when(returnCaseService.submitDamageExemptionRequest(eq(SELLER_ID), eq(CASE_ID), any()))
                .thenReturn(dummyDetailResponse());

        ReturnCaseDtos.EvidenceItemDto evidence = new ReturnCaseDtos.EvidenceItemDto(
                "IMAGE",
                "obj-key-1",
                "hàng vỡ nát",
                "sha256-hash",
                1024L,
                null
        );

        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Hàng trả về bị tráo và hỏng hoàn toàn",
                List.of(evidence),
                "CMD-EXEMPT-1"
        );

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-case/damage-exemption", ORDER_ID)
                        .principal(authToken(SELLER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Endpoint 7: Seller đề nghị miễn trừ trả về 403 khi actor là Buyer")
    void submitDamageExemptionRequest_buyer_forbidden403() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Hàng trả về bị tráo",
                List.of(new ReturnCaseDtos.EvidenceItemDto("IMAGE", "k", "t", "d", 100L, null)),
                "CMD-EXEMPT-1"
        );

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-case/damage-exemption", ORDER_ID)
                        .principal(authToken(BUYER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(returnCaseService, never()).submitDamageExemptionRequest(any(), any(), any());
    }

    @Test
    @DisplayName("Endpoint 7: Seller đề nghị miễn trừ trả về 400 Bad Request khi danh sách bằng chứng rỗng")
    void submitDamageExemptionRequest_emptyEvidence_badRequest400() throws Exception {
        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Lý do hợp lệ dài hơn 5 ký tự",
                List.of(),
                "CMD-EXEMPT-EMPTY"
        );

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-case/damage-exemption", ORDER_ID)
                        .principal(authToken(SELLER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("Endpoint 7: Seller đề nghị miễn trừ trả về 404 khi không có active case")
    void submitDamageExemptionRequest_noActiveCase_notFound404() throws Exception {
        OrderEntity order = stubOrder(BUYER_ID, SELLER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(caseRepository.findActiveCaseByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        ReturnCaseDtos.EvidenceItemDto evidence = new ReturnCaseDtos.EvidenceItemDto(
                "IMAGE",
                "obj-key-1",
                "hàng vỡ nát",
                "sha256-hash",
                1024L,
                null
        );

        ReturnCaseDtos.SellerDamageExemptionRequest request = new ReturnCaseDtos.SellerDamageExemptionRequest(
                "Hàng trả về bị tráo và hỏng hoàn toàn",
                List.of(evidence),
                "CMD-EXEMPT-404"
        );

        mockMvc.perform(post("/api/v1/commerce/orders/{orderId}/return-case/damage-exemption", ORDER_ID)
                        .principal(authToken(SELLER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verify(returnCaseService, never()).submitDamageExemptionRequest(any(), any(), any());
    }

    // ==================== AUTHENTICATION GUARD ====================

    @Test
    @DisplayName("Chung: Yêu cầu không có JWT principal trả về 403 Access Denied")
    void unauthenticatedRequest_forbidden403() throws Exception {
        mockMvc.perform(get("/api/v1/commerce/orders/{orderId}/return-case", ORDER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
