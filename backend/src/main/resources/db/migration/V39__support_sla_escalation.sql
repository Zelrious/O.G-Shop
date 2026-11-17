-- V39: Support SLA Escalation (BD-1, BD-3, BD-4)
-- 1. BD-3: Allow SYSTEM actor (actor_id IS NULL) to insert 'ESCALATED' event in og_guard_case_event
--    when 48h SLA from complete_submission_at has elapsed, round is OPEN POST_RETURN_RESOLUTION,
--    and recipient has role ADMIN. Retain KTV -> Admin escalation.
-- 2. BD-1: In og_apply_case_decision, once a round is escalated, require actor with role ADMIN
--    to make the final decision (reject KTV and reject SYSTEM_TIMEOUT).

CREATE OR REPLACE FUNCTION public.og_guard_case_event()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'public', '$user', 'public'
AS $function$
DECLARE c og_compat.cases; o og_compat.orders;
BEGIN
    SELECT * INTO STRICT c FROM og_compat.cases WHERE case_id=NEW.case_id FOR UPDATE;
    NEW.occurred_at:=clock_timestamp();
    IF NEW.event_type='ESCALATED' THEN
        IF NOT og_has_role(NEW.recipient_id,'ADMIN') THEN
            RAISE EXCEPTION 'Escalation needs Admin recipient' USING ERRCODE='23514';
        END IF;
        IF NEW.round_id IS NOT NULL AND EXISTS(
            SELECT 1 FROM og_compat.case_events
            WHERE case_id=c.case_id AND round_id=NEW.round_id AND event_type='ESCALATED'
        ) THEN
            RAISE EXCEPTION 'Round already escalated' USING ERRCODE='23514';
        END IF;
        IF NEW.actor_id IS NOT NULL THEN
            IF NOT og_has_role(NEW.actor_id,'KTV') THEN
                RAISE EXCEPTION 'Escalation needs KTV or SYSTEM actor' USING ERRCODE='23514';
            END IF;
        ELSE
            -- System actor escalation validations (BD-3 & BD-4)
            IF NEW.round_id IS NULL THEN
                RAISE EXCEPTION 'System escalation requires round_id' USING ERRCODE='23514';
            END IF;
            IF c.state IN ('CLOSED','REJECTED') THEN
                RAISE EXCEPTION 'Cannot escalate closed or rejected case' USING ERRCODE='23514';
            END IF;
            IF NOT EXISTS(SELECT 1 FROM og_compat.case_rounds WHERE round_id=NEW.round_id
                AND stage='POST_RETURN_RESOLUTION' AND state='OPEN') THEN
                RAISE EXCEPTION 'System escalation only for open POST_RETURN_RESOLUTION round' USING ERRCODE='23514';
            END IF;
            IF NOT EXISTS(SELECT 1 FROM og_compat.case_rounds WHERE round_id=NEW.round_id
                AND complete_submission_at IS NOT NULL
                AND NEW.occurred_at > complete_submission_at + INTERVAL '48 hours') THEN
                RAISE EXCEPTION 'System escalation only after 48h SLA from complete submission' USING ERRCODE='23514';
            END IF;
            IF EXISTS(SELECT 1 FROM og_compat.case_decisions WHERE round_id=NEW.round_id AND is_final) THEN
                RAISE EXCEPTION 'Cannot escalate round with final decision' USING ERRCODE='23514';
            END IF;
        END IF;
    END IF;
    IF NEW.event_type='VALID_DAMAGE_REQUEST' THEN
        SELECT * INTO STRICT o FROM og_compat.orders WHERE order_id=c.order_id;
        IF NEW.actor_id<>o.seller_id OR c.valid_returned_at IS NULL OR NEW.occurred_at>c.seller_response_due_at
            OR NOT EXISTS(SELECT 1 FROM og_compat.case_rounds WHERE round_id=NEW.round_id
                AND stage='POST_RETURN_RESOLUTION' AND state='OPEN')
            OR NOT EXISTS(SELECT 1 FROM og_compat.case_evidence WHERE round_id=NEW.round_id AND submitted_by=NEW.actor_id) THEN
            RAISE EXCEPTION 'Only complete in-time Seller request blocks auto refund' USING ERRCODE='23514';
        END IF;
        UPDATE og_compat.case_rounds SET valid_damage_request_at=NEW.occurred_at, complete_submission_at=COALESCE(complete_submission_at, NEW.occurred_at), ktv_due_at=COALESCE(complete_submission_at, NEW.occurred_at) + INTERVAL '48 hours' WHERE round_id=NEW.round_id;
    END IF;
    IF NEW.event_type='BUYER_WITHDRAWN' THEN
        IF NEW.actor_id IS NULL OR c.created_by IS NULL OR c.order_id IS NULL OR c.state IS NULL THEN
            RAISE EXCEPTION 'BUYER_WITHDRAWN requires valid non-null buyer identity and case references' USING ERRCODE='23514';
        END IF;
        SELECT * INTO STRICT o FROM og_compat.orders WHERE order_id=c.order_id;
        IF o.buyer_id IS NULL THEN
            RAISE EXCEPTION 'BUYER_WITHDRAWN requires valid non-null order buyer' USING ERRCODE='23514';
        END IF;
        IF NEW.actor_id<>c.created_by OR NEW.actor_id<>o.buyer_id THEN
            RAISE EXCEPTION 'Only the buyer who created the case can withdraw' USING ERRCODE='23514';
        END IF;
        IF c.state IN ('RETURNING','POST_RETURN','CLOSED','REJECTED') THEN
            RAISE EXCEPTION 'Cannot withdraw case in state %', c.state USING ERRCODE='23514';
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
 NEW og_compat.case_events; OLD og_compat.case_events;
 TG_OP text:=p_op; TG_WHEN text:=p_phase; TG_TABLE_NAME text:='case_events';
 TG_TABLE_SCHEMA text:='og_compat'; TG_ARGV text[]:=p_args; TG_NARGS integer:=cardinality(p_args);
  c og_compat.cases; o og_compat.orders;
