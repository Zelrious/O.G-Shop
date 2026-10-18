-- G07: canonical money ownership. V14 payments remains a legacy projection.
CREATE TABLE payment_intents (
    intent_id UUID PRIMARY KEY,
    purpose VARCHAR(20) NOT NULL CHECK(purpose IN ('CHECKOUT_GROUP','LISTING_FEE')),
    group_id UUID REFERENCES checkout_groups(group_id),
    listing_charge_id UUID REFERENCES listing_fee_charges(charge_id),
    expected_amount NUMERIC(19,2) NOT NULL CHECK(expected_amount>0),
    currency CHAR(3) NOT NULL DEFAULT 'VND' CHECK(currency='VND'),
    payment_method VARCHAR(30) NOT NULL CHECK(payment_method IN ('VNPAY','COD_MOCK','BANK_TRANSFER_MOCK','E_WALLET_MOCK')),
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK(state IN ('PENDING','CONFIRMED','CANCELLED','EXPIRED','RECONCILIATION')),
    deadline TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    command_key VARCHAR(150) NOT NULL UNIQUE,
    CHECK(deadline>created_at),
    CHECK((purpose='CHECKOUT_GROUP' AND group_id IS NOT NULL AND listing_charge_id IS NULL)
       OR (purpose='LISTING_FEE' AND group_id IS NULL AND listing_charge_id IS NOT NULL))
);
CREATE UNIQUE INDEX uq_intent_checkout ON payment_intents(group_id) WHERE group_id IS NOT NULL;
CREATE UNIQUE INDEX uq_intent_listing_charge ON payment_intents(listing_charge_id) WHERE listing_charge_id IS NOT NULL;
CREATE FUNCTION og_guard_payment_intent() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    IF NEW.purpose='CHECKOUT_GROUP' AND NOT EXISTS(
        SELECT 1 FROM checkout_groups WHERE group_id=NEW.group_id AND expected_total=NEW.expected_amount
            AND payment_due_at=NEW.deadline AND payment_method=NEW.payment_method) THEN
        RAISE EXCEPTION 'Payment amount/deadline/method differs from checkout' USING ERRCODE='23514';
    END IF;
    IF NEW.purpose='LISTING_FEE' AND NOT EXISTS(
        SELECT 1 FROM listing_fee_charges WHERE charge_id=NEW.listing_charge_id AND amount=NEW.expected_amount) THEN
        RAISE EXCEPTION 'Payment differs from listing fee charge' USING ERRCODE='23514';
    END IF;
    IF TG_OP='UPDATE' AND ROW(NEW.purpose,NEW.group_id,NEW.listing_charge_id,NEW.expected_amount,NEW.currency,
        NEW.payment_method,NEW.deadline,NEW.created_at,NEW.command_key)
        IS DISTINCT FROM ROW(OLD.purpose,OLD.group_id,OLD.listing_charge_id,OLD.expected_amount,OLD.currency,
        OLD.payment_method,OLD.deadline,OLD.created_at,OLD.command_key) THEN
        RAISE EXCEPTION 'Payment intent snapshot is immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_payment_intent_guard BEFORE INSERT OR UPDATE ON payment_intents
    FOR EACH ROW EXECUTE FUNCTION og_guard_payment_intent();

-- Reuse V14 attempts/receipts. The V13 attempt table is retained for legacy readers.
ALTER TABLE payment_attempts ALTER COLUMN order_id DROP NOT NULL, ALTER COLUMN payment_id DROP NOT NULL;
ALTER TABLE payment_attempts ADD COLUMN intent_id UUID REFERENCES payment_intents(intent_id),
    ADD CONSTRAINT ck_attempt_owner CHECK(
        (intent_id IS NULL AND order_id IS NOT NULL AND payment_id IS NOT NULL)
        OR (intent_id IS NOT NULL AND order_id IS NULL AND payment_id IS NULL)),
    ADD CONSTRAINT uq_attempt_intent UNIQUE(attempt_id,intent_id),
    ADD CONSTRAINT uq_attempt_legacy_owner UNIQUE(attempt_id,order_id,payment_id);
