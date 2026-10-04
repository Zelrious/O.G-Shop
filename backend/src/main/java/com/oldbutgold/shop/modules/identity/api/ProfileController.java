package com.oldbutgold.shop.modules.identity.api;

import com.oldbutgold.shop.modules.identity.application.ProfileService;
import com.oldbutgold.shop.modules.identity.application.ProfileAvatarService;
import com.oldbutgold.shop.modules.identity.application.AvatarImage;
import com.oldbutgold.shop.modules.identity.application.InvalidAvatarException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {
    private final ProfileService profileService;
    private final ProfileAvatarService avatars;

    public ProfileController(ProfileService profileService, ProfileAvatarService avatars) {
        this.profileService = profileService;
        this.avatars = avatars;
    }

    @GetMapping
    public ResponseEntity<ProfileDtos.ProfileResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
        long userId = extractUserId(jwt);
        return ResponseEntity.ok(profileService.getProfile(userId));
    }

    @PutMapping
    public ResponseEntity<ProfileDtos.ProfileResponse> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfileDtos.UpdateProfileRequest request
    ) {
        long userId = extractUserId(jwt);
        return ResponseEntity.ok(profileService.updateProfile(userId, request));
    }

    @PutMapping(value = "/with-avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProfileDtos.ProfileResponse> updateWithAvatar(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestPart("profile") ProfileDtos.AvatarProfileRequest request,
            @RequestPart("avatar") MultipartFile avatar) {
        long userId = extractUserId(jwt);
        if (avatar.getSize() > AvatarImage.MAX_BYTES) {
            throw new InvalidAvatarException("Ảnh đại diện tối đa 5 MiB.");
        }
        try (var input = avatar.getInputStream()) {
            return ResponseEntity.ok(avatars.update(userId, request, input.readNBytes(AvatarImage.MAX_BYTES + 1)));
        } catch (IOException exception) {
            throw new InvalidAvatarException("Không thể đọc ảnh đại diện. Vui lòng chọn lại ảnh.");
        }
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfileDtos.ChangePasswordRequest request
    ) {
        long userId = extractUserId(jwt);
        profileService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/bank")
    public ResponseEntity<ProfileDtos.ProfileResponse> updateBankAccount(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfileDtos.UpdateBankRequest request
    ) {
        long userId = extractUserId(jwt);
        return ResponseEntity.ok(profileService.updateBankAccount(userId, request));
    }

    @GetMapping("/addresses")
    public ResponseEntity<List<ProfileDtos.AddressResponse>> getAddresses(@AuthenticationPrincipal Jwt jwt) {
        long userId = extractUserId(jwt);
        return ResponseEntity.ok(profileService.getAddresses(userId));
    }

    @PostMapping("/addresses")
    public ResponseEntity<ProfileDtos.AddressResponse> createAddress(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfileDtos.CreateAddressRequest request
    ) {
        long userId = extractUserId(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(profileService.createAddress(userId, request));
    }

    @PutMapping("/addresses/{addressId}")
    public ResponseEntity<ProfileDtos.AddressResponse> updateAddress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long addressId,
            @Valid @RequestBody ProfileDtos.UpdateAddressRequest request
    ) {
        long userId = extractUserId(jwt);
        return ResponseEntity.ok(profileService.updateAddress(userId, addressId, request));
    }

    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<Void> deleteAddress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long addressId
    ) {
        long userId = extractUserId(jwt);
        profileService.deleteAddress(userId, addressId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/addresses/{addressId}/default")
    public ResponseEntity<ProfileDtos.AddressResponse> setDefaultAddress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long addressId
    ) {
        long userId = extractUserId(jwt);
        return ResponseEntity.ok(profileService.setDefaultAddress(userId, addressId));
    }

    private static long extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AccessDeniedException("Yêu cầu đăng nhập.");
        }
        return Long.parseLong(jwt.getSubject());
    }
}
