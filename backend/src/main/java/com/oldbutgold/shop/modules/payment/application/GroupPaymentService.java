package com.oldbutgold.shop.modules.payment.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.*;
import com.oldbutgold.shop.modules.payment.application.GroupPaymentDtos.*;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.*;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional
public class GroupPaymentService {

    private static final Logger log = LoggerFactory.getLogger(GroupPaymentService.class);

    private final CheckoutGroupRepository checkoutGroupRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentConfirmationRepository paymentConfirmationRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final OrderFundComponentRepository orderFundComponentRepository;
    private final RewardLedgerRepository rewardLedgerRepository;
    private final PaymentIpnRawReceiptRepository paymentIpnRawReceiptRepository;
    private final PaymentIpnEventRepository paymentIpnEventRepository;
    private final PaymentReconciliationCaseRepository paymentReconciliationCaseRepository;
    private final VnPayService vnPayService;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;
    private final Clock clock;

    @Autowired
    public GroupPaymentService(CheckoutGroupRepository checkoutGroupRepository,
                               OrderRepository orderRepository,
                               OrderItemRepository orderItemRepository,
                               InventoryReservationRepository inventoryReservationRepository,
                               PaymentIntentRepository paymentIntentRepository,
                               PaymentAttemptRepository paymentAttemptRepository,
                               PaymentConfirmationRepository paymentConfirmationRepository,
                               PaymentAllocationRepository paymentAllocationRepository,
                               OrderFundComponentRepository orderFundComponentRepository,
                               RewardLedgerRepository rewardLedgerRepository,
                               PaymentIpnRawReceiptRepository paymentIpnRawReceiptRepository,
                               PaymentIpnEventRepository paymentIpnEventRepository,
                               PaymentReconciliationCaseRepository paymentReconciliationCaseRepository,
                               VnPayService vnPayService,
                               JdbcTemplate jdbcTemplate,
                               EntityManager entityManager,
                               Clock clock) {
        this.checkoutGroupRepository = checkoutGroupRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryReservationRepository = inventoryReservationRepository;
        this.paymentIntentRepository = paymentIntentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentConfirmationRepository = paymentConfirmationRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.orderFundComponentRepository = orderFundComponentRepository;
        this.rewardLedgerRepository = rewardLedgerRepository;
        this.paymentIpnRawReceiptRepository = paymentIpnRawReceiptRepository;
        this.paymentIpnEventRepository = paymentIpnEventRepository;
        this.paymentReconciliationCaseRepository = paymentReconciliationCaseRepository;
        this.vnPayService = vnPayService;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    public GroupPaymentResponse createGroupPayment(long buyerId, UUID groupId, String clientIp) {
        CheckoutGroupEntity group = checkoutGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhóm thanh toán: " + groupId));

        if (!Objects.equals(group.getBuyerId(), buyerId)) {
            throw new AccessDeniedException("Không có quyền thanh toán nhóm đơn hàng này.");
        }

        Instant now = clock.instant();
        if (!CheckoutGroupEntity.STATE_READY.equals(group.getState())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nhóm thanh toán không ở trạng thái chờ thanh toán: " + group.getState());
        }
        if (group.getPaymentDueAt() != null && !group.getPaymentDueAt().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Nhóm thanh toán đã hết hạn.");
        }

        // 1. Get or create PaymentIntentEntity
        PaymentIntentEntity intent = paymentIntentRepository.findByGroupId(groupId).orElseGet(() -> {
            PaymentIntentEntity newIntent = new PaymentIntentEntity(
                    UUID.randomUUID(),
                    PaymentIntentEntity.PURPOSE_CHECKOUT_GROUP,
                    groupId,
                    null,
                    group.getExpectedTotal(),
                    "VND",
                    group.getPaymentMethod(),
                    PaymentIntentEntity.STATE_PENDING,
                    group.getPaymentDueAt(),
                    now,
                    "INTENT:" + groupId
            );
            return paymentIntentRepository.saveAndFlush(newIntent);
        });

        // 2. Get or create PaymentAttemptEntity (unique pending attempt per intent)
        PaymentAttemptEntity attempt = paymentAttemptRepository.findByIntentIdAndStatus(intent.getIntentId(), PaymentAttemptEntity.STATUS_PENDING)
                .orElseGet(() -> {
                    String txnRef = "GRP_" + intent.getIntentId().toString().replace("-", "").substring(0, 16) + "_" + System.currentTimeMillis();
                    PaymentAttemptEntity newAttempt = new PaymentAttemptEntity(
                            intent.getIntentId(),
                            txnRef,
                            intent.getExpectedAmount(),
                            now
                    );
                    return paymentAttemptRepository.saveAndFlush(newAttempt);
                });

        // 3. Generate VNPay URL
        String paymentUrl = vnPayService.createPaymentUrl(
                attempt.getTxnRef(),
                attempt.getAmount(),
                "Thanh toan checkout group " + groupId,
                clientIp,
                group.getPaymentDueAt(),
                null
        );

        return new GroupPaymentResponse(
                intent.getIntentId(),
                attempt.getId(),
                attempt.getTxnRef(),
                attempt.getAmount(),
                "VND",
                paymentUrl,
                group.getPaymentDueAt()
        );
    }

    public GroupPaymentIpnResult processGroupPaymentIpn(Map<String, String> params, String clientIp) {
        if (!vnPayService.verifyCallback(params)) {
            return new GroupPaymentIpnResult("97", "Invalid checksum", null, null);
        }

        String txnRef = params.get("vnp_TxnRef");
        if (txnRef == null || txnRef.isBlank()) {
            return new GroupPaymentIpnResult("01", "Order/attempt not found", null, null);
        }

        // Enforce strict lock hierarchy: CheckoutGroup -> PaymentAttempt -> rawReceipt (og70_write_view advisory lock)
        // to prevent deadlocks when concurrent callbacks hit attempts sharing a group or og_compat dual-write locks.
        paymentAttemptRepository.findGroupIdByTxnRef(txnRef)
                .ifPresent(checkoutGroupRepository::findByIdForUpdate);

        Optional<PaymentAttemptEntity> attemptOpt = paymentAttemptRepository.findByTxnRefForUpdate(txnRef);
        if (attemptOpt.isEmpty()) {
            return new GroupPaymentIpnResult("01", "Attempt not found for txnRef " + txnRef, null, null);
        }

        PaymentAttemptEntity attempt = attemptOpt.get();
        if (PaymentAttemptEntity.STATUS_SUCCESS.equals(attempt.getStatus())) {
            return new GroupPaymentIpnResult("02", "Order already confirmed", attempt.getIntentId(), "ALLOCATABLE");
        }
        if (PaymentAttemptEntity.STATUS_SUCCESS_ON_EXPIRED_ORDER.equals(attempt.getStatus())) {
            return new GroupPaymentIpnResult("02", "Order already expired", attempt.getIntentId(), "RECONCILIATION");
        }
        if (PaymentAttemptEntity.STATUS_SUCCESS_PENDING_REVIEW.equals(attempt.getStatus())) {
            return new GroupPaymentIpnResult("02", "Payment already recorded pending review", attempt.getIntentId(), "RECONCILIATION");
        }
        if (PaymentAttemptEntity.STATUS_FAILED.equals(attempt.getStatus())) {
            String rawAmount = params.get("vnp_Amount");
            if (rawAmount == null || Long.parseLong(rawAmount) != VnPayService.scaledAmount(attempt.getAmount())) {
                return new GroupPaymentIpnResult("04", "Invalid amount", attempt.getIntentId(), null);
            }

            String code = params.get("vnp_ResponseCode");
            String status = params.get("vnp_TransactionStatus");
            String transactionNo = params.get("vnp_TransactionNo");
            boolean isSuccess = "00".equals(code) && "00".equals(status);

            if (!isSuccess) {
                return new GroupPaymentIpnResult("02", "Attempt already in terminal status: FAILED", attempt.getIntentId(), null);
            }

            // Check if this attempt already has a confirmation (e.g. duplicate IPN after Insufficient Coin or late success)
            Optional<PaymentConfirmationEntity> existingConf = paymentConfirmationRepository.findByAttemptId(attempt.getId());
            if (existingConf.isPresent()) {
                return new GroupPaymentIpnResult("02", "Attempt already confirmed/recorded for reconciliation", attempt.getIntentId(), existingConf.get().getDisposition());
            }

            // Group is READY or being processed: provider success arrived on a previously FAILED attempt.
            // 1. Record raw receipt and IPN event first (consistent lock hierarchy with normal flow to prevent deadlock)
            Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            String rawQueryString = params.toString();
            String sha256 = sha256Hex(rawQueryString);
            PaymentIpnRawReceiptEntity rawReceipt = new PaymentIpnRawReceiptEntity(
                    rawQueryString,
                    rawQueryString.length(),
                    false,
                    false,
                    sha256,
                    clientIp,
                    now
            );
            rawReceipt.markLinked();
            rawReceipt.setForensicFields(txnRef, transactionNo, rawAmount);
            rawReceipt.markProcessed();
            rawReceipt = paymentIpnRawReceiptRepository.saveAndFlush(rawReceipt);

            PaymentIpnEventEntity ipnEvent = new PaymentIpnEventEntity(
                    rawReceipt.getId(),
                    attempt.getId(),
                    attempt.getIntentId(),
                    txnRef,
                    transactionNo,
                    code,
                    status,
                    "VND",
                    rawAmount,
                    Long.parseLong(rawAmount),
                    attempt.getAmount(),
                    params.get("vnp_BankCode"),
                    params.get("vnp_BankTranNo"),
                    params.get("vnp_CardType"),
                    params.get("vnp_PayDate"),
                    clientIp,
                    PaymentIpnEventEntity.OUTCOME_APPLIED_SUCCESS,
                    now
            );
            ipnEvent = paymentIpnEventRepository.saveAndFlush(ipnEvent);

            // 2. Lock parent checkout group for update
            PaymentIntentEntity intent = paymentIntentRepository.findById(attempt.getIntentId())
                    .orElseThrow(() -> new IllegalStateException("Payment intent not found: " + attempt.getIntentId()));
            CheckoutGroupEntity group = checkoutGroupRepository.findByIdForUpdate(intent.getGroupId())
                    .orElseThrow(() -> new IllegalStateException("Checkout group not found: " + intent.getGroupId()));

            if (CheckoutGroupEntity.STATE_PAID.equals(group.getState()) || CheckoutGroupEntity.STATE_EXPIRED.equals(group.getState())) {
                return new GroupPaymentIpnResult("02", "Order already confirmed or expired", attempt.getIntentId(), "RECONCILIATION");
            }

            // Check if there is an active PENDING or SUCCESS attempt for this intent (e.g. concurrent attempt in case B)
            Optional<PaymentAttemptEntity> pendingAttemptOpt = paymentAttemptRepository.findByIntentIdAndStatusForUpdate(attempt.getIntentId(), PaymentAttemptEntity.STATUS_PENDING);
            if (pendingAttemptOpt.isPresent() && !pendingAttemptOpt.get().getId().equals(attempt.getId())) {
                return new GroupPaymentIpnResult("02", "Another active attempt is pending for this order", attempt.getIntentId(), "RECONCILIATION");
            }

            boolean hasSuccessfulAttempt = paymentAttemptRepository.findByIntentId(attempt.getIntentId()).stream()
                    .anyMatch(a -> !a.getId().equals(attempt.getId()) && PaymentAttemptEntity.STATUS_SUCCESS.equals(a.getStatus()));
            if (hasSuccessfulAttempt) {
                return new GroupPaymentIpnResult("02", "Another attempt already confirmed for this order", attempt.getIntentId(), "RECONCILIATION");
            }

            // 3. Group is READY, preserve cash evidence in payment_confirmations and create reconciliation case

            PaymentConfirmationEntity confirmation = new PaymentConfirmationEntity(
                    UUID.randomUUID(),
                    attempt.getIntentId(),
                    attempt.getId(),
                    ipnEvent.getId(),
                    "VNPAY",
                    transactionNo,
                    attempt.getAmount(),
                    PaymentConfirmationEntity.DISPOSITION_RECONCILIATION,
                    now
            );
            paymentConfirmationRepository.saveAndFlush(confirmation);

            String dedupKey = "REC:LATE_SUCCESS_ON_FAILED:" + attempt.getId() + ":" + transactionNo;
            if (paymentReconciliationCaseRepository.findByDedupKey(dedupKey).isEmpty()) {
                PaymentReconciliationCaseEntity recCase = PaymentReconciliationCaseEntity.fromIntentIpnEvent(
                        attempt.getIntentId(),
                        attempt.getId(),
                        PaymentReconciliationCaseEntity.CASE_LATE_SUCCESS_ON_FAILED,
                        ipnEvent.getId(),
                        transactionNo,
                        dedupKey,
                        now
                );
                recCase.setResolutionNote("VNPay reported success on previously FAILED attempt while group remains READY. Preserved for manual Ops reconciliation.");
                paymentReconciliationCaseRepository.saveAndFlush(recCase);
            }

            return new GroupPaymentIpnResult("02", "Late success on failed attempt recorded for reconciliation", attempt.getIntentId(), "RECONCILIATION");
        }
        if (attempt.isTerminal()) {
            return new GroupPaymentIpnResult("02", "Attempt already in terminal status: " + attempt.getStatus(), attempt.getIntentId(), null);
        }

        String rawAmount = params.get("vnp_Amount");
        if (rawAmount == null || Long.parseLong(rawAmount) != VnPayService.scaledAmount(attempt.getAmount())) {
            return new GroupPaymentIpnResult("04", "Invalid amount", attempt.getIntentId(), null);
        }

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String code = params.get("vnp_ResponseCode");
        String status = params.get("vnp_TransactionStatus");
        String transactionNo = params.get("vnp_TransactionNo");
        boolean isSuccess = "00".equals(code) && "00".equals(status);

        // Record raw receipt
        String rawQueryString = params.toString();
        String sha256 = sha256Hex(rawQueryString);
        PaymentIpnRawReceiptEntity rawReceipt = new PaymentIpnRawReceiptEntity(
                rawQueryString,
                rawQueryString.length(),
                false,
                false,
                sha256,
                clientIp,
                now
        );
        rawReceipt.markLinked();
        rawReceipt.setForensicFields(txnRef, transactionNo, rawAmount);
        rawReceipt.markProcessed();
        rawReceipt = paymentIpnRawReceiptRepository.saveAndFlush(rawReceipt);

        // Record IPN event
        PaymentIpnEventEntity ipnEvent = new PaymentIpnEventEntity(
                rawReceipt.getId(),
                attempt.getId(),
                attempt.getIntentId(),
                txnRef,
                transactionNo,
                code,
                status,
                "VND",
                rawAmount,
                Long.parseLong(rawAmount),
                attempt.getAmount(),
                params.get("vnp_BankCode"),
                params.get("vnp_BankTranNo"),
                params.get("vnp_CardType"),
                params.get("vnp_PayDate"),
                clientIp,
                isSuccess ? PaymentIpnEventEntity.OUTCOME_APPLIED_SUCCESS : PaymentIpnEventEntity.OUTCOME_APPLIED_FAILED,
                now
        );
        ipnEvent = paymentIpnEventRepository.saveAndFlush(ipnEvent);

        if (!isSuccess) {
            attempt.markFailed("VNPAY response " + code + "/" + status, now);
            paymentAttemptRepository.saveAndFlush(attempt);
            return new GroupPaymentIpnResult("00", "Payment failed recorded", attempt.getIntentId(), null);
        }

        PaymentIntentEntity intent = paymentIntentRepository.findById(attempt.getIntentId())
                .orElseThrow(() -> new IllegalStateException("Payment intent not found: " + attempt.getIntentId()));
        CheckoutGroupEntity group = checkoutGroupRepository.findByIdForUpdate(intent.getGroupId())
                .orElseThrow(() -> new IllegalStateException("Checkout group not found: " + intent.getGroupId()));

        if (CheckoutGroupEntity.STATE_PAID.equals(group.getState()) || CheckoutGroupEntity.STATE_EXPIRED.equals(group.getState())) {
            return new GroupPaymentIpnResult("02", "Order already confirmed or expired", attempt.getIntentId(), "RECONCILIATION");
        }

        List<OrderEntity> orders = orderRepository.findByCheckoutGroupId(group.getGroupId());

        // Check buyer Coin balance at T2 BEFORE saving confirmation as ALLOCATABLE
        BigDecimal totalPointsDiscount = orders.stream()
                .map(OrderEntity::getPointsDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean insufficientCoins = false;
        Long currentCoinBalance = 0L;
        if (totalPointsDiscount.compareTo(BigDecimal.ZERO) > 0 && group.getRewardPolicyId() != null) {
            try {
                Long b = jdbcTemplate.queryForObject("SELECT balance FROM og_compat.reward_accounts WHERE user_id = ?", Long.class, group.getBuyerId());
                currentCoinBalance = b != null ? b : 0L;
            } catch (Exception ex) {
                try {
                    Long b = jdbcTemplate.queryForObject("SELECT COALESCE(sum(points_delta),0) FROM og_compat.reward_ledger WHERE user_id = ?", Long.class, group.getBuyerId());
                    currentCoinBalance = b != null ? b : 0L;
                } catch (Exception ignored) {}
            }
            if (currentCoinBalance < totalPointsDiscount.longValue()) {
                insufficientCoins = true;
            }
        }

        if (insufficientCoins) {
            // Insufficient Coin balance at T2:
            // 1. Save payment confirmation with DISPOSITION_RECONCILIATION to preserve cash collected
            PaymentConfirmationEntity confirmation = new PaymentConfirmationEntity(
                    UUID.randomUUID(),
                    intent.getIntentId(),
                    attempt.getId(),
                    ipnEvent.getId(),
                    "VNPAY",
                    transactionNo,
                    attempt.getAmount(),
                    PaymentConfirmationEntity.DISPOSITION_RECONCILIATION,
                    now
            );
            paymentConfirmationRepository.saveAndFlush(confirmation);

            // 2. Mark attempt FAILED
            attempt.markFailed("Insufficient coin balance at T2", now);
            paymentAttemptRepository.saveAndFlush(attempt);

            // 3. Create reconciliation case to track cash for Ops/Accounting
            String dedupKey = "REC:INSUFFICIENT_COIN:" + intent.getIntentId();
            if (paymentReconciliationCaseRepository.findByDedupKey(dedupKey).isEmpty()) {
                PaymentReconciliationCaseEntity recCase = PaymentReconciliationCaseEntity.fromIntentIpnEvent(
                        intent.getIntentId(),
                        attempt.getId(),
                        PaymentReconciliationCaseEntity.CASE_LATE_SUCCESS_ON_FAILED,
                        ipnEvent.getId(),
                        transactionNo,
                        dedupKey,
                        now
                );
                recCase.setResolutionNote("Insufficient coin balance at T2: required " + totalPointsDiscount + " coins, but balance was " + currentCoinBalance);
                paymentReconciliationCaseRepository.saveAndFlush(recCase);
            }

            // 4. Group remains READY, orders remain PAYMENT_PENDING, no Coin debit, balance not negative
            return new GroupPaymentIpnResult("04", "Insufficient coin balance at T2", intent.getIntentId(), null);
        }

        // 1. Payment Confirmation
        PaymentConfirmationEntity confirmation = new PaymentConfirmationEntity(
                UUID.randomUUID(),
                intent.getIntentId(),
                attempt.getId(),
                ipnEvent.getId(),
                "VNPAY",
                transactionNo,
                attempt.getAmount(),
                PaymentConfirmationEntity.DISPOSITION_ALLOCATABLE,
                now
        );
        confirmation = paymentConfirmationRepository.saveAndFlush(confirmation);
        entityManager.refresh(confirmation);

        if (PaymentConfirmationEntity.DISPOSITION_RECONCILIATION.equals(confirmation.getDisposition())) {
            LocalDate paymentLocalDate = confirmation.getConfirmedAt().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
            LocalDate earliestHandoverDay = orders.stream()
                    .map(OrderEntity::getHandoverDay)
                    .filter(Objects::nonNull)
                    .min(LocalDate::compareTo)
                    .orElse(null);
            boolean isLatePayment = earliestHandoverDay != null && paymentLocalDate.isAfter(earliestHandoverDay);
            if (isLatePayment) {
                // DEC-10-LATE-PAYMENT: Payment confirmed after handover date
                // 1. Group transitions to EXPIRED
                group.setState(CheckoutGroupEntity.STATE_EXPIRED);
                checkoutGroupRepository.saveAndFlush(group);

                // 2. Attempt transitions to SUCCESS_ON_EXPIRED_ORDER
                attempt.markSuccessOnExpiredOrder(now);
                paymentAttemptRepository.saveAndFlush(attempt);

                // 3. IPN Event transitions to APPLIED_EXPIRED_ORDER
                ipnEvent.setProcessingOutcome(PaymentIpnEventEntity.OUTCOME_APPLIED_EXPIRED_ORDER);
                paymentIpnEventRepository.saveAndFlush(ipnEvent);

                // 4. Create reconciliation case EXPIRED_ORDER (idempotent dedup)
                String dedupKey = "REC:EXPIRED_ORDER:" + intent.getIntentId();
                if (paymentReconciliationCaseRepository.findByDedupKey(dedupKey).isEmpty()) {
                    PaymentReconciliationCaseEntity recCase = PaymentReconciliationCaseEntity.fromIntentIpnEvent(
                            intent.getIntentId(),
                            attempt.getId(),
                            PaymentReconciliationCaseEntity.CASE_EXPIRED_ORDER,
                            ipnEvent.getId(),
                            transactionNo,
                            dedupKey,
                            now
                    );
                    paymentReconciliationCaseRepository.saveAndFlush(recCase);
                }

                // 5. Orders remain PAYMENT_PENDING (no fulfillment, no reservation funding)
                // 6. No auto-refund, no Coin debit
                return new GroupPaymentIpnResult("00", "Payment late recorded for reconciliation (EXPIRED_ORDER)", intent.getIntentId(), "RECONCILIATION");
            } else {
                // Payment confirmed after 1-hour deadline (payment_due_at) but before or on handover date
                // 1. Group remains READY (no transition to EXPIRED, no transition to PAID)
                // 2. Attempt transitions to SUCCESS_PENDING_REVIEW (no longer hanging in PENDING)
                attempt.markSuccessPendingReview(now);
                paymentAttemptRepository.saveAndFlush(attempt);

                // 3. Create reconciliation case with COUNTDOWN_EXPIRED dedup key
                String dedupKey = "REC:COUNTDOWN_EXPIRED:" + intent.getIntentId();
                if (paymentReconciliationCaseRepository.findByDedupKey(dedupKey).isEmpty()) {
                    PaymentReconciliationCaseEntity recCase = PaymentReconciliationCaseEntity.fromIntentIpnEvent(
                            intent.getIntentId(),
                            attempt.getId(),
                            PaymentReconciliationCaseEntity.CASE_EXPIRED_ORDER,
                            ipnEvent.getId(),
                            transactionNo,
                            dedupKey,
                            now
                    );
                    recCase.setResolutionNote("Payment confirmed after 1-hour deadline (payment_due_at) but before handover date. Group remains READY. Requires manual Ops review.");
                    paymentReconciliationCaseRepository.saveAndFlush(recCase);
                }

                // 4. Orders remain PAYMENT_PENDING (no fulfillment, no reservation funding)
                // 5. No auto-refund, no Coin debit
                return new GroupPaymentIpnResult("00", "Payment after deadline recorded for reconciliation (COUNTDOWN_EXPIRED)", intent.getIntentId(), "RECONCILIATION");
            }
        }

        // 2. Payment Allocations (while order status is still PAYMENT_PENDING)
        Map<Long, PaymentAllocationEntity> allocations = new HashMap<>();
        for (OrderEntity order : orders) {
            PaymentAllocationEntity allocation = new PaymentAllocationEntity(
                    UUID.randomUUID(),
                    intent.getIntentId(),
                    confirmation.getConfirmationId(),
                    order.getId(),
                    order.getTotalAmount()
            );
            allocation = paymentAllocationRepository.saveAndFlush(allocation);
            allocations.put(order.getId(), allocation);
        }

        // 3. Fund Components
        for (OrderEntity order : orders) {
            PaymentAllocationEntity allocation = allocations.get(order.getId());
            UUID allocId = allocation != null ? allocation.getAllocationId() : null;

            // GOODS_CASH = o.subtotal - voucher - coins
            BigDecimal goodsCash = order.getSubtotal().subtract(order.getVoucherDiscountAmount()).subtract(order.getPointsDiscountAmount());
            OrderFundComponentEntity cGoods = new OrderFundComponentEntity(
                    UUID.randomUUID(),
                    order.getId(),
                    allocId,
                    null,
                    OrderFundComponentEntity.TYPE_GOODS_CASH,
                    goodsCash,
                    null,
                    now
            );
            orderFundComponentRepository.saveAndFlush(cGoods);

            // SHIPPING_CASH = o.shipping_fee - shipping_discount
            BigDecimal shippingCash = order.getShippingFee().subtract(order.getShippingDiscountAmount());
            OrderFundComponentEntity cShipping = new OrderFundComponentEntity(
                    UUID.randomUUID(),
                    order.getId(),
                    allocId,
                    null,
                    OrderFundComponentEntity.TYPE_SHIPPING_CASH,
                    shippingCash,
                    null,
                    now
            );
            orderFundComponentRepository.saveAndFlush(cShipping);

            // PLATFORM_GOODS_SUBSIDY = voucher + coins
            BigDecimal goodsSubsidy = order.getVoucherDiscountAmount().add(order.getPointsDiscountAmount());
            OrderFundComponentEntity cGoodsSub = new OrderFundComponentEntity(
                    UUID.randomUUID(),
                    order.getId(),
                    null,
                    null,
                    OrderFundComponentEntity.TYPE_PLATFORM_GOODS_SUBSIDY,
                    goodsSubsidy,
                    "PLATFORM_PROMOTION",
                    now
            );
            orderFundComponentRepository.saveAndFlush(cGoodsSub);

            // PLATFORM_SHIPPING_SUBSIDY = shipping_discount
            BigDecimal shipSubsidy = order.getShippingDiscountAmount();
            OrderFundComponentEntity cShipSub = new OrderFundComponentEntity(
                    UUID.randomUUID(),
                    order.getId(),
                    null,
                    null,
                    OrderFundComponentEntity.TYPE_PLATFORM_SHIPPING_SUBSIDY,
                    shipSubsidy,
                    "PLATFORM_SHIPPING",
                    now
            );
            orderFundComponentRepository.saveAndFlush(cShipSub);
        }

        // 4. Coin T2 Debit & Discount Allocations
        if (totalPointsDiscount.compareTo(BigDecimal.ZERO) > 0 && group.getRewardPolicyId() != null) {
            boolean alreadyDebited = rewardLedgerRepository.findByGroupIdAndEntryType(group.getGroupId(), RewardLedgerEntity.ENTRY_DEBIT_CHECKOUT).isPresent();
            if (!alreadyDebited) {
                UUID rewardEntryId = UUID.randomUUID();
                jdbcTemplate.update("""
                        INSERT INTO og_compat.reward_ledger(
                            entry_id, user_id, policy_id, entry_type, points_delta, group_id, actor_id, reason, command_key, created_at
                        ) VALUES (?, ?, ?, 'DEBIT_CHECKOUT', ?, ?, NULL, ?, ?, ?)
                        """,
                        rewardEntryId,
                        group.getBuyerId(),
                        group.getRewardPolicyId(),
                        -totalPointsDiscount.longValue(),
                        group.getGroupId(),
                        "Khấu trừ xu đặt hàng nhóm " + group.getGroupId(),
                        "REWARD_DEBIT:" + group.getGroupId(),
                        java.sql.Timestamp.from(now)
                );

                // Insert discount_allocations matching order item points allocations
                for (OrderEntity order : orders) {
                    List<OrderItemEntity> items = orderItemRepository.findByOrderId(order.getId());
                    for (OrderItemEntity item : items) {
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
                }
            }
        }

        // 5. Update Orders
        entityManager.flush();
        for (OrderEntity order : orders) {
            jdbcTemplate.update("""
                UPDATE og_compat.orders
                SET status = 'PAID_HELD',
                    paid_confirmed_at = (SELECT confirmed_at FROM og_compat.payment_confirmations WHERE confirmation_id = ?),
                    seller_accept_due_at = (SELECT confirmed_at + INTERVAL '2 days' FROM og_compat.payment_confirmations WHERE confirmation_id = ?)
                WHERE order_id = ?
                """, confirmation.getConfirmationId(), confirmation.getConfirmationId(), order.getId());

            entityManager.refresh(order);

            // 6. Update inventory reservations
            List<OrderItemEntity> items = orderItemRepository.findByOrderId(order.getId());
            for (OrderItemEntity item : items) {
                List<InventoryReservationEntity> resList = inventoryReservationRepository.findByOrderItemId(item.getId());
                for (InventoryReservationEntity res : resList) {
                    res.setState("FUNDED");
                    res.setChangedAt(now);
                    inventoryReservationRepository.saveAndFlush(res);
                }
            }
        }

        // 7. Update Checkout Group
        group.setState(CheckoutGroupEntity.STATE_PAID);
        checkoutGroupRepository.saveAndFlush(group);

        // 8. Update Payment Attempt
        attempt.markSuccess(now);
        paymentAttemptRepository.saveAndFlush(attempt);

        return new GroupPaymentIpnResult("00", "Confirm Success", intent.getIntentId(), "ALLOCATABLE");
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}

