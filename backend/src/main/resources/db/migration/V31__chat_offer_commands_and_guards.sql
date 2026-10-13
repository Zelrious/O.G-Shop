SET LOCAL lock_timeout='10s';
SET LOCAL statement_timeout='120s';
SELECT pg_advisory_xact_lock(700049);

ALTER TABLE public.offers ADD COLUMN creation_command uuid, ADD COLUMN creation_request jsonb,
  ADD COLUMN decision_command uuid, ADD COLUMN decision_request jsonb,
  ADD COLUMN product_version bigint, ADD COLUMN product_revision_id bigint,
  ADD COLUMN checkout_request varchar(64);
CREATE UNIQUE INDEX og31_offer_creation ON public.offers(creation_command) WHERE creation_command IS NOT NULL;
CREATE UNIQUE INDEX og31_offer_decision ON public.offers(decision_command) WHERE decision_command IS NOT NULL;

CREATE FUNCTION public.og31_chat_permission(buyer bigint,seller bigint,sender bigint,product bigint) RETURNS boolean
LANGUAGE plpgsql VOLATILE SET search_path=public,og_compat AS $$
BEGIN
  PERFORM 1 FROM public.users WHERE record_type='users' AND user_id IN(buyer,seller) ORDER BY user_id FOR UPDATE;
  IF sender NOT IN(buyer,seller) OR public.og28_restricted(sender,'CHAT')
    OR public.og_has_role(sender,'KTV') OR public.og_has_role(sender,'ADMIN')
    OR NOT(public.og_has_role(sender,'BUYER') OR public.og_has_role(sender,'SELLER')) THEN RETURN false; END IF;
  IF EXISTS(SELECT 1 FROM public.seller_buyer_blocks WHERE buyer_id=buyer AND seller_id=seller AND unblocked_at IS NULL)
    AND NOT EXISTS(SELECT 1 FROM og_compat.orders o JOIN og_compat.order_items i USING(order_id)
      WHERE o.buyer_id=buyer AND o.seller_id=seller AND i.product_id=product AND
        (o.status NOT IN('COMPLETED','CANCELLED','REFUNDED') OR EXISTS(
          SELECT 1 FROM og_compat.cases c WHERE c.order_id=o.order_id AND c.state NOT IN('CLOSED','REJECTED'))))
    THEN RETURN false; END IF;
  RETURN true;
END; $$;

CREATE FUNCTION public.og31_guard_message() RETURNS trigger LANGUAGE plpgsql SET search_path=public,og_compat AS $$
DECLARE c public.conversations; sz bigint;
BEGIN
  IF TG_OP='INSERT' THEN
    SELECT * INTO STRICT c FROM public.conversations WHERE conversation_id=NEW.conversation_id;
    IF NOT public.og31_chat_permission(c.buyer_id,c.seller_id,NEW.sender_id,c.product_id) THEN
      RAISE EXCEPTION 'Chat sender restricted or blocked outside old obligations' USING ERRCODE='23514'; END IF;
    IF NEW.message_type='TEXT' AND (length(btrim(NEW.content))=0 OR length(NEW.content)>2000) THEN
      RAISE EXCEPTION 'Invalid chat text' USING ERRCODE='23514'; END IF;
    IF NEW.media_metadata->>'managedBy'='CHAT_V31' THEN
      sz:=(NEW.media_metadata->>'bytes')::bigint;
      IF sz IS NULL OR sz<=0 OR NEW.cloudinary_public_id IS NULL OR NEW.media_expires_at IS NULL
        OR length(NEW.content)>2000
        OR (NEW.message_type='IMAGE' AND sz>5242880)
        OR (NEW.message_type='VIDEO' AND (sz>20971520 OR (NEW.media_metadata->>'duration')::numeric>30))
        THEN RAISE EXCEPTION 'Invalid managed chat media' USING ERRCODE='23514'; END IF;
    END IF;
  END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER og31_message_permission BEFORE INSERT ON public.messages FOR EACH ROW EXECUTE FUNCTION public.og31_guard_message();

