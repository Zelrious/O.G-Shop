SET LOCAL lock_timeout='10s';
SET LOCAL statement_timeout='120s';
SELECT pg_advisory_xact_lock(700048);
DO $migration$ DECLARE definition text; anchor text; BEGIN
  definition:=pg_get_functiondef('public.og28_guard_governance()'::regprocedure);
  anchor:='''NEW_LISTINGS'',''CHAT'',''BUY_EKYC'',''NEW_TRANSACTIONS''';
  IF strpos(definition,anchor)=0 THEN RAISE EXCEPTION 'Expected account scope validation'; END IF;
  EXECUTE replace(definition,anchor,anchor||',''BUY_ALL''');
  definition:=pg_get_functiondef('public.og28_guard_new_activity()'::regprocedure);
  anchor:='public.og28_restricted(NEW.buyer_id,''NEW_TRANSACTIONS'')';
  IF strpos(definition,anchor)=0 THEN RAISE EXCEPTION 'Expected buyer creation guard'; END IF;
  EXECUTE replace(definition,anchor,'public.og28_restricted(NEW.buyer_id,''BUY_ALL'')');
END; $migration$;
-- NEW_TRANSACTIONS remains the legacy/global new-activity scope. BUY_ALL only
-- blocks purchases by this buyer, keeping their listing/chat/selling rights.
