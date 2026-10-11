-- F1-F5: extend existing aggregates; preserve fixed-duration sessions and V25 drafts.
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '120s';
SELECT pg_advisory_xact_lock(700048);

ALTER TABLE public.users ADD COLUMN credential_version bigint NOT NULL DEFAULT 0
    CHECK (credential_version>=0), ADD COLUMN google_subject varchar(255);
UPDATE public.users u SET google_subject=e.provider_subject
FROM og_compat.external_identities e WHERE e.user_id=u.user_id AND e.provider='GOOGLE' AND u.record_type='users';
CREATE UNIQUE INDEX uq_users_google_subject ON public.users(google_subject)
    WHERE record_type='users' AND google_subject IS NOT NULL;
CREATE OR REPLACE VIEW og_compat.users AS SELECT
    p.user_id::bigint AS user_id,p.email::varchar(320) AS email,p.password_hash::varchar(255) AS password_hash,
    p.full_name::varchar(120) AS full_name,p.phone_number::varchar(20) AS phone_number,p.avatar_url::text AS avatar_url,
    p.status::varchar(20) AS status,p.created_at::timestamptz AS created_at,p.updated_at::timestamptz AS updated_at,
    p.deleted_at::timestamptz AS deleted_at,p.bank_name::varchar(100) AS bank_name,
    p.bank_account_number::varchar(50) AS bank_account_number,p.bank_account_holder::varchar(120) AS bank_account_holder,
    p.email_verified_at::timestamptz AS email_verified_at,p.identity_workflow::varchar(20) AS identity_workflow,
    p.credential_version::bigint AS credential_version
FROM public.users p WHERE p.record_type='users';
-- V22 compares the projected OLD row to guard lost writes. Include the new
-- read-only column in that comparison while retaining all other view branches.
DO $migration$ DECLARE definition text; anchor text; BEGIN
    definition:=pg_get_functiondef('public.og70_write_view()'::regprocedure);
    anchor:=$anchor$''identity_workflow'',to_jsonb(t)->''identity_workflow''$anchor$;
    IF strpos(definition,anchor)=0 THEN RAISE EXCEPTION 'Expected users view writer projection'; END IF;
    EXECUTE replace(definition,anchor,anchor||$extra$,''credential_version'',to_jsonb(t)->''credential_version''$extra$);
END; $migration$;
CREATE FUNCTION public.og26_revoke_password_sessions() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.record_type='users' AND OLD.password_hash IS DISTINCT FROM NEW.password_hash THEN
        NEW.credential_version:=OLD.credential_version+1;
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER og26_password_version BEFORE UPDATE ON public.users
    FOR EACH ROW EXECUTE FUNCTION public.og26_revoke_password_sessions();

ALTER TABLE public.auth_challenges ADD COLUMN auth_data jsonb,
    ADD CONSTRAINT ck_auth_flow_data CHECK(auth_data IS NULL OR
        (record_type='auth_challenges' AND jsonb_typeof(auth_data)='object'));
-- Keep all V25 validation. Google handoffs have no user until registration; unknown
-- reset emails have indistinguishable decoy challenges, never eligible to change a password.
DO $migration$ DECLARE definition text; BEGIN
    definition:=pg_get_functiondef('public.og70_validate_row(text,jsonb,boolean)'::regprocedure);
    EXECUTE replace(definition,'FUNCTION public.og70_validate_row(', 'FUNCTION public.og26_validate_existing_row(');
END; $migration$;
CREATE OR REPLACE FUNCTION public.og70_validate_row(s text,j jsonb,skip_not_valid boolean DEFAULT false)
RETURNS void LANGUAGE plpgsql AS $$ BEGIN
    IF s='auth_challenges' AND (j->>'purpose' IN ('GOOGLE_NONCE','GOOGLE_REGISTER','GOOGLE_LINK','PASSWORD_RESET_COMPLETE')
        OR (j->>'purpose'='PASSWORD_RESET' AND j->>'user_id' IS NULL)) THEN
        PERFORM public.og26_validate_existing_row(s,jsonb_set(j,'{purpose}','"REGISTER_EMAIL"'::jsonb),skip_not_valid);
    ELSE
        PERFORM public.og26_validate_existing_row(s,j,skip_not_valid);
    END IF;
END; $$;
COMMENT ON COLUMN public.users.credential_version IS 'Incremented atomically on every password hash change; JWT version must match.';
COMMENT ON COLUMN public.auth_challenges.auth_data IS 'Purpose-bound auth metadata, no OTP/password plaintext; cleared on consume/cancel.';
