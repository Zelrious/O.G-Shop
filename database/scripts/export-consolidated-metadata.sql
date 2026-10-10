-- Structure only. No rows, passwords, tokens or private evidence are exported.
SELECT jsonb_build_object(
 'version',(SELECT max(version::int) FROM public.flyway_schema_history WHERE success),
 'tables',(SELECT jsonb_object_agg(c.relname,jsonb_build_object(
   'columns',(SELECT jsonb_agg(jsonb_build_object(
     'name',a.attname,'type',format_type(a.atttypid,a.atttypmod),
     'notnull',a.attnotnull,'generated',a.attgenerated,
     'default',pg_get_expr(d.adbin,d.adrelid)) ORDER BY a.attnum)
    FROM pg_attribute a LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
    WHERE a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped),
   'constraints',(SELECT coalesce(jsonb_agg(jsonb_build_object(
     'name',x.conname,'kind',x.contype,'definition',pg_get_constraintdef(x.oid),
     'validated',x.convalidated,
     'columns',(SELECT jsonb_agg(a.attname ORDER BY u.ord) FROM unnest(x.conkey) WITH ORDINALITY u(num,ord) JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum=u.num),
     'ref_table',(SELECT relname FROM pg_class WHERE oid=x.confrelid),
     'ref_columns',(SELECT jsonb_agg(a.attname ORDER BY u.ord) FROM unnest(x.confkey) WITH ORDINALITY u(num,ord) JOIN pg_attribute a ON a.attrelid=x.confrelid AND a.attnum=u.num)
   ) ORDER BY x.conname),'[]'::jsonb) FROM pg_constraint x WHERE x.conrelid=c.oid),
   'indexes',(SELECT coalesce(jsonb_agg(pg_get_indexdef(i.indexrelid) ORDER BY i.indexrelid),'[]'::jsonb)
    FROM pg_index i WHERE i.indrelid=c.oid AND NOT EXISTS(SELECT 1 FROM pg_constraint x WHERE x.conindid=i.indexrelid))
 )) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname='public' AND c.relkind='r' AND c.relname<>'flyway_schema_history'),
 'sequences',(SELECT coalesce(jsonb_object_agg(sequencename,jsonb_build_object('type',data_type,'increment',increment_by,'min',min_value,'max',max_value,'start',start_value,'cache',cache_size,'cycle',cycle)),'{}'::jsonb)
 FROM pg_sequences WHERE schemaname='public'),
 'report_views',(SELECT jsonb_agg(viewname ORDER BY viewname) FROM pg_views WHERE schemaname='public'),
 'compatibility_views',(SELECT count(*) FROM pg_views WHERE schemaname='og_compat')
);