BEGIN
 NEW:=jsonb_populate_record(NULL::og_compat.case_events,coalesce(p_new,'{}'::jsonb));
 OLD:=jsonb_populate_record(NULL::og_compat.case_events,coalesce(p_old,'{}'::jsonb));
    SELECT * INTO STRICT c FROM og_compat.cases WHERE case_id=NEW.case_id FOR UPDATE;
    NEW.occurred_at:=clock_timestamp();
    PERFORM public.og28_check_case_response(NEW);
    IF NEW.event_type='ESCALATED' THEN
        IF NOT og_has_role(NEW.recipient_id,'ADMIN') THEN
            RAISE EXCEPTION 'Escalation needs Admin recipient' USING ERRCODE='23514';
        END IF;
        IF NEW.round_id IS NOT NULL AND EXISTS(
            SELECT 1 FROM og_compat.case_events
            WHERE case_id=c.case_id AND round_id=NEW.round_id AND event_type='ESCALATED'
        ) THEN
            RAISE EXCEPTION 'Round already escalated' USING ERRCODE='23514';
        END IF;
        IF NEW.actor_id IS NOT NULL THEN
            IF NOT og_has_role(NEW.actor_id,'KTV') THEN
                RAISE EXCEPTION 'Escalation needs KTV or SYSTEM actor' USING ERRCODE='23514';
            END IF;
        ELSE
            -- System actor escalation validations (BD-3 & BD-4)
            IF NEW.round_id IS NULL THEN
                RAISE EXCEPTION 'System escalation requires round_id' USING ERRCODE='23514';
            END IF;
            IF c.state IN ('CLOSED','REJECTED') THEN
                RAISE EXCEPTION 'Cannot escalate closed or rejected case' USING ERRCODE='23514';
            END IF;
            IF NOT EXISTS(SELECT 1 FROM og_compat.case_rounds WHERE round_id=NEW.round_id
                AND stage='POST_RETURN_RESOLUTION' AND state='OPEN') THEN
                RAISE EXCEPTION 'System escalation only for open POST_RETURN_RESOLUTION round' USING ERRCODE='23514';
            END IF;
            IF NOT EXISTS(SELECT 1 FROM og_compat.case_rounds WHERE round_id=NEW.round_id
                AND complete_submission_at IS NOT NULL
                AND NEW.occurred_at > complete_submission_at + INTERVAL '48 hours') THEN
                RAISE EXCEPTION 'System escalation only after 48h SLA from complete submission' USING ERRCODE='23514';
            END IF;
            IF EXISTS(SELECT 1 FROM og_compat.case_decisions WHERE round_id=NEW.round_id AND is_final) THEN
                RAISE EXCEPTION 'Cannot escalate round with final decision' USING ERRCODE='23514';
            END IF;
        END IF;
    END IF;
    IF NEW.event_type='VALID_DAMAGE_REQUEST' THEN
        SELECT * INTO STRICT o FROM og_compat.orders WHERE order_id=c.order_id;
        IF NEW.actor_id<>o.seller_id OR c.valid_returned_at IS NULL OR NEW.occurred_at>public.og28_response_deadline(c.case_id)
            OR NOT EXISTS(SELECT 1 FROM og_compat.case_rounds WHERE round_id=NEW.round_id
                AND stage='POST_RETURN_RESOLUTION' AND state='OPEN')
            OR NOT EXISTS(SELECT 1 FROM og_compat.case_evidence WHERE round_id=NEW.round_id AND submitted_by=NEW.actor_id) THEN
            RAISE EXCEPTION 'Only complete in-time Seller request blocks auto refund' USING ERRCODE='23514';
        END IF;
        UPDATE og_compat.case_rounds SET valid_damage_request_at=NEW.occurred_at, complete_submission_at=COALESCE(complete_submission_at, NEW.occurred_at), ktv_due_at=COALESCE(complete_submission_at, NEW.occurred_at) + INTERVAL '48 hours' WHERE round_id=NEW.round_id;
    END IF;
    IF NEW.event_type='BUYER_WITHDRAWN' THEN
        IF NEW.actor_id IS NULL OR c.created_by IS NULL OR c.order_id IS NULL OR c.state IS NULL THEN
            RAISE EXCEPTION 'BUYER_WITHDRAWN requires valid non-null buyer identity and case references' USING ERRCODE='23514';
        END IF;
        SELECT * INTO STRICT o FROM og_compat.orders WHERE order_id=c.order_id;
        IF o.buyer_id IS NULL THEN
            RAISE EXCEPTION 'BUYER_WITHDRAWN requires valid non-null order buyer' USING ERRCODE='23514';
        END IF;
        IF NEW.actor_id<>c.created_by OR NEW.actor_id<>o.buyer_id THEN
            RAISE EXCEPTION 'Only the buyer who created the case can withdraw' USING ERRCODE='23514';
        END IF;
        IF c.state IN ('RETURNING','POST_RETURN','CLOSED','REJECTED') THEN
            RAISE EXCEPTION 'Cannot withdraw case in state %', c.state USING ERRCODE='23514';
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
DECLARE r og_compat.case_rounds; c og_compat.cases; o og_compat.orders;
BEGIN
    NEW.decided_at:=clock_timestamp();
    IF NEW.supersedes_decision_id IS NOT NULL OR (NEW.outcome<>'SELLER_DECLINED' AND NOT NEW.is_final) THEN
        RAISE EXCEPTION 'Final decisions retained; reopening needs a separate authorized round' USING ERRCODE='23514';
    END IF;
    SELECT * INTO STRICT r FROM og_compat.case_rounds WHERE round_id=NEW.round_id FOR UPDATE;
    SELECT * INTO STRICT c FROM og_compat.cases WHERE case_id=r.case_id FOR UPDATE;
    IF r.state<>'OPEN' AND NEW.supersedes_decision_id IS NULL THEN
        RAISE EXCEPTION 'Case round already decided' USING ERRCODE='23514';
    END IF;
    IF c.order_id IS NOT NULL THEN SELECT * INTO STRICT o FROM og_compat.orders WHERE order_id=c.order_id FOR UPDATE; END IF;
    IF r.stage='RETURN_APPROVAL' THEN
        IF NEW.actor_type<>'USER' OR NEW.outcome NOT IN ('APPROVE_RETURN','REJECT_RETURN','SELLER_DECLINED')
            OR NOT (og_has_role(NEW.reviewed_by,'KTV') OR
                (NEW.reviewed_by=o.seller_id AND NEW.outcome IN ('APPROVE_RETURN','SELLER_DECLINED'))) THEN
            RAISE EXCEPTION 'Return approval authority/outcome mismatch' USING ERRCODE='23514';
        END IF;
        IF NEW.outcome='APPROVE_RETURN' THEN
            UPDATE og_compat.cases SET state='RETURN_APPROVED',approved_at=NEW.decided_at WHERE case_id=c.case_id;
        ELSIF NEW.outcome='REJECT_RETURN' THEN
            UPDATE og_compat.cases SET state='REJECTED',closed_at=NEW.decided_at WHERE case_id=c.case_id;
        END IF;
    ELSIF r.stage='POST_RETURN_RESOLUTION' THEN
        IF c.valid_returned_at IS NULL OR NEW.outcome NOT IN ('FULL_GOODS_REFUND','EXEMPT_GOODS_REFUND') THEN
            RAISE EXCEPTION 'Post-return decision needs valid returned delivery' USING ERRCODE='23514';
        END IF;
        IF EXISTS(SELECT 1 FROM og_compat.case_events WHERE case_id=c.case_id AND round_id=r.round_id AND event_type='ESCALATED') THEN
            -- BD-1: Case is escalated -> Only ADMIN can make final decision
            IF NEW.actor_type<>'USER' OR NOT og_has_role(NEW.reviewed_by,'ADMIN') THEN
                RAISE EXCEPTION 'Escalated case requires Admin decision' USING ERRCODE='23514';
            END IF;
            IF NEW.outcome='EXEMPT_GOODS_REFUND' AND r.valid_damage_request_at IS NULL THEN
                RAISE EXCEPTION 'Only Admin may grant exemption after valid request' USING ERRCODE='23514';
            END IF;
        ELSE
            -- Pre-escalation normal authority
            IF NEW.outcome='EXEMPT_GOODS_REFUND' AND (NEW.actor_type<>'USER' OR NOT og_has_role(NEW.reviewed_by,'KTV')
                OR r.valid_damage_request_at IS NULL) THEN
                RAISE EXCEPTION 'Only KTV may grant exemption after valid request' USING ERRCODE='23514';
            END IF;
            IF NEW.actor_type='SYSTEM_TIMEOUT' AND (NEW.outcome<>'FULL_GOODS_REFUND'
                OR NEW.decided_at<c.seller_response_due_at OR r.valid_damage_request_at IS NOT NULL) THEN
                RAISE EXCEPTION 'Auto refund blocked by deadline/request' USING ERRCODE='23514';
            END IF;
            IF NEW.actor_type='USER' AND NOT (og_has_role(NEW.reviewed_by,'KTV')
                OR (NEW.reviewed_by=o.seller_id AND NEW.outcome='FULL_GOODS_REFUND')) THEN
                RAISE EXCEPTION 'Post-return decision authority mismatch' USING ERRCODE='23514';
            END IF;
        END IF;
        NEW.goods_refund_amount:=CASE WHEN NEW.outcome='FULL_GOODS_REFUND'
            THEN o.subtotal-o.voucher_discount_amount-o.points_discount_amount ELSE 0 END;
        UPDATE og_compat.cases SET state='CLOSED',closed_at=NEW.decided_at WHERE case_id=c.case_id;
    ELSE
        IF NEW.outcome<>'SYSTEM_RESOLVED' OR NEW.actor_type<>'USER' OR
            NOT (og_has_role(NEW.reviewed_by,'KTV') OR og_has_role(NEW.reviewed_by,'ADMIN')) THEN
            RAISE EXCEPTION 'System issue authority mismatch' USING ERRCODE='23514';
        END IF;
        UPDATE og_compat.cases SET state='CLOSED',closed_at=NEW.decided_at WHERE case_id=c.case_id;
    END IF;
    IF NEW.is_final THEN UPDATE og_compat.case_rounds SET state='DECIDED' WHERE round_id=r.round_id; END IF;
    RETURN NEW;
