package com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class GhnMasterDataDtos {
    private GhnMasterDataDtos() {}
    
    @JsonIgnoreProperties (ignoreUnknown = true)
    public record ProvinceDto(
        @JsonProperty("ProvinceID") Integer provinceId,
        @JsonProperty("ProvinceName") String provinceName,
        @JsonProperty("Code") String code
    ) {}

    @JsonIgnoreProperties (ignoreUnknown = true)
    public record DistrictDto(
        @JsonProperty("DistrictID") Integer districtId,
        @JsonProperty("ProvinceID") Integer provinceId,
        @JsonProperty("DistrictName") String districtName,
        @JsonProperty("Code") String code
    ) {}

    @JsonIgnoreProperties (ignoreUnknown = true)
    public record WardDto(
        @JsonProperty("WardCode") String wardCode,
        @JsonProperty("DistrictID") Integer districtId,
        @JsonProperty("WardName") String wardName
    ) {}
}
