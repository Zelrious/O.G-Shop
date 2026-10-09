-- Owner chose fixed-duration login without renewal. Existing refresh credentials
-- are invalidated. Make a private full backup before applying to a populated DB.
-- No other business table or OTP/quick-auth state is changed.
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '120s';
SELECT pg_advisory_xact_lock(700048);

DROP VIEW og_compat.refresh_sessions;
DROP TABLE public.refresh_sessions;
