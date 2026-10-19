package com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity đại diện cho vận đơn trong hệ thống O.G-Shop, tích hợp giao vận với GHN.
 * Tương ứng với bảng `shipments` trong cơ sở dữ liệu.
 */
@Entity
@Table(name = "shipments")
public class ShipmentEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PICKED_UP = "PICKED_UP";
    public static final String STATUS_IN_TRANSIT = "IN_TRANSIT";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_RETURNED = "RETURNED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shipment_id")
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "carrier", length = 100)
    private String carrier = "GHN";

    @Column(name = "tracking_number", length = 100, unique = true)
    private String trackingNumber;

    @Column(name = "client_order_code", length = 50)
    private String clientOrderCode;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_PENDING;

    @Column(name = "shipping_fee", precision = 19, scale = 2)
    private BigDecimal shippingFee;

    @Column(name = "expected_delivery_at")
    private Instant expectedDeliveryAt;

    @Column(name = "raw_ghn_status", length = 50)
    private String rawGhnStatus;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "claim_token", length = 64)
    private String claimToken;

    @Column(name = "claim_expires_at")
    private Instant claimExpiresAt;

    @Column(name = "next_reconciliation_at")
    private Instant nextReconciliationAt;

    @Column(name = "reconciliation_attempts", nullable = false)
    private int reconciliationAttempts = 0;

    @Column(name = "incident_notes")
    private String incidentNotes;

    @Column(name = "admin_review_required", nullable = false)
    private boolean adminReviewRequired = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected ShipmentEntity() {}

    public ShipmentEntity(Long orderId, String clientOrderCode, Instant now) {
        this(orderId, clientOrderCode, null, null, now);
    }

    public ShipmentEntity(Long orderId, String clientOrderCode, String claimToken, Instant claimExpiresAt, Instant now) {
        this.orderId = orderId;
        this.clientOrderCode = clientOrderCode;
        this.claimToken = claimToken;
        this.claimExpiresAt = claimExpiresAt;
        this.carrier = "GHN";
        this.status = STATUS_PENDING;
        this.nextReconciliationAt = null;
        this.reconciliationAttempts = 0;
        this.createdAt = now != null ? now : Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.carrier == null || this.carrier.isBlank()) {
            this.carrier = "GHN";
        }
        if (this.status == null || this.status.isBlank()) {
            this.status = STATUS_PENDING;
        }
        if (this.updatedAt == null) {
            this.updatedAt = this.createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        if (this.carrier == null || this.carrier.isBlank()) {
            this.carrier = "GHN";
        }
        if (this.status == null || this.status.isBlank()) {
            this.status = STATUS_PENDING;
        }
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getCarrier() { return carrier; }
    public String getTrackingNumber() { return trackingNumber; }
    public String getClientOrderCode() { return clientOrderCode; }
    public String getStatus() { return status; }
    public BigDecimal getShippingFee() { return shippingFee; }
    public Instant getExpectedDeliveryAt() { return expectedDeliveryAt; }
    public String getRawGhnStatus() { return rawGhnStatus; }
    public Instant getShippedAt() { return shippedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public String getClaimToken() { return claimToken; }
    public Instant getClaimExpiresAt() { return claimExpiresAt; }
    public String getIncidentNotes() { return incidentNotes; }
    public boolean isAdminReviewRequired() { return adminReviewRequired; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public void setShippingFee(BigDecimal shippingFee) {
        this.shippingFee = shippingFee;
    }

    public void setExpectedDeliveryAt(Instant expectedDeliveryAt) {
        this.expectedDeliveryAt = expectedDeliveryAt;
    }

    public void setRawGhnStatus(String rawGhnStatus) {
        this.rawGhnStatus = rawGhnStatus;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setClaimExpiresAt(Instant claimExpiresAt) {
        this.claimExpiresAt = claimExpiresAt;
    }

    public void setIncidentNotes(String incidentNotes) {
        this.incidentNotes = incidentNotes;
    }

    public void setAdminReviewRequired(boolean adminReviewRequired) {
        this.adminReviewRequired = adminReviewRequired;
    }

    public Instant getNextReconciliationAt() { return nextReconciliationAt; }
    public void setNextReconciliationAt(Instant nextReconciliationAt) { this.nextReconciliationAt = nextReconciliationAt; }
    public int getReconciliationAttempts() { return reconciliationAttempts; }
    public void setReconciliationAttempts(int reconciliationAttempts) { this.reconciliationAttempts = reconciliationAttempts; }
    public void incrementReconciliationAttempts() { this.reconciliationAttempts++; }
    public void resetReconciliationAttempts() { this.reconciliationAttempts = 0; }

    public boolean isReconciliationDue(Instant now) {
        return this.nextReconciliationAt == null || !this.nextReconciliationAt.isAfter(now != null ? now : Instant.now());
    }

    public boolean hasActiveClaim(Instant now) {
        return this.claimToken != null && this.claimExpiresAt != null && this.claimExpiresAt.isAfter(now != null ? now : Instant.now());
    }

    public void acquireClaim(String token, Instant expiresAt) {
        this.claimToken = token;
        this.claimExpiresAt = expiresAt;
    }

    public void releaseClaim() {
        this.claimToken = null;
        this.claimExpiresAt = null;
    }

    public void recordIncident(String rawStatus, String notes, Instant now) {
        this.rawGhnStatus = rawStatus;
        this.incidentNotes = notes;
        this.adminReviewRequired = true;
        this.updatedAt = now != null ? now : Instant.now();
    }

    /**
     * Kiểm tra vận đơn đã ở trạng thái cuối cùng (DELIVERED, RETURNED, FAILED) hay chưa.
     */
    public boolean isTerminal() {
        return STATUS_DELIVERED.equals(this.status)
                || STATUS_RETURNED.equals(this.status)
                || STATUS_FAILED.equals(this.status);
    }

    /**
     * Kiểm tra xem shipment có đang ở trạng thái terminal xung đột với sự kiện giao hàng thành công (DELIVERED) hay không.
     * RETURNED hoặc FAILED là trạng thái terminal xung đột (hàng đã trả về hoặc hủy/thất bại, không thể đột ngột thành DELIVERED).
     */
    public boolean hasTerminalConflictWithDelivered() {
        return STATUS_RETURNED.equals(this.status) || STATUS_FAILED.equals(this.status);
    }

    /**
     * Cập nhật thông tin sau khi gọi API tạo vận đơn GHN thành công.
     * Vận đơn vẫn giữ status PENDING vì hàng chưa được lấy (shipped_at = null, delivered_at = null).
     * Trả về false nếu shipment đã ở trạng thái cuối (bỏ qua).
     */
    public boolean recordGhnCreated(String orderCode, BigDecimal fee, Instant expectedDelivery, Instant now) {
        if (isTerminal()) {
            return false;
        }
        this.trackingNumber = orderCode;
        this.shippingFee = fee;
        this.expectedDeliveryAt = expectedDelivery;
        this.rawGhnStatus = "ready_to_pick";
        this.claimToken = null;
        this.claimExpiresAt = null;
        this.nextReconciliationAt = null;
        this.reconciliationAttempts = 0;
        this.updatedAt = now != null ? now : Instant.now();
        return true;
    }

    /**
     * Chuyển trạng thái sang PICKED_UP khi bưu tá đã lấy hàng thành công (picked/storing/sorting).
     * Bắt buộc có shipped_at, delivered_at = null để thỏa mãn ck_shipments_times.
     * Trả về false nếu shipment đã ở trạng thái cuối (bỏ qua).
     * Guard chống lùi trạng thái: Nếu đang IN_TRANSIT, giữ nguyên trạng thái IN_TRANSIT, không lùi về PICKED_UP.
     */
    public boolean markPickedUp(Instant shippedTime, String rawStatus, Instant now) {
        if (isTerminal()) {
            return false;
        }
        if (STATUS_IN_TRANSIT.equals(this.status)) {
            if (this.shippedAt == null) {
                this.shippedAt = shippedTime != null ? shippedTime : (now != null ? now : Instant.now());
            }
            this.rawGhnStatus = rawStatus;
            this.updatedAt = now != null ? now : Instant.now();
            return true;
        }
        this.status = STATUS_PICKED_UP;
        if (this.shippedAt == null) {
            this.shippedAt = shippedTime != null ? shippedTime : (now != null ? now : Instant.now());
        }
        this.deliveredAt = null;
        this.rawGhnStatus = rawStatus;
        this.updatedAt = now != null ? now : Instant.now();
        return true;
    }

    /**
     * Chuyển trạng thái sang IN_TRANSIT (transporting/delivering/delivery_fail/return).
     * Bắt buộc có shipped_at (xấp xỉ nếu là lần đầu), delivered_at = null để thỏa mãn ck_shipments_times.
     * Trả về false nếu shipment đã ở trạng thái cuối (bỏ qua).
     */
    public boolean markInTransit(Instant approximateShippedTime, String rawStatus, Instant now) {
        if (isTerminal()) {
            return false;
        }
        this.status = STATUS_IN_TRANSIT;
        if (this.shippedAt == null) {
            this.shippedAt = approximateShippedTime != null ? approximateShippedTime : (now != null ? now : Instant.now());
        }
        this.deliveredAt = null;
        this.rawGhnStatus = rawStatus;
        this.updatedAt = now != null ? now : Instant.now();
        return true;
    }

    /**
     * Chuyển trạng thái sang DELIVERED khi giao thành công.
     * Bắt buộc shipped_at != null, delivered_at != null, và delivered_at >= shipped_at để thỏa mãn ck_shipments_times.
     * Trả về false nếu shipment đã ở trạng thái cuối (bỏ qua).
     */
    public boolean markDelivered(Instant deliveryTime, Instant approximateShippedTime, Instant now) {
        if (isTerminal()) {
            return false;
        }
        this.status = STATUS_DELIVERED;
        Instant effectiveDelivered = deliveryTime != null ? deliveryTime : (now != null ? now : Instant.now());
        if (this.shippedAt == null) {
            this.shippedAt = approximateShippedTime != null ? approximateShippedTime : effectiveDelivered;
        }
        if (effectiveDelivered.isBefore(this.shippedAt)) {
            effectiveDelivered = this.shippedAt;
        }
        this.deliveredAt = effectiveDelivered;
        this.rawGhnStatus = "delivered";
        this.updatedAt = now != null ? now : Instant.now();
        return true;
    }

    /**
     * Chuyển trạng thái sang RETURNED sau khi hàng đã hoàn trả về cho Seller.
     * Bắt buộc shipped_at != null và delivered_at = null để thỏa mãn ck_shipments_times.
     * Trả về false nếu shipment đã ở trạng thái cuối (bỏ qua).
     */
    public boolean markReturned(Instant approximateShippedTime, Instant now) {
        if (isTerminal()) {
            return false;
        }
        if (this.shippedAt == null) {
            this.shippedAt = approximateShippedTime != null ? approximateShippedTime : (now != null ? now : Instant.now());
        }
        this.status = STATUS_RETURNED;
        this.deliveredAt = null;
        this.rawGhnStatus = "returned";
        this.updatedAt = now != null ? now : Instant.now();
        return true;
    }

    /**
     * Chuyển trạng thái sang FAILED khi đơn bị hủy hoặc lỗi không thể khắc phục.
     * Bắt buộc delivered_at = null để thỏa mãn ck_shipments_times.
     * Trả về false nếu shipment đã ở trạng thái cuối (bỏ qua).
     */
    public boolean markFailed(String rawStatus, Instant now) {
        if (isTerminal()) {
            return false;
        }
        this.status = STATUS_FAILED;
        this.deliveredAt = null;
        this.rawGhnStatus = rawStatus;
        this.updatedAt = now != null ? now : Instant.now();
        return true;
    }

    /**
     * Kiểm tra trạng thái và các mốc thời gian có thỏa mãn ràng buộc CHECK `ck_shipments_times` trong DB:
     * - PENDING: shipped_at IS NULL AND delivered_at IS NULL
     * - PICKED_UP / IN_TRANSIT / RETURNED: shipped_at IS NOT NULL AND delivered_at IS NULL
     * - DELIVERED: shipped_at IS NOT NULL AND delivered_at IS NOT NULL AND delivered_at >= shipped_at
     * - FAILED: delivered_at IS NULL
     */
    public boolean satisfiesTimesConstraint() {
        if (STATUS_PENDING.equals(status)) {
            return shippedAt == null && deliveredAt == null;
        }
        if (STATUS_PICKED_UP.equals(status) || STATUS_IN_TRANSIT.equals(status) || STATUS_RETURNED.equals(status)) {
            return shippedAt != null && deliveredAt == null;
        }
        if (STATUS_DELIVERED.equals(status)) {
            return shippedAt != null && deliveredAt != null && !deliveredAt.isBefore(shippedAt);
        }
        if (STATUS_FAILED.equals(status)) {
            return deliveredAt == null;
        }
        return false;
    }
}
