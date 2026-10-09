"""Build the audited V22 migration from PostgreSQL V21 structural metadata.

No application data, credentials or dump contents are read by this generator.
Compatibility views are projections over the 48 physical aggregates, not copies.
"""
import json
import re
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
META = ROOT / "database/design/v21-consolidation-metadata.json"
OUT = ROOT / "backend/src/main/resources/db/migration/V22__consolidate_database_to_48_tables.sql"
metadata = json.loads(META.read_text(encoding="utf-8-sig"))
tables = metadata["tables"]
plan = {}
sources = {}


def name(text):
    return text if len(text) <= 60 else text[:49] + "_" + hashlib.sha256(text.encode()).hexdigest()[:10]


def quote(text):
    return "'" + text.replace("'", "''") + "'"


def physical(target, members):
    plan[target] = {"mode": "ROWS", "members": members, "folds": []}
    for source in members:
        sources[source] = {"target": target, "mode": "ROWS"}


def fold(source, target, owner, key, column=None, owner_kind=None):
    column = column or name(source + "_history")
    entry = {"target": target, "mode": "FOLD", "owner": owner, "key": key,
             "column": column, "owner_kind": owner_kind}
    sources[source] = entry
    plan[target]["folds"].append(source)


physical("users", ["users"])
for src in ["user_security_settings", "reward_accounts", "external_identities", "account_restrictions", "carts"]:
    fold(src, "users", "user_id", "user_id")
for tab in ["roles", "user_roles", "addresses", "refresh_sessions"]:
    physical(tab, [tab])
physical("auth_challenges", ["auth_challenges", "password_reset_challenges", "quick_auth_sessions", "quick_auth_results"])
physical("ekyc_profiles", ["ekyc_profiles", "seller_verifications"])
fold("ekyc_decisions", "ekyc_profiles", "profile_id", "profile_id", owner_kind="ekyc_profiles")
for tab in ["ekyc_private_assets", "identity_document_registry"]:
    physical(tab, [tab])
physical("verification_attempts", ["ekyc_verification_attempts", "quick_auth_attempts", "seller_verification_metrics"])
physical("seller_profiles", ["seller_profiles"])
fold("seller_profile_decisions", "seller_profiles", "seller_profile_id", "seller_profile_id")
for tab in ["categories", "products", "product_categories", "product_revisions"]:
    physical(tab, [tab])
fold("media_analysis_runs", "product_revisions", "revision_id", "revision_id")
fold("product_moderation_decisions", "product_revisions", "revision_id", "revision_id", "moderation_history")
fold("product_moderation_legacy_history", "product_revisions", "revision_id", "decision_id", "legacy_moderation_history")
physical("product_media", ["product_media", "revision_media"])
physical("listing_fee_charges", ["listing_fee_charges", "listing_fee_assessments"])
physical("business_policies", ["system_fee_policies", "listing_fee_policies", "checklist_policies", "reward_policies", "penalty_policies"])
physical("conversations", ["conversations"])
fold("conversation_user_state", "conversations", "conversation_id", "conversation_id", "participant_state")
fold("chat_media_daily_quotas", "conversations", "conversation_id", "conversation_id", "media_quota_usage")
for tab in ["messages", "offers", "cart_items", "checkout_groups", "orders", "order_items"]:
    physical(tab, [tab])
fold("checkout_redemptions", "checkout_groups", "group_id", "group_id", "voucher_redemption")
fold("zero_checkout_confirmations", "checkout_groups", "group_id", "group_id", "zero_confirmation")
fold("order_vouchers", "orders", "order_id", "order_id", "legacy_voucher_history")
fold("conversation_order_links", "orders", "order_id", "order_id", "conversation_links")
fold("discount_allocations", "order_items", "order_item_id", "order_item_id", "discount_history")
physical("inventory_reservations", ["inventory_reservations"])
physical("vouchers", ["vouchers", "voucher_revisions"])
fold("voucher_revision_revocations", "vouchers", "source_voucher_revisions_revision_id", "revision_id", "revocation_history", owner_kind="voucher_revisions")
physical("voucher_products", ["voucher_revision_products"])
physical("voucher_grants", ["voucher_grants"])
fold("voucher_grant_events", "voucher_grants", "grant_id", "grant_id", "grant_history")
physical("reward_ledger", ["reward_ledger"])
physical("payments", ["payments", "payment_intents"])
physical("payment_attempts", ["payment_attempts", "vnpay_payment_attempts"])
physical("payment_events", ["payment_ipn_raw_receipts", "payment_ipn_events"])
physical("payment_confirmations", ["payment_confirmations", "listing_fee_receipts"])
physical("order_fund_components", ["order_fund_components", "payment_allocations"])
physical("settlement_operations", ["settlement_operations"])
physical("shipments", ["shipments"])
physical("shipment_events", ["shipment_events", "pickup_attempts", "handover_confirmations"])
physical("cases", ["cases", "complaints", "reports", "payment_reconciliation_cases"])
physical("case_actions", ["case_rounds", "case_decisions", "case_events", "money_holds", "payment_review_audits"])
physical("case_evidence", ["case_evidence", "order_unboxing_evidences"])
physical("reviews", ["reviews"])
fold("review_revisions", "reviews", "review_id", "review_id", "edit_history")
for tab in ["seller_buyer_blocks", "penalty_ledger"]:
    physical(tab, [tab])
