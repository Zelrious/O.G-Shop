-- Never infer content proof from legacy entity-version numbers.
ALTER TABLE product_moderation_decisions ADD COLUMN decision_content_revision BIGINT;
ALTER TABLE product_moderation_decisions ADD CONSTRAINT ck_moderation_content_proof
    CHECK (decision_content_revision IS NULL OR decision_content_revision >= 1);

-- The pre-V10 callback populates this before immutable V10 normalizes reasons.
-- Already-applied V10 installations require backups to recover removed text.
CREATE TABLE IF NOT EXISTS product_moderation_legacy_history (
    decision_id BIGINT PRIMARY KEY REFERENCES product_moderation_decisions(decision_id),
    original_record JSONB NOT NULL,
    preserved_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TRIGGER trg_no_update_delete_moderation_legacy_history
BEFORE UPDATE OR DELETE ON product_moderation_legacy_history
FOR EACH ROW EXECUTE FUNCTION trg_product_moderation_decisions_append_only();
