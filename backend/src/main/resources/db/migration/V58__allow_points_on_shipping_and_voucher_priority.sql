-- ============================================================================
-- V58__allow_points_on_shipping_and_voucher_priority.sql
-- Module: Commerce / Payment / Reward Ledger (TASK-0122)
-- Business Context:
--   1. Allow buyer Coins (points) to directly offset CARRIER shipping fee
--      after Voucher priority (Voucher applied first, then Coins on goods,
--      then remaining Coins on shipping).
--   2. Adds orders.points_shipping_discount_amount (NUMERIC(19,2) DEFAULT 0)
--      so shipping voucher (shipping_discount_amount) and shipping coins
--      (points_shipping_discount_amount) remain strictly separated for exact
--      accounting, fund components, and full coin refund on return/cancel.
--   3. Preserves 100% backward compatibility for all existing rows (default 0).
-- ============================================================================

-- 1. Add points_shipping_discount_amount column to public.orders
ALTER TABLE public.orders
    ADD COLUMN IF NOT EXISTS points_shipping_discount_amount NUMERIC(19,2) NOT NULL DEFAULT 0;

-- 2. Expose points_shipping_discount_amount in og_compat.orders view
CREATE OR REPLACE VIEW og_compat.orders AS
SELECT
    p.order_id,
    p.checkout_group_id,
    p.buyer_id,
    p.seller_id,
    p.source_address_id,
    p.shipping_recipient_name,
    p.shipping_phone_number,
    p.shipping_province,
    p.shipping_district,
    p.shipping_ward,
    p.shipping_detail_address,
    p.subtotal,
    p.shipping_fee,
    p.total_amount,
    p.currency,
    p.status,
    p.payment_due_at,
    p.version,
    p.created_at,
    p.updated_at,
    p.completed_at,
    p.cancelled_at,
    p.cancellation_reason,
    p.buyer_system_fee,
    p.seller_system_fee,
    p.seller_proceeds,
    p.voucher_discount_amount,
    p.shipping_discount_amount,
    p.sponsor_type,
    p.workflow_model,
    p.group_id,
    p.return_allowed,
    p.delivery_method,
    p.carrier_snapshot,
    p.handover_day,
    p.points_discount_amount,
    p.paid_confirmed_at,
    p.seller_accept_due_at,
    p.valid_delivered_at,
    p.received_at,
    p.return_due_at,
    p.early_completed_at,
    p.early_completion_warning_version,
    p.purchase_shipping_used_at,
    p.shipping_ghn_province_id::integer AS shipping_ghn_province_id,
    p.shipping_ghn_district_id::integer AS shipping_ghn_district_id,
    p.shipping_ghn_ward_code::character varying(20) AS shipping_ghn_ward_code,
    p.points_shipping_discount_amount
FROM public.orders p
WHERE p.record_type = 'orders';

-- 3. Patch og70_write_view() idempotently to persist points_shipping_discount_amount
DO $migration$
DECLARE
    def text;
    prev text;
