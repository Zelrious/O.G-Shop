"""Append compatibility writers, original workflow guards and relational proofs."""
import json
import re
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CTX = ROOT / "output/database-consolidation-2026-10-09/consolidation-layout.json"
OUT = ROOT / "backend/src/main/resources/db/migration/V22__consolidate_database_to_48_tables.sql"
ctx = json.loads(CTX.read_text(encoding="utf-8"))
meta, plan, sources, maps = (ctx[x] for x in ["metadata", "plan", "sources", "maps"])
tables = meta["tables"]
sql = []


def q(value):
    return "'" + str(value).replace("'", "''") + "'"


def short(value):
    return value if len(value) <= 60 else value[:49] + "_" + hashlib.sha256(value.encode()).hexdigest()[:10]


def pk(source):
    return next(c["columns"] for c in tables[source]["constraints"] if c["kind"] == "p")


def original_columns(source):
    return [c["name"] for c in tables[source]["columns"]]


def project(source, record):
    return "jsonb_build_object(" + ",".join(q(k) + "," + record + "->" + q(v) for k, v in maps[source].items()) + ")"


def qualify(text):
    # SQL string literals (reasons, event types and JSON keys) are preserved.
    parts = re.split(r"('(?:''|[^'])*')", text)
    pattern = re.compile(r"(?<![\w.])(?:public\.)?(" + "|".join(sorted(tables, key=len, reverse=True)) + r")\b")
    return "".join(part if i % 2 else pattern.sub(lambda m: "og_compat." + m[1], part)
                   for i, part in enumerate(parts))


def adjust_upserts(definition):
    # Exactly two existing INSERT SELECT upserts need view-compatible forms.
    # All writes hold the same transaction advisory lock, so these checks race
    # neither another adapter writer nor a direct physical aggregate writer.
    definition = re.sub(
        r"SELECT p\.user_id,role_id,NEW\.reviewed_by FROM roles WHERE role_name='SELLER'\s+ON CONFLICT\(user_id,role_id\) DO NOTHING",
        "SELECT p.user_id,r.role_id,NEW.reviewed_by FROM roles r WHERE r.role_name='SELLER' "
        "AND NOT EXISTS(SELECT 1 FROM user_roles ur WHERE ur.user_id=p.user_id AND ur.role_id=r.role_id)",
        definition)
    definition = re.sub(
        r"SELECT gen_random_uuid\(\),component_id,'RETURN_CASE:'\|\|NEW\.case_id,'Return case pending',NEW\.case_id\s+FROM order_fund_components WHERE order_id=NEW\.order_id\s+AND component_type IN \('GOODS_CASH','PLATFORM_GOODS_SUBSIDY'\)\s+ON CONFLICT\(component_id,source_key\) DO NOTHING",
        "SELECT gen_random_uuid(),f.component_id,'RETURN_CASE:'||NEW.case_id,'Return case pending',NEW.case_id "
        "FROM order_fund_components f WHERE f.order_id=NEW.order_id "
        "AND f.component_type IN ('GOODS_CASH','PLATFORM_GOODS_SUBSIDY') "
        "AND NOT EXISTS(SELECT 1 FROM money_holds h WHERE h.component_id=f.component_id "
        "AND h.source_key='RETURN_CASE:'||NEW.case_id)",
        definition)
    assert "ON CONFLICT" not in definition, definition[:100]
    return definition


# Existing public facade functions continue to address logical contracts, while
# their row types now belong to views. Nothing is silently marked paid/approved.
for function, definition in meta["functions"].items():
    sql.append(qualify(adjust_upserts(definition)).rstrip(";") + ";")

