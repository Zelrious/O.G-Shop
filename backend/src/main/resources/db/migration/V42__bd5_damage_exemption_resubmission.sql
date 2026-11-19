-- ============================================================================
-- V38__bd5_damage_exemption_resubmission.sql
-- Module: Trust & Safety / Returns & Multi-round Disputes
-- Business Context: BD-5 Damage Exemption Evidence Request (API 1 Scope)
-- ============================================================================

-- 1. Bảng vật lý lưu trữ yêu cầu bổ sung bằng chứng từ KTV (Schema public)
CREATE TABLE IF NOT EXISTS public.case_evidence_requests (
    request_id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id                     BIGINT NOT NULL REFERENCES public.cases(case_id) ON DELETE RESTRICT,
    round_id                    BIGINT NOT NULL REFERENCES public.case_actions(source_case_rounds_round_id) ON DELETE RESTRICT,
    ktv_id                      BIGINT NOT NULL REFERENCES public.users(user_id) ON DELETE RESTRICT,
    status                      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason                      TEXT NOT NULL,
    requested_items             JSONB,
    issued_at                   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deadline_at                 TIMESTAMP WITH TIME ZONE NOT NULL,
    fulfilled_at                TIMESTAMP WITH TIME ZONE,
    cancelled_at                TIMESTAMP WITH TIME ZONE,
    expired_notified_at         TIMESTAMP WITH TIME ZONE,
    command_key                 VARCHAR(150) NOT NULL,
    payload_hash                CHAR(64) NOT NULL,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_cer_status CHECK (status IN ('PENDING', 'FULFILLED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT chk_cer_deadline CHECK (deadline_at > issued_at)
);

-- Ràng buộc bất biến cốt lõi: Tối đa 1 yêu cầu PENDING trên mỗi Case
CREATE UNIQUE INDEX IF NOT EXISTS idx_case_evidence_requests_pending_unique
    ON public.case_evidence_requests(case_id)
    WHERE status = 'PENDING';

-- Ràng buộc Idempotency phát hành request (API 1 - D-07)
CREATE UNIQUE INDEX IF NOT EXISTS uq_case_evidence_requests_cmd_key
    ON public.case_evidence_requests(command_key);

CREATE INDEX IF NOT EXISTS idx_case_evidence_requests_lookup
    ON public.case_evidence_requests(case_id, round_id, status);

-- 2. Tạo Compatibility View tại schema og_compat phục vụ JPA Entities
CREATE OR REPLACE VIEW og_compat.case_evidence_requests AS
    SELECT * FROM public.case_evidence_requests;
