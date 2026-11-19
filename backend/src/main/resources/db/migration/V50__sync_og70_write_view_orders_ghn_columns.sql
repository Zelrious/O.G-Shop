-- ============================================================================
-- V46__sync_og70_write_view_orders_ghn_columns.sql
-- Module: Commerce / Orders / Fulfillment
-- Business Context: Synchronize public.og70_write_view() orders branch with
--                   og_compat.orders view definition (47 columns).
--                   V36 added shipping_ghn_province_id, shipping_ghn_district_id,
--                   shipping_ghn_ward_code to public.orders and og_compat.orders,
--                   but the test database runtime og70_write_view() retained the
--                   V22 44-attribute JSONB snapshot for orders.
--                   This caused JSONB inequality in the optimistic locking check
--                   (jsonb_build_object(...) IS NOT DISTINCT FROM $2), resulting
--                   in 0 rows affected on UPDATE and Hibernate StaleObjectStateException.
-- Purpose: Extend orders INSERT, UPDATE SET, jsonb_build_object (UPDATE/DELETE),
--          and result projection in public.og70_write_view() to include the 3 GHN columns.
-- Safety: Self-contained, idempotent, atomic, strict occurrence validation (1, 1, 2, 1),
--         detects and aborts on partial patches, preserves all other table branches and logic.
-- ============================================================================