physical("notifications", ["notifications", "notification_dispatches", "notification_reminders", "payment_alert_requests"])
fold("notification_deliveries", "notifications", "notification_id", "notification_id", "delivery_history", owner_kind="notifications")
physical("audit_logs", ["audit_logs", "account_status_events", "order_events", "report_export_runs"])
physical("outbox_events", ["outbox_events"])
fold("outbox_consumer_checkpoints", "outbox_events", "event_id", "event_id", "consumer_progress")
physical("interaction_events", ["interaction_events"])
assert len(plan) == 48
assert set(sources) == set(tables), (set(tables) - set(sources), set(sources) - set(tables))


def pk(src):
    return next(c["columns"] for c in tables[src]["constraints"] if c["kind"] == "p")


def compatible_type(left, right):
    if left == right:
        return left
    a = re.fullmatch(r"character varying\((\d+)\)", left)
    b = re.fullmatch(r"character varying\((\d+)\)", right)
    if a and b:
        return f"character varying({max(int(a[1]), int(b[1]))})"
    return None


sql = ["-- TASK-0070. Approved 48 physical aggregates; immutable V1â€“V21 retained.",
       "-- An atomic conversion with exact source-row fingerprint verification.",
       "-- og_compat contains writable projections for existing services/guards.",
       "SET LOCAL lock_timeout='10s';", "SET LOCAL statement_timeout='120s';",
       "SELECT pg_advisory_xact_lock(700048);",
       "CREATE SCHEMA _og70_v21;",
       "CREATE SCHEMA og_compat;",
       "CREATE TEMP TABLE _og70_proof(source text PRIMARY KEY, rows bigint, fingerprint text) ON COMMIT DROP;"]

# Lock legacy tables before fingerprinting so an existing V21 client cannot
# commit a write between the snapshot and the conversion. Lock timeout aborts
# the migration transaction without removing the original schema.
sql.append("LOCK TABLE " + ",".join("public." + s for s in sorted(tables)) + " IN ACCESS EXCLUSIVE MODE;")

for src in tables:
    sql.append(f"INSERT INTO _og70_proof SELECT {quote(src)},count(*),md5(coalesce(string_agg(md5(to_jsonb(t)::text),'' ORDER BY md5(to_jsonb(t)::text)),'')) FROM public.{src} t;")
for view in metadata["views"]:
    sql.append(f"DROP VIEW IF EXISTS public.{view} CASCADE;")
for definition in metadata["functions"].values():
    signature = re.search(r"FUNCTION (public\.\w+\([^)]*\))", definition).group(1)
    sql.append(f"DROP FUNCTION IF EXISTS {signature} CASCADE;")
for src in tables:
    sql.append(f"ALTER TABLE public.{src} SET SCHEMA _og70_v21;")

sequences = {}
for src, definition in tables.items():
    for col in definition["columns"]:
        if col.get("identity") or (col.get("default") and "nextval(" in col["default"]):
            seq = name(f"og70_{src}_{col['name']}_seq")
            sequences[(src, col["name"])] = seq
            sql.append(f"CREATE SEQUENCE public.{seq};")