row_functions = {}
for source, definition in tables.items():
    for trigger in definition["triggers"]:
        function = trigger["function"]
        key = (source, function)
        if key in row_functions:
            continue
        fn = short("og70_row_" + source + "_" + function)
        row_functions[key] = fn
        text = meta["functions"][function]
        match = re.search(r"AS \$(\w*)\$([\s\S]*)\$\1\$", text)
        body = match[2].strip()
        declare = ""
        if body.upper().startswith("DECLARE"):
            m = re.match(r"DECLARE([\s\S]*?)\bBEGIN\b([\s\S]*)", body, re.I)
            declare, body = m[1], "BEGIN" + m[2]
        body = re.sub(r"\bRETURN\s+(NEW|OLD)\s*;", r"RETURN to_jsonb(\1);", body, flags=re.I)
        body = body.replace("pg_trigger_depth()", "p_depth")
        body = qualify(adjust_upserts(body))
        body = re.sub(r"^\s*BEGIN\b", "BEGIN\n NEW:=jsonb_populate_record(NULL::og_compat." + source +
                      ",coalesce(p_new,'{}'::jsonb));\n OLD:=jsonb_populate_record(NULL::og_compat." + source +
                      ",coalesce(p_old,'{}'::jsonb));", body, count=1, flags=re.I)
        sql.append(f"""CREATE FUNCTION public.{fn}(p_new jsonb,p_old jsonb,p_op text,p_phase text,p_depth integer,p_args text[])
RETURNS jsonb LANGUAGE plpgsql SET search_path=public,og_compat AS $row$
DECLARE
 NEW og_compat.{source}; OLD og_compat.{source};
 TG_OP text:=p_op; TG_WHEN text:=p_phase; TG_TABLE_NAME text:={q(source)};
 TG_TABLE_SCHEMA text:='og_compat'; TG_ARGV text[]:=p_args; TG_NARGS integer:=cardinality(p_args);
 {qualify(declare)}
{body}
$row$;""")

# Stable source primary keys, including composite membership/quota keys.
key_cases = []
for source in tables:
    key_cases.append(f"WHEN {q(source)} THEN RETURN jsonb_build_array(" +
                     ",".join("j->" + q(k) for k in pk(source)) + ");")
sql.append("CREATE FUNCTION public.og70_key(s text,j jsonb) RETURNS jsonb LANGUAGE plpgsql IMMUTABLE AS $key$ BEGIN CASE s " +
           "\n".join(key_cases) + " ELSE RAISE EXCEPTION 'Unknown logical source'; END CASE; END $key$;")

hook_cases = []
for source, definition in tables.items():
    lines = [f"WHEN {q(source)} THEN"]
    for trigger in definition["triggers"]:
        bits = trigger["type"]
        phase = "DEFERRED" if trigger["constraint"] and trigger["deferred"] else ("BEFORE" if bits & 2 else "AFTER")
        operations = [op for flag, op in [(4, "INSERT"), (16, "UPDATE"), (8, "DELETE")] if bits & flag]
        condition = f"p_phase={q(phase)} AND p_op IN (" + ",".join(q(op) for op in operations) + ")"
        # UPDATE OF must use the logical source columns, not aggregate metadata.
        of = re.search(r"UPDATE OF ([\w, ]+) ON", trigger["definition"])
        if of:
            cols = [c.strip() for c in of[1].split(",")]
            changed = " OR ".join(f"p_new->{q(c)} IS DISTINCT FROM p_old->{q(c)}" for c in cols)
            condition += f" AND (p_op<>'UPDATE' OR ({changed}))"
        when = re.search(r" WHEN \(([\s\S]*)\) EXECUTE FUNCTION", trigger["definition"])
        if when:
            expr = re.sub(r"\bnew\.", "n.", when[1], flags=re.I)
            expr = re.sub(r"\bold\.", "o.", expr, flags=re.I)
            condition += " AND (SELECT coalesce((" + expr + "),false) FROM jsonb_populate_record(NULL::og_compat." + source + ",coalesce(p_new,'{}')) n CROSS JOIN jsonb_populate_record(NULL::og_compat." + source + ",coalesce(p_old,'{}')) o)"
        args = [x for x in trigger["arguments"].split("\\000") if x]
        call = "public." + row_functions[(source, trigger["function"])] + "(p_new,p_old,p_op,p_phase,depth,ARRAY[" + ",".join(q(a) for a in args) + "]::text[])"
        lines += [f"IF {condition} THEN", f" result:={call};"]
        if phase == "BEFORE":
            lines += [" IF result IS NULL THEN PERFORM set_config('og70.depth',previous,true); RETURN NULL; END IF;",
                      " IF p_op<>'DELETE' THEN p_new:=result; END IF;"]
        lines.append("END IF;")
    lines.append("NULL;")
    hook_cases.extend(lines)
