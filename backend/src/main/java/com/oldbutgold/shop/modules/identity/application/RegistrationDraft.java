package com.oldbutgold.shop.modules.identity.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationDraft(String email, String passwordHash, String fullName, String phoneNumber,
                                Type accountType, Seller seller, String googleSubject) {
    public RegistrationDraft(String email,String passwordHash,String fullName,String phoneNumber,Type type,Seller seller) {
        this(email,passwordHash,fullName,phoneNumber,type,seller,null);
    }
    public enum Type { BUYER, SELLER }

    public record Address(
            @NotBlank @Size(max=100) String province,
            @NotBlank @Size(max=100) String district,
            @NotBlank @Size(max=100) String ward,
            @NotBlank @Size(max=255) String detailAddress) {}

    public record Seller(
            @NotBlank @Size(max=120) String storeName,
            @NotBlank @Pattern(regexp="^(0|\\+84)[35789][0-9]{8}$") String contactPhone,
            @NotBlank @Size(max=100) String bankName,
            @NotBlank @Pattern(regexp="^[0-9]{6,30}$") String accountNumber,
            @NotBlank @Size(max=120) String accountHolder,
            @NotNull @Valid Address pickupAddress,
            @NotNull @Valid Address returnAddress) {}

    @Override public String toString() { return "RegistrationDraft[redacted]"; }
}
