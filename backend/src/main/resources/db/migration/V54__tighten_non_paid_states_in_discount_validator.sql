-- ============================================================================
-- V50__tighten_non_paid_states_in_discount_validator.sql
-- Module: Commerce / Payment / Reward Ledger (Task 1.2b - Scope Tightening)
-- Business Context:
--   1. V49 differentiated between g.state = 'PAID' and g.state <> 'PAID'.
--   2. In schema, checkout_groups.state is strictly constrained to:
--      ('READY', 'PAID', 'CANCELLED', 'EXPIRED').
--   3. To avoid overly broad ELSE branches that might inadvertently permit
--      unauthorized states to bypass invariant checks, this migration
--      explicitly restricts non-PAID handling to ('READY', 'EXPIRED', 'CANCELLED')
--      and raises an exception for any other state.
--   4. Re-declares public.og_validate_discount_totals() and all 6 wrapper functions.
-- ============================================================================

-- 1. Redefine public.og_validate_discount_totals()
CREATE OR REPLACE FUNCTION public.og_validate_discount_totals()
RETURNS trigger
LANGUAGE plpgsql
SET search_path TO 'public', 'og_compat'
AS $function$
DECLARE
    gid UUID;
    v NUMERIC;
    p NUMERIC;
    g og_compat.checkout_groups;
BEGIN
    IF TG_TABLE_NAME IN ('checkout_groups','orders','checkout_redemptions','reward_ledger') THEN gid:=NEW.group_id;
    ELSE SELECT o.group_id INTO gid FROM og_compat.order_items i JOIN og_compat.orders o USING(order_id) WHERE i.order_item_id=NEW.order_item_id; END IF;
    IF gid IS NULL THEN RETURN NULL; END IF;

    SELECT * INTO g FROM og_compat.checkout_groups WHERE group_id=gid FOR UPDATE;
    IF NOT FOUND THEN RETURN NULL; END IF;

    SELECT COALESCE(sum(voucher_discount_amount+shipping_discount_amount),0),COALESCE(sum(points_discount_amount),0)
        INTO v,p FROM og_compat.orders WHERE group_id=gid;

    -- Voucher conservation checks (common across states)
    IF v<>COALESCE((SELECT discount_amount FROM og_compat.checkout_redemptions WHERE group_id=gid),0)
        OR EXISTS(SELECT 1 FROM og_compat.order_items i JOIN og_compat.orders o USING(order_id) WHERE o.group_id=gid AND (
            i.voucher_allocation<>(SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations d WHERE d.order_item_id=i.order_item_id
                AND redemption_id IS NOT NULL AND component='GOODS')))
        OR EXISTS(SELECT 1 FROM og_compat.orders o WHERE o.group_id=gid AND o.shipping_discount_amount<>(
            SELECT COALESCE(sum(d.amount),0) FROM og_compat.discount_allocations d JOIN og_compat.order_items i USING(order_item_id)
            WHERE i.order_id=o.order_id AND d.component='SHIPPING'))
        OR EXISTS(SELECT 1 FROM og_compat.checkout_redemptions c WHERE c.group_id=gid AND c.discount_amount<>(
            SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations WHERE redemption_id=c.redemption_id)) THEN
        RAISE EXCEPTION 'Voucher/points allocations must exactly conserve checkout discounts' USING ERRCODE='23514';
    END IF;

    -- Differentiate Coin lifecycle strictly by valid states
    IF g.state = 'PAID' THEN
        -- T2 PAID: Must have exactly one Group-level DEBIT_CHECKOUT matching points discount, and discount allocations matching points allocation
        IF p<>COALESCE((SELECT -l.points_delta*r.point_value FROM og_compat.reward_ledger l JOIN og_compat.reward_policies r USING(policy_id)
                WHERE l.group_id=gid AND l.entry_type='DEBIT_CHECKOUT'),0)
            OR EXISTS(SELECT 1 FROM og_compat.order_items i JOIN og_compat.orders o USING(order_id) WHERE o.group_id=gid AND (
                i.points_allocation<>(SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations d WHERE d.order_item_id=i.order_item_id
                    AND reward_entry_id IS NOT NULL))) THEN
            RAISE EXCEPTION 'Voucher/points allocations must exactly conserve checkout discounts' USING ERRCODE='23514';
        END IF;
    ELSIF g.state IN ('READY', 'EXPIRED', 'CANCELLED') THEN
        -- Non-PAID states: MUST NOT have DEBIT_CHECKOUT yet (DEC-10.4 / DEC-10-LATE-PAYMENT)
        IF p > 0 AND g.reward_policy_id IS NULL THEN
            RAISE EXCEPTION 'Reward policy must be pinned on checkout group when points are redeemed' USING ERRCODE='23514';
        END IF;
        IF EXISTS(SELECT 1 FROM og_compat.reward_ledger WHERE group_id=gid AND entry_type='DEBIT_CHECKOUT') THEN
            RAISE EXCEPTION 'DEBIT_CHECKOUT must not be recorded before payment confirmation' USING ERRCODE='23514';
        END IF;
    ELSE
        RAISE EXCEPTION 'Invalid checkout group state: %', g.state USING ERRCODE='23514';
    END IF;

    RETURN NULL;
