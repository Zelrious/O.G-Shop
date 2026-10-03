package com.oldbutgold.shop.modules.commerce.application;

import com.oldbutgold.shop.modules.catalog.application.CatalogCommerceFacade;
import com.oldbutgold.shop.modules.catalog.application.ProductNotFoundException;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.*;
import com.oldbutgold.shop.modules.identity.application.IdentityCommerceFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    public CheckoutService(CatalogCommerceFacade catalogCommerceFacade,
                           IdentityCommerceFacade identityCommerceFacade,
                           SystemFeePolicyRepository systemFeePolicyRepository,
                           VoucherRepository voucherRepository,
                           OrderRepository orderRepository,
                           OrderItemRepository orderItemRepository,
                           OrderVoucherRepository orderVoucherRepository,
                           Clock clock) {
        this.catalogCommerceFacade = catalogCommerceFacade;
        this.identityCommerceFacade = identityCommerceFacade;
        this.systemFeePolicyRepository = systemFeePolicyRepository;
        this.voucherRepository = voucherRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderVoucherRepository = orderVoucherRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CheckoutDtos.CheckoutPreviewResponse getCheckoutPreview(long buyerId, long productId, String voucherCode) {
        CatalogCommerceFacade.CommerceProductSummary product = catalogCommerceFacade.getActiveProduct(productId)
                .orElseThrow(() -> new ProductNotFoundException("Sản phẩm không tồn tại hoặc không ở trạng thái mở bán."));

        if (product.sellerId() == buyerId) {
            throw new IllegalArgumentException("Bạn không thể tự mua sản phẩm của chính mình.");
        }

        IdentityCommerceFacade.UserSummary seller = identityCommerceFacade.getUserSummary(product.sellerId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người bán."));

        List<IdentityCommerceFacade.AddressDto> addresses = identityCommerceFacade.getUserAddresses(buyerId);
        List<CheckoutDtos.AddressSummaryResponse> addressDtos = addresses.stream()
                .map(this::toAddressSummary)
                .toList();

        CheckoutDtos.AddressSummaryResponse selectedAddress = addressDtos.stream()
                .filter(CheckoutDtos.AddressSummaryResponse::isDefault)
                .findFirst()
                .orElse(!addressDtos.isEmpty() ? addressDtos.get(0) : null);

        Instant now = clock.instant();
        List<VoucherEntity> activeVouchers = voucherRepository.findByIsActiveTrueAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(now, now);
        List<CheckoutDtos.VoucherSummaryResponse> voucherDtos = activeVouchers.stream()
                .filter(v -> v.getSellerId() == null || v.getSellerId().equals(product.sellerId()))
                .filter(v -> v.getTotalUsageLimit() == null || v.getCurrentUsageCount() < v.getTotalUsageLimit())
                .map(this::toVoucherSummary)
                .toList();

        BigDecimal subtotal = product.listedPrice();
        BigDecimal shippingFee = STANDARD_SHIPPING_FEE;
        BigDecimal buyerSystemFee = BigDecimal.ZERO;

        BigDecimal voucherDiscount = BigDecimal.ZERO;
        BigDecimal shippingDiscount = BigDecimal.ZERO;
        CheckoutDtos.VoucherSummaryResponse appliedVoucher = null;

        if (voucherCode != null && !voucherCode.isBlank()) {
            VoucherEntity voucher = voucherRepository.findByCodeAndIsActiveTrue(voucherCode.trim().toUpperCase())
                    .orElse(null);

            if (voucher != null && isValidVoucher(voucher, product.sellerId(), subtotal, now)) {
                appliedVoucher = toVoucherSummary(voucher);
                if ("SHIPPING_DISCOUNT".equalsIgnoreCase(voucher.getVoucherType())) {
                    shippingDiscount = calculateDiscount(voucher, shippingFee);
                } else {
                    voucherDiscount = calculateDiscount(voucher, subtotal);
                }
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
                subtotal,
                shippingFee,
                buyerSystemFee,
                voucherDiscount,
                shippingDiscount,
                totalAmount,
                addressDtos,
                selectedAddress,
                voucherDtos,
                appliedVoucher
        );
    }

    public CheckoutDtos.OrderCreatedResponse buyNow(long buyerId, CheckoutDtos.BuyNowRequest request) {
        Instant now = clock.instant();

        // 1. Pessimistic Row Lock on product to prevent double selling
        CatalogCommerceFacade.CommerceProductDetail product = catalogCommerceFacade.getProductForCheckoutLock(request.productId());

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
                    newAddr.isDefault()
            ));
        } else {
            address = identityCommerceFacade.getDefaultAddress(buyerId)
                    .orElseThrow(() -> new IllegalArgumentException("Vui lòng chọn hoặc nhập địa chỉ nhận hàng."));
        }

        // 3. Fee Policy
        SystemFeePolicyEntity feePolicy = systemFeePolicyRepository.findFirstByStatusOrderByEffectiveFromDesc("ACTIVE")
                .orElseThrow(() -> new IllegalStateException("Hệ thống chưa thiết lập chính sách phí hợp lệ."));

        BigDecimal subtotal = product.listedPrice();
        BigDecimal shippingFee = STANDARD_SHIPPING_FEE;
        BigDecimal buyerFee = BigDecimal.ZERO; // Phí sàn Buyer = 0 theo policy V3 0%
        BigDecimal sellerFee = feePolicy.getSellerFixedFee().add(
                subtotal.multiply(feePolicy.getSellerFeeRate()).setScale(0, RoundingMode.HALF_UP)
        );

        // 4. Voucher Resolution
        BigDecimal voucherDiscount = BigDecimal.ZERO;
        BigDecimal shippingDiscount = BigDecimal.ZERO;
        String sponsorType = "PLATFORM";
        VoucherEntity voucher = null;

        if (request.voucherCode() != null && !request.voucherCode().isBlank()) {
            voucher = voucherRepository.findByCodeAndIsActiveTrue(request.voucherCode().trim().toUpperCase())
                    .orElseThrow(() -> new IllegalArgumentException("Mã giảm giá không tồn tại hoặc đã hết hạn."));

            if (!isValidVoucher(voucher, product.sellerId(), subtotal, now)) {
                throw new IllegalArgumentException("Mã giảm giá không đủ điều kiện áp dụng cho đơn hàng này.");
            }

            sponsorType = voucher.getSponsorType();
            if ("SHIPPING_DISCOUNT".equalsIgnoreCase(voucher.getVoucherType())) {
                shippingDiscount = calculateDiscount(voucher, shippingFee);
            } else {
                voucherDiscount = calculateDiscount(voucher, subtotal);
            }

            // Atomic usage count increment
            voucher.incrementUsage();
            voucherRepository.save(voucher);
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
                address.id(),
                address.recipientName(),
                address.phoneNumber(),
                address.province(),
                address.district(),
                address.ward(),
                address.detailAddress(),
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
                product.listedPrice(),
                buyerFee,
                sellerFee,
                subtotal.add(buyerFee),
                sellerProceeds,
                feePolicy.getId(),
                null,
                "LIST_PRICE"
        );
        orderItemRepository.save(orderItem);

        // 8. Snapshot Order Voucher if applied
        if (voucher != null) {
            BigDecimal totalDiscount = voucherDiscount.add(shippingDiscount);
            OrderVoucherEntity orderVoucher = new OrderVoucherEntity(
                    savedOrder.getId(),
                    voucher.getId(),
                    voucher.getCode(),
                    voucher.getVoucherType(),
                    voucher.getSponsorType(),
                    totalDiscount,
                    now
            );
            orderVoucherRepository.save(orderVoucher);
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

    private boolean isValidVoucher(VoucherEntity voucher, Long sellerId, BigDecimal subtotal, Instant now) {
        if (!voucher.isActive()) return false;
        if (now.isBefore(voucher.getStartTime()) || now.isAfter(voucher.getEndTime())) return false;
        if (voucher.getTotalUsageLimit() != null && voucher.getCurrentUsageCount() >= voucher.getTotalUsageLimit()) return false;
        if (subtotal.compareTo(voucher.getMinOrderAmount()) < 0) return false;
        if (voucher.getSellerId() != null && !voucher.getSellerId().equals(sellerId)) return false;
        return true;
    }

    private BigDecimal calculateDiscount(VoucherEntity voucher, BigDecimal baseAmount) {
        BigDecimal discount;
        if ("PERCENTAGE".equalsIgnoreCase(voucher.getDiscountType())) {
            discount = baseAmount.multiply(voucher.getDiscountValue())
                    .divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP);
            if (voucher.getMaxDiscountAmount() != null && discount.compareTo(voucher.getMaxDiscountAmount()) > 0) {
                discount = voucher.getMaxDiscountAmount();
            }
        } else {
            discount = voucher.getDiscountValue();
        }
        return discount.min(baseAmount);
    }

    private CheckoutDtos.AddressSummaryResponse toAddressSummary(IdentityCommerceFacade.AddressDto a) {
        return new CheckoutDtos.AddressSummaryResponse(
                a.id(), a.recipientName(), a.phoneNumber(),
                a.province(), a.district(), a.ward(),
                a.detailAddress(), a.isDefault()
        );
    }

    private CheckoutDtos.VoucherSummaryResponse toVoucherSummary(VoucherEntity v) {
        return new CheckoutDtos.VoucherSummaryResponse(
                v.getId(), v.getCode(), v.getTitle(),
                v.getVoucherType(), v.getDiscountType(),
                v.getDiscountValue(), v.getMaxDiscountAmount(),
                v.getMinOrderAmount(), v.getSponsorType()
        );
    }
}
