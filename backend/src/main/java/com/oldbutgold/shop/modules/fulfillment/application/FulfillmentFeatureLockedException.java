package com.oldbutgold.shop.modules.fulfillment.application;

/**
 * Ném ra khi tính năng tạo vận đơn bị khóa ở tầng server do chưa có nguồn dữ liệu
 * địa chỉ lấy hàng của người bán và thông số kiện hàng đã xác minh (chờ ADDR-01/ADDR-02/ADDR-03).
 */
public class FulfillmentFeatureLockedException extends RuntimeException {
    public FulfillmentFeatureLockedException(String message) {
        super(message);
    }
}
