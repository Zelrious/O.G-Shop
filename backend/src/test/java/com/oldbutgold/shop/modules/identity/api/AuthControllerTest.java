package com.oldbutgold.shop.modules.identity.api;

import com.oldbutgold.shop.modules.identity.application.AuthService;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserEntity;
import com.oldbutgold.shop.shared.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthControllerTest {
    private final AuthService service = mock(AuthService.class);
    private final AuthProperties properties = new AuthProperties(
            "test-signing-key-with-at-least-thirty-two-bytes", Duration.ofMinutes(15),
            "http://localhost:5173", "og_access", true, "/api/v1/auth");
    private final AuthController controller = new AuthController(service, properties, new TrustedOriginValidator(properties));

    private HttpServletRequest trustedRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Origin")).thenReturn("http://localhost:5173");
        return request;
    }

    private UserEntity user() throws Exception {
        var user = new UserEntity("buyer@example.invalid", "{noop}secret", "Buyer", null, Instant.EPOCH);
        Field id = UserEntity.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(user, 1L);
        return user;
    }

    @Test void loginSetsOnlyFixedLifetimeAccessCookieAndClearsLegacyRefreshCookie() throws Exception {
        when(service.login(anyString(), anyString())).thenReturn(new AuthService.SessionResult(user(), "access-token", 900));
        var response = controller.login(new AuthDtos.LoginRequest("buyer@example.invalid", "Password123@"), trustedRequest());
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("og_access=access-token", "HttpOnly", "Secure", "SameSite=Lax", "Path=/api/v1/auth", "Max-Age=900");
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE).get(1)).contains("og_refresh=", "Max-Age=0");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    }

    @Test void restorationDoesNotResetCookieLifetime() throws Exception {
        var request = trustedRequest();
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("og_access", "existing")});
        when(service.restoreSession("existing")).thenReturn(new AuthService.SessionResult(user(), "current-roles", 45));
        var response = controller.restoreSession(request);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(response.getBody().expiresIn()).isEqualTo(45);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    }

    @Test void logoutClearsCookieEvenIfAlreadyLoggedOut() {
        var response = controller.logout(trustedRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("og_access=", "Max-Age=0", "HttpOnly");
        verifyNoInteractions(service);
    }

    @Test void untrustedOriginCannotRestoreOrLogout() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Origin")).thenReturn("https://untrusted.example.invalid");
        assertThatThrownBy(() -> controller.restoreSession(request)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.logout(request)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service);
    }
}
