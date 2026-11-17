-- ============================================================================
-- V42__bd5_damage_exemption_resubmission_hardening.sql
-- Module: Trust & Safety / Returns & Multi-round Disputes
-- Business Context: BD-5 Damage Exemption Resubmission Hardening (Phase 1)
-- ============================================================================

-- 1. Bổ sung các cột Idempotency Resubmission vào bảng public.case_evidence_requests
ALTER TABLE public.case_evidence_requests
    ADD COLUMN IF NOT EXISTS fulfillment_command_key VARCHAR(150),
    ADD COLUMN IF NOT EXISTS fulfillment_payload_hash CHAR(64);

-- Ràng buộc Idempotency nộp bổ sung hoàn thành request (API 3 resubmission - D-07 Option 1 PO APPROVED)
CREATE UNIQUE INDEX IF NOT EXISTS uq_case_evidence_requests_fulfill_cmd_key
    ON public.case_evidence_requests(fulfillment_command_key)
    WHERE fulfillment_command_key IS NOT NULL;

-- 2. Bảng vật lý lưu trữ đề xuất yêu cầu bằng chứng từ ADMIN (Schema public)
CREATE TABLE IF NOT EXISTS public.case_evidence_proposals (
    proposal_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id             BIGINT NOT NULL REFERENCES public.cases(case_id) ON DELETE RESTRICT,
    round_id            BIGINT NOT NULL REFERENCES public.case_actions(source_case_rounds_round_id) ON DELETE RESTRICT,
    admin_id            BIGINT NOT NULL REFERENCES public.users(user_id) ON DELETE RESTRICT,
    status              VARCHAR(20) NOT NULL DEFAULT 'PROPOSED',
    recommendation      TEXT NOT NULL,
    suggested_points    JSONB,
    reviewed_by         BIGINT REFERENCES public.users(user_id) ON DELETE RESTRICT,
    reviewed_at         TIMESTAMP WITH TIME ZONE,
    dismiss_reason      TEXT,
    request_id          UUID REFERENCES public.case_evidence_requests(request_id) ON DELETE SET NULL,
    command_key         VARCHAR(150) NOT NULL,
    payload_hash        CHAR(64) NOT NULL,
    review_command_key  VARCHAR(150),
    review_payload_hash CHAR(64),
    proposed_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_cep_status CHECK (status IN ('PROPOSED', 'ACCEPTED', 'DISMISSED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_case_evidence_proposals_cmd_key
    ON public.case_evidence_proposals(command_key);

CREATE UNIQUE INDEX IF NOT EXISTS uq_case_evidence_proposals_review_cmd_key
    ON public.case_evidence_proposals(review_command_key)
    WHERE review_command_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_case_evidence_proposals_lookup
    ON public.case_evidence_proposals(case_id, status);

-- 3. Bổ sung trường request_id vào bảng vật lý public.case_evidence (D-06 Option A)
ALTER TABLE public.case_evidence
    ADD COLUMN IF NOT EXISTS request_id UUID REFERENCES public.case_evidence_requests(request_id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_case_evidence_request_link
    ON public.case_evidence(request_id)
    WHERE request_id IS NOT NULL;

-- 4. Cập nhật các Compatibility Views tại schema og_compat phục vụ JPA Entities
-- Làm mới view case_evidence_requests để nhận 2 cột mới thêm từ ALTER TABLE
CREATE OR REPLACE VIEW og_compat.case_evidence_requests AS
    SELECT * FROM public.case_evidence_requests;

CREATE OR REPLACE VIEW og_compat.case_evidence_proposals AS
    SELECT * FROM public.case_evidence_proposals;

CREATE OR REPLACE VIEW og_compat.case_evidence AS
    SELECT p.evidence_id::uuid AS evidence_id,
           p.round_id::bigint AS round_id,
           p.submitted_by::bigint AS submitted_by,
           p.submission_no::integer AS submission_no,
           p.media_type::character varying(10) AS media_type,
           p.object_key::text AS object_key,
           p.text_content::text AS text_content,
           p.content_digest::character(64) AS content_digest,
           p.file_size_bytes::bigint AS file_size_bytes,
           p.duration_seconds::integer AS duration_seconds,
           p.submitted_at::timestamp with time zone AS submitted_at,
           p.deleted_at::timestamp with time zone AS deleted_at,
           p.request_id::uuid AS request_id
    FROM public.case_evidence p
    WHERE p.record_type = 'case_evidence';

