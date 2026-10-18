package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.AddressEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.AddressRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.SellerVerificationRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultIdentityCommerceFacadeTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private SellerVerificationRepository sellerVerificationRepository;

    @Mock
    private AccountAccessService accountAccess;

    private Clock clock;
    private DefaultIdentityCommerceFacade facade;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-02T10:00:00Z"), ZoneId.of("UTC"));
        facade = new DefaultIdentityCommerceFacade(
                userRepository,
                addressRepository,
                sellerVerificationRepository,
                clock,
                accountAccess
        );
    }

    @Test
    @DisplayName("createAddress thành công khi có đầy đủ mã GHN chuẩn hóa")
    void createAddress_SuccessWithGhnCodes() {
        when(addressRepository.findByUserIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtDesc(2L))
                .thenReturn(new ArrayList<>());
        when(addressRepository.save(any(AddressEntity.class))).thenAnswer(invocation -> {
            AddressEntity e = invocation.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(e, "id", 100L);
            return e;
        });

        IdentityCommerceFacade.CreateAddressRequest request = new IdentityCommerceFacade.CreateAddressRequest(
                "Lê Văn C", "0987654321", "Đà Nẵng", "Hải Châu", "Hải Châu I",
                "789 Bạch Đằng", 203, 1443, "30101", true
        );

        IdentityCommerceFacade.AddressDto dto = facade.createAddress(2L, request);

        assertThat(dto.id()).isEqualTo(100L);
        assertThat(dto.recipientName()).isEqualTo("Lê Văn C");
        assertThat(dto.ghnProvinceId()).isEqualTo(203);
        assertThat(dto.ghnDistrictId()).isEqualTo(1443);
        assertThat(dto.ghnWardCode()).isEqualTo("30101");
        assertThat(dto.hasValidGhnCodes()).isTrue();
        assertThat(dto.isDefault()).isTrue();
    }

    @Test
    @DisplayName("createAddress ném IllegalArgumentException khi mã GHN không nhất quán")
    void createAddress_ThrowsWhenInconsistent() {
        IdentityCommerceFacade.CreateAddressRequest req1 = new IdentityCommerceFacade.CreateAddressRequest(
                "Lê Văn C", "0987654321", "Đà Nẵng", "Hải Châu", "Hải Châu I",
                "789 Bạch Đằng", 203, null, "30101", false
        );

        assertThatThrownBy(() -> facade.createAddress(2L, req1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ghnDistrictId");
    }

    @Test
    @DisplayName("getAddressById ánh xạ đúng các mã GHN từ entity sang DTO")
    void getAddressById_MapsGhnCodes() {
        AddressEntity entity = new AddressEntity(
                2L, "Lê Văn C", "0987654321", "Đà Nẵng", "Hải Châu", "Hải Châu I",
                "789 Bạch Đằng", 203, 1443, "30101", true, clock.instant()
        );
        org.springframework.test.util.ReflectionTestUtils.setField(entity, "id", 50L);
        when(addressRepository.findByIdAndUserIdAndDeletedAtIsNull(50L, 2L)).thenReturn(Optional.of(entity));

        Optional<IdentityCommerceFacade.AddressDto> opt = facade.getAddressById(2L, 50L);

        assertThat(opt).isPresent();
        assertThat(opt.get().id()).isEqualTo(50L);
        assertThat(opt.get().ghnProvinceId()).isEqualTo(203);
        assertThat(opt.get().ghnDistrictId()).isEqualTo(1443);
        assertThat(opt.get().ghnWardCode()).isEqualTo("30101");
        assertThat(opt.get().hasValidGhnCodes()).isTrue();
    }
}
