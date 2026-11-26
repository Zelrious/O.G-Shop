-- ============================================================================
-- V61__uc61_uc64_uc48_uc81_remediation.sql
-- Consolidated remediation for:
--   1. UC61/UC22/UC11: Reward ledger negative review clawbacks (CREDIT_REVIEW <> 0),
--      recipient (U, W) waiver tracking on public.reviews, active review uniqueness,
--      and legacy delivered-order eligibility even after DISPUTED/REFUNDED transitions.
--   2. UC64/UC65/UC76/UC49/UC53: System issue escalation by KTV, PO-02 Option A
--      designated Admin final authority + ADMIN handover audit, SYSTEM_ISSUE rejection
--      preservation (REJECTED state), and compensation anti-duplication index.
--   3. UC48: Scoped unique anti-duplicate index on notification_reminders for UC48.
-- ============================================================================

-- 1. Chain og70_validate_row() cleanly into og61_validate_existing_row() so
--    CREDIT_REVIEW accepts both positive grants and negative clawbacks (<> 0),
--    while preserving all V58 shipping-points rules in og61_validate_existing_row().
DO $migration$
DECLARE
    definition text;
BEGIN
    IF to_regprocedure('public.og61_validate_existing_row(text,jsonb,boolean)') IS NULL THEN
        definition := pg_get_functiondef('public.og70_validate_row(text,jsonb,boolean)'::regprocedure);
        EXECUTE replace(definition, 'FUNCTION public.og70_validate_row(', 'FUNCTION public.og61_validate_existing_row(');
    END IF;

    IF to_regprocedure('public.og61_validate_existing_row(text,jsonb,boolean)') IS NULL THEN
        RAISE EXCEPTION 'Migration V61 failed: backup validator og61_validate_existing_row not created' USING ERRCODE = '23514';
    END IF;
END;
$migration$;

CREATE OR REPLACE FUNCTION public.og70_validate_row(s text, j jsonb, skip_not_valid boolean DEFAULT false)
 RETURNS void
 LANGUAGE plpgsql
AS $function$
DECLARE
    r og_compat.reward_ledger;
BEGIN
    IF s = 'reward_ledger' AND j->>'entry_type' = 'CREDIT_REVIEW' THEN
        r := jsonb_populate_record(NULL::og_compat.reward_ledger, j);

        IF r.entry_id IS NULL OR r.user_id IS NULL OR r.created_at IS NULL
            OR r.order_id IS NULL OR r.command_key IS NULL
            OR NULLIF(btrim(r.command_key), '') IS NULL THEN
            RAISE EXCEPTION 'Required field in reward_ledger CREDIT_REVIEW' USING ERRCODE = '23502';
        END IF;

        IF r.points_delta IS NULL OR r.points_delta = 0
            OR r.group_id IS NOT NULL
            OR r.reason IS NULL OR NULLIF(btrim(r.reason), '') IS NULL THEN
            RAISE EXCEPTION 'Invalid CREDIT_REVIEW entry in reward_ledger' USING ERRCODE = '23514';
        END IF;

        RETURN;
    END IF;

    IF to_regprocedure('public.og61_validate_existing_row(text,jsonb,boolean)') IS NULL THEN
        RAISE EXCEPTION 'Backup validator public.og61_validate_existing_row missing' USING ERRCODE = '23514';
    END IF;

    PERFORM public.og61_validate_existing_row(s, j, skip_not_valid);
END;
$function$;

-- 2. Track recipient unwaived/waived coins on public.reviews for PO-01 Option A
ALTER TABLE public.reviews
    ADD COLUMN IF NOT EXISTS recipient_unwaived_coins BIGINT NOT NULL DEFAULT 0 CHECK (recipient_unwaived_coins >= 0),
    ADD COLUMN IF NOT EXISTS recipient_waived_coins BIGINT NOT NULL DEFAULT 0 CHECK (recipient_waived_coins >= 0);

-- Backfill existing active reviews' unwaived recipient coins from reward_ledger if any exist
UPDATE public.reviews rev
SET recipient_unwaived_coins = GREATEST(0, COALESCE((
    SELECT SUM(rl.points_delta)
    FROM og_compat.reward_ledger rl
    WHERE rl.order_id = rev.order_id
      AND rl.user_id = rev.reviewee_id
      AND rl.entry_type = 'CREDIT_REVIEW'
      AND (
          rl.command_key LIKE 'REVIEW_RECIPIENT:' || rev.review_id::text || ':%'
          OR rl.command_key LIKE 'REVIEW_RECEIVE_%:' || rev.order_id::text || ':' || rev.reviewee_id::text
      )
), 0))
WHERE rev.record_type = 'reviews';

-- Soft-deduplicate any historical duplicate active reviews per (order_id, reviewer_id) before adding unique index
WITH ranked AS (
    SELECT review_id,
           ROW_NUMBER() OVER (PARTITION BY order_id, reviewer_id ORDER BY COALESCE(updated_at, created_at) DESC, review_id DESC) AS rn
    FROM public.reviews
    WHERE record_type = 'reviews' AND deleted_at IS NULL
)
UPDATE public.reviews r
SET deleted_at = COALESCE(r.updated_at, r.created_at, CURRENT_TIMESTAMP)
FROM ranked rk
WHERE r.review_id = rk.review_id AND rk.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_reviews_order_reviewer_active
ON public.reviews (order_id, reviewer_id)
WHERE record_type = 'reviews' AND deleted_at IS NULL;

-- Update review party guard (both legacy trigger fn & og70 wrapper) to:
-- (a) support LEGACY_V14 valid delivery even if order later moved to DISPUTED/REFUNDED (BR-UC61-01)
-- (b) keep identity check in BEFORE UPDATE while deferring review_revisions recording to AFTER UPDATE
--     so updating edit_history on public.reviews does not suppress the outer UPDATE in PostgreSQL.
CREATE OR REPLACE FUNCTION public.og_guard_review_parties()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'public', '$user', 'public'
AS $function$
DECLARE o og_compat.orders;
BEGIN
    SELECT * INTO STRICT o FROM og_compat.orders WHERE order_id = NEW.order_id;
    IF NOT ((NEW.reviewer_id = o.buyer_id AND NEW.reviewee_id = o.seller_id)
        OR (NEW.reviewer_id = o.seller_id AND NEW.reviewee_id = o.buyer_id))
        OR (o.workflow_model = 'UC83' AND o.valid_delivered_at IS NULL)
        OR (o.workflow_model = 'LEGACY_V14' AND NOT (
            o.valid_delivered_at IS NOT NULL
            OR o.status IN ('DELIVERED', 'COMPLETED')
            OR EXISTS (
                SELECT 1 FROM og_compat.shipments s
                WHERE s.order_id = o.order_id
                  AND s.leg = 'OUTBOUND'
                  AND (s.status = 'DELIVERED' OR s.delivered_at IS NOT NULL)
            )
        )) THEN
        RAISE EXCEPTION 'Review requires delivered order and its two parties' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF ROW(NEW.order_id, NEW.reviewer_id, NEW.reviewee_id) IS DISTINCT FROM ROW(OLD.order_id, OLD.reviewer_id, OLD.reviewee_id) THEN
            RAISE EXCEPTION 'Review identity immutable' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$function$;

