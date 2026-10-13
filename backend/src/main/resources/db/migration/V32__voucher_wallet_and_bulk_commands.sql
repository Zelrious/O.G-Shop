SET LOCAL lock_timeout='10s';
SET LOCAL statement_timeout='120s';
SELECT pg_advisory_xact_lock(700050);

-- Legacy campaigns remain private until an Admin explicitly configures a wallet policy.
ALTER TABLE public.vouchers ADD COLUMN wallet_enabled boolean NOT NULL DEFAULT false;
ALTER TABLE public.orders ADD COLUMN wallet_grant_id uuid REFERENCES public.voucher_grants(grant_id);
CREATE UNIQUE INDEX og32_order_wallet_grant ON public.orders(wallet_grant_id) WHERE wallet_grant_id IS NOT NULL;
CREATE UNIQUE INDEX og32_voucher_command ON public.audit_logs(command_key)
 WHERE record_type='audit_logs' AND entity_type='VOUCHER' AND command_key IS NOT NULL;

DO $$
DECLARE definition text;
BEGIN
 definition:=pg_get_functiondef('public.og70_row_voucher_grants_og_guard_grant_history(jsonb,jsonb,text,text,integer,text[])'::regprocedure);
 EXECUTE replace(definition,'FUNCTION public.og70_row_voucher_grants_og_guard_grant_history(', 'FUNCTION public.og32_existing_grant_history(');
END; $$;
CREATE OR REPLACE FUNCTION public.og70_row_voucher_grants_og_guard_grant_history(p_new jsonb,p_old jsonb,p_op text,p_phase text,p_depth integer,p_args text[])
RETURNS jsonb LANGUAGE plpgsql SET search_path=public,og_compat AS $$
BEGIN
 -- og70 uses its own logical-hook depth, separate from physical trigger depth.
 -- Only the validated nested order trigger may bridge buy-now redemption.
 IF p_op='UPDATE' AND p_old->>'state'='AVAILABLE' AND p_new->>'state'='REDEEMED'
   AND pg_trigger_depth()>=2 AND EXISTS(SELECT 1 FROM public.orders o
    WHERE o.wallet_grant_id=(p_old->>'grant_id')::uuid AND o.buyer_id=(p_old->>'user_id')::bigint)
 THEN RETURN public.og32_existing_grant_history(p_new,p_old,p_op,p_phase,greatest(2,p_depth),p_args); END IF;
 RETURN public.og32_existing_grant_history(p_new,p_old,p_op,p_phase,p_depth,p_args);
END; $$;

CREATE FUNCTION public.og32_voucher_audit_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.entity_type='VOUCHER' THEN RAISE EXCEPTION 'Voucher command receipts are immutable' USING ERRCODE='23514'; END IF;
 RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END; $$;
CREATE TRIGGER og32_voucher_audit_history BEFORE UPDATE OR DELETE ON public.audit_logs FOR EACH ROW EXECUTE FUNCTION public.og32_voucher_audit_history();

CREATE FUNCTION public.og32_guard_grant() RETURNS trigger LANGUAGE plpgsql SET search_path=public,og_compat AS $$
DECLARE v public.vouchers; r public.vouchers;
BEGIN
 SELECT * INTO STRICT r FROM public.vouchers WHERE source_voucher_revisions_revision_id=NEW.revision_id;
 SELECT * INTO STRICT v FROM public.vouchers WHERE voucher_id=r.parent_voucher_id FOR UPDATE;
 IF v.wallet_enabled THEN
   IF NOT v.is_active OR clock_timestamp()<v.start_time OR clock_timestamp()>=v.end_time
     OR NEW.revision_id IS DISTINCT FROM (SELECT max(source_voucher_revisions_revision_id) FROM public.vouchers WHERE parent_voucher_id=v.voucher_id)
     OR (NEW.source='SELF_CLAIM' AND coalesce((r.policy_snapshot->>'selfClaim')::boolean,false)=false)
     OR NOT EXISTS(SELECT 1 FROM public.users u WHERE u.record_type='users' AND u.user_id=NEW.user_id
       AND u.deleted_at IS NULL AND u.status='ACTIVE' AND public.og28_effective_state(u.user_id)='ACTIVE'
       AND (public.og_has_role(u.user_id,'BUYER') OR public.og_has_role(u.user_id,'SELLER'))
       AND NOT public.og_has_role(u.user_id,'ADMIN') AND NOT public.og_has_role(u.user_id,'KTV'))
     OR (SELECT count(*) FROM public.voucher_grants g JOIN public.vouchers p ON p.source_voucher_revisions_revision_id=g.revision_id
       WHERE p.parent_voucher_id=v.voucher_id AND g.user_id=NEW.user_id)>=v.max_usage_per_user
     OR (v.total_usage_limit IS NOT NULL AND (SELECT count(*) FROM public.voucher_grants g JOIN public.vouchers p
       ON p.source_voucher_revisions_revision_id=g.revision_id WHERE p.parent_voucher_id=v.voucher_id)>=v.total_usage_limit)
   THEN RAISE EXCEPTION 'Wallet grant eligibility/issuance limit changed' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER aa_og32_grant BEFORE INSERT ON public.voucher_grants FOR EACH ROW EXECUTE FUNCTION public.og32_guard_grant();