BEGIN
    def := pg_get_functiondef('public.og70_write_view()'::regprocedure);

    IF def LIKE '%points_shipping_discount_amount%' THEN
        RETURN;
    END IF;

    -- orders INSERT
    prev := def;
    def := replace(def,
        $s$shipping_ghn_province_id,shipping_ghn_district_id,shipping_ghn_ward_code) SELECT r.order_id,r.checkout_group_id,r.buyer_id,r.seller_id,r.source_address_id,r.shipping_recipient_name,r.shipping_phone_number,r.shipping_province,r.shipping_district,r.shipping_ward,r.shipping_detail_address,r.subtotal,r.shipping_fee,r.total_amount,r.currency,r.status,r.payment_due_at,r.version,r.created_at,r.updated_at,r.completed_at,r.cancelled_at,r.cancellation_reason,r.buyer_system_fee,r.seller_system_fee,r.seller_proceeds,r.voucher_discount_amount,r.shipping_discount_amount,r.sponsor_type,r.workflow_model,r.group_id,r.return_allowed,r.delivery_method,r.carrier_snapshot,r.handover_day,r.points_discount_amount,r.paid_confirmed_at,r.seller_accept_due_at,r.valid_delivered_at,r.received_at,r.return_due_at,r.early_completed_at,r.early_completion_warning_version,r.purchase_shipping_used_at,''orders'',r.shipping_ghn_province_id,r.shipping_ghn_district_id,r.shipping_ghn_ward_code FROM$s$,
        $s$shipping_ghn_province_id,shipping_ghn_district_id,shipping_ghn_ward_code,points_shipping_discount_amount) SELECT r.order_id,r.checkout_group_id,r.buyer_id,r.seller_id,r.source_address_id,r.shipping_recipient_name,r.shipping_phone_number,r.shipping_province,r.shipping_district,r.shipping_ward,r.shipping_detail_address,r.subtotal,r.shipping_fee,r.total_amount,r.currency,r.status,r.payment_due_at,r.version,r.created_at,r.updated_at,r.completed_at,r.cancelled_at,r.cancellation_reason,r.buyer_system_fee,r.seller_system_fee,r.seller_proceeds,r.voucher_discount_amount,r.shipping_discount_amount,r.sponsor_type,r.workflow_model,r.group_id,r.return_allowed,r.delivery_method,r.carrier_snapshot,r.handover_day,r.points_discount_amount,r.paid_confirmed_at,r.seller_accept_due_at,r.valid_delivered_at,r.received_at,r.return_due_at,r.early_completed_at,r.early_completion_warning_version,r.purchase_shipping_used_at,''orders'',r.shipping_ghn_province_id,r.shipping_ghn_district_id,r.shipping_ghn_ward_code,COALESCE(r.points_shipping_discount_amount,0) FROM$s$
    );
    IF def = prev THEN
        RAISE EXCEPTION 'Migration V58 failed: anchor "orders INSERT" not found in og70_write_view()';
    END IF;

    -- orders UPDATE SET
    prev := def;
    def := replace(def,
        $s$shipping_ghn_ward_code=r.shipping_ghn_ward_code FROM jsonb_populate_record(NULL::og_compat.orders,$1) r$s$,
        $s$shipping_ghn_ward_code=r.shipping_ghn_ward_code,points_shipping_discount_amount=COALESCE(r.points_shipping_discount_amount,0) FROM jsonb_populate_record(NULL::og_compat.orders,$1) r$s$
    );
    IF def = prev THEN
        RAISE EXCEPTION 'Migration V58 failed: anchor "orders UPDATE SET" not found in og70_write_view()';
    END IF;

    -- orders build_object in UPDATE and DELETE
    prev := def;
    def := replace(def,
        $s$''shipping_ghn_ward_code'',to_jsonb(t)->''shipping_ghn_ward_code'')$s$,
        $s$''shipping_ghn_ward_code'',to_jsonb(t)->''shipping_ghn_ward_code'',''points_shipping_discount_amount'',to_jsonb(t)->''points_shipping_discount_amount'')$s$
    );
    IF def = prev THEN
        RAISE EXCEPTION 'Migration V58 failed: anchor "orders build_object" not found in og70_write_view()';
    END IF;

    -- orders result
    prev := def;
    def := replace(def,
        $s$'shipping_ghn_ward_code',stored->'shipping_ghn_ward_code');$s$,
        $s$'shipping_ghn_ward_code',stored->'shipping_ghn_ward_code','points_shipping_discount_amount',stored->'points_shipping_discount_amount');$s$
    );
    IF def = prev THEN
        RAISE EXCEPTION 'Migration V58 failed: anchor "orders result" not found in og70_write_view()';
    END IF;

    EXECUTE def;
END;
$migration$;

-- 3b. Patch og70_dispatch() idempotently so orders nj/oj include points_shipping_discount_amount
DO $dispatch_patch$
DECLARE
    def text;
    prev text;
BEGIN
    def := pg_get_functiondef('public.og70_dispatch()'::regprocedure);

    IF def LIKE '%points_shipping_discount_amount%' THEN
        RETURN;
    END IF;

    prev := def;
    def := replace(def,
        $s$'purchase_shipping_used_at',n->'purchase_shipping_used_at') END;$s$,
        $s$'purchase_shipping_used_at',n->'purchase_shipping_used_at','points_shipping_discount_amount',n->'points_shipping_discount_amount') END;$s$
    );
    IF def = prev THEN
        RAISE EXCEPTION 'Migration V58 failed: anchor "orders nj" not found in og70_dispatch()';
    END IF;

    prev := def;
    def := replace(def,
        $s$'purchase_shipping_used_at',o->'purchase_shipping_used_at') END;$s$,
        $s$'purchase_shipping_used_at',o->'purchase_shipping_used_at','points_shipping_discount_amount',o->'points_shipping_discount_amount') END;$s$
    );
    IF def = prev THEN
        RAISE EXCEPTION 'Migration V58 failed: anchor "orders oj" not found in og70_dispatch()';
    END IF;

    EXECUTE def;