CREATE OR REPLACE FUNCTION public.og70_row_reviews_og_guard_review_parties(p_new jsonb, p_old jsonb, p_op text, p_phase text, p_depth integer, p_args text[])
 RETURNS jsonb
 LANGUAGE plpgsql
 SET search_path TO 'public', 'og_compat'
AS $function$
DECLARE
    NEW og_compat.reviews;
    OLD og_compat.reviews;
    TG_OP text := p_op;
    o og_compat.orders;
BEGIN
    NEW := jsonb_populate_record(NULL::og_compat.reviews, coalesce(p_new, '{}'::jsonb));
    OLD := jsonb_populate_record(NULL::og_compat.reviews, coalesce(p_old, '{}'::jsonb));
    SELECT * INTO STRICT o FROM og_compat.orders WHERE order_id = NEW.order_id;
    IF NOT ((NEW.reviewer_id = o.buyer_id AND NEW.reviewee_id = o.seller_id)
        OR (NEW.reviewer_id = o.seller_id AND NEW.reviewee_id = o.buyer_id))
        OR (o.workflow_model = 'UC83' AND o.valid_delivered_at IS NULL)
        OR (o.workflow_model = 'LEGACY_V14' AND NOT (
            o.valid_delivered_at IS NOT NULL
            OR o.status IN ('DELIVERED', 'COMPLETED')
            OR EXISTS (
                SELECT 1 FROM og_compat.shipments s
                WHERE s.order_id = o.order_id
                  AND s.leg = 'OUTBOUND'
                  AND (s.status = 'DELIVERED' OR s.delivered_at IS NOT NULL)
            )
        )) THEN
        RAISE EXCEPTION 'Review requires delivered order and its two parties' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF ROW(NEW.order_id, NEW.reviewer_id, NEW.reviewee_id) IS DISTINCT FROM ROW(OLD.order_id, OLD.reviewer_id, OLD.reviewee_id) THEN
            RAISE EXCEPTION 'Review identity immutable' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN to_jsonb(NEW);
END;
$function$;

CREATE OR REPLACE FUNCTION public.og61_record_review_revision_after_update()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'public', 'og_compat'
AS $function$
BEGIN
    IF NEW.record_type = 'reviews'
       AND ROW(NEW.rating, NEW.comment, NEW.deleted_at) IS DISTINCT FROM ROW(OLD.rating, OLD.comment, OLD.deleted_at) THEN
        INSERT INTO og_compat.review_revisions(review_id, original_record)
        VALUES (
            OLD.review_id,
            jsonb_build_object(
                'review_id', OLD.review_id,
                'order_id', OLD.order_id,
                'reviewer_id', OLD.reviewer_id,
                'reviewee_id', OLD.reviewee_id,
                'rating', OLD.rating,
                'comment', OLD.comment,
                'created_at', OLD.created_at,
                'updated_at', OLD.updated_at,
                'deleted_at', OLD.deleted_at
            )
        );
    END IF;
    RETURN NEW;
END;
$function$;

DROP TRIGGER IF EXISTS trg_og61_review_revision_after_update ON public.reviews;
CREATE TRIGGER trg_og61_review_revision_after_update
AFTER UPDATE ON public.reviews
FOR EACH ROW
EXECUTE FUNCTION public.og61_record_review_revision_after_update();

