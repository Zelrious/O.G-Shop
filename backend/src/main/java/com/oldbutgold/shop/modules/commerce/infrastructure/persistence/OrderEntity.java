package com.oldbutgold.shop.modules.commerce.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders", schema = "og_compat")
public class OrderEntity {
    public static final String STATUS_PAYMENT_PENDING = "PAYMENT_PENDING";
    public static final String STATUS_PAID_HELD = "PAID_HELD";
    public static final String STATUS_SELLER_CONFIRMED = "SELLER_CONFIRMED";
    public static final String STATUS_SHIPPED = "SHIPPED";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_DISPUTED = "DISPUTED";
    public static final String STATUS_REFUNDED = "REFUNDED";
    public static final String STATUS_UNDER_REVIEW = "UNDER_REVIEW";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    @Column(name = "checkout_group_id", nullable = false)
    private UUID checkoutGroupId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "source_address_id")
    private Long sourceAddressId;

    @Column(name = "shipping_recipient_name", nullable = false, length = 120)
    private String shippingRecipientName;

    @Column(name = "shipping_phone_number", nullable = false, length = 20)
    private String shippingPhoneNumber;

    @Column(name = "shipping_province", nullable = false, length = 100)
    private String shippingProvince;

    @Column(name = "shipping_district", nullable = false, length = 100)
    private String shippingDistrict;

    @Column(name = "shipping_ward", nullable = false, length = 100)
    private String shippingWard;

    @Column(name = "shipping_detail_address", nullable = false, length = 255)
    private String shippingDetailAddress;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "shipping_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "buyer_system_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal buyerSystemFee = BigDecimal.ZERO;

    @Column(name = "seller_system_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal sellerSystemFee = BigDecimal.ZERO;

    @Column(name = "seller_proceeds", nullable = false, precision = 19, scale = 2)
    private BigDecimal sellerProceeds;

    @Column(name = "voucher_discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal voucherDiscountAmount = BigDecimal.ZERO;

    @Column(name = "shipping_discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal shippingDiscountAmount = BigDecimal.ZERO;

    @Column(name = "sponsor_type", nullable = false, length = 20)
    private String sponsorType = "PLATFORM";

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    @Column(nullable = false, length = 3)
    private String currency = "VND";

    @Column(nullable = false, length = 30)
    private String status = STATUS_PAYMENT_PENDING;

    @Column(name = "payment_due_at", nullable = false)
    private Instant paymentDueAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    protected OrderEntity() {}

    public OrderEntity(UUID checkoutGroupId, Long buyerId, Long sellerId, Long sourceAddressId,
                       String shippingRecipientName, String shippingPhoneNumber,
                       String shippingProvince, String shippingDistrict, String shippingWard,
                       String shippingDetailAddress, BigDecimal subtotal, BigDecimal shippingFee,
                       BigDecimal buyerSystemFee, BigDecimal sellerSystemFee, BigDecimal sellerProceeds,
                       BigDecimal voucherDiscountAmount, BigDecimal shippingDiscountAmount,
                       String sponsorType, BigDecimal totalAmount, Instant paymentDueAt, Instant now) {
        this.checkoutGroupId = checkoutGroupId;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.sourceAddressId = sourceAddressId;
        this.shippingRecipientName = shippingRecipientName;
        this.shippingPhoneNumber = shippingPhoneNumber;
        this.shippingProvince = shippingProvince;
        this.shippingDistrict = shippingDistrict;
        this.shippingWard = shippingWard;
        this.shippingDetailAddress = shippingDetailAddress;
        this.subtotal = subtotal;
        this.shippingFee = shippingFee;
        this.buyerSystemFee = buyerSystemFee;
        this.sellerSystemFee = sellerSystemFee;
        this.sellerProceeds = sellerProceeds;
        this.voucherDiscountAmount = voucherDiscountAmount;
        this.shippingDiscountAmount = shippingDiscountAmount;
        this.sponsorType = sponsorType;
        this.totalAmount = totalAmount;
        this.paymentDueAt = paymentDueAt;
        this.createdAt = now;
        this.status = STATUS_PAYMENT_PENDING;
    }

    public Long getId() { return id; }
    public UUID getCheckoutGroupId() { return checkoutGroupId; }
    public Long getBuyerId() { return buyerId; }
    public Long getSellerId() { return sellerId; }
    public Long getSourceAddressId() { return sourceAddressId; }
    public String getShippingRecipientName() { return shippingRecipientName; }
    public String getShippingPhoneNumber() { return shippingPhoneNumber; }
    public String getShippingProvince() { return shippingProvince; }
    public String getShippingDistrict() { return shippingDistrict; }
    public String getShippingWard() { return shippingWard; }
    public String getShippingDetailAddress() { return shippingDetailAddress; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getShippingFee() { return shippingFee; }
    public BigDecimal getBuyerSystemFee() { return buyerSystemFee; }
    public BigDecimal getSellerSystemFee() { return sellerSystemFee; }
    public BigDecimal getSellerProceeds() { return sellerProceeds; }
    public BigDecimal getVoucherDiscountAmount() { return voucherDiscountAmount; }
    public BigDecimal getShippingDiscountAmount() { return shippingDiscountAmount; }
    public String getSponsorType() { return sponsorType; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public Instant getPaymentDueAt() { return paymentDueAt; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancellationReason() { return cancellationReason; }

    public void markPaidHeld(Instant now) {
        if (!STATUS_PAYMENT_PENDING.equals(this.status)) {
            throw new IllegalStateException("Chỉ có thể chuyển sang PAID_HELD khi đơn hàng đang ở trạng thái PAYMENT_PENDING. Trạng thái hiện tại: " + this.status);
        }
        this.status = STATUS_PAID_HELD;
        this.updatedAt = now;
    }

    public void confirmBySeller(Instant now) {
        if (!STATUS_PAID_HELD.equals(this.status)) {
            throw new IllegalStateException("Người bán chỉ có thể xác nhận đơn hàng khi đã được Ký quỹ giữ tiền (PAID_HELD). Trạng thái hiện tại: " + this.status);
        }
        this.status = STATUS_SELLER_CONFIRMED;
        this.updatedAt = now;
    }

    public void cancel(String reason, Instant now) {
        if (STATUS_CANCELLED.equals(this.status)) {
            return; // idempotent
        }
        if (!STATUS_PAYMENT_PENDING.equals(this.status) && !STATUS_PAID_HELD.equals(this.status)) {
            throw new IllegalStateException("Không thể hủy đơn hàng đang ở trạng thái " + this.status);
        }
        this.status = STATUS_CANCELLED;
        this.cancellationReason = (reason != null && !reason.isBlank()) ? reason : "Đã hủy đơn hàng";
        this.cancelledAt = now;
        this.updatedAt = now;
    }

    public void markUnderReview(Instant now) {
        if (!STATUS_PAYMENT_PENDING.equals(this.status)) {
            throw new IllegalStateException("Chỉ có thể chuyển sang UNDER_REVIEW từ trạng thái PAYMENT_PENDING. Trạng thái hiện tại: " + this.status);
        }
        this.status = STATUS_UNDER_REVIEW;
        this.updatedAt = now;
    }

    public void approveFromReview(Instant now) {
        if (!STATUS_UNDER_REVIEW.equals(this.status)) {
            throw new IllegalStateException("Chỉ có thể duyệt hoàn tất ký quỹ khi đơn hàng đang ở trạng thái UNDER_REVIEW. Trạng thái hiện tại: " + this.status);
        }
        this.status = STATUS_PAID_HELD;
        this.updatedAt = now;
    }

    public void cancelFromReview(String reason, Instant now) {
        if (!STATUS_UNDER_REVIEW.equals(this.status)) {
            throw new IllegalStateException("Chỉ có thể hủy đơn hàng từ trạng thái UNDER_REVIEW. Trạng thái hiện tại: " + this.status);
        }
        this.status = STATUS_CANCELLED;
        this.cancellationReason = (reason != null && !reason.isBlank()) ? reason : "Đã hủy do rà soát gian lận";
        this.cancelledAt = now;
        this.updatedAt = now;
    }
}
