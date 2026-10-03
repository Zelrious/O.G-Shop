package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.catalog.application.CatalogCommerceFacade;
import com.oldbutgold.shop.modules.catalog.application.ProductStateConflictException;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.*;
import com.oldbutgold.shop.modules.identity.application.IdentityCommerceFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CheckoutServiceTest {
    private CatalogCommerceFacade catalogCommerceFacade;
    private IdentityCommerceFacade identityCommerceFacade;
    private SystemFeePolicyRepository systemFeePolicyRepository;
    private VoucherRepository voucherRepository;
    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private OrderVoucherRepository orderVoucherRepository;
    private Clock clock;

    private CheckoutService checkoutService;

    private CatalogCommerceFacade.CommerceProductSummary productSummary;
    private CatalogCommerceFacade.CommerceProductDetail productDetail;
    private IdentityCommerceFacade.UserSummary sellerSummary;
    private IdentityCommerceFacade.AddressDto addressDto;
    private SystemFeePolicyEntity feePolicy;
    private VoucherEntity voucher;
    private Instant now;

    @BeforeEach
    void setUp() throws Exception {
        catalogCommerceFacade = mock(CatalogCommerceFacade.class);
        identityCommerceFacade = mock(IdentityCommerceFacade.class);
        systemFeePolicyRepository = mock(SystemFeePolicyRepository.class);
        voucherRepository = mock(VoucherRepository.class);
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        orderVoucherRepository = mock(OrderVoucherRepository.class);

        now = Instant.parse("2026-09-21T10:00:00Z");
        clock = Clock.fixed(now, ZoneId.of("UTC"));

        checkoutService = new CheckoutService(
                catalogCommerceFacade, identityCommerceFacade,
                systemFeePolicyRepository, voucherRepository,
                orderRepository, orderItemRepository, orderVoucherRepository, clock
        );

        productSummary = new CatalogCommerceFacade.CommerceProductSummary(
                1001L, 200L, "Sony WH-1000XM4 Tai nghe chống ồn",
                new BigDecimal("4500000"), "LIKE_NEW", "ACTIVE",
                "/api/v1/media/sony.jpg", false
        );

        productDetail = new CatalogCommerceFacade.CommerceProductDetail(
                1001L, 200L, "Sony WH-1000XM4 Tai nghe chống ồn",
                new BigDecimal("4500000"), "LIKE_NEW", "ACTIVE",
                "/api/v1/media/sony.jpg", false, 1L
        );

        sellerSummary = new IdentityCommerceFacade.UserSummary(
                200L, "Nguyễn Văn Bán", "seller@ogshop.vn", "0909123456"
        );

        addressDto = new IdentityCommerceFacade.AddressDto(
                501L, 300L, "Trần Người Mua", "0918123456",
                "TP.HCM", "Quận 1", "Bến Nghé", "456 Nguyễn Huệ", true, now
        );

        feePolicy = new SystemFeePolicyEntity();
        setField(feePolicy, "id", 1L);
        setField(feePolicy, "policyCode", "DEFAULT_ZERO_V1");
        setField(feePolicy, "buyerFeeRate", BigDecimal.ZERO);
        setField(feePolicy, "buyerFixedFee", BigDecimal.ZERO);
        setField(feePolicy, "sellerFeeRate", BigDecimal.ZERO);
        setField(feePolicy, "sellerFixedFee", BigDecimal.ZERO);
        setField(feePolicy, "status", "ACTIVE");
        setField(feePolicy, "effectiveFrom", now.minusSeconds(86400));

        voucher = new VoucherEntity();
        setField(voucher, "id", 10L);
        setField(voucher, "code", "WELCOMEOG");
        setField(voucher, "title", "Giảm 50.000đ");
        setField(voucher, "voucherType", "ORDER_DISCOUNT");
        setField(voucher, "discountType", "FIXED_AMOUNT");
        setField(voucher, "discountValue", new BigDecimal("50000"));
        setField(voucher, "minOrderAmount", new BigDecimal("100000"));
        setField(voucher, "sponsorType", "PLATFORM");
        setField(voucher, "currentUsageCount", 0);
        setField(voucher, "totalUsageLimit", 100);
        setField(voucher, "startTime", now.minusSeconds(3600));
        setField(voucher, "endTime", now.plusSeconds(86400));
        setField(voucher, "isActive", true);
    }

    @Test
    void getCheckoutPreview_calculatesCorrectPricingWithoutVoucher() {
        when(catalogCommerceFacade.getActiveProduct(1001L)).thenReturn(Optional.of(productSummary));
        when(identityCommerceFacade.getUserSummary(200L)).thenReturn(Optional.of(sellerSummary));
        when(identityCommerceFacade.getUserAddresses(300L)).thenReturn(List.of(addressDto));
        when(voucherRepository.findByIsActiveTrueAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(now, now))
                .thenReturn(List.of(voucher));

        CheckoutDtos.CheckoutPreviewResponse preview = checkoutService.getCheckoutPreview(300L, 1001L, null);

        assertThat(preview.productId()).isEqualTo(1001L);
        assertThat(preview.listedPrice()).isEqualByComparingTo("4500000");
        assertThat(preview.shippingFee()).isEqualByComparingTo("30000");
        assertThat(preview.buyerSystemFee()).isEqualByComparingTo("0");
        assertThat(preview.voucherDiscount()).isEqualByComparingTo("0");
        assertThat(preview.totalAmount()).isEqualByComparingTo("4530000"); // 4.500.000 + 30.000
        assertThat(preview.selectedAddress()).isNotNull();
        assertThat(preview.selectedAddress().recipientName()).isEqualTo("Trần Người Mua");
    }

    @Test
    void getCheckoutPreview_calculatesCorrectPricingWithVoucher() {
        when(catalogCommerceFacade.getActiveProduct(1001L)).thenReturn(Optional.of(productSummary));
        when(identityCommerceFacade.getUserSummary(200L)).thenReturn(Optional.of(sellerSummary));
        when(identityCommerceFacade.getUserAddresses(300L)).thenReturn(List.of(addressDto));
        when(voucherRepository.findByIsActiveTrueAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(now, now))
                .thenReturn(List.of(voucher));
        when(voucherRepository.findByCodeAndIsActiveTrue("WELCOMEOG")).thenReturn(Optional.of(voucher));

        CheckoutDtos.CheckoutPreviewResponse preview = checkoutService.getCheckoutPreview(300L, 1001L, "WELCOMEOG");

        assertThat(preview.voucherDiscount()).isEqualByComparingTo("50000");
        assertThat(preview.totalAmount()).isEqualByComparingTo("4480000"); // 4.530.000 - 50.000
        assertThat(preview.appliedVoucher()).isNotNull();
        assertThat(preview.appliedVoucher().code()).isEqualTo("WELCOMEOG");
    }

    @Test
    void buyNow_succeedsAndReservesProductFor1Hour() {
        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(productDetail);
        when(identityCommerceFacade.getAddressById(300L, 501L)).thenReturn(Optional.of(addressDto));
        when(systemFeePolicyRepository.findFirstByStatusOrderByEffectiveFromDesc("ACTIVE")).thenReturn(Optional.of(feePolicy));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity o = invocation.getArgument(0);
            setField(o, "id", 9901L);
            return o;
        });
        when(orderItemRepository.save(any(OrderItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null);
        CheckoutDtos.OrderCreatedResponse response = checkoutService.buyNow(300L, request);

        assertThat(response.orderId()).isEqualTo(9901L);
        assertThat(response.status()).isEqualTo("PAYMENT_PENDING");
        assertThat(response.totalAmount()).isEqualByComparingTo("4530000");
        // DP-02 & User input: 1 hour timeout (3600 seconds)
        assertThat(response.paymentDueAt()).isEqualTo(now.plusSeconds(3600));

        verify(catalogCommerceFacade).reserveProduct(1001L, 9901L, now.plusSeconds(3600), now);
    }

    @Test
    void buyNow_blocksWhenProductRequiresBuyerEkycAndBuyerNotVerified() {
        CatalogCommerceFacade.CommerceProductDetail ekycProductDetail = new CatalogCommerceFacade.CommerceProductDetail(
                1001L, 200L, "iPhone 15 Pro",
                new BigDecimal("22000000"), "LIKE_NEW", "ACTIVE",
                null, true, 1L // requiresBuyerEkyc = true
        );

        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(ekycProductDetail);
        when(identityCommerceFacade.isUserVerified(300L)).thenReturn(false);

        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null);

        assertThatThrownBy(() -> checkoutService.buyNow(300L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("eKYC CCCD");
    }

    @Test
    void buyNow_failsWhenSellerBuysOwnProduct() {
        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(productDetail);

        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null);

        assertThatThrownBy(() -> checkoutService.buyNow(200L, request)) // 200L is sellerId
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không thể tự mua sản phẩm của chính mình");
    }

    @Test
    void buyNow_failsWhenProductAlreadyReservedOrSold() {
        when(catalogCommerceFacade.getProductForCheckoutLock(1001L))
                .thenThrow(new ProductStateConflictException("Sản phẩm đang ở trạng thái 'RESERVED' và không thể đặt mua."));

        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null);

        assertThatThrownBy(() -> checkoutService.buyNow(300L, request))
                .isInstanceOf(ProductStateConflictException.class)
                .hasMessageContaining("không thể đặt mua");
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