END;
$function$;

-- 2. Redefine public.og70_row_orders_og_validate_discount_totals
CREATE OR REPLACE FUNCTION public.og70_row_orders_og_validate_discount_totals(p_new jsonb, p_old jsonb, p_op text, p_phase text, p_depth integer, p_args text[])
RETURNS jsonb LANGUAGE plpgsql SET search_path=public,og_compat AS $row$
DECLARE
    NEW og_compat.orders; OLD og_compat.orders;
    TG_OP text:=p_op; TG_WHEN text:=p_phase; TG_TABLE_NAME text:='orders';
    TG_TABLE_SCHEMA text:='og_compat'; TG_ARGV text[]:=p_args; TG_NARGS integer:=cardinality(p_args);
    gid UUID; v NUMERIC; p NUMERIC; g og_compat.checkout_groups;
BEGIN
    NEW:=jsonb_populate_record(NULL::og_compat.orders,coalesce(p_new,'{}'::jsonb));
    OLD:=jsonb_populate_record(NULL::og_compat.orders,coalesce(p_old,'{}'::jsonb));
    gid:=COALESCE(NEW.group_id, OLD.group_id);
    IF gid IS NULL THEN RETURN NULL; END IF;

    SELECT * INTO g FROM og_compat.checkout_groups WHERE group_id=gid FOR UPDATE;
    IF NOT FOUND THEN RETURN NULL; END IF;

    SELECT COALESCE(sum(voucher_discount_amount+shipping_discount_amount),0),COALESCE(sum(points_discount_amount),0)
        INTO v,p FROM og_compat.orders WHERE group_id=gid;

    IF v<>COALESCE((SELECT discount_amount FROM og_compat.checkout_redemptions WHERE group_id=gid),0)
        OR EXISTS(SELECT 1 FROM og_compat.order_items i JOIN og_compat.orders o USING(order_id) WHERE o.group_id=gid AND (
            i.voucher_allocation<>(SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations d WHERE d.order_item_id=i.order_item_id
                AND redemption_id IS NOT NULL AND component='GOODS')))
        OR EXISTS(SELECT 1 FROM og_compat.orders o WHERE o.group_id=gid AND o.shipping_discount_amount<>(
            SELECT COALESCE(sum(d.amount),0) FROM og_compat.discount_allocations d JOIN og_compat.order_items i USING(order_item_id)
            WHERE i.order_id=o.order_id AND d.component='SHIPPING'))
        OR EXISTS(SELECT 1 FROM og_compat.checkout_redemptions c WHERE c.group_id=gid AND c.discount_amount<>(
            SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations WHERE redemption_id=c.redemption_id)) THEN
        RAISE EXCEPTION 'Voucher/points allocations must exactly conserve checkout discounts' USING ERRCODE='23514';
    END IF;

    IF g.state = 'PAID' THEN
        IF p<>COALESCE((SELECT -l.points_delta*r.point_value FROM og_compat.reward_ledger l JOIN og_compat.reward_policies r USING(policy_id)
                WHERE l.group_id=gid AND l.entry_type='DEBIT_CHECKOUT'),0)
            OR EXISTS(SELECT 1 FROM og_compat.order_items i JOIN og_compat.orders o USING(order_id) WHERE o.group_id=gid AND (
                i.points_allocation<>(SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations d WHERE d.order_item_id=i.order_item_id
                    AND reward_entry_id IS NOT NULL))) THEN
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

    RETURN NULL;
END;
$row$;

