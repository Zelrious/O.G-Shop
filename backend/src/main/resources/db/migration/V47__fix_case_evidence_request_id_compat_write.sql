-- ============================================================================
-- V43__fix_case_evidence_request_id_compat_write.sql
-- Module: Trust & Safety / Returns & Multi-round Disputes
-- Business Context: BD-5 Damage Exemption Resubmission Hardening
-- Defect Fix: Forward request_id in og70_write_view() INSTEAD OF trigger
--             on og_compat.case_evidence to public.case_evidence.
-- ============================================================================

DO $migration$
DECLARE
    def text;
    prev text;
BEGIN
    def := pg_get_functiondef('public.og70_write_view()'::regprocedure);

    -- Idempotency check: if already patched with case_evidence request_id, skip function rewriting
    IF def LIKE '%record_type,request_id) SELECT r.evidence_id%r.request_id FROM jsonb_populate_record(NULL::og_compat.case_evidence%' THEN
        RETURN;
    END IF;

    -- 1. case_evidence INSERT: include request_id column and value
    prev := def;
    def := replace(def,
        $s$deleted_at,record_type) SELECT r.evidence_id,r.round_id,r.submitted_by,r.submission_no,r.media_type,r.object_key,r.text_content,r.content_digest,r.file_size_bytes,r.duration_seconds,r.submitted_at,r.deleted_at,''case_evidence'' FROM jsonb_populate_record(NULL::og_compat.case_evidence,$1) r$s$,
        $s$deleted_at,record_type,request_id) SELECT r.evidence_id,r.round_id,r.submitted_by,r.submission_no,r.media_type,r.object_key,r.text_content,r.content_digest,r.file_size_bytes,r.duration_seconds,r.submitted_at,r.deleted_at,''case_evidence'',r.request_id FROM jsonb_populate_record(NULL::og_compat.case_evidence,$1) r$s$
    );
    IF def = prev THEN
        RAISE EXCEPTION 'Migration V43 failed: anchor "case_evidence INSERT" not found in og70_write_view()';
    END IF;

    -- 2. case_evidence UPDATE SET: include request_id assignment
    prev := def;
    def := replace(def,
        $s$deleted_at=r.deleted_at FROM jsonb_populate_record(NULL::og_compat.case_evidence,$1) r$s$,
        $s$deleted_at=r.deleted_at,request_id=r.request_id FROM jsonb_populate_record(NULL::og_compat.case_evidence,$1) r$s$
    );
    IF def = prev THEN
        RAISE EXCEPTION 'Migration V43 failed: anchor "case_evidence UPDATE SET" not found in og70_write_view()';
    END IF;

    -- 3. case_evidence result: include request_id in returned jsonb
    prev := def;
    def := replace(def,
        $s$'duration_seconds',stored->'duration_seconds','submitted_at',stored->'submitted_at','deleted_at',stored->'deleted_at'$s$,
        $s$'duration_seconds',stored->'duration_seconds','submitted_at',stored->'submitted_at','deleted_at',stored->'deleted_at','request_id',stored->'request_id'$s$
    );
    IF def = prev THEN
        RAISE EXCEPTION 'Migration V43 failed: anchor "case_evidence result" not found in og70_write_view()';
    END IF;

    EXECUTE def;
END;
$migration$;

