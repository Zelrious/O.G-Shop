-- ============================================================================
-- V57__remediate_v49_reward_ledger_expires_at_in_dispatch.sql
-- Module: Payment / Settlement / Trust & Safety
-- Business Context: Defensive forward-only remediation of public.og70_dispatch()
--                   reward_ledger branch for expires_at forwarding.
--
-- States handled:
--   - State A (Remote V49 fixed): strict no-op (returns without EXECUTE).
--   - State C (Local V52 equivalent already applied): strict no-op (returns without EXECUTE).
--   - State B (Unfixed V22 dispatch missing expires_at in nj/oj/BEFORE):
--       scoped atomic patch inside reward_ledger branch with full pre/post invariant checks.
--   - Malformed / Partial / Duplicate / Out-of-order: fail-closed (SQLSTATE 23514).
-- ============================================================================

DO $migration$
DECLARE
    def text;
    norm_def text;
    reward_branch text;
    norm_branch text;
    patched_branch text;
    patched_def text;
    norm_patched_def text;
    occ integer;

    -- Unique branch boundary markers
    m_when text := $m$WHEN 'reward_ledger'$m$;
    m_header text := E'WHEN ''reward_ledger'' THEN\nCASE coalesce(n->>''record_type'',o->>''record_type'')\nWHEN ''reward_ledger'' THEN';
    m_mark text := $m$PERFORM og70_mark('reward_ledger');$m$;
    branch_pattern text := $re$WHEN 'reward_ledger' THEN\r?\nCASE coalesce\(n->>'record_type',o->>'record_type'\)\r?\nWHEN 'reward_ledger' THEN.*?PERFORM og70_mark\('reward_ledger'\);$re$;
    norm_branch_pattern text := $re$WHEN 'reward_ledger' THEN\nCASE coalesce\(n->>'record_type',o->>'record_type'\)\nWHEN 'reward_ledger' THEN.*?PERFORM og70_mark\('reward_ledger'\);$re$;

    -- Unfixed (State B) anchors
    a_nj text := $s$jsonb_build_object('entry_id',n->'entry_id','user_id',n->'user_id','policy_id',n->'policy_id','entry_type',n->'entry_type','points_delta',n->'points_delta','order_id',n->'order_id','group_id',n->'group_id','actor_id',n->'actor_id','reason',n->'reason','created_at',n->'created_at','command_key',n->'command_key')$s$;
    a_oj text := $s$jsonb_build_object('entry_id',o->'entry_id','user_id',o->'user_id','policy_id',o->'policy_id','entry_type',o->'entry_type','points_delta',o->'points_delta','order_id',o->'order_id','group_id',o->'group_id','actor_id',o->'actor_id','reason',o->'reason','created_at',o->'created_at','command_key',o->'command_key')$s$;
    a_val text := $s$IF TG_OP<>'DELETE' THEN PERFORM og70_validate_row('reward_ledger',result); END IF;$s$;

    -- Fixed (State A / State C) exact expressions
    r_nj text := $s$jsonb_build_object('entry_id',n->'entry_id','user_id',n->'user_id','policy_id',n->'policy_id','entry_type',n->'entry_type','points_delta',n->'points_delta','order_id',n->'order_id','group_id',n->'group_id','actor_id',n->'actor_id','reason',n->'reason','created_at',n->'created_at','command_key',n->'command_key','expires_at',n->'expires_at')$s$;
    r_oj text := $s$jsonb_build_object('entry_id',o->'entry_id','user_id',o->'user_id','policy_id',o->'policy_id','entry_type',o->'entry_type','points_delta',o->'points_delta','order_id',o->'order_id','group_id',o->'group_id','actor_id',o->'actor_id','reason',o->'reason','created_at',o->'created_at','command_key',o->'command_key','expires_at',o->'expires_at')$s$;
    b_exp text := $s$IF TG_OP<>'DELETE' THEN n:=jsonb_set(n,ARRAY['expires_at'],coalesce(result->'expires_at','null'::jsonb),true); END IF;$s$;
    r_val text := $s$IF TG_OP<>'DELETE' THEN n:=jsonb_set(n,ARRAY['expires_at'],coalesce(result->'expires_at','null'::jsonb),true); END IF;
    IF TG_OP<>'DELETE' THEN PERFORM og70_validate_row('reward_ledger',result); END IF;$s$;

    -- Strict ordered r_val regex (handles LF or CRLF + horizontal indentation only between b_exp and a_val)
    r_val_pattern text := $re$IF TG_OP<>'DELETE' THEN n:=jsonb_set\(n,ARRAY\['expires_at'\],coalesce\(result->'expires_at','null'::jsonb\),true\); END IF;\n[ \t]*IF TG_OP<>'DELETE' THEN PERFORM og70_validate_row\('reward_ledger',result\); END IF;$re$;

    cnt_when integer;
    cnt_header integer;
    cnt_mark integer;
    cnt_a_nj integer;
    cnt_a_oj integer;
    cnt_r_nj integer;
    cnt_r_oj integer;
    cnt_b_exp integer;
    cnt_a_val integer;
    cnt_r_val integer;
