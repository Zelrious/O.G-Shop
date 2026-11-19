-- V37: Support Buyer Withdrawal (UC62 7c) with identity validation and deferred state verification
-- 1. Extend event allowlist in og25_validate_existing_row to include 'BUYER_WITHDRAWN'
-- 2. Add NULL-safe identity guard to og_guard_case_event and og70_row_case_events_og_guard_case_event
-- 3. Update deferred case state validator (og_validate_case_state & og70_row_cases_og_validate_case_state)
--    to permit CLOSED when valid BUYER_WITHDRAWN or legacy CLEANUP withdraw evidence is present.

DO $migration$
DECLARE def text;
BEGIN
    def := pg_get_functiondef('public.og25_validate_existing_row(text,jsonb,boolean)'::regprocedure);
    IF strpos(def, $$(''CLEANUP''::character varying)::text$$) = 0 THEN
        RAISE EXCEPTION 'Target string CLEANUP not found in og25_validate_existing_row';
    END IF;
    def := replace(def,
        $$(''CLEANUP''::character varying)::text$$,
        $$(''CLEANUP''::character varying)::text, (''BUYER_WITHDRAWN''::character varying)::text$$);
    EXECUTE def;
END;
$migration$;

CREATE OR REPLACE FUNCTION public.og_guard_case_event()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'public', '$user', 'public'
AS $function$
DECLARE c og_compat.cases; o og_compat.orders;
BEGIN
    SELECT * INTO STRICT c FROM og_compat.cases WHERE case_id=NEW.case_id FOR UPDATE;
    NEW.occurred_at:=clock_timestamp();
    IF NEW.event_type='ESCALATED' AND (NOT og_has_role(NEW.actor_id,'KTV') OR
        NOT og_has_role(NEW.recipient_id,'ADMIN')) THEN
        RAISE EXCEPTION 'Escalation needs KTV to Admin' USING ERRCODE='23514';
    END IF;
    IF NEW.event_type='VALID_DAMAGE_REQUEST' THEN
        SELECT * INTO STRICT o FROM og_compat.orders WHERE order_id=c.order_id;
        IF NEW.actor_id<>o.seller_id OR c.valid_returned_at IS NULL OR NEW.occurred_at>c.seller_response_due_at
            OR NOT EXISTS(SELECT 1 FROM og_compat.case_rounds WHERE round_id=NEW.round_id
                AND stage='POST_RETURN_RESOLUTION' AND state='OPEN')
            OR NOT EXISTS(SELECT 1 FROM og_compat.case_evidence WHERE round_id=NEW.round_id AND submitted_by=NEW.actor_id) THEN
            RAISE EXCEPTION 'Only complete in-time Seller request blocks auto refund' USING ERRCODE='23514';
        END IF;
        UPDATE og_compat.case_rounds SET valid_damage_request_at=NEW.occurred_at WHERE round_id=NEW.round_id;
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
    IF NEW.event_type='ESCALATED' AND (NOT og_has_role(NEW.actor_id,'KTV') OR
        NOT og_has_role(NEW.recipient_id,'ADMIN')) THEN
        RAISE EXCEPTION 'Escalation needs KTV to Admin' USING ERRCODE='23514';
    END IF;
    IF NEW.event_type='VALID_DAMAGE_REQUEST' THEN
        SELECT * INTO STRICT o FROM og_compat.orders WHERE order_id=c.order_id;
        IF NEW.actor_id<>o.seller_id OR c.valid_returned_at IS NULL OR NEW.occurred_at>public.og28_response_deadline(c.case_id)
            OR NOT EXISTS(SELECT 1 FROM og_compat.case_rounds WHERE round_id=NEW.round_id
                AND stage='POST_RETURN_RESOLUTION' AND state='OPEN')
            OR NOT EXISTS(SELECT 1 FROM og_compat.case_evidence WHERE round_id=NEW.round_id AND submitted_by=NEW.actor_id) THEN
            RAISE EXCEPTION 'Only complete in-time Seller request blocks auto refund' USING ERRCODE='23514';
        END IF;
        UPDATE og_compat.case_rounds SET valid_damage_request_at=NEW.occurred_at WHERE round_id=NEW.round_id;
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

