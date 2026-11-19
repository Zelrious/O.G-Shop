package com.oldbutgold.shop.modules.commerce.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductRepository;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductRevisionEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductRevisionRepository;
import com.oldbutgold.shop.modules.commerce.application.GroupCheckoutDtos.GroupCheckoutItemRequest;
import com.oldbutgold.shop.modules.commerce.application.GroupCheckoutDtos.GroupCheckoutRequest;
import com.oldbutgold.shop.modules.commerce.application.GroupCheckoutDtos.ShippingAddressDto;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.CheckoutGroupRepository;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.InventoryReservationRepository;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderItemRepository;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.identity.application.IdentityCommerceFacade;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.RewardPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroupCheckoutServiceEkycTest {

    private ProductRepository productRepository;
    private ProductRevisionRepository productRevisionRepository;
    private CheckoutGroupRepository checkoutGroupRepository;
    private IdentityCommerceFacade identityCommerceFacade;
    private GroupCheckoutService groupCheckoutService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        productRevisionRepository = mock(ProductRevisionRepository.class);
        checkoutGroupRepository = mock(CheckoutGroupRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        InventoryReservationRepository reservationRepository = mock(InventoryReservationRepository.class);
        RewardPolicyRepository rewardPolicyRepository = mock(RewardPolicyRepository.class);
        identityCommerceFacade = mock(IdentityCommerceFacade.class);

        groupCheckoutService = new GroupCheckoutService(
                productRepository,
                productRevisionRepository,
                checkoutGroupRepository,
                orderRepository,
                orderItemRepository,
                reservationRepository,
                rewardPolicyRepository,
                null,
                Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC),
                new ObjectMapper(),
                identityCommerceFacade
        );
    }

    private static ShippingAddressDto sampleAddress() {
        return new ShippingAddressDto("Nguyen Van A", "0901234567", "TP.HCM", "Quan 1", "Ben Nghe", "123 Le Loi");
    }

    private void stubHighValueProduct(long productId, long sellerId) {
        ProductEntity product = mock(ProductEntity.class);
        when(product.getId()).thenReturn(productId);
        when(product.getSellerId()).thenReturn(sellerId);
        when(product.getTitle()).thenReturn("Rolex Vintage High-Value");
        when(product.getStatus()).thenReturn(ProductEntity.STATUS_ACTIVE);
        when(product.getWorkflowModel()).thenReturn("UC83");
        when(product.isRequiresBuyerEkyc()).thenReturn(true);
        when(product.getQuantityAvailable()).thenReturn(2);
        when(product.getPublicRevisionId()).thenReturn(500L);
        when(product.getReturnAllowed()).thenReturn(true);

        ProductRevisionEntity revision = mock(ProductRevisionEntity.class);
        when(revision.isReturnAllowed()).thenReturn(true);
        when(revision.getDeliveryOptions()).thenReturn("[\"CARRIER\"]");
        when(revision.getListedUnitPrice()).thenReturn(new BigDecimal("25000000"));

        when(checkoutGroupRepository.findByCommandKey(any())).thenReturn(Optional.empty());
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRevisionRepository.findById(500L)).thenReturn(Optional.of(revision));
    }

    @Test
    @DisplayName("UC24: High-value product in GroupCheckout rejects unverified buyer eKYC")
    void groupCheckout_blocksWhenRequiresBuyerEkycAndBuyerUnverified() {
        stubHighValueProduct(101L, 200L);
        when(identityCommerceFacade.isUserVerified(300L)).thenReturn(false);

        GroupCheckoutRequest request = new GroupCheckoutRequest(
                "GRP-EKYC-1",
                sampleAddress(),
                "CARRIER",
                "GHN",
                "VNPAY",
                null,
                null,
                List.of(new GroupCheckoutItemRequest(101L, 1, null)),
                UUID.randomUUID()
        );

        assertThatThrownBy(() -> groupCheckoutService.executeCheckout(300L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("eKYC CCCD");

        verify(identityCommerceFacade).requirePurchaseAllowed(300L, 200L, true);
    }

    @Test
    @DisplayName("UC08: High-value product in GroupCheckout enforces quickAuthResultId when buyer is eKYC verified")
    void groupCheckout_blocksWhenQuickAuthMissingOrInvalid() {
        stubHighValueProduct(101L, 200L);
        when(identityCommerceFacade.isUserVerified(300L)).thenReturn(true);
        doThrow(new IllegalArgumentException("Thao tác này yêu cầu xác thực nhanh khuôn mặt."))
                .when(identityCommerceFacade).requireQuickAuthResult(eq(300L), isNull());

        GroupCheckoutRequest request = new GroupCheckoutRequest(
                "GRP-EKYC-2",
                sampleAddress(),
                "CARRIER",
                "GHN",
                "VNPAY",
                null,
                null,
                List.of(new GroupCheckoutItemRequest(101L, 1, null))
        );

        assertThatThrownBy(() -> groupCheckoutService.executeCheckout(300L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("xác thực nhanh khuôn mặt");

        verify(identityCommerceFacade).requireQuickAuthResult(eq(300L), isNull());
    }
}
