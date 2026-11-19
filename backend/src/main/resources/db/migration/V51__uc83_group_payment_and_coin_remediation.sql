-- ============================================================================
-- V47__uc83_group_payment_and_coin_remediation.sql
-- Module: Commerce / Payment / Reward Ledger (Task 1.2b - UC83 Migration)
-- Business Context:
--   1. Support reward_policy_id on checkout_groups for policy pinning (DEC-10).
--   2. Update og_compat.checkout_groups view and og70_write_view() to project
--      and persist reward_policy_id.
--   3. Establish Clean Chained Validator Layer:
--      - Capture existing og70_validate_row as og47_validate_existing_row (fail-closed).
--      - Redefine og70_validate_row with strict DEBIT_CHECKOUT assertions
--        (required fields, non-blank command key, points_delta < 0, dual ownership).
--      - Call backup validator unconditionally without early RETURN, preserving
--        CREDIT_REFUND, CORRECTION, and other entity validations.
--   4. Provide canonical reward policy provisioning function (DEC-10.6B Option 3).
-- ============================================================================

-- 1. Add reward_policy_id column to public.checkout_groups
ALTER TABLE public.checkout_groups
    ADD COLUMN IF NOT EXISTS reward_policy_id BIGINT REFERENCES public.business_policies(source_reward_policies_policy_id);

-- 2. Update og_compat.checkout_groups view to project reward_policy_id
CREATE OR REPLACE VIEW og_compat.checkout_groups AS
SELECT
    p.group_id::uuid AS group_id,
    p.buyer_id::bigint AS buyer_id,
    p.command_key::character varying(150) AS command_key,
    p.request_digest::character(64) AS request_digest,
    p.address_snapshot::jsonb AS address_snapshot,
    p.delivery_method::character varying(20) AS delivery_method,
    p.carrier::character varying(100) AS carrier,
    p.payment_method::character varying(30) AS payment_method,
    p.expected_total::numeric(19,2) AS expected_total,
    p.state::character varying(20) AS state,
    p.created_at::timestamp with time zone AS created_at,
    p.payment_due_at::timestamp with time zone AS payment_due_at,
    p.reward_policy_id::bigint AS reward_policy_id
FROM public.checkout_groups p
WHERE p.record_type = 'checkout_groups';

ALTER VIEW og_compat.checkout_groups ALTER COLUMN state SET DEFAULT 'READY'::character varying;
ALTER VIEW og_compat.checkout_groups ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;

-- Re-attach INSTEAD OF trigger on view
DROP TRIGGER IF EXISTS og70_write ON og_compat.checkout_groups;
CREATE TRIGGER og70_write INSTEAD OF INSERT OR UPDATE OR DELETE ON og_compat.checkout_groups FOR EACH ROW EXECUTE FUNCTION public.og70_write_view();

-- 3. Patch public.og70_write_view() to forward reward_policy_id for checkout_groups
DO $migration$
DECLARE
    def text;
    prev text;
    occ integer;

    -- Anchors
    a_insert text := $s$payment_due_at,record_type) SELECT r.group_id,r.buyer_id,r.command_key,r.request_digest,r.address_snapshot,r.delivery_method,r.carrier,r.payment_method,r.expected_total,r.state,r.created_at,r.payment_due_at,''checkout_groups'' FROM jsonb_populate_record(NULL::og_compat.checkout_groups,$1) r$s$;

    r_insert text := $s$payment_due_at,reward_policy_id,record_type) SELECT r.group_id,r.buyer_id,r.command_key,r.request_digest,r.address_snapshot,r.delivery_method,r.carrier,r.payment_method,r.expected_total,r.state,r.created_at,r.payment_due_at,r.reward_policy_id,''checkout_groups'' FROM jsonb_populate_record(NULL::og_compat.checkout_groups,$1) r$s$;

    a_update text := $s$payment_due_at=r.payment_due_at FROM jsonb_populate_record(NULL::og_compat.checkout_groups,$1) r,jsonb_populate_record(NULL::og_compat.checkout_groups,$2) old_r WHERE t.group_id=old_r.group_id$s$;

    r_update text := $s$payment_due_at=r.payment_due_at,reward_policy_id=r.reward_policy_id FROM jsonb_populate_record(NULL::og_compat.checkout_groups,$1) r,jsonb_populate_record(NULL::og_compat.checkout_groups,$2) old_r WHERE t.group_id=old_r.group_id$s$;

    a_build_obj text := $s$''payment_due_at'',to_jsonb(t)->''payment_due_at'')$s$;

    r_build_obj text := $s$''payment_due_at'',to_jsonb(t)->''payment_due_at'',''reward_policy_id'',to_jsonb(t)->''reward_policy_id'')$s$;

    a_result text := $s$'payment_due_at',stored->'payment_due_at');$s$;

    r_result text := $s$'payment_due_at',stored->'payment_due_at','reward_policy_id',stored->'reward_policy_id');$s$;

    occ_r_insert integer;
    occ_r_update integer;
    occ_r_build_obj integer;
    occ_r_result integer;