maps = {}
target_columns = {}
native_pk = {}
for target, aggregate in plan.items():
    members = aggregate["members"]
    base = target if target in members else None
    columns = {}
    if base and len(pk(base)) == 1:
        primary = pk(base)[0]
    elif base:
        primary = None
    else:
        primary = {"verification_attempts": "attempt_id", "business_policies": "policy_id",
                   "payment_events": "event_id", "case_actions": "action_id"}.get(target)
        if primary is None:
            primary = pk(members[0])[0] if len(pk(members[0])) == 1 else None
        if primary:
            columns[primary] = {"type": "uuid" if target == "verification_attempts" else "bigint",
                                "default": "gen_random_uuid()" if target == "verification_attempts" else None,
                                "generated": ""}
            if target != "verification_attempts":
                seq = name(f"og70_{target}_{primary}_seq")
                sql.append(f"CREATE SEQUENCE public.{seq};")
                columns[primary]["default"] = f"nextval('public.{seq}'::regclass)"
    native_pk[target] = [primary] if primary else pk(base or members[0])
    for src in members:
        mapping = {}
        for col in tables[src]["columns"]:
            key = col["name"]
            dest = key
            if src != base and key in pk(src) and len(members)>1:
                dest = name(f"source_{src}_{key}")
            elif src != base and key == primary:
                dest = "parent_voucher_id" if target == "vouchers" else name(f"{src}_{key}")
            if dest in columns and not compatible_type(columns[dest]["type"], col["type"]):
                dest = name(f"{src}_{key}")
            if dest in columns:
                columns[dest]["type"] = compatible_type(columns[dest]["type"], col["type"])
            else:
                default = col.get("default") if src == base or col.get("generated") else None
                if (src, key) in sequences and src == base:
                    default = f"nextval('public.{sequences[(src,key)]}'::regclass)"
                if src == base and key in pk(src) and col["type"] == "uuid" and not default:
                    default = "gen_random_uuid()"
                columns[dest] = {"type": col["type"], "default": default, "generated": col.get("generated", "")}
            mapping[key] = dest
        maps[src] = mapping
    if primary is None and base is None:
        native_pk[target] = [maps[members[0]][c] for c in pk(members[0])]
    columns["record_type"] = {"type": "text", "default": quote(base or members[0]), "generated": ""}
    for src in aggregate["folds"]:
        columns[sources[src]["column"]] = {"type": "jsonb", "default": "'[]'::jsonb", "generated": ""}
    if target == "cart_items":
        columns["user_id"] = {"type": "bigint", "default": None, "generated": ""}
    if target == "users":
        columns["reward_balance"] = {"type": "bigint", "default": "coalesce((reward_accounts_history->0->>'balance')::bigint,0)", "generated": "s"}
        columns["email_2fa_enabled"] = {"type": "boolean", "default": "coalesce((user_security_settings_history->0->>'email_2fa_enabled')::boolean,false)", "generated": "s"}
    if target == "order_items":
        columns["conversation_id"] = {"type": "bigint", "default": None, "generated": ""}
    target_columns[target] = columns
    definitions = []
    for col, spec in columns.items():
        decl = f"{col} {spec['type']}"
        if spec["generated"]:
            decl += f" GENERATED ALWAYS AS ({spec['default']}) STORED"
        elif spec["default"]:
            decl += f" DEFAULT {spec['default']}"
        if col in native_pk[target] or col == "record_type":
            decl += " NOT NULL"
        definitions.append(decl)
    definitions.append("PRIMARY KEY (" + ",".join(native_pk[target]) + ")")
    definitions.append("CHECK (record_type IN (" + ",".join(quote(s) for s in members) + "))")
    for src in aggregate["folds"]:
        col = sources[src]["column"]
        definitions.append(f"CHECK(jsonb_typeof({col})='array')")
    sql.append(f"CREATE TABLE public.{target} (\n  " + ",\n  ".join(definitions) + "\n);")

# Load physical rows before installing hooks: migration must not invent decisions,
# money confirmations, penalties or send notifications.
for target, aggregate in plan.items():
    for src in aggregate["members"]:
        mapping = maps[src]
        cols = [c["name"] for c in tables[src]["columns"] if not c.get("generated")]
        sql.append(f"INSERT INTO public.{target} ({','.join(mapping[c] for c in cols)},record_type) SELECT {','.join(cols)},{quote(src)} FROM _og70_v21.{src};")
        if target == "ekyc_profiles" and src == "seller_verifications":
            sql.append("UPDATE public.ekyc_profiles SET state='LEGACY_METADATA' WHERE record_type='seller_verifications';")
        for col in native_pk[target]:
            spec = target_columns[target][col]
            match = re.search(r"nextval\('public\.([^']+)'", spec.get("default") or "")
            if match:
                sql.append(f"SELECT setval('public.{match.group(1)}',coalesce((SELECT max({col}) FROM public.{target}),1),EXISTS(SELECT 1 FROM public.{target}));")
    for src in aggregate["folds"]:
        fold_spec = sources[src]
        owner, key, store = fold_spec["owner"], fold_spec["key"], fold_spec["column"]
        condition = f"s.{key}=p.{owner}"
        if src == "product_moderation_decisions":
            condition = "p.revision_id=coalesce(s.revision_id,(SELECT revision_id FROM public.product_revisions r WHERE r.product_id=s.product_id ORDER BY revision_no DESC LIMIT 1))"
        if src == "product_moderation_legacy_history":
            condition = "EXISTS(SELECT 1 FROM jsonb_array_elements(p.moderation_history) d WHERE (d->>'decision_id')::bigint=s.decision_id)"
        where = f" WHERE p.record_type={quote(fold_spec['owner_kind'])}" if fold_spec["owner_kind"] else ""
        sql.append(f"UPDATE public.{target} p SET {store}=coalesce((SELECT jsonb_agg(to_jsonb(s) ORDER BY to_jsonb(s)::text) FROM _og70_v21.{src} s WHERE {condition}),'[]'::jsonb){where};")

