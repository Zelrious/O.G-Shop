package com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity mapping to og_compat.cases table/view.
 * Repositories multi-round return and system dispute requests.
 * Dẫn chứng migration: V19 (line 2-28), V22 (line 13339).
 */
@Entity
@Table(name = "cases", schema = "og_compat")
public class CaseEntity {

    public static final String TYPE_RETURN = "RETURN";
    public static final String TYPE_SYSTEM_ISSUE = "SYSTEM_ISSUE";

    public static final String STATE_OPEN = "OPEN";
    public static final String STATE_REVIEWING = "REVIEWING";
    public static final String STATE_RETURN_APPROVED = "RETURN_APPROVED";
    public static final String STATE_RETURNING = "RETURNING";
    public static final String STATE_POST_RETURN = "POST_RETURN";
    public static final String STATE_CLOSED = "CLOSED";
    public static final String STATE_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "case_id")
    private Long id;

    @Column(name = "case_type", nullable = false, length = 20)
    private String caseType = TYPE_RETURN;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "listing_charge_id")
    private UUID listingChargeId;

    @Column(name = "payment_intent_id")
    private UUID paymentIntentId;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "state", nullable = false, length = 20)
    private String state = STATE_OPEN;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "valid_returned_at")
    private Instant validReturnedAt;

    @Column(name = "seller_response_due_at")
    private Instant sellerResponseDueAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "last_related_completion_at")
    private Instant lastRelatedCompletionAt;

    @Column(name = "command_key", nullable = false, length = 150, unique = true)
    private String commandKey;

    protected CaseEntity() {
    }

    public CaseEntity(String caseType, Long orderId, Long createdBy, String description, String commandKey) {
        this.caseType = caseType;
        this.orderId = orderId;
        this.createdBy = createdBy;
        this.description = description;
        this.commandKey = commandKey;
        this.state = STATE_OPEN;
        this.createdAt = Instant.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCaseType() {
        return caseType;
    }

    public void setCaseType(String caseType) {
        this.caseType = caseType;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public UUID getListingChargeId() {
        return listingChargeId;
    }

    public void setListingChargeId(UUID listingChargeId) {
        this.listingChargeId = listingChargeId;
    }

    public UUID getPaymentIntentId() {
        return paymentIntentId;
    }

    public void setPaymentIntentId(UUID paymentIntentId) {
        this.paymentIntentId = paymentIntentId;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(Long targetUserId) {
        this.targetUserId = targetUserId;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Long getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(Long assignedTo) {
        this.assignedTo = assignedTo;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Instant getValidReturnedAt() {
        return validReturnedAt;
    }

    public void setValidReturnedAt(Instant validReturnedAt) {
        this.validReturnedAt = validReturnedAt;
    }

    public Instant getSellerResponseDueAt() {
        return sellerResponseDueAt;
    }

    public void setSellerResponseDueAt(Instant sellerResponseDueAt) {
        this.sellerResponseDueAt = sellerResponseDueAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public Instant getLastRelatedCompletionAt() {
        return lastRelatedCompletionAt;
    }

    public void setLastRelatedCompletionAt(Instant lastRelatedCompletionAt) {
        this.lastRelatedCompletionAt = lastRelatedCompletionAt;
    }

    public String getCommandKey() {
        return commandKey;
    }

    public void setCommandKey(String commandKey) {
        this.commandKey = commandKey;
    }

    /**
     * Bóc tách mã lý do chuẩn hóa từ tiền tố của description.
     */
    public ReturnReasonCode getReasonCode() {
        return ReturnReasonCode.parse(this.description).reasonCode();
    }

    /**
     * Lấy nội dung mô tả chi tiết của người dùng sau khi đã lọc bỏ tiền tố [CODE].
     */
    public String getCleanDescription() {
        return ReturnReasonCode.parse(this.description).detail();
    }

    /**
     * Gán lý do chuẩn hóa và văn bản chi tiết với định dạng tiền tố an toàn.
     */
    public void setReasonAndDetail(ReturnReasonCode code, String detail) {
        this.description = ReturnReasonCode.format(code, detail);
    }
}
