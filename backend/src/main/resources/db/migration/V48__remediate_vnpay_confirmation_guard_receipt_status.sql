-- ============================================================================
-- V48__remediate_vnpay_confirmation_guard_receipt_status.sql
-- Module: Payment / VNPay Confirmation Guard (Task 1.2b - UC83 Remediation)
-- Business Context:
--   1. V18 introduced og_guard_payment_confirmation with:
--      AND r.verification_status='VERIFIED'
--   2. However, V14/V22 ck_receipt_verification_status strictly constrains
--      payment_ipn_raw_receipts.verification_status to:
--      ('UNVERIFIED', 'MALFORMED_REQUEST', 'SIGNATURE_INVALID', 'SIGNATURE_VALID_UNLINKED', 'LINKED')
--      where 'LINKED' represents a verified receipt linked to an attempt.
--   3. This migration updates og_guard_payment_confirmation and
--      og70_row_payment_confirmations_og_guard_payment_confirmation to check:
--      AND r.verification_status IN ('VERIFIED', 'LINKED')
--      thereby harmonizing the confirmation guard with the raw receipt schema.
-- ============================================================================

CREATE OR REPLACE FUNCTION public.og_guard_payment_confirmation()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE i og_compat.payment_intents;
BEGIN
    SELECT * INTO STRICT i FROM og_compat.payment_intents WHERE intent_id=NEW.intent_id FOR UPDATE;
    IF NEW.amount<>i.expected_amount OR NEW.provider<>i.payment_method OR NOT EXISTS(SELECT 1 FROM og_compat.payment_attempts
        WHERE attempt_id=NEW.attempt_id AND intent_id=i.intent_id AND provider=NEW.provider)
        OR (NEW.provider='VNPAY' AND NOT EXISTS(
            SELECT 1 FROM og_compat.payment_ipn_events e JOIN og_compat.payment_ipn_raw_receipts r USING(receipt_id)
            WHERE e.event_id=NEW.ipn_event_id AND e.attempt_id=NEW.attempt_id AND e.intent_id=i.intent_id
                AND r.verification_status IN ('VERIFIED', 'LINKED') AND e.vnp_amount=NEW.amount
                AND e.processing_outcome IN ('APPLIED_SUCCESS','APPLIED_EXPIRED_ORDER','APPLIED_DUPLICATE_CHARGE'))) THEN
        RAISE EXCEPTION 'Confirmation source/amount/reference not verified' USING ERRCODE='23514';
    END IF;
    NEW.confirmed_at:=clock_timestamp();
    IF i.state<>'PENDING' OR NEW.confirmed_at>=i.deadline OR
       (i.group_id IS NOT NULL AND EXISTS(SELECT 1 FROM og_compat.orders WHERE group_id=i.group_id AND status='CANCELLED')) THEN
        NEW.disposition:='RECONCILIATION';
    END IF;
    IF NEW.disposition='ALLOCATABLE' THEN
        UPDATE og_compat.payment_intents SET state='CONFIRMED' WHERE intent_id=i.intent_id;
        IF i.purpose='LISTING_FEE' THEN
            INSERT INTO og_compat.listing_fee_receipts(receipt_id,charge_id,provider,provider_transaction_id,amount,confirmed_at)
                VALUES(NEW.confirmation_id,i.listing_charge_id,NEW.provider,NEW.provider_transaction_id,NEW.amount,NEW.confirmed_at);
        END IF;
    ELSE
        UPDATE og_compat.payment_intents SET state='RECONCILIATION' WHERE intent_id=i.intent_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public.og70_row_payment_confirmations_og_guard_payment_confirmation(p_new jsonb,p_old jsonb,p_op text,p_phase text,p_depth integer,p_args text[])
RETURNS jsonb LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE
    NEW og_compat.payment_confirmations; OLD og_compat.payment_confirmations;
    TG_OP text:=p_op; TG_WHEN text:=p_phase; TG_TABLE_NAME text:='payment_confirmations';
    TG_TABLE_SCHEMA text:='og_compat'; TG_ARGV text[]:=p_args; TG_NARGS integer:=cardinality(p_args);
    i og_compat.payment_intents;
BEGIN
    NEW:=jsonb_populate_record(NULL::og_compat.payment_confirmations,coalesce(p_new,'{}'::jsonb));
    OLD:=jsonb_populate_record(NULL::og_compat.payment_confirmations,coalesce(p_old,'{}'::jsonb));
    SELECT * INTO STRICT i FROM og_compat.payment_intents WHERE intent_id=NEW.intent_id FOR UPDATE;
    IF NEW.amount<>i.expected_amount OR NEW.provider<>i.payment_method OR NOT EXISTS(SELECT 1 FROM og_compat.payment_attempts
        WHERE attempt_id=NEW.attempt_id AND intent_id=i.intent_id AND provider=NEW.provider)
        OR (NEW.provider='VNPAY' AND NOT EXISTS(
            SELECT 1 FROM og_compat.payment_ipn_events e JOIN og_compat.payment_ipn_raw_receipts r USING(receipt_id)
            WHERE e.event_id=NEW.ipn_event_id AND e.attempt_id=NEW.attempt_id AND e.intent_id=i.intent_id
                AND r.verification_status IN ('VERIFIED', 'LINKED') AND e.vnp_amount=NEW.amount
                AND e.processing_outcome IN ('APPLIED_SUCCESS','APPLIED_EXPIRED_ORDER','APPLIED_DUPLICATE_CHARGE'))) THEN
        RAISE EXCEPTION 'Confirmation source/amount/reference not verified' USING ERRCODE='23514';
    END IF;
    NEW.confirmed_at:=clock_timestamp();
    IF i.state<>'PENDING' OR NEW.confirmed_at>=i.deadline OR
       (i.group_id IS NOT NULL AND EXISTS(SELECT 1 FROM og_compat.orders WHERE group_id=i.group_id AND status='CANCELLED')) THEN
        NEW.disposition:='RECONCILIATION';
    END IF;
    IF NEW.disposition='ALLOCATABLE' THEN
        UPDATE og_compat.payment_intents SET state='CONFIRMED' WHERE intent_id=i.intent_id;
        IF i.purpose='LISTING_FEE' THEN
            INSERT INTO og_compat.listing_fee_receipts(receipt_id,charge_id,provider,provider_transaction_id,amount,confirmed_at)
                VALUES(NEW.confirmation_id,i.listing_charge_id,NEW.provider,NEW.provider_transaction_id,NEW.amount,NEW.confirmed_at);
        END IF;
    ELSE
        UPDATE og_compat.payment_intents SET state='RECONCILIATION' WHERE intent_id=i.intent_id;
    END IF;
    RETURN to_jsonb(NEW);
END;
$$;

