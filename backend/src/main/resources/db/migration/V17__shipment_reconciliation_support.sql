-- Migration V17: Separate reconciliation scheduling from active claim lease
-- 1. Add dedicated next_reconciliation_at and reconciliation_attempts to shipments table
ALTER TABLE shipments ADD COLUMN IF NOT EXISTS next_reconciliation_at TIMESTAMPTZ;
ALTER TABLE shipments ADD COLUMN IF NOT EXISTS reconciliation_attempts INT NOT NULL DEFAULT 0;

-- 2. Partial index for fast untracked pending shipment reconciliation scanning
CREATE INDEX IF NOT EXISTS ix_shipments_untracked_recon
    ON shipments (next_reconciliation_at)
    WHERE status = 'PENDING' AND tracking_number IS NULL AND admin_review_required = FALSE;