BEGIN
    def := pg_get_functiondef('public.og70_write_view()'::regprocedure);

    -- Count occurrences of replacements
    occ_r_insert := (length(def) - length(replace(def, r_insert, ''))) / length(r_insert);
    occ_r_update := (length(def) - length(replace(def, r_update, ''))) / length(r_update);
    occ_r_build_obj := (length(def) - length(replace(def, r_build_obj, ''))) / length(r_build_obj);
    occ_r_result := (length(def) - length(replace(def, r_result, ''))) / length(r_result);

    -- Idempotency guard: ALL four patches must be completely present with exact counts (1, 1, 2, 1)
    IF occ_r_insert = 1 AND occ_r_update = 1 AND occ_r_build_obj = 2 AND occ_r_result = 1 THEN
        RETURN;
    END IF;

    -- Partial-state guard
    IF occ_r_insert > 0 OR occ_r_update > 0 OR occ_r_build_obj > 0 OR occ_r_result > 0
       OR def LIKE '%WHEN ''checkout_groups''%reward_policy_id%' THEN
        RAISE EXCEPTION 'Migration V47 aborted: og70_write_view() is in an inconsistent, partially patched state (insert=%, update=%, build_obj=%, result=%)',
            occ_r_insert, occ_r_update, occ_r_build_obj, occ_r_result;
    END IF;

    -- 1. Patch checkout_groups INSERT statement (expected: exactly 1 occurrence)
    occ := (length(def) - length(replace(def, a_insert, ''))) / length(a_insert);
    IF occ <> 1 THEN
        RAISE EXCEPTION 'Migration V47 failed: anchor "checkout_groups INSERT" expected 1 occurrence, found %', occ;
    END IF;
    prev := def;
    def := replace(def, a_insert, r_insert);
    IF length(def) - length(prev) <> 1 * (length(r_insert) - length(a_insert)) THEN
        RAISE EXCEPTION 'Migration V47 failed: replacement "checkout_groups INSERT" did not apply cleanly';
    END IF;

    -- 2. Patch checkout_groups UPDATE SET clause (expected: exactly 1 occurrence)
    occ := (length(def) - length(replace(def, a_update, ''))) / length(a_update);
    IF occ <> 1 THEN
        RAISE EXCEPTION 'Migration V47 failed: anchor "checkout_groups UPDATE SET" expected 1 occurrence, found %', occ;
    END IF;
    prev := def;
    def := replace(def, a_update, r_update);
    IF length(def) - length(prev) <> 1 * (length(r_update) - length(a_update)) THEN
        RAISE EXCEPTION 'Migration V47 failed: replacement "checkout_groups UPDATE SET" did not apply cleanly';
    END IF;

    -- 3. Patch checkout_groups jsonb_build_object in UPDATE and DELETE (expected: exactly 2 occurrences)
    occ := (length(def) - length(replace(def, a_build_obj, ''))) / length(a_build_obj);
    IF occ <> 2 THEN
        RAISE EXCEPTION 'Migration V47 failed: anchor "checkout_groups build_object" expected 2 occurrences, found %', occ;
    END IF;
    prev := def;
    def := replace(def, a_build_obj, r_build_obj);
    IF length(def) - length(prev) <> 2 * (length(r_build_obj) - length(a_build_obj)) THEN
        RAISE EXCEPTION 'Migration V47 failed: replacement "checkout_groups build_object" did not apply cleanly to both UPDATE and DELETE';
    END IF;

    -- 4. Patch checkout_groups result jsonb_build_object (expected: exactly 1 occurrence)
    occ := (length(def) - length(replace(def, a_result, ''))) / length(a_result);
    IF occ <> 1 THEN
        RAISE EXCEPTION 'Migration V47 failed: anchor "checkout_groups result" expected 1 occurrence, found %', occ;
    END IF;
    prev := def;
    def := replace(def, a_result, r_result);
    IF length(def) - length(prev) <> 1 * (length(r_result) - length(a_result)) THEN
        RAISE EXCEPTION 'Migration V47 failed: replacement "checkout_groups result" did not apply cleanly';
    END IF;

    -- 5. Synchronize addresses GHN columns in og70_write_view() if missing
    IF substring(def from 'WHEN ''addresses'' THEN.*?(?=WHEN|\$function\$)') NOT LIKE '%ghn_province_id%' THEN
        -- addresses INSERT
        prev := def;
        def := replace(def,
            $s$deleted_at,record_type) SELECT r.address_id,r.user_id,r.recipient_name,r.phone_number,r.province,r.district,r.ward,r.detail_address,r.is_default,r.created_at,r.updated_at,r.deleted_at,''addresses'' FROM jsonb_populate_record(NULL::og_compat.addresses,$1) r$s$,
            $s$deleted_at,record_type,ghn_province_id,ghn_district_id,ghn_ward_code) SELECT r.address_id,r.user_id,r.recipient_name,r.phone_number,r.province,r.district,r.ward,r.detail_address,r.is_default,r.created_at,r.updated_at,r.deleted_at,''addresses'',r.ghn_province_id,r.ghn_district_id,r.ghn_ward_code FROM jsonb_populate_record(NULL::og_compat.addresses,$1) r$s$
        );
        IF def = prev THEN RAISE EXCEPTION 'Migration V47 failed: anchor "addresses INSERT" not found in og70_write_view()'; END IF;

        -- addresses UPDATE
        prev := def;
        def := replace(def,
            $s$deleted_at=r.deleted_at FROM jsonb_populate_record(NULL::og_compat.addresses,$1) r,jsonb_populate_record(NULL::og_compat.addresses,$2) old_r WHERE t.address_id=old_r.address_id$s$,
            $s$deleted_at=r.deleted_at,ghn_province_id=r.ghn_province_id,ghn_district_id=r.ghn_district_id,ghn_ward_code=r.ghn_ward_code FROM jsonb_populate_record(NULL::og_compat.addresses,$1) r,jsonb_populate_record(NULL::og_compat.addresses,$2) old_r WHERE t.address_id=old_r.address_id$s$
        );
        IF def = prev THEN RAISE EXCEPTION 'Migration V47 failed: anchor "addresses UPDATE" not found in og70_write_view()'; END IF;

        -- addresses build_object
        prev := def;
        def := replace(def,
            $s$''deleted_at'',to_jsonb(t)->''deleted_at'')$s$,
            $s$''deleted_at'',to_jsonb(t)->''deleted_at'',''ghn_province_id'',to_jsonb(t)->''ghn_province_id'',''ghn_district_id'',to_jsonb(t)->''ghn_district_id'',''ghn_ward_code'',to_jsonb(t)->''ghn_ward_code'')$s$
        );
        IF def = prev THEN RAISE EXCEPTION 'Migration V47 failed: anchor "addresses build_object" not found in og70_write_view()'; END IF;

        -- addresses result
        prev := def;
        def := replace(def,
            $s$'deleted_at',stored->'deleted_at');$s$,
            $s$'deleted_at',stored->'deleted_at','ghn_province_id',stored->'ghn_province_id','ghn_district_id',stored->'ghn_district_id','ghn_ward_code',stored->'ghn_ward_code');$s$
        );
        IF def = prev THEN RAISE EXCEPTION 'Migration V47 failed: anchor "addresses result" not found in og70_write_view()'; END IF;
    END IF;

    EXECUTE def;
