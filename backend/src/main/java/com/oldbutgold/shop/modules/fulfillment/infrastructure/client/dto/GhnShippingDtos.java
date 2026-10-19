package com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class GhnShippingDtos {
    private GhnShippingDtos() {}

    // ========================================================================
    // 1. Available Services
    // ========================================================================

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AvailableServiceQueryRequest(
            @JsonProperty("shop_id") Integer shopId,
            @JsonProperty("from_district") Integer fromDistrict,
            @JsonProperty("to_district") Integer toDistrict
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AvailableServiceDto(
            @JsonProperty("service_id") Integer serviceId,
            @JsonProperty("short_name") String shortName,
            @JsonProperty("service_type_id") Integer serviceTypeId
    ) {}

    // ========================================================================
    // 2. Shipping Fee
    // ========================================================================

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FeeQueryRequest(
            @JsonProperty("from_district_id") Integer fromDistrictId,
            @JsonProperty("from_ward_code") String fromWardCode,
            @JsonProperty("to_district_id") Integer toDistrictId,
            @JsonProperty("to_ward_code") String toWardCode,
            @JsonProperty("service_id") Integer serviceId,
            @JsonProperty("service_type_id") Integer serviceTypeId,
            @JsonProperty("height") Integer height,
            @JsonProperty("length") Integer length,
            @JsonProperty("width") Integer width,
            @JsonProperty("weight") Integer weight,
            @JsonProperty("insurance_value") Integer insuranceValue
    ) {
        public static final int DEFAULT_SERVICE_TYPE_ID_STANDARD = 2;

        public FeeQueryRequest(Integer fromDistrictId, String fromWardCode, Integer toDistrictId, String toWardCode, Integer weight) {
            this(fromDistrictId, fromWardCode, toDistrictId, toWardCode, null, DEFAULT_SERVICE_TYPE_ID_STANDARD, 10, 15, 10, weight, 0);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FeeResponseDto(
            @JsonProperty("total") Integer total,
            @JsonProperty("service_fee") Integer serviceFee,
            @JsonProperty("insurance_fee") Integer insuranceFee
    ) {}

    // ========================================================================
    // 3. Create Shipping Order
    // ========================================================================

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OrderItemDto(
            @JsonProperty("name") String name,
            @JsonProperty("code") String code,
            @JsonProperty("quantity") Integer quantity,
            @JsonProperty("price") Integer price,
            @JsonProperty("length") Integer length,
            @JsonProperty("width") Integer width,
            @JsonProperty("height") Integer height,
            @JsonProperty("weight") Integer weight
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateShippingOrderRequest(
            @JsonProperty("payment_type_id") Integer paymentTypeId,
            @JsonProperty("note") String note,
            @JsonProperty("required_note") String requiredNote,
            @JsonProperty("client_order_code") String clientOrderCode,
            @JsonProperty("from_name") String fromName,
            @JsonProperty("from_phone") String fromPhone,
            @JsonProperty("from_address") String fromAddress,
            @JsonProperty("from_ward_name") String fromWardName,
            @JsonProperty("from_district_name") String fromDistrictName,
            @JsonProperty("from_province_name") String fromProvinceName,
            @JsonProperty("return_phone") String returnPhone,
            @JsonProperty("return_address") String returnAddress,
            @JsonProperty("to_name") String toName,
            @JsonProperty("to_phone") String toPhone,
            @JsonProperty("to_address") String toAddress,
            @JsonProperty("to_ward_code") String toWardCode,
            @JsonProperty("to_district_id") Integer toDistrictId,
            @JsonProperty("weight") Integer weight,
            @JsonProperty("length") Integer length,
            @JsonProperty("width") Integer width,
            @JsonProperty("height") Integer height,
            @JsonProperty("service_type_id") Integer serviceTypeId,
            @JsonProperty("service_id") Integer serviceId,
            @JsonProperty("cod_amount") Integer codAmount,
            @JsonProperty("items") List<OrderItemDto> items
    ) {
        public static final String REQUIRED_NOTE_CHO_THU_HANG = "CHOTHUHANG";
        public static final int DEFAULT_PAYMENT_TYPE_SHOP_PAYS = 1;
        public static final int DEFAULT_COD_AMOUNT_ZERO = 0;
        public static final int DEFAULT_SERVICE_TYPE_ID_STANDARD = 2;
        public static final int MAX_CLIENT_ORDER_CODE_LENGTH = 50;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateOrderResponseDto(
            @JsonProperty("order_code") String orderCode,
            @JsonProperty("district_encode") String districtEncode,
            @JsonProperty("expected_delivery_time") String expectedDeliveryTime,
            @JsonProperty("total_fee") Integer totalFee,
            @JsonProperty("trans_type") String transType
    ) {}

    // ========================================================================
    // 4. Order Detail & Tracking
    // ========================================================================

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OrderDetailQueryRequest(
            @JsonProperty("order_code") String orderCode
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderDetailResponseDto(
            @JsonProperty("order_code") String orderCode,
            @JsonProperty("status") String status,
            @JsonProperty("required_note") String requiredNote,
            @JsonProperty("from_name") String fromName,
            @JsonProperty("from_phone") String fromPhone,
            @JsonProperty("from_address") String fromAddress,
            @JsonProperty("from_ward_code") String fromWardCode,
            @JsonProperty("from_district_id") Integer fromDistrictId,
            @JsonProperty("to_name") String toName,
            @JsonProperty("to_phone") String toPhone,
            @JsonProperty("to_address") String toAddress,
            @JsonProperty("to_ward_code") String toWardCode,
            @JsonProperty("to_district_id") Integer toDistrictId
    ) {}

    // ========================================================================
    // 5. Waybill A5 Print Token
    // ========================================================================

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PrintTokenQueryRequest(
            @JsonProperty("order_codes") List<String> orderCodes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PrintTokenResponseDto(
            @JsonProperty("token") String token
    ) {}
}
