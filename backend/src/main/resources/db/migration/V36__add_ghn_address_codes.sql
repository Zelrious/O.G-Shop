-- Migration V36: Add GHN master-data codes to addresses and snapshot codes to orders
-- 1. Extend addresses table for GHN normalized address structure
ALTER TABLE addresses ADD COLUMN IF NOT EXISTS ghn_province_id INTEGER;
ALTER TABLE addresses ADD COLUMN IF NOT EXISTS ghn_district_id INTEGER;
ALTER TABLE addresses ADD COLUMN IF NOT EXISTS ghn_ward_code VARCHAR(20);

-- 2. Extend orders table to snapshot recipient GHN codes for reliable fulfillment
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_ghn_province_id INTEGER;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_ghn_district_id INTEGER;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_ghn_ward_code VARCHAR(20);

-- 3. Update og_compat views to include new fulfillment and address columns
CREATE OR REPLACE VIEW og_compat.addresses AS
SELECT
    p.address_id::bigint AS address_id,
    p.user_id::bigint AS user_id,
    p.recipient_name::character varying(120) AS recipient_name,
    p.phone_number::character varying(20) AS phone_number,
    p.province::character varying(100) AS province,
    p.district::character varying(100) AS district,
    p.ward::character varying(100) AS ward,
    p.detail_address::character varying(255) AS detail_address,
    p.is_default::boolean AS is_default,
    p.created_at::timestamp with time zone AS created_at,
    p.updated_at::timestamp with time zone AS updated_at,
    p.deleted_at::timestamp with time zone AS deleted_at,
    p.ghn_province_id::integer AS ghn_province_id,
    p.ghn_district_id::integer AS ghn_district_id,
    p.ghn_ward_code::character varying(20) AS ghn_ward_code
FROM public.addresses p
WHERE p.record_type = 'addresses';

CREATE OR REPLACE VIEW og_compat.orders AS
SELECT
    p.order_id,
    p.checkout_group_id,
    p.buyer_id,
    p.seller_id,
    p.source_address_id,
    p.shipping_recipient_name,
    p.shipping_phone_number,
    p.shipping_province,
    p.shipping_district,
    p.shipping_ward,
    p.shipping_detail_address,
    p.subtotal,
    p.shipping_fee,
    p.total_amount,
    p.currency,
    p.status,
    p.payment_due_at,
    p.version,
    p.created_at,
    p.updated_at,
    p.completed_at,
    p.cancelled_at,
    p.cancellation_reason,
    p.buyer_system_fee,
    p.seller_system_fee,
    p.seller_proceeds,
    p.voucher_discount_amount,
    p.shipping_discount_amount,
    p.sponsor_type,
    p.workflow_model,
    p.group_id,
    p.return_allowed,
    p.delivery_method,
    p.carrier_snapshot,
    p.handover_day,
    p.points_discount_amount,
    p.paid_confirmed_at,
    p.seller_accept_due_at,
    p.valid_delivered_at,
    p.received_at,
    p.return_due_at,
    p.early_completed_at,
    p.early_completion_warning_version,
    p.purchase_shipping_used_at,
    p.shipping_ghn_province_id::integer AS shipping_ghn_province_id,
    p.shipping_ghn_district_id::integer AS shipping_ghn_district_id,
    p.shipping_ghn_ward_code::character varying(20) AS shipping_ghn_ward_code
FROM public.orders p
WHERE p.record_type = 'orders';

