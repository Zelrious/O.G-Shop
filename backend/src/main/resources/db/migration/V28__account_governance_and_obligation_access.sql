-- Customer governance blocks new activity, while authentication remains available
-- for existing obligations. Existing auth status/restrictions are not relaxed.
SET LOCAL lock_timeout='10s';
SET LOCAL statement_timeout='120s';
SELECT pg_advisory_xact_lock(700048);
ALTER TABLE public.users
    ADD COLUMN governance_version bigint NOT NULL DEFAULT 0 CHECK(governance_version>=0),
    ADD COLUMN governance_state varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK(governance_state IN ('ACTIVE','LOCKED','DEACTIVATED')),
    ADD COLUMN governance_until timestamptz,
    ADD COLUMN governance_reason text,
    ADD COLUMN governance_restrictions jsonb NOT NULL DEFAULT '[]' CHECK(jsonb_typeof(governance_restrictions)='array');
-- The consolidated audit aggregate already owns command_key for sibling types.
ALTER TABLE public.audit_logs ADD COLUMN command_request jsonb;
CREATE UNIQUE INDEX uq_governance_command ON public.audit_logs(command_key)
    WHERE record_type='audit_logs' AND entity_type='ACCOUNT_GOVERNANCE' AND command_key IS NOT NULL;
CREATE INDEX ix_governance_history ON public.audit_logs(entity_id,created_at DESC,log_id DESC)
    WHERE entity_type='ACCOUNT_GOVERNANCE';

CREATE FUNCTION public.og28_effective_state(p_user bigint) RETURNS text LANGUAGE sql STABLE AS $$
  SELECT CASE WHEN governance_until IS NOT NULL AND governance_until<=CURRENT_TIMESTAMP
    THEN 'ACTIVE' ELSE governance_state END FROM public.users WHERE user_id=p_user AND record_type='users'
$$;
CREATE FUNCTION public.og28_restricted(p_user bigint,p_scope text) RETURNS boolean LANGUAGE sql STABLE AS $$
  SELECT EXISTS(SELECT 1 FROM public.users u WHERE user_id=p_user AND record_type='users' AND (
    u.status<>'ACTIVE' OR u.deleted_at IS NOT NULL OR public.og28_effective_state(p_user)<>'ACTIVE'
    OR EXISTS(SELECT 1 FROM jsonb_array_elements(u.governance_restrictions) r
      WHERE r->>'scope' IN (p_scope,'NEW_TRANSACTIONS') AND r->>'closedAt' IS NULL
        AND (r->>'expiresAt')::timestamptz>CURRENT_TIMESTAMP)
    OR EXISTS(SELECT 1 FROM og_compat.account_restrictions r WHERE r.user_id=u.user_id
      AND r.closed_at IS NULL AND r.scope IN ('LOGIN','NEW_TRANSACTIONS'))))
$$;

CREATE FUNCTION public.og28_guard_governance() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r jsonb; proof record;
BEGIN
  IF TG_OP='INSERT' THEN
    IF NEW.governance_version<>0 OR NEW.governance_state<>'ACTIVE' OR NEW.governance_until IS NOT NULL
      OR NEW.governance_reason IS NOT NULL OR NEW.governance_restrictions<>'[]'::jsonb THEN
      RAISE EXCEPTION 'New users cannot bypass account decision history' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
  END IF;
  IF ROW(NEW.governance_version,NEW.governance_state,NEW.governance_until,NEW.governance_reason,NEW.governance_restrictions)
    IS DISTINCT FROM ROW(OLD.governance_version,OLD.governance_state,OLD.governance_until,OLD.governance_reason,OLD.governance_restrictions) THEN
    IF NEW.governance_version<>OLD.governance_version+1 OR public.og_has_role(NEW.user_id,'ADMIN')
      OR public.og_has_role(NEW.user_id,'KTV') OR NOT (public.og_has_role(NEW.user_id,'BUYER') OR public.og_has_role(NEW.user_id,'SELLER')) THEN
      RAISE EXCEPTION 'Governance needs one version and a customer target' USING ERRCODE='23514';
    END IF;
    SELECT a.* INTO proof FROM public.audit_logs a WHERE a.record_type='audit_logs' AND a.entity_type='ACCOUNT_GOVERNANCE'
      AND a.entity_id=NEW.user_id AND a.command_key IS NOT NULL
      AND (a.new_values->>'version')::bigint=NEW.governance_version
      AND (a.old_values->>'version')::bigint=OLD.governance_version LIMIT 1;
    IF NOT FOUND OR NOT public.og_has_role(proof.user_id,'KTV') OR NOT EXISTS(SELECT 1 FROM public.users WHERE user_id=proof.user_id AND status='ACTIVE' AND deleted_at IS NULL)
      OR proof.new_values->>'state' IS DISTINCT FROM NEW.governance_state
      OR (proof.new_values->>'until')::timestamptz IS DISTINCT FROM NEW.governance_until
      OR proof.new_values->>'reason' IS DISTINCT FROM NEW.governance_reason
      OR proof.new_values->'restrictions' IS DISTINCT FROM NEW.governance_restrictions THEN
      RAISE EXCEPTION 'Governance requires matching KTV audit proof' USING ERRCODE='23514';
    END IF;
    IF NEW.governance_state='ACTIVE' AND (NEW.governance_until IS NOT NULL OR NEW.governance_reason IS NOT NULL)
      OR NEW.governance_state<>'ACTIVE' AND coalesce(btrim(NEW.governance_reason),'')='' THEN
      RAISE EXCEPTION 'Invalid account state reason/deadline' USING ERRCODE='23514';
    END IF;
    FOR r IN SELECT value FROM jsonb_array_elements(NEW.governance_restrictions) LOOP
      IF r->>'id' IS NULL OR (r->>'id')::uuid IS NULL
        OR r->>'scope' IS NULL OR r->>'scope' NOT IN ('NEW_LISTINGS','CHAT','BUY_EKYC','NEW_TRANSACTIONS')
        OR coalesce(btrim(r->>'reason'),'')='' OR (r->>'imposedBy')::bigint IS NULL
        OR (r->>'openedAt')::timestamptz IS NULL OR r->>'expiresAt' IS NULL OR (r->>'expiresAt')::timestamptz<=(r->>'openedAt')::timestamptz
        OR (r->>'closedAt')::timestamptz<(r->>'openedAt')::timestamptz THEN
        RAISE EXCEPTION 'Invalid scoped restriction' USING ERRCODE='23514';
      END IF;
    END LOOP;
    NEW.credential_version:=OLD.credential_version+1;
  END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER og28_governance_guard BEFORE INSERT OR UPDATE ON public.users
  FOR EACH ROW EXECUTE FUNCTION public.og28_guard_governance();

