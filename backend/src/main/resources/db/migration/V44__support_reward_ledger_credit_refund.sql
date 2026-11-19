-- V44__support_reward_ledger_credit_refund.sql (renumbered from V40)
-- Support CREDIT_REFUND in reward_ledger and points discount on orders/order_items per PO D-08 approval.

DO $migration$
DECLARE definition text;
BEGIN
    definition := pg_get_functiondef('public.og70_validate_row(text,jsonb,boolean)'::regprocedure);
    EXECUTE replace(definition, 'FUNCTION public.og70_validate_row(', 'FUNCTION public.og44_validate_existing_row(');
END;
$migration$;

CREATE OR REPLACE FUNCTION public.og70_validate_row(s text, j jsonb, skip_not_valid boolean DEFAULT false)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    r og_compat.reward_ledger;
    o og_compat.orders;
    i og_compat.order_items;
BEGIN
    IF s = 'reward_ledger' AND j->>'entry_type' = 'CREDIT_REFUND' THEN
        r := jsonb_populate_record(NULL::og_compat.reward_ledger, j);
        IF r.entry_id IS NULL OR r.user_id IS NULL OR r.created_at IS NULL
            OR r.points_delta IS NULL OR r.points_delta <= 0
            OR r.order_id IS NULL OR r.group_id IS NOT NULL
            OR r.command_key IS NULL THEN
            RAISE EXCEPTION 'Invalid credit refund entry' USING ERRCODE = '23514';
        END IF;
    ELSIF s = 'reward_ledger' AND j->>'entry_type' = 'DEBIT_CHECKOUT' THEN
        r := jsonb_populate_record(NULL::og_compat.reward_ledger, j);
        IF r.entry_id IS NULL OR r.user_id IS NULL OR r.created_at IS NULL
            OR r.points_delta IS NULL OR r.points_delta >= 0
            OR r.command_key IS NULL THEN
            RAISE EXCEPTION 'Invalid debit checkout entry' USING ERRCODE = '23514';
        END IF;
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
        PERFORM public.og44_validate_existing_row(s, j || jsonb_build_object('points_discount_amount', 0), skip_not_valid);
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
        PERFORM public.og44_validate_existing_row(s, j || jsonb_build_object('points_allocation', 0), skip_not_valid);
    ELSE
        PERFORM public.og44_validate_existing_row(s, j, skip_not_valid);
    END IF;
END;
$$;