sql.append("""CREATE FUNCTION public.og70_hooks(s text,p_op text,p_phase text,p_new jsonb,p_old jsonb)
RETURNS jsonb LANGUAGE plpgsql SET search_path=public,og_compat AS $hooks$
DECLARE previous text:=coalesce(nullif(current_setting('og70.depth',true),''),'0');
 depth integer:=previous::integer+1; result jsonb;
BEGIN
 PERFORM set_config('og70.depth',depth::text,true);
 CASE s
""" + "\n".join(hook_cases) + """
 ELSE RAISE EXCEPTION 'Unknown logical source';
 END CASE;
 PERFORM set_config('og70.depth',previous,true);
 RETURN CASE WHEN p_op='DELETE' THEN p_old ELSE p_new END;
END $hooks$;""")

validation_cases = []
for source, definition in tables.items():
    lines = [f"WHEN {q(source)} THEN"]
    nonnull = [c["name"] for c in definition["columns"] if c["notnull"] and not c.get("generated")]
    if nonnull:
        test = " OR ".join(f"j->{q(c)} IS NULL OR j->{q(c)}='null'::jsonb" for c in nonnull)
        lines.append(f"IF {test} THEN RAISE EXCEPTION 'Required field in {source}' USING ERRCODE='23502'; END IF;")
    for c in definition["constraints"]:
        if c["kind"] == "c":
            match = re.match(r"CHECK \(([\s\S]*)\)(?: NOT VALID)?$", c["definition"])
            assert match, c
            query = "SELECT (" + match[1] + ") IS FALSE FROM jsonb_populate_record(NULL::og_compat." + source + ",$1) r"
            if "NOT VALID" in c["definition"]:
                lines.append("IF NOT skip_not_valid THEN")
            lines += [f"EXECUTE {q(query)} INTO bad USING j;",
                      f"IF bad THEN RAISE EXCEPTION 'Constraint {c['name']}' USING ERRCODE='23514'; END IF;"]
            if "NOT VALID" in c["definition"]:
                lines.append("END IF;")
    lines.append("NULL;")
    validation_cases.extend(lines)
sql.append("""CREATE FUNCTION public.og70_validate_row(s text,j jsonb,skip_not_valid boolean DEFAULT false) RETURNS void LANGUAGE plpgsql AS $valid$
DECLARE bad boolean;
BEGIN
 IF j IS NULL THEN RETURN; END IF;
 CASE s
""" + "\n".join(validation_cases) + """
 ELSE RAISE EXCEPTION 'Unknown logical source';
 END CASE;
END $valid$;""")

# Relational checks for folded metadata use the exact original source contracts.
# Typed financial/order references additionally have real FK constraints below.
graph_blocks = []
incoming = {s: set() for s in tables}
for source, definition in tables.items():
    checks = []
    for c in definition["constraints"]:
        if c["kind"] == "f":
            incoming[c["ref_table"]].add(source)
            if "NOT VALID" in c["definition"]:
                continue
            keys = c["columns"]
            parent = c["ref_table"]
            nonnull = " AND ".join(f"r.{k} IS NOT NULL" for k in keys)
            same = " AND ".join(f"p.{b}=r.{a}" for a, b in zip(keys, c["ref_columns"]))
            query = f"SELECT 1 FROM og_compat.{source} r WHERE {nonnull} AND NOT EXISTS(SELECT 1 FROM og_compat.{parent} p WHERE {same}) LIMIT 1"
            checks.append((c["name"], query, "23503"))
    for idx in definition["indexes"]:
        if not idx["unique"]:
            continue
        expressions = idx["expressions"]
        where = [f"({x}) IS NOT NULL" for x in expressions]
        if idx["predicate"]:
            where.append("(" + idx["predicate"] + ")")
        query = f"SELECT 1 FROM og_compat.{source} WHERE " + " AND ".join(where) + " GROUP BY " + ",".join(expressions) + " HAVING count(*)>1 LIMIT 1"
        checks.append((idx["name"], query, "23505"))
    # Every committed logical row gets the original NOT NULL/CHECK validation.
    graph_blocks.append(f"IF dirty ? {q(source)} THEN\n FOR row IN SELECT to_jsonb(r) AS j FROM og_compat.{source} r LOOP PERFORM og70_validate_row({q(source)},row.j,true); END LOOP;")
    for label, query, code in checks:
        graph_blocks.append(f"IF EXISTS({query}) THEN RAISE EXCEPTION 'Relational constraint {label}' USING ERRCODE={q(code)}; END IF;")
    graph_blocks.append("END IF;")
