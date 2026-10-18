-- Preserve V28; also protect DRAFT/HIDDEN/REJECTED -> PENDING from SQL callers.
SET LOCAL lock_timeout='10s';
SET LOCAL statement_timeout='120s';
SELECT pg_advisory_xact_lock(700048);
ALTER FUNCTION public.og28_restricted(bigint,text) VOLATILE;
CREATE FUNCTION public.og29_guard_submission() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.record_type='products' AND NEW.status='PENDING' AND OLD.status IN ('DRAFT','HIDDEN','REJECTED') THEN
    PERFORM 1 FROM public.users WHERE user_id=NEW.seller_id FOR UPDATE;
    IF public.og28_restricted(NEW.seller_id,'NEW_LISTINGS') THEN
      RAISE EXCEPTION 'Account cannot submit a new listing review' USING ERRCODE='23514';
    END IF;
  END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER og29_submission_access BEFORE UPDATE ON public.products FOR EACH ROW EXECUTE FUNCTION public.og29_guard_submission();
