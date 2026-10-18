-- Structure-only ERD snapshot. Exactly 48 business tables; no data/views/workflow triggers.

-- Import only into an empty disposable database for diagramming. Runtime uses Flyway.

CREATE SCHEMA IF NOT EXISTS public;

CREATE SEQUENCE public.og70_account_status_events_event_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_addresses_address_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_audit_logs_log_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_business_policies_policy_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_carts_cart_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_case_actions_action_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_case_decisions_decision_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_case_rounds_round_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_cases_case_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_categories_category_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_checklist_policies_checklist_policy_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_complaints_complaint_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_conversations_conversation_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_ekyc_decisions_decision_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_ekyc_private_assets_asset_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_ekyc_profiles_profile_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_ekyc_verification_attempts_attempt_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_external_identities_external_identity_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_listing_fee_assessments_assessment_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_listing_fee_policies_policy_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_messages_message_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_notifications_notification_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_offers_offer_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_order_items_order_item_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_order_unboxing_evidences_evidence_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_order_vouchers_order_voucher_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_orders_order_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_payment_alert_requests_alert_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_payment_attempts_attempt_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_payment_events_event_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_payment_ipn_events_event_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_payment_ipn_raw_receipts_receipt_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_payment_reconciliation_cases_case_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_payment_review_audits_review_audit_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_payments_payment_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_penalty_policies_policy_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_product_media_media_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_product_moderation_decisions_decision_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_product_revisions_revision_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_products_product_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_reports_report_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_review_revisions_revision_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_reviews_review_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_revision_media_revision_media_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_reward_policies_policy_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_roles_role_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_seller_profile_decisions_decision_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_seller_profiles_seller_profile_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_seller_verification_metrics_metric_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_seller_verifications_verification_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_shipments_shipment_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_system_fee_policies_fee_policy_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_users_user_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_voucher_revisions_revision_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE SEQUENCE public.og70_vouchers_voucher_id_seq AS bigint INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START WITH 1 CACHE 1 NO CYCLE;

CREATE TABLE public.addresses (
  address_id bigint DEFAULT nextval('og70_addresses_address_id_seq'::regclass) NOT NULL,
  user_id bigint,
  recipient_name character varying(120),
  phone_number character varying(20),
  province character varying(100),
  district character varying(100),
  ward character varying(100),
  detail_address character varying(255),
  is_default boolean DEFAULT false,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamp with time zone,
  deleted_at timestamp with time zone,
  record_type text DEFAULT 'addresses'::text NOT NULL
);

CREATE TABLE public.audit_logs (
  log_id bigint DEFAULT nextval('og70_audit_logs_log_id_seq'::regclass) NOT NULL,
  user_id bigint,
  action character varying(50),
  entity_type character varying(50),
  entity_id bigint,
  old_values jsonb,
  new_values jsonb,
  ip_address inet,
  request_id character varying(100),
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  source_account_status_events_event_id bigint,
  old_status character varying(20),
  new_status character varying(20),
  occurred_at timestamp with time zone,
  source_order_events_event_id uuid,
  order_id bigint,
  actor_id bigint,
  event_type character varying(60),
  reason text,
  metadata jsonb,
  command_key character varying(150),
  source_report_export_runs_export_id uuid,
  requested_by bigint,
  metric character varying(30),
  filters jsonb,
  timezone character varying(60),
  cutoff_at timestamp with time zone,
  object_key text,
  record_type text DEFAULT 'audit_logs'::text NOT NULL
);

CREATE TABLE public.auth_challenges (
  challenge_id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id bigint,
  subject_key character varying(360),
  purpose character varying(30),
  code_digest character varying(128),
  state character varying(15) DEFAULT 'OPEN'::character varying,
  issued_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  expires_at timestamp with time zone,
  next_send_at timestamp with time zone,
  attempt_count integer DEFAULT 0,
  max_attempts integer DEFAULT 5,
  consumed_at timestamp with time zone,
  invalidated_at timestamp with time zone,
  command_key character varying(150),
  source_password_reset_challenges_challenge_id uuid,
  otp_digest character(64),
  attempts smallint,
  created_at timestamp with time zone,
  source_quick_auth_sessions_session_id uuid,
  profile_id bigint,
  reference_asset_id bigint,
  nonmatch_count integer,
  last_nonmatch_at timestamp with time zone,
  source_quick_auth_results_result_id uuid,
  matched_at timestamp with time zone,
  session_id uuid,
  record_type text DEFAULT 'auth_challenges'::text NOT NULL
);

CREATE TABLE public.business_policies (
  policy_id bigint DEFAULT nextval('og70_business_policies_policy_id_seq'::regclass) NOT NULL,
  source_system_fee_policies_fee_policy_id bigint,
  policy_code character varying(60),
  policy_name character varying(120),
  buyer_fee_rate numeric(9,6),
  buyer_fixed_fee numeric(19,2),
  seller_fee_rate numeric(9,6),
  seller_fixed_fee numeric(19,2),
  minimum_buyer_fee numeric(19,2),
  maximum_buyer_fee numeric(19,2),
  minimum_seller_fee numeric(19,2),
  maximum_seller_fee numeric(19,2),
  rounding_scale smallint,
  rounding_mode character varying(20),
  currency character(3),
  status character varying(20),
  version integer,
  effective_from timestamp with time zone,
  effective_to timestamp with time zone,
  created_at timestamp with time zone,
  created_by bigint,
  source_listing_fee_policies_policy_id bigint,
  threshold_amount numeric(19,2),
  fixed_below numeric(19,2),
  rate_at_or_above numeric(8,6),
  effective_until timestamp with time zone,
  source_checklist_policies_checklist_policy_id bigint,
  category_id bigint,
  policy_version integer,
  checklist jsonb,
  source_reward_policies_policy_id bigint,
  point_value numeric(19,2),
  policy_snapshot jsonb,
  source_penalty_policies_policy_id bigint,
  thresholds jsonb,
  record_type text DEFAULT 'system_fee_policies'::text NOT NULL
);

CREATE TABLE public.cart_items (
  cart_id bigint NOT NULL,
  product_id bigint NOT NULL,
  quantity integer DEFAULT 1,
  added_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  record_type text DEFAULT 'cart_items'::text NOT NULL,
  user_id bigint
);

CREATE TABLE public.case_actions (
  action_id bigint DEFAULT nextval('og70_case_actions_action_id_seq'::regclass) NOT NULL,
  source_case_rounds_round_id bigint,
  case_id bigint,
  round_no integer,
  stage character varying(25),
  state character varying(15),
  opened_at timestamp with time zone,
  complete_submission_at timestamp with time zone,
  ktv_due_at timestamp with time zone,
  valid_damage_request_at timestamp with time zone,
  source_case_decisions_decision_id bigint,
  round_id bigint,
  reviewed_by bigint,
  actor_type character varying(20),
  outcome character varying(25),
  is_final boolean,
  goods_refund_amount numeric(19,2),
  reason text,
  decided_at timestamp with time zone,
  supersedes_decision_id bigint,
  command_key character varying(150),
  source_case_events_event_id uuid,
  actor_id bigint,
  recipient_id bigint,
  event_type character varying(30),
  metadata jsonb,
  occurred_at timestamp with time zone,
  source_money_holds_hold_id uuid,
  component_id uuid,
  source_key character varying(150),
  closed_at timestamp with time zone,
  source_payment_review_audits_review_audit_id bigint,
  order_id bigint,
  payment_id bigint,
  attempt_id bigint,
  decided_by character varying(50),
  decision character varying(30),
  reason_note text,
  vnp_verification_ref character varying(100),
  created_at timestamp with time zone,
  intent_id uuid,
  record_type text DEFAULT 'case_rounds'::text NOT NULL
);

CREATE TABLE public.case_evidence (
  evidence_id uuid DEFAULT gen_random_uuid() NOT NULL,
  round_id bigint,
  submitted_by bigint,
  submission_no integer,
  media_type character varying(10),
  object_key text,
  text_content text,
  content_digest character(64),
  file_size_bytes bigint,
  duration_seconds integer,
  submitted_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  deleted_at timestamp with time zone,
  source_order_unboxing_evidences_evidence_id bigint,
  order_id bigint,
  buyer_id bigint,
  status character varying(30),
  video_url text,
  thumbnail_url text,
  cloudinary_public_id character varying(255),
  video_duration_sec integer,
  recorded_at timestamp with time zone,
  skipped_at timestamp with time zone,
  created_at timestamp with time zone,
  recorded_acknowledged_at timestamp with time zone,
  skip_warning_accepted_at timestamp with time zone,
  warning_version character varying(50),
  record_type text DEFAULT 'case_evidence'::text NOT NULL
);

CREATE TABLE public.cases (
  case_id bigint DEFAULT nextval('og70_cases_case_id_seq'::regclass) NOT NULL,
  case_type character varying(40),
  order_id bigint,
  product_id bigint,
  listing_charge_id uuid,
  payment_intent_id uuid,
  target_user_id bigint,
  created_by bigint,
  assigned_to bigint,
  description text,
  state character varying(20) DEFAULT 'OPEN'::character varying,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  approved_at timestamp with time zone,
  valid_returned_at timestamp with time zone,
  seller_response_due_at timestamp with time zone,
  closed_at timestamp with time zone,
  last_related_completion_at timestamp with time zone,
  command_key character varying(150),
  source_complaints_complaint_id bigint,
  reason character varying(50),
  evidence jsonb,
  status character varying(20),
  resolution character varying(30),
  resolution_note text,
  resolved_by bigint,
  refund_amount numeric(19,2),
  resolved_at timestamp with time zone,
  evidence_cleanup_due_at timestamp with time zone,
  evidence_cleaned_at timestamp with time zone,
  source_reports_report_id bigint,
  reporter_id bigint,
  reported_user_id bigint,
  message_id bigint,
  incident_key character varying(150),
  evidence_snapshot jsonb,
  source_payment_reconciliation_cases_case_id bigint,
  payment_id bigint,
  attempt_id bigint,
  source_type character varying(30),
  event_id bigint,
  review_audit_id bigint,
  vnp_transaction_no character varying(100),
  dedup_key character varying(150),
  payment_reconciliation_cases_assigned_to character varying(50),
  intent_id uuid,
  assigned_user_id bigint,
  record_type text DEFAULT 'cases'::text NOT NULL
);

CREATE TABLE public.categories (
  category_id bigint DEFAULT nextval('og70_categories_category_id_seq'::regclass) NOT NULL,
  parent_category_id bigint,
  category_name character varying(100),
  slug character varying(120),
  description character varying(500),
  is_active boolean DEFAULT true,
  display_order integer DEFAULT 0,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamp with time zone,
  record_type text DEFAULT 'categories'::text NOT NULL
);

CREATE TABLE public.checkout_groups (
  group_id uuid DEFAULT gen_random_uuid() NOT NULL,
  buyer_id bigint,
  command_key character varying(150),
  request_digest character(64),
  address_snapshot jsonb,
  delivery_method character varying(20),
  carrier character varying(100),
  payment_method character varying(30),
  expected_total numeric(19,2),
  state character varying(20) DEFAULT 'READY'::character varying,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  payment_due_at timestamp with time zone,
  record_type text DEFAULT 'checkout_groups'::text NOT NULL,
  voucher_redemption jsonb DEFAULT '[]'::jsonb,
  zero_confirmation jsonb DEFAULT '[]'::jsonb
);

CREATE TABLE public.conversations (
  conversation_id bigint DEFAULT nextval('og70_conversations_conversation_id_seq'::regclass) NOT NULL,
  buyer_id bigint,
  seller_id bigint,
  product_id bigint,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  product_title_snapshot character varying(200),
  product_price_at_start numeric(19,2),
  product_thumbnail_snapshot text,
  currency character(3),
  last_message_id bigint,
  last_activity_at timestamp with time zone,
  status character varying(20) DEFAULT 'ACTIVE'::character varying,
  closed_at timestamp with time zone,
  record_type text DEFAULT 'conversations'::text NOT NULL,
  participant_state jsonb DEFAULT '[]'::jsonb,
  media_quota_usage jsonb DEFAULT '[]'::jsonb
);

