package com.oldbutgold.shop.modules.catalog.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "product_media", schema = "og_compat")
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

    @Column(name = "thumbnail_url", columnDefinition = "text")
    private String thumbnailUrl;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "cloudinary_public_id", length = 255)
    private String cloudinaryPublicId;

    @Column(name = "mime_type", length = 50)
    private String mimeType;

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

    public ProductMediaEntity(Long productId, String mediaType, String mediaUrl, int displayOrder,
                              String thumbnailUrl, Integer durationSeconds, Long fileSizeBytes,
                              String mimeType, Instant createdAt) {
        this.productId = productId;
        this.mediaType = mediaType;
        this.mediaUrl = mediaUrl;
        this.displayOrder = displayOrder;
        this.thumbnailUrl = thumbnailUrl;
        this.durationSeconds = durationSeconds;
        this.fileSizeBytes = fileSizeBytes;
        this.mimeType = mimeType;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getMediaType() { return mediaType; }
    public String getMediaUrl() { return mediaUrl; }
    public int getDisplayOrder() { return displayOrder; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public String getCloudinaryPublicId() { return cloudinaryPublicId; }
    public String getMimeType() { return mimeType; }
    public Instant getCreatedAt() { return createdAt; }
}
