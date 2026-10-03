package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.identity.api.ProfileDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ProfileAvatarService {
    private static final Logger log = LoggerFactory.getLogger(ProfileAvatarService.class);
    private final ProfileService profiles;
    private final AvatarStoragePort storage;

    public ProfileAvatarService(ProfileService profiles, AvatarStoragePort storage) {
        this.profiles = profiles;
        this.storage = storage;
    }

    public ProfileDtos.ProfileResponse update(long userId, ProfileDtos.AvatarProfileRequest request, byte[] bytes) {
        AvatarImage image = AvatarImage.inspect(bytes);
        profiles.checkProfileEditable(userId);
        AvatarStoragePort.StoredAvatar uploaded = storage.upload(userId, image);
        try {
            // This call goes through a separate Spring proxy. It returns only after commit,
            // so even a commit-time failure triggers compensation for the new asset.
            return profiles.updateProfileWithAvatar(userId, request, uploaded.secureUrl());
        } catch (RuntimeException exception) {
            try {
                storage.delete(uploaded);
            } catch (RuntimeException cleanupFailure) {
                log.warn("Could not remove a new avatar after profile save failed; cleanup required for user {}", userId);
            }
            throw exception;
        }
    }
}
