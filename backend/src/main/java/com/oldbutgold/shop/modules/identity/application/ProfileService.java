package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.AddressEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.AddressRepository;
import com.oldbutgold.shop.modules.identity.api.ProfileDtos;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RoleEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.SellerVerificationRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProfileService {
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final SellerVerificationRepository sellerVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public ProfileService(UserRepository userRepository,
                          AddressRepository addressRepository,
                          SellerVerificationRepository sellerVerificationRepository,
                          PasswordEncoder passwordEncoder,
                          Clock clock) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.sellerVerificationRepository = sellerVerificationRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProfileDtos.ProfileResponse getProfile(long userId) {
        UserEntity user = getUserOrThrow(userId);
        boolean isVerified = sellerVerificationRepository.findFirstByUserIdAndStatus(userId, "VERIFIED").isPresent();
        Set<String> roles = user.getRoles().stream().map(RoleEntity::getRoleName).collect(Collectors.toSet());

        return new ProfileDtos.ProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getAvatarUrl(),
                roles,
                user.getBankName(),
                maskAccountNumber(user.getBankAccountNumber()),
                user.getBankAccountHolder(),
                isVerified,
                user.getCreatedAt()
        );
    }

    public ProfileDtos.ProfileResponse updateProfile(long userId, ProfileDtos.UpdateProfileRequest request) {
        UserEntity user = getUserOrThrow(userId);
        Instant now = clock.instant();
        user.updateProfile(request.fullName(), request.phoneNumber(), request.avatarUrl(), now);
        userRepository.save(user);
        return getProfile(userId);
    }

    @Transactional(readOnly = true)
    public void checkProfileEditable(long userId) {
        if (!"ACTIVE".equals(getUserOrThrow(userId).getStatus())) {
            throw new AccessDeniedException("Tài khoản không thể cập nhật hồ sơ.");
        }
    }

    public ProfileDtos.ProfileResponse updateProfileWithAvatar(long userId, ProfileDtos.AvatarProfileRequest request, String avatarUrl) {
        checkProfileEditable(userId);
        UserEntity user = getUserOrThrow(userId);
        user.updateProfile(request.fullName(), request.phoneNumber(), avatarUrl, clock.instant());
        userRepository.save(user);
        return getProfile(userId);
    }

    public void changePassword(long userId, ProfileDtos.ChangePasswordRequest request) {
        UserEntity user = getUserOrThrow(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Mật khẩu hiện tại không chính xác.");
        }
        Instant now = clock.instant();
        user.updatePassword(passwordEncoder.encode(request.newPassword()), now);
        userRepository.save(user);
    }

    public ProfileDtos.ProfileResponse updateBankAccount(long userId, ProfileDtos.UpdateBankRequest request) {
        UserEntity user = getUserOrThrow(userId);
        Instant now = clock.instant();
        user.updateBankAccount(request.bankName(), request.bankAccountNumber(), request.bankAccountHolder(), now);
        userRepository.save(user);
        return getProfile(userId);
    }

    @Transactional(readOnly = true)
    public List<ProfileDtos.AddressResponse> getAddresses(long userId) {
        return addressRepository.findByUserIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtDesc(userId)
                .stream()
                .map(this::toAddressResponse)
                .toList();
    }

    public ProfileDtos.AddressResponse createAddress(long userId, ProfileDtos.CreateAddressRequest request) {
        Instant now = clock.instant();
        List<AddressEntity> existingList = addressRepository.findByUserIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtDesc(userId);
        boolean shouldBeDefault = request.isDefault() || existingList.isEmpty();

        if (shouldBeDefault && !existingList.isEmpty()) {
            addressRepository.findByUserIdAndIsDefaultTrueAndDeletedAtIsNull(userId)
                    .ifPresent(currDefault -> {
                        currDefault.setDefault(false);
                        addressRepository.save(currDefault);
                    });
        }

        AddressEntity entity = new AddressEntity(
                userId,
                request.recipientName().trim(),
                request.phoneNumber().trim(),
                request.province().trim(),
                request.district().trim(),
                request.ward().trim(),
                request.detailAddress().trim(),
                shouldBeDefault,
                now
        );
        AddressEntity saved = addressRepository.save(entity);
        return toAddressResponse(saved);
    }

    public ProfileDtos.AddressResponse updateAddress(long userId, long addressId, ProfileDtos.UpdateAddressRequest request) {
        Instant now = clock.instant();
        AddressEntity address = addressRepository.findByIdAndUserIdAndDeletedAtIsNull(addressId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ với ID: " + addressId));

        if (request.isDefault() && !address.isDefault()) {
            addressRepository.findByUserIdAndIsDefaultTrueAndDeletedAtIsNull(userId)
                    .ifPresent(currDefault -> {
                        currDefault.setDefault(false);
                        addressRepository.save(currDefault);
                    });
            address.setDefault(true);
        }

        address.update(
                request.recipientName(),
                request.phoneNumber(),
                request.province(),
                request.district(),
                request.ward(),
                request.detailAddress(),
                now
        );
        AddressEntity saved = addressRepository.save(address);
        return toAddressResponse(saved);
    }

    public void deleteAddress(long userId, long addressId) {
        Instant now = clock.instant();
        AddressEntity address = addressRepository.findByIdAndUserIdAndDeletedAtIsNull(addressId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ với ID: " + addressId));

        boolean wasDefault = address.isDefault();
        address.softDelete(now);
        addressRepository.save(address);

        // Quy tắc đã chốt với Chủ dự án: nếu xóa địa chỉ mặc định, tự động chuyển mặc định cho địa chỉ đầu tiên còn lại
        if (wasDefault) {
            List<AddressEntity> remaining = addressRepository.findByUserIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtDesc(userId);
            if (!remaining.isEmpty()) {
                AddressEntity newDefault = remaining.get(0);
                newDefault.setDefault(true);
                addressRepository.save(newDefault);
            }
        }
    }

    public ProfileDtos.AddressResponse setDefaultAddress(long userId, long addressId) {
        AddressEntity target = addressRepository.findByIdAndUserIdAndDeletedAtIsNull(addressId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ với ID: " + addressId));

        if (!target.isDefault()) {
            addressRepository.findByUserIdAndIsDefaultTrueAndDeletedAtIsNull(userId)
                    .ifPresent(currDefault -> {
                        currDefault.setDefault(false);
                        addressRepository.save(currDefault);
                    });
            target.setDefault(true);
            addressRepository.save(target);
        }

        return toAddressResponse(target);
    }

    private UserEntity getUserOrThrow(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản người dùng với ID: " + userId));
    }

    private ProfileDtos.AddressResponse toAddressResponse(AddressEntity entity) {
        return new ProfileDtos.AddressResponse(
                entity.getId(),
                entity.getRecipientName(),
                entity.getPhoneNumber(),
                entity.getProvince(),
                entity.getDistrict(),
                entity.getWard(),
                entity.getDetailAddress(),
                entity.isDefault(),
                entity.getCreatedAt()
        );
    }

    private static String maskAccountNumber(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        String lastFour = trimmed.substring(trimmed.length() - 4);
        return "*".repeat(trimmed.length() - 4) + lastFour;
    }
}
