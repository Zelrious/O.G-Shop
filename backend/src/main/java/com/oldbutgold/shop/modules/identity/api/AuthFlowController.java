package com.oldbutgold.shop.modules.identity.api;

import com.oldbutgold.shop.modules.identity.application.*;
import com.oldbutgold.shop.shared.config.GoogleAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthFlowController {
    private final AuthFlowService flows;
    private final GoogleIdentityPort google;
    private final GoogleAuthProperties properties;
    private final AuthController sessions;
    private final TrustedOriginValidator origins;
    public AuthFlowController(AuthFlowService flows,GoogleIdentityPort google,GoogleAuthProperties properties,
                              AuthController sessions,TrustedOriginValidator origins) {
        this.flows=flows;this.google=google;this.properties=properties;this.sessions=sessions;this.origins=origins;
    }
    public record EmailRequest(@NotBlank @Email @Size(max=320) String email) {}
    public record ResetRequest(@NotNull UUID challengeId,@NewPassword String password,@NotBlank String confirmPassword) {}
    public record GoogleRequest(@NotNull UUID nonceId,@NotBlank @Size(max=16000) String credential) {
        @Override public String toString() { return "GoogleRequest[redacted]"; }
    }
    @PostMapping("/login/verify")
    ResponseEntity<?> verifyLogin(@Valid @RequestBody AuthDtos.VerifyRegistrationRequest body,HttpServletRequest request) {
        origins.validate(request); return sessions.flowResponse(flows.verify(body.challengeId(),body.code(),"LOGIN_EMAIL_2FA"));
    }
    @PostMapping("/password-reset")
    ResponseEntity<?> startReset(@Valid @RequestBody EmailRequest body,HttpServletRequest request) {
        origins.validate(request);
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(flows.startReset(body.email(),request.getRemoteAddr()));
    }
    @PostMapping("/password-reset/verify")
    ResponseEntity<?> verifyReset(@Valid @RequestBody AuthDtos.VerifyRegistrationRequest body,HttpServletRequest request) {
        origins.validate(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(flows.verify(body.challengeId(),body.code(),"PASSWORD_RESET").pending());
    }
    @PostMapping("/password-reset/complete")
    ResponseEntity<?> finishReset(@Valid @RequestBody ResetRequest body,HttpServletRequest request) {
        origins.validate(request);flows.finishReset(body.challengeId(),body.password(),body.confirmPassword());
        return sessions.logout(request);
    }
    @PostMapping("/challenge/resend")
    ResponseEntity<?> resend(@Valid @RequestBody AuthDtos.RegistrationChallengeRequest body,HttpServletRequest request) {
        origins.validate(request);
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(flows.resend(body.challengeId(),request.getRemoteAddr()));
    }
    @PostMapping("/challenge/cancel")
    ResponseEntity<?> cancel(@Valid @RequestBody AuthDtos.RegistrationChallengeRequest body,HttpServletRequest request) {
        origins.validate(request);flows.cancel(body.challengeId());return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @GetMapping("/google/config")
    ResponseEntity<?> googleConfig() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("enabled",properties.enabled(),"clientId",properties.clientId()));
    }
    @PostMapping("/google/nonce")
    ResponseEntity<?> nonce(HttpServletRequest request) {
        origins.validate(request);
        if(!properties.enabled()) throw new RegistrationException("GOOGLE_NOT_CONFIGURED","Đăng nhập Google chưa được cấu hình.",503);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("nonceId",flows.googleNonce(request.getRemoteAddr())));
    }
    @PostMapping("/google")
    ResponseEntity<?> google(@Valid @RequestBody GoogleRequest body,HttpServletRequest request) {
        origins.validate(request);
        var profile=google.verify(body.credential(),body.nonceId());
        var result=flows.google(body.nonceId(),profile,request.getRemoteAddr());
        if(result.login()!=null) return sessions.flowResponse(result.login());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(Map.of("status","REGISTRATION_REQUIRED",
                "registrationTicket",result.registrationTicket(),"email",result.email(),"fullName",result.fullName()));
    }
    @PostMapping("/google/link/verify")
    ResponseEntity<?> verifyLink(@Valid @RequestBody AuthDtos.VerifyRegistrationRequest body,HttpServletRequest request) {
        origins.validate(request);return sessions.flowResponse(flows.verify(body.challengeId(),body.code(),"GOOGLE_LINK"));
    }
}