END;
$dispatch_patch$;

-- 3c. Patch og70_validate_graph(boolean) idempotently so uq_discount_points_item
--     groups by (reward_entry_id, order_item_id, component) symmetrically with uq_discount_voucher_item
DO $graph_patch$
DECLARE
    def text;
    prev text;
BEGIN
    def := pg_get_functiondef('public.og70_validate_graph(boolean)'::regprocedure);

    IF def LIKE '%GROUP BY reward_entry_id,order_item_id,component%' THEN
        RETURN;
    END IF;

    prev := def;
    def := replace(def,
        $s$GROUP BY reward_entry_id,order_item_id HAVING count(*)>1 LIMIT 1) THEN RAISE EXCEPTION 'Relational constraint uq_discount_points_item'$s$,
        $s$GROUP BY reward_entry_id,order_item_id,component HAVING count(*)>1 LIMIT 1) THEN RAISE EXCEPTION 'Relational constraint uq_discount_points_item'$s$
    );
    IF def = prev THEN
        RAISE EXCEPTION 'Migration V58 failed: anchor "uq_discount_points_item" not found in og70_validate_graph(boolean)';
    END IF;

    EXECUTE def;
END;
$graph_patch$;

-- 4. Update og70_validate_row() to validate UC83 points_shipping_discount_amount
--    and normalize shipping_discount_amount for downstream pre-V58 validators
CREATE OR REPLACE FUNCTION public.og70_validate_row(s text, j jsonb, skip_not_valid boolean DEFAULT false)
 RETURNS void
 LANGUAGE plpgsql
AS $function$
DECLARE
    r og_compat.reward_ledger;
    o og_compat.orders;
    pts_ship NUMERIC;
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

    IF s = 'orders' THEN
        pts_ship := COALESCE((j->>'points_shipping_discount_amount')::numeric, 0);
        IF pts_ship < 0 THEN
            RAISE EXCEPTION 'Constraint orders_points_shipping_discount_amount_check' USING ERRCODE = '23514';
        END IF;
        IF pts_ship > 0 THEN
            o := jsonb_populate_record(NULL::og_compat.orders, j);
            IF o.workflow_model IS DISTINCT FROM 'UC83' THEN
                RAISE EXCEPTION 'Constraint ck_orders_amounts' USING ERRCODE = '23514';
            END IF;
            IF NOT (
                o.subtotal > 0
                AND o.shipping_fee >= 0
                AND o.voucher_discount_amount >= 0
                AND o.shipping_discount_amount >= 0
                AND o.points_discount_amount >= 0
                AND (o.voucher_discount_amount + o.points_discount_amount <= o.subtotal)
                AND (o.shipping_discount_amount + pts_ship <= o.shipping_fee)
                AND (o.total_amount = o.subtotal + o.shipping_fee - o.voucher_discount_amount - o.shipping_discount_amount - o.points_discount_amount - pts_ship)
                AND (o.total_amount >= 0)
            ) THEN
                RAISE EXCEPTION 'Constraint ck_orders_amounts' USING ERRCODE = '23514';
            END IF;

            -- Normalize combined shipping discount for pre-V58 downstream ck_orders_amounts check
            j := j || jsonb_build_object(
                'shipping_discount_amount', o.shipping_discount_amount + pts_ship
            );
        END IF;
    ELSIF s = 'discount_allocations'
          AND j->>'reward_entry_id' IS NOT NULL
          AND (j->>'redemption_id' IS NULL OR j->'redemption_id' = 'null'::jsonb)
          AND j->>'component' = 'SHIPPING' THEN
        IF j->>'allocation_id' IS NULL OR j->>'order_item_id' IS NULL OR j->>'amount' IS NULL THEN
            RAISE EXCEPTION 'Required field in discount_allocations' USING ERRCODE = '23502';
        END IF;
        IF (j->>'amount')::numeric <= 0 THEN
            RAISE EXCEPTION 'Constraint discount_allocations_amount_check' USING ERRCODE = '23514';
        END IF;
        -- Normalize component for pre-V58 discount_allocations_check1 (which only allowed reward_entry_id with GOODS)
        j := j || jsonb_build_object('component', 'GOODS');
    END IF;

    IF to_regprocedure('public.og56_validate_existing_row(text,jsonb,boolean)') IS NULL THEN
        RAISE EXCEPTION 'Backup validator public.og56_validate_existing_row missing' USING ERRCODE = '23514';
    END IF;

    PERFORM public.og56_validate_existing_row(s, j, skip_not_valid);
