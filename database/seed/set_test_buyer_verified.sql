-- DEVELOPMENT ONLY: synthetic positive eKYC guard fixture; no real AI/KYC result.
-- Buyer role remains BUYER. Does not overwrite an existing verification.
BEGIN;
SET LOCAL lock_timeout = '5s';
DO $fixture$
DECLARE
    buyer_id BIGINT;
BEGIN
    PERFORM pg_advisory_xact_lock(73939002);
    SELECT (new_values -> 'accounts' ->> 'buyer')::BIGINT INTO buyer_id FROM audit_logs
    WHERE action = 'DEV_SEED_CREATED' AND request_id = 'og-shop-development-test-v1' ORDER BY log_id LIMIT 1;
    IF buyer_id IS NULL OR NOT EXISTS (SELECT 1 FROM users WHERE user_id = buyer_id AND email = 'mua@gmail.com') THEN
        RAISE EXCEPTION 'Only the buyer created by development_test_data.sql can be changed.';
    END IF;
    IF EXISTS (SELECT 1 FROM seller_verifications WHERE user_id = buyer_id AND status = 'VERIFIED') THEN
        RAISE NOTICE 'Test buyer already VERIFIED; preserving existing verification.';
        RETURN;
    END IF;
    IF EXISTS (SELECT 1 FROM seller_verifications WHERE user_id = buyer_id AND status = 'PENDING') THEN
        RAISE EXCEPTION 'Test buyer has a pending verification. Resolve it through the application first.';
    END IF;
    INSERT INTO seller_verifications (user_id,verification_method,status,document_data,reviewed_at)
    VALUES (buyer_id,'MVP_BYPASS','VERIFIED',
            '{"fixture":"og-shop-development-test-v1","note":"Synthetic BUYER verification for guard test; no AI or real KYC"}',
            CURRENT_TIMESTAMP);
    INSERT INTO audit_logs (user_id,action,entity_type,entity_id,new_values,request_id)
    VALUES (buyer_id,'DEV_TEST_BUYER_VERIFIED','User',buyer_id,
            '{"status":"VERIFIED","method":"MVP_BYPASS","synthetic":true}',
            'og-shop-development-test-v1');
    RAISE NOTICE 'Synthetic test buyer is VERIFIED. No SELLER role granted; no real eKYC performed.';
END;
$fixture$;
COMMIT;
