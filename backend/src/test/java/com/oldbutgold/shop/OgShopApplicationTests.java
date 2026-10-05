package com.oldbutgold.shop;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.oldbutgold.shop.shared.config.VnPayProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.oldbutgold.shop.modules.catalog.api.CatalogDtos;
import com.oldbutgold.shop.modules.catalog.application.CatalogService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class OgShopApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VnPayProperties vnPayProperties;

    @MockitoBean
    private CatalogService catalogService;

    @Test
    void contextLoads() {
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void unspecifiedEndpointIsDeniedByDefault() throws Exception {
        mockMvc.perform(get("/api/not-configured"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedIdentityEndpointRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationRejectsClientSuppliedRoles() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Origin", "http://localhost:5173")
                        .content("""
                                {
                                  "fullName": "Role Attacker",
                                  "email": "role-attacker@example.test",
                                  "phoneNumber": "0900000001",
                                  "password": "Password123@",
                                  "roles": ["ADMIN"]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void vnPayPropertiesShouldBeLoaded() {
        Assertions.assertNotNull(vnPayProperties);
        Assertions.assertTrue(vnPayProperties.payUrl().startsWith("https://"));
    }

    @Test
    void sellerActivationEndpointRequiresBearerToken() throws Exception {
        mockMvc.perform(post("/api/v1/seller-verification/activate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void categoriesEndpointIsPublic() throws Exception {
        when(catalogService.getCategories()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk());
    }

    @Test
    void productsEndpointIsPublic() throws Exception {
        when(catalogService.searchProducts(any(), any(), any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(new CatalogDtos.PageResponse<>(List.of(), 0, 12, 0, 0, false));
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk());
    }

    @Test
    void sellerProductsRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/seller/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createSellerProductRequiresBearerToken() throws Exception {
        mockMvc.perform(post("/api/v1/seller/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
