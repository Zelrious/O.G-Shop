package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RefreshSessionEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RefreshSessionRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RoleEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RoleRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserRepository;
import com.oldbutgold.shop.shared.config.AuthProperties;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshSessionRepository refreshSessions;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuthProperties properties;
    private final Clock clock;

    public AuthService(UserRepository users, RoleRepository roles, RefreshSessionRepository refreshSessions,
                       PasswordEncoder passwordEncoder, TokenService tokenService,
                       AuthProperties properties, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.refreshSessions = refreshSessions;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public SessionResult register(String email, String password, String fullName, String phoneNumber,
                                  ClientMetadata metadata) {
        String normalizedEmail = normalizeEmail(email);
        if (users.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException();
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes");
        }
        Instant now = clock.instant();
        UserEntity user = new UserEntity(
                normalizedEmail,
                passwordEncoder.encode(password),
                fullName.strip(),
                normalizeOptional(phoneNumber),
                now
        );
        RoleEntity buyer = roles.findByRoleName("BUYER")
                .orElseThrow(() -> new IllegalStateException("BUYER role has not been seeded"));
        user.getRoles().add(buyer);
        users.save(user);
        return newSession(user, UUID.randomUUID(), metadata, now);
    }

    @Transactional
    public SessionResult login(String email, String password, ClientMetadata metadata) {
        UserEntity user = users.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!"ACTIVE".equals(user.getStatus()) || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return newSession(user, UUID.randomUUID(), metadata, clock.instant());
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public SessionResult refresh(String rawToken, ClientMetadata metadata) {
        Instant now = clock.instant();
        RefreshSessionEntity current = refreshSessions.findByTokenDigest(digest(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (current.getConsumedAt() != null || current.getRevokedAt() != null) {
            revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }
        if (!current.getExpiresAt().isAfter(now) || !"ACTIVE".equals(current.getUser().getStatus())) {
            current.revoke(now);
            throw new InvalidRefreshTokenException();
        }

        String replacementToken = generateRefreshToken();
        UUID replacementId = UUID.randomUUID();
        RefreshSessionEntity replacement = new RefreshSessionEntity(
                replacementId,
                current.getUser(),
                current.getFamilyId(),
                digest(replacementToken),
                now,
                now.plus(properties.refreshTokenTtl()),
                metadata.ipAddress(),
                metadata.userAgent()
        );
        refreshSessions.save(replacement);
        current.consume(now, replacementId);
        return new SessionResult(
                current.getUser(),
                tokenService.issueAccessToken(current.getUser()),
                replacementToken,
                tokenService.accessTokenExpiresInSeconds()
        );
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshSessions.findByTokenDigest(digest(rawToken))
                .ifPresent(session -> revokeFamily(session.getFamilyId(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public UserEntity currentUser(long userId) {
        return users.findById(userId)
                .filter(user -> "ACTIVE".equals(user.getStatus()))
                .orElseThrow(() -> new BadCredentialsException("Account is unavailable"));
    }

    private SessionResult newSession(UserEntity user, UUID familyId, ClientMetadata metadata, Instant now) {
        String rawToken = generateRefreshToken();
        RefreshSessionEntity session = new RefreshSessionEntity(
                UUID.randomUUID(), user, familyId, digest(rawToken), now,
                now.plus(properties.refreshTokenTtl()), metadata.ipAddress(), metadata.userAgent()
        );
        refreshSessions.save(session);
        return new SessionResult(
                user,
                tokenService.issueAccessToken(user),
                rawToken,
                tokenService.accessTokenExpiresInSeconds()
        );
    }

    private void revokeFamily(UUID familyId, Instant now) {
        refreshSessions.findAllByFamilyId(familyId).forEach(session -> session.revoke(now));
    }

    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String generateRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String digest(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record ClientMetadata(String ipAddress, String userAgent) {
    }

    public record SessionResult(UserEntity user, String accessToken, String refreshToken, long expiresIn) {
    }
}
