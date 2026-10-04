package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.AddressEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.AddressRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.SellerVerificationRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserEntity;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class DefaultIdentityCommerceFacade implements IdentityCommerceFacade {
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final SellerVerificationRepository sellerVerificationRepository;
    private final Clock clock;

    public DefaultIdentityCommerceFacade(UserRepository userRepository,
                                        AddressRepository addressRepository,
                                        SellerVerificationRepository sellerVerificationRepository,
                                        Clock clock) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.sellerVerificationRepository = sellerVerificationRepository;
        this.clock = clock;
    }

    @Override
    public boolean isUserVerified(long userId) {
        return sellerVerificationRepository.findFirstByUserIdAndStatus(userId, "VERIFIED").isPresent();
    }

    @Override
    public Optional<UserSummary> getUserSummary(long userId) {
        return userRepository.findById(userId)
                .map(u -> new UserSummary(u.getId(), u.getFullName(), u.getEmail(), u.getPhoneNumber()));
    }

    @Override
    public Optional<AddressDto> getAddressById(long userId, long addressId) {
        return addressRepository.findByIdAndUserIdAndDeletedAtIsNull(addressId, userId)
                .map(this::toDto);
    }

    @Override
    public Optional<AddressDto> getDefaultAddress(long userId) {
        return addressRepository.findByUserIdAndIsDefaultTrueAndDeletedAtIsNull(userId)
                .map(this::toDto);
    }

    @Override
    public List<AddressDto> getUserAddresses(long userId) {
        return addressRepository.findByUserIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public AddressDto createAddress(long userId, CreateAddressRequest request) {
        Instant now = clock.instant();
        List<AddressEntity> existing = addressRepository.findByUserIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtDesc(userId);
        boolean shouldBeDefault = request.isDefault() || existing.isEmpty();

        if (shouldBeDefault && !existing.isEmpty()) {
            addressRepository.findByUserIdAndIsDefaultTrueAndDeletedAtIsNull(userId)
                    .ifPresent(curr -> {
                        curr.setDefault(false);
                        addressRepository.save(curr);
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
        return toDto(saved);
    }

    private AddressDto toDto(AddressEntity e) {
        return new AddressDto(
                e.getId(),
                e.getUserId(),
                e.getRecipientName(),
                e.getPhoneNumber(),
                e.getProvince(),
                e.getDistrict(),
                e.getWard(),
                e.getDetailAddress(),
                e.isDefault(),
                e.getCreatedAt()
        );
    }
}