CREATE FUNCTION public.og31_guard_block() RETURNS trigger LANGUAGE plpgsql SET search_path=public,og_compat AS $$
BEGIN
  PERFORM 1 FROM public.users WHERE record_type='users' AND user_id IN(NEW.buyer_id,NEW.seller_id) ORDER BY user_id FOR UPDATE;
  IF NOT public.og_has_role(NEW.seller_id,'SELLER') OR NOT(public.og_has_role(NEW.buyer_id,'BUYER') OR public.og_has_role(NEW.buyer_id,'SELLER'))
    OR public.og_has_role(NEW.buyer_id,'KTV') OR public.og_has_role(NEW.buyer_id,'ADMIN')
    OR public.og_has_role(NEW.seller_id,'KTV') OR public.og_has_role(NEW.seller_id,'ADMIN') THEN
    RAISE EXCEPTION 'Invalid seller block target' USING ERRCODE='23514'; END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER og31_block_permission BEFORE INSERT OR UPDATE ON public.seller_buyer_blocks FOR EACH ROW EXECUTE FUNCTION public.og31_guard_block();

CREATE FUNCTION public.og31_guard_blocked_order() RETURNS trigger LANGUAGE plpgsql SET search_path=public,og_compat AS $$
BEGIN
  IF NEW.record_type='orders' THEN
    PERFORM 1 FROM public.users WHERE record_type='users' AND user_id IN(NEW.buyer_id,NEW.seller_id) ORDER BY user_id FOR UPDATE;
    IF EXISTS(SELECT 1 FROM public.seller_buyer_blocks WHERE buyer_id=NEW.buyer_id AND seller_id=NEW.seller_id AND unblocked_at IS NULL) THEN
      RAISE EXCEPTION 'Buyer blocked from new seller transactions' USING ERRCODE='23514'; END IF;
  END IF; RETURN NEW;
END; $$;
CREATE TRIGGER og31_order_block BEFORE INSERT ON public.orders FOR EACH ROW EXECUTE FUNCTION public.og31_guard_blocked_order();

CREATE FUNCTION public.og31_guard_read_cursor() RETURNS trigger LANGUAGE plpgsql SET search_path=public,og_compat AS $$
DECLARE p jsonb; old_cursor bigint; next_cursor bigint;
BEGIN
  FOR p IN SELECT value FROM jsonb_array_elements(NEW.participant_state) LOOP
    IF (p->>'user_id')::bigint NOT IN(NEW.buyer_id,NEW.seller_id) THEN
      RAISE EXCEPTION 'Conversation participant must be a party' USING ERRCODE='23514'; END IF;
    IF TG_OP='UPDATE' THEN
      SELECT (value->>'last_read_message_id')::bigint INTO old_cursor FROM jsonb_array_elements(OLD.participant_state) WHERE value->>'user_id'=p->>'user_id';
      next_cursor:=(p->>'last_read_message_id')::bigint;
      IF coalesce(next_cursor,0)<coalesce(old_cursor,0) THEN RAISE EXCEPTION 'Read cursor cannot decrease' USING ERRCODE='23514'; END IF;
    END IF;
  END LOOP; RETURN NEW;
END; $$;
CREATE TRIGGER og31_read_cursor BEFORE INSERT OR UPDATE OF participant_state ON public.conversations FOR EACH ROW EXECUTE FUNCTION public.og31_guard_read_cursor();

