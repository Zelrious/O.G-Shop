package com.oldbutgold.shop.modules.identity.api;

import com.oldbutgold.shop.modules.identity.application.AuthService;
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

    public AuthController(AuthService authService, AuthProperties properties, TrustedOriginValidator originValidator) {
        this.authService = authService;
        this.properties = properties;
        this.originValidator = originValidator;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest body,
                                                         HttpServletRequest request) {
        originValidator.validate(request);
        return sessionResponse(authService.register(body.email(), body.password(), body.fullName(), body.phoneNumber()), true);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest body,
                                                      HttpServletRequest request) {
        originValidator.validate(request);
        return sessionResponse(authService.login(body.email(), body.password()), true);
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

    private ResponseEntity<AuthDtos.AuthResponse> sessionResponse(AuthService.SessionResult result, boolean start) {
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
