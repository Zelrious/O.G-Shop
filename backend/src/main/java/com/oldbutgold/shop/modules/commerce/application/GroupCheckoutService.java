package com.oldbutgold.shop.modules.commerce.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductRepository;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductRevisionEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductRevisionRepository;
import com.oldbutgold.shop.modules.commerce.application.GroupCheckoutDtos.*;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.*;
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
                rewardPolicyRepository, jdbcTemplate, clock, objectMapper, null);
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
                                @Autowired(required = false) IdentityCommerceFacade identityCommerceFacade) {
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

            BigDecimal agreedPrice = revision.getListedUnitPrice();
            BigDecimal lineSubtotal = agreedPrice.multiply(BigDecimal.valueOf(itemReq.quantity()));
            groupSubtotal = groupSubtotal.add(lineSubtotal);

            validatedItems.add(new ValidatedItem(product, revision, itemReq, agreedPrice, lineSubtotal));
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

        // 3. Voucher calculation
        BigDecimal groupVoucherDiscount = BigDecimal.ZERO;
        // In this phase, if voucherCode provided, check if repository exists or default to 0
        // (Voucher logic can be applied if voucherCode is passed and found)

        // 4. Coin redemption calculation (DEC-10.7A fail-closed)
        Long coinsRequested = request.coinsRequested();
        BigDecimal groupPointsDiscount = BigDecimal.ZERO;
        Long coinsToRedeem = 0L;
        Long rewardPolicyId = null;

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

            BigDecimal maxGoodsRedemption = groupSubtotal.subtract(groupVoucherDiscount).max(BigDecimal.ZERO);
            long maxRedeemableCoins = maxGoodsRedemption
                    .multiply(capPct)
                    .divide(new BigDecimal("100"), 0, RoundingMode.FLOOR)
                    .longValue();

            if (coinsRequested > maxRedeemableCoins) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số xu sử dụng (" + coinsRequested + ") vượt quá giá trị thanh toán cho hàng hóa (" + maxRedeemableCoins + " xu).");
            }

            coinsToRedeem = coinsRequested;
            groupPointsDiscount = BigDecimal.valueOf(coinsToRedeem).multiply(pointValue);
        }

        // 5. Partition items by (sellerId, returnAllowed)
        record PartitionKey(Long sellerId, boolean returnAllowed) {}
        Map<PartitionKey, List<ValidatedItem>> partitions = new LinkedHashMap<>();
        for (ValidatedItem item : validatedItems) {
            PartitionKey key = new PartitionKey(item.product().getSellerId(), item.revision().isReturnAllowed());
            partitions.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }

        // 6. Largest Remainder Method across Orders
        List<PartitionKey> partitionKeys = new ArrayList<>(partitions.keySet());
        Map<PartitionKey, BigDecimal> orderSubtotals = new LinkedHashMap<>();
        for (PartitionKey key : partitionKeys) {
            BigDecimal subtotal = partitions.get(key).stream()
                    .map(ValidatedItem::lineSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            orderSubtotals.put(key, subtotal);
        }

        Map<PartitionKey, BigDecimal> orderVoucherDiscounts = allocateLargestRemainder(
                groupVoucherDiscount,
                partitionKeys,
                orderSubtotals,
                orderSubtotals
        );

        Map<PartitionKey, BigDecimal> orderGoodsAfterVoucher = new LinkedHashMap<>();
        for (PartitionKey key : partitionKeys) {
            orderGoodsAfterVoucher.put(key, orderSubtotals.get(key).subtract(orderVoucherDiscounts.get(key)));
        }

        Map<PartitionKey, BigDecimal> orderPointsDiscounts = allocateLargestRemainder(
                groupPointsDiscount,
                partitionKeys,
                orderGoodsAfterVoucher,
                orderGoodsAfterVoucher
        );

        // 7. Save CheckoutGroupEntity
        UUID groupId = UUID.randomUUID();
        String addressJson = buildAddressSnapshotJson(request.shippingAddress());

        // DEC-08 Option A Shipping Calculation:
        // Group partitions by sellerId. Each seller is charged at most one STANDARD_SHIPPING_FEE (if deliveryMethod == "CARRIER").
        // For each seller, the Primary Seller Order (returnAllowed = true) bears the full shipping fee.
        // Other partitions of that seller (returnAllowed = false) get shipping fee = 0.
        // If a seller has no returnable partitions, the remaining partition bears the shipping fee.
        // For non-CARRIER methods (SELF_PICKUP, SELF_DELIVERY), shipping fee is 0.
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
        BigDecimal expectedTotal = groupSubtotal.add(groupShippingFee).subtract(groupVoucherDiscount).subtract(groupPointsDiscount);

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

        LocalDate handoverDay = LocalDate.now().plusDays(2);

        for (int orderIndex = 0; orderIndex < partitionKeys.size(); orderIndex++) {
            PartitionKey key = partitionKeys.get(orderIndex);
            List<ValidatedItem> itemsInPartition = partitions.get(key);

            BigDecimal orderSubtotal = orderSubtotals.get(key);
            BigDecimal orderVoucher = orderVoucherDiscounts.get(key);
            BigDecimal orderPoints = orderPointsDiscounts.get(key);
            BigDecimal orderShippingFee = partitionShippingFees.get(key);
            BigDecimal orderShippingDiscount = BigDecimal.ZERO;
            BigDecimal orderTotal = orderSubtotal.add(orderShippingFee).subtract(orderVoucher).subtract(orderPoints).subtract(orderShippingDiscount);

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
            order.setCarrierSnapshot(carrier);
            order.setPaymentDueAt(paymentDueAt);
            order.setCreatedAt(now);
            order = orderRepository.saveAndFlush(order);
            savedOrders.add(order);

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
                        "LIST_PRICE",
                        vAlloc,
                        pAlloc,
                        "UC83",
                        vi.revision().getRevisionId(),
                        vi.revision().isReturnAllowed()
                );
                orderItem = orderItemRepository.saveAndFlush(orderItem);
                savedItems.add(orderItem);

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
        }

        // DEC-10.4: T1 READY only pins rewardPolicyId and snapshots points discounts.
        // MUST NOT create DEBIT_CHECKOUT or discount_allocations in T1.
        // Ledger debit and allocations occur strictly in T2 upon payment confirmation.

        return new GroupCheckoutExecutionResult(buildResponse(group), false);
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
            groupVoucherDiscount = groupVoucherDiscount.add(order.getVoucherDiscountAmount());
            groupPointsDiscount = groupPointsDiscount.add(order.getPointsDiscountAmount());

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
