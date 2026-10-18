package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.AddressEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.AddressRepository;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DefaultFulfillmentCommerceFacadeTest {

    private OrderRepository orderRepository;
    private AddressRepository addressRepository;
    private DefaultFulfillmentCommerceFacade facade;

    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        addressRepository = mock(AddressRepository.class);
        facade = new DefaultFulfillmentCommerceFacade(orderRepository, addressRepository);
    }

    @Test
    @DisplayName("getOrderSnapshot: ánh xạ đầy đủ thông tin địa chỉ lấy hàng của Seller và nhận hàng của Buyer")
    void testGetOrderSnapshotMapsFullSellerAndBuyerAddresses() {
        long orderId = 100L;
        long buyerId = 200L;
        long sellerId = 300L;
        long sourceAddressId = 400L;

        OrderEntity order = new OrderEntity(
                UUID.randomUUID(), buyerId, sellerId, sourceAddressId,
                "Trần Người Nhận", "0987654321",
                "TP. Hồ Chí Minh", "Quận 1", "Phường Bến Nghé", "123 Lê Duẩn",
                202, 1444, "20311",
                new BigDecimal("500000"), new BigDecimal("30000"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500000"),
                BigDecimal.ZERO, BigDecimal.ZERO, "PLATFORM", new BigDecimal("530000"),
                NOW.plusSeconds(3600), NOW
        );
        java.lang.reflect.Field idField;
        try {
            idField = OrderEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(order, orderId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        AddressEntity sellerAddress = new AddressEntity(
                sellerId, "Shop Vintage", "0912345678",
                "TP. Hồ Chí Minh", "Quận 3", "Phường Võ Thị Sáu", "456 Hai Bà Trưng",
                202, 1442, "20101", true, NOW
        );

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(addressRepository.findById(sourceAddressId)).thenReturn(Optional.of(sellerAddress));

        Optional<FulfillmentCommerceFacade.OrderFulfillmentSnapshot> snapshotOpt = facade.getOrderSnapshot(orderId);

        assertThat(snapshotOpt).isPresent();
        FulfillmentCommerceFacade.OrderFulfillmentSnapshot snapshot = snapshotOpt.get();

        assertThat(snapshot.orderId()).isEqualTo(orderId);
        assertThat(snapshot.buyerId()).isEqualTo(buyerId);
        assertThat(snapshot.sellerId()).isEqualTo(sellerId);
        assertThat(snapshot.sourceAddressId()).isEqualTo(sourceAddressId);

        // Seller pickup address
        assertThat(snapshot.fromName()).isEqualTo("Shop Vintage");
        assertThat(snapshot.fromPhone()).isEqualTo("0912345678");
        assertThat(snapshot.fromAddress()).isEqualTo("456 Hai Bà Trưng");
        assertThat(snapshot.fromProvinceName()).isEqualTo("TP. Hồ Chí Minh");
        assertThat(snapshot.fromDistrictName()).isEqualTo("Quận 3");
        assertThat(snapshot.fromWardName()).isEqualTo("Phường Võ Thị Sáu");
        assertThat(snapshot.fromDistrictId()).isEqualTo(1442);
        assertThat(snapshot.fromWardCode()).isEqualTo("20101");

        // Buyer shipping address
        assertThat(snapshot.toName()).isEqualTo("Trần Người Nhận");
        assertThat(snapshot.toPhone()).isEqualTo("0987654321");
        assertThat(snapshot.toAddress()).isEqualTo("123 Lê Duẩn");
        assertThat(snapshot.toProvinceName()).isEqualTo("TP. Hồ Chí Minh");
        assertThat(snapshot.toDistrictName()).isEqualTo("Quận 1");
        assertThat(snapshot.toWardName()).isEqualTo("Phường Bến Nghé");
        assertThat(snapshot.toDistrictId()).isEqualTo(1444);
        assertThat(snapshot.toWardCode()).isEqualTo("20311");

        // Verification helpers
        assertThat(snapshot.hasValidGhnShippingCodes()).isTrue();
    }

    @Test
    @DisplayName("getOrderSnapshot: xử lý graceful khi đơn hàng không có sourceAddressId (legacy order)")
    void testGetOrderSnapshotHandlesLegacyOrderWithoutSourceAddress() {
        long orderId = 101L;
        OrderEntity legacyOrder = new OrderEntity(
                UUID.randomUUID(), 200L, 300L, null,
                "Trần Người Nhận", "0987654321",
                "TP. Hồ Chí Minh", "Quận 1", "Phường Bến Nghé", "123 Lê Duẩn",
                null, null, null,
                new BigDecimal("500000"), new BigDecimal("30000"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500000"),
                BigDecimal.ZERO, BigDecimal.ZERO, "PLATFORM", new BigDecimal("530000"),
                NOW.plusSeconds(3600), NOW
        );

        setField(legacyOrder, "id", orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(legacyOrder));

        Optional<FulfillmentCommerceFacade.OrderFulfillmentSnapshot> snapshotOpt = facade.getOrderSnapshot(orderId);

        assertThat(snapshotOpt).isPresent();
        FulfillmentCommerceFacade.OrderFulfillmentSnapshot snapshot = snapshotOpt.get();

        assertThat(snapshot.sourceAddressId()).isNull();
        assertThat(snapshot.fromName()).isNull();
        assertThat(snapshot.fromDistrictId()).isNull();
        assertThat(snapshot.hasValidGhnShippingCodes()).isFalse();

        verify(addressRepository, never()).findById(any());
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
