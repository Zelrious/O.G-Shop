-- Phase 1 keeps legacy checkout/eKYC adapters. MANUAL_V27 is an immutable
-- snapshot of a real manual review, never a fabricated UC83 AI/media result.
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '120s';
SELECT pg_advisory_xact_lock(700048);

ALTER TABLE public.products ADD COLUMN manual_listing_fee_required boolean NOT NULL DEFAULT false;
COMMENT ON COLUMN public.products.manual_listing_fee_required IS
    'Enabled by the V27 submit workflow; old listings are not asserted paid or retroactively charged.';

DO $migration$
DECLARE definition text;
BEGIN
    definition:=pg_get_functiondef('public.og70_validate_row(text,jsonb,boolean)'::regprocedure);
    EXECUTE replace(definition,'FUNCTION public.og70_validate_row(', 'FUNCTION public.og27_validate_existing_row(');

    definition:=pg_get_functiondef('public.og70_row_listing_fee_assessments_og_compute_listi_7358e0ac72(jsonb,jsonb,text,text,integer,text[])'::regprocedure);
    IF strpos(definition, 'r.provenance<>''UC83''')=0 THEN RAISE EXCEPTION 'Unexpected fee adapter'; END IF;
    EXECUTE replace(definition, 'r.provenance<>''UC83''', 'r.provenance NOT IN (''UC83'',''MANUAL_V27'')');

    definition:=pg_get_functiondef('public.og70_row_product_moderation_decisions_og_guard_ca_d058f2f3c5(jsonb,jsonb,text,text,integer,text[])'::regprocedure);
    IF strpos(definition, 'r.provenance=''UC83''')=0 THEN RAISE EXCEPTION 'Unexpected moderation adapter'; END IF;
    definition:=replace(definition, 'r.provenance=''UC83''', 'r.provenance IN (''UC83'',''MANUAL_V27'')');
    definition:=replace(definition, 'NOT og_has_role(NEW.reviewer_id,''KTV'')',
        'NOT (og_has_role(NEW.reviewer_id,''KTV'') OR og_has_role(NEW.reviewer_id,''ADMIN''))');
    EXECUTE definition;

    definition:=pg_get_functiondef('public.og70_row_reward_ledger_og_apply_reward_entry(jsonb,jsonb,text,text,integer,text[])'::regprocedure);
    EXECUTE replace(definition,'FUNCTION public.og70_row_reward_ledger_og_apply_reward_entry(',
        'FUNCTION public.og27_apply_existing_reward(');
END;
$migration$;

CREATE OR REPLACE FUNCTION public.og70_validate_row(s text,j jsonb,skip_not_valid boolean DEFAULT false)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE r og_compat.reward_ledger;
BEGIN
    IF s='product_revisions' AND j->>'provenance'='MANUAL_V27' THEN
        IF j->>'state' IS DISTINCT FROM 'SUBMITTED' OR j->>'submitted_at' IS NULL
            OR jsonb_typeof(j->'content_snapshot') IS DISTINCT FROM 'object'
            OR NOT (j->'content_snapshot' ?& ARRAY['contentRevision','version','title','description','media'])
            OR jsonb_typeof(j->'content_snapshot'->'media') IS DISTINCT FROM 'array' THEN
            RAISE EXCEPTION 'Manual review requires a complete immutable submitted snapshot' USING ERRCODE='23514';
        END IF;
        -- Existing structural/FK rules still run. UC83-specific return options
        -- and digest-bearing revision_media are not claimed by this adapter.
        PERFORM public.og27_validate_existing_row(s,
            j||jsonb_build_object('provenance','LEGACY_MIGRATION','state','LEGACY'),skip_not_valid);
    ELSIF s='reward_ledger' AND j->>'entry_type'='CREDIT_FIRST_LISTING' THEN
        r:=jsonb_populate_record(NULL::og_compat.reward_ledger,j);
        IF r.entry_id IS NULL OR r.user_id IS NULL OR r.created_at IS NULL
            OR r.points_delta IS DISTINCT FROM 10000 OR r.policy_id IS NOT NULL
            OR r.order_id IS NOT NULL OR r.group_id IS NOT NULL OR r.actor_id IS NOT NULL
            OR r.reason IS NULL OR r.reason !~ '^FIRST_LISTING:[1-9][0-9]*$'
            OR r.command_key IS DISTINCT FROM 'FIRST_LISTING:'||r.user_id THEN
            RAISE EXCEPTION 'Invalid first listing reward' USING ERRCODE='23514';
        END IF;
    ELSE
        PERFORM public.og27_validate_existing_row(s,j,skip_not_valid);
    END IF;
