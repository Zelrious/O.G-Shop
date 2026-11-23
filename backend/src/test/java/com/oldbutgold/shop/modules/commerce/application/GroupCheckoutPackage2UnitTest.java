package com.oldbutgold.shop.modules.commerce.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductRepository;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductRevisionEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductRevisionRepository;
import com.oldbutgold.shop.modules.commerce.api.VoucherDtos;
import com.oldbutgold.shop.modules.commerce.application.GroupCheckoutDtos.GroupCheckoutItemRequest;
import com.oldbutgold.shop.modules.commerce.application.GroupCheckoutDtos.GroupCheckoutRequest;
import com.oldbutgold.shop.modules.commerce.application.GroupCheckoutDtos.ShippingAddressDto;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.CheckoutGroupEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.CheckoutGroupRepository;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.InventoryReservationEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.InventoryReservationRepository;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderItemEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderItemRepository;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.communication.application.NegotiatedCheckoutFacade;
import com.oldbutgold.shop.modules.identity.application.IdentityCommerceFacade;
import com.oldbutgold.shop.modules.payment.application.GroupPaymentService;
import com.oldbutgold.shop.modules.payment.application.VnPayService;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.OrderFundComponentRepository;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentAllocationRepository;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentAttemptRepository;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentConfirmationRepository;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentIntentRepository;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentIpnEventRepository;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentIpnRawReceiptRepository;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentReconciliationCaseRepository;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.RewardLedgerRepository;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.RewardPolicyEntity;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.RewardPolicyRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GroupCheckoutPackage2UnitTest - 0-VND fast-path, Voucher, and Negotiated Offer")
class GroupCheckoutPackage2UnitTest {