CREATE TABLE public.ekyc_private_assets (
  asset_id bigint DEFAULT nextval('og70_ekyc_private_assets_asset_id_seq'::regclass) NOT NULL,
  profile_id bigint,
  asset_type character varying(20),
  object_key text,
  content_digest character(64),
  captured_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  expires_at timestamp with time zone,
  deleted_at timestamp with time zone,
  record_type text DEFAULT 'ekyc_private_assets'::text NOT NULL
);

CREATE TABLE public.ekyc_profiles (
  profile_id bigint DEFAULT nextval('og70_ekyc_profiles_profile_id_seq'::regclass) NOT NULL,
  user_id bigint,
  revision_no integer,
  state character varying(20) DEFAULT 'DRAFT'::character varying,
  submitted_data jsonb DEFAULT '{}'::jsonb,
  ai_summary jsonb DEFAULT '{}'::jsonb,
  nonmatch_count integer DEFAULT 0,
  last_nonmatch_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  submitted_at timestamp with time zone,
  verified_at timestamp with time zone,
  reference_expires_at timestamp with time zone,
  version bigint DEFAULT 0,
  document_digest character(64),
  source_seller_verifications_verification_id bigint,
  verification_method character varying(30),
  status character varying(20),
  document_data jsonb,
  rejection_reason character varying(500),
  reviewed_at timestamp with time zone,
  verified_by bigint,
  record_type text DEFAULT 'ekyc_profiles'::text NOT NULL,
  ekyc_decisions_history jsonb DEFAULT '[]'::jsonb
);

CREATE TABLE public.identity_document_registry (
  document_digest character(64) NOT NULL,
  digest_key_version character varying(40),
  user_id bigint,
  first_profile_id bigint,
  registered_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  record_type text DEFAULT 'identity_document_registry'::text NOT NULL
);

CREATE TABLE public.interaction_events (
  event_id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id bigint,
  guest_session_digest character(64),
  event_type character varying(15),
  product_id bigint,
  query_text character varying(200),
  occurred_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  source_key character varying(150),
  record_type text DEFAULT 'interaction_events'::text NOT NULL
);

CREATE TABLE public.inventory_reservations (
  reservation_id uuid DEFAULT gen_random_uuid() NOT NULL,
  product_id bigint,
  order_item_id bigint,
  quantity integer,
  state character varying(15) DEFAULT 'HELD'::character varying,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  expires_at timestamp with time zone,
  changed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  command_key character varying(150),
  record_type text DEFAULT 'inventory_reservations'::text NOT NULL
);

CREATE TABLE public.listing_fee_charges (
  charge_id uuid DEFAULT gen_random_uuid() NOT NULL,
  assessment_id bigint,
  amount numeric(19,2),
  state character varying(20) DEFAULT 'PENDING'::character varying,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  command_key character varying(150),
  source_listing_fee_assessments_assessment_id bigint,
  product_id bigint,
  revision_id bigint,
  decision_id bigint,
  policy_id bigint,
  listed_total numeric(19,2),
  previous_approved_total numeric(19,2),
  computed_fee numeric(19,2),
  cumulative_before numeric(19,2),
  amount_due numeric(19,2),
  assessed_at timestamp with time zone,
  record_type text DEFAULT 'listing_fee_charges'::text NOT NULL
);

CREATE TABLE public.messages (
  message_id bigint DEFAULT nextval('og70_messages_message_id_seq'::regclass) NOT NULL,
  conversation_id bigint,
  sender_id bigint,
  content text,
  message_type character varying(20) DEFAULT 'TEXT'::character varying,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  client_message_id uuid,
  reply_to_message_id bigint,
  edited_at timestamp with time zone,
  deleted_at timestamp with time zone,
  offer_id bigint,
  media_status character varying(20) DEFAULT 'ACTIVE'::character varying,
  media_expires_at timestamp with time zone,
  cloudinary_public_id character varying(255),
  media_metadata jsonb,
  record_type text DEFAULT 'messages'::text NOT NULL
);

CREATE TABLE public.notifications (
  notification_id bigint DEFAULT nextval('og70_notifications_notification_id_seq'::regclass) NOT NULL,
  user_id bigint,
  type character varying(50),
  title character varying(200),
  content text,
  reference_type character varying(50),
  reference_id bigint,
  is_read boolean DEFAULT false,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  read_at timestamp with time zone,
  event_id uuid,
  dispatch_id uuid,
  sender_id bigint,
  reference_uuid uuid,
  reference_revision bigint,
  target_data jsonb DEFAULT '{}'::jsonb,
  source_notification_dispatches_dispatch_id uuid,
  command_key character varying(150),
  source_notification_reminders_reminder_id uuid,
  source_key character varying(150),
  reminder_type character varying(30),
  scheduled_at timestamp with time zone,
  expires_at timestamp with time zone,
  state character varying(15),
  source_snapshot jsonb,
  source_payment_alert_requests_alert_id bigint,
  case_id bigint,
  alert_type character varying(50),
  severity character varying(20),
  status character varying(20),
  attempt_count integer,
  last_error text,
  sent_at timestamp with time zone,
  record_type text DEFAULT 'notifications'::text NOT NULL,
  delivery_history jsonb DEFAULT '[]'::jsonb
);

CREATE TABLE public.offers (
  offer_id bigint DEFAULT nextval('og70_offers_offer_id_seq'::regclass) NOT NULL,
  offered_item_price numeric(19,2),
  status character varying(20) DEFAULT 'PENDING'::character varying,
  expires_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  responded_at timestamp with time zone,
  conversation_id bigint,
  proposer_id bigint,
  responded_by bigint,
  parent_offer_id bigint,
  fee_policy_id bigint,
  buyer_system_fee numeric(19,2),
  seller_system_fee numeric(19,2),
  buyer_subtotal numeric(19,2),
  seller_proceeds numeric(19,2),
  currency character(3),
  version bigint DEFAULT 0,
  record_type text DEFAULT 'offers'::text NOT NULL
);

CREATE TABLE public.order_fund_components (
  component_id uuid DEFAULT gen_random_uuid() NOT NULL,
  order_id bigint,
  allocation_id uuid,
  late_confirmation_id uuid,
  component_type character varying(30),
  confirmed_amount numeric(19,2),
  reserved_amount numeric(19,2) DEFAULT 0,
  refunded_amount numeric(19,2) DEFAULT 0,
  released_amount numeric(19,2) DEFAULT 0,
  funding_reference character varying(150),
  confirmed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  version bigint DEFAULT 0,
  source_payment_allocations_allocation_id uuid,
  intent_id uuid,
  confirmation_id uuid,
  amount numeric(19,2),
  record_type text DEFAULT 'order_fund_components'::text NOT NULL
);

CREATE TABLE public.order_items (
  order_item_id bigint DEFAULT nextval('og70_order_items_order_item_id_seq'::regclass) NOT NULL,
  order_id bigint,
  product_id bigint,
  product_title character varying(200),
  quantity integer DEFAULT 1,
  agreed_price numeric(19,2),
  buyer_line_total numeric(19,2),
  listed_price numeric(19,2),
  buyer_system_fee numeric(19,2),
  seller_system_fee numeric(19,2),
  seller_line_proceeds numeric(19,2),
  fee_policy_id bigint,
  accepted_offer_id bigint,
  pricing_source character varying(20) DEFAULT 'LIST_PRICE'::character varying,
  workflow_model character varying(20) DEFAULT 'LEGACY_V14'::character varying,
  product_revision_id bigint,
  return_allowed boolean,
  voucher_allocation numeric(19,2) DEFAULT 0,
  points_allocation numeric(19,2) DEFAULT 0,
  record_type text DEFAULT 'order_items'::text NOT NULL,
  discount_history jsonb DEFAULT '[]'::jsonb,
  conversation_id bigint
);

CREATE TABLE public.orders (
  order_id bigint DEFAULT nextval('og70_orders_order_id_seq'::regclass) NOT NULL,
  checkout_group_id uuid,
  buyer_id bigint,
  seller_id bigint,
  source_address_id bigint,
  shipping_recipient_name character varying(120),
  shipping_phone_number character varying(20),
  shipping_province character varying(100),
  shipping_district character varying(100),
  shipping_ward character varying(100),
  shipping_detail_address character varying(255),
  subtotal numeric(19,2),
  shipping_fee numeric(19,2) DEFAULT 0,
  total_amount numeric(19,2),
  currency character(3) DEFAULT 'VND'::bpchar,
  status character varying(30) DEFAULT 'PAYMENT_PENDING'::character varying,
  payment_due_at timestamp with time zone,
  version bigint DEFAULT 0,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamp with time zone,
  completed_at timestamp with time zone,
  cancelled_at timestamp with time zone,
  cancellation_reason character varying(500),
  buyer_system_fee numeric(19,2),
  seller_system_fee numeric(19,2),
  seller_proceeds numeric(19,2),
  voucher_discount_amount numeric(19,2) DEFAULT 0,
  shipping_discount_amount numeric(19,2) DEFAULT 0,
  sponsor_type character varying(20) DEFAULT 'PLATFORM'::character varying,
  workflow_model character varying(20) DEFAULT 'LEGACY_V14'::character varying,
  group_id uuid,
  return_allowed boolean,
  delivery_method character varying(20),
  carrier_snapshot character varying(100),
  handover_day date,
  points_discount_amount numeric(19,2) DEFAULT 0,
  paid_confirmed_at timestamp with time zone,
  seller_accept_due_at timestamp with time zone,
  valid_delivered_at timestamp with time zone,
  received_at timestamp with time zone,
  return_due_at timestamp with time zone,
  early_completed_at timestamp with time zone,
  early_completion_warning_version character varying(50),
  purchase_shipping_used_at timestamp with time zone,
  record_type text DEFAULT 'orders'::text NOT NULL,
  legacy_voucher_history jsonb DEFAULT '[]'::jsonb,
  conversation_links jsonb DEFAULT '[]'::jsonb
);

CREATE TABLE public.outbox_events (
  event_id uuid DEFAULT gen_random_uuid() NOT NULL,
  aggregate_type character varying(80),
  aggregate_id character varying(100),
  event_type character varying(120),
  payload jsonb,
  occurred_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  available_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  published_at timestamp with time zone,
  attempt_count integer DEFAULT 0,
  last_error character varying(1000),
  record_type text DEFAULT 'outbox_events'::text NOT NULL,
  consumer_progress jsonb DEFAULT '[]'::jsonb
);

CREATE TABLE public.payment_attempts (
  attempt_id bigint DEFAULT nextval('og70_payment_attempts_attempt_id_seq'::regclass) NOT NULL,
  order_id bigint,
  payment_id bigint,
  txn_ref character varying(100),
  amount numeric(19,2),
  currency character(3) DEFAULT 'VND'::bpchar,
  provider character varying(30) DEFAULT 'VNPAY'::character varying,
  status character varying(35) DEFAULT 'PENDING'::character varying,
  vnp_amount_raw bigint,
  vnp_amount numeric(19,2),
  vnp_transaction_no character varying(100),
  vnp_response_code character varying(10),
  vnp_transaction_status character varying(10),
  vnp_bank_code character varying(50),
  vnp_pay_date character varying(20),
  failure_reason character varying(500),
  ipn_count integer DEFAULT 0,
  first_ipn_received_at timestamp with time zone,
  last_ipn_received_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamp with time zone,
  intent_id uuid,
  source_vnpay_payment_attempts_transaction_ref character varying(100),
  expires_at timestamp with time zone,
  processed_at timestamp with time zone,
  provider_transaction_no character varying(100),
  outcome character varying(20),
  response_code character varying(2),
  transaction_status character varying(2),
  record_type text DEFAULT 'payment_attempts'::text NOT NULL
);

CREATE TABLE public.payment_confirmations (
  confirmation_id uuid DEFAULT gen_random_uuid() NOT NULL,
  intent_id uuid,
  attempt_id bigint,
  ipn_event_id bigint,
  provider character varying(30),
  provider_transaction_id character varying(150),
  amount numeric(19,2),
  disposition character varying(20) DEFAULT 'ALLOCATABLE'::character varying,
  confirmed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  source_listing_fee_receipts_receipt_id uuid,
  charge_id uuid,
  record_type text DEFAULT 'payment_confirmations'::text NOT NULL
);

