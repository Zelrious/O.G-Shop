package com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity mapping to og_compat.case_evidence table/view.
 * Repositories multi-round return evidence (images, videos).
 * Căn cứ nghiệp vụ: BR-UC62-07 (tối đa 5 ảnh, 2 video <= 60s, append-only).
 * Dẫn chứng migration: V19 (line 85-103), V22 (line 2942).
 */
@Entity
@Table(name = "case_evidence", schema = "og_compat")
public class CaseEvidenceEntity {

    public static final String MEDIA_IMAGE = "IMAGE";
    public static final String MEDIA_VIDEO = "VIDEO";

    @Id
    @Column(name = "evidence_id", nullable = false)
    private UUID id;

    @Column(name = "round_id", nullable = false)
    private Long roundId;

    @Column(name = "submitted_by", nullable = false)
    private Long submittedBy;

    @Column(name = "submission_no", nullable = false)
    private Integer submissionNo = 1;

    @Column(name = "media_type", nullable = false, length = 10)
    private String mediaType;

    @Column(name = "object_key", nullable = false, columnDefinition = "text")
    private String objectKey;

    @Column(name = "text_content", columnDefinition = "text")
    private String textContent;

    @Column(name = "content_digest", length = 64)
    private String contentDigest;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected CaseEvidenceEntity() {
    }

    public CaseEvidenceEntity(UUID id, Long roundId, Long submittedBy, Integer submissionNo,
                              String mediaType, String objectKey, String textContent,
                              String contentDigest, Long fileSizeBytes, Integer durationSeconds) {
        this.id = id != null ? id : UUID.randomUUID();
        this.roundId = roundId;
        this.submittedBy = submittedBy;
        this.submissionNo = submissionNo != null ? submissionNo : 1;
        this.mediaType = mediaType;
        this.objectKey = objectKey;
        this.textContent = textContent;
        this.contentDigest = contentDigest;
        this.fileSizeBytes = fileSizeBytes;
        this.durationSeconds = durationSeconds;
        this.submittedAt = Instant.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getRoundId() {
        return roundId;
    }

    public void setRoundId(Long roundId) {
        this.roundId = roundId;
    }

    public Long getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(Long submittedBy) {
        this.submittedBy = submittedBy;
    }

    public Integer getSubmissionNo() {
        return submissionNo;
    }

    public void setSubmissionNo(Integer submissionNo) {
        this.submissionNo = submissionNo;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    public String getContentDigest() {
        return contentDigest;
    }

    public void setContentDigest(String contentDigest) {
        this.contentDigest = contentDigest;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
