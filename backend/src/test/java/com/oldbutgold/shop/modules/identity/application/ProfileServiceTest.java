package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.AddressEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.AddressRepository;
import com.oldbutgold.shop.modules.identity.api.ProfileDtos;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.SellerVerificationRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private SellerVerificationRepository sellerVerificationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EkycRepository ekycRepository;

    @Mock
    private AuthFlowService authFlowService;

    private Clock clock;
    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-02T10:00:00Z"), ZoneId.of("UTC"));
        profileService = new ProfileService(
                userRepository,
                addressRepository,
                sellerVerificationRepository,
                passwordEncoder,
                clock,
                ekycRepository,
                authFlowService
        );
    }

    @Test
    @DisplayName("Lấy thông tin profile với số tài khoản ngân hàng được che mặt định ****1234")
    void getProfile_MasksBankAccountNumber() {
        UserEntity user = new UserEntity("buyer@example.com", "hash", "Nguyễn Văn A", "0901234567", clock.instant());
        user.updateBankAccount("MB Bank", "0388654321", "NGUYEN VAN A", clock.instant());

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ekycRepository.hasVerifiedProfile(1L)).thenReturn(false);

        ProfileDtos.ProfileResponse response = profileService.getProfile(1L);

        assertThat(response.fullName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.bankName()).isEqualTo("MB Bank");
        assertThat(response.bankAccountNumberMasked()).isEqualTo("******4321");
        assertThat(response.bankAccountHolder()).isEqualTo("NGUYEN VAN A");
    }

    @Test
    @DisplayName("Đổi mật khẩu thất bại khi nhập sai mật khẩu hiện tại")
    void changePassword_WrongCurrentPassword_ThrowsException() {
        UserEntity user = new UserEntity("buyer@example.com", "correct_hash", "Nguyễn Văn A", "0901234567", clock.instant());
        when(userRepository.findLockedById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong_pass", "correct_hash")).thenReturn(false);

        assertThatThrownBy(() -> profileService.changePassword(1L, new ProfileDtos.ChangePasswordRequest("wrong_pass", "new_pass_123")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Mật khẩu hiện tại không chính xác");
    }

    @Test
    @DisplayName("Đổi mật khẩu thất bại khi mật khẩu mới trùng với mật khẩu hiện tại")
    void changePassword_SamePassword_ThrowsException() {
        UserEntity user = new UserEntity("buyer@example.com", "correct_hash", "Nguyễn Văn A", "0901234567", clock.instant());
        when(userRepository.findLockedById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current_pass", "correct_hash")).thenReturn(true);
        when(passwordEncoder.matches("same_new_pass", "correct_hash")).thenReturn(true);

        assertThatThrownBy(() -> profileService.changePassword(1L, new ProfileDtos.ChangePasswordRequest("current_pass", "same_new_pass")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mật khẩu mới không được trùng với mật khẩu cũ.");
    }

    @Test void rejectsShortOrOversizedNewPasswordBeforeChangingAccount() {
        for(String value:List.of("short6","ắ".repeat(25))) {
            assertThatThrownBy(()->profileService.changePassword(1L,
                    new ProfileDtos.ChangePasswordRequest("current",value))).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(userRepository,passwordEncoder);
    }

    @Test
    @DisplayName("Xóa địa chỉ mặc định thì tự động chuyển mặc định cho địa chỉ đầu tiên còn lại trong danh sách")
    void deleteAddress_DefaultDeleted_AssignsDefaultToNextAddress() {
        Instant now = clock.instant();
        AddressEntity defaultAddr = new AddressEntity(1L, "Người Nhận 1", "0901234567", "Hà Nội", "Cầu Giấy", "Dịch Vọng", "123 Cầu Giấy", true, now);
        AddressEntity secondAddr = new AddressEntity(1L, "Người Nhận 2", "0909876543", "Hồ Chí Minh", "Quận 1", "Bến Nghé", "456 Lê Lợi", false, now);

        when(addressRepository.findByIdAndUserIdAndDeletedAtIsNull(10L, 1L)).thenReturn(Optional.of(defaultAddr));
        when(addressRepository.findByUserIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtDesc(1L))
                .thenReturn(new ArrayList<>(List.of(secondAddr)));

        profileService.deleteAddress(1L, 10L);

        assertThat(defaultAddr.getDeletedAt()).isNotNull();
        assertThat(defaultAddr.isDefault()).isFalse();
        assertThat(secondAddr.isDefault()).isTrue();
        verify(addressRepository).save(defaultAddr);
        verify(addressRepository).save(secondAddr);
    }

    @Test
    @DisplayName("checkProfileEditable ném AccessDeniedException khi trạng thái không phải ACTIVE")
    void checkProfileEditable_NonActiveUser_ThrowsException() {
        UserEntity user = new UserEntity("buyer@example.com", "hash", "Nguyễn Văn A", "0901234567", clock.instant());
        org.springframework.test.util.ReflectionTestUtils.setField(user, "status", "SUSPENDED");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> profileService.checkProfileEditable(1L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("Tài khoản không thể cập nhật hồ sơ.");
    }

    @Test
    @DisplayName("updateProfileWithAvatar cập nhật thông tin và URL ảnh thành công")
    void updateProfileWithAvatar_Success() {
        UserEntity user = new UserEntity("buyer@example.com", "hash", "Nguyễn Văn A", "0901234567", clock.instant());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        ProfileDtos.AvatarProfileRequest request = new ProfileDtos.AvatarProfileRequest("Nguyễn Văn Mới");
        String avatarUrl = "https://res.cloudinary.com/demo/image/upload/v1/avatar.jpg";

        ProfileDtos.ProfileResponse response = profileService.updateProfileWithAvatar(1L, request, avatarUrl);

        assertThat(response.fullName()).isEqualTo("Nguyễn Văn Mới");
        assertThat(response.phoneNumber()).isEqualTo("0901234567");
        assertThat(response.avatarUrl()).isEqualTo(avatarUrl);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("createAddress thành công khi có đầy đủ mã GHN chuẩn hóa")
    void createAddress_SuccessWithGhnCodes() {
        when(addressRepository.findByUserIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtDesc(1L))
                .thenReturn(new ArrayList<>());
        when(addressRepository.save(any(AddressEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfileDtos.CreateAddressRequest request = new ProfileDtos.CreateAddressRequest(
                "Trần Văn B", "0912345678", "TP. Hồ Chí Minh", "Quận 1", "Phường Bến Nghé",
                "123 Đồng Khởi", 202, 1442, "20101", true
        );

        ProfileDtos.AddressResponse response = profileService.createAddress(1L, request);

        assertThat(response.recipientName()).isEqualTo("Trần Văn B");
        assertThat(response.ghnProvinceId()).isEqualTo(202);
        assertThat(response.ghnDistrictId()).isEqualTo(1442);
        assertThat(response.ghnWardCode()).isEqualTo("20101");
        assertThat(response.isDefault()).isTrue();
    }

    @Test
    @DisplayName("createAddress thành công với địa chỉ legacy (không truyền mã GHN)")
    void createAddress_SuccessWithoutGhnCodes() {
        when(addressRepository.findByUserIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtDesc(1L))
                .thenReturn(new ArrayList<>());
        when(addressRepository.save(any(AddressEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfileDtos.CreateAddressRequest request = new ProfileDtos.CreateAddressRequest(
                "Trần Văn B", "0912345678", "TP. Hồ Chí Minh", "Quận 1", "Phường Bến Nghé",
                "123 Đồng Khởi", null, null, null, false
        );

        ProfileDtos.AddressResponse response = profileService.createAddress(1L, request);

        assertThat(response.recipientName()).isEqualTo("Trần Văn B");
        assertThat(response.ghnProvinceId()).isNull();
        assertThat(response.ghnDistrictId()).isNull();
        assertThat(response.ghnWardCode()).isNull();
    }

    @Test
    @DisplayName("createAddress ném IllegalArgumentException khi truyền mã GHN thiếu nhất quán")
    void createAddress_ThrowsWhenGhnCodesInconsistent() {
        // Chỉ truyền provinceId
        ProfileDtos.CreateAddressRequest req1 = new ProfileDtos.CreateAddressRequest(
                "Trần Văn B", "0912345678", "TP. Hồ Chí Minh", "Quận 1", "Phường Bến Nghé",
                "123 Đồng Khởi", 202, null, null, false
        );
        assertThatThrownBy(() -> profileService.createAddress(1L, req1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ghnDistrictId");

        // Thiếu wardCode
        ProfileDtos.CreateAddressRequest req2 = new ProfileDtos.CreateAddressRequest(
                "Trần Văn B", "0912345678", "TP. Hồ Chí Minh", "Quận 1", "Phường Bến Nghé",
                "123 Đồng Khởi", 202, 1442, "", false
        );
        assertThatThrownBy(() -> profileService.createAddress(1L, req2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ghnWardCode");

        // provinceId âm
        ProfileDtos.CreateAddressRequest req3 = new ProfileDtos.CreateAddressRequest(
                "Trần Văn B", "0912345678", "TP. Hồ Chí Minh", "Quận 1", "Phường Bến Nghé",
                "123 Đồng Khởi", -1, 1442, "20101", false
        );
        assertThatThrownBy(() -> profileService.createAddress(1L, req3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ghnProvinceId");
    }

    @Test
    @DisplayName("updateAddress cập nhật thành công các mã GHN")
    void updateAddress_SuccessWithGhnCodes() {
        AddressEntity existing = new AddressEntity(
                1L, "Người cũ", "0901234567", "Tỉnh cũ", "Huyện cũ", "Xã cũ", "Nhà cũ", false, clock.instant()
        );
        when(addressRepository.findByIdAndUserIdAndDeletedAtIsNull(10L, 1L)).thenReturn(Optional.of(existing));
        when(addressRepository.save(any(AddressEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfileDtos.UpdateAddressRequest request = new ProfileDtos.UpdateAddressRequest(
                "Người Mới", "0919999888", "Hà Nội", "Hoàn Kiếm", "Tràng Tiền",
                "1 Tràng Tiền", 201, 1441, "10001", false
        );

        ProfileDtos.AddressResponse response = profileService.updateAddress(1L, 10L, request);

        assertThat(response.recipientName()).isEqualTo("Người Mới");
        assertThat(response.ghnProvinceId()).isEqualTo(201);
        assertThat(response.ghnDistrictId()).isEqualTo(1441);
        assertThat(response.ghnWardCode()).isEqualTo("10001");
        assertThat(existing.hasValidGhnCodes()).isTrue();
    }

    @Test
    @DisplayName("Lần đầu thiết lập STK ngân hàng thành công mà không cần mã xác thực")
    void updateBankAccount_FirstTimeSetup_SuccessWithoutVerification() {
        UserEntity user = new UserEntity("buyer@example.com", "hash", "Nguyễn Văn A", "0901234567", clock.instant());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));

        ProfileDtos.UpdateBankRequest request = new ProfileDtos.UpdateBankRequest(
                "Vietcombank", "0123456789", "NGUYEN VAN A", null, null, null
        );

        ProfileDtos.ProfileResponse response = profileService.updateBankAccount(1L, request);

        assertThat(response.bankName()).isEqualTo("Vietcombank");
        assertThat(user.getBankAccountNumber()).isEqualTo("0123456789");
    }

    @Test
    @DisplayName("Thay đổi STK với tài khoản đã eKYC bị từ chối nếu thiếu kết quả Quick Face Auth")
    void updateBankAccount_ChangeBank_KycVerified_WithoutQuickAuth_ThrowsException() {
        UserEntity user = new UserEntity("seller@example.com", "hash", "Nguyễn Văn B", "0901234568", clock.instant());
        user.updateBankAccount("MB Bank", "11112222", "NGUYEN VAN B", clock.instant());

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ekycRepository.findApprovedReferenceAsset(1L)).thenReturn(Optional.of(mock(EkycRepository.AssetRecord.class)));

        ProfileDtos.UpdateBankRequest request = new ProfileDtos.UpdateBankRequest(
                "Techcombank", "99998888", "NGUYEN VAN B", null, null, null
        );

        assertThatThrownBy(() -> profileService.updateBankAccount(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("yêu cầu xác thực nhanh khuôn mặt (UC08)");
    }

    @Test
    @DisplayName("Thay đổi STK với tài khoản đã eKYC thành công khi có kết quả Quick Face Auth hợp lệ")
    void updateBankAccount_ChangeBank_KycVerified_WithValidQuickAuth_Success() {
        UserEntity user = new UserEntity("seller@example.com", "hash", "Nguyễn Văn B", "0901234568", clock.instant());
        user.updateBankAccount("MB Bank", "11112222", "NGUYEN VAN B", clock.instant());

        UUID resultId = UUID.randomUUID();
        EkycRepository.QuickAuthResultRecord resultRecord = new EkycRepository.QuickAuthResultRecord(
                resultId, 1L, 10L, 100L, clock.instant(), clock.instant().plusSeconds(900), "cmd-key", UUID.randomUUID()
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ekycRepository.findApprovedReferenceAsset(1L)).thenReturn(Optional.of(mock(EkycRepository.AssetRecord.class)));
        when(ekycRepository.findValidQuickAuthResult(1L)).thenReturn(Optional.of(resultRecord));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));

        ProfileDtos.UpdateBankRequest request = new ProfileDtos.UpdateBankRequest(
                "Techcombank", "99998888", "NGUYEN VAN B", resultId, null, null
        );

        ProfileDtos.ProfileResponse response = profileService.updateBankAccount(1L, request);

        assertThat(response.bankName()).isEqualTo("Techcombank");
        assertThat(user.getBankAccountNumber()).isEqualTo("99998888");
    }

    @Test
    @DisplayName("Thay đổi STK với tài khoản chưa eKYC bị từ chối nếu thiếu mã OTP Email")
    void updateBankAccount_ChangeBank_NonKyc_WithoutOtp_ThrowsException() {
        UserEntity user = new UserEntity("buyer@example.com", "hash", "Nguyễn Văn C", "0901234569", clock.instant());
        user.updateBankAccount("VPBank", "33334444", "NGUYEN VAN C", clock.instant());

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ekycRepository.findApprovedReferenceAsset(1L)).thenReturn(Optional.empty());

        ProfileDtos.UpdateBankRequest request = new ProfileDtos.UpdateBankRequest(
                "VietinBank", "55556666", "NGUYEN VAN C", null, null, null
        );

        assertThatThrownBy(() -> profileService.updateBankAccount(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("yêu cầu nhập mã OTP xác thực gửi qua email");
    }

    @Test
    @DisplayName("Thay đổi STK với tài khoản chưa eKYC thành công khi có OTP hợp lệ")
    void updateBankAccount_ChangeBank_NonKyc_WithValidOtp_Success() {
        UserEntity user = new UserEntity("buyer@example.com", "hash", "Nguyễn Văn C", "0901234569", clock.instant());
        user.updateBankAccount("VPBank", "33334444", "NGUYEN VAN C", clock.instant());

        UUID challengeId = UUID.randomUUID();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ekycRepository.findApprovedReferenceAsset(1L)).thenReturn(Optional.empty());
        doNothing().when(authFlowService).verifySecuritySetting(challengeId, "123456", 1L);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));

        ProfileDtos.UpdateBankRequest request = new ProfileDtos.UpdateBankRequest(
                "VietinBank", "55556666", "NGUYEN VAN C", null, challengeId, "123456"
        );

        ProfileDtos.ProfileResponse response = profileService.updateBankAccount(1L, request);

        assertThat(response.bankName()).isEqualTo("VietinBank");
        assertThat(user.getBankAccountNumber()).isEqualTo("55556666");
        verify(authFlowService).verifySecuritySetting(challengeId, "123456", 1L);
    }

    @Test
    @DisplayName("issueBankChangeOtp phát hành thử thách OTP qua email chính chủ")
    void issueBankChangeOtp_Success() {
        UserEntity user = new UserEntity("buyer@example.com", "hash", "Nguyễn Văn C", "0901234569", clock.instant());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UUID challengeId = UUID.randomUUID();
        AuthFlowService.Pending pending = new AuthFlowService.Pending(
                "OTP_REQUIRED", challengeId, "buyer@example.com",
                clock.instant().plusSeconds(600), clock.instant().plusSeconds(60), 5, "SECURITY_SETTING"
        );
        when(authFlowService.startSecuritySetting(1L, "buyer@example.com", "requester-ip")).thenReturn(pending);

        ProfileDtos.BankOtpChallengeResponse response = profileService.issueBankChangeOtp(1L, "requester-ip");

        assertThat(response.challengeId()).isEqualTo(challengeId);
        assertThat(response.maskedEmail()).isEqualTo("bu***@example.com");
    }

    @Test
    @DisplayName("Thay đổi SĐT với tài khoản đã eKYC bị từ chối nếu thiếu kết quả Quick Face Auth")
    void updateProfile_ChangePhone_KycVerified_WithoutQuickAuth_ThrowsException() {
        UserEntity user = new UserEntity("seller@example.com", "hash", "Nguyễn Văn B", "0901234568", clock.instant());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ekycRepository.findApprovedReferenceAsset(1L)).thenReturn(Optional.of(mock(EkycRepository.AssetRecord.class)));

        ProfileDtos.UpdateProfileRequest request = new ProfileDtos.UpdateProfileRequest(
                "Nguyễn Văn B", "0909999888", null, null, null, null
        );

        assertThatThrownBy(() -> profileService.updateProfile(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("yêu cầu xác thực nhanh khuôn mặt (UC08) hoặc mã OTP");
    }

    @Test
    @DisplayName("Thay đổi SĐT với tài khoản đã eKYC thành công khi có kết quả Quick Face Auth hợp lệ")
    void updateProfile_ChangePhone_KycVerified_WithValidQuickAuth_Success() {
        UserEntity user = new UserEntity("seller@example.com", "hash", "Nguyễn Văn B", "0901234568", clock.instant());
        UUID resultId = UUID.randomUUID();
        EkycRepository.QuickAuthResultRecord resultRecord = new EkycRepository.QuickAuthResultRecord(
                resultId, 1L, 10L, 100L, clock.instant(), clock.instant().plusSeconds(900), "cmd-key", UUID.randomUUID()
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ekycRepository.findApprovedReferenceAsset(1L)).thenReturn(Optional.of(mock(EkycRepository.AssetRecord.class)));
        when(ekycRepository.findValidQuickAuthResult(1L)).thenReturn(Optional.of(resultRecord));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));

        ProfileDtos.UpdateProfileRequest request = new ProfileDtos.UpdateProfileRequest(
                "Nguyễn Văn B", "0909999888", null, resultId, null, null
        );

        ProfileDtos.ProfileResponse response = profileService.updateProfile(1L, request);

        assertThat(response.phoneNumber()).isEqualTo("0909999888");
        assertThat(user.getPhoneNumber()).isEqualTo("0909999888");
    }

    @Test
    @DisplayName("Thay đổi SĐT với tài khoản chưa eKYC bị từ chối nếu thiếu OTP")
    void updateProfile_ChangePhone_NonKyc_WithoutOtp_ThrowsException() {
        UserEntity user = new UserEntity("buyer@example.com", "hash", "Nguyễn Văn C", "0901234569", clock.instant());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ekycRepository.findApprovedReferenceAsset(1L)).thenReturn(Optional.empty());

        ProfileDtos.UpdateProfileRequest request = new ProfileDtos.UpdateProfileRequest(
                "Nguyễn Văn C", "0909999777", null, null, null, null
        );

        assertThatThrownBy(() -> profileService.updateProfile(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("yêu cầu nhập mã OTP");
    }

    @Test
    @DisplayName("Thay đổi SĐT thành công với mã OTP Email hợp lệ")
    void updateProfile_ChangePhone_WithValidOtp_Success() {
        UserEntity user = new UserEntity("buyer@example.com", "hash", "Nguyễn Văn C", "0901234569", clock.instant());
        UUID challengeId = UUID.randomUUID();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ekycRepository.findApprovedReferenceAsset(1L)).thenReturn(Optional.empty());
        doNothing().when(authFlowService).verifySecuritySetting(challengeId, "654321", 1L);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));

        ProfileDtos.UpdateProfileRequest request = new ProfileDtos.UpdateProfileRequest(
                "Nguyễn Văn C", "0909999777", null, null, challengeId, "654321"
        );

        ProfileDtos.ProfileResponse response = profileService.updateProfile(1L, request);

        assertThat(response.phoneNumber()).isEqualTo("0909999777");
        verify(authFlowService).verifySecuritySetting(challengeId, "654321", 1L);
    }

    @Test
    @DisplayName("Thay đổi SĐT bị từ chối nếu số điện thoại đã thuộc về tài khoản khác")
    void updateProfile_ChangePhone_WhenPhoneBelongsToAnotherUser_ThrowsDuplicatePhoneException() {
        UserEntity user = new UserEntity("buyer@example.com", "hash", "Nguyễn Văn C", "0901234569", clock.instant());
        UserEntity otherUser = new UserEntity("other@example.com", "hash", "Người Khác", "0909999777", clock.instant());
        org.springframework.test.util.ReflectionTestUtils.setField(otherUser, "id", 2L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByPhoneNumber("0909999777")).thenReturn(Optional.of(otherUser));

        ProfileDtos.UpdateProfileRequest request = new ProfileDtos.UpdateProfileRequest(
                "Nguyễn Văn C", "0909999777", null, null, UUID.randomUUID(), "654321"
        );

        assertThatThrownBy(() -> profileService.updateProfile(1L, request))
                .isInstanceOf(DuplicatePhoneException.class)
                .hasMessageContaining("đã được sử dụng bởi một tài khoản khác");
    }

    @Test
    @DisplayName("issuePhoneChangeOtp bị từ chối nếu số điện thoại dự kiến đổi đã thuộc về tài khoản khác")
    void issuePhoneChangeOtp_WhenPhoneBelongsToAnotherUser_ThrowsDuplicatePhoneException() {
        UserEntity user = new UserEntity("buyer@example.com", "hash", "Nguyễn Văn C", "0901234569", clock.instant());
        UserEntity otherUser = new UserEntity("other@example.com", "hash", "Người Khác", "0909999777", clock.instant());
        org.springframework.test.util.ReflectionTestUtils.setField(otherUser, "id", 2L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByPhoneNumber("0909999777")).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> profileService.issuePhoneChangeOtp(1L, "0909999777"))
                .isInstanceOf(DuplicatePhoneException.class)
                .hasMessageContaining("đã được sử dụng bởi một tài khoản khác");
    }

    @Test
    @DisplayName("issuePhoneChangeOtp phát hành thử thách OTP qua email cho thao tác đổi SĐT")
    void issuePhoneChangeOtp_Success() {
        UserEntity user = new UserEntity("buyer@example.com", "hash", "Nguyễn Văn C", "0901234569", clock.instant());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UUID challengeId = UUID.randomUUID();
        AuthFlowService.Pending pending = new AuthFlowService.Pending(
                "OTP_REQUIRED", challengeId, "buyer@example.com",
                clock.instant().plusSeconds(600), clock.instant().plusSeconds(60), 5, "SECURITY_SETTING"
        );
        when(authFlowService.startPhoneVerification(1L, "0909999777", "req-ip")).thenReturn(pending);

        ProfileDtos.PhoneOtpChallengeResponse response = profileService.issuePhoneChangeOtp(1L, "0909999777", "req-ip");

        assertThat(response.challengeId()).isEqualTo(challengeId);
        assertThat(response.maskedPhone()).isEqualTo("090***77");
    }

    @Test
    @DisplayName("Thay đổi STK với tài khoản đã eKYC thành công khi chọn phương thức dự phòng OTP Email")
    void updateBankAccount_ChangeBank_KycVerified_WithFallbackOtp_Success() {
        UserEntity user = new UserEntity("seller@example.com", "hash", "Nguyễn Văn B", "0901234568", clock.instant());
        user.updateBankAccount("MB Bank", "11112222", "NGUYEN VAN B", clock.instant());

        UUID challengeId = UUID.randomUUID();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ekycRepository.findApprovedReferenceAsset(1L)).thenReturn(Optional.of(mock(EkycRepository.AssetRecord.class)));
        doNothing().when(authFlowService).verifySecuritySetting(challengeId, "999888", 1L);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));

        ProfileDtos.UpdateBankRequest request = new ProfileDtos.UpdateBankRequest(
                "Techcombank", "99998888", "NGUYEN VAN B", null, challengeId, "999888"
        );

        ProfileDtos.ProfileResponse response = profileService.updateBankAccount(1L, request);

        assertThat(response.bankName()).isEqualTo("Techcombank");
        assertThat(user.getBankAccountNumber()).isEqualTo("99998888");
        verify(authFlowService).verifySecuritySetting(challengeId, "999888", 1L);
    }
}