-- Consume a pre-existing owned grant in the current buy-now adapter, inside the order transaction.
-- The native canonical checkout_redemptions path and its immutable history remain unchanged.
CREATE FUNCTION public.og32_order_grant() RETURNS trigger LANGUAGE plpgsql SET search_path=public,og_compat AS $$
DECLARE g public.voucher_grants; r public.vouchers; v public.vouchers; base numeric; amount numeric;
BEGIN
 IF TG_OP='UPDATE' AND OLD.wallet_grant_id IS NOT NULL AND NEW.wallet_grant_id IS DISTINCT FROM OLD.wallet_grant_id THEN
   RAISE EXCEPTION 'Consumed voucher cannot be detached or restored' USING ERRCODE='23514';
 END IF;
 IF NEW.wallet_grant_id IS NOT NULL AND (TG_OP='INSERT' OR OLD.wallet_grant_id IS NULL) THEN
   SELECT * INTO STRICT g FROM public.voucher_grants WHERE grant_id=NEW.wallet_grant_id FOR UPDATE;
   SELECT * INTO STRICT r FROM public.vouchers WHERE source_voucher_revisions_revision_id=g.revision_id;
   SELECT * INTO STRICT v FROM public.vouchers WHERE voucher_id=r.parent_voucher_id;
   base:=CASE WHEN r.benefit_component='SHIPPING' THEN NEW.shipping_fee ELSE NEW.subtotal END;
   amount:=least(base,CASE WHEN r.discount_type='FIXED_AMOUNT' THEN r.discount_value ELSE round(base*r.discount_value/100,0) END,coalesce(r.max_discount_amount,base));
   IF g.user_id IS DISTINCT FROM NEW.buyer_id OR g.state<>'AVAILABLE' OR g.expires_at<=clock_timestamp()
     OR r.valid_from>clock_timestamp() OR EXISTS(SELECT 1 FROM og_compat.voucher_revision_revocations WHERE revision_id=g.revision_id)
     OR NEW.subtotal<r.min_eligible_amount OR amount<=0 OR amount<>NEW.voucher_discount_amount+NEW.shipping_discount_amount
     OR (r.scope='PRODUCTS' AND NOT EXISTS(SELECT 1 FROM og_compat.order_items i JOIN public.voucher_products p USING(product_id) WHERE i.order_id=NEW.order_id AND p.revision_id=g.revision_id))
     OR NOT EXISTS(SELECT 1 FROM og_compat.order_vouchers s WHERE s.order_id=NEW.order_id AND s.voucher_id=v.voucher_id AND s.discount_amount=amount)
   THEN RAISE EXCEPTION 'Voucher owner/state/policy differs from order' USING ERRCODE='23514'; END IF;
   UPDATE public.voucher_grants SET state='REDEEMED' WHERE grant_id=g.grant_id;
   INSERT INTO og_compat.voucher_grant_events(event_id,grant_id,actor_id,event_type,reason,command_key)
    VALUES(gen_random_uuid(),g.grant_id,NEW.buyer_id,'REDEEMED','Áp dụng khi đặt đơn','wallet-order:'||NEW.order_id);
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER og32_order_grant AFTER INSERT OR UPDATE ON public.orders FOR EACH ROW EXECUTE FUNCTION public.og32_order_grant();
