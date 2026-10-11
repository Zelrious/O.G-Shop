package com.oldbutgold.shop.modules.identity.infrastructure.security;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.sun.net.httpserver.HttpServer;
import com.oldbutgold.shop.shared.config.GoogleAuthProperties;
import com.oldbutgold.shop.modules.identity.application.RegistrationException;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class GoogleIdTokenVerifierTest {
    private HttpServer server;private RSAKey key;private GoogleIdTokenVerifier verifier;private final UUID nonce=UUID.randomUUID();
    @BeforeEach void keys() throws Exception {
        key=new RSAKeyGenerator(2048).keyID("fixture").generate();
        byte[] body=new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/keys",e->{e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);e.close();});server.start();
        verifier=new GoogleIdTokenVerifier(new GoogleAuthProperties("client.apps.googleusercontent.com"),
                NimbusJwtDecoder.withJwkSetUri("http://127.0.0.1:"+server.getAddress().getPort()+"/keys").jwsAlgorithm(SignatureAlgorithm.RS256).build());
    }
    @AfterEach void close(){if(server!=null)server.stop(0);}
    private String token(RSAKey signingKey,String issuer,String audience,String tokenNonce,boolean verified,Instant expiry) {
        var claims=JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject("google-subject")
                .issuedAt(Instant.now().minusSeconds(10)).claim("email","Buyer@EXAMPLE.INVALID").claim("email_verified",verified)
                .claim("nonce",tokenNonce).claim("name","Google Buyer");
        if(expiry!=null)claims.expiresAt(expiry);
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(signingKey)))
                .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("fixture").build(),claims.build())).getTokenValue();
    }
    @Test void validatesRealSignatureAndNormalizedVerifiedIdentity() {
        var profile=verifier.verify(token(key,"https://accounts.google.com","client.apps.googleusercontent.com",nonce.toString(),true,Instant.now().plusSeconds(60)),nonce);
        assertThat(profile.subject()).isEqualTo("google-subject");assertThat(profile.email()).isEqualTo("buyer@example.invalid");
    }
    @Test void rejectsWrongAudienceIssuerNonceUnverifiedExpiredAndMissingExpiry() {
        for(String invalid:List.of(
                token(key,"https://accounts.google.com","attacker-client",nonce.toString(),true,Instant.now().plusSeconds(60)),
                token(key,"https://attacker.invalid","client.apps.googleusercontent.com",nonce.toString(),true,Instant.now().plusSeconds(60)),
                token(key,"https://accounts.google.com","client.apps.googleusercontent.com",UUID.randomUUID().toString(),true,Instant.now().plusSeconds(60)),
                token(key,"https://accounts.google.com","client.apps.googleusercontent.com",nonce.toString(),false,Instant.now().plusSeconds(60)),
                token(key,"https://accounts.google.com","client.apps.googleusercontent.com",nonce.toString(),true,Instant.now().minusSeconds(1)),
                token(key,"https://accounts.google.com","client.apps.googleusercontent.com",nonce.toString(),true,null)))
            assertThatThrownBy(()->verifier.verify(invalid,nonce)).isInstanceOfSatisfying(RegistrationException.class,e->assertThat(e.code()).isEqualTo("GOOGLE_AUTHENTICATION_FAILED"));
    }
    @Test void rejectsForgedSignature() throws Exception {
        var wrong=new RSAKeyGenerator(2048).keyID("fixture").generate();
        assertThatThrownBy(()->verifier.verify(token(wrong,"https://accounts.google.com","client.apps.googleusercontent.com",nonce.toString(),true,Instant.now().plusSeconds(60)),nonce))
                .isInstanceOf(RegistrationException.class);
    }
}
