package com.oldbutgold.shop.modules.fulfillment.application;

import java.math.BigDecimal;

public interface FulfillmentShippingFacade {
    /**
     * Tính phí giao hàng GHN thực tế dựa trên mã quận/huyện và phường/xã.
     */
    BigDecimal calculateShippingFee(int fromDistrictId, String fromWardCode, int toDistrictId, String toWardCode, Integer weightInGrams);
}