END;
$migration$;

-- 4. Clean Chained Validator Layer (Backup og70_validate_row -> og51_validate_existing_row)
DO $migration$
DECLARE
    definition text;
BEGIN
    IF to_regprocedure('public.og51_validate_existing_row(text,jsonb,boolean)') IS NULL THEN
        definition := pg_get_functiondef('public.og70_validate_row(text,jsonb,boolean)'::regprocedure);
        EXECUTE replace(definition, 'FUNCTION public.og70_validate_row(', 'FUNCTION public.og51_validate_existing_row(');
    END IF;

    IF to_regprocedure('public.og51_validate_existing_row(text,jsonb,boolean)') IS NULL THEN
        RAISE EXCEPTION 'Migration V51 failed: backup validator og51_validate_existing_row not created' USING ERRCODE = '23514';
    END IF;
END;
$migration$;

CREATE OR REPLACE FUNCTION public.og70_validate_row(s text, j jsonb, skip_not_valid boolean DEFAULT false)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    r og_compat.reward_ledger;
    o og_compat.orders;
    i og_compat.order_items;
BEGIN
    -- 1. DEBIT_CHECKOUT validation assertions (UC83 Group & Legacy Order debits)
    IF s = 'reward_ledger' AND j->>'entry_type' = 'DEBIT_CHECKOUT' THEN
        r := jsonb_populate_record(NULL::og_compat.reward_ledger, j);

        -- Required non-null fields (SQLSTATE 23502)
        IF r.entry_id IS NULL OR r.user_id IS NULL OR r.policy_id IS NULL
           OR r.created_at IS NULL OR r.command_key IS NULL THEN
            RAISE EXCEPTION 'Required field in reward_ledger' USING ERRCODE = '23502';
        END IF;

        -- Non-blank command key (SQLSTATE 23502)
        IF NULLIF(btrim(r.command_key), '') IS NULL THEN
            RAISE EXCEPTION 'Required field in reward_ledger' USING ERRCODE = '23502';
        END IF;

        -- Debit delta must be strictly negative (SQLSTATE 23514)
        IF r.points_delta IS NULL OR r.points_delta >= 0 THEN
            RAISE EXCEPTION 'Constraint reward_ledger_points_delta_check' USING ERRCODE = '23514';
        END IF;

        -- Dual ownership assertion: either UC83 Group or Legacy Order (SQLSTATE 23514)
        IF NOT ((r.group_id IS NOT NULL AND r.order_id IS NULL) OR (r.group_id IS NULL AND r.order_id IS NOT NULL)) THEN
            RAISE EXCEPTION 'Constraint reward_ledger_check' USING ERRCODE = '23514';
        END IF;

        -- CRITICAL: DO NOT RETURN!
        -- Fall through to invoke backup validator unconditionally so all downstream checks run.

    -- 1b. Legacy Checkout Order Coin Remediation (fixes V40 downstream un-adjusted total_amount)
    ELSIF s = 'orders' AND (j->>'points_discount_amount')::numeric > 0 AND (j->>'workflow_model' IS NULL OR j->>'workflow_model' = 'LEGACY_V14') THEN
        o := jsonb_populate_record(NULL::og_compat.orders, j);
        IF o.order_id IS NULL OR o.checkout_group_id IS NULL OR o.buyer_id IS NULL OR o.seller_id IS NULL
            OR o.shipping_recipient_name IS NULL OR o.shipping_phone_number IS NULL OR o.shipping_province IS NULL
            OR o.shipping_district IS NULL OR o.shipping_ward IS NULL OR o.shipping_detail_address IS NULL
            OR o.subtotal IS NULL OR o.shipping_fee IS NULL OR o.total_amount IS NULL OR o.status IS NULL
            OR o.payment_due_at IS NULL OR o.created_at IS NULL THEN
            RAISE EXCEPTION 'Required field in orders' USING ERRCODE = '23502';
        END IF;
        IF NOT (
            o.subtotal > 0 AND o.shipping_fee >= 0 AND o.voucher_discount_amount >= 0
            AND o.shipping_discount_amount BETWEEN 0 AND o.shipping_fee
            AND o.buyer_system_fee >= 0 AND o.seller_system_fee BETWEEN 0 AND o.subtotal
            AND o.points_discount_amount > 0
            AND (o.voucher_discount_amount + o.points_discount_amount <= o.subtotal)
            AND (o.total_amount = o.subtotal + o.buyer_system_fee + o.shipping_fee - o.voucher_discount_amount - o.shipping_discount_amount - o.points_discount_amount)
            AND (o.seller_proceeds = o.subtotal - CASE WHEN o.sponsor_type = 'SELLER' THEN o.voucher_discount_amount ELSE 0 END - o.seller_system_fee)
        ) THEN
            RAISE EXCEPTION 'Constraint ck_orders_amounts' USING ERRCODE = '23514';
        END IF;

        -- Normalize legacy payload for pre-V40 downstream validators (og25/og22)
        j := j || jsonb_build_object(
            'points_discount_amount', 0,
            'total_amount', o.total_amount + o.points_discount_amount
        );

    -- 1c. Legacy Checkout Order Item Coin Remediation (fixes V40 downstream un-adjusted buyer_line_total)
    ELSIF s = 'order_items' AND (j->>'points_allocation')::numeric > 0 AND (j->>'workflow_model' IS NULL OR j->>'workflow_model' = 'LEGACY_V14') THEN
        i := jsonb_populate_record(NULL::og_compat.order_items, j);
        IF i.order_item_id IS NULL OR i.order_id IS NULL OR i.product_id IS NULL OR i.product_title IS NULL
            OR i.quantity IS NULL OR i.agreed_price IS NULL OR i.buyer_line_total IS NULL OR i.listed_price IS NULL
            OR i.buyer_system_fee IS NULL OR i.seller_system_fee IS NULL OR i.seller_line_proceeds IS NULL
            OR i.pricing_source IS NULL THEN
            RAISE EXCEPTION 'Required field in order_items' USING ERRCODE = '23502';
        END IF;
        IF NOT (
            i.listed_price > 0 AND i.agreed_price > 0 AND i.quantity > 0
            AND i.buyer_system_fee >= 0 AND i.seller_system_fee >= 0 AND i.seller_system_fee <= (i.agreed_price * i.quantity)
            AND i.points_allocation > 0
            AND (i.voucher_allocation + i.points_allocation <= i.agreed_price * i.quantity)
            AND (i.buyer_line_total = (i.agreed_price * i.quantity) + i.buyer_system_fee - i.points_allocation)
            AND (i.seller_line_proceeds = (i.agreed_price * i.quantity) - i.seller_system_fee)
        ) THEN
            RAISE EXCEPTION 'Constraint ck_order_items_prices' USING ERRCODE = '23514';
        END IF;

        -- Normalize legacy payload for pre-V40 downstream validators (og25/og22)
        j := j || jsonb_build_object(
            'points_allocation', 0,
            'buyer_line_total', i.buyer_line_total + i.points_allocation
        );
    END IF;

    -- 2. Unconditional delegation to backup validator
    IF to_regprocedure('public.og51_validate_existing_row(text,jsonb,boolean)') IS NULL THEN
        RAISE EXCEPTION 'Backup validator public.og51_validate_existing_row missing' USING ERRCODE = '23514';
    END IF;

    PERFORM public.og51_validate_existing_row(s, j, skip_not_valid);
