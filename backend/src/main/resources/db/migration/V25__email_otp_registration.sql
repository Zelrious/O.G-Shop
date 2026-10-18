-- UC01: preserve V24 data and fixed-duration sessions; no account before OTP.
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '120s';
SELECT pg_advisory_xact_lock(700048);

ALTER TABLE public.auth_challenges
    ADD COLUMN registration_data jsonb,
    ADD COLUMN registration_expires_at timestamptz,
    ADD COLUMN registration_requester_digest varchar(64),
    ADD CONSTRAINT ck_registration_data CHECK (registration_data IS NULL OR
        (record_type='auth_challenges' AND purpose='REGISTER_EMAIL' AND state='OPEN'
         AND registration_expires_at IS NOT NULL AND registration_expires_at>=expires_at
         AND jsonb_typeof(registration_data)='object')),
    ADD CONSTRAINT ck_registration_requester_digest CHECK
        (registration_requester_digest IS NULL OR registration_requester_digest ~ '^[0-9a-f]{64}$');
CREATE INDEX ix_registration_email_rate ON public.auth_challenges(subject_key,issued_at)
    WHERE purpose='REGISTER_EMAIL' AND record_type='auth_challenges';
CREATE INDEX ix_registration_requester_rate ON public.auth_challenges(registration_requester_digest,issued_at)
    WHERE registration_requester_digest IS NOT NULL;
CREATE UNIQUE INDEX uq_registration_open_challenge ON public.auth_challenges(subject_key,purpose)
    WHERE record_type='auth_challenges' AND state='OPEN';
CREATE UNIQUE INDEX uq_registration_user_email ON public.users(email) WHERE record_type='users';

CREATE FUNCTION public.og25_guard_registration_data() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='UPDATE' AND (
        (OLD.registration_data IS NOT NULL AND NEW.registration_data IS NOT NULL
            AND OLD.registration_data IS DISTINCT FROM NEW.registration_data)
        OR (OLD.registration_data IS NULL AND NEW.registration_data IS NOT NULL AND OLD.state<>'OPEN')
        OR (OLD.registration_requester_digest IS NOT NULL
            AND OLD.registration_requester_digest IS DISTINCT FROM NEW.registration_requester_digest)
        OR (OLD.registration_expires_at IS NOT NULL
            AND OLD.registration_expires_at IS DISTINCT FROM NEW.registration_expires_at)) THEN
        RAISE EXCEPTION 'Registration draft and requester are immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER og25_registration_data BEFORE UPDATE ON public.auth_challenges
    FOR EACH ROW EXECUTE FUNCTION public.og25_guard_registration_data();

ALTER TABLE public.seller_profiles
    ADD COLUMN store_name varchar(120),
    ADD COLUMN contact_phone varchar(20),
    ADD COLUMN return_address_id bigint,
    ADD CONSTRAINT fk_seller_return_address FOREIGN KEY(return_address_id,user_id)
        REFERENCES public.addresses(address_id,user_id) DEFERRABLE INITIALLY DEFERRED;

-- V22 retained its row rules in an adapter validator. Delegate every old type
-- unchanged and add only the system registration credit (no invented Admin/order).
DO $migration$
DECLARE definition text;
BEGIN
    definition := pg_get_functiondef('public.og70_validate_row(text,jsonb,boolean)'::regprocedure);
    IF strpos(definition,'FUNCTION public.og70_validate_row(')=0 THEN
        RAISE EXCEPTION 'Expected V24 row validator';
    END IF;
    EXECUTE replace(definition,'FUNCTION public.og70_validate_row(',
        'FUNCTION public.og25_validate_existing_row(');
END;
$migration$;

CREATE OR REPLACE FUNCTION public.og70_validate_row(s text,j jsonb,skip_not_valid boolean DEFAULT false)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE r og_compat.reward_ledger;
BEGIN
    IF s='reward_ledger' AND j->>'entry_type'='CREDIT_REGISTRATION' THEN
        r:=jsonb_populate_record(NULL::og_compat.reward_ledger,j);
        IF r.entry_id IS NULL OR r.user_id IS NULL OR r.created_at IS NULL
            OR r.points_delta IS DISTINCT FROM 3000 OR r.policy_id IS NOT NULL
            OR r.order_id IS NOT NULL OR r.group_id IS NOT NULL OR r.actor_id IS NOT NULL
            OR r.reason IS DISTINCT FROM 'UC22: verified email registration'
            OR r.command_key IS DISTINCT FROM 'REGISTER_EMAIL:'||r.user_id::text THEN
            RAISE EXCEPTION 'Invalid registration reward' USING ERRCODE='23514';
        END IF;
        RETURN;
    END IF;
    PERFORM public.og25_validate_existing_row(s,j,skip_not_valid);
END;
$$;
CREATE UNIQUE INDEX uq_registration_reward_user ON public.reward_ledger(user_id)
    WHERE entry_type='CREDIT_REGISTRATION';

CREATE OR REPLACE FUNCTION public.og70_row_reward_ledger_og_apply_reward_entry(
    p_new jsonb,p_old jsonb,p_op text,p_phase text,p_depth integer,p_args text[])
RETURNS jsonb LANGUAGE plpgsql SET search_path=public,og_compat AS $$
DECLARE n og_compat.reward_ledger; b bigint;
BEGIN
    n:=jsonb_populate_record(NULL::og_compat.reward_ledger,p_new);
    IF n.entry_type='CREDIT_REGISTRATION' AND NOT EXISTS(
        SELECT 1 FROM public.users u WHERE u.user_id=n.user_id AND u.record_type='users'
          AND u.identity_workflow='UC83' AND u.email_verified_at IS NOT NULL
          AND u.status='ACTIVE' AND u.deleted_at IS NULL) THEN
        RAISE EXCEPTION 'Registration reward requires verified account' USING ERRCODE='23514';
    END IF;
    SELECT balance INTO STRICT b FROM og_compat.reward_accounts WHERE user_id=n.user_id FOR UPDATE;
    IF b+n.points_delta<0 OR (n.entry_type='CREDIT_COMPLETION' AND NOT EXISTS(
        SELECT 1 FROM og_compat.orders WHERE order_id=n.order_id AND status='COMPLETED'
          AND n.user_id IN (buyer_id,seller_id)))
        OR (n.entry_type='CORRECTION' AND NOT og_has_role(n.actor_id,'ADMIN')) THEN
        RAISE EXCEPTION 'Reward source/balance not eligible' USING ERRCODE='23514';
    END IF;
    UPDATE og_compat.reward_accounts SET balance=balance+n.points_delta,version=version+1
        WHERE user_id=n.user_id;
    RETURN to_jsonb(n);
END;
$$;

COMMENT ON COLUMN public.auth_challenges.registration_data IS
    'Pending email registration, password hash only; cleared on success, cancellation or expiry.';
COMMENT ON COLUMN public.seller_profiles.store_name IS
    'Store name collected at registration; PENDING_EKYC does not grant SELLER.';