-- 3. Harden case_events & case_decisions for PO-02 Option A, SYSTEM_ISSUE rejection, and compensation guards
CREATE OR REPLACE FUNCTION public.og_guard_case_event()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'public', '$user', 'public'
AS $function$
DECLARE c og_compat.cases; o og_compat.orders; sys_case og_compat.cases;
BEGIN
    SELECT * INTO STRICT c FROM og_compat.cases WHERE case_id = NEW.case_id FOR UPDATE;
    NEW.occurred_at := clock_timestamp();
    IF NEW.event_type = 'ESCALATED' THEN
        IF NOT og_has_role(NEW.recipient_id, 'ADMIN') THEN
            RAISE EXCEPTION 'Escalation needs Admin recipient' USING ERRCODE = '23514';
        END IF;
        IF NEW.round_id IS NOT NULL AND EXISTS(
            SELECT 1 FROM og_compat.case_events
            WHERE case_id = c.case_id AND round_id = NEW.round_id AND event_type = 'ESCALATED'
        ) THEN
            RAISE EXCEPTION 'Round already escalated' USING ERRCODE = '23514';
        END IF;
        IF NEW.actor_id IS NOT NULL THEN
            IF NOT og_has_role(NEW.actor_id, 'KTV') THEN
                RAISE EXCEPTION 'Escalation needs KTV or SYSTEM actor' USING ERRCODE = '23514';
            END IF;
            IF c.state IN ('CLOSED', 'REJECTED') THEN
                RAISE EXCEPTION 'Cannot escalate closed or rejected case' USING ERRCODE = '23514';
            END IF;
        ELSE
            IF NEW.round_id IS NULL THEN
                RAISE EXCEPTION 'System escalation requires round_id' USING ERRCODE = '23514';
            END IF;
            IF c.state IN ('CLOSED', 'REJECTED') THEN
                RAISE EXCEPTION 'Cannot escalate closed or rejected case' USING ERRCODE = '23514';
            END IF;
            IF NOT EXISTS(SELECT 1 FROM og_compat.case_rounds WHERE round_id = NEW.round_id
                AND stage = 'POST_RETURN_RESOLUTION' AND state = 'OPEN') THEN
                RAISE EXCEPTION 'System escalation only for open POST_RETURN_RESOLUTION round' USING ERRCODE = '23514';
            END IF;
            IF NOT EXISTS(SELECT 1 FROM og_compat.case_rounds WHERE round_id = NEW.round_id
                AND complete_submission_at IS NOT NULL
                AND NEW.occurred_at > complete_submission_at + INTERVAL '48 hours') THEN
                RAISE EXCEPTION 'System escalation only after 48h SLA from complete submission' USING ERRCODE = '23514';
            END IF;
            IF EXISTS(SELECT 1 FROM og_compat.case_decisions WHERE round_id = NEW.round_id AND is_final) THEN
                RAISE EXCEPTION 'Cannot escalate round with final decision' USING ERRCODE = '23514';
            END IF;
        END IF;
    END IF;
    IF NEW.event_type = 'ASSIGNED' AND NEW.round_id IS NOT NULL AND EXISTS(
        SELECT 1 FROM og_compat.case_events
        WHERE case_id = c.case_id AND round_id = NEW.round_id AND event_type = 'ESCALATED'
    ) THEN
        IF NEW.actor_id IS NULL OR NOT og_has_role(NEW.actor_id, 'ADMIN')
            OR NEW.recipient_id IS NULL OR NOT og_has_role(NEW.recipient_id, 'ADMIN')
            OR NULLIF(btrim(NEW.reason), '') IS NULL THEN
            RAISE EXCEPTION 'Escalated case handover requires ADMIN actor, ADMIN recipient, and non-blank reason' USING ERRCODE = '23514';
        END IF;
    END IF;
    IF NEW.event_type = 'COMPENSATION' AND c.state = 'REJECTED' THEN
        RAISE EXCEPTION 'Cannot compensate rejected case' USING ERRCODE = '23514';
    END IF;
    IF NEW.event_type = 'DEADLINE_EXTENSION' THEN
        IF c.state IN ('CLOSED', 'REJECTED') THEN
            RAISE EXCEPTION 'Cannot extend deadline on closed or rejected case' USING ERRCODE = '23514';
        END IF;
        IF NEW.metadata ? 'systemCaseId' AND NULLIF(NEW.metadata->>'systemCaseId', '') IS NOT NULL THEN
            SELECT * INTO sys_case FROM og_compat.cases WHERE case_id = (NEW.metadata->>'systemCaseId')::bigint;
            IF NOT FOUND OR sys_case.case_type <> 'SYSTEM_ISSUE' OR sys_case.state <> 'CLOSED' THEN
                RAISE EXCEPTION 'Deadline extension requires confirmed CLOSED system issue case' USING ERRCODE = '23514';
            END IF;
        END IF;
    END IF;
    IF NEW.event_type = 'VALID_DAMAGE_REQUEST' THEN
        SELECT * INTO STRICT o FROM og_compat.orders WHERE order_id = c.order_id;
        IF NEW.actor_id <> o.seller_id OR c.valid_returned_at IS NULL OR NEW.occurred_at > c.seller_response_due_at
            OR NOT EXISTS(SELECT 1 FROM og_compat.case_rounds WHERE round_id = NEW.round_id
                AND stage = 'POST_RETURN_RESOLUTION' AND state = 'OPEN')
            OR NOT EXISTS(SELECT 1 FROM og_compat.case_evidence WHERE round_id = NEW.round_id AND submitted_by = NEW.actor_id) THEN
            RAISE EXCEPTION 'Only complete in-time Seller request blocks auto refund' USING ERRCODE = '23514';
        END IF;
        UPDATE og_compat.case_rounds
        SET valid_damage_request_at = NEW.occurred_at,
            complete_submission_at = COALESCE(complete_submission_at, NEW.occurred_at),
            ktv_due_at = COALESCE(complete_submission_at, NEW.occurred_at) + INTERVAL '48 hours'
        WHERE round_id = NEW.round_id;
    END IF;
    IF NEW.event_type = 'BUYER_WITHDRAWN' THEN
        IF NEW.actor_id IS NULL OR c.created_by IS NULL OR c.order_id IS NULL OR c.state IS NULL THEN
            RAISE EXCEPTION 'BUYER_WITHDRAWN requires valid non-null buyer identity and case references' USING ERRCODE = '23514';
        END IF;
        SELECT * INTO STRICT o FROM og_compat.orders WHERE order_id = c.order_id;
        IF o.buyer_id IS NULL THEN
            RAISE EXCEPTION 'BUYER_WITHDRAWN requires valid non-null order buyer' USING ERRCODE = '23514';
        END IF;
        IF NEW.actor_id <> c.created_by OR NEW.actor_id <> o.buyer_id THEN
            RAISE EXCEPTION 'Only the buyer who created the case can withdraw' USING ERRCODE = '23514';
        END IF;
        IF c.state IN ('RETURNING', 'POST_RETURN', 'CLOSED', 'REJECTED') THEN
            RAISE EXCEPTION 'Cannot withdraw case in state %', c.state USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$function$;

CREATE OR REPLACE FUNCTION public.og70_row_case_events_og_guard_case_event(p_new jsonb, p_old jsonb, p_op text, p_phase text, p_depth integer, p_args text[])
 RETURNS jsonb
 LANGUAGE plpgsql
 SET search_path TO 'public', 'og_compat'
AS $function$
DECLARE
    NEW og_compat.case_events;
    OLD og_compat.case_events;
    c og_compat.cases;
    o og_compat.orders;
    sys_case og_compat.cases;