CREATE UNIQUE INDEX uq_intent_pending_attempt ON payment_attempts(intent_id)
    WHERE intent_id IS NOT NULL AND status='PENDING';
CREATE FUNCTION og_guard_intent_attempt() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    IF NEW.intent_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM payment_intents
        WHERE intent_id=NEW.intent_id AND expected_amount=NEW.amount AND currency=NEW.currency) THEN
        RAISE EXCEPTION 'Attempt amount/currency differs from intent' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_intent_attempt_guard BEFORE INSERT OR UPDATE ON payment_attempts
    FOR EACH ROW EXECUTE FUNCTION og_guard_intent_attempt();
ALTER TABLE payment_ipn_events ALTER COLUMN order_id DROP NOT NULL, ALTER COLUMN payment_id DROP NOT NULL;
ALTER TABLE payment_ipn_events ADD COLUMN intent_id UUID REFERENCES payment_intents(intent_id),
    ADD CONSTRAINT ck_ipn_owner CHECK(
        (intent_id IS NULL AND order_id IS NOT NULL AND payment_id IS NOT NULL)
        OR (intent_id IS NOT NULL AND order_id IS NULL AND payment_id IS NULL)),
    ADD CONSTRAINT fk_ipn_intent_attempt FOREIGN KEY(attempt_id,intent_id)
        REFERENCES payment_attempts(attempt_id,intent_id),
    ADD CONSTRAINT fk_ipn_legacy_attempt FOREIGN KEY(attempt_id,order_id,payment_id)
        REFERENCES payment_attempts(attempt_id,order_id,payment_id) NOT VALID;
-- NOT VALID preserves any previously inconsistent legacy event without legitimizing it.
ALTER TABLE payment_review_audits ALTER COLUMN order_id DROP NOT NULL, ALTER COLUMN payment_id DROP NOT NULL;
ALTER TABLE payment_review_audits ADD COLUMN intent_id UUID REFERENCES payment_intents(intent_id),
    ADD COLUMN actor_id BIGINT REFERENCES users(user_id),
    ADD CONSTRAINT ck_review_owner CHECK(
        (intent_id IS NULL AND order_id IS NOT NULL AND payment_id IS NOT NULL)
        OR (intent_id IS NOT NULL AND order_id IS NULL AND payment_id IS NULL AND actor_id IS NOT NULL)),
    ADD CONSTRAINT fk_review_intent_attempt FOREIGN KEY(attempt_id,intent_id)
        REFERENCES payment_attempts(attempt_id,intent_id);
ALTER TABLE payment_reconciliation_cases ALTER COLUMN order_id DROP NOT NULL, ALTER COLUMN payment_id DROP NOT NULL;
ALTER TABLE payment_reconciliation_cases ADD COLUMN intent_id UUID REFERENCES payment_intents(intent_id),
    ADD COLUMN assigned_user_id BIGINT REFERENCES users(user_id),
    ADD CONSTRAINT ck_reconciliation_owner CHECK(
        (intent_id IS NULL AND order_id IS NOT NULL AND payment_id IS NOT NULL)
        OR (intent_id IS NOT NULL AND order_id IS NULL AND payment_id IS NULL)),
    ADD CONSTRAINT fk_reconciliation_intent_attempt FOREIGN KEY(attempt_id,intent_id)
        REFERENCES payment_attempts(attempt_id,intent_id);

