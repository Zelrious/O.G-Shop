package com.oldbutgold.shop.modules.identity.api;

import com.oldbutgold.shop.modules.identity.application.AuthService;
import com.oldbutgold.shop.modules.identity.application.EmailRegistrationService;
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
import java.util.UUID;
import com.oldbutgold.shop.modules.identity.application.RegistrationDraft;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthControllerTest {
    private final AuthService service = mock(AuthService.class);
    private final AuthProperties properties = new AuthProperties(
            "test-signing-key-with-at-least-thirty-two-bytes", Duration.ofMinutes(15),
            "http://localhost:5173", "og_access", true, "/api/v1/auth");
    private final EmailRegistrationService registration = mock(EmailRegistrationService.class);
    private final com.oldbutgold.shop.modules.identity.application.AuthFlowService flows = mock(com.oldbutgold.shop.modules.identity.application.AuthFlowService.class);
    private final AuthController controller = new AuthController(service, properties, new TrustedOriginValidator(properties), registration, flows);

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
        when(flows.login(anyString(), anyString(), any())).thenReturn(new com.oldbutgold.shop.modules.identity.application.AuthFlowService.Result(new AuthService.SessionResult(user(), "access-token", 900), null));
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

    @Test void registrationOnlyReturnsChallengeAndNeverIssuesSessionCookie() {
        var pending=new EmailRegistrationService.Pending(UUID.randomUUID(),"new@example.invalid",
                Instant.EPOCH.plusSeconds(300),Instant.EPOCH.plusSeconds(60),5);
        when(registration.start(any(),any())).thenReturn(pending);
        var response=controller.register(new AuthDtos.RegisterRequest("New Buyer","new@example.invalid",null,
                "Password123@",RegistrationDraft.Type.BUYER,null),trustedRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody().status()).isEqualTo("OTP_REQUIRED");
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        verifyNoInteractions(service);
    }

    @Test void verifiedRegistrationIssuesFixedSessionAndReportsPendingSellerApproval() throws Exception {
        var id=UUID.randomUUID();
        var user=user(); user.verifyRegistrationEmail(Instant.EPOCH);
        when(registration.verify(id,"123456")).thenReturn(new EmailRegistrationService.Completion(
                new AuthService.SessionResult(user,"verified-access",900),"SELLER_VERIFICATION",3000));
        var response=controller.verifyRegistration(new AuthDtos.VerifyRegistrationRequest(id,"123456"),trustedRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("og_access=verified-access","HttpOnly","Max-Age=900");
        assertThat(response.getBody().awardedCoins()).isEqualTo(3000);
        assertThat(response.getBody().nextStep()).isEqualTo("SELLER_VERIFICATION");
        assertThat(response.getBody().user().sellerApprovalRequired()).isTrue();
    }

    @Test void untrustedOriginCannotVerifyResendOrCancelRegistration() {
        var request=mock(HttpServletRequest.class);
        when(request.getHeader("Origin")).thenReturn("https://untrusted.example.invalid");
        var id=UUID.randomUUID();
        assertThatThrownBy(()->controller.verifyRegistration(new AuthDtos.VerifyRegistrationRequest(id,"123456"),request)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->controller.resendRegistration(new AuthDtos.RegistrationChallengeRequest(id),request)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->controller.cancelRegistration(new AuthDtos.RegistrationChallengeRequest(id),request)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(registration,service);
    }
}