CREATE TABLE public.payment_events (
  event_id bigint DEFAULT nextval('og70_payment_events_event_id_seq'::regclass) NOT NULL,
  source_payment_ipn_raw_receipts_receipt_id bigint,
  raw_query_params text,
  raw_query_original_length integer,
  raw_query_truncated boolean,
  raw_query_sanitized boolean,
  raw_query_sha256 character(64),
  ip_address character varying(50),
  received_at timestamp with time zone,
  vnp_txn_ref character varying(100),
  vnp_transaction_no character varying(100),
  vnp_amount_text text,
  verification_status character varying(30),
  processing_status character varying(30),
  processing_error character varying(200),
  source_payment_ipn_events_event_id bigint,
  receipt_id bigint,
  attempt_id bigint,
  order_id bigint,
  payment_id bigint,
  txn_ref character varying(100),
  vnp_response_code character varying(10),
  vnp_transaction_status character varying(10),
  vnp_curr_code character varying(10),
  vnp_amount_raw bigint,
  vnp_amount numeric(19,2),
  vnp_bank_code character varying(50),
  vnp_bank_tran_no character varying(100),
  vnp_card_type character varying(20),
  vnp_pay_date character varying(20),
  processing_outcome character varying(50),
  intent_id uuid,
  record_type text DEFAULT 'payment_ipn_raw_receipts'::text NOT NULL
);

CREATE TABLE public.payments (
  payment_id bigint DEFAULT nextval('og70_payments_payment_id_seq'::regclass) NOT NULL,
  order_id bigint,
  amount numeric(19,2),
  currency character(3) DEFAULT 'VND'::bpchar,
  payment_method character varying(30),
  transaction_code character varying(100),
  status character varying(20) DEFAULT 'PENDING'::character varying,
  paid_at timestamp with time zone,
  version bigint DEFAULT 0,
  held_at timestamp with time zone,
  released_at timestamp with time zone,
  refund_amount numeric(19,2),
  refund_reason character varying(500),
  refunded_at timestamp with time zone,
  failure_reason character varying(500),
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamp with time zone,
  source_payment_intents_intent_id uuid,
  purpose character varying(20),
  group_id uuid,
  listing_charge_id uuid,
  expected_amount numeric(19,2),
  state character varying(20),
  deadline timestamp with time zone,
  command_key character varying(150),
  record_type text DEFAULT 'payments'::text NOT NULL
);

CREATE TABLE public.penalty_ledger (
  entry_id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id bigint,
  report_id bigint,
  policy_id bigint,
  points_delta integer,
  adjustment_of uuid,
  decided_by bigint,
  reason text,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  command_key character varying(150),
  record_type text DEFAULT 'penalty_ledger'::text NOT NULL
);

CREATE TABLE public.product_categories (
  product_id bigint NOT NULL,
  category_id bigint NOT NULL,
  record_type text DEFAULT 'product_categories'::text NOT NULL
);

CREATE TABLE public.product_media (
  media_id bigint DEFAULT nextval('og70_product_media_media_id_seq'::regclass) NOT NULL,
  product_id bigint,
  media_type character varying(10),
  media_url text,
  display_order integer DEFAULT 0,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  thumbnail_url text,
  duration_seconds integer,
  file_size_bytes bigint,
  cloudinary_public_id character varying(255),
  mime_type character varying(50),
  source_revision_media_revision_media_id bigint,
  revision_id bigint,
  object_key text,
  content_digest character(64),
  checklist_items jsonb,
  record_type text DEFAULT 'product_media'::text NOT NULL
);

CREATE TABLE public.product_revisions (
  revision_id bigint DEFAULT nextval('og70_product_revisions_revision_id_seq'::regclass) NOT NULL,
  product_id bigint,
  revision_no bigint,
  provenance character varying(20),
  state character varying(15) DEFAULT 'DRAFT'::character varying,
  listed_unit_price numeric(19,2),
  quantity integer,
  return_allowed boolean,
  delivery_options jsonb,
  content_snapshot jsonb,
  checklist_snapshot jsonb DEFAULT '[]'::jsonb,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  submitted_at timestamp with time zone,
  record_type text DEFAULT 'product_revisions'::text NOT NULL,
  media_analysis_runs_history jsonb DEFAULT '[]'::jsonb,
  moderation_history jsonb DEFAULT '[]'::jsonb,
  legacy_moderation_history jsonb DEFAULT '[]'::jsonb
);

CREATE TABLE public.products (
  product_id bigint DEFAULT nextval('og70_products_product_id_seq'::regclass) NOT NULL,
  seller_id bigint,
  category_id bigint,
  title character varying(200),
  description text,
  listed_price numeric(19,2),
  currency character(3) DEFAULT 'VND'::bpchar,
  condition character varying(20),
  usage_duration character varying(100),
  defects text,
  repair_history text,
  included_accessories text,
  location character varying(255),
  status character varying(40) DEFAULT 'DRAFT'::character varying,
  reserved_until timestamp with time zone,
  reserved_order_id bigint,
  version bigint DEFAULT 0,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamp with time zone,
  deleted_at timestamp with time zone,
  requires_buyer_ekyc boolean DEFAULT false,
  content_revision bigint DEFAULT 1,
  workflow_model character varying(20) DEFAULT 'LEGACY_V14'::character varying,
  quantity_total integer DEFAULT 1,
  quantity_held integer DEFAULT 0,
  quantity_sold integer DEFAULT 0,
  quantity_unavailable integer DEFAULT 0,
  quantity_available integer GENERATED ALWAYS AS ((((quantity_total - quantity_held) - quantity_sold) - quantity_unavailable)) STORED,
  return_allowed boolean,
  delivery_options jsonb,
  current_revision_id bigint,
  public_revision_id bigint,
  record_type text DEFAULT 'products'::text NOT NULL
);

CREATE TABLE public.refresh_sessions (
  session_id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id bigint,
  family_id uuid,
  token_digest character varying(64),
  issued_at timestamp with time zone,
  expires_at timestamp with time zone,
  consumed_at timestamp with time zone,
  revoked_at timestamp with time zone,
  replaced_by uuid,
  created_by_ip character varying(45),
  user_agent character varying(255),
  record_type text DEFAULT 'refresh_sessions'::text NOT NULL
);

CREATE TABLE public.reviews (
  review_id bigint DEFAULT nextval('og70_reviews_review_id_seq'::regclass) NOT NULL,
  order_id bigint,
  reviewer_id bigint,
  reviewee_id bigint,
  rating smallint,
  comment character varying(2000),
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamp with time zone,
  deleted_at timestamp with time zone,
  record_type text DEFAULT 'reviews'::text NOT NULL,
  edit_history jsonb DEFAULT '[]'::jsonb
);

CREATE TABLE public.reward_ledger (
  entry_id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id bigint,
  policy_id bigint,
  entry_type character varying(20),
  points_delta bigint,
  order_id bigint,
  group_id uuid,
  actor_id bigint,
  reason text,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  command_key character varying(150),
  record_type text DEFAULT 'reward_ledger'::text NOT NULL
);

CREATE TABLE public.roles (
  role_id smallint DEFAULT nextval('og70_roles_role_id_seq'::regclass) NOT NULL,
  role_name character varying(20),
  description character varying(255),
  record_type text DEFAULT 'roles'::text NOT NULL
);

CREATE TABLE public.seller_buyer_blocks (
  seller_id bigint NOT NULL,
  buyer_id bigint NOT NULL,
  blocked_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  unblocked_at timestamp with time zone,
  reason text,
  record_type text DEFAULT 'seller_buyer_blocks'::text NOT NULL
);

CREATE TABLE public.seller_profiles (
  seller_profile_id bigint DEFAULT nextval('og70_seller_profiles_seller_profile_id_seq'::regclass) NOT NULL,
  user_id bigint,
  revision_no integer DEFAULT 1,
  state character varying(20) DEFAULT 'PENDING_EKYC'::character varying,
  ekyc_profile_id bigint,
  quick_auth_result_id uuid,
  pickup_address_id bigint,
  bank_snapshot jsonb DEFAULT '{}'::jsonb,
  updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  record_type text DEFAULT 'seller_profiles'::text NOT NULL,
  seller_profile_decisions_history jsonb DEFAULT '[]'::jsonb
);

CREATE TABLE public.settlement_operations (
  operation_id uuid DEFAULT gen_random_uuid() NOT NULL,
  component_id uuid,
  operation_type character varying(25),
  amount numeric(19,2),
  cause_type character varying(20),
  case_decision_id bigint,
  recipient_user_id bigint,
  state character varying(15) DEFAULT 'REQUESTED'::character varying,
  requested_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  confirmed_at timestamp with time zone,
  provider character varying(30),
  provider_operation_id character varying(150),
  failure_reason text,
  command_key character varying(150),
  record_type text DEFAULT 'settlement_operations'::text NOT NULL
);

CREATE TABLE public.shipment_events (
  event_id uuid DEFAULT gen_random_uuid() NOT NULL,
  shipment_id bigint,
  source character varying(30),
  source_event_id character varying(150),
  event_type character varying(30),
  occurred_at timestamp with time zone,
  received_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  metadata jsonb DEFAULT '{}'::jsonb,
  source_pickup_attempts_attempt_id uuid,
  attempt_no integer,
  attempted_at timestamp with time zone,
  attempt_day date GENERATED ALWAYS AS (((attempted_at AT TIME ZONE 'Asia/Saigon'::text))::date) STORED,
  outcome character varying(15),
  responsibility character varying(15),
  reason text,
  pickup_attempts_source_event_id uuid,
  source_handover_confirmations_confirmation_id uuid,
  party character varying(10),
  user_id bigint,
  confirmed_at timestamp with time zone,
  command_key character varying(150),
  record_type text DEFAULT 'shipment_events'::text NOT NULL
);

CREATE TABLE public.shipments (
  shipment_id bigint DEFAULT nextval('og70_shipments_shipment_id_seq'::regclass) NOT NULL,
  order_id bigint,
  carrier character varying(100),
  tracking_number character varying(100),
  status character varying(20) DEFAULT 'PENDING'::character varying,
  shipped_at timestamp with time zone,
  delivered_at timestamp with time zone,
  updated_at timestamp with time zone,
  leg character varying(15) DEFAULT 'OUTBOUND'::character varying,
  workflow_model character varying(20) DEFAULT 'LEGACY_V14'::character varying,
  return_case_id bigint,
  delivery_method character varying(20),
  fee_snapshot numeric(19,2),
  fee_payer character varying(10),
  initiated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  pickup_expected_at timestamp with time zone,
  pickup_deadline timestamp with time zone,
  first_failure_at timestamp with time zone,
  delivery_retry_due_at timestamp with time zone,
  valid_delivery_at timestamp with time zone,
  otp_digest character varying(128),
  otp_expires_at timestamp with time zone,
  otp_attempt_count integer DEFAULT 0,
  otp_consumed_at timestamp with time zone,
  record_type text DEFAULT 'shipments'::text NOT NULL
);

CREATE TABLE public.user_roles (
  user_id bigint NOT NULL,
  role_id smallint NOT NULL,
  granted_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  granted_by bigint,
  record_type text DEFAULT 'user_roles'::text NOT NULL
);

CREATE TABLE public.users (
  user_id bigint DEFAULT nextval('og70_users_user_id_seq'::regclass) NOT NULL,
  email character varying(320),
  password_hash character varying(255),
  full_name character varying(120),
  phone_number character varying(20),
  avatar_url text,
  status character varying(20) DEFAULT 'ACTIVE'::character varying,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamp with time zone,
  deleted_at timestamp with time zone,
  bank_name character varying(100),
  bank_account_number character varying(50),
  bank_account_holder character varying(120),
  email_verified_at timestamp with time zone,
  identity_workflow character varying(20) DEFAULT 'LEGACY_V14'::character varying,
  record_type text DEFAULT 'users'::text NOT NULL,
  user_security_settings_history jsonb DEFAULT '[]'::jsonb,
  reward_accounts_history jsonb DEFAULT '[]'::jsonb,
  external_identities_history jsonb DEFAULT '[]'::jsonb,
  account_restrictions_history jsonb DEFAULT '[]'::jsonb,
  carts_history jsonb DEFAULT '[]'::jsonb,
  reward_balance bigint GENERATED ALWAYS AS (COALESCE((((reward_accounts_history -> 0) ->> 'balance'::text))::bigint, (0)::bigint)) STORED,
  email_2fa_enabled boolean GENERATED ALWAYS AS (COALESCE((((user_security_settings_history -> 0) ->> 'email_2fa_enabled'::text))::boolean, false)) STORED
);

