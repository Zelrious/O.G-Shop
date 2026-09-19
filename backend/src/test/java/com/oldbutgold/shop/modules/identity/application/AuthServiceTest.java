package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RefreshSessionEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RefreshSessionRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RoleEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RoleRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserRepository;
import com.oldbutgold.shop.shared.config.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock RefreshSessionRepository refreshSessions;
    @Mock TokenService tokenService;

    private AuthService service;
    private final Instant now = Instant.parse("2026-09-19T00:00:00Z");

    @BeforeEach
    void setUp() {
        service = new AuthService(
                users, roles, refreshSessions,
                PasswordEncoderFactories.createDelegatingPasswordEncoder(), tokenService,
                new AuthProperties(
                        "test-signing-key-with-at-least-thirty-two-bytes", Duration.ofMinutes(15),
                        Duration.ofDays(30), "http://localhost:5173", "og_refresh", false,
                        "/api/v1/auth/refresh"
                ),
                Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    @Test
    void registerHashesPasswordAndOnlyAssignsBuyer() throws Exception {
        var roleConstructor = RoleEntity.class.getDeclaredConstructor();
        roleConstructor.setAccessible(true);
        RoleEntity buyer = roleConstructor.newInstance();
        Field roleName = RoleEntity.class.getDeclaredField("roleName");
        roleName.setAccessible(true);
        roleName.set(buyer, "BUYER");
        when(roles.findByRoleName("BUYER")).thenReturn(Optional.of(buyer));
        when(tokenService.issueAccessToken(any())).thenReturn("access-token");
        when(tokenService.accessTokenExpiresInSeconds()).thenReturn(900L);
        when(users.existsByEmail("new@ogshop.vn")).thenReturn(false);

        service.register(" New@OGSHOP.VN ", "Password123@", "Nguyen An", null,
                new AuthService.ClientMetadata("127.0.0.1", "test"));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(users).save(captor.capture());
        UserEntity saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("new@ogshop.vn");
        assertThat(saved.getPasswordHash()).startsWith("{bcrypt}").isNotEqualTo("Password123@");
        assertThat(saved.getRoles()).extracting(RoleEntity::getRoleName).containsExactly("BUYER");
    }

    @Test
    void loginRejectsWrongPasswordWithGenericAuthenticationFailure() {
        UserEntity user = new UserEntity(
                "buyer@ogshop.vn",
                PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("CorrectPassword123@"),
                "Buyer", null, now
        );
        when(users.findByEmail("buyer@ogshop.vn")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(
                "buyer@ogshop.vn", "WrongPassword", new AuthService.ClientMetadata("127.0.0.1", "test")
        )).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refreshRotatesSingleUseToken() {
        when(tokenService.issueAccessToken(any())).thenReturn("access-token");
        when(tokenService.accessTokenExpiresInSeconds()).thenReturn(900L);
        UserEntity user = new UserEntity("buyer@ogshop.vn", "{noop}password", "Buyer", null, now);
        UUID family = UUID.randomUUID();
        RefreshSessionEntity current = new RefreshSessionEntity(
                UUID.randomUUID(), user, family, AuthService.digest("old-token"), now.minusSeconds(60),
                now.plusSeconds(3600), "127.0.0.1", "test"
        );
        when(refreshSessions.findByTokenDigest(AuthService.digest("old-token"))).thenReturn(Optional.of(current));

        AuthService.SessionResult result = service.refresh(
                "old-token", new AuthService.ClientMetadata("127.0.0.1", "test")
        );

        assertThat(result.refreshToken()).isNotBlank().isNotEqualTo("old-token");
        assertThat(current.getConsumedAt()).isEqualTo(now);
        verify(refreshSessions).save(any(RefreshSessionEntity.class));
    }

    @Test
    void refreshReuseRevokesWholeFamily() {
        UserEntity user = new UserEntity("buyer@ogshop.vn", "{noop}password", "Buyer", null, now);
        UUID family = UUID.randomUUID();
        RefreshSessionEntity used = new RefreshSessionEntity(
                UUID.randomUUID(), user, family, AuthService.digest("used-token"), now.minusSeconds(60),
                now.plusSeconds(3600), "127.0.0.1", "test"
        );
        used.consume(now.minusSeconds(10), UUID.randomUUID());
        RefreshSessionEntity active = new RefreshSessionEntity(
                UUID.randomUUID(), user, family, AuthService.digest("active-token"), now.minusSeconds(10),
                now.plusSeconds(3600), "127.0.0.1", "test"
        );
        when(refreshSessions.findByTokenDigest(AuthService.digest("used-token"))).thenReturn(Optional.of(used));
        when(refreshSessions.findAllByFamilyId(family)).thenReturn(List.of(used, active));

        assertThatThrownBy(() -> service.refresh(
                "used-token", new AuthService.ClientMetadata("127.0.0.1", "test")
        )).isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(used.getRevokedAt()).isEqualTo(now);
        assertThat(active.getRevokedAt()).isEqualTo(now);
    }
}