BEGIN
    NEW := jsonb_populate_record(NULL::og_compat.case_events, coalesce(p_new, '{}'::jsonb));
    OLD := jsonb_populate_record(NULL::og_compat.case_events, coalesce(p_old, '{}'::jsonb));
    SELECT * INTO STRICT c FROM og_compat.cases WHERE case_id = NEW.case_id FOR UPDATE;
    NEW.occurred_at := clock_timestamp();
    PERFORM public.og28_check_case_response(NEW);
    IF NEW.event_type = 'ESCALATED' THEN
        IF NOT og_has_role(NEW.recipient_id, 'ADMIN') THEN
            RAISE EXCEPTION 'Escalation needs Admin recipient' USING ERRCODE = '23514';
        END IF;
        IF NEW.round_id IS NOT NULL AND EXISTS(
            SELECT 1 FROM og_compat.case_events
            WHERE case_id = c.case_id AND round_id = NEW.round_id AND event_type = 'ESCALATED'
        ) THEN
            RAISE EXCEPTION 'Round already escalated' USING ERRCODE = '23514';
        END IF;
        IF NEW.actor_id IS NOT NULL THEN
            IF NOT og_has_role(NEW.actor_id, 'KTV') THEN
                RAISE EXCEPTION 'Escalation needs KTV or SYSTEM actor' USING ERRCODE = '23514';
            END IF;
            IF c.state IN ('CLOSED', 'REJECTED') THEN
                RAISE EXCEPTION 'Cannot escalate closed or rejected case' USING ERRCODE = '23514';
            END IF;
        ELSE
            IF NEW.round_id IS NULL THEN
                RAISE EXCEPTION 'System escalation requires round_id' USING ERRCODE = '23514';
            END IF;
            IF c.state IN ('CLOSED', 'REJECTED') THEN
                RAISE EXCEPTION 'Cannot escalate closed or rejected case' USING ERRCODE = '23514';
            END IF;
            IF NOT EXISTS(SELECT 1 FROM og_compat.case_rounds WHERE round_id = NEW.round_id
                AND stage = 'POST_RETURN_RESOLUTION' AND state = 'OPEN') THEN
                RAISE EXCEPTION 'System escalation only for open POST_RETURN_RESOLUTION round' USING ERRCODE = '23514';
            END IF;
            IF NOT EXISTS(SELECT 1 FROM og_compat.case_rounds WHERE round_id = NEW.round_id
                AND complete_submission_at IS NOT NULL
                AND NEW.occurred_at > complete_submission_at + INTERVAL '48 hours') THEN
                RAISE EXCEPTION 'System escalation only after 48h SLA from complete submission' USING ERRCODE = '23514';
            END IF;
            IF EXISTS(SELECT 1 FROM og_compat.case_decisions WHERE round_id = NEW.round_id AND is_final) THEN
                RAISE EXCEPTION 'Cannot escalate round with final decision' USING ERRCODE = '23514';
            END IF;
        END IF;
    END IF;
    IF NEW.event_type = 'ASSIGNED' AND NEW.round_id IS NOT NULL AND EXISTS(
        SELECT 1 FROM og_compat.case_events
        WHERE case_id = c.case_id AND round_id = NEW.round_id AND event_type = 'ESCALATED'
    ) THEN
        IF NEW.actor_id IS NULL OR NOT og_has_role(NEW.actor_id, 'ADMIN')
            OR NEW.recipient_id IS NULL OR NOT og_has_role(NEW.recipient_id, 'ADMIN')
            OR NULLIF(btrim(NEW.reason), '') IS NULL THEN
            RAISE EXCEPTION 'Escalated case handover requires ADMIN actor, ADMIN recipient, and non-blank reason' USING ERRCODE = '23514';
        END IF;
    END IF;
    IF NEW.event_type = 'COMPENSATION' AND c.state = 'REJECTED' THEN
        RAISE EXCEPTION 'Cannot compensate rejected case' USING ERRCODE = '23514';
    END IF;
    IF NEW.event_type = 'DEADLINE_EXTENSION' THEN
        IF c.state IN ('CLOSED', 'REJECTED') THEN
            RAISE EXCEPTION 'Cannot extend deadline on closed or rejected case' USING ERRCODE = '23514';
        END IF;
        IF NEW.metadata ? 'systemCaseId' AND NULLIF(NEW.metadata->>'systemCaseId', '') IS NOT NULL THEN
            SELECT * INTO sys_case FROM og_compat.cases WHERE case_id = (NEW.metadata->>'systemCaseId')::bigint;
            IF NOT FOUND OR sys_case.case_type <> 'SYSTEM_ISSUE' OR sys_case.state <> 'CLOSED' THEN
                RAISE EXCEPTION 'Deadline extension requires confirmed CLOSED system issue case' USING ERRCODE = '23514';
            END IF;
        END IF;
    END IF;
    IF NEW.event_type = 'VALID_DAMAGE_REQUEST' THEN
        SELECT * INTO STRICT o FROM og_compat.orders WHERE order_id = c.order_id;
        IF NEW.actor_id <> o.seller_id OR c.valid_returned_at IS NULL OR NEW.occurred_at > public.og28_response_deadline(c.case_id)
            OR NOT EXISTS(SELECT 1 FROM og_compat.case_rounds WHERE round_id = NEW.round_id
                AND stage = 'POST_RETURN_RESOLUTION' AND state = 'OPEN')
            OR NOT EXISTS(SELECT 1 FROM og_compat.case_evidence WHERE round_id = NEW.round_id AND submitted_by = NEW.actor_id) THEN
            RAISE EXCEPTION 'Only complete in-time Seller request blocks auto refund' USING ERRCODE = '23514';
        END IF;
        UPDATE og_compat.case_rounds
        SET valid_damage_request_at = NEW.occurred_at,
            complete_submission_at = COALESCE(complete_submission_at, NEW.occurred_at),
            ktv_due_at = COALESCE(complete_submission_at, NEW.occurred_at) + INTERVAL '48 hours'
        WHERE round_id = NEW.round_id;
    END IF;
    IF NEW.event_type = 'BUYER_WITHDRAWN' THEN
        IF NEW.actor_id IS NULL OR c.created_by IS NULL OR c.order_id IS NULL OR c.state IS NULL THEN
            RAISE EXCEPTION 'BUYER_WITHDRAWN requires valid non-null buyer identity and case references' USING ERRCODE = '23514';
        END IF;
        SELECT * INTO STRICT o FROM og_compat.orders WHERE order_id = c.order_id;
        IF o.buyer_id IS NULL THEN
            RAISE EXCEPTION 'BUYER_WITHDRAWN requires valid non-null order buyer' USING ERRCODE = '23514';
        END IF;
        IF NEW.actor_id <> c.created_by OR NEW.actor_id <> o.buyer_id THEN
            RAISE EXCEPTION 'Only the buyer who created the case can withdraw' USING ERRCODE = '23514';
        END IF;
        IF c.state IN ('RETURNING', 'POST_RETURN', 'CLOSED', 'REJECTED') THEN
            RAISE EXCEPTION 'Cannot withdraw case in state %', c.state USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN to_jsonb(NEW);
END;
$function$;

CREATE OR REPLACE FUNCTION public.og_apply_case_decision()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'public', '$user', 'public'
AS $function$
DECLARE
    r og_compat.case_rounds;
    c og_compat.cases;
    o og_compat.orders;
    v_designated_admin_id bigint;
