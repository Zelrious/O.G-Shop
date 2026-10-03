package com.oldbutgold.shop.modules.identity.application;

public interface AvatarStoragePort {
    StoredAvatar upload(long userId, AvatarImage image);

    void delete(StoredAvatar avatar);

    record StoredAvatar(String secureUrl, String publicId) {}
}
