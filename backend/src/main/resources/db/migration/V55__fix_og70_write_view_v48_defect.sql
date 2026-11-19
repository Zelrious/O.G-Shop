-- ============================================================================
-- V55__fix_og70_write_view_v48_defect.sql (renumbered from V51)
-- Module: System / Architecture / Compatibility Views
-- Business Context: Idempotent repair and convergence guard for og70_write_view()
--                   against historical V44/V48 expires_at defect.
--
-- Defect Details:
--   Legacy PR #17 V44 originally used an overly broad anchor 'created_at,command_key,record_type) SELECT'
--   when adding 'expires_at' for reward_ledger, which unintentionally patched:
--     1. public.listing_fee_charges (branch 'listing_fee_charges')
--     2. public.payments (branch 'payment_intents')
--     3. public.penalty_ledger (branch 'penalty_ledger')
--     4. public.notifications (branch 'notification_dispatches')
--   Renumbered V48 has been table-anchored for clean/upgrade paths, while V55
--   serves as an idempotent safety net & invariant convergence guard across all paths.
-- ============================================================================

DO $migration$
DECLARE
    def text;
    prev text;
    occ integer;

    -- Anchors with explicit target tables (defect state introduced by V44)
    a_listing_fee_charges text := $s$public.listing_fee_charges (charge_id,assessment_id,amount,state,created_at,command_key,record_type,expires_at) SELECT$s$;
    r_listing_fee_charges text := $s$public.listing_fee_charges (charge_id,assessment_id,amount,state,created_at,command_key,record_type) SELECT$s$;

    a_payments text := $s$public.payments (source_payment_intents_intent_id,purpose,group_id,listing_charge_id,expected_amount,currency,payment_method,state,deadline,created_at,command_key,record_type,expires_at) SELECT$s$;
    r_payments text := $s$public.payments (source_payment_intents_intent_id,purpose,group_id,listing_charge_id,expected_amount,currency,payment_method,state,deadline,created_at,command_key,record_type) SELECT$s$;

    a_penalty_ledger text := $s$public.penalty_ledger (entry_id,user_id,report_id,policy_id,points_delta,adjustment_of,decided_by,reason,created_at,command_key,record_type,expires_at) SELECT$s$;
    r_penalty_ledger text := $s$public.penalty_ledger (entry_id,user_id,report_id,policy_id,points_delta,adjustment_of,decided_by,reason,created_at,command_key,record_type) SELECT$s$;

    a_notifications text := $s$public.notifications (source_notification_dispatches_dispatch_id,sender_id,event_id,title,content,target_data,created_at,command_key,record_type,expires_at) SELECT$s$;
    r_notifications text := $s$public.notifications (source_notification_dispatches_dispatch_id,sender_id,event_id,title,content,target_data,created_at,command_key,record_type) SELECT$s$;

    -- Occurrence counters for idempotency & partial-state detection
    occ_r_listing_fee_charges integer;
    occ_r_payments integer;
    occ_r_penalty_ledger integer;
    occ_r_notifications integer;
