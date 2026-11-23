package com.oldbutgold.shop.modules.commerce.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductRepository;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductRevisionEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductRevisionRepository;
import com.oldbutgold.shop.modules.commerce.api.VoucherDtos;
import com.oldbutgold.shop.modules.commerce.application.GroupCheckoutDtos.*;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.*;
import com.oldbutgold.shop.modules.communication.application.NegotiatedCheckoutFacade;
import com.oldbutgold.shop.modules.identity.application.IdentityCommerceFacade;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.RewardPolicyEntity;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.RewardPolicyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class GroupCheckoutService {

    private static final Logger log = LoggerFactory.getLogger(GroupCheckoutService.class);
    public static final BigDecimal STANDARD_SHIPPING_FEE = new BigDecimal("30000");

    private final ProductRepository productRepository;
    private final ProductRevisionRepository productRevisionRepository;
    private final CheckoutGroupRepository checkoutGroupRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final RewardPolicyRepository rewardPolicyRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final IdentityCommerceFacade identityCommerceFacade;
    private final VoucherService voucherService;
    private final NegotiatedCheckoutFacade negotiatedCheckoutFacade;

    @Value("${app.reward.canonical-policy-code:}")
    private String configuredCanonicalPolicyCode;

    @Value("${app.reward.max-redemption-percentage:}")
    private String configuredMaxRedemptionPercentage;

    // GAP-1 / DEC-10.7A: PO Approval Gate for Coin Redemption Cap.
    // Default is false (PO decision on max_redemption_percentage is currently OPEN).
    // DevOps configuration alone CANNOT open Coin checkout while this gate is closed.
    private boolean capPoApproved = false;

    public GroupCheckoutService(ProductRepository productRepository,
                                ProductRevisionRepository productRevisionRepository,
                                CheckoutGroupRepository checkoutGroupRepository,
                                OrderRepository orderRepository,
                                OrderItemRepository orderItemRepository,
                                InventoryReservationRepository inventoryReservationRepository,
                                RewardPolicyRepository rewardPolicyRepository,
                                JdbcTemplate jdbcTemplate,
                                Clock clock,
                                ObjectMapper objectMapper) {
        this(productRepository, productRevisionRepository, checkoutGroupRepository,
                orderRepository, orderItemRepository, inventoryReservationRepository,
                rewardPolicyRepository, jdbcTemplate, clock, objectMapper, null, null, null);
    }

    public GroupCheckoutService(ProductRepository productRepository,
                                ProductRevisionRepository productRevisionRepository,
                                CheckoutGroupRepository checkoutGroupRepository,
                                OrderRepository orderRepository,
                                OrderItemRepository orderItemRepository,
                                InventoryReservationRepository inventoryReservationRepository,
                                RewardPolicyRepository rewardPolicyRepository,
                                JdbcTemplate jdbcTemplate,
                                Clock clock,
                                ObjectMapper objectMapper,
                                IdentityCommerceFacade identityCommerceFacade) {
        this(productRepository, productRevisionRepository, checkoutGroupRepository,
                orderRepository, orderItemRepository, inventoryReservationRepository,
                rewardPolicyRepository, jdbcTemplate, clock, objectMapper, identityCommerceFacade, null, null);
    }

    @Autowired
    public GroupCheckoutService(ProductRepository productRepository,
                                ProductRevisionRepository productRevisionRepository,
                                CheckoutGroupRepository checkoutGroupRepository,
                                OrderRepository orderRepository,
                                OrderItemRepository orderItemRepository,
                                InventoryReservationRepository inventoryReservationRepository,
                                RewardPolicyRepository rewardPolicyRepository,
                                @Autowired(required = false) JdbcTemplate jdbcTemplate,
                                Clock clock,
                                ObjectMapper objectMapper,
                                @Autowired(required = false) IdentityCommerceFacade identityCommerceFacade,
                                @Autowired(required = false) VoucherService voucherService,
                                @Autowired(required = false) NegotiatedCheckoutFacade negotiatedCheckoutFacade) {
        this.productRepository = productRepository;
        this.productRevisionRepository = productRevisionRepository;
        this.checkoutGroupRepository = checkoutGroupRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryReservationRepository = inventoryReservationRepository;
        this.rewardPolicyRepository = rewardPolicyRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.identityCommerceFacade = identityCommerceFacade;
        this.voucherService = voucherService;
        this.negotiatedCheckoutFacade = negotiatedCheckoutFacade;
    }

    public GroupCheckoutResponse checkout(long buyerId, GroupCheckoutRequest request) {
        return executeCheckout(buyerId, request).response();
    }

    public GroupCheckoutExecutionResult executeCheckout(long buyerId, GroupCheckoutRequest request) {
        if (request.commandKey() == null || request.commandKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "commandKey là bắt buộc.");
        }
        String normalizedKey = request.commandKey().trim();
        if (normalizedKey.length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "commandKey không được vượt quá 150 ký tự.");
        }

        String requestDigest = computeRequestDigest(buyerId, request);

        // 1. Check Idempotency via existing checkout group
        Optional<CheckoutGroupEntity> existingGroupOpt = checkoutGroupRepository.findByCommandKey(normalizedKey);
        if (existingGroupOpt.isPresent()) {
            CheckoutGroupEntity existingGroup = existingGroupOpt.get();
            if (!Objects.equals(existingGroup.getBuyerId(), buyerId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã thao tác (commandKey) đã thuộc về tài khoản khác.");
            }
            if (!Objects.equals(requestDigest, existingGroup.getRequestDigest())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã thao tác (commandKey) đã được sử dụng với thông tin đặt hàng khác (COMMAND_KEY_CONFLICT)");
            }
            log.info("Idempotent replay: Group checkout already exists for commandKey {}", normalizedKey);
            return new GroupCheckoutExecutionResult(buildResponse(existingGroup), true);
        }

        // Validate delivery method and carrier
        String deliveryMethod = request.deliveryMethod();
        if (!Set.of("CARRIER", "SELF_PICKUP", "SELF_DELIVERY").contains(deliveryMethod)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phương thức giao hàng không hợp lệ: " + deliveryMethod);
        }
        String carrier = null;
        if ("CARRIER".equals(deliveryMethod)) {
            carrier = request.carrier() != null && !request.carrier().isBlank() ? request.carrier().trim() : "GHN";
        }

        // Validate payment method
        String paymentMethod = request.paymentMethod();
        if (!Set.of("VNPAY", "COD_MOCK", "BANK_TRANSFER_MOCK", "E_WALLET_MOCK").contains(paymentMethod)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phương thức thanh toán không hợp lệ: " + paymentMethod);
        }

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant paymentDueAt = now.plus(1, ChronoUnit.HOURS);

        // 2. Validate items & revisions
        if (request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh sách sản phẩm không được để trống.");
        }

        record ValidatedItem(
                ProductEntity product,
                ProductRevisionEntity revision,
                GroupCheckoutItemRequest itemRequest,
                BigDecimal agreedPrice,
                String priceSource,
                BigDecimal lineSubtotal
        ) {}

        List<ValidatedItem> validatedItems = new ArrayList<>();
        BigDecimal groupSubtotal = BigDecimal.ZERO;
        boolean anyRequiresBuyerEkyc = false;

        for (GroupCheckoutItemRequest itemReq : request.items()) {
            ProductEntity product = productRepository.findById(itemReq.productId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm ID: " + itemReq.productId()));

            if (product.getDeletedAt() != null || !ProductEntity.STATUS_ACTIVE.equals(product.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sản phẩm " + product.getTitle() + " không còn mở bán.");
            }
            if (!"UC83".equals(product.getWorkflowModel())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sản phẩm " + product.getTitle() + " không thuộc mô hình UC83.");
            }
            if (identityCommerceFacade != null) {
                identityCommerceFacade.requirePurchaseAllowed(buyerId, product.getSellerId(), product.isRequiresBuyerEkyc());
            }
            if (negotiatedCheckoutFacade != null) {
                negotiatedCheckoutFacade.requireNotBlocked(buyerId, product.getSellerId());
            }
            if (product.isRequiresBuyerEkyc()) {
                anyRequiresBuyerEkyc = true;
            }
            if (product.getQuantityAvailable() < itemReq.quantity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sản phẩm " + product.getTitle() + " không đủ số lượng khả dụng.");
            }
            if (product.getPublicRevisionId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sản phẩm " + product.getTitle() + " chưa có bản sửa đổi công khai.");
            }

            ProductRevisionEntity revision = productRevisionRepository.findById(product.getPublicRevisionId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy bản sửa đổi của sản phẩm ID: " + product.getId()));

            if (revision.isReturnAllowed() != product.getReturnAllowed()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chính sách đổi trả của bản sửa đổi không khớp với sản phẩm.");
            }

            // Check delivery options
            String options = revision.getDeliveryOptions();
            if (options != null && !options.isBlank() && !options.contains(deliveryMethod)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sản phẩm " + product.getTitle() + " không hỗ trợ phương thức nhận hàng " + deliveryMethod);
            }

            // UC24 Alt 1a: Negotiated offer price support via NegotiatedCheckoutFacade
            BigDecimal agreedPrice = revision.getListedUnitPrice();
            String priceSource = "LIST_PRICE";
            if (itemReq.acceptedOfferId() != null && negotiatedCheckoutFacade != null) {
                var agreement = negotiatedCheckoutFacade.bind(buyerId, product.getId(), itemReq.acceptedOfferId(), requestDigest);
                agreedPrice = agreement.price();
                priceSource = "NEGOTIATED_OFFER";
            }

            BigDecimal lineSubtotal = agreedPrice.multiply(BigDecimal.valueOf(itemReq.quantity()));
            groupSubtotal = groupSubtotal.add(lineSubtotal);

            validatedItems.add(new ValidatedItem(product, revision, itemReq, agreedPrice, priceSource, lineSubtotal));
        }

        // UC24 / UC08: Fail-closed Buyer eKYC + Quick Face Auth guard for high-value items
        if (anyRequiresBuyerEkyc) {
            if (identityCommerceFacade == null) {
                throw new IllegalStateException("Không thể xác minh trạng thái eKYC cho sản phẩm yêu cầu xác thực.");
            }
            if (!identityCommerceFacade.isUserVerified(buyerId)) {
                throw new IllegalStateException("Đơn hàng có sản phẩm yêu cầu Người mua phải hoàn tất xác thực eKYC CCCD để đặt mua.");
            }
            identityCommerceFacade.requireQuickAuthResult(buyerId, request.quickAuthResultId());
        }

        // 3. Partition items by (sellerId, returnAllowed) and compute Shipping Fees first (needed for shipping vouchers)
        record PartitionKey(Long sellerId, boolean returnAllowed) {}
        Map<PartitionKey, List<ValidatedItem>> partitions = new LinkedHashMap<>();
        for (ValidatedItem item : validatedItems) {
            PartitionKey key = new PartitionKey(item.product().getSellerId(), item.revision().isReturnAllowed());
            partitions.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }

        List<PartitionKey> partitionKeys = new ArrayList<>(partitions.keySet());
        Map<PartitionKey, BigDecimal> orderSubtotals = new LinkedHashMap<>();
        for (PartitionKey key : partitionKeys) {
            BigDecimal subtotal = partitions.get(key).stream()
                    .map(ValidatedItem::lineSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            orderSubtotals.put(key, subtotal);
        }

        // DEC-08 Option A Shipping Calculation
        Map<PartitionKey, BigDecimal> partitionShippingFees = new LinkedHashMap<>();
        if ("CARRIER".equals(deliveryMethod)) {
            Map<Long, List<PartitionKey>> sellerPartitions = new LinkedHashMap<>();
            for (PartitionKey key : partitionKeys) {
                sellerPartitions.computeIfAbsent(key.sellerId(), k -> new ArrayList<>()).add(key);
            }
            for (Map.Entry<Long, List<PartitionKey>> entry : sellerPartitions.entrySet()) {
                List<PartitionKey> keysForSeller = entry.getValue();
                PartitionKey primaryKey = keysForSeller.stream()
                        .filter(PartitionKey::returnAllowed)
                        .findFirst()
                        .orElse(keysForSeller.get(0));
                for (PartitionKey key : keysForSeller) {
                    if (key.equals(primaryKey)) {
                        partitionShippingFees.put(key, STANDARD_SHIPPING_FEE);
                    } else {
                        partitionShippingFees.put(key, BigDecimal.ZERO);
                    }
                }
            }
        } else {
            for (PartitionKey key : partitionKeys) {
                partitionShippingFees.put(key, BigDecimal.ZERO);
            }
        }
        BigDecimal groupShippingFee = partitionShippingFees.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Voucher calculation (BR-UC24-03: apply Voucher BEFORE Coins; supports goods & shipping vouchers)
        BigDecimal groupVoucherDiscount = BigDecimal.ZERO;
        BigDecimal groupShippingDiscount = BigDecimal.ZERO;
        VoucherDtos.Grant ownedVoucherGrant = null;
        if (request.voucherCode() != null && !request.voucherCode().isBlank() && voucherService != null) {
            ValidatedItem primaryItem = validatedItems.getFirst();
            voucherService.lockCheckout(buyerId, primaryItem.product().getSellerId());
            ownedVoucherGrant = voucherService.resolve(
                    buyerId,
                    request.voucherCode().trim(),
                    primaryItem.product().getId(),
                    groupSubtotal,
                    true
            );
            VoucherDtos.Campaign campaign = ownedVoucherGrant.campaign();
            if ("SHIPPING_DISCOUNT".equalsIgnoreCase(campaign.voucherType())) {
                groupShippingDiscount = calculateVoucherDiscount(campaign, groupShippingFee);
            } else {
                groupVoucherDiscount = calculateVoucherDiscount(campaign, groupSubtotal);
            }
        }

        // 5. Coin redemption calculation (DEC-10.7A fail-closed; Voucher-first priority; auto-clamp excess coins so user never loses coins)
        Long coinsRequested = request.coinsRequested();
        BigDecimal groupPointsDiscount = BigDecimal.ZERO;
        BigDecimal groupPointsGoodsDiscount = BigDecimal.ZERO;
        BigDecimal groupPointsShippingDiscount = BigDecimal.ZERO;
        Long coinsToRedeem = 0L;
        Long rewardPolicyId = null;

        BigDecimal remainingGoodsAfterVoucher = groupSubtotal.subtract(groupVoucherDiscount).max(BigDecimal.ZERO);
        BigDecimal remainingShippingAfterVoucher = groupShippingFee.subtract(groupShippingDiscount).max(BigDecimal.ZERO);
        BigDecimal maxPayableAfterVoucher = remainingGoodsAfterVoucher.add(remainingShippingAfterVoucher);

        if (coinsRequested != null && coinsRequested > 0) {
            BigDecimal capPct = resolveMaxRedemptionPercentage(); // Fail closed if unconfigured
            String canonicalPolicyCode = resolveCanonicalRewardPolicyCode(); // Fail closed if unconfigured
            if (canonicalPolicyCode == null || canonicalPolicyCode.isBlank()) {
                throw new IllegalStateException("Cannot process coin redemption: REWARD_CANONICAL_POLICY_CODE is not configured.");
            }

            RewardPolicyEntity policy = rewardPolicyRepository.findByPolicyCode(canonicalPolicyCode)
                    .orElseThrow(() -> new IllegalStateException("Canonical reward policy not found: " + canonicalPolicyCode));
            rewardPolicyId = policy.getPolicyId();
            BigDecimal pointValue = policy.getPointValue() != null ? policy.getPointValue() : BigDecimal.ONE;

            // Check buyer balance
            long balance = 0L;
            if (jdbcTemplate != null) {
                try {
                    Long b = jdbcTemplate.queryForObject("SELECT balance FROM og_compat.reward_accounts WHERE user_id = ?", Long.class, buyerId);
                    balance = b != null ? b : 0L;
                } catch (Exception ex) {
                    try {
                        Long b = jdbcTemplate.queryForObject("SELECT COALESCE(sum(points_delta),0) FROM og_compat.reward_ledger WHERE user_id = ?", Long.class, buyerId);
                        balance = b != null ? b : 0L;
                    } catch (Exception ignored) {}
                }
            }

            if (coinsRequested > balance) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số dư xu không đủ để thực hiện thanh toán.");
            }

            long maxRedeemableCoins = maxPayableAfterVoucher
                    .multiply(capPct)
                    .divide(new BigDecimal("100"), 0, RoundingMode.FLOOR)
                    .divide(pointValue, 0, RoundingMode.FLOOR)
                    .longValue();

            // Auto-adjust (clamp) coins when Voucher reduced remaining payable, so buyer never wastes or loses coins
            coinsToRedeem = Math.min(coinsRequested, Math.max(0L, maxRedeemableCoins));
            if (coinsToRedeem < coinsRequested) {
                log.info("Auto-adjusted coinsRequested from {} to {} after voucher priority (maxPayableAfterVoucher={}, capPct={}%)",
                        coinsRequested, coinsToRedeem, maxPayableAfterVoucher, capPct);
            }
            if (coinsToRedeem == 0L) {
                rewardPolicyId = null;
            } else {
                groupPointsDiscount = BigDecimal.valueOf(coinsToRedeem).multiply(pointValue);
                // Priority: offset remaining goods first, then remaining shipping fee
                groupPointsGoodsDiscount = groupPointsDiscount.min(remainingGoodsAfterVoucher);
                groupPointsShippingDiscount = groupPointsDiscount.subtract(groupPointsGoodsDiscount).min(remainingShippingAfterVoucher);
            }
        }

        // 6. Largest Remainder Method across Orders
        Map<PartitionKey, BigDecimal> orderVoucherDiscounts = allocateLargestRemainder(
                groupVoucherDiscount,
                partitionKeys,
                orderSubtotals,
                orderSubtotals
        );

        Map<PartitionKey, BigDecimal> orderShippingDiscounts = allocateLargestRemainder(
                groupShippingDiscount,
                partitionKeys,
                partitionShippingFees,
                partitionShippingFees
        );

        Map<PartitionKey, BigDecimal> orderGoodsAfterVoucher = new LinkedHashMap<>();
        Map<PartitionKey, BigDecimal> orderShippingAfterVoucher = new LinkedHashMap<>();
        for (PartitionKey key : partitionKeys) {
            orderGoodsAfterVoucher.put(key, orderSubtotals.get(key).subtract(orderVoucherDiscounts.get(key)));
            orderShippingAfterVoucher.put(key, partitionShippingFees.get(key).subtract(orderShippingDiscounts.get(key)));
        }

        Map<PartitionKey, BigDecimal> orderPointsDiscounts = allocateLargestRemainder(
                groupPointsGoodsDiscount,
                partitionKeys,
                orderGoodsAfterVoucher,
                orderGoodsAfterVoucher
        );

        Map<PartitionKey, BigDecimal> orderPointsShippingDiscounts = allocateLargestRemainder(
                groupPointsShippingDiscount,
                partitionKeys,
                orderShippingAfterVoucher,
                orderShippingAfterVoucher
        );

        // 7. Save CheckoutGroupEntity
        UUID groupId = UUID.randomUUID();
        String addressJson = buildAddressSnapshotJson(request.shippingAddress());
        BigDecimal expectedTotal = groupSubtotal
                .add(groupShippingFee)
                .subtract(groupVoucherDiscount)
                .subtract(groupShippingDiscount)
                .subtract(groupPointsDiscount);

        CheckoutGroupEntity group = new CheckoutGroupEntity(
                groupId,
                buyerId,
                normalizedKey,
                requestDigest,
                addressJson,
                deliveryMethod,
                carrier,
                paymentMethod,
                expectedTotal,
                CheckoutGroupEntity.STATE_READY,
                now,
                paymentDueAt,
                rewardPolicyId
        );
        checkoutGroupRepository.saveAndFlush(group);

        // 8. Create Orders & Order Items
        List<OrderEntity> savedOrders = new ArrayList<>();
        List<OrderItemEntity> savedItems = new ArrayList<>();
        List<InventoryReservationEntity> savedReservations = new ArrayList<>();
        Map<Long, List<OrderItemEntity>> itemsByOrderId = new LinkedHashMap<>();

        LocalDate handoverDay = LocalDate.now().plusDays(2);

        for (int orderIndex = 0; orderIndex < partitionKeys.size(); orderIndex++) {
            PartitionKey key = partitionKeys.get(orderIndex);
            List<ValidatedItem> itemsInPartition = partitions.get(key);

            BigDecimal orderSubtotal = orderSubtotals.get(key);
            BigDecimal orderVoucher = orderVoucherDiscounts.get(key);
            BigDecimal orderPoints = orderPointsDiscounts.get(key);
            BigDecimal orderPointsShipping = orderPointsShippingDiscounts.get(key);
            BigDecimal orderShippingFee = partitionShippingFees.get(key);
            BigDecimal orderShippingDiscount = orderShippingDiscounts.get(key);
            BigDecimal orderTotal = orderSubtotal
                    .add(orderShippingFee)
                    .subtract(orderVoucher)
                    .subtract(orderPoints)
                    .subtract(orderShippingDiscount)
                    .subtract(orderPointsShipping);

            OrderEntity order = new OrderEntity(
                    groupId, // checkoutGroupId
                    buyerId,
                    key.sellerId(),
                    null, // sourceAddressId
                    request.shippingAddress().recipientName(),
                    request.shippingAddress().phoneNumber(),
                    request.shippingAddress().province(),
                    request.shippingAddress().district(),
                    request.shippingAddress().ward(),
                    request.shippingAddress().detailAddress(),
                    null, // shippingGhnProvinceId
                    null, // shippingGhnDistrictId
                    null, // shippingGhnWardCode
                    orderSubtotal,
                    orderShippingFee,
                    BigDecimal.ZERO, // buyerSystemFee
                    BigDecimal.ZERO, // sellerSystemFee
                    orderSubtotal, // sellerProceeds = subtotal in UC83
                    orderVoucher,
                    orderShippingDiscount,
                    orderPoints,
                    "PLATFORM", // sponsorType
                    orderTotal,
                    paymentDueAt,
                    now,
                    "UC83",
                    groupId,
                    key.returnAllowed(),
                    deliveryMethod,
                    handoverDay
            );
            order.setPointsShippingDiscountAmount(orderPointsShipping);
            order.setCarrierSnapshot(carrier);
            order.setPaymentDueAt(paymentDueAt);
            order.setCreatedAt(now);
            order = orderRepository.saveAndFlush(order);
            savedOrders.add(order);
            List<OrderItemEntity> orderSavedItems = new ArrayList<>();

            // Item-level Largest Remainder allocation
            List<Integer> itemIndices = new ArrayList<>();
            Map<Integer, BigDecimal> itemSubtotals = new LinkedHashMap<>();
            for (int i = 0; i < itemsInPartition.size(); i++) {
                itemIndices.add(i);
                itemSubtotals.put(i, itemsInPartition.get(i).lineSubtotal());
            }

            Map<Integer, BigDecimal> itemVoucherAllocations = allocateLargestRemainder(
                    orderVoucher,
                    itemIndices,
                    itemSubtotals,
                    itemSubtotals
            );

            Map<Integer, BigDecimal> itemGoodsAfterVoucher = new LinkedHashMap<>();
            for (int i = 0; i < itemsInPartition.size(); i++) {
                itemGoodsAfterVoucher.put(i, itemSubtotals.get(i).subtract(itemVoucherAllocations.get(i)));
            }

            Map<Integer, BigDecimal> itemPointsAllocations = allocateLargestRemainder(
                    orderPoints,
                    itemIndices,
                    itemGoodsAfterVoucher,
                    itemGoodsAfterVoucher
            );

            for (int i = 0; i < itemsInPartition.size(); i++) {
                ValidatedItem vi = itemsInPartition.get(i);
                BigDecimal vAlloc = itemVoucherAllocations.get(i);
                BigDecimal pAlloc = itemPointsAllocations.get(i);
                BigDecimal buyerLineTotal = vi.lineSubtotal().subtract(vAlloc).subtract(pAlloc);
                BigDecimal sellerLineProceeds = vi.lineSubtotal();

                OrderItemEntity orderItem = new OrderItemEntity(
                        order.getId(),
                        vi.product().getId(),
                        vi.product().getTitle(),
                        vi.itemRequest().quantity(),
                        vi.revision().getListedUnitPrice(),
                        vi.agreedPrice(),
                        BigDecimal.ZERO, // buyerSystemFee = 0
                        BigDecimal.ZERO, // sellerSystemFee = 0
                        buyerLineTotal,
                        sellerLineProceeds,
                        null, // feePolicyId = null
                        vi.itemRequest().acceptedOfferId(),
                        vi.priceSource(),
                        vAlloc,
                        pAlloc,
                        "UC83",
                        vi.revision().getRevisionId(),
                        vi.revision().isReturnAllowed()
                );
                orderItem = orderItemRepository.saveAndFlush(orderItem);
                savedItems.add(orderItem);
                orderSavedItems.add(orderItem);

                if (vi.itemRequest().acceptedOfferId() != null && negotiatedCheckoutFacade != null) {
                    negotiatedCheckoutFacade.linkOrder(buyerId, vi.itemRequest().acceptedOfferId(), order.getId());
                }

                // Create Inventory Reservation
                InventoryReservationEntity reservation = new InventoryReservationEntity(
                        UUID.randomUUID(),
                        vi.product().getId(),
                        orderItem.getId(),
                        orderItem.getQuantity(),
                        InventoryReservationEntity.STATE_HELD,
                        now,
                        paymentDueAt,
                        "RESERVE:" + groupId + ":" + orderItem.getId()
                );
                reservation = inventoryReservationRepository.saveAndFlush(reservation);
                savedReservations.add(reservation);
            }
            itemsByOrderId.put(order.getId(), orderSavedItems);
        }

        // 8b. Persist Voucher Redemption & Discount Allocations (conserving V53 og_validate_discount_totals)
        BigDecimal totalVoucherAndShippingDiscount = groupVoucherDiscount.add(groupShippingDiscount);
        if (ownedVoucherGrant != null && voucherService != null && !savedOrders.isEmpty()) {
            voucherService.bindOrder(savedOrders.getFirst().getId(), ownedVoucherGrant);
            if (jdbcTemplate != null && totalVoucherAndShippingDiscount.compareTo(BigDecimal.ZERO) > 0) {
                UUID redemptionId = UUID.randomUUID();
                jdbcTemplate.update("""
                        INSERT INTO og_compat.checkout_redemptions(
                            redemption_id, group_id, voucher_id, discount_amount, redeemed_at, command_key
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        """,
                        redemptionId,
                        groupId,
                        ownedVoucherGrant.campaign().id(),
                        totalVoucherAndShippingDiscount,
                        java.sql.Timestamp.from(now),
                        "VOUCHER_REDEEM:" + groupId
                );

                for (OrderEntity order : savedOrders) {
                    List<OrderItemEntity> orderItems = itemsByOrderId.getOrDefault(order.getId(), List.of());
                    for (OrderItemEntity item : orderItems) {
                        if (item.getVoucherAllocation() != null && item.getVoucherAllocation().compareTo(BigDecimal.ZERO) > 0) {
                            jdbcTemplate.update("""
                                    INSERT INTO og_compat.discount_allocations(
                                        allocation_id, order_item_id, redemption_id, component, amount
                                    ) VALUES (?, ?, ?, 'GOODS', ?)
                                    """,
                                    UUID.randomUUID(),
                                    item.getId(),
                                    redemptionId,
                                    item.getVoucherAllocation()
                            );
                        }
                    }
                    if (order.getShippingDiscountAmount() != null
                            && order.getShippingDiscountAmount().compareTo(BigDecimal.ZERO) > 0
                            && !orderItems.isEmpty()) {
                        jdbcTemplate.update("""
                                INSERT INTO og_compat.discount_allocations(
                                    allocation_id, order_item_id, redemption_id, component, amount
                                ) VALUES (?, ?, ?, 'SHIPPING', ?)
                                """,
                                UUID.randomUUID(),
                                orderItems.getFirst().getId(),
                                redemptionId,
                                order.getShippingDiscountAmount()
                        );
                    }
                }
            }
        }

        // 9. UC24 Alt 7a / UC25 Alt 2a / BR-UC25-06: Zero-VND fast-path (no cash gateway / VNPay transaction)
        if (expectedTotal.compareTo(BigDecimal.ZERO) == 0) {
            finalizeZeroAmountGroup(
                    group,
                    savedOrders,
                    itemsByOrderId,
                    savedReservations,
                    groupPointsDiscount,
                    coinsToRedeem,
                    rewardPolicyId,
                    now
            );
        }

        // DEC-10.4: For expectedTotal > 0, T1 READY only pins rewardPolicyId and snapshots points discounts.
        // MUST NOT create DEBIT_CHECKOUT or discount_allocations in T1.
        // Ledger debit and allocations occur strictly in T2 upon payment confirmation.

        return new GroupCheckoutExecutionResult(buildResponse(group), false);
    }

    private void finalizeZeroAmountGroup(
            CheckoutGroupEntity group,
            List<OrderEntity> savedOrders,
            Map<Long, List<OrderItemEntity>> itemsByOrderId,
            List<InventoryReservationEntity> savedReservations,
            BigDecimal groupPointsDiscount,
            Long coinsToRedeem,
            Long rewardPolicyId,
            Instant now
    ) {
        UUID groupId = group.getGroupId();
        java.sql.Timestamp nowTs = java.sql.Timestamp.from(now);
        java.sql.Timestamp sellerAcceptDueTs = java.sql.Timestamp.from(now.plus(2, ChronoUnit.DAYS));

        if (jdbcTemplate != null) {
            // 0. Insert canonical 0-VND confirmation proof (required by og_validate_order_workflow_proof)
            UUID zeroConfirmationId = UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO og_compat.zero_checkout_confirmations(
                        confirmation_id, group_id, buyer_id, confirmed_at, command_key
                    ) VALUES (?, ?, ?, ?, ?)
                    """,
                    zeroConfirmationId,
                    groupId,
                    group.getBuyerId(),
                    nowTs,
                    "ZERO_CONFIRM:" + groupId
            );

            // 1. Insert canonical platform subsidy fund components for 0-VND orders
            // (GOODS_CASH / SHIPPING_CASH require allocation_id IS NOT NULL and are omitted when no cash payment occurs)
            for (OrderEntity order : savedOrders) {
                BigDecimal goodsSubsidy = order.getVoucherDiscountAmount().add(order.getPointsDiscountAmount());
                BigDecimal shipSubsidy = order.getShippingDiscountAmount().add(order.getPointsShippingDiscountAmount());

                jdbcTemplate.update("""
                        INSERT INTO og_compat.order_fund_components(
                            component_id, order_id, allocation_id, late_confirmation_id, component_type,
                            confirmed_amount, funding_reference, confirmed_at
                        ) VALUES (?, ?, NULL, NULL, 'PLATFORM_GOODS_SUBSIDY', ?, 'PLATFORM_PROMOTION', ?)
                        """, UUID.randomUUID(), order.getId(), goodsSubsidy, nowTs);

                if (shipSubsidy.compareTo(BigDecimal.ZERO) > 0) {
                    jdbcTemplate.update("""
                            INSERT INTO og_compat.order_fund_components(
                                component_id, order_id, allocation_id, late_confirmation_id, component_type,
                                confirmed_amount, funding_reference, confirmed_at
                            ) VALUES (?, ?, NULL, NULL, 'PLATFORM_SHIPPING_SUBSIDY', ?, 'PLATFORM_SHIPPING', ?)
                            """, UUID.randomUUID(), order.getId(), shipSubsidy, nowTs);
                }
            }

            // 2. Debit coins & record discount_allocations (GOODS + SHIPPING) for 0-VND checkout
            if (groupPointsDiscount.compareTo(BigDecimal.ZERO) > 0 && rewardPolicyId != null && coinsToRedeem != null && coinsToRedeem > 0) {
                UUID rewardEntryId = UUID.randomUUID();
                jdbcTemplate.update("""
                        INSERT INTO og_compat.reward_ledger(
                            entry_id, user_id, policy_id, entry_type, points_delta, group_id, actor_id, reason, command_key, created_at
                        ) VALUES (?, ?, ?, 'DEBIT_CHECKOUT', ?, ?, NULL, ?, ?, ?)
                        """,
                        rewardEntryId,
                        group.getBuyerId(),
                        rewardPolicyId,
                        -coinsToRedeem,
                        groupId,
                        "Khấu trừ xu đặt hàng 0 đồng nhóm " + groupId,
                        "REWARD_DEBIT:" + groupId,
                        nowTs
                );

                for (OrderEntity order : savedOrders) {
                    List<OrderItemEntity> orderItems = itemsByOrderId.getOrDefault(order.getId(), List.of());
                    for (OrderItemEntity item : orderItems) {
                        if (item.getPointsAllocation() != null && item.getPointsAllocation().compareTo(BigDecimal.ZERO) > 0) {
                            jdbcTemplate.update("""
                                    INSERT INTO og_compat.discount_allocations(
                                        allocation_id, order_item_id, reward_entry_id, component, amount
                                    ) VALUES (?, ?, ?, 'GOODS', ?)
                                    """,
                                    UUID.randomUUID(),
                                    item.getId(),
                                    rewardEntryId,
                                    item.getPointsAllocation()
                            );
                        }
                    }
                    if (order.getPointsShippingDiscountAmount().compareTo(BigDecimal.ZERO) > 0 && !orderItems.isEmpty()) {
                        jdbcTemplate.update("""
                                INSERT INTO og_compat.discount_allocations(
                                    allocation_id, order_item_id, reward_entry_id, component, amount
                                ) VALUES (?, ?, ?, 'SHIPPING', ?)
                                """,
                                UUID.randomUUID(),
                                orderItems.getFirst().getId(),
                                rewardEntryId,
                                order.getPointsShippingDiscountAmount()
                        );
                    }
                }
            }

            // 3. Transition orders to PAID_HELD using exact confirmed_at from zero_checkout_confirmations
            for (OrderEntity order : savedOrders) {
                jdbcTemplate.update("""
                        UPDATE og_compat.orders
                        SET status = 'PAID_HELD',
                            paid_confirmed_at = (SELECT confirmed_at FROM og_compat.zero_checkout_confirmations WHERE confirmation_id = ?),
                            seller_accept_due_at = (SELECT confirmed_at + INTERVAL '2 days' FROM og_compat.zero_checkout_confirmations WHERE confirmation_id = ?)
                        WHERE order_id = ?
                        """, zeroConfirmationId, zeroConfirmationId, order.getId());
            }
        }

        // 4. Transition reservations to FUNDED
        for (InventoryReservationEntity res : savedReservations) {
            res.setState("FUNDED");
            res.setChangedAt(now);
            inventoryReservationRepository.saveAndFlush(res);
        }

        // 5. Transition CheckoutGroup to PAID
        group.setState(CheckoutGroupEntity.STATE_PAID);
        checkoutGroupRepository.saveAndFlush(group);
        log.info("Zero-VND group checkout {} finalized immediately to PAID without VNPay transaction.", groupId);
    }

    private BigDecimal calculateVoucherDiscount(VoucherDtos.Campaign voucher, BigDecimal baseAmount) {
        if (voucher == null || baseAmount == null || baseAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
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
        BigDecimal capped = voucher.maxDiscountAmount() == null ? baseAmount : baseAmount.min(voucher.maxDiscountAmount());
        return discount.min(capped).max(BigDecimal.ZERO);
    }

    private <K> Map<K, BigDecimal> allocateLargestRemainder(
            BigDecimal totalAmountToAllocate,
            List<K> keys,
            Map<K, BigDecimal> weights,
            Map<K, BigDecimal> maxCaps
    ) {
        Map<K, BigDecimal> result = new LinkedHashMap<>();
        for (K key : keys) {
            result.put(key, BigDecimal.ZERO);
        }

        if (totalAmountToAllocate == null || totalAmountToAllocate.compareTo(BigDecimal.ZERO) <= 0 || keys.isEmpty()) {
            return result;
        }

        BigDecimal totalWeight = keys.stream()
                .map(weights::get)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return result;
        }

        record AllocCandidate<T>(T key, BigDecimal floorAmount, BigDecimal remainder, int originalIndex) {}
        List<AllocCandidate<K>> candidates = new ArrayList<>();
        BigDecimal sumFloors = BigDecimal.ZERO;

        for (int i = 0; i < keys.size(); i++) {
            K key = keys.get(i);
            BigDecimal w = weights.get(key);
            BigDecimal exact = totalAmountToAllocate.multiply(w).divide(totalWeight, 4, RoundingMode.HALF_UP);
            BigDecimal floor = exact.setScale(0, RoundingMode.FLOOR);
            BigDecimal cap = maxCaps.get(key);
            if (cap != null && floor.compareTo(cap) > 0) {
                floor = cap;
            }
            BigDecimal rem = exact.subtract(floor);
            sumFloors = sumFloors.add(floor);
            candidates.add(new AllocCandidate<>(key, floor, rem, i));
        }

        BigDecimal remainingUnits = totalAmountToAllocate.subtract(sumFloors);

        // Sort by remainder DESC, then originalIndex ASC (tie-break order_item_id ASC)
        candidates.sort((a, b) -> {
            int cmp = b.remainder().compareTo(a.remainder());
            if (cmp != 0) return cmp;
            return Integer.compare(a.originalIndex(), b.originalIndex());
        });

        int unitsToDistribute = remainingUnits.intValue();
        for (AllocCandidate<K> candidate : candidates) {
            BigDecimal allocated = candidate.floorAmount();
            BigDecimal cap = maxCaps.get(candidate.key());
            if (unitsToDistribute > 0) {
                if (cap == null || allocated.add(BigDecimal.ONE).compareTo(cap) <= 0) {
                    allocated = allocated.add(BigDecimal.ONE);
                    unitsToDistribute--;
                }
            }
            result.put(candidate.key(), allocated);
        }

        return result;
    }

    private GroupCheckoutResponse buildResponse(CheckoutGroupEntity group) {
        List<OrderEntity> orders = orderRepository.findByCheckoutGroupId(group.getGroupId());
        BigDecimal groupSubtotal = BigDecimal.ZERO;
        BigDecimal groupShippingFee = BigDecimal.ZERO;
        BigDecimal groupVoucherDiscount = BigDecimal.ZERO;
        BigDecimal groupPointsDiscount = BigDecimal.ZERO;

        List<OrderResponse> orderResponses = new ArrayList<>();
        for (OrderEntity order : orders) {
            groupSubtotal = groupSubtotal.add(order.getSubtotal());
            groupShippingFee = groupShippingFee.add(order.getShippingFee());
            groupVoucherDiscount = groupVoucherDiscount.add(order.getVoucherDiscountAmount()).add(order.getShippingDiscountAmount());
            groupPointsDiscount = groupPointsDiscount.add(order.getTotalPointsDiscountAmount());

            List<OrderItemEntity> items = orderItemRepository.findByOrderId(order.getId());
            List<OrderItemResponse> itemResponses = items.stream().map(i -> new OrderItemResponse(
                    i.getId(),
                    i.getProductId(),
                    i.getProductTitle(),
                    i.getQuantity(),
                    i.getListedPrice(),
                    i.getAgreedPrice(),
                    i.getVoucherAllocation(),
                    i.getPointsAllocation(),
                    i.getBuyerLineTotal(),
                    i.getSellerLineProceeds(),
                    i.getProductRevisionId(),
                    i.getReturnAllowed()
            )).collect(Collectors.toList());

            orderResponses.add(new OrderResponse(
                    order.getId(),
                    order.getSellerId(),
                    order.getReturnAllowed(),
                    order.getDeliveryMethod(),
                    order.getCarrierSnapshot(),
                    order.getHandoverDay(),
                    order.getSubtotal(),
                    order.getShippingFee(),
                    order.getVoucherDiscountAmount(),
                    order.getPointsDiscountAmount(),
                    order.getTotalAmount(),
                    order.getSellerProceeds(),
                    order.getStatus(),
                    itemResponses
            ));
        }

        long coinsUsed = groupPointsDiscount.longValue();

        return new GroupCheckoutResponse(
                group.getGroupId(),
                group.getBuyerId(),
                group.getCommandKey(),
                group.getState(),
                group.getDeliveryMethod(),
                group.getCarrier(),
                group.getPaymentMethod(),
                group.getExpectedTotal(),
                groupSubtotal,
                groupShippingFee,
                groupVoucherDiscount,
                groupPointsDiscount,
                coinsUsed,
                group.getCreatedAt(),
                group.getPaymentDueAt(),
                group.getRewardPolicyId(),
                orderResponses
        );
    }

    private String buildAddressSnapshotJson(ShippingAddressDto a) {
        try {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("recipient_name", a.recipientName());
            map.put("phone_number", a.phoneNumber());
            map.put("province", a.province());
            map.put("district", a.district());
            map.put("ward", a.ward());
            map.put("detail_address", a.detailAddress());
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize address snapshot", e);
        }
    }

    private String computeRequestDigest(long buyerId, GroupCheckoutRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            sb.append("buyer:").append(buyerId).append(";");
            sb.append("delivery:").append(request.deliveryMethod()).append(";");
            sb.append("carrier:").append(request.carrier()).append(";");
            sb.append("payment:").append(request.paymentMethod()).append(";");
            sb.append("addr:").append(request.shippingAddress().recipientName()).append(",")
                    .append(request.shippingAddress().phoneNumber()).append(",")
                    .append(request.shippingAddress().province()).append(",")
                    .append(request.shippingAddress().district()).append(",")
                    .append(request.shippingAddress().ward()).append(",")
                    .append(request.shippingAddress().detailAddress()).append(";");
            sb.append("coins:").append(request.coinsRequested()).append(";");
            if (request.items() != null) {
                for (GroupCheckoutItemRequest item : request.items()) {
                    sb.append("item:").append(item.productId()).append(":").append(item.quantity())
                            .append(":").append(item.acceptedOfferId()).append(";");
                }
            }
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
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

    public void setConfiguredMaxRedemptionPercentage(String configuredMaxRedemptionPercentage) {
        this.configuredMaxRedemptionPercentage = configuredMaxRedemptionPercentage;
    }

    /** For testing / simulated future PO approval gate transition (GAP-1 / DEC-10.7A). */
    public void setCapPoApproved(boolean capPoApproved) {
        this.capPoApproved = capPoApproved;
    }

    boolean isCapPoApproved() {
        return this.capPoApproved;
    }

    public void setConfiguredCanonicalPolicyCode(String configuredCanonicalPolicyCode) {
        this.configuredCanonicalPolicyCode = configuredCanonicalPolicyCode;
    }
}
