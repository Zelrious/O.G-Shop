package com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA Entity mapping to og_compat.case_rounds table/view.
 * Repositories multi-round review stages for each case.
 * Dẫn chứng migration: V19 (line 51-64), V22 (line 2902).
 */
@Entity
@Table(name = "case_rounds", schema = "og_compat")
public class CaseRoundEntity {

    public static final String STAGE_RETURN_APPROVAL = "RETURN_APPROVAL";
    public static final String STAGE_POST_RETURN_RESOLUTION = "POST_RETURN_RESOLUTION";
    public static final String STAGE_SYSTEM_REVIEW = "SYSTEM_REVIEW";

    public static final String STATE_OPEN = "OPEN";
    public static final String STATE_DECIDED = "DECIDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "round_id")
    private Long id;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "round_no", nullable = false)
    private Integer roundNo;

    @Column(name = "stage", nullable = false, length = 25)
    private String stage;

    @Column(name = "state", nullable = false, length = 15)
    private String state = STATE_OPEN;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt = Instant.now();

    @Column(name = "complete_submission_at")
    private Instant completeSubmissionAt;

    @Column(name = "ktv_due_at")
    private Instant ktvDueAt;

    @Column(name = "valid_damage_request_at")
    private Instant validDamageRequestAt;

    protected CaseRoundEntity() {
    }

    public CaseRoundEntity(Long caseId, Integer roundNo, String stage) {
        this.caseId = caseId;
        this.roundNo = roundNo;
        this.stage = stage;
        this.state = STATE_OPEN;
        this.openedAt = Instant.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCaseId() {
        return caseId;
    }

    public void setCaseId(Long caseId) {
        this.caseId = caseId;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(Instant openedAt) {
        this.openedAt = openedAt;
    }

    public Instant getCompleteSubmissionAt() {
        return completeSubmissionAt;
    }

    public void setCompleteSubmissionAt(Instant completeSubmissionAt) {
        this.completeSubmissionAt = completeSubmissionAt;
        if (completeSubmissionAt != null) {
            // SLA 48h từ khi hồ sơ đủ nội dung theo BR-UC62-03 và V19 constraint
            this.ktvDueAt = completeSubmissionAt.plus(java.time.Duration.ofHours(48));
        }
    }

    public Instant getKtvDueAt() {
        return ktvDueAt;
    }

    public void setKtvDueAt(Instant ktvDueAt) {
        this.ktvDueAt = ktvDueAt;
    }

    public Instant getValidDamageRequestAt() {
        return validDamageRequestAt;
    }

    public void setValidDamageRequestAt(Instant validDamageRequestAt) {
        this.validDamageRequestAt = validDamageRequestAt;
    }
}
