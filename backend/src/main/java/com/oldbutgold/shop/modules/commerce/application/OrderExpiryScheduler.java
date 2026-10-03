package com.oldbutgold.shop.modules.commerce.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.scheduling.order-expiry.enabled", havingValue = "true", matchIfMissing = true)
public class OrderExpiryScheduler {
    private static final Logger log = LoggerFactory.getLogger(OrderExpiryScheduler.class);

    private final OrderManagementService orderManagementService;

    public OrderExpiryScheduler(OrderManagementService orderManagementService) {
        this.orderManagementService = orderManagementService;
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void cleanupOverdueOrders() {
        try {
            int expiredCount = orderManagementService.expireOverdueOrders();
            if (expiredCount > 0) {
                log.info("OrderExpiryScheduler: Đã tự động hủy {} đơn hàng quá hạn 1 giờ chưa thanh toán.", expiredCount);
            }
        } catch (Exception e) {
            log.error("OrderExpiryScheduler gặp lỗi khi dọn dẹp đơn quá hạn: {}", e.getMessage(), e);
        }
    }
}
