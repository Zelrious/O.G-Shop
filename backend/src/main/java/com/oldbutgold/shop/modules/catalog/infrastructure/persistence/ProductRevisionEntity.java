package com.oldbutgold.shop.modules.catalog.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "product_revisions", schema = "og_compat")
public class ProductRevisionEntity {

    public static final String STATE_DRAFT = "DRAFT";
    public static final String STATE_PUBLISHED = "PUBLISHED";
    public static final String STATE_SUPERSEDED = "SUPERSEDED";
    public static final String STATE_ARCHIVED = "ARCHIVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "revision_id")
    private Long revisionId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "revision_no", nullable = false)
    private Long revisionNo;

    @Column(name = "provenance", nullable = false, length = 20)
    private String provenance = "DIRECT";

    @Column(name = "state", nullable = false, length = 15)
    private String state = STATE_DRAFT;

    @Column(name = "listed_unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal listedUnitPrice;

    @Column(name = "quantity", nullable = false)
    private int quantity = 1;

    @Column(name = "return_allowed", nullable = false)
    private boolean returnAllowed = true;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "delivery_options", columnDefinition = "jsonb")
    private String deliveryOptions;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "content_snapshot", columnDefinition = "jsonb")
    private String contentSnapshot;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "checklist_snapshot", columnDefinition = "jsonb")
    private String checklistSnapshot;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    protected ProductRevisionEntity() {}

    public ProductRevisionEntity(Long productId, Long revisionNo, String provenance, String state,
                                 BigDecimal listedUnitPrice, int quantity, boolean returnAllowed,
                                 String deliveryOptions, String contentSnapshot, String checklistSnapshot,
                                 Instant createdAt, Instant submittedAt) {
        this.productId = productId;
        this.revisionNo = revisionNo;
        this.provenance = provenance != null ? provenance : "DIRECT";
        this.state = state != null ? state : STATE_DRAFT;
        this.listedUnitPrice = listedUnitPrice;
        this.quantity = quantity;
        this.returnAllowed = returnAllowed;
        this.deliveryOptions = deliveryOptions;
        this.contentSnapshot = contentSnapshot;
        this.checklistSnapshot = checklistSnapshot;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.submittedAt = submittedAt;
    }

    public Long getRevisionId() { return revisionId; }
    public void setRevisionId(Long revisionId) { this.revisionId = revisionId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Long getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Long revisionNo) { this.revisionNo = revisionNo; }

    public String getProvenance() { return provenance; }
    public void setProvenance(String provenance) { this.provenance = provenance; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public BigDecimal getListedUnitPrice() { return listedUnitPrice; }
    public void setListedUnitPrice(BigDecimal listedUnitPrice) { this.listedUnitPrice = listedUnitPrice; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public boolean isReturnAllowed() { return returnAllowed; }
    public void setReturnAllowed(boolean returnAllowed) { this.returnAllowed = returnAllowed; }

    public String getDeliveryOptions() { return deliveryOptions; }
    public void setDeliveryOptions(String deliveryOptions) { this.deliveryOptions = deliveryOptions; }

    public String getContentSnapshot() { return contentSnapshot; }
    public void setContentSnapshot(String contentSnapshot) { this.contentSnapshot = contentSnapshot; }

    public String getChecklistSnapshot() { return checklistSnapshot; }
    public void setChecklistSnapshot(String checklistSnapshot) { this.checklistSnapshot = checklistSnapshot; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
}
