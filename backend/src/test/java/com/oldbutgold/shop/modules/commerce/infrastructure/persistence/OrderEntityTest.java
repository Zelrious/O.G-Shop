package com.oldbutgold.shop.modules.commerce.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderEntityTest {

    private OrderEntity createSampleOrder() {
        Instant now = Instant.parse("2026-10-18T10:00:00Z");
        return new OrderEntity(
                UUID.randomUUID(), 10L, 20L, null,
                "Nguyễn Văn A", "0901234567",
                "TP.HCM", "Quận 1", "Phường Bến Nghé", "123 Lê Duẩn",
                new BigDecimal("500000"), new BigDecimal("20900"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500000"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                "PLATFORM", new BigDecimal("520900"), now.plusSeconds(3600), now
        );
    }

    @Test
    @DisplayName("Luồng chuyển trạng thái hợp lệ chuẩn: SELLER_CONFIRMED -> SHIPPED -> DELIVERED -> COMPLETED")
    void testStandardLifecycleTransitions() {
        OrderEntity order = createSampleOrder();
        Instant tPay = Instant.parse("2026-10-18T10:05:00Z");
        Instant tConfirm = Instant.parse("2026-10-18T10:30:00Z");
        Instant tShipped = Instant.parse("2026-10-18T14:00:00Z");
        Instant tDelivered = Instant.parse("2026-10-20T09:00:00Z");
        Instant tCompleted = Instant.parse("2026-10-23T09:00:00Z");

        order.markPaidHeld(tPay);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_PAID_HELD);

        order.confirmBySeller(tConfirm);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_SELLER_CONFIRMED);

        order.markShipped(tShipped);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_SHIPPED);
        assertThat(order.getUpdatedAt()).isEqualTo(tShipped);

        order.markDelivered(tDelivered);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_DELIVERED);
        assertThat(order.getUpdatedAt()).isEqualTo(tDelivered);
        assertThat(order.getCompletedAt()).isNull();

        order.markCompleted(tCompleted);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_COMPLETED);
        assertThat(order.getCompletedAt()).isEqualTo(tCompleted);
        assertThat(order.getUpdatedAt()).isEqualTo(tCompleted);
    }

    @Test
    @DisplayName("markShipped: từ chối khi không phải SELLER_CONFIRMED; idempotent khi đã SHIPPED/DELIVERED/COMPLETED")
    void testMarkShippedRules() {
        OrderEntity order = createSampleOrder();
        Instant now = Instant.parse("2026-10-18T12:00:00Z");

        // PAYMENT_PENDING -> ném lỗi
        assertThatThrownBy(() -> order.markShipped(now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SELLER_CONFIRMED");

        // Chuyển sang SELLER_CONFIRMED
        order.markPaidHeld(now);
        order.confirmBySeller(now);

        // Chuyển thành công sang SHIPPED
        order.markShipped(now);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_SHIPPED);

        // Idempotent khi đã SHIPPED
        Instant later = now.plusSeconds(300);
        order.markShipped(later);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_SHIPPED);
        assertThat(order.getUpdatedAt()).isEqualTo(now);

        // Idempotent khi đã DELIVERED
        order.markDelivered(later);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_DELIVERED);
        order.markShipped(later.plusSeconds(300));
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_DELIVERED);

        // Idempotent khi đã COMPLETED
        order.markCompleted(later.plusSeconds(600));
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_COMPLETED);
        order.markShipped(later.plusSeconds(900));
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_COMPLETED);
    }

    @Test
    @DisplayName("markDelivered chuẩn: bắt buộc SHIPPED; idempotent khi DELIVERED hoặc COMPLETED")
    void testMarkDeliveredStandardRules() {
        OrderEntity order = createSampleOrder();
        Instant now = Instant.parse("2026-10-18T12:00:00Z");

        order.markPaidHeld(now);
        order.confirmBySeller(now);

        // Từ SELLER_CONFIRMED gọi markDelivered chuẩn -> ném lỗi vì thiếu bước SHIPPED
        assertThatThrownBy(() -> order.markDelivered(now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SHIPPED");

        order.markShipped(now);
        order.markDelivered(now);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_DELIVERED);

        // Idempotent khi gọi lại markDelivered
        Instant later = now.plusSeconds(500);
        order.markDelivered(later);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_DELIVERED);
        assertThat(order.getUpdatedAt()).isEqualTo(now);

        // Idempotent khi đã COMPLETED
        order.markCompleted(later);
        order.markDelivered(later.plusSeconds(500));
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_COMPLETED);
    }

    @Test
    @DisplayName("markDeliveredDirectFromCarrier: cho phép từ SELLER_CONFIRMED hoặc SHIPPED; từ chối PAYMENT_PENDING/CANCELLED")
    void testMarkDeliveredDirectFromCarrier() {
        OrderEntity order = createSampleOrder();
        Instant now = Instant.parse("2026-10-18T12:00:00Z");

        // PAYMENT_PENDING -> ném lỗi
        assertThatThrownBy(() -> order.markDeliveredDirectFromCarrier(now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SELLER_CONFIRMED hoặc SHIPPED");

        // Cho phép bước nhảy từ SELLER_CONFIRMED -> DELIVERED
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_SELLER_CONFIRMED);

        order.markDeliveredDirectFromCarrier(now);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_DELIVERED);
        assertThat(order.getUpdatedAt()).isEqualTo(now);

        // Idempotent khi gọi lại
        order.markDeliveredDirectFromCarrier(now.plusSeconds(300));
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_DELIVERED);
        assertThat(order.getUpdatedAt()).isEqualTo(now);

        // Cho phép khi đang SHIPPED
        OrderEntity order2 = createSampleOrder();
        order2.markPaidHeld(now);
        order2.confirmBySeller(now);
        order2.markShipped(now);
        order2.markDeliveredDirectFromCarrier(now.plusSeconds(600));
        assertThat(order2.getStatus()).isEqualTo(OrderEntity.STATUS_DELIVERED);
        assertThat(order2.getUpdatedAt()).isEqualTo(now.plusSeconds(600));

        // Từ CANCELLED -> ném lỗi
        OrderEntity cancelledOrder = createSampleOrder();
        cancelledOrder.cancel("Hủy đơn", now);
        assertThatThrownBy(() -> cancelledOrder.markDeliveredDirectFromCarrier(now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SELLER_CONFIRMED hoặc SHIPPED");
    }

    @Test
    @DisplayName("markCompleted: chuyển từ DELIVERED sang COMPLETED, từ chối trạng thái khác, idempotent khi COMPLETED")
    void testMarkCompletedRules() {
        OrderEntity order = createSampleOrder();
        Instant now = Instant.parse("2026-10-18T12:00:00Z");

        // Khi đang PAYMENT_PENDING -> ném lỗi
        assertThatThrownBy(() -> order.markCompleted(now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DELIVERED");

        order.markPaidHeld(now);
        order.confirmBySeller(now);

        // Khi đang SELLER_CONFIRMED -> ném lỗi
        assertThatThrownBy(() -> order.markCompleted(now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DELIVERED");

        order.markShipped(now);
        // Khi đang SHIPPED -> ném lỗi
        assertThatThrownBy(() -> order.markCompleted(now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DELIVERED");

        order.markDelivered(now);
        assertThat(order.getCompletedAt()).isNull();

        // Chuyển thành công sang COMPLETED
        Instant completeTime = now.plusSeconds(86400 * 3);
        order.markCompleted(completeTime);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_COMPLETED);
        assertThat(order.getCompletedAt()).isEqualTo(completeTime);
        assertThat(order.getUpdatedAt()).isEqualTo(completeTime);

        // Idempotent khi gọi lại markCompleted: không ghi đè completedAt ban đầu
        order.markCompleted(completeTime.plusSeconds(3600));
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_COMPLETED);
        assertThat(order.getCompletedAt()).isEqualTo(completeTime);
    }
}
