-- A confirmed issue is identified by its original business object and claimant.
CREATE FUNCTION public.og_system_issue_origin(c og_compat.cases) RETURNS text
LANGUAGE sql IMMUTABLE STRICT AS $$
    SELECT c.created_by::text || '/' || COALESCE('payment/' || c.payment_intent_id,
        'charge/' || c.listing_charge_id, 'order/' || c.order_id, 'product/' || c.product_id,
        'account/' || c.target_user_id)
$$;

CREATE TABLE public.system_issue_compensation_claims (
    incident_key text PRIMARY KEY,
    case_id bigint NOT NULL
);

INSERT INTO public.system_issue_compensation_claims(incident_key,case_id)
SELECT public.og_system_issue_origin(c), min(c.case_id)
FROM og_compat.cases c JOIN og_compat.case_events e USING(case_id)
WHERE c.case_type='SYSTEM_ISSUE' AND e.event_type='COMPENSATION'
  AND e.metadata->>'compensationType' IN ('VOUCHER','DEADLINE_EXTENSION')
GROUP BY public.og_system_issue_origin(c);