sql.append("""CREATE FUNCTION public.og70_validate_graph(force_all boolean DEFAULT false) RETURNS void
LANGUAGE plpgsql SET search_path=public,og_compat AS $graph$
DECLARE dirty jsonb:=coalesce(nullif(current_setting('og70.dirty',true),''),'{}')::jsonb; row record;
BEGIN
 IF force_all THEN dirty:='""" + json.dumps({s: True for s in tables}, separators=(",", ":")) + """'::jsonb; END IF;
""" + "\n".join(graph_blocks) + """
 PERFORM set_config('og70.dirty','{}',true);
END $graph$;""")
mark_cases = []
for source in tables:
    affected = sorted({source} | incoming[source])
    mark_cases.append(f"WHEN {q(source)} THEN changes:=" + q(json.dumps({s: True for s in affected}, separators=(",", ":"))) + "::jsonb;")
sql.append("""CREATE FUNCTION public.og70_mark(s text) RETURNS void LANGUAGE plpgsql AS $mark$
DECLARE changes jsonb; previous jsonb:=coalesce(nullif(current_setting('og70.dirty',true),''),'{}')::jsonb;
BEGIN CASE s
""" + "\n".join(mark_cases) + """
 ELSE RAISE EXCEPTION 'Unknown logical source'; END CASE;
 PERFORM set_config('og70.dirty',(previous||changes)::text,true);
END $mark$;""")