CREATE TABLE public.verification_attempts (
  attempt_id uuid DEFAULT gen_random_uuid() NOT NULL,
  source_ekyc_verification_attempts_attempt_id bigint,
  profile_id bigint,
  result character varying(20),
  model_metadata jsonb,
  fallback_authorized_by bigint,
  attempted_at timestamp with time zone,
  command_key character varying(150),
  source_quick_auth_attempts_attempt_id uuid,
  session_id uuid,
  source_seller_verification_metrics_metric_id bigint,
  user_id bigint,
  verification_id bigint,
  match_distance numeric(5,4),
  processed_at timestamp with time zone,
  threshold_used numeric(5,4),
  model_name character varying(80),
  model_version character varying(80),
  is_simulated boolean,
  record_type text DEFAULT 'ekyc_verification_attempts'::text NOT NULL
);

CREATE TABLE public.voucher_grants (
  grant_id uuid DEFAULT gen_random_uuid() NOT NULL,
  revision_id bigint,
  user_id bigint,
  source character varying(20),
  issued_by bigint,
  reason text,
  state character varying(15) DEFAULT 'AVAILABLE'::character varying,
  granted_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  expires_at timestamp with time zone,
  command_key character varying(150),
  record_type text DEFAULT 'voucher_grants'::text NOT NULL,
  grant_history jsonb DEFAULT '[]'::jsonb
);

CREATE TABLE public.voucher_products (
  revision_id bigint NOT NULL,
  product_id bigint NOT NULL,
  record_type text DEFAULT 'voucher_revision_products'::text NOT NULL
);

CREATE TABLE public.vouchers (
  voucher_id bigint DEFAULT nextval('og70_vouchers_voucher_id_seq'::regclass) NOT NULL,
  code character varying(50),
  title character varying(200),
  description text,
  voucher_type character varying(30),
  discount_type character varying(20),
  discount_value numeric(19,2),
  max_discount_amount numeric(19,2),
  min_order_amount numeric(19,2) DEFAULT 0,
  sponsor_type character varying(20) DEFAULT 'PLATFORM'::character varying,
  seller_id bigint,
  total_usage_limit integer,
  current_usage_count integer DEFAULT 0,
  max_usage_per_user integer DEFAULT 1,
  start_time timestamp with time zone,
  end_time timestamp with time zone,
  is_active boolean DEFAULT true,
  version bigint DEFAULT 0,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamp with time zone,
  source_voucher_revisions_revision_id bigint,
  parent_voucher_id bigint,
  revision_no integer,
  policy_snapshot jsonb,
  scope character varying(20),
  claim_limit integer,
  per_user_claim_limit integer,
  valid_from timestamp with time zone,
  valid_until timestamp with time zone,
  created_by bigint,
  benefit_component character varying(10),
  min_eligible_amount numeric(19,2),
  record_type text DEFAULT 'vouchers'::text NOT NULL,
  revocation_history jsonb DEFAULT '[]'::jsonb
);

ALTER TABLE public.addresses ADD CONSTRAINT addresses_pkey PRIMARY KEY (address_id);

ALTER TABLE public.audit_logs ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (log_id);

ALTER TABLE public.auth_challenges ADD CONSTRAINT auth_challenges_pkey PRIMARY KEY (challenge_id);

ALTER TABLE public.business_policies ADD CONSTRAINT business_policies_pkey PRIMARY KEY (policy_id);

ALTER TABLE public.cart_items ADD CONSTRAINT cart_items_pkey PRIMARY KEY (cart_id, product_id);

ALTER TABLE public.case_actions ADD CONSTRAINT case_actions_pkey PRIMARY KEY (action_id);

ALTER TABLE public.case_evidence ADD CONSTRAINT case_evidence_pkey PRIMARY KEY (evidence_id);

ALTER TABLE public.cases ADD CONSTRAINT cases_pkey PRIMARY KEY (case_id);

ALTER TABLE public.categories ADD CONSTRAINT categories_pkey PRIMARY KEY (category_id);

ALTER TABLE public.checkout_groups ADD CONSTRAINT checkout_groups_pkey PRIMARY KEY (group_id);

ALTER TABLE public.conversations ADD CONSTRAINT conversations_pkey PRIMARY KEY (conversation_id);

ALTER TABLE public.ekyc_private_assets ADD CONSTRAINT ekyc_private_assets_pkey PRIMARY KEY (asset_id);

ALTER TABLE public.ekyc_profiles ADD CONSTRAINT ekyc_profiles_pkey PRIMARY KEY (profile_id);

ALTER TABLE public.identity_document_registry ADD CONSTRAINT identity_document_registry_pkey PRIMARY KEY (document_digest);

ALTER TABLE public.interaction_events ADD CONSTRAINT interaction_events_pkey PRIMARY KEY (event_id);

ALTER TABLE public.inventory_reservations ADD CONSTRAINT inventory_reservations_pkey PRIMARY KEY (reservation_id);

ALTER TABLE public.listing_fee_charges ADD CONSTRAINT listing_fee_charges_pkey PRIMARY KEY (charge_id);

ALTER TABLE public.messages ADD CONSTRAINT messages_pkey PRIMARY KEY (message_id);

ALTER TABLE public.notifications ADD CONSTRAINT notifications_pkey PRIMARY KEY (notification_id);

ALTER TABLE public.offers ADD CONSTRAINT offers_pkey PRIMARY KEY (offer_id);

ALTER TABLE public.order_fund_components ADD CONSTRAINT order_fund_components_pkey PRIMARY KEY (component_id);

ALTER TABLE public.order_items ADD CONSTRAINT order_items_pkey PRIMARY KEY (order_item_id);

ALTER TABLE public.orders ADD CONSTRAINT orders_pkey PRIMARY KEY (order_id);

ALTER TABLE public.outbox_events ADD CONSTRAINT outbox_events_pkey PRIMARY KEY (event_id);

ALTER TABLE public.payment_attempts ADD CONSTRAINT payment_attempts_pkey PRIMARY KEY (attempt_id);

ALTER TABLE public.payment_confirmations ADD CONSTRAINT payment_confirmations_pkey PRIMARY KEY (confirmation_id);

ALTER TABLE public.payment_events ADD CONSTRAINT payment_events_pkey PRIMARY KEY (event_id);

ALTER TABLE public.payments ADD CONSTRAINT payments_pkey PRIMARY KEY (payment_id);

ALTER TABLE public.penalty_ledger ADD CONSTRAINT penalty_ledger_pkey PRIMARY KEY (entry_id);

ALTER TABLE public.product_categories ADD CONSTRAINT product_categories_pkey PRIMARY KEY (product_id, category_id);

ALTER TABLE public.product_media ADD CONSTRAINT product_media_pkey PRIMARY KEY (media_id);

ALTER TABLE public.product_revisions ADD CONSTRAINT product_revisions_pkey PRIMARY KEY (revision_id);

ALTER TABLE public.products ADD CONSTRAINT products_pkey PRIMARY KEY (product_id);

ALTER TABLE public.refresh_sessions ADD CONSTRAINT refresh_sessions_pkey PRIMARY KEY (session_id);

ALTER TABLE public.reviews ADD CONSTRAINT reviews_pkey PRIMARY KEY (review_id);

ALTER TABLE public.reward_ledger ADD CONSTRAINT reward_ledger_pkey PRIMARY KEY (entry_id);

ALTER TABLE public.roles ADD CONSTRAINT roles_pkey PRIMARY KEY (role_id);

ALTER TABLE public.seller_buyer_blocks ADD CONSTRAINT seller_buyer_blocks_pkey PRIMARY KEY (seller_id, buyer_id);

ALTER TABLE public.seller_profiles ADD CONSTRAINT seller_profiles_pkey PRIMARY KEY (seller_profile_id);

ALTER TABLE public.settlement_operations ADD CONSTRAINT settlement_operations_pkey PRIMARY KEY (operation_id);

ALTER TABLE public.shipment_events ADD CONSTRAINT shipment_events_pkey PRIMARY KEY (event_id);

ALTER TABLE public.shipments ADD CONSTRAINT shipments_pkey PRIMARY KEY (shipment_id);

ALTER TABLE public.user_roles ADD CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role_id);

ALTER TABLE public.users ADD CONSTRAINT users_pkey PRIMARY KEY (user_id);

ALTER TABLE public.verification_attempts ADD CONSTRAINT verification_attempts_pkey PRIMARY KEY (attempt_id);

ALTER TABLE public.voucher_grants ADD CONSTRAINT voucher_grants_pkey PRIMARY KEY (grant_id);

ALTER TABLE public.voucher_products ADD CONSTRAINT voucher_products_pkey PRIMARY KEY (revision_id, product_id);

ALTER TABLE public.vouchers ADD CONSTRAINT vouchers_pkey PRIMARY KEY (voucher_id);

ALTER TABLE public.addresses ADD CONSTRAINT og70_ref_addresses_address_id_user_id UNIQUE (address_id, user_id);

ALTER TABLE public.audit_logs ADD CONSTRAINT og70_account_status_events_identity UNIQUE (source_account_status_events_event_id);

ALTER TABLE public.audit_logs ADD CONSTRAINT og70_order_events_identity UNIQUE (source_order_events_event_id);

ALTER TABLE public.audit_logs ADD CONSTRAINT og70_report_export_runs_identity UNIQUE (source_report_export_runs_export_id);

ALTER TABLE public.auth_challenges ADD CONSTRAINT og70_password_reset_challenges_identity UNIQUE (source_password_reset_challenges_challenge_id);

ALTER TABLE public.auth_challenges ADD CONSTRAINT og70_quick_auth_results_identity UNIQUE (source_quick_auth_results_result_id);

ALTER TABLE public.auth_challenges ADD CONSTRAINT og70_quick_auth_sessions_identity UNIQUE (source_quick_auth_sessions_session_id);

ALTER TABLE public.auth_challenges ADD CONSTRAINT og70_ref_quick_auth_sessions_session_id_user_id_p_0c14338ed7 UNIQUE (source_quick_auth_sessions_session_id, user_id, profile_id, reference_asset_id);

ALTER TABLE public.business_policies ADD CONSTRAINT og70_checklist_policies_identity UNIQUE (source_checklist_policies_checklist_policy_id);

ALTER TABLE public.business_policies ADD CONSTRAINT og70_listing_fee_policies_identity UNIQUE (source_listing_fee_policies_policy_id);

ALTER TABLE public.business_policies ADD CONSTRAINT og70_penalty_policies_identity UNIQUE (source_penalty_policies_policy_id);

ALTER TABLE public.business_policies ADD CONSTRAINT og70_reward_policies_identity UNIQUE (source_reward_policies_policy_id);

ALTER TABLE public.business_policies ADD CONSTRAINT og70_system_fee_policies_identity UNIQUE (source_system_fee_policies_fee_policy_id);

ALTER TABLE public.case_actions ADD CONSTRAINT og70_case_decisions_identity UNIQUE (source_case_decisions_decision_id);

ALTER TABLE public.case_actions ADD CONSTRAINT og70_case_events_identity UNIQUE (source_case_events_event_id);

ALTER TABLE public.case_actions ADD CONSTRAINT og70_case_rounds_identity UNIQUE (source_case_rounds_round_id);

ALTER TABLE public.case_actions ADD CONSTRAINT og70_money_holds_identity UNIQUE (source_money_holds_hold_id);

ALTER TABLE public.case_actions ADD CONSTRAINT og70_payment_review_audits_identity UNIQUE (source_payment_review_audits_review_audit_id);

ALTER TABLE public.case_actions ADD CONSTRAINT og70_ref_case_rounds_round_id_case_id UNIQUE (source_case_rounds_round_id, case_id);

ALTER TABLE public.case_evidence ADD CONSTRAINT og70_order_unboxing_evidences_identity UNIQUE (source_order_unboxing_evidences_evidence_id);

ALTER TABLE public.cases ADD CONSTRAINT og70_complaints_identity UNIQUE (source_complaints_complaint_id);

ALTER TABLE public.cases ADD CONSTRAINT og70_payment_reconciliation_cases_identity UNIQUE (source_payment_reconciliation_cases_case_id);