CREATE OR REPLACE FUNCTION public.og_validate_case_state()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'public', '$user', 'public'
AS $function$
DECLARE c og_compat.cases;
BEGIN
    SELECT * INTO STRICT c FROM og_compat.cases WHERE case_id=NEW.case_id;
    IF (c.approved_at IS NOT NULL AND NOT EXISTS(SELECT 1 FROM og_compat.case_decisions d JOIN og_compat.case_rounds r USING(round_id)
        WHERE r.case_id=c.case_id AND d.outcome='APPROVE_RETURN' AND d.is_final))
        OR (c.state = 'REJECTED' AND NOT EXISTS(SELECT 1 FROM og_compat.case_decisions d JOIN og_compat.case_rounds r USING(round_id)
            WHERE r.case_id=c.case_id AND d.is_final AND d.outcome IN
                ('REJECT_RETURN','FULL_GOODS_REFUND','EXEMPT_GOODS_REFUND','SYSTEM_RESOLVED')))
        OR (c.state = 'CLOSED' AND NOT (
            EXISTS(SELECT 1 FROM og_compat.case_decisions d JOIN og_compat.case_rounds r USING(round_id)
                WHERE r.case_id=c.case_id AND d.is_final AND d.outcome IN
                    ('REJECT_RETURN','FULL_GOODS_REFUND','EXEMPT_GOODS_REFUND','SYSTEM_RESOLVED'))
            OR EXISTS(SELECT 1 FROM og_compat.case_events e
                WHERE e.case_id=c.case_id AND e.actor_id=c.created_by AND (
                    e.event_type = 'BUYER_WITHDRAWN'
                    OR (e.event_type = 'CLEANUP' AND (e.metadata->>'action' = 'BUYER_WITHDRAW' OR e.metadata->>'action' = 'WITHDRAW' OR e.command_key = 'EVENT:WITHDRAW:' || c.case_id::text))
                ))
        ))
        OR (c.valid_returned_at IS NOT NULL AND NOT EXISTS(SELECT 1 FROM og_compat.shipments s
            WHERE s.return_case_id=c.case_id AND s.leg='RETURN' AND s.valid_delivery_at=c.valid_returned_at)) THEN
        RAISE EXCEPTION 'Case state requires corresponding decision/delivery proof' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END;
$function$;

CREATE OR REPLACE FUNCTION public.og70_row_cases_og_validate_case_state(p_new jsonb, p_old jsonb, p_op text, p_phase text, p_depth integer, p_args text[])
 RETURNS jsonb
 LANGUAGE plpgsql
 SET search_path TO 'public', 'og_compat'
AS $function$
DECLARE
 NEW og_compat.cases; OLD og_compat.cases;
 TG_OP text:=p_op; TG_WHEN text:=p_phase; TG_TABLE_NAME text:='cases';
 TG_TABLE_SCHEMA text:='og_compat'; TG_ARGV text[]:=p_args; TG_NARGS integer:=cardinality(p_args);
  c og_compat.cases;
BEGIN
 NEW:=jsonb_populate_record(NULL::og_compat.cases,coalesce(p_new,'{}'::jsonb));
 OLD:=jsonb_populate_record(NULL::og_compat.cases,coalesce(p_old,'{}'::jsonb));
    SELECT * INTO STRICT c FROM og_compat.cases WHERE case_id=NEW.case_id;
    IF (c.approved_at IS NOT NULL AND NOT EXISTS(SELECT 1 FROM og_compat.case_decisions d JOIN og_compat.case_rounds r USING(round_id)
        WHERE r.case_id=c.case_id AND d.outcome='APPROVE_RETURN' AND d.is_final))
        OR (c.state = 'REJECTED' AND NOT EXISTS(SELECT 1 FROM og_compat.case_decisions d JOIN og_compat.case_rounds r USING(round_id)
            WHERE r.case_id=c.case_id AND d.is_final AND d.outcome IN
                ('REJECT_RETURN','FULL_GOODS_REFUND','EXEMPT_GOODS_REFUND','SYSTEM_RESOLVED')))
        OR (c.state = 'CLOSED' AND NOT (
            EXISTS(SELECT 1 FROM og_compat.case_decisions d JOIN og_compat.case_rounds r USING(round_id)
                WHERE r.case_id=c.case_id AND d.is_final AND d.outcome IN
                    ('REJECT_RETURN','FULL_GOODS_REFUND','EXEMPT_GOODS_REFUND','SYSTEM_RESOLVED'))
            OR EXISTS(SELECT 1 FROM og_compat.case_events e
                WHERE e.case_id=c.case_id AND e.actor_id=c.created_by AND (
                    e.event_type = 'BUYER_WITHDRAWN'
                    OR (e.event_type = 'CLEANUP' AND (e.metadata->>'action' = 'BUYER_WITHDRAW' OR e.metadata->>'action' = 'WITHDRAW' OR e.command_key = 'EVENT:WITHDRAW:' || c.case_id::text))
                ))
        ))
        OR (c.valid_returned_at IS NOT NULL AND NOT EXISTS(SELECT 1 FROM og_compat.shipments s
            WHERE s.return_case_id=c.case_id AND s.leg='RETURN' AND s.valid_delivery_at=c.valid_returned_at)) THEN
        RAISE EXCEPTION 'Case state requires corresponding decision/delivery proof' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END;
$function$;