END;
$function$;

-- 5. Update og_guard_fund_component() and its og70_row wrapper so SHIPPING_CASH
--    and PLATFORM_SHIPPING_SUBSIDY account for points_shipping_discount_amount
CREATE OR REPLACE FUNCTION public.og_guard_fund_component()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'public', '$user', 'public'
AS $function$
DECLARE o og_compat.orders; expected NUMERIC;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Fund component history retained' USING ERRCODE='23514'; END IF;
    IF TG_OP='UPDATE' THEN
        IF ROW(NEW.component_id,NEW.order_id,NEW.allocation_id,NEW.late_confirmation_id,NEW.component_type,
            NEW.confirmed_amount,NEW.funding_reference,NEW.confirmed_at) IS DISTINCT FROM
            ROW(OLD.component_id,OLD.order_id,OLD.allocation_id,OLD.late_confirmation_id,OLD.component_type,
            OLD.confirmed_amount,OLD.funding_reference,OLD.confirmed_at) OR pg_trigger_depth()<2 THEN
            RAISE EXCEPTION 'Money amount/source immutable; balances change through operations only' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    SELECT * INTO STRICT o FROM og_compat.orders WHERE order_id=NEW.order_id FOR UPDATE;
    expected:=CASE NEW.component_type
        WHEN 'GOODS_CASH' THEN o.subtotal-o.voucher_discount_amount-o.points_discount_amount
        WHEN 'SHIPPING_CASH' THEN o.shipping_fee-o.shipping_discount_amount-COALESCE(o.points_shipping_discount_amount,0)
        WHEN 'PLATFORM_GOODS_SUBSIDY' THEN o.voucher_discount_amount+o.points_discount_amount
        WHEN 'PLATFORM_SHIPPING_SUBSIDY' THEN o.shipping_discount_amount+COALESCE(o.points_shipping_discount_amount,0)
        WHEN 'LATE_PAYMENT_CASH' THEN o.total_amount END;
    IF o.workflow_model<>'UC83' OR NEW.confirmed_amount<>expected
        OR NEW.reserved_amount<>0 OR NEW.refunded_amount<>0 OR NEW.released_amount<>0 OR NEW.version<>0
        OR (NEW.component_type='LATE_PAYMENT_CASH' AND NOT EXISTS(
            SELECT 1 FROM og_compat.payment_confirmations c JOIN og_compat.payment_intents i USING(intent_id)
            WHERE c.confirmation_id=NEW.late_confirmation_id AND c.disposition='RECONCILIATION'
                AND i.group_id=o.group_id))
        OR (NEW.component_type LIKE 'PLATFORM_%' AND o.total_amount>0 AND NOT EXISTS(
            SELECT 1 FROM og_compat.payment_allocations WHERE order_id=o.order_id)) THEN
        RAISE EXCEPTION 'Fund component must equal its confirmed order source' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$function$;

CREATE OR REPLACE FUNCTION public.og70_row_order_fund_components_og_guard_fund_component(p_new jsonb, p_old jsonb, p_op text, p_phase text, p_depth integer, p_args text[])
 RETURNS jsonb
 LANGUAGE plpgsql
 SET search_path TO 'public', 'og_compat'
AS $function$
DECLARE
 NEW og_compat.order_fund_components; OLD og_compat.order_fund_components;
 TG_OP text:=p_op; TG_WHEN text:=p_phase; TG_TABLE_NAME text:='order_fund_components';
 TG_TABLE_SCHEMA text:='og_compat'; TG_ARGV text[]:=p_args; TG_NARGS integer:=cardinality(p_args);
  o og_compat.orders; expected NUMERIC;
