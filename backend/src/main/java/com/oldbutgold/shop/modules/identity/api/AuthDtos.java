package com.oldbutgold.shop.modules.identity.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserEntity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.Valid;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.oldbutgold.shop.modules.identity.application.RegistrationDraft;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record LoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 72) String password
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 320) String email,
            @Size(max = 20) @Pattern(regexp="^(0|\\+84)[35789][0-9]{8}$|^$") String phoneNumber,
            @NewPassword String password,
            @NotNull RegistrationDraft.Type accountType,
            @Valid RegistrationDraft.Seller seller,
            UUID googleTicket
    ) {
        public RegisterRequest(String fullName,String email,String phoneNumber,String password,RegistrationDraft.Type type,RegistrationDraft.Seller seller) {
            this(fullName,email,phoneNumber,password,type,seller,null);
        }
        @AssertTrue(message="Người bán cần bổ sung hồ sơ cửa hàng; Người mua không gửi hồ sơ Người bán.")
        @JsonIgnore
        public boolean isSellerSelectionValid() {
            return accountType == null || (accountType == RegistrationDraft.Type.SELLER ? seller != null : seller == null);
        }
    }

    public record VerifyRegistrationRequest(@NotNull UUID challengeId,
                                            @NotBlank @Pattern(regexp="^[0-9]{6}$") String code) {}
    public record RegistrationChallengeRequest(@NotNull UUID challengeId) {}
    public record RegistrationChallengeResponse(String status, UUID challengeId, String email,
                                               Instant expiresAt, Instant resendAt, int attemptsRemaining) {}
    public record RegistrationCompletionResponse(UserResponse user, String accessToken, long expiresIn,
                                                String nextStep, long awardedCoins) {}

    public record UserResponse(
            long userId,
            String email,
            String fullName,
            String phoneNumber,
            String avatarUrl,
            List<String> roles,
            Instant createdAt,
            boolean sellerApprovalRequired
    ) {
        static UserResponse from(UserEntity user) {
            return new UserResponse(
                    user.getId(), user.getEmail(), user.getFullName(), user.getPhoneNumber(), user.getAvatarUrl(),
                    user.getRoles().stream().map(role -> role.getRoleName()).sorted().toList(),
                    user.getCreatedAt(), "UC83".equals(user.getIdentityWorkflow())
            );
        }
    }

    public record AuthResponse(UserResponse user, String accessToken, long expiresIn) {
    }
}
