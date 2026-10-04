-- ============================================================================
-- V7: Buyer eKYC Guard and User Profile Baseline
-- - Add requires_buyer_ekyc to products table (DP-19)
-- - Add bank account details to users table for Seller payout profile (UC05)
-- ============================================================================

ALTER TABLE products
    ADD COLUMN requires_buyer_ekyc BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX ix_products_requires_buyer_ekyc ON products (requires_buyer_ekyc) WHERE requires_buyer_ekyc = TRUE;

ALTER TABLE users
    ADD COLUMN bank_name VARCHAR(100),
    ADD COLUMN bank_account_number VARCHAR(50),
    ADD COLUMN bank_account_holder VARCHAR(120);
