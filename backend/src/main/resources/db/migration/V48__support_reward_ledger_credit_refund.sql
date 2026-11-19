-- ============================================================================
-- V48__support_reward_ledger_credit_refund.sql (renumbered from V44)
-- Module: Payment / Settlement / Trust & Safety
-- Business Context: Support CREDIT_REFUND in reward_ledger with structured
--                   expires_at per PO D-10 Option B / Task 4.1b approval.
-- Scoped strictly to Return Case coin refund without bundling Checkout points discounts.
-- ============================================================================

-- 1. Add dedicated structured expires_at column to public.reward_ledger
ALTER TABLE public.reward_ledger ADD COLUMN IF NOT EXISTS expires_at timestamp with time zone;

-- 2. Update og_compat.reward_ledger view to project expires_at
CREATE OR REPLACE VIEW og_compat.reward_ledger AS
SELECT
    p.entry_id::uuid AS entry_id,
    p.user_id::bigint AS user_id,
    p.policy_id::bigint AS policy_id,
    p.entry_type::character varying(20) AS entry_type,
    p.points_delta::bigint AS points_delta,
    p.order_id::bigint AS order_id,
    p.group_id::uuid AS group_id,
    p.actor_id::bigint AS actor_id,
    p.reason::text AS reason,
    p.created_at::timestamp with time zone AS created_at,
    p.command_key::character varying(150) AS command_key,
    p.expires_at::timestamp with time zone AS expires_at
FROM public.reward_ledger p
WHERE p.record_type = 'reward_ledger';

ALTER VIEW og_compat.reward_ledger ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;

-- Re-attach INSTEAD OF trigger on view
DROP TRIGGER IF EXISTS og70_write ON og_compat.reward_ledger;
CREATE TRIGGER og70_write INSTEAD OF INSERT OR UPDATE OR DELETE ON og_compat.reward_ledger FOR EACH ROW EXECUTE FUNCTION public.og70_write_view();

-- 3. Patch og70_write_view() to forward expires_at to public.reward_ledger
DO $migration$
DECLARE
    def text;
    prev text;
BEGIN
    def := pg_get_functiondef('public.og70_write_view()'::regprocedure);

    -- Idempotency check: if already patched with expires_at, skip function rewriting
    IF def LIKE '%record_type,expires_at) SELECT r.entry_id%r.expires_at FROM jsonb_populate_record(NULL::og_compat.reward_ledger%' THEN
        RETURN;
    END IF;

    -- Patch reward_ledger INSERT values
    prev := def;
    def := replace(def,
        $s$r.created_at,r.command_key,''reward_ledger'' FROM jsonb_populate_record(NULL::og_compat.reward_ledger,$1) r$s$,
        $s$r.created_at,r.command_key,''reward_ledger'',r.expires_at FROM jsonb_populate_record(NULL::og_compat.reward_ledger,$1) r$s$
    );
    IF def = prev THEN
        RAISE EXCEPTION 'Migration V48 failed: anchor "reward_ledger INSERT values" not found in og70_write_view()';
    END IF;

    -- Patch reward_ledger INSERT columns (table-anchored to avoid touching listing_fee_charges/payments/penalty_ledger/notifications)
    prev := def;
    def := replace(def,
        $s$public.reward_ledger (entry_id,user_id,policy_id,entry_type,points_delta,order_id,group_id,actor_id,reason,created_at,command_key,record_type) SELECT$s$,
        $s$public.reward_ledger (entry_id,user_id,policy_id,entry_type,points_delta,order_id,group_id,actor_id,reason,created_at,command_key,record_type,expires_at) SELECT$s$
    );
    IF def = prev THEN
        RAISE EXCEPTION 'Migration V48 failed: anchor "reward_ledger INSERT cols" not found in og70_write_view()';
    END IF;

    -- Patch reward_ledger UPDATE SET
    prev := def;
    def := replace(def,
        $s$command_key=r.command_key FROM jsonb_populate_record(NULL::og_compat.reward_ledger,$1) r$s$,
        $s$command_key=r.command_key,expires_at=r.expires_at FROM jsonb_populate_record(NULL::og_compat.reward_ledger,$1) r$s$
    );
    IF def = prev THEN
        RAISE EXCEPTION 'Migration V48 failed: anchor "reward_ledger UPDATE SET" not found in og70_write_view()';
    END IF;

    EXECUTE def;