CREATE TABLE payment_confirmations (
    confirmation_id UUID PRIMARY KEY,
    intent_id UUID NOT NULL REFERENCES payment_intents(intent_id),
    attempt_id BIGINT NOT NULL,
    ipn_event_id BIGINT UNIQUE REFERENCES payment_ipn_events(event_id),
    provider VARCHAR(30) NOT NULL,
    provider_transaction_id VARCHAR(150) NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK(amount>0),
    disposition VARCHAR(20) NOT NULL DEFAULT 'ALLOCATABLE'
        CHECK(disposition IN ('ALLOCATABLE','RECONCILIATION')),
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(provider,provider_transaction_id),
    UNIQUE(confirmation_id,intent_id),
    FOREIGN KEY(attempt_id,intent_id) REFERENCES payment_attempts(attempt_id,intent_id)
);
CREATE UNIQUE INDEX uq_intent_allocatable_confirmation ON payment_confirmations(intent_id)
    WHERE disposition='ALLOCATABLE';
CREATE FUNCTION og_guard_payment_confirmation() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE i payment_intents;
BEGIN
    SELECT * INTO STRICT i FROM payment_intents WHERE intent_id=NEW.intent_id FOR UPDATE;
    IF NEW.amount<>i.expected_amount OR NEW.provider<>i.payment_method OR NOT EXISTS(SELECT 1 FROM payment_attempts
        WHERE attempt_id=NEW.attempt_id AND intent_id=i.intent_id AND provider=NEW.provider)
        OR (NEW.provider='VNPAY' AND NOT EXISTS(
            SELECT 1 FROM payment_ipn_events e JOIN payment_ipn_raw_receipts r USING(receipt_id)
            WHERE e.event_id=NEW.ipn_event_id AND e.attempt_id=NEW.attempt_id AND e.intent_id=i.intent_id
                AND r.verification_status='VERIFIED' AND e.vnp_amount=NEW.amount
                AND e.processing_outcome IN ('APPLIED_SUCCESS','APPLIED_EXPIRED_ORDER','APPLIED_DUPLICATE_CHARGE'))) THEN
        RAISE EXCEPTION 'Confirmation source/amount/reference not verified' USING ERRCODE='23514';
    END IF;
    NEW.confirmed_at:=clock_timestamp();
    IF i.state<>'PENDING' OR NEW.confirmed_at>=i.deadline OR
       (i.group_id IS NOT NULL AND EXISTS(SELECT 1 FROM orders WHERE group_id=i.group_id AND status='CANCELLED')) THEN
        NEW.disposition:='RECONCILIATION';
    END IF;
    IF NEW.disposition='ALLOCATABLE' THEN
        UPDATE payment_intents SET state='CONFIRMED' WHERE intent_id=i.intent_id;
        IF i.purpose='LISTING_FEE' THEN
            INSERT INTO listing_fee_receipts(receipt_id,charge_id,provider,provider_transaction_id,amount,confirmed_at)
                VALUES(NEW.confirmation_id,i.listing_charge_id,NEW.provider,NEW.provider_transaction_id,NEW.amount,NEW.confirmed_at);
        END IF;
    ELSE
        UPDATE payment_intents SET state='RECONCILIATION' WHERE intent_id=i.intent_id;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_payment_confirmation_guard BEFORE INSERT ON payment_confirmations
    FOR EACH ROW EXECUTE FUNCTION og_guard_payment_confirmation();
CREATE TRIGGER trg_payment_confirmation_history BEFORE UPDATE OR DELETE ON payment_confirmations
    FOR EACH ROW EXECUTE FUNCTION og_reject_history_mutation();

CREATE TABLE payment_allocations (
    allocation_id UUID PRIMARY KEY,
    intent_id UUID NOT NULL REFERENCES payment_intents(intent_id),
    confirmation_id UUID NOT NULL,
    order_id BIGINT NOT NULL UNIQUE REFERENCES orders(order_id),
    amount NUMERIC(19,2) NOT NULL CHECK(amount>=0),
    UNIQUE(allocation_id,order_id),
    FOREIGN KEY(confirmation_id,intent_id) REFERENCES payment_confirmations(confirmation_id,intent_id)
);
CREATE FUNCTION og_guard_payment_allocation() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    PERFORM 1 FROM payment_intents WHERE intent_id=NEW.intent_id FOR UPDATE;
    IF NOT EXISTS(SELECT 1 FROM payment_intents i JOIN payment_confirmations c USING(intent_id)
        JOIN orders o ON o.group_id=i.group_id WHERE i.intent_id=NEW.intent_id AND i.purpose='CHECKOUT_GROUP'
          AND c.confirmation_id=NEW.confirmation_id AND c.disposition='ALLOCATABLE'
          AND o.order_id=NEW.order_id AND o.total_amount=NEW.amount AND o.status='PAYMENT_PENDING') THEN
        RAISE EXCEPTION 'Allocation does not belong to eligible order/confirmed group' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_payment_allocation_guard BEFORE INSERT ON payment_allocations
    FOR EACH ROW EXECUTE FUNCTION og_guard_payment_allocation();
