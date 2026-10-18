package com.oldbutgold.shop.modules.identity.api;

import com.oldbutgold.shop.modules.identity.application.AuthService;
import com.oldbutgold.shop.modules.identity.application.EmailRegistrationService;
import com.oldbutgold.shop.shared.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthProperties properties;
    private final TrustedOriginValidator originValidator;
    private final EmailRegistrationService registration;
    private final com.oldbutgold.shop.modules.identity.application.AuthFlowService flows;

    public AuthController(AuthService authService, AuthProperties properties, TrustedOriginValidator originValidator,
                          EmailRegistrationService registration,
                          com.oldbutgold.shop.modules.identity.application.AuthFlowService flows) {
        this.authService = authService;
        this.properties = properties;
        this.originValidator = originValidator;
        this.registration = registration;
        this.flows=flows;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.RegistrationChallengeResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest body,
                                                         HttpServletRequest request) {
        originValidator.validate(request);
        var input=new EmailRegistrationService.Input(body.fullName(),body.email(),body.phoneNumber(),body.password(),body.accountType(),body.seller());
        return pendingResponse(body.googleTicket()==null?registration.start(input,request.getRemoteAddr())
                :registration.start(input,request.getRemoteAddr(),body.googleTicket()));
    }

    @PostMapping("/register/verify")
    public ResponseEntity<AuthDtos.RegistrationCompletionResponse> verifyRegistration(
            @Valid @RequestBody AuthDtos.VerifyRegistrationRequest body, HttpServletRequest request) {
        originValidator.validate(request);
        var completed = registration.verify(body.challengeId(), body.code());
        var session = completed.session();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, sessionCookie(session.accessToken(), Duration.ofSeconds(session.expiresIn())).toString())
                .header(HttpHeaders.SET_COOKIE, legacyCookieRemoval().toString())
                .body(new AuthDtos.RegistrationCompletionResponse(AuthDtos.UserResponse.from(session.user()),
                        session.accessToken(), session.expiresIn(), completed.nextStep(), completed.awardedCoins()));
    }

    @PostMapping("/register/resend")
    public ResponseEntity<AuthDtos.RegistrationChallengeResponse> resendRegistration(
            @Valid @RequestBody AuthDtos.RegistrationChallengeRequest body, HttpServletRequest request) {
        originValidator.validate(request);
        return pendingResponse(registration.resend(body.challengeId(), request.getRemoteAddr()));
    }

    @PostMapping("/register/cancel")
    public ResponseEntity<Void> cancelRegistration(@Valid @RequestBody AuthDtos.RegistrationChallengeRequest body,
                                                  HttpServletRequest request) {
        originValidator.validate(request);
        registration.cancel(body.challengeId());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private ResponseEntity<AuthDtos.RegistrationChallengeResponse> pendingResponse(EmailRegistrationService.Pending pending) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .body(new AuthDtos.RegistrationChallengeResponse("OTP_REQUIRED", pending.id(), pending.email(),
                        pending.expiresAt(), pending.resendAt(), pending.attemptsRemaining()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthDtos.LoginRequest body,
                                                      HttpServletRequest request) {
        originValidator.validate(request);
        return flowResponse(flows.login(body.email(),body.password(),request.getRemoteAddr()));
    }

    @PostMapping("/session")
    public ResponseEntity<AuthDtos.AuthResponse> restoreSession(HttpServletRequest request) {
        originValidator.validate(request);
        String credential = null;
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (properties.sessionCookieName().equals(cookie.getName())) credential = cookie.getValue();
            }
        }
        // Read the existing cookie without renewing its lifetime.
        return sessionResponse(authService.restoreSession(credential), false);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        originValidator.validate(request);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString())
                .header(HttpHeaders.SET_COOKIE, legacyCookieRemoval().toString())
                .cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthDtos.UserResponse> currentUser(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(AuthDtos.UserResponse.from(authService.currentUser(Long.parseLong(jwt.getSubject()))));
    }

    ResponseEntity<?> flowResponse(com.oldbutgold.shop.modules.identity.application.AuthFlowService.Result result) {
        return result.session()!=null?sessionResponse(result.session(),true)
                :ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(result.pending());
    }

    ResponseEntity<AuthDtos.AuthResponse> sessionResponse(AuthService.SessionResult result, boolean start) {
        var response = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        if (start) {
            response.header(HttpHeaders.SET_COOKIE,
                    sessionCookie(result.accessToken(), Duration.ofSeconds(result.expiresIn())).toString());
            response.header(HttpHeaders.SET_COOKIE, legacyCookieRemoval().toString());
        }
        return response.body(new AuthDtos.AuthResponse(
                AuthDtos.UserResponse.from(result.user()), result.accessToken(), result.expiresIn()));
    }

    private ResponseCookie sessionCookie(String value, Duration lifetime) {
        return ResponseCookie.from(properties.sessionCookieName(), value).httpOnly(true)
                .secure(properties.sessionCookieSecure()).sameSite("Lax").path(properties.sessionCookiePath())
                .maxAge(lifetime).build();
    }

    private ResponseCookie legacyCookieRemoval() {
        return ResponseCookie.from("og_refresh", "").httpOnly(true).secure(properties.sessionCookieSecure())
                .sameSite("Lax").path("/api/v1/auth/refresh").maxAge(Duration.ZERO).build();
    }
}