dispatch_cases = []
for target, aggregate in plan.items():
    lines = [f"WHEN {q(target)} THEN", "CASE coalesce(n->>'record_type',o->>'record_type')"]
    for source in aggregate["members"]:
        original_new, original_old = project(source, "n"), project(source, "o")
        lines += [f"WHEN {q(source)} THEN",
                  f" nj:=CASE WHEN TG_OP='DELETE' THEN NULL ELSE {original_new} END;",
                  f" oj:=CASE WHEN TG_OP='INSERT' THEN NULL ELSE {original_old} END;",
                  " IF TG_OP<>'UPDATE' OR nj IS DISTINCT FROM oj THEN",
                  f"  result:=og70_hooks({q(source)},TG_OP,phase,nj,oj);"]
        if True:
            lines += ["  IF phase='BEFORE' THEN",
                      "   IF result IS NULL THEN RETURN NULL; END IF;"]
            for col in tables[source]["columns"]:
                if not col.get("generated"):
                    dest = maps[source][col["name"]]
                    lines.append(f"   IF TG_OP<>'DELETE' THEN n:=jsonb_set(n,ARRAY[{q(dest)}],coalesce(result->{q(col['name'])},'null'::jsonb),true); END IF;")
            lines += [f"   IF TG_OP<>'DELETE' THEN PERFORM og70_validate_row({q(source)},result); END IF;",
                      f"   PERFORM og70_mark({q(source)});",
                      "  END IF;", " END IF;"]
    lines += ["ELSE RAISE EXCEPTION 'Unknown record type in aggregate'; END CASE;"]
    for source in aggregate["folds"]:
        field = sources[source]["column"]
        lines += [
            f"FOR change IN SELECT a.j AS old_j,b.j AS new_j FROM jsonb_array_elements(coalesce(o->{q(field)},'[]')) a(j) FULL JOIN jsonb_array_elements(coalesce(n->{q(field)},'[]')) b(j) ON og70_key({q(source)},a.j)=og70_key({q(source)},b.j) WHERE a.j IS DISTINCT FROM b.j LOOP",
            " operation:=CASE WHEN change.new_j IS NULL THEN 'DELETE' WHEN change.old_j IS NULL THEN 'INSERT' ELSE 'UPDATE' END;",
            " IF phase='BEFORE' THEN",
            f"  IF coalesce(current_setting('og70.prepared',true),'')<>{q(source)} THEN RAISE EXCEPTION 'Change aggregate history through its workflow operation' USING ERRCODE='23514'; END IF;",
            f"  PERFORM og70_mark({q(source)});",
            f" ELSIF phase='DEFERRED' OR coalesce(current_setting('og70.prepared',true),'')<>{q(source)} THEN result:=og70_hooks({q(source)},operation,phase,change.new_j,change.old_j);",
            " END IF;", "END LOOP;"
        ]
    if target == "cart_items":
        lines += ["IF phase='BEFORE' AND TG_OP<>'DELETE' THEN",
                  " n:=jsonb_set(n,'{user_id}',coalesce((SELECT to_jsonb(user_id) FROM og_compat.carts WHERE cart_id=(n->>'cart_id')::bigint),'null'::jsonb),true); END IF;"]
    if target == "product_media":
        lines += ["IF phase='BEFORE' AND TG_OP<>'DELETE' AND n->>'record_type'='revision_media' THEN",
                  " n:=jsonb_set(n,'{product_id}',coalesce((SELECT to_jsonb(product_id) FROM og_compat.product_revisions WHERE revision_id=(n->>'revision_id')::bigint),'null'::jsonb),true); END IF;"]
    if target == "case_actions":
        lines += ["IF phase='BEFORE' AND TG_OP<>'DELETE' AND n->>'record_type'='case_decisions' THEN",
                  " n:=jsonb_set(n,'{case_id}',coalesce((SELECT to_jsonb(case_id) FROM og_compat.case_rounds WHERE round_id=(n->>'round_id')::bigint),'null'::jsonb),true); END IF;"]
    if target == "order_items":
        lines += ["IF phase='BEFORE' AND TG_OP<>'DELETE' THEN",
                  " n:=jsonb_set(n,'{conversation_id}',coalesce((SELECT to_jsonb(l.conversation_id) FROM og_compat.conversation_order_links l JOIN og_compat.conversations c ON c.conversation_id=l.conversation_id WHERE l.order_id=(n->>'order_id')::bigint AND c.product_id=(n->>'product_id')::bigint LIMIT 1),'null'::jsonb),true); END IF;"]
    dispatch_cases.extend(lines)
sql.append("""CREATE FUNCTION public.og70_dispatch() RETURNS trigger LANGUAGE plpgsql
SET search_path=public,og_compat AS $dispatch$
DECLARE n jsonb:=CASE WHEN TG_OP='DELETE' THEN NULL ELSE to_jsonb(NEW) END;
 o jsonb:=CASE WHEN TG_OP='INSERT' THEN NULL ELSE to_jsonb(OLD) END;
 phase text:=TG_ARGV[0]; nj jsonb; oj jsonb; result jsonb; change record; operation text; items jsonb;
BEGIN
 IF phase='BEFORE' THEN
  PERFORM pg_advisory_xact_lock(700048);
  IF TG_OP='UPDATE' AND n->>'record_type' IS DISTINCT FROM o->>'record_type' THEN
   RAISE EXCEPTION 'Aggregate record type is immutable' USING ERRCODE='23514'; END IF;
 END IF;
 CASE TG_TABLE_NAME
""" + "\n".join(dispatch_cases) + """
 ELSE RAISE EXCEPTION 'Unknown physical aggregate'; END CASE;
 IF phase='DEFERRED' THEN PERFORM og70_validate_graph(); END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN jsonb_populate_record(NEW,n);
END $dispatch$;""")