BEGIN
    NEW.decided_at := clock_timestamp();
    IF NEW.supersedes_decision_id IS NOT NULL OR (NEW.outcome <> 'SELLER_DECLINED' AND NOT NEW.is_final) THEN
        RAISE EXCEPTION 'Final decisions retained; reopening needs a separate authorized round' USING ERRCODE = '23514';
    END IF;
    SELECT * INTO STRICT r FROM og_compat.case_rounds WHERE round_id = NEW.round_id FOR UPDATE;
    SELECT * INTO STRICT c FROM og_compat.cases WHERE case_id = r.case_id FOR UPDATE;
    IF r.state <> 'OPEN' AND NEW.supersedes_decision_id IS NULL THEN
        RAISE EXCEPTION 'Case round already decided' USING ERRCODE = '23514';
    END IF;
    IF c.order_id IS NOT NULL THEN SELECT * INTO STRICT o FROM og_compat.orders WHERE order_id = c.order_id FOR UPDATE; END IF;
    IF r.stage = 'RETURN_APPROVAL' THEN
        IF NEW.actor_type <> 'USER' OR NEW.outcome NOT IN ('APPROVE_RETURN', 'REJECT_RETURN', 'SELLER_DECLINED')
            OR NOT (og_has_role(NEW.reviewed_by, 'KTV') OR
                (NEW.reviewed_by = o.seller_id AND NEW.outcome IN ('APPROVE_RETURN', 'SELLER_DECLINED'))) THEN
            RAISE EXCEPTION 'Return approval authority/outcome mismatch' USING ERRCODE = '23514';
        END IF;
        IF NEW.outcome = 'APPROVE_RETURN' THEN
            UPDATE og_compat.cases SET state = 'RETURN_APPROVED', approved_at = NEW.decided_at WHERE case_id = c.case_id;
        ELSIF NEW.outcome = 'REJECT_RETURN' THEN
            UPDATE og_compat.cases SET state = 'REJECTED', closed_at = NEW.decided_at WHERE case_id = c.case_id;
        END IF;
    ELSIF r.stage = 'POST_RETURN_RESOLUTION' THEN
        IF c.valid_returned_at IS NULL OR NEW.outcome NOT IN ('FULL_GOODS_REFUND', 'EXEMPT_GOODS_REFUND') THEN
            RAISE EXCEPTION 'Post-return decision needs valid returned delivery' USING ERRCODE = '23514';
        END IF;
        IF EXISTS(SELECT 1 FROM og_compat.case_events WHERE case_id = c.case_id AND round_id = r.round_id AND event_type = 'ESCALATED') THEN
            IF NEW.actor_type <> 'USER' OR NOT og_has_role(NEW.reviewed_by, 'ADMIN') THEN
                RAISE EXCEPTION 'Escalated case requires Admin decision' USING ERRCODE = '23514';
            END IF;
            SELECT COALESCE(
                (SELECT e.recipient_id FROM og_compat.case_events e
                 WHERE e.case_id = c.case_id AND e.round_id = r.round_id
                   AND e.event_type = 'ASSIGNED' AND e.actor_id IS NOT NULL AND og_has_role(e.actor_id, 'ADMIN')
                 ORDER BY e.occurred_at DESC, e.event_id DESC LIMIT 1),
                (SELECT e.recipient_id FROM og_compat.case_events e
                 WHERE e.case_id = c.case_id AND e.round_id = r.round_id
                   AND e.event_type = 'ESCALATED'
                 ORDER BY e.occurred_at DESC LIMIT 1)
            ) INTO v_designated_admin_id;
            IF v_designated_admin_id IS NOT NULL AND v_designated_admin_id <> NEW.reviewed_by THEN
                RAISE EXCEPTION 'Escalated case requires designated Admin decision' USING ERRCODE = '23514';
            END IF;
            IF NEW.outcome = 'EXEMPT_GOODS_REFUND' AND r.valid_damage_request_at IS NULL THEN
                RAISE EXCEPTION 'Only Admin may grant exemption after valid request' USING ERRCODE = '23514';
            END IF;
        ELSE
            IF NEW.outcome = 'EXEMPT_GOODS_REFUND' AND (NEW.actor_type <> 'USER' OR NOT og_has_role(NEW.reviewed_by, 'KTV')
                OR r.valid_damage_request_at IS NULL) THEN
                RAISE EXCEPTION 'Only KTV may grant exemption after valid request' USING ERRCODE = '23514';
            END IF;
            IF NEW.actor_type = 'SYSTEM_TIMEOUT' AND (NEW.outcome <> 'FULL_GOODS_REFUND'
                OR NEW.decided_at < c.seller_response_due_at OR r.valid_damage_request_at IS NOT NULL) THEN
                RAISE EXCEPTION 'Auto refund blocked by deadline/request' USING ERRCODE = '23514';
            END IF;
            IF NEW.actor_type = 'USER' AND NOT (og_has_role(NEW.reviewed_by, 'KTV')
                OR (NEW.reviewed_by = o.seller_id AND NEW.outcome = 'FULL_GOODS_REFUND')) THEN
                RAISE EXCEPTION 'Post-return decision authority mismatch' USING ERRCODE = '23514';
            END IF;
        END IF;
        NEW.goods_refund_amount := CASE WHEN NEW.outcome = 'FULL_GOODS_REFUND'
            THEN o.subtotal - o.voucher_discount_amount - o.points_discount_amount ELSE 0 END;
        UPDATE og_compat.cases SET state = 'CLOSED', closed_at = NEW.decided_at WHERE case_id = c.case_id;
    ELSE
        IF EXISTS(SELECT 1 FROM og_compat.case_events WHERE case_id = c.case_id AND round_id = r.round_id AND event_type = 'ESCALATED') THEN
            IF NEW.outcome <> 'SYSTEM_RESOLVED' OR NEW.actor_type <> 'USER' OR NOT og_has_role(NEW.reviewed_by, 'ADMIN') THEN
                RAISE EXCEPTION 'Escalated system issue requires Admin decision' USING ERRCODE = '23514';
            END IF;
            SELECT COALESCE(
                (SELECT e.recipient_id FROM og_compat.case_events e
                 WHERE e.case_id = c.case_id AND e.round_id = r.round_id
                   AND e.event_type = 'ASSIGNED' AND e.actor_id IS NOT NULL AND og_has_role(e.actor_id, 'ADMIN')
                 ORDER BY e.occurred_at DESC, e.event_id DESC LIMIT 1),
                (SELECT e.recipient_id FROM og_compat.case_events e
                 WHERE e.case_id = c.case_id AND e.round_id = r.round_id
                   AND e.event_type = 'ESCALATED'
                 ORDER BY e.occurred_at DESC LIMIT 1)
            ) INTO v_designated_admin_id;
            IF v_designated_admin_id IS NOT NULL AND v_designated_admin_id <> NEW.reviewed_by THEN
                RAISE EXCEPTION 'Escalated system issue requires designated Admin decision' USING ERRCODE = '23514';
            END IF;
        ELSE
            IF NEW.outcome <> 'SYSTEM_RESOLVED' OR NEW.actor_type <> 'USER' OR
                NOT (og_has_role(NEW.reviewed_by, 'KTV') OR og_has_role(NEW.reviewed_by, 'ADMIN')) THEN
                RAISE EXCEPTION 'System issue authority mismatch' USING ERRCODE = '23514';
            END IF;
        END IF;
        IF c.state = 'REJECTED' THEN
            UPDATE og_compat.cases SET state = 'REJECTED', closed_at = NEW.decided_at WHERE case_id = c.case_id;
        ELSE
            UPDATE og_compat.cases SET state = 'CLOSED', closed_at = NEW.decided_at WHERE case_id = c.case_id;
        END IF;
    END IF;
    IF NEW.is_final THEN UPDATE og_compat.case_rounds SET state = 'DECIDED' WHERE round_id = r.round_id; END IF;
    RETURN NEW;