BEGIN
    def := pg_get_functiondef('public.og70_dispatch()'::regprocedure);
    norm_def := regexp_replace(def, E'\r\n', E'\n', 'g');

    -- 1. Pre-extraction unique branch marker verification (fail-closed)
    cnt_when   := (length(norm_def) - length(replace(norm_def, m_when, ''))) / length(m_when);
    cnt_header := (length(norm_def) - length(replace(norm_def, m_header, ''))) / length(m_header);
    cnt_mark   := (length(norm_def) - length(replace(norm_def, m_mark, ''))) / length(m_mark);

    IF cnt_when <> 2 OR cnt_header <> 1 OR cnt_mark <> 1 THEN
        RAISE EXCEPTION 'Migration V57 failed: unique reward_ledger branch markers violated (when=%, header=%, mark=%)',
            cnt_when, cnt_header, cnt_mark USING ERRCODE = '23514';
    END IF;

    -- 2. Verify branch_pattern matches exactly once before extraction
    SELECT count(*) INTO occ FROM regexp_matches(norm_def, norm_branch_pattern, 'g');
    IF occ <> 1 THEN
        RAISE EXCEPTION 'Migration V57 failed: branch_pattern expected 1 match, found %', occ USING ERRCODE = '23514';
    END IF;

    reward_branch := substring(def FROM branch_pattern);
    IF reward_branch IS NULL THEN
        RAISE EXCEPTION 'Migration V57 failed: reward_ledger branch extraction returned NULL' USING ERRCODE = '23514';
    END IF;

    norm_branch := regexp_replace(reward_branch, E'\r\n', E'\n', 'g');

    cnt_a_nj  := (length(reward_branch) - length(replace(reward_branch, a_nj, ''))) / length(a_nj);
    cnt_a_oj  := (length(reward_branch) - length(replace(reward_branch, a_oj, ''))) / length(a_oj);
    cnt_r_nj  := (length(reward_branch) - length(replace(reward_branch, r_nj, ''))) / length(r_nj);
    cnt_r_oj  := (length(reward_branch) - length(replace(reward_branch, r_oj, ''))) / length(r_oj);
    cnt_b_exp := (length(reward_branch) - length(replace(reward_branch, b_exp, ''))) / length(b_exp);
    cnt_a_val := (length(reward_branch) - length(replace(reward_branch, a_val, ''))) / length(a_val);
    SELECT count(*) INTO cnt_r_val FROM regexp_matches(norm_branch, r_val_pattern, 'g');

    -- 3. State A & State C: already fully fixed -> strict no-op (NO EXECUTE)
    IF cnt_r_nj = 1 AND cnt_r_oj = 1 AND cnt_b_exp = 1 AND cnt_a_val = 1 AND cnt_r_val = 1
       AND cnt_a_nj = 0 AND cnt_a_oj = 0 THEN
        RETURN;
    END IF;

    -- 4. State B guard: reject any partial/duplicate/out-of-order state (fail-closed)
    IF NOT (cnt_a_nj = 1 AND cnt_a_oj = 1 AND cnt_a_val = 1
            AND cnt_r_nj = 0 AND cnt_r_oj = 0 AND cnt_b_exp = 0 AND cnt_r_val = 0) THEN
        RAISE EXCEPTION 'Migration V57 failed: unexpected/partial/duplicate reward_ledger branch state (a_nj=%, a_oj=%, r_nj=%, r_oj=%, b_exp=%, a_val=%, r_val=%)',
            cnt_a_nj, cnt_a_oj, cnt_r_nj, cnt_r_oj, cnt_b_exp, cnt_a_val, cnt_r_val USING ERRCODE = '23514';
    END IF;

    -- 5. Patch strictly inside reward_branch
    patched_branch := replace(reward_branch, a_nj, r_nj);
    patched_branch := replace(patched_branch, a_oj, r_oj);
    patched_branch := replace(patched_branch, a_val, r_val);

    -- 6. Post-patch branch invariant verification
    norm_branch := regexp_replace(patched_branch, E'\r\n', E'\n', 'g');
    IF (length(patched_branch) - length(replace(patched_branch, r_nj, ''))) / length(r_nj) <> 1
       OR (length(patched_branch) - length(replace(patched_branch, r_oj, ''))) / length(r_oj) <> 1
       OR (length(patched_branch) - length(replace(patched_branch, b_exp, ''))) / length(b_exp) <> 1
       OR (length(patched_branch) - length(replace(patched_branch, a_val, ''))) / length(a_val) <> 1
       OR (SELECT count(*) FROM regexp_matches(norm_branch, r_val_pattern, 'g')) <> 1 THEN
        RAISE EXCEPTION 'Migration V57 failed: post-patch branch verification failed' USING ERRCODE = '23514';
    END IF;

    -- 7. Splice and post-patch full-function marker count verification before EXECUTE
    occ := (length(def) - length(replace(def, reward_branch, ''))) / length(reward_branch);
    IF occ <> 1 THEN
        RAISE EXCEPTION 'Migration V57 failed: reward_ledger branch splice expected 1, found %', occ USING ERRCODE = '23514';
    END IF;

    patched_def := replace(def, reward_branch, patched_branch);
    norm_patched_def := regexp_replace(patched_def, E'\r\n', E'\n', 'g');

    IF (length(norm_patched_def) - length(replace(norm_patched_def, m_when, ''))) / length(m_when) <> 2
       OR (length(norm_patched_def) - length(replace(norm_patched_def, m_header, ''))) / length(m_header) <> 1
       OR (length(norm_patched_def) - length(replace(norm_patched_def, m_mark, ''))) / length(m_mark) <> 1 THEN
        RAISE EXCEPTION 'Migration V57 failed: post-patch marker counts changed unexpectedly' USING ERRCODE = '23514';
    END IF;

    EXECUTE patched_def;
END;
$migration$;