adapter_cases = []
sql.append("""CREATE FUNCTION public.og70_legacy_revision(p_product bigint) RETURNS void
LANGUAGE plpgsql SET search_path=public,og_compat AS $legacy$
DECLARE p og_compat.products; n integer;
BEGIN
 SELECT * INTO p FROM og_compat.products WHERE product_id=p_product FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Unknown product' USING ERRCODE='23503'; END IF;
 IF p.workflow_model<>'LEGACY_V14' THEN RETURN; END IF;
 SELECT coalesce(max(revision_no),0)+1 INTO n FROM og_compat.product_revisions WHERE product_id=p_product;
 INSERT INTO og_compat.product_revisions(product_id,revision_no,provenance,listed_unit_price,quantity,return_allowed,delivery_options,content_snapshot)
 VALUES(p_product,n,'LEGACY_MIGRATION',p.listed_price,p.quantity_total,p.return_allowed,p.delivery_options,to_jsonb(p));
END $legacy$;""")
for source, entry in sources.items():
    target = entry["target"]
    lines = [f"WHEN {q(source)} THEN"]
    if entry["mode"] == "ROWS":
        mapping = maps[source]
        writable = [c["name"] for c in tables[source]["columns"] if not c.get("generated")]
        # Directly inserted union child rows get the physical aggregate PK by
        # its own default; logical source identities retain separate keys.
        insert_cols = [mapping[c] for c in writable] + ["record_type"]
        values = ["r." + c for c in writable] + [q(source)]
        query = f"INSERT INTO public.{target} ({','.join(insert_cols)}) SELECT {','.join(values)} FROM jsonb_populate_record(NULL::og_compat.{source},$1) r RETURNING to_jsonb({target})"
        where = " AND ".join(f"t.{mapping[k]}=r.{k}" for k in pk(source)) + f" AND t.record_type={q(source)}"
        update = ",".join(mapping[c] + "=r." + c for c in writable)
        # View predicates run before an INSTEAD OF trigger acquires the lock.
        # Recheck the complete logical OLD row after serialization, preserving
        # Hibernate @Version's zero-row optimistic-lock result on stale writes.
        unchanged = project(source, "to_jsonb(t)") + " IS NOT DISTINCT FROM $2"
        update_query = f"UPDATE public.{target} t SET {update} FROM jsonb_populate_record(NULL::og_compat.{source},$1) r,jsonb_populate_record(NULL::og_compat.{source},$2) old_r WHERE " + " AND ".join(f"t.{mapping[k]}=old_r.{k}" for k in pk(source)) + f" AND t.record_type={q(source)} AND {unchanged} RETURNING to_jsonb(t)"
        delete_query = f"DELETE FROM public.{target} t USING jsonb_populate_record(NULL::og_compat.{source},$1) r WHERE {where} AND " + project(source, "to_jsonb(t)") + " IS NOT DISTINCT FROM $1 RETURNING to_jsonb(t)"
        lines += [f"IF TG_OP='INSERT' THEN EXECUTE {q(query)} INTO stored USING j;",
                  f"ELSIF TG_OP='UPDATE' THEN EXECUTE {q(update_query)} INTO stored USING j,old_j;",
                  f"ELSE EXECUTE {q(delete_query)} INTO stored USING old_j; END IF;",
                  "IF stored IS NULL THEN RETURN NULL; END IF;",
                  "IF TG_OP='DELETE' THEN RETURN OLD; END IF;",
                  "result:=" + project(source, "stored") + ";",
                  "RETURN jsonb_populate_record(NEW,result);"]
    else:
        owner, key, field = entry["owner"], entry["key"], entry["column"]
        lookup = f"t.{owner}=r.{key}"
        if source == "product_moderation_decisions":
            lookup = "t.revision_id=coalesce(r.revision_id,(SELECT revision_id FROM og_compat.product_revisions v WHERE v.product_id=r.product_id ORDER BY revision_no DESC LIMIT 1))"
        if source == "product_moderation_legacy_history":
            lookup = "EXISTS(SELECT 1 FROM jsonb_array_elements(t.moderation_history) x WHERE (x->>'decision_id')::bigint=r.decision_id)"
        if entry["owner_kind"]:
            lookup += f" AND t.record_type={q(entry['owner_kind'])}"
        delete_old = f"coalesce((SELECT jsonb_agg(x.j) FROM jsonb_array_elements(t.{field}) x(j) WHERE og70_key({q(source)},x.j)<>og70_key({q(source)},$2)),'[]')"
        new_items = f"CASE WHEN $3='INSERT' THEN t.{field}||jsonb_build_array($1) WHEN $3='UPDATE' THEN {delete_old}||jsonb_build_array($1) ELSE {delete_old} END"
        query = f"UPDATE public.{target} t SET {field}={new_items} FROM jsonb_populate_record(NULL::og_compat.{source},CASE WHEN $3='DELETE' THEN $2 ELSE $1 END) r WHERE {lookup} RETURNING t.{field}"
        lines += [f"result:=og70_hooks({q(source)},TG_OP,'BEFORE',j,old_j);",
                  "IF result IS NULL THEN RETURN NULL; END IF;",
                  "IF TG_OP<>'DELETE' THEN j:=result; END IF;",
                  f"IF TG_OP<>'DELETE' THEN PERFORM og70_validate_row({q(source)},j); END IF;",
                  "previous_prepared:=coalesce(current_setting('og70.prepared',true),'');",
                  f"PERFORM set_config('og70.prepared',{q(source)},true);",
                  f"EXECUTE {q(query)} INTO stored USING j,old_j,TG_OP;",
                  "PERFORM set_config('og70.prepared',previous_prepared,true);",
                  "IF stored IS NULL THEN RAISE EXCEPTION 'Missing aggregate owner for logical record' USING ERRCODE='23503'; END IF;",
                  f"PERFORM og70_hooks({q(source)},TG_OP,'AFTER',j,old_j);",
                  f"PERFORM og70_mark({q(source)});",
                  "IF TG_OP='DELETE' THEN RETURN OLD; END IF;",
                  f"SELECT x.j INTO result FROM jsonb_array_elements(stored) x(j) WHERE og70_key({q(source)},x.j)=og70_key({q(source)},writer.j);",
                  "RETURN jsonb_populate_record(NEW,result);"]
        if source == "conversation_order_links":
            lines.insert(-3, "UPDATE public.order_items SET conversation_id=CASE WHEN TG_OP='DELETE' THEN NULL ELSE (j->>'conversation_id')::bigint END WHERE order_id=(coalesce(j,old_j)->>'order_id')::bigint AND product_id=(SELECT product_id FROM og_compat.conversations WHERE conversation_id=(coalesce(j,old_j)->>'conversation_id')::bigint);")
        if source == "product_moderation_decisions":
            lines.insert(1,"IF TG_OP='INSERT' AND j->>'revision_id' IS NULL THEN PERFORM og70_legacy_revision((j->>'product_id')::bigint); END IF;")
    adapter_cases.extend(lines)