-- 3. Redefine public.og70_row_order_items_og_validate_discount_totals
CREATE OR REPLACE FUNCTION public.og70_row_order_items_og_validate_discount_totals(p_new jsonb, p_old jsonb, p_op text, p_phase text, p_depth integer, p_args text[])
RETURNS jsonb LANGUAGE plpgsql SET search_path=public,og_compat AS $row$
DECLARE
    NEW og_compat.order_items; OLD og_compat.order_items;
    TG_OP text:=p_op; TG_WHEN text:=p_phase; TG_TABLE_NAME text:='order_items';
    TG_TABLE_SCHEMA text:='og_compat'; TG_ARGV text[]:=p_args; TG_NARGS integer:=cardinality(p_args);
    gid UUID; v NUMERIC; p NUMERIC; g og_compat.checkout_groups;
BEGIN
    NEW:=jsonb_populate_record(NULL::og_compat.order_items,coalesce(p_new,'{}'::jsonb));
    OLD:=jsonb_populate_record(NULL::og_compat.order_items,coalesce(p_old,'{}'::jsonb));
    SELECT o.group_id INTO gid FROM og_compat.orders o WHERE o.order_id=COALESCE(NEW.order_id, OLD.order_id);
    IF gid IS NULL THEN RETURN NULL; END IF;

    SELECT * INTO g FROM og_compat.checkout_groups WHERE group_id=gid FOR UPDATE;
    IF NOT FOUND THEN RETURN NULL; END IF;

    SELECT COALESCE(sum(voucher_discount_amount+shipping_discount_amount),0),COALESCE(sum(points_discount_amount),0)
        INTO v,p FROM og_compat.orders WHERE group_id=gid;

    IF v<>COALESCE((SELECT discount_amount FROM og_compat.checkout_redemptions WHERE group_id=gid),0)
        OR EXISTS(SELECT 1 FROM og_compat.order_items i JOIN og_compat.orders o USING(order_id) WHERE o.group_id=gid AND (
            i.voucher_allocation<>(SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations d WHERE d.order_item_id=i.order_item_id
                AND redemption_id IS NOT NULL AND component='GOODS')))
        OR EXISTS(SELECT 1 FROM og_compat.orders o WHERE o.group_id=gid AND o.shipping_discount_amount<>(
            SELECT COALESCE(sum(d.amount),0) FROM og_compat.discount_allocations d JOIN og_compat.order_items i USING(order_item_id)
            WHERE i.order_id=o.order_id AND d.component='SHIPPING'))
        OR EXISTS(SELECT 1 FROM og_compat.checkout_redemptions c WHERE c.group_id=gid AND c.discount_amount<>(
            SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations WHERE redemption_id=c.redemption_id)) THEN
        RAISE EXCEPTION 'Voucher/points allocations must exactly conserve checkout discounts' USING ERRCODE='23514';
    END IF;

    IF g.state = 'PAID' THEN
        IF p<>COALESCE((SELECT -l.points_delta*r.point_value FROM og_compat.reward_ledger l JOIN og_compat.reward_policies r USING(policy_id)
                WHERE l.group_id=gid AND l.entry_type='DEBIT_CHECKOUT'),0)
            OR EXISTS(SELECT 1 FROM og_compat.order_items i JOIN og_compat.orders o USING(order_id) WHERE o.group_id=gid AND (
                i.points_allocation<>(SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations d WHERE d.order_item_id=i.order_item_id
                    AND reward_entry_id IS NOT NULL))) THEN
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

    RETURN NULL;
END;
$row$;

-- 4. Redefine public.og70_row_reward_ledger_og_validate_discount_totals
CREATE OR REPLACE FUNCTION public.og70_row_reward_ledger_og_validate_discount_totals(p_new jsonb, p_old jsonb, p_op text, p_phase text, p_depth integer, p_args text[])
RETURNS jsonb LANGUAGE plpgsql SET search_path=public,og_compat AS $row$
DECLARE
    NEW og_compat.reward_ledger; OLD og_compat.reward_ledger;
    TG_OP text:=p_op; TG_WHEN text:=p_phase; TG_TABLE_NAME text:='reward_ledger';
    TG_TABLE_SCHEMA text:='og_compat'; TG_ARGV text[]:=p_args; TG_NARGS integer:=cardinality(p_args);
    gid UUID; v NUMERIC; p NUMERIC; g og_compat.checkout_groups;
