-- V60: Add opt-in public avatar consent to seller_profiles and composite indexes for Public Seller Profile read queries

ALTER TABLE public.seller_profiles
    ADD COLUMN IF NOT EXISTS allow_public_avatar BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN public.seller_profiles.allow_public_avatar
    IS 'Opt-in consent (default FALSE) allowing seller profile avatar_url to be shown on Public Seller Profile.';

CREATE OR REPLACE VIEW og_compat.seller_profiles AS
SELECT seller_profile_id,
       user_id,
       revision_no,
       state,
       ekyc_profile_id,
       quick_auth_result_id,
       pickup_address_id,
       bank_snapshot,
       updated_at,
       allow_public_avatar
FROM public.seller_profiles p
WHERE record_type = 'seller_profiles'::text;

CREATE INDEX IF NOT EXISTS idx_seller_profiles_user_latest
    ON public.seller_profiles (user_id, seller_profile_id DESC)
    WHERE record_type = 'seller_profiles';

CREATE INDEX IF NOT EXISTS idx_products_seller_active
    ON public.products (seller_id, created_at DESC, product_id DESC)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_reviews_reviewee_active
    ON public.reviews (reviewee_id, created_at DESC, review_id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_orders_seller_completed
    ON public.orders (seller_id)
    WHERE status = 'COMPLETED';