BEGIN
    def := pg_get_functiondef('public.og70_write_view()'::regprocedure);

    -- Count occurrences of repaired patterns
    occ_r_listing_fee_charges := (length(def) - length(replace(def, r_listing_fee_charges, ''))) / length(r_listing_fee_charges);
    occ_r_payments := (length(def) - length(replace(def, r_payments, ''))) / length(r_payments);
    occ_r_penalty_ledger := (length(def) - length(replace(def, r_penalty_ledger, ''))) / length(r_penalty_ledger);
    occ_r_notifications := (length(def) - length(replace(def, r_notifications, ''))) / length(r_notifications);

    -- 1. Idempotency guard: If all four branches are already clean/repaired, verify invariants and return
    IF occ_r_listing_fee_charges = 1
       AND occ_r_payments = 1
       AND occ_r_penalty_ledger = 1
       AND occ_r_notifications = 1
       AND NOT (def LIKE '%' || a_listing_fee_charges || '%'
                OR def LIKE '%' || a_payments || '%'
                OR def LIKE '%' || a_penalty_ledger || '%'
                OR def LIKE '%' || a_notifications || '%') THEN
        IF NOT (def LIKE '%public.reward_ledger (entry_id,user_id,policy_id,entry_type,points_delta,order_id,group_id,actor_id,reason,created_at,command_key,record_type,expires_at) SELECT%') THEN
            RAISE EXCEPTION 'Migration V55 invariant violation: reward_ledger expires_at missing in INSERT columns';
        END IF;
        IF NOT (def LIKE '%' || $s$''reward_ledger'',r.expires_at FROM jsonb_populate_record(NULL::og_compat.reward_ledger$s$ || '%') THEN
            RAISE EXCEPTION 'Migration V55 invariant violation: reward_ledger expires_at missing in SELECT values';
        END IF;
        IF NOT (def LIKE '%case_evidence%request_id%') THEN
            RAISE EXCEPTION 'Migration V55 invariant violation: V47 case_evidence request_id missing';
        END IF;
        IF NOT (def LIKE '%shipping_ghn_province_id%shipping_ghn_district_id%shipping_ghn_ward_code%') THEN
            RAISE EXCEPTION 'Migration V55 invariant violation: V50 orders GHN columns missing';
        END IF;
        IF NOT (def LIKE '%reward_policy_id%') THEN
            RAISE EXCEPTION 'Migration V55 invariant violation: V51 checkout_groups reward_policy_id missing';
        END IF;
        RETURN;
    END IF;

    -- 2. Partial-state guard: If any replacement is already partially present alongside defective anchors, abort
    IF (occ_r_listing_fee_charges > 0 AND def LIKE '%' || a_listing_fee_charges || '%')
       OR (occ_r_payments > 0 AND def LIKE '%' || a_payments || '%')
       OR (occ_r_penalty_ledger > 0 AND def LIKE '%' || a_penalty_ledger || '%')
       OR (occ_r_notifications > 0 AND def LIKE '%' || a_notifications || '%') THEN
        RAISE EXCEPTION 'Migration V51 aborted: og70_write_view() is in an inconsistent, partially patched state';
    END IF;

    -- 3. Exact occurrence-count validation and replacement for each of the 4 defective branches

    -- Branch 1: listing_fee_charges
    occ := (length(def) - length(replace(def, a_listing_fee_charges, ''))) / length(a_listing_fee_charges);
    IF occ <> 1 THEN
        RAISE EXCEPTION 'Migration V51 failed: anchor "listing_fee_charges INSERT" expected 1 occurrence, found %', occ;
    END IF;
    prev := def;
    def := replace(def, a_listing_fee_charges, r_listing_fee_charges);
    IF length(def) - length(prev) <> 1 * (length(r_listing_fee_charges) - length(a_listing_fee_charges)) THEN
        RAISE EXCEPTION 'Migration V51 failed: replacement "listing_fee_charges INSERT" did not apply cleanly';
    END IF;

    -- Branch 2: payment_intents -> public.payments
    occ := (length(def) - length(replace(def, a_payments, ''))) / length(a_payments);
    IF occ <> 1 THEN
        RAISE EXCEPTION 'Migration V51 failed: anchor "payments INSERT" expected 1 occurrence, found %', occ;
    END IF;
    prev := def;
    def := replace(def, a_payments, r_payments);
    IF length(def) - length(prev) <> 1 * (length(r_payments) - length(a_payments)) THEN
        RAISE EXCEPTION 'Migration V51 failed: replacement "payments INSERT" did not apply cleanly';
    END IF;

    -- Branch 3: penalty_ledger
    occ := (length(def) - length(replace(def, a_penalty_ledger, ''))) / length(a_penalty_ledger);
    IF occ <> 1 THEN
        RAISE EXCEPTION 'Migration V51 failed: anchor "penalty_ledger INSERT" expected 1 occurrence, found %', occ;
    END IF;
    prev := def;
    def := replace(def, a_penalty_ledger, r_penalty_ledger);
    IF length(def) - length(prev) <> 1 * (length(r_penalty_ledger) - length(a_penalty_ledger)) THEN
        RAISE EXCEPTION 'Migration V51 failed: replacement "penalty_ledger INSERT" did not apply cleanly';
    END IF;

    -- Branch 4: notification_dispatches -> public.notifications
    occ := (length(def) - length(replace(def, a_notifications, ''))) / length(a_notifications);
    IF occ <> 1 THEN
        RAISE EXCEPTION 'Migration V51 failed: anchor "notifications INSERT" expected 1 occurrence, found %', occ;
    END IF;
    prev := def;
    def := replace(def, a_notifications, r_notifications);
    IF length(def) - length(prev) <> 1 * (length(r_notifications) - length(a_notifications)) THEN
        RAISE EXCEPTION 'Migration V51 failed: replacement "notifications INSERT" did not apply cleanly';
    END IF;

    -- 4. Post-replacement invariant assertions
    -- Invariant A: reward_ledger MUST retain expires_at in both INSERT columns and SELECT values
    IF NOT (def LIKE '%public.reward_ledger (entry_id,user_id,policy_id,entry_type,points_delta,order_id,group_id,actor_id,reason,created_at,command_key,record_type,expires_at) SELECT%') THEN
        RAISE EXCEPTION 'Migration V51 invariant violation: reward_ledger expires_at must be preserved in INSERT columns';
    END IF;

    IF NOT (def LIKE '%' || $s$''reward_ledger'',r.expires_at FROM jsonb_populate_record(NULL::og_compat.reward_ledger$s$ || '%') THEN
        RAISE EXCEPTION 'Migration V51 invariant violation: reward_ledger expires_at must be preserved in SELECT values';
    END IF;

    -- Invariant B: None of the 4 defective anchors can remain
    IF def LIKE '%' || a_listing_fee_charges || '%'
       OR def LIKE '%' || a_payments || '%'
       OR def LIKE '%' || a_penalty_ledger || '%'
       OR def LIKE '%' || a_notifications || '%' THEN
        RAISE EXCEPTION 'Migration V51 invariant violation: residual defective anchor found in og70_write_view()';
    END IF;

    -- Invariant C: Prior migrations V43, V46, V47 modifications must remain intact
    IF NOT (def LIKE '%case_evidence%request_id%') THEN
        RAISE EXCEPTION 'Migration V51 invariant violation: V43 case_evidence request_id was unexpectedly corrupted';
    END IF;

    IF NOT (def LIKE '%shipping_ghn_province_id%shipping_ghn_district_id%shipping_ghn_ward_code%') THEN
        RAISE EXCEPTION 'Migration V51 invariant violation: V46 orders GHN columns were unexpectedly corrupted';
    END IF;

    IF NOT (def LIKE '%reward_policy_id%') THEN
        RAISE EXCEPTION 'Migration V51 invariant violation: V47 checkout_groups reward_policy_id was unexpectedly corrupted';
    END IF;

    -- 5. Execute updated definition
    EXECUTE def;
END;
$migration$;