BEGIN
    NEW:=jsonb_populate_record(NULL::og_compat.reward_ledger,coalesce(p_new,'{}'::jsonb));
    OLD:=jsonb_populate_record(NULL::og_compat.reward_ledger,coalesce(p_old,'{}'::jsonb));
    gid:=COALESCE(NEW.group_id, OLD.group_id);
    IF gid IS NULL THEN RETURN NULL; END IF;

    SELECT * INTO g FROM og_compat.checkout_groups WHERE group_id=gid FOR UPDATE;
    IF NOT FOUND THEN RETURN NULL; END IF;

    SELECT COALESCE(sum(voucher_discount_amount+shipping_discount_amount),0),COALESCE(sum(points_discount_amount),0)
        INTO v,p FROM og_compat.orders WHERE group_id=gid;

    IF v<>COALESCE((SELECT discount_amount FROM og_compat.checkout_redemptions WHERE group_id=gid),0)
        OR EXISTS(SELECT 1 FROM og_compat.order_items i JOIN og_compat.orders o USING(order_id) WHERE o.group_id=gid AND (
            i.voucher_allocation<>(SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations d WHERE d.order_item_id=i.order_item_id
                AND redemption_id IS NOT NULL AND component='GOODS')))
        OR EXISTS(SELECT 1 FROM og_compat.orders o WHERE o.group_id=gid AND o.shipping_discount_amount<>(
            SELECT COALESCE(sum(d.amount),0) FROM og_compat.discount_allocations d JOIN og_compat.order_items i USING(order_item_id)
            WHERE i.order_id=o.order_id AND d.component='SHIPPING'))
        OR EXISTS(SELECT 1 FROM og_compat.checkout_redemptions c WHERE c.group_id=gid AND c.discount_amount<>(
            SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations WHERE redemption_id=c.redemption_id)) THEN
        RAISE EXCEPTION 'Voucher/points allocations must exactly conserve checkout discounts' USING ERRCODE='23514';
    END IF;

    IF g.state = 'PAID' THEN
        IF p<>COALESCE((SELECT -l.points_delta*r.point_value FROM og_compat.reward_ledger l JOIN og_compat.reward_policies r USING(policy_id)
                WHERE l.group_id=gid AND l.entry_type='DEBIT_CHECKOUT'),0)
            OR EXISTS(SELECT 1 FROM og_compat.order_items i JOIN og_compat.orders o USING(order_id) WHERE o.group_id=gid AND (
                i.points_allocation<>(SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations d WHERE d.order_item_id=i.order_item_id
                    AND reward_entry_id IS NOT NULL))) THEN
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

    RETURN NULL;
END;
$row$;

-- 5. Redefine public.og70_row_checkout_groups_og_validate_discount_totals
CREATE OR REPLACE FUNCTION public.og70_row_checkout_groups_og_validate_discount_totals(p_new jsonb, p_old jsonb, p_op text, p_phase text, p_depth integer, p_args text[])
RETURNS jsonb LANGUAGE plpgsql SET search_path=public,og_compat AS $row$
DECLARE
    NEW og_compat.checkout_groups; OLD og_compat.checkout_groups;
    TG_OP text:=p_op; TG_WHEN text:=p_phase; TG_TABLE_NAME text:='checkout_groups';
    TG_TABLE_SCHEMA text:='og_compat'; TG_ARGV text[]:=p_args; TG_NARGS integer:=cardinality(p_args);
    gid UUID; v NUMERIC; p NUMERIC; g og_compat.checkout_groups;