CREATE OR REPLACE VIEW og_compat.shipments AS
SELECT
    p.shipment_id::bigint AS shipment_id,
    p.order_id::bigint AS order_id,
    p.carrier::character varying(100) AS carrier,
    p.tracking_number::character varying(100) AS tracking_number,
    p.status::character varying(20) AS status,
    p.shipped_at::timestamp with time zone AS shipped_at,
    p.delivered_at::timestamp with time zone AS delivered_at,
    p.updated_at::timestamp with time zone AS updated_at,
    p.leg::character varying(15) AS leg,
    p.workflow_model::character varying(20) AS workflow_model,
    p.return_case_id::bigint AS return_case_id,
    p.delivery_method::character varying(20) AS delivery_method,
    p.fee_snapshot::numeric(19,2) AS fee_snapshot,
    p.fee_payer::character varying(10) AS fee_payer,
    p.initiated_at::timestamp with time zone AS initiated_at,
    p.pickup_expected_at::timestamp with time zone AS pickup_expected_at,
    p.pickup_deadline::timestamp with time zone AS pickup_deadline,
    p.first_failure_at::timestamp with time zone AS first_failure_at,
    p.delivery_retry_due_at::timestamp with time zone AS delivery_retry_due_at,
    p.valid_delivery_at::timestamp with time zone AS valid_delivery_at,
    p.otp_digest::character varying(128) AS otp_digest,
    p.otp_expires_at::timestamp with time zone AS otp_expires_at,
    p.otp_attempt_count::integer AS otp_attempt_count,
    p.otp_consumed_at::timestamp with time zone AS otp_consumed_at,
    p.client_order_code::character varying(50) AS client_order_code,
    p.shipping_fee::numeric(19,2) AS shipping_fee,
    p.expected_delivery_at::timestamp with time zone AS expected_delivery_at,
    p.raw_ghn_status::character varying(50) AS raw_ghn_status,
    p.created_at::timestamp with time zone AS created_at,
    p.claim_token::character varying(64) AS claim_token,
    p.claim_expires_at::timestamp with time zone AS claim_expires_at,
    p.incident_notes::text AS incident_notes,
    p.admin_review_required::boolean AS admin_review_required,
    p.next_reconciliation_at::timestamp with time zone AS next_reconciliation_at,
    p.reconciliation_attempts::integer AS reconciliation_attempts
FROM public.shipments p
WHERE p.record_type = 'shipments';

-- 4. Update og70_write_view for addresses and orders persistence
DO $migration$
DECLARE
    def text;
