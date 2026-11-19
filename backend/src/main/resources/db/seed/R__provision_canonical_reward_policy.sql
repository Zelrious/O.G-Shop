-- ============================================================================
-- R__provision_canonical_reward_policy.sql
-- Module: Payment / Commerce / Reward Policy (DEC-10.6B Option 3)
-- Purpose: Controlled seed script for canonical reward policy 'REWARD_CHECKOUT_STANDARD'
-- Deployment Contract:
--   Requires explicit Admin ID provided via deployment configuration parameter
--   'app.reward_policy_admin_id'.
--   If parameter is missing, empty, or references an invalid/non-admin user,
--   execution FAILS-CLOSED immediately.
-- ============================================================================

DO $seed$
DECLARE
    v_config_admin text;
    v_admin_id bigint;
    v_policy_id bigint;
BEGIN
    -- 1. Read Admin ID from explicit deployment configuration parameter
    v_config_admin := nullif(btrim(current_setting('app.reward_policy_admin_id', true)), '');

    IF v_config_admin IS NULL THEN
        RAISE EXCEPTION 'Seed failed: Missing explicit deployment configuration for Admin ID (parameter app.reward_policy_admin_id)'
            USING ERRCODE = '23502';
    END IF;

    BEGIN
        v_admin_id := v_config_admin::bigint;
    EXCEPTION WHEN OTHERS THEN
        RAISE EXCEPTION 'Seed failed: Invalid Admin ID format "%" (must be numeric bigint)', v_config_admin
            USING ERRCODE = '22023';
    END;

    -- 2. Provision canonical reward policy via DEC-10.6B Option 3 function
    -- (function verifies admin exists, status = 'ACTIVE', deleted_at IS NULL, ROLE_ADMIN, and is idempotent)
    v_policy_id := public.og_provision_canonical_reward_policy(v_admin_id);
END;
$seed$;