ALTER TABLE public.cases ADD CONSTRAINT og70_reports_identity UNIQUE (source_reports_report_id);

ALTER TABLE public.checkout_groups ADD CONSTRAINT og70_ref_checkout_groups_group_id_buyer_id UNIQUE (group_id, buyer_id);

ALTER TABLE public.ekyc_profiles ADD CONSTRAINT og70_ref_ekyc_profiles_profile_id_user_id UNIQUE (profile_id, user_id);

ALTER TABLE public.ekyc_profiles ADD CONSTRAINT og70_seller_verifications_identity UNIQUE (source_seller_verifications_verification_id);

ALTER TABLE public.identity_document_registry ADD CONSTRAINT og70_ref_identity_document_registry_document_digest_user_id UNIQUE (document_digest, user_id);

ALTER TABLE public.listing_fee_charges ADD CONSTRAINT og70_listing_fee_assessments_identity UNIQUE (source_listing_fee_assessments_assessment_id);

ALTER TABLE public.messages ADD CONSTRAINT og70_ref_messages_conversation_id_message_id UNIQUE (conversation_id, message_id);

ALTER TABLE public.notifications ADD CONSTRAINT og70_notification_dispatches_identity UNIQUE (source_notification_dispatches_dispatch_id);

ALTER TABLE public.notifications ADD CONSTRAINT og70_notification_reminders_identity UNIQUE (source_notification_reminders_reminder_id);

ALTER TABLE public.notifications ADD CONSTRAINT og70_payment_alert_requests_identity UNIQUE (source_payment_alert_requests_alert_id);

ALTER TABLE public.offers ADD CONSTRAINT og70_ref_offers_conversation_id_offer_id UNIQUE (conversation_id, offer_id);

ALTER TABLE public.order_fund_components ADD CONSTRAINT og70_payment_allocations_identity UNIQUE (source_payment_allocations_allocation_id);

ALTER TABLE public.order_fund_components ADD CONSTRAINT og70_ref_payment_allocations_allocation_id_order_id UNIQUE (source_payment_allocations_allocation_id, order_id);

ALTER TABLE public.order_items ADD CONSTRAINT og70_ref_order_items_order_item_id_product_id UNIQUE (order_item_id, product_id);

ALTER TABLE public.orders ADD CONSTRAINT og70_ref_orders_order_id_buyer_id UNIQUE (order_id, buyer_id);

ALTER TABLE public.payment_attempts ADD CONSTRAINT og70_ref_payment_attempts_attempt_id_intent_id UNIQUE (attempt_id, intent_id);

ALTER TABLE public.payment_attempts ADD CONSTRAINT og70_ref_payment_attempts_attempt_id_order_id_payment_id UNIQUE (attempt_id, order_id, payment_id);

ALTER TABLE public.payment_attempts ADD CONSTRAINT og70_vnpay_payment_attempts_identity UNIQUE (source_vnpay_payment_attempts_transaction_ref);

ALTER TABLE public.payment_confirmations ADD CONSTRAINT og70_listing_fee_receipts_identity UNIQUE (source_listing_fee_receipts_receipt_id);

ALTER TABLE public.payment_confirmations ADD CONSTRAINT og70_ref_payment_confirmations_confirmation_id_intent_id UNIQUE (confirmation_id, intent_id);

ALTER TABLE public.payment_events ADD CONSTRAINT og70_payment_ipn_events_identity UNIQUE (source_payment_ipn_events_event_id);

ALTER TABLE public.payment_events ADD CONSTRAINT og70_payment_ipn_raw_receipts_identity UNIQUE (source_payment_ipn_raw_receipts_receipt_id);

ALTER TABLE public.payments ADD CONSTRAINT og70_payment_intents_identity UNIQUE (source_payment_intents_intent_id);

ALTER TABLE public.payments ADD CONSTRAINT og70_ref_payments_payment_id_order_id UNIQUE (payment_id, order_id);

ALTER TABLE public.product_media ADD CONSTRAINT og70_revision_media_identity UNIQUE (source_revision_media_revision_media_id);

ALTER TABLE public.product_revisions ADD CONSTRAINT og70_ref_product_revisions_revision_id_product_id UNIQUE (revision_id, product_id);

ALTER TABLE public.products ADD CONSTRAINT og70_ref_products_product_id_seller_id UNIQUE (product_id, seller_id);

ALTER TABLE public.shipment_events ADD CONSTRAINT og70_handover_confirmations_identity UNIQUE (source_handover_confirmations_confirmation_id);

ALTER TABLE public.shipment_events ADD CONSTRAINT og70_pickup_attempts_identity UNIQUE (source_pickup_attempts_attempt_id);

ALTER TABLE public.verification_attempts ADD CONSTRAINT og70_ekyc_verification_attempts_identity UNIQUE (source_ekyc_verification_attempts_attempt_id);

ALTER TABLE public.verification_attempts ADD CONSTRAINT og70_quick_auth_attempts_identity UNIQUE (source_quick_auth_attempts_attempt_id);

ALTER TABLE public.verification_attempts ADD CONSTRAINT og70_seller_verification_metrics_identity UNIQUE (source_seller_verification_metrics_metric_id);

ALTER TABLE public.vouchers ADD CONSTRAINT og70_voucher_revisions_identity UNIQUE (source_voucher_revisions_revision_id);

ALTER TABLE public.addresses ADD CONSTRAINT addresses_record_type_check CHECK ((record_type = 'addresses'::text));

ALTER TABLE public.audit_logs ADD CONSTRAINT audit_logs_record_type_check CHECK ((record_type = ANY (ARRAY['audit_logs'::text, 'account_status_events'::text, 'order_events'::text, 'report_export_runs'::text])));

ALTER TABLE public.auth_challenges ADD CONSTRAINT auth_challenges_record_type_check CHECK ((record_type = ANY (ARRAY['auth_challenges'::text, 'password_reset_challenges'::text, 'quick_auth_sessions'::text, 'quick_auth_results'::text])));

ALTER TABLE public.business_policies ADD CONSTRAINT business_policies_record_type_check CHECK ((record_type = ANY (ARRAY['system_fee_policies'::text, 'listing_fee_policies'::text, 'checklist_policies'::text, 'reward_policies'::text, 'penalty_policies'::text])));

ALTER TABLE public.cart_items ADD CONSTRAINT cart_items_record_type_check CHECK ((record_type = 'cart_items'::text));

ALTER TABLE public.case_actions ADD CONSTRAINT case_actions_record_type_check CHECK ((record_type = ANY (ARRAY['case_rounds'::text, 'case_decisions'::text, 'case_events'::text, 'money_holds'::text, 'payment_review_audits'::text])));

ALTER TABLE public.case_evidence ADD CONSTRAINT case_evidence_record_type_check CHECK ((record_type = ANY (ARRAY['case_evidence'::text, 'order_unboxing_evidences'::text])));

ALTER TABLE public.cases ADD CONSTRAINT cases_record_type_check CHECK ((record_type = ANY (ARRAY['cases'::text, 'complaints'::text, 'reports'::text, 'payment_reconciliation_cases'::text])));

ALTER TABLE public.categories ADD CONSTRAINT categories_record_type_check CHECK ((record_type = 'categories'::text));

ALTER TABLE public.checkout_groups ADD CONSTRAINT checkout_groups_record_type_check CHECK ((record_type = 'checkout_groups'::text));

ALTER TABLE public.checkout_groups ADD CONSTRAINT checkout_groups_voucher_redemption_check CHECK ((jsonb_typeof(voucher_redemption) = 'array'::text));

ALTER TABLE public.checkout_groups ADD CONSTRAINT checkout_groups_zero_confirmation_check CHECK ((jsonb_typeof(zero_confirmation) = 'array'::text));

ALTER TABLE public.conversations ADD CONSTRAINT conversations_media_quota_usage_check CHECK ((jsonb_typeof(media_quota_usage) = 'array'::text));

ALTER TABLE public.conversations ADD CONSTRAINT conversations_participant_state_check CHECK ((jsonb_typeof(participant_state) = 'array'::text));

ALTER TABLE public.conversations ADD CONSTRAINT conversations_record_type_check CHECK ((record_type = 'conversations'::text));

ALTER TABLE public.ekyc_private_assets ADD CONSTRAINT ekyc_private_assets_record_type_check CHECK ((record_type = 'ekyc_private_assets'::text));

ALTER TABLE public.ekyc_profiles ADD CONSTRAINT ekyc_profiles_ekyc_decisions_history_check CHECK ((jsonb_typeof(ekyc_decisions_history) = 'array'::text));

ALTER TABLE public.ekyc_profiles ADD CONSTRAINT ekyc_profiles_record_type_check CHECK ((record_type = ANY (ARRAY['ekyc_profiles'::text, 'seller_verifications'::text])));

ALTER TABLE public.identity_document_registry ADD CONSTRAINT identity_document_registry_record_type_check CHECK ((record_type = 'identity_document_registry'::text));

ALTER TABLE public.interaction_events ADD CONSTRAINT interaction_events_record_type_check CHECK ((record_type = 'interaction_events'::text));

ALTER TABLE public.inventory_reservations ADD CONSTRAINT inventory_reservations_record_type_check CHECK ((record_type = 'inventory_reservations'::text));

ALTER TABLE public.listing_fee_charges ADD CONSTRAINT listing_fee_charges_record_type_check CHECK ((record_type = ANY (ARRAY['listing_fee_charges'::text, 'listing_fee_assessments'::text])));

ALTER TABLE public.messages ADD CONSTRAINT messages_record_type_check CHECK ((record_type = 'messages'::text));

ALTER TABLE public.notifications ADD CONSTRAINT notifications_delivery_history_check CHECK ((jsonb_typeof(delivery_history) = 'array'::text));

ALTER TABLE public.notifications ADD CONSTRAINT notifications_record_type_check CHECK ((record_type = ANY (ARRAY['notifications'::text, 'notification_dispatches'::text, 'notification_reminders'::text, 'payment_alert_requests'::text])));

ALTER TABLE public.offers ADD CONSTRAINT offers_record_type_check CHECK ((record_type = 'offers'::text));

ALTER TABLE public.order_fund_components ADD CONSTRAINT order_fund_components_record_type_check CHECK ((record_type = ANY (ARRAY['order_fund_components'::text, 'payment_allocations'::text])));

ALTER TABLE public.order_items ADD CONSTRAINT order_items_discount_history_check CHECK ((jsonb_typeof(discount_history) = 'array'::text));

ALTER TABLE public.order_items ADD CONSTRAINT order_items_record_type_check CHECK ((record_type = 'order_items'::text));

ALTER TABLE public.orders ADD CONSTRAINT orders_conversation_links_check CHECK ((jsonb_typeof(conversation_links) = 'array'::text));

ALTER TABLE public.orders ADD CONSTRAINT orders_legacy_voucher_history_check CHECK ((jsonb_typeof(legacy_voucher_history) = 'array'::text));

ALTER TABLE public.orders ADD CONSTRAINT orders_record_type_check CHECK ((record_type = 'orders'::text));

ALTER TABLE public.outbox_events ADD CONSTRAINT outbox_events_consumer_progress_check CHECK ((jsonb_typeof(consumer_progress) = 'array'::text));

ALTER TABLE public.outbox_events ADD CONSTRAINT outbox_events_record_type_check CHECK ((record_type = 'outbox_events'::text));

ALTER TABLE public.payment_attempts ADD CONSTRAINT payment_attempts_record_type_check CHECK ((record_type = ANY (ARRAY['payment_attempts'::text, 'vnpay_payment_attempts'::text])));

ALTER TABLE public.payment_confirmations ADD CONSTRAINT payment_confirmations_record_type_check CHECK ((record_type = ANY (ARRAY['payment_confirmations'::text, 'listing_fee_receipts'::text])));

ALTER TABLE public.payment_events ADD CONSTRAINT payment_events_record_type_check CHECK ((record_type = ANY (ARRAY['payment_ipn_raw_receipts'::text, 'payment_ipn_events'::text])));

ALTER TABLE public.payments ADD CONSTRAINT payments_record_type_check CHECK ((record_type = ANY (ARRAY['payments'::text, 'payment_intents'::text])));

ALTER TABLE public.penalty_ledger ADD CONSTRAINT penalty_ledger_record_type_check CHECK ((record_type = 'penalty_ledger'::text));