BEGIN
    NEW:=jsonb_populate_record(NULL::og_compat.checkout_groups,coalesce(p_new,'{}'::jsonb));
    OLD:=jsonb_populate_record(NULL::og_compat.checkout_groups,coalesce(p_old,'{}'::jsonb));
    gid:=COALESCE(NEW.group_id, OLD.group_id);
    IF gid IS NULL THEN RETURN NULL; END IF;

    SELECT * INTO g FROM og_compat.checkout_groups WHERE group_id=gid FOR UPDATE;
    IF NOT FOUND THEN RETURN NULL; END IF;

    SELECT COALESCE(sum(voucher_discount_amount+shipping_discount_amount),0),COALESCE(sum(points_discount_amount),0)
        INTO v,p FROM og_compat.orders WHERE group_id=gid;

    IF v<>COALESCE((SELECT discount_amount FROM og_compat.checkout_redemptions WHERE group_id=gid),0)
        OR EXISTS(SELECT 1 FROM og_compat.order_items i JOIN og_compat.orders o USING(order_id) WHERE o.group_id=gid AND (
            i.voucher_allocation<>(SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations d WHERE d.order_item_id=i.order_item_id
                AND redemption_id IS NOT NULL AND component='GOODS')))
        OR EXISTS(SELECT 1 FROM og_compat.orders o WHERE o.group_id=gid AND o.shipping_discount_amount<>(
            SELECT COALESCE(sum(d.amount),0) FROM og_compat.discount_allocations d JOIN og_compat.order_items i USING(order_item_id)
            WHERE i.order_id=o.order_id AND d.component='SHIPPING'))
        OR EXISTS(SELECT 1 FROM og_compat.checkout_redemptions c WHERE c.group_id=gid AND c.discount_amount<>(
            SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations WHERE redemption_id=c.redemption_id)) THEN
        RAISE EXCEPTION 'Voucher/points allocations must exactly conserve checkout discounts' USING ERRCODE='23514';
    END IF;

    IF g.state = 'PAID' THEN
        IF p<>COALESCE((SELECT -l.points_delta*r.point_value FROM og_compat.reward_ledger l JOIN og_compat.reward_policies r USING(policy_id)
                WHERE l.group_id=gid AND l.entry_type='DEBIT_CHECKOUT'),0)
            OR EXISTS(SELECT 1 FROM og_compat.order_items i JOIN og_compat.orders o USING(order_id) WHERE o.group_id=gid AND (
                i.points_allocation<>(SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations d WHERE d.order_item_id=i.order_item_id
                    AND reward_entry_id IS NOT NULL))) THEN
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

    RETURN NULL;
END;
$row$;

-- 6. Redefine public.og70_row_checkout_redemptions_og_validate_discount_totals
CREATE OR REPLACE FUNCTION public.og70_row_checkout_redemptions_og_validate_discount_totals(p_new jsonb, p_old jsonb, p_op text, p_phase text, p_depth integer, p_args text[])
RETURNS jsonb LANGUAGE plpgsql SET search_path=public,og_compat AS $row$
DECLARE
    NEW og_compat.checkout_redemptions; OLD og_compat.checkout_redemptions;
    TG_OP text:=p_op; TG_WHEN text:=p_phase; TG_TABLE_NAME text:='checkout_redemptions';
    TG_TABLE_SCHEMA text:='og_compat'; TG_ARGV text[]:=p_args; TG_NARGS integer:=cardinality(p_args);
    gid UUID; v NUMERIC; p NUMERIC; g og_compat.checkout_groups;
BEGIN
    NEW:=jsonb_populate_record(NULL::og_compat.checkout_redemptions,coalesce(p_new,'{}'::jsonb));
    OLD:=jsonb_populate_record(NULL::og_compat.checkout_redemptions,coalesce(p_old,'{}'::jsonb));
    gid:=COALESCE(NEW.group_id, OLD.group_id);
    IF gid IS NULL THEN RETURN NULL; END IF;

    SELECT * INTO g FROM og_compat.checkout_groups WHERE group_id=gid FOR UPDATE;
    IF NOT FOUND THEN RETURN NULL; END IF;

    SELECT COALESCE(sum(voucher_discount_amount+shipping_discount_amount),0),COALESCE(sum(points_discount_amount),0)
        INTO v,p FROM og_compat.orders WHERE group_id=gid;

    IF v<>COALESCE((SELECT discount_amount FROM og_compat.checkout_redemptions WHERE group_id=gid),0)
        OR EXISTS(SELECT 1 FROM og_compat.order_items i JOIN og_compat.orders o USING(order_id) WHERE o.group_id=gid AND (
            i.voucher_allocation<>(SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations d WHERE d.order_item_id=i.order_item_id
                AND redemption_id IS NOT NULL AND component='GOODS')))
        OR EXISTS(SELECT 1 FROM og_compat.orders o WHERE o.group_id=gid AND o.shipping_discount_amount<>(
            SELECT COALESCE(sum(d.amount),0) FROM og_compat.discount_allocations d JOIN og_compat.order_items i USING(order_item_id)
            WHERE i.order_id=o.order_id AND d.component='SHIPPING'))
        OR EXISTS(SELECT 1 FROM og_compat.checkout_redemptions c WHERE c.group_id=gid AND c.discount_amount<>(
            SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations WHERE redemption_id=c.redemption_id)) THEN
        RAISE EXCEPTION 'Voucher/points allocations must exactly conserve checkout discounts' USING ERRCODE='23514';
    END IF;

    IF g.state = 'PAID' THEN
        IF p<>COALESCE((SELECT -l.points_delta*r.point_value FROM og_compat.reward_ledger l JOIN og_compat.reward_policies r USING(policy_id)
                WHERE l.group_id=gid AND l.entry_type='DEBIT_CHECKOUT'),0)
            OR EXISTS(SELECT 1 FROM og_compat.order_items i JOIN og_compat.orders o USING(order_id) WHERE o.group_id=gid AND (
                i.points_allocation<>(SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations d WHERE d.order_item_id=i.order_item_id
                    AND reward_entry_id IS NOT NULL))) THEN
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

    RETURN NULL;
