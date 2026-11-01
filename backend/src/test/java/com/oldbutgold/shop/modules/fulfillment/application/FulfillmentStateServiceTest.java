package com.oldbutgold.shop.modules.fulfillment.application;

import com.oldbutgold.shop.modules.commerce.application.FulfillmentCommerceFacade;
import com.oldbutgold.shop.modules.commerce.application.FulfillmentCommerceFacade.OrderFulfillmentSnapshot;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentEntity;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FulfillmentStateServiceTest {

    private FulfillmentCommerceFacade commerceFacade;
    private ShipmentRepository shipmentRepository;
    private Clock clock;
    private FulfillmentStateService service;

    private final Instant now = Instant.parse("2026-10-18T10:00:00Z");

    @BeforeEach
    void setUp() {
        commerceFacade = mock(FulfillmentCommerceFacade.class);
        shipmentRepository = mock(ShipmentRepository.class);
        clock = Clock.fixed(now, ZoneOffset.UTC);
        service = new FulfillmentStateService(commerceFacade, shipmentRepository, clock);
    }

    @Test
    @DisplayName("applyCarrierDelivered: Khóa theo đúng thứ tự Order -> Shipment và cập nhật cả hai")
    void testApplyCarrierDeliveredHappyPath() {
        Long orderId = 100L;
        String trackingNumber = "GHN_TRACK_123";

        ShipmentEntity preliminary = new ShipmentEntity(orderId, "OG-CLIENT-123", now);
        preliminary.setTrackingNumber(trackingNumber);
        preliminary.markPickedUp(now, "picked", now);

        ShipmentEntity lockedShipment = spy(new ShipmentEntity(orderId, "OG-CLIENT-123", now));
        lockedShipment.setTrackingNumber(trackingNumber);
        lockedShipment.markPickedUp(now, "picked", now);

        OrderFulfillmentSnapshot snapshot = new OrderFulfillmentSnapshot(
                orderId, 1L, 2L, "SHIPPED", null, null
        );

        when(shipmentRepository.findOrderIdByTrackingNumber(trackingNumber)).thenReturn(Optional.of(orderId));
        when(commerceFacade.lockOrderForFulfillment(orderId)).thenReturn(Optional.of(snapshot));
        when(shipmentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(lockedShipment));

        service.applyCarrierDelivered(trackingNumber, now.plusSeconds(3600), "delivered");

        // Kiểm tra thứ tự khóa: Order PHẢI được khóa TRƯỚC Shipment
        InOrder inOrder = inOrder(commerceFacade, shipmentRepository);
        inOrder.verify(commerceFacade).lockOrderForFulfillment(orderId);
        inOrder.verify(shipmentRepository).findByOrderIdForUpdate(orderId);
        inOrder.verify(shipmentRepository).save(lockedShipment);
        inOrder.verify(commerceFacade).markDeliveredDirectFromCarrier(eq(orderId), eq(now.plusSeconds(3600)));

        assertThat(lockedShipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_DELIVERED);
    }

    @Test
    @DisplayName("applyCarrierDelivered: Tự động catch-up từ SELLER_CONFIRMED sang DELIVERED khi nhận webhook delivered")
    void testApplyCarrierDeliveredCatchesUpFromSellerConfirmed() {
        Long orderId = 105L;
        String trackingNumber = "GHN_TRACK_CATCHUP";

        ShipmentEntity lockedShipment = spy(new ShipmentEntity(orderId, "OG-CLIENT-CATCHUP", now));
        lockedShipment.setTrackingNumber(trackingNumber);

        // Order đang ở SELLER_CONFIRMED (chưa nhận webhook picked)
        OrderFulfillmentSnapshot snapshot = new OrderFulfillmentSnapshot(
                orderId, 1L, 2L, "SELLER_CONFIRMED", null, null
        );

        when(shipmentRepository.findOrderIdByTrackingNumber(trackingNumber)).thenReturn(Optional.of(orderId));
        when(commerceFacade.lockOrderForFulfillment(orderId)).thenReturn(Optional.of(snapshot));
        when(shipmentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(lockedShipment));

        service.applyCarrierDelivered(trackingNumber, now.plusSeconds(3600), "delivered");

        verify(commerceFacade).markDeliveredDirectFromCarrier(eq(orderId), eq(now.plusSeconds(3600)));
        assertThat(lockedShipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_DELIVERED);
        assertThat(lockedShipment.getShippedAt()).isNotNull();
        assertThat(lockedShipment.getDeliveredAt()).isNotNull();
    }

    @Test
    @DisplayName("applyCarrierDelivered: Bỏ qua khi Shipment có xung đột terminal (RETURNED hoặc FAILED) không ném lỗi làm GHN retry")
    void testApplyCarrierDeliveredRejectsTerminalConflictShipment() {
        Long orderId = 101L;
        String trackingNumber = "GHN_TRACK_RET";

        ShipmentEntity lockedShipment = new ShipmentEntity(orderId, "OG-CLIENT-RET", now);
        lockedShipment.setTrackingNumber(trackingNumber);
        lockedShipment.markReturned(now, now);

        OrderFulfillmentSnapshot snapshot = new OrderFulfillmentSnapshot(
                orderId, 1L, 2L, "SHIPPED", null, null
        );

        when(shipmentRepository.findOrderIdByTrackingNumber(trackingNumber)).thenReturn(Optional.of(orderId));
        when(commerceFacade.lockOrderForFulfillment(orderId)).thenReturn(Optional.of(snapshot));
        when(shipmentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(lockedShipment));

        // Webhook ignores cleanly without exception
        service.applyCarrierDelivered(trackingNumber, now, "delivered");

        verify(commerceFacade, never()).markDeliveredDirectFromCarrier(anyLong(), any());

        // But DEMO simulation throws IllegalStateException
        assertThatThrownBy(() -> service.markOrderDelivered(orderId, now, "DEMO_SIMULATION"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("xung đột");
    }

    @Test
    @DisplayName("applyCarrierDelivered: Bỏ qua khi Order có xung đột terminal (CANCELLED) không ném lỗi làm GHN retry")
    void testApplyCarrierDeliveredRejectsTerminalConflictOrder() {
        Long orderId = 102L;
        String trackingNumber = "GHN_TRACK_CANCEL";

        ShipmentEntity lockedShipment = new ShipmentEntity(orderId, "OG-CLIENT-CANCEL", now);
        lockedShipment.setTrackingNumber(trackingNumber);
        lockedShipment.markPickedUp(now, "picked", now);

        OrderFulfillmentSnapshot snapshot = new OrderFulfillmentSnapshot(
                orderId, 1L, 2L, "CANCELLED", null, now
        );

        when(shipmentRepository.findOrderIdByTrackingNumber(trackingNumber)).thenReturn(Optional.of(orderId));
        when(commerceFacade.lockOrderForFulfillment(orderId)).thenReturn(Optional.of(snapshot));
        when(shipmentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(lockedShipment));

        // Webhook ignores cleanly without exception
        service.applyCarrierDelivered(trackingNumber, now, "delivered");

        verify(commerceFacade, never()).markDeliveredDirectFromCarrier(anyLong(), any());

        // But DEMO simulation throws IllegalStateException
        assertThatThrownBy(() -> service.markOrderDelivered(orderId, now, "DEMO_SIMULATION"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("xung đột");
    }

    @Test
    @DisplayName("applyCarrierDelivered: Ném NoSuchElementException khi trackingNumber không tồn tại")
    void testApplyCarrierDeliveredNotFound() {
        when(shipmentRepository.findOrderIdByTrackingNumber("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyCarrierDelivered("UNKNOWN", now, "delivered"))
                .isInstanceOf(NoSuchElementException.class);

        verify(commerceFacade, never()).lockOrderForFulfillment(anyLong());
    }

    @Test
    @DisplayName("applyCarrierPickedUp: Khóa Order -> Shipment và cập nhật SHIPPED")
    void testApplyCarrierPickedUpHappyPath() {
        Long orderId = 200L;
        String trackingNumber = "GHN_TRACK_PICK";

        ShipmentEntity lockedShipment = spy(new ShipmentEntity(orderId, "OG-CLIENT-PICK", now));
        lockedShipment.setTrackingNumber(trackingNumber);

        OrderFulfillmentSnapshot snapshot = new OrderFulfillmentSnapshot(
                orderId, 1L, 2L, "SELLER_CONFIRMED", null, null
        );

        when(shipmentRepository.findOrderIdByTrackingNumber(trackingNumber)).thenReturn(Optional.of(orderId));
        when(commerceFacade.lockOrderForFulfillment(orderId)).thenReturn(Optional.of(snapshot));
        when(shipmentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(lockedShipment));

        service.applyCarrierPickedUp(trackingNumber, now, "picked");

        InOrder inOrder = inOrder(commerceFacade, shipmentRepository);
        inOrder.verify(shipmentRepository).findOrderIdByTrackingNumber(trackingNumber);
        inOrder.verify(commerceFacade).lockOrderForFulfillment(orderId);
        inOrder.verify(shipmentRepository).findByOrderIdForUpdate(orderId);
        inOrder.verify(shipmentRepository).save(lockedShipment);
        inOrder.verify(commerceFacade).markShipped(orderId, now);

        assertThat(lockedShipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_PICKED_UP);
    }

    @Test
    @DisplayName("getIncidentQueue: Trả về danh sách shipment có admin_review_required = true")
    void testGetIncidentQueue() {
        ShipmentEntity s1 = new ShipmentEntity(101L, "OG-CLIENT-101", now);
        s1.setTrackingNumber("GHN_101");
        s1.recordIncident("damage", "Hàng bị hư hỏng", now);

        when(shipmentRepository.findByAdminReviewRequiredTrueOrderByUpdatedAtDesc()).thenReturn(List.of(s1));

        List<com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.ShipmentIncidentResponse> queue = service.getIncidentQueue();
        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).orderId()).isEqualTo(101L);
        assertThat(queue.get(0).trackingNumber()).isEqualTo("GHN_101");
        assertThat(queue.get(0).adminReviewRequired()).isTrue();
        assertThat(queue.get(0).rawGhnStatus()).isEqualTo("damage");
    }

    @Test
    @DisplayName("resolveIncident DISMISS_AND_RESUME: Gỡ cờ sự cố, reset attempts, lưu ghi chú")
    void testResolveIncidentDismissAndResume() {
        Long orderId = 200L;
        ShipmentEntity shipment = new ShipmentEntity(orderId, "OG-CLIENT-200", now);
        shipment.setTrackingNumber("GHN_200");
        shipment.recordIncident("exception", "Lỗi bất thường GHN", now);
        shipment.setReconciliationAttempts(5);

        OrderFulfillmentSnapshot snapshot = new OrderFulfillmentSnapshot(
                orderId, 1L, 2L, "SHIPPED", null, null
        );

        when(commerceFacade.lockOrderForFulfillment(orderId)).thenReturn(Optional.of(snapshot));
        when(shipmentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(shipment));

        var response = service.resolveIncident(
                orderId,
                com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.IncidentResolutionAction.DISMISS_AND_RESUME,
                "Đã liên hệ GHN xác nhận kiện hàng vẫn đang lưu kho an toàn",
                "admin@ogshop.vn"
        );

        assertThat(response.adminReviewRequired()).isFalse();
        assertThat(shipment.isAdminReviewRequired()).isFalse();
        assertThat(shipment.getReconciliationAttempts()).isEqualTo(0);
        assertThat(shipment.getIncidentNotes()).contains("DISMISS_AND_RESUME by admin@ogshop.vn");
        assertThat(shipment.getIncidentNotes()).contains("Đã liên hệ GHN xác nhận");
        verify(shipmentRepository).save(shipment);
    }

    @Test
    @DisplayName("resolveIncident MARK_DELIVERED: Chuyển shipment sang DELIVERED và đồng bộ Order")
    void testResolveIncidentMarkDelivered() {
        Long orderId = 300L;
        ShipmentEntity shipment = new ShipmentEntity(orderId, "OG-CLIENT-300", now);
        shipment.setTrackingNumber("GHN_300");
        shipment.markPickedUp(now, "picked", now);
        shipment.recordIncident("exception", "GHN không gửi webhook giao hàng", now);

        OrderFulfillmentSnapshot snapshot = new OrderFulfillmentSnapshot(
                orderId, 1L, 2L, "SHIPPED", null, null
        );

        when(commerceFacade.lockOrderForFulfillment(orderId)).thenReturn(Optional.of(snapshot));
        when(shipmentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(shipment));

        var response = service.resolveIncident(
                orderId,
                com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.IncidentResolutionAction.MARK_DELIVERED,
                "Buyer đã nhận được hàng và gửi ảnh chứng minh",
                "admin@ogshop.vn"
        );

        assertThat(response.adminReviewRequired()).isFalse();
        assertThat(response.status()).isEqualTo(ShipmentEntity.STATUS_DELIVERED);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_DELIVERED);
        assertThat(shipment.isAdminReviewRequired()).isFalse();

        verify(commerceFacade).markDeliveredDirectFromCarrier(orderId, now);
        verify(shipmentRepository).save(shipment);
    }

    @Test
    @DisplayName("resolveIncident MARK_DELIVERED: Chặn ném lỗi nếu đơn hàng đang ở trạng thái xung đột terminal")
    void testResolveIncidentMarkDeliveredTerminalConflict() {
        Long orderId = 400L;
        ShipmentEntity shipment = new ShipmentEntity(orderId, "OG-CLIENT-400", now);
        shipment.setTrackingNumber("GHN_400");
        shipment.markPickedUp(now, "picked", now);
        shipment.markReturned(now, now); // Shipment is RETURNED

        OrderFulfillmentSnapshot snapshot = new OrderFulfillmentSnapshot(
                orderId, 1L, 2L, "CANCELLED", null, null
        );

        when(commerceFacade.lockOrderForFulfillment(orderId)).thenReturn(Optional.of(snapshot));
        when(shipmentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> service.resolveIncident(
                orderId,
                com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.IncidentResolutionAction.MARK_DELIVERED,
                "Admin can thiệp giao hàng",
                "admin@ogshop.vn"
        )).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("xung đột");
    }

    @Test
    @DisplayName("resolveIncident MARK_RETURNED: Chuyển shipment sang RETURNED và tắt cờ review")
    void testResolveIncidentMarkReturned() {
        Long orderId = 500L;
        ShipmentEntity shipment = new ShipmentEntity(orderId, "OG-CLIENT-500", now);
        shipment.setTrackingNumber("GHN_500");
        shipment.markPickedUp(now, "picked", now);
        shipment.recordIncident("return", "Hàng chuyển hoàn về người bán", now);

        OrderFulfillmentSnapshot snapshot = new OrderFulfillmentSnapshot(
                orderId, 1L, 2L, "SHIPPED", null, null
        );

        when(commerceFacade.lockOrderForFulfillment(orderId)).thenReturn(Optional.of(snapshot));
        when(shipmentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(shipment));

        var response = service.resolveIncident(
                orderId,
                com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.IncidentResolutionAction.MARK_RETURNED,
                "Seller xác nhận đã nhận lại kiện hàng",
                "admin@ogshop.vn"
        );

        assertThat(response.adminReviewRequired()).isFalse();
        assertThat(response.status()).isEqualTo(ShipmentEntity.STATUS_RETURNED);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_RETURNED);
        verify(shipmentRepository).save(shipment);
    }

    @Test
    @DisplayName("resolveIncident MARK_FAILED: Chuyển shipment sang FAILED và tắt cờ review")
    void testResolveIncidentMarkFailed() {
        Long orderId = 600L;
        ShipmentEntity shipment = new ShipmentEntity(orderId, "OG-CLIENT-600", now);
        shipment.setTrackingNumber("GHN_600");
        shipment.markPickedUp(now, "picked", now);
        shipment.recordIncident("lost", "Kiện hàng bị thất lạc hoàn toàn", now);

        OrderFulfillmentSnapshot snapshot = new OrderFulfillmentSnapshot(
                orderId, 1L, 2L, "SHIPPED", null, null
        );

        when(commerceFacade.lockOrderForFulfillment(orderId)).thenReturn(Optional.of(snapshot));
        when(shipmentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(shipment));

        var response = service.resolveIncident(
                orderId,
                com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.IncidentResolutionAction.MARK_FAILED,
                "Biên bản bồi thường bảo hiểm GHN hoàn tất",
                "admin@ogshop.vn"
        );

        assertThat(response.adminReviewRequired()).isFalse();
        assertThat(response.status()).isEqualTo(ShipmentEntity.STATUS_FAILED);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentEntity.STATUS_FAILED);
        verify(shipmentRepository).save(shipment);
    }
}
