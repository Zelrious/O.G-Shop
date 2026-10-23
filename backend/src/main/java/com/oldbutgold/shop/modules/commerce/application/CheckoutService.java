package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.catalog.application.CatalogCommerceFacade;
import com.oldbutgold.shop.modules.catalog.application.ProductNotFoundException;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.*;
import com.oldbutgold.shop.modules.identity.application.IdentityCommerceFacade;
import com.oldbutgold.shop.modules.communication.application.NegotiatedCheckoutFacade;
import com.oldbutgold.shop.modules.fulfillment.application.FulfillmentShippingFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.oldbutgold.shop.modules.commerce.api.VoucherDtos;

@Service
@Transactional
public class CheckoutService {
    public static final BigDecimal STANDARD_SHIPPING_FEE = new BigDecimal("30000");

    private final CatalogCommerceFacade catalogCommerceFacade;
    private final IdentityCommerceFacade identityCommerceFacade;
    private final SystemFeePolicyRepository systemFeePolicyRepository;
    private final VoucherRepository voucherRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderVoucherRepository orderVoucherRepository;
    private final Clock clock;
    private final NegotiatedCheckoutFacade negotiated;
    private final ObjectMapper json;
    private final VoucherService wallet;
    private final FulfillmentShippingFacade shippingFacade;

    @org.springframework.beans.factory.annotation.Autowired
    public CheckoutService(CatalogCommerceFacade catalogCommerceFacade,
                           IdentityCommerceFacade identityCommerceFacade,
                           SystemFeePolicyRepository systemFeePolicyRepository,
                           VoucherRepository voucherRepository,
                           OrderRepository orderRepository,
                           OrderItemRepository orderItemRepository,
                           OrderVoucherRepository orderVoucherRepository,
                           Clock clock,
                           NegotiatedCheckoutFacade negotiated,
                           ObjectMapper json,
                           VoucherService wallet,
                           @org.springframework.beans.factory.annotation.Autowired(required = false)
                           FulfillmentShippingFacade shippingFacade) {
        this.catalogCommerceFacade = catalogCommerceFacade;
        this.identityCommerceFacade = identityCommerceFacade;
        this.systemFeePolicyRepository = systemFeePolicyRepository;
        this.voucherRepository = voucherRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderVoucherRepository = orderVoucherRepository;
        this.clock = clock;
        this.negotiated = negotiated;
        this.json = json;
        this.wallet = wallet;
        this.shippingFacade = shippingFacade;
    }

    public CheckoutService(CatalogCommerceFacade catalogCommerceFacade,
                           IdentityCommerceFacade identityCommerceFacade,
                           SystemFeePolicyRepository systemFeePolicyRepository,
                           VoucherRepository voucherRepository,
                           OrderRepository orderRepository,
                           OrderItemRepository orderItemRepository,
                           OrderVoucherRepository orderVoucherRepository,
                           Clock clock,
                           NegotiatedCheckoutFacade negotiated,
                           ObjectMapper json,
                           VoucherService wallet) {
        this(catalogCommerceFacade, identityCommerceFacade, systemFeePolicyRepository, voucherRepository,
             orderRepository, orderItemRepository, orderVoucherRepository, clock, negotiated, json, wallet, null);
    }