END;
$function$;

CREATE OR REPLACE FUNCTION public.og70_row_case_decisions_og_apply_case_decision(p_new jsonb, p_old jsonb, p_op text, p_phase text, p_depth integer, p_args text[])
 RETURNS jsonb
 LANGUAGE plpgsql
 SET search_path TO 'public', 'og_compat'
AS $function$
DECLARE
 NEW og_compat.case_decisions; OLD og_compat.case_decisions;
 TG_OP text:=p_op; TG_WHEN text:=p_phase; TG_TABLE_NAME text:='case_decisions';
 TG_TABLE_SCHEMA text:='og_compat'; TG_ARGV text[]:=p_args; TG_NARGS integer:=cardinality(p_args);
  r og_compat.case_rounds; c og_compat.cases; o og_compat.orders;
BEGIN
 NEW:=jsonb_populate_record(NULL::og_compat.case_decisions,coalesce(p_new,'{}'::jsonb));
 OLD:=jsonb_populate_record(NULL::og_compat.case_decisions,coalesce(p_old,'{}'::jsonb));
    NEW.decided_at:=clock_timestamp();
    IF NEW.supersedes_decision_id IS NOT NULL OR (NEW.outcome<>'SELLER_DECLINED' AND NOT NEW.is_final) THEN
        RAISE EXCEPTION 'Final decisions retained; reopening needs a separate authorized round' USING ERRCODE='23514';
    END IF;
    SELECT * INTO STRICT r FROM og_compat.case_rounds WHERE round_id=NEW.round_id FOR UPDATE;
    SELECT * INTO STRICT c FROM og_compat.cases WHERE case_id=r.case_id FOR UPDATE;
    IF r.state<>'OPEN' AND NEW.supersedes_decision_id IS NULL THEN
        RAISE EXCEPTION 'Case round already decided' USING ERRCODE='23514';
    END IF;
    IF c.order_id IS NOT NULL THEN SELECT * INTO STRICT o FROM og_compat.orders WHERE order_id=c.order_id FOR UPDATE; END IF;
    IF r.stage='RETURN_APPROVAL' THEN
        IF NEW.actor_type<>'USER' OR NEW.outcome NOT IN ('APPROVE_RETURN','REJECT_RETURN','SELLER_DECLINED')
            OR NOT (og_has_role(NEW.reviewed_by,'KTV') OR
                (NEW.reviewed_by=o.seller_id AND NEW.outcome IN ('APPROVE_RETURN','SELLER_DECLINED'))) THEN
            RAISE EXCEPTION 'Return approval authority/outcome mismatch' USING ERRCODE='23514';
        END IF;
        IF NEW.outcome='APPROVE_RETURN' THEN
            UPDATE og_compat.cases SET state='RETURN_APPROVED',approved_at=NEW.decided_at WHERE case_id=c.case_id;
        ELSIF NEW.outcome='REJECT_RETURN' THEN
            UPDATE og_compat.cases SET state='REJECTED',closed_at=NEW.decided_at WHERE case_id=c.case_id;
        END IF;
    ELSIF r.stage='POST_RETURN_RESOLUTION' THEN
        IF c.valid_returned_at IS NULL OR NEW.outcome NOT IN ('FULL_GOODS_REFUND','EXEMPT_GOODS_REFUND') THEN
            RAISE EXCEPTION 'Post-return decision needs valid returned delivery' USING ERRCODE='23514';
        END IF;
        IF EXISTS(SELECT 1 FROM og_compat.case_events WHERE case_id=c.case_id AND round_id=r.round_id AND event_type='ESCALATED') THEN
            -- BD-1: Case is escalated -> Only ADMIN can make final decision
            IF NEW.actor_type<>'USER' OR NOT og_has_role(NEW.reviewed_by,'ADMIN') THEN
                RAISE EXCEPTION 'Escalated case requires Admin decision' USING ERRCODE='23514';
            END IF;
            IF NEW.outcome='EXEMPT_GOODS_REFUND' AND r.valid_damage_request_at IS NULL THEN
                RAISE EXCEPTION 'Only Admin may grant exemption after valid request' USING ERRCODE='23514';
            END IF;
        ELSE
            -- Pre-escalation normal authority
            IF NEW.outcome='EXEMPT_GOODS_REFUND' AND (NEW.actor_type<>'USER' OR NOT og_has_role(NEW.reviewed_by,'KTV')
                OR r.valid_damage_request_at IS NULL) THEN
                RAISE EXCEPTION 'Only KTV may grant exemption after valid request' USING ERRCODE='23514';
            END IF;
            IF NEW.actor_type='SYSTEM_TIMEOUT' AND (NEW.outcome<>'FULL_GOODS_REFUND'
                OR NEW.decided_at<c.seller_response_due_at OR r.valid_damage_request_at IS NOT NULL) THEN
                RAISE EXCEPTION 'Auto refund blocked by deadline/request' USING ERRCODE='23514';
            END IF;
            IF NEW.actor_type='USER' AND NOT (og_has_role(NEW.reviewed_by,'KTV')
                OR (NEW.reviewed_by=o.seller_id AND NEW.outcome='FULL_GOODS_REFUND')) THEN
                RAISE EXCEPTION 'Post-return decision authority mismatch' USING ERRCODE='23514';
            END IF;
        END IF;
        NEW.goods_refund_amount:=CASE WHEN NEW.outcome='FULL_GOODS_REFUND'
            THEN o.subtotal-o.voucher_discount_amount-o.points_discount_amount ELSE 0 END;
        UPDATE og_compat.cases SET state='CLOSED',closed_at=NEW.decided_at WHERE case_id=c.case_id;
    ELSE
        IF NEW.outcome<>'SYSTEM_RESOLVED' OR NEW.actor_type<>'USER' OR
            NOT (og_has_role(NEW.reviewed_by,'KTV') OR og_has_role(NEW.reviewed_by,'ADMIN')) THEN
            RAISE EXCEPTION 'System issue authority mismatch' USING ERRCODE='23514';
        END IF;
        UPDATE og_compat.cases SET state='CLOSED',closed_at=NEW.decided_at WHERE case_id=c.case_id;
    END IF;
    IF NEW.is_final THEN UPDATE og_compat.case_rounds SET state='DECIDED' WHERE round_id=r.round_id; END IF;
    RETURN to_jsonb(NEW);
END;
$function$;

-- 3. Storage-level protection against duplicate ESCALATED events (Task 3.2.5 & 3.2.6 hardening)
-- Enforces at most one ESCALATED event per (case_id, round_id) on physical table public.case_actions.
CREATE UNIQUE INDEX IF NOT EXISTS uq_case_actions_round_escalated
ON public.case_actions (case_id, round_id)
WHERE record_type = 'case_events'
  AND event_type = 'ESCALATED'
  AND round_id IS NOT NULL;


