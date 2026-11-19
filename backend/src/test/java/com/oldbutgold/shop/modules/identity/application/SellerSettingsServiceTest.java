package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.AddressEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.AddressRepository;
import com.oldbutgold.shop.modules.identity.api.SellerSettingsDtos.*;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.EkycRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.SellerVerificationEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.SellerVerificationRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SellerSettingsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private SellerVerificationRepository sellerVerificationRepository;

    @Mock
    private EkycRepository ekycRepository;

    @Mock
    private JdbcTemplate jdbc;

    private Clock clock;
    private SellerSettingsService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC);
        service = new SellerSettingsService(
                userRepository,
                addressRepository,
                sellerVerificationRepository,
                ekycRepository,
                jdbc,
                clock
        );
    }

    @Test
    @DisplayName("getSettings trả về đầy đủ thông tin gian hàng và hotline từ SĐT chính")
    void getSettings_ReturnsFullSellerSettings() {
        UserEntity user = new UserEntity("seller@example.com", "hash", "Trần Thị B", "0912345678", clock.instant());
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 1L);
        user.updateBankAccount("Vietcombank", "0123456789", "TRAN THI B", clock.instant());

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of(
                Map.of("store_name", "Shop Vintage B", "pickup_address_id", 10L, "return_address_id", 11L)
        ));
        when(ekycRepository.hasVerifiedProfile(1L)).thenReturn(true);
        when(ekycRepository.findLatestProfile(1L)).thenReturn(Optional.empty());

        SellerSettingsResponse response = service.getSettings(1L);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.storeName()).isEqualTo("Shop Vintage B");
        assertThat(response.hotlinePhone()).isEqualTo("0912345678");
        assertThat(response.pickupAddressId()).isEqualTo(10L);
        assertThat(response.returnAddressId()).isEqualTo(11L);
        assertThat(response.bankName()).isEqualTo("Vietcombank");
        assertThat(response.bankAccountNumberMasked()).isEqualTo("******6789");
        assertThat(response.verifiedSeller()).isTrue();
    }

    @Test
    @DisplayName("updateSettings cập nhật thành công kho lấy hàng và kho trả hàng hợp lệ")
    void updateSettings_Success() {
        UserEntity user = new UserEntity("seller@example.com", "hash", "Trần Thị B", "0912345678", clock.instant());

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(addressRepository.findByIdAndUserIdAndDeletedAtIsNull(10L, 1L)).thenReturn(Optional.of(mock(AddressEntity.class)));
        when(addressRepository.findByIdAndUserIdAndDeletedAtIsNull(11L, 1L)).thenReturn(Optional.of(mock(AddressEntity.class)));

        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of(
                Map.of("seller_profile_id", 100L, "state", "PENDING_EKYC", "pickup_address_id", 10L)
        ));

        UpdateSellerSettingsRequest req = new UpdateSellerSettingsRequest("New Store Name", 10L, 11L);
        SellerSettingsResponse response = service.updateSettings(1L, req);

        verify(jdbc).update(anyString(), eq("New Store Name"), eq(10L), eq(11L), eq("0912345678"), any(), eq(100L));
    }

    @Test
    @DisplayName("updateSettings ném ngoại lệ khi pickupAddressId không thuộc về người dùng")
    void updateSettings_ThrowsWhenAddressInvalid() {
        UserEntity user = new UserEntity("seller@example.com", "hash", "Trần Thị B", "0912345678", clock.instant());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(addressRepository.findByIdAndUserIdAndDeletedAtIsNull(999L, 1L)).thenReturn(Optional.empty());

        UpdateSellerSettingsRequest req = new UpdateSellerSettingsRequest("Store", 999L, null);

        assertThatThrownBy(() -> service.updateSettings(1L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Địa chỉ lấy hàng không hợp lệ");
    }
}