END;
$row$;

-- 7. Redefine public.og70_row_discount_allocations_og_validate_discount_totals
CREATE OR REPLACE FUNCTION public.og70_row_discount_allocations_og_validate_discount_totals(p_new jsonb, p_old jsonb, p_op text, p_phase text, p_depth integer, p_args text[])
RETURNS jsonb LANGUAGE plpgsql SET search_path=public,og_compat AS $row$
DECLARE
    NEW og_compat.discount_allocations; OLD og_compat.discount_allocations;
    TG_OP text:=p_op; TG_WHEN text:=p_phase; TG_TABLE_NAME text:='discount_allocations';
    TG_TABLE_SCHEMA text:='og_compat'; TG_ARGV text[]:=p_args; TG_NARGS integer:=cardinality(p_args);
    gid UUID; v NUMERIC; p NUMERIC; g og_compat.checkout_groups;
BEGIN
    NEW:=jsonb_populate_record(NULL::og_compat.discount_allocations,coalesce(p_new,'{}'::jsonb));
    OLD:=jsonb_populate_record(NULL::og_compat.discount_allocations,coalesce(p_old,'{}'::jsonb));
    SELECT o.group_id INTO gid FROM og_compat.orders o JOIN og_compat.order_items i USING(order_id) WHERE i.order_item_id=COALESCE(NEW.order_item_id, OLD.order_item_id);
    IF gid IS NULL THEN RETURN NULL; END IF;

    SELECT * INTO g FROM og_compat.checkout_groups WHERE group_id=gid FOR UPDATE;
    IF NOT FOUND THEN RETURN NULL; END IF;

    SELECT COALESCE(sum(voucher_discount_amount+shipping_discount_amount),0),COALESCE(sum(points_discount_amount),0)
        INTO v,p FROM og_compat.orders WHERE group_id=gid;

    IF v<>COALESCE((SELECT discount_amount FROM og_compat.checkout_redemptions WHERE group_id=gid),0)
        OR EXISTS(SELECT 1 FROM og_compat.order_items i JOIN og_compat.orders o USING(order_id) WHERE o.group_id=gid AND (
            i.voucher_allocation<>(SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations d WHERE d.order_item_id=i.order_item_id
                AND redemption_id IS NOT NULL AND component='GOODS')))
        OR EXISTS(SELECT 1 FROM og_compat.orders o WHERE o.group_id=gid AND o.shipping_discount_amount<>(
            SELECT COALESCE(sum(d.amount),0) FROM og_compat.discount_allocations d JOIN og_compat.order_items i USING(order_item_id)
            WHERE i.order_id=o.order_id AND d.component='SHIPPING'))
        OR EXISTS(SELECT 1 FROM og_compat.checkout_redemptions c WHERE c.group_id=gid AND c.discount_amount<>(
            SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations WHERE redemption_id=c.redemption_id)) THEN
        RAISE EXCEPTION 'Voucher/points allocations must exactly conserve checkout discounts' USING ERRCODE='23514';
    END IF;

    IF g.state = 'PAID' THEN
        IF p<>COALESCE((SELECT -l.points_delta*r.point_value FROM og_compat.reward_ledger l JOIN og_compat.reward_policies r USING(policy_id)
                WHERE l.group_id=gid AND l.entry_type='DEBIT_CHECKOUT'),0)
            OR EXISTS(SELECT 1 FROM og_compat.order_items i JOIN og_compat.orders o USING(order_id) WHERE o.group_id=gid AND (
                i.points_allocation<>(SELECT COALESCE(sum(amount),0) FROM og_compat.discount_allocations d WHERE d.order_item_id=i.order_item_id
                    AND reward_entry_id IS NOT NULL))) THEN
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

    RETURN NULL;
END;
$row$;

