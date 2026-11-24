SET LOCAL lock_timeout='10s';
SET LOCAL statement_timeout='120s';
SELECT pg_advisory_xact_lock(700059);

ALTER TABLE public.seller_buyer_blocks
  ADD COLUMN IF NOT EXISTS block_scope VARCHAR(20) NOT NULL DEFAULT 'ALL';

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'chk_seller_buyer_blocks_scope'
      AND conrelid = 'public.seller_buyer_blocks'::regclass
  ) THEN
    ALTER TABLE public.seller_buyer_blocks
      ADD CONSTRAINT chk_seller_buyer_blocks_scope
      CHECK (block_scope IN ('CHAT', 'PURCHASE', 'ALL'));
  END IF;
END $$;

CREATE OR REPLACE FUNCTION public.og31_chat_permission(buyer bigint, seller bigint, sender bigint, product bigint) RETURNS boolean
LANGUAGE plpgsql VOLATILE SET search_path=public,og_compat AS $$
BEGIN
  PERFORM 1 FROM public.users WHERE record_type='users' AND user_id IN(buyer,seller) ORDER BY user_id FOR UPDATE;
  IF sender NOT IN(buyer,seller) OR public.og28_restricted(sender,'CHAT')
    OR public.og_has_role(sender,'KTV') OR public.og_has_role(sender,'ADMIN')
    OR NOT(public.og_has_role(sender,'BUYER') OR public.og_has_role(sender,'SELLER')) THEN RETURN false; END IF;
  IF EXISTS(SELECT 1 FROM public.seller_buyer_blocks WHERE buyer_id=buyer AND seller_id=seller AND unblocked_at IS NULL AND block_scope IN ('CHAT','ALL'))
    AND NOT EXISTS(SELECT 1 FROM og_compat.orders o JOIN og_compat.order_items i USING(order_id)
      WHERE o.buyer_id=buyer AND o.seller_id=seller AND i.product_id=product AND
        (o.status NOT IN('COMPLETED','CANCELLED','REFUNDED') OR EXISTS(
          SELECT 1 FROM og_compat.cases c WHERE c.order_id=o.order_id AND c.state NOT IN('CLOSED','REJECTED'))))
    THEN RETURN false; END IF;
  RETURN true;
END; $$;

CREATE OR REPLACE FUNCTION public.og31_guard_blocked_order() RETURNS trigger LANGUAGE plpgsql SET search_path=public,og_compat AS $$
BEGIN
  IF NEW.record_type='orders' THEN
    PERFORM 1 FROM public.users WHERE record_type='users' AND user_id IN(NEW.buyer_id,NEW.seller_id) ORDER BY user_id FOR UPDATE;
    IF EXISTS(SELECT 1 FROM public.seller_buyer_blocks WHERE buyer_id=NEW.buyer_id AND seller_id=NEW.seller_id AND unblocked_at IS NULL AND block_scope IN ('PURCHASE','ALL')) THEN
      RAISE EXCEPTION 'Buyer blocked from new seller transactions' USING ERRCODE='23514'; END IF;
  END IF; RETURN NEW;
END; $$;

CREATE OR REPLACE FUNCTION public.og31_guard_offer_command() RETURNS trigger LANGUAGE plpgsql SET search_path=public,og_compat AS $$
DECLARE c public.conversations; p public.products;
BEGIN
  IF NEW.creation_command IS NULL THEN RETURN NEW; END IF;
  IF NEW.creation_request IS NULL OR jsonb_typeof(NEW.creation_request)<>'object' OR NEW.product_version IS NULL OR NEW.product_version<0 THEN
    RAISE EXCEPTION 'Managed offer requires command and listing snapshot' USING ERRCODE='23514'; END IF;
  IF TG_OP='INSERT' AND NEW.status<>'PENDING' THEN RAISE EXCEPTION 'New offer must be pending' USING ERRCODE='23514'; END IF;
  SELECT * INTO STRICT c FROM public.conversations WHERE conversation_id=NEW.conversation_id;
  IF TG_OP='INSERT' OR (NEW.status='ACCEPTED' AND OLD.status='PENDING') THEN
    PERFORM 1 FROM public.users WHERE record_type='users' AND user_id IN(c.buyer_id,c.seller_id) ORDER BY user_id FOR UPDATE;
    SELECT * INTO STRICT p FROM public.products WHERE product_id=c.product_id;
    IF p.status<>'ACTIVE' OR p.deleted_at IS NOT NULL OR p.quantity_available<1
      OR NEW.product_version<>p.version OR NEW.product_revision_id IS DISTINCT FROM p.public_revision_id
      OR public.og28_restricted(c.buyer_id,'BUY_ALL') OR public.og28_restricted(c.seller_id,'NEW_TRANSACTIONS')
      OR public.og28_restricted(c.buyer_id,'CHAT') OR public.og28_restricted(c.seller_id,'CHAT')
      OR (p.requires_buyer_ekyc AND public.og28_restricted(c.buyer_id,'BUY_EKYC'))
      OR (NEW.responded_by IS NOT NULL AND public.og28_restricted(NEW.responded_by,'CHAT'))
      OR EXISTS(SELECT 1 FROM public.seller_buyer_blocks WHERE buyer_id=c.buyer_id AND seller_id=c.seller_id AND unblocked_at IS NULL AND block_scope IN ('PURCHASE','ALL')) THEN
      RAISE EXCEPTION 'Offer parties or listing no longer eligible' USING ERRCODE='23514'; END IF;
  END IF;
  IF TG_OP='UPDATE' THEN
    IF NEW.offered_item_price IS DISTINCT FROM OLD.offered_item_price OR NEW.proposer_id<>OLD.proposer_id
      OR NEW.product_version<>OLD.product_version OR NEW.product_revision_id IS DISTINCT FROM OLD.product_revision_id
      OR NEW.creation_command<>OLD.creation_command OR NEW.creation_request IS DISTINCT FROM OLD.creation_request
      OR NEW.expires_at IS DISTINCT FROM OLD.expires_at
      OR NEW.fee_policy_id<>OLD.fee_policy_id OR NEW.buyer_system_fee<>OLD.buyer_system_fee OR NEW.seller_system_fee<>OLD.seller_system_fee
      OR NEW.buyer_subtotal<>OLD.buyer_subtotal OR NEW.seller_proceeds<>OLD.seller_proceeds
      OR NEW.conversation_id<>OLD.conversation_id OR NEW.parent_offer_id IS DISTINCT FROM OLD.parent_offer_id
      OR (OLD.checkout_request IS NOT NULL AND NEW.checkout_request IS DISTINCT FROM OLD.checkout_request) THEN
      RAISE EXCEPTION 'Offer snapshot immutable' USING ERRCODE='23514'; END IF;
    IF NEW.status<>OLD.status AND (OLD.status<>'PENDING' OR NEW.version<>OLD.version+1) THEN
      RAISE EXCEPTION 'Illegal offer transition or version' USING ERRCODE='23514'; END IF;
    IF NEW.status='ACCEPTED' AND OLD.status='PENDING' AND OLD.expires_at IS NOT NULL AND OLD.expires_at<=CURRENT_TIMESTAMP THEN
      RAISE EXCEPTION 'Offer expired before acceptance' USING ERRCODE='23514'; END IF;
  END IF;
  RETURN NEW;
END; $$;