ALTER TABLE public.product_categories ADD CONSTRAINT product_categories_record_type_check CHECK ((record_type = 'product_categories'::text));

ALTER TABLE public.product_media ADD CONSTRAINT product_media_record_type_check CHECK ((record_type = ANY (ARRAY['product_media'::text, 'revision_media'::text])));

ALTER TABLE public.product_revisions ADD CONSTRAINT product_revisions_legacy_moderation_history_check CHECK ((jsonb_typeof(legacy_moderation_history) = 'array'::text));

ALTER TABLE public.product_revisions ADD CONSTRAINT product_revisions_media_analysis_runs_history_check CHECK ((jsonb_typeof(media_analysis_runs_history) = 'array'::text));

ALTER TABLE public.product_revisions ADD CONSTRAINT product_revisions_moderation_history_check CHECK ((jsonb_typeof(moderation_history) = 'array'::text));

ALTER TABLE public.product_revisions ADD CONSTRAINT product_revisions_record_type_check CHECK ((record_type = 'product_revisions'::text));

ALTER TABLE public.products ADD CONSTRAINT products_record_type_check CHECK ((record_type = 'products'::text));

ALTER TABLE public.refresh_sessions ADD CONSTRAINT refresh_sessions_record_type_check CHECK ((record_type = 'refresh_sessions'::text));

ALTER TABLE public.reviews ADD CONSTRAINT reviews_edit_history_check CHECK ((jsonb_typeof(edit_history) = 'array'::text));

ALTER TABLE public.reviews ADD CONSTRAINT reviews_record_type_check CHECK ((record_type = 'reviews'::text));

ALTER TABLE public.reward_ledger ADD CONSTRAINT reward_ledger_record_type_check CHECK ((record_type = 'reward_ledger'::text));

ALTER TABLE public.roles ADD CONSTRAINT roles_record_type_check CHECK ((record_type = 'roles'::text));

ALTER TABLE public.seller_buyer_blocks ADD CONSTRAINT seller_buyer_blocks_record_type_check CHECK ((record_type = 'seller_buyer_blocks'::text));

ALTER TABLE public.seller_profiles ADD CONSTRAINT seller_profiles_record_type_check CHECK ((record_type = 'seller_profiles'::text));

ALTER TABLE public.seller_profiles ADD CONSTRAINT seller_profiles_seller_profile_decisions_history_check CHECK ((jsonb_typeof(seller_profile_decisions_history) = 'array'::text));

ALTER TABLE public.settlement_operations ADD CONSTRAINT settlement_operations_record_type_check CHECK ((record_type = 'settlement_operations'::text));

ALTER TABLE public.shipment_events ADD CONSTRAINT shipment_events_record_type_check CHECK ((record_type = ANY (ARRAY['shipment_events'::text, 'pickup_attempts'::text, 'handover_confirmations'::text])));

ALTER TABLE public.shipments ADD CONSTRAINT shipments_record_type_check CHECK ((record_type = 'shipments'::text));

ALTER TABLE public.user_roles ADD CONSTRAINT user_roles_record_type_check CHECK ((record_type = 'user_roles'::text));

ALTER TABLE public.users ADD CONSTRAINT users_account_restrictions_history_check CHECK ((jsonb_typeof(account_restrictions_history) = 'array'::text));

ALTER TABLE public.users ADD CONSTRAINT users_carts_history_check CHECK ((jsonb_typeof(carts_history) = 'array'::text));

ALTER TABLE public.users ADD CONSTRAINT users_external_identities_history_check CHECK ((jsonb_typeof(external_identities_history) = 'array'::text));

ALTER TABLE public.users ADD CONSTRAINT users_record_type_check CHECK ((record_type = 'users'::text));

ALTER TABLE public.users ADD CONSTRAINT users_reward_accounts_history_check CHECK ((jsonb_typeof(reward_accounts_history) = 'array'::text));

ALTER TABLE public.users ADD CONSTRAINT users_user_security_settings_history_check CHECK ((jsonb_typeof(user_security_settings_history) = 'array'::text));

ALTER TABLE public.verification_attempts ADD CONSTRAINT verification_attempts_record_type_check CHECK ((record_type = ANY (ARRAY['ekyc_verification_attempts'::text, 'quick_auth_attempts'::text, 'seller_verification_metrics'::text])));

ALTER TABLE public.voucher_grants ADD CONSTRAINT voucher_grants_grant_history_check CHECK ((jsonb_typeof(grant_history) = 'array'::text));

ALTER TABLE public.voucher_grants ADD CONSTRAINT voucher_grants_record_type_check CHECK ((record_type = 'voucher_grants'::text));

ALTER TABLE public.voucher_products ADD CONSTRAINT voucher_products_record_type_check CHECK ((record_type = 'voucher_revision_products'::text));

ALTER TABLE public.vouchers ADD CONSTRAINT vouchers_record_type_check CHECK ((record_type = ANY (ARRAY['vouchers'::text, 'voucher_revisions'::text])));

ALTER TABLE public.vouchers ADD CONSTRAINT vouchers_revocation_history_check CHECK ((jsonb_typeof(revocation_history) = 'array'::text));

