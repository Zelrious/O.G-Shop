-- V64: Move legacy dispute_cases runtime DDL into Flyway migration and add public seller review lookup index

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
    seller_response_due_at TIMESTAMPTZ NULL,
    verdict_action VARCHAR(64) NULL,
    verdict_notes TEXT NULL,
    refund_amount NUMERIC(19, 2) NULL,
    penalty_points INT NOT NULL DEFAULT 0,
    resolved_by BIGINT NULL,
    resolved_at TIMESTAMPTZ NULL,
    return_tracking_code VARCHAR(100) NULL,
    return_carrier VARCHAR(64) NULL,
    return_shipment_status VARCHAR(32) NULL,
    return_handed_over_at TIMESTAMPTZ NULL,
    return_handed_over_by BIGINT NULL,
    return_delivered_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE public.dispute_cases ADD COLUMN IF NOT EXISTS return_tracking_code VARCHAR(100) NULL;
ALTER TABLE public.dispute_cases ADD COLUMN IF NOT EXISTS return_carrier VARCHAR(64) NULL;
ALTER TABLE public.dispute_cases ADD COLUMN IF NOT EXISTS return_shipment_status VARCHAR(32) NULL;
ALTER TABLE public.dispute_cases ADD COLUMN IF NOT EXISTS return_handed_over_at TIMESTAMPTZ NULL;
ALTER TABLE public.dispute_cases ADD COLUMN IF NOT EXISTS return_handed_over_by BIGINT NULL;
ALTER TABLE public.dispute_cases ADD COLUMN IF NOT EXISTS return_delivered_at TIMESTAMPTZ NULL;

CREATE INDEX IF NOT EXISTS idx_dispute_cases_order_created
    ON public.dispute_cases (order_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_dispute_cases_status_created
    ON public.dispute_cases (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_reviews_public_seller_lookup
    ON public.reviews (reviewee_id, deleted_at, rating, created_at DESC, review_id DESC)
    WHERE record_type = 'reviews';

