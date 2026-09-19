package com.oldbutgold.shop.modules.identity.api;

import com.oldbutgold.shop.modules.identity.application.AuthService;
import com.oldbutgold.shop.shared.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
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
    public ResponseEntity<AuthDtos.AuthResponse> register(
            @Valid @RequestBody AuthDtos.RegisterRequest body,
            HttpServletRequest request
    ) {
        originValidator.validate(request);
        AuthService.SessionResult result = authService.register(
                body.email(), body.password(), body.fullName(), body.phoneNumber(), metadata(request)
        );
        return sessionResponse(result);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.AuthResponse> login(
            @Valid @RequestBody AuthDtos.LoginRequest body,
            HttpServletRequest request
    ) {
        originValidator.validate(request);
        return sessionResponse(authService.login(body.email(), body.password(), metadata(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthDtos.AuthResponse> refresh(
            @CookieValue(name = "og_refresh", required = false) String defaultCookie,
            HttpServletRequest request
    ) {
        originValidator.validate(request);
        String rawToken = cookieValue(request, defaultCookie);
        return sessionResponse(authService.refresh(rawToken, metadata(request)));
    }

    @PostMapping("/refresh/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "og_refresh", required = false) String defaultCookie,
            HttpServletRequest request
    ) {
        originValidator.validate(request);
        authService.logout(cookieValue(request, defaultCookie));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie().toString())
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthDtos.UserResponse> currentUser(@AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AuthDtos.UserResponse.from(authService.currentUser(userId)));
    }

    private ResponseEntity<AuthDtos.AuthResponse> sessionResponse(AuthService.SessionResult result) {
        AuthDtos.AuthResponse body = new AuthDtos.AuthResponse(
                AuthDtos.UserResponse.from(result.user()), result.accessToken(), result.expiresIn()
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(properties.refreshCookieName(), value)
                .httpOnly(true)
                .secure(properties.refreshCookieSecure())
                .sameSite("Lax")
                .path(properties.refreshCookiePath())
                .maxAge(properties.refreshTokenTtl())
                .build();
    }

    private ResponseCookie clearCookie() {
        return ResponseCookie.from(properties.refreshCookieName(), "")
                .httpOnly(true)
                .secure(properties.refreshCookieSecure())
                .sameSite("Lax")
                .path(properties.refreshCookiePath())
                .maxAge(Duration.ZERO)
                .build();
    }

    private String cookieValue(HttpServletRequest request, String defaultCookie) {
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (properties.refreshCookieName().equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        if (defaultCookie != null) {
            return defaultCookie;
        }
        throw new com.oldbutgold.shop.modules.identity.application.InvalidRefreshTokenException();
    }

    private static AuthService.ClientMetadata metadata(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.length() > 255) {
            userAgent = userAgent.substring(0, 255);
        }
        return new AuthService.ClientMetadata(request.getRemoteAddr(), userAgent);
    }
}
