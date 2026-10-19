package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.AddressEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.AddressRepository;
import com.oldbutgold.shop.modules.identity.api.ProfileDtos;
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
                clock
        );
    }

    @Test
    @DisplayName("Lấy thông tin profile với số tài khoản ngân hàng được che mặt định ****1234")
    void getProfile_MasksBankAccountNumber() {
        UserEntity user = new UserEntity("buyer@example.com", "hash", "Nguyễn Văn A", "0901234567", clock.instant());
        user.updateBankAccount("MB Bank", "0388654321", "NGUYEN VAN A", clock.instant());

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sellerVerificationRepository.findFirstByUserIdAndStatus(1L, "VERIFIED")).thenReturn(Optional.empty());

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

        ProfileDtos.AvatarProfileRequest request = new ProfileDtos.AvatarProfileRequest("Nguyễn Văn Mới", "0909999888");
        String avatarUrl = "https://res.cloudinary.com/demo/image/upload/v1/avatar.jpg";

        ProfileDtos.ProfileResponse response = profileService.updateProfileWithAvatar(1L, request, avatarUrl);

        assertThat(response.fullName()).isEqualTo("Nguyễn Văn Mới");
        assertThat(response.phoneNumber()).isEqualTo("0909999888");
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
}
