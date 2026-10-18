-- A fully subsidized checkout has no provider charge. Keep its proof separate from cash confirmations.
CREATE TABLE zero_checkout_confirmations (
    confirmation_id UUID PRIMARY KEY,
    group_id UUID NOT NULL UNIQUE REFERENCES checkout_groups(group_id),
    buyer_id BIGINT NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    command_key VARCHAR(150) NOT NULL UNIQUE,
    FOREIGN KEY(group_id,buyer_id) REFERENCES checkout_groups(group_id,buyer_id)
);
CREATE FUNCTION og_confirm_zero_checkout() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE g checkout_groups;
BEGIN
    SELECT * INTO STRICT g FROM checkout_groups WHERE group_id=NEW.group_id FOR UPDATE;
    NEW.confirmed_at:=clock_timestamp();
    IF g.expected_total<>0 OR g.state<>'READY' OR NEW.confirmed_at>=g.payment_due_at
        OR NOT EXISTS(SELECT 1 FROM orders WHERE group_id=g.group_id)
        OR EXISTS(SELECT 1 FROM orders WHERE group_id=g.group_id AND (total_amount<>0 OR status<>'PAYMENT_PENDING'))
        OR EXISTS(SELECT 1 FROM payment_intents WHERE group_id=g.group_id) THEN
        RAISE EXCEPTION 'Zero checkout needs an in-time, unpaid, fully subsidized group' USING ERRCODE='23514';
    END IF;
    UPDATE orders SET status='PAID_HELD',paid_confirmed_at=NEW.confirmed_at,
        seller_accept_due_at=NEW.confirmed_at+INTERVAL '2 days' WHERE group_id=g.group_id;
    UPDATE inventory_reservations r SET state='FUNDED' FROM order_items i JOIN orders o USING(order_id)
        WHERE r.order_item_id=i.order_item_id AND o.group_id=g.group_id AND r.state='HELD';
    UPDATE checkout_groups SET state='PAID' WHERE group_id=g.group_id;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_zero_checkout_confirmation BEFORE INSERT ON zero_checkout_confirmations
    FOR EACH ROW EXECUTE FUNCTION og_confirm_zero_checkout();
CREATE TRIGGER trg_zero_checkout_history BEFORE UPDATE OR DELETE ON zero_checkout_confirmations
    FOR EACH ROW EXECUTE FUNCTION og_reject_history_mutation();

CREATE OR REPLACE FUNCTION og_validate_order_workflow_proof() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE o orders;
BEGIN
    SELECT * INTO STRICT o FROM orders WHERE order_id=NEW.order_id;
    IF o.workflow_model='UC83' AND (
        (o.paid_confirmed_at IS NOT NULL AND NOT EXISTS(SELECT 1 FROM payment_allocations a
            JOIN payment_confirmations c USING(confirmation_id) WHERE a.order_id=o.order_id
                AND c.disposition='ALLOCATABLE' AND c.confirmed_at=o.paid_confirmed_at)
            AND NOT (o.total_amount=0 AND EXISTS(SELECT 1 FROM zero_checkout_confirmations z
                WHERE z.group_id=o.group_id AND z.buyer_id=o.buyer_id AND z.confirmed_at=o.paid_confirmed_at)))
        OR (o.valid_delivered_at IS NOT NULL AND NOT EXISTS(SELECT 1 FROM shipments s WHERE s.order_id=o.order_id
            AND s.leg='OUTBOUND' AND s.valid_delivery_at=o.valid_delivered_at))
        OR (o.early_completed_at IS NOT NULL AND NOT EXISTS(SELECT 1 FROM order_events e WHERE e.order_id=o.order_id
            AND e.actor_id=o.buyer_id AND e.event_type='EARLY_COMPLETION_ACCEPTED'))
        OR (o.seller_accept_due_at IS NOT NULL AND o.seller_accept_due_at<>
            COALESCE(o.paid_confirmed_at,o.created_at)+INTERVAL '2 days')) THEN
        RAISE EXCEPTION 'Order deadlines/payment/delivery/consent need source proof' USING ERRCODE='23514'; END IF;
    RETURN NULL;
END;
$$;
COMMENT ON TABLE zero_checkout_confirmations IS
    'No cash received. Confirm zero checkout atomically; fund subsidy components from its discount sources, never create a provider payment.';