DO $migration$
DECLARE
    def text;
    prev text;
    occ integer;

    -- Anchors (exact fragments from V22 baseline)
    a_insert text := $s$purchase_shipping_used_at,record_type) SELECT r.order_id,r.checkout_group_id,r.buyer_id,r.seller_id,r.source_address_id,r.shipping_recipient_name,r.shipping_phone_number,r.shipping_province,r.shipping_district,r.shipping_ward,r.shipping_detail_address,r.subtotal,r.shipping_fee,r.total_amount,r.currency,r.status,r.payment_due_at,r.version,r.created_at,r.updated_at,r.completed_at,r.cancelled_at,r.cancellation_reason,r.buyer_system_fee,r.seller_system_fee,r.seller_proceeds,r.voucher_discount_amount,r.shipping_discount_amount,r.sponsor_type,r.workflow_model,r.group_id,r.return_allowed,r.delivery_method,r.carrier_snapshot,r.handover_day,r.points_discount_amount,r.paid_confirmed_at,r.seller_accept_due_at,r.valid_delivered_at,r.received_at,r.return_due_at,r.early_completed_at,r.early_completion_warning_version,r.purchase_shipping_used_at,''orders'' FROM jsonb_populate_record(NULL::og_compat.orders,$1) r$s$;

    r_insert text := $s$purchase_shipping_used_at,record_type,shipping_ghn_province_id,shipping_ghn_district_id,shipping_ghn_ward_code) SELECT r.order_id,r.checkout_group_id,r.buyer_id,r.seller_id,r.source_address_id,r.shipping_recipient_name,r.shipping_phone_number,r.shipping_province,r.shipping_district,r.shipping_ward,r.shipping_detail_address,r.subtotal,r.shipping_fee,r.total_amount,r.currency,r.status,r.payment_due_at,r.version,r.created_at,r.updated_at,r.completed_at,r.cancelled_at,r.cancellation_reason,r.buyer_system_fee,r.seller_system_fee,r.seller_proceeds,r.voucher_discount_amount,r.shipping_discount_amount,r.sponsor_type,r.workflow_model,r.group_id,r.return_allowed,r.delivery_method,r.carrier_snapshot,r.handover_day,r.points_discount_amount,r.paid_confirmed_at,r.seller_accept_due_at,r.valid_delivered_at,r.received_at,r.return_due_at,r.early_completed_at,r.early_completion_warning_version,r.purchase_shipping_used_at,''orders'',r.shipping_ghn_province_id,r.shipping_ghn_district_id,r.shipping_ghn_ward_code FROM jsonb_populate_record(NULL::og_compat.orders,$1) r$s$;

    a_update text := $s$purchase_shipping_used_at=r.purchase_shipping_used_at FROM jsonb_populate_record(NULL::og_compat.orders,$1) r,jsonb_populate_record(NULL::og_compat.orders,$2) old_r WHERE t.order_id=old_r.order_id$s$;

    r_update text := $s$purchase_shipping_used_at=r.purchase_shipping_used_at,shipping_ghn_province_id=r.shipping_ghn_province_id,shipping_ghn_district_id=r.shipping_ghn_district_id,shipping_ghn_ward_code=r.shipping_ghn_ward_code FROM jsonb_populate_record(NULL::og_compat.orders,$1) r,jsonb_populate_record(NULL::og_compat.orders,$2) old_r WHERE t.order_id=old_r.order_id$s$;

    a_build_obj text := $s$''purchase_shipping_used_at'',to_jsonb(t)->''purchase_shipping_used_at'')$s$;

    r_build_obj text := $s$''purchase_shipping_used_at'',to_jsonb(t)->''purchase_shipping_used_at'',''shipping_ghn_province_id'',to_jsonb(t)->''shipping_ghn_province_id'',''shipping_ghn_district_id'',to_jsonb(t)->''shipping_ghn_district_id'',''shipping_ghn_ward_code'',to_jsonb(t)->''shipping_ghn_ward_code'')$s$;

    a_result text := $s$'purchase_shipping_used_at',stored->'purchase_shipping_used_at');$s$;

    r_result text := $s$'purchase_shipping_used_at',stored->'purchase_shipping_used_at','shipping_ghn_province_id',stored->'shipping_ghn_province_id','shipping_ghn_district_id',stored->'shipping_ghn_district_id','shipping_ghn_ward_code',stored->'shipping_ghn_ward_code');$s$;

    -- Occurrence counters for idempotency & partial-state detection
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

    -- A1. Idempotency guard: ALL four patches must be completely present with exact counts (1, 1, 2, 1)
    IF occ_r_insert = 1 AND occ_r_update = 1 AND occ_r_build_obj = 2 AND occ_r_result = 1 THEN
        RETURN;
    END IF;

    -- A2. Partial-state guard: If any GHN order replacement or orders GHN column is partially present, abort immediately
    IF occ_r_insert > 0 OR occ_r_update > 0 OR occ_r_build_obj > 0 OR occ_r_result > 0
       OR def LIKE '%WHEN ''orders''%shipping_ghn_province_id%' THEN
        RAISE EXCEPTION 'Migration V46 aborted: og70_write_view() is in an inconsistent, partially patched state (insert=%, update=%, build_obj=%, result=%)',
            occ_r_insert, occ_r_update, occ_r_build_obj, occ_r_result;
    END IF;

    -- A3. Exact occurrence-count validation before and after each replacement

    -- 1. Patch orders INSERT statement (expected: exactly 1 occurrence)
    occ := (length(def) - length(replace(def, a_insert, ''))) / length(a_insert);
    IF occ <> 1 THEN
        RAISE EXCEPTION 'Migration V46 failed: anchor "orders INSERT" expected 1 occurrence, found %', occ;
    END IF;
    prev := def;
    def := replace(def, a_insert, r_insert);
    IF length(def) - length(prev) <> 1 * (length(r_insert) - length(a_insert)) THEN
        RAISE EXCEPTION 'Migration V46 failed: replacement "orders INSERT" did not apply cleanly';
    END IF;

    -- 2. Patch orders UPDATE SET clause (expected: exactly 1 occurrence)
    occ := (length(def) - length(replace(def, a_update, ''))) / length(a_update);
    IF occ <> 1 THEN
        RAISE EXCEPTION 'Migration V46 failed: anchor "orders UPDATE SET" expected 1 occurrence, found %', occ;
    END IF;
    prev := def;
    def := replace(def, a_update, r_update);
    IF length(def) - length(prev) <> 1 * (length(r_update) - length(a_update)) THEN
        RAISE EXCEPTION 'Migration V46 failed: replacement "orders UPDATE SET" did not apply cleanly';
    END IF;

    -- 3. Patch orders jsonb_build_object in UPDATE and DELETE (expected: exactly 2 occurrences)
    occ := (length(def) - length(replace(def, a_build_obj, ''))) / length(a_build_obj);
    IF occ <> 2 THEN
        RAISE EXCEPTION 'Migration V46 failed: anchor "orders build_object" expected 2 occurrences, found %', occ;
    END IF;
    prev := def;
    def := replace(def, a_build_obj, r_build_obj);
    IF length(def) - length(prev) <> 2 * (length(r_build_obj) - length(a_build_obj)) THEN
        RAISE EXCEPTION 'Migration V46 failed: replacement "orders build_object" did not apply cleanly to both UPDATE and DELETE';
    END IF;

    -- 4. Patch orders result jsonb_build_object projection (expected: exactly 1 occurrence)
    occ := (length(def) - length(replace(def, a_result, ''))) / length(a_result);
    IF occ <> 1 THEN
        RAISE EXCEPTION 'Migration V46 failed: anchor "orders result" expected 1 occurrence, found %', occ;
    END IF;
    prev := def;
    def := replace(def, a_result, r_result);
    IF length(def) - length(prev) <> 1 * (length(r_result) - length(a_result)) THEN
        RAISE EXCEPTION 'Migration V46 failed: replacement "orders result" did not apply cleanly';
    END IF;

    -- Post-check: verify all replacements now have exact expected counts (1, 1, 2, 1) and anchors have 0
    IF ((length(def) - length(replace(def, r_insert, ''))) / length(r_insert) <> 1) OR
       ((length(def) - length(replace(def, r_update, ''))) / length(r_update) <> 1) OR
       ((length(def) - length(replace(def, r_build_obj, ''))) / length(r_build_obj) <> 2) OR
       ((length(def) - length(replace(def, r_result, ''))) / length(r_result) <> 1) THEN
        RAISE EXCEPTION 'Migration V46 failed: post-replacement occurrence check failed';
    END IF;

    EXECUTE def;
END;
$migration$;
