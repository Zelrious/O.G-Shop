package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.catalog.application.CatalogCommerceFacade;
import com.oldbutgold.shop.modules.catalog.application.ProductStateConflictException;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.*;
import com.oldbutgold.shop.modules.identity.application.IdentityCommerceFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
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
    private VoucherService wallet;
    private JdbcTemplate jdbcTemplate;

    private CheckoutService checkoutService;

    private CatalogCommerceFacade.CommerceProductSummary productSummary;
    private CatalogCommerceFacade.CommerceProductDetail productDetail;
    private IdentityCommerceFacade.UserSummary sellerSummary;
    private IdentityCommerceFacade.AddressDto addressDto;
    private IdentityCommerceFacade.AddressDto sellerAddressDto;
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
        wallet = mock(VoucherService.class);
        jdbcTemplate = mock(JdbcTemplate.class);

        now = Instant.parse("2026-09-21T10:00:00Z");
        clock = Clock.fixed(now, ZoneId.of("UTC"));

        checkoutService = new CheckoutService(
                catalogCommerceFacade, identityCommerceFacade,
                systemFeePolicyRepository, voucherRepository,
                orderRepository, orderItemRepository, orderVoucherRepository, clock,
                mock(com.oldbutgold.shop.modules.communication.application.NegotiatedCheckoutFacade.class),
                new com.fasterxml.jackson.databind.ObjectMapper(), wallet, null, jdbcTemplate
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
                "TP.HCM", "Quận 1", "Bến Nghé", "456 Nguyễn Huệ",
                202, 1442, "20101", true, now
        );

        sellerAddressDto = new IdentityCommerceFacade.AddressDto(
                601L, 200L, "Nguyễn Văn Bán", "0909123456",
                "TP.HCM", "Quận 7", "Tân Phú", "123 Nguyễn Thị Thập",
                202, 1447, "20601", true, now
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
        var campaign=new com.oldbutgold.shop.modules.commerce.api.VoucherDtos.Campaign(10,11,0,"WELCOMEOG","Giảm 50.000đ","","ORDER_DISCOUNT","FIXED_AMOUNT",new BigDecimal("50000"),null,new BigDecimal("100000"),"CHECKOUT",List.of(),100,now.minusSeconds(3600),now.plusSeconds(86400),true,true,1,0);
        var grant=new com.oldbutgold.shop.modules.commerce.api.VoucherDtos.Grant(java.util.UUID.randomUUID(),300,"Buyer","AVAILABLE",now,now.plusSeconds(86400),campaign);
        when(wallet.usable(300L,1001L,new BigDecimal("4500000"))).thenReturn(List.of(grant));
        when(wallet.resolve(300L,"WELCOMEOG",1001L,new BigDecimal("4500000"),false)).thenReturn(grant);

        when(jdbcTemplate.query(contains("INSERT INTO public.checkout_idempotency_entries"), any(RowMapper.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of(300L));
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
        when(identityCommerceFacade.getDefaultAddress(200L)).thenReturn(Optional.of(sellerAddressDto));
        when(systemFeePolicyRepository.findFirstByStatusOrderByEffectiveFromDesc("ACTIVE")).thenReturn(Optional.of(feePolicy));
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(balance, 0) FROM og_compat.reward_accounts"), eq(Long.class), eq(300L))).thenReturn(100000L);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_accounts WHERE user_id = ?"), eq(Long.class), eq(300L))).thenReturn(1L);
        when(jdbcTemplate.queryForList(contains("SELECT policy_id FROM og_compat.reward_policies"), eq(Long.class), eq("REWARD_CHECKOUT_STANDARD"))).thenReturn(List.of(100L));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity o = invocation.getArgument(0);
            setField(o, "id", 9901L);
            return o;
        });
        when(orderItemRepository.saveAndFlush(any(OrderItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, "CMD-UNIT-TEST");
        CheckoutDtos.OrderCreatedResponse response = checkoutService.buyNow(300L, request);

        assertThat(response.orderId()).isEqualTo(9901L);
        assertThat(response.status()).isEqualTo("PAYMENT_PENDING");
        assertThat(response.totalAmount()).isEqualByComparingTo("4530000");
        // DP-02 & User input: 1 hour timeout (3600 seconds)
        assertThat(response.paymentDueAt()).isEqualTo(now.plusSeconds(3600));

        org.mockito.ArgumentCaptor<OrderEntity> orderCaptor = org.mockito.ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository).save(orderCaptor.capture());
        OrderEntity savedOrder = orderCaptor.getValue();
        // ADDR-02: verify sourceAddressId is seller's address, and GHN codes are snapshotted
        assertThat(savedOrder.getSourceAddressId()).isEqualTo(601L);
        assertThat(savedOrder.getShippingGhnProvinceId()).isEqualTo(202);
        assertThat(savedOrder.getShippingGhnDistrictId()).isEqualTo(1442);
        assertThat(savedOrder.getShippingGhnWardCode()).isEqualTo("20101");

        verify(catalogCommerceFacade).reserveProduct(1001L, 9901L, now.plusSeconds(3600), now);
    }

    @Test
    void buyNow_failsWhenBuyerAddressMissingGhnCodes() {
        IdentityCommerceFacade.AddressDto invalidBuyerAddress = new IdentityCommerceFacade.AddressDto(
                501L, 300L, "Trần Người Mua", "0918123456",
                "TP.HCM", "Quận 1", "Bến Nghé", "456 Nguyễn Huệ",
                null, null, null, true, now
        );
        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(productDetail);
        when(identityCommerceFacade.getAddressById(300L, 501L)).thenReturn(Optional.of(invalidBuyerAddress));

        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, "CMD-UNIT-TEST");

        assertThatThrownBy(() -> checkoutService.buyNow(300L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chưa được chuẩn hóa theo mã bưu chính GHN");
    }

    @Test
    void buyNow_failsWhenSellerHasNoDefaultAddress() {
        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(productDetail);
        when(identityCommerceFacade.getAddressById(300L, 501L)).thenReturn(Optional.of(addressDto));
        when(identityCommerceFacade.getDefaultAddress(200L)).thenReturn(Optional.empty());

        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, "CMD-UNIT-TEST");

        assertThatThrownBy(() -> checkoutService.buyNow(300L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("người bán chưa thiết lập địa chỉ lấy hàng chuẩn hóa GHN");
    }

    @Test
    void buyNow_failsWhenSellerAddressMissingGhnCodes() {
        IdentityCommerceFacade.AddressDto invalidSellerAddress = new IdentityCommerceFacade.AddressDto(
                601L, 200L, "Nguyễn Văn Bán", "0909123456",
                "TP.HCM", "Quận 7", "Tân Phú", "123 Nguyễn Thị Thập",
                202, null, null, true, now
        );
        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(productDetail);
        when(identityCommerceFacade.getAddressById(300L, 501L)).thenReturn(Optional.of(addressDto));
        when(identityCommerceFacade.getDefaultAddress(200L)).thenReturn(Optional.of(invalidSellerAddress));

        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, "CMD-UNIT-TEST");

        assertThatThrownBy(() -> checkoutService.buyNow(300L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("người bán chưa thiết lập địa chỉ lấy hàng chuẩn hóa GHN");
    }

    @Test
    void buyNow_withNewAddress_succeedsWhenGhnCodesProvided() {
        CheckoutDtos.CreateAddressRequest newAddrRequest = new CheckoutDtos.CreateAddressRequest(
                "Người Nhận Mới", "0988776655", "TP.HCM", "Quận 3", "Võ Thị Sáu", "12 Hai Bà Trưng",
                202, 1444, "20301", false
        );
        IdentityCommerceFacade.AddressDto createdAddress = new IdentityCommerceFacade.AddressDto(
                502L, 300L, "Người Nhận Mới", "0988776655", "TP.HCM", "Quận 3", "Võ Thị Sáu", "12 Hai Bà Trưng",
                202, 1444, "20301", false, now
        );
        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(productDetail);
        when(identityCommerceFacade.createAddress(eq(300L), any(IdentityCommerceFacade.CreateAddressRequest.class)))
                .thenReturn(createdAddress);
        when(identityCommerceFacade.getDefaultAddress(200L)).thenReturn(Optional.of(sellerAddressDto));
        when(systemFeePolicyRepository.findFirstByStatusOrderByEffectiveFromDesc("ACTIVE")).thenReturn(Optional.of(feePolicy));
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(balance, 0) FROM og_compat.reward_accounts"), eq(Long.class), eq(300L))).thenReturn(100000L);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_accounts WHERE user_id = ?"), eq(Long.class), eq(300L))).thenReturn(1L);
        when(jdbcTemplate.queryForList(contains("SELECT policy_id FROM og_compat.reward_policies"), eq(Long.class), eq("REWARD_CHECKOUT_STANDARD"))).thenReturn(List.of(100L));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity o = invocation.getArgument(0);
            setField(o, "id", 9902L);
            return o;
        });
        when(orderItemRepository.save(any(OrderItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, null, newAddrRequest, null, "CMD-UNIT-TEST");
        CheckoutDtos.OrderCreatedResponse response = checkoutService.buyNow(300L, request);

        assertThat(response.orderId()).isEqualTo(9902L);

        org.mockito.ArgumentCaptor<OrderEntity> orderCaptor = org.mockito.ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository).save(orderCaptor.capture());
        OrderEntity savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getSourceAddressId()).isEqualTo(601L);
        assertThat(savedOrder.getShippingGhnProvinceId()).isEqualTo(202);
        assertThat(savedOrder.getShippingGhnDistrictId()).isEqualTo(1444);
        assertThat(savedOrder.getShippingGhnWardCode()).isEqualTo("20301");
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

        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, "CMD-UNIT-TEST");

        assertThatThrownBy(() -> checkoutService.buyNow(300L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("eKYC CCCD");
    }

    @Test
    void buyNow_failsWhenSellerBuysOwnProduct() {
        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(productDetail);

        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, "CMD-UNIT-TEST");

        assertThatThrownBy(() -> checkoutService.buyNow(200L, request)) // 200L is sellerId
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không thể tự mua sản phẩm của chính mình");
    }

    @Test
    void buyNow_failsWhenProductAlreadyReservedOrSold() {
        when(catalogCommerceFacade.getProductForCheckoutLock(1001L))
                .thenThrow(new ProductStateConflictException("Sản phẩm đang ở trạng thái 'RESERVED' và không thể đặt mua."));

        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, "CMD-UNIT-TEST");

        assertThatThrownBy(() -> checkoutService.buyNow(300L, request))
                .isInstanceOf(ProductStateConflictException.class)
                .hasMessageContaining("không thể đặt mua");
    }

    @Test
    void buyNow_redeemsCoinsSuccessfully() {
        checkoutService.setCanonicalRewardPolicyCode("REWARD_CHECKOUT_STANDARD");
        checkoutService.setMaxRedemptionPercentage("50"); // DEC-10.7A: cap must be configured
        checkoutService.setCapPoApproved(true); // GAP-1: simulated PO-approved state for unit calculation verification
        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(productDetail);
        when(identityCommerceFacade.getUserSummary(200L)).thenReturn(Optional.of(sellerSummary));
        when(identityCommerceFacade.getAddressById(300L, 501L)).thenReturn(Optional.of(addressDto));
        when(identityCommerceFacade.getDefaultAddress(200L)).thenReturn(Optional.of(sellerAddressDto));
        when(systemFeePolicyRepository.findFirstByStatusOrderByEffectiveFromDesc("ACTIVE")).thenReturn(Optional.of(feePolicy));
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(balance, 0) FROM og_compat.reward_accounts"), eq(Long.class), eq(300L))).thenReturn(100000L);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_accounts WHERE user_id = ?"), eq(Long.class), eq(300L))).thenReturn(1L);
        when(jdbcTemplate.queryForList(contains("SELECT policy_id FROM og_compat.reward_policies"), eq(Long.class), eq("REWARD_CHECKOUT_STANDARD"))).thenReturn(List.of(100L));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity saved = invocation.getArgument(0);
            setField(saved, "id", 9902L);
            return saved;
        });
        when(orderItemRepository.saveAndFlush(any(OrderItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Subtotal = 4,500,000, Shipping = 30,000, Redeem 50,000 coins (1 Coin = 1 VND)
        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 50000L, "CMD-UNIT-TEST");
        CheckoutDtos.OrderCreatedResponse response = checkoutService.buyNow(300L, request);

        assertThat(response).isNotNull();
        assertThat(response.pointsDiscountAmount()).isEqualByComparingTo(new BigDecimal("50000"));
        // Total = 4,500,000 + 30,000 - 50,000 = 4,480,000
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("4480000"));
    }

    @Test
    @DisplayName("DEC-10.7A: fails closed when REWARD_MAX_REDEMPTION_PERCENTAGE is not configured")
    void buyNow_failsClosedWhenCapNotConfigured() {
        // cap not configured — policy code configured correctly, but cap is missing
        checkoutService.setCanonicalRewardPolicyCode("REWARD_CHECKOUT_STANDARD");
        checkoutService.setMaxRedemptionPercentage(null); // DEC-10.7A: no cap configured
        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(productDetail);
        when(identityCommerceFacade.getUserSummary(200L)).thenReturn(Optional.of(sellerSummary));
        when(identityCommerceFacade.getAddressById(300L, 501L)).thenReturn(Optional.of(addressDto));
        when(identityCommerceFacade.getDefaultAddress(200L)).thenReturn(Optional.of(sellerAddressDto));
        when(systemFeePolicyRepository.findFirstByStatusOrderByEffectiveFromDesc("ACTIVE")).thenReturn(Optional.of(feePolicy));

        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 50000L, "CMD-FAIL-NO-CAP");

        assertThatThrownBy(() -> checkoutService.buyNow(300L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REWARD_MAX_REDEMPTION_PERCENTAGE")
                .hasMessageContaining("DEC-10.7A");
    }

    @Test
    @DisplayName("DEC-10.7A: fails closed when REWARD_MAX_REDEMPTION_PERCENTAGE is a non-numeric string")
    void buyNow_failsClosedWhenCapIsNonNumeric() {
        checkoutService.setCanonicalRewardPolicyCode("REWARD_CHECKOUT_STANDARD");
        checkoutService.setMaxRedemptionPercentage("not-a-number");
        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(productDetail);
        when(identityCommerceFacade.getUserSummary(200L)).thenReturn(Optional.of(sellerSummary));
        when(identityCommerceFacade.getAddressById(300L, 501L)).thenReturn(Optional.of(addressDto));
        when(identityCommerceFacade.getDefaultAddress(200L)).thenReturn(Optional.of(sellerAddressDto));
        when(systemFeePolicyRepository.findFirstByStatusOrderByEffectiveFromDesc("ACTIVE")).thenReturn(Optional.of(feePolicy));

        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 50000L, "CMD-FAIL-BAD-CAP");

        assertThatThrownBy(() -> checkoutService.buyNow(300L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a valid number")
                .hasMessageContaining("DEC-10.7A");
    }

    @Test
    @DisplayName("DEC-10.7A: fails closed when REWARD_MAX_REDEMPTION_PERCENTAGE is zero or negative")
    void buyNow_failsClosedWhenCapIsZeroOrNegative() {
        checkoutService.setCanonicalRewardPolicyCode("REWARD_CHECKOUT_STANDARD");
        checkoutService.setMaxRedemptionPercentage("0");
        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(productDetail);
        when(identityCommerceFacade.getUserSummary(200L)).thenReturn(Optional.of(sellerSummary));
        when(identityCommerceFacade.getAddressById(300L, 501L)).thenReturn(Optional.of(addressDto));
        when(identityCommerceFacade.getDefaultAddress(200L)).thenReturn(Optional.of(sellerAddressDto));
        when(systemFeePolicyRepository.findFirstByStatusOrderByEffectiveFromDesc("ACTIVE")).thenReturn(Optional.of(feePolicy));

        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 50000L, "CMD-FAIL-ZERO-CAP");

        assertThatThrownBy(() -> checkoutService.buyNow(300L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be in range (0, 100]")
                .hasMessageContaining("DEC-10.7A");
    }

    @Test
    @DisplayName("GAP-1 / DEC-10.7A: Coin checkout is BLOCKED when PO decision is OPEN even if DevOps configures 100%")
    void buyNow_failsClosedWhenPoDecisionIsOpen_evenIfDevOpsConfigures100Percent() {
        checkoutService.setCanonicalRewardPolicyCode("REWARD_CHECKOUT_STANDARD");
        checkoutService.setMaxRedemptionPercentage("100"); // DevOps sets 100
        checkoutService.setCapPoApproved(false); // PO decision remains OPEN (default)

        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(productDetail);
        when(identityCommerceFacade.getUserSummary(200L)).thenReturn(Optional.of(sellerSummary));
        when(identityCommerceFacade.getAddressById(300L, 501L)).thenReturn(Optional.of(addressDto));
        when(identityCommerceFacade.getDefaultAddress(200L)).thenReturn(Optional.of(sellerAddressDto));
        when(systemFeePolicyRepository.findFirstByStatusOrderByEffectiveFromDesc("ACTIVE")).thenReturn(Optional.of(feePolicy));

        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 50000L, "CMD-FAIL-DEVOPS-100");

        assertThatThrownBy(() -> checkoutService.buyNow(300L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PO decision on coin redemption cap (max_redemption_percentage) is currently OPEN")
                .hasMessageContaining("PO Approval Gate")
                .hasMessageContaining("DEC-10.7A");

        // Verify: No order is saved and no ledger debit is written
        verify(orderRepository, never()).save(any());
        verify(orderRepository, never()).saveAndFlush(any());
        verify(jdbcTemplate, never()).update(contains("INSERT INTO og_compat.reward_ledger"), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("GAP-1 / DEC-10.7A: Coin checkout is BLOCKED when PO decision is OPEN even if DevOps configures 50%")
    void buyNow_failsClosedWhenPoDecisionIsOpen_evenIfDevOpsConfigures50Percent() {
        checkoutService.setCanonicalRewardPolicyCode("REWARD_CHECKOUT_STANDARD");
        checkoutService.setMaxRedemptionPercentage("50"); // DevOps sets 50
        checkoutService.setCapPoApproved(false); // PO decision remains OPEN (default)

        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(productDetail);
        when(identityCommerceFacade.getUserSummary(200L)).thenReturn(Optional.of(sellerSummary));
        when(identityCommerceFacade.getAddressById(300L, 501L)).thenReturn(Optional.of(addressDto));
        when(identityCommerceFacade.getDefaultAddress(200L)).thenReturn(Optional.of(sellerAddressDto));
        when(systemFeePolicyRepository.findFirstByStatusOrderByEffectiveFromDesc("ACTIVE")).thenReturn(Optional.of(feePolicy));

        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 50000L, "CMD-FAIL-DEVOPS-50");

        assertThatThrownBy(() -> checkoutService.buyNow(300L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PO decision on coin redemption cap (max_redemption_percentage) is currently OPEN")
                .hasMessageContaining("PO Approval Gate");

        // Verify: No order is saved and no ledger debit is written
        verify(orderRepository, never()).save(any());
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("GAP-1 / DEC-10.7A: Coin checkout is BLOCKED when PO decision is OPEN even with valid Spring property")
    void buyNow_failsClosedWhenPoDecisionIsOpen_evenWithValidSpringProperty() {
        checkoutService.setCanonicalRewardPolicyCode("REWARD_CHECKOUT_STANDARD");
        checkoutService.setMaxRedemptionPercentage("25");
        // default capPoApproved is false

        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(productDetail);
        when(identityCommerceFacade.getUserSummary(200L)).thenReturn(Optional.of(sellerSummary));
        when(identityCommerceFacade.getAddressById(300L, 501L)).thenReturn(Optional.of(addressDto));
        when(identityCommerceFacade.getDefaultAddress(200L)).thenReturn(Optional.of(sellerAddressDto));
        when(systemFeePolicyRepository.findFirstByStatusOrderByEffectiveFromDesc("ACTIVE")).thenReturn(Optional.of(feePolicy));

        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 25000L, "CMD-FAIL-PROP-OPEN");

        assertThatThrownBy(() -> checkoutService.buyNow(300L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PO decision on coin redemption cap (max_redemption_percentage) is currently OPEN")
                .hasMessageContaining("PO Approval Gate");
    }

    @Test
    @DisplayName("GAP-1 / DEC-10.7A: GroupCheckoutService resolveMaxRedemptionPercentage fails closed when PO decision is OPEN")
    void groupCheckout_resolveMaxRedemptionPercentage_failsClosedWhenPoDecisionIsOpen() {
        GroupCheckoutService groupService = new GroupCheckoutService(
                null, null, null, null, null, null, null, null, Clock.systemUTC(), new com.fasterxml.jackson.databind.ObjectMapper()
        );
        groupService.setConfiguredMaxRedemptionPercentage("50");
        groupService.setCapPoApproved(false); // default OPEN

        assertThatThrownBy(groupService::resolveMaxRedemptionPercentage)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PO decision on coin redemption cap (max_redemption_percentage) is currently OPEN")
                .hasMessageContaining("PO Approval Gate");

        // Once approved, resolves the configured percentage
        groupService.setCapPoApproved(true);
        assertThat(groupService.resolveMaxRedemptionPercentage()).isEqualByComparingTo(new BigDecimal("50"));
    }

    @Test
    @DisplayName("DEC-10.7A: checkout WITHOUT Coin is unaffected by missing cap configuration")
    void buyNow_withoutCoins_succeedsEvenWhenCapNotConfigured() {
        // cap not configured — but no coins requested → must succeed
        checkoutService.setCanonicalRewardPolicyCode("REWARD_CHECKOUT_STANDARD");
        checkoutService.setMaxRedemptionPercentage(null); // DEC-10.7A: cap absent, but coins = 0
        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(productDetail);
        when(identityCommerceFacade.getUserSummary(200L)).thenReturn(Optional.of(sellerSummary));
        when(identityCommerceFacade.getAddressById(300L, 501L)).thenReturn(Optional.of(addressDto));
        when(identityCommerceFacade.getDefaultAddress(200L)).thenReturn(Optional.of(sellerAddressDto));
        when(systemFeePolicyRepository.findFirstByStatusOrderByEffectiveFromDesc("ACTIVE")).thenReturn(Optional.of(feePolicy));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity saved = invocation.getArgument(0);
            setField(saved, "id", 9910L);
            return saved;
        });
        when(orderItemRepository.saveAndFlush(any(OrderItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // coinsToRedeem = 0 → cap not checked → should succeed
        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 0L, "CMD-NO-COIN-NO-CAP");

        CheckoutDtos.OrderCreatedResponse response = checkoutService.buyNow(300L, request);
        assertThat(response).isNotNull();
        assertThat(response.pointsDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("PO-DEC Canonical Policy: fails closed when REWARD_CANONICAL_POLICY_CODE is not configured")
    void buyNow_failsClosedWhenPolicyCodeNotConfigured() {
        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(productDetail);
        when(identityCommerceFacade.getUserSummary(200L)).thenReturn(Optional.of(sellerSummary));
        when(identityCommerceFacade.getAddressById(300L, 501L)).thenReturn(Optional.of(addressDto));
        when(identityCommerceFacade.getDefaultAddress(200L)).thenReturn(Optional.of(sellerAddressDto));
        when(systemFeePolicyRepository.findFirstByStatusOrderByEffectiveFromDesc("ACTIVE")).thenReturn(Optional.of(feePolicy));
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(balance, 0) FROM og_compat.reward_accounts"), eq(Long.class), eq(300L))).thenReturn(100000L);
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity saved = invocation.getArgument(0);
            setField(saved, "id", 9905L);
            return saved;
        });
        when(orderItemRepository.saveAndFlush(any(OrderItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // cap configured so we reach policy code check; policy code not configured
        checkoutService.setMaxRedemptionPercentage("50");
        checkoutService.setCapPoApproved(true);
        checkoutService.setCanonicalRewardPolicyCode(null);

        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 50000L, "CMD-FAIL-NO-CONFIG");

        assertThatThrownBy(() -> checkoutService.buyNow(300L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing explicit configuration for canonical reward policy code");
    }

    @Test
    @DisplayName("PO-DEC Canonical Policy: fails closed when canonical policy is not found in reward_policies")
    void buyNow_failsClosedWhenCanonicalPolicyNotFound() {
        checkoutService.setCanonicalRewardPolicyCode("REWARD_CHECKOUT_STANDARD");
        checkoutService.setMaxRedemptionPercentage("50"); // DEC-10.7A: cap must be configured to reach policy lookup
        checkoutService.setCapPoApproved(true);
        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(productDetail);
        when(identityCommerceFacade.getUserSummary(200L)).thenReturn(Optional.of(sellerSummary));
        when(identityCommerceFacade.getAddressById(300L, 501L)).thenReturn(Optional.of(addressDto));
        when(identityCommerceFacade.getDefaultAddress(200L)).thenReturn(Optional.of(sellerAddressDto));
        when(systemFeePolicyRepository.findFirstByStatusOrderByEffectiveFromDesc("ACTIVE")).thenReturn(Optional.of(feePolicy));
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(balance, 0) FROM og_compat.reward_accounts"), eq(Long.class), eq(300L))).thenReturn(100000L);
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity saved = invocation.getArgument(0);
            setField(saved, "id", 9903L);
            return saved;
        });
        when(orderItemRepository.saveAndFlush(any(OrderItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Returns empty list -> policy not found
        when(jdbcTemplate.queryForList(contains("SELECT policy_id FROM og_compat.reward_policies"), eq(Long.class), eq("REWARD_CHECKOUT_STANDARD")))
                .thenReturn(List.of());

        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 50000L, "CMD-FAIL-NO-POLICY");

        assertThatThrownBy(() -> checkoutService.buyNow(300L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Canonical reward policy not found for policy_code: REWARD_CHECKOUT_STANDARD");
    }

    @Test
    @DisplayName("PO-DEC Canonical Policy: fails closed when ambiguous multiple policies found with same code")
    void buyNow_failsClosedWhenMultiplePoliciesFound() {
        checkoutService.setCanonicalRewardPolicyCode("REWARD_CHECKOUT_STANDARD");
        checkoutService.setMaxRedemptionPercentage("50"); // DEC-10.7A: cap must be configured to reach policy lookup
        checkoutService.setCapPoApproved(true);
        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(productDetail);
        when(identityCommerceFacade.getUserSummary(200L)).thenReturn(Optional.of(sellerSummary));
        when(identityCommerceFacade.getAddressById(300L, 501L)).thenReturn(Optional.of(addressDto));
        when(identityCommerceFacade.getDefaultAddress(200L)).thenReturn(Optional.of(sellerAddressDto));
        when(systemFeePolicyRepository.findFirstByStatusOrderByEffectiveFromDesc("ACTIVE")).thenReturn(Optional.of(feePolicy));
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(balance, 0) FROM og_compat.reward_accounts"), eq(Long.class), eq(300L))).thenReturn(100000L);
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity saved = invocation.getArgument(0);
            setField(saved, "id", 9904L);
            return saved;
        });
        when(orderItemRepository.saveAndFlush(any(OrderItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Returns 2 policies -> ambiguous
        when(jdbcTemplate.queryForList(contains("SELECT policy_id FROM og_compat.reward_policies"), eq(Long.class), eq("REWARD_CHECKOUT_STANDARD")))
                .thenReturn(List.of(100L, 101L));

        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 50000L, "CMD-FAIL-AMBIGUOUS-POLICY");

        assertThatThrownBy(() -> checkoutService.buyNow(300L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ambiguous multiple reward policies found for canonical code: REWARD_CHECKOUT_STANDARD");
    }

    @Test
    void buyNow_failsWhenNegativeCoinsRequested() {
        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(productDetail);
        when(identityCommerceFacade.getUserSummary(200L)).thenReturn(Optional.of(sellerSummary));
        when(identityCommerceFacade.getAddressById(300L, 501L)).thenReturn(Optional.of(addressDto));
        when(identityCommerceFacade.getDefaultAddress(200L)).thenReturn(Optional.of(sellerAddressDto));
        when(systemFeePolicyRepository.findFirstByStatusOrderByEffectiveFromDesc("ACTIVE")).thenReturn(Optional.of(feePolicy));

        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, -100L, "CMD-UNIT-TEST");

        assertThatThrownBy(() -> checkoutService.buyNow(300L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Số xu sử dụng không hợp lệ");
    }

    @Test
    void buyNow_failsWhenCoinsExceedGoodsTotal() {
        checkoutService.setMaxRedemptionPercentage("100"); // DEC-10.7A: 100% cap so coin limit is goods value, not cap
        checkoutService.setCapPoApproved(true);
        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(productDetail);
        when(identityCommerceFacade.getUserSummary(200L)).thenReturn(Optional.of(sellerSummary));
        when(identityCommerceFacade.getAddressById(300L, 501L)).thenReturn(Optional.of(addressDto));
        when(identityCommerceFacade.getDefaultAddress(200L)).thenReturn(Optional.of(sellerAddressDto));
        when(systemFeePolicyRepository.findFirstByStatusOrderByEffectiveFromDesc("ACTIVE")).thenReturn(Optional.of(feePolicy));
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(balance, 0) FROM og_compat.reward_accounts"), eq(Long.class), eq(300L))).thenReturn(10000000L);

        // Subtotal = 4,500,000; trying to redeem 5,000,000 coins (> subtotal even at 100% cap)
        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 5000000L, "CMD-UNIT-TEST");

        assertThatThrownBy(() -> checkoutService.buyNow(300L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vượt quá giá trị thanh toán cho hàng hóa");
    }

    @Test
    void getCheckoutPreview_includesCoinBalanceFields() {
        when(catalogCommerceFacade.getActiveProduct(1001L)).thenReturn(Optional.of(productSummary));
        when(identityCommerceFacade.getUserSummary(200L)).thenReturn(Optional.of(sellerSummary));
        when(identityCommerceFacade.getUserAddresses(300L)).thenReturn(List.of(addressDto));

        CheckoutDtos.CheckoutPreviewResponse preview = checkoutService.getCheckoutPreview(300L, 1001L, null);

        assertThat(preview).isNotNull();
        assertThat(preview.buyerCoinBalance()).isNotNull();
        assertThat(preview.maxRedeemableCoins()).isNotNull();
        assertThat(preview.coinDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("DEC-10.7A: Cap 50% — rejects coins requested exceeding 50% of goods value (post-voucher)")
    void buyNow_withCap50Percent_rejectsCoinsExceeding50Percent() {
        checkoutService.setCanonicalRewardPolicyCode("REWARD_CHECKOUT_STANDARD");
        checkoutService.setMaxRedemptionPercentage("50");
        checkoutService.setCapPoApproved(true);
        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(productDetail);
        when(identityCommerceFacade.getUserSummary(200L)).thenReturn(Optional.of(sellerSummary));
        when(identityCommerceFacade.getAddressById(300L, 501L)).thenReturn(Optional.of(addressDto));
        when(identityCommerceFacade.getDefaultAddress(200L)).thenReturn(Optional.of(sellerAddressDto));
        when(systemFeePolicyRepository.findFirstByStatusOrderByEffectiveFromDesc("ACTIVE")).thenReturn(Optional.of(feePolicy));
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(balance, 0) FROM og_compat.reward_accounts"), eq(Long.class), eq(300L))).thenReturn(5000000L);

        // Subtotal = 4,500,000; 50% cap = 2,250,000 coins. Requesting 2,250,001 -> exceeds by 1 coin
        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 2250001L, "CMD-50-EXCEED");

        assertThatThrownBy(() -> checkoutService.buyNow(300L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vượt quá giá trị thanh toán cho hàng hóa (2250000 xu)");
    }

    @Test
    @DisplayName("DEC-10.7A: Cap 50% — accepts coins requested exactly at the 50% limit")
    void buyNow_withCap50Percent_acceptsCoinsAtExact50PercentLimit() {
        checkoutService.setCanonicalRewardPolicyCode("REWARD_CHECKOUT_STANDARD");
        checkoutService.setMaxRedemptionPercentage("50");
        checkoutService.setCapPoApproved(true);
        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(productDetail);
        when(identityCommerceFacade.getUserSummary(200L)).thenReturn(Optional.of(sellerSummary));
        when(identityCommerceFacade.getAddressById(300L, 501L)).thenReturn(Optional.of(addressDto));
        when(identityCommerceFacade.getDefaultAddress(200L)).thenReturn(Optional.of(sellerAddressDto));
        when(systemFeePolicyRepository.findFirstByStatusOrderByEffectiveFromDesc("ACTIVE")).thenReturn(Optional.of(feePolicy));
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(balance, 0) FROM og_compat.reward_accounts"), eq(Long.class), eq(300L))).thenReturn(5000000L);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_accounts WHERE user_id = ?"), eq(Long.class), eq(300L))).thenReturn(1L);
        when(jdbcTemplate.queryForList(contains("SELECT policy_id FROM og_compat.reward_policies"), eq(Long.class), eq("REWARD_CHECKOUT_STANDARD"))).thenReturn(List.of(100L));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity saved = invocation.getArgument(0);
            setField(saved, "id", 9920L);
            return saved;
        });
        when(orderItemRepository.saveAndFlush(any(OrderItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Subtotal = 4,500,000, Shipping = 30,000; 50% cap = 2,250,000 coins. Requesting exact 2,250,000
        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 2250000L, "CMD-50-EXACT");
        CheckoutDtos.OrderCreatedResponse response = checkoutService.buyNow(300L, request);

        assertThat(response).isNotNull();
        assertThat(response.pointsDiscountAmount()).isEqualByComparingTo(new BigDecimal("2250000"));
        // Total = 4,500,000 (subtotal) + 30,000 (shipping) - 2,250,000 (coins) = 2,280,000
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("2280000"));
    }

    @Test
    @DisplayName("DEC-10.7A: Cap 100% — allows up to 100% of goods value and rejects 1 coin above")
    void buyNow_withCap100Percent_acceptsExact100PercentAndRejectsAbove() {
        checkoutService.setCanonicalRewardPolicyCode("REWARD_CHECKOUT_STANDARD");
        checkoutService.setMaxRedemptionPercentage("100");
        checkoutService.setCapPoApproved(true);
        when(catalogCommerceFacade.getProductForCheckoutLock(1001L)).thenReturn(productDetail);
        when(identityCommerceFacade.getUserSummary(200L)).thenReturn(Optional.of(sellerSummary));
        when(identityCommerceFacade.getAddressById(300L, 501L)).thenReturn(Optional.of(addressDto));
        when(identityCommerceFacade.getDefaultAddress(200L)).thenReturn(Optional.of(sellerAddressDto));
        when(systemFeePolicyRepository.findFirstByStatusOrderByEffectiveFromDesc("ACTIVE")).thenReturn(Optional.of(feePolicy));
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(balance, 0) FROM og_compat.reward_accounts"), eq(Long.class), eq(300L))).thenReturn(10000000L);
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM og_compat.reward_accounts WHERE user_id = ?"), eq(Long.class), eq(300L))).thenReturn(1L);
        when(jdbcTemplate.queryForList(contains("SELECT policy_id FROM og_compat.reward_policies"), eq(Long.class), eq("REWARD_CHECKOUT_STANDARD"))).thenReturn(List.of(100L));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity saved = invocation.getArgument(0);
            setField(saved, "id", 9921L);
            return saved;
        });
        when(orderItemRepository.saveAndFlush(any(OrderItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 1. Exactly 100% of goods value: 4,500,000 coins
        CheckoutDtos.BuyNowRequest exactRequest = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 4500000L, "CMD-100-EXACT");
        CheckoutDtos.OrderCreatedResponse exactResponse = checkoutService.buyNow(300L, exactRequest);
        assertThat(exactResponse).isNotNull();
        assertThat(exactResponse.pointsDiscountAmount()).isEqualByComparingTo(new BigDecimal("4500000"));
        // Total = 4,500,000 + 30,000 (shipping) - 4,500,000 = 30,000 (shipping fee preserved!)
        assertThat(exactResponse.totalAmount()).isEqualByComparingTo(new BigDecimal("30000"));

        // 2. 1 coin above 100%: 4,500,001 coins -> rejects
        CheckoutDtos.BuyNowRequest overRequest = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 4500001L, "CMD-100-OVER");
        assertThatThrownBy(() -> checkoutService.buyNow(300L, overRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vượt quá giá trị thanh toán cho hàng hóa (4500000 xu)");
    }

    @Test
    @DisplayName("DEC-10.7A: getCheckoutPreview reflects configured cap percentage accurately")
    void getCheckoutPreview_withCap50Percent_capsMaxRedeemableCoinsAt50Percent() {
        checkoutService.setMaxRedemptionPercentage("50");
        checkoutService.setCapPoApproved(true);
        when(catalogCommerceFacade.getActiveProduct(1001L)).thenReturn(Optional.of(productSummary));
        when(identityCommerceFacade.getUserSummary(200L)).thenReturn(Optional.of(sellerSummary));
        when(identityCommerceFacade.getUserAddresses(300L)).thenReturn(List.of(addressDto));
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(balance, 0) FROM og_compat.reward_accounts"), eq(Long.class), eq(300L))).thenReturn(5000000L);

        // Product = 4,500,000, buyer balance = 5,000,000, Cap = 50% -> max = 2,250,000 coins
        CheckoutDtos.CheckoutPreviewResponse preview = checkoutService.getCheckoutPreview(300L, 1001L, null);

        assertThat(preview.buyerCoinBalance()).isEqualTo(5000000L);
        assertThat(preview.maxRedeemableCoins()).isEqualTo(2250000L);
    }

    @Test
    @DisplayName("DEC-10.7A: getCheckoutPreview sets maxRedeemableCoins to 0 when cap is not configured")
    void getCheckoutPreview_withoutCapConfigured_setsMaxRedeemableCoinsToZero() {
        checkoutService.setMaxRedemptionPercentage(null);
        when(catalogCommerceFacade.getActiveProduct(1001L)).thenReturn(Optional.of(productSummary));
        when(identityCommerceFacade.getUserSummary(200L)).thenReturn(Optional.of(sellerSummary));
        when(identityCommerceFacade.getUserAddresses(300L)).thenReturn(List.of(addressDto));
        when(jdbcTemplate.queryForObject(contains("SELECT COALESCE(balance, 0) FROM og_compat.reward_accounts"), eq(Long.class), eq(300L))).thenReturn(5000000L);

        CheckoutDtos.CheckoutPreviewResponse preview = checkoutService.getCheckoutPreview(300L, 1001L, null);

        assertThat(preview.buyerCoinBalance()).isEqualTo(5000000L);
        assertThat(preview.maxRedeemableCoins()).isEqualTo(0L);
    }

    @Test
    @DisplayName("PO-DEC-06 & F-03: buyNow with identical commandKey returns idempotent replay")
    void buyNow_withCommandKey_returnsIdempotentReplay() throws Exception {
        String cmdKey = "CMD-CHECKOUT-TEST-1";
        Long orderId = 9902L;
        Long buyerId = 300L;
        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 50000L, cmdKey);

        UUID existingGroupId = UUID.nameUUIDFromBytes(("CHECKOUT:" + buyerId + ":" + cmdKey).getBytes(StandardCharsets.UTF_8));
        String requestHash = ClientCheckoutFingerprint.of(buyerId, request).sha256Hex();

        when(jdbcTemplate.query(contains("INSERT INTO public.checkout_idempotency_entries"), any(RowMapper.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("SELECT request_hash, checkout_group_id"), eq(buyerId), eq(cmdKey)))
                .thenReturn(List.of(Map.of("request_hash", requestHash, "checkout_group_id", existingGroupId)));
        when(jdbcTemplate.query(contains("SELECT order_id FROM og_compat.orders"), any(RowMapper.class), eq(existingGroupId), eq(buyerId)))
                .thenReturn(List.of(orderId));

        OrderEntity mockOrder = mock(OrderEntity.class);
        when(mockOrder.getId()).thenReturn(orderId);
        when(mockOrder.getCheckoutGroupId()).thenReturn(existingGroupId);
        when(mockOrder.getBuyerId()).thenReturn(buyerId);
        when(mockOrder.getSubtotal()).thenReturn(new BigDecimal("4500000"));
        when(mockOrder.getShippingFee()).thenReturn(new BigDecimal("30000"));
        when(mockOrder.getBuyerSystemFee()).thenReturn(BigDecimal.ZERO);
        when(mockOrder.getVoucherDiscountAmount()).thenReturn(BigDecimal.ZERO);
        when(mockOrder.getShippingDiscountAmount()).thenReturn(BigDecimal.ZERO);
        when(mockOrder.getPointsDiscountAmount()).thenReturn(new BigDecimal("50000"));
        when(mockOrder.getTotalAmount()).thenReturn(new BigDecimal("4480000"));
        when(mockOrder.getStatus()).thenReturn("PAYMENT_PENDING");
        when(mockOrder.getShippingRecipientName()).thenReturn("Nguyen Van A");
        when(mockOrder.getShippingPhoneNumber()).thenReturn("0901234567");
        when(mockOrder.getShippingDetailAddress()).thenReturn("123 Le Loi");
        when(mockOrder.getShippingWard()).thenReturn("Phuong Ben Nghe");
        when(mockOrder.getShippingDistrict()).thenReturn("Quan 1");
        when(mockOrder.getShippingProvince()).thenReturn("TP Ho Chi Minh");
        when(mockOrder.getPaymentDueAt()).thenReturn(Instant.now().plus(Duration.ofHours(1)));
        when(mockOrder.getCreatedAt()).thenReturn(Instant.now());
        when(orderRepository.findByIdAndBuyerId(orderId, buyerId)).thenReturn(Optional.of(mockOrder));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());

        CheckoutDtos.BuyNowExecutionResult executionResult = checkoutService.executeBuyNow(buyerId, request);

        assertThat(executionResult).isNotNull();
        assertThat(executionResult.replayed()).isTrue();
        assertThat(executionResult.response().orderId()).isEqualTo(orderId);
        // Verify product lock was never requested because idempotent check short-circuits
        verify(catalogCommerceFacade, never()).getProductForCheckoutLock(anyLong());
    }

    @Test
    @DisplayName("PO-DEC-06 & F-03: buyNow with same commandKey but different payload throws 409 COMMAND_KEY_CONFLICT")
    void buyNow_withCommandKey_throwsConflictOnDifferentPayload() {
        String cmdKey = "CMD-CHECKOUT-TEST-2";
        Long buyerId = 300L;
        CheckoutDtos.BuyNowRequest originalRequest = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 50000L, cmdKey);

        UUID existingGroupId = UUID.nameUUIDFromBytes(("CHECKOUT:" + buyerId + ":" + cmdKey).getBytes(StandardCharsets.UTF_8));
        String originalHash = ClientCheckoutFingerprint.of(buyerId, originalRequest).sha256Hex();

        when(jdbcTemplate.query(contains("INSERT INTO public.checkout_idempotency_entries"), any(RowMapper.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("SELECT request_hash, checkout_group_id"), eq(buyerId), eq(cmdKey)))
                .thenReturn(List.of(Map.of("request_hash", originalHash, "checkout_group_id", existingGroupId)));

        // 1. Attempt submit with 20,000 coins instead of 50,000 coins -> conflict
        CheckoutDtos.BuyNowRequest diffCoinsRequest = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 20000L, cmdKey);
        assertThatThrownBy(() -> checkoutService.buyNow(buyerId, diffCoinsRequest))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("COMMAND_KEY_CONFLICT");

        // 2. Attempt submit with different productId -> conflict
        CheckoutDtos.BuyNowRequest diffProductRequest = new CheckoutDtos.BuyNowRequest(9999L, 501L, null, null, null, 50000L, cmdKey);
        assertThatThrownBy(() -> checkoutService.buyNow(buyerId, diffProductRequest))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("COMMAND_KEY_CONFLICT");

        // 3. Attempt submit with different addressId -> conflict
        CheckoutDtos.BuyNowRequest diffAddressRequest = new CheckoutDtos.BuyNowRequest(1001L, 777L, null, null, null, 50000L, cmdKey);
        assertThatThrownBy(() -> checkoutService.buyNow(buyerId, diffAddressRequest))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("COMMAND_KEY_CONFLICT");

        // 4. Attempt submit with different offerId -> conflict
        CheckoutDtos.BuyNowRequest diffOfferRequest = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, 999L, 50000L, cmdKey);
        assertThatThrownBy(() -> checkoutService.buyNow(buyerId, diffOfferRequest))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("COMMAND_KEY_CONFLICT");

        // 5. Attempt submit with different voucherCode -> conflict
        CheckoutDtos.BuyNowRequest diffVoucherRequest = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, "SUMMER50", null, 50000L, cmdKey);
        assertThatThrownBy(() -> checkoutService.buyNow(buyerId, diffVoucherRequest))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("COMMAND_KEY_CONFLICT");
    }

    @Test
    @DisplayName("PO-DEC-06 & D-08: server-derived fee changes do not cause false payload conflict on retry")
    void buyNow_serverDerivedFeesChanged_doesNotCreateFalsePayloadConflict() {
        String cmdKey = "CMD-CHECKOUT-TEST-FEES";
        Long orderId = 9904L;
        Long buyerId = 300L;
        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 50000L, cmdKey);

        UUID existingGroupId = UUID.nameUUIDFromBytes(("CHECKOUT:" + buyerId + ":" + cmdKey).getBytes(StandardCharsets.UTF_8));
        String requestHash = ClientCheckoutFingerprint.of(buyerId, request).sha256Hex();

        when(jdbcTemplate.query(contains("INSERT INTO public.checkout_idempotency_entries"), any(RowMapper.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("SELECT request_hash, checkout_group_id"), eq(buyerId), eq(cmdKey)))
                .thenReturn(List.of(Map.of("request_hash", requestHash, "checkout_group_id", existingGroupId)));
        when(jdbcTemplate.query(contains("SELECT order_id FROM og_compat.orders"), any(RowMapper.class), eq(existingGroupId), eq(buyerId)))
                .thenReturn(List.of(orderId));

        OrderEntity mockOrder = mock(OrderEntity.class);
        when(mockOrder.getId()).thenReturn(orderId);
        when(mockOrder.getCheckoutGroupId()).thenReturn(existingGroupId);
        when(mockOrder.getBuyerId()).thenReturn(buyerId);
        when(mockOrder.getSubtotal()).thenReturn(new BigDecimal("4500000"));
        when(mockOrder.getShippingFee()).thenReturn(new BigDecimal("30000"));
        when(mockOrder.getBuyerSystemFee()).thenReturn(BigDecimal.ZERO);
        when(mockOrder.getVoucherDiscountAmount()).thenReturn(BigDecimal.ZERO);
        when(mockOrder.getShippingDiscountAmount()).thenReturn(BigDecimal.ZERO);
        when(mockOrder.getPointsDiscountAmount()).thenReturn(new BigDecimal("50000"));
        when(mockOrder.getTotalAmount()).thenReturn(new BigDecimal("4480000"));
        when(mockOrder.getStatus()).thenReturn("PAYMENT_PENDING");
        when(mockOrder.getShippingRecipientName()).thenReturn("Nguyen Van A");
        when(mockOrder.getShippingPhoneNumber()).thenReturn("0901234567");
        when(mockOrder.getShippingDetailAddress()).thenReturn("123 Le Loi");
        when(mockOrder.getShippingWard()).thenReturn("Phuong Ben Nghe");
        when(mockOrder.getShippingDistrict()).thenReturn("Quan 1");
        when(mockOrder.getShippingProvince()).thenReturn("TP Ho Chi Minh");
        when(mockOrder.getPaymentDueAt()).thenReturn(Instant.now().plus(Duration.ofHours(1)));
        when(mockOrder.getCreatedAt()).thenReturn(Instant.now());
        when(orderRepository.findByIdAndBuyerId(orderId, buyerId)).thenReturn(Optional.of(mockOrder));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());

        CheckoutDtos.BuyNowExecutionResult executionResult = checkoutService.executeBuyNow(buyerId, request);

        assertThat(executionResult).isNotNull();
        assertThat(executionResult.replayed()).isTrue();
        assertThat(executionResult.response().orderId()).isEqualTo(orderId);
        verify(catalogCommerceFacade, never()).getProductForCheckoutLock(anyLong());
    }

    @Test
    @DisplayName("Task 1: buyNow fails with 400 Bad Request when commandKey is null")
    void buyNow_failsWhenCommandKeyMissing() {
        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 0L, null);

        assertThatThrownBy(() -> checkoutService.buyNow(300L, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).contains("Mã thao tác (commandKey) là bắt buộc");
                });
    }

    @Test
    @DisplayName("Task 1: buyNow fails with 400 Bad Request when commandKey is blank")
    void buyNow_failsWhenCommandKeyBlank() {
        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 0L, "   ");

        assertThatThrownBy(() -> checkoutService.buyNow(300L, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).contains("Mã thao tác (commandKey) là bắt buộc");
                });
    }

    @Test
    @DisplayName("Task 1: buyNow fails with 400 Bad Request when commandKey exceeds 150 chars")
    void buyNow_failsWhenCommandKeyOversized() {
        String oversizedKey = "K".repeat(151);
        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 0L, oversizedKey);

        assertThatThrownBy(() -> checkoutService.buyNow(300L, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).contains("không được vượt quá 150 ký tự");
                });
    }

    @Test
    @DisplayName("PO-DEC-06 & D-08: normalizes null coins and empty voucher to match zero/null defaults")
    void buyNow_withNormalizedDefaultCoinsAndVoucher_returnsIdempotentReplay() {
        String cmdKey = "CMD-CHECKOUT-TEST-NORM";
        Long orderId = 9905L;
        Long buyerId = 300L;

        // Base request has 0 coins and null voucher
        CheckoutDtos.BuyNowRequest baseRequest = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, null, null, 0L, cmdKey);
        UUID existingGroupId = UUID.nameUUIDFromBytes(("CHECKOUT:" + buyerId + ":" + cmdKey).getBytes(StandardCharsets.UTF_8));
        String baseHash = ClientCheckoutFingerprint.of(buyerId, baseRequest).sha256Hex();

        when(jdbcTemplate.query(contains("INSERT INTO public.checkout_idempotency_entries"), any(RowMapper.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("SELECT request_hash, checkout_group_id"), eq(buyerId), eq(cmdKey)))
                .thenReturn(List.of(Map.of("request_hash", baseHash, "checkout_group_id", existingGroupId)));
        when(jdbcTemplate.query(contains("SELECT order_id FROM og_compat.orders"), any(RowMapper.class), eq(existingGroupId), eq(buyerId)))
                .thenReturn(List.of(orderId));

        OrderEntity mockOrder = mock(OrderEntity.class);
        when(mockOrder.getId()).thenReturn(orderId);
        when(mockOrder.getCheckoutGroupId()).thenReturn(existingGroupId);
        when(mockOrder.getBuyerId()).thenReturn(buyerId);
        when(mockOrder.getSubtotal()).thenReturn(new BigDecimal("4500000"));
        when(mockOrder.getShippingFee()).thenReturn(new BigDecimal("30000"));
        when(mockOrder.getBuyerSystemFee()).thenReturn(BigDecimal.ZERO);
        when(mockOrder.getVoucherDiscountAmount()).thenReturn(BigDecimal.ZERO);
        when(mockOrder.getShippingDiscountAmount()).thenReturn(BigDecimal.ZERO);
        when(mockOrder.getPointsDiscountAmount()).thenReturn(BigDecimal.ZERO);
        when(mockOrder.getTotalAmount()).thenReturn(new BigDecimal("4530000"));
        when(mockOrder.getStatus()).thenReturn("PAYMENT_PENDING");
        when(mockOrder.getShippingRecipientName()).thenReturn("Nguyen Van A");
        when(mockOrder.getShippingPhoneNumber()).thenReturn("0901234567");
        when(mockOrder.getShippingDetailAddress()).thenReturn("123 Le Loi");
        when(mockOrder.getShippingWard()).thenReturn("Phuong Ben Nghe");
        when(mockOrder.getShippingDistrict()).thenReturn("Quan 1");
        when(mockOrder.getShippingProvince()).thenReturn("TP Ho Chi Minh");
        when(mockOrder.getPaymentDueAt()).thenReturn(Instant.now().plus(Duration.ofHours(1)));
        when(mockOrder.getCreatedAt()).thenReturn(Instant.now());
        when(orderRepository.findByIdAndBuyerId(orderId, buyerId)).thenReturn(Optional.of(mockOrder));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());

        // Client request has coinsToRedeem = null, voucherCode = "   "
        CheckoutDtos.BuyNowRequest request = new CheckoutDtos.BuyNowRequest(1001L, 501L, null, "   ", null, null, cmdKey);
        CheckoutDtos.BuyNowExecutionResult executionResult = checkoutService.executeBuyNow(buyerId, request);

        assertThat(executionResult).isNotNull();
        assertThat(executionResult.replayed()).isTrue();
        assertThat(executionResult.response().orderId()).isEqualTo(orderId);
        verify(catalogCommerceFacade, never()).getProductForCheckoutLock(anyLong());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