    @Transactional(readOnly = true)
    public CheckoutDtos.CheckoutPreviewResponse getCheckoutPreview(long buyerId, long productId, String voucherCode) {
        return getCheckoutPreview(buyerId,productId,voucherCode,null);
    }
    @Transactional(readOnly=true)
    public CheckoutDtos.CheckoutPreviewResponse getCheckoutPreview(long buyerId,long productId,String voucherCode,Long offerId) {
        CatalogCommerceFacade.CommerceProductSummary product = catalogCommerceFacade.getActiveProduct(productId)
                .orElseThrow(() -> new ProductNotFoundException("Sản phẩm không tồn tại hoặc không ở trạng thái mở bán."));

        identityCommerceFacade.requirePurchaseAllowed(buyerId,product.sellerId(),product.requiresBuyerEkyc());
        negotiated.requireNotBlocked(buyerId,product.sellerId());
        var agreement=offerId==null?null:negotiated.preview(buyerId,productId,offerId);
        if (product.sellerId() == buyerId) {
            throw new IllegalArgumentException("Bạn không thể tự mua sản phẩm của chính mình.");
        }

        IdentityCommerceFacade.UserSummary seller = identityCommerceFacade.getUserSummary(product.sellerId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người bán."));

        IdentityCommerceFacade.AddressDto sellerAddress = identityCommerceFacade.getDefaultAddress(product.sellerId()).orElse(null);

        Long eligibleOfferId = null;
        BigDecimal eligibleOfferPrice = null;
        if (offerId == null) {
            var activeOfferOpt = negotiated.findActiveAcceptedOffer(buyerId, productId);
            if (activeOfferOpt.isPresent()) {
                eligibleOfferId = activeOfferOpt.get().offerId();
                eligibleOfferPrice = activeOfferOpt.get().price();
            }
        }

        List<IdentityCommerceFacade.AddressDto> addresses = identityCommerceFacade.getUserAddresses(buyerId);
        List<CheckoutDtos.AddressSummaryResponse> addressDtos = addresses.stream()
                .map(this::toAddressSummary)
                .toList();

        CheckoutDtos.AddressSummaryResponse selectedAddress = addressDtos.stream()
                .filter(CheckoutDtos.AddressSummaryResponse::isDefault)
                .findFirst()
                .orElse(!addressDtos.isEmpty() ? addressDtos.get(0) : null);

        Instant now = clock.instant();
        BigDecimal subtotal = agreement==null?product.listedPrice():agreement.price();
        List<CheckoutDtos.VoucherSummaryResponse> voucherDtos = wallet.usable(buyerId,productId,subtotal).stream().map(g->toVoucherSummary(g.campaign())).toList();
        BigDecimal shippingFee = STANDARD_SHIPPING_FEE;
        if (shippingFacade != null && sellerAddress != null && sellerAddress.hasValidGhnCodes() && selectedAddress != null && selectedAddress.hasValidGhnCodes()) {
            shippingFee = shippingFacade.calculateShippingFee(sellerAddress.ghnDistrictId(), sellerAddress.ghnWardCode(), selectedAddress.ghnDistrictId(), selectedAddress.ghnWardCode(), 500);
        }
        BigDecimal buyerSystemFee = agreement==null?BigDecimal.ZERO:agreement.buyerFee();

        BigDecimal voucherDiscount = BigDecimal.ZERO;
        BigDecimal shippingDiscount = BigDecimal.ZERO;
        CheckoutDtos.VoucherSummaryResponse appliedVoucher = null;
        String voucherRejectionReason = null;

        if (voucherCode != null && !voucherCode.isBlank()) {
            var voucher = wallet.usable(buyerId,productId,subtotal).stream().map(VoucherDtos.Grant::campaign)
                .filter(c->c.code().equalsIgnoreCase(voucherCode.trim())).findFirst().orElse(null);
            if(voucher!=null) {
                appliedVoucher = toVoucherSummary(voucher);
                if ("SHIPPING_DISCOUNT".equalsIgnoreCase(voucher.voucherType())) {
                    shippingDiscount = calculateDiscount(voucher, shippingFee);
                } else {
                    voucherDiscount = calculateDiscount(voucher, subtotal);
                }
            } else {
                voucherRejectionReason = wallet.checkEligibilityReason(buyerId, voucherCode, productId, subtotal);
            }
        }

        BigDecimal totalAmount = subtotal.add(shippingFee).add(buyerSystemFee)
                .subtract(voucherDiscount).subtract(shippingDiscount);

        return new CheckoutDtos.CheckoutPreviewResponse(
                product.productId(),
                product.title(),
                product.thumbnailUrl(),
                seller.userId(),
                seller.fullName(),
                product.listedPrice(),
                shippingFee,
                buyerSystemFee,
                voucherDiscount,
                shippingDiscount,
                totalAmount,
                addressDtos,
                selectedAddress,
                voucherDtos,
                appliedVoucher,
                agreement == null ? null : agreement.price(),
                offerId,
                eligibleOfferId,
                eligibleOfferPrice,
                voucherRejectionReason
        );
    }

    public CheckoutDtos.OrderCreatedResponse buyNow(long buyerId, CheckoutDtos.BuyNowRequest request) {
        Instant now = clock.instant();
        String requestFingerprint=fingerprint(request);
        if(request.offerId()!=null){var prior=negotiated.existingCheckout(buyerId,request.productId(),request.offerId(),requestFingerprint);if(prior.isPresent())return getOrder(buyerId,prior.get());}

        // 1. Pessimistic Row Lock on product to prevent double selling
        CatalogCommerceFacade.CommerceProductDetail product = catalogCommerceFacade.getProductForCheckoutLock(request.productId());
        identityCommerceFacade.requirePurchaseAllowed(buyerId,product.sellerId(),product.requiresBuyerEkyc());
        negotiated.requireNotBlocked(buyerId,product.sellerId());
        var agreement=request.offerId()==null?null:negotiated.bind(buyerId,product.productId(),request.offerId(),requestFingerprint);

        if (product.sellerId() == buyerId) {
            throw new IllegalArgumentException("Bạn không thể tự mua sản phẩm của chính mình.");
        }

        // DP-19: Guard eKYC requirement
        if (product.requiresBuyerEkyc() && !identityCommerceFacade.isUserVerified(buyerId)) {
            throw new IllegalStateException("Sản phẩm này yêu cầu Người mua phải hoàn tất xác thực eKYC CCCD để đặt mua.");
        }

        // 2. Resolve Shipping Address
        IdentityCommerceFacade.AddressDto address;
        if (request.addressId() != null) {
            address = identityCommerceFacade.getAddressById(buyerId, request.addressId())
                    .orElseThrow(() -> new IllegalArgumentException("Địa chỉ nhận hàng không hợp lệ."));
        } else if (request.newAddress() != null) {
            CheckoutDtos.CreateAddressRequest newAddr = request.newAddress();
            address = identityCommerceFacade.createAddress(buyerId, new IdentityCommerceFacade.CreateAddressRequest(
                    newAddr.recipientName().trim(),
                    newAddr.phoneNumber().trim(),
                    newAddr.province().trim(),
                    newAddr.district().trim(),
                    newAddr.ward().trim(),
                    newAddr.detailAddress().trim(),
                    newAddr.ghnProvinceId(),
                    newAddr.ghnDistrictId(),
                    newAddr.ghnWardCode(),
                    newAddr.isDefault()
            ));
        } else {
            address = identityCommerceFacade.getDefaultAddress(buyerId)
                    .orElseThrow(() -> new IllegalArgumentException("Vui lòng chọn hoặc nhập địa chỉ nhận hàng."));
        }

        // ADDR-03: Kiểm tra địa chỉ nhận hàng của Buyer có đủ mã chuẩn hóa GHN
        if (!address.hasValidGhnCodes()) {
            throw new IllegalArgumentException("Địa chỉ nhận hàng chưa được chuẩn hóa theo mã bưu chính GHN. Vui lòng cập nhật hoặc thêm địa chỉ chuẩn GHN để tiếp tục.");
        }

        // ADDR-02 & ADDR-03: Lấy địa chỉ lấy hàng mặc định của Seller và kiểm tra chuẩn hóa GHN
        IdentityCommerceFacade.AddressDto sellerAddress = identityCommerceFacade.getDefaultAddress(product.sellerId())
                .orElse(null);
        if (sellerAddress == null || !sellerAddress.hasValidGhnCodes()) {
            throw new IllegalStateException("Sản phẩm hiện chưa thể đặt mua do người bán chưa thiết lập địa chỉ lấy hàng chuẩn hóa GHN. Vui lòng liên hệ người bán.");
        }
        Long sourceAddressId = sellerAddress.id();

        // 3. Fee Policy
        SystemFeePolicyEntity feePolicy = systemFeePolicyRepository.findFirstByStatusOrderByEffectiveFromDesc("ACTIVE")
                .orElseThrow(() -> new IllegalStateException("Hệ thống chưa thiết lập chính sách phí hợp lệ."));

        BigDecimal subtotal = agreement==null?product.listedPrice():agreement.price();
        BigDecimal shippingFee = STANDARD_SHIPPING_FEE;
        if (shippingFacade != null && sellerAddress.hasValidGhnCodes() && address.hasValidGhnCodes()) {
            shippingFee = shippingFacade.calculateShippingFee(sellerAddress.ghnDistrictId(), sellerAddress.ghnWardCode(), address.ghnDistrictId(), address.ghnWardCode(), 500);
        }
        BigDecimal buyerFee = agreement==null?BigDecimal.ZERO:agreement.buyerFee(); // Accepted offers preserve the fee snapshot.
        BigDecimal sellerFee = agreement==null?feePolicy.getSellerFixedFee().add(
                subtotal.multiply(feePolicy.getSellerFeeRate()).setScale(0, RoundingMode.HALF_UP)
        ):agreement.sellerFee();

        // 4. Voucher Resolution
        BigDecimal voucherDiscount = BigDecimal.ZERO;
        BigDecimal shippingDiscount = BigDecimal.ZERO;
        String sponsorType = "PLATFORM";
        VoucherDtos.Grant ownedGrant = null;
        VoucherDtos.Campaign voucher = null;

        if (request.voucherCode() != null && !request.voucherCode().isBlank()) {
            wallet.lockCheckout(buyerId,product.sellerId());
            ownedGrant = wallet.resolve(buyerId,request.voucherCode(),product.productId(),subtotal,true);
            voucher = ownedGrant.campaign();
            if ("SHIPPING_DISCOUNT".equalsIgnoreCase(voucher.voucherType())) {
                shippingDiscount = calculateDiscount(voucher, shippingFee);
            } else {
                voucherDiscount = calculateDiscount(voucher, subtotal);
            }

        }

        // 5. Calculate Final Totals
        BigDecimal totalAmount = subtotal.add(shippingFee).add(buyerFee)
                .subtract(voucherDiscount).subtract(shippingDiscount);

        BigDecimal sellerProceeds = subtotal.subtract(
                "SELLER".equalsIgnoreCase(sponsorType) ? voucherDiscount : BigDecimal.ZERO
        ).subtract(sellerFee);

        UUID checkoutGroupId = UUID.randomUUID();
        // DP-02 & User input: 1 hour payment countdown window
        Instant paymentDueAt = now.plus(Duration.ofHours(1));

        // 6. Save Order
        OrderEntity order = new OrderEntity(
                checkoutGroupId,
                buyerId,
                product.sellerId(),
                sourceAddressId,
                address.recipientName(),
                address.phoneNumber(),
                address.province(),
                address.district(),
                address.ward(),
                address.detailAddress(),
                address.ghnProvinceId(),
                address.ghnDistrictId(),
                address.ghnWardCode(),
                subtotal,
                shippingFee,
                buyerFee,
                sellerFee,
                sellerProceeds,
                voucherDiscount,
                shippingDiscount,
                sponsorType,
                totalAmount,
                paymentDueAt,
                now
        );
        OrderEntity savedOrder = orderRepository.save(order);

        // 7. Save OrderItem
        OrderItemEntity orderItem = new OrderItemEntity(
                savedOrder.getId(),
                product.productId(),
                product.title(),
                (short) 1,
                product.listedPrice(),
                subtotal,
                buyerFee,
                sellerFee,
                subtotal.add(buyerFee),
                sellerProceeds,
                agreement==null?feePolicy.getId():agreement.feePolicyId(),
                request.offerId(),
                agreement==null?"LIST_PRICE":"OFFER"
        );
        orderItemRepository.saveAndFlush(orderItem);
        if (request.offerId()!=null) negotiated.linkOrder(buyerId,request.offerId(),savedOrder.getId());

        // 8. Snapshot Order Voucher if applied
        if (voucher != null) {
            BigDecimal totalDiscount = voucherDiscount.add(shippingDiscount);
            OrderVoucherEntity orderVoucher = new OrderVoucherEntity(
                    savedOrder.getId(),
                    voucher.id(),
                    voucher.code(),
                    voucher.voucherType(),
                    "PLATFORM",
                    totalDiscount,
                    now
            );
            orderVoucherRepository.saveAndFlush(orderVoucher);
            wallet.bindOrder(savedOrder.getId(),ownedGrant);
        }

        // 9. Reserve Product
        catalogCommerceFacade.reserveProduct(product.productId(), savedOrder.getId(), paymentDueAt, now);

        String fullAddress = String.format("%s, %s, %s, %s",
                address.detailAddress(), address.ward(), address.district(), address.province());

        return new CheckoutDtos.OrderCreatedResponse(
                savedOrder.getId(),
                checkoutGroupId.toString(),
                product.productId(),
                product.title(),
                savedOrder.getStatus(),
                savedOrder.getSubtotal(),
                savedOrder.getShippingFee(),
                savedOrder.getBuyerSystemFee(),
                savedOrder.getVoucherDiscountAmount(),
                savedOrder.getShippingDiscountAmount(),
                savedOrder.getTotalAmount(),
                savedOrder.getShippingRecipientName(),
                savedOrder.getShippingPhoneNumber(),
                fullAddress,
                savedOrder.getPaymentDueAt(),
                savedOrder.getCreatedAt()
        );
    }
    private String fingerprint(CheckoutDtos.BuyNowRequest request){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(request)));}catch(Exception e){throw new IllegalStateException("Cannot fingerprint checkout request",e);}}

    @Transactional(readOnly = true)
    public CheckoutDtos.OrderCreatedResponse getOrder(long buyerId, long orderId) {
        OrderEntity order = orderRepository.findByIdAndBuyerId(orderId, buyerId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        List<OrderItemEntity> items = orderItemRepository.findByOrderId(orderId);
        String productTitle = !items.isEmpty() ? items.get(0).getProductTitle() : "Sản phẩm O.G Shop";
        Long productId = !items.isEmpty() ? items.get(0).getProductId() : 0L;

        String fullAddress = String.format("%s, %s, %s, %s",
                order.getShippingDetailAddress(), order.getShippingWard(),
                order.getShippingDistrict(), order.getShippingProvince());

        return new CheckoutDtos.OrderCreatedResponse(
                order.getId(),
                order.getCheckoutGroupId().toString(),
                productId,
                productTitle,
                order.getStatus(),
                order.getSubtotal(),
                order.getShippingFee(),
                order.getBuyerSystemFee(),
                order.getVoucherDiscountAmount(),
                order.getShippingDiscountAmount(),
                order.getTotalAmount(),
                order.getShippingRecipientName(),
                order.getShippingPhoneNumber(),
                fullAddress,
                order.getPaymentDueAt(),
                order.getCreatedAt()
        );
    }

    private BigDecimal calculateDiscount(VoucherDtos.Campaign voucher, BigDecimal baseAmount) {
        BigDecimal discount;
        if ("PERCENTAGE".equalsIgnoreCase(voucher.discountType())) {
            discount = baseAmount.multiply(voucher.discountValue())
                    .divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP);
            if (voucher.maxDiscountAmount() != null && discount.compareTo(voucher.maxDiscountAmount()) > 0) {
                discount = voucher.maxDiscountAmount();
            }
        } else {
            discount = voucher.discountValue();
        }
        return discount.min(baseAmount).min(voucher.maxDiscountAmount()==null?baseAmount:voucher.maxDiscountAmount());
    }

    private CheckoutDtos.AddressSummaryResponse toAddressSummary(IdentityCommerceFacade.AddressDto a) {
        return new CheckoutDtos.AddressSummaryResponse(
                a.id(), a.recipientName(), a.phoneNumber(),
                a.province(), a.district(), a.ward(),
                a.detailAddress(), a.ghnProvinceId(), a.ghnDistrictId(), a.ghnWardCode(),
                a.isDefault()
        );
    }

    private CheckoutDtos.VoucherSummaryResponse toVoucherSummary(VoucherDtos.Campaign v) {
        return new CheckoutDtos.VoucherSummaryResponse(
                v.id(), v.code(), v.title(),
                v.voucherType(), v.discountType(),
                v.discountValue(), v.maxDiscountAmount(),
                v.minOrderAmount(), "PLATFORM"
        );
    }
}
