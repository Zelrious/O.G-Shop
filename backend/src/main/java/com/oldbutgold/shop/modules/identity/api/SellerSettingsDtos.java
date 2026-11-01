package com.oldbutgold.shop.modules.identity.api;

import jakarta.validation.constraints.Size;

public final class SellerSettingsDtos {
    private SellerSettingsDtos() {}

    public record SellerSettingsResponse(
            Long userId,
            String storeName,
            String hotlinePhone,
            Long pickupAddressId,
            Long returnAddressId,
            String bankName,
            String bankAccountNumberMasked,
            String bankAccountHolder,
            boolean verifiedSeller,
            String ekycState,
            boolean hasStoreInfo,
            boolean sellerOnboarded
    ) {
        public SellerSettingsResponse(
                Long userId,
                String storeName,
                String hotlinePhone,
                Long pickupAddressId,
                Long returnAddressId,
                String bankName,
                String bankAccountNumberMasked,
                String bankAccountHolder,
                boolean verifiedSeller,
                String ekycState
        ) {
            this(
                    userId,
                    storeName,
                    hotlinePhone,
                    pickupAddressId,
                    returnAddressId,
                    bankName,
                    bankAccountNumberMasked,
                    bankAccountHolder,
                    verifiedSeller,
                    ekycState,
                    (pickupAddressId != null && returnAddressId != null && storeName != null && !storeName.isBlank() && bankAccountNumberMasked != null),
                    (verifiedSeller && pickupAddressId != null && returnAddressId != null && storeName != null && !storeName.isBlank() && bankAccountNumberMasked != null)
            );
        }
    }

    public record UpdateSellerSettingsRequest(
            @Size(max = 120, message = "Tên gian hàng tối đa 120 ký tự")
            String storeName,

            Long pickupAddressId,
            Long returnAddressId
    ) {}
}