END;
$migration$;

-- 4. Preserve existing validator chain and enforce CREDIT_REFUND validation
DO $migration$
DECLARE definition text;
BEGIN
    IF to_regprocedure('public.og48_validate_existing_row(text,jsonb,boolean)') IS NULL THEN
        definition := pg_get_functiondef('public.og70_validate_row(text,jsonb,boolean)'::regprocedure);
        EXECUTE replace(definition, 'FUNCTION public.og70_validate_row(', 'FUNCTION public.og48_validate_existing_row(');
    END IF;
END;
$migration$;

CREATE OR REPLACE FUNCTION public.og70_validate_row(s text, j jsonb, skip_not_valid boolean DEFAULT false)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    r og_compat.reward_ledger;
    has_prior_credit_refund boolean;
BEGIN
    IF s = 'reward_ledger' AND j->>'entry_type' = 'CREDIT_REFUND' THEN
        -- 1. Directly inspect the captured validator definition to determine if it already handles CREDIT_REFUND (e.g. V44)
        SELECT pg_get_functiondef('public.og48_validate_existing_row(text,jsonb,boolean)'::regprocedure) LIKE '%CREDIT_REFUND%'
        INTO has_prior_credit_refund;

        IF has_prior_credit_refund THEN
            -- Invoke preserved pre-V48 validator first (runs V44 CREDIT_REFUND validation)
            PERFORM public.og48_validate_existing_row(s, j, skip_not_valid);
        ELSE
            -- Baseline CREDIT_REFUND checks if V44 was not present in the migration chain
            r := jsonb_populate_record(NULL::og_compat.reward_ledger, j);
            IF r.entry_id IS NULL OR r.user_id IS NULL OR r.created_at IS NULL
                OR r.points_delta IS NULL OR r.points_delta <= 0
                OR r.order_id IS NULL OR r.group_id IS NOT NULL
                OR r.command_key IS NULL THEN
                RAISE EXCEPTION 'Invalid credit refund entry' USING ERRCODE = '23514';
            END IF;
        END IF;

        -- 2. Apply additional D-10 Option B validation: structured expires_at is required
        r := jsonb_populate_record(NULL::og_compat.reward_ledger, j);
        IF r.expires_at IS NULL THEN
            RAISE EXCEPTION 'Invalid credit refund entry: missing expires_at' USING ERRCODE = '23514';
        END IF;

    ELSIF s = 'reward_ledger' AND j->>'entry_type' = 'CORRECTION' THEN
        r := jsonb_populate_record(NULL::og_compat.reward_ledger, j);
        IF r.entry_id IS NULL OR r.user_id IS NULL OR r.created_at IS NULL
            OR r.actor_id IS NULL OR r.command_key IS NULL THEN
            RAISE EXCEPTION 'Required field in reward_ledger' USING ERRCODE = '23502';
        END IF;
        IF r.points_delta IS NULL OR r.points_delta = 0
            OR NULLIF(btrim(r.command_key), '') IS NULL
            OR r.reason IS NULL OR NULLIF(btrim(r.reason), '') IS NULL THEN
            RAISE EXCEPTION 'Constraint violation in reward_ledger' USING ERRCODE = '23514';
        END IF;

    -- 3. For all other tables and entry types, invoke the preserved validator chain
    ELSIF to_regprocedure('public.og48_validate_existing_row(text,jsonb,boolean)') IS NOT NULL THEN
        PERFORM public.og48_validate_existing_row(s, j, skip_not_valid);
    END IF;
END;
$$;