END;
$$;

CREATE FUNCTION public.og27_guard_manual_listing() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='UPDATE' AND OLD.manual_listing_fee_required AND NOT NEW.manual_listing_fee_required THEN
        RAISE EXCEPTION 'Manual listing fee enforcement cannot be removed' USING ERRCODE='23514';
    END IF;
    IF NEW.manual_listing_fee_required AND NEW.status='ACTIVE' AND (
        NEW.current_revision_id IS DISTINCT FROM NEW.public_revision_id OR NOT EXISTS (
            SELECT 1 FROM og_compat.product_revisions r
            JOIN og_compat.listing_fee_assessments a USING(revision_id)
            JOIN og_compat.product_moderation_decisions d ON d.decision_id=a.decision_id
            WHERE r.revision_id=NEW.current_revision_id AND r.product_id=NEW.product_id
              AND r.provenance='MANUAL_V27' AND r.state='SUBMITTED'
              AND r.listed_unit_price=NEW.listed_price AND r.quantity=NEW.quantity_total
              AND (r.content_snapshot->>'contentRevision')::bigint=NEW.content_revision
              AND d.decision='APPROVED' AND d.decision_content_revision=NEW.content_revision
              AND (a.amount_due=0 OR EXISTS (
                  SELECT 1 FROM og_compat.listing_fee_charges c
                  JOIN og_compat.listing_fee_receipts receipt USING(charge_id)
                  WHERE c.assessment_id=a.assessment_id AND c.state='CONFIRMED' AND receipt.amount=a.amount_due))
        ) OR NOT EXISTS(SELECT 1 FROM og_compat.users WHERE user_id=NEW.seller_id AND status='ACTIVE' AND deleted_at IS NULL)
        OR NOT og_has_role(NEW.seller_id,'SELLER')
    ) THEN
        RAISE EXCEPTION 'Manual listing needs current approval, confirmed fee and active seller' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER og27_manual_listing_guard BEFORE INSERT OR UPDATE ON public.products
    FOR EACH ROW EXECUTE FUNCTION public.og27_guard_manual_listing();

CREATE UNIQUE INDEX uq_first_listing_reward_user ON public.reward_ledger(user_id)
    WHERE record_type='reward_ledger' AND entry_type='CREDIT_FIRST_LISTING';
CREATE UNIQUE INDEX uq_listing_notification_recipient ON public.notifications(event_id,user_id)
    WHERE record_type='notifications' AND event_id IS NOT NULL;

CREATE OR REPLACE FUNCTION public.og70_row_reward_ledger_og_apply_reward_entry(
    p_new jsonb,p_old jsonb,p_op text,p_phase text,p_depth integer,p_args text[])
RETURNS jsonb LANGUAGE plpgsql SET search_path=public,og_compat AS $$
DECLARE n og_compat.reward_ledger;
BEGIN
    n:=jsonb_populate_record(NULL::og_compat.reward_ledger,p_new);
    IF n.entry_type='CREDIT_FIRST_LISTING' AND NOT EXISTS (
        SELECT 1 FROM public.products p JOIN og_compat.product_revisions r ON r.revision_id=p.public_revision_id
        JOIN og_compat.listing_fee_assessments a USING(revision_id)
        WHERE p.product_id=split_part(n.reason,':',2)::bigint AND p.seller_id=n.user_id
          AND p.status='ACTIVE' AND p.deleted_at IS NULL AND p.manual_listing_fee_required
          AND r.provenance='MANUAL_V27' AND r.state='SUBMITTED'
          AND (a.amount_due=0 OR EXISTS(SELECT 1 FROM og_compat.listing_fee_charges c
              JOIN og_compat.listing_fee_receipts receipt USING(charge_id)
              WHERE c.assessment_id=a.assessment_id AND c.state='CONFIRMED'))
    ) THEN
        RAISE EXCEPTION 'First listing reward requires an approved open listing' USING ERRCODE='23514';
    END IF;
    RETURN public.og27_apply_existing_reward(p_new,p_old,p_op,p_phase,p_depth,p_args);
END;
$$;
