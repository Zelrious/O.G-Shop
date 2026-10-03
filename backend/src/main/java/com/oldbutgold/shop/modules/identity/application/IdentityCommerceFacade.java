package com.oldbutgold.shop.modules.identity.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IdentityCommerceFacade {
    boolean isUserVerified(long userId);

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
            boolean isDefault,
            Instant createdAt
    ) {}

    record CreateAddressRequest(
            String recipientName,
            String phoneNumber,
            String province,
            String district,
            String ward,
            String detailAddress,
            boolean isDefault
    ) {}
}