CREATE FUNCTION public.og31_guard_media_quota() RETURNS trigger LANGUAGE plpgsql SET search_path=public,og_compat AS $$
DECLARE q jsonb;
BEGIN
  FOR q IN SELECT value FROM jsonb_array_elements(NEW.media_quota_usage) LOOP
    IF coalesce((q->>'sender_id')::bigint,0) NOT IN(NEW.buyer_id,NEW.seller_id)
      OR coalesce((q->>'images_sent')::int,-1) NOT BETWEEN 0 AND 5
      OR coalesce((q->>'videos_sent')::int,-1) NOT BETWEEN 0 AND 1
      OR coalesce((q->>'total_bytes_sent')::bigint,-1) NOT BETWEEN 0 AND 47185920 THEN
      RAISE EXCEPTION 'Invalid chat media quota owner or limits' USING ERRCODE='23514'; END IF;
  END LOOP; RETURN NEW;
END; $$;
CREATE TRIGGER og31_media_quota BEFORE INSERT OR UPDATE OF media_quota_usage ON public.conversations FOR EACH ROW EXECUTE FUNCTION public.og31_guard_media_quota();

CREATE FUNCTION public.og31_guard_offer_command() RETURNS trigger LANGUAGE plpgsql SET search_path=public,og_compat AS $$
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
      OR EXISTS(SELECT 1 FROM public.seller_buyer_blocks WHERE buyer_id=c.buyer_id AND seller_id=c.seller_id AND unblocked_at IS NULL) THEN
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
CREATE TRIGGER og31_offer_command BEFORE INSERT OR UPDATE ON public.offers FOR EACH ROW EXECUTE FUNCTION public.og31_guard_offer_command();

CREATE FUNCTION public.og31_guard_offer_checkout() RETURNS trigger LANGUAGE plpgsql SET search_path=public,og_compat AS $$
DECLARE a public.offers; c public.conversations; o public.orders; p public.products;
BEGIN
  IF NEW.accepted_offer_id IS NULL THEN RETURN NEW; END IF;
  SELECT * INTO STRICT a FROM public.offers WHERE offer_id=NEW.accepted_offer_id FOR UPDATE;
  SELECT * INTO STRICT c FROM public.conversations WHERE conversation_id=a.conversation_id;
  SELECT * INTO STRICT o FROM public.orders WHERE order_id=NEW.order_id;
  IF a.status<>'ACCEPTED' OR c.buyer_id<>o.buyer_id OR c.seller_id<>o.seller_id OR c.product_id<>NEW.product_id
    OR a.offered_item_price<>NEW.agreed_price THEN RAISE EXCEPTION 'Offer price/party mismatch' USING ERRCODE='23514'; END IF;
  IF a.creation_command IS NOT NULL THEN
    SELECT * INTO STRICT p FROM public.products WHERE product_id=NEW.product_id;
    IF a.checkout_request IS NULL OR NEW.quantity<>1 OR a.product_version<>p.version
      OR a.product_revision_id IS DISTINCT FROM p.public_revision_id THEN RAISE EXCEPTION 'Offer checkout source invalid' USING ERRCODE='23514'; END IF;
  END IF; RETURN NEW;
END; $$;
CREATE TRIGGER og31_offer_checkout BEFORE INSERT ON public.order_items FOR EACH ROW EXECUTE FUNCTION public.og31_guard_offer_checkout();

CREATE FUNCTION public.og31_hold_case_media() RETURNS trigger LANGUAGE plpgsql SET search_path=public,og_compat AS $$
BEGIN
  IF NEW.message_id IS NOT NULL AND NEW.record_type IN('cases','reports') THEN
    PERFORM 1 FROM public.messages WHERE message_id=NEW.message_id FOR UPDATE;
    UPDATE public.messages SET media_status='HOLD_FOR_DISPUTE' WHERE message_id=NEW.message_id
      AND message_type IN('IMAGE','VIDEO') AND media_status='ACTIVE' AND media_metadata->>'managedBy'='CHAT_V31';
  END IF; RETURN NEW;
END; $$;
CREATE TRIGGER og31_case_media_hold BEFORE INSERT ON public.cases FOR EACH ROW EXECUTE FUNCTION public.og31_hold_case_media();