BEGIN
 NEW:=jsonb_populate_record(NULL::og_compat.order_fund_components,coalesce(p_new,'{}'::jsonb));
 OLD:=jsonb_populate_record(NULL::og_compat.order_fund_components,coalesce(p_old,'{}'::jsonb));
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Fund component history retained' USING ERRCODE='23514'; END IF;
    IF TG_OP='UPDATE' THEN
        IF ROW(NEW.component_id,NEW.order_id,NEW.allocation_id,NEW.late_confirmation_id,NEW.component_type,
            NEW.confirmed_amount,NEW.funding_reference,NEW.confirmed_at) IS DISTINCT FROM
            ROW(OLD.component_id,OLD.order_id,OLD.allocation_id,OLD.late_confirmation_id,OLD.component_type,
            OLD.confirmed_amount,OLD.funding_reference,OLD.confirmed_at) OR p_depth<2 THEN
            RAISE EXCEPTION 'Money amount/source immutable; balances change through operations only' USING ERRCODE='23514';
        END IF;
        RETURN to_jsonb(NEW);
    END IF;
    SELECT * INTO STRICT o FROM og_compat.orders WHERE order_id=NEW.order_id FOR UPDATE;
    expected:=CASE NEW.component_type
        WHEN 'GOODS_CASH' THEN o.subtotal-o.voucher_discount_amount-o.points_discount_amount
        WHEN 'SHIPPING_CASH' THEN o.shipping_fee-o.shipping_discount_amount-COALESCE(o.points_shipping_discount_amount,0)
        WHEN 'PLATFORM_GOODS_SUBSIDY' THEN o.voucher_discount_amount+o.points_discount_amount
        WHEN 'PLATFORM_SHIPPING_SUBSIDY' THEN o.shipping_discount_amount+COALESCE(o.points_shipping_discount_amount,0)
        WHEN 'LATE_PAYMENT_CASH' THEN o.total_amount END;
    IF o.workflow_model<>'UC83' OR NEW.confirmed_amount<>expected
        OR NEW.reserved_amount<>0 OR NEW.refunded_amount<>0 OR NEW.released_amount<>0 OR NEW.version<>0
        OR (NEW.component_type='LATE_PAYMENT_CASH' AND NOT EXISTS(
            SELECT 1 FROM og_compat.payment_confirmations c JOIN og_compat.payment_intents i USING(intent_id)
            WHERE c.confirmation_id=NEW.late_confirmation_id AND c.disposition='RECONCILIATION'
                AND i.group_id=o.group_id))
        OR (NEW.component_type LIKE 'PLATFORM_%' AND o.total_amount>0 AND NOT EXISTS(
            SELECT 1 FROM og_compat.payment_allocations WHERE order_id=o.order_id)) THEN
        RAISE EXCEPTION 'Fund component must equal its confirmed order source' USING ERRCODE='23514';
    END IF;
    RETURN to_jsonb(NEW);
END;
$function$;

-- 6. Centralize discount conservation check for both Voucher (GOODS/SHIPPING)
--    and Coins (GOODS + SHIPPING) across og_validate_discount_totals and all 6 og70_row wrappers
CREATE OR REPLACE FUNCTION public.og58_validate_discount_totals_for_group(gid UUID)
 RETURNS void
 LANGUAGE plpgsql
 SET search_path TO 'public', 'og_compat'
AS $function$
DECLARE
    v NUMERIC;
    p NUMERIC;
    g og_compat.checkout_groups;
