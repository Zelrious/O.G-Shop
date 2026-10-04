package com.oldbutgold.shop.modules.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class ProfileDtos {
    private ProfileDtos() {}

    public record ProfileResponse(
            Long id,
            String email,
            String fullName,
            String phoneNumber,
            String avatarUrl,
            Set<String> roles,
            String bankName,
            String bankAccountNumberMasked,
            String bankAccountHolder,
            boolean verifiedSeller,
            Instant createdAt
    ) {}

    public record UpdateProfileRequest(
            @NotBlank(message = "Họ và tên không được để trống")
            @Size(max = 120, message = "Họ và tên tối đa 120 ký tự")
            String fullName,

            @Pattern(regexp = "^(0|\\+84)[3|5|7|8|9][0-9]{8}$|^$", message = "Số điện thoại không hợp lệ")
            String phoneNumber,

            @Size(max = 500, message = "Đường dẫn ảnh đại diện tối đa 500 ký tự")
            String avatarUrl
    ) {}

    public record AvatarProfileRequest(
            @NotBlank(message = "Họ và tên không được để trống")
            @Size(max = 120, message = "Họ và tên tối đa 120 ký tự")
            String fullName,
            @Pattern(regexp = "^(0|\\+84)[35789][0-9]{8}$|^$", message = "Số điện thoại không hợp lệ")
            String phoneNumber
    ) {}

    public record ChangePasswordRequest(
            @NotBlank(message = "Mật khẩu hiện tại không được để trống")
            String currentPassword,

            @NotBlank(message = "Mật khẩu mới không được để trống")
            @Size(min = 6, max = 72, message = "Mật khẩu mới phải từ 6 đến 72 ký tự")
            String newPassword
    ) {}

    public record UpdateBankRequest(
            @NotBlank(message = "Tên ngân hàng không được để trống")
            @Size(max = 100, message = "Tên ngân hàng tối đa 100 ký tự")
            String bankName,

            @NotBlank(message = "Số tài khoản không được để trống")
            @Size(max = 50, message = "Số tài khoản tối đa 50 ký tự")
            String bankAccountNumber,

            @NotBlank(message = "Tên chủ tài khoản không được để trống")
            @Size(max = 120, message = "Tên chủ tài khoản tối đa 120 ký tự")
            String bankAccountHolder
    ) {}

    public record AddressResponse(
            Long id,
            String recipientName,
            String phoneNumber,
            String province,
            String district,
            String ward,
            String detailAddress,
            boolean isDefault,
            Instant createdAt
    ) {}

    public record CreateAddressRequest(
            @NotBlank(message = "Tên người nhận không được để trống")
            @Size(max = 120, message = "Tên người nhận tối đa 120 ký tự")
            String recipientName,

            @NotBlank(message = "Số điện thoại không được để trống")
            @Pattern(regexp = "^(0|\\+84)[3|5|7|8|9][0-9]{8}$", message = "Số điện thoại người nhận không hợp lệ")
            String phoneNumber,

            @NotBlank(message = "Tỉnh/Thành phố không được để trống")
            String province,

            @NotBlank(message = "Quận/Huyện không được để trống")
            String district,

            @NotBlank(message = "Phường/Xã không được để trống")
            String ward,

            @NotBlank(message = "Địa chỉ chi tiết không được để trống")
            @Size(max = 255, message = "Địa chỉ chi tiết tối đa 255 ký tự")
            String detailAddress,

            boolean isDefault
    ) {}

    public record UpdateAddressRequest(
            @NotBlank(message = "Tên người nhận không được để trống")
            @Size(max = 120, message = "Tên người nhận tối đa 120 ký tự")
            String recipientName,

            @NotBlank(message = "Số điện thoại không được để trống")
            @Pattern(regexp = "^(0|\\+84)[3|5|7|8|9][0-9]{8}$", message = "Số điện thoại người nhận không hợp lệ")
            String phoneNumber,

            @NotBlank(message = "Tỉnh/Thành phố không được để trống")
            String province,

            @NotBlank(message = "Quận/Huyện không được để trống")
            String district,

            @NotBlank(message = "Phường/Xã không được để trống")
            String ward,

            @NotBlank(message = "Địa chỉ chi tiết không được để trống")
            @Size(max = 255, message = "Địa chỉ chi tiết tối đa 255 ký tự")
            String detailAddress,

            boolean isDefault
    ) {}
}