ALTER TABLE public.addresses ADD CONSTRAINT og70_fk_addresses_fk_addresses_user FOREIGN KEY (user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.audit_logs ADD CONSTRAINT og70_fk_account_status_events_account_status_even_7f7813debb FOREIGN KEY (user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.audit_logs ADD CONSTRAINT og70_fk_audit_logs_fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.audit_logs ADD CONSTRAINT og70_fk_order_events_order_events_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.audit_logs ADD CONSTRAINT og70_fk_order_events_order_events_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(order_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.audit_logs ADD CONSTRAINT og70_fk_report_export_runs_report_export_runs_req_954f725945 FOREIGN KEY (requested_by) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.auth_challenges ADD CONSTRAINT og70_fk_auth_challenges_auth_challenges_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.auth_challenges ADD CONSTRAINT og70_fk_password_reset_challenges_fk_password_res_acc0ca4f30 FOREIGN KEY (user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.auth_challenges ADD CONSTRAINT og70_fk_quick_auth_results_fk_quick_result_session FOREIGN KEY (session_id, user_id, profile_id, reference_asset_id) REFERENCES auth_challenges(source_quick_auth_sessions_session_id, user_id, profile_id, reference_asset_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.auth_challenges ADD CONSTRAINT og70_fk_quick_auth_results_quick_auth_results_pro_60d5b26f3f FOREIGN KEY (profile_id, user_id) REFERENCES ekyc_profiles(profile_id, user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.auth_challenges ADD CONSTRAINT og70_fk_quick_auth_results_quick_auth_results_ref_90b4709a8c FOREIGN KEY (reference_asset_id) REFERENCES ekyc_private_assets(asset_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.auth_challenges ADD CONSTRAINT og70_fk_quick_auth_results_quick_auth_results_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.auth_challenges ADD CONSTRAINT og70_fk_quick_auth_sessions_quick_auth_sessions_p_1b2e62aaef FOREIGN KEY (profile_id, user_id) REFERENCES ekyc_profiles(profile_id, user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.auth_challenges ADD CONSTRAINT og70_fk_quick_auth_sessions_quick_auth_sessions_r_0072f9adfd FOREIGN KEY (reference_asset_id) REFERENCES ekyc_private_assets(asset_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.business_policies ADD CONSTRAINT og70_fk_checklist_policies_checklist_policies_cat_f4cb5b27c3 FOREIGN KEY (category_id) REFERENCES categories(category_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.business_policies ADD CONSTRAINT og70_fk_checklist_policies_checklist_policies_cre_9bc6414d02 FOREIGN KEY (created_by) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.business_policies ADD CONSTRAINT og70_fk_listing_fee_policies_listing_fee_policies_6e39689fd1 FOREIGN KEY (created_by) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.business_policies ADD CONSTRAINT og70_fk_penalty_policies_penalty_policies_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.business_policies ADD CONSTRAINT og70_fk_reward_policies_reward_policies_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.business_policies ADD CONSTRAINT og70_fk_system_fee_policies_fk_system_fee_policie_4081d0ed39 FOREIGN KEY (created_by) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cart_items ADD CONSTRAINT og70_cart_user_fk FOREIGN KEY (user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cart_items ADD CONSTRAINT og70_fk_cart_items_fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products(product_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.case_actions ADD CONSTRAINT og70_fk_case_decisions_case_decisions_reviewed_by_fkey FOREIGN KEY (reviewed_by) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.case_actions ADD CONSTRAINT og70_fk_case_decisions_case_decisions_round_id_fkey FOREIGN KEY (round_id) REFERENCES case_actions(source_case_rounds_round_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.case_actions ADD CONSTRAINT og70_fk_case_decisions_case_decisions_supersedes__4799cfd056 FOREIGN KEY (supersedes_decision_id) REFERENCES case_actions(source_case_decisions_decision_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.case_actions ADD CONSTRAINT og70_fk_case_events_case_events_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.case_actions ADD CONSTRAINT og70_fk_case_events_case_events_case_id_fkey FOREIGN KEY (case_id) REFERENCES cases(case_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.case_actions ADD CONSTRAINT og70_fk_case_events_case_events_recipient_id_fkey FOREIGN KEY (recipient_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.case_actions ADD CONSTRAINT og70_fk_case_events_case_events_round_id_case_id_fkey FOREIGN KEY (round_id, case_id) REFERENCES case_actions(source_case_rounds_round_id, case_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.case_actions ADD CONSTRAINT og70_fk_case_rounds_case_rounds_case_id_fkey FOREIGN KEY (case_id) REFERENCES cases(case_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.case_actions ADD CONSTRAINT og70_fk_money_holds_money_holds_case_id_fkey FOREIGN KEY (case_id) REFERENCES cases(case_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.case_actions ADD CONSTRAINT og70_fk_money_holds_money_holds_component_id_fkey FOREIGN KEY (component_id) REFERENCES order_fund_components(component_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.case_actions ADD CONSTRAINT og70_fk_payment_review_audits_fk_review_intent_attempt FOREIGN KEY (attempt_id, intent_id) REFERENCES payment_attempts(attempt_id, intent_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.case_actions ADD CONSTRAINT og70_fk_payment_review_audits_payment_review_audi_246cac2d8e FOREIGN KEY (attempt_id) REFERENCES payment_attempts(attempt_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.case_actions ADD CONSTRAINT og70_fk_payment_review_audits_payment_review_audi_5cbe7abecf FOREIGN KEY (actor_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.case_actions ADD CONSTRAINT og70_fk_payment_review_audits_payment_review_audi_785a7a809b FOREIGN KEY (order_id) REFERENCES orders(order_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.case_actions ADD CONSTRAINT og70_fk_payment_review_audits_payment_review_audi_8110b0a0b1 FOREIGN KEY (payment_id) REFERENCES payments(payment_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.case_actions ADD CONSTRAINT og70_fk_payment_review_audits_payment_review_audi_fd89cd58e5 FOREIGN KEY (intent_id) REFERENCES payments(source_payment_intents_intent_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.case_evidence ADD CONSTRAINT og70_fk_case_evidence_case_evidence_round_id_fkey FOREIGN KEY (round_id) REFERENCES case_actions(source_case_rounds_round_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.case_evidence ADD CONSTRAINT og70_fk_case_evidence_case_evidence_submitted_by_fkey FOREIGN KEY (submitted_by) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.case_evidence ADD CONSTRAINT og70_fk_order_unboxing_evidences_fk_unboxing_buyer FOREIGN KEY (buyer_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.case_evidence ADD CONSTRAINT og70_fk_order_unboxing_evidences_fk_unboxing_order FOREIGN KEY (order_id) REFERENCES orders(order_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.case_evidence ADD CONSTRAINT og70_fk_order_unboxing_evidences_fk_unboxing_order_buyer FOREIGN KEY (order_id, buyer_id) REFERENCES orders(order_id, buyer_id) DEFERRABLE INITIALLY DEFERRED NOT VALID;

ALTER TABLE public.cases ADD CONSTRAINT og70_fk_cases_cases_assigned_to_fkey FOREIGN KEY (assigned_to) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cases ADD CONSTRAINT og70_fk_cases_cases_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cases ADD CONSTRAINT og70_fk_cases_cases_listing_charge_id_fkey FOREIGN KEY (listing_charge_id) REFERENCES listing_fee_charges(charge_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cases ADD CONSTRAINT og70_fk_cases_cases_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(order_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cases ADD CONSTRAINT og70_fk_cases_cases_payment_intent_id_fkey FOREIGN KEY (payment_intent_id) REFERENCES payments(source_payment_intents_intent_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cases ADD CONSTRAINT og70_fk_cases_cases_product_id_fkey FOREIGN KEY (product_id) REFERENCES products(product_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cases ADD CONSTRAINT og70_fk_cases_cases_target_user_id_fkey FOREIGN KEY (target_user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cases ADD CONSTRAINT og70_fk_complaints_fk_complaints_created_by FOREIGN KEY (created_by) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cases ADD CONSTRAINT og70_fk_complaints_fk_complaints_order FOREIGN KEY (order_id) REFERENCES orders(order_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cases ADD CONSTRAINT og70_fk_complaints_fk_complaints_resolved_by FOREIGN KEY (resolved_by) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cases ADD CONSTRAINT og70_fk_payment_reconciliation_cases_fk_reconcili_009609a61d FOREIGN KEY (attempt_id, intent_id) REFERENCES payment_attempts(attempt_id, intent_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cases ADD CONSTRAINT og70_fk_payment_reconciliation_cases_payment_reco_0b15a8fa23 FOREIGN KEY (event_id) REFERENCES payment_events(source_payment_ipn_events_event_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cases ADD CONSTRAINT og70_fk_payment_reconciliation_cases_payment_reco_2f65fac2e2 FOREIGN KEY (order_id) REFERENCES orders(order_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cases ADD CONSTRAINT og70_fk_payment_reconciliation_cases_payment_reco_47acb382d6 FOREIGN KEY (review_audit_id) REFERENCES case_actions(source_payment_review_audits_review_audit_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cases ADD CONSTRAINT og70_fk_payment_reconciliation_cases_payment_reco_83f2237ffd FOREIGN KEY (attempt_id) REFERENCES payment_attempts(attempt_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cases ADD CONSTRAINT og70_fk_payment_reconciliation_cases_payment_reco_af7ca13ee8 FOREIGN KEY (assigned_user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cases ADD CONSTRAINT og70_fk_payment_reconciliation_cases_payment_reco_bbbc061557 FOREIGN KEY (intent_id) REFERENCES payments(source_payment_intents_intent_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cases ADD CONSTRAINT og70_fk_payment_reconciliation_cases_payment_reco_f1c1b32b7c FOREIGN KEY (payment_id) REFERENCES payments(payment_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cases ADD CONSTRAINT og70_fk_reports_fk_reports_product FOREIGN KEY (product_id) REFERENCES products(product_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cases ADD CONSTRAINT og70_fk_reports_fk_reports_reported_user FOREIGN KEY (reported_user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cases ADD CONSTRAINT og70_fk_reports_fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cases ADD CONSTRAINT og70_fk_reports_fk_reports_resolved_by FOREIGN KEY (resolved_by) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.cases ADD CONSTRAINT og70_fk_reports_reports_message_id_fkey FOREIGN KEY (message_id) REFERENCES messages(message_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.categories ADD CONSTRAINT og70_fk_categories_fk_categories_parent FOREIGN KEY (parent_category_id) REFERENCES categories(category_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.checkout_groups ADD CONSTRAINT og70_fk_checkout_groups_checkout_groups_buyer_id_fkey FOREIGN KEY (buyer_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.conversations ADD CONSTRAINT og70_fk_conversations_fk_conversations_buyer FOREIGN KEY (buyer_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.conversations ADD CONSTRAINT og70_fk_conversations_fk_conversations_last_message FOREIGN KEY (conversation_id, last_message_id) REFERENCES messages(conversation_id, message_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.conversations ADD CONSTRAINT og70_fk_conversations_fk_conversations_product FOREIGN KEY (product_id) REFERENCES products(product_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.conversations ADD CONSTRAINT og70_fk_conversations_fk_conversations_product_seller FOREIGN KEY (product_id, seller_id) REFERENCES products(product_id, seller_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.conversations ADD CONSTRAINT og70_fk_conversations_fk_conversations_seller FOREIGN KEY (seller_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.ekyc_private_assets ADD CONSTRAINT og70_fk_ekyc_private_assets_ekyc_private_assets_p_65d3aea3d7 FOREIGN KEY (profile_id) REFERENCES ekyc_profiles(profile_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.ekyc_profiles ADD CONSTRAINT og70_fk_ekyc_profiles_ekyc_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.ekyc_profiles ADD CONSTRAINT og70_fk_ekyc_profiles_fk_ekyc_document_owner FOREIGN KEY (document_digest, user_id) REFERENCES identity_document_registry(document_digest, user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.ekyc_profiles ADD CONSTRAINT og70_fk_seller_verifications_fk_seller_verifications_admin FOREIGN KEY (verified_by) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.ekyc_profiles ADD CONSTRAINT og70_fk_seller_verifications_fk_seller_verifications_user FOREIGN KEY (user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.identity_document_registry ADD CONSTRAINT og70_fk_identity_document_registry_identity_docum_171282d604 FOREIGN KEY (user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.identity_document_registry ADD CONSTRAINT og70_fk_identity_document_registry_identity_docum_4019d76acb FOREIGN KEY (first_profile_id, user_id) REFERENCES ekyc_profiles(profile_id, user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.interaction_events ADD CONSTRAINT og70_fk_interaction_events_interaction_events_pro_b18cf120cc FOREIGN KEY (product_id) REFERENCES products(product_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.interaction_events ADD CONSTRAINT og70_fk_interaction_events_interaction_events_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.inventory_reservations ADD CONSTRAINT og70_fk_inventory_reservations_inventory_reservat_22a85188ce FOREIGN KEY (order_item_id, product_id) REFERENCES order_items(order_item_id, product_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.inventory_reservations ADD CONSTRAINT og70_fk_inventory_reservations_inventory_reservat_9cc7e481f0 FOREIGN KEY (product_id) REFERENCES products(product_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.listing_fee_charges ADD CONSTRAINT og70_fk_listing_fee_assessments_listing_fee_asses_11385342b0 FOREIGN KEY (revision_id, product_id) REFERENCES product_revisions(revision_id, product_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.listing_fee_charges ADD CONSTRAINT og70_fk_listing_fee_assessments_listing_fee_asses_1f230a7c75 FOREIGN KEY (policy_id) REFERENCES business_policies(source_listing_fee_policies_policy_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.listing_fee_charges ADD CONSTRAINT og70_fk_listing_fee_assessments_listing_fee_asses_59e0f2bc5f FOREIGN KEY (product_id) REFERENCES products(product_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.listing_fee_charges ADD CONSTRAINT og70_fk_listing_fee_charges_listing_fee_charges_a_2d81f89c36 FOREIGN KEY (assessment_id) REFERENCES listing_fee_charges(source_listing_fee_assessments_assessment_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.messages ADD CONSTRAINT og70_fk_messages_fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(conversation_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.messages ADD CONSTRAINT og70_fk_messages_fk_messages_offer FOREIGN KEY (conversation_id, offer_id) REFERENCES offers(conversation_id, offer_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.messages ADD CONSTRAINT og70_fk_messages_fk_messages_reply FOREIGN KEY (conversation_id, reply_to_message_id) REFERENCES messages(conversation_id, message_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.messages ADD CONSTRAINT og70_fk_messages_fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.notifications ADD CONSTRAINT og70_fk_notification_dispatches_notification_disp_c53d0fe508 FOREIGN KEY (event_id) REFERENCES outbox_events(event_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.notifications ADD CONSTRAINT og70_fk_notification_dispatches_notification_disp_db9f847f16 FOREIGN KEY (sender_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.notifications ADD CONSTRAINT og70_fk_notification_reminders_notification_remin_3c53b6de51 FOREIGN KEY (user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.notifications ADD CONSTRAINT og70_fk_notifications_fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.notifications ADD CONSTRAINT og70_fk_notifications_notifications_dispatch_id_fkey FOREIGN KEY (dispatch_id) REFERENCES notifications(source_notification_dispatches_dispatch_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.notifications ADD CONSTRAINT og70_fk_notifications_notifications_event_id_fkey FOREIGN KEY (event_id) REFERENCES outbox_events(event_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.notifications ADD CONSTRAINT og70_fk_notifications_notifications_sender_id_fkey FOREIGN KEY (sender_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.notifications ADD CONSTRAINT og70_fk_payment_alert_requests_payment_alert_requ_fd02b63dd2 FOREIGN KEY (case_id) REFERENCES cases(source_payment_reconciliation_cases_case_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.offers ADD CONSTRAINT og70_fk_offers_fk_offers_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(conversation_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.offers ADD CONSTRAINT og70_fk_offers_fk_offers_fee_policy FOREIGN KEY (fee_policy_id) REFERENCES business_policies(source_system_fee_policies_fee_policy_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.offers ADD CONSTRAINT og70_fk_offers_fk_offers_parent FOREIGN KEY (conversation_id, parent_offer_id) REFERENCES offers(conversation_id, offer_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.order_fund_components ADD CONSTRAINT og70_fk_order_fund_components_order_fund_componen_8e2ad92d19 FOREIGN KEY (allocation_id, order_id) REFERENCES order_fund_components(source_payment_allocations_allocation_id, order_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.order_fund_components ADD CONSTRAINT og70_fk_order_fund_components_order_fund_componen_9fb36150a8 FOREIGN KEY (late_confirmation_id) REFERENCES payment_confirmations(confirmation_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.order_fund_components ADD CONSTRAINT og70_fk_order_fund_components_order_fund_componen_ac6ddb1bf0 FOREIGN KEY (order_id) REFERENCES orders(order_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.order_fund_components ADD CONSTRAINT og70_fk_payment_allocations_payment_allocations_c_8409d8c31c FOREIGN KEY (confirmation_id, intent_id) REFERENCES payment_confirmations(confirmation_id, intent_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.order_fund_components ADD CONSTRAINT og70_fk_payment_allocations_payment_allocations_i_d4289c9182 FOREIGN KEY (intent_id) REFERENCES payments(source_payment_intents_intent_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.order_fund_components ADD CONSTRAINT og70_fk_payment_allocations_payment_allocations_o_6e98180e36 FOREIGN KEY (order_id) REFERENCES orders(order_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.order_items ADD CONSTRAINT og70_fk_order_items_fk_item_product_revision FOREIGN KEY (product_revision_id, product_id) REFERENCES product_revisions(revision_id, product_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.order_items ADD CONSTRAINT og70_fk_order_items_fk_order_items_accepted_offer FOREIGN KEY (accepted_offer_id) REFERENCES offers(offer_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.order_items ADD CONSTRAINT og70_fk_order_items_fk_order_items_fee_policy FOREIGN KEY (fee_policy_id) REFERENCES business_policies(source_system_fee_policies_fee_policy_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.order_items ADD CONSTRAINT og70_fk_order_items_fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(order_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.order_items ADD CONSTRAINT og70_fk_order_items_fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(product_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.order_items ADD CONSTRAINT og70_item_conversation_fk FOREIGN KEY (conversation_id) REFERENCES conversations(conversation_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.orders ADD CONSTRAINT og70_fk_orders_fk_order_checkout_group FOREIGN KEY (group_id, buyer_id) REFERENCES checkout_groups(group_id, buyer_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.orders ADD CONSTRAINT og70_fk_orders_fk_orders_buyer FOREIGN KEY (buyer_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.orders ADD CONSTRAINT og70_fk_orders_fk_orders_seller FOREIGN KEY (seller_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.orders ADD CONSTRAINT og70_fk_orders_fk_orders_source_address FOREIGN KEY (source_address_id) REFERENCES addresses(address_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.payment_attempts ADD CONSTRAINT og70_fk_payment_attempts_fk_attempt_payment_order FOREIGN KEY (payment_id, order_id) REFERENCES payments(payment_id, order_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.payment_attempts ADD CONSTRAINT og70_fk_payment_attempts_payment_attempts_intent_id_fkey FOREIGN KEY (intent_id) REFERENCES payments(source_payment_intents_intent_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.payment_attempts ADD CONSTRAINT og70_fk_vnpay_payment_attempts_vnpay_payment_atte_7f2981e6dd FOREIGN KEY (payment_id) REFERENCES payments(payment_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.payment_attempts ADD CONSTRAINT og70_fk_vnpay_payment_attempts_vnpay_payment_atte_e30a3951c9 FOREIGN KEY (order_id) REFERENCES orders(order_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.payment_confirmations ADD CONSTRAINT og70_fk_listing_fee_receipts_listing_fee_receipts_e92c2c95ae FOREIGN KEY (charge_id) REFERENCES listing_fee_charges(charge_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.payment_confirmations ADD CONSTRAINT og70_fk_payment_confirmations_payment_confirmatio_0eaf0b33a1 FOREIGN KEY (ipn_event_id) REFERENCES payment_events(source_payment_ipn_events_event_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.payment_confirmations ADD CONSTRAINT og70_fk_payment_confirmations_payment_confirmatio_4d40708fb2 FOREIGN KEY (attempt_id, intent_id) REFERENCES payment_attempts(attempt_id, intent_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.payment_confirmations ADD CONSTRAINT og70_fk_payment_confirmations_payment_confirmatio_549a6d5b54 FOREIGN KEY (intent_id) REFERENCES payments(source_payment_intents_intent_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.payment_events ADD CONSTRAINT og70_fk_payment_ipn_events_fk_ipn_intent_attempt FOREIGN KEY (attempt_id, intent_id) REFERENCES payment_attempts(attempt_id, intent_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.payment_events ADD CONSTRAINT og70_fk_payment_ipn_events_fk_ipn_legacy_attempt FOREIGN KEY (attempt_id, order_id, payment_id) REFERENCES payment_attempts(attempt_id, order_id, payment_id) DEFERRABLE INITIALLY DEFERRED NOT VALID;

ALTER TABLE public.payment_events ADD CONSTRAINT og70_fk_payment_ipn_events_payment_ipn_events_att_8d63a2e436 FOREIGN KEY (attempt_id) REFERENCES payment_attempts(attempt_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.payment_events ADD CONSTRAINT og70_fk_payment_ipn_events_payment_ipn_events_intent_id_fkey FOREIGN KEY (intent_id) REFERENCES payments(source_payment_intents_intent_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.payment_events ADD CONSTRAINT og70_fk_payment_ipn_events_payment_ipn_events_rec_4c693e90d7 FOREIGN KEY (receipt_id) REFERENCES payment_events(source_payment_ipn_raw_receipts_receipt_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.payments ADD CONSTRAINT og70_fk_payment_intents_payment_intents_group_id_fkey FOREIGN KEY (group_id) REFERENCES checkout_groups(group_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.payments ADD CONSTRAINT og70_fk_payment_intents_payment_intents_listing_c_914dfc7386 FOREIGN KEY (listing_charge_id) REFERENCES listing_fee_charges(charge_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.payments ADD CONSTRAINT og70_fk_payments_fk_payments_order FOREIGN KEY (order_id) REFERENCES orders(order_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.penalty_ledger ADD CONSTRAINT og70_fk_penalty_ledger_penalty_ledger_adjustment_of_fkey FOREIGN KEY (adjustment_of) REFERENCES penalty_ledger(entry_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.penalty_ledger ADD CONSTRAINT og70_fk_penalty_ledger_penalty_ledger_decided_by_fkey FOREIGN KEY (decided_by) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.penalty_ledger ADD CONSTRAINT og70_fk_penalty_ledger_penalty_ledger_policy_id_fkey FOREIGN KEY (policy_id) REFERENCES business_policies(source_penalty_policies_policy_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.penalty_ledger ADD CONSTRAINT og70_fk_penalty_ledger_penalty_ledger_report_id_fkey FOREIGN KEY (report_id) REFERENCES cases(source_reports_report_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.penalty_ledger ADD CONSTRAINT og70_fk_penalty_ledger_penalty_ledger_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.product_categories ADD CONSTRAINT og70_fk_product_categories_fk_product_categories_category FOREIGN KEY (category_id) REFERENCES categories(category_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.product_categories ADD CONSTRAINT og70_fk_product_categories_fk_product_categories_product FOREIGN KEY (product_id) REFERENCES products(product_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.product_media ADD CONSTRAINT og70_fk_product_media_fk_product_media_product FOREIGN KEY (product_id) REFERENCES products(product_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.product_media ADD CONSTRAINT og70_fk_revision_media_revision_media_revision_id_fkey FOREIGN KEY (revision_id) REFERENCES product_revisions(revision_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.product_revisions ADD CONSTRAINT og70_fk_product_revisions_product_revisions_product_id_fkey FOREIGN KEY (product_id) REFERENCES products(product_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.products ADD CONSTRAINT og70_fk_products_fk_product_current_revision FOREIGN KEY (current_revision_id, product_id) REFERENCES product_revisions(revision_id, product_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.products ADD CONSTRAINT og70_fk_products_fk_product_public_revision FOREIGN KEY (public_revision_id, product_id) REFERENCES product_revisions(revision_id, product_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.products ADD CONSTRAINT og70_fk_products_fk_products_category FOREIGN KEY (category_id) REFERENCES categories(category_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.products ADD CONSTRAINT og70_fk_products_fk_products_compatibility_catego_9dadae3742 FOREIGN KEY (product_id, category_id) REFERENCES product_categories(product_id, category_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.products ADD CONSTRAINT og70_fk_products_fk_products_seller FOREIGN KEY (seller_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.refresh_sessions ADD CONSTRAINT og70_fk_refresh_sessions_fk_refresh_sessions_replacement FOREIGN KEY (replaced_by) REFERENCES refresh_sessions(session_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.refresh_sessions ADD CONSTRAINT og70_fk_refresh_sessions_fk_refresh_sessions_user FOREIGN KEY (user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.reviews ADD CONSTRAINT og70_fk_reviews_fk_reviews_order FOREIGN KEY (order_id) REFERENCES orders(order_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.reviews ADD CONSTRAINT og70_fk_reviews_fk_reviews_reviewee FOREIGN KEY (reviewee_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.reviews ADD CONSTRAINT og70_fk_reviews_fk_reviews_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.reward_ledger ADD CONSTRAINT og70_fk_reward_ledger_reward_ledger_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.reward_ledger ADD CONSTRAINT og70_fk_reward_ledger_reward_ledger_group_id_user_id_fkey FOREIGN KEY (group_id, user_id) REFERENCES checkout_groups(group_id, buyer_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.reward_ledger ADD CONSTRAINT og70_fk_reward_ledger_reward_ledger_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(order_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.reward_ledger ADD CONSTRAINT og70_fk_reward_ledger_reward_ledger_policy_id_fkey FOREIGN KEY (policy_id) REFERENCES business_policies(source_reward_policies_policy_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.seller_buyer_blocks ADD CONSTRAINT og70_fk_seller_buyer_blocks_seller_buyer_blocks_b_7e87edc3fd FOREIGN KEY (buyer_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.seller_buyer_blocks ADD CONSTRAINT og70_fk_seller_buyer_blocks_seller_buyer_blocks_s_3d0d4b1b7f FOREIGN KEY (seller_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.seller_profiles ADD CONSTRAINT og70_fk_seller_profiles_seller_profiles_ekyc_prof_d27a5c2c7e FOREIGN KEY (ekyc_profile_id, user_id) REFERENCES ekyc_profiles(profile_id, user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.seller_profiles ADD CONSTRAINT og70_fk_seller_profiles_seller_profiles_pickup_ad_bfe57aa53e FOREIGN KEY (pickup_address_id, user_id) REFERENCES addresses(address_id, user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.seller_profiles ADD CONSTRAINT og70_fk_seller_profiles_seller_profiles_quick_aut_d7103356ff FOREIGN KEY (quick_auth_result_id) REFERENCES auth_challenges(source_quick_auth_results_result_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.seller_profiles ADD CONSTRAINT og70_fk_seller_profiles_seller_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.settlement_operations ADD CONSTRAINT og70_fk_settlement_operations_fk_settlement_case_decision FOREIGN KEY (case_decision_id) REFERENCES case_actions(source_case_decisions_decision_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.settlement_operations ADD CONSTRAINT og70_fk_settlement_operations_settlement_operatio_04f3e6f339 FOREIGN KEY (recipient_user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.settlement_operations ADD CONSTRAINT og70_fk_settlement_operations_settlement_operatio_cfb95afae3 FOREIGN KEY (component_id) REFERENCES order_fund_components(component_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.shipment_events ADD CONSTRAINT og70_fk_handover_confirmations_handover_confirmat_6b7a6c5d52 FOREIGN KEY (shipment_id) REFERENCES shipments(shipment_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.shipment_events ADD CONSTRAINT og70_fk_handover_confirmations_handover_confirmat_8f8b7cbd12 FOREIGN KEY (user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.shipment_events ADD CONSTRAINT og70_fk_pickup_attempts_pickup_attempts_shipment_id_fkey FOREIGN KEY (shipment_id) REFERENCES shipments(shipment_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.shipment_events ADD CONSTRAINT og70_fk_pickup_attempts_pickup_attempts_source_event_id_fkey FOREIGN KEY (pickup_attempts_source_event_id) REFERENCES shipment_events(event_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.shipment_events ADD CONSTRAINT og70_fk_shipment_events_shipment_events_shipment_id_fkey FOREIGN KEY (shipment_id) REFERENCES shipments(shipment_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.shipments ADD CONSTRAINT og70_fk_shipments_fk_shipments_order FOREIGN KEY (order_id) REFERENCES orders(order_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.shipments ADD CONSTRAINT og70_fk_shipments_shipments_return_case_id_fkey FOREIGN KEY (return_case_id) REFERENCES cases(case_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.user_roles ADD CONSTRAINT og70_fk_user_roles_fk_user_roles_granted_by FOREIGN KEY (granted_by) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.user_roles ADD CONSTRAINT og70_fk_user_roles_fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(role_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.user_roles ADD CONSTRAINT og70_fk_user_roles_fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.verification_attempts ADD CONSTRAINT og70_fk_ekyc_verification_attempts_ekyc_verificat_3523601195 FOREIGN KEY (fallback_authorized_by) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.verification_attempts ADD CONSTRAINT og70_fk_ekyc_verification_attempts_ekyc_verificat_975141b2cf FOREIGN KEY (profile_id) REFERENCES ekyc_profiles(profile_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.verification_attempts ADD CONSTRAINT og70_fk_quick_auth_attempts_quick_auth_attempts_s_c73276cafd FOREIGN KEY (session_id) REFERENCES auth_challenges(source_quick_auth_sessions_session_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.verification_attempts ADD CONSTRAINT og70_fk_seller_verification_metrics_fk_seller_bio_8823d0e126 FOREIGN KEY (user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.verification_attempts ADD CONSTRAINT og70_fk_seller_verification_metrics_fk_seller_bio_a812f807d3 FOREIGN KEY (verification_id) REFERENCES ekyc_profiles(source_seller_verifications_verification_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.voucher_grants ADD CONSTRAINT og70_fk_voucher_grants_voucher_grants_issued_by_fkey FOREIGN KEY (issued_by) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.voucher_grants ADD CONSTRAINT og70_fk_voucher_grants_voucher_grants_revision_id_fkey FOREIGN KEY (revision_id) REFERENCES vouchers(source_voucher_revisions_revision_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.voucher_grants ADD CONSTRAINT og70_fk_voucher_grants_voucher_grants_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.voucher_products ADD CONSTRAINT og70_fk_voucher_revision_products_voucher_revisio_35b19cbe37 FOREIGN KEY (product_id) REFERENCES products(product_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.voucher_products ADD CONSTRAINT og70_fk_voucher_revision_products_voucher_revisio_58445402de FOREIGN KEY (revision_id) REFERENCES vouchers(source_voucher_revisions_revision_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.vouchers ADD CONSTRAINT og70_fk_voucher_revisions_voucher_revisions_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.vouchers ADD CONSTRAINT og70_fk_voucher_revisions_voucher_revisions_voucher_id_fkey FOREIGN KEY (parent_voucher_id) REFERENCES vouchers(voucher_id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.vouchers ADD CONSTRAINT og70_fk_vouchers_fk_vouchers_seller FOREIGN KEY (seller_id) REFERENCES users(user_id) DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX og70_cart_user_idx ON public.cart_items USING btree (user_id);