    private ProductRepository productRepository;
    private ProductRevisionRepository productRevisionRepository;
    private CheckoutGroupRepository checkoutGroupRepository;
    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private InventoryReservationRepository inventoryReservationRepository;
    private RewardPolicyRepository rewardPolicyRepository;
    private JdbcTemplate jdbcTemplate;
    private IdentityCommerceFacade identityCommerceFacade;
    private VoucherService voucherService;
    private NegotiatedCheckoutFacade negotiatedCheckoutFacade;
    private Clock fixedClock;
    private GroupCheckoutService service;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        productRevisionRepository = mock(ProductRevisionRepository.class);
        checkoutGroupRepository = mock(CheckoutGroupRepository.class);
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        inventoryReservationRepository = mock(InventoryReservationRepository.class);
        rewardPolicyRepository = mock(RewardPolicyRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        identityCommerceFacade = mock(IdentityCommerceFacade.class);
        voucherService = mock(VoucherService.class);
        negotiatedCheckoutFacade = mock(NegotiatedCheckoutFacade.class);
        fixedClock = Clock.fixed(Instant.parse("2026-11-23T14:00:00Z"), ZoneOffset.UTC);

        service = new GroupCheckoutService(
                productRepository,
                productRevisionRepository,
                checkoutGroupRepository,
                orderRepository,
                orderItemRepository,
                inventoryReservationRepository,
                rewardPolicyRepository,
                jdbcTemplate,
                fixedClock,
                new ObjectMapper(),
                identityCommerceFacade,
                voucherService,
                negotiatedCheckoutFacade
        );
        service.setConfiguredCanonicalPolicyCode("UC83_DEFAULT");
        service.setConfiguredMaxRedemptionPercentage("100");
        service.setCapPoApproved(true);

        when(checkoutGroupRepository.findByCommandKey(anyString())).thenReturn(Optional.empty());
        when(checkoutGroupRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.saveAndFlush(any())).thenAnswer(inv -> {
            OrderEntity o = inv.getArgument(0);
            if (o.getId() == null) {
                var f = OrderEntity.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(o, 1001L);
            }
            return o;
        });
        when(orderItemRepository.saveAndFlush(any())).thenAnswer(inv -> {
            OrderItemEntity oi = inv.getArgument(0);
            if (oi.getId() == null) {
                var f = OrderItemEntity.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(oi, 2001L);
            }
            return oi;
        });
        when(inventoryReservationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ProductEntity mockUc83Product(long productId, long sellerId, BigDecimal price) {
        ProductEntity p = mock(ProductEntity.class);
        when(p.getId()).thenReturn(productId);
        when(p.getSellerId()).thenReturn(sellerId);
        when(p.getTitle()).thenReturn("Vintage Camera");
        when(p.getStatus()).thenReturn(ProductEntity.STATUS_ACTIVE);
        when(p.getDeletedAt()).thenReturn(null);
        when(p.getWorkflowModel()).thenReturn("UC83");
        when(p.isRequiresBuyerEkyc()).thenReturn(false);
        when(p.getQuantityAvailable()).thenReturn(5);
        when(p.getPublicRevisionId()).thenReturn(99L);
        when(p.getReturnAllowed()).thenReturn(true);

        ProductRevisionEntity rev = mock(ProductRevisionEntity.class);
        when(rev.getRevisionId()).thenReturn(99L);
        when(rev.isReturnAllowed()).thenReturn(true);
        when(rev.getDeliveryOptions()).thenReturn("CARRIER,SELF_PICKUP,SELF_DELIVERY");
        when(rev.getListedUnitPrice()).thenReturn(price);

        when(productRepository.findById(productId)).thenReturn(Optional.of(p));
        when(productRevisionRepository.findById(99L)).thenReturn(Optional.of(rev));
        return p;
    }

    private ShippingAddressDto sampleAddress() {
        return new ShippingAddressDto("Buyer A", "0901234567", "HCM", "Q1", "Ben Nghe", "123 Le Loi");
    }

    @Test
    @DisplayName("2B: Negotiated offer price overrides listed price and links offer to order")
    void checkout_withAcceptedOffer_usesNegotiatedPriceAndLinksOrder() {
        mockUc83Product(10L, 20L, new BigDecimal("500000"));
        when(negotiatedCheckoutFacade.bind(eq(1L), eq(10L), eq(77L), anyString()))
                .thenReturn(new NegotiatedCheckoutFacade.Agreement(77L, new BigDecimal("350000"), BigDecimal.ZERO, BigDecimal.ZERO, 1L));
        when(orderRepository.findByCheckoutGroupId(any())).thenReturn(List.of());

        GroupCheckoutRequest req = new GroupCheckoutRequest(
                "CMD_OFFER_1",
                sampleAddress(),
                "SELF_PICKUP",
                null,
                "VNPAY",
                null,
                0L,
                List.of(new GroupCheckoutItemRequest(10L, 1, 77L)),
                null
        );

        var result = service.executeCheckout(1L, req);

        assertThat(result.response().expectedTotal()).isEqualByComparingTo(new BigDecimal("350000"));
        assertThat(result.response().state()).isEqualTo(CheckoutGroupEntity.STATE_READY);
        verify(negotiatedCheckoutFacade).requireNotBlocked(1L, 20L);
        verify(negotiatedCheckoutFacade).bind(eq(1L), eq(10L), eq(77L), anyString());
        verify(negotiatedCheckoutFacade).linkOrder(1L, 77L, 1001L);
    }

    @Test
    @DisplayName("2A + 2B: Shipping voucher + 100% coins on goods yields 0-VND fast-path (STATE_PAID, no VNPay)")
    void checkout_shippingVoucherAndFullCoins_finalizesZeroVndImmediatelyToPaid() {
        mockUc83Product(10L, 20L, new BigDecimal("100000"));

        RewardPolicyEntity policy = mock(RewardPolicyEntity.class);
        when(policy.getPolicyId()).thenReturn(1L);
        when(policy.getPointValue()).thenReturn(BigDecimal.ONE);
        when(rewardPolicyRepository.findByPolicyCode("UC83_DEFAULT")).thenReturn(Optional.of(policy));
        when(jdbcTemplate.queryForObject(contains("reward_accounts"), eq(Long.class), eq(1L))).thenReturn(200000L);

        Instant now = fixedClock.instant();
        VoucherDtos.Campaign shipCampaign = new VoucherDtos.Campaign(
                55L, 550L, 1L, "FREESHIP30K", "Freeship 30k", "Freeship 30k",
                "SHIPPING_DISCOUNT", "FIXED_AMOUNT", new BigDecimal("30000"), new BigDecimal("30000"),
                BigDecimal.ZERO, "CHECKOUT", List.of(), 100,
                now.minusSeconds(3600), now.plusSeconds(3600),
                true, true, 1L, 0L
        );
        VoucherDtos.Grant grant = new VoucherDtos.Grant(
                UUID.randomUUID(), 1L, "Buyer A", "AVAILABLE",
                now.minusSeconds(60), now.plusSeconds(3600), shipCampaign
        );
        when(voucherService.resolve(eq(1L), eq("FREESHIP30K"), eq(10L), eq(new BigDecimal("100000")), eq(true)))
                .thenReturn(grant);
        when(orderRepository.findByCheckoutGroupId(any())).thenReturn(List.of());

        GroupCheckoutRequest req = new GroupCheckoutRequest(
                "CMD_ZERO_VND_1",
                sampleAddress(),
                "CARRIER",
                "GHN",
                "VNPAY",
                "FREESHIP30K",
                100000L,
                List.of(new GroupCheckoutItemRequest(10L, 1, null)),
                null
        );

        var result = service.executeCheckout(1L, req);

        assertThat(result.response().expectedTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.response().state()).isEqualTo(CheckoutGroupEntity.STATE_PAID);
        verify(voucherService).bindOrder(1001L, grant);
        verify(jdbcTemplate).update(contains("INSERT INTO og_compat.checkout_redemptions"), any(), any(), eq(55L), eq(new BigDecimal("30000")), any(), anyString());
        verify(jdbcTemplate).update(contains("DEBIT_CHECKOUT"), any(), eq(1L), eq(1L), eq(-100000L), any(), anyString(), anyString(), any());
        verify(jdbcTemplate).update(contains("SET status = 'PAID_HELD'"), any(), any(), eq(1001L));
    }

    @Test
    @DisplayName("V58: Coins directly offset CARRIER shipping when no shipping voucher is applied")
    void checkout_coinsDirectlyOffsetCarrierShipping_reachesZeroVndWithoutVoucher() {
        mockUc83Product(10L, 20L, new BigDecimal("100000"));

        RewardPolicyEntity policy = mock(RewardPolicyEntity.class);
        when(policy.getPolicyId()).thenReturn(1L);
        when(policy.getPointValue()).thenReturn(BigDecimal.ONE);
        when(rewardPolicyRepository.findByPolicyCode("UC83_DEFAULT")).thenReturn(Optional.of(policy));
        when(jdbcTemplate.queryForObject(contains("reward_accounts"), eq(Long.class), eq(1L))).thenReturn(200000L);
        when(orderRepository.findByCheckoutGroupId(any())).thenReturn(List.of());

        GroupCheckoutRequest req = new GroupCheckoutRequest(
                "CMD_COIN_SHIP_1",
                sampleAddress(),
                "CARRIER",
                "GHN",
                "VNPAY",
                null,
                130000L, // 100k goods + 30k CARRIER shipping
                List.of(new GroupCheckoutItemRequest(10L, 1, null)),
                null
        );

        var result = service.executeCheckout(1L, req);

        assertThat(result.response().expectedTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.response().state()).isEqualTo(CheckoutGroupEntity.STATE_PAID);
        verify(jdbcTemplate).update(contains("PLATFORM_GOODS_SUBSIDY"), any(), eq(1001L), eq(new BigDecimal("100000")), any());
        verify(jdbcTemplate).update(contains("PLATFORM_SHIPPING_SUBSIDY"), any(), eq(1001L), eq(new BigDecimal("30000")), any());
        verify(jdbcTemplate).update(contains("DEBIT_CHECKOUT"), any(), eq(1L), eq(1L), eq(-130000L), any(), anyString(), anyString(), any());
        verify(jdbcTemplate).update(contains("'SHIPPING'"), any(), eq(2001L), any(), eq(new BigDecimal("30000")));
    }

    @Test
    @DisplayName("V58: Voucher takes priority over Coins and auto-clamps excess requested coins so buyer loses 0 coins")
    void checkout_voucherAppliedWithExcessCoins_autoClampsCoinsWithoutLoss() {
        mockUc83Product(10L, 20L, new BigDecimal("100000"));

        RewardPolicyEntity policy = mock(RewardPolicyEntity.class);
        when(policy.getPolicyId()).thenReturn(1L);
        when(policy.getPointValue()).thenReturn(BigDecimal.ONE);
        when(rewardPolicyRepository.findByPolicyCode("UC83_DEFAULT")).thenReturn(Optional.of(policy));
        when(jdbcTemplate.queryForObject(contains("reward_accounts"), eq(Long.class), eq(1L))).thenReturn(200000L);

        Instant now = fixedClock.instant();
        VoucherDtos.Campaign shipCampaign = new VoucherDtos.Campaign(
                55L, 550L, 1L, "FREESHIP30K", "Freeship 30k", "Freeship 30k",
                "SHIPPING_DISCOUNT", "FIXED_AMOUNT", new BigDecimal("30000"), new BigDecimal("30000"),
                BigDecimal.ZERO, "CHECKOUT", List.of(), 100,
                now.minusSeconds(3600), now.plusSeconds(3600),
                true, true, 1L, 0L
        );
        VoucherDtos.Grant grant = new VoucherDtos.Grant(
                UUID.randomUUID(), 1L, "Buyer A", "AVAILABLE",
                now.minusSeconds(60), now.plusSeconds(3600), shipCampaign
        );
        when(voucherService.resolve(eq(1L), eq("FREESHIP30K"), eq(10L), eq(new BigDecimal("100000")), eq(true)))
                .thenReturn(grant);
        when(orderRepository.findByCheckoutGroupId(any())).thenReturn(List.of());

        // Buyer originally selected 130,000 coins (100k goods + 30k ship), then also applied FREESHIP30K voucher
        GroupCheckoutRequest req = new GroupCheckoutRequest(
                "CMD_AUTO_CLAMP_1",
                sampleAddress(),
                "CARRIER",
                "GHN",
                "VNPAY",
                "FREESHIP30K",
                130000L,
                List.of(new GroupCheckoutItemRequest(10L, 1, null)),
                null
        );

        var result = service.executeCheckout(1L, req);

        assertThat(result.response().expectedTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.response().state()).isEqualTo(CheckoutGroupEntity.STATE_PAID);
        // Must auto-clamp debit to 100,000 coins (NOT 130,000), saving the buyer's 30,000 coins!
        verify(jdbcTemplate).update(contains("DEBIT_CHECKOUT"), any(), eq(1L), eq(1L), eq(-100000L), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("2A Negative: GroupPaymentService blocks createGroupPayment for 0-VND group with 400")
    void createGroupPayment_whenZeroAmount_throwsBadRequestWithoutOpeningVnPay() {
        CheckoutGroupRepository groupRepo = mock(CheckoutGroupRepository.class);
        PaymentIntentRepository intentRepo = mock(PaymentIntentRepository.class);
        PaymentAttemptRepository attemptRepo = mock(PaymentAttemptRepository.class);
        VnPayService vnPayService = mock(VnPayService.class);

        GroupPaymentService paymentService = new GroupPaymentService(
                groupRepo,
                mock(OrderRepository.class),
                mock(OrderItemRepository.class),
                mock(InventoryReservationRepository.class),
                intentRepo,
                attemptRepo,
                mock(PaymentConfirmationRepository.class),
                mock(PaymentAllocationRepository.class),
                mock(OrderFundComponentRepository.class),
                mock(RewardLedgerRepository.class),
                mock(PaymentIpnRawReceiptRepository.class),
                mock(PaymentIpnEventRepository.class),
                mock(PaymentReconciliationCaseRepository.class),
                vnPayService,
                mock(JdbcTemplate.class),
                mock(EntityManager.class),
                fixedClock
        );

        UUID gid = UUID.randomUUID();
        CheckoutGroupEntity zeroGroup = mock(CheckoutGroupEntity.class);
        when(zeroGroup.getBuyerId()).thenReturn(1L);
        when(zeroGroup.getState()).thenReturn(CheckoutGroupEntity.STATE_READY);
        when(zeroGroup.getExpectedTotal()).thenReturn(BigDecimal.ZERO);
        when(zeroGroup.getPaymentDueAt()).thenReturn(Instant.parse("2026-11-23T15:00:00Z"));
        when(groupRepo.findById(gid)).thenReturn(Optional.of(zeroGroup));

        assertThatThrownBy(() -> paymentService.createGroupPayment(1L, gid, "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("ZERO_AMOUNT_NO_PAYMENT_REQUIRED");
                });

        verify(intentRepo, never()).saveAndFlush(any());
        verify(vnPayService, never()).createPaymentUrl(any(), any(), any(), any(), any(), any());
    }
}