BEGIN
    def := pg_get_functiondef('public.og70_write_view()'::regprocedure);

    -- addresses INSERT
    def := replace(def,
        'deleted_at,record_type) SELECT r.address_id,r.user_id,r.recipient_name,r.phone_number,r.province,r.district,r.ward,r.detail_address,r.is_default,r.created_at,r.updated_at,r.deleted_at,''addresses'' FROM',
        'deleted_at,record_type,ghn_province_id,ghn_district_id,ghn_ward_code) SELECT r.address_id,r.user_id,r.recipient_name,r.phone_number,r.province,r.district,r.ward,r.detail_address,r.is_default,r.created_at,r.updated_at,r.deleted_at,''addresses'',r.ghn_province_id,r.ghn_district_id,r.ghn_ward_code FROM'
    );

    -- addresses UPDATE SET
    def := replace(def,
        'deleted_at=r.deleted_at FROM jsonb_populate_record(NULL::og_compat.addresses,$1) r',
        'deleted_at=r.deleted_at,ghn_province_id=r.ghn_province_id,ghn_district_id=r.ghn_district_id,ghn_ward_code=r.ghn_ward_code FROM jsonb_populate_record(NULL::og_compat.addresses,$1) r'
    );

    -- addresses build_object in UPDATE and DELETE
    def := replace(def,
        $a$''deleted_at'',to_jsonb(t)->''deleted_at'')$a$,
        $a$''deleted_at'',to_jsonb(t)->''deleted_at'',''ghn_province_id'',to_jsonb(t)->''ghn_province_id'',''ghn_district_id'',to_jsonb(t)->''ghn_district_id'',''ghn_ward_code'',to_jsonb(t)->''ghn_ward_code'')$a$
    );

    -- addresses result
    def := replace(def,
        $a$''deleted_at'',stored->''deleted_at'');$a$,
        $a$''deleted_at'',stored->''deleted_at'',''ghn_province_id'',stored->''ghn_province_id'',''ghn_district_id'',stored->''ghn_district_id'',''ghn_ward_code'',stored->''ghn_ward_code'');$a$
    );

    -- orders INSERT
    def := replace(def,
        'purchase_shipping_used_at,record_type) SELECT r.order_id,r.checkout_group_id,r.buyer_id,r.seller_id,r.source_address_id,r.shipping_recipient_name,r.shipping_phone_number,r.shipping_province,r.shipping_district,r.shipping_ward,r.shipping_detail_address,r.subtotal,r.shipping_fee,r.total_amount,r.currency,r.status,r.payment_due_at,r.version,r.created_at,r.updated_at,r.completed_at,r.cancelled_at,r.cancellation_reason,r.buyer_system_fee,r.seller_system_fee,r.seller_proceeds,r.voucher_discount_amount,r.shipping_discount_amount,r.sponsor_type,r.workflow_model,r.group_id,r.return_allowed,r.delivery_method,r.carrier_snapshot,r.handover_day,r.points_discount_amount,r.paid_confirmed_at,r.seller_accept_due_at,r.valid_delivered_at,r.received_at,r.return_due_at,r.early_completed_at,r.early_completion_warning_version,r.purchase_shipping_used_at,''orders'' FROM',
        'purchase_shipping_used_at,record_type,shipping_ghn_province_id,shipping_ghn_district_id,shipping_ghn_ward_code) SELECT r.order_id,r.checkout_group_id,r.buyer_id,r.seller_id,r.source_address_id,r.shipping_recipient_name,r.shipping_phone_number,r.shipping_province,r.shipping_district,r.shipping_ward,r.shipping_detail_address,r.subtotal,r.shipping_fee,r.total_amount,r.currency,r.status,r.payment_due_at,r.version,r.created_at,r.updated_at,r.completed_at,r.cancelled_at,r.cancellation_reason,r.buyer_system_fee,r.seller_system_fee,r.seller_proceeds,r.voucher_discount_amount,r.shipping_discount_amount,r.sponsor_type,r.workflow_model,r.group_id,r.return_allowed,r.delivery_method,r.carrier_snapshot,r.handover_day,r.points_discount_amount,r.paid_confirmed_at,r.seller_accept_due_at,r.valid_delivered_at,r.received_at,r.return_due_at,r.early_completed_at,r.early_completion_warning_version,r.purchase_shipping_used_at,''orders'',r.shipping_ghn_province_id,r.shipping_ghn_district_id,r.shipping_ghn_ward_code FROM'
    );

    -- orders UPDATE SET
    def := replace(def,
        'purchase_shipping_used_at=r.purchase_shipping_used_at FROM jsonb_populate_record(NULL::og_compat.orders,$1) r',
        'purchase_shipping_used_at=r.purchase_shipping_used_at,shipping_ghn_province_id=r.shipping_ghn_province_id,shipping_ghn_district_id=r.shipping_ghn_district_id,shipping_ghn_ward_code=r.shipping_ghn_ward_code FROM jsonb_populate_record(NULL::og_compat.orders,$1) r'
    );

    -- orders build_object in UPDATE and DELETE
    def := replace(def,
        $o$''purchase_shipping_used_at'',to_jsonb(t)->''purchase_shipping_used_at'')$o$,
        $o$''purchase_shipping_used_at'',to_jsonb(t)->''purchase_shipping_used_at'',''shipping_ghn_province_id'',to_jsonb(t)->''shipping_ghn_province_id'',''shipping_ghn_district_id'',to_jsonb(t)->''shipping_ghn_district_id'',''shipping_ghn_ward_code'',to_jsonb(t)->''shipping_ghn_ward_code'')$o$
    );

    -- orders result
    def := replace(def,
        $o$''purchase_shipping_used_at'',stored->''purchase_shipping_used_at'');$o$,
        $o$''purchase_shipping_used_at'',stored->''purchase_shipping_used_at'',''shipping_ghn_province_id'',stored->''shipping_ghn_province_id'',''shipping_ghn_district_id'',stored->''shipping_ghn_district_id'',''shipping_ghn_ward_code'',stored->''shipping_ghn_ward_code'');$o$
    );

    -- shipments INSERT
    def := replace(def,
        'otp_consumed_at,''shipments'') SELECT r.shipment_id,r.order_id,r.carrier,r.tracking_number,r.status,r.shipped_at,r.delivered_at,r.updated_at,r.leg,r.workflow_model,r.return_case_id,r.delivery_method,r.fee_snapshot,r.fee_payer,r.initiated_at,r.pickup_expected_at,r.pickup_deadline,r.first_failure_at,r.delivery_retry_due_at,r.valid_delivery_at,r.otp_digest,r.otp_expires_at,r.otp_attempt_count,r.otp_consumed_at FROM',
        'otp_consumed_at,''shipments'',client_order_code,shipping_fee,expected_delivery_at,raw_ghn_status,created_at,claim_token,claim_expires_at,incident_notes,admin_review_required,next_reconciliation_at,reconciliation_attempts) SELECT r.shipment_id,r.order_id,r.carrier,r.tracking_number,r.status,r.shipped_at,r.delivered_at,r.updated_at,r.leg,r.workflow_model,r.return_case_id,r.delivery_method,r.fee_snapshot,r.fee_payer,r.initiated_at,r.pickup_expected_at,r.pickup_deadline,r.first_failure_at,r.delivery_retry_due_at,r.valid_delivery_at,r.otp_digest,r.otp_expires_at,r.otp_attempt_count,r.otp_consumed_at,r.client_order_code,r.shipping_fee,r.expected_delivery_at,r.raw_ghn_status,r.created_at,r.claim_token,r.claim_expires_at,r.incident_notes,r.admin_review_required,r.next_reconciliation_at,r.reconciliation_attempts FROM'
    );

    -- shipments UPDATE SET
    def := replace(def,
        'otp_consumed_at=r.otp_consumed_at FROM jsonb_populate_record(NULL::og_compat.shipments,$1) r',
        'otp_consumed_at=r.otp_consumed_at,client_order_code=r.client_order_code,shipping_fee=r.shipping_fee,expected_delivery_at=r.expected_delivery_at,raw_ghn_status=r.raw_ghn_status,created_at=r.created_at,claim_token=r.claim_token,claim_expires_at=r.claim_expires_at,incident_notes=r.incident_notes,admin_review_required=r.admin_review_required,next_reconciliation_at=r.next_reconciliation_at,reconciliation_attempts=r.reconciliation_attempts FROM jsonb_populate_record(NULL::og_compat.shipments,$1) r'
    );

    -- shipments build_object in UPDATE and DELETE
    def := replace(def,
        $s$''otp_consumed_at'',to_jsonb(t)->''otp_consumed_at'')$s$,
        $s$''otp_consumed_at'',to_jsonb(t)->''otp_consumed_at'',''client_order_code'',to_jsonb(t)->''client_order_code'',''shipping_fee'',to_jsonb(t)->''shipping_fee'',''expected_delivery_at'',to_jsonb(t)->''expected_delivery_at'',''raw_ghn_status'',to_jsonb(t)->''raw_ghn_status'',''created_at'',to_jsonb(t)->''created_at'',''claim_token'',to_jsonb(t)->''claim_token'',''claim_expires_at'',to_jsonb(t)->''claim_expires_at'',''incident_notes'',to_jsonb(t)->''incident_notes'',''admin_review_required'',to_jsonb(t)->''admin_review_required'',''next_reconciliation_at'',to_jsonb(t)->''next_reconciliation_at'',''reconciliation_attempts'',to_jsonb(t)->''reconciliation_attempts'')$s$
    );

    -- shipments result
    def := replace(def,
        $s$''otp_consumed_at'',stored->''otp_consumed_at'');$s$,
        $s$''otp_consumed_at'',stored->''otp_consumed_at'',''client_order_code'',stored->''client_order_code'',''shipping_fee'',stored->''shipping_fee'',''expected_delivery_at'',stored->''expected_delivery_at'',''raw_ghn_status'',stored->''raw_ghn_status'',''created_at'',stored->''created_at'',''claim_token'',stored->''claim_token'',''claim_expires_at'',stored->''claim_expires_at'',''incident_notes'',stored->''incident_notes'',''admin_review_required'',stored->''admin_review_required'',''next_reconciliation_at'',stored->''next_reconciliation_at'',''reconciliation_attempts'',stored->''reconciliation_attempts'');$s$
    );

    EXECUTE def;
END;
$migration$;

-- 6. Ensure og_compat has view for ghn_webhook_events so search_path = og_compat,public validates correctly
CREATE OR REPLACE VIEW og_compat.ghn_webhook_events AS
SELECT * FROM public.ghn_webhook_events;
