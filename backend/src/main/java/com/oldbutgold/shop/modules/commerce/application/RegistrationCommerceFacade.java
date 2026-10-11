package com.oldbutgold.shop.modules.commerce.application;

public interface RegistrationCommerceFacade {
    void grantRegistrationReward(long userId);
    AddressPair createSellerAddresses(long userId, String name, String phone, Address pickup, Address returns);
    record Address(String province, String district, String ward, String detailAddress) {}
    record AddressPair(long pickupId, long returnId) {}
}
