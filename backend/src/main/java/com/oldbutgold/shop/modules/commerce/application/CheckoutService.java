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
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.oldbutgold.shop.modules.commerce.api.VoucherDtos;

@Service
@Transactional
public class CheckoutService {
    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);
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
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @org.springframework.beans.factory.annotation.Value("${app.reward.canonical-policy-code:}")
    private String configuredCanonicalPolicyCode;
    // DEC-10.7A: no default — must be explicitly approved by PO and configured by DevOps
    @org.springframework.beans.factory.annotation.Value("${app.reward.max-redemption-percentage:}")
    private String configuredMaxRedemptionPercentage;

    // GAP-1 / DEC-10.7A: PO Approval Gate for Coin Redemption Cap.
    // Default is false (PO decision on max_redemption_percentage is currently OPEN).
    // DevOps configuration alone CANNOT open Coin checkout while this gate is closed.
    private boolean capPoApproved = false;

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
                           FulfillmentShippingFacade shippingFacade,
                           @org.springframework.beans.factory.annotation.Autowired(required = false)
                           org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
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
        this.jdbcTemplate = jdbcTemplate;
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
                           VoucherService wallet,
                           FulfillmentShippingFacade shippingFacade) {
        this(catalogCommerceFacade, identityCommerceFacade, systemFeePolicyRepository, voucherRepository,
             orderRepository, orderItemRepository, orderVoucherRepository, clock, negotiated, json, wallet, shippingFacade, null);
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
             orderRepository, orderItemRepository, orderVoucherRepository, clock, negotiated, json, wallet, null, null);
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

        long buyerCoinBalance = 0L;
        if (jdbcTemplate != null) {
            try {
                Long bal = jdbcTemplate.queryForObject("SELECT COALESCE(balance, 0) FROM og_compat.reward_accounts WHERE user_id = ?", Long.class, buyerId);
                buyerCoinBalance = bal != null ? bal : 0L;
            } catch (Exception ignored) {
                try {
                    Long pts = jdbcTemplate.queryForObject("SELECT coalesce(sum(points_delta),0) FROM public.reward_ledger WHERE user_id = ?", Long.class, buyerId);
                    buyerCoinBalance = pts != null ? pts : 0L;
                } catch (Exception ignored2) {}
            }
        }
        long maxRedeemableCoins = 0L;
        try {
            BigDecimal capPct = resolveMaxRedemptionPercentage();
            BigDecimal maxGoodsRedemption = subtotal.subtract(voucherDiscount).max(BigDecimal.ZERO);
            long cappedCoins = maxGoodsRedemption
                    .multiply(capPct)
                    .divide(new BigDecimal("100"), 0, RoundingMode.FLOOR)
                    .longValue();
            maxRedeemableCoins = Math.min(Math.max(0L, buyerCoinBalance), cappedCoins);
        } catch (Exception ignored) {
            // DEC-10.7A: When cap is unconfigured or invalid, 0 coins can be redeemed
            maxRedeemableCoins = 0L;
        }

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
                voucherRejectionReason,
                buyerCoinBalance,
                maxRedeemableCoins,
                BigDecimal.ZERO
        );
    }

    public CheckoutDtos.OrderCreatedResponse buyNow(long buyerId, CheckoutDtos.BuyNowRequest request) {
        return executeBuyNow(buyerId, request).response();
    }

    public CheckoutDtos.BuyNowExecutionResult executeBuyNow(long buyerId, CheckoutDtos.BuyNowRequest request) {
        if (request.commandKey() == null || request.commandKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã thao tác (commandKey) là bắt buộc.");
        }
        String normalizedKey = request.commandKey().trim();
        if (normalizedKey.length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã thao tác (commandKey) không được vượt quá 150 ký tự.");
        }

        Instant now = clock.instant();
        String requestFingerprint = fingerprint(request);
        UUID deterministicGroupId = UUID.nameUUIDFromBytes(("CHECKOUT:" + buyerId + ":" + normalizedKey).getBytes(StandardCharsets.UTF_8));
        ClientCheckoutFingerprint fingerprint = ClientCheckoutFingerprint.of(buyerId, request);
        String requestHash = fingerprint.sha256Hex();

        if (jdbcTemplate != null) {
            List<Long> claimed = jdbcTemplate.query("""
                    INSERT INTO public.checkout_idempotency_entries (buyer_id, command_key, request_hash, checkout_group_id, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (buyer_id, command_key) DO NOTHING
                    RETURNING buyer_id
                    """,
                    (rs, rowNum) -> rs.getLong("buyer_id"),
                    buyerId, normalizedKey, requestHash, deterministicGroupId, Timestamp.from(now)
            );

            if (claimed.isEmpty()) {
                List<Map<String, Object>> existingEntries = jdbcTemplate.queryForList("""
                        SELECT request_hash, checkout_group_id
                        FROM public.checkout_idempotency_entries
                        WHERE buyer_id = ? AND command_key = ?
                        """,
                        buyerId, normalizedKey
                );

                if (!existingEntries.isEmpty()) {
                    Map<String, Object> entry = existingEntries.get(0);
                    String existingHash = (String) entry.get("request_hash");
                    UUID existingGroupId = (UUID) entry.get("checkout_group_id");

                    if (!Objects.equals(requestHash, existingHash)) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Mã thao tác (commandKey) đã được sử dụng với thông tin đặt hàng khác (COMMAND_KEY_CONFLICT)"
                        );
                    }

                    log.info("Idempotent replay: Đơn hàng đã tồn tại cho commandKey {}", normalizedKey);
                    return new CheckoutDtos.BuyNowExecutionResult(replayOrdersByGroupId(buyerId, existingGroupId), true);
                }
            }
        }
        if (request.offerId() != null) {
            var prior = negotiated.existingCheckout(buyerId, request.productId(), request.offerId(), requestFingerprint);
            if (prior.isPresent()) return new CheckoutDtos.BuyNowExecutionResult(getOrder(buyerId, prior.get()), true);
        }

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

        // 5. Calculate Final Totals & Coin Redemption
        long coinsRequested = request.coinsToRedeem() != null ? request.coinsToRedeem() : 0L;
        if (coinsRequested < 0) {
            throw new IllegalArgumentException("Số xu sử dụng không hợp lệ.");
        }

        long coinsToRedeem = 0L;
        BigDecimal coinDiscount = BigDecimal.ZERO;

        // DEC-10.7A: fail-closed — cap percentage must be explicitly configured by DevOps after PO approval
        if (coinsRequested > 0) {
            BigDecimal capPct = resolveMaxRedemptionPercentage(); // throws IllegalStateException if not configured
            BigDecimal maxGoodsRedemption = subtotal.subtract(voucherDiscount).max(BigDecimal.ZERO);
            long maxRedeemableCoins = maxGoodsRedemption
                    .multiply(capPct)
                    .divide(new BigDecimal("100"), 0, RoundingMode.FLOOR)
                    .longValue();
            if (jdbcTemplate != null) {
                jdbcTemplate.queryForObject("SELECT user_id FROM public.users WHERE user_id = ? FOR UPDATE", Long.class, buyerId);
                long balance = 0L;
                try {
                    Long b = jdbcTemplate.queryForObject("SELECT COALESCE(balance, 0) FROM og_compat.reward_accounts WHERE user_id = ? FOR UPDATE", Long.class, buyerId);
                    balance = b != null ? b : 0L;
                } catch (Exception ex) {
                    try {
                        Long pts = jdbcTemplate.queryForObject("SELECT coalesce(sum(points_delta),0) FROM public.reward_ledger WHERE user_id = ?", Long.class, buyerId);
                        balance = pts != null ? pts : 0L;
                    } catch (Exception ignored) {}
                }
                if (coinsRequested > balance) {
                    throw new IllegalArgumentException("Số dư xu không đủ để thực hiện thanh toán.");
                }
            }

            if (coinsRequested > maxRedeemableCoins) {
                throw new IllegalArgumentException("Số xu sử dụng (" + coinsRequested + ") vượt quá giá trị thanh toán cho hàng hóa (" + maxRedeemableCoins + " xu).");
            }

            coinsToRedeem = coinsRequested;
            coinDiscount = BigDecimal.valueOf(coinsToRedeem); // Fixed 1:1 scalar conversion: 1 Coin = 1 VND (PO-DEC-01)
        }

        BigDecimal totalAmount = subtotal.add(shippingFee).add(buyerFee)
                .subtract(voucherDiscount).subtract(shippingDiscount).subtract(coinDiscount);

        BigDecimal sellerProceeds = subtotal.subtract(
                "SELLER".equalsIgnoreCase(sponsorType) ? voucherDiscount : BigDecimal.ZERO
        ).subtract(sellerFee);

        UUID checkoutGroupId = deterministicGroupId != null
                ? deterministicGroupId
                : (request.offerId() != null
                    ? UUID.nameUUIDFromBytes(("CHECKOUT:OFFER:" + buyerId + ":" + request.offerId()).getBytes(StandardCharsets.UTF_8))
                    : UUID.randomUUID());
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
                coinDiscount,
                sponsorType,
                totalAmount,
                paymentDueAt,
                now
        );
        OrderEntity savedOrder = orderRepository.save(order);

        // 7. Save OrderItem with PO-DEC-04B item allocation
        OrderItemEntity orderItem = new OrderItemEntity(
                savedOrder.getId(),
                product.productId(),
                product.title(),
                (short) 1,
                product.listedPrice(),
                subtotal,
                buyerFee,
                sellerFee,
                subtotal.add(buyerFee).subtract(coinDiscount),
                sellerProceeds,
                agreement == null ? feePolicy.getId() : agreement.feePolicyId(),
                request.offerId(),
                agreement == null ? "LIST_PRICE" : "OFFER",
                voucherDiscount,
                coinDiscount
        );
        orderItemRepository.saveAndFlush(orderItem);
        if (request.offerId() != null) negotiated.linkOrder(buyerId, request.offerId(), savedOrder.getId());

        // 7b. Record Buyer Coin Debit in reward_ledger
        if (coinsToRedeem > 0 && jdbcTemplate != null) {
            String policyCode = resolveCanonicalRewardPolicyCode();
            if (policyCode == null || policyCode.isBlank()) {
                throw new IllegalStateException("Cannot process coin redemption: Missing explicit configuration for canonical reward policy code (environment parameter REWARD_CANONICAL_POLICY_CODE is not configured)");
            }
            List<Long> canonicalPolicies = jdbcTemplate.queryForList(
                    "SELECT policy_id FROM og_compat.reward_policies WHERE policy_code = ? ORDER BY policy_id ASC",
                    Long.class,
                    policyCode
            );
            if (canonicalPolicies == null || canonicalPolicies.isEmpty()) {
                throw new IllegalStateException("Cannot process coin redemption: Canonical reward policy not found for policy_code: " + policyCode);
            }
            if (canonicalPolicies.size() > 1) {
                throw new IllegalStateException("Cannot process coin redemption: Ambiguous multiple reward policies found for canonical code: " + policyCode);
            }
            Long policyId = canonicalPolicies.get(0);

            if (jdbcTemplate.queryForObject("SELECT count(*) FROM og_compat.reward_accounts WHERE user_id = ?", Long.class, buyerId) == 0) {
                jdbcTemplate.update("INSERT INTO og_compat.reward_accounts(user_id) VALUES(?)", buyerId);
            }
            jdbcTemplate.update("""
                    INSERT INTO og_compat.reward_ledger(entry_id, user_id, entry_type, points_delta, order_id, reason, command_key, policy_id)
                    VALUES (?, ?, 'DEBIT_CHECKOUT', ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    buyerId,
                    -coinsToRedeem,
                    savedOrder.getId(),
                    "CHECKOUT:" + savedOrder.getId(),
                    "CHECKOUT:DEBIT:" + savedOrder.getId(),
                    policyId
            );
        }

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

        var primaryResponse = new CheckoutDtos.OrderCreatedResponse(
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
                savedOrder.getPointsDiscountAmount(),
                savedOrder.getTotalAmount(),
                savedOrder.getShippingRecipientName(),
                savedOrder.getShippingPhoneNumber(),
                fullAddress,
                savedOrder.getPaymentDueAt(),
                savedOrder.getCreatedAt()
        );

        var finalResponse = new CheckoutDtos.OrderCreatedResponse(
                primaryResponse.orderId(),
                primaryResponse.checkoutGroupId(),
                primaryResponse.productId(),
                primaryResponse.productTitle(),
                primaryResponse.status(),
                primaryResponse.subtotal(),
                primaryResponse.shippingFee(),
                primaryResponse.buyerSystemFee(),
                primaryResponse.voucherDiscount(),
                primaryResponse.shippingDiscount(),
                primaryResponse.pointsDiscountAmount(),
                primaryResponse.totalAmount(),
                primaryResponse.recipientName(),
                primaryResponse.phoneNumber(),
                primaryResponse.fullAddress(),
                primaryResponse.paymentDueAt(),
                primaryResponse.createdAt(),
                List.of(primaryResponse)
        );

        return new CheckoutDtos.BuyNowExecutionResult(finalResponse, false);
    }

    @Transactional(readOnly = true)
    public CheckoutDtos.OrderCreatedResponse replayOrdersByGroupId(long buyerId, UUID checkoutGroupId) {
        List<Long> orderIds = List.of();
        if (jdbcTemplate != null) {
            orderIds = jdbcTemplate.query(
                    "SELECT order_id FROM og_compat.orders WHERE checkout_group_id = ? AND buyer_id = ? ORDER BY order_id ASC",
                    (rs, rowNum) -> rs.getLong("order_id"),
                    checkoutGroupId, buyerId
            );
        }
        if (orderIds.isEmpty()) {
            orderIds = orderRepository.findByCheckoutGroupIdAndBuyerIdOrderByIdAsc(checkoutGroupId, buyerId).stream()
                    .map(OrderEntity::getId)
                    .toList();
        }
        if (orderIds.isEmpty()) {
            throw new IllegalStateException("Không tìm thấy đơn hàng cho phiên checkout đã xác nhận: " + checkoutGroupId);
        }

        List<CheckoutDtos.OrderCreatedResponse> allOrders = orderIds.stream()
                .map(id -> getOrder(buyerId, id))
                .toList();

        CheckoutDtos.OrderCreatedResponse primary = allOrders.get(0);
        return new CheckoutDtos.OrderCreatedResponse(
                primary.orderId(),
                primary.checkoutGroupId(),
                primary.productId(),
                primary.productTitle(),
                primary.status(),
                primary.subtotal(),
                primary.shippingFee(),
                primary.buyerSystemFee(),
                primary.voucherDiscount(),
                primary.shippingDiscount(),
                primary.pointsDiscountAmount(),
                primary.totalAmount(),
                primary.recipientName(),
                primary.phoneNumber(),
                primary.fullAddress(),
                primary.paymentDueAt(),
                primary.createdAt(),
                allOrders
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
                order.getPointsDiscountAmount(),
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

    public void setCanonicalRewardPolicyCode(String code) {
        this.configuredCanonicalPolicyCode = code;
    }

    /** For testing only. Sets the max redemption percentage config (DEC-10.7A). */
    public void setMaxRedemptionPercentage(String pct) {
        this.configuredMaxRedemptionPercentage = pct;
    }

    /** For package-internal unit testing / simulated future PO approval gate transition (GAP-1 / DEC-10.7A). */
    void setCapPoApproved(boolean capPoApproved) {
        this.capPoApproved = capPoApproved;
    }

    boolean isCapPoApproved() {
        return this.capPoApproved;
    }

    public String resolveCanonicalRewardPolicyCode() {
        String env = System.getenv("REWARD_CANONICAL_POLICY_CODE");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String prop = System.getProperty("app.reward.canonical-policy-code");
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        if (configuredCanonicalPolicyCode != null && !configuredCanonicalPolicyCode.isBlank()) {
            return configuredCanonicalPolicyCode.trim();
        }
        return null;
    }

    /**
     * DEC-10.7A & GAP-1 PO APPROVAL GATE:
     * Resolves the PO-approved max coin redemption percentage.
     * No default — must be explicitly configured AND approved by PO.
     * Throws IllegalStateException (fail-closed) if absent, invalid, or if PO decision is OPEN.
     * policy_snapshot.max_redemption_percentage is NOT used here — it is informational only.
     */
    public BigDecimal resolveMaxRedemptionPercentage() {
        String raw = null;
        String env = System.getenv("REWARD_MAX_REDEMPTION_PERCENTAGE");
        if (env != null && !env.isBlank()) {
            raw = env.trim();
        } else if (configuredMaxRedemptionPercentage != null && !configuredMaxRedemptionPercentage.isBlank()) {
            raw = configuredMaxRedemptionPercentage.trim();
        }
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(
                "Cannot process coin redemption: Coin redemption cap (REWARD_MAX_REDEMPTION_PERCENTAGE) is not configured. " +
                "PO approval and explicit DevOps configuration are required before Coin checkout can proceed (DEC-10.7A)."
            );
        }
        BigDecimal pct;
        try {
            pct = new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                "Cannot process coin redemption: REWARD_MAX_REDEMPTION_PERCENTAGE is not a valid number: '" + raw + "' (DEC-10.7A)."
            );
        }
        if (pct.compareTo(BigDecimal.ZERO) <= 0 || pct.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalStateException(
                "Cannot process coin redemption: REWARD_MAX_REDEMPTION_PERCENTAGE must be in range (0, 100], got: " + raw + " (DEC-10.7A)."
            );
        }

        // GAP-1: PO Approval Gate.
        // Even if DevOps has provided a technical value, it CANNOT be used until PO decision is formally APPROVED.
        if (!capPoApproved) {
            throw new IllegalStateException(
                "Cannot process coin redemption: PO decision on coin redemption cap (max_redemption_percentage) is currently OPEN. " +
                "No cap percentage has been approved by PO. DevOps configuration ('" + raw + "') cannot bypass the PO Approval Gate (DEC-10.7A)."
            );
        }

        return pct;
    }
}