CREATE FUNCTION public.og28_guard_new_activity() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent og_compat.orders; ekyc_required boolean;
BEGIN
  IF TG_TABLE_NAME='products' AND NEW.record_type='products' THEN
    IF (TG_OP='INSERT' OR NEW.content_revision IS DISTINCT FROM OLD.content_revision
      OR (NEW.status='ACTIVE' AND OLD.status IN ('DRAFT','PENDING','HIDDEN','REJECTED','APPROVED_AWAITING_FEE')))
      AND public.og28_restricted(NEW.seller_id,'NEW_LISTINGS') THEN
      RAISE EXCEPTION 'Account cannot create or publish listings' USING ERRCODE='23514';
    END IF;
  ELSIF TG_TABLE_NAME='orders' AND TG_OP='INSERT' AND NEW.record_type IN ('orders','checkout_groups') THEN
    -- Serialize creation against decisions for both parties in a stable order.
    PERFORM 1 FROM public.users WHERE user_id IN (NEW.buyer_id,NEW.seller_id) ORDER BY user_id FOR UPDATE;
    IF public.og28_restricted(NEW.buyer_id,'NEW_TRANSACTIONS') OR public.og28_restricted(NEW.seller_id,'NEW_TRANSACTIONS') THEN
      RAISE EXCEPTION 'Account cannot create a purchase' USING ERRCODE='23514';
    END IF;
  ELSIF TG_TABLE_NAME='order_items' AND TG_OP='INSERT' AND NEW.record_type='order_items' THEN
    SELECT * INTO STRICT parent FROM og_compat.orders WHERE order_id=NEW.order_id;
    SELECT requires_buyer_ekyc INTO ekyc_required FROM og_compat.products WHERE product_id=NEW.product_id;
    IF ekyc_required AND public.og28_restricted(parent.buyer_id,'BUY_EKYC') THEN
      RAISE EXCEPTION 'Account cannot purchase eKYC listings' USING ERRCODE='23514';
    END IF;
  END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER og28_product_access BEFORE INSERT OR UPDATE ON public.products FOR EACH ROW EXECUTE FUNCTION public.og28_guard_new_activity();
CREATE TRIGGER og28_order_access BEFORE INSERT ON public.orders FOR EACH ROW EXECUTE FUNCTION public.og28_guard_new_activity();
CREATE TRIGGER og28_order_item_access BEFORE INSERT ON public.order_items FOR EACH ROW EXECUTE FUNCTION public.og28_guard_new_activity();
COMMENT ON COLUMN public.users.governance_state IS 'Customer new-activity state; authentication remains available for history and old obligations. Not hard deletion.';

CREATE FUNCTION public.og28_response_deadline(p_case bigint) RETURNS timestamptz LANGUAGE sql STABLE AS $$
  SELECT c.seller_response_due_at+coalesce((SELECT sum((e.metadata->>'affectedUntil')::timestamptz-(e.metadata->>'affectedFrom')::timestamptz)
    FROM og_compat.case_events e WHERE e.case_id=c.case_id AND e.event_type='DEADLINE_EXTENSION'
    AND e.metadata->>'source'='ACCOUNT_UC67'),interval '0 seconds') FROM og_compat.cases c WHERE c.case_id=p_case
