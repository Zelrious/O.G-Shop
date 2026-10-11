package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.identity.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.jwt.*;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock TokenService tokenService;
    @Mock JwtDecoder jwtDecoder;
    private AuthService service;
    private final Instant now = Instant.parse("2026-09-19T00:00:00Z");

    @BeforeEach void setUp() {
        service = new AuthService(users, roles, PasswordEncoderFactories.createDelegatingPasswordEncoder(),
                tokenService, jwtDecoder, Clock.fixed(now, ZoneOffset.UTC));
    }

    private UserEntity user() {
        return new UserEntity("buyer@ogshop.vn", "{noop}password", "Buyer", null, now);
    }

    private Jwt credential(Instant expiry) {
        return Jwt.withTokenValue("original").header("alg", "HS256").subject("7")
                .issuedAt(now.minusSeconds(600)).expiresAt(expiry).build();
    }

    @Test void verifiedAccountUsesPreparedHashAndOnlyAssignsBuyerWithoutIssuingToken() throws Exception {
        var constructor = RoleEntity.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        RoleEntity buyer = constructor.newInstance();
        Field name = RoleEntity.class.getDeclaredField("roleName");
        name.setAccessible(true);
        name.set(buyer, "BUYER");
        when(roles.findByRoleName("BUYER")).thenReturn(Optional.of(buyer));
        when(users.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var created = service.createVerifiedAccount(new RegistrationDraft("new@ogshop.vn", "{bcrypt}verified-hash",
                "Nguyen An", null, RegistrationDraft.Type.BUYER, null));
        var captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(users).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("new@ogshop.vn");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("{bcrypt}verified-hash");
        assertThat(captor.getValue().getRoles()).extracting(RoleEntity::getRoleName).containsExactly("BUYER");
        assertThat(created.getEmailVerifiedAt()).isEqualTo(now);
        assertThat(created.getIdentityWorkflow()).isEqualTo("UC83");
        verifyNoInteractions(tokenService);
    }

    @Test void loginRejectsWrongPasswordWithGenericAuthenticationFailure() {
        UserEntity user = new UserEntity("buyer@ogshop.vn",
                PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("CorrectPassword123@"),
                "Buyer", null, now);
        when(users.findByEmail("buyer@ogshop.vn")).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.authenticatePassword("buyer@ogshop.vn", "WrongPassword"))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(tokenService);
    }

    @Test void legacySixCharacterPasswordCanStillLogin() {
        var user=new UserEntity("legacy@example.invalid","{noop}short6","Legacy",null,now);
        when(users.findByEmail("legacy@example.invalid")).thenReturn(Optional.of(user));
        assertThat(service.authenticatePassword("legacy@example.invalid","short6")).isSameAs(user);
        verifyNoInteractions(tokenService);
    }

    @Test void oversizedUnicodePasswordFailsBeforeBcryptWithoutServerError() {
        assertThatThrownBy(()->service.authenticatePassword("buyer@example.invalid","ắ".repeat(25)))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(users,tokenService);
    }

    @Test void restoreReloadsCurrentRolesWithoutExtendingOriginalDeadline() {
        Jwt jwt = credential(now.plusSeconds(90));
        UserEntity user = user();
        when(jwtDecoder.decode("original")).thenReturn(jwt);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(tokenService.issueAccessToken(user, jwt.getIssuedAt(), jwt.getExpiresAt())).thenReturn("current-roles");
        var restored = service.restoreSession("original");
        assertThat(restored.expiresIn()).isEqualTo(90);
        assertThat(restored.accessToken()).isEqualTo("current-roles");
        verify(tokenService, never()).issueAccessToken(any());
    }

    @Test void restoreRejectsExpiredCredentialEvenIfDecoderAllowsClockTolerance() {
        when(jwtDecoder.decode("expired")).thenReturn(credential(now.minusSeconds(1)));
        assertThatThrownBy(() -> service.restoreSession("expired")).isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(users, tokenService);
    }

    @Test void restoreRejectsInvalidSignature() {
        when(jwtDecoder.decode("forged")).thenThrow(new JwtException("Invalid signature"));
        assertThatThrownBy(() -> service.restoreSession("forged")).isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(users, tokenService);
    }

    @Test void restoreRejectsMissingCookie() {
        assertThatThrownBy(() -> service.restoreSession(null)).isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(jwtDecoder, users, tokenService);
    }

    @Test void restoreRejectsBlockedAccount() throws Exception {
        UserEntity user = user();
        Field status = UserEntity.class.getDeclaredField("status");
        status.setAccessible(true);
        status.set(user, "BANNED");
        when(jwtDecoder.decode("original")).thenReturn(credential(now.plusSeconds(90)));
        when(users.findById(7L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.restoreSession("original")).isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(tokenService);
    }
}
