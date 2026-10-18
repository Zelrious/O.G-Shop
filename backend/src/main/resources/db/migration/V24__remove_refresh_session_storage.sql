-- Owner chose fixed-duration login without renewal. Existing refresh credentials
-- are invalidated. Make a private full backup before applying to a populated DB.
-- No other business table or OTP/quick-auth state is changed.
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '120s';
SELECT pg_advisory_xact_lock(700048);

-- V22 checks the full relationship graph at commit. Remove only the refresh
-- branch before dropping its projection; retain all other checks unchanged.
DO $migration$
DECLARE
    definition text;
    block_start integer;
    block_end integer;
    anchor constant text := 'IF dirty ? ''refresh_sessions'' THEN';
    terminator constant text := E'\nEND IF;';
BEGIN
    definition := replace(pg_get_functiondef('public.og70_validate_graph(boolean)'::regprocedure), E'\r\n', E'\n');
    block_start := strpos(definition, anchor);
    IF block_start = 0 OR strpos(substring(definition FROM block_start + length(anchor)), anchor) <> 0 THEN
        RAISE EXCEPTION 'Expected one V23 refresh graph branch';
    END IF;
    block_end := strpos(substring(definition FROM block_start), terminator);
    IF block_end = 0 THEN RAISE EXCEPTION 'Cannot locate refresh graph branch terminator'; END IF;
    definition := overlay(definition PLACING '' FROM block_start FOR block_end + length(terminator) - 1);
    definition := replace(definition, '"refresh_sessions":true,', '');
    IF strpos(definition, 'refresh_sessions') <> 0 THEN
        RAISE EXCEPTION 'Unexpected remaining refresh graph reference';
    END IF;
    EXECUTE definition;
END;
$migration$;

DROP VIEW og_compat.refresh_sessions;
DROP TABLE public.refresh_sessions;
