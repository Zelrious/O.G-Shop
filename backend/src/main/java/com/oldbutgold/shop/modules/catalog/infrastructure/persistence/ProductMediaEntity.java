package com.oldbutgold.shop.modules.catalog.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "product_media")
public class ProductMediaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "media_id")
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "media_type", nullable = false, length = 10)
    private String mediaType;

    @Column(name = "media_url", nullable = false, columnDefinition = "text")
    private String mediaUrl;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProductMediaEntity() {
    }

    public ProductMediaEntity(Long productId, String mediaType, String mediaUrl, int displayOrder, Instant createdAt) {
        this.productId = productId;
        this.mediaType = mediaType;
        this.mediaUrl = mediaUrl;
        this.displayOrder = displayOrder;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getMediaType() { return mediaType; }
    public String getMediaUrl() { return mediaUrl; }
    public int getDisplayOrder() { return displayOrder; }
    public Instant getCreatedAt() { return createdAt; }
}
