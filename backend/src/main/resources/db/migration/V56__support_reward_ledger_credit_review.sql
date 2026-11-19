-- ============================================================================
-- V56__support_reward_ledger_credit_review.sql
-- Module: Trust & Safety / Order Reviews / Reward Ledger (UC61)
-- Business Context:
--   1. OrderReviewService (UC61) awards 500 coins to reviewer and 400/500 coins
--      to 4/5-star reviewees using entry_type = 'CREDIT_REVIEW'.
--   2. Legacy og70_validate_row() in V22 only allowed ('CREDIT_COMPLETION',
--      'DEBIT_CHECKOUT', 'CORRECTION') and required policy_id IS NOT NULL.
--   3. This migration captures the current validator into og56_validate_existing_row
--      and adds strict fail-closed validation for CREDIT_REVIEW entries.
-- ============================================================================

DO $migration$
DECLARE
    definition text;
BEGIN
    IF to_regprocedure('public.og56_validate_existing_row(text,jsonb,boolean)') IS NULL THEN
        definition := pg_get_functiondef('public.og70_validate_row(text,jsonb,boolean)'::regprocedure);
        EXECUTE replace(definition, 'FUNCTION public.og70_validate_row(', 'FUNCTION public.og56_validate_existing_row(');
    END IF;

    IF to_regprocedure('public.og56_validate_existing_row(text,jsonb,boolean)') IS NULL THEN
        RAISE EXCEPTION 'Migration V56 failed: backup validator og56_validate_existing_row not created' USING ERRCODE = '23514';
    END IF;
END;
$migration$;

CREATE OR REPLACE FUNCTION public.og70_validate_row(s text, j jsonb, skip_not_valid boolean DEFAULT false)
RETURNS void LANGUAGE plpgsql AS $$
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

        IF r.points_delta IS NULL OR r.points_delta <= 0
            OR r.group_id IS NOT NULL
            OR r.reason IS NULL OR NULLIF(btrim(r.reason), '') IS NULL THEN
            RAISE EXCEPTION 'Invalid CREDIT_REVIEW entry in reward_ledger' USING ERRCODE = '23514';
        END IF;

        RETURN;
    END IF;

    IF to_regprocedure('public.og56_validate_existing_row(text,jsonb,boolean)') IS NULL THEN
        RAISE EXCEPTION 'Backup validator public.og56_validate_existing_row missing' USING ERRCODE = '23514';
    END IF;

    PERFORM public.og56_validate_existing_row(s, j, skip_not_valid);
END;
$$;
