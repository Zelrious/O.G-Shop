package com.oldbutgold.shop.shared.config;

import com.oldbutgold.shop.modules.identity.api.AuthController;
import com.oldbutgold.shop.modules.identity.api.TrustedOriginValidator;
import com.oldbutgold.shop.modules.identity.application.AuthService;
import com.oldbutgold.shop.modules.identity.application.EmailRegistrationService;
import com.oldbutgold.shop.modules.identity.application.RegistrationException;
import com.oldbutgold.shop.shared.api.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CorsFilter;

import java.time.Duration;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RegistrationCorsTest {
    private static final String FRONTEND_ORIGIN = "http://localhost:5173";
    private static final String REGISTER_BODY = """
            {"fullName":"Buyer","email":"buyer@example.invalid","password":"Password123@","accountType":"BUYER"}
            """;
    private final EmailRegistrationService registration = mock(EmailRegistrationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var properties = new AuthProperties("test-signing-key-with-at-least-thirty-two-bytes",
                Duration.ofMinutes(15), FRONTEND_ORIGIN, "og_access", false, "/api/v1/auth");
        var controller = new AuthController(mock(AuthService.class), properties,
                new TrustedOriginValidator(properties), registration, mock(com.oldbutgold.shop.modules.identity.application.AuthFlowService.class));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new CorsFilter(new SecurityConfig().corsConfigurationSource(properties)))
                .build();
    }

    @Test
    void exposesRetryAfterOnRegistrationRateLimitForConfiguredOrigin() throws Exception {
        when(registration.start(any(), anyString())).thenThrow(tooSoon());
        assertRateLimitedResponse("/api/v1/auth/register", REGISTER_BODY);
    }

    @Test
    void exposesRetryAfterOnResendRateLimitForConfiguredOrigin() throws Exception {
        when(registration.resend(any(), anyString())).thenThrow(tooSoon());
        assertRateLimitedResponse("/api/v1/auth/register/resend",
                "{\"challengeId\":\"" + UUID.randomUUID() + "\"}");
    }

    @Test
    void rejectsUntrustedOriginWithoutExposingHeadersOrCallingRegistration() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example.invalid")
                        .contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS));
        verifyNoInteractions(registration);
    }

    private void assertRateLimitedResponse(String path, String body) throws Exception {
        mvc.perform(post(path).header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.RETRY_AFTER))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("OTP_RESEND_TOO_SOON"));
    }

    private RegistrationException tooSoon() {
        return new RegistrationException("OTP_RESEND_TOO_SOON", "Vui lòng chờ trước khi gửi mã mới.", 429, 60);
    }
}