$$;
CREATE FUNCTION public.og28_check_case_response(e og_compat.case_events) RETURNS void LANGUAGE plpgsql AS $$
DECLARE c og_compat.cases; o og_compat.orders; affected_from timestamptz; affected_until timestamptz; source_case bigint; party bigint;
BEGIN
  IF e.metadata->>'partyResponse'='true' OR (e.event_type='DEADLINE_EXTENSION' AND e.metadata->>'source'='ACCOUNT_UC67') THEN
    SELECT * INTO STRICT c FROM og_compat.cases WHERE case_id=e.case_id;
    IF c.order_id IS NOT NULL THEN SELECT * INTO o FROM og_compat.orders WHERE order_id=c.order_id; END IF;
    IF c.state IN ('CLOSED','REJECTED') THEN RAISE EXCEPTION 'Closed case cannot receive account response/extension' USING ERRCODE='23514'; END IF;
    IF e.metadata->>'partyResponse'='true' THEN
      IF e.event_type<>'SUPPLEMENTED' OR e.actor_id IS NULL OR NOT
        (e.actor_id=c.created_by OR e.actor_id=coalesce(o.buyer_id,-1) OR e.actor_id=coalesce(o.seller_id,-1))
        OR (e.actor_id=o.seller_id AND c.seller_response_due_at IS NOT NULL AND CURRENT_TIMESTAMP>public.og28_response_deadline(c.case_id)) THEN
        RAISE EXCEPTION 'Account response needs an authorized party within its deadline' USING ERRCODE='23514';
      END IF;
    ELSE
      source_case:=(e.metadata->>'systemCaseId')::bigint;party:=(e.metadata->>'userId')::bigint;
      affected_from:=(e.metadata->>'affectedFrom')::timestamptz;affected_until:=(e.metadata->>'affectedUntil')::timestamptz;
      IF NOT public.og_has_role(e.actor_id,'KTV') OR c.seller_response_due_at IS NULL OR party IS DISTINCT FROM o.seller_id
        OR affected_from IS NULL OR affected_until IS NULL OR affected_until<=affected_from OR affected_until>CURRENT_TIMESTAMP
        OR NOT EXISTS(SELECT 1 FROM og_compat.cases sc LEFT JOIN og_compat.orders so USING(order_id)
          WHERE sc.case_id=source_case AND sc.case_type='SYSTEM_ISSUE' AND sc.state='CLOSED'
            AND (sc.created_by=party OR so.buyer_id=party OR so.seller_id=party)
            AND EXISTS(SELECT 1 FROM og_compat.case_rounds cr JOIN og_compat.case_decisions d USING(round_id)
              WHERE cr.case_id=sc.case_id AND d.outcome='SYSTEM_RESOLVED' AND d.is_final))
        OR NOT EXISTS(SELECT 1 FROM og_compat.case_events se WHERE se.case_id=source_case AND se.event_type='COMPENSATION'
          AND (public.og_has_role(se.actor_id,'KTV') OR public.og_has_role(se.actor_id,'ADMIN'))
          AND (se.metadata->>'affectedFrom')::timestamptz=affected_from AND (se.metadata->>'affectedUntil')::timestamptz=affected_until)
        OR EXISTS(SELECT 1 FROM og_compat.case_events previous WHERE previous.case_id=e.case_id AND previous.event_id<>e.event_id
          AND previous.event_type='DEADLINE_EXTENSION' AND previous.metadata->>'source'='ACCOUNT_UC67'
          AND previous.metadata->>'systemCaseId'=source_case::text) THEN
        RAISE EXCEPTION 'Deadline compensation needs confirmed exact outage and cannot repeat' USING ERRCODE='23514';
      END IF;
    END IF;
  END IF;
END; $$;
DO $migration$ DECLARE definition text; function_name text; anchor text; BEGIN
  SELECT p.oid::regprocedure::text INTO STRICT function_name FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
    WHERE n.nspname='public' AND p.proname LIKE 'og70_row_case_events_og_guard_case_event%';
  definition:=pg_get_functiondef(function_name::regprocedure);
  anchor:='IF NEW.event_type=''ESCALATED''';
  IF strpos(definition,anchor)=0 THEN RAISE EXCEPTION 'Expected case event adapter'; END IF;
  definition:=replace(definition,anchor,'PERFORM public.og28_check_case_response(NEW); '||anchor);
  definition:=replace(definition,'NEW.occurred_at>c.seller_response_due_at','NEW.occurred_at>public.og28_response_deadline(c.case_id)');
  EXECUTE definition;
END; $migration$;
