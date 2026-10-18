-- G05/G06. Legacy IDs and money snapshots remain unchanged.
ALTER TABLE cart_items DROP CONSTRAINT ck_cart_items_mvp_quantity;
ALTER TABLE cart_items ALTER COLUMN quantity TYPE INTEGER;
ALTER TABLE cart_items ADD CONSTRAINT ck_cart_quantity_positive CHECK(quantity>0);
ALTER TABLE order_items DROP CONSTRAINT ck_order_items_mvp_quantity;
ALTER TABLE order_items ALTER COLUMN quantity TYPE INTEGER;
ALTER TABLE order_items ADD CONSTRAINT ck_order_quantity_positive CHECK(quantity>0);

CREATE TABLE checkout_groups (
    group_id UUID PRIMARY KEY,
    buyer_id BIGINT NOT NULL REFERENCES users(user_id),
    command_key VARCHAR(150) NOT NULL,
    request_digest CHAR(64) NOT NULL CHECK (request_digest ~ '^[0-9a-f]{64}$'),
    address_snapshot JSONB NOT NULL CHECK (jsonb_typeof(address_snapshot)='object'),
    delivery_method VARCHAR(20) NOT NULL CHECK (delivery_method IN ('CARRIER','SELF_PICKUP','SELF_DELIVERY')),
    carrier VARCHAR(100),
    payment_method VARCHAR(30) NOT NULL CHECK (payment_method IN ('VNPAY','COD_MOCK','BANK_TRANSFER_MOCK','E_WALLET_MOCK')),
    expected_total NUMERIC(19,2) NOT NULL CHECK (expected_total>=0),
    state VARCHAR(20) NOT NULL DEFAULT 'READY' CHECK (state IN ('READY','PAID','CANCELLED','EXPIRED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payment_due_at TIMESTAMPTZ NOT NULL,
    UNIQUE(buyer_id,command_key),
    UNIQUE(group_id,buyer_id),
    CHECK (payment_due_at=created_at+INTERVAL '1 hour'),
    CHECK (delivery_method<>'CARRIER' OR NULLIF(btrim(carrier),'') IS NOT NULL)
);
CREATE INDEX ix_checkout_group_due ON checkout_groups(payment_due_at) WHERE state='READY';

ALTER TABLE orders
    ADD COLUMN workflow_model VARCHAR(20) NOT NULL DEFAULT 'LEGACY_V14'
        CHECK (workflow_model IN ('LEGACY_V14','UC83')),
    ADD COLUMN group_id UUID,
    ADD COLUMN return_allowed BOOLEAN,
    ADD COLUMN delivery_method VARCHAR(20),
    ADD COLUMN carrier_snapshot VARCHAR(100),
    ADD COLUMN handover_day DATE,
    ADD COLUMN points_discount_amount NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK(points_discount_amount>=0),
    ADD COLUMN paid_confirmed_at TIMESTAMPTZ,
    ADD COLUMN seller_accept_due_at TIMESTAMPTZ,
    ADD COLUMN valid_delivered_at TIMESTAMPTZ,
    ADD COLUMN received_at TIMESTAMPTZ,
    ADD COLUMN return_due_at TIMESTAMPTZ,
    ADD COLUMN early_completed_at TIMESTAMPTZ,
    ADD COLUMN early_completion_warning_version VARCHAR(50),
    ADD CONSTRAINT fk_order_checkout_group FOREIGN KEY(group_id,buyer_id) REFERENCES checkout_groups(group_id,buyer_id),
    ADD CONSTRAINT uq_order_group UNIQUE(order_id,group_id),
    ADD CONSTRAINT uq_order_buyer UNIQUE(order_id,buyer_id),
    ADD CONSTRAINT uq_order_seller UNIQUE(order_id,seller_id),
    ADD CONSTRAINT ck_order_uc83_snapshot CHECK (workflow_model='LEGACY_V14' OR (
        group_id IS NOT NULL AND group_id=checkout_group_id AND return_allowed IS NOT NULL
        AND delivery_method IN ('CARRIER','SELF_PICKUP','SELF_DELIVERY') AND handover_day IS NOT NULL)),
    ADD CONSTRAINT ck_order_early_completion CHECK (early_completed_at IS NULL OR (
        valid_delivered_at IS NOT NULL AND early_completed_at>=valid_delivered_at
        AND early_completion_warning_version IS NOT NULL)),
    ADD CONSTRAINT ck_order_return_window CHECK (return_due_at IS NULL OR (
        return_allowed AND valid_delivered_at IS NOT NULL
        AND return_due_at=valid_delivered_at+INTERVAL '3 days'));
ALTER TABLE orders DROP CONSTRAINT ck_orders_amounts;
ALTER TABLE orders ADD CONSTRAINT ck_orders_amounts CHECK (
    subtotal>0 AND shipping_fee>=0 AND voucher_discount_amount>=0
    AND shipping_discount_amount BETWEEN 0 AND shipping_fee
    AND buyer_system_fee>=0 AND seller_system_fee BETWEEN 0 AND subtotal
    AND (
        (workflow_model='LEGACY_V14' AND points_discount_amount=0
         AND voucher_discount_amount+shipping_discount_amount<subtotal+buyer_system_fee+shipping_fee
         AND seller_proceeds=subtotal-CASE WHEN sponsor_type='SELLER' THEN voucher_discount_amount ELSE 0 END-seller_system_fee
         AND total_amount=subtotal+buyer_system_fee+shipping_fee-voucher_discount_amount-shipping_discount_amount)
        OR
        (workflow_model='UC83' AND buyer_system_fee=0 AND seller_system_fee=0 AND sponsor_type='PLATFORM'
         AND voucher_discount_amount+points_discount_amount<=subtotal AND seller_proceeds=subtotal
         AND total_amount=subtotal+shipping_fee-voucher_discount_amount-shipping_discount_amount-points_discount_amount
         AND total_amount>=0 AND (delivery_method='CARRIER' OR shipping_fee=0))
    ));

ALTER TABLE order_items
    ADD COLUMN workflow_model VARCHAR(20) NOT NULL DEFAULT 'LEGACY_V14'
        CHECK (workflow_model IN ('LEGACY_V14','UC83')),
    ADD COLUMN product_revision_id BIGINT,
    ADD COLUMN return_allowed BOOLEAN,
    ADD COLUMN voucher_allocation NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK(voucher_allocation>=0),
    ADD COLUMN points_allocation NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK(points_allocation>=0),
    ADD CONSTRAINT fk_item_product_revision FOREIGN KEY(product_revision_id,product_id)
        REFERENCES product_revisions(revision_id,product_id),
    ADD CONSTRAINT uq_item_product UNIQUE(order_item_id,product_id),
    ADD CONSTRAINT uq_item_order UNIQUE(order_item_id,order_id),
    ADD CONSTRAINT ck_item_uc83_revision CHECK (workflow_model='LEGACY_V14' OR
        (product_revision_id IS NOT NULL AND return_allowed IS NOT NULL));
ALTER TABLE order_items ALTER COLUMN fee_policy_id DROP NOT NULL;
ALTER TABLE order_items DROP CONSTRAINT ck_order_items_prices;
ALTER TABLE order_items ADD CONSTRAINT ck_order_items_prices CHECK (
    listed_price>0 AND agreed_price>0 AND buyer_system_fee>=0 AND seller_system_fee>=0
    AND seller_system_fee<=agreed_price*quantity AND (
      (workflow_model='LEGACY_V14' AND fee_policy_id IS NOT NULL AND voucher_allocation=0 AND points_allocation=0
       AND buyer_line_total=agreed_price*quantity+buyer_system_fee
       AND seller_line_proceeds=agreed_price*quantity-seller_system_fee)
      OR (workflow_model='UC83' AND fee_policy_id IS NULL AND buyer_system_fee=0 AND seller_system_fee=0
       AND voucher_allocation+points_allocation<=agreed_price*quantity
       AND buyer_line_total=agreed_price*quantity-voucher_allocation-points_allocation
       AND seller_line_proceeds=agreed_price*quantity)));

CREATE TABLE inventory_reservations (
    reservation_id UUID PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(product_id),
    order_item_id BIGINT NOT NULL UNIQUE,
    quantity INTEGER NOT NULL CHECK(quantity>0),
    state VARCHAR(15) NOT NULL DEFAULT 'HELD' CHECK (state IN ('HELD','FUNDED','CONSUMED','RELEASED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    command_key VARCHAR(150) NOT NULL UNIQUE,
    FOREIGN KEY(order_item_id,product_id) REFERENCES order_items(order_item_id,product_id),
    CHECK(expires_at>created_at)
);
CREATE INDEX ix_inventory_expiry ON inventory_reservations(expires_at) WHERE state='HELD';
CREATE FUNCTION og_guard_inventory_reservation() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE p products; o orders; i order_items; delta INTEGER;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Reservation history is retained' USING ERRCODE='23514'; END IF;
    SELECT * INTO STRICT p FROM products WHERE product_id=NEW.product_id FOR UPDATE;
    SELECT * INTO STRICT i FROM order_items WHERE order_item_id=NEW.order_item_id;
    SELECT * INTO STRICT o FROM orders WHERE order_id=i.order_id;
    IF p.workflow_model<>'UC83' OR i.workflow_model<>'UC83' OR o.workflow_model<>'UC83'
       OR NEW.quantity<>i.quantity OR NEW.expires_at<>o.payment_due_at THEN
        RAISE EXCEPTION 'Reservation must match UC83 item and original deadline' USING ERRCODE='23514';
    END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.state<>'HELD' OR p.status<>'ACTIVE' OR p.public_revision_id IS DISTINCT FROM i.product_revision_id OR p.quantity_available<NEW.quantity
           OR p.deleted_at IS NOT NULL OR clock_timestamp()>=NEW.expires_at THEN
            RAISE EXCEPTION 'Product quantity unavailable' USING ERRCODE='23514';
        END IF;
        UPDATE products SET quantity_held=quantity_held+NEW.quantity WHERE product_id=p.product_id;
    ELSE
        IF ROW(NEW.product_id,NEW.order_item_id,NEW.quantity,NEW.created_at,NEW.expires_at,NEW.command_key)
           IS DISTINCT FROM ROW(OLD.product_id,OLD.order_item_id,OLD.quantity,OLD.created_at,OLD.expires_at,OLD.command_key) THEN
            RAISE EXCEPTION 'Reservation snapshot is immutable' USING ERRCODE='23514';
        END IF;
        IF NEW.state=OLD.state THEN RETURN NEW; END IF;
        IF NOT ((OLD.state='HELD' AND NEW.state IN ('FUNDED','RELEASED'))
            OR (OLD.state='FUNDED' AND NEW.state IN ('CONSUMED','RELEASED'))) THEN
            RAISE EXCEPTION 'Invalid reservation transition' USING ERRCODE='23514';
        END IF;
        IF NEW.state='FUNDED' AND (o.paid_confirmed_at IS NULL OR o.paid_confirmed_at>OLD.expires_at
            OR o.status='CANCELLED') THEN
            RAISE EXCEPTION 'Late payment cannot fund cancelled/expired reservation' USING ERRCODE='23514';
        END IF;
        IF NEW.state='CONSUMED' AND (o.status<>'SELLER_CONFIRMED' OR o.seller_accept_due_at IS NULL
            OR clock_timestamp()>o.seller_accept_due_at) THEN
            RAISE EXCEPTION 'Seller acceptance is not eligible' USING ERRCODE='23514';
        END IF;
        IF NEW.state IN ('RELEASED','CONSUMED') THEN
            delta := CASE WHEN NEW.state='CONSUMED' THEN NEW.quantity ELSE 0 END;
            UPDATE products SET quantity_held=quantity_held-NEW.quantity,quantity_sold=quantity_sold+delta,
                status=CASE WHEN quantity_sold+delta=quantity_total THEN 'SOLD' ELSE status END
                WHERE product_id=p.product_id;
        END IF;
        NEW.changed_at:=clock_timestamp();
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_inventory_reservation_guard BEFORE INSERT OR UPDATE OR DELETE ON inventory_reservations
    FOR EACH ROW EXECUTE FUNCTION og_guard_inventory_reservation();

CREATE FUNCTION og_guard_checkout_item() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE o orders; p products; r product_revisions;
BEGIN
    IF TG_OP='DELETE' THEN
        IF OLD.workflow_model='UC83' THEN RAISE EXCEPTION 'Order item history retained' USING ERRCODE='23514'; END IF;
        RETURN OLD;
    END IF;
    SELECT * INTO STRICT o FROM orders WHERE order_id=NEW.order_id;
    IF NEW.workflow_model<>o.workflow_model THEN RAISE EXCEPTION 'Item workflow mismatch' USING ERRCODE='23514'; END IF;
    IF NEW.workflow_model='UC83' THEN
        SELECT * INTO STRICT p FROM products WHERE product_id=NEW.product_id;
        SELECT * INTO STRICT r FROM product_revisions WHERE revision_id=NEW.product_revision_id;
        IF p.seller_id<>o.seller_id OR NEW.return_allowed IS DISTINCT FROM o.return_allowed
           OR NEW.return_allowed IS DISTINCT FROM r.return_allowed OR NEW.listed_price<>r.listed_unit_price
           OR NOT (r.delivery_options ? o.delivery_method) THEN
            RAISE EXCEPTION 'Seller/return/delivery/price snapshot mismatch' USING ERRCODE='23514';
        END IF;
        IF NEW.accepted_offer_id IS NOT NULL AND NOT EXISTS(
            SELECT 1 FROM offers offer JOIN conversations c USING(conversation_id)
            WHERE offer.offer_id=NEW.accepted_offer_id AND offer.status='ACCEPTED'
              AND c.buyer_id=o.buyer_id AND c.seller_id=o.seller_id AND c.product_id=NEW.product_id
              AND offer.offered_item_price=NEW.agreed_price) THEN
            RAISE EXCEPTION 'Accepted offer belongs to another transaction' USING ERRCODE='23514';
        END IF;
        IF TG_OP='UPDATE' AND NEW IS DISTINCT FROM OLD THEN
            RAISE EXCEPTION 'UC83 order item snapshot is immutable' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_checkout_item_guard BEFORE INSERT OR UPDATE OR DELETE ON order_items
    FOR EACH ROW EXECUTE FUNCTION og_guard_checkout_item();

CREATE FUNCTION og_validate_checkout_aggregate() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE g checkout_groups; gid UUID; o orders; n INTEGER;
BEGIN
    IF TG_TABLE_NAME='checkout_groups' THEN gid:=NEW.group_id;
    ELSIF TG_TABLE_NAME='orders' THEN gid:=NEW.group_id;
    ELSE SELECT group_id INTO gid FROM orders WHERE order_id=NEW.order_id; END IF;
    IF gid IS NULL THEN RETURN NULL; END IF;
    SELECT * INTO STRICT g FROM checkout_groups WHERE group_id=gid FOR UPDATE;
    IF g.expected_total<>(SELECT COALESCE(sum(total_amount),0) FROM orders WHERE group_id=gid)
       OR NOT EXISTS(SELECT 1 FROM orders WHERE group_id=gid) THEN
        RAISE EXCEPTION 'Checkout total must equal all orders' USING ERRCODE='23514';
    END IF;
    FOR o IN SELECT * FROM orders WHERE group_id=gid LOOP
        IF o.payment_due_at<>g.payment_due_at OR o.delivery_method<>g.delivery_method
            OR o.carrier_snapshot IS DISTINCT FROM g.carrier
            OR jsonb_build_object('recipient_name',o.shipping_recipient_name,'phone_number',o.shipping_phone_number,
                'province',o.shipping_province,'district',o.shipping_district,'ward',o.shipping_ward,
                'detail_address',o.shipping_detail_address)<>g.address_snapshot
            OR o.subtotal<>(SELECT COALESCE(sum(agreed_price*quantity),0) FROM order_items WHERE order_id=o.order_id)
            OR o.voucher_discount_amount<>(SELECT COALESCE(sum(voucher_allocation),0) FROM order_items WHERE order_id=o.order_id)
            OR o.points_discount_amount<>(SELECT COALESCE(sum(points_allocation),0) FROM order_items WHERE order_id=o.order_id)
            OR EXISTS(SELECT 1 FROM order_items i WHERE i.order_id=o.order_id AND NOT EXISTS(
                SELECT 1 FROM inventory_reservations r WHERE r.order_item_id=i.order_item_id AND r.quantity=i.quantity)) THEN
            RAISE EXCEPTION 'Checkout snapshots/items/reservations incomplete' USING ERRCODE='23514';
        END IF;
    END LOOP;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER trg_group_aggregate AFTER INSERT OR UPDATE ON checkout_groups
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION og_validate_checkout_aggregate();
CREATE CONSTRAINT TRIGGER trg_order_aggregate AFTER INSERT OR UPDATE ON orders
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION og_validate_checkout_aggregate();
CREATE CONSTRAINT TRIGGER trg_item_aggregate AFTER INSERT OR UPDATE ON order_items
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION og_validate_checkout_aggregate();

CREATE TABLE order_events (
    event_id UUID PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(order_id),
    actor_id BIGINT REFERENCES users(user_id),
    event_type VARCHAR(60) NOT NULL,
    reason TEXT,
    metadata JSONB NOT NULL DEFAULT '{}' CHECK(jsonb_typeof(metadata)='object'),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    command_key VARCHAR(150) NOT NULL UNIQUE
);
CREATE TRIGGER trg_order_event_history BEFORE UPDATE OR DELETE ON order_events
    FOR EACH ROW EXECUTE FUNCTION og_reject_history_mutation();
CREATE TABLE conversation_order_links (
    conversation_id BIGINT NOT NULL REFERENCES conversations(conversation_id),
    order_id BIGINT NOT NULL REFERENCES orders(order_id),
    PRIMARY KEY(conversation_id,order_id)
);

CREATE TABLE voucher_revisions (
    revision_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    voucher_id BIGINT NOT NULL REFERENCES vouchers(voucher_id),
    revision_no INTEGER NOT NULL CHECK(revision_no>0),
    policy_snapshot JSONB NOT NULL CHECK(jsonb_typeof(policy_snapshot)='object'),
    scope VARCHAR(20) NOT NULL CHECK(scope IN ('CHECKOUT','PRODUCTS')),
    claim_limit INTEGER CHECK(claim_limit>0),
    per_user_claim_limit INTEGER NOT NULL DEFAULT 1 CHECK(per_user_claim_limit>0),
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL CHECK(valid_until>valid_from),
    created_by BIGINT NOT NULL REFERENCES users(user_id),
    UNIQUE(voucher_id,revision_no)
);
CREATE TRIGGER trg_voucher_revision_history BEFORE UPDATE OR DELETE ON voucher_revisions
    FOR EACH ROW EXECUTE FUNCTION og_reject_history_mutation();
CREATE TABLE voucher_revision_products (
    revision_id BIGINT NOT NULL REFERENCES voucher_revisions(revision_id),
    product_id BIGINT NOT NULL REFERENCES products(product_id),
    PRIMARY KEY(revision_id,product_id)
);
CREATE TABLE voucher_grants (
    grant_id UUID PRIMARY KEY,
    revision_id BIGINT NOT NULL REFERENCES voucher_revisions(revision_id),
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    source VARCHAR(20) NOT NULL CHECK(source IN ('SELF_CLAIM','KTV_GIFT','COMPENSATION')),
    issued_by BIGINT NOT NULL REFERENCES users(user_id),
    reason TEXT,
    state VARCHAR(15) NOT NULL DEFAULT 'AVAILABLE' CHECK(state IN ('AVAILABLE','REDEEMED','REVOKED')),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    command_key VARCHAR(150) NOT NULL UNIQUE,
    UNIQUE(grant_id,user_id)
);
CREATE FUNCTION og_guard_voucher_grant() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE r voucher_revisions;
BEGIN
    SELECT * INTO STRICT r FROM voucher_revisions WHERE revision_id=NEW.revision_id FOR UPDATE;
    IF NEW.state<>'AVAILABLE' OR NEW.expires_at>r.valid_until OR NEW.expires_at<=NEW.granted_at
        OR EXISTS(SELECT 1 FROM voucher_revision_revocations WHERE revision_id=r.revision_id)
        OR NEW.granted_at<r.valid_from OR NEW.granted_at>=r.valid_until
        OR (NEW.source='SELF_CLAIM' AND NEW.issued_by<>NEW.user_id)
        OR (NEW.source<>'SELF_CLAIM' AND (NOT og_has_role(NEW.issued_by,'KTV') OR NULLIF(btrim(NEW.reason),'') IS NULL))
        OR (SELECT count(*) FROM voucher_grants WHERE revision_id=r.revision_id AND user_id=NEW.user_id)>=r.per_user_claim_limit
        OR (r.claim_limit IS NOT NULL AND (SELECT count(*) FROM voucher_grants WHERE revision_id=r.revision_id)>=r.claim_limit) THEN
        RAISE EXCEPTION 'Voucher grant not eligible or claim limit exceeded' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_voucher_grant_guard BEFORE INSERT ON voucher_grants
    FOR EACH ROW EXECUTE FUNCTION og_guard_voucher_grant();
CREATE TABLE voucher_grant_events (
    event_id UUID PRIMARY KEY,
    grant_id UUID NOT NULL REFERENCES voucher_grants(grant_id),
    actor_id BIGINT NOT NULL REFERENCES users(user_id),
    event_type VARCHAR(15) NOT NULL CHECK(event_type IN ('GRANTED','REVOKED','REDEEMED')),
    reason TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    command_key VARCHAR(150) NOT NULL UNIQUE
);
CREATE TRIGGER trg_voucher_grant_event_history BEFORE UPDATE OR DELETE ON voucher_grant_events
    FOR EACH ROW EXECUTE FUNCTION og_reject_history_mutation();
CREATE TABLE checkout_redemptions (
    redemption_id UUID PRIMARY KEY,
    group_id UUID NOT NULL UNIQUE,
    buyer_id BIGINT NOT NULL,
    grant_id UUID NOT NULL UNIQUE,
    policy_snapshot JSONB NOT NULL CHECK(jsonb_typeof(policy_snapshot)='object'),
    discount_amount NUMERIC(19,2) NOT NULL CHECK(discount_amount>0),
    redeemed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(group_id,buyer_id) REFERENCES checkout_groups(group_id,buyer_id),
    FOREIGN KEY(grant_id,buyer_id) REFERENCES voucher_grants(grant_id,user_id)
);
CREATE FUNCTION og_redeem_voucher() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE v voucher_grants; r voucher_revisions; base NUMERIC; benefit NUMERIC;
BEGIN
    SELECT * INTO STRICT v FROM voucher_grants WHERE grant_id=NEW.grant_id FOR UPDATE;
    SELECT * INTO STRICT r FROM voucher_revisions WHERE revision_id=v.revision_id FOR UPDATE;
    IF v.state<>'AVAILABLE' OR v.expires_at<=NEW.redeemed_at THEN
        RAISE EXCEPTION 'Voucher already used/revoked/expired' USING ERRCODE='23514';
    END IF;
    IF r.benefit_component='GOODS' THEN
        SELECT COALESCE(sum(i.agreed_price*i.quantity),0) INTO base FROM order_items i JOIN orders o USING(order_id)
            WHERE o.group_id=NEW.group_id AND (r.scope='CHECKOUT' OR EXISTS(SELECT 1 FROM voucher_revision_products
                WHERE revision_id=r.revision_id AND product_id=i.product_id));
    ELSE SELECT COALESCE(sum(shipping_fee),0) INTO base FROM orders WHERE group_id=NEW.group_id; END IF;
    benefit:=LEAST(base,CASE WHEN r.discount_type='FIXED_AMOUNT' THEN r.discount_value
        ELSE round(base*r.discount_value/100,0) END,COALESCE(r.max_discount_amount,base));
    IF base<r.min_eligible_amount OR NEW.discount_amount<>benefit OR EXISTS(
        SELECT 1 FROM voucher_revision_revocations WHERE revision_id=r.revision_id) THEN
        RAISE EXCEPTION 'Voucher amount/minimum/revocation differs from immutable policy' USING ERRCODE='23514'; END IF;
    NEW.policy_snapshot:=r.policy_snapshot||jsonb_build_object('benefit_component',r.benefit_component,
        'discount_type',r.discount_type,'discount_value',r.discount_value,'max_discount_amount',r.max_discount_amount,
        'min_eligible_amount',r.min_eligible_amount,'scope',r.scope);
    UPDATE voucher_grants SET state='REDEEMED' WHERE grant_id=v.grant_id;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_checkout_redeem BEFORE INSERT ON checkout_redemptions
    FOR EACH ROW EXECUTE FUNCTION og_redeem_voucher();
CREATE TRIGGER trg_redemption_history BEFORE UPDATE OR DELETE ON checkout_redemptions
    FOR EACH ROW EXECUTE FUNCTION og_reject_history_mutation();

CREATE TABLE reward_policies (
    policy_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    policy_code VARCHAR(60) NOT NULL UNIQUE,
    point_value NUMERIC(19,2) NOT NULL CHECK(point_value>0),
    policy_snapshot JSONB NOT NULL CHECK(jsonb_typeof(policy_snapshot)='object'),
    created_by BIGINT NOT NULL REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER trg_reward_policy_history BEFORE UPDATE OR DELETE ON reward_policies
    FOR EACH ROW EXECUTE FUNCTION og_reject_history_mutation();
CREATE TABLE reward_accounts (
    user_id BIGINT PRIMARY KEY REFERENCES users(user_id),
    balance BIGINT NOT NULL DEFAULT 0 CHECK(balance>=0),
    version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0)
);
CREATE TABLE reward_ledger (
    entry_id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES reward_accounts(user_id),
    policy_id BIGINT NOT NULL REFERENCES reward_policies(policy_id),
    entry_type VARCHAR(20) NOT NULL CHECK(entry_type IN ('CREDIT_COMPLETION','DEBIT_CHECKOUT','CORRECTION')),
    points_delta BIGINT NOT NULL CHECK(points_delta<>0),
    order_id BIGINT REFERENCES orders(order_id),
    group_id UUID,
    actor_id BIGINT REFERENCES users(user_id),
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    command_key VARCHAR(150) NOT NULL UNIQUE,
    FOREIGN KEY(group_id,user_id) REFERENCES checkout_groups(group_id,buyer_id),
    CHECK ((entry_type='CREDIT_COMPLETION' AND points_delta>0 AND order_id IS NOT NULL AND group_id IS NULL)
        OR (entry_type='DEBIT_CHECKOUT' AND points_delta<0 AND group_id IS NOT NULL AND order_id IS NULL)
        OR (entry_type='CORRECTION' AND actor_id IS NOT NULL AND NULLIF(btrim(reason),'') IS NOT NULL))
);
CREATE UNIQUE INDEX uq_reward_order_credit ON reward_ledger(user_id,order_id) WHERE entry_type='CREDIT_COMPLETION';
CREATE UNIQUE INDEX uq_reward_group_debit ON reward_ledger(group_id) WHERE entry_type='DEBIT_CHECKOUT';
CREATE FUNCTION og_apply_reward_entry() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE b BIGINT;
BEGIN
    SELECT balance INTO STRICT b FROM reward_accounts WHERE user_id=NEW.user_id FOR UPDATE;
    IF b+NEW.points_delta<0 OR (NEW.entry_type='CREDIT_COMPLETION' AND NOT EXISTS(
        SELECT 1 FROM orders WHERE order_id=NEW.order_id AND status='COMPLETED'
            AND NEW.user_id IN (buyer_id,seller_id)))
        OR (NEW.entry_type='CORRECTION' AND NOT og_has_role(NEW.actor_id,'ADMIN')) THEN
        RAISE EXCEPTION 'Reward source/balance not eligible' USING ERRCODE='23514';
    END IF;
    UPDATE reward_accounts SET balance=balance+NEW.points_delta,version=version+1 WHERE user_id=NEW.user_id;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_reward_apply BEFORE INSERT ON reward_ledger
    FOR EACH ROW EXECUTE FUNCTION og_apply_reward_entry();
CREATE TRIGGER trg_reward_history BEFORE UPDATE OR DELETE ON reward_ledger
    FOR EACH ROW EXECUTE FUNCTION og_reject_history_mutation();

CREATE TABLE discount_allocations (
    allocation_id UUID PRIMARY KEY,
    order_item_id BIGINT NOT NULL REFERENCES order_items(order_item_id),
    redemption_id UUID REFERENCES checkout_redemptions(redemption_id),
    reward_entry_id UUID REFERENCES reward_ledger(entry_id),
    component VARCHAR(10) NOT NULL CHECK(component IN ('GOODS','SHIPPING')),
    amount NUMERIC(19,2) NOT NULL CHECK(amount>0),
    CHECK ((redemption_id IS NOT NULL)::INTEGER+(reward_entry_id IS NOT NULL)::INTEGER=1),
    CHECK(reward_entry_id IS NULL OR component='GOODS')
);
CREATE UNIQUE INDEX uq_discount_voucher_item ON discount_allocations(redemption_id,order_item_id,component)
    WHERE redemption_id IS NOT NULL;
CREATE UNIQUE INDEX uq_discount_points_item ON discount_allocations(reward_entry_id,order_item_id)
    WHERE reward_entry_id IS NOT NULL;
CREATE TRIGGER trg_discount_history BEFORE UPDATE OR DELETE ON discount_allocations
    FOR EACH ROW EXECUTE FUNCTION og_reject_history_mutation();

CREATE FUNCTION og_guard_discount_source() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE gid UUID; pid BIGINT;
BEGIN
    SELECT o.group_id,i.product_id INTO STRICT gid,pid FROM order_items i JOIN orders o USING(order_id)
        WHERE i.order_item_id=NEW.order_item_id AND i.workflow_model='UC83';
    IF NEW.redemption_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM checkout_redemptions c
        JOIN voucher_grants g USING(grant_id) JOIN voucher_revisions r USING(revision_id)
        WHERE c.redemption_id=NEW.redemption_id AND c.group_id=gid AND r.benefit_component=NEW.component AND (r.scope='CHECKOUT' OR EXISTS(
            SELECT 1 FROM voucher_revision_products WHERE revision_id=r.revision_id AND product_id=pid))) THEN
        RAISE EXCEPTION 'Voucher allocation outside checkout/product scope' USING ERRCODE='23514';
    END IF;
    IF NEW.reward_entry_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM reward_ledger
        WHERE entry_id=NEW.reward_entry_id AND group_id=gid AND entry_type='DEBIT_CHECKOUT') THEN
        RAISE EXCEPTION 'Point allocation belongs to another checkout' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_discount_source BEFORE INSERT ON discount_allocations
    FOR EACH ROW EXECUTE FUNCTION og_guard_discount_source();
CREATE FUNCTION og_validate_discount_totals() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE gid UUID; v NUMERIC; p NUMERIC;
BEGIN
    IF TG_TABLE_NAME IN ('checkout_groups','orders','checkout_redemptions','reward_ledger') THEN gid:=NEW.group_id;
    ELSE SELECT o.group_id INTO gid FROM order_items i JOIN orders o USING(order_id) WHERE i.order_item_id=NEW.order_item_id; END IF;
    IF gid IS NULL THEN RETURN NULL; END IF;
    PERFORM 1 FROM checkout_groups WHERE group_id=gid FOR UPDATE;
    SELECT COALESCE(sum(voucher_discount_amount+shipping_discount_amount),0),COALESCE(sum(points_discount_amount),0)
        INTO v,p FROM orders WHERE group_id=gid;
    IF v<>COALESCE((SELECT discount_amount FROM checkout_redemptions WHERE group_id=gid),0)
        OR p<>COALESCE((SELECT -l.points_delta*r.point_value FROM reward_ledger l JOIN reward_policies r USING(policy_id)
            WHERE l.group_id=gid AND l.entry_type='DEBIT_CHECKOUT'),0)
        OR EXISTS(SELECT 1 FROM order_items i JOIN orders o USING(order_id) WHERE o.group_id=gid AND (
            i.voucher_allocation<>(SELECT COALESCE(sum(amount),0) FROM discount_allocations d WHERE d.order_item_id=i.order_item_id
                AND redemption_id IS NOT NULL AND component='GOODS')
            OR i.points_allocation<>(SELECT COALESCE(sum(amount),0) FROM discount_allocations d WHERE d.order_item_id=i.order_item_id
                AND reward_entry_id IS NOT NULL)))
        OR EXISTS(SELECT 1 FROM orders o WHERE o.group_id=gid AND o.shipping_discount_amount<>(
            SELECT COALESCE(sum(d.amount),0) FROM discount_allocations d JOIN order_items i USING(order_item_id)
            WHERE i.order_id=o.order_id AND d.component='SHIPPING'))
        OR EXISTS(SELECT 1 FROM checkout_redemptions c WHERE c.group_id=gid AND c.discount_amount<>(
            SELECT COALESCE(sum(amount),0) FROM discount_allocations WHERE redemption_id=c.redemption_id)) THEN
        RAISE EXCEPTION 'Voucher/points allocations must exactly conserve checkout discounts' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER trg_discount_group_sum AFTER INSERT OR UPDATE ON checkout_groups
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION og_validate_discount_totals();
CREATE CONSTRAINT TRIGGER trg_discount_order_sum AFTER INSERT OR UPDATE ON orders
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION og_validate_discount_totals();
CREATE CONSTRAINT TRIGGER trg_discount_item_sum AFTER INSERT OR UPDATE ON order_items
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION og_validate_discount_totals();
CREATE CONSTRAINT TRIGGER trg_discount_redemption_sum AFTER INSERT ON checkout_redemptions
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION og_validate_discount_totals();
CREATE CONSTRAINT TRIGGER trg_discount_reward_sum AFTER INSERT ON reward_ledger
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION og_validate_discount_totals();
CREATE CONSTRAINT TRIGGER trg_discount_allocation_sum AFTER INSERT ON discount_allocations
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION og_validate_discount_totals();
CREATE FUNCTION og_guard_reward_account() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' OR (TG_OP='INSERT' AND (NEW.balance<>0 OR NEW.version<>0))
        OR (TG_OP='UPDATE' AND (NEW.user_id<>OLD.user_id OR pg_trigger_depth()<2)) THEN
        RAISE EXCEPTION 'Reward balance only changes through ledger' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_reward_account_guard BEFORE INSERT OR UPDATE OR DELETE ON reward_accounts
    FOR EACH ROW EXECUTE FUNCTION og_guard_reward_account();
CREATE FUNCTION og_guard_grant_history() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' OR ROW(NEW.grant_id,NEW.revision_id,NEW.user_id,NEW.source,NEW.issued_by,NEW.reason,
        NEW.granted_at,NEW.expires_at,NEW.command_key) IS DISTINCT FROM ROW(OLD.grant_id,OLD.revision_id,OLD.user_id,
        OLD.source,OLD.issued_by,OLD.reason,OLD.granted_at,OLD.expires_at,OLD.command_key)
        OR OLD.state<>'AVAILABLE' OR NEW.state NOT IN ('REDEEMED','REVOKED')
        OR (NEW.state='REDEEMED' AND pg_trigger_depth()<2) THEN
        RAISE EXCEPTION 'Voucher ownership/history cannot be restored after use' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_grant_history BEFORE UPDATE OR DELETE ON voucher_grants
    FOR EACH ROW EXECUTE FUNCTION og_guard_grant_history();
CREATE FUNCTION og_guard_checkout_snapshot() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_TABLE_NAME='checkout_groups' THEN
        IF TG_OP='DELETE' OR (to_jsonb(NEW)-'state') IS DISTINCT FROM (to_jsonb(OLD)-'state') THEN
            RAISE EXCEPTION 'Checkout command, amount and deadline are immutable' USING ERRCODE='23514'; END IF;
    ELSIF OLD.workflow_model='UC83' AND (TG_OP='DELETE' OR
        ROW(NEW.workflow_model,NEW.group_id,NEW.checkout_group_id,NEW.buyer_id,NEW.seller_id,NEW.subtotal,
            NEW.shipping_fee,NEW.total_amount,NEW.voucher_discount_amount,NEW.shipping_discount_amount,
            NEW.points_discount_amount,NEW.return_allowed,NEW.delivery_method,NEW.carrier_snapshot,NEW.handover_day,
            NEW.payment_due_at,NEW.created_at,NEW.shipping_recipient_name,NEW.shipping_phone_number,NEW.shipping_province,
            NEW.shipping_district,NEW.shipping_ward,NEW.shipping_detail_address)
        IS DISTINCT FROM ROW(OLD.workflow_model,OLD.group_id,OLD.checkout_group_id,OLD.buyer_id,OLD.seller_id,OLD.subtotal,
            OLD.shipping_fee,OLD.total_amount,OLD.voucher_discount_amount,OLD.shipping_discount_amount,
            OLD.points_discount_amount,OLD.return_allowed,OLD.delivery_method,OLD.carrier_snapshot,OLD.handover_day,
            OLD.payment_due_at,OLD.created_at,OLD.shipping_recipient_name,OLD.shipping_phone_number,OLD.shipping_province,
            OLD.shipping_district,OLD.shipping_ward,OLD.shipping_detail_address)) THEN
        RAISE EXCEPTION 'UC83 order checkout snapshot retained' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_checkout_snapshot BEFORE UPDATE OR DELETE ON checkout_groups
    FOR EACH ROW EXECUTE FUNCTION og_guard_checkout_snapshot();
CREATE TRIGGER trg_order_snapshot BEFORE UPDATE OR DELETE ON orders
    FOR EACH ROW EXECUTE FUNCTION og_guard_checkout_snapshot();

ALTER TABLE voucher_revisions
    ADD COLUMN benefit_component VARCHAR(10) NOT NULL CHECK(benefit_component IN ('GOODS','SHIPPING')),
    ADD COLUMN discount_type VARCHAR(20) NOT NULL CHECK(discount_type IN ('FIXED_AMOUNT','PERCENTAGE')),
    ADD COLUMN discount_value NUMERIC(19,2) NOT NULL CHECK(discount_value>0),
    ADD COLUMN max_discount_amount NUMERIC(19,2) CHECK(max_discount_amount>0),
    ADD COLUMN min_eligible_amount NUMERIC(19,2) NOT NULL CHECK(min_eligible_amount>=0),
    ADD CONSTRAINT ck_voucher_revision_percentage CHECK(discount_type<>'PERCENTAGE' OR discount_value<=100);
CREATE FUNCTION og_guard_voucher_policy() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE v vouchers;
BEGIN
    SELECT * INTO STRICT v FROM vouchers WHERE voucher_id=NEW.voucher_id;
    IF NOT og_has_role(NEW.created_by,'ADMIN') OR v.voucher_type='BUYER_FEE_DISCOUNT' THEN
        RAISE EXCEPTION 'Admin authors canonical goods/shipping policies' USING ERRCODE='23514'; END IF;
    NEW.benefit_component:=COALESCE(NEW.benefit_component,CASE v.voucher_type WHEN 'SHIPPING_DISCOUNT' THEN 'SHIPPING' ELSE 'GOODS' END);
    NEW.discount_type:=COALESCE(NEW.discount_type,v.discount_type);
    NEW.discount_value:=COALESCE(NEW.discount_value,v.discount_value);
    NEW.max_discount_amount:=COALESCE(NEW.max_discount_amount,v.max_discount_amount);
    NEW.min_eligible_amount:=COALESCE(NEW.min_eligible_amount,v.min_order_amount);
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_voucher_policy_guard BEFORE INSERT ON voucher_revisions
    FOR EACH ROW EXECUTE FUNCTION og_guard_voucher_policy();
CREATE TABLE voucher_revision_revocations (
    revision_id BIGINT PRIMARY KEY REFERENCES voucher_revisions(revision_id),
    revoked_by BIGINT NOT NULL REFERENCES users(user_id),
    reason TEXT NOT NULL CHECK(btrim(reason)<>''),
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE FUNCTION og_guard_voucher_revocation() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    PERFORM 1 FROM voucher_revisions WHERE revision_id=NEW.revision_id FOR UPDATE;
    IF NOT og_has_role(NEW.revoked_by,'ADMIN') THEN RAISE EXCEPTION 'Admin revokes policy' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_voucher_revocation_guard BEFORE INSERT ON voucher_revision_revocations
    FOR EACH ROW EXECUTE FUNCTION og_guard_voucher_revocation();
CREATE TRIGGER trg_voucher_revocation_history BEFORE UPDATE OR DELETE ON voucher_revision_revocations
    FOR EACH ROW EXECUTE FUNCTION og_reject_history_mutation();