END;
$function$;

CREATE OR REPLACE FUNCTION public.og70_row_case_decisions_og_apply_case_decision(p_new jsonb, p_old jsonb, p_op text, p_phase text, p_depth integer, p_args text[])
 RETURNS jsonb
 LANGUAGE plpgsql
 SET search_path TO 'public', 'og_compat'
AS $function$
DECLARE
    NEW og_compat.case_decisions;
    OLD og_compat.case_decisions;
    r og_compat.case_rounds;
    c og_compat.cases;
    o og_compat.orders;
    v_designated_admin_id bigint;
BEGIN
    NEW := jsonb_populate_record(NULL::og_compat.case_decisions, coalesce(p_new, '{}'::jsonb));
    OLD := jsonb_populate_record(NULL::og_compat.case_decisions, coalesce(p_old, '{}'::jsonb));
    NEW.decided_at := clock_timestamp();
    IF NEW.supersedes_decision_id IS NOT NULL OR (NEW.outcome <> 'SELLER_DECLINED' AND NOT NEW.is_final) THEN
        RAISE EXCEPTION 'Final decisions retained; reopening needs a separate authorized round' USING ERRCODE = '23514';
    END IF;
    SELECT * INTO STRICT r FROM og_compat.case_rounds WHERE round_id = NEW.round_id FOR UPDATE;
    SELECT * INTO STRICT c FROM og_compat.cases WHERE case_id = r.case_id FOR UPDATE;
    IF r.state <> 'OPEN' AND NEW.supersedes_decision_id IS NULL THEN
        RAISE EXCEPTION 'Case round already decided' USING ERRCODE = '23514';
    END IF;
    IF c.order_id IS NOT NULL THEN SELECT * INTO STRICT o FROM og_compat.orders WHERE order_id = c.order_id FOR UPDATE; END IF;
    IF r.stage = 'RETURN_APPROVAL' THEN
        IF NEW.actor_type <> 'USER' OR NEW.outcome NOT IN ('APPROVE_RETURN', 'REJECT_RETURN', 'SELLER_DECLINED')
            OR NOT (og_has_role(NEW.reviewed_by, 'KTV') OR
                (NEW.reviewed_by = o.seller_id AND NEW.outcome IN ('APPROVE_RETURN', 'SELLER_DECLINED'))) THEN
            RAISE EXCEPTION 'Return approval authority/outcome mismatch' USING ERRCODE = '23514';
        END IF;
        IF NEW.outcome = 'APPROVE_RETURN' THEN
            UPDATE og_compat.cases SET state = 'RETURN_APPROVED', approved_at = NEW.decided_at WHERE case_id = c.case_id;
        ELSIF NEW.outcome = 'REJECT_RETURN' THEN
            UPDATE og_compat.cases SET state = 'REJECTED', closed_at = NEW.decided_at WHERE case_id = c.case_id;
        END IF;
    ELSIF r.stage = 'POST_RETURN_RESOLUTION' THEN
        IF c.valid_returned_at IS NULL OR NEW.outcome NOT IN ('FULL_GOODS_REFUND', 'EXEMPT_GOODS_REFUND') THEN
            RAISE EXCEPTION 'Post-return decision needs valid returned delivery' USING ERRCODE = '23514';
        END IF;
        IF EXISTS(SELECT 1 FROM og_compat.case_events WHERE case_id = c.case_id AND round_id = r.round_id AND event_type = 'ESCALATED') THEN
            IF NEW.actor_type <> 'USER' OR NOT og_has_role(NEW.reviewed_by, 'ADMIN') THEN
                RAISE EXCEPTION 'Escalated case requires Admin decision' USING ERRCODE = '23514';
            END IF;
            SELECT COALESCE(
                (SELECT e.recipient_id FROM og_compat.case_events e
                 WHERE e.case_id = c.case_id AND e.round_id = r.round_id
                   AND e.event_type = 'ASSIGNED' AND e.actor_id IS NOT NULL AND og_has_role(e.actor_id, 'ADMIN')
                 ORDER BY e.occurred_at DESC, e.event_id DESC LIMIT 1),
                (SELECT e.recipient_id FROM og_compat.case_events e
                 WHERE e.case_id = c.case_id AND e.round_id = r.round_id
                   AND e.event_type = 'ESCALATED'
                 ORDER BY e.occurred_at DESC LIMIT 1)
            ) INTO v_designated_admin_id;
            IF v_designated_admin_id IS NOT NULL AND v_designated_admin_id <> NEW.reviewed_by THEN
                RAISE EXCEPTION 'Escalated case requires designated Admin decision' USING ERRCODE = '23514';
            END IF;
            IF NEW.outcome = 'EXEMPT_GOODS_REFUND' AND r.valid_damage_request_at IS NULL THEN
                RAISE EXCEPTION 'Only Admin may grant exemption after valid request' USING ERRCODE = '23514';
            END IF;
        ELSE
            IF NEW.outcome = 'EXEMPT_GOODS_REFUND' AND (NEW.actor_type <> 'USER' OR NOT og_has_role(NEW.reviewed_by, 'KTV')
                OR r.valid_damage_request_at IS NULL) THEN
                RAISE EXCEPTION 'Only KTV may grant exemption after valid request' USING ERRCODE = '23514';
            END IF;
            IF NEW.actor_type = 'SYSTEM_TIMEOUT' AND (NEW.outcome <> 'FULL_GOODS_REFUND'
                OR NEW.decided_at < c.seller_response_due_at OR r.valid_damage_request_at IS NOT NULL) THEN
                RAISE EXCEPTION 'Auto refund blocked by deadline/request' USING ERRCODE = '23514';
            END IF;
            IF NEW.actor_type = 'USER' AND NOT (og_has_role(NEW.reviewed_by, 'KTV')
                OR (NEW.reviewed_by = o.seller_id AND NEW.outcome = 'FULL_GOODS_REFUND')) THEN
                RAISE EXCEPTION 'Post-return decision authority mismatch' USING ERRCODE = '23514';
            END IF;
        END IF;
        NEW.goods_refund_amount := CASE WHEN NEW.outcome = 'FULL_GOODS_REFUND'
            THEN o.subtotal - o.voucher_discount_amount - o.points_discount_amount ELSE 0 END;
        UPDATE og_compat.cases SET state = 'CLOSED', closed_at = NEW.decided_at WHERE case_id = c.case_id;
    ELSE
        IF EXISTS(SELECT 1 FROM og_compat.case_events WHERE case_id = c.case_id AND round_id = r.round_id AND event_type = 'ESCALATED') THEN
            IF NEW.outcome <> 'SYSTEM_RESOLVED' OR NEW.actor_type <> 'USER' OR NOT og_has_role(NEW.reviewed_by, 'ADMIN') THEN
                RAISE EXCEPTION 'Escalated system issue requires Admin decision' USING ERRCODE = '23514';
            END IF;
            SELECT COALESCE(
                (SELECT e.recipient_id FROM og_compat.case_events e
                 WHERE e.case_id = c.case_id AND e.round_id = r.round_id
                   AND e.event_type = 'ASSIGNED' AND e.actor_id IS NOT NULL AND og_has_role(e.actor_id, 'ADMIN')
                 ORDER BY e.occurred_at DESC, e.event_id DESC LIMIT 1),
                (SELECT e.recipient_id FROM og_compat.case_events e
                 WHERE e.case_id = c.case_id AND e.round_id = r.round_id
                   AND e.event_type = 'ESCALATED'
                 ORDER BY e.occurred_at DESC LIMIT 1)
            ) INTO v_designated_admin_id;
            IF v_designated_admin_id IS NOT NULL AND v_designated_admin_id <> NEW.reviewed_by THEN
                RAISE EXCEPTION 'Escalated system issue requires designated Admin decision' USING ERRCODE = '23514';
            END IF;
        ELSE
            IF NEW.outcome <> 'SYSTEM_RESOLVED' OR NEW.actor_type <> 'USER' OR
                NOT (og_has_role(NEW.reviewed_by, 'KTV') OR og_has_role(NEW.reviewed_by, 'ADMIN')) THEN
                RAISE EXCEPTION 'System issue authority mismatch' USING ERRCODE = '23514';
            END IF;
        END IF;
        IF c.state = 'REJECTED' THEN
            UPDATE og_compat.cases SET state = 'REJECTED', closed_at = NEW.decided_at WHERE case_id = c.case_id;
        ELSE
            UPDATE og_compat.cases SET state = 'CLOSED', closed_at = NEW.decided_at WHERE case_id = c.case_id;
        END IF;
    END IF;
    IF NEW.is_final THEN UPDATE og_compat.case_rounds SET state = 'DECIDED' WHERE round_id = r.round_id; END IF;
    RETURN to_jsonb(NEW);
