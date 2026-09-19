package com.oldbutgold.shop.modules.identity.api;

import com.oldbutgold.shop.modules.identity.application.AuthService;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserEntity;
import com.oldbutgold.shop.shared.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {
    @Test
    void loginSetsHttpOnlyRefreshCookieAndDoesNotExposeItInBody() throws Exception {
        AuthService authService = mock(AuthService.class);
        TrustedOriginValidator validator = mock(TrustedOriginValidator.class);
        AuthProperties properties = new AuthProperties(
                "test-signing-key-with-at-least-thirty-two-bytes", Duration.ofMinutes(15), Duration.ofDays(30),
                "http://localhost:5173", "og_refresh", true, "/api/v1/auth/refresh"
        );
        UserEntity user = new UserEntity("buyer@ogshop.vn", "{noop}secret", "Buyer", null, Instant.EPOCH);
        Field id = UserEntity.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(user, 1L);
        when(authService.login(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn(
                new AuthService.SessionResult(user, "access-token", "refresh-secret", 900)
        );
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        AuthController controller = new AuthController(authService, properties, validator);

        var response = controller.login(new AuthDtos.LoginRequest("buyer@ogshop.vn", "Password123@"), request);

        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).contains("og_refresh=refresh-secret", "HttpOnly", "Secure", "SameSite=Lax",
                "Path=/api/v1/auth/refresh");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isEqualTo("access-token");
        assertThat(response.getBody().toString()).doesNotContain("refresh-secret");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    }
}