sql.append("""CREATE FUNCTION public.og70_write_view() RETURNS trigger LANGUAGE plpgsql
SET search_path=public,og_compat AS $write$
<<writer>>
DECLARE j jsonb:=CASE WHEN TG_OP='DELETE' THEN NULL ELSE to_jsonb(NEW) END;
 old_j jsonb:=CASE WHEN TG_OP='INSERT' THEN NULL ELSE to_jsonb(OLD) END;
 stored jsonb; result jsonb; previous_prepared text;
BEGIN
 PERFORM pg_advisory_xact_lock(700048);
 CASE TG_TABLE_NAME
""" + "\n".join(adapter_cases) + """
 ELSE RAISE EXCEPTION 'Unknown compatibility view'; END CASE;
END $write$;""")
for source in tables:
    sql.append(f"CREATE TRIGGER og70_write INSTEAD OF INSERT OR UPDATE OR DELETE ON og_compat.{source} FOR EACH ROW EXECUTE FUNCTION public.og70_write_view();")
for target in plan:
    sql.append(f"CREATE TRIGGER og70_before BEFORE INSERT OR UPDATE OR DELETE ON public.{target} FOR EACH ROW EXECUTE FUNCTION public.og70_dispatch('BEFORE');")
    sql.append(f"CREATE TRIGGER og70_after AFTER INSERT OR UPDATE OR DELETE ON public.{target} FOR EACH ROW EXECUTE FUNCTION public.og70_dispatch('AFTER');")
    sql.append(f"CREATE CONSTRAINT TRIGGER og70_deferred AFTER INSERT OR UPDATE OR DELETE ON public.{target} DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.og70_dispatch('DEFERRED');")