BEGIN
    IF gid IS NULL THEN RETURN; END IF;

    SELECT * INTO g FROM og_compat.checkout_groups WHERE group_id=gid FOR UPDATE;
    IF NOT FOUND THEN RETURN; END IF;

    SELECT COALESCE(sum(voucher_discount_amount+shipping_discount_amount),0),
           COALESCE(sum(points_discount_amount+COALESCE(points_shipping_discount_amount,0)),0)
      INTO v, p
      FROM og_compat.orders WHERE group_id=gid;

    -- Voucher conservation checks (common across all states; strictly scoped to redemption_id IS NOT NULL)
    IF v<>COALESCE((SELECT discount_amount FROM og_compat.checkout_redemptions WHERE group_id=gid),0)
        OR EXISTS(SELECT 1 FROM og_compat.order_items i JOIN og_compat.orders o USING(order_id) WHERE o.group_id=gid AND (
            i.voucher_allocation<>(SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations d WHERE d.order_item_id=i.order_item_id
                AND redemption_id IS NOT NULL AND component='GOODS')))
        OR EXISTS(SELECT 1 FROM og_compat.orders o WHERE o.group_id=gid AND o.shipping_discount_amount<>(
            SELECT COALESCE(sum(d.amount),0) FROM og_compat.discount_allocations d JOIN og_compat.order_items i USING(order_item_id)
            WHERE i.order_id=o.order_id AND d.redemption_id IS NOT NULL AND d.component='SHIPPING'))
        OR EXISTS(SELECT 1 FROM og_compat.checkout_redemptions c WHERE c.group_id=gid AND c.discount_amount<>(
            SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations WHERE redemption_id=c.redemption_id)) THEN
        RAISE EXCEPTION 'Voucher/points allocations must exactly conserve checkout discounts' USING ERRCODE='23514';
    END IF;

    -- Coin lifecycle conservation strictly by valid CheckoutGroup states
    IF g.state = 'PAID' THEN
        IF p<>COALESCE((SELECT -l.points_delta*r.point_value FROM og_compat.reward_ledger l JOIN og_compat.reward_policies r USING(policy_id)
                WHERE l.group_id=gid AND l.entry_type='DEBIT_CHECKOUT'),0)
            OR EXISTS(SELECT 1 FROM og_compat.order_items i JOIN og_compat.orders o USING(order_id) WHERE o.group_id=gid AND (
                i.points_allocation<>(SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations d WHERE d.order_item_id=i.order_item_id
                    AND reward_entry_id IS NOT NULL AND d.component='GOODS')))
            OR EXISTS(SELECT 1 FROM og_compat.orders o WHERE o.group_id=gid AND COALESCE(o.points_shipping_discount_amount,0)<>(
                SELECT COALESCE(sum(d.amount),0) FROM og_compat.discount_allocations d JOIN og_compat.order_items i USING(order_item_id)
                WHERE i.order_id=o.order_id AND d.reward_entry_id IS NOT NULL AND d.component='SHIPPING')) THEN
            RAISE EXCEPTION 'Voucher/points allocations must exactly conserve checkout discounts' USING ERRCODE='23514';
        END IF;
    ELSIF g.state IN ('READY', 'EXPIRED', 'CANCELLED') THEN
        IF p > 0 AND g.reward_policy_id IS NULL THEN
            RAISE EXCEPTION 'Reward policy must be pinned on checkout group when points are redeemed' USING ERRCODE='23514';
        END IF;
        IF EXISTS(SELECT 1 FROM og_compat.reward_ledger WHERE group_id=gid AND entry_type='DEBIT_CHECKOUT') THEN
            RAISE EXCEPTION 'DEBIT_CHECKOUT must not be recorded before payment confirmation' USING ERRCODE='23514';
        END IF;
    ELSE
        RAISE EXCEPTION 'Invalid checkout group state: %', g.state USING ERRCODE='23514';
    END IF;
END;
$function$;

CREATE OR REPLACE FUNCTION public.og_validate_discount_totals()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'public', 'og_compat'
AS $function$
DECLARE gid UUID;
BEGIN
    IF TG_TABLE_NAME IN ('checkout_groups','orders','checkout_redemptions','reward_ledger') THEN gid:=NEW.group_id;
    ELSE SELECT o.group_id INTO gid FROM og_compat.order_items i JOIN og_compat.orders o USING(order_id) WHERE i.order_item_id=NEW.order_item_id; END IF;
    PERFORM public.og58_validate_discount_totals_for_group(gid);
    RETURN NULL;
END;
$function$;

CREATE OR REPLACE FUNCTION public.og70_row_orders_og_validate_discount_totals(p_new jsonb, p_old jsonb, p_op text, p_phase text, p_depth integer, p_args text[])
 RETURNS jsonb LANGUAGE plpgsql SET search_path=public,og_compat AS $row$
DECLARE NEW og_compat.orders; OLD og_compat.orders;
BEGIN
    NEW:=jsonb_populate_record(NULL::og_compat.orders,coalesce(p_new,'{}'::jsonb));
    OLD:=jsonb_populate_record(NULL::og_compat.orders,coalesce(p_old,'{}'::jsonb));
    PERFORM public.og58_validate_discount_totals_for_group(COALESCE(NEW.group_id, OLD.group_id));
    RETURN NULL;
END;
$row$;

