package com.oldbutgold.shop.modules.commerce.application;

import java.time.Instant;
import java.util.Optional;

public interface FulfillmentCommerceFacade {

    record OrderFulfillmentSnapshot(
            long orderId,
            long buyerId,
            long sellerId,
            String status,
            Instant completedAt,
            Instant cancelledAt,
            Long sourceAddressId,
            // Thông tin địa chỉ lấy hàng của người bán (Seller Pickup Address từ bảng addresses)
            String fromName,
            String fromPhone,
            String fromAddress,
            String fromProvinceName,
            String fromDistrictName,
            String fromWardName,
            Integer fromDistrictId,
            String fromWardCode,
            // Thông tin địa chỉ nhận hàng của người mua (Buyer Shipping Address từ OrderEntity)
            String toName,
            String toPhone,
            String toProvinceName,
            String toDistrictName,
            String toWardName,
            String toAddress,
            Integer toDistrictId,
            String toWardCode
    ) {
        public OrderFulfillmentSnapshot(long orderId, long buyerId, long sellerId, String status, Instant completedAt, Instant cancelledAt) {
            this(orderId, buyerId, sellerId, status, completedAt, cancelledAt, null,
                    null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null);
        }

        public OrderFulfillmentSnapshot(long orderId, long buyerId, long sellerId, String status, Instant completedAt, Instant cancelledAt,
                                        String recipientName, String phoneNumber, String province, String district, String ward, String detailAddress) {
            this(orderId, buyerId, sellerId, status, completedAt, cancelledAt, null,
                    null, null, null, null, null, null, null, null,
                    recipientName, phoneNumber, province, district, ward, detailAddress, null, null);
        }

        // Getter tương thích ngược cho các nơi truy cập recipientName/phoneNumber/province/district/ward/detailAddress
        public String recipientName() { return toName; }
        public String phoneNumber() { return toPhone; }
        public String province() { return toProvinceName; }
        public String district() { return toDistrictName; }
        public String ward() { return toWardName; }
        public String detailAddress() { return toAddress; }

        public boolean canShip() {
            return "SELLER_CONFIRMED".equals(status);
        }

        public boolean isTerminalConflict() {
            return "CANCELLED".equals(status) || "REFUNDED".equals(status);
        }

        public boolean isAlreadyDeliveredOrCompleted() {
            return "DELIVERED".equals(status) || "COMPLETED".equals(status);
        }

        public boolean hasValidGhnShippingCodes() {
            return sourceAddressId != null
                    && fromDistrictId != null
                    && toDistrictId != null
                    && toWardCode != null
                    && !toWardCode.isBlank();
        }
    }

    /**
     * Đọc Order snapshot không khóa dùng cho các truy vấn tra cứu (tracking, info).
     */
    Optional<OrderFulfillmentSnapshot> getOrderSnapshot(long orderId);

    /**
     * Khóa Order theo thứ tự bất biến Order -> Shipment.
     * Trả về snapshot an toàn, không làm lộ OrderEntity qua ranh giới module.
     */
    Optional<OrderFulfillmentSnapshot> lockOrderForFulfillment(long orderId);

    /**
     * Chuyển Order sang SHIPPED (yêu cầu SELLER_CONFIRMED, idempotent nếu đã SHIPPED/DELIVERED/COMPLETED).
     */
    void markShipped(long orderId, Instant shippedAt);

    /**
     * Chuyển Order sang DELIVERED từ carrier proof (chấp nhận SELLER_CONFIRMED hoặc SHIPPED).
     */
    void markDeliveredDirectFromCarrier(long orderId, Instant deliveredAt);

    /**
     * Hoàn thành đơn hàng sau khi giao thành công và giải ngân.
     */
    void markCompleted(long orderId, Instant completedAt);
}