CREATE TRIGGER trg_allocation_history BEFORE UPDATE OR DELETE ON payment_allocations
    FOR EACH ROW EXECUTE FUNCTION og_reject_history_mutation();
CREATE FUNCTION og_validate_payment_allocations() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE i payment_intents; n NUMERIC;
BEGIN
    SELECT * INTO STRICT i FROM payment_intents WHERE intent_id=NEW.intent_id FOR UPDATE;
    IF i.purpose='CHECKOUT_GROUP' AND EXISTS(SELECT 1 FROM payment_confirmations
        WHERE intent_id=i.intent_id AND disposition='ALLOCATABLE') THEN
        SELECT COALESCE(sum(amount),0) INTO n FROM payment_allocations WHERE intent_id=i.intent_id;
        IF n<>i.expected_amount OR EXISTS(SELECT 1 FROM orders o WHERE o.group_id=i.group_id
            AND NOT EXISTS(SELECT 1 FROM payment_allocations a WHERE a.order_id=o.order_id AND a.intent_id=i.intent_id)) THEN
            RAISE EXCEPTION 'Confirmed payment must allocate every order exactly' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER trg_confirmation_allocation_sum AFTER INSERT ON payment_confirmations
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION og_validate_payment_allocations();
CREATE CONSTRAINT TRIGGER trg_payment_allocation_sum AFTER INSERT ON payment_allocations
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION og_validate_payment_allocations();

ALTER TABLE orders ADD COLUMN purchase_shipping_used_at TIMESTAMPTZ;
CREATE TABLE order_fund_components (
    component_id UUID PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(order_id),
    allocation_id UUID,
    late_confirmation_id UUID REFERENCES payment_confirmations(confirmation_id),
    component_type VARCHAR(30) NOT NULL CHECK(component_type IN
        ('GOODS_CASH','SHIPPING_CASH','PLATFORM_GOODS_SUBSIDY','PLATFORM_SHIPPING_SUBSIDY','LATE_PAYMENT_CASH')),
    confirmed_amount NUMERIC(19,2) NOT NULL CHECK(confirmed_amount>=0),
    reserved_amount NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK(reserved_amount>=0),
    refunded_amount NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK(refunded_amount>=0),
    released_amount NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK(released_amount>=0),
    funding_reference VARCHAR(150),
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(late_confirmation_id,order_id),
    FOREIGN KEY(allocation_id,order_id) REFERENCES payment_allocations(allocation_id,order_id),
    CHECK(reserved_amount+refunded_amount+released_amount<=confirmed_amount),
    CHECK((component_type IN ('GOODS_CASH','SHIPPING_CASH') AND allocation_id IS NOT NULL AND late_confirmation_id IS NULL)
      OR (component_type LIKE 'PLATFORM_%' AND funding_reference IS NOT NULL AND allocation_id IS NULL AND late_confirmation_id IS NULL)
      OR (component_type='LATE_PAYMENT_CASH' AND late_confirmation_id IS NOT NULL AND allocation_id IS NULL))
);
CREATE UNIQUE INDEX uq_order_component ON order_fund_components(order_id,component_type)
    WHERE component_type<>'LATE_PAYMENT_CASH';
