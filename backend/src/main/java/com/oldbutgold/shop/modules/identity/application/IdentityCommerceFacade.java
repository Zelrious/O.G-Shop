package com.oldbutgold.shop.modules.identity.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IdentityCommerceFacade {
    boolean isUserVerified(long userId);
    void requirePurchaseAllowed(long buyerId,long sellerId,boolean requiresEkyc);

    Optional<UserSummary> getUserSummary(long userId);

    Optional<AddressDto> getAddressById(long userId, long addressId);

    Optional<AddressDto> getDefaultAddress(long userId);

    List<AddressDto> getUserAddresses(long userId);

    AddressDto createAddress(long userId, CreateAddressRequest request);

    record UserSummary(long userId, String fullName, String email, String phoneNumber) {}

    record AddressDto(
            long id,
            long userId,
            String recipientName,
            String phoneNumber,
            String province,
            String district,
            String ward,
            String detailAddress,
            Integer ghnProvinceId,
            Integer ghnDistrictId,
            String ghnWardCode,
            boolean isDefault,
            Instant createdAt
    ) {
        public AddressDto(long id, long userId, String recipientName, String phoneNumber,
                          String province, String district, String ward, String detailAddress,
                          boolean isDefault, Instant createdAt) {
            this(id, userId, recipientName, phoneNumber, province, district, ward, detailAddress, null, null, null, isDefault, createdAt);
        }

        public boolean hasValidGhnCodes() {
            return ghnProvinceId != null && ghnDistrictId != null && ghnWardCode != null && !ghnWardCode.isBlank();
        }
    }

    record CreateAddressRequest(
            String recipientName,
            String phoneNumber,
            String province,
            String district,
            String ward,
            String detailAddress,
            Integer ghnProvinceId,
            Integer ghnDistrictId,
            String ghnWardCode,
            boolean isDefault
    ) {
        public CreateAddressRequest(String recipientName, String phoneNumber, String province,
                                    String district, String ward, String detailAddress, boolean isDefault) {
            this(recipientName, phoneNumber, province, district, ward, detailAddress, null, null, null, isDefault);
        }
    }
}
