-- ============================================================================
-- V4: MVP Seller Activation Baseline
-- Allows instant seller verification via 'MVP_BYPASS' method without
-- requiring manual admin review or artificial AI biometric metrics.
-- ============================================================================

ALTER TABLE seller_verifications DROP CONSTRAINT ck_seller_verifications_method;
ALTER TABLE seller_verifications ADD CONSTRAINT ck_seller_verifications_method CHECK (
    verification_method IN ('MANUAL_ID_DOCUMENT', 'AI_EKYC', 'MVP_BYPASS')
);

ALTER TABLE seller_verifications DROP CONSTRAINT ck_seller_verifications_resolution;
ALTER TABLE seller_verifications ADD CONSTRAINT ck_seller_verifications_resolution CHECK (
    (status = 'PENDING' AND reviewed_at IS NULL AND verified_by IS NULL AND rejection_reason IS NULL)
    OR
    (status = 'VERIFIED' AND reviewed_at IS NOT NULL AND rejection_reason IS NULL
        AND (
            (verification_method = 'AI_EKYC' AND verified_by IS NULL)
            OR (verification_method = 'MANUAL_ID_DOCUMENT' AND verified_by IS NOT NULL)
            OR (verification_method = 'MVP_BYPASS' AND verified_by IS NULL)
        ))
    OR
    (status = 'REJECTED' AND reviewed_at IS NOT NULL AND rejection_reason IS NOT NULL)
);