END;
$function$;

-- 4. Anti-duplicate index on COMPENSATION events per (case_id, recipient_id, compensationType, targetRef)
CREATE UNIQUE INDEX IF NOT EXISTS uq_case_events_compensation_dedup
ON public.case_actions (
    case_id,
    COALESCE(recipient_id, 0),
    COALESCE(metadata->>'compensationType', 'DEFAULT'),
    COALESCE(metadata->>'targetRef', '')
)
WHERE record_type = 'case_events'
  AND event_type = 'COMPENSATION';

-- 5. Anti-duplicate unique index on UC48 notification_reminders strictly scoped to UC48:% keys
CREATE UNIQUE INDEX IF NOT EXISTS uq_uc48_notification_reminders_user_source_key
ON public.notifications (user_id, source_key)
WHERE record_type = 'notification_reminders'
  AND source_key IS NOT NULL
  AND source_key LIKE 'UC48:%';

-- 6. Allow ADMIN (in addition to KTV) to issue COMPENSATION voucher grants (UC65/UC76/UC70 PO-02 A)
CREATE OR REPLACE FUNCTION public.og70_row_voucher_grants_og_guard_voucher_grant(p_new jsonb, p_old jsonb, p_op text, p_phase text, p_depth integer, p_args text[])
 RETURNS jsonb
 LANGUAGE plpgsql
 SET search_path TO 'public', 'og_compat'
AS $function$
DECLARE
 NEW og_compat.voucher_grants; OLD og_compat.voucher_grants;
 TG_OP text:=p_op; TG_WHEN text:=p_phase; TG_TABLE_NAME text:='voucher_grants';
 TG_TABLE_SCHEMA text:='og_compat'; TG_ARGV text[]:=p_args; TG_NARGS integer:=cardinality(p_args);
  r og_compat.voucher_revisions;