CREATE TABLE money_holds (
    hold_id UUID PRIMARY KEY,
    component_id UUID NOT NULL REFERENCES order_fund_components(component_id),
    source_key VARCHAR(150) NOT NULL,
    reason TEXT NOT NULL CHECK(btrim(reason)<>''),
    opened_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMPTZ,
    UNIQUE(component_id,source_key),
    CHECK(closed_at IS NULL OR closed_at>=opened_at)
);
CREATE INDEX ix_money_hold_open ON money_holds(component_id) WHERE closed_at IS NULL;
CREATE FUNCTION og_lock_money_hold() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    PERFORM 1 FROM orders WHERE order_id=(SELECT order_id FROM order_fund_components WHERE component_id=NEW.component_id) FOR UPDATE;
    PERFORM 1 FROM order_fund_components WHERE component_id=NEW.component_id FOR UPDATE;
    IF TG_OP='UPDATE' AND (NEW.component_id<>OLD.component_id OR NEW.source_key<>OLD.source_key
       OR OLD.closed_at IS NOT NULL) THEN RAISE EXCEPTION 'Hold history is immutable' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_money_hold_lock BEFORE INSERT OR UPDATE ON money_holds
    FOR EACH ROW EXECUTE FUNCTION og_lock_money_hold();

CREATE TABLE settlement_operations (
    operation_id UUID PRIMARY KEY,
    component_id UUID NOT NULL REFERENCES order_fund_components(component_id),
    operation_type VARCHAR(25) NOT NULL CHECK(operation_type IN ('REFUND_BUYER','RELEASE_SELLER','RELEASE_CARRIER','RETURN_PLATFORM')),
    amount NUMERIC(19,2) NOT NULL CHECK(amount>0),
    cause_type VARCHAR(20) NOT NULL CHECK(cause_type IN ('CANCELLATION','RETURN','COMPLETION','EXEMPTION','RECONCILIATION')),
    case_decision_id BIGINT,
    recipient_user_id BIGINT REFERENCES users(user_id),
    state VARCHAR(15) NOT NULL DEFAULT 'REQUESTED' CHECK(state IN ('REQUESTED','PROCESSING','CONFIRMED','FAILED')),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMPTZ,
    provider VARCHAR(30),
    provider_operation_id VARCHAR(150),
    failure_reason TEXT,
    command_key VARCHAR(150) NOT NULL UNIQUE,
    UNIQUE(provider,provider_operation_id),
    CHECK(state<>'CONFIRMED' OR (confirmed_at IS NOT NULL AND provider IS NOT NULL AND provider_operation_id IS NOT NULL))
);
-- Replaced with typed case-decision checks in V19.
CREATE FUNCTION og_case_settlement_allowed(p_decision BIGINT,p_order BIGINT,p_cause TEXT) RETURNS BOOLEAN
LANGUAGE sql STABLE AS $$ SELECT FALSE $$;
CREATE FUNCTION og_order_has_blocking_case(p_order BIGINT) RETURNS BOOLEAN LANGUAGE sql STABLE AS $$ SELECT FALSE $$;
CREATE FUNCTION og_guard_settlement_operation() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE c order_fund_components; o orders; old_reserved BOOLEAN; new_reserved BOOLEAN;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Settlement history retained' USING ERRCODE='23514'; END IF;
    SELECT * INTO STRICT o FROM orders WHERE order_id=(SELECT order_id FROM order_fund_components WHERE component_id=NEW.component_id) FOR UPDATE;
    SELECT * INTO STRICT c FROM order_fund_components WHERE component_id=NEW.component_id FOR UPDATE;
    IF TG_OP='INSERT' AND NEW.state<>'REQUESTED' THEN RAISE EXCEPTION 'Operation starts requested' USING ERRCODE='23514'; END IF;
    IF TG_OP='UPDATE' AND (OLD.state='CONFIRMED' OR
        ROW(NEW.component_id,NEW.operation_type,NEW.amount,NEW.cause_type,NEW.case_decision_id,NEW.recipient_user_id,NEW.command_key)
        IS DISTINCT FROM ROW(OLD.component_id,OLD.operation_type,OLD.amount,OLD.cause_type,OLD.case_decision_id,OLD.recipient_user_id,OLD.command_key)) THEN
        RAISE EXCEPTION 'Settlement identity/confirmed result immutable' USING ERRCODE='23514';
    END IF;
    IF NEW.state IN ('REQUESTED','PROCESSING','CONFIRMED') THEN
        IF EXISTS(SELECT 1 FROM money_holds WHERE component_id=c.component_id AND closed_at IS NULL) THEN
            RAISE EXCEPTION 'Money component has active hold' USING ERRCODE='23514';
        END IF;
        IF c.component_type IN ('GOODS_CASH','PLATFORM_GOODS_SUBSIDY') AND og_order_has_blocking_case(o.order_id) THEN
            RAISE EXCEPTION 'Order has blocking case' USING ERRCODE='23514';
        END IF;
        IF NEW.operation_type='REFUND_BUYER' THEN
            IF NEW.recipient_user_id IS DISTINCT FROM o.buyer_id OR c.component_type NOT IN ('GOODS_CASH','SHIPPING_CASH','LATE_PAYMENT_CASH')
              OR NEW.amount<>c.confirmed_amount
              OR (c.component_type='SHIPPING_CASH' AND (NEW.cause_type<>'CANCELLATION' OR o.purchase_shipping_used_at IS NOT NULL))
              OR NOT ((NEW.cause_type='CANCELLATION' AND o.status='CANCELLED')
                OR (NEW.cause_type='RETURN' AND og_case_settlement_allowed(NEW.case_decision_id,o.order_id,'RETURN'))
                OR (NEW.cause_type='RECONCILIATION' AND c.component_type='LATE_PAYMENT_CASH')) THEN
                RAISE EXCEPTION 'Buyer refund not eligible/full net component' USING ERRCODE='23514';
            END IF;
        ELSIF NEW.operation_type='RELEASE_SELLER' THEN
            IF NEW.recipient_user_id IS DISTINCT FROM o.seller_id
                OR c.component_type NOT IN ('GOODS_CASH','PLATFORM_GOODS_SUBSIDY')
                OR NOT ((NEW.cause_type='COMPLETION' AND o.valid_delivered_at IS NOT NULL
                    AND (o.early_completed_at IS NOT NULL OR clock_timestamp()>=o.valid_delivered_at+INTERVAL '3 days'))
                    OR (NEW.cause_type='EXEMPTION' AND og_case_settlement_allowed(NEW.case_decision_id,o.order_id,'EXEMPTION'))) THEN
                RAISE EXCEPTION 'Seller release is not eligible' USING ERRCODE='23514';
            END IF;
        ELSIF NEW.operation_type='RELEASE_CARRIER' THEN
            IF c.component_type NOT IN ('SHIPPING_CASH','PLATFORM_SHIPPING_SUBSIDY') OR o.purchase_shipping_used_at IS NULL THEN
                RAISE EXCEPTION 'Shipping was not used' USING ERRCODE='23514';
            END IF;
        ELSIF c.component_type NOT LIKE 'PLATFORM_%' OR NOT (
            (NEW.cause_type='CANCELLATION' AND o.status='CANCELLED') OR
            (NEW.cause_type='RETURN' AND og_case_settlement_allowed(NEW.case_decision_id,o.order_id,'RETURN'))) THEN
            RAISE EXCEPTION 'Platform return requires subsidy component' USING ERRCODE='23514';
        END IF;
    END IF;
    old_reserved:=TG_OP='UPDATE' AND OLD.state IN ('REQUESTED','PROCESSING');
    new_reserved:=NEW.state IN ('REQUESTED','PROCESSING');
    IF (TG_OP='INSERT' OR (TG_OP='UPDATE' AND OLD.state='FAILED')) AND new_reserved THEN
        UPDATE order_fund_components SET reserved_amount=reserved_amount+NEW.amount,version=version+1
            WHERE component_id=c.component_id;
    ELSIF old_reserved AND NEW.state IN ('FAILED','CONFIRMED') THEN
        UPDATE order_fund_components SET reserved_amount=reserved_amount-NEW.amount,
            refunded_amount=refunded_amount+CASE WHEN NEW.state='CONFIRMED' AND NEW.operation_type IN ('REFUND_BUYER','RETURN_PLATFORM') THEN NEW.amount ELSE 0 END,
            released_amount=released_amount+CASE WHEN NEW.state='CONFIRMED' AND NEW.operation_type IN ('RELEASE_SELLER','RELEASE_CARRIER') THEN NEW.amount ELSE 0 END,
            version=version+1 WHERE component_id=c.component_id;
    ELSIF TG_OP='UPDATE' AND NEW.state<>OLD.state AND NOT (OLD.state='REQUESTED' AND NEW.state='PROCESSING') THEN
        RAISE EXCEPTION 'Invalid settlement transition' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_settlement_operation_guard BEFORE INSERT OR UPDATE OR DELETE ON settlement_operations
    FOR EACH ROW EXECUTE FUNCTION og_guard_settlement_operation();
