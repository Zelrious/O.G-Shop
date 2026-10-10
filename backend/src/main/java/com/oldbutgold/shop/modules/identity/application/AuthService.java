package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RoleEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RoleRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final JwtDecoder jwtDecoder;
    private final Clock clock;

    public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                       TokenService tokenService, JwtDecoder jwtDecoder, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.jwtDecoder = jwtDecoder;
        this.clock = clock;
    }

    @Transactional
    public SessionResult register(String email, String password, String fullName, String phoneNumber) {
        String normalizedEmail = normalizeEmail(email);
        if (users.existsByEmail(normalizedEmail)) throw new DuplicateEmailException();
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes");
        }
        UserEntity user = new UserEntity(normalizedEmail, passwordEncoder.encode(password), fullName.strip(),
                normalizeOptional(phoneNumber), clock.instant());
        RoleEntity buyer = roles.findByRoleName("BUYER")
                .orElseThrow(() -> new IllegalStateException("BUYER role has not been seeded"));
        user.getRoles().add(buyer);
        users.save(user);
        return newSession(user);
    }

    @Transactional(readOnly = true)
    public SessionResult login(String email, String password) {
        UserEntity user = users.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!"ACTIVE".equals(user.getStatus()) || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return newSession(user);
    }

    @Transactional(readOnly = true)
    public SessionResult restoreSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw unavailableSession();
        Jwt jwt;
        long userId;
        try {
            jwt = jwtDecoder.decode(rawToken);
            userId = Long.parseLong(jwt.getSubject());
        } catch (JwtException | NumberFormatException exception) {
            throw unavailableSession();
        }
        Instant deadline = jwt.getExpiresAt();
        long remaining = deadline == null ? 0 : Duration.between(clock.instant(), deadline).getSeconds();
        // Decoder clock tolerance must never extend the fixed session deadline.
        if (jwt.getIssuedAt() == null || remaining <= 0) throw unavailableSession();
        UserEntity user = currentUser(userId);
        return new SessionResult(user, tokenService.issueAccessToken(user, jwt.getIssuedAt(), deadline), remaining);
    }

    @Transactional(readOnly = true)
    public UserEntity currentUser(long userId) {
        return users.findById(userId).filter(user -> "ACTIVE".equals(user.getStatus()))
                .orElseThrow(AuthService::unavailableSession);
    }

    private SessionResult newSession(UserEntity user) {
        return new SessionResult(user, tokenService.issueAccessToken(user), tokenService.accessTokenExpiresInSeconds());
    }

    private static BadCredentialsException unavailableSession() {
        return new BadCredentialsException("Session is unavailable");
    }

    private static String normalizeEmail(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public record SessionResult(UserEntity user, String accessToken, long expiresIn) {}
}
