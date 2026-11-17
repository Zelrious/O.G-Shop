-- ============================================================================
-- V45__forward_reward_ledger_expires_at_in_dispatch.sql
-- Module: Payment / Settlement / Trust & Safety
-- Business Context: Support CREDIT_REFUND in reward_ledger with structured
--                   expires_at per PO D-10 Option B / Task 4.1b approval.
-- Purpose: Forward expires_at in public.og70_dispatch() for reward_ledger
--          so that BEFORE trigger populates expires_at prior to row validation
--          and table insertion.
-- Safety: Self-contained, idempotent, preserves all other table branches.
-- ============================================================================

DO $migration$
DECLARE
    def text;
    prev text;
BEGIN
    def := pg_get_functiondef('public.og70_dispatch()'::regprocedure);

    -- Idempotency check: if already patched with reward_ledger expires_at, skip function rewriting
    IF def LIKE '%WHEN ''reward_ledger''%''expires_at'',n->''expires_at''%' THEN
        RETURN;
    END IF;

    -- 1. Patch reward_ledger nj construction to include expires_at
    prev := def;
    def := replace(def,
        $s$jsonb_build_object('entry_id',n->'entry_id','user_id',n->'user_id','policy_id',n->'policy_id','entry_type',n->'entry_type','points_delta',n->'points_delta','order_id',n->'order_id','group_id',n->'group_id','actor_id',n->'actor_id','reason',n->'reason','created_at',n->'created_at','command_key',n->'command_key')$s$,
        $s$jsonb_build_object('entry_id',n->'entry_id','user_id',n->'user_id','policy_id',n->'policy_id','entry_type',n->'entry_type','points_delta',n->'points_delta','order_id',n->'order_id','group_id',n->'group_id','actor_id',n->'actor_id','reason',n->'reason','created_at',n->'created_at','command_key',n->'command_key','expires_at',n->'expires_at')$s$
    );
    IF def = prev THEN
        RAISE EXCEPTION 'Migration V45 failed: anchor "reward_ledger nj" not found in og70_dispatch()';
    END IF;

    -- 2. Patch reward_ledger oj construction to include expires_at
    prev := def;
    def := replace(def,
        $s$jsonb_build_object('entry_id',o->'entry_id','user_id',o->'user_id','policy_id',o->'policy_id','entry_type',o->'entry_type','points_delta',o->'points_delta','order_id',o->'order_id','group_id',o->'group_id','actor_id',o->'actor_id','reason',o->'reason','created_at',o->'created_at','command_key',o->'command_key')$s$,
        $s$jsonb_build_object('entry_id',o->'entry_id','user_id',o->'user_id','policy_id',o->'policy_id','entry_type',o->'entry_type','points_delta',o->'points_delta','order_id',o->'order_id','group_id',o->'group_id','actor_id',o->'actor_id','reason',o->'reason','created_at',o->'created_at','command_key',o->'command_key','expires_at',o->'expires_at')$s$
    );
    IF def = prev THEN
        RAISE EXCEPTION 'Migration V45 failed: anchor "reward_ledger oj" not found in og70_dispatch()';
    END IF;

    -- 3. Patch reward_ledger BEFORE phase: assign expires_at to n before row validation
    prev := def;
    def := replace(def,
        $s$IF TG_OP<>'DELETE' THEN PERFORM og70_validate_row('reward_ledger',result); END IF;$s$,
        $s$IF TG_OP<>'DELETE' THEN n:=jsonb_set(n,ARRAY['expires_at'],coalesce(result->'expires_at','null'::jsonb),true); END IF;
    IF TG_OP<>'DELETE' THEN PERFORM og70_validate_row('reward_ledger',result); END IF;$s$
    );
    IF def = prev THEN
        RAISE EXCEPTION 'Migration V45 failed: anchor "reward_ledger BEFORE validate" not found in og70_dispatch()';
    END IF;

    EXECUTE def;
END;
$migration$;

