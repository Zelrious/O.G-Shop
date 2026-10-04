-- ============================================================================
-- V10: Product Moderation Enforcement & Revision Tracking (UC70)
-- 1. Add content_revision to products to decouple content changes from @Version
-- 2. Enforce product_version >= 0 and strict decision-reason checks on decisions
-- 3. Enforce append-only invariants on product_moderation_decisions via trigger
-- ============================================================================

-- 1. Decouple content changes from entity @Version
ALTER TABLE products ADD COLUMN IF NOT EXISTS content_revision BIGINT NOT NULL DEFAULT 1;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_products_content_revision'
    ) THEN
        ALTER TABLE products ADD CONSTRAINT ck_products_content_revision CHECK (content_revision >= 1);
    END IF;
END $$;

-- 2. Clean up legacy data and enforce strict checks on product_moderation_decisions
UPDATE product_moderation_decisions SET reason = NULL WHERE decision = 'APPROVED' AND reason IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_moderation_decisions_version'
    ) THEN
        ALTER TABLE product_moderation_decisions ADD CONSTRAINT ck_moderation_decisions_version CHECK (product_version >= 0);
    END IF;
END $$;

ALTER TABLE product_moderation_decisions DROP CONSTRAINT IF EXISTS ck_moderation_decisions_reason;

ALTER TABLE product_moderation_decisions ADD CONSTRAINT ck_moderation_decisions_reason CHECK (
    (decision = 'REJECTED' AND reason IS NOT NULL AND length(trim(reason)) > 0)
    OR (decision = 'APPROVED' AND reason IS NULL)
);

-- 3. Append-only enforcement via trigger preventing UPDATE and DELETE
CREATE OR REPLACE FUNCTION trg_product_moderation_decisions_append_only()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Table product_moderation_decisions is append-only. UPDATE and DELETE operations are prohibited.';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_no_update_delete_moderation_decisions ON product_moderation_decisions;

CREATE TRIGGER trg_no_update_delete_moderation_decisions
BEFORE UPDATE OR DELETE ON product_moderation_decisions
FOR EACH ROW
EXECUTE FUNCTION trg_product_moderation_decisions_append_only();
