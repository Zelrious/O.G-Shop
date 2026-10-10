package com.oldbutgold.shop.modules.identity.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.identity.application.AuthService;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserEntity;
import com.oldbutgold.shop.shared.config.SecurityConfig;
import com.oldbutgold.shop.shared.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FixedSessionSecurityTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder decoder;
    @MockitoBean AuthService service;

    private String token(Instant expiry) {
        var claims = JwtClaimsSet.builder().issuer("og-shop").subject("7")
                .issuedAt(Instant.now().minusSeconds(600)).expiresAt(expiry).claim("roles", List.of("BUYER")).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @Test void validSessionCookieDoesNotAuthorizeBusinessEndpoints() throws Exception {
        http.perform(get("/api/v1/profile").cookie(new Cookie("og_access", token(Instant.now().plusSeconds(60)))))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void expiredBearerIsRejectedWithoutClockGrace() throws Exception {
        String expired = token(Instant.now().minusSeconds(2));
        assertThatThrownBy(() -> decoder.decode(expired)).isInstanceOf(JwtException.class);
        http.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void removedRefreshRouteCannotRenewLogin() throws Exception {
        http.perform(post("/api/v1/auth/refresh").header("Origin", "http://localhost:5173")
                .cookie(new Cookie("og_refresh", "obsolete"))).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void sessionCookieCanRestoreOnlyFromTrustedOrigin() throws Exception {
        String existing = token(Instant.now().plusSeconds(45));
        var user = new UserEntity("fixture@example.invalid", "{noop}fixture", "Fixture", null, Instant.EPOCH);
        Field id = UserEntity.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(user, 7L);
        when(service.restoreSession(existing)).thenReturn(new AuthService.SessionResult(user, existing, 45));
        var response = http.perform(post("/api/v1/auth/session").header("Origin", "http://localhost:5173")
                .cookie(new Cookie("og_access", existing))).andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie")).andExpect(jsonPath("$.expiresIn").value(45)).andReturn();
        assertThat(json.readTree(response.getResponse().getContentAsString()).get("accessToken").asText()).isEqualTo(existing);
        http.perform(post("/api/v1/auth/session").header("Origin", "https://untrusted.example.invalid")
                .cookie(new Cookie("og_access", existing))).andExpect(status().isForbidden());
        verify(service, times(1)).restoreSession(existing);
    }
}
