-- Migration V18: Add GHN master-data codes to addresses and snapshot codes to orders
-- 1. Extend addresses table for GHN normalized address structure
ALTER TABLE addresses ADD COLUMN IF NOT EXISTS ghn_province_id INTEGER;
ALTER TABLE addresses ADD COLUMN IF NOT EXISTS ghn_district_id INTEGER;
ALTER TABLE addresses ADD COLUMN IF NOT EXISTS ghn_ward_code VARCHAR(20);

-- 2. Extend orders table to snapshot recipient GHN codes for reliable fulfillment
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_ghn_province_id INTEGER;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_ghn_district_id INTEGER;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_ghn_ward_code VARCHAR(20);
