package com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShipmentEntityTest {

    @Test
    @DisplayName("Khởi tạo shipment ở trạng thái PENDING với carrier = GHN")
    void testInitialPendingShipment() {
        Instant now = Instant.now();
        ShipmentEntity shipment = new ShipmentEntity(101L, "OG-DEV-101-ABC123", now);

        assertThat(shipment.getOrderId()).isEqualTo(101L);
        assertThat(shipment.getClientOrderCode()).isEqualTo("OG-DEV-101-ABC123");
        assertThat(shipment.getCarrier()).isEqualTo("GHN");
        assertThat(shipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_PENDING);
        assertThat(shipment.getTrackingNumber()).isNull();
        assertThat(shipment.getShippedAt()).isNull();
        assertThat(shipment.getDeliveredAt()).isNull();
        assertThat(shipment.getCreatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Cập nhật khi GHN tạo đơn thành công: giữ status PENDING, gán order_code và ready_to_pick")
    void testRecordGhnCreated() {
        Instant now = Instant.now();
        Instant delivery = now.plusSeconds(86400 * 2);
        ShipmentEntity shipment = new ShipmentEntity(101L, "OG-DEV-101-ABC123", now);

        shipment.recordGhnCreated("L8RL44", new BigDecimal("20900.00"), delivery, now);

        assertThat(shipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_PENDING);
        assertThat(shipment.getTrackingNumber()).isEqualTo("L8RL44");
        assertThat(shipment.getShippingFee()).isEqualByComparingTo("20900.00");
        assertThat(shipment.getExpectedDeliveryAt()).isEqualTo(delivery);
        assertThat(shipment.getRawGhnStatus()).isEqualTo("ready_to_pick");
        assertThat(shipment.getShippedAt()).isNull();
    }

    @Test
    @DisplayName("Chuyển trạng thái sang PICKED_UP, IN_TRANSIT, DELIVERED tuân thủ quy tắc shipped_at")
    void testStateTransitionsAndShippedAtRules() {
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");
        Instant tPicked = Instant.parse("2026-10-18T14:30:00Z");
        Instant tDelivered = Instant.parse("2026-10-20T09:15:00Z");

        ShipmentEntity shipment = new ShipmentEntity(101L, "OG-DEV-101-ABC123", t0);

        // Bưu tá lấy hàng thành công
        shipment.markPickedUp(tPicked, "picked", tPicked);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_PICKED_UP);
        assertThat(shipment.getShippedAt()).isEqualTo(tPicked);
        assertThat(shipment.getDeliveredAt()).isNull();

        // Đang luân chuyển
        shipment.markInTransit(null, "transporting", tPicked.plusSeconds(3600));
        assertThat(shipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_IN_TRANSIT);
        assertThat(shipment.getShippedAt()).isEqualTo(tPicked);

        // Giao hàng thành công
        shipment.markDelivered(tDelivered, null, tDelivered);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_DELIVERED);
        assertThat(shipment.getDeliveredAt()).isEqualTo(tDelivered);
        assertThat(shipment.getShippedAt()).isEqualTo(tPicked);
    }

    @Test
    @DisplayName("markInTransit lần đầu từ PENDING: gán shippedAt xấp xỉ, deliveredAt = null, thỏa ck_shipments_times")
    void testMarkInTransitFirstTimeFromPending() {
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");
        Instant tInTransit = Instant.parse("2026-10-18T15:00:00Z");
        ShipmentEntity shipment = new ShipmentEntity(102L, "OG-DEV-102-XYZ789", t0);

        assertThat(shipment.getShippedAt()).isNull();
        assertThat(shipment.satisfiesTimesConstraint()).isTrue();

        shipment.markInTransit(tInTransit, "delivering", tInTransit);

        assertThat(shipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_IN_TRANSIT);
        assertThat(shipment.getShippedAt()).isEqualTo(tInTransit);
        assertThat(shipment.getDeliveredAt()).isNull();
        assertThat(shipment.satisfiesTimesConstraint()).isTrue();
    }

    @Test
    @DisplayName("markReturned khi shippedAt ban đầu là NULL: gán shippedAt xấp xỉ, deliveredAt = null, thỏa ck_shipments_times")
    void testMarkReturnedWhenShippedAtNull() {
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");
        Instant tReturn = Instant.parse("2026-10-19T08:00:00Z");
        ShipmentEntity shipment = new ShipmentEntity(103L, "OG-DEV-103-RET123", t0);

        assertThat(shipment.getShippedAt()).isNull();

        shipment.markReturned(tReturn, tReturn);

        assertThat(shipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_RETURNED);
        assertThat(shipment.getShippedAt()).isNotNull();
        assertThat(shipment.getShippedAt()).isEqualTo(tReturn);
        assertThat(shipment.getDeliveredAt()).isNull();
        assertThat(shipment.satisfiesTimesConstraint()).isTrue();
    }

    @Test
    @DisplayName("Từ DELIVERED không chuyển sang FAILED: markFailed trả về false, status giữ DELIVERED và deliveredAt giữ nguyên")
    void testMarkFailedWhenDeliveredAtHadValue() {
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");
        Instant tShipped = Instant.parse("2026-10-18T14:00:00Z");
        Instant tDelivered = Instant.parse("2026-10-19T10:00:00Z");
        Instant tFail = Instant.parse("2026-10-20T12:00:00Z");

        ShipmentEntity shipment = new ShipmentEntity(104L, "OG-DEV-104-FAI456", t0);
        shipment.markPickedUp(tShipped, "picked", tShipped);
        boolean deliveredOk = shipment.markDelivered(tDelivered, null, tDelivered);

        assertThat(deliveredOk).isTrue();
        assertThat(shipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_DELIVERED);
        assertThat(shipment.getDeliveredAt()).isEqualTo(tDelivered);
        assertThat(shipment.isTerminal()).isTrue();

        // Cố gắng chuyển sang FAILED từ DELIVERED -> phải bị bỏ qua (trả về false)
        boolean failResult = shipment.markFailed("exception", tFail);

        assertThat(failResult).isFalse();
        assertThat(shipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_DELIVERED);
        assertThat(shipment.getDeliveredAt()).isEqualTo(tDelivered);
        assertThat(shipment.satisfiesTimesConstraint()).isTrue();
    }

    @Test
    @DisplayName("Shipment ở trạng thái DELIVERED: mọi hàm mark* và recordGhnCreated đều trả về false và không đổi trạng thái")
    void testTerminalStateDeliveredRejectsFurtherTransitions() {
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");
        Instant tShipped = Instant.parse("2026-10-18T14:00:00Z");
        Instant tDelivered = Instant.parse("2026-10-19T10:00:00Z");
        Instant tAfter = Instant.parse("2026-10-20T10:00:00Z");

        ShipmentEntity shipment = new ShipmentEntity(201L, "OG-DEV-201-DEL001", t0);
        shipment.markPickedUp(tShipped, "picked", tShipped);
        shipment.markDelivered(tDelivered, null, tDelivered);

        assertThat(shipment.isTerminal()).isTrue();
        assertThat(shipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_DELIVERED);

        assertThat(shipment.recordGhnCreated("NEW_CODE", BigDecimal.TEN, tAfter, tAfter)).isFalse();
        assertThat(shipment.markPickedUp(tAfter, "picked", tAfter)).isFalse();
        assertThat(shipment.markInTransit(tAfter, "delivering", tAfter)).isFalse();
        assertThat(shipment.markDelivered(tAfter, null, tAfter)).isFalse();
        assertThat(shipment.markReturned(tAfter, tAfter)).isFalse();
        assertThat(shipment.markFailed("cancel", tAfter)).isFalse();

        assertThat(shipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_DELIVERED);
        assertThat(shipment.getDeliveredAt()).isEqualTo(tDelivered);
        assertThat(shipment.getShippedAt()).isEqualTo(tShipped);
    }

    @Test
    @DisplayName("Shipment ở trạng thái RETURNED: mọi hàm mark* và recordGhnCreated đều trả về false và không đổi trạng thái")
    void testTerminalStateReturnedRejectsFurtherTransitions() {
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");
        Instant tReturn = Instant.parse("2026-10-19T10:00:00Z");
        Instant tAfter = Instant.parse("2026-10-20T10:00:00Z");

        ShipmentEntity shipment = new ShipmentEntity(202L, "OG-DEV-202-RET001", t0);
        shipment.markReturned(tReturn, tReturn);

        assertThat(shipment.isTerminal()).isTrue();
        assertThat(shipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_RETURNED);

        assertThat(shipment.recordGhnCreated("NEW_CODE", BigDecimal.TEN, tAfter, tAfter)).isFalse();
        assertThat(shipment.markPickedUp(tAfter, "picked", tAfter)).isFalse();
        assertThat(shipment.markInTransit(tAfter, "delivering", tAfter)).isFalse();
        assertThat(shipment.markDelivered(tAfter, null, tAfter)).isFalse();
        assertThat(shipment.markReturned(tAfter, tAfter)).isFalse();
        assertThat(shipment.markFailed("cancel", tAfter)).isFalse();

        assertThat(shipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_RETURNED);
        assertThat(shipment.getDeliveredAt()).isNull();
        assertThat(shipment.getShippedAt()).isEqualTo(tReturn);
    }

    @Test
    @DisplayName("Shipment ở trạng thái FAILED: mọi hàm mark* và recordGhnCreated đều trả về false và không đổi trạng thái")
    void testTerminalStateFailedRejectsFurtherTransitions() {
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");
        Instant tFail = Instant.parse("2026-10-19T10:00:00Z");
        Instant tAfter = Instant.parse("2026-10-20T10:00:00Z");

        ShipmentEntity shipment = new ShipmentEntity(203L, "OG-DEV-203-FAI001", t0);
        boolean failOk = shipment.markFailed("cancel", tFail);
        assertThat(failOk).isTrue();

        assertThat(shipment.isTerminal()).isTrue();
        assertThat(shipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_FAILED);

        assertThat(shipment.recordGhnCreated("NEW_CODE", BigDecimal.TEN, tAfter, tAfter)).isFalse();
        assertThat(shipment.markPickedUp(tAfter, "picked", tAfter)).isFalse();
        assertThat(shipment.markInTransit(tAfter, "delivering", tAfter)).isFalse();
        assertThat(shipment.markDelivered(tAfter, null, tAfter)).isFalse();
        assertThat(shipment.markReturned(tAfter, tAfter)).isFalse();
        assertThat(shipment.markFailed("damage", tAfter)).isFalse();

        assertThat(shipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_FAILED);
        assertThat(shipment.getDeliveredAt()).isNull();
    }

    @Test
    @DisplayName("markDelivered bảo đảm deliveredAt >= shippedAt khi deliveryTime sớm hơn shippedAt")
    void testMarkDeliveredEnsureDeliveredAtAfterShippedAt() {
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");
        Instant tShipped = Instant.parse("2026-10-18T16:00:00Z");
        // Giả sử timestamp GHN gửi deliveryTime bị lệch trước shippedTime
        Instant tEarlyDelivery = Instant.parse("2026-10-18T15:00:00Z");

        ShipmentEntity shipment = new ShipmentEntity(105L, "OG-DEV-105-ADJ789", t0);
        shipment.markPickedUp(tShipped, "picked", tShipped);

        shipment.markDelivered(tEarlyDelivery, null, tShipped.plusSeconds(3600));

        assertThat(shipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_DELIVERED);
        assertThat(shipment.getShippedAt()).isEqualTo(tShipped);
        assertThat(shipment.getDeliveredAt()).isNotNull();
        assertThat(shipment.getDeliveredAt()).isAfterOrEqualTo(shipment.getShippedAt());
        assertThat(shipment.satisfiesTimesConstraint()).isTrue();
    }

    @Test
    @DisplayName("Kiểm tra chuyển trạng thái OrderEntity: markShipped và markDelivered")
    void testOrderEntityTransitions() {
        Instant now = Instant.now();
        OrderEntity order = new OrderEntity(
                UUID.randomUUID(), 10L, 20L, null,
                "Nguyễn Văn A", "0901234567",
                "TP.HCM", "Quận 1", "Phường Bến Nghé", "123 Lê Duẩn",
                new BigDecimal("500000"), new BigDecimal("20900"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500000"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                "PLATFORM", new BigDecimal("520900"), now.plusSeconds(3600), now
        );

        // Đơn mới tạo ở trạng thái PAYMENT_PENDING -> không thể chuyển thẳng sang SHIPPED
        assertThatThrownBy(() -> order.markShipped(now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SELLER_CONFIRMED");

        // Giả lập luồng: thanh toán -> xác nhận người bán -> vận chuyển -> giao thành công
        order.markPaidHeld(now);
        order.confirmBySeller(now);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_SELLER_CONFIRMED);

        order.markShipped(now);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_SHIPPED);

        // Idempotent khi gọi lại markShipped
        order.markShipped(now);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_SHIPPED);

        order.markDelivered(now);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_DELIVERED);

        // Idempotent khi gọi lại markDelivered
        order.markDelivered(now);
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_DELIVERED);
    }

    @Test
    @DisplayName("Shipment đang IN_TRANSIT: nhận sự kiện picked_up đến trễ thì không bị lùi về PICKED_UP")
    void testMonotonicityGuardWhenInTransitReceivesLatePickedUp() {
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");
        Instant tShipped = Instant.parse("2026-10-18T12:00:00Z");
        Instant tInTransit = Instant.parse("2026-10-18T14:00:00Z");
        Instant tLatePicked = Instant.parse("2026-10-18T11:00:00Z");

        ShipmentEntity shipment = new ShipmentEntity(301L, "OG-DEV-301-MONO", t0);
        shipment.markInTransit(tShipped, "transporting", tInTransit);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_IN_TRANSIT);
        assertThat(shipment.getShippedAt()).isEqualTo(tShipped);

        // Webhook picked_up gửi đến muộn
        boolean result = shipment.markPickedUp(tLatePicked, "picked", Instant.now());

        assertThat(result).isTrue();
        // Không bị lùi trạng thái về PICKED_UP, vẫn giữ IN_TRANSIT
        assertThat(shipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_IN_TRANSIT);
        assertThat(shipment.getShippedAt()).isEqualTo(tShipped);
        assertThat(shipment.getDeliveredAt()).isNull();
        assertThat(shipment.getRawGhnStatus()).isEqualTo("picked");
        assertThat(shipment.satisfiesTimesConstraint()).isTrue();
    }

    @Test
    @DisplayName("hasTerminalConflictWithDelivered nhận diện đúng RETURNED và FAILED là xung đột, các trạng thái khác không xung đột")
    void testHasTerminalConflictWithDelivered() {
        Instant now = Instant.now();
        ShipmentEntity shipment = new ShipmentEntity(302L, "OG-DEV-302-CONFLICT", now);

        // PENDING -> không xung đột
        assertThat(shipment.hasTerminalConflictWithDelivered()).isFalse();

        // PICKED_UP -> không xung đột
        shipment.markPickedUp(now, "picked", now);
        assertThat(shipment.hasTerminalConflictWithDelivered()).isFalse();

        // IN_TRANSIT -> không xung đột
        shipment.markInTransit(now, "transporting", now);
        assertThat(shipment.hasTerminalConflictWithDelivered()).isFalse();

        // RETURNED -> Xung đột!
        ShipmentEntity returnedShipment = new ShipmentEntity(303L, "OG-DEV-303-RET", now);
        returnedShipment.markReturned(now, now);
        assertThat(returnedShipment.hasTerminalConflictWithDelivered()).isTrue();

        // FAILED -> Xung đột!
        ShipmentEntity failedShipment = new ShipmentEntity(304L, "OG-DEV-304-FAIL", now);
        failedShipment.markFailed("lost_damage", now);
        assertThat(failedShipment.hasTerminalConflictWithDelivered()).isTrue();

        // DELIVERED -> Đã giao thành công, không phải xung đột với delivered
        ShipmentEntity deliveredShipment = new ShipmentEntity(305L, "OG-DEV-305-DEL", now);
        deliveredShipment.markPickedUp(now, "picked", now);
        deliveredShipment.markDelivered(now, null, now);
        assertThat(deliveredShipment.hasTerminalConflictWithDelivered()).isFalse();
    }

    @Test
    @DisplayName("Tách biệt claim lease và reconciliation backoff: releaseClaim không xóa nextReconciliationAt")
    void testReleaseClaimDoesNotClearNextReconciliationAt() {
        Instant now = Instant.now();
        ShipmentEntity shipment = new ShipmentEntity(401L, "OG-DEV-401-CLAIM", now);

        // Giành claim lease 10s và đặt backoff đối soát 60s
        shipment.acquireClaim("token-123", now.plusSeconds(10));
        shipment.setNextReconciliationAt(now.plusSeconds(60));
        shipment.incrementReconciliationAttempts();

        assertThat(shipment.hasActiveClaim(now)).isTrue();
        assertThat(shipment.getNextReconciliationAt()).isEqualTo(now.plusSeconds(60));
        assertThat(shipment.getReconciliationAttempts()).isEqualTo(1);

        // Giải phóng claim lease
        shipment.releaseClaim();

        // Claim token và claimExpiresAt đã được xóa
        assertThat(shipment.hasActiveClaim(now)).isFalse();
        assertThat(shipment.getClaimToken()).isNull();
        assertThat(shipment.getClaimExpiresAt()).isNull();

        // NHƯNG nextReconciliationAt và reconciliationAttempts vẫn được giữ nguyên vẹn
        assertThat(shipment.getNextReconciliationAt()).isEqualTo(now.plusSeconds(60));
        assertThat(shipment.getReconciliationAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("isReconciliationDue kiểm tra chính xác thời điểm đối soát đến hạn")
    void testIsReconciliationDue() {
        Instant now = Instant.now();
        ShipmentEntity shipment = new ShipmentEntity(402L, "OG-DEV-402-DUE", now);

        // Mặc định nextReconciliationAt = null -> đến hạn
        assertThat(shipment.isReconciliationDue(now)).isTrue();

        // Đặt nextReconciliationAt trong tương lai (+30s) -> chưa đến hạn
        shipment.setNextReconciliationAt(now.plusSeconds(30));
        assertThat(shipment.isReconciliationDue(now)).isFalse();

        // Khi thời gian trôi qua (+35s) -> đã đến hạn
        assertThat(shipment.isReconciliationDue(now.plusSeconds(35))).isTrue();
    }

    @Test
    @DisplayName("recordGhnCreated dọn sạch cả claim lease lẫn reconciliation state")
    void testRecordGhnCreatedResetsReconciliationState() {
        Instant now = Instant.now();
        ShipmentEntity shipment = new ShipmentEntity(403L, "OG-DEV-403-RESET", now);

        shipment.acquireClaim("token-xyz", now.plusSeconds(20));
        shipment.setNextReconciliationAt(now.plusSeconds(120));
        shipment.setReconciliationAttempts(3);

        shipment.recordGhnCreated("GHN-TRACK-403", BigDecimal.valueOf(20000), now.plusSeconds(86400), now);

        assertThat(shipment.getClaimToken()).isNull();
        assertThat(shipment.getClaimExpiresAt()).isNull();
        assertThat(shipment.getNextReconciliationAt()).isNull();
        assertThat(shipment.getReconciliationAttempts()).isEqualTo(0);
        assertThat(shipment.getTrackingNumber()).isEqualTo("GHN-TRACK-403");
    }
}
