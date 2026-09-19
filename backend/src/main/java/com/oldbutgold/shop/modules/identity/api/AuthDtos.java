package com.oldbutgold.shop.modules.identity.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserEntity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AuthDtos {
    private AuthDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record LoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 72) String password
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 320) String email,
            @Size(max = 20) String phoneNumber,
            @NotBlank @Size(min = 8, max = 72) String password
    ) {
    }

    public record UserResponse(
            long userId,
            String email,
            String fullName,
            String phoneNumber,
            String avatarUrl,
            List<String> roles,
            Instant createdAt
    ) {
        static UserResponse from(UserEntity user) {
            return new UserResponse(
                    user.getId(), user.getEmail(), user.getFullName(), user.getPhoneNumber(), user.getAvatarUrl(),
                    user.getRoles().stream().map(role -> role.getRoleName()).sorted().toList(),
                    user.getCreatedAt()
            );
        }
    }

    public record AuthResponse(UserResponse user, String accessToken, long expiresIn) {
    }
}