# Views retain the exact V21 column types/order. This makes row fingerprint
# verification meaningful and prevents accidental credential/media truncation.
sql += [
    "UPDATE public.cart_items i SET user_id=(SELECT c.user_id FROM _og70_v21.carts c WHERE c.cart_id=i.cart_id);",
    "UPDATE public.product_media m SET product_id=(SELECT r.product_id FROM public.product_revisions r WHERE r.revision_id=m.revision_id) WHERE m.record_type='revision_media';",
    "UPDATE public.case_actions a SET case_id=(SELECT r.case_id FROM public.case_actions r WHERE r.record_type='case_rounds' AND r.source_case_rounds_round_id=a.round_id) WHERE a.record_type='case_decisions';",
    "UPDATE public.order_items i SET conversation_id=(SELECT l.conversation_id FROM _og70_v21.conversation_order_links l JOIN public.conversations c ON c.conversation_id=l.conversation_id WHERE l.order_id=i.order_id AND c.product_id=i.product_id LIMIT 1);"
]
for src, entry in sources.items():
    target = entry["target"]
    if entry["mode"] == "ROWS":
        mapping = maps[src]
        projection = ",".join(f"p.{mapping[c['name']]}::{c['type']} AS {c['name']}" for c in tables[src]["columns"])
        query = f"SELECT {projection} FROM public.{target} p WHERE p.record_type={quote(src)}"
    else:
        typed = ",".join(f"{c['name']} {c['type']}" for c in tables[src]["columns"])
        query = f"SELECT r.* FROM public.{target} p CROSS JOIN LATERAL jsonb_to_recordset(p.{entry['column']}) AS r({typed})"
    sql.append(f"CREATE VIEW og_compat.{src} AS {query};")
    for col in tables[src]["columns"]:
        default = col.get("default")
        if (src, col["name"]) in sequences:
            default = f"nextval('public.{sequences[(src,col['name'])]}'::regclass)"
        if default and not col.get("generated"):
            sql.append(f"ALTER VIEW og_compat.{src} ALTER COLUMN {col['name']} SET DEFAULT {default};")
    for col in tables[src]["columns"]:
        if (src, col["name"]) in sequences:
            sql.append(f"SELECT setval('public.{sequences[(src,col['name'])]}',coalesce((SELECT max({col['name']}) FROM og_compat.{src}),1),EXISTS(SELECT 1 FROM og_compat.{src}));")

# Exact source preservation is checked while the old schema still exists.
sql.append("""DO $proof$
DECLARE r record; n bigint; h text;
BEGIN
 FOR r IN SELECT * FROM _og70_proof LOOP
  EXECUTE format('SELECT count(*),md5(coalesce(string_agg(md5(to_jsonb(t)::text),%L ORDER BY md5(to_jsonb(t)::text)),%L)) FROM og_compat.%I t','','',r.source) INTO n,h;
  IF n<>r.rows OR h<>r.fingerprint THEN
   RAISE EXCEPTION 'V22 source preservation failed: %, expected % rows, observed %',r.source,r.rows,n;
  END IF;
 END LOOP;
END $proof$;""")

# Remaining generated source hooks/compatibility writers live in a separate
# builder so the layout/data conversion can be reviewed independently.
context = {"metadata": metadata, "plan": plan, "sources": sources, "maps": maps,
           "columns": target_columns, "pk": native_pk, "sequences": {f"{s}.{c}": v for (s,c),v in sequences.items()}}
context_file = ROOT / "output/database-consolidation-2026-10-09/consolidation-layout.json"
context_file.parent.mkdir(parents=True, exist_ok=True)
context_file.write_text(json.dumps(context, ensure_ascii=False, indent=2), encoding="utf-8")
OUT.write_text("\n\n".join(sql) + "\n", encoding="utf-8")
print(f"Generated layout/data conversion: {len(tables)} source projections -> {len(plan)} physical tables.")