# Index source identities separately inside shared ledgers. Reference keys with
# real storage retain actual PostgreSQL FK relationships for ER diagrams.
unique_keys = {t: {tuple(ctx["pk"][t])} for t in plan}
for source, entry in sources.items():
    if entry["mode"] != "ROWS":
        continue
    target = entry["target"]
    columns = tuple(maps[source][k] for k in pk(source))
    if columns not in unique_keys[target]:
        label = short("og70_" + source + "_identity")
        sql.append(f"ALTER TABLE public.{target} ADD CONSTRAINT {label} UNIQUE({','.join(columns)});")
        unique_keys[target].add(columns)
for source, definition in tables.items():
    if sources[source]["mode"] != "ROWS":
        continue
    owner = sources[source]["target"]
    for c in definition["constraints"]:
        if c["kind"] != "f" or sources[c["ref_table"]]["mode"] != "ROWS":
            continue
        parent_source = c["ref_table"]
        parent = sources[parent_source]["target"]
        a = tuple(maps[source][x] for x in c["columns"])
        b = tuple(maps[parent_source][x] for x in c["ref_columns"])
        parent_pk = set(maps[parent_source][x] for x in pk(parent_source))
        if b not in unique_keys[parent]:
            if not parent_pk.issubset(b):
                continue
            label = short("og70_ref_" + parent_source + "_" + "_".join(c["ref_columns"]))
            sql.append(f"ALTER TABLE public.{parent} ADD CONSTRAINT {label} UNIQUE({','.join(b)});")
            unique_keys[parent].add(b)
        label = short("og70_fk_" + source + "_" + c["name"])
        # Different row types may share similarly named fields. Only emit a
        # full FK when those fields have no incompatible owner across types.
        sibling_constraints = []
        for sibling in plan[owner]["members"]:
            for sibling_c in tables[sibling]["constraints"]:
                if sibling_c["kind"] == "f":
                    physical_cols = tuple(maps[sibling][x] for x in sibling_c["columns"])
                    if physical_cols == a:
                        sibling_constraints.append((sources[sibling_c["ref_table"]]["target"], sibling_c["ref_columns"]))
        if any(p != parent for p, keys in sibling_constraints):
            continue
        suffix = " NOT VALID" if "NOT VALID" in c["definition"] else ""
        sql.append(f"ALTER TABLE public.{owner} ADD CONSTRAINT {label} FOREIGN KEY({','.join(a)}) REFERENCES public.{parent}({','.join(b)}) DEFERRABLE INITIALLY DEFERRED{suffix};")

for view, definition in meta["views"].items():
    sql.append(f"CREATE VIEW public.{view} AS {qualify(definition).rstrip(';')};")

sql += [
    "ALTER TABLE public.cart_items ADD CONSTRAINT og70_cart_user_fk FOREIGN KEY(user_id) REFERENCES public.users(user_id) DEFERRABLE INITIALLY DEFERRED;",
    "CREATE INDEX og70_cart_user_idx ON public.cart_items(user_id);",
    "ALTER TABLE public.order_items ADD CONSTRAINT og70_item_conversation_fk FOREIGN KEY(conversation_id) REFERENCES public.conversations(conversation_id) DEFERRABLE INITIALLY DEFERRED;"
]

sql += ["SELECT public.og70_validate_graph(true);",
        "DROP SCHEMA _og70_v21 CASCADE;",
        """DO $count$ BEGIN
IF (SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tablename<>'flyway_schema_history')<>48 THEN
 RAISE EXCEPTION 'Expected exactly 48 physical business tables'; END IF;
END $count$;"""]

with OUT.open("a", encoding="utf-8") as stream:
    stream.write("\n\n".join(sql) + "\n")
print(f"Appended {len(row_functions)} adapted row guards, 104 writable projections, native FK constraints and final relational proof.")
