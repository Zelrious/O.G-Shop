-- V22 compatibility defaults may reset a shared aggregate PK sequence using
-- only the logical base subtype. Imported sibling rows also consume that PK.
-- Advance shared sequences over every physical subtype without changing IDs.
DO $align$
DECLARE r record; seq_name text; maximum_id bigint; last_id bigint; used boolean;
BEGIN
 PERFORM pg_advisory_xact_lock(700048);
 FOR r IN
  SELECT c.relname AS table_name,a.attname AS column_name,
         pg_get_expr(d.adbin,d.adrelid) AS expression
  FROM pg_class c
  JOIN pg_namespace n ON n.oid=c.relnamespace
  JOIN pg_constraint k ON k.conrelid=c.oid AND k.contype='p'
  JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum=ANY(k.conkey)
  JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum
  WHERE n.nspname='public' AND c.relkind='r' AND c.relname<>'flyway_schema_history'
    AND pg_get_expr(d.adbin,d.adrelid) LIKE 'nextval(%'
 LOOP
  seq_name:=substring(r.expression FROM 'nextval\(''([^'']+)''');
  IF seq_name IS NULL THEN RAISE EXCEPTION 'Unknown identity default'; END IF;
  EXECUTE format('LOCK TABLE public.%I IN ACCESS EXCLUSIVE MODE',r.table_name);
  EXECUTE format('SELECT max(%I) FROM public.%I',r.column_name,r.table_name) INTO maximum_id;
  EXECUTE format('SELECT last_value,is_called FROM %s',seq_name::regclass) INTO last_id,used;
  PERFORM setval(seq_name::regclass,greatest(coalesce(maximum_id,1),last_id),used OR maximum_id IS NOT NULL);
 END LOOP;
END $align$;
