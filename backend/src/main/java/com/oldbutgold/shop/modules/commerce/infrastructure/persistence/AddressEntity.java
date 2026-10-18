package com.oldbutgold.shop.modules.commerce.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "addresses")
public class AddressEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "address_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "recipient_name", nullable = false, length = 120)
    private String recipientName;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(nullable = false, length = 100)
    private String province;

    @Column(nullable = false, length = 100)
    private String district;

    @Column(nullable = false, length = 100)
    private String ward;

    @Column(name = "detail_address", nullable = false, length = 255)
    private String detailAddress;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "ghn_province_id")
    private Integer ghnProvinceId;

    @Column(name = "ghn_district_id")
    private Integer ghnDistrictId;

    @Column(name = "ghn_ward_code", length = 20)
    private String ghnWardCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected AddressEntity() {}

    public AddressEntity(Long userId, String recipientName, String phoneNumber,
                         String province, String district, String ward,
                         String detailAddress, boolean isDefault, Instant now) {
        this(userId, recipientName, phoneNumber, province, district, ward, detailAddress, null, null, null, isDefault, now);
    }

    public AddressEntity(Long userId, String recipientName, String phoneNumber,
                         String province, String district, String ward,
                         String detailAddress, Integer ghnProvinceId, Integer ghnDistrictId,
                         String ghnWardCode, boolean isDefault, Instant now) {
        this.userId = userId;
        this.recipientName = recipientName;
        this.phoneNumber = phoneNumber;
        this.province = province;
        this.district = district;
        this.ward = ward;
        this.detailAddress = detailAddress;
        this.ghnProvinceId = ghnProvinceId;
        this.ghnDistrictId = ghnDistrictId;
        this.ghnWardCode = ghnWardCode != null ? ghnWardCode.trim() : null;
        this.isDefault = isDefault;
        this.createdAt = now;
    }

    public void update(String recipientName, String phoneNumber, String province,
                       String district, String ward, String detailAddress, Instant now) {
        update(recipientName, phoneNumber, province, district, ward, detailAddress, this.ghnProvinceId, this.ghnDistrictId, this.ghnWardCode, now);
    }

    public void update(String recipientName, String phoneNumber, String province,
                       String district, String ward, String detailAddress,
                       Integer ghnProvinceId, Integer ghnDistrictId, String ghnWardCode, Instant now) {
        this.recipientName = recipientName.trim();
        this.phoneNumber = phoneNumber.trim();
        this.province = province.trim();
        this.district = district.trim();
        this.ward = ward.trim();
        this.detailAddress = detailAddress.trim();
        this.ghnProvinceId = ghnProvinceId;
        this.ghnDistrictId = ghnDistrictId;
        this.ghnWardCode = ghnWardCode != null ? ghnWardCode.trim() : null;
        this.updatedAt = now;
    }

    public boolean hasValidGhnCodes() {
        return ghnProvinceId != null && ghnDistrictId != null && ghnWardCode != null && !ghnWardCode.isBlank();
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
        this.isDefault = false;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getRecipientName() { return recipientName; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getProvince() { return province; }
    public String getDistrict() { return district; }
    public String getWard() { return ward; }
    public String getDetailAddress() { return detailAddress; }
    public Integer getGhnProvinceId() { return ghnProvinceId; }
    public void setGhnProvinceId(Integer ghnProvinceId) { this.ghnProvinceId = ghnProvinceId; }
    public Integer getGhnDistrictId() { return ghnDistrictId; }
    public void setGhnDistrictId(Integer ghnDistrictId) { this.ghnDistrictId = ghnDistrictId; }
    public String getGhnWardCode() { return ghnWardCode; }
    public void setGhnWardCode(String ghnWardCode) { this.ghnWardCode = ghnWardCode != null ? ghnWardCode.trim() : null; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