BEGIN
 NEW:=jsonb_populate_record(NULL::og_compat.voucher_grants,coalesce(p_new,'{}'::jsonb));
 OLD:=jsonb_populate_record(NULL::og_compat.voucher_grants,coalesce(p_old,'{}'::jsonb));
    SELECT * INTO STRICT r FROM og_compat.voucher_revisions WHERE revision_id=NEW.revision_id FOR UPDATE;
    IF NEW.state<>'AVAILABLE' OR NEW.expires_at>r.valid_until OR NEW.expires_at<=NEW.granted_at
        OR EXISTS(SELECT 1 FROM og_compat.voucher_revision_revocations WHERE revision_id=r.revision_id)
        OR NEW.granted_at<r.valid_from OR NEW.granted_at>=r.valid_until
        OR (NEW.source='SELF_CLAIM' AND NEW.issued_by<>NEW.user_id)
        OR (NEW.source='COMPENSATION' AND (NOT (og_has_role(NEW.issued_by,'KTV') OR og_has_role(NEW.issued_by,'ADMIN')) OR NULLIF(btrim(NEW.reason),'') IS NULL))
        OR (NEW.source NOT IN ('SELF_CLAIM','COMPENSATION') AND (NOT og_has_role(NEW.issued_by,'KTV') OR NULLIF(btrim(NEW.reason),'') IS NULL))
        OR (SELECT count(*) FROM og_compat.voucher_grants WHERE revision_id=r.revision_id AND user_id=NEW.user_id)>=r.per_user_claim_limit
        OR (r.claim_limit IS NOT NULL AND (SELECT count(*) FROM og_compat.voucher_grants WHERE revision_id=r.revision_id)>=r.claim_limit) THEN
        RAISE EXCEPTION 'Voucher grant not eligible or claim limit exceeded' USING ERRCODE='23514';
    END IF;
    RETURN to_jsonb(NEW);
END;
$function$;

-- 7. Repair og70_write_view() for:
--    (a) V54 accidental ghn_province_id/ghn_district_id/ghn_ward_code injection into reviews, case_evidence, and ekyc_private_assets
--    (b) V60 seller_profiles.allow_public_avatar column missing from og70_write_view()
DO $migration$
DECLARE
    def text;
    idx_after_addr int;
    head_part text;
    tail_part text;
    updated_def text;
BEGIN
    def := pg_get_functiondef('public.og70_write_view()'::regprocedure);
    idx_after_addr := strpos(def, $k$WHEN 'refresh_sessions' THEN$k$);
    IF idx_after_addr <= 0 THEN
        RETURN;
    END IF;

    head_part := substr(def, 1, idx_after_addr - 1);
    tail_part := substr(def, idx_after_addr);

    -- Strip erroneous ghn_* keys from branches after addresses (ekyc_private_assets, case_evidence, reviews)
    tail_part := replace(tail_part,
        $s$,''ghn_province_id'',to_jsonb(t)->''ghn_province_id'',''ghn_district_id'',to_jsonb(t)->''ghn_district_id'',''ghn_ward_code'',to_jsonb(t)->''ghn_ward_code''$s$,
        '');
    tail_part := replace(tail_part,
        $s$,'ghn_province_id',stored->'ghn_province_id','ghn_district_id',stored->'ghn_district_id','ghn_ward_code',stored->'ghn_ward_code'$s$,
        '');

    -- Include allow_public_avatar in seller_profiles branch idempotently
    IF strpos(tail_part, 'allow_public_avatar') = 0 THEN
        tail_part := replace(tail_part,
            $s$INSERT INTO public.seller_profiles (seller_profile_id,user_id,revision_no,state,ekyc_profile_id,quick_auth_result_id,pickup_address_id,bank_snapshot,updated_at,record_type) SELECT r.seller_profile_id,r.user_id,r.revision_no,r.state,r.ekyc_profile_id,r.quick_auth_result_id,r.pickup_address_id,r.bank_snapshot,r.updated_at,''seller_profiles'' FROM jsonb_populate_record(NULL::og_compat.seller_profiles,$1)$s$,
            $s$INSERT INTO public.seller_profiles (seller_profile_id,user_id,revision_no,state,ekyc_profile_id,quick_auth_result_id,pickup_address_id,bank_snapshot,updated_at,allow_public_avatar,record_type) SELECT r.seller_profile_id,r.user_id,r.revision_no,r.state,r.ekyc_profile_id,r.quick_auth_result_id,r.pickup_address_id,r.bank_snapshot,r.updated_at,COALESCE(r.allow_public_avatar,false),''seller_profiles'' FROM jsonb_populate_record(NULL::og_compat.seller_profiles,$1)$s$);
        tail_part := replace(tail_part,
            $s$UPDATE public.seller_profiles t SET seller_profile_id=r.seller_profile_id,user_id=r.user_id,revision_no=r.revision_no,state=r.state,ekyc_profile_id=r.ekyc_profile_id,quick_auth_result_id=r.quick_auth_result_id,pickup_address_id=r.pickup_address_id,bank_snapshot=r.bank_snapshot,updated_at=r.updated_at FROM jsonb_populate_record(NULL::og_compat.seller_profiles,$1)$s$,
            $s$UPDATE public.seller_profiles t SET seller_profile_id=r.seller_profile_id,user_id=r.user_id,revision_no=r.revision_no,state=r.state,ekyc_profile_id=r.ekyc_profile_id,quick_auth_result_id=r.quick_auth_result_id,pickup_address_id=r.pickup_address_id,bank_snapshot=r.bank_snapshot,updated_at=r.updated_at,allow_public_avatar=COALESCE(r.allow_public_avatar,false) FROM jsonb_populate_record(NULL::og_compat.seller_profiles,$1)$s$);
        tail_part := replace(tail_part,
            $s$''pickup_address_id'',to_jsonb(t)->''pickup_address_id'',''bank_snapshot'',to_jsonb(t)->''bank_snapshot'',''updated_at'',to_jsonb(t)->''updated_at''$s$,
            $s$''pickup_address_id'',to_jsonb(t)->''pickup_address_id'',''bank_snapshot'',to_jsonb(t)->''bank_snapshot'',''updated_at'',to_jsonb(t)->''updated_at'',''allow_public_avatar'',to_jsonb(t)->''allow_public_avatar''$s$);
        tail_part := replace(tail_part,
            $s$'pickup_address_id',stored->'pickup_address_id','bank_snapshot',stored->'bank_snapshot','updated_at',stored->'updated_at'$s$,
            $s$'pickup_address_id',stored->'pickup_address_id','bank_snapshot',stored->'bank_snapshot','updated_at',stored->'updated_at','allow_public_avatar',stored->'allow_public_avatar'$s$);
    END IF;

    updated_def := head_part || tail_part;
    IF updated_def IS DISTINCT FROM def THEN
        EXECUTE updated_def;
    END IF;
END;
$migration$;

