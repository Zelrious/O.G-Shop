package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.AddressEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.AddressRepository;
import com.oldbutgold.shop.modules.identity.api.ProfileDtos;
import com.oldbutgold.shop.modules.identity.domain.PasswordPolicy;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProfileService {
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final SellerVerificationRepository sellerVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final EkycRepository ekycRepository;
    private final AuthFlowService authFlowService;

    public ProfileService(UserRepository userRepository,
                          AddressRepository addressRepository,
                          SellerVerificationRepository sellerVerificationRepository,
                          PasswordEncoder passwordEncoder,
                          Clock clock,
                          EkycRepository ekycRepository,
                          AuthFlowService authFlowService) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.sellerVerificationRepository = sellerVerificationRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.ekycRepository = ekycRepository;
        this.authFlowService = authFlowService;
    }

    @Transactional(readOnly = true)
    public ProfileDtos.ProfileResponse getProfile(long userId) {
        UserEntity user = getUserOrThrow(userId);
        boolean isVerified = ekycRepository.hasVerifiedProfile(userId);
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
        String currentPhone = user.getPhoneNumber() != null ? user.getPhoneNumber().trim() : "";
        String newPhone = request.phoneNumber() != null ? request.phoneNumber().trim() : "";
        boolean isPhoneChanging = !currentPhone.isEmpty() && !newPhone.isEmpty() && !currentPhone.equals(newPhone);

        // Chặn trùng số điện thoại với tài khoản người dùng khác trên toàn hệ thống
        if (!newPhone.isEmpty() && !newPhone.equals(currentPhone)) {
            userRepository.findByPhoneNumber(newPhone).ifPresent(existingUser -> {
                if (existingUser.getId() == null || !existingUser.getId().equals(userId)) {
                    throw new DuplicatePhoneException("Số điện thoại này đã được sử dụng bởi một tài khoản khác.");
                }
            });
        }

        if (isPhoneChanging) {
            boolean hasVerifiedKyc = ekycRepository.findApprovedReferenceAsset(userId).isPresent();

            if (hasVerifiedKyc && request.quickAuthResultId() != null) {
                var validResult = ekycRepository.findValidQuickAuthResult(userId);
                if (validResult.isEmpty() || !validResult.get().resultId().equals(request.quickAuthResultId())) {
                    throw new IllegalStateException("Kết quả xác thực khuôn mặt không hợp lệ hoặc đã hết hạn. Vui lòng quét lại khuôn mặt.");
                }
            } else if (request.challengeId() != null) {
                boolean verified = false;
                if (request.otpCode() != null && !request.otpCode().isBlank()) {
                    try {
                        authFlowService.verifySecuritySetting(request.challengeId(), request.otpCode().trim(), userId);
                        verified = true;
                    } catch (Exception e) {
                        try {
                            authFlowService.verifyPhoneVerification(request.challengeId(), request.otpCode().trim(), userId);
                            verified = true;
                        } catch (Exception ignored) {
                        }
                    }
                }
                if (!verified) {
                    // Cho phép nếu mã challenge này đã được xác thực trước đó qua verifyPhoneOtp (trong vòng 15 phút)
                    if (authFlowService.isRecentlyConsumedChallenge(request.challengeId(), userId, "CHANGE_PHONE", Duration.ofMinutes(15))
                            || authFlowService.isRecentlyConsumedChallenge(request.challengeId(), userId, "SECURITY_SETTING", Duration.ofMinutes(15))) {
                        verified = true;
                    }
                }
                if (!verified) {
                    throw new IllegalStateException("Mã xác thực OTP không chính xác hoặc đã hết hạn.");
                }
            } else if (hasVerifiedKyc) {
                throw new IllegalStateException("Thao tác thay đổi số điện thoại yêu cầu xác thực nhanh khuôn mặt (UC08) hoặc mã OTP qua Email.");
            } else {
                throw new IllegalStateException("Thao tác thay đổi số điện thoại yêu cầu nhập mã OTP qua Email.");
            }
        }

        Instant now = clock.instant();
        String targetFullName = (request.fullName() != null && !request.fullName().isBlank())
                ? request.fullName().trim()
                : user.getFullName();
        user.updateProfile(targetFullName, request.phoneNumber(), request.avatarUrl(), now);
        userRepository.save(user);
        return getProfile(userId);
    }

    public ProfileDtos.PhoneOtpChallengeResponse issuePhoneChangeOtp(long userId, String newPhone) {
        return issuePhoneChangeOtp(userId, newPhone, null);
    }

    public ProfileDtos.PhoneOtpChallengeResponse issuePhoneChangeOtp(long userId, String newPhone, String requester) {
        UserEntity user = getUserOrThrow(userId);
        if (newPhone != null && !newPhone.isBlank()) {
            String trimmedPhone = newPhone.trim();
            String currentPhone = user.getPhoneNumber() != null ? user.getPhoneNumber().trim() : "";
            if (!trimmedPhone.equals(currentPhone)) {
                userRepository.findByPhoneNumber(trimmedPhone).ifPresent(existingUser -> {
                    if (existingUser.getId() == null || !existingUser.getId().equals(userId)) {
                        throw new DuplicatePhoneException("Số điện thoại này đã được sử dụng bởi một tài khoản khác.");
                    }
                });
            }
        }
        AuthFlowService.Pending pending = authFlowService.startPhoneVerification(
                userId,
                newPhone,
                requester != null ? requester : "profile-phone:" + userId
        );
        return new ProfileDtos.PhoneOtpChallengeResponse(
                pending.challengeId(),
                maskPhone(newPhone),
                maskEmail(user.getEmail()),
                pending.expiresAt(),
                pending.resendAt()
        );
    }

    public record PhoneAvailabilityResult(
            boolean available,
            String reason,
            String message
    ) {}

    public PhoneAvailabilityResult checkPhoneAvailability(long userId, String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return new PhoneAvailabilityResult(false, "INVALID_FORMAT", "Số điện thoại không được để trống.");
        }
        String trimmed = phoneNumber.trim();
        UserEntity user = getUserOrThrow(userId);
        String currentPhone = user.getPhoneNumber() != null ? user.getPhoneNumber().trim() : "";
        if (trimmed.equals(currentPhone)) {
            return new PhoneAvailabilityResult(false, "SAME_AS_CURRENT", "Số điện thoại này trùng với số điện thoại bạn đang sử dụng hiện tại.");
        }
        var existing = userRepository.findByPhoneNumber(trimmed);
        if (existing.isPresent() && (existing.get().getId() == null || !existing.get().getId().equals(userId))) {
            return new PhoneAvailabilityResult(false, "ALREADY_EXISTS", "Số điện thoại này đã được đăng ký bởi một người khác.");
        }
        return new PhoneAvailabilityResult(true, "AVAILABLE", "Số điện thoại hợp lệ và khả dụng.");
    }

    public boolean isPhoneAvailable(long userId, String phoneNumber) {
        return checkPhoneAvailability(userId, phoneNumber).available();
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return phone.substring(0, 3) + "***" + phone.substring(phone.length() - 2);
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int atIndex = email.indexOf('@');
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (name.length() <= 2) {
            return name.charAt(0) + "***" + domain;
        }
        return name.substring(0, 2) + "***" + domain;
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
        user.updateProfile(request.fullName(), null, avatarUrl, clock.instant());
        userRepository.save(user);
        return getProfile(userId);
    }

    public void changePassword(long userId, ProfileDtos.ChangePasswordRequest request) {
        PasswordPolicy.requireNewPassword(request.newPassword());
        UserEntity user = userRepository.findLockedById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Mật khẩu hiện tại không chính xác.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu mới không được trùng với mật khẩu cũ.");
        }
        Instant now = clock.instant();
        user.updatePassword(passwordEncoder.encode(request.newPassword()), now);
        userRepository.save(user);
    }

    public ProfileDtos.BankOtpChallengeResponse issueBankChangeOtp(long userId, String requester) {
        UserEntity user = getUserOrThrow(userId);
        AuthFlowService.Pending pending = authFlowService.startSecuritySetting(userId, user.getEmail(), requester != null ? requester : "profile-bank:" + userId);
        return new ProfileDtos.BankOtpChallengeResponse(
                pending.challengeId(),
                maskEmail(user.getEmail()),
                pending.expiresAt(),
                pending.resendAt()
        );
    }

    public ProfileDtos.ProfileResponse updateBankAccount(long userId, ProfileDtos.UpdateBankRequest request) {
        UserEntity user = getUserOrThrow(userId);
        boolean isChanging = user.getBankAccountNumber() != null && !user.getBankAccountNumber().isBlank();

        if (isChanging) {
            boolean hasVerifiedKyc = ekycRepository.findApprovedReferenceAsset(userId).isPresent();

            if (hasVerifiedKyc && request.quickAuthResultId() != null) {
                // Nhánh 1: Tài khoản đã eKYC -> Bắt buộc xác thực Quick Face Auth (UC08)
                var validResult = ekycRepository.findValidQuickAuthResult(userId);
                if (validResult.isEmpty() || !validResult.get().resultId().equals(request.quickAuthResultId())) {
                    throw new IllegalStateException("Kết quả xác thực khuôn mặt không hợp lệ hoặc đã hết hạn. Vui lòng quét lại khuôn mặt.");
                }
            } else if (request.challengeId() != null) {
                boolean verified = false;
                if (request.otpCode() != null && !request.otpCode().isBlank()) {
                    try {
                        authFlowService.verifySecuritySetting(request.challengeId(), request.otpCode().trim(), userId);
                        verified = true;
                    } catch (Exception ignored) {
                    }
                }
                if (!verified) {
                    if (authFlowService.isRecentlyConsumedChallenge(request.challengeId(), userId, "SECURITY_SETTING", Duration.ofMinutes(15))) {
                        verified = true;
                    }
                }
                if (!verified) {
                    throw new IllegalStateException("Mã xác thực OTP không chính xác hoặc đã hết hạn.");
                }
            } else if (hasVerifiedKyc) {
                throw new IllegalStateException("Thao tác thay đổi tài khoản ngân hàng yêu cầu xác thực nhanh khuôn mặt (UC08).");
            } else {
                // Nhánh 2: Tài khoản chưa eKYC -> Bắt buộc xác thực qua OTP Email
                throw new IllegalStateException("Thao tác thay đổi tài khoản ngân hàng yêu cầu nhập mã OTP xác thực gửi qua email.");
            }
        }

        Instant now = clock.instant();
        user.updateBankAccount(request.bankName(), request.bankAccountNumber(), request.bankAccountHolder(), now);
        userRepository.save(user);

        return getProfile(userId);
    }

    public void verifyPhoneOtp(long userId, UUID challengeId, String code) {
        try {
            authFlowService.verifySecuritySetting(challengeId, code.trim(), userId);
        } catch (Exception e) {
            authFlowService.verifyPhoneVerification(challengeId, code.trim(), userId);
        }
    }

    @Transactional(readOnly = true)
    public List<ProfileDtos.AddressResponse> getAddresses(long userId) {
        return addressRepository.findByUserIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtDesc(userId)
                .stream()
                .map(this::toAddressResponse)
                .toList();
    }

    public ProfileDtos.AddressResponse createAddress(long userId, ProfileDtos.CreateAddressRequest request) {
        validateGhnCodes(request.ghnProvinceId(), request.ghnDistrictId(), request.ghnWardCode());

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
                request.ghnProvinceId(),
                request.ghnDistrictId(),
                request.ghnWardCode() != null ? request.ghnWardCode().trim() : null,
                shouldBeDefault,
                now
        );
        AddressEntity saved = addressRepository.save(entity);
        return toAddressResponse(saved);
    }

    public ProfileDtos.AddressResponse updateAddress(long userId, long addressId, ProfileDtos.UpdateAddressRequest request) {
        validateGhnCodes(request.ghnProvinceId(), request.ghnDistrictId(), request.ghnWardCode());

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
                request.ghnProvinceId(),
                request.ghnDistrictId(),
                request.ghnWardCode() != null ? request.ghnWardCode().trim() : null,
                now
        );
        AddressEntity saved = addressRepository.save(address);
        return toAddressResponse(saved);
    }

    private static void validateGhnCodes(Integer provinceId, Integer districtId, String wardCode) {
        boolean anyProvided = provinceId != null || districtId != null || wardCode != null;
        if (!anyProvided) {
            return;
        }
        if (provinceId == null || provinceId <= 0) {
            throw new IllegalArgumentException("Mã tỉnh/thành GHN (ghnProvinceId) không hợp lệ.");
        }
        if (districtId == null || districtId <= 0) {
            throw new IllegalArgumentException("Mã quận/huyện GHN (ghnDistrictId) không hợp lệ.");
        }
        if (wardCode == null || wardCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Mã phường/xã GHN (ghnWardCode) không được để trống.");
        }
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
                entity.getGhnProvinceId(),
                entity.getGhnDistrictId(),
                entity.getGhnWardCode(),
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
