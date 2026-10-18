-- Migration V15: Enhance shipments table for GHN Integration
-- Standardize carrier column, add client_order_code, fee, expected delivery, raw status, and created_at

-- 1. Set default carrier to 'GHN' (carrier is VARCHAR(100) nullable in V1, migration sets default to 'GHN')
ALTER TABLE shipments ALTER COLUMN carrier SET DEFAULT 'GHN';

-- 2. Add client_order_code for partner tracking code (max 50 chars as required by GHN)
ALTER TABLE shipments ADD COLUMN IF NOT EXISTS client_order_code VARCHAR(50);

-- 3. Add actual shipping fee from GHN
ALTER TABLE shipments ADD COLUMN IF NOT EXISTS shipping_fee NUMERIC(19, 2);

-- 4. Add expected delivery time provided by GHN
ALTER TABLE shipments ADD COLUMN IF NOT EXISTS expected_delivery_at TIMESTAMPTZ;

-- 5. Add raw GHN status string for audit & state machine tracking
ALTER TABLE shipments ADD COLUMN IF NOT EXISTS raw_ghn_status VARCHAR(50);

-- 6. Add record creation timestamp
ALTER TABLE shipments ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 7. Partial unique index on client_order_code (allow NULL for legacy rows, unique when present)
CREATE UNIQUE INDEX IF NOT EXISTS uq_shipments_client_order_code
    ON shipments (client_order_code)
    WHERE client_order_code IS NOT NULL;
