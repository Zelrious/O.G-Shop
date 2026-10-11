package com.oldbutgold.shop.modules.identity.infrastructure.security;

import com.oldbutgold.shop.modules.identity.application.*;
import com.oldbutgold.shop.shared.config.GoogleAuthProperties;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.*;

/** Google signature checked with rotating JWKs; never accept a client-decoded profile. */
@Component
public class GoogleIdTokenVerifier implements GoogleIdentityPort {
    private final GoogleAuthProperties properties;
    private final JwtDecoder decoder;
    @org.springframework.beans.factory.annotation.Autowired
    public GoogleIdTokenVerifier(GoogleAuthProperties properties) {
        this(properties,googleDecoder());
    }
    private static NimbusJwtDecoder googleDecoder() {
        var factory=new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);factory.setReadTimeout(5000);
        return NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .restOperations(new org.springframework.web.client.RestTemplate(factory)).jwsAlgorithm(SignatureAlgorithm.RS256).build();
    }
    GoogleIdTokenVerifier(GoogleAuthProperties properties,NimbusJwtDecoder decoder) {
        this.properties=properties;this.decoder=decoder;
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ZERO),jwt->{
            if(jwt.getAudience().contains(properties.clientId()) && Set.of("https://accounts.google.com","accounts.google.com")
                    .contains(jwt.getClaims().get("iss"))) return OAuth2TokenValidatorResult.success();
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        }));
    }
    @Override public AuthFlowService.GoogleProfile verify(String credential,UUID nonce) {
        if(!properties.enabled()) throw new RegistrationException("GOOGLE_NOT_CONFIGURED","Đăng nhập Google chưa được cấu hình.",503);
        try {
            Jwt jwt=decoder.decode(credential);
            String email=jwt.getClaimAsString("email"),sub=jwt.getSubject();
            if(!nonce.toString().equals(jwt.getClaimAsString("nonce")) || jwt.getExpiresAt()==null
                    || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")) || email==null || email.length()>320
                    || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || sub==null || sub.isBlank() || sub.length()>255)
                throw new IllegalArgumentException();
            String name=jwt.getClaimAsString("name");
            return new AuthFlowService.GoogleProfile(sub,email.strip().toLowerCase(Locale.ROOT),name==null?"":name);
        } catch(RuntimeException error) {
            throw new RegistrationException("GOOGLE_AUTHENTICATION_FAILED","Không xác minh được tài khoản Google. Vui lòng thử lại.",401);
        }
    }
}
