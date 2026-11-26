-- H2 compatibility fixture schema for test profile (replaces runtime DDL previously in DisputeRepository)
CREATE SCHEMA IF NOT EXISTS og_compat;

CREATE TABLE IF NOT EXISTS public.dispute_cases (
    dispute_id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    reason VARCHAR(64) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    buyer_evidence_urls TEXT NULL,
    video_unboxing_url TEXT NULL,
    seller_response TEXT NULL,
    seller_evidence_urls TEXT NULL,
    seller_response_due_at TIMESTAMP WITH TIME ZONE NULL,
    verdict_action VARCHAR(64) NULL,
    verdict_notes TEXT NULL,
    refund_amount NUMERIC(19, 2) NULL,
    penalty_points INT NOT NULL DEFAULT 0,
    resolved_by BIGINT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE NULL,
    return_tracking_code VARCHAR(100) NULL,
    return_carrier VARCHAR(64) NULL,
    return_shipment_status VARCHAR(32) NULL,
    return_handed_over_at TIMESTAMP WITH TIME ZONE NULL,
    return_handed_over_by BIGINT NULL,
    return_delivered_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS og_compat.reviews (
    review_id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    reviewee_id BIGINT NOT NULL,
    rating SMALLINT NOT NULL,
    comment VARCHAR(2000) NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE NULL,
    record_type VARCHAR(32) NOT NULL DEFAULT 'reviews'
);

CREATE TABLE IF NOT EXISTS og_compat.penalty_ledger (
    entry_id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    report_id BIGINT NULL,
    policy_id BIGINT NULL,
    points_delta INT NOT NULL,
    adjustment_of UUID NULL,
    decided_by BIGINT NULL,
    reason TEXT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    command_key VARCHAR(150) NULL,
    record_type VARCHAR(32) NOT NULL DEFAULT 'penalty_ledger'
);