COMMENT ON TABLE payments IS 'V14 legacy aggregate. UC83 intents/components/operations are canonical; do not double-write money.';

CREATE FUNCTION og_guard_fund_component() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE o orders; expected NUMERIC;
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
    SELECT * INTO STRICT o FROM orders WHERE order_id=NEW.order_id FOR UPDATE;
    expected:=CASE NEW.component_type
        WHEN 'GOODS_CASH' THEN o.subtotal-o.voucher_discount_amount-o.points_discount_amount
        WHEN 'SHIPPING_CASH' THEN o.shipping_fee-o.shipping_discount_amount
        WHEN 'PLATFORM_GOODS_SUBSIDY' THEN o.voucher_discount_amount+o.points_discount_amount
        WHEN 'PLATFORM_SHIPPING_SUBSIDY' THEN o.shipping_discount_amount
        WHEN 'LATE_PAYMENT_CASH' THEN o.total_amount END;
    IF o.workflow_model<>'UC83' OR NEW.confirmed_amount<>expected
        OR NEW.reserved_amount<>0 OR NEW.refunded_amount<>0 OR NEW.released_amount<>0 OR NEW.version<>0
        OR (NEW.component_type='LATE_PAYMENT_CASH' AND NOT EXISTS(
            SELECT 1 FROM payment_confirmations c JOIN payment_intents i USING(intent_id)
            WHERE c.confirmation_id=NEW.late_confirmation_id AND c.disposition='RECONCILIATION'
                AND i.group_id=o.group_id))
        OR (NEW.component_type LIKE 'PLATFORM_%' AND o.total_amount>0 AND NOT EXISTS(
            SELECT 1 FROM payment_allocations WHERE order_id=o.order_id)) THEN
        RAISE EXCEPTION 'Fund component must equal its confirmed order source' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_fund_component_guard BEFORE INSERT OR UPDATE OR DELETE ON order_fund_components
    FOR EACH ROW EXECUTE FUNCTION og_guard_fund_component();
CREATE TRIGGER trg_money_hold_retained BEFORE DELETE ON money_holds
    FOR EACH ROW EXECUTE FUNCTION og_reject_history_mutation();
CREATE FUNCTION og_validate_intent_confirmation() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    IF EXISTS(SELECT 1 FROM payment_intents i WHERE i.intent_id=NEW.intent_id AND i.state='CONFIRMED'
        AND NOT EXISTS(SELECT 1 FROM payment_confirmations c WHERE c.intent_id=i.intent_id AND disposition='ALLOCATABLE')) THEN
        RAISE EXCEPTION 'Confirmed intent needs verified confirmation' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER trg_intent_confirmation_proof AFTER INSERT OR UPDATE ON payment_intents
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION og_validate_intent_confirmation();
