-- Migration V40: Dispute arbitration cases and order reviews (UC61 / UC62 / UC63 / UC74 / UC75 / UC83)

CREATE TABLE IF NOT EXISTS public.dispute_cases (
  dispute_id BIGSERIAL PRIMARY KEY,
  order_id BIGINT NOT NULL REFERENCES public.orders(order_id),
  buyer_id BIGINT NOT NULL REFERENCES public.users(user_id),
  seller_id BIGINT NOT NULL REFERENCES public.users(user_id),
  reason VARCHAR(64) NOT NULL,
  description TEXT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
  buyer_evidence_urls TEXT NULL,
  video_unboxing_url TEXT NULL,
  seller_response TEXT NULL,
  seller_evidence_urls TEXT NULL,
  seller_response_due_at TIMESTAMP WITH TIME ZONE NULL,
  verdict_action VARCHAR(64) NULL,
  verdict_notes TEXT NULL,
  refund_amount NUMERIC(19, 2) NULL,
  penalty_points INT NOT NULL DEFAULT 0,
  resolved_by BIGINT NULL REFERENCES public.users(user_id),
  resolved_at TIMESTAMP WITH TIME ZONE NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dispute_cases_status ON public.dispute_cases(status);
CREATE INDEX IF NOT EXISTS idx_dispute_cases_order_id ON public.dispute_cases(order_id);
CREATE INDEX IF NOT EXISTS idx_dispute_cases_buyer_id ON public.dispute_cases(buyer_id);
CREATE INDEX IF NOT EXISTS idx_dispute_cases_seller_id ON public.dispute_cases(seller_id);