CREATE OR REPLACE FUNCTION public.og70_row_order_items_og_validate_discount_totals(p_new jsonb, p_old jsonb, p_op text, p_phase text, p_depth integer, p_args text[])
 RETURNS jsonb LANGUAGE plpgsql SET search_path=public,og_compat AS $row$
DECLARE NEW og_compat.order_items; OLD og_compat.order_items; gid UUID;
BEGIN
    NEW:=jsonb_populate_record(NULL::og_compat.order_items,coalesce(p_new,'{}'::jsonb));
    OLD:=jsonb_populate_record(NULL::og_compat.order_items,coalesce(p_old,'{}'::jsonb));
    SELECT o.group_id INTO gid FROM og_compat.orders o WHERE o.order_id=COALESCE(NEW.order_id, OLD.order_id);
    PERFORM public.og58_validate_discount_totals_for_group(gid);
    RETURN NULL;
END;
$row$;

CREATE OR REPLACE FUNCTION public.og70_row_checkout_groups_og_validate_discount_totals(p_new jsonb, p_old jsonb, p_op text, p_phase text, p_depth integer, p_args text[])
 RETURNS jsonb LANGUAGE plpgsql SET search_path=public,og_compat AS $row$
DECLARE NEW og_compat.checkout_groups; OLD og_compat.checkout_groups;
BEGIN
    NEW:=jsonb_populate_record(NULL::og_compat.checkout_groups,coalesce(p_new,'{}'::jsonb));
    OLD:=jsonb_populate_record(NULL::og_compat.checkout_groups,coalesce(p_old,'{}'::jsonb));
    PERFORM public.og58_validate_discount_totals_for_group(COALESCE(NEW.group_id, OLD.group_id));
    RETURN NULL;
END;
$row$;

CREATE OR REPLACE FUNCTION public.og70_row_checkout_redemptions_og_validate_discount_totals(p_new jsonb, p_old jsonb, p_op text, p_phase text, p_depth integer, p_args text[])
 RETURNS jsonb LANGUAGE plpgsql SET search_path=public,og_compat AS $row$
DECLARE NEW og_compat.checkout_redemptions; OLD og_compat.checkout_redemptions;
BEGIN
    NEW:=jsonb_populate_record(NULL::og_compat.checkout_redemptions,coalesce(p_new,'{}'::jsonb));
    OLD:=jsonb_populate_record(NULL::og_compat.checkout_redemptions,coalesce(p_old,'{}'::jsonb));
    PERFORM public.og58_validate_discount_totals_for_group(COALESCE(NEW.group_id, OLD.group_id));
    RETURN NULL;
END;
$row$;

CREATE OR REPLACE FUNCTION public.og70_row_reward_ledger_og_validate_discount_totals(p_new jsonb, p_old jsonb, p_op text, p_phase text, p_depth integer, p_args text[])
 RETURNS jsonb LANGUAGE plpgsql SET search_path=public,og_compat AS $row$
DECLARE NEW og_compat.reward_ledger; OLD og_compat.reward_ledger;
BEGIN
    NEW:=jsonb_populate_record(NULL::og_compat.reward_ledger,coalesce(p_new,'{}'::jsonb));
    OLD:=jsonb_populate_record(NULL::og_compat.reward_ledger,coalesce(p_old,'{}'::jsonb));
    PERFORM public.og58_validate_discount_totals_for_group(COALESCE(NEW.group_id, OLD.group_id));
    RETURN NULL;
END;
$row$;

CREATE OR REPLACE FUNCTION public.og70_row_discount_allocations_og_validate_discount_totals(p_new jsonb, p_old jsonb, p_op text, p_phase text, p_depth integer, p_args text[])
 RETURNS jsonb LANGUAGE plpgsql SET search_path=public,og_compat AS $row$
DECLARE NEW og_compat.discount_allocations; OLD og_compat.discount_allocations; gid UUID;
BEGIN
    NEW:=jsonb_populate_record(NULL::og_compat.discount_allocations,coalesce(p_new,'{}'::jsonb));
    OLD:=jsonb_populate_record(NULL::og_compat.discount_allocations,coalesce(p_old,'{}'::jsonb));
    SELECT o.group_id INTO gid FROM og_compat.order_items i JOIN og_compat.orders o USING(order_id)
     WHERE i.order_item_id=COALESCE(NEW.order_item_id, OLD.order_item_id);
    PERFORM public.og58_validate_discount_totals_for_group(gid);
    RETURN NULL;
END;
$row$;