END;
$$;

-- 5. Canonical Reward Policy Provisioning Function (DEC-10.6B Option 3)
CREATE OR REPLACE FUNCTION public.og_provision_canonical_reward_policy(p_admin_id bigint)
RETURNS bigint LANGUAGE plpgsql AS $$
DECLARE
    v_policy_id bigint;
    v_is_admin boolean;
BEGIN
    IF p_admin_id IS NULL THEN
        RAISE EXCEPTION 'Provisioning failed: admin_id must not be null' USING ERRCODE = '23502';
    END IF;

    -- Verify admin exists, is ACTIVE, not deleted, and holds ADMIN role
    SELECT EXISTS (
        SELECT 1
        FROM og_compat.users u
        JOIN og_compat.user_roles ur ON u.user_id = ur.user_id
        JOIN og_compat.roles r ON ur.role_id = r.role_id
        WHERE u.user_id = p_admin_id
          AND u.status = 'ACTIVE'
          AND u.deleted_at IS NULL
          AND r.role_name = 'ADMIN'
    ) INTO v_is_admin;

    IF NOT v_is_admin THEN
        RAISE EXCEPTION 'Provisioning failed: user % is not an active, non-deleted ADMIN', p_admin_id USING ERRCODE = '23514';
    END IF;

    -- Idempotent check
    SELECT policy_id INTO v_policy_id
    FROM og_compat.reward_policies
    WHERE policy_code = 'REWARD_CHECKOUT_STANDARD'
    ORDER BY policy_id ASC
    LIMIT 1;

    IF v_policy_id IS NOT NULL THEN
        RETURN v_policy_id;
    END IF;

    -- Insert canonical policy
    INSERT INTO og_compat.reward_policies (
        policy_code,
        point_value,
        policy_snapshot,
        created_by
    ) VALUES (
        'REWARD_CHECKOUT_STANDARD',
        1.00,
        jsonb_build_object(
            'policy_code', 'REWARD_CHECKOUT_STANDARD',
            'max_redemption_percentage', 100,
            'description', 'Canonical standard reward policy for checkout redemption'
        ),
        p_admin_id
    ) RETURNING policy_id INTO v_policy_id;

    RETURN v_policy_id;
END;
$$;
