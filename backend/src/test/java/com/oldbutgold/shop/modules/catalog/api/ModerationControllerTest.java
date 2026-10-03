package com.oldbutgold.shop.modules.catalog.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.catalog.application.CommandKeyConflictException;
import com.oldbutgold.shop.modules.catalog.application.ProductStateConflictException;
import com.oldbutgold.shop.modules.catalog.application.ProductVersionConflictException;
import com.oldbutgold.shop.modules.catalog.application.SellerProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SellerProductService sellerProductService;

    @Test
    void unauthenticatedRequest_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/moderation/products"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/moderation/products/100/approve"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/moderation/products/100/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void approveProduct_extractsReviewerFromJwtPrincipal() throws Exception {
        when(sellerProductService.approveProduct(888L, 100L, 2L, "key-1"))
                .thenReturn(new CatalogDtos.ActionResponse(100L, "ACTIVE", "Phê duyệt tin đăng thành công."));

        var auth = jwt().jwt(token -> token.subject("888"))
                .authorities(new SimpleGrantedAuthority("ROLE_BUYER"));

        var body = Map.of("expectedVersion", 2, "commandKey", "key-1");

        mockMvc.perform(post("/api/v1/moderation/products/100/approve")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(100))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(sellerProductService).approveProduct(888L, 100L, 2L, "key-1");
    }

    @Test
    void rejectProduct_rejectsMissingBodyOrBlankReason() throws Exception {
        var auth = jwt().jwt(token -> token.subject("888"))
                .authorities(new SimpleGrantedAuthority("ROLE_BUYER"));

        // Missing body
        mockMvc.perform(post("/api/v1/moderation/products/100/reject")
                        .with(auth))
                .andExpect(status().isBadRequest());

        // Empty JSON object
        mockMvc.perform(post("/api/v1/moderation/products/100/reject")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        // Blank reason
        mockMvc.perform(post("/api/v1/moderation/products/100/reject")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());

        // Null reason
        mockMvc.perform(post("/api/v1/moderation/products/100/reject")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":null}"))
                .andExpect(status().isBadRequest());

        // Reason > 500 chars
        mockMvc.perform(post("/api/v1/moderation/products/100/reject")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "x".repeat(501)))))
                .andExpect(status().isBadRequest());

        verify(sellerProductService, never()).rejectProduct(any(Long.class), any(Long.class), any(), any(), any());
    }

    @Test
    void rejectProduct_extractsReviewerFromJwtAndCallsService() throws Exception {
        when(sellerProductService.rejectProduct(888L, 100L, "Video không rõ chi tiết", 3L, "key-rej"))
                .thenReturn(new CatalogDtos.ActionResponse(100L, "REJECTED", "Đã từ chối tin đăng: Video không rõ chi tiết"));

        var auth = jwt().jwt(token -> token.subject("888"))
                .authorities(new SimpleGrantedAuthority("ROLE_BUYER"));

        var body = Map.of("reason", "Video không rõ chi tiết", "expectedVersion", 3, "commandKey", "key-rej");

        mockMvc.perform(post("/api/v1/moderation/products/100/reject")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(100))
                .andExpect(jsonPath("$.status").value("REJECTED"));

        verify(sellerProductService).rejectProduct(888L, 100L, "Video không rõ chi tiết", 3L, "key-rej");
    }

    @Test
    void approveProduct_rejectsMissingBodyOrInvalidFields() throws Exception {
        var auth = jwt().jwt(token -> token.subject("888"))
                .authorities(new SimpleGrantedAuthority("ROLE_BUYER"));

        // Missing body
        mockMvc.perform(post("/api/v1/moderation/products/100/approve")
                        .with(auth))
                .andExpect(status().isBadRequest());

        // Empty JSON object
        mockMvc.perform(post("/api/v1/moderation/products/100/approve")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        // Missing expectedVersion
        mockMvc.perform(post("/api/v1/moderation/products/100/approve")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandKey\":\"key-1\"}"))
                .andExpect(status().isBadRequest());

        // Negative expectedVersion
        mockMvc.perform(post("/api/v1/moderation/products/100/approve")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":-1,\"commandKey\":\"key-1\"}"))
                .andExpect(status().isBadRequest());

        // Blank commandKey
        mockMvc.perform(post("/api/v1/moderation/products/100/approve")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1,\"commandKey\":\"   \"}"))
                .andExpect(status().isBadRequest());

        // commandKey > 100 chars
        mockMvc.perform(post("/api/v1/moderation/products/100/approve")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("expectedVersion", 1, "commandKey", "k".repeat(101)))))
                .andExpect(status().isBadRequest());

        verify(sellerProductService, never()).approveProduct(any(Long.class), any(Long.class), any(), any());
    }

    @Test
    void stateOrVersionConflict_returnsHttp409() throws Exception {
        var auth = jwt().jwt(token -> token.subject("888"))
                .authorities(new SimpleGrantedAuthority("ROLE_BUYER"));

        var validBody = Map.of("expectedVersion", 1, "commandKey", "key-valid");

        when(sellerProductService.approveProduct(eq(888L), eq(100L), any(), any()))
                .thenThrow(new ProductStateConflictException("Chỉ có thể phê duyệt tin ở trạng thái PENDING."));

        mockMvc.perform(post("/api/v1/moderation/products/100/approve")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBody)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_STATE_CONFLICT"));

        when(sellerProductService.approveProduct(eq(888L), eq(101L), any(), any()))
                .thenThrow(new ProductVersionConflictException("Phiên bản đã thay đổi."));

        mockMvc.perform(post("/api/v1/moderation/products/101/approve")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBody)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_VERSION_CONFLICT"));

        when(sellerProductService.approveProduct(eq(888L), eq(102L), any(), any()))
                .thenThrow(new CommandKeyConflictException("Command key conflict."));

        mockMvc.perform(post("/api/v1/moderation/products/102/approve")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBody)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMAND_KEY_CONFLICT"));
    }

    @Test
    void publishProduct_blocksBypassOverHttp() throws Exception {
        var auth = jwt().jwt(token -> token.subject("555"))
                .authorities(new SimpleGrantedAuthority("ROLE_SELLER"));

        when(sellerProductService.publishProduct(555L, 200L))
                .thenThrow(new ProductStateConflictException("Chỉ có thể đăng bán lại sản phẩm đang ở trạng thái HIDDEN."));

        mockMvc.perform(post("/api/v1/seller/products/200/publish")
                        .with(auth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_STATE_CONFLICT"));
    }
}
